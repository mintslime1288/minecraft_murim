# MurimRPG 아키텍처

## 원칙

Paper/Bukkit은 외부 프레임워크 경계로 취급한다. 명령과 Listener는 입력을 애플리케이션 형태로 변환하고 결과를 전달하는 어댑터이며, 게임 규칙을 포함하지 않는다. 밸런스 값은 YAML에서 읽고, 플레이어 영속성은 향후 `PlayerRepository` 인터페이스 뒤에 둔다.

## 패키지 책임

- `com.murimrpg`: 플러그인 생명주기, 객체 조립, 명령 등록
- `com.murimrpg.command`: Paper 명령 입력 검증 및 서비스 호출
- `com.murimrpg.presentation`: 도메인/서비스 결과를 Adventure `Component`로 표현
- `com.murimrpg.service`: 유스케이스 조정과 애플리케이션 규칙
- `com.murimrpg.domain`: Paper에 의존하지 않는 값 객체와 게임 규칙
- `com.murimrpg.repository`: 모든 플레이어 데이터 접근을 정의하는 `PlayerRepository` 계약
- `com.murimrpg.listener`: 접속·종료 이벤트에서 UUID를 서비스 입력으로 변환
- `com.murimrpg.infrastructure.sqlite`: PreparedStatement 기반 SQLite 저장소와 스키마 마이그레이션
- `com.murimrpg.infrastructure.paper`: 비동기 완료 결과를 Paper 메인 스레드로 전달하는 실행기와 개인 승급 연출 어댑터
- `com.murimrpg.infrastructure.config`: `realms.yml`을 읽어 설정 정의로 변환하고 구조 오류를 보고

## 의존 방향

```text
Paper entry point -> command/listener -> service -> domain
                                  |          |
                                  v          v
                           presentation   repository interface
                                               ^
                                               |
                                      infrastructure adapter
```

안쪽 계층은 바깥쪽 계층을 알지 않는다. 특히 `domain`은 Bukkit/Paper 및 저장소 구현에 의존하지 않는다. SQLite I/O는 비동기로 실행하되, 완료 후 Bukkit/Paper 객체를 변경해야 하면 서버 스케줄러를 통해 메인 스레드로 복귀한다.

## 비동기와 캐시

- 단일 `MurimRPG-Database` 실행기가 초기화, 조회, 저장, 마이그레이션을 순서대로 실행한다.
- Command와 Listener는 SQL을 모르며 `PlayerProfileService`만 호출한다.
- DB 작업 안에서는 UUID와 순수 Java 도메인 객체만 사용한다.
- 비동기 완료 후 플레이어 메시지가 필요하면 `BukkitMainThreadExecutor`로 메인 스레드에 복귀한다.
- 변경된 프로필은 DB 저장이 성공한 뒤에만 캐시에 반영한다. 저장 실패 시 이전 캐시를 유지한다.
- `ProgressionService`는 `PlayerProfileService.mutate`를 통해 EXP 검사, 조건 검사, 한 단계 변경, 해금 합치기, DB 저장을 하나의 DB 실행기 작업으로 수행한다.
- 같은 UUID의 동시 승급 요청은 플레이어별 in-flight 집합으로 거부하며, 같은 서버 tick 토큰의 두 번째 요청도 첫 저장 완료 여부와 관계없이 거부한다. 로그아웃 시 토큰을 제거한다.
- 종료 시 새 작업을 차단하고, 이미 대기 중인 작업 뒤에 전체 캐시 flush를 넣어 완료를 기다린다.
- 로그아웃 저장이 성공한 프로필만 캐시에서 제거한다. 실패하면 복구 저장을 위해 캐시를 보존하고 상태를 `ERROR`로 표시한다. 접속 세션 토큰이 바뀐 빠른 재접속에서는 이전 quit 저장을 완료하되 새 세션 캐시는 제거하지 않는다.
- `/murim admin saveprofiles`는 메인 스레드를 막지 않고 DB 실행기에서 캐시 사본을 저장하며 성공/실패 개수를 반환한다. 성공한 재저장은 상태를 `LOADED`로 복구한다.
- 승급 Title·효과음·파티클과 액션바는 DB 작업이 끝난 뒤 메인 스레드에서만 Paper 객체에 적용한다.

## SQLite 스키마

DB 파일은 `plugins/MurimRPG/murimrpg.db`이며 현재 스키마 버전은 6이다. v2는 조직·기술, v3는 직접 소속 조직·계급, v4는 활동·내공·완료 활동, v5~v6은 캐릭터 생성 데이터를 추가한다.

```text
schema_version
  singleton INTEGER PRIMARY KEY
  version   INTEGER NOT NULL

player_profiles
  uuid                   TEXT PRIMARY KEY NOT NULL
  major_realm_id         TEXT NOT NULL
  phase_id               TEXT NOT NULL
  cultivation_exp        INTEGER NOT NULL CHECK >= 0
  completed_requirements TEXT NOT NULL
  unlocked_content       TEXT NOT NULL
  created_at             INTEGER NOT NULL
  updated_at             INTEGER NOT NULL
```

경지와 소경지는 Enum ordinal이 아닌 명시적인 문자열 ID로 저장한다. ID 집합은 정렬된 줄바꿈 구분 문자열로 저장하며 허용된 안정 ID 문자만 사용한다. DB에서 알 수 없는 ID나 잘못된 경지 조합을 읽으면 UUID와 저장된 ID를 포함한 `SEVERE` 로그를 남기고 기본값으로 덮어쓰지 않는다.

## 현재 구현 범위

플러그인 생명주기, 프로필 자동 생성·저장, 32단계 경지, 세력·조직·계급 설정, 화산파 입문·육합공·운기조식·두 승급 시험을 포함한다. Listener는 Paper 이벤트를 입력으로 변환하고 합격·계급 규칙은 `HuashanService`가 처리한다. 시험 엔티티는 PDC의 콘텐츠 ID와 소유자 UUID로 구분한다.

## 경지 설정

`realms.yml`의 각 항목은 stage ID, 대경지/소경지 ID, 표시명, 명시적 order, 누적 EXP, 최대 내공, 회복량, 진입 조건, 해금 ID, 다음 stage ID를 가진다. `CultivationConfigLoader`와 `CultivationProgression`은 시작 시 다음 오류를 검증한다.

- 중복 stage ID
- 중복 또는 비연속 order
- 잘못된 대경지·소경지 조합
- 중복 경지 조합
- 정렬 순서와 맞지 않는 next-stage 연결
- 잘못된 숫자·조건·콘텐츠 ID
- 이전 단계보다 감소하는 누적 EXP·최대 내공·회복량
- 0 이하의 최대 내공·회복량

설정이 잘못되면 부분적인 규칙으로 서버를 운영하지 않고 플러그인을 안전하게 비활성화한다.

설정 로더의 일반 `load`는 향후 확장을 위해 임의 개수의 유효한 단계를 지원한다. 플러그인 시작 경로는 현재 배포 기본값이 정확히 32단계인지 추가 검증하며 기본 `realms.yml` 회귀 테스트도 이를 보장한다. 최고 단계 표시, 테스트 준비 EXP와 조건, 관리자 EXP 자동완성은 모두 로드된 `CultivationProgression`에서 계산한다.

이번 확장은 기존 v6 컬럼을 그대로 사용하므로 스키마 버전을 올리지 않는다. 기존 경지와 캐릭터 행은 변경 없이 읽힌다.

## UI 경계

`ui` 패키지는 Bukkit 인벤토리·ActionBar·Scoreboard를 표현만 한다. `GanghoMenuGui`는 서비스가 제공하거나 이미 메모리에 적재된 `PlayerProfile`·`PlayerJourney` 스냅샷을 렌더링하며 SQLite를 직접 호출하지 않는다. 돌파와 심법 장착은 각각 `ProgressionService`와 `HuashanService`에 위임한다. GUI Holder는 화면 종류만 보관하고, 클릭·드래그는 항상 취소하여 아이템 이동이나 shift-click 복제를 막는다. HUD/Sidebar의 10 tick 갱신은 UUID 기반 캐시만 읽고 로그아웃과 플러그인 종료 시 캐시·작업을 정리한다.
