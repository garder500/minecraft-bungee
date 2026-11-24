#!/usr/bin/env python3
"""32x32 sprites of the Putrid Marshes items (issue 43): Rot Resin, Mire Cyst, Mire Core, Mire Ichor and the Recycler
Module, plus their item definitions and models. The sprites are drawn in realm32.py (they replace the 16x16 placeholders
of lore_sprites.py); this script owns where they are written. Stdlib only, deterministic output.

    python3 resource-pack/art/marsh_items.py
"""
import json
import pathlib

import goods32
import realm32

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack/assets/vampyuyu"
ITEMS = ["ore/rot_resin", "boss/mire_cyst", "boss/mire_core", "boss/mire_ichor", "ore/recycler_module"]


def main():
    for name in ITEMS:
        family, item = name.split("/")
        tex = PACK / f"textures/item/{family}/{item}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        tex.write_bytes((goods32.rot_resin() if item == "rot_resin" else realm32.draw(item)).png())   # Rot Resin: repainted in goods32
        model = PACK / f"models/item/{family}/{item}.json"
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/{name}"}}, indent=1) + "\n")
        definition = PACK / f"items/{family}/{item}.json"
        definition.parent.mkdir(parents=True, exist_ok=True)
        definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"vampyuyu:item/{name}"}}, indent=1) + "\n")
        print("wrote", name)


if __name__ == "__main__":
    main()
