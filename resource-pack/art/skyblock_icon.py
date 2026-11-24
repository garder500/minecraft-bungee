#!/usr/bin/env python3
"""Paints the menu and werewolf icons (item models vampyuyu:icon/*), among them the Skyblock icon: a floating island.

    python3 resource-pack/art/skyblock_icon.py [--sheet out.png] [--preview]

Standard library only, deterministic output, committed under pack/. 32x32, painted with sb_paint in the style of the
imported item/menu icons: every object in its own material (wood, iron, gold, parchment, glass), light from the top
left, a near-black outline, no anti-aliasing. The four greyed icons (potion-*-off, mark-last, mark-wolf) are derived
from their coloured ones. render.py calls write_icons().
"""
import json, math, pathlib

from sb_paint import (Art, INK, GOLD, WOOD, DARKWOOD, STONE, IRON, STEEL, LEAF, RED, BLUE, WHITE, PAPER, GLASS, LEATHER,
                      CLOTH, SKIN, BLACK, ramp, tones, rect, rrect, ell, ring, poly, line, path, pts, shift, edge,
                      ball, grad, vcyl, hcyl, png_bytes, sheet)
from sb_things import GREEN, ORANGE, TEAL, coin, arrow, harrow, scroll, chest, check, plus, bolt, magnifier, potion

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"

PINK = tones("7a1c50", "c83c78", "f070a0", "ffa8c4", "ffe0ea")
PURPLE = tones("240e4a", "44208a", "6e3cc8", "a070f0", "d8b8ff")
BONE = tones("5a5470", "9c98a8", "d0ccc4", "f0ece0", "ffffff")
NIGHT = tones("0e0e2c", "1c1c4c", "2e3074", "4a50a0", "7a84d0")
DIRT = tones("2e1a22", "523026", "7a4a2e", "a06a3c", "c8905a")


def star_points(cx, cy, r, inner=0.45, n=5, turn=-math.pi / 2):
    out = []
    for i in range(n * 2):
        k = r if i % 2 == 0 else r * inner
        ang = turn + i * math.pi / n
        out.append((cx + k * math.cos(ang), cy + k * math.sin(ang)))
    return out


def heart_mask(cx, cy, s):
    return (ell(cx - s * 0.5, cy - s * 0.3, s * 0.56, s * 0.56) | ell(cx + s * 0.5, cy - s * 0.3, s * 0.56, s * 0.56)
            | poly((cx - s * 1.02, cy - s * 0.1), (cx + s * 1.02, cy - s * 0.1), (cx, cy + s)))


def shield_mask(x0, y0, x1, y1):
    cx = (x0 + x1) / 2
    return poly((x0, y0 + 2), (cx, y0), (x1, y0 + 2), (x1, y0 + (y1 - y0) * 0.55), (cx, y1), (x0, y0 + (y1 - y0) * 0.55))


def sword_(a, metal=IRON, k=1.0):
    blade = poly((27, 2), (29, 2), (29, 5), (13, 21), (10, 18))
    a.fill(blade, metal, grad(12, 6, 24, 18, 1.0, 0.3))
    a.set_tone(line(26, 4, 12, 18), len(metal) - 1)
    a.fill(line(7, 14, 17, 24, 3), GOLD, grad(7, 14, 17, 24, 1.0, 0.4))
    a.fill(line(10, 21, 5, 26, 2.6), LEATHER, None)
    a.fill(ell(4, 27, 2.4, 2.4), GOLD, ball(4, 27, 2.6))


# ------------------------------------------------------------------ the icons
def i_axe(a):
    a.fill(line(5, 28, 22, 7, 3), WOOD, grad(8, 10, 18, 22, 0.95, 0.3))
    head = poly((15, 4), (21, 2), (28, 5), (30, 12), (28, 18), (24, 12), (19, 11))
    a.fill(head, IRON, grad(15, 2, 30, 16, 1.0, 0.3))
    a.set_tone(path([(29, 8), (30, 12), (28, 17)]), 4)
    a.fill(poly((17, 6), (21, 4), (24, 9), (20, 11)), STEEL, 0.45, ink=False)


def i_back(a):
    harrow(a, 15, 29, 2, IRON, half=4, head=11)


def i_next(a):
    harrow(a, 15, 2, 29, IRON, half=4, head=11)


def i_play(a):
    tri = poly((6, 3), (28, 15.5), (6, 28))
    a.fill(tri, GREEN, grad(6, 3, 20, 26, 0.98, 0.3), rim=0.12)


def i_stop(a):
    a.fill(rrect(4, 4, 27, 27, 3), RED, None)


def i_skip(a):
    for x in (2, 13):
        a.fill(poly((x, 5), (x + 11, 15.5), (x, 26)), ORANGE, grad(x, 5, x + 8, 26, 0.98, 0.3))
    a.fill(rect(25, 5, 28, 26), ORANGE, grad(25, 5, 28, 26, 0.9, 0.35))


def i_plus(a):
    plus(a, 15, 15, 12, GREEN, half=3)


def i_bed(a):
    a.fill(rect(2, 20, 4, 29) | rect(27, 22, 29, 29), DARKWOOD, None)
    a.fill(rrect(1, 8, 5, 22, 1), WOOD, None)
    a.fill(rect(4, 20, 29, 24), WOOD, grad(0, 20, 0, 24, 0.7, 0.3), rim=0.15)
    a.fill(rrect(5, 14, 29, 20, 2), RED, grad(5, 13, 29, 21, 0.95, 0.3), rim=0.14)
    a.fill(rrect(5, 12, 13, 17, 2), WHITE, None)
    a.fill(rect(14, 14, 16, 20), WHITE, 0.7, ink=False)


def i_bone(a):
    a.fill(line(8, 23, 23, 8, 4.4), BONE, grad(10, 10, 20, 20, 1.0, 0.25))
    for cx, cy in ((5, 22), (9, 26), (22, 5), (26, 9)):
        a.fill(ell(cx, cy, 3.3, 3.3), BONE, ball(cx, cy, 3.5), ink=False)
    a.set_tone(line(9, 20, 20, 9), 4)


def i_book(a):
    a.fill(rrect(5, 3, 27, 29, 1), PAPER, grad(0, 3, 0, 29, 0.8, 0.4))
    a.fill(rrect(3, 2, 25, 27, 1), PURPLE, grad(3, 2, 25, 27, 0.9, 0.3), rim=0.14)
    a.fill(rect(3, 2, 6, 27), PURPLE, 0.25, ink=False)
    for x, y in ((8, 3), (22, 3), (8, 24), (22, 24)):
        a.fill(rect(x, y, x + 2, y + 2), GOLD, None, ink=False)
    a.fill(poly(star_points(15.5, 14, 5.5, 0.4, 4)), GOLD, grad(10, 9, 21, 19, 1.0, 0.4))
    a.tone(rect(7, 28, 26, 28), -1)


def i_chest(a):
    chest(a)


def i_clock(a):
    a.fill(ell(15.5, 15.5, 14, 14), GOLD, ball(15.5, 15.5, 14.5))
    a.fill(ell(15.5, 15.5, 11, 11), WHITE, grad(5, 5, 26, 26, 0.98, 0.6))
    for i in range(12):
        ang = i * math.pi / 6
        a.put(pts(round(15.5 + 9 * math.cos(ang)), round(15.5 + 9 * math.sin(ang))), STEEL[1])
    a.put(line(15, 15, 15, 8, 1.6) | line(15, 15, 21, 18, 1.6), INK)
    a.put(pts(15, 15, 16, 15, 15, 16, 16, 16), RED[2])


def i_coin(a):
    coin(a, 15, 15, 13)


def i_compass(a):
    brass = tones("5a3414", "96641e", "cc9a34", "f0cc64", "fff0b0")
    a.fill(ell(15.5, 15.5, 14, 14), brass, ball(15.5, 15.5, 14.5))
    a.fill(ell(15.5, 15.5, 11, 11), tones("9aa8c0", "d0dcea", "f4f8fc"), grad(5, 5, 26, 26, 0.95, 0.4))
    a.fill(poly((15.5, 5), (19, 15.5), (12, 15.5)), RED, grad(12, 5, 19, 15, 0.9, 0.4), ink=False)
    a.fill(poly((15.5, 26), (19, 15.5), (12, 15.5)), STEEL, grad(12, 15, 19, 26, 0.9, 0.3), ink=False)
    a.fill(ell(15.5, 15.5, 1.6, 1.6), brass, 0.9)
    a.put(pts(6, 15, 25, 15, 6, 16, 25, 16), STEEL[1])


def i_door(a, opened=False):
    a.fill(rrect(5, 1, 26, 30, 3), STONE, None)
    if opened:
        a.fill(rrect(8, 4, 23, 30, 2), NIGHT, grad(8, 4, 23, 30, 0.2, 0.5))
        a.fill(poly((8, 4), (15, 7), (15, 30), (8, 30)), WOOD, grad(8, 4, 15, 30, 0.95, 0.4))
    else:
        a.fill(rrect(8, 4, 23, 30, 2), WOOD, grad(8, 4, 23, 30, 0.95, 0.3), rim=0.12)
        for x in (13, 18):
            a.tone(rect(x, 5, x, 30), -1)
        a.fill(rect(8, 11, 23, 12) | rect(8, 22, 23, 23), IRON, 0.6, ink=False)
        a.fill(ell(20.5, 17, 1.8, 1.8), GOLD, ball(20.5, 17, 2))


def i_exit(a):
    i_door(a, opened=True)
    harrow(a, 17, 14, 30, ORANGE, half=2, head=6)


def i_heart(a):
    m = heart_mask(15.5, 13, 13.5)
    a.fill(m, RED, ball(15.5, 14, 15, 15))
    a.set_tone(ell(9, 9, 3, 2.2) | pts(6, 12), 4)


def i_lever(a):
    a.fill(rrect(3, 22, 28, 29, 2), STONE, None)
    a.fill(rect(11, 20, 20, 23), STEEL, None)
    a.fill(line(15, 21, 23, 7, 3), WOOD, grad(14, 8, 22, 18, 0.95, 0.35))
    a.fill(ell(24, 5.5, 4, 4), RED, ball(24, 5.5, 4.3))


def i_lock(a):
    a.fill(ring(15.5, 11, 8.5, 9.5, 3.2) & rect(0, 0, 31, 14), IRON, grad(6, 1, 25, 12, 0.98, 0.35))
    a.fill(rrect(4, 13, 27, 29, 3), GOLD, grad(4, 13, 27, 29, 0.98, 0.3), rim=0.14)
    a.put(ell(15.5, 19.5, 2.6, 2.6) | rect(14, 20, 17, 25), INK)


def i_map(a):
    sheet_ = poly((2, 6), (10, 3), (20, 6), (29, 3), (29, 26), (20, 29), (10, 26), (2, 29))
    a.fill(sheet_, PAPER, grad(2, 3, 29, 29, 0.95, 0.5))
    a.tone(rect(10, 4, 10, 26) | rect(20, 6, 20, 28), -1)
    a.tone(rect(11, 4, 19, 28) & sheet_, -1)
    a.fill(ell(7, 12, 3, 2.2) | ell(24, 20, 3.4, 3), tones("3a9a58", "74c868"), 0.6, ink=False)
    a.put(pts(7, 22, 9, 20, 12, 19, 14, 17, 16, 14, 18, 12, 21, 11), (106, 70, 60))
    a.put(line(22, 7, 26, 11) | line(26, 7, 22, 11), RED[2])


def i_moon(a):
    moon = ell(14, 15.5, 12.5, 13) - ell(20, 12, 10.5, 11)
    a.fill(moon, tones("5a5a9a", "9aa0d0", "d4daf0", "f6f4dc", "ffffff"), ball(12, 15.5, 14, 14))
    a.tone(pts(6, 18, 7, 22, 9, 25), -1)
    for x, y in ((23, 7), (27, 16), (21, 21)):
        a.put(pts(x, y, x - 1, y, x + 1, y, x, y - 1, x, y + 1), (255, 240, 170))
        a.put(pts(x, y), (255, 255, 255))


def i_sun(a):
    for i in range(8):
        ang = i * math.pi / 4
        tip = (15.5 + 14.5 * math.cos(ang), 15.5 + 14.5 * math.sin(ang))
        l, r = ang - 0.26, ang + 0.26
        a.fill(poly(tip, (15.5 + 9 * math.cos(l), 15.5 + 9 * math.sin(l)), (15.5 + 9 * math.cos(r), 15.5 + 9 * math.sin(r))),
               ORANGE, grad(2, 2, 29, 29, 1.0, 0.45), ink=False)
    a.fill(ell(15.5, 15.5, 9, 9), GOLD, ball(15.5, 15.5, 9.5, ambient=0.4))


def i_note(a):
    ink = tones("101a4a", "1c3c8e", "3470c8", "6aa8f0", "b8e0ff")
    a.fill(rect(11, 4, 13, 23) | rect(25, 2, 27, 20), ink, vcyl(11, 13))
    a.fill(poly((11, 4), (27, 1), (27, 7), (11, 10)), ink, grad(11, 1, 27, 10, 0.95, 0.4))
    a.fill(ell(8, 24, 5.5, 4.5), ink, ball(8, 24, 6, 5))
    a.fill(ell(22, 21.5, 5.5, 4.5), ink, ball(22, 21.5, 6, 5))


def i_pearl(a):
    mat = tones("062a34", "0e5a5a", "1f9a84", "5cd8b0", "c0ffe4")
    a.fill(ell(15.5, 15.5, 13.5, 13.5), mat, ball(15.5, 15.5, 14))
    a.tone((ring(15.5, 15.5, 9, 9, 1.4) & rect(14, 5, 28, 26)) | (ring(15.5, 15.5, 5, 5, 1.4) & rect(4, 9, 17, 22)), -1)
    a.set_tone(pts(9, 7, 10, 6, 11, 6, 8, 8, 8, 9), 4)


def i_pencil(a):
    a.fill(line(8, 23, 23, 8, 5.4), GOLD, grad(10, 10, 21, 21, 1.0, 0.3))
    a.fill(poly((2, 29), (4, 21), (10, 27)), tones("b8905a", "e8c890", "fff0c8"), grad(2, 21, 10, 29, 0.95, 0.4))
    a.put(pts(2, 29, 3, 28, 3, 29, 2, 28), INK)
    a.fill(line(24, 7, 26, 5, 5.4), PINK, grad(22, 3, 28, 9, 0.95, 0.4))
    a.fill(line(21, 7, 24, 10, 1.6), IRON, 0.7, ink=False)
    a.set_tone(line(8, 21, 20, 9), 4)


def i_ruler(a):
    body = poly((2, 22), (22, 2), (29, 9), (9, 29))
    a.fill(body, ramp("d8a84a"), grad(8, 8, 22, 24, 0.98, 0.35), rim=0.12)
    for i in range(7):
        x, y = 5 + i * 3, 21 - i * 3
        n = 3 if i % 2 == 0 else 2
        a.put(line(x, y, x + n, y + n), (90, 56, 40))


def i_shield(a):
    m = shield_mask(3, 2, 28, 30)
    a.fill(m, GOLD, grad(3, 2, 28, 30, 1.0, 0.35))
    inner = shield_mask(6, 5, 25, 26)
    a.fill(inner, BLUE, grad(6, 5, 25, 26, 0.95, 0.25))
    a.fill((rect(14, 6, 17, 24) | rect(8, 11, 23, 14)) & inner, WHITE, grad(8, 6, 23, 24, 0.98, 0.55), ink=False)


def i_skyblock(a):
    rock = poly((3, 16), (28, 16), (25, 21), (20, 24), (17, 30), (14, 30), (11, 25), (6, 21))
    a.fill(rock, STONE, grad(4, 16, 24, 30, 0.85, 0.15))
    a.fill(poly((3, 16), (28, 16), (26, 19), (21, 20), (16, 22), (10, 20), (5, 19)), DIRT, grad(3, 16, 28, 22, 0.9, 0.35), ink=False)
    a.tone(pts(9, 22, 10, 23, 19, 23, 16, 26, 15, 27, 22, 21), -1)
    a.fill(ell(15.5, 15, 13.5, 3.2), LEAF, grad(3, 12, 28, 18, 0.98, 0.45))
    a.put(pts(6, 15, 24, 14), (255, 226, 110))
    a.put(pts(9, 16, 21, 16), (240, 110, 150))
    a.fill(rect(15, 8, 17, 15), WOOD, vcyl(15, 17))
    for cx, cy, r in ((11, 7, 4.5), (20, 7, 4.5), (15.5, 4.5, 5.5), (15.5, 8.5, 4)):
        a.fill(ell(cx, cy, r, r * 0.8) & rect(0, 1, 31, 31), LEAF, ball(cx, cy, r + 0.5, r), ink=False)
    a.tone(pts(10, 10, 13, 11, 18, 11, 21, 10), -1)


def i_star(a):
    a.fill(poly(star_points(15.5, 16.5, 15, 0.48)), GOLD, grad(3, 2, 26, 28, 1.0, 0.3), rim=0.12)
    a.set_tone(line(15, 6, 12, 12) | pts(13, 9), 4)


def i_sword(a):
    sword_(a)


def i_tag(a):
    a.fill(path([(6, 9), (3, 5), (5, 2), (9, 3), (9, 8)], 1.4), tones("8a6a4a", "c8a070"), 0.6, ink=False)
    tag = poly((3, 12), (9, 6), (29, 6), (29, 24), (9, 24), (3, 18))
    a.fill(shift(tag, 0, 2), PAPER, grad(3, 6, 29, 26, 0.95, 0.5), rim=0.14)
    a.put(ell(8.5, 17, 1.6, 1.6), INK)
    for y, x1 in ((13, 25), (17, 22), (21, 24)):
        a.put(rect(13, y, x1, y), (106, 86, 80))


def i_target(a):
    for r, mat, v in ((14, RED, None), (10.5, WHITE, None), (7, RED, None), (3.5, GOLD, None)):
        a.fill(ell(15.5, 15.5, r, r), mat, ball(15.5, 15.5, 15, ambient=0.45), ink=False)
    a.fill(line(16, 15, 27, 4, 1.8), WOOD, 0.6)
    a.fill(poly((24, 2), (29, 2), (29, 7), (27, 5)), GREEN, None)


def i_timer(a):
    a.fill(rrect(5, 1, 26, 4, 1) | rrect(5, 27, 26, 30, 1), WOOD, hcyl(1, 4))
    a.fill(rrect(5, 27, 26, 30, 1), WOOD, hcyl(27, 30), ink=False)
    glass = poly((7, 5), (24, 5), (24, 9), (17, 15.5), (24, 22), (24, 26), (7, 26), (7, 22), (14, 15.5), (7, 9))
    a.fill(glass, GLASS, grad(7, 5, 24, 26, 0.98, 0.35))
    sand = tones("a8742a", "e0b04a", "ffe08a")
    a.fill(poly((9, 8), (22, 8), (16, 14), (15, 14)), sand, grad(9, 8, 22, 14, 0.95, 0.4), ink=False)
    a.fill(poly((9, 25), (22, 25), (16, 19), (15, 19)), sand, grad(9, 19, 22, 25, 0.95, 0.4), ink=False)
    a.put(rect(15, 15, 16, 18), sand[1])
    a.set_tone(pts(9, 6, 9, 7, 9, 22, 9, 23), 4)


def i_trash(a):
    a.fill(rect(12, 1, 19, 3), STEEL, None)
    a.fill(rrect(3, 4, 28, 8, 1), IRON, hcyl(4, 8))
    can = poly((5, 9), (26, 9), (24, 30), (7, 30))
    a.fill(can, STEEL, vcyl(5, 26))
    for x in (10, 15, 20):
        a.tone(rect(x, 12, x + 1, 27), -1)


def i_vial(a):
    a.fill(rect(12, 1, 19, 3), WOOD, None)
    tube = rrect(11, 4, 20, 30, 4) | rect(11, 4, 20, 8)
    a.fill(tube, GLASS, vcyl(11, 20))
    a.fill(rect(10, 4, 21, 5), GLASS, 0.9)
    liq = rrect(12, 14, 19, 29, 3) | rect(12, 14, 19, 18)
    a.fill(liq, GREEN, vcyl(12, 19), ink=False)
    a.set_tone(rect(12, 14, 19, 14), 4)
    a.put(pts(15, 19, 17, 23, 14, 25), (230, 255, 220))
    a.set_tone(rect(13, 7, 13, 12), 4)


def i_warn(a):
    tri = poly((15.5, 1), (30, 28), (1, 28))
    a.fill(tri, GOLD, grad(6, 4, 26, 30, 1.0, 0.35), rim=0.12)
    a.put(rect(14, 9, 17, 19) | rect(14, 22, 17, 25), INK)


def i_potion_life(a):
    potion(a, tones("7a1c50", "d0407a", "ff7aa8", "ffc0d4"), round_=True, level=0.62)
    a.fill(heart_mask(15.5, 21, 4.2), WHITE, 0.95, ink=False)


def i_potion_death(a):
    potion(a, tones("240e4a", "5a2aa8", "9058e8", "d0a8ff"), round_=False, cork=DARKWOOD, level=0.6)
    a.fill(ell(15.5, 20.5, 4.2, 3.6) | rect(13, 23, 18, 25), BONE, 0.9, ink=False)
    a.put(pts(13, 20, 14, 20, 17, 20, 18, 20, 13, 21, 18, 21, 15, 22, 16, 22, 14, 25, 17, 25), INK)


# ------------------------------------------------------------------ the role cards
CARD_ROLES = {"back": NIGHT, "cupid": PINK, "guard": BLUE, "hunter": ORANGE, "seer": PURPLE,
              "villager": tones("4a3a1c", "8a6a2c", "c8a048", "ecd07a", "fff2b8"), "werewolf": RED, "witch": GREEN}


def card(role):
    a = Art()
    mat = CARD_ROLES[role]
    a.fill(rrect(5, 1, 26, 30, 2), GOLD if role == "back" else PAPER, grad(5, 1, 26, 30, 0.98, 0.45), rim=0.1)
    a.fill(rect(7, 3, 24, 28), mat, grad(7, 3, 24, 28, 0.62, 0.12), ink=True)
    if role == "back":
        for cy in (9, 22):
            a.fill(poly(star_points(15.5, cy, 3, 0.4, 4)), GOLD, 0.9, ink=False)
        a.fill(ell(15.5, 15.5, 5, 5) - ell(17.5, 14, 4, 4), tones("d4daf0", "fffbe0"), 0.8, ink=False)
        a.set_tone(rect(7, 3, 24, 3) | rect(7, 3, 7, 28), 3)
    elif role == "cupid":
        a.fill(heart_mask(15.5, 14, 7), tones("b01c4a", "f04a78", "ff9ab4", "ffe0ea"), ball(15.5, 14, 9, 9), ink=True)
        a.fill(line(9, 23, 22, 8, 1.4), GOLD, 0.8)
        a.fill(poly((20, 6), (24, 6), (24, 10)), GOLD, 0.9, ink=False)
    elif role == "guard":
        m = shield_mask(9, 7, 22, 25)
        a.fill(m, IRON, grad(9, 7, 22, 25, 1.0, 0.3))
        a.fill((rect(15, 9, 16, 22) | rect(11, 13, 20, 14)) & m, GOLD, 0.8, ink=False)
    elif role == "hunter":
        a.fill(ring(15.5, 15.5, 7, 7, 1.8), WHITE, 0.9)
        a.fill(rect(15, 6, 16, 11) | rect(15, 20, 16, 25) | rect(6, 15, 11, 16) | rect(20, 15, 25, 16), WHITE, 0.9, ink=False)
        a.fill(ell(15.5, 15.5, 1.6, 1.6), RED, 0.7, ink=False)
    elif role == "seer":
        a.fill(ell(15.5, 15.5, 7.5, 5), WHITE, grad(8, 10, 23, 21, 0.98, 0.6))
        a.fill(ell(15.5, 15.5, 3.4, 3.4), TEAL, ball(15.5, 15.5, 3.6), ink=False)
        a.put(ell(15.5, 15.5, 1.4, 1.4), INK)
        a.put(pts(15, 7, 16, 7, 10, 8, 21, 8, 15, 24, 16, 24, 10, 23, 21, 23), (255, 240, 170))
    elif role == "villager":
        a.fill(ell(15.5, 18, 5.5, 5.5), SKIN, ball(15.5, 18, 6))
        a.put(pts(13, 18, 17, 18), INK)
        a.fill(ell(15.5, 13.5, 8, 2.2) | rrect(11, 8, 20, 13, 3), tones("7a4a1c", "b8802c", "e2b44a", "fff0a0"), grad(8, 8, 23, 15, 0.98, 0.4))
        a.fill(rect(11, 12, 20, 12), RED, 0.5, ink=False)
    elif role == "werewolf":
        fur = tones("1c1626", "3a3048", "5e5270", "8a7e9a")
        a.fill(poly((9, 7), (12, 13), (19, 13), (22, 7), (23, 18), (15.5, 26), (8, 18)), fur, grad(8, 7, 23, 26, 0.98, 0.25))
        a.put(pts(11, 16, 12, 16, 12, 17, 19, 16, 20, 16, 19, 17), (255, 214, 60))
        a.put(pts(15, 23, 16, 23, 15, 24, 16, 24), INK)
        a.put(pts(13, 22, 18, 22), (255, 255, 255))
    elif role == "witch":
        hat = tones("1a0e34", "34205e", "54368e", "7a58b8")
        a.fill(poly((16, 5), (20, 19), (11, 19), (13, 10)), hat, grad(11, 5, 20, 19, 0.95, 0.3))
        a.fill(ell(15.5, 20, 8, 2.6), hat, grad(7, 18, 23, 22, 0.9, 0.3))
        a.fill(rect(12, 16, 19, 17), GOLD, 0.7, ink=False)
        a.put(pts(10, 25, 13, 24, 18, 25, 21, 24), (200, 255, 160))
    return a.outline()


ICONS = {
    "axe": i_axe, "back": i_back, "bed": i_bed, "bone": i_bone, "book": i_book, "chest": i_chest, "clock": i_clock,
    "coin": i_coin, "compass": i_compass, "door": i_door, "exit": i_exit, "heart": i_heart, "lever": i_lever,
    "lock": i_lock, "map": i_map, "moon": i_moon, "next": i_next, "note": i_note, "pearl": i_pearl, "pencil": i_pencil,
    "play": i_play, "plus": i_plus, "potion-death": i_potion_death, "potion-life": i_potion_life, "ruler": i_ruler,
    "shield": i_shield, "skip": i_skip, "skyblock": i_skyblock, "star": i_star, "stop": i_stop, "sun": i_sun,
    "sword": i_sword, "tag": i_tag, "target": i_target, "timer": i_timer, "trash": i_trash, "vial": i_vial,
    "warn": i_warn,
}
GREYED = {"potion-life-off": "potion-life", "potion-death-off": "potion-death", "mark-last": "shield",
          "mark-wolf": "card-werewolf"}


def all_icons():
    """name -> Art, every icon of textures/item/icon."""
    icons = {}
    for name, draw in ICONS.items():
        art = Art()
        draw(art)
        icons[name] = art.outline()
    for role in CARD_ROLES:
        icons[f"card-{role}"] = card(role)
    for name, source in GREYED.items():
        icons[name] = icons[source].greyed()
    return dict(sorted(icons.items()))


def png(rows, palette=None):
    """rows of (r, g, b) or None -> PNG bytes (the second argument is kept for older callers and ignored)."""
    return png_bytes(rows)


ROWS = all_icons()["skyblock"].grid()   # the Skyblock icon: the floating island


def write_one(name, art):
    for sub in ("textures/item/icon", "models/item/icon", "items/icon"):
        (PACK / sub).mkdir(parents=True, exist_ok=True)
    (PACK / "textures/item/icon" / f"{name}.png").write_bytes(art.png())
    (PACK / "models/item/icon" / f"{name}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/icon/{name}"}}, indent=2) + "\n")
    (PACK / "items/icon" / f"{name}.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/icon/{name}"}}, indent=2) + "\n")


def write_icons():
    icons = all_icons()
    for name, art in icons.items():
        write_one(name, art)
    print("wrote", len(icons), "icons")


def write():
    write_one("skyblock", all_icons()["skyblock"])
    print("wrote the skyblock icon")


def preview(scale=16):
    """Nearest-neighbour enlargement for reviewers (docs/skyblock-notes/netmenu-icon-preview.png)."""
    return png([[c for c in row for _ in range(scale)] for row in ROWS for _ in range(scale)])


if __name__ == "__main__":
    import sys
    if "--sheet" in sys.argv:
        pathlib.Path(sys.argv[sys.argv.index("--sheet") + 1]).write_bytes(
            sheet([art.grid() for art in all_icons().values()], cols=10, scale=6))
    else:
        write_icons()
        if "--preview" in sys.argv:
            out = pathlib.Path(__file__).resolve().parents[2] / "docs" / "skyblock-notes" / "netmenu-icon-preview.png"
            out.write_bytes(preview())
            print("wrote", out)
