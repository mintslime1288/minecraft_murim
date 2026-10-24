# 플레이어 Blockbench 애니메이션 템플릿

## 지금 개발 게임에서 쓰는 기본 플레이어 편집본

`player_current_java_editable.bbmodel`은 현재 활성화된 Java 기본 플레이어
체형(넓은 팔)을 Blockbench에서 수정하기 위한 사본이다. 머리·몸통·좌우 팔·
좌우 다리와 현재 옵션에서 켜진 모자·재킷·소매·바지 겉레이어를 포함한다.
총 13개 큐브와 12개 뼈이며, 손 위치 검토용 `rightItem`·`leftItem` 뼈도 있다.
`current_player_uv.png`는 직접 그린 중립 편집 텍스처다.

집 PC 개발 계정 `Player716`의 기록된 UUID와 Minecraft 26.2의 기본 스킨
선택 규칙을 대조하면 넓은 팔 `Makena` 스킨이다. 이 컴퓨터에 설치된
Minecraft JAR에서 해당 PNG를 읽어 만든
`local/player_current_dev_skin.bbmodel`은 **로컬 전용**이다. 그 파일을
Blockbench에서 열면 현재 개발 게임의 기본 스킨 모습으로 직접 수정할 수
있다. Minecraft 원본 스킨 이미지를 포함하므로 GitHub에는 올리지 않는다.
사용자가 다른 계정이나 별도 스킨으로 게임에 접속하면 해당 계정의 64×64
스킨으로 교체해야 한다.

이 편집본은 게임에서 지금 사용하는 **바닐라 지오메트리의 사본**이다.
PAL과 Bendable Cuboids가 게임 실행 중 팔꿈치·무릎을 구부리는 변형까지
`.bbmodel` 하나에 저장된 것은 아니다. 파일을 수정해도 게임에 자동 반영되지
않으며, 수정한 `.bbmodel`을 전달받아 Java 렌더러/모션과 대조해 적용해야 한다.
게임 설정과 서버 데이터는 변경하지 않았다.

아래의 `player_animation_wide/slim.bbmodel`은 먼저 만든 일반 제작 템플릿으로
보존한다. 현재 게임 외형을 고치려면 위의 `player_current_dev_skin.bbmodel`을
사용한다.

사용자가 직접 플레이어 모션을 만들기 위한 편집 원본이다. Blockbench의
**File → New → Bedrock Player Model**에서 만든 기본 플레이어 모델을
`.bbmodel`로 저장했다. `player_animation_wide.bbmodel`은 4px 팔,
`player_animation_slim.bbmodel`은 3px 팔이다. 둘 다 64×64 스킨 UV,
32 모델 픽셀 높이, 기본 플레이어 머리·몸통·팔·다리 비율을 사용한다.
`rightItem`과 `leftItem`은 손 아이템 위치를 검토할 기준 뼈다.

Blockbench에서 해당 `.bbmodel`을 열고 상단 **애니메이션** 탭에서 왼쪽의
`+`로 클립을 추가한다. `root` 아래의 머리·몸통·좌우 팔·좌우 다리 뼈를
회전시키며 키프레임을 만든다. `root`의 이동으로 걷기 거리를 넣지 않는다.
게임의 실제 위치 이동은 Minecraft가 담당한다. 공격 모션에서는
`rightArm`을 주로 움직이고 검집을 든 `leftArm`이 검의 공격을 따라
휘둘리지 않는지 확인한다.

`neutral_player_uv.png`는 편집용으로 새로 그린 방향 구분 텍스처다.
완성된 게임 스킨이 아니며 두 `.bbmodel` 안에도 같은 텍스처가 포함된다.
원하면 Blockbench에서 본인 64×64 스킨으로 교체할 수 있다.

## 게임 적용과의 관계

현재 개발 게임은 Java Edition의 기본 플레이어 모델에 PAL 1.2.6과
Bendable Cuboids 모션을 적용한다. 이 Bedrock 형식 `.bbmodel`은 **제작
원본/시각 참조**이고, 저장하거나 Bedrock animation JSON으로 내보낸
것만으로 Java 모드에 자동 적용되지는 않는다. 완성한 `.bbmodel`을
전달하면 뼈 이름·축·회전 단위·시간을 현재 PAL 클립과 대조해 별도로
적용해야 한다. Blockbench의 기본 템플릿은 팔꿈치·무릎 연속 굽힘을
그대로 미리보기하지 못하므로, 그 구간은 게임의 Bendable Cuboids 확인이
필요하다. 기존 모션·게임 설정·서버 데이터는 이 템플릿으로 바뀌지 않는다.

Blockbench에서 와이드 파일을 다시 열어 중립 스킨과 애니메이션 탭을
확인했다. 슬림 파일은 같은 뼈 계층과 3px 팔 너비를 저장 구조로 검증했다.
