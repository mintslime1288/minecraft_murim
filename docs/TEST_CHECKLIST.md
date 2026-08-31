# 테스트 체크리스트

## 자동 테스트

- [ ] Java 25에서 `./gradlew test`와 `./gradlew build`가 성공한다.

## 최초 캐릭터 생성과 HUD UI

1. 새 프로필로 접속해 `강호이문록 | 운명 결정` GUI가 열리는지 확인한다.
2. 가문을 선택하고 `확정`을 누른다.
3. GUI가 닫힌 뒤 0.5초 안에 경지·가문·힘·신법 ActionBar HUD가 표시되는지 확인한다.
4. 문파에 가입한 뒤 `/내정보`를 실행해 `江湖錄 · 인물정보` GUI에 가문, 경지, 힘, 신법, 재능, 체질과 실제 문파·계급·기여도가 표시되는지 확인한다.
5. 로그아웃 후 재접속해 운명 결정 GUI는 다시 열리지 않고 HUD가 다시 표시되는지 확인한다.
6. 진행 중인 화산파 퀘스트에서 오른쪽 Sidebar에 퀘스트명과 타격·운기조식 진행도가 표시되고, 진행 종료 시 이전 Sidebar가 복원되는지 확인한다.
7. `/문파 가입 화산파` 또는 `/문파 승급` 후 즉시 안내 메시지가 표시되고 3초 뒤 필요한 수련 표적·시험관·산적 대장이 자동 소환되는지 확인한다.
8. OP로 `/강호dev reset`을 실행한 뒤 재접속 또는 `/내정보`를 실행해 운명 결정 GUI가 다시 열리고 경지·문파·퀘스트·무공 진행도 초기화되는지 확인한다.
- [ ] 기본 `realms.yml`이 정확히 32단계이며 order 1~32와 모든 next 연결이 일치한다.
- [ ] 기존 12단계 ID/순서와 일류·절정 단계가 유지되고 새 20단계 ID/순서가 일치한다.
- [ ] 최종 단계가 `saengsagyeong_realm_complete`, 생사경·원만, EXP 280000이다.
- [ ] 모든 EXP·최대 내공·회복량이 유효하고 이전 단계보다 감소하지 않는다.
- [ ] 중복 stage ID/order/경지 조합, 비연속 order, 잘못된 next, 경지 조합, 감소 EXP와 0 이하 내공이 거부된다.
- [ ] EXP가 충분해도 한 요청에는 한 단계만 승급하며 동시/같은 tick 중복 승급이 거부된다.
- [ ] 일류→절정경과 이후 네 경계에서 해당 조건이 없으면 실패하고 부여 후 성공한다.
- [ ] 생사경·원만 이후 승급이 거부되고 최고 단계 메시지가 설정 표시명을 사용한다.
- [ ] `testprogress prepare`가 EXP 280000과 일곱 조건을 준비하고 `clear`가 신규 기본값으로 복원한다.
- [ ] 기존 일류 프로필의 EXP·조건·해금이 그대로 로드된다.
- [ ] 생사경 프로필이 저장·재조회되고 저장소 재생성 후에도 유지된다.
- [ ] 알 수 없거나 잘못 조합된 저장 ID가 기본값으로 덮어써지지 않고 오류가 된다.
- [ ] 기존 저장 실패 캐시 보존, 빠른 재접속, 로그아웃 정리, 종료 flush 테스트가 통과한다.

## 배포 전 준비

서버 폴더에서 아래 절차를 수행한다.

1. `/stop`으로 서버를 완전히 종료한다.
2. `plugins/MurimRPG/murimrpg.db`와 `realms.yml`을 날짜가 붙은 별도 백업 파일로 복사한다.
3. 기존 12단계 `realms.yml`은 `realms.yml.pre-32`로 이름을 바꾼 뒤 새 `build/libs/MurimRPG-0.1.0.jar`을 설치한다. 첫 시작에서 32단계 기본 설정이 생성되며 기존 사용자 밸런스는 백업과 비교해 수동 병합한다.
4. Java 25로 Paper 26.2 서버를 시작한다.
5. 활성화 로그, `/plugins`, `/murim status`에서 플러그인과 SQLite 상태가 정상인지 확인한다.

새 대경지 데이터가 저장된 DB는 이전 JAR이 읽지 못할 수 있다. 롤백할 때는 서버를 완전히 종료하고 이전 JAR과 확장 전 DB 백업을 함께 복원한다.

## 32단계 인게임 최종 절차

아래 `<player>`는 접속 중인 테스트 플레이어 이름으로 바꾼다. 모든 관리자 명령은 OP 또는 `murim.admin` 권한이 필요하다.

1. 기존 DB 백업이 존재하는지 확인한다.
2. 서버가 완전히 종료된 상태에서 기존 12단계 `realms.yml`을 백업·이름 변경하고 새 JAR을 설치한다.
3. 서버를 시작한다.
4. 기존 플레이어로 접속한다.
5. `/murim profile`을 실행해 기존 경지·EXP·조건·해금이 정상 로드되는지 확인한다.
6. `/murim admin testprogress <player> clear` — 삼류·입문, EXP 0, 조건 없음, `QI_BAR`만 남아야 한다.
7. `/murim profile` — 기본 프로필과 저장 상태를 확인한다.
8. `/murim realm` — 다음 단계 삼류·하수와 요구 EXP 100을 확인한다.
9. `/murim admin testprogress <player> prepare` — 삼류·입문 상태로 EXP 280000과 일곱 조건이 준비되어야 한다.
10. `/murim cultivate`를 한 번씩 실행한다. 한 명령에 정확히 한 단계만 올라야 한다.
11. 각 승급 후 `/murim realm`으로 현재 경지와 다음 경지를 확인한다.
12. 일류·절정에서 `/murim admin requirement revoke <player> jeoljeong_breakthrough_trial`, `/murim cultivate`를 실행해 해당 조건 부족으로 실패하는지 확인한다.
13. `/murim admin requirement grant <player> jeoljeong_breakthrough_trial`, `/murim cultivate` — 절정경·초기로 승급해야 한다.
14. 아래 네 경계도 같은 방식으로 조건을 제거하면 실패하고 다시 부여하면 성공하는지 확인한다.
    - 절정경·원만: `chojeoljeong_breakthrough_trial` → 초절정·초기
    - 초절정·원만: `hwagyeong_breakthrough_trial` → 화경·초기
    - 화경·원만: `hyeongyeong_breakthrough_trial` → 현경·초기
    - 현경·원만: `saengsagyeong_breakthrough_trial` → 생사경·초기
15. `/murim cultivate`를 단계별로 반복해 생사경·원만에 도달한다.
16. 다시 `/murim cultivate` — `현재 최고 경지인 [생사경·원만]입니다.`가 표시되어야 한다.
17. `/murim admin debugprofile <player>`을 실행한다.
18. stage ID가 `saengsagyeong_realm_complete`, EXP가 280000, 일곱 조건과 기존 해금이 유지되는지 확인한다.
19. 서버에서 나갔다가 다시 접속한다.
20. `/murim profile`과 `/murim realm`으로 생사경·원만 데이터 유지를 확인한다.
21. `/stop`으로 서버를 완전히 종료하고 저장 완료/비활성화 로그를 확인한다.
22. 서버 프로세스를 다시 시작하고 같은 플레이어로 접속한다.
23. `/murim profile`, `/murim realm`, `/murim admin debugprofile <player>`로 생사경·원만과 EXP·조건·해금 유지를 확인한다.
24. 서버 로그에 설정, 저장, 조회 오류가 없는지 확인한다.

## 경계별 정확한 명령

각 경계에 도달한 뒤 아래 세 명령을 순서대로 실행한다. 첫 승급은 조건 부족, 두 번째 승급은 성공이어야 한다.

```text
/murim admin requirement revoke <player> jeoljeong_breakthrough_trial
/murim cultivate
/murim admin requirement grant <player> jeoljeong_breakthrough_trial
/murim cultivate

/murim admin requirement revoke <player> chojeoljeong_breakthrough_trial
/murim cultivate
/murim admin requirement grant <player> chojeoljeong_breakthrough_trial
/murim cultivate

/murim admin requirement revoke <player> hwagyeong_breakthrough_trial
/murim cultivate
/murim admin requirement grant <player> hwagyeong_breakthrough_trial
/murim cultivate

/murim admin requirement revoke <player> hyeongyeong_breakthrough_trial
/murim cultivate
/murim admin requirement grant <player> hyeongyeong_breakthrough_trial
/murim cultivate

/murim admin requirement revoke <player> saengsagyeong_breakthrough_trial
/murim cultivate
/murim admin requirement grant <player> saengsagyeong_breakthrough_trial
/murim cultivate
```

## 기존 기능 회귀

- [ ] `/murim status`에 플러그인 버전, Paper API, `configured (SQLite)`가 표시된다.
- [ ] `/murim profile`과 `/murim realm` 표시 및 한 번짜리 액션바가 정상이다.
- [ ] 승급 성공 시 설정에서 활성화한 개인 Title·효과음·제한된 파티클·채팅만 표시된다.
- [ ] 권한 없는 사용자의 `/murim admin ...` 실행이 거부된다.
- [ ] 음수 EXP, 잘못된 경지 조합, 알 수 없는 requirement ID가 데이터를 변경하지 않는다.
- [ ] `/murim admin saveprofiles`가 메인 스레드를 오래 막지 않고 성공·실패 개수를 표시한다.
- [ ] 서버 종료 후 캐시 저장 완료와 명확한 비활성화 로그가 보인다.

## 통합 UI 수동 점검

1. 캐릭터 생성 완료 후 `/강호`, `/ui`, `/메뉴`가 모두 `江湖錄 · 강호 메뉴`를 여는지 확인한다.
2. 인물 정보·경지·무공·문파·임무 버튼을 각각 눌러 실제 저장된 가문, 경지, 무공, 문파, 진행 중 활동만 표시되는지 확인한다.
3. 경지 화면에서 조건이 부족한 돌파는 거부되고, 조건을 충족한 돌파는 정확히 한 단계만 진행되는지 확인한다.
4. 습득한 육합공을 클릭하면 활성 심법이 되고, 미습득 자하신공 클릭은 거부되는지 확인한다.
5. 모든 UI에서 shift-click, 숫자키 교환, 드래그로 메뉴 아이템을 옮길 수 없는지 확인한다.
6. 진행 중 화산파 퀘스트에서 메뉴 임무 화면과 Sidebar 진행도가 같은 타격·운기조식 수치를 보여 주는지 확인한다.
7. 로그아웃·재접속 후 HUD가 `내공/최대 내공 | 활성 심법 | 준비` 형식으로 다시 나타나고, 메뉴 데이터가 정상 표시되는지 확인한다.
