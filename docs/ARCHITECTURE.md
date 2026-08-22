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
- `com.murimrpg.infrastructure.paper`: 비동기 완료 결과를 Paper 메인 스레드로 전달하는 실행기

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
- 종료 시 새 작업을 차단하고, 이미 대기 중인 작업 뒤에 전체 캐시 flush를 넣어 완료를 기다린다.

## SQLite 스키마

DB 파일은 `plugins/MurimRPG/murimrpg.db`이며 현재 스키마 버전은 1이다.

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

플러그인 생명주기, `/murim status`, 프로필 자동 생성·저장, `/murim profile`, 관리자 테스트용 진행도 설정·초기화만 포함한다. 실제 승급 판정과 콘텐츠 시스템은 포함하지 않는다.
