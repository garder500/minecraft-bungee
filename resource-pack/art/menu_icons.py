#!/usr/bin/env python3
"""Paints the two generated Skyblock menu item icons (32x32, stdlib only, deterministic).

    python3 resource-pack/art/menu_icons.py [--sheet out.png]

Writes textures/item/menu/*.png, models/item/menu/*.json and items/menu/*.json under
pack/assets/vampyuyu/, used as item models vampyuyu:menu/<name>. The other item/menu icons are imported painted
sprites (art/imported.txt); these two are painted with sb_paint to stand next to them.
"""
import json
import pathlib
import sys

from sb_paint import (Art, GOLD, STONE, LEAF, WOOD, tones, rect, ell, poly, pts, ball, grad, vcyl, sheet)
from skyblock_icon import star_points, DIRT

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"


def skyblock_menu():
    # A grass topped floating island in front of a golden star: "your Skyblock".
    a = Art()
    a.fill(poly(star_points(15.5, 13, 15, 0.3, 4)), GOLD, grad(3, 0, 28, 26, 1.0, 0.4), rim=0.12)
    a.fill(ell(15.5, 13, 5, 5), GOLD, 0.95, ink=False)
    rock = poly((5, 19), (26, 19), (23, 24), (19, 26), (16, 30), (14, 30), (12, 26), (8, 24))
    a.fill(rock, STONE, grad(5, 19, 24, 30, 0.85, 0.15))
    a.fill(poly((5, 19), (26, 19), (24, 22), (16, 24), (7, 22)), DIRT, grad(5, 19, 26, 24, 0.9, 0.35), ink=False)
    a.fill(ell(15.5, 18.5, 11.5, 2.8), LEAF, grad(4, 16, 27, 21, 0.98, 0.45))
    a.fill(rect(15, 13, 16, 18), WOOD, vcyl(15, 16))
    for cx, cy, r in ((12.5, 12, 3), (18.5, 12, 3), (15.5, 10, 3.6)):
        a.fill(ell(cx, cy, r, r * 0.85), LEAF, ball(cx, cy, r + 0.5, r), ink=False)
    a.put(pts(8, 19, 22, 18), (255, 240, 170))
    return a.outline()


def fairy_soul():
    a = Art()
    wing = tones("4a78c0", "8ac0f0", "c8ecff", "ffffff")
    for sx in (-1, 1):
        a.fill(ell(15.5 + sx * 10, 10, 5.5, 7.5), wing, grad(2, 2, 29, 18, 0.98, 0.4))
        a.fill(ell(15.5 + sx * 9, 21, 4, 5), wing, grad(2, 16, 29, 26, 0.9, 0.3))
    pink = tones("7a1c6a", "c83ca8", "ff7ad8", "ffb8ec", "fff0fa")
    a.fill(ell(15.5, 16, 8.5, 8.5), pink, ball(15.5, 16, 9, ambient=0.3))
    a.fill(ell(14.5, 15, 3.4, 3.4), pink, 0.95, ink=False)
    a.put(pts(14, 14, 15, 14, 14, 15), (255, 255, 255))
    for x, y in ((15, 3), (26, 28), (4, 28)):
        a.spark(x, y, 1, ((255, 200, 240), (255, 255, 255)))
    return a.outline()


ICONS = {"skyblock_menu": skyblock_menu, "fairy_soul": fairy_soul}


def write(path, data, binary=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if binary else (json.dumps(data, indent=2) + "\n").encode())


def main():
    if "--sheet" in sys.argv:
        pathlib.Path(sys.argv[sys.argv.index("--sheet") + 1]).write_bytes(sheet([d().grid() for d in ICONS.values()], cols=2, scale=6))
        return
    for name, draw in ICONS.items():
        write(ASSETS / "textures" / "item" / "menu" / f"{name}.png", draw().png(), binary=True)
        write(ASSETS / "models" / "item" / "menu" / f"{name}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/menu/{name}"}})
        write(ASSETS / "items" / "menu" / f"{name}.json",
              {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/menu/{name}"}})
    print("wrote", ", ".join(ICONS))


if __name__ == "__main__":
    main()
