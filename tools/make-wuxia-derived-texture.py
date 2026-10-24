#!/usr/bin/env python3
"""Produce a reduced-texture copy of the rigged Wuxia player GLB.

The original asset embeds a 4096x4096 base colour PNG, which costs about 85 MB of VRAM once
mipmapped. This script rewrites only that image at a smaller size and leaves every byte of the
geometry, skin and animation data untouched, so the derived file stays bit-for-bit compatible
with the loader and the original is never modified.

    python3 tools/make-wuxia-derived-texture.py <source.glb> <output.glb> [--size 2048]

It prints the SHA-256 of both files and the measured quality difference, so the trade can be
recorded rather than assumed.
"""
import argparse
import hashlib
import io
import json
import struct
import sys

from PIL import Image, ImageChops

MAGIC = 0x46546C67
CHUNK_JSON = 0x4E4F534A
CHUNK_BIN = 0x004E4942


def read_glb(path):
    raw = open(path, "rb").read()
    magic, version, _ = struct.unpack_from("<III", raw, 0)
    if magic != MAGIC or version != 2:
        raise SystemExit(f"{path} is not a glTF 2.0 binary file")
    offset, js, binary = 12, None, None
    while offset + 8 <= len(raw):
        length, kind = struct.unpack_from("<II", raw, offset)
        offset += 8
        chunk = raw[offset:offset + length]
        offset += length
        if kind == CHUNK_JSON:
            js = json.loads(chunk.decode("utf-8").strip())
        elif kind == CHUNK_BIN:
            binary = chunk
    if js is None or binary is None:
        raise SystemExit(f"{path} is missing its JSON or BIN chunk")
    return raw, js, bytearray(binary)


def write_glb(path, js, binary):
    text = json.dumps(js, separators=(",", ":")).encode("utf-8")
    text += b" " * ((4 - len(text) % 4) % 4)
    blob = bytes(binary)
    blob += b"\0" * ((4 - len(blob) % 4) % 4)
    total = 12 + 8 + len(text) + 8 + len(blob)
    with open(path, "wb") as out:
        out.write(struct.pack("<III", MAGIC, 2, total))
        out.write(struct.pack("<II", len(text), CHUNK_JSON))
        out.write(text)
        out.write(struct.pack("<II", len(blob), CHUNK_BIN))
        out.write(blob)


def digest(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("output")
    parser.add_argument("--size", type=int, default=2048)
    args = parser.parse_args()

    _, js, binary = read_glb(args.source)
    images = js.get("images", [])
    if len(images) != 1 or "bufferView" not in images[0]:
        raise SystemExit("expected exactly one embedded image with a bufferView")

    view_index = images[0]["bufferView"]
    view = js["bufferViews"][view_index]
    start = view.get("byteOffset", 0)
    length = view["byteLength"]
    original_png = bytes(binary[start:start + length])
    original = Image.open(io.BytesIO(original_png))
    print(f"source image: {original.size[0]}x{original.size[1]} {original.mode}, {length} bytes")

    reduced = original.convert("RGBA").resize((args.size, args.size), Image.LANCZOS)
    buffer = io.BytesIO()
    reduced.save(buffer, format="PNG", optimize=True)
    new_png = buffer.getvalue()
    print(f"derived image: {args.size}x{args.size} RGBA, {len(new_png)} bytes")

    # Measure what was lost, upscaled back so the comparison is like for like.
    restored = reduced.resize(original.size, Image.LANCZOS)
    difference = ImageChops.difference(original.convert("RGBA"), restored)
    histogram = difference.convert("L").histogram()
    pixels = sum(histogram)
    mean = sum(i * n for i, n in enumerate(histogram)) / pixels
    peak = max(i for i, n in enumerate(histogram) if n)
    print(f"quality: mean absolute difference {mean:.3f}/255, worst pixel {peak}/255")

    # Rebuild the binary chunk with the new image in place and fix every later offset.
    tail_shift = len(new_png) - length
    binary[start:start + length] = new_png
    for other in js["bufferViews"]:
        if other.get("byteOffset", 0) > start:
            other["byteOffset"] = other.get("byteOffset", 0) + tail_shift
    view["byteLength"] = len(new_png)
    js["buffers"][0]["byteLength"] = len(binary)

    write_glb(args.output, js, binary)
    print()
    print(f"source  SHA-256 {digest(args.source)}  {args.source}")
    print(f"derived SHA-256 {digest(args.output)}  {args.output}")
    vram = args.size * args.size * 4 * 4 / 3 / 1048576
    print(f"derived VRAM with mipmaps: {vram:.1f} MB "
          f"(original {original.size[0] ** 2 * 4 * 4 / 3 / 1048576:.1f} MB)")


if __name__ == "__main__":
    sys.exit(main())
