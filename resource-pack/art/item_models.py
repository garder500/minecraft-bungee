#!/usr/bin/env python3
"""True 3D models of every other custom item: ores, cores, lens, tools, the Forge and the machines.

    python3 resource-pack/art/item_models.py

Same method as sword_models.py and boss_skins.py (boxes on a 16x16 atlas of 4x4 materials). The machines and the
Forge are drawn as full blocks: in the world the plugin puts them on an item display over the vanilla block
(`BlockModels.java`), in the inventory they are the item. Deterministic.
"""
import json

import boss_skins as bs
from imported import imported
import sword_models as sm

PACK = bs.PACK
Y = ("y", 45, [8, 8, 8])

ITEM_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 3, 0], "scale": [0.55, 0.55, 0.55]},
    "fixed": {"scale": [0.9, 0.9, 0.9]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
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


def write(model_id, s, display, block=False):
    """3D model as <name>_3d; in the GUI (inventory, hotbar) a non-block item shows its flat sprite instead."""
    kind, name = model_id.split("/")
    if imported(model_id) and kind in ("ore", "weapon", "tool", "armor"):  # flat imported sprite everywhere
        return
    if imported(f"{model_id}_3d"):  # a textured block model from block_models.py
        return
    three = f"vampyuyu:item/{kind}/{name}_3d"
    model = {"textures": {"0": three, "particle": three},
             "gui_light": "front", "display": display, "elements": s.elements}
    flat = {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/{model_id}"}}
    definition = {"model": {"type": "minecraft:model", "model": three}} if block else {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": flat["model"]}],
        "fallback": {"type": "minecraft:model", "model": three}}}
    for path, data in ((bs.PACK / "models" / "item" / kind / f"{name}_3d.json", model),
                       (bs.PACK / "items" / kind / f"{name}.json", definition)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    tex = bs.PACK / "textures" / "item" / kind / f"{name}_3d.png"
    tex.write_bytes(bs.png(s.atlas()))


def shade(c, f):
    return tuple(min(255, int(v * f)) for v in c)


def crystals(name, stone, body, light, seed):
    """An ore chunk: a rock base with crystals poking out at different angles."""
    s = bs.Skin(name, [("stone", stone, "plain"), ("rock", shade(stone, 0.75), "plain"), ("body", body, "plain"),
                       ("light", light, "glow")], seed)
    s.box((2, 0, 2), (14, 5, 14), "stone")
    s.box((4, 5, 4), (12, 7, 11), "rock")
    s.box((7, 7, 6), (10, 15, 9), "body", rot=("z", 12, [8, 7, 8]))
    s.box((7.4, 11, 6.4), (9.6, 15.5, 8.6), "light", rot=("z", 12, [8, 7, 8]))
    s.box((3.5, 5, 7), (6.5, 12, 10), "body", rot=("z", -22, [5, 5, 8]))
    s.box((10, 5, 4), (13, 11, 7), "body", rot=("x", 20, [11, 5, 5]))
    s.box((10.4, 8, 4.4), (12.6, 11.5, 6.6), "light", rot=("x", 20, [11, 5, 5]))
    s.box((6, 5, 10), (9, 10, 13), "body", rot=("x", -18, [7, 5, 11]))
    return s


def core(name, shell, gem, glow, seed):
    """A gem core: a faceted gem (two boxes turned against each other) inside a four-post frame."""
    s = bs.Skin(name, [("shell", shell, "plain"), ("dark", shade(shell, 0.6), "plain"), ("gem", gem, "plain"),
                       ("glow", glow, "glow")], seed)
    for x, z in ((3, 3), (11.5, 3), (3, 11.5), (11.5, 11.5)):
        s.box((x, 1, z), (x + 1.5, 15, z + 1.5), "shell")
    s.box((3, 0, 3), (13, 1.5, 13), "dark")
    s.box((3, 14.5, 3), (13, 16, 13), "dark")
    s.box((5.5, 4.5, 5.5), (10.5, 11.5, 10.5), "gem", rot=("y", 45, [8, 8, 8]))
    s.box((5.5, 4.5, 5.5), (10.5, 11.5, 10.5), "gem", rot=("x", 45, [8, 8, 8]))
    s.box((6.8, 6.8, 6.8), (9.2, 9.2, 9.2), "glow", rot=("y", 45, [8, 8, 8]))
    return s


def lens(name, rim, glass, glow, seed):
    s = bs.Skin(name, [("rim", rim, "plain"), ("glass", glass, "plain"), ("glow", glow, "glow"),
                       ("grip", shade(rim, 0.55), "plain")], seed)
    s.box((2, 5, 6.5), (14, 6.5, 9.5), "rim")          # not a disc: rim bars on a diamond frame
    s.box((2, 12, 6.5), (14, 13.5, 9.5), "rim")
    s.box((2, 5, 6.5), (3.5, 13.5, 9.5), "rim")
    s.box((12.5, 5, 6.5), (14, 13.5, 9.5), "rim")
    s.box((3.5, 6.5, 7.4), (12.5, 12, 8.6), "glass")
    s.box((6, 7.8, 7.0), (10, 10.7, 9.0), "glow", rot=("z", 45, [8, 9.25, 8]))
    s.box((7, 0, 7), (9, 5, 9), "grip")
    s.box((6, 0, 6), (10, 1.5, 10), "rim")
    return s


def voidglass(name, glass, dark, glow, seed):
    s = bs.Skin(name, [("glass", glass, "plain"), ("dark", dark, "plain"), ("glow", glow, "glow")], seed)
    s.box((1, 0, 1), (15, 2, 15), "dark")
    s.box((2, 2, 2), (14, 14, 14), "glass")
    s.box((1, 14, 1), (15, 16, 15), "dark")
    s.box((5.5, 5.5, 5.5), (10.5, 10.5, 10.5), "glow", rot=("y", 45, [8, 8, 8]))
    s.box((5.5, 5.5, 5.5), (10.5, 10.5, 10.5), "glow", rot=("z", 45, [8, 8, 8]))
    return s


def starcore(name, body, edge, glow, seed):
    s = bs.Skin(name, [("body", body, "plain"), ("edge", edge, "plain"), ("glow", glow, "glow")], seed)
    for ang in (0, 45, 90, 135):
        s.box((7, 1.5, 7), (9, 14.5, 9), "body", rot=("z", ang, [8, 8, 8]))
    s.box((7, 1.5, 7), (9, 14.5, 9), "edge", rot=("x", 45, [8, 8, 8]))
    s.box((6, 6, 6), (10, 10, 10), "glow", rot=("y", 45, [8, 8, 8]))
    return s


def forge(name, seed):
    s = bs.Skin(name, [("stone", (88, 88, 96), "plain"), ("dark", (46, 46, 52), "plain"),
                       ("bronze", (201, 132, 66), "plain"), ("glow", (255, 150, 50), "glow"),
                       ("iron", (150, 152, 158), "plain")], seed)
    s.box((0, 0, 0), (16, 11, 16), "stone")
    s.box((0, 11, 0), (16, 13, 16), "dark", "iron")
    s.box((2, 13, 2), (14, 14.5, 14), "iron")           # the anvil top plate
    s.box((5, 14.5, 4), (11, 16, 12), "dark")
    s.box((11, 13, 10), (15, 16, 14), "dark")           # chimney
    s.box((3, 2, 16.0), (13, 9, 16.5), "dark")            # furnace mouth
    s.box((4, 3, 16.0), (12, 6.5, 16.7), "glow")
    s.box((0, 9, 16.0), (16, 10.5, 16.5), "bronze")
    s.box((0, 0, 0), (16, 1.5, 16), "bronze")
    return s


def coal_generator(name, seed):
    s = bs.Skin(name, [("body", (76, 80, 88), "plain"), ("dark", (38, 40, 46), "plain"), ("bronze", (201, 132, 66), "plain"),
                       ("glow", (255, 140, 40), "glow"), ("coal", (28, 28, 30), "plain")], seed)
    s.box((0, 0, 0), (16, 12, 16), "body")
    s.box((0, 0, 0), (16, 2, 16), "dark")
    s.box((0, 10, 0), (16, 12, 16), "bronze")
    s.box((3, 12, 3), (13, 14, 13), "dark")
    s.box((4, 13.5, 4), (12, 14.2, 12), "coal")
    s.box((11, 12, 11), (14.5, 16, 14.5), "dark")       # chimney
    s.box((2, 2.5, 16.0), (14, 11, 16.4), "dark")
    for k in range(4):                                              # four heat grills: they go out as the coal burns down
        s.box((3, 3.3 + k * 1.9, 16.0), (13, 4.4 + k * 1.9, 16.7), "glow")
    return s


def battery(name, seed):
    s = bs.Skin(name, [("copper", (184, 106, 62), "plain"), ("dark", (60, 38, 30), "plain"), ("cell", (80, 200, 120), "glow"),
                       ("cap", (120, 124, 130), "plain")], seed)
    s.box((0, 0, 0), (16, 13, 16), "copper")
    s.box((0, 0, 0), (16, 2, 16), "dark")
    s.box((0, 11, 0), (16, 13, 16), "cap")
    for k in range(4):
        s.box((2.0 + k * 3.2, 3, 16.0), (4.4 + k * 3.2, 10, 16.5), "cell")
    s.box((3, 13, 3), (6.5, 16, 6.5), "cap")
    s.box((9.5, 13, 9.5), (13, 16, 13), "cap")
    return s


def cable(name, seed):
    s = bs.Skin(name, [("copper", (200, 120, 70), "plain"), ("dark", (80, 50, 38), "plain"), ("glow", (255, 190, 90), "glow")], seed)
    s.box((5.5, 0, 5.5), (10.5, 16, 10.5), "copper")
    s.box((0, 5.5, 5.5), (16, 10.5, 10.5), "copper")
    s.box((5.5, 5.5, 0), (10.5, 10.5, 16), "copper")
    s.box((5, 5, 5), (11, 11, 11), "dark")
    s.box((6.5, 6.5, 6.5), (9.5, 9.5, 9.5), "glow")
    for k in (2, 13):
        s.box((5, k, 5), (11, k + 1, 11), "dark")
    return s


def cable_variant(name, mask, seed):
    """A cable seen on the redstone dust under it: a node and one arm per connected side (N=1 E=2 S=4 W=8)."""
    s = bs.Skin(name, [("copper", (200, 120, 70), "plain"), ("dark", (80, 50, 38), "plain"), ("glow", (255, 190, 90), "glow")], seed)
    s.box((5, 0, 5), (11, 6, 11), "dark")
    s.box((6, 1, 6), (10, 5, 10), "copper")
    s.box((7, 2, 7), (9, 4, 9), "glow")
    short = mask == 0
    arms = {1: ((6, 0, 0), (10, 4, 5)), 2: ((11, 0, 6), (16, 4, 10)), 4: ((6, 0, 11), (10, 4, 16)), 8: ((0, 0, 6), (5, 4, 10))}
    for bit, (a, b) in arms.items():
        if mask & bit:
            s.box(a, b, "copper")
        elif short:
            ia = tuple(a[i] + (0 if i != 0 and i != 2 else 0) for i in range(3))
            stub = {1: ((6.5, 0, 3), (9.5, 3, 5)), 2: ((11, 0, 6.5), (13, 3, 9.5)), 4: ((6.5, 0, 11), (9.5, 3, 13)), 8: ((3, 0, 6.5), (5, 3, 9.5))}[bit]
            s.box(stub[0], stub[1], "copper")
    return s


def solar_panel(name, seed):
    s = bs.Skin(name, [("frame", (150, 156, 164), "plain"), ("cell", (40, 80, 170), "glow"), ("dark", (50, 54, 62), "plain"),
                       ("line", (120, 150, 220), "plain")], seed)
    s.box((0, 0, 0), (16, 6, 16), "dark")
    s.box((0, 5, 0), (16, 6.5, 16), "frame")
    s.box((1, 6.5, 1), (15, 7.5, 15), "cell")
    s.box((7.6, 7.5, 1), (8.4, 7.9, 15), "line")
    s.box((1, 7.5, 7.6), (15, 7.9, 8.4), "line")
    return s


def miner(name, tier, seed):
    head = (80, 200, 210) if tier == 2 else (200, 130, 70)
    s = bs.Skin(name, [("body", (70, 74, 82), "plain"), ("dark", (36, 38, 44), "plain"), ("head", head, "plain"),
                       ("glow", shade(head, 1.1), "glow"), ("hazard", (230, 190, 50), "plain")], seed)
    s.box((0, 0, 0), (16, 16, 16), "body")
    s.box((0, 0, 0), (16, 2.5, 16), "dark")
    s.box((0, 13.5, 0), (16, 16, 16), "dark")
    s.box((0, 6, 0), (16, 8, 16), "hazard")
    s.box((3, 8.5, 16.0), (13, 13, 16.5), "dark")           # hopper mouth
    s.box((4, 9.5, 16.0), (12, 12, 16.7), "glow")
    s.box((5, 2.5, 16.0), (11, 5.5, 16.5), "head")          # the drill bit, seen from the front
    s.box((6.5, 3, 16.0), (9.5, 5, 16.7), "dark")
    if tier == 2:
        s.box((0, 8, 0), (16, 9, 16), "glow")
    return s


def pebble_model(name, seed):
    s = bs.Skin(name, [("stone", (150, 148, 146), "plain"), ("dark", (104, 102, 100), "plain"),
                       ("light", (190, 188, 184), "plain")], seed)
    s.box((3, 0, 5), (9, 4, 11), "stone", rot=("y", 20, [6, 2, 8]))
    s.box((8, 0, 7), (13, 3, 12), "dark", rot=("y", -25, [10, 2, 9]))
    s.box((4, 4, 6), (8, 5, 10), "light", rot=("y", 20, [6, 4, 8]))
    s.box((6, 0, 2), (10, 2.5, 5.5), "light", rot=("y", 40, [8, 1, 4]))
    return s


def fountain(name, seed):
    s = bs.Skin(name, [("stone", (120, 124, 130), "plain"), ("dark", (70, 74, 80), "plain"),
                       ("water", (60, 130, 220), "glow"), ("rim", (170, 174, 180), "plain")], seed)
    s.box((0, 0, 0), (16, 12, 16), "stone")
    s.box((0, 0, 0), (16, 2, 16), "dark")
    s.box((0, 11, 0), (16, 13, 16), "rim")
    s.box((3, 13, 3), (13, 14, 13), "water")
    s.box((7, 13, 7), (9, 18, 9), "rim")
    s.box((6.2, 18, 6.2), (9.8, 19, 9.8), "water")
    s.box((2, 3, 16.0), (14, 9, 16.5), "dark")
    s.box((4, 4, 16.0), (12, 8, 16.7), "water")
    return s


def spring_stone(name, seed):
    s = bs.Skin(name, [("stone", (90, 170, 190), "plain"), ("dark", (40, 100, 130), "plain"),
                       ("water", (110, 190, 255), "glow"), ("rim", (150, 215, 225), "plain")], seed)
    s.box((0, 0, 0), (16, 14, 16), "stone")
    s.box((0, 0, 0), (16, 2, 16), "dark")
    s.box((0, 12, 0), (16, 14, 16), "rim")
    s.box((3, 14, 3), (13, 15, 13), "water")
    s.box((2, 3, 16.0), (14, 11, 16.5), "dark")
    s.box((3.5, 4.5, 16.0), (12.5, 9.5, 16.7), "water")
    return s


def pick(name, head, edge, handle, seed):
    s = sm.Sword(name, [("head", head, "plain"), ("edge", edge, "plain"), ("handle", handle, "plain"),
                        ("wrap", shade(handle, 0.6), "plain")], seed)
    s.box((7.25, 0, 7.25), (8.75, 11, 8.75), "handle")
    for k in range(2):
        s.box((7.1, 1 + k * 1.3, 7.1), (8.9, 1.8 + k * 1.3, 8.9), "wrap")
    s.box((7.0, 11, 7.0), (9.0, 12.4, 9.0), "head")
    s.box((2, 11.2, 7.3), (14, 12.8, 8.7), "head")
    s.box((0.6, 10.2, 7.4), (3.4, 12.2, 8.6), "edge", rot=("z", 18, [3, 11, 8]))
    s.box((12.6, 10.2, 7.4), (15.4, 12.2, 8.6), "edge", rot=("z", -18, [13, 11, 8]))
    return s


TOOL_COLOURS = {
    "bronze": ((201, 132, 66), (244, 190, 110), (110, 70, 40), None),
    "rime": ((127, 200, 232), (214, 244, 255), (60, 80, 120), (200, 245, 255)),
    "ember": ((216, 104, 58), (255, 162, 58), (74, 40, 30), (255, 140, 40)),
    "void": ((74, 42, 144), (228, 106, 255), (28, 16, 56), (200, 100, 255)),
    "starcore": ((226, 215, 130), (255, 255, 255), (138, 106, 28), (255, 250, 220)),
}
BOW_DISPLAY = {  # the hand transforms of a vanilla bow, so it is aimed and drawn like one
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0]},
    "gui": {"rotation": [8, -20, 0], "scale": [0.95, 0.95, 0.95]},
}
TOOL_DISPLAY = {  # a sword's hold, smaller in first person since the heads are wider than a blade, bigger as an icon
    **sm.DISPLAY,
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.0, 1.13], "scale": [0.34, 0.34, 0.34]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.0, 1.13], "scale": [0.34, 0.34, 0.34]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, -0.5, 0], "scale": [0.95, 0.95, 0.95]},
}


def tool_materials(tier):
    head, edge, handle, glow = TOOL_COLOURS[tier]
    mats = [("head", head, "plain"), ("edge", edge, "plain"), ("handle", handle, "plain"),
            ("wrap", shade(handle, 0.6), "plain"), ("dark", shade(head, 0.62), "plain")]
    if glow:
        mats.append(("gem", glow, "glow"))
    return mats, bool(glow)


def axe(tier, seed):
    mats, glow = tool_materials(tier)
    s = sm.Sword(f"{tier}_axe", mats, seed)
    s.box((7.3, -1.5, 7.3), (8.7, 13.4, 8.7), "handle")
    s.box((7.05, -2.2, 7.05), (8.95, -1.0, 8.95), "dark")                  # butt of the handle
    for k in range(3):
        s.box((7.15, 0.2 + k * 1.3, 7.15), (8.85, 0.9 + k * 1.3, 8.85), "wrap")
    s.box((6.8, 9.2, 6.9), (9.2, 13.6, 9.1), "dark")                       # socket
    s.box((9.2, 9.8, 7.3), (11.4, 13.0, 8.7), "head")                      # cheek
    s.box((11.4, 8.8, 7.5), (13.4, 14.0, 8.5), "head")                     # blade, flaring out
    s.box((13.4, 7.8, 7.65), (15.2, 15.0, 8.35), "edge")                   # bit
    s.box((5.0, 10.4, 7.4), (6.8, 12.4, 8.6), "head")                      # poll
    s.box((4.2, 10.8, 7.5), (5.0, 12.0, 8.5), "edge")
    if glow:
        s.box((10.0, 10.8, 8.7), (11.2, 12.0, 8.95), "gem")                # gem on both cheeks
        s.box((10.0, 10.8, 7.05), (11.2, 12.0, 7.3), "gem")
    return s


def shovel(tier, seed):
    mats, glow = tool_materials(tier)
    s = sm.Sword(f"{tier}_shovel", mats, seed)
    s.box((7.35, -1.0, 7.35), (8.65, 11.2, 8.65), "handle")
    s.box((5.8, -2.4, 7.3), (10.2, -1.0, 8.7), "dark")                     # T grip
    for k in range(3):
        s.box((7.2, 1.0 + k * 1.3, 7.2), (8.8, 1.7 + k * 1.3, 8.8), "wrap")
    s.box((6.9, 10.4, 7.0), (9.1, 12.2, 9.0), "dark")                      # socket
    s.box((5.2, 12.2, 7.55), (10.8, 16.6, 8.45), "head")                   # blade
    s.box((5.8, 16.6, 7.6), (10.2, 17.8, 8.4), "head")
    s.box((6.8, 17.8, 7.65), (9.2, 18.8, 8.35), "edge")                    # tip
    s.box((4.8, 12.6, 7.65), (5.2, 16.2, 8.35), "edge")                    # rim
    s.box((10.8, 12.6, 7.65), (11.2, 16.2, 8.35), "edge")
    s.box((7.6, 12.2, 8.45), (8.4, 16.4, 8.7), "dark")                     # spine on both faces
    s.box((7.6, 12.2, 7.3), (8.4, 16.4, 7.55), "dark")
    if glow:
        s.box((7.3, 13.2, 8.7), (8.7, 14.6, 8.95), "gem")
        s.box((7.3, 13.2, 7.05), (8.7, 14.6, 7.3), "gem")
    return s


def bow(tier, pull, seed):
    """Written with the arrow flying towards +x, then mirrored and turned to the diagonal of a vanilla bow's sprite: the
    arrow points to the upper left, which the hand transforms aim forward. pull: 0 is at rest, 1 to 3 are the draw
    stages: the string comes back, the limbs bend and the arrow shows."""
    mats, glow = tool_materials(tier)
    mats = mats + [("string", (235, 235, 225), "plain"), ("shaft", (120, 90, 60), "plain"), ("tip", (200, 205, 212), "plain"),
                   ("fletch", (235, 235, 235), "plain")]
    s = sm.Sword(f"{tier}_bow_{pull}", mats, seed)

    def box(a, b, mat):
        s.box((16 - b[0], a[1], a[2]), (16 - a[0], b[1], b[2]), mat)

    box((9.2, 5.8, 7.2), (10.8, 10.2, 8.8), "handle")                      # grip
    for k in range(2):
        box((9.05, 6.4 + k * 2.0, 7.05), (10.95, 7.2 + k * 2.0, 8.95), "wrap")
    bend = pull * 0.14
    limbs = [(8.9, 10.2, 11.8, "head"), (7.7, 11.8, 13.4, "head"), (6.3, 13.4, 14.8, "edge"), (4.9, 14.8, 16.0, "head")]
    for i, (x, y0, y1, mat) in enumerate(limbs):
        shift = bend * i
        lo, hi = 16 - y1, 16 - y0                                          # the lower limb mirrors the upper one
        box((x - shift, y0, 7.4), (x + 1.5 - shift + bend, y1, 8.6), mat)
        box((x - shift, lo, 7.4), (x + 1.5 - shift + bend, hi, 8.6), mat)
    tip = 4.9 - bend * 3
    if glow:
        box((10.8, 7.4, 7.5), (11.25, 8.6, 8.5), "gem")                    # a gem on the riser
    if pull == 0:
        box((tip + 0.2, 0.6, 7.85), (tip + 0.55, 15.4, 8.15), "string")
    else:
        nock = tip - pull * 1.3
        steps = 6
        for k in range(steps):                                             # the string in steps from the tips to the nock
            x = tip + (nock - tip) * (k + 0.5) / steps
            y0, y1 = 15.4 - (7.4 / steps) * (k + 1), 15.4 - (7.4 / steps) * k
            box((x, y0, 7.85), (x + 0.45, y1, 8.15), "string")
            box((x, 16 - y1, 7.85), (x + 0.45, 16 - y0, 8.15), "string")
        box((nock, 7.7, 7.75), (nock + 0.5, 8.3, 8.25), "fletch")
        head = nock + 12.2
        box((nock + 0.3, 7.8, 7.8), (head, 8.2, 8.2), "shaft")             # the arrow
        box((nock + 0.5, 7.55, 7.9), (nock + 2.0, 8.45, 8.1), "fletch")
        box((head, 7.55, 7.75), (head + 1.0, 8.45, 8.25), "tip")
        box((head + 1.0, 7.8, 7.85), (head + 1.6, 8.2, 8.15), "tip")
    return s


def write_bow(tier, seed):
    """A bow's models and its definition: at rest, then the three draw stages of a vanilla bow."""
    kind, name = "tool", f"{tier}_bow"
    refs = []
    for pull in range(4):
        s = bow(tier, pull, seed + pull)
        three = f"vampyuyu:item/{kind}/{name}_{pull}"
        model = {"textures": {"0": three, "particle": three}, "gui_light": "front", "display": BOW_DISPLAY,
                 "elements": s.elements}
        (bs.PACK / "models" / "item" / kind / f"{name}_{pull}.json").write_text(json.dumps(model, indent=1) + "\n", encoding="utf-8")
        (bs.PACK / "textures" / "item" / kind / f"{name}_{pull}.png").write_bytes(bs.png(s.atlas()))
        refs.append({"type": "minecraft:model", "model": three})
    definition = {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": refs[0],
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05, "fallback": refs[1],
                    "entries": [{"threshold": 0.65, "model": refs[2]}, {"threshold": 0.9, "model": refs[3]}]}}}
    (bs.PACK / "items" / kind / f"{name}.json").write_text(json.dumps(definition, indent=1) + "\n", encoding="utf-8")


def main():
    for i, tier in enumerate(TOOL_COLOURS):
        write_bow(tier, 70 + i * 4)
    out = {
        **{f"tool/{tier}_axe": (axe(tier, 90 + i), TOOL_DISPLAY) for i, tier in enumerate(TOOL_COLOURS)},
        **{f"tool/{tier}_shovel": (shovel(tier, 95 + i), TOOL_DISPLAY) for i, tier in enumerate(TOOL_COLOURS)},
        "ore/bronzite": (crystals("bronzite", (96, 92, 90), (201, 132, 66), (255, 206, 130), 21), ITEM_DISPLAY),
        "ore/frostium": (crystals("frostium", (92, 100, 108), (127, 200, 232), (225, 247, 255), 22), ITEM_DISPLAY),
        "ore/sunstone": (crystals("sunstone", (110, 90, 70), (242, 193, 78), (255, 240, 170), 23), ITEM_DISPLAY),
        "ore/bronze_core": (core("bronze_core", (190, 140, 70), (230, 150, 70), (255, 210, 120), 24), ITEM_DISPLAY),
        "ore/ember_core": (core("ember_core", (110, 60, 40), (240, 90, 40), (255, 200, 80), 25), ITEM_DISPLAY),
        "ore/void_lens": (lens("void_lens", (80, 60, 130), (60, 40, 110), (200, 120, 255), 26), ITEM_DISPLAY),
        "ore/voidglass": (voidglass("voidglass", (110, 70, 180), (40, 24, 70), (210, 140, 255), 27), ITEM_DISPLAY),
        "ore/starcore": (starcore("starcore", (230, 200, 120), (255, 245, 200), (255, 250, 220), 28), ITEM_DISPLAY),
        "ore/pebble": (pebble_model("pebble", 40), ITEM_DISPLAY),
        "machine/fountain": (fountain("fountain", 41), BLOCK_DISPLAY),
        "machine/spring_stone": (spring_stone("spring_stone", 42), BLOCK_DISPLAY),
        "forge/forge": (forge("forge", 29), BLOCK_DISPLAY),
        "machine/coal_generator": (coal_generator("coal_generator", 30), BLOCK_DISPLAY),
        "machine/battery": (battery("battery", 31), BLOCK_DISPLAY),
        "machine/cable": (cable("cable", 32), BLOCK_DISPLAY),
        **{f"machine/cable_{m}": (cable_variant(f"cable_{m}", m, 60 + m), BLOCK_DISPLAY) for m in range(16)},
        "machine/solar_panel": (solar_panel("solar_panel", 33), BLOCK_DISPLAY),
        "machine/miner_1": (miner("miner_1", 1, 34), BLOCK_DISPLAY),
        "machine/miner_2": (miner("miner_2", 2, 35), BLOCK_DISPLAY),
        "tool/bronze_pick": (pick("bronze_pick", (201, 132, 66), (244, 190, 110), (110, 70, 40), 36), sm.DISPLAY),
        "tool/ember_pick": (pick("ember_pick", (216, 104, 58), (255, 162, 58), (74, 40, 30), 43), sm.DISPLAY),
        "tool/void_pick": (pick("void_pick", (74, 42, 144), (228, 106, 255), (28, 16, 56), 44), sm.DISPLAY),
        "tool/starcore_pick": (pick("starcore_pick", (226, 215, 130), (255, 255, 255), (138, 106, 28), 45), sm.DISPLAY),
        "tool/rime_pick": (pick("rime_pick", (127, 200, 232), (214, 244, 255), (60, 80, 120), 37), sm.DISPLAY),
    }
    for model_id, (s, display) in out.items():
        write(model_id, s, display, display is BLOCK_DISPLAY or model_id.endswith(("_axe", "_shovel")))
    print(f"wrote {len(out)} 3D item models")


if __name__ == "__main__":
    main()
