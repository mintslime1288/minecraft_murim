# Meshy 플레이어 GLB 렌더러 구현 계획

작성일: 2026-09-05  
상태: **1차 안전 구현 완료 — 고밀도 renderer 검증 실패 시 바닐라 fallback 적용**

관련 리서치: [PLAYER_GLB_RENDERER_RESEARCH.md](../research/PLAYER_GLB_RENDERER_RESEARCH.md)

## 완료 목표

- MurimRPG 전용 Fabric 클라이언트의 **로컬 플레이어만** 고밀도 Meshy 캐릭터로 렌더링한다.
- 모델 발은 월드 바닥에 맞고, 외형 높이와 폭은 기본 플레이어와 자연스럽게 보인다.
- IDLE, WALK, RUN은 검증된 pose에서만 전환한다.
- 공격, 손 아이템 사용, 웅크리기, 수영, 탑승, 비행, 사망, 갑옷 상태에서는 안전하게 바닐라 모델로
  fallback 한다.
- 클라이언트 외형만 바꾸며 서버 이동·충돌·피해·EXP·화폐 판정은 변경하지 않는다.
- GLB가 비정상, 스켈레톤이 폭발, bounds가 비정상, 텍스처가 실패하면 크래시 대신 바닐라 fallback 한다.

## 반드시 먼저 결정할 설계 선택지

### 선택지 A — 현재 CPU glTF 스키너를 규격대로 보정

장점: 새 의존성이 없고 현재 자산을 그대로 쓴다.  
위험: glTF skinning/행렬/VertexConsumer 구현을 직접 유지해야 하며 32k triangles가 무겁다.

필수 조건:

1. Blender 또는 독립 GLB 뷰어에서 walking/running bind pose와 실제 애니메이션 pose를 확인한다.
2. glTF 행렬 convention과 JOML 행렬 convention을 단위 테스트로 증명한다.
3. 모든 keyframe의 skinned bounds가 정상 범위인지 검사한다.
4. 한 프레임에 쓸 수 있는 최대 정점/CPU 시간을 측정한다.

### 선택지 B — 검증된 glTF/animation runtime 도입

장점: 직접 작성한 skinning 행렬 오류 위험을 줄인다.  
위험: Fabric 26.2 호환성, 라이선스, 렌더 파이프라인 통합, 배포 크기를 검증해야 한다.

승인 전에는 새 라이브러리를 추가하지 않는다. 후보가 필요하면 계획 갱신본에 아래 표를 먼저 작성한다.

| 후보 | Minecraft 26.2/Fabric 호환 | 라이선스 | GPU skinning | 장점 | 위험 |
| --- | --- | --- | --- | --- | --- |

### 선택지 C — full mesh renderer를 보류하고 animation-only로 복귀

장점: 바닐라 스킨·갑옷·아이템 호환성이 가장 높다.  
위험: 팔꿈치·무릎 등 고밀도 모델의 장점을 얻지 못한다.

현재 screenshot 수준의 mesh 폭발이 A/B에서 안전하게 해결되지 않으면 이 선택지를 fallback으로 사용한다.

## 승인 후 구현 순서

### 1. GLB validation 계층 추가

예상 파일:

```text
src/main/java/com/murimrpg/client/render/GlbAssetValidator.java
src/main/java/com/murimrpg/client/render/GlbSkinningPose.java
src/main/java/com/murimrpg/client/render/GlbAnimatedPlayerMesh.java
src/test/java/com/murimrpg/client/render/GlbAssetValidatorTest.java
src/test/java/com/murimrpg/client/render/GlbAnimatedPlayerMeshTest.java
```

검증 항목:

- glTF version, mesh, skin, inverse bind matrices, texture 존재
- POSITION, NORMAL, UV, JOINTS_0, WEIGHTS_0, indices accessor 형식
- joint index가 `0 <= index < skin.joints.length`인지
- weight가 유한하고 합계가 1에 가까운지
- parent tree에 cycle이 없는지
- 노드/애니메이션 quaternion이 유한하고 정규화 가능한지
- bind pose와 animation sample 0%, 25%, 50%, 75%, 100%의 skinned bounds 검사
- 높이, 폭, 깊이, 팔/다리 최고점이 안전 범위를 벗어나면 asset invalid 처리

행렬 단위 테스트는 Blender glTF 2.0의 column-major MAT4 fixture를 사용해, matrix transpose 여부와
`inverse(global(mesh)) × global(joint) × inverseBind` 결과가 bind pose에서 원본 POSITION을 보존하는지
검증한다.

### 2. 좌표계 하나로 고정

GLB mesh local units → Minecraft entity world units 변환은 하나의 명시적 `ModelSpaceTransform`에서만
수행한다. PoseStack scale과 emitted vertex scale을 동시에 사용하지 않는다.

계획 승인 뒤 실측 결과에 따라 다음을 확정한다.

```text
GLB mesh local space
→ skinning 결과 space
→ entity local block space
→ LivingEntityRenderer pose
```

각 단계의 expected bounds를 로그와 테스트에 기록한다. `Armature scale=0.01`을 무작정 두 번 상쇄하거나
무시하지 않는다.

### 3. IDLE/WALK/RUN 상태 분리

- IDLE은 walking 첫 프레임을 재사용하지 않는다.
- `character.glb` bind pose가 자연스럽지 않다면, 별도의 idle asset을 준비할 때까지 고밀도 mesh를 IDLE에
  표시하지 않고 바닐라 모델을 사용한다.
- WALK와 RUN은 skeleton compatibility 검사 뒤에만 0.15~0.25초 blend를 사용한다.
- 공격·사용·점프·수영·웅크리기·탑승·활공·사망은 locomotion보다 높은 우선순위이며 바닐라 fallback 한다.
- Meshy 모델이 그 상태에서 표시되더라도 서버의 충돌 박스는 절대 바꾸지 않는다.

### 4. 성능 보호

- GLB·텍스처·애니메이션을 최초 resource load 때만 읽는다.
- 프레임마다 배열/벡터/행렬을 대량 할당하지 않는다.
- 로컬 플레이어와 3인칭에서만 고밀도 렌더링한다.
- 품질 설정에 `OFF`, `SAFE`, `HIGH`를 둔다.
- `SAFE`는 bounds 검증을 통과한 경우에만 표시한다.
- CPU skinning과 130k 이상 vertex emission이 목표 PC에서 지속적으로 프레임 저하를 일으키면, 자동으로
  바닐라 fallback 하거나 GPU skinning 설계로 전환한다.

### 5. Mixin과 fallback 안전성

- `LivingEntityRendererMixin` redirect가 실제 local avatar base model 한 번만 바꾸는지 검증한다.
- base mesh 교체 실패 시 원래 `submitModel`을 반드시 호출한다.
- F3+B hitbox 표시는 건드리지 않는다.
- 갑옷/손 아이템을 숨기거나 잘못된 위치에 띄우지 않는다. 정확한 external skeleton attachment가 구현되기
  전에는 해당 상태에서 바닐라 fallback 한다.

## 예상 변경 파일

| 파일 | 조치 | 이유 |
| --- | --- | --- |
| `GlbAnimatedPlayerMesh.java` | 수정 가능 | 규격 검증, pose palette, allocation/scale 처리 |
| `AnimatedPlayerGlbRenderer.java` | 수정 가능 | 상태 전환, fallback, 품질 제어 |
| `LivingEntityRendererMixin.java` | 수정 가능 | 안전한 base model 교체 범위 |
| `PlayerModelMixin.java` | 수정 가능 | full mesh와 animation-only 상호 배제 |
| `player-model.yml` | 수정 가능 | YAML 기반 quality/fallback 설정 |
| GLB renderer 테스트 | 추가/수정 | matrix, bounds, fallback 회귀 방지 |
| `docs/BASIC_COMBAT_ANIMATIONS.md` | 수정 | 실제 지원 상태 문서화 |
| 화폐·목검·허수아비 서버 코드 | 수정 금지 | 이번 범위 밖 |

## 테스트와 수동 검증

자동 테스트:

1. walking/running GLB의 mesh/skeleton/joint compatibility
2. 각 accessor 및 weight/index 검증
3. bind pose position 보존
4. 여러 animation phase의 finite/skinned bounds 검증
5. invalid GLB에서 renderer disabled + vanilla fallback
6. scale transform이 한 번만 적용됨
7. animation-only mixin과 full-mesh mixin이 같은 base mesh에 중복 적용되지 않음

수동 검증:

1. Java Edition Fabric 프로필 실행
2. F3+B를 끈 상태에서 F5 3인칭 전환
3. IDLE, WALK, RUN, 방향 전환, 점프 확인
4. 목검 장착/공격, 갑옷 장착, 수영, 웅크리기, 탑승 확인
5. 모델 발이 지면에 닿고 머리·팔·다리 길이가 정상인지 확인
6. `latest.log`에 asset validation, renderer 선택, fallback 이유가 명확한지 확인
7. 최소 5분 이동 후 프레임 저하와 메모리 증가를 확인

## 롤백

고밀도 renderer가 invalid로 판정되거나 수동 검증에서 문제가 확인되면 `player-model.yml`의
`enabled: false`로 설정하여 바닐라 모델로 즉시 복귀한다. 기존 mods JAR 백업을 사용할 필요 없이
클라이언트 재시작만 하면 된다.

# 사용자 검토 메모

<!-- 사용자가 여기에 수정 의견을 작성한다. -->

# 승인 상태

- [ ] 리서치 확인
- [ ] 계획 확인
- [x] 구현 승인 (`2026-09-05`)

## 구현 전 필요한 사용자 결정

1. Blender 설치 후 GLB를 직접 확인할지, 또는 Blender 없이 자동 validation부터 구현할지
2. 안전성을 위해 IDLE/갑옷/공격 상태를 바닐라 fallback 하는 것을 허용할지
3. CPU skinning 성능이 부족하면 GPU skinning 라이브러리 조사를 먼저 할지

사용자가 `승인: 구현 시작`이라고 입력하기 전에는 Java, YAML, GLB, JAR, 리소스팩, 서버 파일을 수정하지 않는다.
