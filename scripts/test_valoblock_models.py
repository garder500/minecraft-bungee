#!/usr/bin/env python3
"""ValoBlock 3D guns: no face darker than 25% grey, every element textured on all six faces, every state present."""
import json
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / "resource-pack/valoblock/assets/vampyuyu"
MIN_LUM = 0.25 * 255


def read_png(path):
    data = path.read_bytes()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride = 1 + 4 * w
    return w, h, [[tuple(raw[y * stride + 1 + x * 4:y * stride + 5 + x * 4]) for x in range(w)] for y in range(h)]


def main():
    errors = []
    models = sorted((PACK / "models/item/valoblock").glob("*_3d.json"))
    if not models:
        errors.append("no 3D weapon models found")
    for m in models:
        name = m.stem[:-3]
        for state in ("3d", "fire", "reload", "aim", "inspect"):
            if not (PACK / f"models/item/valoblock/{name}_{state}.json").exists():
                errors.append(f"{name}: missing {state} model")
        w, h, px = read_png(PACK / f"textures/item/valoblock/{name}_3d.png")
        if (w, h) != (32, 32):
            errors.append(f"{name}: atlas is {w}x{h}, expected 32x32")
        for y, row in enumerate(px):
            for x, (r, g, b, a) in enumerate(row):
                if 0.2126 * r + 0.7152 * g + 0.0722 * b < MIN_LUM:
                    errors.append(f"{name}: atlas pixel {x},{y} darker than 25% grey {(r, g, b)}")
                    break
        for state in ("3d", "fire", "reload", "aim", "inspect"):
            data = json.loads((PACK / f"models/item/valoblock/{name}_{state}.json").read_text())
            if len(data["elements"]) < (8 if name == "shard_knife" else 14):
                errors.append(f"{name}_{state}: only {len(data['elements'])} parts")
            for el in data["elements"]:
                if set(el["faces"]) != {"north", "east", "south", "west", "up", "down"}:
                    errors.append(f"{name}_{state}: element with missing faces")
                for f in el["faces"].values():
                    u0, v0, u1, v1 = f["uv"]
                    if not (0 <= u0 < u1 <= 16 and 0 <= v0 < v1 <= 16):
                        errors.append(f"{name}_{state}: bad uv {f['uv']}")
    # The Rift Seed (rift_seed_model: not a *_3d gun): same floor under the darkest pixel, every box has six textured faces.
    seed = PACK / "models/item/valoblock/rift_seed_model.json"
    if not seed.exists():
        errors.append("rift_seed: model missing")
    else:
        w, h, px = read_png(PACK / "textures/item/valoblock/rift_seed_model.png")
        if (w, h) != (32, 32):
            errors.append(f"rift_seed: atlas is {w}x{h}, expected 32x32")
        for y, row in enumerate(px):
            for x, (r, g, b, a) in enumerate(row):
                if 0.2126 * r + 0.7152 * g + 0.0722 * b < MIN_LUM:
                    errors.append(f"rift_seed: atlas pixel {x},{y} darker than 25% grey {(r, g, b)}")
                    break
        data = json.loads(seed.read_text())
        if len(data["elements"]) < 16:
            errors.append(f"rift_seed: only {len(data['elements'])} parts")
        for el in data["elements"]:
            if set(el["faces"]) != {"north", "east", "south", "west", "up", "down"}:
                errors.append("rift_seed: element with missing faces")
            for f in el["faces"].values():
                u0, v0, u1, v1 = f["uv"]
                if not (0 <= u0 < u1 <= 16 and 0 <= v0 < v1 <= 16):
                    errors.append(f"rift_seed: bad uv {f['uv']}")
    for e in errors[:30]:
        print("FAIL", e)
    print(f"valoblock models: {len(models)} weapons checked, {len(errors)} problems")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
