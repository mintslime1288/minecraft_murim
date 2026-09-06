"""Create a non-destructive GLB candidate with the imported Armature scale baked.

The Meshy source is kept untouched. The result must still pass MurimRPG's skinning
bounds validator before it is eligible for packaging.

Usage:
    blender --background --python rebake_player_armature.py -- <source.glb> <output.glb> <output.blend>
"""
import bpy
import pathlib
import sys


def args_after_separator():
    return sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


args = args_after_separator()
if len(args) != 3:
    raise RuntimeError("Expected: <source.glb> <output.glb> <output.blend>")

source, output_glb, output_blend = map(lambda value: pathlib.Path(value).resolve(), args)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(source))

armatures = [item for item in bpy.context.scene.objects if item.type == "ARMATURE"]
if len(armatures) != 1:
    raise RuntimeError(f"Expected exactly one Armature, found {len(armatures)}")

armature = armatures[0]
if max(abs(armature.scale.x - 1.0), abs(armature.scale.y - 1.0), abs(armature.scale.z - 1.0)) < 0.0001:
    raise RuntimeError("Armature already has unit scale; refusing a no-op candidate")

# Apply only the object-scale conversion introduced by the source export. This bakes the
# 0.01 unit conversion into armature data while retaining the mesh's world-space size.
bpy.context.view_layer.objects.active = armature
armature.select_set(True)
bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
if max(abs(armature.scale.x - 1.0), abs(armature.scale.y - 1.0), abs(armature.scale.z - 1.0)) > 0.0001:
    raise RuntimeError("Blender did not apply the Armature scale")

output_glb.parent.mkdir(parents=True, exist_ok=True)
output_blend.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=str(output_blend))
bpy.ops.export_scene.gltf(filepath=str(output_glb), export_format="GLB", export_animations=True)
print("MURIM_PLAYER_GLB_REBAKED", output_glb)
