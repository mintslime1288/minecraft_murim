"""Build MurimRPG's original, editable Blender animation source file.

This file creates only new MurimRPG geometry, a humanoid armature, and original
keyframes. It imports no external rigs, meshes, actions, textures, or animation
data. Run with Blender 5.2+: blender --background --python build_murim_motion_studio.py
"""
from pathlib import Path
import math
import bpy
from mathutils import Vector

OUT = Path(__file__).with_name("murim_motion_studio_v1.blend")
PREVIEW = Path(__file__).with_name("murim_walk_preview.png")
FPS = 24


def deg(value):
    return math.radians(value)


def clean_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for data in (bpy.data.armatures, bpy.data.meshes, bpy.data.materials, bpy.data.cameras, bpy.data.lights):
        for block in list(data):
            if block.users == 0:
                data.remove(block)


def material(name, color):
    value = bpy.data.materials.new(name)
    value.diffuse_color = (*color, 1.0)
    value.use_nodes = True
    value.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (*color, 1.0)
    value.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.82
    return value


def create_materials():
    return {
        "skin": material("Murim Skin Placeholder", (0.72, 0.50, 0.34)),
        "cloth": material("Murim Indigo Cloth", (0.055, 0.09, 0.22)),
        "pants": material("Murim Charcoal Pants", (0.075, 0.08, 0.11)),
        "boot": material("Murim Leather Boots", (0.18, 0.09, 0.035)),
        "accent": material("Murim Gold Accent", (0.55, 0.34, 0.06)),
    }


def bone(armature, name, head, tail, parent=None):
    item = armature.edit_bones.new(name)
    item.head = head
    item.tail = tail
    if parent:
        item.parent = armature.edit_bones[parent]
        item.use_connect = False
    return item


def create_armature():
    data = bpy.data.armatures.new("MURIM_HUMANOID_RIG")
    rig = bpy.data.objects.new("MURIM_HUMANOID_RIG", data)
    bpy.context.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    bone(data, "root", (0, 0, 0), (0, 0, 0.45))
    bone(data, "hips", (0, 0, 0.45), (0, 0, 1.00), "root")
    bone(data, "spine", (0, 0, 1.00), (0, 0, 1.55), "hips")
    bone(data, "chest", (0, 0, 1.55), (0, 0, 1.88), "spine")
    bone(data, "neck", (0, 0, 1.88), (0, 0, 2.02), "chest")
    bone(data, "head", (0, 0, 2.02), (0, 0, 2.42), "neck")
    for side, x in (("L", 0.34), ("R", -0.34)):
        bone(data, f"upper_arm.{side}", (x, 0, 1.79), (x * 1.85, 0, 1.48), "chest")
        bone(data, f"lower_arm.{side}", (x * 1.85, 0, 1.48), (x * 2.15, 0.015, 1.12), f"upper_arm.{side}")
        bone(data, f"hand.{side}", (x * 2.15, 0.015, 1.12), (x * 2.30, 0.02, 0.98), f"lower_arm.{side}")
        bone(data, f"thigh.{side}", (x * 0.42, 0, 0.93), (x * 0.45, 0, 0.42), "hips")
        bone(data, f"shin.{side}", (x * 0.45, 0, 0.42), (x * 0.47, 0.015, 0.06), f"thigh.{side}")
        bone(data, f"foot.{side}", (x * 0.47, 0.015, 0.06), (x * 0.47, -0.22, 0.04), f"shin.{side}")
    bpy.ops.object.mode_set(mode="POSE")
    for pose_bone in rig.pose.bones:
        pose_bone.rotation_mode = "XYZ"
    bpy.ops.object.mode_set(mode="OBJECT")
    rig.show_in_front = True
    return rig


def box(name, dimensions, location, material_value, rig, parent_bone):
    bpy.ops.mesh.primitive_cube_add(location=location)
    result = bpy.context.object
    result.name = name
    result.dimensions = dimensions
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    result.data.materials.append(material_value)
    bevel = result.modifiers.new("Soft block edges", "BEVEL")
    bevel.width = 0.018
    bevel.segments = 2
    # Keep a world-space rest transform, then convert it into the selected bone's
    # local space. Blender otherwise evaluates the just-assigned bone transform
    # twice when a pose action becomes active.
    world_matrix = result.matrix_world.copy()
    result.parent = rig
    result.parent_type = "BONE"
    result.parent_bone = parent_bone
    # Preserve the reference block's world-space rest position relative to the
    # actual bone, not only the armature object. Without this, every child is
    # offset by its parent's transform and appears to explode away from the rig.
    result.matrix_parent_inverse = (rig.matrix_world @ rig.pose.bones[parent_bone].matrix).inverted()
    result.matrix_world = world_matrix
    return result


def create_body(rig, materials):
    # Each block is original reference geometry. It makes pose editing obvious;
    # Minecraft itself still uses the player's skin rather than this mesh.
    box("Torso", (0.68, 0.34, 0.72), (0, 0, 1.53), materials["cloth"], rig, "spine")
    box("Head", (0.54, 0.48, 0.52), (0, 0, 2.19), materials["skin"], rig, "head")
    for side, x in (("L", 1), ("R", -1)):
        box(f"UpperArm.{side}", (0.25, 0.27, 0.42), (x * 0.48, 0, 1.61), materials["cloth"], rig, f"upper_arm.{side}")
        box(f"LowerArm.{side}", (0.23, 0.25, 0.38), (x * 0.65, 0, 1.25), materials["cloth"], rig, f"lower_arm.{side}")
        box(f"Hand.{side}", (0.24, 0.24, 0.18), (x * 0.70, 0, 1.03), materials["skin"], rig, f"hand.{side}")
        box(f"Thigh.{side}", (0.28, 0.30, 0.48), (x * 0.16, 0, 0.69), materials["pants"], rig, f"thigh.{side}")
        box(f"Shin.{side}", (0.26, 0.28, 0.42), (x * 0.17, 0, 0.27), materials["pants"], rig, f"shin.{side}")
        box(f"Foot.{side}", (0.28, 0.48, 0.18), (x * 0.17, -0.13, 0.08), materials["boot"], rig, f"foot.{side}")
    belt = box("Belt", (0.71, 0.37, 0.08), (0, 0, 1.15), materials["accent"], rig, "hips")
    return belt


def set_pose(rig, frame, values):
    bpy.context.scene.frame_set(frame)
    for name, rotate in values.items():
        item = rig.pose.bones[name]
        item.rotation_mode = "XYZ"
        item.rotation_euler = tuple(deg(value) for value in rotate)
        item.keyframe_insert(data_path="rotation_euler", frame=frame)


def action(rig, name, frames, marks=()):
    data = bpy.data.actions.new(name)
    rig.animation_data_create()
    rig.animation_data.action = data
    for frame, pose in frames:
        set_pose(rig, frame, pose)
    for marker_frame, marker_name in marks:
        data.pose_markers.new(marker_name).frame = marker_frame
    # Blender 5.2 stores action curves behind slots/layers. Keep Blender's
    # default Bezier interpolation here so this source is valid across 5.x.
    return data


def mirrored_leg_pose(left_leg, right_leg, left_knee=0, right_knee=0, left_arm=0, right_arm=0, torso=0, hips=0):
    return {
        "thigh.L": (left_leg, 0, 0), "shin.L": (left_knee, 0, 0),
        "thigh.R": (right_leg, 0, 0), "shin.R": (right_knee, 0, 0),
        "upper_arm.L": (left_arm, 0, 0), "lower_arm.L": (-abs(left_arm) * .18, 0, 0),
        "upper_arm.R": (right_arm, 0, 0), "lower_arm.R": (-abs(right_arm) * .18, 0, 0),
        "spine": (torso, 0, 0), "chest": (torso * .42, 0, 0), "hips": (hips, 0, 0),
    }


def create_actions(rig):
    # Original action layout: contact, down, passing, up, mirrored contact.
    # Root never translates, so game conversion cannot introduce vertical bouncing.
    action(rig, "MURIM_IDLE", [
        (1, {"spine": (1.0, 0, 0), "chest": (0.5, 0, 0), "upper_arm.L": (-2, 0, 0), "upper_arm.R": (-2, 0, 0)}),
        (24, {"spine": (-1.0, 0, 0), "chest": (-0.5, 0, 0), "upper_arm.L": (-1, 0, 0), "upper_arm.R": (-1, 0, 0)}),
        (48, {"spine": (1.0, 0, 0), "chest": (0.5, 0, 0), "upper_arm.L": (-2, 0, 0), "upper_arm.R": (-2, 0, 0)}),
    ])
    action(rig, "MURIM_WALK", [
        (1, mirrored_leg_pose(19, -19, 3, 10, -15, 15, torso=1.2, hips=-1.5)),
        (7, mirrored_leg_pose(7, -7, 12, 4, -6, 6, torso=0.5, hips=-0.6)),
        (13, mirrored_leg_pose(-19, 19, 10, 3, 15, -15, torso=-1.2, hips=1.5)),
        (19, mirrored_leg_pose(-7, 7, 4, 12, 6, -6, torso=-0.5, hips=0.6)),
        (25, mirrored_leg_pose(19, -19, 3, 10, -15, 15, torso=1.2, hips=-1.5)),
    ], ((1, "LEFT_CONTACT"), (7, "DOWN"), (13, "RIGHT_CONTACT"), (19, "PASSING"), (25, "LOOP")))
    action(rig, "MURIM_RUN", [
        (1, mirrored_leg_pose(31, -26, 4, 25, -31, 31, torso=-8.0, hips=-3.0)),
        (5, mirrored_leg_pose(11, -10, 33, 8, -12, 12, torso=-9.0, hips=-1.0)),
        (9, mirrored_leg_pose(-26, 31, 25, 4, 31, -31, torso=-8.0, hips=3.0)),
        (13, mirrored_leg_pose(-10, 11, 8, 33, 12, -12, torso=-9.0, hips=1.0)),
        (17, mirrored_leg_pose(31, -26, 4, 25, -31, 31, torso=-8.0, hips=-3.0)),
    ], ((1, "LEFT_CONTACT"), (5, "LEFT_PASSING"), (9, "RIGHT_CONTACT"), (13, "RIGHT_PASSING"), (17, "LOOP")))
    action(rig, "MURIM_JUMP", [
        (1, mirrored_leg_pose(-8, -8, 20, 20, 10, 10, torso=4.0, hips=-5.0)),
        (6, mirrored_leg_pose(13, 13, 37, 37, -22, -22, torso=-5.0, hips=0.0)),
        (13, mirrored_leg_pose(20, 20, 48, 48, -14, -14, torso=-3.0, hips=0.0)),
        (19, mirrored_leg_pose(-5, -5, 26, 26, 8, 8, torso=3.0, hips=-3.0)),
        (25, mirrored_leg_pose(0, 0, 0, 0, 0, 0)),
    ], ((1, "PREPARE"), (6, "TAKEOFF"), (13, "APEX"), (19, "LAND"), (25, "RECOVER")))
    action(rig, "MURIM_PUNCH", [
        (1, {"spine": (0, 0, 0), "chest": (0, 0, 0), "upper_arm.R": (0, 0, 0), "lower_arm.R": (0, 0, 0)}),
        (5, {"spine": (0, 0, 7), "chest": (0, 0, 11), "upper_arm.R": (-34, 0, -18), "lower_arm.R": (-52, 0, 0)}),
        (8, {"spine": (0, 0, -4), "chest": (0, 0, -7), "upper_arm.R": (51, 0, 0), "lower_arm.R": (-8, 0, 0)}),
        (14, {"spine": (0, 0, 0), "chest": (0, 0, 0), "upper_arm.R": (0, 0, 0), "lower_arm.R": (0, 0, 0)}),
    ], ((1, "READY"), (5, "ANTICIPATION"), (8, "IMPACT"), (14, "RECOVER")))


def create_stage():
    bpy.ops.mesh.primitive_plane_add(size=12, location=(0, 0, 0))
    floor = bpy.context.object
    floor.name = "Preview Floor"
    floor.data.materials.append(material("Preview Floor", (0.07, 0.085, 0.11)))
    bpy.ops.object.light_add(type="AREA", location=(3.5, -4.0, 5.0))
    bpy.context.object.data.energy = 900
    bpy.context.object.data.shape = "DISK"
    bpy.context.object.data.size = 5
    bpy.ops.object.light_add(type="AREA", location=(-4.0, -1.5, 3.0))
    bpy.context.object.data.energy = 450
    bpy.context.object.data.color = (0.22, 0.35, 1.0)
    bpy.context.object.data.size = 4
    bpy.ops.object.camera_add(location=(4.4, -7.5, 3.2), rotation=(math.radians(73), 0, math.radians(31)))
    camera = bpy.context.object
    bpy.context.scene.camera = camera
    constraint = camera.constraints.new(type="TRACK_TO")
    constraint.target = bpy.data.objects["MURIM_HUMANOID_RIG"]
    constraint.track_axis = "TRACK_NEGATIVE_Z"
    constraint.up_axis = "UP_Y"


def main():
    clean_scene()
    scene = bpy.context.scene
    # Blender 5.2 exposes the Eevee renderer under this stable enum name.
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 900
    scene.render.resolution_y = 900
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.render.fps = FPS
    scene.frame_start = 1
    scene.frame_end = 25
    scene["murim_license"] = "Original MurimRPG source geometry and keyframes; no third-party animation data imported."
    scene["murim_game_rule"] = "Root bone stays at zero translation. Only rotations are intended for game conversion."
    rig = create_armature()
    create_body(rig, create_materials())
    create_actions(rig)
    create_stage()
    rig.animation_data.action = bpy.data.actions["MURIM_WALK"]
    scene.frame_set(7)
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
    scene.render.filepath = str(PREVIEW)
    bpy.ops.render.render(write_still=True)
    print(f"MURIM_MOTION_STUDIO_BLEND={OUT}")
    print(f"MURIM_MOTION_STUDIO_PREVIEW={PREVIEW}")


if __name__ == "__main__":
    main()
