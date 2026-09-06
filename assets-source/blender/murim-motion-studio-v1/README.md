# Murim Motion Studio v1

This folder contains a new MurimRPG-owned Blender 5.2 source rig. It is a
reference authoring rig only: Minecraft continues to render the player's normal
skin, while the future exporter reads approved bone rotation curves.

## Files

- `build_murim_motion_studio.py`: reproducible source generator.
- `murim_motion_studio_v1.blend`: generated editable Blender file.
- `murim_walk_preview.png`: generated frame-7 walk preview.
- `murim_motion_studio_v3_meshy_walk_editable.blend`: a separate editable
  workspace generated from the user-provided Meshy walking GLB; v1 remains
  unchanged. It uses only per-bone rotation deltas from the Meshy action, so
  Meshy's incompatible rest-pose axes do not deform the Murim rig.

## Editing in Blender

1. Open `murim_motion_studio_v1.blend`.
2. Select `MURIM_HUMANOID_RIG`.
3. Switch to Pose Mode.
4. In the Dope Sheet, choose an action such as `MURIM_WALK` or `MURIM_RUN`.
5. Move to an existing keyframe, rotate a bone, and press `I` → Rotation.
6. Keep `root` location at `(0, 0, 0)`; game movement is server-authoritative.
7. Preserve the matching first/last pose for looping actions.

## Originality rule

Do not import another creator's `.blend`, rig, action, pose library, mesh, or
animation data. Tutorials may be used to learn principles such as contact,
down, passing, and up poses; every MurimRPG keyframe in this file is authored
inside this project.

## User-provided Meshy motion

The v3 file is a local editing workspace for the Meshy walking GLB explicitly
provided by the user. Its original source armature lives in the hidden
`MESHY_USER_PROVIDED_REFERENCE` collection. `MURIM_MESHY_WALK_EDITABLE` is the
rotation-delta retargeted action for `MURIM_HUMANOID_RIG`; root translation is
discarded. The Meshy source is not copied into the repository or game assets.
Confirm the Meshy account's applicable license before redistributing any source
asset or derivative animation.
