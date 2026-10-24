# Claude 전달용 통합 프롬프트: 인벤토리 한 칸의 검과 검집

아래 블록을 Claude에게 통째로 전달한다. Codex는 2026-10-24 현재 트리를 확인했으며,
프로젝트 `AGENTS.md`의 클라이언트 렌더러 작성 분담에 따라 새 렌더러 로직을
추측해서 추가하지 않았다. Claude 패치를 받으면 Codex가 검토·빌드·적용한다.

```text
MurimRPG (Minecraft Java 26.2, Fabric Loader 0.19.3, Fabric API,
Player Animation Library 1.2.6)의 현재 소스에 아래 기능을 구현해 주세요.

요구: 검과 검집은 인벤토리/핫바에서 단 하나의 검 아이템이다. 검을 오른손에
들면 검 모델은 오른손, 긴 검집 모델은 빈 왼손에 동시에 보인다. 검집은 가상
표시이며 실제 보조손 ItemStack, 인벤토리 슬롯, 서버 지급 아이템, 아이템
드롭, 스택 수, 내구도, 충돌/공격 판정으로 만들지 않는다. 사용자가 검을
내리거나 다른 아이템으로 바꾸면 검집 표시도 사라진다. 실제 보조손에 다른
아이템을 들면 겹쳐 보이지 않게 안전하게 처리한다. 공격 모션은 오른팔만
검을 휘두르고 검집을 쥔 왼팔은 공격 때문에 따라 휘둘리지 않는다. 걷기·
달리기·점프의 기존 팔 움직임은 유지한다. 1인칭·F5 3인칭·타 플레이어
관찰에서 모두 일관되게 보인다. 사용자가 Blockbench에서 저장한 검과
검집의 큐브·텍스처·display.scale/rotation/translation 및 크기는 그대로
유지한다. 플레이어 히트박스와 게임 상호작용은 바꾸지 않는다.

현재 상태와 입력:
- 실제 저장소: MurimRPG-restored-20261017/minecraft_murim
- 저장본: assets-source/blockbench/top-straight-sword-v1/review/
  user-saved-20261024/{top_straight_sword,extended_black_scabbard}.bbmodel
- 기존 임시 리소스팩: assets-source/blockbench/top-straight-sword-v1/
  preview-pack-user-20261024/MurimRPG-Sword-Scabbard-Preview-26.2.zip
  두 모델, 별도 텍스처, 검의 minecraft:item_model 정의,
  오른팔 키만 남긴 pal_sword_swing.json을 포함한다.
- 현재 임시 시연은 iron_sword + 실제 offhand stick 두 아이템이었다.
  이것은 사용자의 최종 요구와 다르다. 게임에서 이 두 아이템을 지우거나
  자동 복제하는 것으로 해결하지 말 것.
- 실제 소스에는 PalPlayerAnimationIntegration.combatController()가
  주손 ItemTags.SWORDS와 avatar.swinging을 확인하여 pal_sword_swing을
  재생한다. 현재 리소스팩의 검 모델은 iron_sword의 minecraft:item_model
  컴포넌트로 지정한다.
- src/main/java/com/murimrpg/client/MurimRpgClient.java가 클라이언트
  진입점이다. PlayerModelMixin과 LivingEntityRendererMixin에는 이미
  대체 플레이어 렌더러 조건이 있으므로 그 경로와 충돌하지 않게 할 것.
- run/config/murimrpg/pal-natural-motion.yml은 켜져 있고,
  run/config/murimrpg/articulated-player.yml의 combat-motion은 꺼져 있다.

구현 조건:
1. 실제 주손 아이템 하나만으로 검집을 왼손 관절에 시각적으로 붙이는
   최소 범위의 클라이언트 렌더링 경로를 설계·구현해 주세요. 1인칭과 3인칭
   렌더링의 차이를 확인하고 각각 처리해 주세요. 서버 동기화나 실제
   보조손 슬롯 변조로 우회하지 마세요.
2. 검 모델은 바닐라 주손 렌더링을 최대한 유지하고, 검집만 추가 렌더링해
   손·팔 애니메이션과 카메라에 자연스럽게 따라가게 해 주세요. 실제 보조손
   아이템과 겹침, 왼손잡이 옵션, 관전자/숨김 상태, 리소스팩 미설치,
   다른 플레이어에게서의 모델 표시를 명시적으로 판정해 주세요.
3. 새 렌더러를 안전하게 끌 수 있는 설정 기반 fallback을 제공하고,
   외부 설정 경로와 적용 시점을 문서화해 주세요. 기본 플레이어 스킨,
   기존 목검, 다른 아이템, 서버 데이터, 월드, 현재 모션 cadence/기울기
   설정은 바꾸지 마세요.
4. Minecraft/Fabric 26.2의 실제 매핑/API에 맞춰 컴파일 가능한 unified
   diff 또는 완전한 수정 파일과 의미 있는 테스트를 주세요. 렌더 API를
   추측하지 말고 현재 프로젝트 의존성에서 확인해 주세요. Blockbench
   저장본과 게임 모델의 display 변환이 동일한지 검증해 주세요.
5. Codex에게 적용 방법, 필요 리소스팩/서버·클라이언트 배포 순서,
   테스트 및 F5 확인 기준, 되돌리는 방법을 전달해 주세요. 실제 인게임
   확인 전에는 품질 완료라고 주장하지 마세요.

완료 판정: 핫바에는 검 하나만 있다. 보조손 슬롯은 비어 있다. 그 상태로
검은 오른손, 검집은 왼손에 보인다. 공격 시 왼팔이 검 공격에 동조하지
않는다. 검을 내려놓으면 양손의 해당 시각 자산이 사라진다. 실제 보조손
아이템을 든 경우에도 아이템 복제·손 겹침·서버 상태 변경이 없다.
```

## Codex 현재 검증

- 사용자 제공 두 `.bbmodel`을 별도 파일로 복사하고 SHA-256을 대조했다.
- 변환기는 저장본 `display` 설정을 그대로 내보낸다. 1인칭 양손 동시
  표시는 두 아이템 임시 시연에서만 확인했다.
- `Snapshot`, `Test`, `Build`가 2026-10-24 개발 환경에서 성공했다.
  이것은 한 칸 아이템 구현 검증을 뜻하지 않는다.
- 한 칸 기능용 렌더러 패치는 아직 없으므로 F5·공격 확인은 미완료다.
