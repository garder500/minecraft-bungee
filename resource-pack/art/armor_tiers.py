#!/usr/bin/env python3
"""Armor of the five tiers (Bronze, Rime, Ember, Void, Starcore): flat inventory icons and the worn textures.

    python3 resource-pack/art/armor_tiers.py

Per tier: equipment/<tier>.json, the two 64x32 worn layers (humanoid and humanoid_leggings) and one icon per piece
(items/armor/<tier>_<slot>.json, models/item/armor/..., textures/item/armor/...). Item ids: <tier>_helmet,
<tier>_chestplate, <tier>_leggings, <tier>_boots (see QuarryItems.java). Deterministic, stdlib only.
"""
import json
import zlib
import random

import boss_items as b
import boss_skins as bs

ASSETS = bs.PACK
TIERS = {
    "bronze": dict(dark=(122, 74, 36), main=(201, 132, 66), light=(240, 184, 120), glow=(255, 210, 122)),
    "rime": dict(dark=(63, 127, 159), main=(127, 200, 232), light=(212, 241, 255), glow=(232, 248, 255)),
    "ember": dict(dark=(74, 32, 24), main=(138, 58, 40), light=(216, 104, 58), glow=(255, 162, 58)),
    "void": dict(dark=(28, 16, 56), main=(74, 42, 144), light=(164, 122, 255), glow=(228, 106, 255)),
    "starcore": dict(dark=(138, 106, 28), main=(226, 183, 42), light=(255, 240, 168), glow=(255, 255, 255)),
}
SLOTS = ("helmet", "chestplate", "leggings", "boots")


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def layer(tier, leggings):
    """A 64x32 worn layer: the regions armor uses, painted with trim, a light top edge and the glow detail."""
    t = TIERS[tier]
    rnd = random.Random(zlib.crc32(tier.encode()))
    px = [[(0, 0, 0, 0)] * 64 for _ in range(32)]

    def fill(x0, y0, x1, y1, color, jitter=0.07):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                f = 1 + rnd.uniform(-jitter, jitter)
                r, g, bl = shade(color, f)
                px[y][x] = (r, g, bl, 255)

    if not leggings:
        fill(0, 0, 31, 15, t["main"])                    # head: all six faces
        fill(0, 0, 31, 1, t["light"])
        fill(8, 8, 15, 15, t["main"])
        fill(9, 11, 14, 12, t["glow"], 0.02)             # visor
        fill(8, 8, 15, 8, t["dark"])
        fill(16, 16, 39, 31, t["main"])                  # body and its sides
        fill(16, 16, 39, 17, t["light"])
        fill(20, 20, 27, 31, shade(t["main"], 0.92))
        fill(22, 21, 25, 24, t["glow"], 0.02)            # chest core
        fill(20, 29, 27, 31, t["dark"])
        fill(40, 16, 55, 31, t["main"])                  # arms
        fill(40, 16, 55, 19, t["light"])
        fill(40, 28, 55, 31, t["dark"])
        fill(0, 16, 15, 31, t["main"])                   # boots on the legs region
        fill(0, 28, 15, 31, t["dark"])
        fill(0, 16, 15, 17, t["light"])
    else:
        fill(16, 16, 39, 31, t["main"])                  # belt and hips
        fill(16, 16, 39, 18, t["dark"])
        fill(18, 18, 37, 19, t["light"])
        fill(0, 16, 15, 31, t["main"])                   # legs
        fill(0, 16, 15, 17, t["light"])
        fill(0, 24, 15, 24, t["dark"])
        fill(4, 20, 6, 22, t["glow"], 0.02)
    return px


def icon(tier, slot):
    t = TIERS[tier]
    c = b.Canvas()
    main, dark, light, glow = (b.shade(t[k], 1.0) for k in ("main", "dark", "light", "glow"))  # RGBA for the canvas
    if slot == "helmet":
        c.rect(3, 3, 12, 9, main)
        c.rect(3, 3, 12, 4, light)
        c.rect(3, 10, 5, 12, main)
        c.rect(10, 10, 12, 12, main)
        c.rect(5, 7, 10, 8, glow)
        c.rect(3, 9, 12, 9, dark)
    elif slot == "chestplate":
        c.rect(2, 2, 5, 6, light)
        c.rect(10, 2, 13, 6, light)
        c.rect(3, 3, 12, 14, main)
        c.rect(3, 3, 12, 4, light)
        c.rect(6, 6, 9, 9, glow)
        c.rect(3, 13, 12, 14, dark)
    elif slot == "leggings":
        c.rect(3, 2, 12, 5, main)
        c.rect(3, 2, 12, 3, light)
        c.rect(3, 6, 6, 14, main)
        c.rect(9, 6, 12, 14, main)
        c.rect(3, 4, 12, 4, dark)
        c.set(4, 8, glow)
        c.set(10, 8, glow)
    else:
        c.rect(2, 8, 6, 13, main)
        c.rect(9, 8, 13, 13, main)
        c.rect(2, 8, 6, 9, light)
        c.rect(9, 8, 13, 9, light)
        c.rect(1, 12, 6, 14, dark)
        c.rect(9, 12, 14, 14, dark)
        c.set(4, 11, glow)
        c.set(11, 11, glow)
    c.outline()
    return c


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def main():
    for tier in TIERS:
        write_json(ASSETS / "equipment" / f"{tier}.json", {"layers": {
            "humanoid": [{"texture": f"vampyuyu:{tier}"}], "humanoid_leggings": [{"texture": f"vampyuyu:{tier}"}]}})
        for folder, leggings in (("humanoid", False), ("humanoid_leggings", True)):
            path = ASSETS / "textures" / "entity" / "equipment" / folder / f"{tier}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(bs.png(layer(tier, leggings)))
        for slot in SLOTS:
            ident = f"{tier}_{slot}"
            tex = ASSETS / "textures" / "item" / "armor" / f"{ident}.png"
            tex.parent.mkdir(parents=True, exist_ok=True)
            tex.write_bytes(icon(tier, slot).png())
            write_json(ASSETS / "models" / "item" / "armor" / f"{ident}.json",
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/armor/{ident}"}})
            write_json(ASSETS / "items" / "armor" / f"{ident}.json",
                       {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/armor/{ident}"}})
    print(f"wrote {len(TIERS) * len(SLOTS)} armor icons and {len(TIERS)} worn sets")


if __name__ == "__main__":
    main()
