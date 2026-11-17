#!/usr/bin/env python3
"""The Rift Seed (the plant item of ValoBlock), stdlib only, deterministic.

    python3 resource-pack/art/valoblock_seed.py        (also run by `make art-valoblock`)

An original design, not a copy of any shooter's bomb: a dark Devourer seed, an ovoid husk of boxes with a ring of near-black
plates, four thorn roots and a pink rift that splits it open on every side. Astral palette: #0e0a1a #3a1a6a #8a4aff #e0c8ff
and the rift pink #ff4a8a. Written under the ValoBlock pack (assets/vampyuyu/):

    textures/item/valoblock/rift_seed.png         32x32 flat sprite (the hotbar slot and every menu: display context gui)
    textures/item/valoblock/rift_seed_model.png   32x32 atlas of the 3D model (a lit and a side tile per material)
    models/item/valoblock/rift_seed.json          item/generated, the flat sprite
    models/item/valoblock/rift_seed_model.json    about 20 boxes: held, on the ground, on a display
    items/valoblock/rift_seed.json                select on the display context: gui -> flat sprite, anything else -> 3D

The model is deliberately not named *_3d: scripts/test_valoblock_models.py checks the gun states on those. It keeps the same
floor under the darkest face (31 % grey) so the husk is never a black hole, and its boxes are `shade: false`.

`seed_pixels` is also what valoblock_ui.py draws for the HUD icon (16x16, pulsing between two frames) and the head badge.
"""
import json
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import valoblock_items as vi  # noqa: E402

PACK = vi.PACK
NAME = "rift_seed"
PALETTE = {"ink": "0e0a1a", "body": "3a1a6a", "violet": "8a4aff", "lavender": "e0c8ff", "rift": "ff4a8a"}
FLOOR = 80


def hexc(name, a=255):
    r, g, b = vi.rgb(PALETTE[name])
    return (r, g, b, a)


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ------------------------------------------------------------------ the flat sprite

def seed_pixels(size, glow=False):
    """size x size RGBA rows: a teardrop husk (point up), a pink rift down the middle, two thorns, an optional pink halo."""
    img = [[(0, 0, 0, 0)] * size for _ in range(size)]
    s = size / 32.0
    cx, cy = (size - 1) / 2.0, size * 0.52
    rx, ry = size * 0.27, size * 0.36
    ink, body, violet, lav, rift = hexc("ink"), hexc("body"), hexc("violet"), hexc("lavender"), hexc("rift")

    def inside(x, y):
        dx, dy = (x - cx) / rx, (y - cy) / ry
        if dy < 0:  # the upper half narrows to a point: a seed, not an egg
            dx /= max(0.2, 1 + dy * 0.82)
        return dx * dx + dy * dy <= 1.0

    def crack(x, y):  # a jagged vertical rift, a little left of the middle at the top and right at the bottom
        t = (y - (cy - ry * 0.85)) / (ry * 1.7)
        if not 0 <= t <= 1:
            return False
        off = (0.9 * math.sin(t * 9.0)) * s
        w = max(0.5, 1.15 * s) * (1 - abs(t - 0.5) * 0.9)
        return abs((x - cx) - off) <= w

    if glow:  # a pink halo around the whole seed
        for y in range(size):
            for x in range(size):
                d = math.hypot((x - cx) / (rx + 3.2 * s), (y - cy) / (ry + 3.2 * s))
                if d <= 1.0:
                    img[y][x] = (rift[0], rift[1], rift[2], round(110 * (1 - d) + 40))
    thorns = ((-0.95, 0.62, -1), (0.95, 0.62, 1))
    for tx, ty, sign in thorns:  # two roots curling out of the foot
        for k in range(round(6 * s) + 1):
            x = round(cx + tx * rx + sign * k * 0.6 * s)
            y = round(cy + ty * ry + k * 0.5 * s)
            for w in range(max(1, round(1.4 * s))):
                if 0 <= x < size and 0 <= y + w < size:
                    img[y + w][x] = violet if k < round(4 * s) else body
    for y in range(size):
        for x in range(size):
            if not inside(x, y):
                continue
            hx, hy = (x - cx + rx * 0.45) / rx, (y - cy + ry * 0.4) / ry   # the light comes from the upper left
            t = max(0.0, min(1.0, 1.0 - math.hypot(hx, hy) / 1.15))
            c = mix(ink, body, 0.25 + 0.75 * t)
            if t > 0.62:
                c = mix(body, violet, (t - 0.62) / 0.38 * 0.8)
            img[y][x] = c
    for y in range(size):  # the ring of plates: two dark bands across the belly
        for x in range(size):
            if inside(x, y) and abs(y - cy) in (round(2.2 * s), round(2.2 * s) + 1) and size >= 24:
                img[y][x] = mix(img[y][x], ink, 0.8)
    for y in range(size):
        for x in range(size):
            if inside(x, y) and crack(x, y):
                img[y][x] = lav if abs(x - cx) < 0.35 * s and size >= 24 and (y % 5 == 0) else rift
    # the outline last, around the husk only
    solid = [[img[y][x][3] == 255 for x in range(size)] for y in range(size)]
    for y in range(size):
        for x in range(size):
            if solid[y][x]:
                continue
            near = any(0 <= x + dx < size and 0 <= y + dy < size and solid[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near:
                img[y][x] = ink
    return img


def badge_pixels():
    """A 5 x 5 seed badge for the corner of a squad head in the top bar: a pink bead in a dark rim."""
    ink, rift, lav = hexc("ink"), hexc("rift"), hexc("lavender")
    rows = [".ooo.", "oKLKo", "oKKKo", ".oKo.", "..o.."]
    pal = {"o": ink, "K": rift, "L": lav}
    return [[pal.get(ch, (0, 0, 0, 0)) for ch in row] for row in rows]


# ------------------------------------------------------------------ the 3D model

MATS = ["o", "p", "v", "l", "k"]   # ink, body, violet, lavender, rift (two tiles each: lit, side)
MAT_COLOR = {"o": "ink", "p": "body", "v": "violet", "l": "lavender", "k": "rift"}
RAMP = {"o": 98, "p": 108, "v": 150, "l": 215, "k": 150}


def lift(c, floor):
    l = lum(c)
    if l >= floor:
        return tuple(c)
    m = max(c) or 1
    add = floor - l
    return tuple(min(255, round(v + add * (0.6 + 0.4 * v / m))) for v in c)


def atlas():
    """32x32 RGBA rows: tile 2i is the lit tile of material i, tile 2i + 1 its side tile (8x8 each, a bevel and a speckle)."""
    px = [[(0, 0, 0, 255)] * 32 for _ in range(32)]
    for i, ch in enumerate(MATS):
        base = hexc(MAT_COLOR[ch])[:3]
        for side in (0, 1):
            t = 2 * i + side
            u, v = (t % 4) * 8, (t // 4) * 8
            for y in range(8):
                for x in range(8):
                    k = 1.0 + 0.05 * math.sin(x * 2.3 + y * 1.7 + t)
                    if ch == "k":
                        k = 1.1 - 0.04 * (x + y) / 7
                    else:
                        if x == 0 or y == 0:
                            k *= 1.1
                        if x == 7 or y == 7:
                            k *= 0.9
                        if side:
                            k *= 0.84
                    c = tuple(min(255, round(a * k)) for a in base)
                    c = lift(c, RAMP[ch] * (0.9 if side and ch != "k" else 1.0))
                    px[v + y][u + x] = (c[0], c[1], c[2], 255)
    for t in range(2 * len(MATS), 16):  # the six tiles no box uses repeat the body tile (no black pixel in the atlas)
        u, v = (t % 4) * 8, (t // 4) * 8
        for y in range(8):
            for x in range(8):
                px[v + y][u + x] = px[y][16 + x]
    return px


def face_uv(mat, side):
    t = 2 * MATS.index(mat) + (1 if side else 0)
    u, v = (t % 4) * 4, (t // 4) * 4
    return [u, v, u + 4, v + 4]


def box(frm, to, mat, rot=None):
    el = {"from": [round(c, 3) for c in frm], "to": [round(c, 3) for c in to], "shade": False, "faces": {
        d: {"uv": face_uv(mat, d != "up"), "texture": "#0"} for d in ("north", "east", "south", "west", "up", "down")}}
    if rot:
        angle, axis, origin = rot
        el["rotation"] = {"angle": angle, "axis": axis, "origin": origin}
    return el


def elements():
    e = []
    e.append(box((5, 4, 5), (11, 12, 11), "p"))                  # the husk
    e.append(box((6, 12, 6), (10, 14, 10), "p"))                 # the shoulder
    e.append(box((7, 14, 7), (9, 15.6, 9), "v"))                 # the tip
    e.append(box((6, 2.5, 6), (10, 4, 10), "p"))                 # the foot
    e.append(box((7, 1, 7), (9, 2.5, 9), "o"))
    e.append(box((4.6, 7.4, 4.6), (11.4, 8.6, 11.4), "o"))       # the ring of plates
    e.append(box((4.6, 5.0, 4.6), (11.4, 5.7, 11.4), "o"))
    # the rift: a slit proud of each of the four faces
    e.append(box((7.4, 4.5, 4.4), (8.6, 11.5, 4.7), "k"))        # north
    e.append(box((7.4, 4.5, 11.3), (8.6, 11.5, 11.6), "k"))      # south
    e.append(box((4.4, 4.5, 7.4), (4.7, 11.5, 8.6), "k"))        # west
    e.append(box((11.3, 4.5, 7.4), (11.6, 11.5, 8.6), "k"))      # east
    e.append(box((7.2, 11.5, 5.2), (8.8, 12.2, 10.8), "k"))      # the rift spills over the top
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):            # four thorn roots curling out of the foot
        if dx:
            frm, to = (10 if dx > 0 else 2, 2.2, 7.4), (14 if dx > 0 else 6, 3.4, 8.6)
            rot = (-22.5 * dx, "z", [10 if dx > 0 else 6, 2.8, 8])
        else:
            frm, to = (7.4, 2.2, 10 if dz > 0 else 2), (8.6, 3.4, 14 if dz > 0 else 6)
            rot = (22.5 * dz, "x", [8, 2.8, 10 if dz > 0 else 6])
        e.append(box(frm, to, "v", rot))
    for frm, to in (((2.4, 10, 3), (3.4, 11, 4)), ((12.6, 5, 11.6), (13.6, 6, 12.6)), ((11, 13.2, 3), (12, 14.2, 4)), ((3.2, 6, 11.5), (4.0, 6.8, 12.3))):
        e.append(box(frm, to, "l"))                              # motes of rift light
    return e


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [1.5, 1.5, -3], "scale": [0.6, 0.6, 0.6]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 12, 0], "scale": [0.9, 0.9, 0.9]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [20, 40, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def main():
    tex = PACK / "textures/item/valoblock"
    tex.mkdir(parents=True, exist_ok=True)
    (tex / f"{NAME}.png").write_bytes(vi.png(seed_pixels(32)))
    (tex / f"{NAME}_model.png").write_bytes(vi.png(atlas()))
    dump(PACK / f"models/item/valoblock/{NAME}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/valoblock/{NAME}"}})
    atlas_ref = f"vampyuyu:item/valoblock/{NAME}_model"
    els = elements()
    dump(PACK / f"models/item/valoblock/{NAME}_model.json",
         {"textures": {"0": atlas_ref, "particle": atlas_ref}, "gui_light": "front", "display": DISPLAY, "elements": els})
    ref = lambda m: {"type": "minecraft:model", "model": f"vampyuyu:item/valoblock/{m}"}  # noqa: E731
    dump(PACK / f"items/valoblock/{NAME}.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": ref(NAME)}], "fallback": ref(f"{NAME}_model")}})
    print(f"wrote the Rift Seed ({len(els)} boxes) under {PACK}")


if __name__ == "__main__":
    main()
