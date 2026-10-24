"""Replace Blockbench's bundled example skin with an original UV guide.

The player geometry and bone pivots are saved directly by Blockbench's
Bedrock Player Model preset. This script only replaces its example texture.
"""

import base64
import json
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SIZE = 64


def chunk(kind, payload):
    return (struct.pack('>I', len(payload)) + kind + payload
            + struct.pack('>I', zlib.crc32(kind + payload) & 0xffffffff))


def guide_pixels():
    pixels = [[(80, 86, 94, 255) for _ in range(SIZE)] for _ in range(SIZE)]
    regions = (
        (0, 0, 32, 16, (182, 187, 191, 255)),   # head
        (0, 16, 16, 32, (72, 102, 135, 255)),   # right leg
        (16, 16, 40, 32, (112, 126, 137, 255)), # torso
        (40, 16, 56, 32, (183, 116, 80, 255)),  # right arm
        (16, 48, 32, 64, (72, 102, 135, 255)),  # left leg
        (32, 48, 48, 64, (78, 153, 166, 255)),  # left arm
    )
    for x0, y0, x1, y1, color in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                shade = -14 if x % 4 == 0 or y % 4 == 0 else 0
                pixels[y][x] = tuple(max(0, c + shade) for c in color[:3]) + (255,)
    # A small face marker makes the head's front direction obvious in preview.
    for x in (10, 14):
        for y in (10, 11):
            pixels[y][x] = (32, 43, 54, 255)
    return pixels


def png_bytes(pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for pixel in row:
            raw.extend(pixel)
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), level=9))
            + chunk(b'IEND', b''))


def main():
    image = png_bytes(guide_pixels())
    (ROOT / 'neutral_player_uv.png').write_bytes(image)
    source = 'data:image/png;base64,' + base64.b64encode(image).decode('ascii')
    for shape in ('wide', 'slim'):
        path = ROOT / f'player_animation_{shape}.bbmodel'
        model = json.loads(path.read_text(encoding='utf-8'))
        assert model['meta']['model_format'] == 'bedrock'
        assert len(model['textures']) == 1
        model['name'] = f'murim_player_animation_{shape}'
        model['textures'][0]['name'] = 'neutral_player_uv.png'
        model['textures'][0]['path'] = ''
        model['textures'][0]['source'] = source
        path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(f'{path.name}: {len(model["elements"])} cubes, {len(model["groups"])} bones')


if __name__ == '__main__':
    main()
