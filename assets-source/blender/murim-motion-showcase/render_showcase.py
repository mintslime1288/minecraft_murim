"""Render a concept-only wuxia motion reel from the project's editable player rig.

Usage: blender -b murim_player_rig_v1_editable.blend --python render_showcase.py -- OUT_DIR [--still FRAME]
This script does not export gameplay animation or modify the source .blend.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

FPS = 24
SECONDS_PER_MOTION = 3
MOTIONS = ("idle", "walk", "run", "jump", "crouch", "punch")


def color(name, rgb):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*rgb, 1)
    return mat


def cube(name, center, size, mat, rig=None, bone=None, bevel=0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=center)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(mat)
    if bevel:
        mod = obj.modifiers.new("Subtle cut corners", "BEVEL")
        mod.width = bevel
        mod.segments = 1
    if rig and bone:
        world = obj.matrix_world.copy()
        obj.parent = rig
        obj.parent_type = 'BONE'
        obj.parent_bone = bone
        obj.matrix_parent_inverse = (rig.matrix_world @ rig.pose.bones[bone].matrix).inverted()
        obj.matrix_world = world
    return obj


def tube(name, centers, widths, depths, bone_names, mats, side_material):
    """A single continuous rectangular mesh over an elbow or knee.

    Ring weights blend across adjacent bones, so every face stays connected.
    """
    verts, faces = [], []
    for (x, y, z), width, depth in zip(centers, widths, depths):
        verts.extend([(x-width/2, y-depth/2, z), (x+width/2, y-depth/2, z),
                      (x+width/2, y+depth/2, z), (x-width/2, y+depth/2, z)])
    for i in range(len(centers)-1):
        for j in range(4):
            faces.append((4*i+j, 4*i+(j+1)%4, 4*(i+1)+(j+1)%4, 4*(i+1)+j))
    faces.extend([(3,2,1,0), tuple(4*(len(centers)-1)+j for j in range(4))])
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    for mat in mats:
        mesh.materials.append(mat)
    for poly in mesh.polygons:
        poly.material_index = 0 if poly.index % 4 in (0, 2) else side_material
    top = obj.vertex_groups.new(name=bone_names[0])
    bottom = obj.vertex_groups.new(name=bone_names[1])
    for i in range(len(centers)):
        # Blend both sides of the joint. No independently moving upper/lower box ends.
        a = (1, .96, .70, .50, .30, .04, 0)[i]
        top.add(list(range(4*i,4*i+4)), a, 'REPLACE') if a else None
        bottom.add(list(range(4*i,4*i+4)), 1-a, 'REPLACE') if a < 1 else None
    mod = obj.modifiers.new("Continuous joint deformation", 'ARMATURE')
    mod.object = RIG
    obj.parent = RIG
    return obj


def stroke(name, points, mat, radius):
    data = bpy.data.curves.new(name, 'CURVE')
    data.dimensions = '3D'
    data.resolution_u = 2
    data.bevel_depth = radius
    data.bevel_resolution = 1
    spline = data.splines.new('POLY')
    spline.points.add(len(points)-1)
    for item, position in zip(spline.points, points):
        item.co = (*position, 1)
    obj = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(obj)
    data.materials.append(mat)
    obj.hide_render = True
    return obj


def setup():
    global RIG, EFFECTS
    RIG = bpy.data.objects['MURIM_HUMANOID_RIG']
    RIG.animation_data_clear()
    for bone in RIG.pose.bones:
        bone.rotation_mode = 'XYZ'
        bone.rotation_euler = (0,0,0)
    for obj in list(bpy.data.objects):
        if obj.type == 'MESH':
            bpy.data.objects.remove(obj, do_unlink=True)
    ink = color('ink black', (.025,.035,.055))
    robe = color('deep indigo robe', (.065,.13,.23))
    robe_light = color('folds in robe', (.12,.22,.32))
    skin = color('skin', (.70,.49,.32))
    boot = color('black cloth shoes', (.09,.075,.07))
    gold = color('antique gold', (.55,.38,.14))
    paper = color('warm paper', (.74,.68,.54))
    stone = color('blue-black stone', (.095,.13,.18))

    cube('Tunic, continuous body', (0,0,1.48), (.71,.40,.79), robe, RIG, 'spine', .025)
    cube('Collar', (0,-.015,1.875), (.58,.44,.10), gold, RIG, 'chest', .012)
    cube('Head', (0,0,2.20), (.54,.48,.52), skin, RIG, 'head', .012)
    cube('Hair', (0,.015,2.40), (.57,.50,.14), ink, RIG, 'head', .012)
    cube('Top knot', (0,.02,2.52), (.20,.20,.18), ink, RIG, 'head', .01)
    for s in (-1,1):
        cube('Eye '+str(s),(s*.13,-.246,2.22),(.065,.014,.045),ink,RIG,'head')
        cube('Brow '+str(s),(s*.13,-.253,2.30),(.12,.014,.025),ink,RIG,'head')
    cube('Hair fringe',(0,-.25,2.375),(.34,.035,.07),ink,RIG,'head')
    for s, side in ((1,'L'),(-1,'R')):
        x = s*.34
        arm_centers = [(x,0,1.79),(s*.44,0,1.69),(s*.54,0,1.58),
                       (s*.63,0,1.48),(s*.68,.005,1.36),(s*.72,.01,1.23),
                       (s*.73,.015,1.13)]
        tube('Unbroken sleeve '+side, arm_centers,
             [.30,.29,.27,.26,.25,.235,.23], [.32,.31,.30,.29,.28,.27,.26],
             ('upper_arm.'+side,'lower_arm.'+side), (robe,robe_light), 1)
        cube('Cuff '+side,(s*.73,.015,1.115),(.25,.27,.07),gold,RIG,'lower_arm.'+side,.006)
        cube('Fist '+side,(s*.73,.02,1.04),(.23,.24,.16),skin,RIG,'hand.'+side,.012)
        leg_centers = [(s*.145,0,.97),(s*.15,0,.82),(s*.155,0,.65),
                       (s*.16,0,.46),(s*.165,0,.35),(s*.17,.005,.21),
                       (s*.17,.01,.10)]
        tube('Unbroken trouser '+side,leg_centers,
             [.32,.31,.30,.29,.28,.275,.27], [.35,.34,.33,.32,.31,.30,.29],
             ('thigh.'+side,'shin.'+side),(robe,robe_light),1)
        cube('Boot '+side,(s*.17,-.11,.09),(.29,.48,.18),boot,RIG,'foot.'+side,.016)
    cube('Sash', (0,-.01,1.15),(.74,.44,.095),gold,RIG,'hips',.008)
    for s in (-1,1):
        cube('Tunic side panel '+str(s),(s*.25,0,1.06),(.22,.39,.27),robe,RIG,'hips',.01)
    # Visual ink-stage, separate from the gameplay world.
    cube('Dais', (0,0,-.15), (4.7,3.5,.26), stone, bevel=.03)
    for x in (-1.8,1.8):
        for y in (-1.25,1.25):
            cube('Corner inlay',(x,y,-.012),(.35,.08,.012),gold)
    EFFECTS = {}
    EFFECTS['jump'] = stroke('Qi ring at takeoff',
        [(.72*math.cos(2*math.pi*i/48),.72*math.sin(2*math.pi*i/48),.015)
         for i in range(49)],gold,.011)
    EFFECTS['punch'] = []
    for index, height in enumerate((1.48,1.67,1.85)):
        EFFECTS['punch'].append(stroke('Fist wind '+str(index),
            [(-.03,-.68,height),(-.06,-.92,height+.025),
             (-.10,-1.20,height+.07),(-.14,-1.48,height+.11)],
            gold,.009 if index != 1 else .014))
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_WORKBENCH'
    scene.display.shading.light = 'STUDIO'
    scene.display.shading.studio_light = 'paint.sl'
    scene.display.shading.color_type = 'MATERIAL'
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    scene.display.shading.cavity_type = 'BOTH'
    scene.display.shading.curvature_ridge_factor = 1.3
    scene.display.shading.curvature_valley_factor = 1.0
    scene.render.film_transparent = False
    scene.world.color = (.045,.06,.08)
    scene.render.resolution_x = 960
    scene.render.resolution_y = 540
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.fps = FPS
    camera_data = bpy.data.cameras.new('Showcase camera')
    camera = bpy.data.objects.new('Showcase camera', camera_data)
    bpy.context.collection.objects.link(camera)
    camera.location = (4.6,-6.3,2.7)
    direction = Vector((0,0,1.22))-camera.location
    camera.rotation_euler = direction.to_track_quat('-Z','Y').to_euler()
    camera_data.type = 'ORTHO'
    camera_data.ortho_scale = 7.9
    scene.camera = camera
    RIG.hide_render = False


def ease(a):
    return a*a*(3-2*a)


def pose_for(motion, u):
    p = {}
    root_z = 0
    if motion == 'idle':
        b = math.sin(2*math.pi*u)
        p.update({'spine':(1.0*b,0,-4), 'chest':(-.5*b,0,2),
                  'upper_arm.L':(-8,0,-5), 'upper_arm.R':(-8,0,5),
                  'lower_arm.L':(-12,0,0),'lower_arm.R':(-12,0,0)})
    elif motion in ('walk','run'):
        cyc = 3*u if motion=='walk' else 4.5*u
        s = math.sin(2*math.pi*cyc)
        c = math.cos(2*math.pi*cyc)
        amp = 23 if motion=='walk' else 35
        lift = max(0,c)
        p.update({'thigh.L':(amp*s,0,0),'thigh.R':(-amp*s,0,0),
                  'shin.L':(6+lift*(13 if motion=='walk' else 28),0,0),
                  'shin.R':(6+max(0,-c)*(13 if motion=='walk' else 28),0,0),
                  'upper_arm.L':(-amp*.68*s-8,0,0),
                  'upper_arm.R':(amp*.68*s-8,0,0),
                  'lower_arm.L':(-14 if motion=='walk' else -33,0,0),
                  'lower_arm.R':(-14 if motion=='walk' else -33,0,0),
                  'spine':(-1.2 if motion=='walk' else -3.5,0,2.4*s),
                  'chest':(0,0,-2*s),'hips':(0,0,-2.5*s)})
        root_z = (.018 if motion=='walk' else .035)*(1+math.cos(4*math.pi*cyc))
    elif motion == 'jump':
        flight = math.sin(math.pi*min(1,max(0,(u-.13)/.70)))
        root_z = .73*flight
        tuck = math.sin(math.pi*min(1,max(0,(u-.21)/.58)))
        p.update({'thigh.L':(13+18*tuck,0,0),'thigh.R':(13+18*tuck,0,0),
                  'shin.L':(25+27*tuck,0,0),'shin.R':(25+27*tuck,0,0),
                  'upper_arm.L':(-25-17*flight,0,-8),
                  'upper_arm.R':(-25-17*flight,0,8),
                  'spine':(-3*flight,0,0)})
    elif motion == 'crouch':
        c = ease(min(1,u/.30, (1-u)/.25))
        root_z = -.18*c
        p.update({'thigh.L':(-56*c,0,-6*c),'thigh.R':(-56*c,0,6*c),
                  'shin.L':(102*c,0,0),'shin.R':(102*c,0,0),
                  'spine':(8*c,0,0),'upper_arm.L':(-23*c,0,0),
                  'upper_arm.R':(-23*c,0,0),'lower_arm.L':(-18*c,0,0),
                  'lower_arm.R':(-18*c,0,0)})
    else: # grounded, bare-handed wuxia strike with wind-up and recovery
        def bell(center, width):
            return max(0,1-abs(u-center)/width)
        wind = ease(bell(.28,.28))
        strike = ease(bell(.48,.18))
        p.update({'upper_arm.R':(-22*wind-70*strike,0,-30*strike),
                  'lower_arm.R':(-61*wind-70*strike,0,0),
                  'upper_arm.L':(-28*wind,0,12*wind),
                  'lower_arm.L':(-48*wind,0,0),
                  'spine':(-5*wind+4*strike,0,12*wind-15*strike),
                  'chest':(0,0,8*wind-12*strike),
                  'thigh.R':(-12*wind,0,0),'thigh.L':(10*wind,0,0),
                  'shin.R':(15*wind,0,0)})
        root_z = -.04*wind
    return p, root_z


def frame_pose(frame):
    total = SECONDS_PER_MOTION*FPS
    motion_index = min(len(MOTIONS)-1,(frame-1)//total)
    local = (frame-1)%total
    u = local/(total-1)
    p,z = pose_for(MOTIONS[motion_index],u)
    EFFECTS['jump'].hide_render = not (MOTIONS[motion_index] == 'jump' and .16 < u < .87)
    for obj in EFFECTS['punch']:
        obj.hide_render = not (MOTIONS[motion_index] == 'punch' and .39 < u < .58)
    camera = bpy.context.scene.camera
    camera.location = (7.0,-2.5,2.7) if MOTIONS[motion_index] == 'punch' else (4.6,-6.3,2.7)
    camera.rotation_euler = (Vector((0,0,1.22))-camera.location).to_track_quat('-Z','Y').to_euler()
    # Blend each section gently from/to the same neutral stance.
    envelope = min(1,ease(min(1,u/.13)),ease(min(1,(1-u)/.13)))
    for bone in RIG.pose.bones:
        angles = p.get(bone.name,(0,0,0))
        bone.rotation_euler = tuple(math.radians(v*envelope) for v in angles)
    RIG.location.z = z*envelope
    bpy.context.view_layer.update()
    if MOTIONS[motion_index] == 'crouch':
        # Keep shoes on the dais even at maximum knee bend.
        low = min((obj.matrix_world @ Vector(corner)).z
                  for obj in bpy.data.objects if obj.name.startswith('Boot ')
                  for corner in obj.bound_box)
        if low < 0:
            RIG.location.z -= low
            bpy.context.view_layer.update()


def main():
    args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    out = Path(args[0]) if args else Path.cwd()/'motion_frames'
    out.mkdir(parents=True,exist_ok=True)
    setup()
    if '--still' in args:
        frames = [int(args[args.index('--still')+1])]
    else:
        frames = range(1,len(MOTIONS)*SECONDS_PER_MOTION*FPS+1)
    for frame in frames:
        bpy.context.scene.frame_set(frame)
        frame_pose(frame)
        bpy.context.scene.render.filepath = str(out/f'{frame:04d}.png')
        bpy.ops.render.render(write_still=True)
        if frame % 24 == 0 or '--still' in args:
            print(f'RENDERED {frame}/{len(MOTIONS)*SECONDS_PER_MOTION*FPS}',flush=True)


if __name__ == '__main__':
    main()
