"""Author rotation-only MURIM_WALK_GROUNDED_V5; V4 legs plus carried, delayed arms."""
import math
from pathlib import Path
import bpy

RIG_NAME = "MURIM_HUMANOID_RIG"
ACTION_NAME = "MURIM_WALK_GROUNDED_V5"
OUTPUT = Path(__file__).resolve().parent / "murim_player_rig_v4_armswing.blend"
CYCLE_FRAMES, HALF_CYCLE = 24, 12
LEG_KEYS = {
    1: (30, 3, 3.10, 1.20), 4: (24, 15, 3.30, 1.30), 7: (10, 8, 3.40, 1.35),
    10: (-10, 5, 3.30, 1.30), 13: (-30, 12, 3.10, 1.20), 16: (-12, 36, 3.25, 1.30),
    19: (12, 24, 3.40, 1.35), 22: (26, 10, 3.25, 1.30), 25: (30, 3, 3.10, 1.20),
}
# Arm extrema lag one frame behind the leg keys; forearms remain comfortably flexed.
ARM_KEYS = {
    1: (-14.0, -25.3), 2: (-17.0, -26.0), 5: (-10.0, -24.0), 8: (-2.0, -20.0),
    11: (8.0, -16.0), 14: (17.0, -14.0), 17: (10.0, -16.0), 20: (2.0, -20.0),
    23: (-8.0, -24.0), 25: (-14.0, -25.3),
}
KEY_FRAMES = sorted(set(LEG_KEYS) | set(ARM_KEYS))

def wrapped(frame):
    return ((frame - 1 + HALF_CYCLE) % CYCLE_FRAMES) + 1

def curve(table, frame):
    frame = ((frame - 1) % CYCLE_FRAMES) + 1
    keys = sorted(table)
    if frame in table:
        return table[frame]
    lower = max((key for key in keys if key <= frame), default=keys[-1] - CYCLE_FRAMES)
    upper = min((key for key in keys if key >= frame), default=keys[0] + CYCLE_FRAMES)
    low = table[lower if lower in table else lower + CYCLE_FRAMES]
    high = table[upper if upper in table else upper - CYCLE_FRAMES]
    return low + (high - low) * (frame - lower) / (upper - lower)

def leg_value(index, frame):
    return curve({key: values[index] for key, values in LEG_KEYS.items()}, frame)

def arm_value(index, frame):
    return curve({key: values[index] for key, values in ARM_KEYS.items()}, frame)

def pose(frame):
    thigh, shin, spine, chest = (leg_value(index, frame) for index in range(4))
    arm, elbow = arm_value(0, frame), arm_value(1, frame)
    return {
        "thigh.L": (thigh, 0, 0), "thigh.R": (-thigh, 0, 0),
        "shin.L": (shin, 0, 0), "shin.R": (leg_value(1, wrapped(frame)), 0, 0),
        "upper_arm.L": (arm, 0, 0), "upper_arm.R": (-arm, 0, 0),
        "lower_arm.L": (elbow, 0, 0), "lower_arm.R": (arm_value(1, wrapped(frame)), 0, 0),
        "spine": (spine, 0, 0), "chest": (chest, 0, 0),
    }

def verify():
    for frame in range(1, CYCLE_FRAMES + 1):
        values = pose(frame)
        for bone, angles in values.items():
            assert all(math.isfinite(angle) for angle in angles), f"{bone} at {frame} is not finite"
            if bone not in ("spine", "chest"):
                assert angles[1] == 0 and angles[2] == 0, f"{bone} left X axis"
        assert values["shin.L"][0] >= 0 and values["shin.R"][0] >= 0, "knee hyperextends"
        assert values["lower_arm.L"][0] <= 0 and values["lower_arm.R"][0] <= 0, "elbow hinges backwards"
        mirror = pose(wrapped(frame))
        assert values["shin.R"] == mirror["shin.L"]
        assert values["lower_arm.R"] == mirror["lower_arm.L"]
    assert pose(1) == pose(25), "loop does not close"
    elbows = [pose(frame)["lower_arm.L"][0] for frame in range(1, CYCLE_FRAMES + 1)]
    assert max(elbows) - min(elbows) > 8, "forearm remains a straight plank"

def key_pose(rig, frame, values):
    bpy.context.scene.frame_set(frame)
    for name, degrees in values.items():
        bone = rig.pose.bones[name]
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
for frame, marker in ((1, "LEFT_CONTACT"), (2, "ARM_FORWARD"), (4, "LEFT_LOADING"), (7, "LEFT_MIDSTANCE"),
                      (13, "LEFT_TOE_OFF_RIGHT_CONTACT"), (14, "ARM_BACK"), (16, "LEFT_PASSING"), (25, "LOOP")):
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
print(f"MURIM_WALK_GROUNDED_V5={OUTPUT}")
