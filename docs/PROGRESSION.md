# 경지 진행

## 구현된 MVP 진행 순서

| Order | Stage ID | 표시명 | 누적 EXP | 진입 조건 | 도달 시 해금 |
|---:|---|---|---:|---|---|
| 1 | `third_rate_initiate` | 삼류·입문 | 0 | 없음 | `QI_BAR` |
| 2 | `third_rate_beginner` | 삼류·하수 | 100 | 없음 | `CRIMSON_COMBO` |
| 3 | `third_rate_intermediate` | 삼류·중수 | 250 | 없음 | `CLOUD_STEP` |
| 4 | `third_rate_peak` | 삼류·평정 | 450 | 없음 | `SECOND_RATE_TRIAL_ACCESS` |
| 5 | `second_rate_early` | 이류·초기 | 700 | `training_trial` | `QI_REGEN_1` |
| 6 | `second_rate_middle` | 이류·중기 | 1,000 | 없음 | `FALLING_PETAL_COUNTER` |
| 7 | `second_rate_late` | 이류·후기 | 1,350 | 없음 | `AERIAL_STEP` |
| 8 | `second_rate_complete` | 이류·원만 | 1,750 | 없음 | `BLACK_PINE_CHIEF_ACCESS` |
| 9 | `first_rate_early` | 일류·초기 | 2,200 | `black_pine_chief` | `SWORD_AURA_VISUAL` |
| 10 | `first_rate_middle` | 일류·중기 | 2,700 | 없음 | `MAX_QI_1` |
| 11 | `first_rate_late` | 일류·후기 | 3,300 | 없음 | `MOVEMENT_UPGRADE_1` |
| 12 | `first_rate_peak` | 일류·절정 | 4,000 | 없음 | `MVP_COMPLETE_TITLE` |

수치는 개발 테스트용이며 Java 코드를 수정하지 않고 `realms.yml`에서 변경한다. EXP는 누적값이며 승급할 때 차감하지 않는다. 한 번의 `/murim cultivate`는 조건을 모두 만족해도 한 단계만 진행한다.

## 게임 내부 표시

- `/murim profile`은 현재 경지, 누적 EXP, 완료 조건 ID, 해금 개수, SQLite/캐시 상태를 표시한다.
- `/murim realm`은 다음 경지의 누적 요구 EXP, 부족 EXP, 아직 부족한 조건, 다음 단계 해금 ID를 표시한다.
- `/murim realm`과 승급 요청은 현재 경지와 다음 요구 EXP를 액션바에 한 번 표시한다. 반복 스케줄러나 가짜 내공 회복은 없다.
- 승급 성공 연출은 해당 플레이어의 Title, 짧은 효과음, 제한된 파티클, 채팅으로만 구성한다. 각 채널은 `config.yml`에서 끌 수 있다.
- 일류·절정에서는 MVP 최고 경지와 다음 대경지 미구현 안내를 표시한다.

일류·절정은 `FIRST_RATE` 안의 마지막 소경지다. 별도의 대경지인 절정경이 아니다.

## 향후 범위

절정 대경지, 초절정, 화경, 현경, 생사경은 향후 대경지이며 이번 구현에 포함하지 않는다. 이 단계들의 stage ID, 밸런스, 승급 규칙 또는 콘텐츠를 현재 코드와 YAML에 미리 추가하지 않는다.

## 아직 기능이 없는 해금 ID

프로필에는 모든 해금 ID가 기록되지만 무공, 경공, 내공 HUD, 회복 효과, 검기 시각 효과, 이동 강화, 칭호의 실제 동작은 아직 구현하지 않는다.
