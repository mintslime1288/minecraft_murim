# Paper → Fabric Migration

## 기준 환경

- Minecraft: 26.2 (기존 서버 버전 유지)
- Java: 25
- Gradle Wrapper: 9.7.0
- Fabric Loom: 1.17.20 (`1.17-SNAPSHOT` 해석 결과)
- Fabric Loader: 0.19.3
- Fabric API: 0.158.0+26.2
- Mappings: Minecraft 26.2의 비난독화 Mojang 이름. 26.1 이후 `net.fabricmc.fabric-loom`을 사용하므로 별도 Yarn 의존성이 없다.
- 모드 환경: `server`. Vanilla 클라이언트 접속을 전제로 하며 클라이언트 모드는 요구하지 않는다.

기존은 Paper API `26.2.build.112-stable`, Java 25, SQLite JDBC 3.53.2.1이었다. `paper-api`, `plugin.yml`, Adventure 직접 의존성은 제거했다. 기존 YAML을 안전하게 읽기 위해 SnakeYAML 2.5를 추가했다.

## 기존 구현 조사

플랫폼 독립 계층에는 32개 세부 경지(8대 경지 × 단계), 캐릭터 운명 결정, 문파·계급 카탈로그, 화산파 입문/육합공/승급/자하신공 진행, 퀘스트, 내공 상태, SQLite v6 저장소와 비동기 캐시 서비스가 있었다. 실제 범용 전투 무공, 경공, 장비, 지역, 몬스터 사냥 콘텐츠는 구현 전 상태였다.

Paper 결합 지점은 이전 엔트리포인트, `CommandExecutor`/`TabCompleter` 명령 3개, Listener 2개, Inventory GUI/HUD/Sidebar 6개, Paper 엔티티·피드백·scheduler adapter 6개, Bukkit YAML loader 4곳이었다. PDC는 화산 수련 표적/시험관 소유자 표식에만 사용했고 플레이어 RPG 데이터는 이미 SQLite를 사용했다. 별도 리소스팩과 CustomModelData 자산은 저장소에 없었다.

## API 대응표

| Paper API | Fabric 구현 | 상태 |
|---|---|---|
| `JavaPlugin` | `ModInitializer` (`GanghoImunrok`) | MIGRATED |
| `plugin.yml` | `fabric.mod.json` | MIGRATED |
| `CommandExecutor`, `TabCompleter` | Brigadier + `CommandRegistrationCallback` | MIGRATED |
| Join/Quit listener | `ServerPlayConnectionEvents` | MIGRATED |
| Entity damage/death listener | `AttackEntityCallback`, `ServerLivingEntityEvents` | MIGRATED |
| `BukkitScheduler` | 공용 `ServerTickScheduler` + `ServerTickEvents` | MIGRATED |
| 엔티티 PDC | Vanilla persistent entity tags (`murimrpg.*`) | MIGRATED |
| Bukkit YAML | 안전 모드 SnakeYAML + `YamlDocument` | MIGRATED |
| Adventure `Component` | Mojang `net.minecraft.network.chat.Component` | MIGRATED |
| Inventory GUI | Vanilla `ChestMenu` + `SimpleMenuProvider` | MIGRATED |
| ActionBar HUD | `ServerPlayer.sendOverlayMessage` + 캐시 기반 tick | MIGRATED |
| Scoreboard sidebar | 플레이어별 Vanilla objective 패킷 | MIGRATED |

Mixin은 사용하지 않았다. 현재 필요한 접속, 공격, 피해, 사망, tick 훅을 Fabric API가 제공하기 때문이다.

## 시스템 상태

| 시스템 | 기존 | Fabric | 상태 |
|---|---:|---:|---|
| 경지 | YES | YES | TESTED |
| 캐릭터 생성 | YES | YES (명령 UI) | TESTED |
| 문파/계급 | YES | YES | TESTED |
| 화산파 입문/시험 | YES | YES | MIGRATED |
| 육합공/자하신공 진행 | YES | YES | TESTED (도메인) |
| 퀘스트 | YES | YES | TESTED (도메인) |
| 내공/운기조식 | YES | YES | MIGRATED |
| SQLite 플레이어 데이터 | YES | YES | TESTED |
| 범용 무공 사용/전투 | NO | NO | NOT STARTED |
| 경공/장비/지역 | NO | NO | NOT STARTED |
| Inventory GUI/HUD/Sidebar | YES | YES | TESTED (도메인/빌드) |
| 리소스팩 | NO | NO | NOT STARTED |

## 데이터 마이그레이션

기존 데이터는 PDC가 아니라 `plugins/MurimRPG/murimrpg.db`의 SQLite v6에 저장된다. 새 모드는 `config/murimrpg/murimrpg.db`를 사용한다. 첫 실행 때 새 파일이 없으면 `LegacyPlayerDataMigrator`가 기존 DB, WAL/SHM, YAML을 **복사**하며 원본은 수정하거나 삭제하지 않는다. 스키마가 같으므로 레코드 변환은 하지 않는다. 이전 서버는 완전히 종료한 뒤 첫 Fabric 실행을 해야 한다.

시험 엔티티는 `murimrpg.training_dummy`, `murimrpg.examiner`, `murimrpg.bandit_leader`, `murimrpg.owner.<uuid>` 태그를 사용한다. Vanilla 엔티티 태그는 월드 저장에 포함된다.

## 서버 전용 범위와 위험

- SERVER ONLY: 데이터, 경지, 문파, 화산파 진행, 퀘스트, 명령, Vanilla 컨테이너 UI, HUD/Sidebar, 시험 엔티티.
- OPTIONAL CLIENT: 현재 없음.
- CLIENT REQUIRED: 현재 없음.
- 강호록·문파·무공 화면은 Vanilla chest menu로 구현했다. 상단 슬롯 클릭과 shift-click을 차단하여 표시 아이템 이동/복제를 막는다.
- HUD는 캐시된 프로필/여정만 읽고, Sidebar는 서버의 공유 scoreboard를 교체하지 않도록 플레이어별 objective 패킷을 사용한다.
- 자동 소환은 Vanilla `summon` 명령을 서버 내부에서 호출한다. 첫 실제 서버 QA에서 26.2 SNBT와 엔티티 동작을 확인해야 한다.
- 운기조식은 웅크림·제자리 유지 조건, 준비 시간, 1초 단위 회복과 자하신공 분홍 파티클을 공용 tick scheduler 위에서 처리한다.

## 실행 확인

`gradlew.bat runServer`에서 Minecraft 26.2, Loader 0.19.3, Fabric API 0.158.0과 `murimrpg 0.2.0` 로딩, SQLite 초기화, 32개 경지·16개 조직·2개 무공 설정 로드를 확인했다. 개발 서버 EULA에는 대신 동의하지 않았으므로 모드는 정상 초기화된 뒤 Mojang의 EULA 안내와 함께 서버가 종료됐다. Windows 성능 카운터(OSHI) 경고도 출력됐지만 모드 초기화와 무관하다.

현재 루트 Gradle 빌드에는 Paper/Bukkit API가 남아 있지 않다. 로컬의 untracked `server_build/`는 이 전환 전 UI 기능을 확인하기 위한 별도 과거 Paper 작업본이며 루트 `settings.gradle.kts`에 포함되지 않는다. 원본 비교와 사용자 변경 보존을 위해 수정·삭제하지 않았다.
