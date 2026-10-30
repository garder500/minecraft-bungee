#!/usr/bin/env python3
"""Draws the dungeon item icons (16x16 pixel art, stdlib only, deterministic).

    python3 resource-pack/art/dungeon_icons.py

Writes textures/item/dungeon/*.png, models/item/dungeon/*.json and items/dungeon/*.json under
pack/assets/vampyuyu/, used as item models vampyuyu:dungeon/<name>. Rarity colours follow the Skyblock
convention: the brighter and more saturated the item, the rarer it is.
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


def lit(img, base, light_dir=(-1, -1)):
    """Re-colours opaque pixels by their position along the light direction (upper left lit, lower right dark)."""
    for y in range(16):
        for x in range(16):
            if img[y][x] == base:
                t = ((x - 8) * light_dir[0] + (y - 8) * light_dir[1]) / 12.0
                img[y][x] = shade(base, 1.0 + max(-0.35, min(0.35, t * 0.5)))


# ---------------------------------------------------------------- icons

def bone_fragment():
    img = canvas()
    ivory = rgb(226, 220, 196)
    line(img, 4, 12, 12, 4, ivory, 2)
    for cx, cy in ((3, 13), (5, 13), (3, 11), (11, 3), (13, 3), (13, 5)):
        disc(img, cx, cy, 1, ivory)
    for x, y in ((6, 9), (9, 6)):
        put(img, x, y, rgb(170, 162, 140))
    put(img, 12, 4, rgb(255, 252, 238))
    lit(img, ivory)
    outline(img)
    return img


def crypt_shard():
    img = canvas()
    teal = rgb(70, 190, 180)
    for y in range(1, 15):
        half = (y - 1) * 0.55 if y < 8 else (14 - y) * 0.75
        for x in range(16):
            if abs(x - 7.5) <= half:
                put(img, x, y, teal)
    for y in range(16):
        for x in range(16):
            if img[y][x] == teal:
                img[y][x] = shade(teal, 1.25 if x < 7 else 0.78 if x > 8 else 1.0)
    for y in range(3, 9):
        put(img, 6, y, rgb(190, 255, 245))
    outline(img)
    return img


def wither_essence():
    img = canvas()
    disc(img, 8, 8, 5, rgb(40, 24, 62))
    disc(img, 8, 8, 3, rgb(86, 44, 130))
    disc(img, 7, 7, 1, rgb(190, 110, 255))
    for x, y in ((3, 3), (12, 4), (13, 11), (3, 12), (8, 1), (8, 14)):
        put(img, x, y, rgb(130, 70, 190))
    for x, y in ((6, 6), (7, 6)):
        put(img, x, y, rgb(235, 200, 255))
    outline(img)
    return img


def spirit_dust():
    img = canvas()
    cyan = rgb(110, 225, 245)
    for y in range(8, 14):
        half = (y - 7) * 1.3
        for x in range(16):
            if abs(x - 7.5) <= half:
                put(img, x, y, shade(cyan, 0.7 + 0.5 * (1 - (y - 8) / 6) if x < 8 else 0.55 + 0.4 * (1 - (y - 8) / 6)))
    for x, y in ((5, 5), (10, 4), (8, 2), (12, 7), (3, 8)):
        put(img, x, y, rgb(235, 255, 255))
        put(img, x, y - 1, rgb(160, 235, 250))
        put(img, x, y + 1, rgb(160, 235, 250))
        put(img, x - 1, y, rgb(160, 235, 250))
        put(img, x + 1, y, rgb(160, 235, 250))
    outline(img)
    return img


def relic():
    img = canvas()
    gold = rgb(240, 190, 50)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 5.5)
            if 2.6 <= d <= 4.6:
                put(img, x, y, gold)
    line(img, 8, 9, 8, 14, gold, 2)
    line(img, 4, 11, 11, 11, gold, 2)
    lit(img, gold)
    put(img, 7, 5, rgb(220, 40, 60))
    put(img, 8, 5, rgb(255, 110, 120))
    put(img, 7, 6, rgb(170, 20, 50))
    put(img, 8, 6, rgb(220, 40, 60))
    outline(img)
    return img


def dark_scroll():
    img = canvas()
    paper = rgb(74, 66, 96)
    for y in range(4, 12):
        for x in range(3, 13):
            put(img, x, y, paper)
    for y in range(3, 13):
        for x in (2, 13):
            put(img, x, y, rgb(120, 110, 150))
        put(img, 3, y, rgb(104, 94, 134))
        put(img, 12, y, rgb(104, 94, 134))
    for y in (6, 9):
        line(img, 5, y, 10, y, rgb(150, 70, 190))
    disc(img, 8, 8, 1, rgb(190, 30, 50))
    put(img, 8, 7, rgb(250, 90, 100))
    outline(img)
    return img


def spirit_leaf():
    img = canvas()
    green = rgb(80, 205, 120)
    c = math.cos(math.radians(45))
    for y in range(16):
        for x in range(16):
            u = ((x - 8) * c + (y - 8) * c)
            v = ((x - 8) * c - (y - 8) * c)
            # the leaf points to the upper right: u runs along the leaf
            if (v / 3.6) ** 2 + ((u + 0.5) / 7.2) ** 2 <= 1.0 and u > -6:
                put(img, x, y, shade(green, 1.2 - 0.5 * (v + 3.6) / 7.2))
    line(img, 3, 13, 12, 4, rgb(210, 255, 225), 1)
    line(img, 2, 14, 3, 13, rgb(110, 80, 50), 1)
    for x, y in ((11, 11), (4, 4), (13, 8)):
        put(img, x, y, rgb(200, 255, 255))
    outline(img)
    return img


def healing_flask():
    img = canvas()
    glass = rgb(190, 225, 240)
    red = rgb(215, 40, 60)
    disc(img, 8, 10, 4, glass)
    for y in range(6, 15):
        for x in range(16):
            if (x - 8) ** 2 + (y - 10) ** 2 <= 9 and y >= 9:
                put(img, x, y, red if x > 6 else shade(red, 1.25))
    for y in range(3, 7):
        put(img, 7, y, glass)
        put(img, 8, y, glass)
    put(img, 6, 6, glass)
    put(img, 9, 6, glass)
    put(img, 7, 2, rgb(150, 105, 60))
    put(img, 8, 2, rgb(125, 85, 48))
    put(img, 6, 9, rgb(255, 255, 255))
    put(img, 7, 11, rgb(255, 150, 160))
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
