#!/usr/bin/env python3
"""Writes the dungeon item icons (32x32, painted, stdlib only, deterministic).

    python3 resource-pack/art/dungeon_icons.py

Writes textures/item/dungeon/*.png, models/item/dungeon/*.json and items/dungeon/*.json under
pack/assets/vampyuyu/, used as item models vampyuyu:dungeon/<name>. The sprites are drawn in goods32.py with the
paint32 painter: a five-tone ramp per material, light from the top left, a dark coloured outline, no alpha.
"""
import json
import pathlib

import goods32

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"
ICONS = goods32.DUNGEON


def write(path, data, binary=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    if binary:
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def main():
    for name, draw in ICONS.items():
        write(ASSETS / "textures" / "item" / "dungeon" / f"{name}.png", draw().png(), binary=True)
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
