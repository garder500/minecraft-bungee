#!/usr/bin/env python3
"""Draws the dungeon item icons (16x16 pixel art, stdlib only, deterministic).

    python3 resource-pack/art/dungeon_icons.py

Writes textures/item/dungeon/*.png, models/item/dungeon/*.json and items/dungeon/*.json under
pack/assets/vampyuyu/, used as item models vampyuyu:dungeon/<name>. Rarity colours follow the lore
bible: every item takes the five-colour palette of its Realm (shadow, base, light, highlight, glow), one outline
colour, light from the top left, flat steps only (no gradients, no alpha); the glow pixel marks the Realm motif.
"""
import json
import math
import pathlib
import struct
import zlib

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"

OUT = (16, 12, 22, 255)
CLEAR = (0, 0, 0, 0)


def png(pixels):
    """pixels: 16 rows of 16 RGBA tuples -> PNG bytes."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def canvas():
    return [[CLEAR for _ in range(16)] for _ in range(16)]


def put(img, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y][x] = color


def rgb(r, g, b):
    return (r, g, b, 255)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def outline(img):
    """One pixel dark outline around every opaque pixel."""
    marks = []
    for y in range(16):
        for x in range(16):
            if img[y][x][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and img[ny][nx][3] != 0 and img[ny][nx] != OUT:
                        marks.append((x, y))
                        break
    for x, y in marks:
        img[y][x] = OUT


def line(img, x0, y0, x1, y1, color, width=1):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        x = round(x0 + (x1 - x0) * i / steps)
        y = round(y0 + (y1 - y0) * i / steps)
        for dx in range(width):
            for dy in range(width):
                put(img, x + dx, y + dy, color)


def disc(img, cx, cy, r, color):
    for y in range(16):
        for x in range(16):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                put(img, x, y, color)


def pal(*hexes):
    return tuple(tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,) for h in hexes)


NECRO = pal("#241f3a", "#4a4570", "#9c95c8", "#e8e4ff", "#6affd8")      # Ethereal Necropolis
GROVE = pal("#2c3a1e", "#4f6b2a", "#8fb84a", "#d6f08a", "#f1e7c9")      # Whispering Grove
CITADEL = pal("#5a3a12", "#a8742a", "#f2c14e", "#fff1b0", "#3fb6a8")    # Citadel of Golden Sands
MARSH = pal("#2a2a1a", "#4e5a22", "#8a9a2e", "#c9d36a", "#b04aa0")      # Putrid Marshes


def lit(img, p, base):
    """Flat three-step shading of the `base` pixels: highlight on the upper-left rim, shadow on the lower-right rim."""
    sh, mid, lt, hi, glow = p
    for y in range(16):
        for x in range(16):
            if img[y][x] != base:
                continue
            up = y == 0 or img[y - 1][x][3] == 0
            left = x == 0 or img[y][x - 1][3] == 0
            down = y == 15 or img[y + 1][x][3] == 0
            right = x == 15 or img[y][x + 1][3] == 0
            if up or left:
                img[y][x] = hi if (up and left) else lt
            elif down or right:
                img[y][x] = mid
            else:
                img[y][x] = lt if x + y < 14 else mid


# ---------------------------------------------------------------- icons

def bone_fragment():
    """A broken bone with two knobs at each end, a crack and one soul-flame pixel."""
    sh, mid, lt, hi, glow = NECRO
    img = canvas()
    line(img, 4, 12, 12, 4, lt, 2)
    for cx, cy in ((3, 13), (5, 13), (3, 11), (11, 3), (13, 3), (13, 5)):
        disc(img, cx, cy, 1, lt)
    lit(img, NECRO, lt)
    for x, y in ((6, 9), (9, 6)):
        put(img, x, y, sh)
    put(img, 7, 8, glow)
    outline(img)
    return img


def crypt_shard():
    """A pointed crystal sliver in ash lavender with a soul-flame core."""
    sh, mid, lt, hi, glow = NECRO
    img = canvas()
    for y in range(1, 15):
        half = (y - 1) * 0.55 if y < 8 else (14 - y) * 0.75
        for x in range(16):
            if abs(x - 7.5) <= half:
                put(img, x, y, lt if x < 8 else mid)
    for y in range(3, 9):
        put(img, 6, y, hi)
    for y in range(9, 13):
        put(img, 7, y, glow if y < 11 else lt)
    put(img, 7, 8, glow)
    outline(img)
    return img


def wither_essence():
    """A decaying drop of essence: a dark orb, a bruise-purple core, spores around it (Putrid Marshes decay)."""
    sh, mid, lt, hi, glow = MARSH
    img = canvas()
    disc(img, 8, 8, 5, sh)
    disc(img, 8, 8, 3, mid)
    for x, y in ((7, 6), (8, 6), (6, 7)):
        put(img, x, y, lt)
    disc(img, 9, 9, 1, glow)
    put(img, 6, 6, hi)
    for x, y in ((3, 3), (12, 4), (13, 11), (3, 12), (8, 1), (8, 14)):
        put(img, x, y, mid)
    outline(img)
    return img


def spirit_dust():
    """A small heap of dust under four drifting motes; the soul flame glows in the heap."""
    sh, mid, lt, hi, glow = NECRO
    img = canvas()
    for y in range(8, 14):
        half = (y - 7) * 1.3
        for x in range(16):
            if abs(x - 7.5) <= half:
                put(img, x, y, lt)
    lit(img, NECRO, lt)
    for x in range(16):
        put(img, x, 13, mid if img[13][x][3] else CLEAR)
    for x, y in ((5, 5), (10, 4), (8, 2), (12, 7), (3, 8)):
        put(img, x, y, hi)
        for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            put(img, x + dx, y + dy, lt)
    for x, y in ((8, 11), (7, 11)):
        put(img, x, y, glow)
    outline(img)
    return img


def relic():
    """A golden ring-and-cross (ankh) from the Citadel with a turquoise inlay."""
    sh, mid, lt, hi, glow = CITADEL
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 5.5)
            if 2.6 <= d <= 4.6:
                put(img, x, y, lt)
    line(img, 8, 9, 8, 14, lt, 2)
    line(img, 4, 11, 11, 11, lt, 2)
    lit(img, CITADEL, lt)
    put(img, 7, 5, glow)
    put(img, 8, 5, glow)
    put(img, 7, 6, mid)
    put(img, 8, 6, glow)
    outline(img)
    return img


def dark_scroll():
    """A rolled scroll of ash paper, two lines of script and a soul-flame seal."""
    sh, mid, lt, hi, glow = NECRO
    img = canvas()
    for y in range(4, 12):
        for x in range(3, 13):
            put(img, x, y, mid)
    for y in range(3, 13):
        for x in (2, 13):
            put(img, x, y, lt)
        put(img, 3, y, lt)
        put(img, 12, y, sh)
    for x in range(2, 14):
        put(img, x, 3, hi if x < 8 else lt)
    for y in (6, 9):
        line(img, 5, y, 10, y, lt)
    disc(img, 8, 8, 1, glow)
    put(img, 8, 7, hi)
    outline(img)
    return img


def spirit_leaf():
    """A leaf of the Whispering Grove with a pale vein and a spore."""
    sh, mid, lt, hi, glow = GROVE
    img = canvas()
    c = math.cos(math.radians(45))
    for y in range(16):
        for x in range(16):
            u = ((x - 8) * c + (y - 8) * c)
            v = ((x - 8) * c - (y - 8) * c)
            # the leaf points to the upper right: u runs along the leaf
            if (v / 3.6) ** 2 + ((u + 0.5) / 7.2) ** 2 <= 1.0 and u > -6:
                put(img, x, y, lt if v < 0 else mid)
    line(img, 3, 13, 12, 4, hi, 1)
    line(img, 2, 14, 3, 13, sh, 1)
    for x, y in ((11, 11), (4, 4), (13, 8)):
        put(img, x, y, glow)
    outline(img)
    return img


def healing_flask():
    """A round flask: glass, a green sap of the Grove, a cork; the pale glow pixel is the light in the liquid."""
    sh, mid, lt, hi, glow = GROVE
    img = canvas()
    glass = pal("#bfe3d0")[0]
    disc(img, 8, 10, 4, glass)
    for y in range(6, 15):
        for x in range(16):
            if (x - 8) ** 2 + (y - 10) ** 2 <= 9 and y >= 9:
                put(img, x, y, lt if x <= 6 else mid)
    for y in range(3, 7):
        put(img, 7, y, glass)
        put(img, 8, y, glass)
    put(img, 6, 6, glass)
    put(img, 9, 6, glass)
    put(img, 7, 2, sh)
    put(img, 8, 2, mid)
    put(img, 6, 9, hi)
    put(img, 7, 11, glow)
    outline(img)
    return img


ICONS = {
    "bone_fragment": bone_fragment,
    "crypt_shard": crypt_shard,
    "wither_essence": wither_essence,
    "spirit_dust": spirit_dust,
    "relic": relic,
    "dark_scroll": dark_scroll,
    "spirit_leaf": spirit_leaf,
    "healing_flask": healing_flask,
}


def write(path, data, binary=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    if binary:
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def main():
    for name, draw in ICONS.items():
        write(ASSETS / "textures" / "item" / "dungeon" / f"{name}.png", png(draw()), binary=True)
        write(ASSETS / "models" / "item" / "dungeon" / f"{name}.json", json.dumps({
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"vampyuyu:item/dungeon/{name}"},
        }, indent=2) + "\n")
        write(ASSETS / "items" / "dungeon" / f"{name}.json", json.dumps({
            "model": {"type": "minecraft:model", "model": f"vampyuyu:item/dungeon/{name}"},
        }, indent=2) + "\n")
    print(f"wrote {len(ICONS)} icons")


if __name__ == "__main__":
    main()
