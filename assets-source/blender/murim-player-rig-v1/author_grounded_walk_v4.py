"""Author MURIM_WALK_GROUNDED_V4 with a programmatically mirrored right knee and elbow."""
import math
from pathlib import Path
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_WALK_GROUNDED_V4"
OUTPUT = Path(__file__).resolve().parent / "murim_player_rig_v3_grounded.blend"
CYCLE_FRAMES = 24
HALF_CYCLE = CYCLE_FRAMES // 2
KEY_FRAMES = (1, 4, 7, 10, 13, 16, 19, 22, 25)
# frame: thigh.L, shin.L, upper_arm.L, lower_arm.L, spine.x, chest.x
LEFT = {
    1: (30, 3, -17, -4, 3.10, 1.20), 4: (24, 15, -10, -7, 3.30, 1.30),
    7: (10, 8, -2, -8, 3.40, 1.35), 10: (-10, 5, 8, -6, 3.30, 1.30),
    13: (-30, 12, 17, -8, 3.10, 1.20), 16: (-12, 36, 10, -4, 3.25, 1.30),
    19: (12, 24, 2, -4, 3.40, 1.35), 22: (26, 10, -8, -4, 3.25, 1.30),
    25: (30, 3, -17, -4, 3.10, 1.20),
}

def wrapped(frame):
    return ((frame - 1 + HALF_CYCLE) % CYCLE_FRAMES) + 1

def pose(frame):
    thigh, shin, arm, elbow, spine, chest = LEFT[frame]
    mirrored = LEFT[wrapped(frame)]
    return {
        "thigh.L": (thigh, 0, 0), "thigh.R": (-thigh, 0, 0),
        "shin.L": (shin, 0, 0), "shin.R": (mirrored[1], 0, 0),
        "upper_arm.L": (arm, 0, 0), "upper_arm.R": (-arm, 0, 0),
        "lower_arm.L": (elbow, 0, 0), "lower_arm.R": (mirrored[3], 0, 0),
        "spine": (spine, 0, 0), "chest": (chest, 0, 0),
    }

def verify():
    for frame in KEY_FRAMES:
        values = pose(frame)
        for bone in ("shin.L", "shin.R"):
            assert values[bone][0] >= 0, f"{bone} at {frame} hyperextends"
        for bone, value in values.items():
            if bone not in ("spine", "chest"):
                assert value[1] == 0 and value[2] == 0, f"{bone} left X axis"
    for frame in KEY_FRAMES[:-1]:
        assert pose(frame)["shin.R"] == pose(wrapped(frame))["shin.L"]
        assert pose(frame)["lower_arm.R"] == pose(wrapped(frame))["lower_arm.L"]
    assert pose(1) == pose(25)

def key_pose(rig, frame, values):
    bpy.context.scene.frame_set(frame)
    for bone_name, degrees in values.items():
        bone = rig.pose.bones[bone_name]
        bone.rotation_mode = "XYZ"
        bone.rotation_euler = tuple(math.radians(value) for value in degrees)
        bone.keyframe_insert(data_path="rotation_euler", frame=frame)

verify()
rig = bpy.data.objects.get(RIG_NAME)
if rig is None:
    raise RuntimeError(f"Missing rig: {RIG_NAME}")
old = bpy.data.actions.get(ACTION_NAME)
if old is not None:
    bpy.data.actions.remove(old)
action = bpy.data.actions.new(ACTION_NAME)
rig.animation_data_create()
rig.animation_data.action = action
for frame in KEY_FRAMES:
    key_pose(rig, frame, pose(frame))
for frame, marker in ((1, "LEFT_CONTACT"), (4, "LEFT_LOADING"), (7, "LEFT_MIDSTANCE"),
                      (13, "RIGHT_CONTACT"), (16, "RIGHT_LOADING"), (19, "RIGHT_MIDSTANCE"), (25, "LOOP")):
    action.pose_markers.new(marker).frame = frame
for slot in action.slots:
    for layer in action.layers:
        for strip in layer.strips:
            for channelbag in strip.channelbags:
                if channelbag.slot == slot:
                    for fcurve in channelbag.fcurves:
                        for point in fcurve.keyframe_points:
                            point.interpolation = "BEZIER"
bpy.context.scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(OUTPUT))
print(f"MURIM_WALK_GROUNDED_V4={OUTPUT}")
