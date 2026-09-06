# 관절 플레이어 리그

현재 기본 구현은 직접 만든 `LivingEntityRenderer` 대체 모델이 아니라 PAL 1.2.6과
Bendable Cuboids 2.0.4 조합이다. 이 조합은 Minecraft 26.2/Fabric 전용 공식 배포를
사용하며, 플레이어의 원래 스킨·겉옷·손 아이템 경로를 유지한 채 `bend` 채널로
팔꿈치와 무릎을 변형한다. 기존 네이티브 ModelPart 리그와 Meshy GLB 렌더러는 삭제하지
않고 설정 기반 실험/fallback으로 보존하며 기본값은 꺼져 있다.

MurimRPG의 이전 실험 구현에는 전용 클라이언트에서 로컬 플레이어 3인칭의 팔과 다리를 상완/하완,
허벅지/종아리로 분리하는 네이티브 ModelPart 렌더러가 있다. 위험했던 지연 커스텀
지오메트리 경로는 사용하지 않으며, 설정이 꺼져 있거나 호환되지 않는 자세에서는
바닐라 플레이어 모델로 전환한다.

이전 실험 경로는 `config/murimrpg/articulated-player.yml`에서 켤 수 있지만 PAL이
활성인 동안에는 중복 렌더링 방지를 위해 사용하지 않는다. PAL 선택 기준은
`config/murimrpg/pal-player-animation.yml`에서 관리한다.
이 설정은 시각 효과만 바꾸며 서버 이동, 충돌, 판정, EXP에는 영향을 주지 않는다.

현재 기본 이동 클립은 Blender로 독자 제작한
`assets-source/blender/murim-articulated-locomotion-v3/` 원본에서 내보낸
`native_walk.glb`, `native_run.glb`, `native_fast_run.glb`다. 단순 사인파가 아니라
접지(Contact), 하강(Down), 통과(Passing), 상승(Up)을 양발에 각각 배치한 32프레임
사이클이다. Hips의 X/Y/Z 이동은 사용하지 않고 관절 회전만 제공한다. Meshy
원본은 `assets-source/meshy/motion-library/`에 비교·백업용으로 별도 보존한다.

v3는 v2의 접지 순서를 유지하면서 걷기 보폭 31도, 무릎 29도, 팔꿈치 14도와 작은
몸통 반회전을 사용한다. 달리기는 보폭 42도, 무릎 38도까지 확장한다. 루트 이동은
추가하지 않아 역동성 증가가 서버 위치나 카메라 상하 흔들림으로 이어지지 않는다.

`walk.stride-degrees`, `walk.knee-degrees`, `walk.elbow-degrees`는 실제 관절 상한으로
적용된다. `retarget.*-gain`은 관절 회전 강도를 조절하며 현재 걷기는 상체보다
허벅지와 종아리만 더 강화해 발이 움직이지 않는 것처럼 보이는 문제를 줄인다.

걷기 제작 방식은 사용자가 제공한 Woodplank Blender 걷기·달리기 튜토리얼을 작업 원리
참고용으로만 사용했다. 영상의 리그, 키프레임, 모델 또는 자산을 복사하지 않았으며,
MurimRPG 리그와 애니메이션 수치는 프로젝트 내부에서 새로 제작했다.

현재는 바닐라 64x64 기본 스킨 영역을 관절 메시로 다시 사용하는 단계다. 갑옷, 웅크리기,
수영, 탑승, 아이템 사용, 공격은 바닐라 렌더러로 안전하게 전환한다. 슬림 스킨과
겉옷 레이어, 갑옷 관절 메시, 고품질 손/발 메시, GLB 리깅 기반 공격은 후속 작업이다.
기존 Meshy GLB는 사용자의 스킨 UV와 일치하지 않으므로 기본 플레이어 대체에 사용하지
않으며 원본은 `assets-source/meshy/`에 보존한다.
