"""Make a reversible Minecraft 26.2 preview pack from saved Blockbench models.

Converts the actual saved .bbmodel files; it never regenerates their cubes or
changes their display scales. PAL sword attack is overlaid with right-arm keys
only, leaving the left hand free to keep its scabbard.
"""
import argparse
import base64
import copy
import hashlib
import json
import math
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[2]
DEFAULT_CLIP = PROJECT / 'src/main/resources/assets/murimrpg/player_animations/pal_sword_swing.json'


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def texture_bytes(texture, model_file):
    source = texture.get('source', '')
    if source.startswith('data:image/png;base64,'):
        return base64.b64decode(source.partition(',')[2], validate=True)
    for candidate in (model_file.parent / texture.get('relative_path', ''),
                      model_file.parent / texture.get('name', '')):
        if candidate.is_file():
            return candidate.read_bytes()
    raise ValueError(f'Texture data not found in {model_file}')


def normalize_model(source_file, dest_root, stem):
    source = json.loads(source_file.read_text(encoding='utf-8'))
    texture_list = source.get('textures', [])
    if len(texture_list) != 1:
        raise ValueError(f'Expected one shared texture in {source_file}, got {len(texture_list)}')
    resolution = source['resolution']
    width, height = resolution['width'], resolution['height']
    atlas = texture_bytes(texture_list[0], source_file)
    texture_file = dest_root / f'assets/murimrpg/textures/item/{stem}.png'
    texture_file.parent.mkdir(parents=True, exist_ok=True)
    texture_file.write_bytes(atlas)

    model_elements = []
    for element in source['elements']:
        if element.get('visibility') is False:
            continue
        lo = element['from']
        hi = element['to']
        inflate = float(element.get('inflate', 0))
        lo = [float(value) - inflate for value in lo]
        hi = [float(value) + inflate for value in hi]
        if any(a >= b or not all(math.isfinite(v) for v in (a, b))
               for a, b in zip(lo, hi)):
            raise ValueError(f'Invalid cube: {element.get("name")}')
        if any(v < -16 or v > 32 for v in lo + hi):
            raise ValueError(f'Java item model bounds exceeded: {element.get("name")}')
        faces = {}
        for side, entry in element.get('faces', {}).items():
            if entry.get('texture') is None:
                continue
            uv = entry['uv']
            if len(uv) != 4 or any(not math.isfinite(float(value)) for value in uv):
                raise ValueError(f'Invalid UV on {element.get("name")}/{side}')
            if any(not (0 <= uv[index] <= (width if index % 2 == 0 else height))
                   for index in range(4)):
                raise ValueError(f'UV outside atlas: {element.get("name")}/{side}')
            face = {'uv': [round(float(v) * 16 / (width if i % 2 == 0 else height), 7)
                           for i, v in enumerate(uv)], 'texture': '#0'}
            if entry.get('rotation'):
                face['rotation'] = entry['rotation']
            if entry.get('cullface'):
                face['cullface'] = entry['cullface']
            faces[side] = face
        block = {'from': lo, 'to': hi, 'faces': faces}
        if element.get('name'):
            block['name'] = element['name']
        angles = element.get('rotation', [0, 0, 0])
        active = [(axis, angle) for axis, angle in zip('xyz', angles) if abs(float(angle)) > 1e-6]
        if len(active) > 1:
            raise ValueError(f'Multi-axis cube rotation is not Java JSON compatible: {element.get("name")}')
        if active:
            axis, angle = active[0]
            if not -45 <= angle <= 45:
                raise ValueError(f'Unsupported Java item rotation: {element.get("name")}')
            block['rotation'] = {'origin': element.get('origin', [8, 8, 8]),
                                 'axis': axis, 'angle': angle,
                                 'rescale': bool(element.get('rescale', False))}
        model_elements.append(block)

    display = copy.deepcopy(source.get('display', {}))
    game_model = {
        'credit': 'MurimRPG saved Blockbench model; user scale preserved',
        'ambientocclusion': source.get('ambientocclusion', False),
        'gui_light': source.get('gui_light', 'front'),
        'textures': {'0': f'murimrpg:item/{stem}',
                     'particle': f'murimrpg:item/{stem}'},
        'display': display,
        'elements': model_elements,
    }
    path = dest_root / f'assets/murimrpg/models/item/{stem}.json'
    write_json(path, game_model)
    assert game_model['display'] == source.get('display', {})
    return {'file': source_file.name, 'sha256': sha256(source_file),
            'cubes': len(model_elements),
            'display_scale': {key: value.get('scale') for key, value in display.items()}}


def item_definition(stem):
    return {'model': {'type': 'minecraft:model', 'model': f'murimrpg:item/{stem}'}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sword', type=Path, required=True)
    parser.add_argument('--scabbard', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'preview-pack')
    opt = parser.parse_args()
    output = opt.output.resolve()
    stage = output / 'pack'
    stage.mkdir(parents=True, exist_ok=True)

    sword = normalize_model(opt.sword.resolve(), stage, 'top_straight_sword')
    scabbard = normalize_model(opt.scabbard.resolve(), stage, 'extended_black_scabbard')
    write_json(stage / 'pack.mcmeta', {
        'pack': {'min_format': 88, 'max_format': 88,
                 'description': 'MurimRPG right-hand sword + left-hand scabbard preview (26.2)'}
    })
    write_json(stage / 'assets/murimrpg/items/top_straight_sword.json',
               item_definition('top_straight_sword'))
    write_json(stage / 'assets/murimrpg/items/extended_black_scabbard.json',
               item_definition('extended_black_scabbard'))

    original_clip = json.loads(DEFAULT_CLIP.read_text(encoding='utf-8'))
    preview_clip = copy.deepcopy(original_clip)
    for pose in preview_clip['emote']['moves']:
        pose.pop('leftArm', None)
        pose.pop('torso', None)
        if 'rightArm' not in pose:
            raise ValueError('Sword clip is missing right-arm keys')
    write_json(stage / 'assets/murimrpg/player_animations/pal_sword_swing.json', preview_clip)

    package = output / 'MurimRPG-Sword-Scabbard-Preview-26.2.zip'
    with zipfile.ZipFile(package, 'w', compression=zipfile.ZIP_DEFLATED,
                         compresslevel=8) as archive:
        for path in sorted(stage.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(stage).as_posix())
    manifest = {
        'status': 'PREVIEW_PACK_NOT_APPLIED',
        'base_item_mainhand': 'minecraft:iron_sword',
        'mainhand_item_model': 'murimrpg:top_straight_sword',
        'base_item_offhand': 'minecraft:stick',
        'offhand_item_model': 'murimrpg:extended_black_scabbard',
        'sword': sword, 'scabbard': scabbard,
        'animation': 'PAL sword clip with only original rightArm keys',
        'zip_sha256': sha256(package),
    }
    write_json(output / 'manifest.json', manifest)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
