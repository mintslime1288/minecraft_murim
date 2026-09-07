"""Export the MurimRPG-owned natural run as rotation-only game data."""
from pathlib import Path
import json
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_RUN_NATURAL_V1"
BONES = ("upper_arm.L", "upper_arm.R", "lower_arm.L", "lower_arm.R", "thigh.L", "thigh.R",
         "shin.L", "shin.R", "spine", "chest")
OUT = Path(__file__).resolve().parents[3] / "src" / "main" / "resources" / "assets" / "murimrpg" / "player_motion" / "murim_run.json"

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
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({
    "schema": 1, "source": "MurimRPG-owned Blender action MURIM_RUN_NATURAL_V1",
    "rotation_only": True, "fps": bpy.context.scene.render.fps,
    "frame_start": start, "frame_end": end, "bones": samples,
}, indent=2), encoding="utf-8")
print(f"MURIM_NATURAL_RUN_V1_EXPORT={OUT}")
