#!/usr/bin/env python3
"""True 3D models of every other custom item: ores, cores, lens, tools, the Forge and the machines.

    python3 resource-pack/art/item_models.py

Same method as sword_models.py and boss_skins.py (boxes on a 16x16 atlas of 4x4 materials). The machines and the
Forge are drawn as full blocks: in the world the plugin puts them on an item display over the vanilla block
(`BlockModels.java`), in the inventory they are the item. Deterministic.
"""
import json

import boss_skins as bs
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
    s.box((2, 3, 16.0), (14, 9, 16.5), "dark")
    s.box((3.5, 4, 16.0), (12.5, 7.5, 16.7), "glow")
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


def main():
    out = {
        "ore/bronzite": (crystals("bronzite", (96, 92, 90), (201, 132, 66), (255, 206, 130), 21), ITEM_DISPLAY),
        "ore/frostium": (crystals("frostium", (92, 100, 108), (127, 200, 232), (225, 247, 255), 22), ITEM_DISPLAY),
        "ore/sunstone": (crystals("sunstone", (110, 90, 70), (242, 193, 78), (255, 240, 170), 23), ITEM_DISPLAY),
        "ore/bronze_core": (core("bronze_core", (190, 140, 70), (230, 150, 70), (255, 210, 120), 24), ITEM_DISPLAY),
        "ore/ember_core": (core("ember_core", (110, 60, 40), (240, 90, 40), (255, 200, 80), 25), ITEM_DISPLAY),
        "ore/void_lens": (lens("void_lens", (80, 60, 130), (60, 40, 110), (200, 120, 255), 26), ITEM_DISPLAY),
        "ore/voidglass": (voidglass("voidglass", (110, 70, 180), (40, 24, 70), (210, 140, 255), 27), ITEM_DISPLAY),
        "ore/starcore": (starcore("starcore", (230, 200, 120), (255, 245, 200), (255, 250, 220), 28), ITEM_DISPLAY),
        "forge/forge": (forge("forge", 29), BLOCK_DISPLAY),
        "machine/coal_generator": (coal_generator("coal_generator", 30), BLOCK_DISPLAY),
        "machine/battery": (battery("battery", 31), BLOCK_DISPLAY),
        "machine/cable": (cable("cable", 32), BLOCK_DISPLAY),
        "machine/solar_panel": (solar_panel("solar_panel", 33), BLOCK_DISPLAY),
        "machine/miner_1": (miner("miner_1", 1, 34), BLOCK_DISPLAY),
        "machine/miner_2": (miner("miner_2", 2, 35), BLOCK_DISPLAY),
        "tool/bronze_pick": (pick("bronze_pick", (201, 132, 66), (244, 190, 110), (110, 70, 40), 36), sm.DISPLAY),
        "tool/rime_pick": (pick("rime_pick", (127, 200, 232), (214, 244, 255), (60, 80, 120), 37), sm.DISPLAY),
    }
    for model_id, (s, display) in out.items():
        write(model_id, s, display, display is BLOCK_DISPLAY)
    print(f"wrote {len(out)} 3D item models")


if __name__ == "__main__":
    main()
