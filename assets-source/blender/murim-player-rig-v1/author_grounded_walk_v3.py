"""Author an original, grounded walk without altering the prior Murim actions.

Run with Blender 5.2 against murim_player_rig_v1_editable.blend.  The result is saved
as murim_player_rig_v2_grounded.blend; root location and every scale channel remain
unkeyed.  This first B-stage focuses on contact, loading response and passing.
"""
import math
from pathlib import Path
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_WALK_GROUNDED_V3"
SOURCE = Path(__file__).resolve().parent / "murim_player_rig_v1_editable.blend"
OUTPUT = Path(__file__).resolve().parent / "murim_player_rig_v2_grounded.blend"


def degrees(value):
    return math.radians(value)


def pose(left_leg, right_leg, left_knee, right_knee, left_arm, right_arm,
         left_elbow, right_elbow, spine_x, chest_x):
    return {
        "thigh.L": (left_leg, 0, 0), "thigh.R": (right_leg, 0, 0),
        "shin.L": (left_knee, 0, 0), "shin.R": (right_knee, 0, 0),
        "upper_arm.L": (left_arm, 0, 0), "upper_arm.R": (right_arm, 0, 0),
        "lower_arm.L": (left_elbow, 0, 0), "lower_arm.R": (right_elbow, 0, 0),
        "spine": (spine_x, 0, 0), "chest": (chest_x, 0, 0),
    }


def key_pose(rig, frame, values):
    bpy.context.scene.frame_set(frame)
    for bone_name, values_degrees in values.items():
        bone = rig.pose.bones[bone_name]
        bone.rotation_mode = "XYZ"
        bone.rotation_euler = tuple(degrees(value) for value in values_degrees)
        bone.keyframe_insert(data_path="rotation_euler", frame=frame)


rig = bpy.data.objects.get(RIG_NAME)
if rig is None:
    raise RuntimeError(f"Missing rig: {RIG_NAME}")
old = bpy.data.actions.get(ACTION_NAME)
if old is not None:
    bpy.data.actions.remove(old)
action = bpy.data.actions.new(ACTION_NAME)
rig.animation_data_create()
rig.animation_data.action = action

# Left foot contact at f1. f4 is the loading response, f16 the knee-high passing pose.
# Right-side values are mirrored half a cycle later; only rotation keys are created.
keys = (
    (1,  pose(30, -30, 3, 12, -17, 17, -4, -8, 3.1, 1.2)),
    (4,  pose(24, -24, 15, 6, -10, 10, -7, -4, 3.3, 1.3)),
    (7,  pose(10, -10, 8, 5, -2, 2, -8, -4, 3.4, 1.35)),
    (10, pose(-10, 10, 5, 8, 8, -8, -6, -6, 3.3, 1.3)),
    (13, pose(-30, 30, 12, 3, 17, -17, -8, -4, 3.1, 1.2)),
    (16, pose(-12, 12, 36, 9, 10, -10, -4, -7, 3.25, 1.3)),
    (19, pose(12, -12, 24, 6, 2, -2, -4, -8, 3.4, 1.35)),
    (22, pose(26, -26, 10, 13, -8, 8, -4, -6, 3.25, 1.3)),
    (25, pose(30, -30, 3, 12, -17, 17, -4, -8, 3.1, 1.2)),
)
for frame, values in keys:
    key_pose(rig, frame, values)
for frame, marker in ((1, "LEFT_CONTACT"), (4, "LEFT_LOADING"), (7, "LEFT_MIDSTANCE"),
                      (13, "RIGHT_CONTACT"), (16, "RIGHT_LOADING"), (19, "RIGHT_MIDSTANCE"),
                      (25, "LOOP")):
    action.pose_markers.new(marker).frame = frame

for slot in action.slots:
    for layer in action.layers:
        for strip in layer.strips:
            for channelbag in strip.channelbags:
                if channelbag.slot != slot:
                    continue
                for fcurve in channelbag.fcurves:
                    for point in fcurve.keyframe_points:
                        point.interpolation = "BEZIER"

bpy.context.scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(OUTPUT))
print(f"MURIM_WALK_GROUNDED_V3={OUTPUT}")
