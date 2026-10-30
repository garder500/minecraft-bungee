#!/usr/bin/env python3
"""Draws the 16x16 icons of the Skyblock skills feature (enchanted and compacted resources).

    python3 resource-pack/art/skills_icons.py            # write textures, models and item definitions
    python3 resource-pack/art/skills_icons.py --check    # fail when the committed files differ from the output

Stdlib only and fully deterministic. Every item is a shape (gem, ingot, lump...) painted in two or three colours,
outlined automatically, with a sparkle overlay that marks the tier (green for enchanted, blue for compacted).
Output, all under assets/vampyuyu/ and all containing "skills" in the path:
    textures/item/skills/<id>.png, models/item/skills/<id>.json, items/skills/<id>.json
The same ITEMS table is the reference for the enchanted_items section of survival-plugin skills.yml.
"""
import json
import pathlib
import struct
import sys
import zlib

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"

# id, display name, base material, shape, (main, shade, highlight) colours, tier
ITEMS = [
    # mining, tier 1
    ("enchanted_cobblestone", "Enchanted Cobblestone", "COBBLESTONE", "cube", ("8c8c8c", "5e5e5e", "b4b4b4"), 1),
    ("enchanted_coal", "Enchanted Coal", "COAL", "lump", ("2b2b2b", "141414", "555555"), 1),
    ("enchanted_iron", "Enchanted Iron", "IRON_INGOT", "ingot", ("d8d8d8", "9a9a9a", "ffffff"), 1),
    ("enchanted_gold", "Enchanted Gold", "GOLD_INGOT", "ingot", ("f5c93b", "b98a14", "fff09a"), 1),
    ("enchanted_diamond", "Enchanted Diamond", "DIAMOND", "gem", ("4de8e0", "1fa9b0", "c8fffb"), 1),
    ("enchanted_emerald", "Enchanted Emerald", "EMERALD", "gem", ("3fd35f", "1a8f38", "b6ffc4"), 1),
    ("enchanted_lapis", "Enchanted Lapis Lazuli", "LAPIS_LAZULI", "lump", ("2f4fd0", "1b2f8a", "7d96ff"), 1),
    ("enchanted_redstone", "Enchanted Redstone", "REDSTONE", "pile", ("d62222", "8a1010", "ff7a6a"), 1),
    ("enchanted_quartz", "Enchanted Quartz", "QUARTZ", "gem", ("efe6dc", "bdb0a2", "ffffff"), 1),
    ("enchanted_obsidian", "Enchanted Obsidian", "OBSIDIAN", "cube", ("2a1b45", "150d28", "5a3d8f"), 1),
    ("enchanted_glowstone", "Enchanted Glowstone Dust", "GLOWSTONE_DUST", "pile", ("f2c14a", "b98a1c", "fff2a8"), 1),
    # mining, tier 2
    ("enchanted_coal_block", "Enchanted Coal Block", "COAL_BLOCK", "cube", ("2b2b2b", "141414", "555555"), 2),
    ("enchanted_iron_block", "Enchanted Iron Block", "IRON_BLOCK", "cube", ("d8d8d8", "9a9a9a", "ffffff"), 2),
    ("enchanted_gold_block", "Enchanted Gold Block", "GOLD_BLOCK", "cube", ("f5c93b", "b98a14", "fff09a"), 2),
    ("enchanted_diamond_block", "Enchanted Diamond Block", "DIAMOND_BLOCK", "cube", ("4de8e0", "1fa9b0", "c8fffb"), 2),
    ("enchanted_emerald_block", "Enchanted Emerald Block", "EMERALD_BLOCK", "cube", ("3fd35f", "1a8f38", "b6ffc4"), 2),
    ("enchanted_lapis_block", "Enchanted Lapis Block", "LAPIS_BLOCK", "cube", ("2f4fd0", "1b2f8a", "7d96ff"), 2),
    ("enchanted_redstone_block", "Enchanted Redstone Block", "REDSTONE_BLOCK", "cube", ("d62222", "8a1010", "ff7a6a"), 2),
    ("enchanted_quartz_block", "Enchanted Quartz Block", "QUARTZ_BLOCK", "cube", ("efe6dc", "bdb0a2", "ffffff"), 2),
    # farming
    ("enchanted_bread", "Enchanted Bread", "BREAD", "sheaf", ("e0b84a", "a8801f", "fff0a0"), 1),
    ("enchanted_carrot", "Enchanted Carrot", "CARROT", "carrot", ("f2851c", "b25a08", "ffc27a"), 1),
    ("enchanted_potato", "Enchanted Potato", "POTATO", "lump", ("d9b469", "a07c35", "f5e0a8"), 1),
    ("enchanted_pumpkin", "Enchanted Pumpkin", "PUMPKIN", "striped", ("e8801a", "a8520a", "ffbb6a"), 1),
    ("enchanted_melon", "Enchanted Melon", "MELON_SLICE", "striped", ("63b83a", "357a1a", "b6ee8f"), 1),
    ("enchanted_sugar", "Enchanted Sugar", "SUGAR", "pile", ("f4f4f4", "bcbcbc", "ffffff"), 1),
    ("enchanted_cactus", "Enchanted Cactus", "CACTUS", "cactus", ("3f9a3a", "226a20", "8fdc86"), 1),
    ("enchanted_cocoa", "Enchanted Cocoa Beans", "COCOA_BEANS", "lump", ("7a4a22", "4a2a10", "b0804a"), 1),
    ("enchanted_nether_wart", "Enchanted Nether Wart", "NETHER_WART", "lump", ("b0243a", "6e1022", "ff6a80"), 1),
    ("enchanted_golden_carrot", "Enchanted Golden Carrot", "GOLDEN_CARROT", "carrot", ("f5c93b", "b98a14", "fff09a"), 2),
    ("enchanted_baked_potato", "Enchanted Baked Potato", "BAKED_POTATO", "lump", ("b9823a", "7a5018", "e8c070"), 2),
    # foraging
    ("enchanted_oak_log", "Enchanted Oak Wood", "OAK_LOG", "log", ("a9814a", "6e5228", "d8b87a"), 1),
    ("enchanted_spruce_log", "Enchanted Spruce Wood", "SPRUCE_LOG", "log", ("6e4a2a", "3e2812", "a07850"), 1),
    ("enchanted_birch_log", "Enchanted Birch Wood", "BIRCH_LOG", "log", ("dcd2b0", "a39a78", "ffffee"), 1),
    ("enchanted_jungle_log", "Enchanted Jungle Wood", "JUNGLE_LOG", "log", ("8a6a30", "523a14", "c0a060"), 1),
    ("enchanted_acacia_log", "Enchanted Acacia Wood", "ACACIA_LOG", "log", ("b5602f", "7a3a14", "e89a66"), 1),
    ("enchanted_dark_oak_log", "Enchanted Dark Oak Wood", "DARK_OAK_LOG", "log", ("4a3018", "2a1808", "7a5a38"), 1),
    # combat
    ("enchanted_rotten_flesh", "Enchanted Rotten Flesh", "ROTTEN_FLESH", "lump", ("8a6a3a", "56401c", "c8a468"), 1),
    ("enchanted_bone", "Enchanted Bone", "BONE", "stick", ("ece8d8", "b4ae96", "ffffff"), 1),
    ("enchanted_string", "Enchanted String", "STRING", "stick", ("f2f2f2", "b0b0b0", "ffffff"), 1),
    ("enchanted_gunpowder", "Enchanted Gunpowder", "GUNPOWDER", "pile", ("7a7a7a", "4a4a4a", "a8a8a8"), 1),
    ("enchanted_ender_pearl", "Enchanted Ender Pearl", "ENDER_PEARL", "orb", ("1f8f7a", "0e5a4c", "7affdd"), 1),
    ("enchanted_blaze_rod", "Enchanted Blaze Rod", "BLAZE_ROD", "stick", ("f2a012", "b0620a", "ffe070"), 1),
    ("enchanted_slime_ball", "Enchanted Slimeball", "SLIME_BALL", "orb", ("66d34a", "389a22", "bdf5a8"), 1),
    ("enchanted_slime_block", "Enchanted Slime Block", "SLIME_BLOCK", "cube", ("66d34a", "389a22", "bdf5a8"), 2),
    ("enchanted_eye_of_ender", "Enchanted Eye of Ender", "ENDER_EYE", "orb", ("2fb86a", "167a3e", "a8ffc8"), 2),
    # fishing
    ("enchanted_raw_fish", "Enchanted Raw Fish", "COD", "fish", ("c8a878", "8a6a3e", "f0dcb4"), 1),
    ("enchanted_raw_salmon", "Enchanted Raw Salmon", "SALMON", "fish", ("d86a5a", "9a3a2c", "ffb0a0"), 1),
    ("enchanted_pufferfish", "Enchanted Pufferfish", "PUFFERFISH", "striped", ("e0c030", "9a8010", "fff088"), 1),
    ("enchanted_clownfish", "Enchanted Clownfish", "TROPICAL_FISH", "fish", ("f27a1a", "b04a08", "ffc080"), 1),
    ("enchanted_lily_pad", "Enchanted Lily Pad", "LILY_PAD", "leaf", ("3f9a3a", "226a20", "8fdc86"), 1),
    ("enchanted_ink_sac", "Enchanted Ink Sac", "INK_SAC", "orb", ("22222e", "0e0e16", "5a5a7a"), 1),
    ("enchanted_cooked_fish", "Enchanted Cooked Fish", "COOKED_COD", "fish", ("a8763a", "6e4818", "dca868"), 2),
    ("enchanted_cooked_salmon", "Enchanted Cooked Salmon", "COOKED_SALMON", "fish", ("d8803a", "984c14", "ffbe80"), 2),
]

SIZE = 16
OUTLINE = (14, 11, 24)


def hexrgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


# ---------------------------------------------------------------- shape masks (x, y in 0..15) -> bool

def _ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def _bump(x, y):
    # deterministic wobble so blobs are not perfect ellipses
    return ((x * 7 + y * 13) % 5) * 0.035


def shape_gem(x, y):
    return abs(x - 7.5) / 6.0 + abs(y - 8.0) / 6.8 <= 1.0


def shape_ingot(x, y):
    if not 5 <= y <= 11:
        return False
    shift = (11 - y) * 0.6
    return 2 + shift <= x <= 12 + shift


def shape_lump(x, y):
    return ((x - 7.5) / 6.0) ** 2 + ((y - 8.5) / 5.2) ** 2 <= 1.0 - _bump(x, y)


def shape_pile(x, y):
    return 5 <= y <= 12 and abs(x - 7.5) <= (y - 4) * 0.85 and (y < 12 or abs(x - 7.5) < 6)


def _inside(poly, x, y):
    inside = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


CUBE_TOP = [(7.5, 1.5), (14.5, 5.0), (7.5, 8.5), (0.5, 5.0)]
CUBE_LEFT = [(0.5, 5.5), (7.5, 9.0), (7.5, 15.0), (0.5, 11.5)]
CUBE_RIGHT = [(7.5, 9.0), (14.5, 5.5), (14.5, 11.5), (7.5, 15.0)]


def shape_cube(x, y):
    px, py = x + 0.5, y + 0.5
    return _inside(CUBE_TOP, px, py) or _inside(CUBE_LEFT, px, py) or _inside(CUBE_RIGHT, px, py)


def shape_log(x, y):
    return ((x - 7.5) / 6.4) ** 2 + ((y - 7.5) / 6.4) ** 2 <= 1.0


def shape_fish(x, y):
    body = ((x - 6.5) / 5.6) ** 2 + ((y - 8.0) / 3.6) ** 2 <= 1.0
    tail = 11 <= x <= 14 and abs(y - 8.0) <= (x - 10.0) * 1.1
    return body or tail


def shape_orb(x, y):
    return ((x - 7.5) / 5.8) ** 2 + ((y - 7.5) / 5.8) ** 2 <= 1.0


def shape_stick(x, y):
    return abs((x + y) - 15.0) <= 1.6 and 2 <= x <= 13


def shape_carrot(x, y):
    body = abs((x + (15 - y)) - 15.0) <= 0 or False
    # tilted triangle from top-left (wide) to bottom-right (point)
    d = (x + y) / 2.0
    cone = 3 <= x <= 14 and 4 <= y <= 14 and abs((x - y) + 1) <= (14 - d) * 0.55 + 0.4
    leaves = (2 <= x <= 5 and 1 <= y <= 4) and (x + y) <= 7
    return cone or leaves or body


def shape_sheaf(x, y):
    stalks = any(abs(x - sx) <= 0.5 for sx in (4, 7.5, 11)) and 3 <= y <= 14
    heads = any(((x - sx) / 1.8) ** 2 + ((y - 4.0) / 3.0) ** 2 <= 1.0 for sx in (4, 7.5, 11))
    band = 8 <= y <= 9 and 3 <= x <= 12
    return stalks or heads or band


def shape_cactus(x, y):
    trunk = 5 <= x <= 10 and 1 <= y <= 14
    left = 2 <= x <= 5 and 6 <= y <= 8 or (2 <= x <= 3 and 4 <= y <= 8)
    right = 10 <= x <= 13 and 8 <= y <= 10 or (12 <= x <= 13 and 5 <= y <= 10)
    return trunk or left or right


def shape_leaf(x, y):
    return ((x - 7.5) / 6.6) ** 2 + ((y - 9.0) / 3.4) ** 2 <= 1.0 and not (x >= 8 and y == 9 and x <= 14 and False)


SHAPES = {
    "gem": shape_gem, "ingot": shape_ingot, "lump": shape_lump, "pile": shape_pile, "cube": shape_cube,
    "log": shape_log, "fish": shape_fish, "orb": shape_orb, "stick": shape_stick, "carrot": shape_carrot,
    "sheaf": shape_sheaf, "cactus": shape_cactus, "leaf": shape_leaf, "striped": shape_orb,
}


# ---------------------------------------------------------------- painting

def paint(shape, colours, tier):
    main, shade, light = (hexrgb(c) for c in colours)
    mask = SHAPES[shape]
    grid = [[None] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if mask(x, y):
                grid[y][x] = main

    def filled(x, y):
        return 0 <= x < SIZE and 0 <= y < SIZE and grid[y][x] is not None

    # light from the top left: highlight an edge pixel without a neighbour above-left, shade the opposite edge
    painted = [row[:] for row in grid]
    for y in range(SIZE if shape != 'cube' else 0):
        for x in range(SIZE):
            if not filled(x, y):
                continue
            if not filled(x - 1, y - 1) or not filled(x, y - 1) and not filled(x - 1, y):
                painted[y][x] = light
            elif not filled(x + 1, y + 1) or not filled(x + 1, y) or not filled(x, y + 1):
                painted[y][x] = shade
    grid = painted

    if shape == "cube":
        for y in range(SIZE):
            for x in range(SIZE):
                if filled(x, y):
                    px, py = x + 0.5, y + 0.5
                    if _inside(CUBE_TOP, px, py):
                        grid[y][x] = light
                    elif _inside(CUBE_RIGHT, px, py):
                        grid[y][x] = shade
                    else:
                        grid[y][x] = main
    elif shape == "log":
        for y in range(SIZE):
            for x in range(SIZE):
                if filled(x, y):
                    r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                    grid[y][x] = shade if r > 5.0 else light if 2.0 < r <= 3.0 or r <= 1.0 else main
    elif shape == "striped":
        for y in range(SIZE):
            for x in range(SIZE):
                if filled(x, y) and x in (4, 7, 8, 11):
                    grid[y][x] = shade
    elif shape in ("fish",):
        grid[7][4] = OUTLINE  # eye
        for y in range(5, 11):
            if filled(8, y):
                grid[y][8] = shade
    elif shape == "orb" and colours[0] in ("2fb86a",):
        grid[7][7] = OUTLINE
        grid[7][8] = OUTLINE
        grid[8][7] = OUTLINE
        grid[8][8] = OUTLINE
    elif shape == "sheaf":
        for x in range(3, 13):
            if filled(x, 8):
                grid[8][x] = shade

    # outline pass
    out = [row[:] for row in grid]
    for y in range(SIZE):
        for x in range(SIZE):
            if grid[y][x] is None:
                if any(filled(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = OUTLINE
    grid = out

    # tier sparkle: enchanted = green four-point star, compacted = blue star plus a second small one
    star = (166, 255, 166) if tier == 1 else (150, 200, 255)
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        px, py = 12 + dx, 3 + dy
        if 0 <= px < SIZE and 0 <= py < SIZE:
            grid[py][px] = star if (dx, dy) != (0, 0) else (255, 255, 255)
    if tier == 2:
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            px, py = 3 + dx, 12 + dy
            if 0 <= px < SIZE and 0 <= py < SIZE:
                grid[py][px] = star if (dx, dy) != (0, 0) else (255, 255, 255)
    return grid


def png_bytes(grid):
    raw = bytearray()
    for row in grid:
        raw.append(0)
        for px in row:
            raw.extend((px[0], px[1], px[2], 255) if px else (0, 0, 0, 0))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def outputs():
    files = {}
    for item_id, _name, _material, shape, colours, tier in ITEMS:
        files[PACK / "textures/item/skills" / f"{item_id}.png"] = png_bytes(paint(shape, colours, tier))
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/skills/{item_id}"}}
        files[PACK / "models/item/skills" / f"{item_id}.json"] = (json.dumps(model, indent=2) + "\n").encode()
        definition = {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/skills/{item_id}"}}
        files[PACK / "items/skills" / f"{item_id}.json"] = (json.dumps(definition, indent=2) + "\n").encode()
    return files


def main(argv):
    files = outputs()
    if "--check" in argv:
        bad = [str(p) for p, data in files.items() if not p.exists() or p.read_bytes() != data]
        if bad:
            print("out of date:", *bad, sep="\n  ")
            return 1
        print(f"{len(files)} files up to date")
        return 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    print(f"wrote {len(files)} files for {len(ITEMS)} items")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
