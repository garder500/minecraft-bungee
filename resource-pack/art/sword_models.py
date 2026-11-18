#!/usr/bin/env python3
"""True 3D sword models: blade with a fuller and edges, guard, gem, grip and pommel, all made of boxes.

    python3 resource-pack/art/sword_models.py

Writes, for the two weapons and the six skins,
  pack/assets/vampyuyu/models/item/<kind>/<id>.json    (boxes, hand transforms of a vanilla sword)
  pack/assets/vampyuyu/textures/item/<kind>/<id>.png   (16x16 atlas of 4x4 materials)
  pack/assets/vampyuyu/items/<kind>/<id>.json
The sword is drawn upright and the whole model is turned 45 degrees about z, so it sits on the diagonal like the flat
sprite of a vanilla sword and the vanilla `handheld` hand transforms hold it right. Deterministic.
"""
import json
import math
import pathlib

import boss_skins as bs
from imported import gui_model, has_gui, imported

PACK = bs.PACK
TURN = ("z", -45, [8, 8, 8])

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


class Sword(bs.Skin):
    def box(self, a, b, mat, top=None, rot=TURN):
        super().box(a, b, mat, top, rot)

    def write_to(self, kind, name):
        """3D model as <name>_3d; the GUI keeps the flat sprite (see item_models.write)."""
        if imported(f"{kind}/{name}"):  # shown as its imported flat sprite everywhere (import_textures.py)
            return
        if has_gui(f"{kind}/{name}") or (kind == "skin" and (PACK / "models/item/skin" / f"{name}_3d.json").exists()):
            return  # its 3D model is grown from its flat sprite (item_models.write_sprite)
        three = f"vampyuyu:item/{kind}/{name}_3d"
        model = {"textures": {"0": three, "particle": three},
                 "gui_light": "front", "display": DISPLAY, "elements": self.elements}
        definition = {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                                "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": gui_model(f"{kind}/{name}")}}],
                                "fallback": {"type": "minecraft:model", "model": three}}}
        for path, data in (
                (PACK / "models" / "item" / kind / f"{name}_3d.json", model),
                (PACK / "items" / kind / f"{name}.json", definition)):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
        tex = PACK / "textures" / "item" / kind / f"{name}_3d.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        tex.write_bytes(bs.png(self.atlas()))


def build(name, blade, edge, fuller, guard, grip, gem, glow=None, length=10.0, width=2.6, guard_w=6.0, seed=1,
          kind="plain"):
    """kind is the Realm silhouette: ashes (split obsidian blade, lava fuller), frost (faceted crystal), sands (scarab
    guard, gold pommel with turquoise inlay), astral (eye in the guard, rift notch), star (star pommel, night blade
    with gold edges)."""
    mats = [("blade", blade, "plain"), ("edge", edge, "plain"), ("fuller", fuller, "glow" if glow else "plain"),
            ("guard", guard, "plain"), ("grip", grip, "plain"), ("gem", gem, "glow"), ("pommel", guard, "plain"),
            ("wrap", tuple(int(v * 0.6) for v in grip), "plain"),
            ("glow", glow or gem, "glow"), ("inlay", (64, 224, 208), "glow"),
            ("white", (240, 235, 255), "plain"), ("dark", (12, 8, 24), "plain")]
    s = Sword(name, mats, seed)
    hw = width / 2
    top = 5.0 + length

    def seg(y0, y1, xl, xr, z=0.0, edges=True):
        """One piece of blade with its two edge strips."""
        s.box((xl, y0, 7.25 + z), (xr, y1, 8.75 - z), "blade")
        if edges:
            s.box((xl, y0, 7.15 + z), (xl + 0.55, y1, 8.85 - z), "edge")
            s.box((xr - 0.55, y0, 7.15 + z), (xr, y1, 8.85 - z), "edge")

    def both(a, b, mat, zf=(8.75, 7.05), th=0.2):
        """A thin plate on both faces of the blade: a=(x0,y0), b=(x1,y1)."""
        s.box((a[0], a[1], zf[0]), (b[0], b[1], zf[0] + th), mat)
        s.box((a[0], a[1], zf[1]), (b[0], b[1], zf[1] + th), mat)

    # ---- pommel and grip
    if kind == "sands":                                                       # gold pommel with turquoise inlay
        s.box((6.7, -2.5, 6.7), (9.3, -0.4, 9.3), "pommel")
        s.box((6.9, -2.3, 9.3), (9.1, -0.6, 9.5), "pommel")
        s.box((7.35, -2.0, 9.5), (8.65, -0.9, 9.7), "inlay")
        s.box((7.35, -2.0, 6.3), (8.65, -0.9, 6.5), "inlay")
        s.box((7.4, -2.9, 7.4), (8.6, -2.5, 8.6), "pommel")
    elif kind == "star":                                                      # eight-point star pommel
        cx, cy = 8.0, -1.3
        ca, sa = math.cos(math.radians(-45)), math.sin(math.radians(-45))
        dx, dy = 0.0, cy - 8.0
        ax, ay = 8.0 + dx * ca - dy * sa, 8.0 + dx * sa + dy * ca                # the star centre after the turn
        s.box((7.55, cy - 1.7, 7.1), (8.45, cy + 1.7, 8.9), "pommel")           # four long points
        s.box((cx - 1.7, cy - 0.45, 7.1), (cx + 1.7, cy + 0.45, 8.9), "pommel")
        s.box((ax - 0.45, ay - 1.2, 7.2), (ax + 0.45, ay + 1.2, 8.8), "pommel", rot=None)   # four short points
        s.box((ax - 1.2, ay - 0.45, 7.2), (ax + 1.2, ay + 0.45, 8.8), "pommel", rot=None)
        s.box((7.5, cy - 0.5, 6.85), (8.5, cy + 0.5, 9.15), "gem")
    else:
        s.box((6.9, -2.2, 7.1), (9.1, -0.4, 8.9), "pommel")
    s.box((7.35, -0.4, 7.4), (8.65, 3.4, 8.6), "grip")
    for k in range(3):
        s.box((7.25, 0.0 + k * 1.2, 7.3), (8.75, 0.5 + k * 1.2, 8.7), "wrap")

    # ---- guard
    s.box((8 - guard_w / 2, 3.4, 6.7), (8 + guard_w / 2, 5.0, 9.3), "guard")
    s.box((8 - guard_w / 2 - 0.8, 3.0, 7.2), (8 - guard_w / 2, 5.4, 8.8), "guard")
    s.box((8 + guard_w / 2, 3.0, 7.2), (8 + guard_w / 2 + 0.8, 5.4, 8.8), "guard")
    if kind == "sands":                                                       # scarab: shell, head, wing tips, legs
        s.box((8 - guard_w / 2 - 0.8, 5.4, 7.4), (8 - guard_w / 2 - 0.1, 6.6, 8.6), "guard")   # raised wing tips
        s.box((8 + guard_w / 2 + 0.1, 5.4, 7.4), (8 + guard_w / 2 + 0.8, 6.6, 8.6), "guard")
        s.box((6.9, 2.9, 6.5), (9.1, 5.5, 9.5), "guard")                      # shell
        s.box((7.3, 3.2, 9.5), (8.7, 5.1, 9.75), "inlay")
        s.box((7.3, 3.2, 6.25), (8.7, 5.1, 6.5), "inlay")
        s.box((7.9, 3.2, 9.75), (8.1, 5.1, 9.95), "guard")                    # wing split line
        s.box((7.9, 3.2, 6.05), (8.1, 5.1, 6.25), "guard")
        s.box((7.4, 5.5, 6.9), (8.6, 6.2, 9.1), "guard")                      # head
        s.box((6.4, 2.6, 7.6), (6.9, 3.4, 8.4), "guard")                      # legs
        s.box((9.1, 2.6, 7.6), (9.6, 3.4, 8.4), "guard")
    else:
        s.box((7.3, 3.5, 9.3), (8.7, 4.9, 9.7), "gem")
        s.box((7.3, 3.5, 6.3), (8.7, 4.9, 6.7), "gem")
    if kind == "astral":                                                      # an eye in the guard, both faces
        for z0, z1, zi in ((9.3, 9.5, 9.5), (6.5, 6.7, 6.3)):
            sg = 1 if zi > 8 else -1
            s.box((6.7, 3.9, z0), (9.3, 4.6, z1), "white")
            s.box((7.2, 3.5, z0), (8.8, 5.0, z1), "white")
            a, b = (7.55, 3.6, zi), (8.45, 4.9, zi + 0.25 * sg)
            s.box((a[0], a[1], min(a[2], b[2])), (b[0], b[1], max(a[2], b[2])), "gem")
            a, b = (7.85, 3.8, zi + 0.25 * sg), (8.15, 4.7, zi + 0.45 * sg)
            s.box((a[0], a[1], min(a[2], b[2])), (b[0], b[1], max(a[2], b[2])), "dark")

    # ---- blade
    if kind == "ashes":                                                       # split obsidian blade, lava fuller
        gap = 0.35
        s.box((8 - hw, 5.0, 7.25), (8 - gap, top + 1.4, 8.75), "blade")
        s.box((8 - hw, 5.0, 7.15), (8 - hw + 0.5, top + 1.4, 8.85), "edge")
        s.box((8 + gap, 5.0, 7.25), (8 + hw, top - 1.8, 8.75), "blade")       # the right half broke off lower
        s.box((8 + hw - 0.5, 5.0, 7.15), (8 + hw, top - 1.8, 8.85), "edge")
        s.box((8 - hw + 0.3, top + 1.4, 7.4), (8 - gap, top + 2.8, 8.6), "edge")    # tip on the left half
        s.box((8 + gap, top - 1.8, 7.4), (8 + hw - 0.2, top - 1.0, 8.6), "edge")   # jagged break
        s.box((8 + gap + 0.3, top - 1.0, 7.5), (8 + hw - 0.4, top - 0.5, 8.5), "edge")
        s.box((8 - gap, 5.4, 7.45), (8 + gap, top + 1.2, 8.55), "glow")       # lava in the split
        s.box((8 - gap - 0.1, 5.3, 8.3), (8 + gap + 0.1, top - 2.0, 8.9), "glow")
        s.box((8 - gap - 0.1, 5.3, 7.1), (8 + gap + 0.1, top - 2.0, 7.7), "glow")
        for k, y in enumerate((6.2, 8.2, 10.0)):                              # lava cracks
            x = 8 - hw + 0.55 if k % 2 == 0 else 8 + gap + 0.35
            both((x, y), (x + 0.5, y + 1.0), "glow")
    elif kind == "frost":                                                     # faceted crystal
        ys = [5.0 + (top + 2.6 - 5.0) * i / 7 for i in range(8)]
        prof = [0.55, 0.8, 1.0, 0.9, 0.8, 0.6, 0.35]
        for i, f in enumerate(prof):
            w = hw * f * 1.25
            s.box((8 - w, ys[i], 7.3 + 0.05 * i), (8, ys[i + 1], 8.7 - 0.05 * i), "edge")     # lit facet
            s.box((8, ys[i], 7.3 + 0.05 * i), (8 + w, ys[i + 1], 8.7 - 0.05 * i), "blade")     # shadow facet
        both((7.7, 5.7), (8.3, top - 0.5), "fuller")
        for sgn in (-1, 1):                                                   # crystal shards at the base
            x0 = 8 + sgn * (hw * 1.1)
            xa, xb = sorted((x0, x0 + sgn * 0.9))
            s.box((xa, 4.9, 7.5), (xb, 6.9, 8.5), "blade")
            xa, xb = sorted((x0 + sgn * 0.6, x0 + sgn * 1.4))
            s.box((xa, 4.9, 7.6), (xb, 5.9, 8.4), "edge")
        for k, y in enumerate((7.4, 9.4)):                                    # side crystals
            s.box((8 + hw * 1.1, y, 7.6), (8 + hw * 1.1 + 1.0, y + 1.2, 8.4), "fuller")
            s.box((8 - hw * 1.1 - 1.0, y + 1.0, 7.6), (8 - hw * 1.1, y + 2.0, 8.4), "fuller")
    elif kind == "astral":                                                    # rift notch with light behind it
        ny0, ny1 = 5.0 + length * 0.5, 5.0 + length * 0.5 + 1.8
        d = min(0.9, width * 0.3)
        seg(5.0, ny0, 8 - hw, 8 + hw)
        seg(ny0, ny1, 8 - hw + d, 8 + hw)
        seg(ny1, top, 8 - hw, 8 + hw)
        s.box((8 - hw + 0.1, ny0 + 0.3, 7.5), (8 - hw + d + 0.2, ny1 - 0.3, 8.5), "glow")
        s.box((8 - hw - 0.35, ny0 - 0.7, 7.5), (8 - hw, ny0, 8.5), "glow")      # the rift leaks light
        s.box((8 - hw - 0.35, ny1, 7.5), (8 - hw, ny1 + 0.7, 8.5), "glow")
        both((7.55, 5.6), (8.45, ny0 - 0.6), "fuller")
        both((7.55, ny1 + 0.6), (8.45, top - 0.6), "fuller")
        s.box((8 - hw + 0.5, top, 7.35), (8 + hw - 0.5, top + 1.6, 8.65), "blade")
        s.box((8 - hw + 1.0, top + 1.6, 7.45), (8 + hw - 1.0, top + 2.6, 8.55), "edge")
    elif kind == "star":                                                      # night blade with gold edges
        e = 0.7
        s.box((8 - hw, 5.0, 7.25), (8 + hw, top, 8.75), "blade")
        s.box((8 - hw, 5.0, 7.1), (8 - hw + e, top, 8.9), "edge")
        s.box((8 + hw - e, 5.0, 7.1), (8 + hw, top, 8.9), "edge")
        both((7.6, 5.6), (8.4, top - 1.0), "fuller")
        for sx, y in ((7.0, 7.0), (8.6, 8.4), (7.2, 10.2), (8.9, 11.6)):      # stars in the night
            if 8 - hw + e <= sx <= 8 + hw - e - 0.4 and y < top - 0.5:
                both((sx, y), (sx + 0.4, y + 0.4), "glow", th=0.25)
        s.box((8 - hw + 0.5, top, 7.35), (8 + hw - 0.5, top + 1.6, 8.65), "blade")
        s.box((8 - hw + 1.0, top + 1.6, 7.45), (8 + hw - 1.0, top + 2.6, 8.55), "edge")
        s.box((8 - hw + 0.5, top, 7.25), (8 - hw + 1.0, top + 1.6, 8.75), "edge")
    else:                                                                     # sands and plain: straight blade
        seg(5.0, top, 8 - hw, 8 + hw)
        both((7.55, 5.6), (8.45, top - 1.2), "fuller")
        s.box((8 - hw + 0.5, top, 7.35), (8 + hw - 0.5, top + 1.6, 8.65), "blade")
        s.box((8 - hw + 1.0, top + 1.6, 7.45), (8 + hw - 1.0, top + 2.6, 8.55), "edge")
        if kind == "sands":                                                   # turquoise inlay along the fuller
            both((7.8, 6.2), (8.2, 6.9), "inlay", th=0.3)
            both((7.8, 8.0), (8.2, 8.7), "inlay", th=0.3)
    return s


def main():
    swords = {
        ("weapon", "bronze_cleaver"): build("bronze_cleaver", (201, 132, 66), (244, 190, 110), (150, 90, 40),
                                            (190, 150, 60), (110, 70, 40), (242, 193, 78), length=9, width=3.6, guard_w=6.5, kind="sands", seed=3),
        ("weapon", "rimeblade"): build("rimeblade", (127, 200, 232), (214, 244, 255), (230, 250, 255), (150, 180, 200),
                                       (60, 80, 120), (170, 240, 255), glow=(200, 245, 255), length=11, width=2.8, kind="frost", seed=4),
        ("weapon", "ember_sword"): build("ember_sword", (34, 26, 38), (255, 130, 50), (138, 58, 40), (74, 32, 24),
                                         (58, 36, 24), (255, 162, 58), glow=(255, 140, 40), length=10.5, width=3.0, kind="ashes", seed=11),
        ("weapon", "void_sword"): build("void_sword", (74, 42, 144), (228, 106, 255), (28, 16, 56), (40, 24, 90),
                                        (24, 14, 50), (228, 106, 255), glow=(200, 100, 255), length=11.5, width=2.8, kind="astral", seed=12),
        ("weapon", "starcore_sword"): build("starcore_sword", (26, 30, 72), (242, 193, 78), (138, 106, 28), (242, 193, 78),
                                            (120, 88, 40), (255, 255, 255), glow=(255, 250, 220), length=12, width=3.0, kind="star", seed=13),
        ("skin", "kitsune"): build("kitsune", (215, 220, 226), (255, 255, 255), (170, 176, 184), (201, 163, 90),
                                   (192, 57, 43), (192, 57, 43), length=12.5, width=1.8, guard_w=3.4, kind="astral", seed=5),
        ("skin", "titan_edge"): build("titan_edge", (174, 180, 187), (228, 232, 236), (111, 118, 126), (184, 132, 28),
                                      (96, 74, 50), (242, 193, 78), length=10, width=4.2, guard_w=7.5, kind="sands", seed=6),
        ("skin", "crystal_saber"): build("crystal_saber", (95, 224, 214), (185, 255, 247), (42, 157, 149), (138, 144, 153),
                                         (58, 63, 72), (185, 255, 247), glow=(200, 255, 250), length=11, width=2.6, kind="frost", seed=7),
        ("skin", "flame_brand"): build("flame_brand", (52, 40, 52), (255, 160, 60), (120, 120, 126), (107, 58, 32),
                                       (58, 36, 24), (255, 106, 42), glow=(255, 140, 40), length=11, width=2.8, kind="ashes", seed=8),
        ("skin", "frost_fang"): build("frost_fang", (127, 200, 232), (212, 241, 255), (63, 127, 159), (63, 127, 159),
                                      (74, 58, 42), (212, 241, 255), glow=(212, 241, 255), length=11, width=2.6, kind="frost", seed=9),
        ("skin", "wyrmfang"): build("wyrmfang", (233, 228, 216), (255, 255, 255), (138, 92, 240), (180, 140, 242),
                                    (42, 22, 80), (138, 92, 240), glow=(180, 100, 255), length=11.5, width=2.6, kind="astral", seed=10),
    }
    for (kind, name), sword in swords.items():
        sword.write_to(kind, name)
    print(f"wrote {len(swords)} 3D sword models")


if __name__ == "__main__":
    main()
