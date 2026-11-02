#!/usr/bin/env python3
"""Placeholder 32x32 sprites for the Putrid Marshes items (issue 43): Rot Resin, Marsh Bastion Key, Abomination Core,
Bog Ichor and the Recycler Module, plus their item definitions and models. Needs Pillow. Replace with generated art
(concept sheet -> import_textures.py) when it is available; the ids stay the same.

    python3 resource-pack/art/marsh_items.py
"""
import json
import math
import pathlib

from PIL import Image

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack/assets/vampyuyu"
OUT = (20, 24, 12, 255)


def blob(img, cx, cy, rx, ry, base, light, dark):
    px = img.load()
    for y in range(32):
        for x in range(32):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if d <= 1.0:
                shade = (x - cx) * 0.5 + (y - cy) * 0.8
                px[x, y] = light if shade < -rx * 0.35 else dark if shade > rx * 0.35 else base
    # outline
    src = img.copy().load()
    for y in range(32):
        for x in range(32):
            if src[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and src[x + dx, y + dy][3] > 0
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT


def rect(img, x0, y0, x1, y1, color):
    px = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = color


def sprites():
    out = {}
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    blob(img, 16, 17, 9, 10, (150, 140, 40, 255), (200, 190, 80, 255), (90, 80, 25, 255))
    px = img.load()
    for x, y in ((13, 14), (18, 19), (15, 21), (19, 13)):
        px[x, y] = (230, 225, 130, 255)
    out["ore/rot_resin"] = img
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    blob(img, 16, 16, 10, 8, (70, 110, 40, 255), (120, 160, 70, 255), (40, 70, 25, 255))
    rect(img, 15, 5, 16, 9, (95, 70, 35, 255))
    blob(img, 16, 24, 4, 3, (95, 70, 35, 255), (130, 100, 55, 255), (60, 45, 25, 255))
    out["boss/mire_cyst"] = img
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    blob(img, 16, 16, 10, 10, (60, 70, 50, 255), (95, 110, 75, 255), (35, 40, 30, 255))
    blob(img, 16, 16, 4, 4, (90, 220, 190, 255), (160, 255, 230, 255), (40, 150, 130, 255))
    out["boss/mire_core"] = img
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    blob(img, 16, 18, 7, 9, (60, 130, 60, 255), (110, 190, 100, 255), (30, 80, 40, 255))
    rect(img, 14, 4, 17, 8, (170, 170, 160, 255))
    out["boss/mire_ichor"] = img
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    rect(img, 6, 6, 25, 25, (70, 110, 60, 255))
    rect(img, 6, 6, 25, 7, (110, 160, 90, 255))
    rect(img, 6, 24, 25, 25, (35, 60, 35, 255))
    rect(img, 11, 11, 20, 20, (30, 40, 30, 255))
    px = img.load()
    for a in range(0, 300, 6):  # recycling arrow ring
        x = round(15.5 + 3.8 * math.cos(math.radians(a)))
        y = round(15.5 + 3.8 * math.sin(math.radians(a)))
        px[x, y] = (150, 255, 120, 255)
    for x, y in ((19, 12), (20, 13), (21, 12)):
        px[x, y] = (150, 255, 120, 255)
    for x in range(5, 27):
        for y in (5, 26):
            px[x, y] = OUT
    for y in range(5, 27):
        for x in (5, 26):
            px[x, y] = OUT
    out["ore/recycler_module"] = img
    return out


def main():
    for name, img in sprites().items():
        family, item = name.split("/")
        tex = PACK / f"textures/item/{family}/{item}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        img.save(tex)
        model = PACK / f"models/item/{family}/{item}.json"
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/{name}"}}, indent=1) + "\n")
        definition = PACK / f"items/{family}/{item}.json"
        definition.parent.mkdir(parents=True, exist_ok=True)
        definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"vampyuyu:item/{name}"}}, indent=1) + "\n")
        print("wrote", name)


if __name__ == "__main__":
    main()
