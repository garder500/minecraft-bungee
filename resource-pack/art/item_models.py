#!/usr/bin/env python3
"""The 3D models of the custom items that are not swords: ores, cores, lens, tools, bows, the Forge and the machines.

    python3 resource-pack/art/item_models.py

Two kinds. The machines and the Forge are true blocks made of boxes on a 16x16 atlas of 4x4 materials (same method as
boss_skins.py): in the world the plugin puts them on an item display over the vanilla block (`BlockModels.java`), in
the inventory they are the item. Every other item is its flat sprite grown into a slim bevelled slab (sprite_models.py),
so the hand, the ground and the inventory show the same drawing: the imported sprites (<id>_gui.png: ores, swords,
pickaxes), the sprites other scripts paint (cores, lens, pebble, sword skins), and for the axes, shovels and bows, which
never had a sprite, one drawn here from their box model. Deterministic.
"""
import json

import boss_skins as bs
from imported import gui_model, has_gui, imported
import sprite_models
import sword_models as sm

PACK = bs.PACK
Y = ("y", 45, [8, 8, 8])

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


def write(model_id, s, display, block=False):
    """3D model as <name>_3d; in the GUI (inventory, hotbar) a non-block item shows its flat sprite instead."""
    kind, name = model_id.split("/")
    if imported(model_id) and kind in ("ore", "weapon", "tool", "armor"):  # flat imported sprite everywhere
        return
    if imported(f"{model_id}_3d"):  # a textured block model from block_models.py
        return
    if has_gui(model_id):  # grown from its imported sprite instead (write_sprite)
        return
    three = f"vampyuyu:item/{kind}/{name}_3d"
    model = {"textures": {"0": three, "particle": three},
             "gui_light": "front", "display": display, "elements": s.elements}
    flat = {"model": {"type": "minecraft:model", "model": gui_model(model_id)}}
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


def flat_model(model_id, handheld):
    """The flat GUI model of a sprite this script made itself (items/<kind>/<name>.png)."""
    kind, name = model_id.split("/")
    parent = "minecraft:item/handheld" if handheld else "minecraft:item/generated"
    (bs.PACK / "models" / "item" / kind / f"{name}.json").write_text(json.dumps(
        {"parent": parent, "textures": {"layer0": f"vampyuyu:item/{model_id}"}}, indent=2) + "\n", encoding="utf-8")
    return f"vampyuyu:item/{model_id}"


def slab(sprite_id, display, thickness):
    """The bevelled slab grown from textures/item/<sprite_id>.png (sprite_models.py), as a model."""
    sprite = f"vampyuyu:item/{sprite_id}"
    return {"textures": {"0": sprite, "particle": sprite}, "gui_light": "front", "display": display,
            "elements": sprite_models.elements(bs.PACK / "textures" / "item" / f"{sprite_id}.png", thickness)}


def gui_or_hand(flat, three):
    """An item definition piece: the flat sprite in the GUI, the slab everywhere else."""
    return {"type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": flat}}],
            "fallback": {"type": "minecraft:model", "model": three}}


def write_sprite(model_id, sprite_id=None, flat=None):
    """The 3D model of an item, grown out of its flat sprite: the same drawing in the hand, on the ground and in the
    inventory (where the flat sprite itself is shown). One look for all of them: a flat vanilla-like item with a
    one-pixel bevel, the ores and cores a little thicker than the blades and tools. sprite_id: the texture to grow
    (default the imported <id>_gui); flat: the GUI model (default the one of the imported sprite)."""
    kind, name = model_id.split("/")
    handheld = kind in ("weapon", "tool", "skin")
    thickness, display = (0.8, SPRITE_HANDHELD) if handheld else (1.3, SPRITE_DISPLAY)
    model = slab(sprite_id or f"{model_id}_gui", display, thickness)
    definition = {"model": gui_or_hand(flat or gui_model(model_id), f"vampyuyu:item/{kind}/{name}_3d")}
    for path, data in ((bs.PACK / "models" / "item" / kind / f"{name}_3d.json", model),
                       (bs.PACK / "items" / kind / f"{name}.json", definition)):
        path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    (bs.PACK / "textures" / "item" / kind / f"{name}_3d.png").unlink(missing_ok=True)  # the box atlas it replaced


def write_drawn(model_id, s):
    """An item that never had a painted sprite (axe, shovel): its sprite is drawn from its box model s, seen from the
    front, then it is grown like the others."""
    kind, name = model_id.split("/")
    pixels, _ = sprite_models.render(s.elements, s.atlas())
    (bs.PACK / "textures" / "item" / kind / f"{name}.png").write_bytes(bs.png(pixels))
    write_sprite(model_id, sprite_id=model_id, flat=flat_model(model_id, True))


def shade(c, f):
    return tuple(min(255, int(v * f)) for v in c)


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


def hx(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


# head, edge, handle, glow: each tier takes the five-tone palette of its Realm (shadow, base, light, highlight, glow);
# the glow is the one saturated colour and marks the motif (Flux cyan, lava vein, Watcher's eye, turquoise inlay, rift)
TOOL_COLOURS = {
    "bronze": (hx("#c98442"), hx("#f2c14e"), hx("#4a2a12"), hx("#4af0ff")),      # Flux machinery
    "rime": (hx("#7fc8e8"), hx("#e6f7ff"), hx("#1f3557"), hx("#b6a8ff")),        # Frozen Heights
    "ember": (hx("#c8461e"), hx("#ff8a2a"), hx("#2a1a2e"), hx("#ff8a2a")),       # Cracked Ashes
    "void": (hx("#3a1a6a"), hx("#e0c8ff"), hx("#0e0a1a"), hx("#ff4a8a")),        # Astral Interiority
    "starcore": (hx("#f2c14e"), hx("#fff1b0"), hx("#5a3a12"), hx("#3fb6a8")),    # Citadel of Golden Sands
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
def tool_materials(tier):
    head, edge, handle, glow = TOOL_COLOURS[tier]
    mats = [("head", head, "plain"), ("edge", edge, "plain"), ("handle", handle, "plain"),
            ("wrap", shade(handle, 0.6), "plain"), ("dark", shade(head, 0.62), "plain")]
    if glow:
        mats.append(("gem", glow, "glow"))
    return mats, bool(glow)


OBSIDIAN = hx("#3a2440")
RIFT = hx("#1c0f3a")


def tier_materials(tier):
    """The tool materials plus what the head of this Realm needs: obsidian for Cracked Ashes, a darker void body..."""
    mats, glow = tool_materials(tier)
    body = {"ember": OBSIDIAN, "void": RIFT}.get(tier)
    if body:
        mats = mats + [("body", body, "plain")]
    return mats, glow


def decal(s, x0, y0, x1, y1, mat, surf, t=0.15):
    """A thin patch on both faces of a head whose faces are at z = 8 -/+ surf (the front is the +z one)."""
    s.box((x0, y0, 8 + surf), (x1, y1, 8 + surf + t), mat)
    s.box((x0, y0, 8 - surf - t), (x1, y1, 8 - surf), mat)


def gem_socket(s, tier, x0, y0, x1, y1, z):
    """The gem sits at the same place on every tool of a tier: a dark bezel on the face, then the gem. Starcore's is a
    four-point star, the others a square cabochon. z: the face the bezel rests on."""
    s.box((x0 - 0.45, y0 - 0.45, z), (x1 + 0.45, y1 + 0.45, z + 0.15), "dark")
    s.box((x0 - 0.45, y0 - 0.45, 16 - z - 0.15), (x1 + 0.45, y1 + 0.45, 16 - z), "dark")
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    for zz in (z + 0.15, 16 - z - 0.15 - 0.25):
        if tier == "starcore":
            s.box((x0 - 0.2, cy - 0.3, zz), (x1 + 0.2, cy + 0.3, zz + 0.25), "gem")
            s.box((cx - 0.3, y0 - 0.2, zz), (cx + 0.3, y1 + 0.2, zz + 0.25), "gem")
            s.box((cx - 0.55, cy - 0.55, zz), (cx + 0.55, cy + 0.55, zz + 0.3), "gem")
        else:
            s.box((x0, y0, zz), (x1, y1, zz + 0.25), "gem")


def axe(tier, seed):
    mats, glow = tier_materials(tier)
    s = sm.Sword(f"{tier}_axe", mats, seed)
    s.box((7.3, -1.5, 7.3), (8.7, 13.4, 8.7), "handle")
    s.box((7.05, -2.2, 7.05), (8.95, -1.0, 8.95), "dark")                  # butt of the handle
    for k in range(3):
        s.box((7.15, 0.2 + k * 1.3, 7.15), (8.85, 0.9 + k * 1.3, 8.85), "wrap")
    s.box((6.8, 9.2, 6.9), (9.2, 13.6, 9.1), "dark")                       # socket
    s.box((9.2, 9.8, 7.3), (11.4, 13.0, 8.7), "body" if tier in ("ember", "void") else "head")  # cheek
    s.box((5.0, 10.4, 7.4), (6.8, 12.4, 8.6), "head")                      # poll
    s.box((4.2, 10.8, 7.5), (5.0, 12.0, 8.5), "edge")
    gem_socket(s, tier, 10.0, 10.8, 11.2, 12.0, 8.7)                       # the same place on every tier
    if tier == "bronze":                                                   # riveted Flux bronze: plate, seam, rivets
        s.box((11.4, 8.8, 7.5), (13.4, 14.0, 8.5), "head")
        s.box((13.4, 7.8, 7.65), (15.2, 15.0, 8.35), "edge")
        s.box((11.4, 8.8, 7.45), (11.9, 14.0, 8.55), "dark")                # the seam of the riveted plate
        for y in (9.4, 13.2):
            decal(s, 12.3, y, 13.0, y + 0.7, "edge", 0.5)
        decal(s, 13.8, 11.0, 14.6, 11.8, "dark", 0.35)
    elif tier == "ember":                                                  # jagged obsidian with lava cracks
        s.box((11.4, 9.4, 7.5), (13.0, 13.6, 8.5), "body")
        for y0, y1, x1 in ((13.2, 15.8, 14.6), (10.8, 13.0, 15.8), (8.4, 10.6, 14.4), (6.6, 8.2, 13.8)):
            s.box((13.0, y0, 7.6), (x1, y1, 8.4), "body")                  # a tooth each, no two alike
            s.box((x1, y0 + 0.5, 7.7), (x1 + 0.7, y1 - 0.5, 8.3), "edge")  # the tips glow hot
        decal(s, 11.8, 10.0, 12.2, 13.2, "gem", 0.5)                       # cracks
        decal(s, 12.2, 11.2, 13.0, 11.6, "gem", 0.5)
        decal(s, 12.6, 9.6, 13.0, 11.4, "gem", 0.5)
    elif tier == "rime":                                                   # crystal teeth
        s.box((11.4, 9.0, 7.55), (12.8, 13.8, 8.45), "head")
        for i, y in enumerate((7.6, 9.4, 11.2, 13.0)):
            long = (1.4, 2.2, 2.6, 1.8)[i]
            s.box((12.8, y, 7.65), (14.2, y + 1.6, 8.35), "head")
            s.box((14.2, y + 0.3, 7.75), (14.2 + long, y + 1.3, 8.25), "edge")
            s.box((14.2 + long, y + 0.6, 7.85), (14.2 + long + 0.6, y + 1.0, 8.15), "edge")
    elif tier == "starcore":                                               # gold, broad, with sun-ray horns
        s.box((11.4, 8.4, 7.5), (13.6, 14.4, 8.5), "head")
        s.box((13.6, 7.6, 7.65), (15.4, 15.2, 8.35), "edge")
        s.box((12.4, 14.4, 7.65), (14.2, 15.8, 8.35), "head")
        s.box((12.4, 7.0, 7.65), (14.2, 8.4, 8.35), "head")
        s.box((12.9, 15.8, 7.75), (13.7, 16.6, 8.25), "edge")
        s.box((12.9, 6.2, 7.75), (13.7, 7.0, 8.25), "edge")
    elif tier == "void":                                                   # dark, split by a rift with a pink eye
        s.box((11.4, 9.0, 7.5), (13.0, 14.2, 8.5), "body")
        s.box((13.0, 11.8, 7.6), (15.4, 15.2, 8.4), "body")                # upper horn
        s.box((13.0, 7.8, 7.6), (14.6, 10.0, 8.4), "body")                 # lower horn, the rift between the two
        s.box((13.0, 10.0, 7.7), (13.5, 11.8, 8.3), "edge")
        decal(s, 11.9, 10.3, 12.9, 11.5, "edge", 0.5)                      # the eye: lid, rift, pupil
        decal(s, 12.1, 10.6, 12.7, 11.2, "gem", 0.62)
        decal(s, 12.25, 10.7, 12.55, 11.1, "dark", 0.77)
    return s


def shovel(tier, seed):
    mats, glow = tier_materials(tier)
    s = sm.Sword(f"{tier}_shovel", mats, seed)
    s.box((7.35, -1.0, 7.35), (8.65, 11.2, 8.65), "handle")
    s.box((5.8, -2.4, 7.3), (10.2, -1.0, 8.7), "dark")                     # T grip
    for k in range(3):
        s.box((7.2, 1.0 + k * 1.3, 7.2), (8.8, 1.7 + k * 1.3, 8.8), "wrap")
    s.box((6.9, 10.4, 7.0), (9.1, 12.2, 9.0), "dark")                      # socket
    body = "body" if tier in ("ember", "void") else "head"
    if tier == "rime":
        s.box((5.8, 12.2, 7.55), (10.2, 15.6, 8.45), body)                 # narrower: the crystals do the rest
        for i, x in enumerate((5.8, 6.9, 8.0, 9.1)):
            top = (17.2, 18.6, 19.0, 17.8)[i]
            s.box((x, 15.6, 7.65), (x + 1.1, top - 1.0, 8.35), "head")
            s.box((x + 0.15, top - 1.0, 7.75), (x + 0.95, top, 8.25), "edge")
    else:
        s.box((5.2, 12.2, 7.55), (10.8, 16.6, 8.45), body)                 # blade
        s.box((4.8, 12.6, 7.65), (5.2, 16.2, 8.35), "edge")                # rim
        s.box((10.8, 12.6, 7.65), (11.2, 16.2, 8.35), "edge")
    if tier == "bronze":                                                   # riveted plate
        s.box((5.8, 16.6, 7.6), (10.2, 17.8, 8.4), "head")
        s.box((6.8, 17.8, 7.65), (9.2, 18.8, 8.35), "edge")
        s.box((5.2, 12.2, 7.5), (10.8, 12.7, 8.5), "dark")
        for x in (5.7, 9.9):
            for y in (13.0, 15.6):
                decal(s, x, y, x + 0.6, y + 0.6, "edge", 0.45)
    elif tier == "ember":                                                  # jagged tip, lava cracks
        for x0, x1, top in ((5.2, 6.8, 18.4), (7.2, 8.8, 19.4), (9.2, 10.8, 17.8)):
            s.box((x0, 16.6, 7.6), (x1, top - 0.8, 8.4), "body")
            s.box((x0 + 0.2, top - 0.8, 7.7), (x1 - 0.2, top, 8.3), "edge")
        decal(s, 6.0, 14.8, 6.35, 16.4, "gem", 0.45)
        decal(s, 9.5, 13.6, 9.85, 15.6, "gem", 0.45)
        decal(s, 6.0, 15.0, 7.2, 15.35, "gem", 0.45)
    elif tier == "starcore":                                               # gold, the point of a star
        s.box((5.8, 16.6, 7.6), (10.2, 17.6, 8.4), "head")
        s.box((6.8, 17.6, 7.65), (9.2, 18.6, 8.35), "head")
        s.box((7.4, 18.6, 7.7), (8.6, 19.8, 8.3), "edge")
        decal(s, 6.2, 16.0, 6.8, 16.5, "edge", 0.45)
        decal(s, 9.2, 16.0, 9.8, 16.5, "edge", 0.45)
    elif tier == "void":                                                   # dark, a rift eye under a split tip
        s.box((5.8, 16.6, 7.6), (10.2, 17.8, 8.4), "body")
        s.box((5.8, 17.8, 7.65), (7.4, 19.4, 8.35), "body")
        s.box((8.6, 17.8, 7.65), (10.2, 18.8, 8.35), "body")
        s.box((7.4, 17.8, 7.7), (8.6, 18.4, 8.3), "edge")
        decal(s, 6.4, 16.8, 9.6, 17.7, "edge", 0.4)
        decal(s, 6.8, 17.0, 9.2, 17.5, "gem", 0.55)
        decal(s, 7.6, 17.05, 8.4, 17.45, "dark", 0.7)
    s.box((7.6, 12.2, 8.45), (8.4, 16.4, 8.7), "dark")                     # spine on both faces
    s.box((7.6, 12.2, 7.3), (8.4, 16.4, 7.55), "dark")
    gem_socket(s, tier, 7.3, 13.2, 8.7, 14.6, 8.45)
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
    """A bow's models and its definition: at rest, then the three draw stages of a vanilla bow. Each stage is drawn as
    a sprite from its box model and grown into a slab; the GUI shows the flat sprite of the stage."""
    kind, name = "tool", f"{tier}_bow"
    refs = []
    for pull in range(4):
        s = bow(tier, pull, seed + pull)
        stage = f"{kind}/{name}_{pull}"
        pixels, _ = sprite_models.render(s.elements, s.atlas())
        (bs.PACK / "textures" / "item" / kind / f"{name}_{pull}.png").write_bytes(bs.png(pixels))
        (bs.PACK / "models" / "item" / kind / f"{name}_{pull}_flat.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/{stage}"}}, indent=2) + "\n",
            encoding="utf-8")
        (bs.PACK / "models" / "item" / kind / f"{name}_{pull}.json").write_text(
            json.dumps(slab(stage, BOW_DISPLAY, 0.8), indent=1) + "\n", encoding="utf-8")
        refs.append(gui_or_hand(f"vampyuyu:item/{stage}_flat", f"vampyuyu:item/{stage}"))
    definition = {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": refs[0],
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05, "fallback": refs[1],
                    "entries": [{"threshold": 0.65, "model": refs[2]}, {"threshold": 0.9, "model": refs[3]}]}}}
    (bs.PACK / "items" / kind / f"{name}.json").write_text(json.dumps(definition, indent=1) + "\n", encoding="utf-8")


def main():
    for i, tier in enumerate(TOOL_COLOURS):
        write_bow(tier, 70 + i * 4)
        write_drawn(f"tool/{tier}_axe", axe(tier, 90 + i))
        write_drawn(f"tool/{tier}_shovel", shovel(tier, 95 + i))
    out = {
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
    }
    for model_id, (s, display) in out.items():  # the machines and the Forge stay true blocks
        write(model_id, s, display, True)
    sprites = sorted(f"{p.parent.name}/{p.name[:-len('_gui.png')]}" for p in (PACK / "textures/item").glob("*/*_gui.png"))
    for model_id in sprites:  # the imported ores, swords and pickaxes
        write_sprite(model_id)
    # the items whose flat sprite another script paints: the cores, the lens, the pebble and the sword skins
    painted = [f"ore/{n}" for n in ("bronze_core", "ember_core", "void_lens", "pebble")]
    painted += sorted(f"skin/{p.name[:-len('_3d.json')]}" for p in (PACK / "models/item/skin").glob("*_3d.json"))
    for model_id in painted:
        write_sprite(model_id, sprite_id=model_id, flat=f"vampyuyu:item/{model_id}")
    print(f"wrote {len(out)} block models, {len(TOOL_COLOURS) * 6} drawn tool sprites and "
          f"{len(sprites) + len(painted)} more sprite models")


if __name__ == "__main__":
    main()
