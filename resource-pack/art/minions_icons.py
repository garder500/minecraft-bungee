#!/usr/bin/env python3
"""Paints the 32x32 item icons of the Skyblock minions feature (stdlib only, deterministic).

    python3 resource-pack/art/minions_icons.py [--sheet out.png]

Writes, for every name below, three files that all contain "minions" in their path:
  assets/vampyuyu/textures/item/minions/<name>.png
  assets/vampyuyu/models/item/minions/<name>.json
  assets/vampyuyu/items/minions/<name>.json       (item model "vampyuyu:minions/<name>")
Names: one per minion type (the tier is shown by the item name and rarity colour), fuel_<id> for the custom fuels and
upgrade_<id> for the upgrade items. Keep the lists in step with the minion_* tables of the survival plugin seed.

A minion is the network's little golem (the one of item/menu/minions.png), made of or dressed for its resource: every
type has its own body material, headgear, tool and the thing it gathers in its left hand. Fuels and upgrades are real
objects (a lava bucket, a furnace, a press...). Painted with sb_paint: material ramps, light from the top left.
"""
import json, math, pathlib, sys

from sb_paint import (Art, INK, GOLD, WOOD, DARKWOOD, STONE, IRON, STEEL, LEAF, RED, WHITE, LEATHER, GLASS,
                      ramp, tones, rect, rrect, ell, ring, poly, line, path, pts, shift, edge, grow,
                      ball, grad, vcyl, hcyl, sheet)

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SIZE = 32

# ------------------------------------------------------------------ the golem
HEAD = rrect(4, 5, 23, 18, 3)
TORSO = rrect(7, 18, 20, 26, 2)
ARM_L = rrect(2, 18, 6, 25, 2)
ARM_R = rrect(21, 18, 25, 24, 2)
LEG_L = rect(8, 27, 12, 30)
LEG_R = rect(15, 27, 19, 30)
FIST = rrect(22, 20, 26, 24, 2)

MOSS = tones("1f4a34", "3a7a3a", "6aa848", "a4d468")
EMBER = tones("7a1c14", "d8481c", "ff9a2a", "ffe27a")


def eyes(a, colour=INK, shine=(255, 255, 255), y=11):
    a.put(rect(8, y, 9, y + 2) | rect(17, y, 18, y + 2), colour)
    if shine:
        a.put(pts(8, y, 17, y), shine)


def smile(a, y=15):
    a.tone(pts(11, y, 12, y + 1, 13, y + 1, 14, y + 1, 15, y), -2)


def face(a):
    eyes(a)
    smile(a)


def body(a, mat, head=None, limbs=None, tool=None):
    """Legs, arms, torso, the tool behind the head, then the head."""
    limbs = limbs or mat
    head = head or mat
    a.fill(LEG_L, limbs, 0.36, rim=0.16)
    a.fill(LEG_R, limbs, 0.30, rim=0.16)
    a.tone(rect(8, 30, 12, 30) | rect(15, 30, 19, 30), -1)
    a.fill(ARM_L, limbs, None)
    a.fill(ARM_R, limbs, grad(21, 18, 25, 24, 0.6, 0.24), rim=0.16)
    a.fill(TORSO, mat, grad(7, 18, 20, 27, 0.66, 0.22), rim=0.18)
    a.tone(rect(8, 18, 19, 18), -1)                       # the head's shadow on the chest
    if tool:
        tool(a)
    a.fill(HEAD, head, None)
    return a


def fist(a, mat):
    a.fill(FIST, mat, None)


# ------------------------------------------------------------------ tools (right hand)
def shaft(a, x0=23, y0=30, x1=27, y1=6, mat=WOOD):
    a.fill(line(x0, y0, x1, y1, 2), mat, grad(x0 - 1, 0, x0 + 3, 0, 0.8, 0.3), ink=True)


def pick(metal=IRON):
    def f(a):
        shaft(a)
        head = path([(20, 7), (23, 4), (27, 3), (30, 5), (30, 10)], 2.4)
        a.fill(head, metal, grad(22, 2, 30, 10, 0.98, 0.3))
    return f


def axe(metal=IRON):
    def f(a):
        shaft(a, 23, 30, 26, 4)
        blade = poly((25, 2), (29, 2), (30, 5), (30, 10), (28, 12), (26, 9))
        a.fill(blade, metal, grad(25, 2, 30, 11, 0.98, 0.3))
        a.tone(edge(blade, "r") | pts(29, 11, 28, 12), +1)
    return f


def shovel(metal=IRON):
    def f(a):
        shaft(a, 24, 30, 27, 9)
        blade = rrect(24, 1, 30, 10, 3) | rect(25, 8, 29, 11)
        a.fill(blade, metal, grad(24, 1, 30, 11, 0.98, 0.3))
        a.tone(rect(27, 3, 27, 8), -1)
    return f


def hoe(metal=IRON):
    def f(a):
        shaft(a, 24, 30, 26, 4)
        blade = rect(25, 2, 30, 4) | rect(29, 4, 30, 10)
        a.fill(blade, metal, grad(25, 2, 30, 10, 0.98, 0.3))
    return f


def sword(metal=IRON, guard=GOLD):
    def f(a):
        blade = poly((27, 1), (29, 1), (30, 3), (26, 19), (23, 19), (25, 5))
        a.fill(blade, metal, grad(24, 2, 30, 10, 0.98, 0.3))
        a.tone(line(27, 3, 24, 18), +1)
        a.fill(line(21, 19, 29, 20, 2.2), guard, None)
        a.fill(line(24, 22, 23, 28, 2), LEATHER, None)
    return f


def bow(a):
    arc = (ring(18, 17, 12, 13, 2.2) & rect(25, 4, 31, 30))
    a.fill(line(25, 5, 25, 29, 1), WHITE, 0.7, ink=False)
    a.fill(arc, WOOD, grad(24, 4, 30, 30, 0.9, 0.3))
    a.fill(rect(28, 15, 30, 19), LEATHER, None)


def rod(a):
    a.fill(line(23, 26, 30, 2, 1.6), WOOD, None)
    a.fill(line(30, 3, 30, 11, 1), WHITE, 0.7, ink=False)
    fish = ell(28.5, 15, 2.2, 3.6) | pts(28, 19, 29, 19, 27, 20, 30, 20)
    a.fill(fish, ramp("58a8e0"), ball(28.5, 15, 2.5, 4))
    a.put(pts(28, 13), INK)


def bucket(fill_=WHITE):
    def f(a):
        a.fill(ring(26, 22, 4, 4, 1.2) & rect(21, 17, 31, 22), STEEL, 0.6, ink=False)
        pail = poly((21, 22), (30, 22), (29, 30), (22, 30))
        a.fill(pail, IRON, vcyl(21, 30))
        a.fill(rect(22, 22, 29, 23), fill_, 0.8, ink=False)
        a.tone(rect(22, 26, 29, 26), -1)
    return f


def shears(a):
    a.fill(line(24, 16, 30, 3, 2), IRON, grad(24, 3, 30, 16, 0.95, 0.4))
    a.fill(line(28, 16, 24, 3, 2), IRON, grad(24, 3, 30, 16, 0.95, 0.4))
    a.fill(ring(24, 19, 2.6, 2.6, 1.3) | ring(29, 19, 2.6, 2.6, 1.3), RED, None)


def carrot_rod(a):
    a.fill(line(23, 26, 29, 2, 1.6), WOOD, None)
    a.fill(line(29, 2, 29, 6, 1), WHITE, 0.7, ink=False)
    car = poly((27, 7), (30, 7), (30, 10), (28, 15))
    a.fill(car, ramp("f08a24"), None)
    a.fill(pts(28, 6, 29, 5, 30, 6), LEAF, 0.7, ink=False)


def basket(a):
    a.fill(ring(26, 23, 4.5, 5, 1.2) & rect(20, 17, 31, 23), WOOD, 0.5, ink=False)
    for cx in (23.5, 26.5, 28.5):
        a.fill(ell(cx, 23, 1.8, 2.2), WHITE, ball(cx, 23, 2, 2.4))
    pan = poly((21, 24), (30, 24), (29, 30), (22, 30))
    a.fill(pan, ramp("c89a4a"), vcyl(21, 30))
    a.tone(rect(22, 26, 29, 26) | rect(22, 28, 29, 28), -1)


# ------------------------------------------------------------------ things held in the left hand
def lump(mat, spark=None):
    def f(a):
        a.fill(ell(4.5, 23.5, 3.6, 3.2), mat, ball(4.5, 23.5, 3.8, 3.4))
        if spark:
            a.put(pts(3, 22), spark)
    return f


def gem(mat):
    def f(a):
        g = poly((3, 20), (6, 20), (8, 23), (4.5, 27), (1, 23))
        a.fill(g, mat, grad(1, 20, 8, 27, 1.0, 0.2))
        a.set_tone(pts(3, 21, 4, 21, 2, 22), len(mat) - 1)
    return f


def ingot(mat):
    def f(a):
        a.fill(poly((1, 24), (3, 21), (8, 21), (8, 24), (6, 27), (1, 27)), mat, grad(1, 21, 8, 27, 0.95, 0.25))
        a.tone(line(1, 24, 6, 24), +1)
    return f


def sprig(stalk, top):
    """A bundle of wheat ears."""
    def f(a):
        a.fill(line(4, 27, 2, 21) | line(4, 27, 4, 20) | line(5, 27, 7, 21), stalk, 0.6)
        for cx, cy in ((2, 19.5), (4.5, 18), (7, 19.5)):
            a.fill(ell(cx, cy, 1.5, 2.7), top, ball(cx, cy, 1.8, 3))
        a.fill(rect(3, 25, 6, 25), RED, 0.5, ink=False)
    return f


def veg(mat, leaf=LEAF, long=False):
    def f(a):
        if long:
            a.fill(poly((2, 20), (7, 20), (7, 23), (4, 28), (2, 24)), mat, None)
            a.tone(pts(3, 22, 4, 24), -1)
        else:
            a.fill(ell(4.5, 24, 3.6, 3.3), mat, ball(4.5, 24, 3.8, 3.5))
        a.fill(pts(3, 19, 4, 18, 5, 19, 6, 18, 4, 19), leaf, 0.7, ink=False)
    return f


def log_piece(bark, wood):
    def f(a):
        a.fill(rrect(1, 20, 8, 27, 1), bark, vcyl(1, 8))
        a.fill(ell(4.5, 21, 3.5, 1.6), wood, 0.8, ink=False)
        a.tone(pts(4, 21, 5, 21), -1)
    return f


def slice_(rind, flesh):
    def f(a):
        a.fill(poly((1, 21), (8, 21), (7, 25), (4.5, 27), (2, 25)), flesh, None)
        a.fill(rect(1, 20, 8, 21), rind, 0.6, ink=False)
        a.put(pts(3, 23, 5, 24, 6, 22), INK)
    return f


def egg(a):
    a.fill(ell(4.5, 23.5, 3, 3.8), tones("8a7a8a", "c8b8a8", "f0e4d0", "fffaf0"), ball(4.5, 23.5, 3.2, 4))


def bone_(a):
    a.fill(line(2, 27, 7, 20, 2), WHITE, 0.75)
    a.fill(pts(1, 26, 2, 28, 3, 28, 6, 19, 8, 20, 8, 21, 7, 19), WHITE, 0.9, ink=False)


# ------------------------------------------------------------------ headgear
def hardhat(colour, lamp=True):
    mat = ramp(colour)

    def f(a):
        dome = (ell(13.5, 8, 10.5, 7) & rect(0, 1, 31, 7))
        a.fill(dome, mat, ball(13.5, 9, 11, 8))
        a.fill(rect(2, 8, 25, 9), mat, 0.45, rim=0.2)
        a.tone(rect(5, 10, 22, 10), -1)
        if lamp:
            a.fill(rrect(11, 3, 16, 8, 1), STEEL, None)
            a.fill(rect(12, 4, 15, 7), tones("e89a20", "ffe27a", "ffffe0"), ball(13.5, 5.5, 2.6))
    return f


def leather_cap(a):
    cap = (ell(13.5, 8, 10.5, 6.5) & rect(0, 2, 31, 8))
    a.fill(cap, LEATHER, ball(13.5, 9, 11, 8))
    a.fill(rect(4, 8, 23, 9), DARKWOOD, 0.6)
    a.fill(ell(13.5, 6, 2.6, 2.6), tones("b84a14", "ff9a2a", "ffe27a", "ffffe0"), ball(13.5, 6, 2.8))
    a.tone(rect(5, 10, 22, 10), -1)


def steel_helm(a):
    helm = (ell(13.5, 8, 10.5, 7) & rect(0, 1, 31, 8)) | rect(3, 8, 24, 10)
    a.fill(helm, IRON, ball(13.5, 9, 12, 9))
    a.fill(rect(12, 1, 15, 12), IRON, grad(12, 0, 16, 0, 0.9, 0.5))            # the nose guard
    a.put(pts(5, 9, 9, 9, 18, 9, 22, 9), STEEL[0])
    a.tone(rect(5, 11, 11, 11) | rect(16, 11, 22, 11), -1)


def crown(a):
    c = rect(4, 6, 23, 9) | poly((4, 6), (4, 2), (7, 5)) | poly((9, 6), (13.5, 1), (18, 6)) | poly((23, 6), (23, 2), (20, 5)) \
        | poly((7, 6), (9, 3), (11, 6)) | poly((16, 6), (18, 3), (20, 6))
    a.fill(c, GOLD, grad(4, 2, 23, 10, 1.0, 0.35))
    a.put(pts(13, 7, 14, 7), (224, 56, 72))
    a.put(pts(7, 8, 20, 8), (70, 200, 230))
    a.tone(rect(5, 10, 22, 10), -1)


def crystals(mat):
    def f(a):
        for p in (poly((6, 7), (8, 1), (11, 7)), poly((11, 7), (15, 0), (18, 7)), poly((17, 7), (20, 3), (22, 7)),
                  poly((2, 19), (4, 14), (6, 19))):
            a.fill({q for q in p if q[1] >= 1}, mat, grad(6, 0, 22, 8, 1.0, 0.25))
    return f


def goggles(a):
    a.fill(rect(4, 10, 23, 11), LEATHER, 0.35, ink=False)
    for cx in (8.5, 17.5):
        a.fill(ell(cx, 11.5, 3.2, 3.2), GOLD, None)
        a.fill(ell(cx, 11.5, 2, 2), GLASS, ball(cx, 11.5, 2.2), ink=False)


def fedora(a):
    a.fill(ell(13.5, 8.5, 13, 2.6), LEATHER, grad(0, 6, 0, 11, 0.7, 0.25))
    top = rrect(6, 1, 21, 7, 3)
    a.fill(top, LEATHER, None)
    a.fill(rect(6, 6, 21, 7), ramp("2f8a56"), 0.5, ink=False)
    a.tone(pts(12, 2, 13, 3, 14, 3, 15, 2), -1)
    a.tone(rect(5, 11, 22, 11), -1)


def hood(mat, trim=None):
    def f(a):
        h = (ell(13.5, 9, 11.5, 8.5) & rect(0, 1, 31, 9)) | rect(2, 9, 6, 16) | rect(21, 9, 25, 16)
        a.fill(h, mat, ball(13.5, 10, 13, 11))
        if trim:
            a.put(pts(6, 4, 10, 2, 17, 3, 21, 6, 3, 12, 24, 11), trim)
        a.tone(rect(7, 10, 20, 10), -1)
    return f


def straw_hat(band=RED):
    straw = tones("7a4a1c", "b8802c", "e2b44a", "f8dc7a", "fff4b4")

    def f(a):
        a.fill(ell(13.5, 8.5, 13, 2.8), straw, grad(0, 6, 0, 11, 0.85, 0.3))
        a.fill(rrect(6, 1, 21, 7, 4), straw, None)
        a.fill(rect(6, 6, 21, 7), band, 0.5, ink=False)
        a.tone(pts(9, 3, 12, 2, 16, 4, 3, 9, 8, 10, 19, 10, 24, 9), -1)
        a.tone(rect(5, 11, 22, 11), -1)
    return f


def leaves(mat=LEAF, fruit=None):
    def f(a):
        for cx, cy, r in ((6, 6, 4), (21, 6, 4), (13.5, 4.5, 5.2), (10, 7, 3.5), (17, 7, 3.5)):
            a.fill(ell(cx, cy, r, r * 0.8) & rect(0, 1, 31, 31), mat, ball(cx, cy, r + 0.5, r), ink=False)
        a.tone(pts(8, 8, 12, 8, 15, 8, 19, 8, 13, 9), -1)
        if fruit:
            a.put(pts(8, 4, 18, 3, 13, 6), fruit)
        a.tone(rect(5, 10, 22, 10), -1)
    return f


def pine_hat(a):
    pine = tones("0e2a2e", "17493c", "2a6e48", "4a9658", "86c47a")
    for y0, half in ((6, 11), (3, 8), (1, 5)):
        a.fill(poly((13.5 - half, y0 + 4), (13.5, y0 - 2), (13.5 + half, y0 + 4)) & rect(0, 1, 31, 10), pine,
               grad(4, 0, 24, 10, 0.95, 0.25))
    a.tone(rect(5, 11, 22, 11), -1)


def turban(a):
    cloth = ramp("e8e0d0")
    a.fill((ell(13.5, 7, 11.5, 6.5) & rect(0, 1, 31, 9)), cloth, ball(13.5, 8, 12, 8))
    a.tone(line(3, 7, 14, 3) | line(8, 9, 22, 4) | line(15, 9, 24, 7), -1)
    a.fill(ell(13.5, 6, 1.8, 1.8), ramp("28b8b0"), ball(13.5, 6, 2))
    a.tone(rect(5, 10, 22, 10), -1)


def bucket_hat(mat):
    def f(a):
        a.fill(rrect(5, 2, 22, 8, 3), mat, None)
        a.fill(poly((2, 10), (5, 7), (22, 7), (25, 10)), mat, grad(0, 7, 0, 10, 0.6, 0.25))
        a.tone(rect(5, 11, 22, 11), -1)
    return f


def souwester(a):
    oil = tones("8a5a10", "c8901a", "f2c62a", "ffe66a", "fff6b8")
    a.fill((ell(13.5, 7, 10.5, 6.5) & rect(0, 1, 31, 8)), oil, ball(13.5, 8, 11, 8))
    a.fill(poly((1, 11), (4, 8), (23, 8), (27, 11), (26, 12), (2, 12)), oil, grad(0, 8, 0, 12, 0.8, 0.3))
    a.tone(rect(13, 2, 13, 7), -1)
    a.tone(rect(5, 13, 22, 13), -1)


# ------------------------------------------------------------------ the minions
def stone(a, mat, cracks=True):
    if cracks:
        a.tone(path([(20, 12), (21, 14), (20, 16)]) | path([(5, 14), (6, 16)]) | pts(17, 21, 18, 22, 17, 23, 9, 24), -1)


def moss(a, where=((5, 16, 2.2), (22, 9, 2), (18, 25, 2), (3, 19, 1.8))):
    for cx, cy, r in where:
        m = ell(cx, cy, r, r * 0.8) & a.mask()
        a.fill(m, MOSS, None, ink=False)


def studs(a, mat, where):
    for x, y in where:
        a.fill(rect(x, y, x + 1, y + 1), mat, grad(x, y, x + 1, y + 1, 1.0, 0.4), ink=True)


def miner(base, ore, hat, held, extra=None, eye=None, tool_metal=IRON, moss_=False):
    def f():
        a = Art()
        mat = ramp(base)
        body(a, mat, tool=pick(tool_metal))
        stone(a, mat)
        if moss_:
            moss(a)
        if ore:
            studs(a, ore, ((10, 20), (15, 23), (4, 21), (20, 15), (6, 14)))
        if eye:
            eyes(a, eye[0], eye[1])
            smile(a)
        else:
            face(a)
        if extra:
            extra(a)
        hat(a)
        fist(a, mat)
        held(a)
        return a.outline()
    return f


COAL = tones("0e0c16", "1c1a26", "302e3c", "4c4a5a", "787688")
RAWIRON = tones("5a3830", "96684e", "c89a78", "e8c8a4", "fff0d8")
DIAMOND = tones("14406e", "1f7cb0", "3cc4dc", "8cf0ee", "e2fffa")
REDSTONE = tones("4a0818", "8a0e1c", "d01c1c", "ff5a3a", "ffb08a")
LAPIS = tones("101850", "1c3496", "2c5cd8", "5a94f8", "a8d0ff")
EMERALD = tones("0a3a34", "108a48", "2cc85a", "74f08a", "d0ffc8")
CLAY = tones("3a3e5c", "66708e", "949eb6", "bcc6d6", "e4eaf2")
SAND = tones("7a5a3a", "b8945a", "e0c684", "f6e4ac", "fffad8")
WHEAT = tones("7a4a14", "b8801e", "e8b83a", "ffe070", "fff6b0")
CARROT = ramp("f08a24")
POTATO = ramp("c89a5a")
PUMPKIN = tones("6a2414", "a84a14", "e07a1a", "f8a83a", "ffd47a")
MELON = tones("123a22", "1f6a2c", "3f9a38", "7cc850", "c0ec84")
MELON_FLESH = tones("8a1430", "d02c3c", "f8584c", "ff9a84")
PINK = tones("7a2c50", "c05a7a", "f08ca0", "ffb8c0", "ffe0e0")
WOOL = tones("6a6a8e", "a4a8c4", "d4d8e6", "f2f4fa", "ffffff")
SLIME = tones("145a34", "2c9a3a", "5cd04a", "9cf07a", "dcffb8")
ZOMBIE = tones("1c3a34", "2f6a40", "4f9a50", "80c468", "b8e890")
BONE = tones("5a5470", "9c98a8", "d0ccc4", "f0ece0", "ffffff")


def farmer(base, hat, held, extra=None, overalls="3a62b8", face_=face, tool=None):
    def f():
        a = Art()
        mat = base if isinstance(base, list) else ramp(base)
        body(a, mat, tool=tool or hoe())
        if overalls:
            cloth = ramp(overalls)
            a.fill(rect(8, 21, 19, 26) | rect(9, 18, 10, 20) | rect(17, 18, 18, 20), cloth,
                   grad(7, 18, 20, 27, 0.7, 0.25), ink=False)
            a.fill(LEG_L, cloth, 0.36, rim=0.16, ink=False)
            a.fill(LEG_R, cloth, 0.30, rim=0.16, ink=False)
            a.put(pts(10, 21, 17, 21), GOLD[3])
            a.fill(rect(8, 30, 12, 30) | rect(15, 30, 19, 30), DARKWOOD, 0.4, ink=False)
        face_(a)
        if extra:
            extra(a)
        hat(a)
        fist(a, mat)
        held(a)
        return a.outline()
    return f


def pumpkin_face(a):
    glow = tones("ffb020", "fff08a")
    for m in (poly((7, 13), (9, 10), (11, 13)), poly((16, 13), (18, 10), (20, 13)),
              rect(9, 15, 18, 16) - pts(11, 15, 14, 16, 16, 15)):
        a.put(m, glow[0])
    a.put(pts(9, 12, 18, 12, 10, 16, 13, 16), glow[1])
    for x in (8, 13, 19):
        a.tone(rect(x, 6, x, 17) & HEAD, -1)


def pumpkin_stem(a):
    a.fill(rect(12, 2, 15, 5) | pts(16, 2, 17, 2), LEAF, None)
    a.fill(pts(9, 5, 10, 4, 11, 5, 8, 6), LEAF, 0.7, ink=False)


def melon_stripes(a):
    for x in (6, 10, 14, 18, 21):
        a.tone((rect(x, 5, x + 1, 18)) & HEAD, -1)


def tuft(a):
    for p in (poly((12, 5), (9, 1), (13, 3)), poly((13, 5), (13.5, 0), (15, 5)), poly((15, 5), (18, 1), (14, 3))):
        a.fill({q for q in p if q[1] >= 1}, LEAF, grad(9, 0, 18, 6, 0.95, 0.4))


def potato_eyes(a):
    a.tone(pts(6, 8, 20, 7, 21, 15, 5, 15, 12, 7, 9, 22, 16, 24), -1)


def sprouts(a):
    """Two shoots growing out of a potato head."""
    for points in (((8, 5), (6, 1), (10, 3)), ((17, 5), (20, 1), (16, 2))):
        a.fill(poly(*points) | line(points[0][0], 5, points[1][0], 2, 1.6), LEAF, grad(6, 1, 20, 6, 0.95, 0.4))


def sack(a):
    burlap = ramp("b89a6a")
    a.fill(rrect(1, 21, 8, 28, 2), burlap, None)
    a.fill(ell(3, 20, 2, 1.8) | ell(6, 20, 2, 1.8), POTATO, 0.7)
    a.tone(pts(3, 24, 5, 25, 6, 23), -1)


def woodcutter(bark, wood, hat, extra=None):
    def f():
        a = Art()
        body(a, bark, tool=axe())
        for x, y0, y1 in ((6, 9, 15), (21, 10, 16), (11, 20, 24), (16, 21, 25), (4, 20, 23), (23, 20, 22)):
            a.tone(rect(x, y0, x, y1), -1)
        a.fill(rect(7, 9, 20, 17), wood, grad(7, 9, 20, 17, 0.85, 0.5), ink=True)     # the face is cut wood
        face(a)
        if extra:
            extra(a)
        hat(a)
        fist(a, bark)
        log_piece(bark, wood)(a)
        return a.outline()
    return f


def birch_marks(a):
    a.put(pts(5, 12, 6, 12, 22, 14, 21, 14, 9, 22, 10, 22, 17, 24, 18, 24, 3, 21, 24, 21, 9, 28, 17, 28), (52, 46, 60))


def simple(base, tool, hat, held, face_=face, extra=None, limbs=None, head=None):
    def f():
        a = Art()
        mat = base if isinstance(base, list) else ramp(base)
        body(a, mat, head=head, limbs=limbs, tool=tool)
        if extra:
            extra(a)
        face_(a)
        if hat:
            hat(a)
        fist(a, limbs or mat)
        if held:
            held(a)
        return a.outline()
    return f


def cow_bits(a):
    dark = tones("1e1a26", "3a3444", "565064")
    for m in (ell(6, 8, 3, 2.6), ell(21, 15, 2.6, 3), ell(16, 22, 3, 2.4), ell(8, 25, 2, 1.6)):
        a.fill(m & a.mask(), dark, None, ink=False)


def cow_face(a):
    eyes(a, y=10)
    a.fill(rrect(8, 13, 19, 18, 2), PINK, None)
    a.put(pts(11, 15, 11, 16, 16, 15, 16, 16), PINK[0])


def cow_horns(a):
    for m in (poly((4, 6), (2, 1), (7, 4)), poly((23, 6), (25, 1), (20, 4))):
        a.fill(m, BONE, grad(2, 1, 25, 6, 0.95, 0.5))
    a.fill(ell(2.5, 8, 2, 1.6) | ell(24.5, 8, 2, 1.6), PINK, 0.5)


def chicken_face(a):
    eyes(a, y=10)
    a.fill(poly((11, 13), (16, 13), (13.5, 17)), GOLD, None)
    a.fill(rect(12, 17, 14, 19), RED, 0.6)


def comb(a):
    a.fill(ell(10, 3.5, 2.4, 2.6) | ell(13.5, 2.8, 2.6, 2.8) | ell(17, 3.5, 2.4, 2.6) | rect(9, 4, 18, 5), RED,
           grad(8, 1, 19, 6, 0.95, 0.4))


def pig_face(a):
    eyes(a, y=10)
    a.fill(rrect(9, 13, 18, 17, 2), PINK, 0.82, rim=0.15)
    a.put(pts(11, 15, 12, 15, 15, 15, 16, 15), PINK[0])


def pig_ears(a):
    for m in (poly((4, 7), (4, 2), (9, 5)), poly((23, 7), (23, 2), (18, 5))):
        a.fill(m, PINK, grad(4, 2, 23, 7, 0.9, 0.4))


def wool_puff(a):
    for cx, cy, r in ((5, 6, 3.6), (10, 4, 3.8), (16, 4, 3.8), (22, 6, 3.6), (3, 11, 2.6), (24, 11, 2.6), (13.5, 6, 4)):
        a.fill(ell(cx, cy, r, r * 0.85) & rect(1, 1, 30, 31), WOOL, ball(cx, cy, r + 0.5, r), ink=False)
    for cx, cy, r in ((8, 21, 3), (13.5, 23, 3.4), (19, 21, 3), (13.5, 19, 3)):
        a.fill(ell(cx, cy, r, r * 0.8) & TORSO, WOOL, ball(cx, cy, r + 0.5, r), ink=False)


def sheep_face(a):
    a.put(rect(8, 12, 9, 14) | rect(17, 12, 18, 14), (250, 240, 200))
    a.put(pts(9, 13, 9, 14, 18, 13, 18, 14), INK)
    a.tone(pts(12, 16, 13, 17, 14, 17, 15, 16), +2)


def zombie_bits(a):
    a.put(rect(8, 11, 10, 13), (240, 226, 120))
    a.put(pts(9, 12), INK)
    a.put(rect(17, 11, 18, 12), INK)
    a.tone(rect(11, 16, 16, 16), -2)
    a.put(pts(12, 16, 15, 16), BONE[3])
    rag = ramp("3a66a8")
    a.fill(rect(8, 20, 19, 26) - pts(8, 26, 12, 26, 13, 26, 19, 25, 19, 26, 8, 20, 19, 20), rag,
           grad(7, 18, 20, 27, 0.7, 0.25), ink=False)
    a.tone(pts(6, 8, 7, 9, 20, 7, 21, 16), -1)


def skull_face(a):
    a.put(rrect(7, 10, 10, 14, 1) | rrect(16, 10, 19, 14, 1), INK)
    a.put(pts(8, 12, 17, 12), (120, 240, 220))
    a.put(pts(13, 14, 14, 14, 13, 15), INK)
    a.tone(rect(8, 17, 19, 17), -2)
    for x in (10, 13, 16):
        a.tone(rect(x, 16, x, 18), -2)


def ribs(a):
    for y in (20, 22, 24):
        a.tone(rect(9, y, 18, y), -2)
    a.tone(rect(13, 19, 14, 25), +1)


def slime_bits(a):
    a.fill(rrect(9, 9, 18, 16, 2), tones("1f7a34", "3cae40", "74dc5a"), 0.45, ink=False)
    a.set_tone(rect(6, 7, 9, 7) | rect(6, 8, 6, 10) | pts(9, 20, 10, 20, 9, 21), 4)


TYPES = {
    "cobblestone": miner("8c8c98", None, hardhat("f0a81e"), lump(STONE), moss_=True),
    "coal": miner("4a4856", COAL, leather_cap, lump(COAL), eye=((255, 236, 170), None)),
    "iron": miner("a09088", RAWIRON, steel_helm, ingot(IRON)),
    "gold": miner("b09a78", GOLD, crown, ingot(GOLD), tool_metal=GOLD),
    "diamond": miner("7486a8", DIAMOND, crystals(DIAMOND), gem(DIAMOND), extra=goggles, eye=(INK, None), tool_metal=DIAMOND),
    "redstone": miner("6a4a54", REDSTONE, hardhat("c8281e", lamp=False), lump(REDSTONE, (255, 200, 170)),
                      eye=((255, 96, 60), (255, 220, 190))),
    "lapis": miner("5a6aa0", LAPIS, hood(LAPIS, GOLD[3]), lump(LAPIS, GOLD[3])),
    "emerald": miner("6a8a78", EMERALD, fedora, gem(EMERALD)),
    "clay": simple(CLAY, shovel(), bucket_hat(ramp("b8623a")), lump(CLAY)),
    "sand": simple(SAND, shovel(GOLD), turban, lump(SAND),
                   extra=lambda a: a.tone(rect(7, 21, 20, 21) | rect(7, 24, 20, 24) | rect(5, 15, 22, 15), -1)),
    "wheat": farmer("c08a5e", straw_hat(RED), sprig(LEAF, WHEAT)),
    "carrot": farmer("e09048", tuft, veg(CARROT, long=True), overalls="5a8a3a"),
    "potato": farmer("c8a05e", sprouts, sack, extra=potato_eyes, overalls=None, tool=shovel()),
    "pumpkin": farmer(PUMPKIN, pumpkin_stem, veg(PUMPKIN), face_=pumpkin_face, overalls="5a3a7a"),
    "melon": farmer(MELON, lambda a: None, slice_(MELON, MELON_FLESH), extra=melon_stripes, overalls="c84a4a"),
    "oak": woodcutter(ramp("7a5632"), ramp("d0a868"), leaves()),
    "spruce": woodcutter(ramp("4e3626"), ramp("a8784a"), pine_hat),
    "birch": woodcutter(ramp("dcd8cc"), ramp("e8d09a"), leaves(tones("3a5a1c", "6a9a2a", "a4cc3a", "dcec7a")), extra=birch_marks),
    "cow": simple(WOOL, bucket(), cow_horns, None, face_=cow_face, extra=cow_bits),
    "chicken": simple(WOOL, basket, comb, egg, face_=chicken_face),
    "pig": simple(PINK, carrot_rod, pig_ears, veg(CARROT, long=True), face_=pig_face),
    "sheep": simple(ramp("4a4252"), shears, None, lump(WOOL), face_=sheep_face, extra=wool_puff),
    "fishing": simple(ramp("3a78c8"), rod, souwester, None, head=ramp("9aa4b8")),
    "zombie": simple(ZOMBIE, sword(STEEL, DARKWOOD), None, lump(ramp("a84a3a")), face_=zombie_bits),
    "skeleton": simple(BONE, bow, None, bone_, face_=skull_face, extra=ribs),
    "slime": simple(SLIME, sword(), None, lump(SLIME), extra=slime_bits),
}
FUELS = ["enchanted_coal", "enchanted_charcoal", "enchanted_lava_bucket", "hamster_wheel", "foul_flesh", "catalyst",
         "hyper_catalyst"]
UPGRADES = ["auto_smelter", "compactor", "super_compactor", "diamond_spreading", "minion_expander", "flycatcher",
            "mithril_infusion"]


def minion(type_id):
    return TYPES[type_id]()


# ------------------------------------------------------------------ fuels
def coal_lump(mat, glow=None):
    a = Art()
    shape = poly((6, 12), (12, 5), (21, 4), (27, 10), (28, 20), (22, 27), (11, 28), (4, 21))
    a.fill(shape, mat, 0.45, ink=False)
    for p, v in ((poly((6, 12), (12, 5), (15, 12), (9, 16)), 0.92), (poly((12, 5), (21, 4), (20, 11), (15, 12)), 0.7),
                 (poly((21, 4), (27, 10), (22, 15), (20, 11)), 0.5), (poly((9, 16), (15, 12), (20, 11), (22, 15), (16, 21)), 0.58),
                 (poly((4, 21), (6, 12), (9, 16), (11, 23)), 0.66), (poly((22, 15), (27, 10), (28, 20), (23, 22)), 0.24),
                 (poly((11, 23), (16, 21), (23, 22), (22, 27), (11, 28)), 0.1), (poly((9, 16), (16, 21), (11, 23)), 0.36)):
        a.fill(p & shape, mat, v, ink=False)
    a.set_tone(line(7, 12, 12, 6) | pts(13, 7, 14, 8, 10, 14), len(mat) - 1)
    if glow:
        a.put(path([(9, 16), (15, 12), (20, 11)]) | path([(16, 21), (22, 15)]) | pts(11, 23, 12, 22), glow[1])
        a.put(pts(15, 12, 13, 13, 20, 12, 19, 18), glow[2])
    return a


def fuel(name):
    if name == "enchanted_coal":
        return coal_lump(COAL).outline().glint(1)
    if name == "enchanted_charcoal":
        a = Art()
        char = tones("140e14", "2a1c1e", "46302a", "684a3a", "8e6c54")
        a.fill(poly((3, 20), (20, 5), (27, 9), (28, 14), (11, 28), (5, 27)), char, grad(8, 8, 22, 26, 0.9, 0.15), ink=False)
        end = ell(23.5, 10.5, 4.2, 5)
        a.fill(end, char, 0.75)
        a.fill(ell(23.5, 10.5, 2.6, 3.2), EMBER, ball(23.5, 10.5, 3, 3.6), ink=False)
        for x0, y0, x1, y1 in ((7, 20, 12, 15), (12, 22, 18, 16), (9, 25, 12, 22), (15, 12, 18, 9)):
            a.put(line(x0, y0, x1, y1), EMBER[1])
            a.put(pts(x0, y0), EMBER[3])
        a.tone(line(5, 20, 19, 7), +1, only=char)
        return a.outline().glint(1)
    if name == "enchanted_lava_bucket":
        a = Art()
        a.fill(ring(15.5, 10, 12, 8.5, 1.6) & rect(0, 2, 31, 10), STEEL, grad(3, 2, 28, 2, 0.9, 0.4), ink=False)
        pail = poly((4, 10), (27, 10), (24, 28), (7, 28))
        a.fill(pail, IRON, vcyl(4, 27))
        a.fill(ell(15.5, 10.5, 11.5, 3.6), IRON, 0.3)
        lava = tones("b0280c", "f0601a", "ffa82a", "ffe86a", "fffad0")
        a.fill(ell(15.5, 10.5, 10, 2.6), lava, grad(6, 8, 25, 13, 0.95, 0.35), ink=False)
        a.set_tone(pts(10, 10, 11, 10, 17, 11, 18, 11, 22, 10), 4)
        a.fill(rect(6, 17, 25, 18) & pail, STEEL, 0.4, ink=False)
        a.fill(poly((19, 12), (22, 12), (22, 19), (20.5, 22), (19, 19)), lava, grad(19, 12, 22, 22, 0.9, 0.3))   # a drip
        a.tone(rect(8, 13, 9, 26) & pail, +1)
        return a.outline().glint(1)
    if name == "hamster_wheel":
        a = Art()
        a.fill(poly((3, 29), (13, 14), (18, 14), (28, 29), (24, 29), (15.5, 18), (7, 29)), DARKWOOD, None)
        wheel = ring(15.5, 14, 12.5, 12.5, 3)
        a.fill(wheel, WOOD, ball(15.5, 14, 13))
        for i in range(8):
            ang = i * math.pi / 4 + 0.2
            a.fill(line(15.5 + 9.5 * math.cos(ang), 14 + 9.5 * math.sin(ang), 15.5 + 12 * math.cos(ang), 14 + 12 * math.sin(ang), 1.2),
                   DARKWOOD, 0.3, ink=False)
        fur = tones("7a3c1c", "b8702c", "e8a850", "ffd890", "fff4d8")
        a.fill(ell(15.5, 18, 6.5, 5), fur, ball(15.5, 18, 7, 5.5))
        a.fill(ell(11, 14.5, 3.6, 3.4), fur, ball(11, 14.5, 4))
        a.fill(ell(17, 20, 3.4, 2.4), WHITE, 0.85, ink=False)
        a.fill(pts(9, 11, 10, 11, 13, 11), PINK, 0.6, ink=False)
        a.put(pts(10, 14), INK)
        a.put(pts(8, 16), PINK[2])
        a.fill(ell(15.5, 14, 1.6), GOLD, None)
        return a.outline()
    if name == "foul_flesh":
        a = Art()
        a.fill(line(20, 12, 27, 5, 3), BONE, 0.8)
        a.fill(ell(27, 3.5, 2.4) | ell(28.5, 6.5, 2.4), BONE, 0.85, ink=False)
        meat = tones("2a2a30", "4a5a2c", "7a8a34", "aab44c", "d8dc7c")
        a.fill(ell(13, 18, 11, 9.5), meat, ball(13, 18, 11.5, 10))
        rot = tones("5a1438", "9a2c5a", "cc5a84")
        for cx, cy, r in ((9, 15, 2.6), (16, 21, 3), (7, 22, 1.8), (17, 13, 1.6)):
            a.fill(ell(cx, cy, r, r * 0.8), rot, ball(cx, cy, r + 0.6), ink=False)
        a.tone(path([(4, 19), (8, 19), (11, 18)]) | path([(13, 25), (18, 25), (21, 23)]), -1, only=meat)
        a.put(pts(25, 20, 27, 17, 2, 8, 5, 6), (150, 220, 90))
        a.put(pts(26, 21, 28, 18, 3, 9, 6, 7), (70, 130, 50))
        return a.outline()
    hyper = name == "hyper_catalyst"
    a = Art()
    cryst = (tones("3a0a5a", "8a1ca8", "e040d0", "ff8ae8", "ffe0fa") if hyper
             else tones("0a3a5a", "0e7a94", "2cc8c0", "8af4dc", "e8fff4"))
    if hyper:
        for x0, y0, x1, y1 in ((15.5, 1, 15.5, 30), (1, 15.5, 30, 15.5), (5, 5, 26, 26), (26, 5, 5, 26)):
            a.fill(line(x0, y0, x1, y1, 1.6), GOLD, grad(2, 2, 29, 29, 1.0, 0.45), ink=False)
    shard = poly((15.5, 3), (22, 10), (22, 21), (15.5, 28), (9, 21), (9, 10))
    a.fill(shard, cryst, 0.5)
    for p, v in ((poly((15.5, 3), (15.5, 12), (9, 10)), 0.95), (poly((15.5, 3), (22, 10), (15.5, 12)), 0.62),
                 (poly((9, 10), (15.5, 12), (15.5, 20), (9, 21)), 0.72), (poly((15.5, 12), (22, 10), (22, 21), (15.5, 20)), 0.36),
                 (poly((9, 21), (15.5, 20), (15.5, 28)), 0.45), (poly((15.5, 20), (22, 21), (15.5, 28)), 0.12)):
        a.fill(p, cryst, v, ink=False)
    a.set_tone(line(15, 4, 15, 27), 4)
    a.set_tone(pts(11, 11, 12, 12, 11, 13, 12, 9), 4)
    if not hyper:
        a.fill(rect(7, 14, 24, 17), GOLD, grad(7, 14, 24, 17, 0.95, 0.35))
        a.fill(poly((3, 15.5), (7, 12), (7, 19)) | poly((28, 15.5), (24, 12), (24, 19)), GOLD, None)
    else:
        a.fill(ell(15.5, 15.5, 3, 3), tones("ff8ae8", "ffe0fa", "ffffff"), ball(15.5, 15.5, 3.2))
    return a.outline()


# ------------------------------------------------------------------ upgrades
def press(gold):
    a = Art()
    frame = tones("2a1c48", "4a3a8a", "7a66c8", "a89af0", "dcd4ff") if gold else STEEL
    trim = GOLD if gold else IRON
    a.fill(rrect(3, 18, 28, 29, 1), frame, None)                                   # the bed
    a.fill(rect(3, 18, 28, 19), trim, 0.6)
    a.fill(rect(4, 2, 7, 18) | rect(24, 2, 27, 18), frame, vcyl(4, 7), ink=True)   # the pillars
    a.fill(rect(24, 2, 27, 18), frame, vcyl(24, 27), ink=False)
    a.fill(rrect(3, 1, 28, 5, 1), frame, grad(0, 1, 0, 5, 0.9, 0.4))               # the head
    a.fill(rect(13, 5, 18, 10), IRON, vcyl(13, 18))                                # the piston
    a.fill(rect(9, 10, 22, 12), trim, grad(9, 10, 22, 12, 0.95, 0.4))              # the plate
    cube = rrect(11, 13, 20, 17, 1)
    a.fill(cube, ramp("3cc4dc") if gold else ramp("9a9aa6"), None)
    for x, y in ((5, 22), (25, 22), (5, 27), (25, 27)):
        a.put(rect(x, y, x + 1, y + 1), trim[4])
    if gold:
        star = poly((15.5, 20), (17, 23), (20, 24), (17, 25), (15.5, 28), (14, 25), (11, 24), (14, 23))
        a.fill(star, GOLD, grad(11, 20, 20, 28, 1.0, 0.5))
    else:
        a.tone(rect(10, 22, 21, 22) | rect(10, 25, 21, 25), -1)
    return a.outline()


def upgrade(name):
    if name == "auto_smelter":
        a = Art()
        brick = ramp("8a8a96")
        a.fill(rect(19, 1, 24, 8), brick, vcyl(19, 24))
        a.fill(rect(18, 1, 25, 2), brick, 0.75)
        a.fill(rrect(3, 7, 28, 29, 2), brick, grad(3, 7, 28, 29, 0.9, 0.2), rim=0.18)
        for y in (12, 17, 24):
            a.tone(rect(4, y, 27, y), -1)
        for x, y0, y1 in ((9, 8, 11), (20, 8, 11), (5, 13, 16), (26, 13, 16), (6, 18, 23), (25, 18, 23), (10, 25, 28), (21, 25, 28)):
            a.tone(rect(x, y0, x, y1), -1)
        mouth = rrect(8, 14, 23, 25, 3)
        a.fill(mouth, tones("1a0e14", "3a1410"), 0.3)
        a.fill(poly((10, 25), (11, 19), (13, 22), (15.5, 15), (18, 22), (20, 18), (21, 25)), EMBER, grad(10, 25, 21, 15, 0.35, 1.0), ink=False)
        a.put(pts(15, 23, 16, 23, 15, 22, 16, 24, 12, 24, 19, 24), EMBER[3])
        a.fill(rect(7, 26, 24, 27), IRON, 0.6)
        return a.outline()
    if name in ("compactor", "super_compactor"):
        return press(name == "super_compactor")
    if name == "diamond_spreading":
        a = Art()

        def gemstone(cx, cy, s):
            g = poly((cx - s * 0.55, cy - s), (cx + s * 0.55, cy - s), (cx + s, cy - s * 0.35), (cx, cy + s), (cx - s, cy - s * 0.35))
            a.fill(g, DIAMOND, 0.5)
            a.fill(poly((cx - s * 0.55, cy - s), (cx - s * 0.3, cy - s * 0.35), (cx - s, cy - s * 0.35)), DIAMOND, 0.95, ink=False)
            a.fill(poly((cx - s * 0.55, cy - s), (cx + s * 0.55, cy - s), (cx + s * 0.3, cy - s * 0.35), (cx - s * 0.3, cy - s * 0.35)), DIAMOND, 0.75, ink=False)
            a.fill(poly((cx - s, cy - s * 0.35), (cx - s * 0.3, cy - s * 0.35), (cx, cy + s)), DIAMOND, 0.62, ink=False)
            a.fill(poly((cx + s * 0.3, cy - s * 0.35), (cx + s, cy - s * 0.35), (cx, cy + s)), DIAMOND, 0.15, ink=False)
        gemstone(6, 7, 4.5)
        gemstone(25, 6, 4)
        gemstone(26, 23, 4.5)
        gemstone(5, 25, 4)
        for x0, y0, x1, y1 in ((11, 11, 9, 9), (20, 10, 22, 8), (21, 20, 23, 21), (10, 21, 8, 23)):
            a.put(line(x0, y0, x1, y1), (255, 255, 255))
        gemstone(15.5, 15.5, 8.5)
        a.set_tone(pts(11, 9, 12, 9, 10, 11), 4)
        return a.outline()
    if name == "minion_expander":
        a = Art()
        plate = rrect(8, 8, 23, 23, 2)
        a.fill(plate, STONE, None)
        a.fill(rrect(11, 11, 20, 20, 1), LEAF, None)
        a.set_tone(pts(13, 13, 14, 13, 13, 14), 4)
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            cx, cy = 15.5 + sx * 10, 15.5 + sy * 10
            tip = poly((cx + sx * 4.5, cy + sy * 4.5), (cx + sx * 4.5, cy - sy * 2.5), (cx - sx * 2.5, cy + sy * 4.5))
            a.fill(tip, GOLD, grad(1, 1, 30, 30, 1.0, 0.4))
            a.fill(line(cx - sx * 3, cy - sy * 3, cx + sx * 2, cy + sy * 2, 2.4), GOLD, grad(1, 1, 30, 30, 1.0, 0.4), ink=False)
        return a.outline()
    if name == "flycatcher":
        a = Art()
        pot = poly((8, 22), (23, 22), (21, 30), (10, 30))
        a.fill(pot, ramp("c8683a"), vcyl(8, 23))
        a.fill(rect(7, 20, 24, 22), ramp("c8683a"), grad(7, 0, 24, 0, 0.9, 0.4))
        a.fill(path([(15.5, 20), (15, 15), (16, 11)], 2.2), LEAF, None)
        a.fill(poly((14, 18), (7, 15), (5, 18), (10, 19)) | poly((17, 17), (24, 16), (26, 19), (20, 19)), LEAF, None)
        jaw = tones("14402c", "1f7a34", "4cb040", "90dc5a", "d4f890")
        top = ell(13, 6.5, 9, 4.5) & poly((2, 12), (2, 0), (24, 0), (24, 9))
        a.fill(ell(15, 9, 10, 5.5), tones("5a0a2c", "a81c44", "e04a5c"), grad(6, 4, 24, 14, 0.9, 0.3))    # the mouth
        a.fill(ell(15.5, 12, 10, 4) & rect(0, 10, 31, 16), jaw, grad(5, 10, 26, 16, 0.75, 0.2))
        a.fill(ell(15.5, 5, 10.5, 4.5) & rect(0, 1, 31, 7), jaw, ball(15.5, 6, 11, 5.5))
        a.put(pts(6, 8, 9, 9, 12, 8, 15, 9, 18, 8, 21, 9, 24, 8, 7, 10, 11, 10, 15, 10, 19, 10, 23, 10), (255, 250, 230))
        a.put(pts(28, 3, 29, 2, 30, 4), (70, 60, 90))
        a.put(pts(29, 3), (200, 220, 255))
        return a.outline()
    a = Art()   # mithril_infusion: a flask of living silver
    a.fill(rect(13, 2, 18, 5), WOOD, None)
    a.fill(rect(12, 5, 19, 12), GLASS, vcyl(12, 19))
    a.fill(rect(11, 5, 20, 6), GLASS, 0.9)
    bulb = ell(15.5, 20, 11, 9.5)
    a.fill(bulb, GLASS, ball(15.5, 20, 11.5, 10))
    mith = tones("14505a", "1f8a8e", "4cc8c0", "a0f0e0", "ecfffa")
    a.fill(ell(15.5, 21, 9.4, 7.8) & rect(0, 17, 31, 31), mith, ball(15.5, 19, 10, 9.5), ink=False)
    a.fill(ell(15.5, 17.5, 8.6, 1.8), mith, 0.9, ink=False)
    a.put(pts(12, 22, 18, 24, 15, 20, 20, 21), (255, 255, 255))
    a.set_tone(path([(7, 15), (6, 18), (7, 21)]) | pts(13, 7, 13, 8, 13, 9), 4)
    a.put(pts(15, 13, 17, 10, 14, 9), mith[3])
    return a.outline()


def entries():
    for type_id in TYPES:
        yield type_id, minion(type_id)
    for name in FUELS:
        yield "fuel_" + name, fuel(name)
    for name in UPGRADES:
        yield "upgrade_" + name, upgrade(name)


def write(path_, data, mode="wb"):
    path_.parent.mkdir(parents=True, exist_ok=True)
    if mode == "wb":
        path_.write_bytes(data)
    else:
        path_.write_text(data, encoding="utf-8")


def main():
    if "--sheet" in sys.argv:
        out = pathlib.Path(sys.argv[sys.argv.index("--sheet") + 1])
        out.write_bytes(sheet([cv.grid() for _, cv in entries()], cols=10, scale=6))
        return 0
    count = 0
    for name, cv in entries():
        write(PACK / "textures" / "item" / "minions" / f"{name}.png", cv.png())
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/minions/{name}"}}
        write(PACK / "models" / "item" / "minions" / f"{name}.json", json.dumps(model, indent=2) + "\n", "w")
        item = {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/minions/{name}"}}
        write(PACK / "items" / "minions" / f"{name}.json", json.dumps(item, indent=2) + "\n", "w")
        count += 1
    print(f"wrote {count} minion icons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
