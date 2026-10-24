"""Export the rotation-only MURIM_WALK_GROUNDED_V4 game clip."""
from pathlib import Path
import json
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_WALK_GROUNDED_V4"
BONES = ("upper_arm.L", "upper_arm.R", "lower_arm.L", "lower_arm.R", "thigh.L", "thigh.R", "shin.L", "shin.R", "spine", "chest")
OUT = Path(__file__).resolve().parents[3] / "src" / "main" / "resources" / "assets" / "murimrpg" / "player_motion" / "murim_walk_v4.json"
rig = bpy.data.objects.get(RIG_NAME)
action = bpy.data.actions.get(ACTION_NAME)
if rig is None or action is None:
    raise RuntimeError(f"Expected {RIG_NAME} and {ACTION_NAME}")
rig.animation_data_create()
rig.animation_data.action = action
start, end = (round(value) for value in action.frame_range)
frames = list(range(start, end + 1))
samples = {bone: [] for bone in BONES}
for frame in frames:
    bpy.context.scene.frame_set(frame)
    for bone in BONES:
        rotation = rig.pose.bones[bone].rotation_euler
        samples[bone].append([round(rotation.x, 7), round(rotation.y, 7), round(rotation.z, 7)])
loop, half = len(frames) - 1, (len(frames) - 1) // 2
# Bezier handle lengths are not stored symmetrically by Blender when the source action is
# duplicated. The runtime clip is sampled data, so make the one-way flexion tracks an
# exact cyclic half-shift after sampling; this preserves every left-side authored value.
for left, right in (("shin.L", "shin.R"), ("lower_arm.L", "lower_arm.R")):
    for index in range(loop):
        samples[right][index] = list(samples[left][(index + half) % loop])
    samples[right][-1] = list(samples[right][0])
for left, right in (("shin.L", "shin.R"), ("lower_arm.L", "lower_arm.R")):
    for index in range(loop):
        if abs(samples[right][index][0] - samples[left][(index + half) % loop][0]) > 1e-4:
            raise RuntimeError(f"{right} is not {left} half a cycle later at frame {index}")
for bone in ("shin.L", "shin.R"):
    if min(value[0] for value in samples[bone]) < -1e-9:
        raise RuntimeError(f"{bone} goes negative")
for bone in BONES:
    if samples[bone][0] != samples[bone][-1]:
        raise RuntimeError(f"{bone} does not close its loop")
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({"schema": 1, "source": "MurimRPG-owned Blender action MURIM_WALK_GROUNDED_V4", "rotation_only": True, "fps": bpy.context.scene.render.fps, "frame_start": start, "frame_end": end, "bones": samples}, indent=2), encoding="utf-8")
print(f"MURIM_WALK_GROUNDED_V4_EXPORT={OUT}")
