# 목검 모델 적용

2026-10-17 사용자 검토본 승인 후 기존 수련 목검에 3,536개 큐브 모델을 연결했다.
게임 규칙 및 애니메이션 Java 코드는 변경하지 않았다.

- 선택: minecraft:wooden_sword의 custom_model_data.floats[0] = 32001.
- Minecraft 26.2 item definition의 range_dispatch를 사용한다. 상한은 32001의 다음 float32인 32001.001953125이므로 다른 float32 값은 바닐라 모델로 돌아간다.
- 일반 나무 검/다른 모델 번호는 기존 minecraft:item/wooden_sword를 사용한다.
- 모델: assets/murimrpg/models/item/mokgeom_reference_v1.json
- 텍스처: assets/murimrpg/textures/item/mokgeom_reference.png
- 편집 원본: assets-source/blockbench/mokgeom-v1/review/mokgeom_reference_v1.bbmodel
- 원본 PNG를 그대로 UV 텍스처로 사용했으며 보이지 않는 뒷면은 앞면 재질로 구성했다.

개발 실행은 상위 Play-MurimRPG-Dev.cmd. OP/치트가 있는 테스트 월드에서
`/아이템관리 지급 @s 목검 1`로 수련용 목검을 얻는다.
`/give @s minecraft:wooden_sword`로 일반 나무 검을 받아 외형이 유지되는지도 비교한다.
F5/1인칭/인벤토리에서 손 위치, 크기, 휘두르기, 왼손을 확인한다.
많은 개수를 동시에 렌더링하는 상황의 프레임 성능은 별도 확인이 필요하다.

사용자가 오프라인 미리보기를 승인했으며, Blockbench 자동 제어는 승인 거부로 실제 열기 검증을 하지 못했다.
리소스 로딩 확인과 실제 손에 든 모습 확인은 구분해서 보고한다.
별도 정식 gameDir가 확인되지 않아 개발 환경에 적용하며 임의의 외부 mods 폴더에 설치하지 않는다.

이전 JAR은 ../MurimRPG-Claude-Collab/03-codex-review/mokgeom-before-install-20261017에 보관했다.

## 손잡이 기준점 수정 (2026-10-18)

사용자가 손잡이 끝을 잡는 것으로 보인다고 보고하여 held display 4개만 수정했다. Minecraft 26.2 로컬 client JAR의 item/handheld.json과 wooden_sword.json을 비교했다. 기본 검 손잡이의 추정 기준점 (3.5,3.5,8)을 회전/배율/이동 변환한 좌표에 목검 손잡이 윗부분 (7.9,-2.5,8)을 맞췄다. 왼손은 대칭 기준점을 사용한다. 기본 검의 대각선 축과 목검 세로축 차이 45도를 반영했다. 실제 손/텍스처 접촉점은 F5 확인 전의 보정 가설이다.

설정 원본은 assets-source/blockbench/mokgeom-v1/grip-display.yaml(JSON 호환 YAML), 생성기는 이 설정을 읽는다. 기본 검의 표시 설정 전체를 복사하지 않고 기존 목검 크기는 유지했다. 큐브/UV/텍스처/GUI 표시/플레이어 애니메이션에는 변화가 없다. 변경 전 모델은 협업 폴더 mokgeom-grip-before-20261018에 백업했다.

개발 런타임 build/resources/main의 모델이 소스와 같은지 확인했다. 게임에서 F3+T를 눌러 리소스를 다시 불러오면 적용된다. 서버 재시작은 필요 없다. 현재 서버 JAR은 이전 리소스를 보존하며 이번 배포 JAR은 별도 목검_손위치수정_20261018 폴더에 저장했다.

검증: sourceSnapshotBuild 성공, sourceSnapshotTest 336개 통과. 전체 test build는 기존 외부 자산 누락 13개 실패(349개 중). 실제 인게임 손 위치는 아직 미확인. F5 정면/측면, 1인칭, F키 왼손 전환으로 손이 손잡이를 감싸고 칼막이를 뚫지 않는지 확인해야 한다. 추가 조정은 실제 스크린샷에 근거한다.
