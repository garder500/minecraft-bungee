#!/usr/bin/env python3
"""16x16 sprites of the Putrid Marshes items (issue 43): Rot Resin, Mire Cyst, Mire Core, Mire Ichor and the Recycler
Module, plus their item definitions and models. The pixel maps live in lore_sprites.py (ported from the approved
design); this script only owns where they are written. Stdlib only, deterministic output.

    python3 resource-pack/art/marsh_items.py
"""
import json
import pathlib

import lore_sprites

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack/assets/vampyuyu"
ITEMS = ["ore/rot_resin", "boss/mire_cyst", "boss/mire_core", "boss/mire_ichor", "ore/recycler_module"]


def main():
    for name in ITEMS:
        family, item = name.split("/")
        tex = PACK / f"textures/item/{family}/{item}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        tex.write_bytes(lore_sprites.png(item))
        model = PACK / f"models/item/{family}/{item}.json"
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/{name}"}}, indent=1) + "\n")
        definition = PACK / f"items/{family}/{item}.json"
        definition.parent.mkdir(parents=True, exist_ok=True)
        definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"vampyuyu:item/{name}"}}, indent=1) + "\n")
        print("wrote", name)


if __name__ == "__main__":
    main()
