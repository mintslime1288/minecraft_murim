# 경지 진행

## 구현된 32단계

모든 EXP와 내공 수치는 개발 테스트용이며 `realms.yml`에서만 관리한다. EXP는 누적값이고 승급 시 차감하지 않는다. 한 번의 `/murim cultivate`는 조건을 모두 충족해도 정확히 한 단계만 진행한다.

| Order | Stage ID | 표시명 | 누적 EXP | 최대 내공 | 회복량 | 진입 조건 | 해금 ID |
|---:|---|---|---:|---:|---:|---|---|
| 1 | `third_rate_initiate` | 삼류·입문 | 0 | 100 | 2 | 없음 | `QI_BAR` |
| 2 | `third_rate_beginner` | 삼류·하수 | 100 | 110 | 2 | 없음 | `CRIMSON_COMBO` |
| 3 | `third_rate_intermediate` | 삼류·중수 | 250 | 120 | 3 | 없음 | `CLOUD_STEP` |
| 4 | `third_rate_peak` | 삼류·평정 | 450 | 130 | 3 | 없음 | `SECOND_RATE_TRIAL_ACCESS` |
| 5 | `second_rate_early` | 이류·초기 | 700 | 150 | 4 | `training_trial` | `QI_REGEN_1` |
| 6 | `second_rate_middle` | 이류·중기 | 1,000 | 165 | 4 | 없음 | `FALLING_PETAL_COUNTER` |
| 7 | `second_rate_late` | 이류·후기 | 1,350 | 180 | 5 | 없음 | `AERIAL_STEP` |
| 8 | `second_rate_complete` | 이류·원만 | 1,750 | 200 | 5 | 없음 | `BLACK_PINE_CHIEF_ACCESS` |
| 9 | `first_rate_early` | 일류·초기 | 2,200 | 230 | 6 | `black_pine_chief` | `SWORD_AURA_VISUAL` |
| 10 | `first_rate_middle` | 일류·중기 | 2,700 | 260 | 7 | 없음 | `MAX_QI_1` |
| 11 | `first_rate_late` | 일류·후기 | 3,300 | 300 | 8 | 없음 | `MOVEMENT_UPGRADE_1` |
| 12 | `first_rate_peak` | 일류·절정 | 4,000 | 350 | 10 | 없음 | `MVP_COMPLETE_TITLE` |
| 13 | `jeoljeong_realm_early` | 절정경·초기 | 5,000 | 380 | 11 | `jeoljeong_breakthrough_trial` | 없음 |
| 14 | `jeoljeong_realm_middle` | 절정경·중기 | 6,500 | 410 | 12 | 없음 | 없음 |
| 15 | `jeoljeong_realm_late` | 절정경·후기 | 8,000 | 440 | 13 | 없음 | 없음 |
| 16 | `jeoljeong_realm_complete` | 절정경·원만 | 10,000 | 480 | 14 | 없음 | 없음 |
| 17 | `chojeoljeong_realm_early` | 초절정·초기 | 12,500 | 520 | 15 | `chojeoljeong_breakthrough_trial` | 없음 |
| 18 | `chojeoljeong_realm_middle` | 초절정·중기 | 15,500 | 560 | 16 | 없음 | 없음 |
| 19 | `chojeoljeong_realm_late` | 초절정·후기 | 19,000 | 600 | 17 | 없음 | 없음 |
| 20 | `chojeoljeong_realm_complete` | 초절정·원만 | 23,000 | 650 | 18 | 없음 | 없음 |
| 21 | `hwagyeong_realm_early` | 화경·초기 | 28,000 | 700 | 19 | `hwagyeong_breakthrough_trial` | 없음 |
| 22 | `hwagyeong_realm_middle` | 화경·중기 | 34,000 | 760 | 20 | 없음 | 없음 |
| 23 | `hwagyeong_realm_late` | 화경·후기 | 41,000 | 820 | 21 | 없음 | 없음 |
| 24 | `hwagyeong_realm_complete` | 화경·원만 | 50,000 | 900 | 22 | 없음 | 없음 |
| 25 | `hyeongyeong_realm_early` | 현경·초기 | 62,000 | 980 | 23 | `hyeongyeong_breakthrough_trial` | 없음 |
| 26 | `hyeongyeong_realm_middle` | 현경·중기 | 76,000 | 1,070 | 24 | 없음 | 없음 |
| 27 | `hyeongyeong_realm_late` | 현경·후기 | 93,000 | 1,170 | 25 | 없음 | 없음 |
| 28 | `hyeongyeong_realm_complete` | 현경·원만 | 115,000 | 1,280 | 26 | 없음 | 없음 |
| 29 | `saengsagyeong_realm_early` | 생사경·초기 | 145,000 | 1,400 | 27 | `saengsagyeong_breakthrough_trial` | 없음 |
| 30 | `saengsagyeong_realm_middle` | 생사경·중기 | 180,000 | 1,530 | 28 | 없음 | 없음 |
| 31 | `saengsagyeong_realm_late` | 생사경·후기 | 225,000 | 1,670 | 29 | 없음 | 없음 |
| 32 | `saengsagyeong_realm_complete` | 생사경·원만 | 280,000 | 1,820 | 30 | 없음 | 없음 |

기존 1~12단계의 ID와 순서는 유지된다. `일류·절정`은 `FIRST_RATE + PEAK`인 중간 단계이며 새 대경지 `절정경`과 다르다. `MVP_COMPLETE_TITLE`은 기존 데이터 호환을 위해 12단계 해금으로 유지하지만 이제 최종 경지 보상이 아니다. 새 20단계는 정의되지 않은 콘텐츠 ID를 만들지 않기 위해 해금 목록이 비어 있다.

내공 수치는 실제 내공 시스템이 아닌 설정 데이터다. 최대 내공은 100에서 1,820까지 단계별로 완만하게 증가하고, 회복량은 2에서 30까지 감소 없이 증가한다. 소비·회복·운기조식은 구현하지 않았다.

## 게임 내부 표시와 테스트 도구

- `/murim profile`은 현재 경지, 누적 EXP, 완료 조건, 해금 개수, 저장 상태를 표시한다.
- `/murim realm`은 다음 단계, 요구/부족 EXP, 조건, 다음 해금을 표시하며 최종 단계에서는 설정의 `생사경·원만`을 최고 경지로 표시한다.
- `/murim admin testprogress <player> prepare`는 설정의 최종 EXP와 모든 승급 조건을 동적으로 준비하되 자동 승급하지 않는다.
- `/murim realm`과 승급 요청의 액션바는 한 번만 표시된다. 승급 연출은 개인 Title·효과음·제한된 파티클·채팅뿐이다.

실제 돌파 시험, 퀘스트, 무공, 경공, 내공 기능, 몬스터, 장비와 고급 경지 해금 콘텐츠는 다음 작업 범위다.
