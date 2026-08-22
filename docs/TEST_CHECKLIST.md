# 테스트 체크리스트

## 자동 테스트

- [ ] `./gradlew test`가 Java 25에서 성공한다.
- [ ] `./gradlew build`가 Java 25에서 성공한다.
- [ ] 상태 서비스가 메타데이터와 저장소 초기화 전/후 상태 전환을 반환한다.
- [ ] 신규 프로필이 삼류·입문, EXP 0, 빈 완료 조건, `QI_BAR` 해금으로 생성된다.
- [ ] 프로필 전체 필드를 저장하고 다시 조회할 수 있다.
- [ ] 저장소 인스턴스를 다시 만들어 서버 재시작을 모사해도 값이 유지된다.
- [ ] 잘못된 대경지·소경지 조합과 음수 EXP가 거부된다.
- [ ] 완료 조건 및 해금 콘텐츠 ID 집합이 유지된다.
- [ ] 같은 UUID의 신규 프로필 요청이 기존 행을 중복 생성하거나 덮어쓰지 않는다.
- [ ] 저장 실패 시 변경 전 캐시가 보존된다.
- [ ] `resetprogress` 도메인 동작이 필수 기본값으로 돌아간다.
- [ ] 빈 DB가 `schema_version` 1로 마이그레이션된다.

## 수동 서버 테스트

- [ ] Java 25로 Paper `26.2` 안정 서버를 실행한다.
- [ ] 빌드된 JAR을 서버의 `plugins/` 디렉터리에 복사한다.
- [ ] 시작 로그에 MurimRPG 활성화 메시지와 버전이 명확히 보인다.
- [ ] `/plugins`에서 MurimRPG가 활성 상태로 보인다.
- [ ] 플레이어와 콘솔에서 `/murim status`를 실행할 수 있다.
- [ ] 출력에 플러그인명, 플러그인 버전, `26.2.build.112-stable`, `configured (SQLite)`가 보인다.
- [ ] `/murim`처럼 잘못된 입력에 사용법이 Adventure 컴포넌트로 표시된다.
- [ ] 서버 종료 시 MurimRPG 비활성화 로그가 명확히 보인다.
- [ ] 최초 실행 시 `config.yml`, `skills.yml`, `realms.yml`이 플러그인 데이터 폴더에 생성된다.
- [ ] 최초 DB 초기화 후 `plugins/MurimRPG/murimrpg.db`가 생성된다.

## 마인크래프트 내부 프로필 테스트

`setprogress`와 `resetprogress`는 테스트 전용 관리자 명령이다. `murim.admin` 권한이 있는 계정으로 실행한다.

1. 서버에 접속한다.
2. `/murim status`
3. `/murim profile`
4. `/murim admin setprogress <내닉네임> THIRD_RATE BEGINNER 100`
5. `/murim profile`
6. `/stop`으로 서버를 완전히 종료한다. `/reload`는 사용하지 않는다.
7. 서버를 다시 시작하고 재접속한다.
8. `/murim profile`
9. 삼류·하수, EXP 100이 유지되는지 확인한다.
10. `/murim admin setprogress <내닉네임> THIRD_RATE EARLY 100`
11. 잘못된 조합 오류가 출력되는지 확인한다.
12. `/murim admin resetprogress <내닉네임>`
13. `/murim profile`
14. 삼류·입문, EXP 0, 해금 콘텐츠 1개인지 확인한다.
