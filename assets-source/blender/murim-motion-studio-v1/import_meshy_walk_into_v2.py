"""Create an editable Murim v2 walk action from a user-provided Meshy GLB.

The user-provided source is imported only into the generated local .blend as a
hidden reference. This script does not copy the GLB into the repository or the
game. The v1 Murim source is never overwritten.
"""
from pathlib import Path
import sys
import math
import bpy

HERE = Path(__file__).resolve().parent
V1 = HERE / "murim_motion_studio_v1.blend"
V2 = HERE / "murim_motion_studio_v3_meshy_walk_editable.blend"
PREVIEW = HERE / "murim_meshy_walk_delta_retarget_preview.png"
SOURCE = Path(sys.argv[-1]).resolve()

MAPPING = {
    "Hips": "hips", "Spine": "spine", "Spine01": "chest",
    "neck": "neck", "Head": "head",
    "LeftUpLeg": "thigh.L", "LeftLeg": "shin.L", "LeftFoot": "foot.L",
    "RightUpLeg": "thigh.R", "RightLeg": "shin.R", "RightFoot": "foot.R",
    "LeftArm": "upper_arm.L", "LeftForeArm": "lower_arm.L", "LeftHand": "hand.L",
    "RightArm": "upper_arm.R", "RightForeArm": "lower_arm.R", "RightHand": "hand.R",
}


def imported_armature(before):
    result = [obj for obj in bpy.context.scene.objects if obj.type == "ARMATURE" and obj.name not in before]
    if len(result) != 1:
        raise RuntimeError(f"Expected one imported armature, found {[obj.name for obj in result]}")
    return result[0]


def move_to_reference_collection(objects):
    collection = bpy.data.collections.get("MESHY_USER_PROVIDED_REFERENCE")
    if collection is None:
        collection = bpy.data.collections.new("MESHY_USER_PROVIDED_REFERENCE")
        bpy.context.scene.collection.children.link(collection)
    for obj in objects:
        for owner in list(obj.users_collection):
            owner.objects.unlink(obj)
        collection.objects.link(obj)
    collection.hide_render = True
    collection.hide_viewport = True
    return collection


def make_editable_walk(source_rig, target_rig):
    if source_rig.animation_data is None or source_rig.animation_data.action is None:
        raise RuntimeError("Meshy GLB has no active walking action")
    source_action = source_rig.animation_data.action
    start, end = source_action.frame_range
    action = bpy.data.actions.new("MURIM_MESHY_WALK_EDITABLE")
    target_rig.animation_data_create()
    target_rig.animation_data.action = action
    target_rig["murim_motion_source"] = str(SOURCE)
    target_rig["murim_motion_license"] = "User-provided Meshy source; license review required before redistribution."
    target_rig["murim_root_motion"] = "discarded: root location is never sampled into this action"

    # Meshy and Murim rigs have different rest-axis orientations. First capture
    # the Meshy contact-frame rotations, then sample only the local change from
    # that pose. This avoids applying Meshy's bind orientation as a false elbow,
    # knee, or spine twist on the Murim authoring rig.
    start_whole = math.floor(start)
    bpy.context.scene.frame_set(start_whole, subframe=start - start_whole)
    base_rotations = {
        source_name: source_rig.pose.bones[source_name].rotation_quaternion.copy()
        for source_name in MAPPING if source_rig.pose.bones.get(source_name) is not None
    }
    for target_name in set(MAPPING.values()):
        target_bone = target_rig.pose.bones.get(target_name)
        if target_bone is not None:
            target_bone.rotation_mode = "QUATERNION"
            target_bone.rotation_quaternion = (1.0, 0.0, 0.0, 0.0)

    # Sample the full source curve at an author-friendly 25-frame loop. We copy
    # only local rotational deltas; root translation is deliberately discarded.
    for target_frame in range(1, 26):
        source_frame = start + (end - start) * (target_frame - 1) / 24.0
        whole_frame = math.floor(source_frame)
        bpy.context.scene.frame_set(whole_frame, subframe=source_frame - whole_frame)
        for source_name, target_name in MAPPING.items():
            source_bone = source_rig.pose.bones.get(source_name)
            target_bone = target_rig.pose.bones.get(target_name)
            if source_bone is None or target_bone is None:
                continue
            target_bone.rotation_mode = "QUATERNION"
            target_bone.rotation_quaternion = base_rotations[source_name].inverted() @ source_bone.rotation_quaternion
            target_bone.keyframe_insert(data_path="rotation_quaternion", frame=target_frame)
    for frame, label in ((1, "LEFT_CONTACT"), (7, "DOWN"), (13, "RIGHT_CONTACT"), (19, "PASSING"), (25, "LOOP")):
        action.pose_markers.new(label).frame = frame
    return action


def main():
    if not V1.is_file():
        raise RuntimeError(f"Missing Murim v1 source: {V1}")
    if not SOURCE.is_file():
        raise RuntimeError(f"Missing user-provided Meshy source: {SOURCE}")
    bpy.ops.wm.open_mainfile(filepath=str(V1))
    target = bpy.data.objects.get("MURIM_HUMANOID_RIG")
    if target is None:
        raise RuntimeError("MURIM_HUMANOID_RIG is missing from v1")
    before = set(bpy.context.scene.objects.keys())
    bpy.ops.import_scene.gltf(filepath=str(SOURCE))
    source = imported_armature(before)
    imported = [obj for obj in bpy.context.scene.objects if obj.name not in before]
    source.name = "MESHY_WALK_SOURCE_ARMATURE"
    source["source_file"] = str(SOURCE)
    source["license_status"] = "USER_PROVIDED_LICENSE_PENDING"
    move_to_reference_collection(imported)
    action = make_editable_walk(source, target)
    bpy.context.scene.frame_start = 1
    bpy.context.scene.frame_end = 25
    target.animation_data.action = action
    bpy.context.scene.frame_set(7)
    bpy.ops.wm.save_as_mainfile(filepath=str(V2))
    bpy.context.scene.render.filepath = str(PREVIEW)
    bpy.ops.render.render(write_still=True)
    print(f"MURIM_EDITABLE_ACTION={action.name}")
    print(f"MURIM_V2_BLEND={V2}")
    print(f"MURIM_V2_PREVIEW={PREVIEW}")


if __name__ == "__main__":
    main()
