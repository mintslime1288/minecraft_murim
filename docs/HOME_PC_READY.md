# 집 PC 개발 시작 안내

작업 소스는 `MurimRPG-restored-20261017/minecraft_murim`이다. 상위 작업 폴더의 `.git`과 혼동하지 말고 이 프로젝트 폴더에서 Git 명령을 실행한다.

## 실행

상위 작업 폴더의 `Build-MurimRPG.cmd`를 더블클릭하면 소스 스냅샷 테스트와 JAR 빌드를 실행한다.
`Play-MurimRPG-Dev.cmd`는 Fabric Loom 개발용 클라이언트를 실행한다.
이는 정식 런처의 로그인 프로필과 별개이며, 인증이 필요한 원격 서버 접속용 런처가 아니다.

프로젝트에서 PowerShell로 직접 실행할 수도 있다.

```powershell
pwsh -NoProfile -File .\tools\dev-home.ps1 -Action Snapshot
pwsh -NoProfile -File .\tools\dev-home.ps1 -Action Build
pwsh -NoProfile -File .\tools\dev-home.ps1 -Action Test
pwsh -NoProfile -File .\tools\dev-home.ps1 -Action Prepare
pwsh -NoProfile -File .\tools\dev-home.ps1 -Action Client
pwsh -NoProfile -File .\tools\dev-home.ps1 -Action Info
```

스크립트가 사용자 폴더의 `MurimRPG-Tools/toolchains/active-jdk25.txt`에서 JDK를 선택한다.
Gradle 캐시는 `MurimRPG-Tools/gradle`에 둔다. 시스템 전역 Java/PATH는 바꾸지 않는다.
실행 버튼의 빌드 출력은 `C:/Users/namja/MurimRPG-Tools/workspace/source-snapshot-20260929/build`이다. OneDrive의 생성 파일 잠금 오류를 피하도록 `murimBuildDir` 및 `--project-cache-dir`을 전달한다. 일반 Gradle 직접 실행은 기본 `build/` 경로를 사용한다.
JDK는 Adoptium 공식 API에서 내려받고 제공된 SHA-256과 대조했다.
출처: https://adoptium.net/installation

## 개발용 게임과 결과물

- 개발 gameDir: 프로젝트의 `run/`
- 개발 로그: `run/logs/latest.log`
- 개발 모션 설정: `run/config/murimrpg/pal-natural-motion.yml`
- 설정 변경 적용: 개발 클라이언트를 닫고 다시 실행
- 빌드 JAR: 위 빌드 출력의 `libs/MurimRPG-Fabric-0.2.0.jar`
- 전체 테스트 보고서: 위 빌드 출력의 `reports/tests/test/index.html`
- 소스 스냅샷 테스트 보고서: 위 빌드 출력의 `reports/tests/sourceSnapshotTest/index.html`

`Snapshot`은 별도 Gradle task `sourceSnapshotBuild`를 실행한다. ZIP에서 제외된 GLB/텍스처가 필요한 `MeshyWalkAnimationTest`, `GlbAnimatedPlayerMeshTest`, `GlbStaticMeshTest`의 13개 테스트를 제외한다. 기존 `test` 및 `build` 동작과 테스트 소스는 그대로 유지한다. `Build`는 전체 검증이며 현재 누락된 외부 자산 때문에 실패한다. Snapshot 성공은 외부 자산 검증이나 전체 빌드 성공을 뜻하지 않는다.

개발 클라이언트는 Gradle runtimeClasspath의 Fabric API, PAL 및 Bendable Cuboids를 사용하므로 같은 모드를 `run/mods`에 중복 설치하지 않는다.
새 개발 월드를 사용한다. 옛 서버 DB/world 및 개인 계정 정보는 ZIP에 없으며 복원된 것으로 간주하지 않는다.
`runtime-config`의 설정은 옛 참고 사본이다. 소스 기본 설정을 덮어쓰지 않는다.

README의 초기 서버 전용 설명과 달리 현재 `fabric.mod.json`에는 클라이언트 엔트리포인트 및 PAL/Bendable Cuboids 권장 의존성이 있다. 현재 클라이언트 작업에서는 실제 코드와 이 안내를 기준으로 한다.

## 다음 제작 작업

## 시각 자산 제작 절대 규칙

사용자가 전달한 참고 이미지로 Minecraft 모델을 제작할 때는 이미지를 픽셀 단위로 관찰해 윤곽,
비율, 재질 구획 및 UV를 독자적인 큐브·텍스처로 재구성한다. 단순한 유사 형태나 임의의 저해상도
대체물을 만들지 않는다. 원본 이미지는 사용자가 제공한 범위 안에서만 참고하며, 외부 영상·모델의
텍스처, 메시, 키프레임을 복사하거나 가져오지 않는다. Blockbench 편집 원본, 생성 근거, 게임용
표시 변환을 함께 보존하고, 실제 인게임 손 위치·크기·방향 확인 전에는 적용 품질을 단정하지 않는다.

1. `AGENTS.md`와 `../MurimRPG-Claude-Collab/03-codex-review/`의 최신 검증 기록을 읽는다.
2. 아이디어 보드 확인 스크립트를 실행한다. 아이디어를 자동 적용하지 않는다.
3. 사용자에게 이번 기능 또는 F5 증상을 받고 한 단계의 목표를 정한다.
4. 시각/애니메이션 코드 작성은 기존 Claude 협업 규칙을 따른다. 이미 반영된 패치를 다시 적용하지 않는다.
5. 변경 후 Snapshot을 실행하고, 외부 자산이 복구되면 Build로 전체 검증한다. 시각 변경은 F5 및 로그를 별도로 확인한다.

F5 확인 항목: 평지 걷기의 접지와 발 미끄러짐, 걷기↔달리기, 후진·옆걸음, 점프·착지, 머리 시선, 공격.
테스트와 빌드 성공만으로 애니메이션 자연스러움이 확인된 것은 아니다.

기존 미커밋 변경 88개는 복원 원본이다. 초기 상태 및 해시 목록은 협업 검토 폴더에 보존되어 있다. 커밋·push는 별도 요청 전 수행하지 않는다.

## 로컬 서버 명령 사용 및 권한 수정 (2026-10-18)

`Play-MurimRPG-Dev.cmd`는 이제 `Player716` 이름으로 실행한다. 멀티플레이에서 `localhost:25565`에 접속한다. 현재 코드는 CLIENT 환경에서 서버 초기화를 건너뛰므로 싱글플레이에서는 강호 서버 명령을 사용할 수 없다.

- 목검 지급: `/아이템관리 지급 @s 목검 1`
- 도움말: `/강호 도움말`
- OP는 실제 접속한 개발 계정 UUID `57e72990-8f4d-370a-9882-2dee063faae3`에 연결했다.
- 서버 콘솔 UTF-8 입력을 수정했고, 재접속 후 플레이어를 실행 주체로 한 목검 지급 성공을 클라이언트 로그에서 확인했다.
- 통나무 허수아비 3D 자산 누락 경고는 별도 미해결 항목이다. 원본 ZIP에 없는 자산을 복원한 것으로 간주하지 않는다.
