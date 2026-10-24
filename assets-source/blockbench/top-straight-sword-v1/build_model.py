"""Build Blockbench and Minecraft Java item models of ONLY the top sword and sheath.

Run inside Blender 5.2+:
  blender -b --python build_model.py -- --reference path/to/user-image.png

The user image is sampled into two straightened texture strips. No other
reference objects are incorporated, and the source image is never modified.
"""
import argparse
import base64
import hashlib
import json
import math
import sys
import uuid
from pathlib import Path

import bpy
import numpy as np

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'review'
GAME = ROOT / 'game'
OUT.mkdir(exist_ok=True)
GAME.mkdir(exist_ok=True)
args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
parser = argparse.ArgumentParser()
parser.add_argument('--reference', type=Path, required=True)
REF = parser.parse_args(args).reference.resolve()
if not REF.is_file():
    raise SystemExit(f'Missing supplied reference: {REF}')

TEX_SIZE = 1024
SWORD_U = 130
SHEATH_U = 360
PIXELS_PER_UNIT = 37


def stable_id(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'murim-top-straight-sword-v1/' + name))


def make_texture():
    source = bpy.data.images.load(str(REF), check_existing=False)
    width, height = source.size
    if width != 1024 or height != 1024:
        raise ValueError(f'Expected supplied 1024 px reference, got {width}×{height}')
    raw = np.empty(width * height * 4, dtype=np.float32)
    source.pixels.foreach_get(raw)
    source_top = raw.reshape(height, width, 4)[::-1].copy()
    atlas = np.zeros((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    atlas[:, :, :] = (.11, .10, .09, 1.0)

    # Pixel-coordinate axes measured from the supplied image. Right/bottom
    # ornate swords lie below these strips and are deliberately excluded.
    def rectify(u_mid, p0, p1, half_span):
        for v in range(1024):
            t = v / 1023
            cx = p0[0] + t * (p1[0] - p0[0])
            cy = p0[1] + t * (p1[1] - p0[1])
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            length = math.hypot(dx, dy)
            nx, ny = -dy / length, dx / length
            us = np.arange(u_mid - half_span, u_mid + half_span + 1)
            offset = (us - u_mid) * 0.52
            sx = np.clip(np.rint(cx + offset * nx).astype(int), 0, width - 1)
            sy = np.clip(np.rint(cy + offset * ny).astype(int), 0, height - 1)
            atlas[v, us] = source_top[sy, sx]
            atlas[v, us, 3] = 1.0

    # Sword tip -> pommel. Sheath mouth -> longer chape.
    rectify(SWORD_U, (43, 447), (909, 46), 100)
    rectify(SHEATH_U, (837, 176), (328, 409), 100)

    # Side/end faces sample authored material patches, rather than checkerboard.
    swatches = {
        'steel_edge': (0.63, 0.67, 0.69),
        'steel_dark': (0.18, 0.21, 0.23),
        'steel_shine': (0.84, 0.86, 0.84),
        'brass': (0.53, 0.39, 0.20),
        'brass_light': (0.72, 0.58, 0.35),
        'black_leather': (0.085, 0.087, 0.09),
        'wrap_highlight': (0.17, 0.17, 0.17),
        'dark_lacquer': (0.075, 0.065, 0.06),
    }
    for index, (name, rgb) in enumerate(swatches.items()):
        x0 = 630 + (index % 4) * 90
        y0 = 32 + (index // 4) * 90
        for y in range(y0, y0 + 72):
            for x in range(x0, x0 + 72):
                noise = (((x * 13 + y * 17) % 19) - 9) / 900
                shade = 1 + noise + (x - x0) / 900
                atlas[y, x, :3] = np.clip(np.array(rgb) * shade, 0, 1)
                atlas[y, x, 3] = 1
        swatches[name] = [x0 + 7, y0 + 7, x0 + 65, y0 + 65]

    image = bpy.data.images.new('top_sword_atlas', width=TEX_SIZE, height=TEX_SIZE, alpha=True)
    image.pixels.foreach_set(atlas[::-1].copy().ravel())
    image.file_format = 'PNG'
    image.filepath_raw = str(OUT / 'top_sword_atlas.png')
    image.save()
    (GAME / 'top_sword_atlas.png').write_bytes((OUT / 'top_sword_atlas.png').read_bytes())
    return swatches


SWATCH = make_texture()
TEX_BYTES = (OUT / 'top_sword_atlas.png').read_bytes()


class Model:
    def __init__(self, name, texture_center, y_top, y_bottom):
        self.name = name
        self.texture_center = texture_center
        self.y_top = y_top
        self.y_bottom = y_bottom
        self.elements = []
        self.groups = {}

    def front_uv(self, x0, x1, y0, y1):
        u0 = self.texture_center + (x0 - 8) * PIXELS_PER_UNIT
        u1 = self.texture_center + (x1 - 8) * PIXELS_PER_UNIT
        if self.name == 'Top Straight Sword':
            # Perspective in the reference compresses the photographed grip.
            # Align blade shoulder, guard, grip and pommel independently.
            anchors = [(31.3,0),(10.25,751),(9.55,789),(9.18,822),
                       (8.56,836),(2.08,990),(1.30,1023)]
            def source_v(y):
                for (high, first), (low, last) in zip(anchors, anchors[1:]):
                    if high >= y >= low:
                        return first + (high-y)/(high-low)*(last-first)
                return 0 if y > anchors[0][0] else 1023
            v0, v1 = source_v(y1), source_v(y0)
        else:
            v0 = (self.y_top - y1) / (self.y_top - self.y_bottom) * 1023
            v1 = (self.y_top - y0) / (self.y_top - self.y_bottom) * 1023
        return [u0, v0, u1, v1]

    def cube(self, group, name, box, material=None, front=None):
        x0, y0, z0, x1, y1, z1 = box
        if not (x0 < x1 and y0 < y1 and z0 < z1):
            raise ValueError(f'Invalid cube {name}: {box}')
        if not all(-16 <= value <= 32 for value in box):
            raise ValueError(f'Out of Minecraft element bounds: {name}')
        uv = front or self.front_uv(x0, x1, y0, y1)
        side = SWATCH[material or 'steel_dark']
        faces = {direction: {'uv': side, 'texture': 0}
                 for direction in ('east', 'west', 'up', 'down')}
        faces['south'] = {'uv': uv, 'texture': 0}
        faces['north'] = {'uv': [uv[2], uv[1], uv[0], uv[3]], 'texture': 0}
        element = {
            'name': name, 'type': 'cube', 'uuid': stable_id(self.name + '/' + name),
            'from': [round(x0, 5), round(y0, 5), round(z0, 5)],
            'to': [round(x1, 5), round(y1, 5), round(z1, 5)],
            'origin': [8, 8, 8], 'rotation': [0, 0, 0],
            'box_uv': False, 'rescale': False, 'locked': False,
            'visibility': True, 'autouv': 0, 'color': len(self.groups) % 8,
            'faces': faces,
        }
        self.elements.append(element)
        self.groups.setdefault(group, []).append(element['uuid'])

    def faceted_tube(self, group, y0, y1, step, strips, radius_at, depth_at, material):
        count = math.ceil((y1 - y0) / step)
        for row in range(count):
            a = y0 + row * (y1 - y0) / count
            b = y0 + (row + 1) * (y1 - y0) / count
            mid = (a + b) / 2
            width = radius_at(mid)
            for col in range(strips):
                f0 = -1 + 2 * col / strips
                f1 = -1 + 2 * (col + 1) / strips
                t = (f0 + f1) / 2
                x0, x1 = 8 + width * f0, 8 + width * f1
                depth = depth_at(mid, t)
                self.cube(group, f'{group}_{row:03d}_{col:02d}',
                          (x0, a, 8-depth, x1, b, 8+depth), material)

    def bbmodel(self):
        atlas = base64.b64encode(TEX_BYTES).decode('ascii')
        display = {
            'gui': {'rotation': [0, 0, -35], 'translation': [0, 0, 0], 'scale': [.55, .55, .55]},
            'thirdperson_righthand': {'rotation': [0, -90, 8], 'translation': [0, 5, 0], 'scale': [.72, .72, .72]},
            'thirdperson_lefthand': {'rotation': [0, 90, -8], 'translation': [0, 5, 0], 'scale': [.72, .72, .72]},
            'firstperson_righthand': {'rotation': [0, -90, 12], 'translation': [1, 5, 1], 'scale': [.65, .65, .65]},
            'firstperson_lefthand': {'rotation': [0, 90, -12], 'translation': [1, 5, 1], 'scale': [.65, .65, .65]},
            'ground': {'rotation': [0, 0, 90], 'translation': [0, 3, 0], 'scale': [.43, .43, .43]},
            'fixed': {'rotation': [0, 0, -35], 'translation': [0, 0, 0], 'scale': [.55, .55, .55]},
        }
        data = {
            'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
            'name': self.name, 'model_identifier': self.name.lower().replace(' ', '_'),
            'resolution': {'width': 1024, 'height': 1024},
            'ambientocclusion': False, 'gui_light': 'front',
            'elements': self.elements,
            'outliner': [{'name': group, 'uuid': stable_id(self.name + '/group/' + group),
                          'origin': [8, 8, 8], 'rotation': [0, 0, 0],
                          'export': True, 'isOpen': False, 'visibility': True,
                          'children': children} for group, children in self.groups.items()],
            'textures': [{'name': 'top_sword_atlas.png', 'id': '0',
                          'uuid': stable_id('shared/top_sword_atlas'), 'relative_path': 'top_sword_atlas.png',
                          'namespace': 'murimrpg', 'folder': 'item',
                          'width': 1024, 'height': 1024, 'uv_width': 1024, 'uv_height': 1024,
                          'source': 'data:image/png;base64,' + atlas, 'internal': True, 'mode': 'bitmap'}],
            'display': display,
        }
        return data, display

    def save(self, stem):
        bb, display = self.bbmodel()
        (OUT / (stem + '.bbmodel')).write_text(
            json.dumps(bb, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
        elements = []
        for item in self.elements:
            faces = {key: {'uv': [round(value / 64, 6) for value in face['uv']],
                           'texture': '#0'} for key, face in item['faces'].items()}
            elements.append({'name': item['name'], 'from': item['from'], 'to': item['to'],
                             'faces': faces})
        game = {'credit': 'MurimRPG; user reference rectified for original cuboid geometry',
                'ambientocclusion': False, 'gui_light': 'front',
                'textures': {'0': 'murimrpg:item/top_sword_atlas',
                             'particle': 'murimrpg:item/top_sword_atlas'},
                'display': display, 'elements': elements}
        (GAME / (stem + '.json')).write_text(
            json.dumps(game, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')


SWORD = Model('Top Straight Sword', SWORD_U, 31.3, 1.3)


def blade_radius(y):
    if y >= 29.7:
        return max(.018, .63 * (31.3 - y) / 1.6)
    return .63 - .045 * ((y - 10) / 21.3)


SWORD.faceted_tube('01_Steel_blade_faceted', 9.8, 31.3, .24, 8,
                   blade_radius,
                   lambda y, t: .019 + .107 * (1 - abs(t) ** 1.35),
                   'steel_edge')
for side in (-1, 1):
    for row in range(56):
        y0 = 11.0 + row * .315
        y1 = y0 + .315
        if y1 > 28.7:
            break
        x = 8 + side * .16
        SWORD.cube('02_Low_fullers', f'fuller_{side}_{row:02d}',
                   (x-.018, y0, 8+.116, x+.018, y1, 8+.124), 'steel_dark',
                   SWATCH['steel_dark'])
SWORD.cube('03_Blade_ricasso', 'ricasso', (7.57, 9.45, 7.90, 8.43, 10.25, 8.10),
           'steel_shine', SWATCH['steel_shine'])
SWORD.cube('04_Brass_ferrule', 'ferrule', (7.47, 8.54, 7.62, 8.53, 9.43, 8.38),
           'brass_light', SWATCH['brass_light'])
for tier, (a, b, rx, rz) in enumerate(((9.18,9.27,1.28,.72),
                                       (9.27,9.48,1.36,.78),
                                       (9.48,9.55,1.22,.70))):
    for col in range(32):
        f0, f1 = -1 + col/16, -1 + (col+1)/16
        t = (f0+f1)/2
        d = rz * math.sqrt(max(.04, 1-t*t))
        SWORD.cube('05_Brass_oval_guard', f'guard_{tier}_{col:02d}',
                   (8+rx*f0,a,8-d,8+rx*f1,b,8+d), 'brass')

SWORD.faceted_tube('06_Black_wrapped_grip', 2.08, 8.56, .22, 12,
                   lambda y: .43 - .032 * ((y-2.08)/6.48),
                   lambda y,t: .28 * math.sqrt(max(.08,1-t*t)),
                   'black_leather')
for turn in range(19):
    base = 2.30 + turn * .325
    for col in range(9):
        t = -1 + (col+.5)*2/9
        x = 8 + .35*t
        y = base + .24*(t+1)/2
        d = .28 * math.sqrt(max(.10,1-t*t))
        for face in (-1,1):
            z = 8 + face*d
            SWORD.cube('07_Raised_leather_spiral',
                       f'wrap_{turn:02d}_{col}_{face}',
                       (x-.045,y-.029,z-.015,x+.045,y+.029,z+.015),
                       'wrap_highlight', SWATCH['wrap_highlight'])
for tier,(a,b,rx,rz) in enumerate(((1.30,1.52,.49,.34),
                                   (1.52,1.88,.53,.38),
                                   (1.88,2.13,.46,.32))):
    for col in range(12):
        f0, f1 = -1 + col/6, -1 + (col+1)/6
        t = (f0+f1)/2
        d = rz*math.sqrt(max(.05,1-t*t))
        SWORD.cube('08_Brass_pommel', f'pommel_{tier}_{col:02d}',
                   (8+rx*f0,a,8-d,8+rx*f1,b,8+d), 'brass_light')
SWORD.save('top_straight_sword')

SHEATH = Model('Extended Black Lacquer Scabbard', SHEATH_U, 30.5, 5.3)
SHEATH.faceted_tube('01_Black_lacquer_body', 6.0, 29.85, .27, 10,
                    lambda y: .65 + .08*((y-6.0)/23.85),
                    lambda y,t: .37*math.sqrt(max(.08,1-t*t)),
                    'dark_lacquer')
# A slender polished edge, running with the scabbard's long black body.
for side in (-1,1):
    SHEATH.cube('02_Raised_side_bead', f'side_bead_{side}',
                (8+side*.62-.035,7.1,7.70,8+side*.62+.035,28.9,8.30),
                'dark_lacquer', SWATCH['dark_lacquer'])
for label,a,b,rx,rz,tiers in (
    ('03_Long_brass_chape',5.30,7.10,.75,.42,7),
    ('04_Mouth_band',27.30,27.70,.81,.45,3),
    ('05_Brass_throat',29.64,30.50,.83,.47,5),
):
    for row in range(tiers):
        low=a+(b-a)*row/tiers
        high=a+(b-a)*(row+1)/tiers
        taper = 1 - .13*(1-row/max(1,tiers-1)) if 'chape' in label else 1
        for col in range(16):
            f0,f1=-1+col/8,-1+(col+1)/8
            t=(f0+f1)/2
            d=rz*math.sqrt(max(.06,1-t*t))
            SHEATH.cube(label,f'{label}_{row}_{col:02d}',
                        (8+rx*taper*f0,low,8-d,8+rx*taper*f1,high,8+d),
                        'brass_light')
# Small raised latch and ring at the throat, directly observed in the top sheath.
SHEATH.cube('06_Throat_latch', 'latch_base',
            (8.53,27.36,8.20,8.93,27.73,8.53),'brass')
SHEATH.cube('06_Throat_latch', 'latch_tip',
            (8.70,27.55,8.24,9.01,27.70,8.49),'brass_light')
SHEATH.save('extended_black_scabbard')

manifest = {
    'status': 'BLOCKBENCH_REVIEW_ASSETS_NOT_INSTALLED_IN_GAME',
    'source_sha256': hashlib.sha256(REF.read_bytes()).hexdigest(),
    'source_pixels': [1024,1024],
    'source_selection': {
        'top_sword_tip': [43,447], 'top_sword_pommel': [909,46],
        'top_scabbard_mouth': [837,176], 'top_scabbard_chape': [328,409],
        'lower_ornate_swords_used': False,
    },
    'dimensions_model_units': {
        'blade_length': 31.3 - 9.8,
        'scabbard_length': 30.5 - 5.3,
        'sheath_to_blade_ratio': round((30.5-5.3)/(31.3-9.8),3),
    },
    'elements': {'sword':len(SWORD.elements), 'scabbard':len(SHEATH.elements)},
    'groups': {'sword':{k:len(v) for k,v in SWORD.groups.items()},
               'scabbard':{k:len(v) for k,v in SHEATH.groups.items()}},
}
assert manifest['dimensions_model_units']['sheath_to_blade_ratio'] > 1.1
for model in (SWORD,SHEATH):
    assert all(all(-16 <= v <= 32 for v in e['from']+e['to']) for e in model.elements)
    assert all(all(0 <= v <= 1024 for v in f['uv'])
               for e in model.elements for f in e['faces'].values())
(OUT / 'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(manifest,ensure_ascii=False,indent=2))
