"""Create an original, neutral T-pose humanoid mesh suitable for Meshy auto-rig upload."""
import bpy
import pathlib
import sys


def args_after_separator():
    return sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


output = pathlib.Path(args_after_separator()[0]).resolve()
output.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)

material = bpy.data.materials.new("MurimRPG_Neutral_Clay")
material.diffuse_color = (0.42, 0.48, 0.56, 1.0)
parts = []


def box(name, location, scale):
    bpy.ops.mesh.primitive_cube_add(location=location)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(material)
    parts.append(obj)


# All measurements are metres. Feet rest at Z=0 and the top of the head is Z=1.8.
box("Head", (0, 0, 1.57), (.46, .40, .46))
box("Torso", (0, 0, 1.20), (.52, .28, .56))
box("Pelvis", (0, 0, .88), (.46, .27, .14))
box("RightUpperArm", (-.43, 0, 1.38), (.34, .20, .20))
box("RightLowerArm", (-.76, 0, 1.38), (.32, .18, .18))
box("RightHand", (-.98, 0, 1.38), (.13, .18, .18))
box("LeftUpperArm", (.43, 0, 1.38), (.34, .20, .20))
box("LeftLowerArm", (.76, 0, 1.38), (.32, .18, .18))
box("LeftHand", (.98, 0, 1.38), (.13, .18, .18))
box("RightUpperLeg", (-.13, 0, .63), (.22, .23, .50))
box("RightLowerLeg", (-.13, 0, .22), (.20, .21, .34))
box("RightFoot", (-.13, -.08, .06), (.21, .34, .12))
box("LeftUpperLeg", (.13, 0, .63), (.22, .23, .50))
box("LeftLowerLeg", (.13, 0, .22), (.20, .21, .34))
box("LeftFoot", (.13, -.08, .06), (.21, .34, .12))

# Join disconnected but touching sections into one clean upload mesh. This gives Meshy a
# clear human silhouette while preserving visible elbow and knee locations for auto-rigging.
bpy.ops.object.select_all(action='DESELECT')
for part in parts:
    part.select_set(True)
bpy.context.view_layer.objects.active = parts[0]
bpy.ops.object.join()
character = bpy.context.object
character.name = "MurimRPG_Original_TPose_Character"

# Put origin at ground centre, apply transforms and write source + upload asset.
bpy.ops.object.origin_set(type='ORIGIN_CURSOR', center='MEDIAN')
bpy.context.scene.frame_start = 1
bpy.context.scene.frame_end = 1
bpy.ops.wm.save_as_mainfile(filepath=str(output / "murim_player_tpose_for_meshy.blend"))
bpy.ops.export_scene.gltf(filepath=str(output / "murim_player_tpose_for_meshy.glb"), export_format='GLB',
                          use_selection=True, export_materials='EXPORT', export_animations=False)
print("MURIM_MESHY_UPLOAD_CHARACTER_CREATED", output)
