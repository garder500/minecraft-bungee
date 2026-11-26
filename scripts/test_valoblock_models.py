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
    """(w, h, rows) of a PNG, any filter (Blockbench saves with filters)."""
    sys.path.insert(0, str(ROOT / "resource-pack/art"))
    import valoblock_models as vm
    rows = vm.read_png(path.read_bytes())
    return len(rows[0]), len(rows), rows


def used_pixels(px, elements):
    """Texels the faces of a model actually show (uv 0..16 over the whole texture)."""
    h, w = len(px), len(px[0])
    seen = set()
    for el in elements:
        for f in el["faces"].values():
            u0, v0, u1, v1 = f["uv"]
            for y in range(int(min(v0, v1) * h / 16), max(int(min(v0, v1) * h / 16) + 1, int(max(v0, v1) * h / 16 - 1e-6) + 1)):
                for x in range(int(min(u0, u1) * w / 16), max(int(min(u0, u1) * w / 16) + 1, int(max(u0, u1) * w / 16 - 1e-6) + 1)):
                    seen.add((x, y))
    return seen


def coplanar(label, elements):
    """Two overlapping boxes with the same rotation must not share a face plane (the faces would flicker)."""
    out = []
    key = lambda e: json.dumps(e.get("rotation"), sort_keys=True)  # noqa: E731
    for j, q in enumerate(elements):
        for p in elements[:j]:
            if key(p) != key(q):
                continue
            if not all(p["from"][i] < q["to"][i] - 1e-6 and q["from"][i] < p["to"][i] - 1e-6 for i in range(3)):
                continue
            for i in range(3):
                if abs(p["from"][i] - q["from"][i]) < 1e-4 or abs(p["to"][i] - q["to"][i]) < 1e-4:
                    out.append(f"{label}: boxes {p['from']}-{p['to']} and {q['from']}-{q['to']} share a face plane on {'xyz'[i]}")
                    break
    return out


def main():
    errors = []
    models = sorted((PACK / "models/item/valoblock").glob("*_3d.json"))
    if not models:
        errors.append("no 3D weapon models found")
    for m in models:
        name = m.stem[:-3]
        for state in ("3d", "fire", "reload", "aim", "inspect") + (() if name == "shard_knife" else tuple(f"reload_{k}" for k in range(6))):
            if not (PACK / f"models/item/valoblock/{name}_{state}.json").exists():
                errors.append(f"{name}: missing {state} model")
        w, h, px = read_png(PACK / f"textures/item/valoblock/{name}_3d.png")
        if w != h or w not in (32, 64, 128, 256):
            errors.append(f"{name}: atlas is {w}x{h}, expected a square of 32 to 256")
        fire = json.loads((PACK / f"models/item/valoblock/{name}_fire.json").read_text())["elements"]
        for x, y in sorted(used_pixels(px, fire)):      # the fire state shows every part, flash included
            r, g, b, a = px[y][x]
            if 0.2126 * r + 0.7152 * g + 0.0722 * b < MIN_LUM:
                errors.append(f"{name}: texel {x},{y} on a face is darker than 25% grey {(r, g, b)}")
                break
        for state in ("3d", "fire", "reload", "aim", "inspect"):
            data = json.loads((PACK / f"models/item/valoblock/{name}_{state}.json").read_text())
            if len(data["elements"]) < (8 if name == "shard_knife" else 14):
                errors.append(f"{name}_{state}: only {len(data['elements'])} parts")
            if data["elements"] and any(el.get("shade", True) for el in data["elements"]):
                errors.append(f"{name}_{state}: an element is shaded (the engine would darken it again)")
            errors += coplanar(f"{name}_{state}", data["elements"])
            for el in data["elements"]:
                if set(el["faces"]) != {"north", "east", "south", "west", "up", "down"}:
                    errors.append(f"{name}_{state}: element with missing faces")
                for f in el["faces"].values():
                    u0, v0, u1, v1 = f["uv"]
                    if not (0 <= u0 < u1 <= 16 and 0 <= v0 < v1 <= 16):
                        errors.append(f"{name}_{state}: bad uv {f['uv']}")
    # The first-person twins with the gloved hands (<id>_hands_<state>): complete boxes, unshaded, no flickering shared planes.
    hands = sorted((PACK / "models/item/valoblock").glob("*_hands_*.json"))
    if len(hands) < 11 * 11:
        errors.append(f"only {len(hands)} first-person hand models (11 guns x (5 states + 6 reload frames) expected)")
    for h in hands:
        data = json.loads(h.read_text())
        errors += coplanar(h.stem, data["elements"])
        for el in data["elements"]:
            if el.get("shade", True):
                errors.append(f"{h.stem}: an element is shaded")
            if set(el["faces"]) != {"north", "east", "south", "west", "up", "down"}:
                errors.append(f"{h.stem}: element with missing faces")
            for f in el["faces"].values():
                u0, v0, u1, v1 = f["uv"]
                if not (0 <= u0 < u1 <= 16 and 0 <= v0 < v1 <= 16):
                    errors.append(f"{h.stem}: bad uv {f['uv']}")
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
