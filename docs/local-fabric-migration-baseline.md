# Local Fabric Migration Baseline

기록일: 2026-09-01 (Asia/Seoul)

이 문서는 이번 후속 작업이 시작된 순간의 **로컬 working tree**를 기준으로 작성했다. 원격 저장소 상태는 조회하거나 가져오지 않았다.

## Git 기준

- Branch: `feature/advanced-realms`
- HEAD: `584896aac57b4b3d5e1ec274a15e6030318270e3`
- Staged files: 없음
- 모든 tracked 변경: unstaged
- Remote fetch/pull/merge/rebase: 수행하지 않음

## 시작 전 Modified

- `README.md`
- `build.gradle.kts`
- `settings.gradle.kts`
- `src/main/java/com/murimrpg/infrastructure/config/ContentConfigLoader.java`
- `src/main/java/com/murimrpg/infrastructure/config/CultivationConfigLoader.java`
- `src/main/java/com/murimrpg/infrastructure/config/GanghoCommandCatalogLoader.java`
- `src/main/java/com/murimrpg/service/CharacterCreationService.java`
- `src/main/java/com/murimrpg/service/CharacterSettings.java`
- `src/main/java/com/murimrpg/service/CombatService.java`
- `src/main/java/com/murimrpg/service/MeditationService.java`
- `src/main/java/com/murimrpg/service/MonsterService.java`

## 시작 전 Deleted (tracked, unstaged)

이 파일들은 이전 로컬 Fabric 전환에서 역할을 Fabric 어댑터로 옮긴 뒤 삭제 표시된 상태였다.

- Paper 엔트리포인트: `MurimRpgPlugin.java`
- Paper 명령: `GanghoCommand.java`, `HuashanCommandHandler.java`, `MurimCommand.java`
- Paper infrastructure: `infrastructure/paper/` 7개 파일
- Paper listener: `HuashanGameplayListener.java`, `PlayerProfileListener.java`
- Paper presentation/UI: `StatusMessageFactory.java`, `ui/` 6개 파일
- Paper metadata: `src/main/resources/plugin.yml`
- Paper 전용 테스트: `PromotionFeedbackSettingsTest.java`, `StatusMessageFactoryTest.java`

## 시작 전 Untracked

- Fabric 문서: `docs/fabric-migration-checklist.md`, `docs/paper-to-fabric-migration.md`
- Fabric Gradle 설정: `gradle.properties`
- Fabric 진입점/컨텍스트: `GanghoImunrok.java`, `GanghoContext.java`
- Fabric 명령/이벤트/스케줄러: `CommandRegistry.java`, `EventRegistry.java`, `ServerTickScheduler.java`
- Fabric 설정/데이터/운기조식: `ModResources.java`, `YamlDocument.java`, `LegacyPlayerDataMigrator.java`, `MeditationController.java`
- Fabric metadata: `fabric.mod.json`
- YAML 회귀 테스트: `CharacterSettingsTest.java`
- `server_build/`: 별도 로컬 Paper 프로젝트 복사본 전체

## 시작 시점 주요 시스템

### 루트 working tree

- Minecraft 26.2 / Java 25 / Fabric Loader 0.19.3 / Fabric API 0.158.0+26.2
- YAML 기반 32단계 경지(8대 경지)
- 캐릭터 운명 결정과 가문/재능/체질
- 16개 조직 카탈로그와 화산파 입문·육합공·승급·자하신공 흐름
- SQLite v6 플레이어/콘텐츠/여정 저장, 전용 DB executor
- Brigadier 명령, Fabric 접속·공격·피해·사망 이벤트
- tick scheduler, 연속 운기조식과 자하신공 파티클
- 기존 Paper 데이터 디렉터리의 비파괴 복사
- 채팅/명령 기반 UI

### `server_build/` 로컬 복사본

- Paper 26.2 / Java 25 / SQLite 기반의 별도 12단계 구현
- 루트에 없는 Inventory GUI: 프로필, 문파, 무공
- ActionBar HUD, Quest Sidebar, UI snapshot/view service와 `ui.yml`
- Paper 명령·Listener·PDC 수련 표적

`server_build/`의 도메인/DB는 루트보다 오래되었으므로 그대로 덮어쓰지 않는다. 단, 그 안에만 있는 UI 정보 모델과 사용자 흐름은 현재 로컬 기능으로 보고 루트 Fabric 구현에 맞게 보존 이전한다.

## 보호 원칙

- 이 기준선에 기록된 기존 변경과 untracked 파일은 HEAD 버전으로 복원하거나 삭제하지 않는다.
- `server_build/` 원본은 비교 기준으로 유지하며 직접 삭제하지 않는다.
- 원격 Git 동작과 자동 commit/push를 수행하지 않는다.
