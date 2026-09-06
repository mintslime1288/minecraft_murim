"""Create a restrained, original human walk action on the Murim-owned rig.

Run with Blender 5.2 in background against murim_player_rig_v1_editable.blend.
The existing MURIM_WALK action remains untouched.  This script writes only
rotation keys, has no root-motion or scale keys, and creates MURIM_WALK_NATURAL_V2.
"""
import math
from pathlib import Path
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_WALK_NATURAL_V2"
BLEND = Path(__file__).resolve().parent / "murim_player_rig_v1_editable.blend"


def pose(left_leg, right_leg, left_knee, right_knee, left_arm, right_arm,
         left_elbow, right_elbow, spine_x, chest_x, body_roll):
    # Contact / passing poses.  Arms and opposite legs deliberately counter-swing;
    # the torso keeps a quiet forward bias instead of a conspicuous side-to-side bob.
    return {
        "thigh.L": (left_leg, 0, 0), "thigh.R": (right_leg, 0, 0),
        "shin.L": (left_knee, 0, 0), "shin.R": (right_knee, 0, 0),
        "upper_arm.L": (left_arm, 0, 0), "upper_arm.R": (right_arm, 0, 0),
        "lower_arm.L": (left_elbow, 0, 0), "lower_arm.R": (right_elbow, 0, 0),
        "spine": (spine_x, 0, body_roll), "chest": (chest_x, 0, body_roll * .35),
    }


def key_pose(rig, frame, values):
    bpy.context.scene.frame_set(frame)
    for bone_name, degrees in values.items():
        bone = rig.pose.bones[bone_name]
        bone.rotation_mode = "XYZ"
        bone.rotation_euler = tuple(math.radians(value) for value in degrees)
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

# First and final frame are identical: loop closes with no pose jump.
keys = (
    (1, pose(21, -21, 2, 11, -17, 17, -3, -9, 3.1, 1.25, -0.45)),
    (7, pose(8, -8, 13, 4, -7, 7, -7, -3, 3.35, 1.35, -0.15)),
    (13, pose(-21, 21, 11, 2, 17, -17, -9, -3, 3.1, 1.25, 0.45)),
    (19, pose(-8, 8, 4, 13, 7, -7, -3, -7, 3.35, 1.35, 0.15)),
    (25, pose(21, -21, 2, 11, -17, 17, -3, -9, 3.1, 1.25, -0.45)),
)
for frame, values in keys:
    key_pose(rig, frame, values)
for frame, marker in ((1, "LEFT_CONTACT"), (7, "LEFT_PASSING"), (13, "RIGHT_CONTACT"),
                      (19, "RIGHT_PASSING"), (25, "LOOP")):
    action.pose_markers.new(marker).frame = frame

# Keep the authored source readable: smooth Blender interpolation, no location/scale F-curves.
for slot in action.slots:
    for layer in action.layers:
        for strip in layer.strips:
            for channelbag in strip.channelbags:
                if channelbag.slot != slot:
                    continue
                for fcurve in channelbag.fcurves:
                    for point in fcurve.keyframe_points:
                        point.interpolation = 'BEZIER'

bpy.context.scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(BLEND))
print(f"MURIM_NATURAL_WALK_V2={BLEND}")
