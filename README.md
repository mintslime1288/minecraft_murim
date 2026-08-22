# MurimRPG

Paper 기반 Minecraft Java Edition 무협 RPG 플러그인의 유지보수 가능한 기반 프로젝트다. 현재 SQLite 플레이어 프로필과 서버 내부 테스트 명령을 제공하며, 실제 승급·무공·사냥 기능은 포함하지 않는다.

## 고정 환경

- Paper API: `26.2.build.112-stable` (`plugin.yml` API 버전 `26.2`)
- Java: 25
- Gradle Wrapper: 9.7.0
- 테스트: JUnit 5.14.3
- SQLite JDBC: 3.53.2.1

Paper 26.2부터 Java 25가 필요하다. 시스템의 기본 JDK를 사용하거나 Gradle이 찾을 수 있는 Java 25 toolchain을 준비한다.

## 빌드

Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

macOS/Linux:

```bash
./gradlew test
./gradlew build
```

성공한 플러그인 JAR은 `build/libs/MurimRPG-0.1.0.jar`에 생성된다.

## 서버에서 확인

1. Java 25로 구동되는 Paper 26.2 안정 서버를 준비한다.
2. `build/libs/MurimRPG-0.1.0.jar`을 서버의 `plugins/` 폴더에 복사한다.
3. 서버를 시작하고 활성화 로그를 확인한다.
4. `/murim status`와 `/murim profile`을 실행한다.
5. OP 또는 `murim.admin` 권한으로 테스트 명령을 실행한다.
   - `/murim admin setprogress <player> THIRD_RATE BEGINNER 100`
   - `/murim admin resetprogress <player>`
6. `/stop`으로 완전히 종료한 뒤 다시 시작하여 데이터가 유지되는지 확인한다. `/reload`는 사용하지 않는다.
7. 서버 종료 시 비활성화 로그를 확인한다.

프로필 DB는 서버의 `plugins/MurimRPG/murimrpg.db`에 생성된다. 관리자 진행도 명령은 개발 및 수동 테스트 전용이다.

상세 MVP 범위와 개발 규칙은 `docs/` 및 `AGENTS.md`를 참고한다.
