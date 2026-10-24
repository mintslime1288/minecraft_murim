# 맨 위 은색 직검과 긴 흑칠 검집 v1

사용자가 제공한 1024×1024 참고 이미지에서 **맨 위의 단순한 은색 직검과 그
바로 아래 검집만** 선택했다. 아래쪽 두 자루의 화려한 검과 검집 장식은 포함하지
않았다. 참고 이미지는 원본 파일 그대로 읽고, 검·검집의 축을 픽셀 좌표로
곧게 펴서 1024×1024 텍스처로 만들었다.

## 편집 파일

- [검 Blockbench 모델](review/top_straight_sword.bbmodel)
- [검집 Blockbench 모델](review/extended_black_scabbard.bbmodel)
- [공유 텍스처](review/top_sword_atlas.png)
- [제작 기록](review/manifest.json)

Blender 생성기로 큐브를 작성한 뒤 두 `.bbmodel`을 **Blockbench 앱에서 직접
열어** 텍스처, 그룹, 전체 윤곽을 확인했다. 검 1,668개, 검집 1,134개 큐브다.
검집 길이는 모델 단위 25.2, 칼날 길이는 21.5로 **약 17.2% 길다**. 칼집은
검 전체 길이보다는 짧다.

## Minecraft Java용 내보내기

`game/top_straight_sword.json`, `game/extended_black_scabbard.json`은 Java
아이템 모델 초안이다. `game/top_sword_atlas.png`을 두 모델이 공유한다.
적용할 때 JSON은 `assets/murimrpg/models/item/`, PNG는
`assets/murimrpg/textures/item/`에 배치한다. 아이템 정의 및
CustomModelData 연결은 아직 하지 않았다. 현재 게임의 목검이나 일반
나무 검을 대체하지 않는다.

검의 보이지 않는 뒷면은 앞면을 반전해 구성했다. 인게임 손 위치·화면 크기·
검집 장착 위치는 별도 F5 확인이 필요하므로 표시 변환값은 초안이다.

## 양손 미리보기 (2026-10-24)

사용자가 Blockbench에서 크기와 손 위치를 편집해 저장한 파일은
`review/user-saved-20261024/`에 별도로 보존했다. 이 폴더의 검과 검집을
`make_preview_pack.py`의 입력으로 사용한다. 변환기는 큐브·UV와 `display`
설정 전체를 저장본에서 읽으며, 배율을 자체 값으로 덮어쓰지 않는다.
검과 검집의 텍스처도 각각의 저장본에서 가져온다.

`preview-pack-user-20261024/MurimRPG-Sword-Scabbard-Preview-26.2.zip`은
Minecraft 26.2 **두 아이템 시각 검토용** 리소스팩이다. 바닐라 검/막대기 전체를 바꾸지 않고 아래
`minecraft:item_model`을 지정한 두 아이템에만 모델을 입힌다. PAL의 기존
`pal_sword_swing` 공격 클립에서는 오른팔 키만 유지해, 공격 때문에 검집을 든
왼팔이 따라 움직이지 않게 한다. 일반 걷기·달리기 모션은 그대로 적용된다.

```mcfunction
/give @s minecraft:iron_sword[minecraft:item_model="murimrpg:top_straight_sword"] 1
/item replace entity @s weapon.offhand with minecraft:stick[minecraft:item_model="murimrpg:extended_black_scabbard"] 1
```

위 두 명령은 모델·크기·손 위치를 시험하는 임시 방법이다. 사용자가 요청한
**인벤토리 한 칸의 검+검집**은 아직 아니다. 최종 구조에서는 오른손 검 한 개만
실제 ItemStack/인벤토리 칸을 사용한다. 왼손 검집은 주손 아이템을 감지한
클라이언트 렌더러가 빈 왼손에만 보여주는 가상 표시다. 검집을 실제 막대기
아이템으로 지급하거나 서버의 보조손 칸을 채우지 않는다. 해당 렌더러는
Claude 패치 검토 전까지 추가하지 않는다.

두 아이템 시각 검토를 재현할 때는 개발 gameDir의 `run/resourcepacks/`에 ZIP을 넣고 리소스팩을 켠 뒤,
검을 오른손 핫바에서 선택한다. 왼손 검집 명령은 기존 왼손 아이템을 교체하므로
비워둔 상태에서 실행한다. 되돌릴 때는 이 팩을 끄고 다른 아이템을 들면 된다.
기존 목검 리소스팩은 별도로 유지한다.

2026-10-24 개발 클라이언트에서 두 손에 모델이 동시에 표시되는 것은
1인칭으로 확인했다. 한 칸 아이템 동작, 공격 중 왼손 고정과 3인칭 손 위치는
구현·F5 판정 대기 중이다.

## 재생성

Blender 5.2 이상에서 프로젝트 루트를 기준으로 실행한다.

```powershell
blender -b --python assets-source/blockbench/top-straight-sword-v1/build_model.py -- --reference 'C:\path\to\the-supplied-image.png'
```
