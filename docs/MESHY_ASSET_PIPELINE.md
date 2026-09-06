# Meshy 자산 연동

MurimRPG는 Meshy를 고품질 원본 3D 자산 제작 도구로 사용한다. Meshy 생성 결과는 Minecraft 바닐라 모델이나 리소스팩에 직접 넣지 않는다. 전용 Fabric 클라이언트의 정적 GLB 렌더러가 `훈련 허수아비`용으로 등록되어 있으며, 라이선스 확인을 마친 GLB만 클라이언트 자산으로 포함한다.

Text to 3D는 Meshy API v2의 두 단계 작업이다. 도구는 먼저 텍스처 없는 preview 메시를 생성한 뒤, 같은 메시를 refine하여 PBR 텍스처 GLB로 저장한다.

텍스트 생성이 원하지 않는 원형 표적·문양 같은 기하를 반복해서 만들면, 먼저 기준 이미지를 만들고 Image to 3D로 전환한다. 이 방식은 기준 이미지의 실루엣을 우선하므로 이번 통나무 수련대처럼 **원형 표적 없이 이어지는 몸통**이 필요한 자산에 적합하다.

## 한 번만 하는 API 키 설정

1. Meshy 계정의 API 설정에서 별도의 키를 만든다.
2. Windows **사용자 환경 변수**에 이름 `MESHY_API_KEY`로 저장한다.
3. Codex와 PowerShell을 모두 다시 연다.

API 키는 채팅, Git, JSON 요청 파일, `asset-metadata.json`에 절대 넣지 않는다.

## 통나무 수련대 첫 생성

아래 파일을 복사해 `tools/meshy/training-dummy-log-pillar.request.json`으로 이름을 바꾸고, 필요하면 프롬프트만 고친다.

`tools/meshy/training-dummy-log-pillar.request.example.json`

PowerShell에서 프로젝트 폴더를 연 뒤 실행한다.

```powershell
.\tools\meshy\Invoke-MeshyAsset.ps1 -RequestFile .\tools\meshy\training-dummy-log-pillar.request.json
```

성공하면 다음 경로에 GLB와 메타데이터가 생긴다.

```text
assets-source/meshy/training_dummy_log_pillar/
```

## 이미지 기준 통나무 수련대

현재 채택 후보는 아래 Image to 3D 작업이다. 앞·옆·뒤에 원형 표적이나 별도 원판이 없는 연속 통나무 몸통으로 생성됐다.

```text
assets-source/meshy/training_dummy_log_pillar_image_guided/
├─ reference-log-pillar.png
├─ model.glb
├─ preview.png
├─ preview-front.png / preview-right.png / preview-back.png / preview-left.png
└─ asset-metadata.json
```

로컬 기준 이미지를 사용할 때는 요청 JSON 최상단에 `image_file`을 지정한다. `Invoke-MeshyAsset.ps1`가 해당 PNG/JPEG를 메모리에서 data URI로 변환해 Meshy에 전송하므로, 이미지를 별도 공개 업로드할 필요가 없다.

```powershell
.\tools\meshy\Invoke-MeshyAsset.ps1 -RequestFile .\tools\meshy\training-dummy-log-pillar-image-guided.request.json
```

## 배포 전 필수 순서

1. Meshy task 화면에서 실제 사용 요금제와 라이선스를 확인한다.
2. `asset-metadata.json`의 `license_status`를 검토 완료 상태로 갱신한다.
3. Blender에서 불필요한 폴리곤·너무 큰 텍스처를 줄이고 스케일을 확인한다.
4. 전용 Fabric 클라이언트의 GLB 렌더러에 모델 ID를 등록한다.
5. 게임에서 거리·시야·품질 설정별 성능을 검증한다.

## 현재 적용: 통나무 수련대

원본 `training_dummy_log_pillar_image_guided`(8,830 삼각형)는 보존한다. 실제 클라이언트에는 같은 기준 이미지를 사용해 Smart Topology로 재생성한 `training_dummy_log_pillar_game_ready`(1,878 삼각형)를 `src/main/resources/assets/murimrpg/models_glb/training_dummy_log_pillar/model.glb`로 복사한다. 서버는 기존처럼 태그가 달린 보이지 않는 갑옷거치대로 판정·EXP·회수만 처리한다.

현재 빌드는 본인 로컬 테스트용이다. `asset-metadata.json`의 `license_status`가 아직 `REVIEW_REQUIRED`이므로, 공개 서버 클라이언트에 배포하기 전에는 사용 중인 Meshy 요금제·생성물 라이선스를 확인해 상태를 갱신해야 한다.

- 렌더 대상: 동기화된 머리 장비 CMD `32003`을 가진 훈련용 갑옷거치대만
- 실제 표시 크기: 높이 1.75블록, 폭 약 0.70블록 (원본 2.0×0.436블록 메시를 `1.60, 0.875, 1.60`으로 변환)
- 서버 충돌 크기: 보이지 않는 `murimrpg:training_dummy_collision` 블록이 약 0.70×1.75블록의 둥근 기둥을 5개 조각으로 근사한다. 선택선과 렌더 모델은 없고, 위치 보정 없이 바닐라 블록 충돌이 플레이어를 자연스럽게 멈춘다. 기존 태그 허수아비는 엔티티 로드 시 충돌 블록을 자동 복구한다.
- 일반 갑옷거치대: 바닐라 렌더러로 그대로 표시
- 바닐라 클라이언트: 기존 리소스팩 fallback을 계속 사용

GLB 2.0 중 `POSITION`, `NORMAL`, `TEXCOORD_0`, indexed triangles와 base-color 이미지만 읽는다. 현재 Meshy 게임용 GLB는 내장 JPEG UV 아틀라스와 Minecraft의 사각형 단위 엔티티 버퍼가 맞지 않아 표면이 찢어진 것처럼 보였다. 원본 GLB·미리보기·메타데이터는 보관하지만, 실제 게임에서는 사용하지 않는다. 대신 `TrainingLogPillarMesh`가 생성하는 **닫힌 24각 통나무(384개 옆면 사각형 + 48개 단면 사각형)** 에 `assets/murimrpg/textures/entity/training_dummy_log_continuous.png`의 연속 세로 나뭇결을 입힌다. 렌더러는 꼭짓점을 항상 4개씩 제출해 서로 다른 면이 잘못 연결되거나 구멍이 생기지 않게 한다. 원본 텍스처는 `assets-source/generated/training_dummy_log_pillar_continuous_texture/source.png`에 보관하며, 생성 도구·용도는 같은 폴더의 메타데이터에 기록한다. 불투명 통나무 재질은 성능을 위해 비투명 양면 엔티티 렌더 패스로 그린다. 전용 표시 전환은 바닐라 갑옷거치대 렌더 단계의 클라이언트 전용 mixin에서 동기화되는 머리 장비 CMD `32003`을 확인한 경우에만 수행한다. 서버 전용 태그는 클라이언트 표시 판별에 사용하지 않는다. 리깅·스킨·애니메이션·PBR 노멀맵은 이 정적 소품 렌더러 범위 밖이며, 고급 캐릭터 모델은 별도 렌더 파이프라인으로 확장한다.

무료 플랜 결과물은 일반적으로 CC BY 4.0 조건과 표기가 필요할 수 있고, 유료 플랜의 사용·소유 조건도 생성 시점의 Meshy 약관으로 다시 확인한다. 라이선스가 불명확한 자산은 서버 배포물에 넣지 않는다.
