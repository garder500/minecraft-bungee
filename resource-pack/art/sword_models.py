#!/usr/bin/env python3
"""True 3D sword models (blade with a fuller and edges, guard, gem, grip and pommel): Blockbench sources to pack models.

    python3 resource-pack/art/sword_models.py

The source of each sword is a Blockbench project, resource-pack/art/blockbench/items/<id>.bbmodel (one embedded palette
texture, shade: false everywhere; edit them there, then run this script). The sword is modelled upright and every
element is turned -45 degrees about z, so it sits on the diagonal like the flat sprite of a vanilla sword and the vanilla
`handheld` hand transforms hold it right. Writes, for the five weapons and the six skins,
  pack/assets/vampyuyu/models/item/<kind>/<id>_3d.json    (the boxes, hand transforms of a vanilla sword)
  pack/assets/vampyuyu/textures/item/<kind>/<id>_3d.png   (the Blockbench texture, byte for byte)
  pack/assets/vampyuyu/items/<kind>/<id>.json             (gui -> the flat sprite, anything else -> the 3D sword)
Deterministic.
"""
import json

import bbmodel
import boss_skins as bs
from imported import gui_model

PACK = bs.PACK
SOURCES = bbmodel.ROOT / "items"

# The hand transforms of a vanilla sword (item/handheld): the 3D sword is as long as the flat one it replaces.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 90, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.78, 0.78, 0.78]},
}


SWORDS = (("weapon", "bronze_cleaver"), ("weapon", "rimeblade"), ("weapon", "ember_sword"), ("weapon", "void_sword"),
          ("weapon", "starcore_sword"), ("skin", "kitsune"), ("skin", "titan_edge"), ("skin", "crystal_saber"),
          ("skin", "flame_brand"), ("skin", "frost_fang"), ("skin", "wyrmfang"))


def write(kind, name):
    src = bbmodel.Source(SOURCES / f"{name}.bbmodel")
    three = f"vampyuyu:item/{kind}/{name}_3d"
    flat = gui_model(f"{kind}/{name}")      # the imported <id>_gui sprite of a weapon, the painted sprite of a skin
    for path, data in (
            (PACK / "models" / "item" / kind / f"{name}_3d.json",
             {"textures": {"0": three, "particle": three}, "gui_light": "front", "display": DISPLAY, "elements": src.java()}),
            (PACK / "items" / kind / f"{name}.json",
             {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                        "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": flat}}],
                        "fallback": {"type": "minecraft:model", "model": three}}})):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    (PACK / "textures" / "item" / kind / f"{name}_3d.png").write_bytes(src.png)


def main():
    for kind, name in SWORDS:
        write(kind, name)
    print(f"wrote {len(SWORDS)} 3D swords from {SOURCES.name}/")


if __name__ == "__main__":
    main()
