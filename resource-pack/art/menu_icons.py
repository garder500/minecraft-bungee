#!/usr/bin/env python3
"""Draws the Skyblock menu item icons (16x16 pixel art, stdlib only, deterministic).

    python3 resource-pack/art/menu_icons.py

Writes textures/item/menu/*.png, models/item/menu/*.json and items/menu/*.json under
pack/assets/vampyuyu/, used as item models vampyuyu:menu/<name>.
"""
import json
import pathlib
import struct
import zlib

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"

OUT = (14, 11, 24, 255)
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


def star(cx, cy, r_out, base, light, dark):
    """Four pointed star with a lit upper left and a shaded lower right."""
    img = canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - cx + 0.5), abs(y - cy + 0.5)
            # astroid-ish: |dx|^0.6 + |dy|^0.6 <= r^0.6
            if dx ** 0.6 + dy ** 0.6 <= r_out ** 0.6:
                shade = (x - cx) + (y - cy)
                img[y][x] = light if shade < -1 else (dark if shade > 1 else base)
    return img


def skyblock_menu():
    # A grass topped floating island under a golden star: "your Skyblock".
    img = star(8, 7, 7.2, (255, 214, 64, 255), (255, 245, 170, 255), (214, 150, 28, 255))
    # island body
    dirt, dirt_dark, grass, grass_light = (134, 92, 56, 255), (96, 64, 40, 255), (92, 184, 70, 255), (140, 222, 100, 255)
    for y, (x0, x1) in zip(range(9, 14), [(4, 11), (4, 11), (5, 10), (6, 9), (7, 8)]):
        for x in range(x0, x1 + 1):
            put(img, x, y, dirt if (x + y) % 3 else dirt_dark)
    for x in range(3, 13):
        put(img, x, 8, grass if x % 3 else grass_light)
        put(img, x, 9, grass)
    for x, y in ((3, 9), (12, 9)):
        put(img, x, y, grass)
    # sparkle
    put(img, 13, 2, (255, 255, 255, 255))
    put(img, 2, 3, (255, 255, 255, 255))
    outline(img)
    return img


def fairy_soul():
    img = canvas()
    pink, light, deep = (255, 122, 217, 255), (255, 200, 240, 255), (200, 60, 170, 255)
    # glowing orb
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 8.5) ** 2) ** 0.5
            if d <= 3.6:
                img[y][x] = light if d < 1.8 else pink
            elif d <= 4.4:
                img[y][x] = deep
    # little wings
    for (x, y) in ((2, 6), (3, 5), (3, 6), (2, 7), (3, 7), (12, 6), (13, 5), (12, 5), (13, 7), (12, 7)):
        put(img, x, y, (190, 240, 255, 255))
    put(img, 7, 3, (255, 255, 255, 255))
    put(img, 8, 2, (255, 255, 255, 255))
    put(img, 12, 12, (255, 255, 255, 255))
    outline(img)
    return img


ICONS = {"skyblock_menu": skyblock_menu, "fairy_soul": fairy_soul}


def write(path, data, binary=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if binary else (json.dumps(data, indent=2) + "\n").encode())


def main():
    for name, draw in ICONS.items():
        write(ASSETS / "textures" / "item" / "menu" / f"{name}.png", png(draw()), binary=True)
        write(ASSETS / "models" / "item" / "menu" / f"{name}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/menu/{name}"}})
        write(ASSETS / "items" / "menu" / f"{name}.json",
              {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/menu/{name}"}})
    print("wrote", ", ".join(ICONS))


if __name__ == "__main__":
    main()
