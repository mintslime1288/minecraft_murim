# 강호이문록 (MurimRPG)

Minecraft 26.2용 **서버 전용 Fabric 무협 RPG 모드**다. Vanilla 26.2 클라이언트 접속을 목표로 하며 클라이언트 모드는 요구하지 않는다.

현재 구현 범위는 SQLite 플레이어 데이터, YAML 기반 32단계 경지(삼류~생사경), 최초 캐릭터 생성, 문파·계급, 화산파 입문/육합공/승급/자하신공 진행, 퀘스트와 내공 상태, Vanilla 컨테이너 기반 강호록 UI, ActionBar HUD와 퀘스트 Sidebar다. 범용 전투 무공, 경공, 장비, 지역 콘텐츠는 아직 구현 전이다.

## 환경

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Fabric Loom 1.17
- Gradle Wrapper 9.7.0
- SQLite JDBC 3.53.2.1

## 빌드

Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

결과물은 `build/libs/MurimRPG-Fabric-0.2.0.jar`이다. Fabric Loader 서버의 `mods/`에 이 JAR과 호환 Fabric API를 설치한다. SQLite와 SnakeYAML은 모드 JAR에 포함된다.

개발 서버:

```powershell
.\gradlew.bat runServer
```

첫 실행 뒤 `run/eula.txt`를 확인하고 Minecraft EULA에 동의하는 경우에만 `eula=true`로 변경한 뒤 다시 실행한다.

## 데이터

설정과 DB는 `config/murimrpg/`에 생성된다. 모든 SQLite I/O는 전용 단일 스레드 executor에서 수행하고, Minecraft 객체 처리는 서버 스레드에서만 수행한다.

기존 서버의 `plugins/MurimRPG/`가 있으면 첫 Fabric 실행 시 새 위치에 없는 YAML 및 SQLite v6 파일을 복사한다. 원본은 삭제하지 않는다. 데이터 이동 전 기존 서버를 완전히 종료하고 백업한다.

## 명령

`/강호`는 메뉴를 열며 모든 기능은 다음 여섯 그룹으로 분류한다. `/강호 도움말`은 같은 구조를 인게임 채팅으로 출력한다.

- 정보: `/강호 정보 <내정보|상태|경지|돌파|캐릭터 보기|캐릭터 리롤|캐릭터 확정 <family-id>>`
- 문파: `/강호 문파 <목록|가입 화산파|승급>`
- 무공: `/강호 무공 <내공|운기조식>`
- 모험: `/강호 모험 <퀘스트|기연|도감|지도|지역|길찾기>`
- 경제: `/강호 경제 화폐`
- 관리(OP): `/강호 관리 <저장|경지테스트 <준비|초기화>|조건 <부여|제거> <조건>|경험치추가 <수량>>`

`/gangho`와 `/murim`은 같은 허브의 별칭이다. 반복 사용 기능만 `/내정보`, `/경지`, `/문파`, `/무공`, `/내공`, `/운기조식` 단축 명령으로 유지한다. `/메뉴`, `/menu`, `/화폐`, `/money`는 기존 사용자를 위한 호환 별칭이며 새 도움말에는 노출하지 않는다.

`/강호`와 `/내정보`는 강호록을, `/문파`와 `/무공`은 해당 Vanilla 컨테이너 UI를 연다. ActionBar HUD와 활성 퀘스트 Sidebar도 서버 패킷만 사용하므로 클라이언트 모드가 필요 없다. 모든 주기 갱신은 메모리 캐시만 읽으며 SQLite I/O를 발생시키지 않는다. 운기조식은 웅크린 채 `/운기조식`으로 시작하며 이동하거나 웅크리기를 풀면 취소된다.

## 문서

- [전환 분석 및 API 대응표](docs/paper-to-fabric-migration.md)
- [실행 검증 체크리스트](docs/fabric-migration-checklist.md)
- [로컬 작업 기준선](docs/local-fabric-migration-baseline.md)
- [아키텍처](docs/ARCHITECTURE.md)
