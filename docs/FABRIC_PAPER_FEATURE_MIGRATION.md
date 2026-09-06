# Paper 기능의 Fabric 이식 현황

Paper 플러그인 JAR과 기존 리소스팩 ZIP을 읽기 전용으로 분석해, Fabric 모드에 다시 구현한다. Paper의 Bukkit 코드나 저장 형식은 직접 복사하지 않는다.

## 이식 완료: 인벤토리 화폐 기반

- 설정: `config/murimrpg/currencies.yml`
- 아이템 식별: Minecraft 26.2 `custom_data` 컴포넌트의 `murimrpg_currency` 값
- 외형 분기: 기존 CustomModelData 숫자 `31001`~`31004`를 float CustomModelData 값으로 유지
- 저장 방식: 실제 인벤토리 ItemStack만 검사한다. 별도의 지갑·은행 DB는 만들지 않는다.

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/화폐`, `/money` | 플레이어 | 보유 화폐와 총 현대 체감 가치를 확인 |
| `/화폐관리 지급 <대상> <화폐> <수량>` | OP | 실제 화폐 아이템 지급 |
| `/화폐관리 회수 <대상> <화폐> <수량>` | OP | 실제 화폐 아이템 회수 |
| `/화폐관리 확인 <대상>` | OP | 대상 인벤토리의 화폐를 확인 |

화폐 입력은 `엽전`, `철전`, `은원보`, `금원보`를 우선 사용한다. 안정 ID도 허용하며, 과거 `EUNHWA`, `GEUMHWA`는 각각 `EUNWONBO`, `GEUMWONBO`로 해석한다.

## 다음 이식 순서

1. 목검과 훈련 허수아비 ItemStack 식별·관리자 지급 명령
2. Fabric 이벤트 기반 설치·타격·쿨다운·EXP·회수
3. 기존 ZIP을 소스 폴더로 안전하게 복구하고 Fabric 배포용 리소스팩으로 문서화

고밀도 GLB 모델 직접 렌더링은 전용 Fabric 클라이언트의 별도 단계다. 현재 서버 전용 이식 범위에는 포함하지 않는다.
