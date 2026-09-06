# 런처 설정 수정

런처의 사용자 설정은 `%APPDATA%\MurimRPGLauncher\launcher-config.json`에 저장된다.

- `serverAddress`: 복사 버튼에 사용할 서버 주소
- `minecraftDirectory`: Java Edition `.minecraft` 폴더
- `fabricProfileId` / `fabricVersionId`: 공식 Launcher에 설치되어야 할 Fabric 프로필
- `defaultRamMiB`: 런처에 표시하고 저장할 권장 메모리 값
- `manifestFile`: EXE에 포함된 모드 검증 manifest

RAM은 현재 공식 Launcher를 통해 게임을 실행하므로 런처가 Java 실행 인수에 직접 강제하지 않는다. 공식 런처의 Java 프로필 설정과 일치시키는 용도다.
