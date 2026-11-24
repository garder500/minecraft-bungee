#!/usr/bin/env python3
"""Objects shared by the economy and menu icons (coin, chest, scroll, arrows, potion...), painted with sb_paint."""
from sb_paint import (Art, INK, GOLD, WOOD, DARKWOOD, STONE, IRON, STEEL, LEAF, RED, WHITE, PAPER, GLASS, LEATHER,
                      ramp, tones, rect, rrect, ell, ring, poly, line, path, pts, shift, edge, ball, grad, vcyl, hcyl)

GREEN = tones("0e4a34", "1a8a3e", "3cc04a", "86e86a", "d4ffa8")
ORANGE = tones("7a1c14", "c8401c", "f47a24", "ffb04a", "ffe4a0")
TEAL = tones("0a3a4a", "0e7a84", "2cbcb0", "7aeccc", "d8fff0")
INKY = tones("3a2a34", "6a5650")


def coin(a, cx, cy, r, emblem=True, mat=GOLD):
    """A coin seen a little from the side: its edge shows at the bottom right."""
    t = 1 if r < 7 else 2
    a.fill(ell(cx + t * 0.6, cy + t, r, r), mat, 0.18)
    face = ell(cx, cy, r, r)
    a.fill(face, mat, grad(cx - r, cy - r, cx + r, cy + r, 0.98, 0.45), ink=False)
    a.tone(ring(cx, cy, r, r, 1.2) & edge(face, "u", "l"), +1)
    if r >= 5:
        inner = ring(cx, cy, r * 0.76, r * 0.76, 1.0)
        a.tone(inner, -1)
    if emblem and r >= 9:
        k = r * 0.52
        star = poly((cx, cy - k), (cx + k * 0.3, cy - k * 0.3), (cx + k, cy), (cx + k * 0.3, cy + k * 0.3),
                    (cx, cy + k), (cx - k * 0.3, cy + k * 0.3), (cx - k, cy), (cx - k * 0.3, cy - k * 0.3))
        a.set_tone(shift(star, 1, 1), 1)
        a.set_tone(star, 4)
    elif emblem and r >= 5:
        a.set_tone(rect(round(cx - 0.6), round(cy - r * 0.4), round(cx + 0.4), round(cy + r * 0.4)), 4)
    a.set_tone(pts(round(cx - r * 0.55), round(cy - r * 0.7), round(cx - r * 0.7), round(cy - r * 0.55)), 4)
    return a


def arrow(a, cx, tail, tip, mat, half=2, head=5):
    """A vertical arrow centred on cx + 0.5, from y = tail to y = tip."""
    up = tip < tail
    s = 1 if up else -1
    hd = poly((cx - head + 1, tip + s * head), (cx + 0.5, tip), (cx + head, tip + s * head))
    sh = rect(cx - half + 1, min(tip + s * head, tail), cx + half, max(tip + s * head, tail))
    a.fill(hd | sh, mat, grad(cx - head, 0, cx + head, 0, 0.95, 0.35))
    return a


def harrow(a, cy, tail, tip, mat, half=2, head=5):
    """A horizontal arrow centred on cy + 0.5, from x = tail to x = tip."""
    s = 1 if tip < tail else -1
    hd = poly((tip + s * head, cy - head + 1), (tip, cy + 0.5), (tip + s * head, cy + head))
    sh = rect(min(tip + s * head, tail), cy - half + 1, max(tip + s * head, tail), cy + half)
    a.fill(hd | sh, mat, grad(0, cy - head, 0, cy + head, 0.95, 0.35))
    return a


def scroll(a, x0, y0, x1, y1, lines=True, mat=PAPER):
    """A sheet of parchment with a rolled top and bottom."""
    a.fill(rect(x0 + 1, y0 + 2, x1 - 1, y1 - 2), mat, grad(x0, y0, x1, y1, 0.9, 0.5))
    a.fill(rrect(x0, y0, x1, y0 + 3, 1), mat, hcyl(y0, y0 + 3, 0.98, 0.4))
    a.fill(rrect(x0, y1 - 3, x1, y1, 1), mat, hcyl(y1 - 3, y1, 0.9, 0.3))
    if lines:
        for i, y in enumerate(range(y0 + 7, y1 - 5, 3)):
            a.put(rect(x0 + 4, y, x1 - (4 if i % 2 == 0 else 7), y), INKY[1])
    return a


def chest(a, x0=2, y0=4, x1=29, y1=28, wood=WOOD, metal=GOLD, opened=False):
    mid = y0 + (y1 - y0) * 3 // 8
    if opened:
        a.fill(rrect(x0 + 1, y0, x1 - 1, mid + 1, 2), DARKWOOD, grad(0, y0, 0, mid, 0.2, 0.5))     # the inside of the lid
        a.fill(rect(x0 + 1, y0, x1 - 1, y0 + 1), metal, 0.6)
    a.fill(rrect(x0, mid, x1, y1, 1), wood, grad(0, mid, 0, y1, 0.62, 0.22), rim=0.2)
    if not opened:
        a.fill(rrect(x0, y0, x1, mid, 3), wood, grad(0, y0, 0, mid, 0.98, 0.5), rim=0.15)
        a.tone(rect(x0 + 1, y0 + 4, x1 - 1, y0 + 4), -1)
    a.tone(rect(x0 + 1, (mid + y1) // 2, x1 - 1, (mid + y1) // 2), -1)
    a.tone(rect(x0, mid + 1, x1, mid + 1), -2)
    top = mid if opened else y0
    for bx in (x0 + 3, x1 - 6):
        a.fill(rect(bx, top, bx + 3, y1), metal, grad(0, top, 0, y1, 0.95, 0.35), rim=0.2)
    a.fill(rect(x0, y1 - 2, x1, y1), metal, 0.45, rim=0.2)
    cx = (x0 + x1) // 2
    a.fill(rrect(cx - 3, mid - 3 if not opened else mid, cx + 4, mid + 6, 2), metal, None)
    a.put(rect(cx, mid + 1, cx + 1, mid + 3), INK)
    return a


def check(a, x, y, mat=GREEN, w=3.4, k=1.0):
    """A check mark whose elbow is at (x, y)."""
    a.fill(path([(x - 5 * k, y - 5 * k), (x, y), (x + 9 * k, y - 11 * k)], w), mat, grad(x - 6 * k, y - 11 * k, x + 9 * k, y, 0.95, 0.4))
    return a


def plus(a, cx, cy, r, mat=GREEN, half=1):
    m = rect(cx - r, cy - half, cx + r + 1, cy + half + 1) | rect(cx - half, cy - r, cx + half + 1, cy + r + 1)
    a.fill(m, mat, grad(cx - r, cy - r, cx + r, cy + r, 0.98, 0.35))
    return a


def bolt(a, x, y, k=1.0, mat=GOLD):
    """A lightning bolt whose top is at (x, y)."""
    m = poly((x, y), (x + 6 * k, y), (x + 2 * k, y + 6 * k), (x + 6 * k, y + 6 * k), (x - 3 * k, y + 15 * k), (x - 1 * k, y + 8 * k), (x - 5 * k, y + 8 * k))
    a.fill(m, mat, grad(x - 4 * k, y, x + 6 * k, y + 14 * k, 1.0, 0.4))
    return a


def gavel(a, mat=WOOD, band=GOLD):
    a.fill(line(6, 26, 19, 13, 2.6), mat, grad(8, 12, 16, 24, 0.9, 0.3))
    head = poly((13, 7), (19, 1), (30, 12), (24, 18))
    a.fill(head, mat, grad(15, 3, 27, 16, 0.98, 0.25))
    a.fill(poly((13, 7), (15, 5), (17, 7), (15, 9)), band, grad(13, 5, 17, 9, 1.0, 0.4), ink=False)
    a.fill(poly((26, 16), (28, 14), (30, 12), (24, 18), (22, 16), (24, 14)), band, grad(22, 12, 30, 18, 0.8, 0.3), ink=False)
    a.tone(line(17, 7, 26, 16), +1)
    return a


def magnifier(a, cx=13, cy=13, r=10.5):
    a.fill(line(cx + r * 0.7, cy + r * 0.7, 28, 28, 4), WOOD, grad(20, 22, 28, 26, 0.9, 0.3))
    a.fill(ell(cx, cy, r, r), GOLD, ball(cx, cy, r + 1))
    a.fill(ell(cx, cy, r - 2.6, r - 2.6), GLASS, grad(cx - r, cy - r, cx + r, cy + r, 0.3, 0.95), ink=True)
    a.set_tone(line(cx - 4, cy - 1, cx - 1, cy - 4, 1.4) | pts(cx - 4, cy + 2), 4)
    return a


def potion(a, liquid, round_=True, cork=WOOD, level=0.5):
    """A flask: round (life) or angular (death)."""
    a.fill(rect(13, 1, 18, 4), cork, None)
    a.fill(rect(12, 5, 19, 11), GLASS, vcyl(12, 19))
    a.fill(rect(11, 4, 20, 6), GLASS, 0.85)
    if round_:
        bulb = ell(15.5, 19.5, 11, 10)
        shade = ball(15.5, 19.5, 11.5, 10.5)
    else:
        bulb = poly((12, 11), (19, 11), (28, 26), (26, 29), (5, 29), (3, 26))
        shade = grad(5, 11, 26, 29, 0.95, 0.3)
    a.fill(bulb, GLASS, shade)
    y_level = int(11 + 18 * (1 - level))
    inside = {p for p in bulb if p[1] >= y_level} & {p for p in bulb if all(q in bulb for q in ((p[0] - 1, p[1]), (p[0] + 1, p[1]), (p[0], p[1] + 1)))}
    a.fill(inside, liquid, shade, ink=False)
    a.set_tone({p for p in inside if p[1] == y_level}, len(liquid) - 1)
    a.set_tone(path([(7, 16), (6, 19), (7, 22)]) | pts(14, 7, 14, 8, 14, 9), 4)
    return a
