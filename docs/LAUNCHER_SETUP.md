# MurimRPG Launcher 기반

현재 `launcher/MurimRpgLauncher.ps1`은 개발용 Java Edition 실행 도우미다. Microsoft 계정 비밀번호를 받거나 저장하지 않으며, 공식 Minecraft Launcher를 연 뒤 사용자가 Java Edition의 `fabric-loader-26.2` 프로필을 선택하도록 안내한다.

## 현재 동작

- Java Edition 게임 폴더와 Fabric 프로필 존재 여부 검사
- MurimRPG 클라이언트 모드 존재 여부 검사
- 공식 Minecraft Launcher 열기
- `launcher/manifest.json`으로 향후 모드팩 업데이트 목록·SHA-256·HTTPS URL을 관리할 준비

## 아직 배포하지 않는 기능

실제 HTTPS 파일 호스팅 주소, 릴리스 SHA-256, 코드서명 인증서가 정해지지 않아 자동 다운로드와 `.exe` 배포는 활성화하지 않았다. 임의 URL을 넣거나 외부 업로드하지 않는다.

추후 Tauri 또는 Electron으로 서명된 `MurimRPGLauncher.exe`를 만들 때는 공식 런처 인증을 사용하고, 런처는 파일 다운로드·무결성 확인·Fabric 프로필 실행만 담당한다.
