# MurimRPG Windows Launcher

이 폴더는 외부 프레임워크 없이 만든 Windows PowerShell WPF 런처 원본이다. `MurimRpgLauncher.ps1`과 `launcher-config.json`을 직접 수정할 수 있다.

`MurimRpgLauncher.exe`는 Windows 기본 IExpress로 패키징한다. 공식 Minecraft Launcher를 열고 Java Edition의 Fabric 프로필을 검사하며, Microsoft 계정 비밀번호·토큰을 수집하지 않는다.

실제 자동 다운로드는 `manifest.json`의 HTTPS `downloadUrl`이 릴리스 서버 주소로 교체된 뒤 활성화한다. 현재 placeholder URL은 다운로드하지 않는다.
