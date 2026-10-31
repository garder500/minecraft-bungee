#!/usr/bin/env python3
"""Draws the 16x16 icons of the Quarry (ores, Bronze Core, Forge, first gear) with the stdlib only.

    python3 resource-pack/art/quarry_items.py            # writes textures, models and item definitions
    python3 resource-pack/art/quarry_items.py --sheet F  # also writes a 10x contact sheet PNG to F (not committed)

Item models (see QuarryItems.java): vampyuyu:ore/bronzite, ore/bronze_core, forge/forge, weapon/bronze_cleaver,
tool/bronze_pick. Same style and helpers as boss_items.py. The output is deterministic.
"""
import json
import pathlib
import struct
import sys
import zlib

import boss_items as b

BRONZE = (0xC9, 0x84, 0x42)
GOLD = (0xF2, 0xC1, 0x4E)
STEEL = (0x8E, 0x94, 0x9C)
WOOD = (0x7A, 0x52, 0x2E)
FIRE = (0xFF, 0x8A, 0x2A)
FROST = (0x7F, 0xC8, 0xE8)


def bronzite(c):
    lump = b.union(b.circle(7.5, 9, 4.4), b.circle(10.5, 7, 3.4), b.circle(5.2, 6.2, 2.6))
    c.fill_shape(lump, BRONZE)
    for x, y in ((6, 8), (10, 6), (8, 11), (5, 5)):
        c.set(x, y, b.shade(GOLD, 1.0))
    b.sparkle(c, 11, 5, GOLD)
    c.outline()


def bronze_core(c):
    c.fill_shape(b.diamond(8, 8, 6.2, 6.2), BRONZE)
    c.fill_shape(b.diamond(8, 8, 3.4, 3.4), b.mix(BRONZE, GOLD, 0.6))
    c.fill_shape(b.diamond(8, 8, 1.4, 1.4), (0xFF, 0xF1, 0xC2))
    b.sparkle(c, 4, 4, GOLD)
    c.outline()


def forge(c):
    c.rect(2, 4, 13, 13, b.shade(STEEL, 0.9))
    c.rect(2, 4, 13, 5, b.shade(STEEL, 1.25))
    c.rect(2, 12, 13, 13, b.shade(STEEL, 0.65))
    c.rect(5, 7, 10, 11, (0x1A, 0x14, 0x12, 255))
    for x, y in ((6, 10), (7, 9), (8, 10), (9, 9), (7, 10), (8, 9)):
        c.set(x, y, b.shade(FIRE, 1.0))
    c.set(7, 8, b.shade(GOLD, 1.0))
    c.rect(10, 1, 12, 3, b.shade(STEEL, 0.75))
    c.rect(3, 6, 3, 12, b.shade(BRONZE, 1.0))
    c.outline()


def blade(c, metal, trim):
    c.fill_shape(lambda x, y: abs(x + y - 16) <= 2.6 and 4.5 <= x <= 14.5, metal)
    c.fill_shape(lambda x, y: abs(x - y + 4) <= 1.3 and abs(x + y - 16) <= 5.0, tuple(int(v * 0.8) for v in trim))
    c.line(5, 11, 2, 14, b.shade(WOOD, 1.0))
    c.line(6, 11, 3, 14, b.shade(WOOD, 0.8))
    c.set(1, 15, b.shade(trim, 0.8))
    c.outline()


def bronze_cleaver(c):
    blade(c, BRONZE, GOLD)


def rimeblade(c):
    blade(c, FROST, (0xE6, 0xF7, 0xFF))
    for x, y in ((11, 5), (9, 7)):
        c.set(x, y, b.shade((0xFF, 0xFF, 0xFF), 1.0))


def pick(c, metal, accent):
    c.line(3, 14, 11, 6, b.shade(WOOD, 1.0))
    c.line(4, 14, 12, 6, b.shade(WOOD, 0.8))
    pts = [(4, 6), (6, 4), (9, 3), (12, 4), (14, 7)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        c.line(x0, y0, x1, y1, b.shade(metal, 1.1))
        c.line(x0, y0 + 1, x1, y1 + 1, b.shade(metal, 0.8))
    c.set(8, 3, b.shade(accent, 1.0))
    c.outline()


def rime_pick(c):
    pick(c, FROST, (0xFF, 0xFF, 0xFF))


def frostium(c):
    shards = b.union(b.poly([(7, 2), (10, 8), (8, 13), (5, 8)]), b.poly([(10, 6), (13, 11), (11, 14), (8, 11)]),
                     b.poly([(4, 8), (7, 12), (5, 14), (2, 11)]))
    c.fill_shape(shards, FROST)
    b.sparkle(c, 7, 6, (0xFF, 0xFF, 0xFF))
    c.outline()


def sunstone(c):
    c.fill_shape(b.circle(8, 8, 5.2), (0xFF, 0xC8, 0x3A))
    c.fill_shape(b.circle(8, 8, 3.0), (0xFF, 0xE9, 0x8A))
    for dx, dy in ((0, -7), (0, 7), (-7, 0), (7, 0)):
        c.set(8 + dx, 8 + dy, b.shade(GOLD, 1.0))
    b.sparkle(c, 6, 6, (0xFF, 0xFF, 0xFF))
    c.outline()


def ember_core(c):
    c.fill_shape(b.diamond(8, 8, 6.2, 6.2), (0xC2, 0x3B, 0x1E))
    c.fill_shape(b.diamond(8, 8, 3.6, 3.6), FIRE)
    c.fill_shape(b.diamond(8, 8, 1.5, 1.5), (0xFF, 0xF1, 0xC2))
    b.sparkle(c, 4, 4, FIRE)
    c.outline()


def bronze_pick(c):
    pick(c, BRONZE, GOLD)


COPPER = (0xD9, 0x83, 0x5A)


def cable(c):
    c.rect(7, 1, 8, 14, b.shade(COPPER, 1.0))
    c.rect(7, 1, 7, 14, b.shade(COPPER, 1.25))
    c.rect(5, 5, 10, 6, b.shade(COPPER, 0.8))
    c.rect(5, 10, 10, 11, b.shade(COPPER, 0.8))
    c.set(8, 0, b.shade(GOLD, 1.0))
    c.outline()


def battery(c):
    c.rect(3, 3, 12, 14, b.shade(STEEL, 0.75))
    c.rect(3, 3, 4, 14, b.shade(STEEL, 1.1))
    c.rect(5, 5, 11, 12, (0x10, 0x14, 0x12, 255))
    for i, y in enumerate((11, 9, 7)):
        c.rect(6, y, 10, y + 1, b.shade((0x6B, 0xE0, 0x7A), 1.0 - 0.15 * i))
    c.rect(6, 1, 9, 2, b.shade(COPPER, 1.0))
    c.outline()


def coal_generator(c):
    c.rect(2, 4, 13, 14, b.shade(STEEL, 0.8))
    c.rect(2, 4, 13, 5, b.shade(STEEL, 1.2))
    c.rect(2, 13, 13, 14, b.shade(STEEL, 0.6))
    c.rect(5, 7, 10, 11, (0x16, 0x12, 0x10, 255))
    c.rect(6, 9, 9, 11, (0x30, 0x30, 0x34, 255))
    c.set(7, 8, b.shade(FIRE, 1.0))
    c.set(8, 7, b.shade(GOLD, 1.0))
    c.rect(11, 1, 12, 4, b.shade(STEEL, 0.6))
    c.outline()


def miner(c, trim):
    c.rect(2, 2, 13, 13, b.shade((0x6A, 0x62, 0x70), 1.0))
    c.rect(2, 2, 13, 3, b.shade((0x6A, 0x62, 0x70), 1.3))
    c.rect(2, 12, 13, 13, b.shade((0x6A, 0x62, 0x70), 0.7))
    c.rect(4, 4, 11, 11, (0x1C, 0x18, 0x22, 255))
    c.line(5, 10, 10, 5, b.shade(WOOD, 1.0))
    c.line(5, 6, 8, 5, b.shade(trim, 1.1))
    c.line(8, 5, 10, 8, b.shade(trim, 1.1))
    c.outline()


def miner_1(c):
    miner(c, BRONZE)


def miner_2(c):
    miner(c, FROST)


def void_lens(c):
    c.fill_shape(b.ellipse(8, 8, 6.4, 4.4), (0x6A, 0x3F, 0xB0))
    c.fill_shape(b.ellipse(8, 8, 3.6, 3.6), (0x2A, 0x16, 0x50))
    c.fill_shape(b.circle(8, 8, 1.6), (0xE6, 0xD4, 0xFF))
    b.sparkle(c, 4, 5, (0xB4, 0x8C, 0xF2))
    c.outline()


def px(c, cells, color):
    for x, y in cells:
        c.set(x, y, color if len(color) == 4 else color + (255,))


def diag(x0, y0, n, dx=1, dy=1):
    return [(x0 + i * dx, y0 + i * dy) for i in range(n)]


DARK = (0x2A, 0x1C, 0x14)


def skin_blade(c, main, edge, guard, accent, handle=DARK, kind="straight"):
    """The diagonal sword frame of the skins (blade up and to the right), as in the design board."""
    if kind == "wide":
        px(c, diag(14, 1, 8, -1, 1), main)
        px(c, diag(14, 0, 8, -1, 1), edge)
        px(c, diag(15, 1, 8, -1, 1), accent)
        px(c, diag(3, 6, 7), guard)
        px(c, diag(5, 10, 3, -1, 1), (0x6B, 0x4A, 0x2B))
        px(c, [(2, 13)], guard)
        return
    if kind == "katana":
        px(c, diag(15, 0, 8, -1, 1), main)
        px(c, [(13, 1), (11, 3), (9, 5)], edge)
        px(c, diag(6, 7, 3), guard)
        px(c, [(6, 9), (4, 11), (2, 13)], accent)
        px(c, [(5, 10), (3, 12)], handle)
        return
    if kind == "fang":
        px(c, [(14, 1), (14, 2), (13, 3), (12, 4), (11, 5), (10, 6), (9, 7), (8, 8)], main)
        px(c, [(13, 0), (13, 1), (12, 2), (12, 3), (11, 4), (10, 5), (9, 6), (8, 7)], edge)
        px(c, diag(6, 8, 3), guard)
        px(c, diag(6, 10, 3, -1, 1), handle)
        px(c, [(3, 13)], guard)
        return
    if kind == "wyrm":
        px(c, diag(13, 1, 7, -1, 1), accent)
        px(c, diag(15, 1, 7, -1, 1), accent)
    px(c, diag(14, 1, 8, -1, 1), main)
    if kind == "crystal":
        px(c, [(15, 0), (12, 2), (10, 4), (8, 6)], edge)
        px(c, [(13, 3), (11, 5), (9, 7)], accent)
        px(c, [(13, 1), (11, 3)], (0xFF, 0xFF, 0xFF))
    elif kind == "flame":
        px(c, [(13, 0), (11, 2), (9, 4), (7, 6)], edge)
        px(c, [(15, 2), (13, 4), (11, 6), (9, 8), (12, 1), (10, 3)], accent)
    else:
        px(c, [(13, 1), (11, 3), (9, 5)], edge)
    px(c, diag(4, 7, 5), guard)
    px(c, diag(5, 10, 3, -1, 1), handle)
    px(c, [(2, 13)], guard if kind != "wyrm" else accent)


def skin_kitsune(c):
    skin_blade(c, (0xD7, 0xDC, 0xE2), (0xFF, 0xFF, 0xFF), (0xC9, 0xA3, 0x5A), (0xC0, 0x39, 0x2B), kind="katana")
    c.outline()


def skin_titan_edge(c):
    skin_blade(c, (0xAE, 0xB4, 0xBB), (0xE4, 0xE8, 0xEC), (0xB8, 0x84, 0x1C), (0x6F, 0x76, 0x7E), kind="wide")
    c.outline()


def skin_crystal_saber(c):
    skin_blade(c, (0x5F, 0xE0, 0xD6), (0xB9, 0xFF, 0xF7), (0x8A, 0x90, 0x99), (0x2A, 0x9D, 0x95), (0x3A, 0x3F, 0x48), "crystal")
    c.outline()


def skin_flame_brand(c):
    skin_blade(c, (0xC2, 0xC7, 0xCD), (0xFF, 0xD2, 0x5A), (0x6B, 0x3A, 0x20), (0xFF, 0x6A, 0x2A), (0x3A, 0x24, 0x18), "flame")
    c.outline()


def skin_frost_fang(c):
    skin_blade(c, (0x7F, 0xC8, 0xE8), (0xD4, 0xF1, 0xFF), (0x3F, 0x7F, 0x9F), (0x7F, 0xC8, 0xE8), (0x4A, 0x3A, 0x2A), "fang")
    c.outline()


def skin_wyrmfang(c):
    skin_blade(c, (0xE9, 0xE4, 0xD8), (0xFF, 0xFF, 0xFF), (0xB4, 0x8C, 0xF2), (0x8A, 0x5C, 0xF0), (0x2A, 0x16, 0x50), "wyrm")
    c.outline()


def voidglass(c):
    c.fill_shape(b.poly([(8, 1), (11, 6), (10, 13), (8, 15), (6, 13), (5, 6)]), (0xB4, 0x8C, 0xF2))
    c.fill_shape(b.poly([(8, 5), (9.5, 8), (8.5, 12), (7, 8)]), (0x2A, 0x16, 0x50))
    b.sparkle(c, 6, 4, (0xE6, 0xD4, 0xFF))
    c.outline()


def starcore(c):
    c.fill_shape(b.union(b.poly([(8, 0.5), (9.5, 6.5), (15.5, 8), (9.5, 9.5), (8, 15.5), (6.5, 9.5), (0.5, 8), (6.5, 6.5)])),
                 (0xFF, 0x7A, 0x9C))
    c.fill_shape(b.circle(8, 8, 1.8), (0xFF, 0xFF, 0xFF))
    c.outline()


def solar_panel(c):
    c.rect(1, 4, 14, 12, b.shade((0x1E, 0x2A, 0x4A), 1.0))
    for x in (4, 7, 10):
        c.rect(x, 4, x, 12, b.shade((0x6A, 0x7A, 0xA8), 1.0))
    for y in (7, 10):
        c.rect(1, y, 14, y, b.shade((0x6A, 0x7A, 0xA8), 1.0))
    c.rect(2, 5, 3, 6, b.shade((0x7F, 0xA8, 0xE8), 1.2))
    c.rect(6, 13, 9, 14, b.shade(STEEL, 0.8))
    c.set(13, 2, b.shade(GOLD, 1.0))
    c.outline()



ICONS = {
    "ore/bronzite": bronzite,
    "ore/bronze_core": bronze_core,
    "forge/forge": forge,
    "weapon/bronze_cleaver": bronze_cleaver,
    "tool/bronze_pick": bronze_pick,
    "ore/frostium": frostium,
    "ore/sunstone": sunstone,
    "ore/ember_core": ember_core,
    "ore/void_lens": void_lens,
    "ore/voidglass": voidglass,
    "ore/starcore": starcore,
    "machine/solar_panel": solar_panel,
    "skin/kitsune": skin_kitsune,
    "skin/titan_edge": skin_titan_edge,
    "skin/crystal_saber": skin_crystal_saber,
    "skin/flame_brand": skin_flame_brand,
    "skin/frost_fang": skin_frost_fang,
    "skin/wyrmfang": skin_wyrmfang,
    "weapon/rimeblade": rimeblade,
    "tool/rime_pick": rime_pick,
    "machine/cable": cable,
    "machine/battery": battery,
    "machine/coal_generator": coal_generator,
    "machine/miner_1": miner_1,
    "machine/miner_2": miner_2,
}


def draw(icon_id):
    c = b.Canvas()
    ICONS[icon_id](c)
    return c


def sheet(path):
    scale_up = 10
    ids = list(ICONS)
    w, h = len(ids) * 18 * scale_up, 18 * scale_up
    drawn = {i: draw(i).p for i in ids}
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            idx = x // (18 * scale_up)
            ix, iy = (x % (18 * scale_up)) // scale_up - 1, y // scale_up - 1
            px = drawn[ids[idx]][iy][ix] if 0 <= ix < 16 and 0 <= iy < 16 else None
            raw.extend(px if px else ((60, 60, 70, 255) if (x // 10 + y // 10) % 2 else (70, 70, 80, 255)))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    pathlib.Path(path).write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# Blocks are drawn only as 3D box models (item_models.py). Everything else keeps this sprite for the GUI and gets a
# 3D model (sword_models.py, item_models.py) for the hand, the ground and item frames.
SWORDS_3D = {"forge/forge", "machine/coal_generator", "machine/battery", "machine/cable", "machine/solar_panel",
             "machine/miner_1", "machine/miner_2"}  # blocks only: the flat sprite is kept for every other item


def main():
    for icon_id in ICONS:
        if icon_id in SWORDS_3D:
            continue
        tex = b.PACK / "textures" / "item" / f"{icon_id}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        tex.write_bytes(draw(icon_id).png())
        b.write_json(b.PACK / "models" / "item" / f"{icon_id}.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/{icon_id}"}})
        b.write_json(b.PACK / "items" / f"{icon_id}.json",
                     {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/{icon_id}"}})
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1])
    print(f"wrote {len(ICONS)} quarry icons")


if __name__ == "__main__":
    main()
