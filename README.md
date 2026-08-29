# MurimRPG

 Paper 기반 Minecraft Java Edition 무협 RPG 플러그인이다. 현재 구현 범위는 SQLite 플레이어 프로필과 YAML 기반 삼류부터 생사경까지의 32단계 경지 진행, 그리고 최초 입장 캐릭터 생성이다.

실제 내공, 돌파 시험, 무공, 경공, 몬스터, 장비는 아직 구현하지 않았다. 최초 캐릭터 생성의 가문/재능/체질 데이터와 숙련도·무공 modifier 확장 지점은 구현했지만, 아직 없는 무공·내공·운기조식 콘텐츠의 효과를 가짜로 실행하지는 않는다. 설정에 기록된 기존 해금 ID는 진행 확인용 데이터이며 해당 콘텐츠의 실제 동작을 제공하지 않는다. 새 고급 경지 20단계에는 해금 ID를 임의로 추가하지 않았다.

## 고정 환경

- Paper API: `26.2.build.112-stable` (`plugin.yml` API 버전 `26.2`)
- Java: 25
- Gradle Wrapper: 9.7.0
- 테스트: JUnit 5.14.3
- SQLite JDBC: 3.53.2.1

Paper 26.2부터 Java 25가 필요하다. 시스템의 기본 JDK를 사용하거나 Gradle이 찾을 수 있는 Java 25 toolchain을 준비한다.

## 빌드

## 강호 명령어

최초 입장 시에는 운명 결정 GUI가 열리며, 확정 뒤와 재접속 뒤에는 상시 ActionBar HUD가 다시 표시된다. `/내정보`는 강호록 Inventory GUI를 연다. 캐릭터 생성 미완료 상태에서 `/내정보`를 실행하면 운명 결정 GUI가 다시 열린다.

- 메인/캐릭터: `/강호`, `/강호 도움말`, `/내정보`, `/경지`
- 문파/무공: `/문파`, `/무공`, `/내공`, `/운기조식`
- 모험: `/퀘스트`, `/기연`, `/도감`, `/지도`, `/지역`, `/길찾기`
- 운영: `/강호관리` (`gangho.admin`), `/강호dev` (`gangho.dev`)

현재 문파·무공·퀘스트 영속 서비스는 아직 구현되지 않았다. 명령은 이를 명확히 안내하며, 화산파 입문은 즉시 가입 처리하지 않는다. 공개 문파·무공 메타데이터는 `gangho.yml`에서 관리한다.

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
2. 기존 서버라면 먼저 서버를 완전히 종료하고 `plugins/MurimRPG/murimrpg.db`와 `realms.yml`을 별도 위치에 복사해 백업한다.
3. 기존 12단계 `realms.yml`이 있으면 이름을 `realms.yml.pre-32`처럼 바꾼다. 새 JAR의 첫 시작에서 32단계 기본 파일이 생성된다. 기존 밸런스 사용자 변경은 백업과 새 파일을 비교해 수동 병합한다.
4. `build/libs/MurimRPG-0.1.0.jar`을 서버의 `plugins/` 폴더에 복사한다.
5. 서버를 완전히 시작하고 활성화 로그를 확인한다.
6. `/murim status`, `/murim profile`, `/murim realm`, `/murim cultivate`를 실행한다.
7. OP 또는 `murim.admin` 권한으로 테스트 명령을 실행한다.
   - `/murim admin setprogress <player> THIRD_RATE BEGINNER 100`
   - `/murim admin resetprogress <player>`
   - `/murim admin addexp <player> <amount>`
   - `/murim admin setexp <player> <amount>`
   - `/murim admin requirement <grant|revoke> <player> <requirementId>`
   - `/murim admin stage <player>`
   - `/murim admin debugprofile <player>`
   - `/murim admin saveprofiles`
   - `/murim admin testprogress <player> <prepare|clear>`
8. `/stop`으로 서버를 완전히 종료하고 프로필 flush 완료 로그를 확인한 뒤 서버 프로세스를 다시 시작한다.
9. 재접속하여 `/murim profile`, `/murim realm`, `/murim admin debugprofile <player>`로 경지·EXP·조건·해금이 유지되는지 확인한다.
10. 서버 종료 시 비활성화 로그를 확인한다.

### 최초 캐릭터 생성

최초 접속 시 인벤토리 UI에서 독립 20% 확률로 등장한 가문 후보(또는 항상 가능한 `길거리 출신`)를 선택하고 확정한다. 첫 드래프트와 매 리롤은 SQLite에 즉시 저장되므로 재접속이나 서버 종료로 리롤을 초기화할 수 없다. 리롤은 `character.yml`의 `reroll.max`(기본 5회)까지 가능하다. 가문/재능/체질 수치와 확률은 모두 `character.yml`에서 조정한다.

관리자(`murim.admin`) 테스트 명령:

```
/murim admin character stats <player>
/murim admin character reroll <player>
/murim admin character reset <player>
```

`reset`은 최초 생성만 초기화하고, 다음 UI에서 새 드래프트를 저장한다. 천무지체는 실제 0.01% weight를 사용하며, 보유 상한은 `floor(max(전체 프로필 수, minimum-population) / 100)`이다. 기본 `minimum-population: 100` 정책에서는 프로필 100명 미만에도 최대 한 명을 허용한다.

프로필 DB는 서버의 `plugins/MurimRPG/murimrpg.db`에 생성된다. 관리자 진행도 명령은 개발 및 수동 테스트 전용이다.

DB 컬럼 구조는 이번 확장에서 바뀌지 않는다. 다만 생사경 등 새 대경지 문자열 ID가 저장된 DB는 이전 JAR이 읽지 못할 수 있으므로, 이전 JAR로 되돌리기 전에는 반드시 확장 전 DB 백업도 함께 복원한다.

승급 성공 시 개인 Title, 효과음, 제한된 파티클, 채팅을 표시한다. 각 항목은 `config.yml`의 `promotion-feedback`에서 개별적으로 비활성화할 수 있다. 액션바는 `/murim realm` 또는 승급 요청 때 한 번만 표시하며 반복 작업이나 가짜 내공 회복을 실행하지 않는다.

상세 MVP 범위와 개발 규칙은 `docs/` 및 `AGENTS.md`를 참고한다.
