# MurimRPG player-rig handoff for Claude

## Scope

This is a Fabric client-only visual experiment for Minecraft Java 26.2 / Fabric
Loader 0.19.3 / Java 25. The server, player position, movement speed, collision,
combat, EXP and currency must not change.

The objective is a skin-preserving Minecraft player with visible elbow and knee
segments in local third-person view. It is not a request to render an external
GLB mesh in game.

## Current status

- Build JAR SHA-256: `6BA6B03F8B0F2050AF29725501F2BEE6889AA1FCB9D533D75B16F6578D963C3C`.
- `./gradlew test` and `./gradlew build` succeeded before that JAR was deployed.
- The active user-only test switch is
  `.minecraft/config/murimrpg/articulated-player.yml`, `enabled: true`.
- The experimental renderer is local player only and falls back to vanilla for
  sprinting, crouching, fall flying, swimming, riding, using an item, attacking,
  death and all armour.
- In-game verification of the newest connection has not yet happened.

## Blender source and export pipeline

- Editable working file:
  `assets-source/blender/murim-player-rig-v1/murim_player_rig_v1_editable.blend`
- Export script:
  `assets-source/blender/murim-player-rig-v1/export_walk_for_minecraft.py`
- Generated resource:
  `src/main/resources/assets/murimrpg/player_motion/murim_walk.json`

The Blender action `MURIM_WALK` is exported as rotations only. No mesh, skin,
root translation, scale, texture or third-party action is exported. The game
reader is `AuthoredRigWalkAnimation.java`; it samples that JSON and supplies the
result to `ArticulatedHumanoidModel.java`.

## Important history / prohibitions

Earlier full Meshy GLB renderer attempts created giant, inverted, floating and
horizontal-limbed characters, texture tearing and lag. Do not re-enable direct
GLB mesh rendering, GLB root motion, scale data or position translation. Do not
copy external rigs, models, animations or textures. Do not claim in-game success
from a build result.

`NaturalWalkMotion.java` is a separate vanilla-mesh procedural walk path. It
already uses an odd third harmonic, not a second harmonic. It is not evidence
that the articulated replacement renderer is working.

## Files to inspect before proposing a patch

1. `src/main/java/com/murimrpg/client/render/AuthoredRigWalkAnimation.java`
2. `src/main/java/com/murimrpg/client/render/ModelPartArticulatedPlayerRenderer.java`
3. `src/main/java/com/murimrpg/client/render/ArticulatedHumanoidModel.java`
4. `src/main/java/com/murimrpg/client/render/ArticulatedPlayerSettings.java`
5. `src/main/java/com/murimrpg/mixin/client/LivingEntityRendererMixin.java`
6. `src/main/java/com/murimrpg/mixin/client/PlayerModelMixin.java`
7. `src/main/resources/articulated-player.yml`
8. `.minecraft/config/murimrpg/articulated-player.yml` (active user setting)
9. The Blender file and exporter above.

## Requested Claude work

Read the files first. Identify whether the custom articulated ModelPart renderer
is actually submitted after the vanilla renderer redirect, whether the authored
JSON track is sampled with the correct phase and axes, and whether skin UVs and
joint pivots remain Minecraft-compatible. Provide exact unified diffs only after
reading the files. Prefer a diagnostic overlay or rate-limited log that proves
the final renderer and sampled authored pose. Keep a config-based vanilla
fallback. Do not substitute Meshy clips or add a third-party animation mod.
