# 플레이어 Blockbench 애니메이션 템플릿

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
