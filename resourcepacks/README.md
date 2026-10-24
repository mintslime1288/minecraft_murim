# 목검 리소스팩 (Minecraft Java 26.2)

`MurimRPG-Mokgeom-26.2.zip`은 친구가 Minecraft의 `resourcepacks` 폴더에 넣어 활성화할 수 있는 클라이언트 리소스팩이다. ZIP을 풀지 않는다. Fabric 모드 JAR에도 동일한 모델이 포함되지만, 모드를 설치하지 않은 클라이언트에는 이 별도 팩이 필요하다. 서버에 접속해 `/아이템관리 지급 @s 목검 1`로 받은 특수 목검만 3D 외형을 사용한다. 일반 나무칼은 바닐라 모양을 유지한다.

ZIP에는 `pack.mcmeta`와 아이템 선택 JSON, 3D 모델 JSON, 텍스처 PNG만 포함한다. 월드·계정·서버 데이터는 없다. 소스 변경 후 프로젝트 루트에서 `pwsh -File .\tools\build-mokgeom-resourcepack.ps1`을 실행해 ZIP을 다시 만든다. 모델 번호는 `custom_model_data.floats[0] = 32001`이다.

게임에서 **설정 → 리소스 팩**을 열어 `MurimRPG 목검 3D 모델`을 활성화하고, 목검을 다시 지급받아 인벤토리·1인칭·F5에서 확인한다. 기존 목검이 바닐라로 보이면 해당 아이템에 모델 번호가 있는지와 팩이 활성화됐는지 확인한다. 이 팩은 Minecraft Java 26.2의 리소스팩 형식 88 전용이다.
