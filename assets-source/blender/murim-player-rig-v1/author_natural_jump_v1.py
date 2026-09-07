"""Create original rotation-only jump poses for the MurimRPG-owned humanoid rig."""
import math
from pathlib import Path
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_JUMP_NATURAL_V1"
BLEND = Path(__file__).resolve().parent / "murim_player_rig_v1_editable.blend"


def pose(left_leg, right_leg, left_knee, right_knee, left_arm, right_arm,
         left_elbow, right_elbow, spine_x, chest_x, body_roll=0):
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

# Frame 1 is neutral backup; the other frames are selected explicitly by vertical motion.
# No location/root/scale values are keyed.
keys = (
    (1, pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    (7, pose(-8, -8, 24, 24, -14, -14, -6, -6, 4.2, 1.8)),
    (13, pose(4, 4, 31, 31, -9, -9, -8, -8, 3.8, 1.6)),
    (19, pose(5, 5, 20, 20, 7, 7, -3, -3, 3.2, 1.3)),
    (25, pose(-8, -8, 27, 27, -2, -2, -4, -4, 4.8, 2.0)),
)
for frame, values in keys:
    key_pose(rig, frame, values)
for frame, marker in ((1, "NEUTRAL"), (7, "RISE"), (13, "APEX"), (19, "FALL"), (25, "LAND")):
    action.pose_markers.new(marker).frame = frame
bpy.context.scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(BLEND))
print(f"MURIM_NATURAL_JUMP_V1={BLEND}")
