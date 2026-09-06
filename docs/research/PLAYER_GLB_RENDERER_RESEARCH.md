# Meshy 플레이어 GLB 렌더러 리서치

작성일: 2026-09-05  
상태: **리서치 완료 — 구현 승인 대기**

## 범위와 보존 원칙

이 문서는 MurimRPG Fabric 전용 클라이언트의 고밀도 Meshy 플레이어 모델 렌더링만 다룬다. 서버의
공격 판정, 이동 속도, 충돌 크기, EXP, 화폐, SQLite, 목검 및 허수아비 기능은 변경 범위가 아니다.
현재 작업 트리는 사용자 변경사항을 포함해 이미 변경된 상태였으므로 삭제·되돌리기 없이 읽기 전용으로
분석했다.

관련 영상은 모델링 튜토리얼이 아니라 복잡한 AI 개발을 `리서치 → 계획 문서 → 사람의 주석·승인 →
구현 → 검증`으로 분리하는 워크플로우를 설명한다. 이 문서는 그 리서치 산출물이다.

- 영상: <https://www.youtube.com/watch?v=6Z6Le3Xwqdg>
- 현재 브랜치: `feature/advanced-realms`
- 대상: Minecraft Java 26.2, Fabric Loader 0.19.3, Java 25

## 현재 호출 흐름

```text
MurimRpgClient.onInitializeClient
  → ClientLifecycleEvents.CLIENT_STARTED
  → AnimatedPlayerGlbRenderer.loadResources
  → walking.glb / running.glb 파싱, 동적 텍스처 등록

LivingEntityRendererMixin (submitModel redirect)
  → 로컬 AvatarRenderState이며 지원되는 상태인지 shouldReplace 검사
  → AnimatedPlayerGlbRenderer.submit
  → GlbAnimatedPlayerMesh.render
  → CPU skinning 후 SubmitNodeCollector custom geometry 출력
```

`PlayerModelMixin`은 바닐라 HumanoidModel의 자세를 보정한다. 현재는 `shouldReplace`가 참이면
일찍 반환하므로, 고밀도 모델을 보이는 프레임에는 바닐라 팔·다리 보정이 적용되지 않는다.

`LivingEntityRendererMixin`은 일반 `LivingEntityRenderer`의 `submitModel` 호출을 가로채며,
실제로는 `AvatarRenderState`이고 로컬 플레이어 ID와 일치할 때만 고밀도 메시를 낸다. 갑옷, 웅크리기,
활공, 수영, 탑승, 아이템 사용, 공격, 사망 상태는 바닐라 렌더러로 fallback 한다.

## 실제 GLB 분석

두 파일은 `Khronos glTF Blender I/O v4.0.43`가 생성한 glTF 2.0 GLB다.

| 항목 | walking.glb | running.glb | 판정 |
| --- | ---: | ---: | --- |
| 노드 수 | 26 | 26 | 동일 |
| 메시 수 | 1 | 1 | 동일 |
| 스킨 수 | 1 | 1 | 동일 |
| 애니메이션 수 | 1 | 1 | 동일 |
| 정점 수 | 22,395 | 22,395 | 동일 |
| 삼각형 수 | 32,774 | 32,774 | 동일 |
| JOINTS_0 | UNSIGNED_BYTE VEC4 | UNSIGNED_BYTE VEC4 | skin.joints 인덱스 형식 |
| WEIGHTS_0 | FLOAT VEC4 | FLOAT VEC4 | 런타임에서 합계 검증 필요 |
| index | UNSIGNED_SHORT | UNSIGNED_SHORT | 유효 |
| inverse bind matrices | FLOAT MAT4 × 24 | FLOAT MAT4 × 24 | 존재 |
| 스킨 조인트 | 24 | 24 | 동일 |
| 애니메이션 길이 | 1.0667초 | 0.6667초 | 서로 다름 |
| 원본 POSITION 높이 | 1.8 | 1.8 | 메시 로컬 공간 |
| 원본 POSITION 폭 | 0.87245 | 0.87245 | 메시 로컬 공간 |
| 원본 POSITION 깊이 | 0.61716 | 0.61716 | 메시 로컬 공간 |

조인트는 `Hips`, 양쪽 허벅지·종아리·발·발끝, `Spine` 계열, 양쪽 어깨·상완·하완·손,
`neck`, `Head` 계열로 구성된다. 두 파일은 같은 메시와 같은 조인트 순서를 가지므로, 원칙적으로
동일한 스켈레톤에서 WALK/RUN 블렌딩이 가능하다.

### 좌표계에서 발견한 핵심 사실

`char1` 메시 노드는 `Armature`의 자식이다. `Armature`의 로컬 scale은
`0.01, 0.01, 0.01`이다. 반면 Hips의 로컬 Y 좌표는 약 `98.18`, 다리·척추·팔 관절도
10~40 단위 범위다. 즉 Blender 내보내기 과정에서 **메시 로컬 좌표와 아마추어 계층 좌표의 단위가
다르며, 루트 scale 0.01이 이를 보정한다.**

현재 구현은 다음 세 변환을 함께 사용한다.

1. `inverse(global(meshNode))`를 joint palette의 앞에 곱한다.
2. 루트 Armature의 `0.01` scale은 global 행렬에 포함된다.
3. 출력 정점에 `player-model.yml`의 별도 scale을 곱한다.

이 조합이 glTF bind pose에서 정확히 상쇄되는지 아직 자동 검증되지 않았다. 스크린샷에서 모델이
지나치게 커지고 팔·다리가 비정상 위치로 가는 증상은 이 좌표계/행렬 조합이 가장 유력한 원인이다.

### 애니메이션 채널

walking GLB는 각 주요 관절에 translation, rotation, scale 채널을 모두 갖는다(총 72 channels).
예를 들어 Hips translation 첫 키는 `(-0.9448, 96.9476, -8.3561)`로 노드 기본값
`(-0.8772, 98.1758, -13.0766)`와 같은 절대 로컬 공간이다. 따라서 이를 단순 delta로 더하면 안 되며,
애니메이션 출력은 로컬 TRS를 대체해야 한다.

현재 스키너는 rotation·translation·scale을 모두 대체하는 방향으로 작성되어 있어 이 점은 맞다.
다만 다음은 아직 증명되지 않았다.

- glTF column-major MAT4가 JOML `Matrix4f`에 같은 방향으로 들어가는지
- `inverseMesh × globalJoint × inverseBind` 행렬 순서가 이 Blender export에 맞는지
- 루트 Hips X/Z root motion만 제거하고 Y motion은 유지하는 방식이 전체 pose를 깨지 않는지
- 각 keyframe에서 skinning 후 정점 bounds가 정상 인체 범위에 있는지
- JOINTS_0의 값이 실제로 0~23 범위인지, 모든 weight 합이 1에 가까운지

## 스크린샷과 로그 근거

2026-09-05 11:29 로그에는 다음이 확인된다.

- 클라이언트가 `walking.glb`, `running.glb`를 모두 로드했다.
- 고밀도 렌더러가 22,395 정점, 32,774 삼각형, 24 조인트로 초기화됐다.
- 설정 scale `0.055 / 0.0625 / 0.055`를 실제로 읽었다.
- `Submitted full Meshy player geometry` 로그가 출력됐다.

따라서 “GLB를 못 읽어서 바닐라 모델이 보이는 문제”는 아니다. 반대로 제공된 스크린샷에서는 모델의
크기·관절·대기 포즈가 모두 불안정하므로, **현재 스키닝 결과가 정상 인체 pose라는 증거도 없다.**

또한 스크린샷의 흰색·빨간 상자는 Minecraft의 `F3+B` 엔티티 hitbox/방향 디버그 표시다. 이는
고밀도 모델의 메시 오류가 아니며, 렌더러가 hitbox를 변경하는 근거도 없다.

## 성능 평가

현재 구현은 32,774개의 삼각형을 `SubmitNodeCollector`에 사각형 형태로 내보내기 위해 마지막 정점을
중복하여 프레임당 약 131,096 정점을 출력한다. CPU skinning은 프레임마다 노드·행렬 배열을 만들고,
각 정점의 최대 4개 influence를 계산한다. 로컬 플레이어 한 명만 대상이더라도 장시간 F5 시점에서는
프레임 저하 위험이 높다.

정점·normal scratch 객체 일부는 재사용하지만, 최종적인 GPU skinning·정점 버퍼 캐시·거리/품질 제어는
아직 구현되지 않았다.

## 외부 검증 상태

이 작업 환경에서는 `blender` 실행 파일을 표준 PATH 및 Program Files 검색에서 찾지 못했다.
따라서 Blender에서 정상 pose를 확인했다고 주장할 수 없다. 구현 승인 뒤 Blender를 설치하거나,
별도 검증 가능한 GLB 뷰어/변환 파이프라인을 선택해야 한다.

`rig-metadata.json`의 라이선스 상태는 `REVIEW_REQUIRED`다. 상업 배포 전에는 Meshy 계정의
현재 약관·사용 플랜·생성 asset 권리를 사용자가 직접 확인해야 한다.

## 원인 판정

| 증상 | 현재 판정 | 근거 | 확신도 |
| --- | --- | --- | --- |
| 모델이 과도하게 커 보임 | GLB Armature 0.01과 renderer 최종 scale/PoseStack 좌표계가 혼합됨 | node 25 Armature scale, 실제 설정 로드 로그, 스크린샷 | 높음 |
| 팔·다리 뒤틀림 | joint palette 행렬 방향 또는 mesh/bind transform 상쇄 오류 가능성 | 스크린샷, 현재 bounds/keyframe 검증 부재 | 중간 |
| 대기 자세가 이상함 | walking clip 0초를 idle에 사용함 | `phase = 0f`, 별도 idle clip 없음 | 높음 |
| hitbox와 외형이 어긋남 | 서버 hitbox는 바닐라 0.6×1.8, 외형 mesh는 별도 pose/scale | F3+B 표시와 renderer 분리 | 높음 |
| 렉 가능성 | 매 프레임 CPU skinning + 약 131k emitted vertices | 실제 renderer loop | 높음 |
| 리소스팩 pack metadata 경고 | 별도 pack.mcmeta 형식 문제 | client latest.log | 높음, 플레이어 GLB 원인과는 별개 |

## 구현 검증 결과

승인 후 `GlbAnimatedPlayerMesh.skinnedBounds()`와 `GlbAssetValidator`를 추가해 실제 CPU skinning 결과를
검사했다. walking clip의 0% sample은 다음 bounds를 보였다.

```text
x: -41.59 .. 33.44
y: -1.35 .. 178.18
z: -44.80 .. 49.83
```

원본 POSITION bounds가 약 `0.87 × 1.80 × 0.62`인 것과 달리, 관절 palette 적용 뒤 약 179블록으로
폭발한다. 따라서 현 CPU 스키너로 이 GLB를 라이브 플레이어 외형으로 내보내는 것은 안전하지 않다.

## 리서치 결론

현재 구현은 “GLB가 로드되는 prototype” 단계다. 정상 인체 pose·정상 scale·성능·fallback을 보장하는
완성된 전용 플레이어 렌더러로 판단할 수 없다. 다음 구현은 scale 숫자를 다시 조정하는 것이 아니라,
bind pose와 skinned bounds를 프레임별로 검증하고, 정지용 asset을 분리하며, 검증 실패 시 바닐라
renderer로 자동 복귀하는 구조를 먼저 만들어야 한다. 이 구조는 구현됐으며, 클라이언트는 이제 비정상
bounds를 감지하면 full mesh를 자동 비활성화하고 animation-only/바닐라 fallback을 사용한다.

## Blender 재내보내기 결과

Blender 5.2 포터블로 원본을 가져온 뒤 Armature object의 `0.01` scale만 armature data에 적용하고,
원본 파일은 보존한 채 새 GLB와 `.blend`를 생성했다. 아래 검증본은 0%, 25%, 50%, 75%, 100% sample에서
`GlbAssetValidator`를 통과했다.

```text
assets-source/meshy/murim_player_base/rebaked/walking-rebaked.glb
assets-source/meshy/murim_player_base/rebaked/running-rebaked.glb
```

이 검증본을 `src/main/resources/assets/murimrpg/models_glb/player/`의 활성 파일로 교체했다. 기존 패키지
자산은 같은 폴더의 `source-original/`에 백업했다.
