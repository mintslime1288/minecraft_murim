"""Create a restrained, original adult run cycle on the Murim-owned rig.

This action contains rotation keys only.  Root translation and scale stay untouched,
so it cannot move the Minecraft player or introduce vertical bobbing.
"""
import math
from pathlib import Path
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_RUN_NATURAL_V1"
BLEND = Path(__file__).resolve().parent / "murim_player_rig_v1_editable.blend"


def pose(left_leg, right_leg, left_knee, right_knee, left_arm, right_arm,
         left_elbow, right_elbow, spine_x, chest_x, body_roll):
    return {
        "thigh.L": (left_leg, 0, 0), "thigh.R": (right_leg, 0, 0),
        "shin.L": (left_knee, 0, 0), "shin.R": (right_knee, 0, 0),
        "upper_arm.L": (left_arm, 0, 0), "upper_arm.R": (right_arm, 0, 0),
        "lower_arm.L": (left_elbow, 0, 0), "lower_arm.R": (right_elbow, 0, 0),
        # Positive X is the same restrained forward torso bias used by the verified walk.
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

# Contact, passing, opposite contact, passing, closed loop.  The pose is athletic,
# not a 45-degree dive: modest forward lean, broad stride, and quiet body roll.
keys = (
    (1, pose(31, -26, 3, 23, -27, 27, -4, -12, 6.0, 2.5, -0.8)),
    (5, pose(8, -10, 26, 7, -8, 8, -9, -3, 6.5, 2.7, -0.25)),
    (9, pose(-26, 31, 23, 3, 27, -27, -12, -4, 6.0, 2.5, 0.8)),
    (13, pose(-10, 8, 7, 26, 8, -8, -3, -9, 6.5, 2.7, 0.25)),
    (17, pose(31, -26, 3, 23, -27, 27, -4, -12, 6.0, 2.5, -0.8)),
)
for frame, values in keys:
    key_pose(rig, frame, values)
for frame, marker in ((1, "LEFT_CONTACT"), (5, "LEFT_PASSING"), (9, "RIGHT_CONTACT"),
                      (13, "RIGHT_PASSING"), (17, "LOOP")):
    action.pose_markers.new(marker).frame = frame
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
print(f"MURIM_NATURAL_RUN_V1={BLEND}")
