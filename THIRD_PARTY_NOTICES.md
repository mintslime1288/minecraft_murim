# Third-party notices

MurimRPG의 전용 Fabric 클라이언트는 아래 라이브러리를 선택적으로 사용한다.

## Player Animation Library

- Version: 1.2.6 for Minecraft 26.2
- Source: https://github.com/ZigyTheBird/PlayerAnimationLibrary
- Distribution: https://modrinth.com/mod/player-animation-library
- License: MIT
- Copyright: Copyright (c) 2025 ZigyTheBird; upstream portions retain their own MIT notices.
- Use: player animation layers, animation resource loading, first-person isolation.

## Bendable Cuboids

- Version: 2.0.4 for Minecraft 26.2
- Source: https://github.com/PlayerAnimationLibrary/BendableCuboids
- Distribution: https://modrinth.com/mod/bendable-cuboids
- License: MIT
- Use: visible bending of the original Minecraft player skin cuboids.

두 라이브러리의 MIT 저작권 및 허가 고지는 배포물과 함께 유지해야 한다. MurimRPG의
`pal_*.json` 동작 파일은 이 프로젝트에서 새로 작성했으며 다른 모드의 애니메이션을
추출하거나 포함하지 않는다. `assets-source/meshy/`에 보존된 외부 생성 자산은 별도
라이선스 확인 없이 배포 대상에 포함하지 않는다.

## Custom Player Animations

- Tested local-client version: 5.9.8-fabric+26.2
- Source and distribution: https://modrinth.com/mod/cpa
- License: CC-BY-NC-SA-4.0
- Use: client-side-only smooth vanilla-skin movement, jump, action and tool animations.

이 모드는 로컬 테스트 클라이언트에만 설치한다. CC-BY-NC-SA-4.0 조건 때문에 MurimRPG의
상업 전용 런처·모드팩에 포함하거나 수정본을 배포하기 전에 저작자에게 별도 허가를 받아야 한다.
MurimRPG는 이 모드의 코드·애니메이션·리소스를 JAR에 포함하지 않으며, 모드가 설치됐을 때만
자체 PAL/GLB 플레이어 모션을 안전하게 비활성화한다.

## Cloth Config API

- Tested local-client version: 26.2.155+fabric
- Source and distribution: https://modrinth.com/mod/cloth-config
- License: LGPL-3.0-only
- Use: Custom Player Animations configuration screen dependency.
