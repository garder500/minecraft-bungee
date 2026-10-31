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
import pathlib

import boss_skins as bs

PACK = bs.PACK
TURN = ("z", -45, [8, 8, 8])

DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 3.0, 0.5], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 3.0, 0.5], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2.0, 1.13], "scale": [0.42, 0.42, 0.42]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 2.0, 1.13], "scale": [0.42, 0.42, 0.42]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 90, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.78, 0.78, 0.78]},
}


class Sword(bs.Skin):
    def box(self, a, b, mat, top=None, rot=TURN):
        super().box(a, b, mat, top, rot)

    def write_to(self, kind, name):
        """3D model as <name>_3d; the GUI keeps the flat sprite (see item_models.write)."""
        three = f"vampyuyu:item/{kind}/{name}_3d"
        model = {"textures": {"0": three, "particle": three},
                 "gui_light": "front", "display": DISPLAY, "elements": self.elements}
        definition = {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                                "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": f"vampyuyu:item/{kind}/{name}"}}],
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
          kind="straight"):
    mats = [("blade", blade, "plain"), ("edge", edge, "plain"), ("fuller", fuller, "glow" if glow else "plain"),
            ("guard", guard, "plain"), ("grip", grip, "plain"), ("gem", gem, "glow"), ("pommel", guard, "plain"),
            ("wrap", tuple(int(v * 0.6) for v in grip), "plain")]
    if glow:
        mats.append(("glow", glow, "glow"))
    s = Sword(name, mats, seed)
    s.box((6.9, -2.2, 7.1), (9.1, -0.4, 8.9), "pommel")                       # pommel
    s.box((7.35, -0.4, 7.4), (8.65, 3.4, 8.6), "grip")                        # grip
    for k in range(3):                                                        # grip wrap
        s.box((7.25, 0.0 + k * 1.2, 7.3), (8.75, 0.5 + k * 1.2, 8.7), "wrap")
    s.box((8 - guard_w / 2, 3.4, 6.7), (8 + guard_w / 2, 5.0, 9.3), "guard")  # guard
    s.box((8 - guard_w / 2 - 0.8, 3.0, 7.2), (8 - guard_w / 2, 5.4, 8.8), "guard")
    s.box((8 + guard_w / 2, 3.0, 7.2), (8 + guard_w / 2 + 0.8, 5.4, 8.8), "guard")
    s.box((7.3, 3.5, 9.3), (8.7, 4.9, 9.7), "gem")                            # gem front and back
    s.box((7.3, 3.5, 6.3), (8.7, 4.9, 6.7), "gem")
    top = 5.0 + length
    s.box((8 - width / 2, 5.0, 7.25), (8 + width / 2, top, 8.75), "blade")    # blade
    s.box((8 - width / 2, 5.0, 7.15), (8 - width / 2 + 0.55, top, 8.85), "edge")
    s.box((8 + width / 2 - 0.55, 5.0, 7.15), (8 + width / 2, top, 8.85), "edge")
    s.box((7.55, 5.6, 8.75), (8.45, top - 1.2, 8.95), "fuller")               # fuller on both faces
    s.box((7.55, 5.6, 7.05), (8.45, top - 1.2, 7.25), "fuller")
    s.box((8 - width / 2 + 0.5, top, 7.35), (8 + width / 2 - 0.5, top + 1.6, 8.65), "blade")   # tip
    s.box((8 - width / 2 + 1.0, top + 1.6, 7.45), (8 + width / 2 - 1.0, top + 2.6, 8.55), "edge")
    if kind == "crystal":                                                     # jagged crystal teeth
        for k in range(4):
            y = 6.5 + k * 2.2
            s.box((8 + width / 2, y, 7.5), (8 + width / 2 + 1.3, y + 1.3, 8.5), "fuller")
            s.box((8 - width / 2 - 1.1, y + 1.1, 7.5), (8 - width / 2, y + 2.2, 8.5), "fuller")
    elif kind == "flame":                                                     # flames licking the blade
        for k in range(4):
            y = 6.0 + k * 2.4
            s.box((8 + width / 2, y, 7.6), (8 + width / 2 + 1.2, y + 1.8 + (k % 2), 8.4), "glow")
            s.box((8 - width / 2 - 1.2, y + 1.0, 7.6), (8 - width / 2, y + 2.6 - (k % 2), 8.4), "glow")
    elif kind == "wyrm":                                                      # bone spines and a glowing outline
        for k in range(3):
            y = 6.0 + k * 2.8
            s.box((8 + width / 2, y, 7.7), (8 + width / 2 + 1.4, y + 1.4, 8.3), "edge")
        s.box((8 - width / 2 - 0.35, 5.0, 7.3), (8 - width / 2, top, 8.7), "glow")
        s.box((8 + width / 2, 5.0, 7.3), (8 + width / 2 + 0.35, top, 8.7), "glow")
    elif kind == "fang":                                                      # a curved look: the upper blade leans
        s.box((8 - width / 2 + 0.7, top - 3.0, 7.3), (8 + width / 2 + 0.7, top + 0.2, 8.7), "blade")
    return s


def main():
    swords = {
        ("weapon", "bronze_cleaver"): build("bronze_cleaver", (201, 132, 66), (244, 190, 110), (150, 90, 40),
                                            (190, 150, 60), (110, 70, 40), (242, 193, 78), length=9, width=3.6, guard_w=6.5, seed=3),
        ("weapon", "rimeblade"): build("rimeblade", (127, 200, 232), (214, 244, 255), (230, 250, 255), (150, 180, 200),
                                       (60, 80, 120), (170, 240, 255), glow=(200, 245, 255), length=11, width=2.8, kind="fang", seed=4),
        ("weapon", "ember_sword"): build("ember_sword", (216, 104, 58), (255, 162, 58), (138, 58, 40), (74, 32, 24),
                                         (58, 36, 24), (255, 162, 58), glow=(255, 140, 40), length=10.5, width=3.0, kind="flame", seed=11),
        ("weapon", "void_sword"): build("void_sword", (74, 42, 144), (228, 106, 255), (28, 16, 56), (40, 24, 90),
                                        (24, 14, 50), (228, 106, 255), glow=(200, 100, 255), length=11.5, width=2.8, kind="wyrm", seed=12),
        ("weapon", "starcore_sword"): build("starcore_sword", (226, 215, 130), (255, 255, 255), (138, 106, 28), (242, 193, 78),
                                            (120, 88, 40), (255, 255, 255), glow=(255, 250, 220), length=12, width=3.0, kind="crystal", seed=13),
        ("skin", "kitsune"): build("kitsune", (215, 220, 226), (255, 255, 255), (170, 176, 184), (201, 163, 90),
                                   (192, 57, 43), (192, 57, 43), length=12.5, width=1.8, guard_w=3.4, seed=5),
        ("skin", "titan_edge"): build("titan_edge", (174, 180, 187), (228, 232, 236), (111, 118, 126), (184, 132, 28),
                                      (96, 74, 50), (242, 193, 78), length=10, width=4.2, guard_w=7.5, seed=6),
        ("skin", "crystal_saber"): build("crystal_saber", (95, 224, 214), (185, 255, 247), (42, 157, 149), (138, 144, 153),
                                         (58, 63, 72), (185, 255, 247), glow=(200, 255, 250), length=11, width=2.6, kind="crystal", seed=7),
        ("skin", "flame_brand"): build("flame_brand", (194, 199, 205), (255, 210, 90), (120, 120, 126), (107, 58, 32),
                                       (58, 36, 24), (255, 106, 42), glow=(255, 140, 40), length=11, width=2.8, kind="flame", seed=8),
        ("skin", "frost_fang"): build("frost_fang", (127, 200, 232), (212, 241, 255), (63, 127, 159), (63, 127, 159),
                                      (74, 58, 42), (212, 241, 255), length=11, width=2.6, kind="fang", seed=9),
        ("skin", "wyrmfang"): build("wyrmfang", (233, 228, 216), (255, 255, 255), (138, 92, 240), (180, 140, 242),
                                    (42, 22, 80), (138, 92, 240), glow=(180, 100, 255), length=11.5, width=2.6, kind="wyrm", seed=10),
    }
    for (kind, name), sword in swords.items():
        sword.write_to(kind, name)
    print(f"wrote {len(swords)} 3D sword models")


if __name__ == "__main__":
    main()
