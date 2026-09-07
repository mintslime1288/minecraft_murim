# Murim Player Rig v1

This is a dedicated editable working copy of the MurimRPG-owned humanoid rig.
It keeps the original source file untouched and is not yet an in-game renderer
asset. The mesh is a visual authoring model; the future game exporter may use
only approved bone rotations.

## Current scope

- Minecraft-like block proportions.
- Separate upper/lower arms and legs, hands, feet, neck, hips and spine.
- Editable `MURIM_IDLE`, `MURIM_WALK`, `MURIM_RUN`, `MURIM_JUMP` and `MURIM_PUNCH` actions.
- `MURIM_WALK_NATURAL_V2` and `MURIM_RUN_NATURAL_V1` are the exported locomotion actions.
  They preserve the original actions and use rotation-only contact/passing poses with a
  restrained forward torso bias.
- Root location stays at zero. Do not keyframe root location or scale.

## Safe editing

1. Select `MURIM_HUMANOID_RIG`.
2. Enter Pose Mode.
3. Use the Dope Sheet Action Editor to select an action.
4. Move to an existing keyframe, rotate a bone and press `I` then `Rotation`.
5. Keep the first and final frame identical for looping walk/run actions.

This file does not change Minecraft, player movement, collision or server data.

To regenerate the exported game JSON after editing the natural actions:

`blender --background murim_player_rig_v1_editable.blend --python author_natural_walk_v2.py`

then run `export_walk_for_minecraft.py` or `export_run_for_minecraft.py` with the same Blender file.
