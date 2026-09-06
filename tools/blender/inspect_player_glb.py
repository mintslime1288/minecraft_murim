"""Read a GLB in Blender without altering the source asset.

Usage:
    blender --background --python inspect_player_glb.py -- <input.glb> <report.txt>
"""
import bpy
import pathlib
import sys


def arguments_after_separator():
    values = sys.argv
    return values[values.index("--") + 1:] if "--" in values else []


args = arguments_after_separator()
if len(args) != 2:
    raise RuntimeError("Expected: <input.glb> <report.txt>")

source = pathlib.Path(args[0]).resolve()
report = pathlib.Path(args[1]).resolve()
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(source))

lines = [f"source={source}"]
for obj in bpy.context.scene.objects:
    parent = obj.parent.name if obj.parent else "<none>"
    lines.append(
        f"object name={obj.name!r} type={obj.type} parent={parent!r} "
        f"location={tuple(round(v, 6) for v in obj.location)} "
        f"rotation={tuple(round(v, 6) for v in obj.rotation_euler)} "
        f"scale={tuple(round(v, 6) for v in obj.scale)}"
    )
    if obj.type == "ARMATURE":
        lines.append(f"  bones={len(obj.data.bones)} actions={len(bpy.data.actions)}")
        for bone in obj.data.bones:
            lines.append(f"  bone={bone.name!r} head={tuple(round(v, 6) for v in bone.head_local)} tail={tuple(round(v, 6) for v in bone.tail_local)}")
    if obj.type == "MESH":
        lines.append(f"  vertices={len(obj.data.vertices)} polygons={len(obj.data.polygons)}")

report.parent.mkdir(parents=True, exist_ok=True)
report.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("MURIM_PLAYER_GLB_INSPECTION_WRITTEN", report)
