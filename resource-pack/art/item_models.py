#!/usr/bin/env python3
"""The 3D models of the custom items that are not swords: ore clusters, cores, lens, pebble, pickaxes, axes, shovels,
bows, all from Blockbench sources.

    python3 resource-pack/art/item_models.py

The items are modelled in Blockbench: resource-pack/art/blockbench/items/<id>.bbmodel (one embedded palette texture,
shade: false everywhere; edit them there, then run this script). A tool is modelled upright and every element is
turned -45 degrees about z, so it lies on the diagonal of a vanilla tool sprite and the vanilla handheld transforms hold
it (sword_models.DISPLAY). A bow has a shared "grip" group and one group per draw stage (p0 at rest, p1..p3 drawn).
In the GUI an item keeps its flat sprite: the imported <id>_gui.png (ores, pickaxes), the painted sprite (cores, lens,
pebble), or for the axes, shovels and bow stages, which never had one, a front view of the model (sprite_models.render).
Writes per item models/item/<kind>/<id>_3d.json (bows: <id>_<stage>.json), textures/item/<kind>/<id>_3d.png (the
Blockbench texture, byte for byte) and items/<kind>/<id>.json.
The machines, the Forge and the cables are block_models.py.
"""
import json

import bbmodel
import boss_skins as bs
from imported import gui_model
import sprite_models
import sword_models as sm

PACK = bs.PACK

# A sprite grown into a slab (sprite_models.py) stands upright like a vanilla flat item, so it is held like one.
SPRITE_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.45, 0.45, 0.45]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7]},
    "fixed": {"rotation": [0, 180, 0]},
    "gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
}
# The same for a sword or a tool: its sprite lies on the diagonal like a vanilla one, so it takes the vanilla
# item/handheld hold and is exactly as long as the flat item.
SPRITE_HANDHELD = {
    **SPRITE_DISPLAY,
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
BLOCK_DISPLAY = {
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
}


TIERS = ("bronze", "rime", "ember", "void", "starcore")
SOURCES = bbmodel.ROOT / "items"
BOW_DISPLAY = {  # the hand transforms of a vanilla bow, so it is aimed and drawn like one
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0]},
    "gui": {"rotation": [8, -20, 0], "scale": [0.95, 0.95, 0.95]},
}
# An ore cluster sits in the hand and on the ground like a small block.
CLUSTER_DISPLAY = {**BLOCK_DISPLAY,
                   "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 5, 0], "scale": [0.55, 0.55, 0.55]},
                   "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 5, 0], "scale": [0.55, 0.55, 0.55]},
                   "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]}}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def gui_or_hand(flat, three):
    """An item definition piece: the flat sprite in the GUI, the 3D model everywhere else."""
    return {"type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": flat}}],
            "fallback": {"type": "minecraft:model", "model": three}}


def model(kind, name, elements, display):
    tex = f"vampyuyu:item/{kind}/{name}_3d"
    return {"textures": {"0": tex, "particle": tex}, "gui_light": "front", "display": display, "elements": elements}


def front_sprite(src, elements):
    """32x32 front view of a model (sprite_models.render reads the texture with uv in texture pixels)."""
    w = len(src.tex[0])
    scaled = [dict(el, faces={f: dict(face, uv=[v * w / 16 for v in face["uv"]]) for f, face in el["faces"].items()})
              for el in elements]
    pixels, _ = sprite_models.render(scaled, src.tex)
    return pixels


def flat_model(model_id, handheld):
    """The flat GUI model of a sprite this script draws itself (textures/item/<kind>/<name>.png)."""
    kind, name = model_id.split("/")
    parent = "minecraft:item/handheld" if handheld else "minecraft:item/generated"
    dump(PACK / "models/item" / kind / f"{name}.json", {"parent": parent, "textures": {"layer0": f"vampyuyu:item/{model_id}"}})
    return f"vampyuyu:item/{model_id}"


def write_item(model_id, display, flat=None, drawn=False):
    """One Blockbench item: its 3D model, its texture and its definition (flat sprite in the GUI). drawn: the flat
    sprite is a front view of the model (the axes and shovels never had a painted one)."""
    kind, name = model_id.split("/")
    src = bbmodel.Source(SOURCES / f"{name}.bbmodel")
    elements = src.java()
    (PACK / "textures/item" / kind / f"{name}_3d.png").write_bytes(src.png)
    dump(PACK / "models/item" / kind / f"{name}_3d.json", model(kind, name, elements, display))
    if drawn:
        (PACK / "textures/item" / kind / f"{name}.png").write_bytes(bs.png(front_sprite(src, elements)))
        flat = flat_model(model_id, True)
    dump(PACK / "items" / kind / f"{name}.json",
         {"model": gui_or_hand(flat or gui_model(model_id), f"vampyuyu:item/{kind}/{name}_3d")})


def write_bow(tier):
    """A bow's models and definition: at rest, then the three draw stages of a vanilla bow (groups p0..p3 of the source,
    each with the shared grip). The GUI shows a front view of the stage."""
    kind, name = "tool", f"{tier}_bow"
    src = bbmodel.Source(SOURCES / f"{name}.bbmodel")
    (PACK / "textures/item" / kind / f"{name}_3d.png").write_bytes(src.png)
    refs = []
    for pull, (_, elements) in enumerate(bbmodel.states(src)):
        stage = f"{kind}/{name}_{pull}"
        (PACK / "textures/item" / kind / f"{name}_{pull}.png").write_bytes(bs.png(front_sprite(src, elements)))
        dump(PACK / "models/item" / kind / f"{name}_{pull}_flat.json",
             {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/{stage}"}})
        dump(PACK / "models/item" / kind / f"{name}_{pull}.json", model(kind, name, elements, BOW_DISPLAY))
        refs.append(gui_or_hand(f"vampyuyu:item/{stage}_flat", f"vampyuyu:item/{stage}"))
    dump(PACK / "items" / kind / f"{name}.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": refs[0],
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05, "fallback": refs[1],
                    "entries": [{"threshold": 0.65, "model": refs[2]}, {"threshold": 0.9, "model": refs[3]}]}}})


ORES = ("bronzite", "frostium", "starcore", "sunstone", "voidglass")
PAINTED = ("bronze_core", "ember_core", "void_lens", "pebble")


def main():
    for tier in TIERS:
        write_bow(tier)
        write_item(f"tool/{tier}_pick", sm.DISPLAY)
        write_item(f"tool/{tier}_axe", sm.DISPLAY, drawn=True)
        write_item(f"tool/{tier}_shovel", sm.DISPLAY, drawn=True)
    for name in ORES:
        write_item(f"ore/{name}", CLUSTER_DISPLAY)
    for name in PAINTED:
        write_item(f"ore/{name}", SPRITE_DISPLAY, flat=f"vampyuyu:item/ore/{name}")
    print(f"wrote {len(TIERS) * 4 + len(ORES) + len(PAINTED)} Blockbench items from {SOURCES.name}/")


if __name__ == "__main__":
    main()
