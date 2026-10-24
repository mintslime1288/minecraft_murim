# 한 칸 검·가상 검집 (2026-10-25)

Claude의 `murim-single-slot-sword-scabbard-v1.patch`를 현재 Minecraft 26.2
클라이언트 소스에 적용했다. 검집은 실제 보조손 슬롯이나 서버 아이템이 아닌
클라이언트 표시다. 기존 플레이어 모델, 목검, 월드와 서버 데이터는 바꾸지 않았다.

## 개발 클라이언트에서 사용

1. 검·검집 모델이 들어 있는
   `assets-source/blockbench/top-straight-sword-v1/preview-pack-user-20261024/MurimRPG-Sword-Scabbard-Preview-26.2.zip`
   을 `run/resourcepacks/`에 두고 게임 설정에서 활성화한다.
2. `run/config/murimrpg/sword-scabbard.yml`의 `enabled: true`를 설정한다.
   저장소의 기본값은 `false`이며 개발 컴퓨터의 설정 파일은 Git에서 제외된다.
3. 클라이언트를 다시 시작하거나 `/murimwalk reload`를 실행한다.
4. 검 한 개만 지급한다.

   ```mcfunction
   /give @s minecraft:iron_sword[minecraft:item_model="murimrpg:top_straight_sword"] 1
   ```

보조손의 실제 아이템을 비워 둔다. 검집 지급 명령이나 보조손 막대기는 쓰지 않는다.
기능을 끄려면 같은 설정에서 `enabled: false`로 바꾸고 재시작하거나
`/murimwalk reload`를 실행한다. 첫 실행에 구성 요소 등록이 끝나기 전에
`ItemStack`을 만들면 게임이 종료되어, 렌더 전용 객체를 첫 렌더 시점에
만들도록 Claude 패치의 초기화 시점만 보정했다.

## 검증과 남은 확인

- 소스 Snapshot, 전체 Test·Build 성공.
- 개발 클라이언트가 로컬 서버에 접속했고, 리소스팩이 로드됐다.
- 1인칭에서 검과 왼손 검집, F5 뒷모습에서 양손 표시를 확인했다.
- `run/logs/latest.log`에 `Virtual scabbard drawn for the first time on the LEFT arm` 기록.
- 보조손 슬롯의 실제 NBT, 공격 중 왼팔, 다른 플레이어 시점과 장시간 동작은
  아직 수동 판정이 필요하다. 빌드 성공만으로 이를 완료로 간주하지 않는다.

F5에서 검집의 크기·손 위치가 어긋나면 현재 Blockbench 저장본의
`thirdperson_lefthand` 변환을 확인한다. 코드에 임의의 위치 보정을 더하지 않는다.
