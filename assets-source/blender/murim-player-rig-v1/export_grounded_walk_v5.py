"""Export MURIM_WALK_GROUNDED_V5 as a validated rotation-only JSON resource."""
from pathlib import Path
import json
import math
import bpy

RIG_NAME, ACTION_NAME = "MURIM_HUMANOID_RIG", "MURIM_WALK_GROUNDED_V5"
BONES = ("upper_arm.L", "upper_arm.R", "lower_arm.L", "lower_arm.R", "thigh.L", "thigh.R", "shin.L", "shin.R", "spine", "chest")
OUT = Path(__file__).resolve().parents[3] / "src" / "main" / "resources" / "assets" / "murimrpg" / "player_motion" / "murim_walk_v5.json"
rig, action = bpy.data.objects.get(RIG_NAME), bpy.data.actions.get(ACTION_NAME)
if rig is None or action is None:
    raise RuntimeError(f"Expected {RIG_NAME} and {ACTION_NAME}")
rig.animation_data_create(); rig.animation_data.action = action
start, end = (round(value) for value in action.frame_range)
frames = list(range(start, end + 1))
samples = {bone: [] for bone in BONES}
for frame in frames:
    bpy.context.scene.frame_set(frame)
    for bone in BONES:
        rotation = rig.pose.bones[bone].rotation_euler
        samples[bone].append([round(rotation.x, 7), round(rotation.y, 7), round(rotation.z, 7)])
loop, half = len(frames) - 1, (len(frames) - 1) // 2
# Blender Bezier handle generation can make intermediate mirrored values differ. The game
# resource is sampled data, so enforce exact cyclic relations after sampling.
for left, right, transform in (("shin.L", "shin.R", lambda value: value), ("lower_arm.L", "lower_arm.R", lambda value: value),
                               ("thigh.L", "thigh.R", lambda value: -value), ("upper_arm.L", "upper_arm.R", lambda value: -value)):
    for index in range(loop):
        source = samples[left][(index + half) % loop] if left.startswith(("shin", "lower_arm")) else samples[left][index]
        samples[right][index] = [round(transform(source[0]), 7), source[1], source[2]]
    samples[right][-1] = list(samples[right][0])
for bone in BONES:
    if samples[bone][0] != samples[bone][-1]:
        raise RuntimeError(f"{bone} does not close its loop")
    for frame in samples[bone]:
        if not all(math.isfinite(value) for value in frame):
            raise RuntimeError(f"{bone} contains a non-finite rotation")
for bone in ("shin.L", "shin.R"):
    if min(frame[0] for frame in samples[bone]) < -1e-9:
        raise RuntimeError(f"{bone} hyperextends")
for bone in ("lower_arm.L", "lower_arm.R"):
    if max(frame[0] for frame in samples[bone]) > 1e-9:
        raise RuntimeError(f"{bone} hinges backwards")
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({"schema": 1, "source": "MurimRPG-owned Blender action MURIM_WALK_GROUNDED_V5", "rotation_only": True, "fps": bpy.context.scene.render.fps, "frame_start": start, "frame_end": end, "bones": samples}, indent=2), encoding="utf-8")
print(f"MURIM_WALK_GROUNDED_V5_EXPORT={OUT}")
