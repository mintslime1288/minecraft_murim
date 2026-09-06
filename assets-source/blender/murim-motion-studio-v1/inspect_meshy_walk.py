"""Read-only Blender inspection of the user-provided Meshy walking GLB."""
import sys
from pathlib import Path
import bpy

source = Path(sys.argv[-1])
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(source))
print(f"SOURCE={source}")
for obj in bpy.context.scene.objects:
    if obj.type == "ARMATURE":
        print(f"ARMATURE={obj.name}")
        print("BONES=" + ",".join(bone.name for bone in obj.data.bones))
        if obj.animation_data and obj.animation_data.action:
            print(f"ACTIVE_ACTION={obj.animation_data.action.name}")
for action in bpy.data.actions:
    print(f"ACTION={action.name} FRAME_RANGE={tuple(round(v, 3) for v in action.frame_range)}")
