"""Make an editable copy of the active Java player's geometry and skin layers.

The input geometry is Blockbench's own Bedrock Player Model project. The
locally previewed skin is read from the user's installed Minecraft JAR and is
never committed; the public project uses an original neutral UV guide.
"""

import argparse
import base64
import copy
import json
import struct
import uuid
import zipfile
from pathlib import Path

from make_neutral_skin import guide_pixels, png_bytes


ROOT = Path(__file__).resolve().parent
BASE = ROOT / 'player_animation_wide.bbmodel'
OUTER_LAYERS = (
    ('head', 'helmet', (32, 0), 0.5),
    ('body', 'body', (0, 16), 0.25),
    ('rightArm', 'rightArm', (0, 16), 0.25),
    ('leftArm', 'leftArm', (16, 0), 0.25),
    ('rightLeg', 'rightLeg', (0, 16), 0.25),
    ('leftLeg', 'leftLeg', (-16, 0), 0.25),
)
TRANSPARENT_UV = (
    (32, 0, 64, 16), (16, 32, 40, 48), (40, 32, 56, 48),
    (0, 32, 16, 48), (0, 48, 16, 64), (48, 48, 64, 64),
)


def model_with_layers():
    model = json.loads(BASE.read_text(encoding='utf-8'))
    assert model['meta']['model_format'] == 'bedrock'
    assert len(model['elements']) == 7
    elements = {item['name']: item for item in model['elements']}
    groups = {item['name']: item for item in model['groups']}

    def outliner_group(nodes, target):
        for node in nodes:
            if isinstance(node, dict):
                if node.get('uuid') == target:
                    return node
                result = outliner_group(node.get('children', []), target)
                if result is not None:
                    return result
        return None

    for source, parent, (du, dv), inflate in OUTER_LAYERS:
        cube = copy.deepcopy(elements[source])
        cube['uuid'] = str(uuid.uuid5(uuid.NAMESPACE_URL,
                                      f'murimrpg/player-overlay/{source}'))
        cube['name'] = f'{source}_outer'
        cube['inflate'] = inflate
        cube['uv_offset'] = [a + b for a, b in
                             zip(cube.get('uv_offset', [0, 0]), (du, dv))]
        for face in cube['faces'].values():
            face['uv'] = [value + (du if index % 2 == 0 else dv)
                          for index, value in enumerate(face['uv'])]
        model['elements'].append(cube)
        group = outliner_group(model['outliner'], groups[parent]['uuid'])
        if group is None:
            raise ValueError(f'Missing Blockbench group {parent}')
        group['children'].append(cube['uuid'])

    # The player may not own a cape. Keep the optional cape bone but do not
    # display an invented cape in the editable default pose.
    elements['cape']['visibility'] = False
    model['name'] = 'murim_current_java_player_editable'
    return model


def set_skin(model, image, name):
    if image[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', image[16:24]) != (64, 64):
        raise ValueError('Expected a 64x64 PNG skin')
    texture = model['textures'][0]
    texture['name'] = name
    texture['path'] = ''
    texture['source'] = ('data:image/png;base64,'
                         + base64.b64encode(image).decode('ascii'))


def write_model(model, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n',
                    encoding='utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--minecraft-jar', type=Path)
    parser.add_argument('--skin-entry',
                        default='assets/minecraft/textures/entity/player/wide/makena.png')
    opt = parser.parse_args()

    neutral = guide_pixels()
    for x0, y0, x1, y1 in TRANSPARENT_UV:
        for y in range(y0, y1):
            for x in range(x0, x1):
                neutral[y][x] = (0, 0, 0, 0)
    image = png_bytes(neutral)
    (ROOT / 'current_player_uv.png').write_bytes(image)
    model = model_with_layers()
    set_skin(model, image, 'current_player_uv.png')
    write_model(model, ROOT / 'player_current_java_editable.bbmodel')
    print('Public editable geometry:', len(model['elements']), 'cubes,',
          len(model['groups']), 'bones')

    if opt.minecraft_jar:
        with zipfile.ZipFile(opt.minecraft_jar) as jar:
            installed_skin = jar.read(opt.skin_entry)
        local_model = copy.deepcopy(model)
        set_skin(local_model, installed_skin, 'installed_player_skin.png')
        write_model(local_model, ROOT / 'local/player_current_dev_skin.bbmodel')
        print('Local preview skin embedded (excluded from Git)')


if __name__ == '__main__':
    main()
