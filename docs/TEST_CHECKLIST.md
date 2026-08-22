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
- [ ] 로그아웃 저장 성공 시 캐시가 제거되고, 실패 시 복구를 위해 캐시가 보존된다.
- [ ] 수동 캐시 저장의 성공·실패 개수가 정확하고 성공 시 오류 상태가 복구된다.
- [ ] `resetprogress` 도메인 동작이 필수 기본값으로 돌아간다.
- [ ] 빈 DB가 `schema_version` 1로 마이그레이션된다.
- [ ] 12개 stage가 지정된 ID와 order 순서로 로드된다.
- [ ] EXP 부족, 일반 승급, 대경지 조건, 최고 단계가 검증된다.
- [ ] EXP 4,000이어도 한 요청에 한 단계만 승급한다.
- [ ] `training_trial`, `black_pine_chief` 부여와 제거가 저장된다.
- [ ] 동시 승급 요청 중 하나만 실행된다.
- [ ] 첫 저장이 이미 끝났어도 같은 서버 tick 토큰의 두 번째 승급은 실행되지 않는다.
- [ ] 빠른 재접속 시 이전 quit 저장이 새 세션의 캐시를 제거하지 않는다.
- [ ] 해금 ID가 중복 저장되지 않는다.
- [ ] 중복 stage ID/order, 잘못된 경지 조합과 next-stage가 거부된다.

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
6. `/stop`으로 서버를 완전히 종료한다.
7. 서버를 다시 시작하고 재접속한다.
8. `/murim profile`
9. 삼류·하수, EXP 100이 유지되는지 확인한다.
10. `/murim admin setprogress <내닉네임> THIRD_RATE EARLY 100`
11. 잘못된 조합 오류가 출력되는지 확인한다.
12. `/murim admin resetprogress <내닉네임>`
13. `/murim profile`
14. 삼류·입문, EXP 0, 해금 콘텐츠 1개인지 확인한다.

## 정상 진행 테스트

1. 서버를 완전히 종료한다.
2. 새 JAR을 `plugins` 폴더에 배치한다.
3. 서버를 시작한다.
4. 마인크래프트에 접속한다.
5. `/murim admin testprogress <내닉네임> clear`
6. `/murim profile`
7. `/murim realm`
8. `/murim cultivate`
9. EXP 부족 메시지를 확인한다.
10. `/murim admin addexp <내닉네임> 100`
11. `/murim cultivate`
12. 삼류·하수 승급을 확인한다.
13. `/murim realm`
14. `CRIMSON_COMBO` 해금 표시를 확인한다.

## 대경지 조건 실패 테스트

1. `/murim admin setprogress <내닉네임> THIRD_RATE PEAK 700`
2. `/murim admin requirement revoke <내닉네임> training_trial`
3. `/murim cultivate`
4. `training_trial` 부족 메시지를 확인한다.
5. `/murim admin requirement grant <내닉네임> training_trial`
6. `/murim cultivate`
7. 이류·초기 승급을 확인한다.

## 전체 12단계 테스트

1. `/murim admin testprogress <내닉네임> prepare`
2. `/murim realm`
3. `/murim cultivate`를 한 단계씩 반복한다.
4. 매 단계마다 `/murim realm`으로 경지와 해금을 확인한다.
5. 일류·절정에 도달한다.
6. 다시 `/murim cultivate`
7. 최고 단계 메시지를 확인한다.
8. `/stop`으로 서버를 완전히 종료한다.
9. 서버를 다시 시작한다.
10. `/murim profile`
11. `/murim realm`
12. 일류·절정과 EXP·조건·해금 데이터가 유지되는지 확인한다.

## 권한 테스트

1. 관리자 권한 없이 `/murim admin addexp <내닉네임> 100`을 실행한다.
2. 권한 오류를 확인한다.
3. OP 또는 `murim.admin` 권한을 부여한다.
4. 같은 명령을 다시 실행한다.
5. 정상 작동을 확인한다.

## 데이터 유지 최종 테스트

1. `/murim admin testprogress <내닉네임> clear`
2. `/murim admin addexp <내닉네임> 1350`
3. 필요한 만큼 `/murim cultivate`를 실행한다.
4. `/murim admin requirement grant <내닉네임> training_trial`
5. `/murim cultivate`
6. `/murim profile`
7. `/murim admin saveprofiles`
8. `/stop`으로 서버를 완전히 종료한다.
9. 서버 프로세스를 다시 시작한다.
10. `/murim profile`
11. 경지·EXP·조건·해금이 유지되는지 확인한다.

## 잘못된 입력 테스트

아래 명령을 각각 실행하고 오류가 표시되며, 실행 전후 `/murim admin debugprofile <내닉네임>` 결과가 동일한지 확인한다.

- `/murim admin addexp <내닉네임> -100`
- `/murim admin setexp <내닉네임> -1`
- `/murim admin setprogress <내닉네임> THIRD_RATE EARLY 100`
- `/murim admin requirement grant <내닉네임> unknown_requirement`

## 최고 경지 최종 테스트

1. `/murim admin testprogress <내닉네임> prepare`
2. 일류·절정까지 `/murim cultivate`를 한 단계씩 반복한다.
3. `/murim realm`
4. 다시 `/murim cultivate`
5. MVP 최고 경지와 다음 대경지 미구현 메시지를 확인한다.
6. `/murim admin debugprofile <내닉네임>`
7. `first_rate_peak` stage ID와 해금 정보를 확인한다.

## 재접속 테스트

1. `/murim profile`과 `/murim realm`으로 현재 경지를 확인한다.
2. 서버에서 나간다.
3. 다시 접속한다.
4. `/murim profile`
5. 값이 유지되는지 확인한다.
6. 서버 콘솔에 프로필 저장·조회 예외가 없는지 확인한다.

## 표시와 승급 연출 테스트

1. `/murim realm` 실행 직후 액션바가 한 번 표시되는지 확인한다.
2. 승급 성공 시 해당 플레이어에게만 Title, 짧은 효과음, 제한된 파티클, 채팅이 표시되는지 확인한다.
3. `config.yml`의 `promotion-feedback` 항목을 하나씩 비활성화하고 서버를 완전히 재시작한다.
4. 비활성화한 연출만 나오지 않고 나머지 표시와 승급 저장은 정상인지 확인한다.
5. 지속 액션바, 내공 회복 작업, 서버 전역 메시지나 전역 연출이 없는지 확인한다.
