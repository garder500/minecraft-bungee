#!/usr/bin/env python3
"""Paints the 32x32 icons of the Skyblock skills feature (enchanted and compacted resources).

    python3 resource-pack/art/skills_icons.py            # write textures, models and item definitions
    python3 resource-pack/art/skills_icons.py --check    # fail when the committed files differ from the output
    python3 resource-pack/art/skills_icons.py --sheet out.png

Stdlib only and fully deterministic (painter: sb_paint.py). Every item is the object itself, painted in its own
material: a log shows its bark and rings, a diamond is a cut gem, a block is a block of that ore. The enchantment is
one rim shared by all of them (sb_paint.GLINT, a violet line around the outline with corner sparks); a compacted item
(tier 2) has gold sparks as well. Light from the top left, no anti-aliasing, no noise.
Output, all under assets/vampyuyu/ and all containing "skills" in the path:
    textures/item/skills/<id>.png, models/item/skills/<id>.json, items/skills/<id>.json
The same ITEMS table is the reference for the enchanted_items rows of the survival plugin's skills seed. Its shape and
colour columns are the old 16 px hints: PAINT below decides what is drawn.
"""
import json
import math
import pathlib
import sys

from sb_paint import (Art, INK, GOLD, WOOD, DARKWOOD, STONE, IRON, STEEL, LEAF, RED, WHITE, PAPER,
                      ramp, tones, rect, rrect, ell, ring, poly, line, path, pts, shift, flip, edge,
                      ball, grad, vcyl, hcyl, sheet)

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

SIZE = 32

COAL = tones("0e0c16", "1c1a26", "302e3c", "4c4a5a", "787688")
DIAMOND = tones("14406e", "1f7cb0", "3cc4dc", "8cf0ee", "e2fffa", "ffffff")
EMERALD = tones("0a3a34", "0e7a44", "22b855", "5ce87a", "b8ffb4", "f0fff0")
LAPIS = tones("101850", "1c3496", "2c5cd8", "5a94f8", "a8d0ff")
REDSTONE = tones("4a0818", "8a0e1c", "d01c1c", "ff5a3a", "ffb08a")
QUARTZ = tones("7a6a8a", "b4a4b0", "e0d4d0", "f6eee6", "ffffff")
OBSIDIAN = tones("0e0a1c", "1e1436", "34245c", "5a3e94", "9a7ad8")
GLOW = tones("8a4a10", "c8861a", "f4c43a", "ffec7a", "fffcd0")
SLIME = tones("145a34", "2c9a3a", "5cd04a", "9cf07a", "dcffb8")
COBBLE = tones("33313f", "55535f", "7c7a85", "a3a2a8", "cbcbcd")
EMBER = tones("7a1c14", "d8481c", "ff9a2a", "ffe27a")
BONE = tones("5a5470", "9c98a8", "d0ccc4", "f0ece0", "ffffff")


# ---------------------------------------------------------------- blocks
class Cube:
    """An isometric block: top, left and right faces with their own (u, v) coordinates in 0..1."""

    def __init__(self, cx=15.5, top=2, hw=13.5, hh=6.5, height=14):
        self.cx, self.top, self.hw, self.hh, self.height = cx, top, hw, hh, height
        o = {"top": ((cx, top), (hw, hh), (-hw, hh)),
             "left": ((cx - hw, top + hh), (hw, hh), (0, height)),
             "right": ((cx, top + 2 * hh), (hw, -hh), (0, height))}
        self.axes = o
        self.faces = {k: poly(self.at(k, 0, 0), self.at(k, 1, 0), self.at(k, 1, 1), self.at(k, 0, 1)) for k in o}
        self.faces["left"] -= self.faces["top"]
        self.faces["right"] -= self.faces["top"] | self.faces["left"]

    def at(self, face, u, v):
        (ox, oy), (ux, uy), (vx, vy) = self.axes[face]
        return (ox + ux * u + vx * v, oy + uy * u + vy * v)

    def seg(self, face, u0, v0, u1, v1, w=1.0):
        a, b = self.at(face, u0, v0), self.at(face, u1, v1)
        return line(a[0], a[1], b[0], b[1], w) & self.faces[face]

    def quad(self, face, u0, v0, u1, v1):
        return poly(self.at(face, u0, v0), self.at(face, u1, v0), self.at(face, u1, v1), self.at(face, u0, v1)) & self.faces[face]

    def paint(self, a, mat, lights=(0.88, 0.58, 0.26)):
        cx, top, hw, hh, h = self.cx, self.top, self.hw, self.hh, self.height
        a.fill(self.faces["top"], mat, grad(cx - hw, top, cx + hw, top + 2 * hh, lights[0] + 0.1, lights[0] - 0.12), ink=False)
        a.fill(self.faces["left"], mat, grad(0, top + hh, 0, top + 2 * hh + h, lights[1] + 0.1, lights[1] - 0.14), ink=False)
        a.fill(self.faces["right"], mat, grad(0, top + hh, 0, top + 2 * hh + h, lights[2] + 0.1, lights[2] - 0.12), ink=False)
        a.tone(self.seg("left", 0, 0, 1, 0) | self.seg("left", 1, 0, 1, 1), +1)      # the lit front edges
        return self

    def frame(self, a, inset=0.18, delta=-1):
        for face in ("top", "left", "right"):
            lo, hi = inset, 1 - inset
            a.tone(self.seg(face, lo, lo, hi, lo) | self.seg(face, lo, lo, lo, hi), delta)
            a.tone(self.seg(face, hi, lo, hi, hi) | self.seg(face, lo, hi, hi, hi), -delta if face == "top" else delta)


def block(mat, deco=None, lights=(0.88, 0.58, 0.26)):
    def f():
        a = Art()
        c = Cube().paint(a, mat, lights)
        if deco:
            deco(a, c)
        return a
    return f


def d_cobble(a, c):
    for face, segs in (("top", ((0.3, 0, 0.35, 0.45), (0.35, 0.45, 0, 0.55), (0.35, 0.45, 0.7, 0.6), (0.7, 0.6, 0.75, 1), (0.7, 0.6, 1, 0.4), (0.62, 0, 0.66, 0.25))),
                       ("left", ((0, 0.4, 0.45, 0.45), (0.45, 0.45, 0.5, 0), (0.45, 0.45, 0.6, 0.8), (0.6, 0.8, 1, 0.7), (0.6, 0.8, 0.5, 1), (0.2, 0.45, 0.25, 1))),
                       ("right", ((0, 0.5, 0.4, 0.45), (0.4, 0.45, 0.45, 0), (0.4, 0.45, 0.55, 1), (0.55, 0.7, 1, 0.6), (0.75, 0.62, 0.8, 0)))):
        for s in segs:
            a.tone(c.seg(face, *s), -1)
    a.tone(c.quad("top", 0.4, 0.05, 0.58, 0.3) | c.quad("left", 0.05, 0.05, 0.4, 0.3) | c.quad("right", 0.5, 0.1, 0.7, 0.4), +1)


def d_plate(a, c):
    c.frame(a, 0.14)
    for face in ("left", "right"):
        a.tone(c.seg(face, 0.14, 0.5, 0.86, 0.5), -1)
    a.tone(c.seg("top", 0.5, 0.14, 0.5, 0.86), -1)
    for face in ("left", "right"):
        for u, v in ((0.26, 0.28), (0.74, 0.28), (0.26, 0.74), (0.74, 0.74)):
            p = c.at(face, u, v)
            a.tone({(round(p[0]), round(p[1]))}, +2)


def d_bevel(a, c):
    c.frame(a, 0.2)
    a.tone(c.seg("top", 0.3, 0.42, 0.55, 0.42) | c.seg("left", 0.32, 0.36, 0.5, 0.36) | c.seg("left", 0.32, 0.36, 0.32, 0.6), +1)


def d_facets(a, c):
    for face in ("top", "left", "right"):
        a.tone(c.seg(face, 0, 0.5, 0.5, 0) | c.seg(face, 0.5, 1, 1, 0.5), -1)
        a.tone(c.seg(face, 0.5, 0, 1, 0.5) | c.seg(face, 0, 0.5, 0.5, 1), +1)
    a.tone(c.quad("left", 0.38, 0.38, 0.62, 0.62) | c.quad("top", 0.4, 0.4, 0.6, 0.6), +1)


def d_specks(colour):
    def f(a, c):
        dark = (("top", 0.2, 0.5), ("top", 0.7, 0.3), ("left", 0.2, 0.3), ("left", 0.7, 0.7), ("right", 0.3, 0.6), ("right", 0.75, 0.25),
                ("top", 0.5, 0.8), ("left", 0.45, 0.15), ("right", 0.55, 0.85))
        for face, u, v in dark:
            a.tone(c.quad(face, u - 0.08, v - 0.08, u + 0.08, v + 0.08), -1)
        for face, u, v in (("top", 0.45, 0.35), ("left", 0.3, 0.62), ("right", 0.5, 0.3), ("left", 0.78, 0.3), ("top", 0.75, 0.7)):
            p = c.at(face, u, v)
            a.put(pts(round(p[0]), round(p[1]), round(p[0]) + 1, round(p[1])), colour)
    return f


def d_coal(a, c):
    for face, u, v, s in (("top", 0.3, 0.4, 0.16), ("top", 0.7, 0.65, 0.12), ("left", 0.3, 0.3, 0.16), ("left", 0.7, 0.7, 0.14),
                          ("right", 0.4, 0.55, 0.16), ("right", 0.78, 0.2, 0.1)):
        a.tone(c.quad(face, u - s, v - s, u + s, v + s), +1)
        a.tone(c.seg(face, u - s, v + s, u + s, v + s) | c.seg(face, u + s, v - s, u + s, v + s), -2)


def d_redstone(a, c):
    for face in ("top", "left", "right"):
        a.tone(c.seg(face, 0.5, 0.1, 0.5, 0.9, 1.6) | c.seg(face, 0.1, 0.5, 0.9, 0.5, 1.6), +1)
        a.tone(c.quad(face, 0.4, 0.4, 0.6, 0.6), +2)
    c.frame(a, 0.1)


def d_quartz(a, c):
    for v in (0.25, 0.5, 0.75):
        a.tone(c.seg("left", 0, v, 1, v) | c.seg("right", 0, v, 1, v), -1)
    c.frame(a, 0.12)


def d_slime(a, c):
    inner = Cube(15.5, 8.5, 7, 3.4, 7.5)
    for k, d in (("top", 0), ("left", -1), ("right", -2)):
        a.tone(inner.faces[k], d - 1 if k != "top" else -1)
    a.tone(c.seg("top", 0.12, 0.3, 0.12, 0.7) | c.seg("top", 0.12, 0.3, 0.4, 0.12) | c.seg("left", 0.1, 0.12, 0.1, 0.5), +2)


def d_obsidian(a, c):
    for face, s in (("top", (0.2, 0.3, 0.6, 0.5)), ("left", (0.15, 0.2, 0.5, 0.75)), ("right", (0.3, 0.2, 0.75, 0.6)), ("left", (0.6, 0.1, 0.85, 0.5))):
        a.tone(c.seg(face, *s), +2)
    for face, s in (("top", (0.6, 0.1, 0.9, 0.6)), ("left", (0.3, 0.5, 0.4, 1)), ("right", (0.1, 0.6, 0.5, 1))):
        a.tone(c.seg(face, *s), -1)


# ---------------------------------------------------------------- minerals
def facets(a, mat, parts):
    for points, value in parts:
        a.fill(poly(*points), mat, value, ink=False)


def ingot(mat):
    def f():
        a = Art()
        p1, p2, p3, p4 = (2, 15), (19, 4), (29, 9), (12, 20)
        drop = 8
        facets(a, mat, [((p1, p4, (p4[0], p4[1] + drop), (p1[0], p1[1] + drop)), 0.56),
                        ((p4, p3, (p3[0], p3[1] + drop), (p4[0], p4[1] + drop)), 0.24),
                        ((p1, p2, p3, p4), 0.78)])
        a.fill(poly((5, 15), (19, 6), (26, 9.5), (12, 18.5)), mat, grad(4, 6, 26, 18, 1.0, 0.72), ink=False)   # the raised top
        a.tone(line(2, 15, 12, 20) | line(12, 20, 29, 9), +1)
        a.tone(line(12, 21, 12, 28), +1)
        a.tone(line(13, 27, 29, 16) | line(3, 23, 11, 27), -1)
        a.tone(line(15, 22, 26, 15), +1)
        a.set_tone(pts(8, 13, 9, 12, 10, 12, 11, 11), len(mat) - 1)
        return a
    return f


def diamond():
    a = Art()
    top, mid, bot = 4, 12, 28
    facets(a, DIAMOND, [(((9, top), (22, top), (28, mid), (15.5, bot), (3, mid)), 0.5),
                        (((9, top), (22, top), (20, mid), (11, mid)), 0.62),
                        (((9, top), (11, mid), (3, mid)), 0.78),
                        (((22, top), (28, mid), (20, mid)), 0.36),
                        (((3, mid), (11, mid), (15.5, bot)), 0.52),
                        (((11, mid), (20, mid), (15.5, bot)), 0.36),
                        (((20, mid), (28, mid), (15.5, bot)), 0.1)])
    a.set_tone(line(3, mid, 28, mid), 4)
    a.set_tone(line(11, mid, 15.5, bot), 3)
    a.set_tone(line(9, top, 11, mid) | line(10, 5, 15, 5) | pts(12, 7, 13, 7, 12, 8, 6, 10, 7, 9, 13, 15, 13, 16, 14, 17), 5)
    a.set_tone(line(20, mid + 1, 16, bot - 2), 0)
    return a


def emerald():
    a = Art()
    o = [(9, 3), (22, 3), (27, 8), (27, 23), (22, 28), (9, 28), (4, 23), (4, 8)]
    i = [(11, 8), (20, 8), (22, 10), (22, 21), (20, 23), (11, 23), (9, 21), (9, 10)]
    facets(a, EMERALD, [(o, 0.45),
                        ((o[0], o[1], i[1], i[0]), 0.8), ((o[7], o[0], i[0], i[7]), 0.92), ((o[6], o[7], i[7], i[6]), 0.66),
                        ((o[1], o[2], i[2], i[1]), 0.5), ((o[2], o[3], i[3], i[2]), 0.28), ((o[3], o[4], i[4], i[3]), 0.12),
                        ((o[4], o[5], i[5], i[4]), 0.2), ((o[5], o[6], i[6], i[5]), 0.36),
                        (i, 0.55)])
    a.fill(poly((11, 8), (20, 8), (9, 19), (9, 10)), EMERALD, 0.7, ink=False)
    a.set_tone(line(10, 16, 17, 9) | line(10, 13, 14, 9), 5)
    a.set_tone(path(i + [i[0]]), 2)
    a.set_tone(line(11, 8, 20, 8) | line(9, 10, 9, 21) | pts(10, 9), 4)
    a.set_tone(pts(6, 8, 7, 7, 8, 6), 5)
    return a


def shard(a, mat, bx, by, tx, ty, w, cut=0.72):
    """A hexagonal crystal from its base to its tip: a lit left side and a shaded right side."""
    dx, dy = tx - bx, ty - by
    n = math.hypot(dx, dy)
    px, py = -dy / n * w / 2, dx / n * w / 2
    if px > 0:
        px, py = -px, -py
    sx, sy = bx + dx * cut, by + dy * cut
    a.fill(poly((bx + px, by + py), (sx + px, sy + py), (tx, ty), (sx - px, sy - py), (bx - px, by - py)), mat, 0.3)
    a.fill(poly((bx + px, by + py), (sx + px, sy + py), (tx, ty), (sx, sy), (bx, by)), mat, 0.72, ink=False)
    a.fill(poly((sx + px, sy + py), (tx, ty), (sx, sy)), mat, 0.95, ink=False)
    a.set_tone(line(bx + px * 0.5, by + py * 0.5, sx + px * 0.5, sy + py * 0.5), len(mat) - 1)


def quartz():
    a = Art()
    shard(a, QUARTZ, 9, 26, 5, 8, 7)
    shard(a, QUARTZ, 23, 26, 27, 11, 7)
    shard(a, QUARTZ, 12, 28, 10, 15, 6)
    shard(a, QUARTZ, 16, 28, 17, 2, 9)
    shard(a, QUARTZ, 21, 28, 23, 18, 5)
    a.fill(rrect(4, 25, 27, 29, 2), ramp("a89aa4"), None)
    return a


ROCK = [(((6, 12), (12, 5), (21, 4), (27, 10), (28, 20), (22, 27), (11, 28), (4, 21)), 0.45),
        (((6, 12), (12, 5), (15, 12), (9, 16)), 0.92), (((12, 5), (21, 4), (20, 11), (15, 12)), 0.7),
        (((21, 4), (27, 10), (22, 15), (20, 11)), 0.5), (((9, 16), (15, 12), (20, 11), (22, 15), (16, 21)), 0.58),
        (((4, 21), (6, 12), (9, 16), (11, 23)), 0.66), (((22, 15), (27, 10), (28, 20), (23, 22)), 0.24),
        (((11, 23), (16, 21), (23, 22), (22, 27), (11, 28)), 0.1), (((9, 16), (16, 21), (11, 23)), 0.36)]


def rock(mat):
    def f():
        a = Art()
        whole = poly(*ROCK[0][0])
        for points, value in ROCK:
            a.fill(poly(*points) & whole, mat, value, ink=False)
        a.set_tone(line(7, 12, 12, 6) | pts(13, 7, 14, 8, 10, 14), len(mat) - 1)
        a.tone(line(9, 16, 15, 12) | line(15, 12, 20, 11), +1)
        return a
    return f


def nugget(a, mat, cx, cy, r):
    """A rough faceted stone: a lit facet at the top left, a shaded one at the bottom right."""
    o = [(cx - r * 0.5, cy - r), (cx + r * 0.55, cy - r * 0.85), (cx + r, cy - r * 0.1), (cx + r * 0.7, cy + r * 0.8),
         (cx - r * 0.2, cy + r), (cx - r, cy + r * 0.45), (cx - r * 0.95, cy - r * 0.4)]
    mid = (cx - r * 0.05, cy - r * 0.1)
    a.fill(poly(*o), mat, 0.5)
    for points, value in (((o[6], o[0], mid), 0.95), ((o[0], o[1], mid), 0.72), ((o[1], o[2], mid), 0.5),
                          ((o[2], o[3], mid), 0.24), ((o[3], o[4], mid), 0.1), ((o[4], o[5], mid), 0.36), ((o[5], o[6], mid), 0.66)):
        a.fill(poly(*points) & poly(*o), mat, value, ink=False)


def lapis():
    a = Art()
    nugget(a, LAPIS, 16, 10, 8)
    nugget(a, LAPIS, 9.5, 20, 7.5)
    nugget(a, LAPIS, 21.5, 20.5, 7.5)
    a.put(pts(13, 8, 14, 8, 19, 12, 7, 18, 11, 23, 12, 23, 20, 17, 24, 22, 19, 24), (255, 214, 90))
    return a


HEAPS = [
    ((15.5, 10, 4, 3.6), (11, 15, 5.2, 4.6), (20, 15, 5, 4.4), (15.5, 18, 6, 5), (7, 23, 5.5, 5), (24, 23, 5.5, 5), (15.5, 24, 7.5, 5)),
    ((10, 12, 4, 3.6), (20, 9, 4.4, 4), (15, 15, 5.5, 4.6), (23, 16, 4.6, 4.2), (7, 19, 4.6, 4.2), (13, 22, 6.5, 5.5), (22, 24, 7, 5), (5, 25, 3.6, 3.4)),
    ((15.5, 5, 3, 3), (15.5, 10, 4.6, 4.2), (12, 15, 5, 4.6), (19, 16, 5, 4.6), (15.5, 20, 6.5, 5), (9, 24, 6, 4.6), (22, 24, 6.5, 4.6), (15.5, 25, 6, 4)),
]


def pile(mat, shape=0, sparks=None):
    """A heap of dust clods, the back ones first, with crumbs fallen at its foot."""
    def f():
        a = Art()
        floor = rect(0, 0, 31, 28)
        for cx, cy, rx, ry in HEAPS[shape]:
            a.fill(ell(cx, cy, rx, ry) & floor, mat, ball(cx, cy, rx + 0.6, ry + 0.6))
        for x, y in ((2, 27), (28, 27), (26, 18) if shape == 0 else (4, 15)):
            a.fill(rect(x, y, x + 1, y + 1), mat, grad(x, y, x + 1, y + 1, 0.95, 0.4))
        if sparks:
            for cx, cy, rx, ry in HEAPS[shape][::2]:
                a.put(pts(round(cx - rx * 0.35), round(cy - ry * 0.4)), sparks)
        return a
    return f


def sugar():
    a = Art()
    mat = tones("6a6a9a", "a8acd0", "d8dcee", "f4f6fc", "ffffff")
    for cx, top in ((9, 13), (22, 12), (15.5, 3), (15.5, 16)):
        Cube(cx, top, 7, 3.4, 7).paint(a, mat)
    a.set_tone(pts(13, 6, 14, 5, 7, 16, 20, 15, 13, 19), 4)
    return a


# ---------------------------------------------------------------- wood
def log(bark, wood, style="grooves", extra=None):
    def f():
        a = Art()
        a.fill(rrect(4, 8, 27, 28, 3), bark, vcyl(4, 27), ink=False)
        if style == "grooves":
            for x, y0, y1 in ((7, 14, 21), (10, 18, 27), (13, 15, 20), (16, 20, 27), (19, 14, 22), (22, 18, 25), (8, 24, 27), (24, 13, 16), (14, 23, 26)):
                a.tone(rect(x, y0, x, y1), -1)
                a.tone(rect(x + 1, y0 + 1, x + 1, y1 - 1), +1)
        elif style == "tight":
            for x in range(6, 27, 3):
                a.tone(rect(x, 13 + x % 4, x, 27 - x % 3), -1)
        elif style == "birch":
            for x0, x1, y in ((6, 10, 16), (15, 21, 15), (9, 13, 21), (19, 25, 20), (6, 8, 25), (13, 18, 26), (22, 24, 25), (11, 12, 17)):
                a.put(rect(x0, y, x1, y) | rect(x0 + 1, y + 1, x1 - 1, y + 1), (58, 50, 64))
        elif style == "plates":
            for x, y0, y1 in ((9, 13, 27), (15, 14, 27), (21, 13, 27)):
                a.tone(rect(x, y0, x, y1), -1)
            for x0, x1, y in ((5, 8, 18), (10, 14, 22), (16, 20, 17), (22, 26, 21), (10, 14, 15), (16, 20, 25)):
                a.tone(rect(x0, y, x1, y), -1)
        a.fill(ell(15.5, 8.5, 11.5, 5.8), bark, 0.3, ink=False)
        a.fill(ell(15.5, 8, 10, 4.6), wood, grad(7, 4, 25, 12, 1.0, 0.5), ink=False)
        a.tone(ring(15.5, 8, 7, 3.1, 1.0), -1)
        a.tone(ring(15.5, 8, 3.8, 1.6, 1.0), -1)
        a.tone(ell(15.5, 8, 1.2, 0.6), -2)
        a.tone(line(16, 9, 22, 10), -1)
        if extra:
            extra(a)
        return a
    return f


def vine(a):
    a.fill(path([(6, 12), (10, 16), (9, 21), (14, 25), (13, 28)], 1.6), LEAF, 0.5, ink=True)
    for cx, cy in ((11, 14), (7, 20), (16, 24)):
        a.fill(ell(cx, cy, 2.4, 1.8), LEAF, ball(cx, cy, 2.6, 2))


# ---------------------------------------------------------------- farming
def bread():
    a = Art()
    crust = tones("6a3214", "a85a1c", "d88a2c", "f4b84c", "ffe08a")
    loaf = rrect(2, 9, 29, 24, 7)
    a.fill(loaf, crust, hcyl(9, 24, 0.95, 0.15, 0.25))
    a.tone(rect(4, 22, 27, 24) & loaf, -1)
    crumb = tones("d8a85a", "f4d898", "fff2c8")
    for x in (7, 14, 21):
        cut = poly((x, 11), (x + 3, 10), (x + 5, 16), (x + 2, 17))
        a.fill(cut, crumb, grad(x, 10, x + 5, 17, 1.0, 0.3), ink=True)
    a.set_tone(pts(5, 12, 6, 11, 4, 14), 4)
    return a


def carrot(mat, leaf=LEAF):
    def f():
        a = Art()
        for points in (((21, 9), (20, 2), (24, 6)), ((23, 8), (27, 2), (28, 7)), ((23, 10), (29, 8), (27, 12))):
            a.fill(poly(*points) | line(22, 9, points[1][0], points[1][1], 1.8), leaf, grad(20, 2, 29, 12, 0.95, 0.3))
        root = poly((18, 7), (22, 8), (25, 11), (25, 15), (13, 25), (3, 29), (7, 19))
        a.fill(root, mat, grad(8, 10, 20, 24, 1.0, 0.1))
        for x0, y0, x1, y1 in ((17, 10, 20, 13), (13, 15, 17, 18), (9, 20, 12, 23), (20, 16, 23, 14), (7, 24, 9, 26)):
            a.tone(line(x0, y0, x1, y1), -1)
        a.set_tone(line(11, 15, 16, 10), len(mat) - 1)
        return a
    return f


def potato(mat, baked=False):
    def f():
        a = Art()
        shape = ell(15.5, 16, 12.5, 9) | ell(8, 13, 5.5, 5.5) | ell(23, 19, 5.5, 6)
        a.fill(shape, mat, ball(15, 16, 14, 11))
        if baked:
            flesh = tones("c89a3a", "f0d06a", "fff0a8", "fffce0")
            split = poly((7, 13), (13, 10), (22, 12), (25, 17), (18, 16), (11, 17))
            a.fill(split, flesh, grad(7, 10, 25, 17, 1.0, 0.4))
            a.fill(rect(13, 11, 18, 14), tones("e0a81e", "ffd84a", "fff4a0"), None)
            a.tone(pts(6, 19, 10, 22, 19, 22, 23, 21, 14, 24), -1)
        else:
            for x, y in ((9, 12), (17, 11), (22, 17), (12, 19), (18, 22), (6, 16)):
                a.tone(pts(x, y, x + 1, y), -1)
                a.tone(pts(x, y - 1), +1)
        return a
    return f


def pumpkin():
    a = Art()
    mat = tones("6a2414", "a84a14", "e07a1a", "f8a83a", "ffd47a")
    a.fill(rect(14, 2, 17, 8) | pts(18, 2, 19, 2, 18, 3), tones("3a3a1c", "5a6a24", "8a9a3a", "b8c85a"), None)
    for cx, rx in ((5.5, 4), (25.5, 4), (10, 5), (21, 5), (15.5, 5.5)):
        a.fill(ell(cx, 18, rx, 10.5 - abs(cx - 15.5) * 0.18), mat, ball(15.5 + (cx - 15.5) * 0.75, 17, 15, 12), ink=True)
    a.set_tone(pts(8, 11, 9, 10, 13, 10, 14, 9, 7, 13), 4)
    return a


def melon():
    a = Art()
    rind = tones("123a22", "1f6a2c", "3f9a38", "7cc850", "c0ec84")
    flesh = tones("8a1430", "d02c3c", "f8584c", "ff8a7a", "ffc4b4")
    half = ell(15.5, 9, 14, 19.5) & rect(0, 9, 31, 31)
    a.fill(half, rind, grad(4, 9, 27, 28, 0.9, 0.15), ink=False)
    a.fill(ell(15.5, 9, 12, 17) & rect(0, 9, 31, 31), tones("c8dc9a", "f0f8d0"), 0.7, ink=False)
    a.fill(ell(15.5, 9, 11, 15.6) & rect(0, 9, 31, 31), flesh, grad(5, 9, 26, 26, 0.95, 0.25), ink=False)
    a.fill(rect(2, 8, 29, 9), flesh, 0.9, ink=False)
    a.put(pts(9, 13, 9, 14, 14, 17, 14, 18, 20, 13, 20, 14, 12, 21, 18, 20, 18, 21, 23, 16, 7, 17), (48, 20, 36))
    return a


def cactus():
    a = Art()
    green = tones("0e3a2c", "1a6a30", "3a9a3a", "6cc84e", "b0ec80")
    a.fill(rrect(3, 9, 8, 19, 2) | rect(3, 15, 12, 19), green, vcyl(3, 9), ink=False)
    a.fill(rrect(23, 12, 28, 22, 2) | rect(19, 18, 28, 22), green, vcyl(23, 28), ink=False)
    a.fill(rrect(10, 4, 21, 29, 4), green, vcyl(10, 21))
    for x in (13, 17):
        a.tone(rect(x, 6, x, 28), -1)
    a.tone(rect(5, 11, 5, 17) | rect(25, 14, 25, 20), -1)
    a.put(pts(11, 9, 15, 7, 19, 10, 11, 15, 15, 13, 19, 17, 11, 21, 15, 20, 19, 24, 15, 26, 4, 12, 7, 14, 24, 15, 27, 18), (250, 244, 200))
    flower = tones("a01c5a", "e8488a", "ffa0c0")
    a.fill(ell(15.5, 3.5, 3.2, 2.4), flower, ball(15.5, 3.5, 3.4, 2.6))
    a.put(pts(15, 3, 16, 3), (255, 226, 110))
    return a


def cocoa():
    a = Art()
    pod = tones("4a1c14", "7a3a1a", "b0642a", "dc9a4a", "f8cc84")
    bean = tones("1e0e14", "3e1e16", "62341e", "8a5230", "b47a4c")
    a.fill(path([(17, 5), (18, 2), (21, 1)], 2), DARKWOOD, None)
    a.fill(ell(17.5, 14, 9, 11.5), pod, ball(17.5, 14, 9.5, 12))
    for rx in (3, 6.5):
        a.tone(ring(17.5, 14, rx, 11, 1) - rect(16, 0, 19, 31), -1)
    a.set_tone(pts(12, 7, 13, 6, 11, 9, 11, 10), 4)
    for cx, cy in ((7, 22), (14, 25), (23, 25)):
        a.fill(ell(cx, cy, 4.6, 3.4), bean, ball(cx, cy, 5, 3.8))
        a.tone(line(cx - 2, cy, cx + 2, cy), -1)
    return a


def nether_wart():
    a = Art()
    wart = tones("3a0a2c", "7a1030", "b8203a", "e84a50", "ff9a8a")
    a.fill(rect(14, 21, 17, 29) | rect(9, 19, 11, 24) | rect(20, 18, 22, 23), tones("2a1420", "4a2028", "6a3030"), None)
    for cx, cy, rx, ry in ((8, 13, 6, 7), (23, 12, 6, 7), (15.5, 11, 7.5, 9), (11, 20, 4, 3.5), (20.5, 20, 4, 3.5)):
        a.fill(ell(cx, cy, rx, ry), wart, ball(cx, cy, rx + 0.5, ry + 0.5))
    a.set_tone(pts(12, 6, 13, 5, 5, 10, 20, 9), 4)
    a.tone(pts(15, 14, 17, 12, 9, 15, 24, 14, 13, 9), -1)
    return a


# ---------------------------------------------------------------- fish
def fish(back, belly, deco=None, cooked=False):
    def f():
        a = Art()
        tail = poly((21, 16), (29, 8), (27, 16), (29, 25))
        a.fill(tail, back, grad(21, 8, 29, 25, 0.8, 0.2))
        a.tone(line(23, 15, 28, 10) | line(23, 17, 28, 23), -1)
        a.fill(poly((8, 11), (15, 4), (19, 6), (20, 11)), back, grad(8, 4, 20, 11, 0.9, 0.4))
        a.fill(poly((10, 21), (14, 27), (17, 26), (16, 21)), back, 0.3)
        b = ell(13, 16, 11, 7)
        a.fill(b, back, ball(13, 15, 12, 8))
        a.fill(ell(13, 18.5, 9.5, 4) & b, belly, grad(4, 16, 22, 23, 0.9, 0.35), ink=False)
        a.tone(ring(7, 16, 4, 6, 1) & rect(9, 10, 12, 22), -1)                   # the gill
        if deco:
            deco(a, b)
        if cooked:
            for x in (8, 12, 16, 20):
                a.tone(line(x, 11, x - 3, 21, 1.4) & b, -2)
            a.put(pts(5, 14, 6, 15, 6, 14, 5, 15), (60, 34, 30))
        else:
            a.put(rect(5, 13, 7, 15), (255, 255, 255))
            a.put(pts(6, 14, 6, 15, 7, 14, 7, 15), INK)
        a.tone(pts(2, 17, 3, 17, 4, 18), -2)
        a.set_tone(line(8, 11, 15, 10), len(back) - 1)
        return a
    return f


def clown_bands(a, b):
    for x0 in (9, 17):
        a.fill(rect(x0, 8, x0 + 2, 24) & b, WHITE, grad(0, 9, 0, 23, 0.95, 0.5), ink=True)
    a.fill(rect(27, 8, 29, 25) & a.mask(), WHITE, 0.8, ink=False)


def salmon_spots(a, b):
    a.tone(pts(11, 11, 14, 12, 17, 11, 19, 13, 9, 13) & b, -1)
    a.tone(line(10, 16, 21, 16) & b, +1)


def cod_spots(a, b):
    a.tone(pts(12, 12, 15, 13, 18, 12, 10, 14, 20, 14, 16, 15) & b, -1)


def pufferfish():
    a = Art()
    yel = tones("7a4a14", "c08a1a", "f0c42c", "ffe66a", "fff8c0")
    spikes = set()
    for i in range(12):
        ang = i * math.pi / 6 + 0.26
        cx, cy = 15 + 11 * math.cos(ang), 16 + 10.5 * math.sin(ang)
        tx, ty = 15 + 14 * math.cos(ang), 16 + 13.5 * math.sin(ang)
        spikes |= line(cx, cy, tx, ty, 1.6)
    a.fill(spikes, yel, 0.3, ink=False)
    a.fill(poly((23, 16), (29, 11), (28, 16), (29, 21)), yel, 0.5)
    body_ = ell(15, 16, 11, 10.5)
    a.fill(body_, yel, ball(15, 16, 11.5, 11))
    a.fill(ell(15, 21, 8.5, 5) & body_, WHITE, grad(6, 17, 24, 26, 0.95, 0.5), ink=False)
    a.tone(pts(11, 9, 15, 8, 19, 10, 9, 12, 21, 13, 14, 11) & body_, -1)
    for cx in (9, 17):
        a.put(ell(cx + 0.5, 14.5, 2.2, 2.2), (255, 255, 255))
        a.put(rect(cx, 14, cx + 1, 15), INK)
    a.fill(rect(12, 19, 14, 20), tones("c84a3a", "f08a6a"), 0.5)
    return a


def ink_sac():
    a = Art()
    ink = tones("0a0a1c", "1a1a34", "2e2e54", "4c4c80", "8a8ac0")
    a.fill(poly((12, 12), (19, 12), (18, 5), (20, 2), (11, 2), (13, 5)), ink, grad(11, 2, 20, 12, 0.85, 0.3))
    a.fill(rect(12, 6, 18, 7), tones("7a5a3a", "c8a06a"), 0.6)
    a.fill(ell(15.5, 19.5, 10.5, 9.5), ink, ball(15.5, 19.5, 11, 10))
    a.set_tone(path([(8, 16), (9, 14), (11, 13)]) | pts(8, 18), 4)
    a.tone(path([(20, 24), (22, 21), (23, 18)]), +1)
    return a


def lily_pad():
    a = Art()
    pad = ell(15.5, 17, 13.5, 10.5) - poly((15.5, 17), (30, 9), (30, 17))
    a.fill(pad, LEAF, grad(4, 8, 26, 28, 0.95, 0.2), rim=0.14)
    for x1, y1 in ((4, 13), (5, 22), (12, 27), (21, 26), (27, 21), (10, 8), (19, 8)):
        a.tone(line(15.5, 17, x1, y1) & pad, -1)
    petal = tones("b84a8a", "f08ab8", "ffc8dc", "ffffff")
    for cx, cy in ((9, 12), (13, 10), (11, 15), (15, 13)):
        a.fill(ell(cx, cy, 2.6, 2.2), petal, ball(cx, cy, 2.8, 2.4))
    a.put(pts(12, 12, 12, 13), (255, 214, 90))
    return a


# ---------------------------------------------------------------- combat
def rotten_flesh():
    a = Art()
    meat = tones("4a1424", "8a2c2c", "c0563a", "e88a58", "ffc090")
    rot = tones("2a3a1c", "4e6a26", "86a03a", "bcd060")
    shape = ell(13, 18, 11, 9) | ell(21, 11, 7.5, 7) | poly((3, 16), (9, 7), (16, 8))
    a.fill(shape, meat, ball(15, 15, 15, 14))
    for cx, cy, rx, ry in ((9, 12, 3.5, 2.6), (20, 19, 4, 3), (22, 8, 2.6, 2), (9, 23, 3, 2)):
        a.fill(ell(cx, cy, rx, ry) & shape, rot, ball(cx, cy, rx + 0.5, ry + 0.5), ink=False)
    a.tone(path([(5, 18), (10, 17), (14, 19)]) | path([(15, 11), (18, 13), (17, 16)]) | path([(13, 24), (17, 24)]), -1)
    a.fill(ell(25, 23, 3, 3), BONE, ball(25, 23, 3.2))
    return a


def bone():
    a = Art()
    a.fill(line(8, 23, 23, 8, 4.4), BONE, grad(10, 10, 20, 20, 1.0, 0.25))
    for cx, cy in ((5, 22), (9, 26), (22, 5), (26, 9)):
        a.fill(ell(cx, cy, 3.2, 3.2), BONE, ball(cx, cy, 3.4), ink=False)
    a.tone(line(11, 22, 22, 11), -1)
    a.set_tone(line(9, 20, 20, 9), 4)
    return a


def string():
    a = Art()
    yarn = tones("6a6a94", "a4a8c8", "d6daea", "f4f6fc", "ffffff")
    a.fill(path([(22, 22), (26, 25), (24, 28), (28, 29)], 1.8), yarn, 0.6)
    a.fill(ell(14, 15.5, 11.5, 11.5), yarn, ball(14, 15.5, 12))
    for r, cx, cy in ((9, 9, 12), (12, 20, 8), (14, 6, 22), (8, 20, 20)):
        a.tone(ring(cx, cy, r, r, 1) & ell(14, 15.5, 10.5, 10.5), -1)
    return a


def orb(mat, deco=None):
    def f():
        a = Art()
        a.fill(ell(15.5, 15.5, 13, 13), mat, ball(15.5, 15.5, 13.5))
        if deco:
            deco(a)
        a.set_tone(pts(9, 7, 10, 6, 11, 6, 8, 8, 8, 9), len(mat) - 1)
        return a
    return f


def pearl_swirl(a):
    a.tone((ring(15.5, 15.5, 9, 9, 1.4) & rect(14, 5, 28, 26)) | (ring(15.5, 15.5, 5, 5, 1.4) & rect(4, 9, 17, 22)), -1)
    a.tone(ell(15.5, 15.5, 1.6), -2)


def eye_iris(a):
    iris = tones("0a2a1c", "14562c", "2a8a3a", "5cc050")
    a.fill(ell(15.5, 15.5, 8.5, 6), iris, grad(8, 10, 23, 21, 0.2, 0.95), ink=True)
    a.put(ell(15.5, 15.5, 1.8, 5.2), INK)
    a.put(pts(14, 13), (220, 255, 220))


def slime_ball():
    a = Art()
    shape = ell(15.5, 17, 12.5, 11) | ell(9, 10, 5, 4.5) | ell(21, 9, 4, 3.5)
    a.fill(shape, SLIME, ball(15.5, 16, 14, 13))
    a.tone(ell(17, 19, 5, 4), -1)
    a.set_tone(ell(9, 11, 2.6, 1.8) | pts(20, 8, 13, 15), 4)
    a.set_tone(pts(22, 22, 23, 21, 20, 24), 3)
    return a


def blaze_rod():
    a = Art()
    rodm = tones("8a3a0c", "d0701a", "f8ac2a", "ffe06a", "fffad0")
    for points in (((20, 6), (22, 2), (25, 5)), ((25, 9), (29, 7), (27, 12)), ((23, 4), (28, 2), (27, 7))):
        a.fill(poly(*points), EMBER, grad(20, 12, 29, 2, 0.3, 1.0), ink=False)
    a.fill(line(6, 25, 23, 8, 6.4), rodm, grad(9, 9, 21, 21, 1.0, 0.15))
    for t in (0.18, 0.5, 0.82):
        cx, cy = 6 + 17 * t, 25 - 17 * t
        a.tone(line(cx - 3, cy - 3, cx + 3, cy + 3, 1.2), -1)
    a.set_tone(line(6, 22, 20, 8), 4)
    a.put(pts(11, 27, 14, 28, 27, 16), EMBER[2])
    return a


OAK, SPRUCE, BIRCH = ramp("7a5632"), ramp("4e3626"), tones("6a6478", "a8a4a8", "d8d4c8", "f0ece0", "ffffff")
JUNGLE, ACACIA, DARK_OAK = ramp("6e5a30"), ramp("7a7068"), ramp("3e2a1e")

PAINT = {
    "enchanted_cobblestone": block(COBBLE, d_cobble),
    "enchanted_coal": rock(COAL),
    "enchanted_iron": ingot(IRON),
    "enchanted_gold": ingot(GOLD),
    "enchanted_diamond": diamond,
    "enchanted_emerald": emerald,
    "enchanted_lapis": lapis,
    "enchanted_redstone": pile(REDSTONE, 0, (255, 220, 190)),
    "enchanted_quartz": quartz,
    "enchanted_obsidian": block(OBSIDIAN, d_obsidian),
    "enchanted_glowstone": pile(GLOW, 1, (255, 255, 255)),
    "enchanted_coal_block": block(COAL, d_coal),
    "enchanted_iron_block": block(IRON, d_plate),
    "enchanted_gold_block": block(GOLD, d_bevel),
    "enchanted_diamond_block": block(DIAMOND[:5], d_facets),
    "enchanted_emerald_block": block(EMERALD[:5], d_bevel),
    "enchanted_lapis_block": block(LAPIS, d_specks((255, 214, 90))),
    "enchanted_redstone_block": block(REDSTONE, d_redstone),
    "enchanted_quartz_block": block(QUARTZ, d_quartz),
    "enchanted_bread": bread,
    "enchanted_carrot": carrot(ramp("f08a24")),
    "enchanted_potato": potato(ramp("d0a45e")),
    "enchanted_pumpkin": pumpkin,
    "enchanted_melon": melon,
    "enchanted_sugar": sugar,
    "enchanted_cactus": cactus,
    "enchanted_cocoa": cocoa,
    "enchanted_nether_wart": nether_wart,
    "enchanted_golden_carrot": carrot(GOLD),
    "enchanted_baked_potato": potato(ramp("b07a34"), baked=True),
    "enchanted_oak_log": log(OAK, ramp("d0a868")),
    "enchanted_spruce_log": log(SPRUCE, ramp("a8784a"), "tight"),
    "enchanted_birch_log": log(BIRCH, ramp("e8d09a"), "birch"),
    "enchanted_jungle_log": log(JUNGLE, ramp("c09a5e"), "grooves", vine),
    "enchanted_acacia_log": log(ACACIA, ramp("d8683a"), "plates"),
    "enchanted_dark_oak_log": log(DARK_OAK, ramp("7a5234"), "plates"),
    "enchanted_rotten_flesh": rotten_flesh,
    "enchanted_bone": bone,
    "enchanted_string": string,
    "enchanted_gunpowder": pile(tones("22202e", "45434f", "6c6a74", "92919a", "bcbcc2"), 2),
    "enchanted_ender_pearl": orb(tones("062a34", "0e5a5a", "1f9a84", "5cd8b0", "c0ffe4"), pearl_swirl),
    "enchanted_blaze_rod": blaze_rod,
    "enchanted_slime_ball": slime_ball,
    "enchanted_slime_block": block(SLIME, d_slime),
    "enchanted_eye_of_ender": orb(tones("0a3a2c", "1a7a44", "3cb85a", "8ae880", "e0ffd0"), eye_iris),
    "enchanted_raw_fish": fish(ramp("b08a5e"), ramp("e8d4b0"), cod_spots),
    "enchanted_raw_salmon": fish(ramp("d05a50"), ramp("f4b0a0"), salmon_spots),
    "enchanted_pufferfish": pufferfish,
    "enchanted_clownfish": fish(ramp("f07a1e"), ramp("ffb060"), clown_bands),
    "enchanted_lily_pad": lily_pad,
    "enchanted_ink_sac": ink_sac,
    "enchanted_cooked_fish": fish(ramp("b8823e"), ramp("e8c078"), cooked=True),
    "enchanted_cooked_salmon": fish(ramp("d8743a"), ramp("f4a868"), cooked=True),
}


def paint(item_id, tier):
    """The painted object, inside the outline, inside the enchant rim."""
    art = PAINT[item_id]()
    art.erase({p for p in art.mask() if not (2 <= p[0] <= 29 and 2 <= p[1] <= 29)})
    return art.outline().glint(tier)


def outputs():
    files = {}
    for item_id, _name, _material, _shape, _colours, tier in ITEMS:
        files[PACK / "textures/item/skills" / f"{item_id}.png"] = paint(item_id, tier).png()
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/skills/{item_id}"}}
        files[PACK / "models/item/skills" / f"{item_id}.json"] = (json.dumps(model, indent=2) + "\n").encode()
        definition = {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/skills/{item_id}"}}
        files[PACK / "items/skills" / f"{item_id}.json"] = (json.dumps(definition, indent=2) + "\n").encode()
    return files


def main(argv):
    if "--sheet" in argv:
        out = pathlib.Path(argv[argv.index("--sheet") + 1])
        out.write_bytes(sheet([paint(i[0], i[5]).grid() for i in ITEMS], cols=11, scale=6))
        return 0
    files = outputs()
    if "--check" in argv:
        bad = [str(p) for p, data in files.items() if not p.exists() or p.read_bytes() != data]
        if bad:
            print("out of date:", *bad, sep="\n  ")
            return 1
        print(f"{len(files)} files up to date")
        return 0
    for path_, data in files.items():
        path_.parent.mkdir(parents=True, exist_ok=True)
        path_.write_bytes(data)
    print(f"wrote {len(files)} files for {len(ITEMS)} items")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
