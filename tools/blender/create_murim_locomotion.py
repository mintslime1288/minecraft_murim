"""Create original MurimRPG humanoid locomotion clips without external animation assets.

The script writes one .blend source file and three in-place GLB animation clips.  The
Hips translation remains zero in every frame; only local joint rotations are animated.
"""
import bpy
import math
import pathlib
import sys


def args():
    values = sys.argv
    return values[values.index("--") + 1:] if "--" in values else []


output = pathlib.Path(args()[0]).resolve()
output.mkdir(parents=True, exist_ok=True)

SKELETON = [
    ("Hips", None, (0, 0, 1.0), (0, 0, 1.2)),
    ("Spine01", "Hips", (0, 0, 1.2), (0, 0, 1.45)),
    ("Spine02", "Spine01", (0, 0, 1.45), (0, 0, 1.65)),
    ("RightUpLeg", "Hips", (-.12, 0, 1.0), (-.12, 0, .55)),
    ("RightLeg", "RightUpLeg", (-.12, 0, .55), (-.12, 0, .10)),
    ("RightFoot", "RightLeg", (-.12, 0, .10), (-.12, -.18, .05)),
    ("LeftUpLeg", "Hips", (.12, 0, 1.0), (.12, 0, .55)),
    ("LeftLeg", "LeftUpLeg", (.12, 0, .55), (.12, 0, .10)),
    ("LeftFoot", "LeftLeg", (.12, 0, .10), (.12, -.18, .05)),
    ("RightShoulder", "Spine02", (-.20, 0, 1.58), (-.42, 0, 1.58)),
    ("RightArm", "RightShoulder", (-.42, 0, 1.58), (-.42, 0, 1.18)),
    ("RightForeArm", "RightArm", (-.42, 0, 1.18), (-.42, 0, .84)),
    ("RightHand", "RightForeArm", (-.42, 0, .84), (-.42, 0, .70)),
    ("LeftShoulder", "Spine02", (.20, 0, 1.58), (.42, 0, 1.58)),
    ("LeftArm", "LeftShoulder", (.42, 0, 1.58), (.42, 0, 1.18)),
    ("LeftForeArm", "LeftArm", (.42, 0, 1.18), (.42, 0, .84)),
    ("LeftHand", "LeftForeArm", (.42, 0, .84), (.42, 0, .70)),
]


def reset_and_rig():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.object.armature_add(enter_editmode=True)
    arm = bpy.context.object
    arm.name = "MurimRPG_Articulated_Humanoid"
    data = arm.data
    data.name = "MurimRPG_Articulated_Humanoid"
    data.edit_bones.remove(data.edit_bones[0])
    bones = {}
    for name, parent, head, tail in SKELETON:
        bone = data.edit_bones.new(name)
        bone.head, bone.tail = head, tail
        bone.parent = bones.get(parent)
        bone.use_connect = False
        bones[name] = bone
    bpy.ops.object.mode_set(mode='POSE')
    for bone in arm.pose.bones:
        bone.rotation_mode = 'XYZ'
    bpy.ops.object.mode_set(mode='OBJECT')
    return arm


def create_action(arm, name, stride, knee, elbow, torso):
    action = bpy.data.actions.new(name)
    arm.animation_data_create()
    arm.animation_data.action = action
    # Original eight-pose locomotion cycle authored for the Minecraft proportions:
    # contact -> down -> passing -> up -> opposite contact, then mirrored.
    # First and last poses match exactly, producing a seamless 32-frame loop.
    frames = (1, 5, 9, 13, 17, 21, 25, 29, 33)
    leg_swing = (1.0, .55, 0.0, -.60, -1.0, -.55, 0.0, .60, 1.0)
    right_knee = (.08, .35, .10, .06, .55, .85, .90, .40, .08)
    left_knee = (.55, .85, .90, .40, .08, .35, .10, .06, .55)
    right_elbow = (.35, .45, .60, .75, .55, .40, .30, .25, .35)
    left_elbow = (.55, .40, .30, .25, .35, .45, .60, .75, .55)
    down_pose = (0.0, 1.0, .15, -.25, 0.0, 1.0, .15, -.25, 0.0)
    for index, frame in enumerate(frames):
        swing = leg_swing[index]
        for bone in arm.pose.bones:
            bone.rotation_euler = (0, 0, 0)
        arm.pose.bones['RightUpLeg'].rotation_euler.x = stride * swing
        arm.pose.bones['LeftUpLeg'].rotation_euler.x = -stride * swing
        arm.pose.bones['RightLeg'].rotation_euler.x = knee * right_knee[index]
        arm.pose.bones['LeftLeg'].rotation_euler.x = knee * left_knee[index]
        # Arms counter-swing against the opposite leg; elbows remain softly flexed.
        arm.pose.bones['RightArm'].rotation_euler.x = -stride * .78 * swing
        arm.pose.bones['LeftArm'].rotation_euler.x = stride * .78 * swing
        arm.pose.bones['RightForeArm'].rotation_euler.x = elbow * right_elbow[index]
        arm.pose.bones['LeftForeArm'].rotation_euler.x = elbow * left_elbow[index]
        # Small counter-rotation and breathing pitch keep the torso alive without sway.
        arm.pose.bones['Spine01'].rotation_euler.z = -torso * .58 * swing
        arm.pose.bones['Spine02'].rotation_euler.z = -torso * .42 * swing
        arm.pose.bones['Spine01'].rotation_euler.x = torso * .12 * down_pose[index]
        arm.pose.bones['Spine02'].rotation_euler.y = torso * .10 * swing
        # Feet roll only as animation metadata; the current ModelPart renderer ignores
        # foot translation and therefore cannot move the player or cause ground bounce.
        arm.pose.bones['RightFoot'].rotation_euler.x = -knee * .18 * right_knee[index]
        arm.pose.bones['LeftFoot'].rotation_euler.x = -knee * .18 * left_knee[index]
        # Root translation is intentionally locked to zero.
        arm.pose.bones['Hips'].location = (0, 0, 0)
        for bone in arm.pose.bones:
            bone.keyframe_insert(data_path='rotation_euler', frame=frame)
        arm.pose.bones['Hips'].keyframe_insert(data_path='location', frame=frame)
    # Blender's Bézier interpolation eases between the key poses. Export force-sampling
    # bakes the curve so the runtime never depends on Blender-only interpolation data.
    return action


def export_single(name, stride, knee, elbow, torso, file_name):
    arm = reset_and_rig()
    create_action(arm, name, stride, knee, elbow, torso)
    bpy.context.scene.frame_start, bpy.context.scene.frame_end = 1, 33
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(output / file_name), export_format='GLB', use_selection=True,
                              export_animations=True, export_force_sampling=True, export_frame_range=True,
                              export_nla_strips=False, export_animation_mode='ACTIONS')


export_single('Murim_Walk', math.radians(31), math.radians(29), math.radians(14), math.radians(4.5), 'murim_walk.glb')
export_single('Murim_Run', math.radians(42), math.radians(38), math.radians(26), math.radians(6.0), 'murim_run.glb')
export_single('Murim_Fast_Run', math.radians(50), math.radians(46), math.radians(32), math.radians(7.0), 'murim_fast_run.glb')

# Preserve a single Blender source containing all original actions.
arm = reset_and_rig()
create_action(arm, 'Murim_Walk', math.radians(31), math.radians(29), math.radians(14), math.radians(4.5))
create_action(arm, 'Murim_Run', math.radians(42), math.radians(38), math.radians(26), math.radians(6.0))
create_action(arm, 'Murim_Fast_Run', math.radians(50), math.radians(46), math.radians(32), math.radians(7.0))
bpy.ops.wm.save_as_mainfile(filepath=str(output / 'murim_articulated_locomotion.blend'))
print('MURIM_LOCOMOTION_CREATED', output)
