# MurimRPG

Paper 기반 Minecraft Java Edition 무협 RPG 플러그인이다. 현재 구현 범위는 SQLite 플레이어 프로필과 YAML 기반 삼류·이류·일류 12단계 경지 진행이다.

절정 대경지 이상, 실제 무공, 경공, 몬스터, 장비는 아직 구현하지 않았다. 설정에 기록된 해금 ID는 진행 확인용 데이터이며 해당 콘텐츠의 실제 동작을 제공하지 않는다.

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

## JAR 설치와 서버 재시작

1. Java 25로 구동되는 Paper 26.2 안정 서버를 준비한다.
2. `build/libs/MurimRPG-0.1.0.jar`을 서버의 `plugins/` 폴더에 복사한다.
3. 서버를 완전히 시작하고 활성화 로그를 확인한다.
4. `/murim status`, `/murim profile`, `/murim realm`, `/murim cultivate`를 실행한다.
5. OP 또는 `murim.admin` 권한으로 테스트 명령을 실행한다.
   - `/murim admin setprogress <player> THIRD_RATE BEGINNER 100`
   - `/murim admin resetprogress <player>`
   - `/murim admin addexp <player> <amount>`
   - `/murim admin setexp <player> <amount>`
   - `/murim admin requirement <grant|revoke> <player> <requirementId>`
   - `/murim admin stage <player>`
   - `/murim admin debugprofile <player>`
   - `/murim admin saveprofiles`
   - `/murim admin testprogress <player> <prepare|clear>`
6. `/stop`으로 서버를 완전히 종료하고 프로필 flush 완료 로그를 확인한 뒤 서버 프로세스를 다시 시작한다.
7. 재접속하여 `/murim profile`, `/murim realm`, `/murim admin debugprofile <player>`로 경지·EXP·조건·해금이 유지되는지 확인한다.
8. 서버 종료 시 비활성화 로그를 확인한다.

프로필 DB는 서버의 `plugins/MurimRPG/murimrpg.db`에 생성된다. 관리자 진행도 명령은 개발 및 수동 테스트 전용이다.

승급 성공 시 개인 Title, 효과음, 제한된 파티클, 채팅을 표시한다. 각 항목은 `config.yml`의 `promotion-feedback`에서 개별적으로 비활성화할 수 있다. 액션바는 `/murim realm` 또는 승급 요청 때 한 번만 표시하며 반복 작업이나 가짜 내공 회복을 실행하지 않는다.

상세 MVP 범위와 개발 규칙은 `docs/` 및 `AGENTS.md`를 참고한다.
