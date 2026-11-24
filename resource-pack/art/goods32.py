"""Painted 32 px icons of the Skyblock goods (paint32): the misc items of items_icons.py (reforge stones, singularities,
drill fuels, pet items, the warp pearl, the grappling hook...), the dungeon drops of dungeon_icons.py and the small ore
materials and ore blocks of quarry_items.py. Each function returns a paint32.Pic; the three scripts write the files."""
import math

from paint32 import (AMBER, AMETHYST, AQUA, BONE, BRONZE, COPPER, DARKSTEEL, EMERALD, GOLD, IRON, LEATHER, PEARL, ROPE, RUBY,
                     SAPPHIRE, SILVER, STONE, WOOD, Pic, R, both, circle, ellipse, inter, mirror, move, path, poly, rect, ring,
                     seg, star, sub, sym, taper, union)

SHADE = R("#08060c", "#120e18", "#1e1826", "#2e2638", "#44384e")
RIFT = R("#4a0a50", "#961496", "#e040d8", "#ff8cf4", "#ffe2ff")
FIRE = R("#6a1208", "#c0300c", "#f4701c", "#ffb43c", "#fff2a0")
HAY = R("#6a4612", "#a67626", "#d6a840", "#f0cf68", "#fff0a8")
BARK = R("#1e120a", "#3c2414", "#5e3c20", "#845a32", "#b0844e")
LIME = R("#1c4a08", "#3c8a0c", "#7cd01c", "#b8f84a", "#eeffb0")
VOLT = R("#0c2a6a", "#1458c0", "#2c9cf4", "#7cd8ff", "#e4fbff")
OLIVE = R("#1a1e0c", "#343c16", "#566226", "#7e8c3c", "#aebc66")
OIL = R("#050408", "#0e0c14", "#1a1824", "#2c2a3c", "#5a5a7a")
VIOLET = R("#1c0f4a", "#3c2296", "#6a48e0", "#a288ff", "#e0d6ff")
GLASS = R("#2a4a5a", "#4c7c8c", "#84b4c0", "#bce0e6", "#f0fcff")
PARCH = R("#5a4020", "#967444", "#c8a870", "#e6d09c", "#fbf0cc")
NECRO = R("#1c1832", "#3c3866", "#6c68a4", "#a8a4dc", "#e8e4ff")
SPIRIT = R("#0c4a40", "#14907a", "#34d8b4", "#8cfce0", "#e4fff8")
LEAF = R("#14300c", "#2c5c14", "#4c8c24", "#82bc44", "#c4ec84")
WITHER = R("#08080c", "#16161e", "#28283a", "#40405a", "#646488")
SAND = R("#7a4a14", "#b8822c", "#e6b84c", "#fadc80", "#fff6c4")
TEAL = R("#0a3a3a", "#14787a", "#34bcae", "#7ceedc", "#d8fff6")
EMBERROCK = R("#1c1412", "#3a2a26", "#5c463e", "#84685c", "#b09484")
CANDY = R("#143a6a", "#1c6cc0", "#3ca8f4", "#8cd8ff", "#e4f8ff")
CANDYP = R("#6a0c4a", "#c01c84", "#f458b4", "#ffa0d8", "#ffe2f2")
SCALE = R("#0c2a24", "#1a5444", "#308468", "#5cb890", "#a6eac4")
FROST = R("#12345c", "#2a6aa6", "#56aae0", "#9edcfa", "#e8fbff")
NAVY = R("#07081c", "#10143a", "#1e2660", "#34408c", "#5a6cc0")
SUN = R("#6a2a04", "#b85a0c", "#f0921c", "#ffc24a", "#fff0a4")
VOIDG = R("#0c0718", "#1c1034", "#321e5c", "#50348c", "#8058c8")


# ================================================================================================ misc items
def grappling_hook():
    p = Pic()
    coil = ring(22, 23, 7.6, 4.2)
    p.part(coil, ROPE, form="round", bevel=1.7)
    for i in range(10):                                      # the lay of the rope
        a = 2 * math.pi * i / 10
        p.shift(inter(coil, seg(22 + 4 * math.cos(a), 23 + 4 * math.sin(a), 22 + 8 * math.cos(a + 0.35), 23 + 8 * math.sin(a + 0.35), 0.45)), -1)
    p.part(path([(27, 29), (30, 30.5)], 1.1), ROPE, form="flat")
    p.part(seg(9, 9, 18, 18, 1.6), IRON, form="round", bevel=1.5)                # the shank
    p.part(ring(18.5, 18.5, 2.9, 1.2), IRON, bevel=1)
    for pts in ([(9, 9), (4, 9.5), (2, 14), (4, 18)], [(9, 9), (9.5, 4), (14, 2), (18, 4)]):
        p.part(path(pts, 1.3), IRON, form="round", bevel=1.3)
    p.part(taper(9, 9, 1.6, 2.5, 2.5, 0.4), IRON, form="round", bevel=1.3, lift=1)
    p.part(circle(9, 9, 2.4), SILVER, form="round", bevel=2)
    p.dots([(4, 18), (18, 4)], SILVER, 4)
    p.spark(2, 2, VOLT)
    return p


def _singularity(body, glow, texture):
    """A ball of the material crushed round a dark heart: spiral arms falling in, chunks of it in orbit."""
    p = Pic()
    ball = circle(16, 16.5, 11.5)
    p.part(ball, body, form="round", bevel=11.5)
    texture(p, ball)
    for i in range(4):                                       # matter spiralling in
        a = 1.57 * i + 0.5
        pts = [(16 + r * math.cos(a + r * 0.16), 16.5 + r * math.sin(a + r * 0.16)) for r in (3.5, 5.5, 7.5, 9.5, 11)]
        p.fill(inter(ball, path(pts, 0.55)), glow, 3)
        p.shift(inter(ball, move(path(pts, 0.55), 1, 1)), -1)
    p.fill(circle(16, 16.5, 4.4), glow, 2), p.fill(circle(16, 16.5, 3.3), SHADE, 0)
    p.fill(inter(ring(16, 16.5, 4.4, 3.3), poly([(0, 0), (20, 0), (0, 20)])), glow, 4)
    for x, y, s in ((1, 3, 3), (27, 25, 3), (27, 3, 2), (3, 27, 2)):   # chunks falling in
        p.part(rect(x, y, x + s - 1, y + s - 1), body, bevel=1, lift=1)
    p.spark(24, 1, glow), p.spark(8, 30, glow)
    return p


def wood_singularity():
    def tex(p, ball):
        for r in (9, 7.4, 5.8):
            p.shift(inter(ball, ring(13, 13, r + 0.5, r - 0.2)), -1)
        p.shift(inter(ball, rect(21, 9, 21, 24)), -1), p.shift(inter(ball, rect(9, 20, 9, 25)), -1)
    return _singularity(BARK, LIME, tex)


def stone_singularity():
    def tex(p, ball):
        for pts in ([(8, 9), (11, 12), (10, 15)], [(22, 9), (20, 12), (23, 15)], [(11, 23), (14, 21), (18, 24), (22, 22)]):
            p.shift(inter(ball, path(pts, 0.5)), -2)
        p.dots([(10, 10), (19, 8)], STONE, 4)
    return _singularity(STONE, FIRE, tex)


def hay_singularity():
    def tex(p, ball):
        for i in range(-12, 14, 3):
            p.shift(inter(ball, seg(6 + i, 4, 12 + i, 28, 0.45)), -1)
        p.shift(inter(ball, rect(0, 11, 31, 11)), -2), p.shift(inter(ball, rect(0, 21, 31, 21)), -2)   # the bale twine
    return _singularity(HAY, TEAL, tex)


def _cyl_h(cx, r):
    return lambda x, y: math.sqrt(max(0.0, r * r - (x - cx) ** 2))


def drill_fuel_cell():
    p = Pic()
    p.part(rect(11, 6, 20, 26), GLASS, hf=_cyl_h(16, 5.5), grad=0.2)             # the glass tube
    p.fill(rect(12, 11, 19, 25), LIME, 2)
    p.fill(rect(12, 11, 13, 25), LIME, 3), p.fill(rect(18, 11, 19, 25), LIME, 1), p.fill(rect(12, 11, 19, 11), LIME, 4)
    p.dots([(15, 15), (17, 20), (14, 22)], LIME, 4)                              # bubbles
    p.fill(rect(13, 7, 13, 9), GLASS, 4)
    for y0 in (2, 25):                                                          # copper caps
        p.part(rect(9, y0, 22, y0 + 4), COPPER, hf=_cyl_h(16, 7.5), grad=0.2)
        p.shift(rect(9, y0 + 2, 22, y0 + 2), -1)
    p.part(rect(14, 0, 17, 2), COPPER, bevel=1, lift=1)                          # the terminal
    for y in (14, 18, 22):
        p.put(20, y, GLASS, 1), p.put(21, y, COPPER, 3)                          # the gauge marks
    p.spark(26, 8, LIME), p.spark(4, 22, LIME)
    return p


def drill_fuel_volta():
    p = Pic()
    body = poly([(8, 5), (24, 5), (27, 9), (27, 24), (24, 28), (8, 28), (5, 24), (5, 9)])
    p.part(body, DARKSTEEL, form="round", bevel=2.5, lift=1)
    p.part(rect(11, 1, 14, 5), GOLD, bevel=1), p.part(rect(18, 1, 21, 5), GOLD, bevel=1)   # terminals
    window = poly([(10, 9), (22, 9), (23.5, 11), (23.5, 22), (22, 24), (10, 24), (8.5, 22), (8.5, 11)])
    p.part(window, VOLT, bevel=1.5, seam="line", grad=0.5)
    bolt = poly([(18, 9.5), (11.5, 17), (15.5, 17), (13.5, 24), (20.5, 15.5), (16.5, 15.5)])
    p.fill(bolt, VOLT, 4)
    p.shift(sub(union(*[move(bolt, dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]), bolt), 1)
    p.paint(rect(5, 25, 26, 26), GOLD), p.paint(rect(5, 7, 26, 7), GOLD)
    for x, y in ((7, 10), (25, 10), (7, 23), (25, 23)):
        p.put(x, y, SILVER, 4)
    p.spark(29, 3, VOLT, big=True), p.spark(2, 29, VOLT), p.spark(2, 4, VOLT)
    return p


def drill_fuel_oil():
    p = Pic()
    p.part(rect(6, 4, 25, 28), OLIVE, hf=_cyl_h(16, 11), grad=0.2)               # the drum
    p.part(ellipse(16, 5, 10, 3), OLIVE, form="round", bevel=1.5, lift=1)
    p.fill(ellipse(16, 5, 7.6, 1.7), OIL, 1), p.fill(rect(11, 4, 14, 4), OIL, 4)
    for y in (11, 21):                                                          # hoops
        p.paint(rect(6, y, 25, y + 1), IRON)
    p.part(rect(18, 1, 21, 4), IRON, bevel=1)                                    # the bung
    drop = union(circle(15, 17.5, 2.6), poly([(15, 12.5), (17.4, 16.8), (12.6, 16.8)]))
    p.fill(drop, OIL, 1), p.put(14, 17, OIL, 4), p.put(14, 16, OIL, 4)
    p.part(path([(22, 5), (23, 9), (22.5, 14)], 1.0), OIL, form="flat")          # a spill down the side
    p.put(22, 15, OIL, 2), p.put(22, 6, OIL, 4)
    p.part(ellipse(20, 30, 5, 1.3), OIL, form="flat", lift=-1)
    p.put(18, 30, OIL, 4)
    return p


def warp_pearl():
    p = Pic()
    ball = circle(16, 16.5, 11)
    p.part(ball, VIOLET, form="round", bevel=11)
    for i in range(22):                                      # the spiral of the gate inside
        a = 0.55 * i
        r = 1 + i * 0.42
        x, y = 17 + r * math.cos(a), 17.5 + r * math.sin(a) * 0.9
        if ball(x, y):
            p.put(int(x), int(y), RIFT, 4 if i < 6 else 3 if i < 14 else 2)
    p.fill(circle(17, 17.5, 1.3), PEARL, 4)
    p.fill(poly([(9, 9), (13, 7.5), (12, 10), (9.5, 12.5)]), VIOLET, 4)          # the glint
    p.put(8, 14, VIOLET, 4)
    p.shift(inter(ball, sub(circle(16, 16.5, 11), circle(15, 15.5, 10.4))), 1)   # light bouncing in the lower rim
    for x, y in ((3, 6), (28, 9), (27, 27), (4, 26)):
        p.extra[(x, y)] = RIFT[3]
    p.spark(27, 3, RIFT, big=True), p.spark(3, 29, VIOLET)
    return p


def _stone(p, pts, ramp, bevel=3):
    shape = poly(pts)
    p.part(shape, ramp, form="plate", bevel=bevel)
    return shape


def reforge_stone_fabled():
    p = Pic()
    s = _stone(p, [(16, 1.5), (27, 7), (29, 19), (22, 29), (10, 29), (3, 19), (5, 7)], SAND)
    p.fill(inter(s, star(16, 15.5, 8, 3, 4)), GOLD, 3)
    p.fill(inter(s, star(16, 15.5, 4, 1.6, 4)), GOLD, 4)
    p.shift(sub(star(16.8, 16.3, 8, 3, 4), star(16, 15.5, 8, 3, 4)), -2)
    for a, b in (((7, 9), (10, 12)), ((25, 9), (22, 12)), ((9, 26), (11, 23)), ((23, 26), (21, 23))):   # gold veins
        p.line(a[0], a[1], b[0], b[1], GOLD, 3, only=True)
    p.spark(28, 3, GOLD, big=True), p.spark(3, 28, AMBER)
    return p


def reforge_stone_withered():
    p = Pic()
    s = _stone(p, [(13, 1), (21, 4), (26, 2.5), (28, 13), (24, 20), (27, 27), (17, 30.5), (8, 28), (4, 18), (7, 12), (5, 6)], WITHER)
    for pts in ([(12, 4), (14, 10), (11, 15), (15, 20), (13, 27)], [(14, 10), (20, 12), (23, 9)], [(15, 20), (21, 23)]):   # rot veins
        p.fill(inter(s, path(pts, 0.5)), LIME, 2)
    p.dots([(14, 10), (15, 20), (11, 15)], LIME, 4)
    p.fill(inter(s, both(rect(18, 14, 19, 15), 19.5)), SHADE, 0)
    p.erase(poly([(26, 14), (31, 12), (31, 19), (25.5, 18)]))                    # a bite out of the side
    for x, y in ((29, 22), (2, 9), (27, 30)):
        p.extra[(x, y)] = LIME[2]
    return p


def reforge_stone_ancient():
    p = Pic()
    s = _stone(p, [(10, 3), (22, 2.5), (29, 10), (29.5, 21), (23, 29), (10, 29.5), (2.5, 22), (2.5, 10)], EMBERROCK, bevel=3.5)
    for i in range(30):                                      # the fossil spiral
        a = 0.5 * i
        r = 1 + i * 0.3
        x, y = int(16 + r * math.cos(a)), int(16 + r * math.sin(a))
        if s(x + 0.5, y + 0.5):
            p.put(x, y, BONE, 3 if i % 3 else 2)
            if p.has(x + 1, y + 1) and p.px[(x + 1, y + 1)][0] is EMBERROCK:
                p.shift(rect(x + 1, y + 1, x + 1, y + 1), -1)
    for cx, cy in ((7, 8), (25, 24), (6, 22)):               # moss
        p.paint(inter(s, circle(cx, cy, 3)), R("#14300c", "#2c5c14", "#4c8c24", "#82bc44", "#c4ec84"))
    p.dots([(24, 7), (25, 8)], FIRE, 3)
    return p


def reforge_stone_refined():
    p = Pic()
    top = poly([(3, 15), (18, 5), (29, 10.5), (14, 21)])
    left = poly([(3, 15), (14, 21), (14, 27.5), (3, 21.5)])
    right = poly([(14, 21), (29, 10.5), (29, 17), (14, 27.5)])
    p.part(top, PEARL, form="flat", lift=1, grad=0.5)
    p.part(left, PEARL, form="flat", lift=-1, seam=None)
    p.part(right, PEARL, form="flat", lift=-2, seam=None, grad=0.2)
    p.line(4, 15, 18, 6, PEARL, 4), p.line(14, 21, 14, 27, PEARL, 3)
    for i in range(3):                                       # honing lines on the polished face
        p.shift(inter(top, seg(8 + i * 5, 14 - i * 0, 13 + i * 5, 17.5, 0.4)), -1)
    p.paint(inter(union(left, right), rect(0, 24, 31, 31)), SAPPHIRE, 1)
    p.paint(poly([(16, 7.5), (19, 6), (29, 10.5), (26, 12.5)]), SAPPHIRE, 1)     # the fine-grit end
    p.spark(26, 3, PEARL, big=True), p.spark(4, 27, SAPPHIRE)
    return p


def enchanted_book():
    p = Pic()
    cover = poly([(5, 5), (24, 3), (27, 6), (27, 27), (8, 29.5), (5, 26.5)])
    p.part(cover, VIOLET, form="plate", bevel=2)
    p.part(poly([(8, 27), (27, 24.5), (27, 27), (8, 29.5)]), PARCH, form="flat", lift=1)      # the page block
    p.shift(seg(8, 28.5, 27, 26, 0.4), -1)
    p.part(poly([(5, 5), (8, 6.5), (8, 29.5), (5, 26.5)]), VIOLET, form="flat", lift=-1, seam=None)   # the spine
    for y in (10, 21):
        p.paint(rect(5, y, 7, y + 1), GOLD)
    for pts in ([(22, 3.2), (27, 6), (27, 9), (24, 6)], [(8, 24), (11, 26.8), (8, 27)], [(24, 24.9), (27, 21.5), (27, 24.5)], [(8, 6.6), (11, 6.2), (8, 9.5)]):
        p.paint(poly(pts), GOLD)                             # corners
    p.part(circle(17.5, 15, 5), GOLD, form="round", bevel=1.4)
    p.facet([(17.5, 11), (21.5, 15), (17.5, 19), (13.5, 15)], RIFT)
    p.part(rect(21, 26, 23, 31), RUBY, bevel=1)              # the ribbon
    p.erase(poly([(21, 32), (22.5, 30), (24, 32)]))
    p.spark(28, 2, RIFT, big=True), p.spark(2, 2, VIOLET), p.spark(30, 20, RIFT)
    return p


def overflux_capacitor():
    p = Pic()
    p.part(rect(10, 5, 21, 26), GLASS, hf=_cyl_h(16, 6.5), grad=0.2)
    core = poly([(16, 6), (19.5, 12), (18, 21), (16, 26), (14, 21), (12.5, 12)])
    p.fill(core, FIRE, 3), p.fill(poly([(16, 8), (17.8, 12.5), (16, 23), (14.4, 12.5)]), FIRE, 4)
    p.fill(inter(core, rect(17, 0, 31, 31)), FIRE, 2)
    for y in (9, 14, 19, 24):                                # the coil winding round the tube
        p.part(poly([(9, y + 1.5), (22.5, y - 1.5), (22.5, y + 0.3), (9, y + 3.3)]), COPPER, hf=_cyl_h(16, 7), grad=0.2)
    for y0 in (1, 26):
        p.part(rect(8, y0, 23, y0 + 4), BRONZE, hf=_cyl_h(16, 8.5), grad=0.2)
        p.dots([(10, y0 + 2), (21, y0 + 2)], GOLD, 4)
    p.fill(rect(11, 6, 11, 8), GLASS, 4)
    for pts in ([(24, 12), (27, 10), (26, 14), (30, 12)], [(8, 20), (5, 18), (6, 22), (2, 21)]):   # arcs
        for a, b in zip(pts, pts[1:]):
            n = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
            for i in range(n + 1):
                p.extra[(round(a[0] + (b[0] - a[0]) * i / n), round(a[1] + (b[1] - a[1]) * i / n))] = FIRE[4]
    return p


def pet_egg():
    p = Pic()
    nest = sub(ellipse(16, 24.5, 14, 6.5), rect(0, 0, 31, 19))
    egg = union(inter(ellipse(16, 15, 8.6, 12), rect(0, 0, 31, 14)), inter(ellipse(16, 15, 8.6, 9.5), rect(0, 15, 31, 31)))
    p.part(egg, R("#4a5a3c", "#84a06c", "#c2d8a4", "#e6f4cc", "#ffffee"), form="round", bevel=8)
    for cx, cy, r in ((12, 9, 1.6), (19, 13, 2.0), (13, 17, 1.4), (20, 6.5, 1.2), (17, 19, 1.2)):   # speckles
        p.paint(inter(egg, circle(cx, cy, r)), LEAF, 0)
    p.part(nest, HAY, form="round", bevel=3)
    for i in range(-2, 30, 3):                               # woven twigs
        p.shift(inter(nest, seg(i, 20, i + 5, 31, 0.45)), -1)
        p.shift(inter(nest, seg(i + 6, 20, i + 2, 31, 0.45)), 1 if i % 2 else -1)
    p.dots([(2, 20), (1, 19), (29, 20), (30, 18), (25, 30)], HAY, 3)
    p.spark(27, 5, LIME)
    return p


def pet_candy():
    p = Pic()
    for sign in (1, -1):                                     # twisted wrapper ends
        cx, cy = 16 + sign * 10.5, 16 - sign * 7.5
        fan = poly([(16 + sign * 6.5, 16 - sign * 4.5), (cx + sign * 3.5, cy - sign * 0.5 - 4.5), (cx + sign * 4.5, cy + 3.5)])
        p.part(fan, CANDYP, form="plate", bevel=1.5)
        p.shift(seg(16 + sign * 7, 16 - sign * 5, cx + sign * 3, cy, 0.4), -1)
    ball = circle(16, 16, 8.8)
    p.part(ball, CANDY, form="round", bevel=8.8)
    for k in (-9, -3, 3, 9):                                 # the swirl
        band = inter(ball, sub(circle(16 + k - 9, 16 + 9, 13), circle(16 + k - 9, 16 + 9, 11)))
        p.paint(band, CANDYP)
    p.fill(poly([(10.5, 10.5), (14, 9), (12.5, 11.5), (10.5, 13.5)]), PEARL, 4)
    p.spark(4, 27, CANDYP), p.spark(28, 28, CANDY)
    return p


def pet_tier_boost():
    p = Pic()
    for i, (y, ramp) in enumerate(((19, VIOLET), (11, AMETHYST), (3, RIFT))):   # three chevrons climbing
        w = 12 - i * 2
        chev = poly([(16, y), (16 + w, y + 8), (16 + w, y + 12), (16, y + 5), (16 - w, y + 12), (16 - w, y + 8)])
        p.part(chev, ramp, form="plate", bevel=1.6)
        p.paint(sub(chev, move(chev, 0, -1.3)), GOLD)
    p.part(star(16, 4, 3.6, 1.5, 4), GOLD, form="flat", lift=2, seam=None)
    p.spark(4, 4, RIFT, big=True), p.spark(28, 6, GOLD), p.spark(27, 29, VIOLET)
    return p


def pet_xp_share():
    p = Pic()
    for cx, flip in ((10.5, 1), (21.5, -1)):                 # two flasks joined by a tube
        p.part(rect(int(cx - 1.5), 6, int(cx + 0.5), 13), GLASS, hf=_cyl_h(cx, 1.8), grad=0.2)
        ball = circle(cx, 20, 7.6)
        p.part(ball, GLASS, form="round", bevel=7.6)
        liquid = inter(circle(cx, 20, 6.2), rect(0, 17 if flip == 1 else 21, 31, 31))
        p.part(liquid, LIME, form="round", bevel=5, seam=None, grad=0.4, lift=1)
        p.fill(inter(liquid, rect(0, 17 if flip == 1 else 21, 31, 17 if flip == 1 else 21)), LIME, 4)
        p.part(rect(int(cx - 2.5), 3, int(cx + 1.5), 6), WOOD, bevel=1)
        p.put(int(cx - 4), 16, PEARL, 4), p.put(int(cx - 5), 17, PEARL, 4)
    p.dots([(9, 22), (12, 24), (21, 23)], LIME, 4)
    p.part(path([(10.5, 9), (16, 12), (21.5, 9)], 0.9), GOLD, form="flat")
    for x, y in ((14, 9), (16, 8), (18, 9)):                 # sparks travelling across
        p.extra[(x, y)] = LIME[3]
    p.spark(16, 2, LIME, big=True)
    return p


def pet_big_teeth():
    p = Pic()
    gum = poly([(2, 9), (8, 5), (24, 5), (30, 9), (29, 15), (3, 15)])
    for x0, ln, w in ((7, 15, 3.2), (16, 11, 2.6), (25, 15, 3.2)):               # the fangs
        tooth = poly([(x0 - w, 12), (x0 + w, 12), (x0 + w * 0.8, 12 + ln * 0.5), (x0 + 0.5, 13 + ln), (x0 - w * 0.8, 12 + ln * 0.5)])
        p.part(tooth, BONE, form="round", bevel=2.5, lift=1)
        p.shift(seg(x0 + 1, 15, x0 + 0.8, 10 + ln, 0.4), -1)
    for x0 in (11.5, 20.5):
        p.part(poly([(x0 - 1.8, 12), (x0 + 1.8, 12), (x0, 19)]), BONE, form="round", bevel=1.5)
    p.part(gum, R("#4a0c1c", "#8c2034", "#c44858", "#ec7c84", "#ffc0c0"), form="round", bevel=3)
    p.shift(inter(gum, both(path([(7, 15), (7, 12), (11.5, 13.5), (16, 12)], 0.4))), -1)
    p.dots([(7, 22), (25, 21)], BONE, 0)                     # chips
    p.spark(28, 28, BONE)
    return p


def pet_hardened_scales():
    p = Pic()
    rows = [(24, (10, 16, 22)), (17.5, (7, 13, 19, 25)), (11, (10, 16, 22)), (5, (13, 19))]
    for y, xs in rows:                                       # overlapping plates, the lowest row first
        for x in xs:
            scale = poly([(x - 4.2, y - 2), (x, y - 5), (x + 4.2, y - 2), (x + 3.4, y + 3.5), (x, y + 7), (x - 3.4, y + 3.5)])
            p.part(scale, SCALE, form="round", bevel=2.2)
            p.shift(seg(x, y - 3, x, y + 4.5, 0.4), 1)
    p.dots([(13, 3), (10, 9), (7, 15)], SCALE, 4)
    p.spark(28, 3, SCALE, big=True), p.spark(3, 28, SCALE)
    return p


def _heartleaf(cx, cy, s, a):
    """A heart-shaped leaf whose point sits at (cx, cy), opening in the direction a (radians)."""
    ca, sa = math.cos(a), math.sin(a)

    def rot(u, v):
        return (cx + u * ca - v * sa, cy + u * sa + v * ca)
    lobes = [circle(*rot(s * 0.72, -s * 0.36), s * 0.46), circle(*rot(s * 0.72, s * 0.36), s * 0.46)]
    tri = poly([rot(0, 0), rot(s * 0.75, -s * 0.8), rot(s * 0.75, s * 0.8)])
    return union(tri, *lobes)


def pet_lucky_clover():
    p = Pic()
    p.part(path([(16, 15), (17, 22), (21, 28), (25, 30.5)], 1.1), LEAF, form="flat", lift=-1)
    for a in (-0.78, 0.78, 2.36, 3.92):
        leaf = _heartleaf(16, 14, 11.5, a)
        p.part(leaf, EMERALD, form="round", bevel=3)
        p.line(16, 14, round(16 + 7 * math.cos(a)), round(14 + 7 * math.sin(a)), EMERALD, 4, only=True)
    p.fill(circle(16, 14, 1.3), GOLD, 3)
    p.dots([(9, 6), (10, 5)], PEARL, 4)                      # dew
    p.spark(28, 3, EMERALD, big=True), p.spark(3, 27, GOLD)
    return p


MISC = {
    "grappling_hook": grappling_hook, "wood_singularity": wood_singularity, "stone_singularity": stone_singularity,
    "hay_singularity": hay_singularity, "drill_fuel_cell": drill_fuel_cell, "drill_fuel_volta": drill_fuel_volta,
    "drill_fuel_oil": drill_fuel_oil, "warp_pearl": warp_pearl, "reforge_stone_fabled": reforge_stone_fabled,
    "reforge_stone_withered": reforge_stone_withered, "reforge_stone_ancient": reforge_stone_ancient,
    "reforge_stone_refined": reforge_stone_refined, "enchanted_book": enchanted_book, "overflux_capacitor": overflux_capacitor,
    "pet_egg": pet_egg, "pet_candy": pet_candy, "pet_tier_boost": pet_tier_boost, "pet_xp_share": pet_xp_share,
    "pet_big_teeth": pet_big_teeth, "pet_hardened_scales": pet_hardened_scales, "pet_lucky_clover": pet_lucky_clover,
}


# ================================================================================================ dungeon drops
def bone_fragment():
    p = Pic()
    p.part(seg(10, 22, 21, 11, 2.8), BONE, form="round", bevel=2.8)              # the shaft
    p.part(union(circle(6.5, 22, 3.8), circle(10, 26, 3.8)), BONE, form="round", bevel=3.4, lift=1)   # the knuckle
    p.shift(seg(8, 24, 9, 23, 0.5), -1)
    jag = poly([(19, 7), (22, 3), (23, 7.5), (26.5, 5), (25.5, 9.5), (29, 10), (25, 13.5), (21.5, 14.5)])   # the splintered end
    p.part(jag, BONE, form="plate", bevel=1.4)
    p.fill(inter(jag, poly([(21, 9), (25, 8), (26, 11), (22.5, 13)])), R("#4a3c38", "#8a7a6a", "#c2b49c", "#e6dcc4", "#fffaec"), 0)
    p.shift(seg(12, 21, 18, 15, 0.4), 1)
    p.dots([(14, 20), (16, 16)], BONE, 1)                    # hairline cracks
    for x, y in ((27, 16), (29, 2), (24, 1)):
        p.extra[(x, y)] = BONE[3]
    return p


def crypt_shard():
    p = Pic()
    for pts, lift in (([(5, 30), (3, 18), (8, 13), (11, 22)], -1), ([(21, 30), (22, 17), (27, 12), (29, 21), (26, 30)], -1)):
        p.part(poly(pts), NECRO, form="plate", bevel=1.5, lift=lift)
    main = poly([(8, 30), (9, 13), (15, 1.5), (21, 9), (23, 30)])
    p.part(main, NECRO, form="flat")
    p.paint(inter(main, poly([(8, 30), (9, 13), (15, 1.5), (14.5, 30)])), NECRO, 1)   # the lit face
    p.paint(inter(main, poly([(14.5, 30), (15, 1.5), (21, 9), (23, 30)])), NECRO, -1)
    p.line(15, 2, 14, 29, NECRO, 4)
    p.line(9, 13, 14, 16, NECRO, 4, only=True), p.line(21, 9, 15, 14, NECRO, 1, only=True)
    for y in range(8, 27, 3):                                # the soul caught inside
        p.put(17 + (y // 3) % 2, y, SPIRIT, 4 if y < 20 else 3)
        p.put(18 - (y // 3) % 2, y + 1, SPIRIT, 2)
    p.put(12, 9, PEARL, 4), p.put(11, 11, PEARL, 4)
    p.spark(27, 4, SPIRIT, big=True), p.spark(3, 8, NECRO)
    return p


def wither_essence():
    p = Pic()
    flame = poly([(16, 1), (20, 7), (19, 11), (24, 8), (27, 16), (25, 25), (19, 30.5), (12, 30.5), (6, 25), (5, 17), (9, 11), (11, 14), (12, 6)])
    p.part(flame, WITHER, form="round", bevel=5)
    inner = poly([(16, 9), (19, 15), (21, 14), (22, 21), (18.5, 27), (13, 27), (10, 21), (11.5, 16), (13, 18)])
    p.part(inner, RIFT, form="round", bevel=4, seam="line", lift=-1)
    p.fill(poly([(16, 16), (18.5, 21), (16, 25.5), (13.5, 21)]), RIFT, 4)
    p.fill(both(rect(13, 20, 14, 21)), SHADE, 0)             # two hollow eyes in the heart of it
    p.put(16, 23, SHADE, 0)
    for x, y in ((4, 8), (27, 5), (29, 11), (2, 13)):        # ash
        p.extra[(x, y)] = WITHER[3]
    p.spark(28, 28, RIFT), p.spark(3, 28, RIFT)
    return p


def spirit_dust():
    p = Pic()
    pile = sub(ellipse(16, 30, 14.5, 9.5), rect(0, 30, 31, 31))
    p.part(pile, NECRO, form="round", bevel=6)
    for x, y in ((9, 26), (14, 23), (20, 25), (24, 28), (17, 28), (6, 29), (12, 28)):   # glints in the dust
        p.put(x, y, SPIRIT, 4 if (x + y) % 2 else 3)
    wisp = path([(16, 21), (13, 17), (17, 13), (14, 9), (17, 5), (16, 2)], 1.2)
    p.part(wisp, SPIRIT, form="flat", seam=None, lift=1)
    for x, y in ((16, 21), (17, 13), (17, 5)):
        p.put(x, y, SPIRIT, 4)
    p.part(path([(22, 20), (24, 16), (22, 12)], 0.8), SPIRIT, form="flat", seam=None)
    p.part(path([(9, 21), (7, 17), (9, 14)], 0.8), SPIRIT, form="flat", seam=None)
    p.spark(26, 6, SPIRIT, big=True), p.spark(5, 7, NECRO), p.spark(28, 22, SPIRIT)
    return p


def relic():
    p = Pic()
    loop = ring(16, 8.5, 7, 3.2)
    p.part(loop, GOLD, form="round", bevel=1.8)
    p.part(rect(13, 14, 18, 30), GOLD, form="round", bevel=2)
    p.part(poly([(3, 15.5), (29, 15.5), (29, 21), (3, 21)]), GOLD, form="round", bevel=2)
    p.part(rect(11, 27, 20, 30), GOLD, bevel=1.4)
    p.facet([(16, 14), (20, 18), (16, 22.5), (12, 18)], TEAL, GOLD)
    p.dots([(5, 18), (26, 18), (15, 25), (16, 2)], TEAL, 3)
    p.dots([(8, 17), (23, 17)], GOLD, 0)
    p.spark(27, 5, TEAL, big=True), p.spark(4, 27, GOLD)
    return p


def dark_scroll():
    p = Pic()
    sheet = poly([(7, 6), (25, 6), (25, 26), (7, 26)])
    p.part(sheet, R("#1c1430", "#3a2c5a", "#5c4a84", "#8470ac", "#b4a2d8"), form="flat", grad=0.5)
    for y, x0, x1 in ((10, 10, 21), (13, 10, 18), (19, 10, 22), (22, 10, 16)):   # lines of script
        for x in range(x0, x1 + 1):
            if (x * 7 + y) % 5:
                p.put(x, y, SHADE, 1)
    for cy in (4.5, 27.5):                                   # the rolls
        p.part(rect(4, int(cy - 2.5), 27, int(cy + 1.5)), R("#1c1430", "#3a2c5a", "#5c4a84", "#8470ac", "#b4a2d8"),
               hf=lambda x, y, c=cy: math.sqrt(max(0.0, 6.5 - (y - c) ** 2)), grad=0.2, lift=1)
        for x0 in (2, 28):
            p.part(rect(x0, int(cy - 1.5), x0 + 1, int(cy + 0.5)), GOLD, bevel=1)
    p.part(circle(16, 16.5, 3.4), RUBY, form="round", bevel=2.4)                 # the seal
    p.put(15, 16, SPIRIT, 4), p.put(16, 16, SPIRIT, 3), p.put(16, 17, SPIRIT, 4), p.put(17, 17, SPIRIT, 3)
    p.part(path([(17, 19), (19, 24), (18, 29)], 0.9), RUBY, form="flat", lift=-1)
    p.spark(29, 14, SPIRIT), p.spark(2, 18, SPIRIT)
    return p


def spirit_leaf():
    p = Pic()
    leaf = poly([(27, 2.5), (29, 12), (25, 21), (17, 27), (8, 27.5), (5, 22), (6.5, 13), (13, 6), (21, 2.5)])
    p.part(leaf, LEAF, form="round", bevel=4)
    p.part(path([(7, 26), (3, 30)], 1.0), LEAF, form="flat", lift=-1)
    p.line(7, 25, 26, 5, R("#3c5a40", "#7ca078", "#bcd8ac", "#e6f4d0", "#ffffee"), 3)   # the midrib
    for i in range(4):                                       # side veins
        x, y = 10 + i * 4, 22 - i * 4
        p.line(x, y, x - 1, y - 5, LEAF, 4, only=True), p.line(x, y, x + 6, y + 1, LEAF, 1, only=True)
    p.erase(poly([(27, 14), (31, 13), (31, 19), (26, 18)]))                      # the edge fading into mist
    for x, y in ((27, 15), (29, 17), (28, 20), (25, 23), (22, 26)):
        p.extra[(x, y)] = SPIRIT[3]
    p.spark(4, 6, SPIRIT, big=True), p.spark(28, 28, SPIRIT)
    return p


def healing_flask():
    p = Pic()
    p.part(rect(13, 5, 18, 12), GLASS, hf=_cyl_h(16, 3.5), grad=0.2)
    ball = circle(16, 20, 9.6)
    p.part(ball, GLASS, form="round", bevel=9.6)
    liquid = inter(circle(16, 20, 8.2), rect(0, 16, 31, 31))
    p.part(liquid, R("#5a0a1c", "#a8162c", "#e63a44", "#ff7c78", "#ffd0c8"), form="round", bevel=7, seam=None, grad=0.4)
    p.fill(inter(liquid, rect(0, 16, 31, 16)), R("#5a0a1c", "#a8162c", "#e63a44", "#ff7c78", "#ffd0c8"), 4)
    cross = union(rect(15, 19, 16, 25), rect(13, 21, 18, 22))
    p.fill(cross, PEARL, 4), p.fill(union(rect(16, 23, 16, 25), rect(17, 22, 18, 22)), PEARL, 2)
    p.fill(poly([(9, 14.5), (12, 12.5), (11, 15.5), (9.5, 18)]), PEARL, 4)
    p.part(rect(12, 4, 19, 6), LEATHER, bevel=1)             # the cord round the neck
    p.part(rect(13, 1, 18, 4), WOOD, form="round", bevel=1.5, lift=1)
    p.dots([(13, 18), (20, 24)], PEARL, 3)
    p.spark(28, 8, RUBY), p.spark(4, 28, RUBY)
    return p


DUNGEON = {"bone_fragment": bone_fragment, "crypt_shard": crypt_shard, "wither_essence": wither_essence, "spirit_dust": spirit_dust,
           "relic": relic, "dark_scroll": dark_scroll, "spirit_leaf": spirit_leaf, "healing_flask": healing_flask}


# ================================================================================================ ore materials
def pebble():
    p = Pic()
    rock = poly([(9, 7), (19, 5), (27, 10), (29, 19), (24, 26.5), (12, 27.5), (4, 22), (3.5, 13)])
    p.part(rock, EMBERROCK, form="round", bevel=5)
    p.part(poly([(9, 7), (19, 5), (22, 10), (13, 13), (6, 11.5)]), EMBERROCK, form="flat", lift=1, seam=None, grad=0.6)   # the top facet
    crack = path([(12, 14), (15, 17), (14, 20), (18, 22), (20, 25)], 0.5)
    p.fill(inter(rock, crack), FIRE, 3)
    p.dots([(15, 17), (18, 22)], FIRE, 4)
    p.shift(inter(rock, move(crack, 1, 1)), -1)
    p.dots([(23, 14), (8, 20), (24, 20)], EMBERROCK, 0)      # pits
    return p


def _core(casing, glow, spokes):
    p = Pic()
    octo = [(10.5, 2.5), (21.5, 2.5), (29.5, 10.5), (29.5, 21.5), (21.5, 29.5), (10.5, 29.5), (2.5, 21.5), (2.5, 10.5)]
    p.part(poly(octo), casing, form="plate", bevel=2.5)
    p.part(circle(16, 16, 9.4), casing, form="flat", seam="line", lift=-2)
    p.part(circle(16, 16, 7.6), glow, form="round", bevel=7.6, seam=None, grad=0.5)
    p.fill(circle(15, 15, 3), glow, 4), p.fill(circle(14.5, 14.5, 1.3), PEARL, 4)
    for i in range(spokes):                                  # the cage over the heart
        a = math.pi * i / spokes + 0.39
        p.part(seg(16 + 9 * math.cos(a), 16 + 9 * math.sin(a), 16 - 9 * math.cos(a), 16 - 9 * math.sin(a), 0.9), casing, form="flat", lift=0)
    p.part(circle(16, 16, 2.6), casing, form="round", bevel=2)
    for x, y in ((11, 4), (20, 4), (27, 11), (27, 20), (20, 27), (11, 27), (4, 20), (4, 11)):   # rivets
        p.put(x, y, casing, 4), p.put(x + 1, y + 1, casing, 0)
    return p


def bronze_core():
    p = _core(BRONZE, AMBER, 2)
    p.spark(29, 3, AMBER)
    return p


def ember_core():
    p = _core(DARKSTEEL, FIRE, 3)
    for x, y in ((3, 3), (28, 2), (29, 29)):
        p.extra[(x, y)] = FIRE[3]
        p.extra[(x, y - 1)] = FIRE[2]
    p.spark(2, 28, FIRE)
    return p


def void_lens():
    p = Pic()
    p.part(ring(16, 16, 13.6, 9.6), VOIDG, form="round", bevel=2, lift=1)
    lens = circle(16, 16, 9.8)
    p.part(lens, AMETHYST, form="round", bevel=9.8, seam="line", grad=0.5)
    p.fill(poly([(9, 12), (14, 8), (13, 11), (10, 15)]), PEARL, 4)               # the glint on the glass
    p.fill(inter(lens, ring(17, 17, 5.2, 4.2)), RIFT, 3)
    p.fill(circle(17, 17, 2), SHADE, 0), p.put(16, 16, RIFT, 4)
    p.shift(inter(lens, sub(circle(16, 16, 9.8), circle(15, 15, 9.2))), 1)
    for a in range(4):                                       # gold claws holding the lens
        ang = math.pi / 4 + a * math.pi / 2
        p.part(circle(16 + 11.5 * math.cos(ang), 16 + 11.5 * math.sin(ang), 2.2), GOLD, form="round", bevel=1.6)
    p.spark(29, 3, RIFT, big=True), p.spark(2, 29, AMETHYST)
    return p


def chrono_sand():
    p = Pic()
    pile = sub(ellipse(16, 29, 14.5, 12), rect(0, 29, 31, 31))
    p.part(pile, SAND, form="round", bevel=7)
    for x, y in ((10, 24), (15, 20), (21, 23), (18, 26), (7, 27), (25, 27), (13, 27)):
        p.put(x, y, TEAL if (x + y) % 3 == 0 else SAND, 4)
    p.shift(inter(pile, seg(12, 22, 9, 28, 0.4)), -1), p.shift(inter(pile, seg(20, 21, 24, 28, 0.4)), -1)   # slip lines
    for i, (x, y) in enumerate(((16, 14), (16, 11), (15, 8), (17, 5), (16, 2))):  # grains falling upwards
        p.extra[(x, y)] = SAND[4] if i % 2 == 0 else TEAL[3]
    for x, y in ((11, 13), (21, 12), (9, 8), (23, 7)):
        p.extra[(x, y)] = SAND[3]
    p.spark(26, 3, TEAL, big=True), p.spark(5, 4, SAND)
    return p


def sandglass():
    p = Pic()
    glass = union(poly([(8, 5), (24, 5), (23, 10), (17.5, 15.5), (14.5, 15.5), (9, 10)]),
                  poly([(14.5, 16), (17.5, 16), (23, 21.5), (24, 27), (8, 27), (9, 21.5)]))
    p.part(glass, GLASS, form="round", bevel=3)
    p.fill(poly([(10.5, 8), (21.5, 8), (21, 10), (17, 14), (15, 14), (11, 10)]), SAND, 2)       # sand above
    p.fill(rect(11, 8, 21, 8), SAND, 4), p.fill(poly([(11, 9), (14, 9), (15.5, 13.5)]), SAND, 3)
    p.fill(rect(16, 15, 16, 22), SAND, 4)                    # the falling thread
    p.fill(poly([(16, 21), (22.5, 26.5), (9.5, 26.5)]), SAND, 2), p.fill(poly([(16, 21), (14, 26.5), (9.5, 26.5)]), SAND, 3)
    p.dots([(12, 11), (19, 25)], TEAL, 4)
    p.fill(rect(10, 18, 10, 23), GLASS, 4), p.fill(rect(10, 6, 10, 7), GLASS, 4)
    for y0 in (1, 27):                                       # the frame
        p.part(rect(5, y0, 26, y0 + 3), GOLD, form="round", bevel=1.6)
    for x in (5, 25):
        p.part(rect(x, 4, x + 1, 27), GOLD, bevel=1, seam=None)
    p.dots([(16, 2), (16, 29)], TEAL, 3)
    p.spark(29, 15, TEAL), p.spark(2, 14, SAND)
    return p


def thunder_quartz():
    p = Pic()
    def crystal(x0, y0, x1, y1, w, ramp):
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy)
        nx, ny = -dy / ln * w, dx / ln * w
        sx, sy = x1 - dx / ln * w * 1.3, y1 - dy / ln * w * 1.3
        whole = poly([(x0 - nx, y0 - ny), (sx - nx, sy - ny), (x1, y1), (sx + nx, sy + ny), (x0 + nx, y0 + ny)])
        p.part(whole, ramp, form="flat")
        left = poly([(x0 - nx, y0 - ny), (sx - nx, sy - ny), (x1, y1), (sx, sy), (x0, y0)])
        a, b = (left, sub(whole, left)) if -nx - ny < 0 else (sub(whole, left), left)
        p.paint(inter(whole, a), ramp, 1), p.paint(inter(whole, b), ramp, -1)
        p.line(round(x0), round(y0), round(sx), round(sy), ramp, 4, only=True)
    crystal(8, 27, 3.5, 11, 2.8, NAVY)
    crystal(24, 27, 29, 13, 2.8, NAVY)
    crystal(12, 28, 10, 5, 3.4, VOLT)
    crystal(21, 28, 23.5, 8, 3.2, VOLT)
    crystal(16, 29, 16.5, 1.5, 4.2, VOLT)
    p.part(sub(ellipse(16, 30, 13, 4.5), rect(0, 30, 31, 31)), STONE, form="round", bevel=2.5, lift=-1)
    bolt = [(18, 6), (15, 12), (18, 13), (14, 20), (17, 21), (15, 26)]
    for a, b in zip(bolt, bolt[1:]):
        p.line(a[0], a[1], b[0], b[1], R("#6a5204", "#b8920c", "#f0d21c", "#fff26a", "#ffffd0"), 4, only=True)
    for x, y in ((27, 6), (5, 5), (30, 9)):
        p.extra[(x, y)] = (255, 242, 106)
    p.spark(27, 3, VOLT, big=True), p.spark(3, 3, VOLT)
    return p


def _block(ramp, emblem):
    """An ore block as an isometric cube: a lit top, a mid left face, a dark right face, an emblem on the two fronts."""
    p = Pic()
    top = poly([(16, 1.5), (30, 8.5), (16, 15.5), (2, 8.5)])
    left = poly([(2, 8.5), (16, 15.5), (16, 30.5), (2, 23.5)])
    right = poly([(16, 15.5), (30, 8.5), (30, 23.5), (16, 30.5)])
    p.part(left, ramp, form="flat", seam=None, grad=0.4)
    p.part(right, ramp, form="flat", seam=None, grad=0.4, lift=-1)
    p.part(top, ramp, form="flat", seam=None, grad=0.4, lift=1)
    p.line(2, 9, 15, 15, ramp, 4), p.line(16, 16, 29, 9, ramp, 3), p.line(16, 16, 16, 30, ramp, 3)   # the lit edges
    p.line(3, 23, 15, 29, ramp, 0), p.line(17, 29, 29, 23, ramp, 0)
    emblem(p, top, left, right)
    return p


def _iso_left(u, v):   # a point of the left face from (u, v) in 0..1
    return (2 + 14 * u, 8.5 + 7 * u + 15 * v)


def _iso_right(u, v):
    return (16 + 14 * u, 15.5 - 7 * u + 15 * v)


def _iso_top(u, v):
    return (16 + 14 * u - 14 * v, 1.5 + 7 * u + 7 * v)


def _quad(f, u0, v0, u1, v1):
    return poly([f(u0, v0), f(u1, v0), f(u1, v1), f(u0, v1)])


def bronzite_block():
    def emblem(p, top, left, right):
        for f, d in ((_iso_left, -1), (_iso_right, -1), (_iso_top, -1)):
            p.shift(sub(_quad(f, 0.12, 0.12, 0.88, 0.88), _quad(f, 0.22, 0.22, 0.78, 0.78)), d)   # an inset panel
            for u, v in ((0.08, 0.08), (0.9, 0.08), (0.08, 0.9), (0.9, 0.9)):
                x, y = f(u, v)
                p.put(int(x), int(y), GOLD, 4)
        p.part(circle(9, 19.5, 2.2), GOLD, form="round", bevel=2, seam=None)
    return _block(BRONZE, emblem)


def frostium_block():
    def emblem(p, top, left, right):
        for f in (_iso_left, _iso_right):
            for a, b in (((0.5, 0.15), (0.5, 0.85)), ((0.15, 0.5), (0.85, 0.5)), ((0.25, 0.25), (0.75, 0.75)), ((0.75, 0.25), (0.25, 0.75))):
                x0, y0 = f(*a)
                x1, y1 = f(*b)
                p.line(int(x0), int(y0), int(x1), int(y1), PEARL, 4 if f is _iso_left else 2, only=True)
        p.shift(inter(top, seg(8, 8, 15, 5, 0.5)), 1), p.shift(inter(top, seg(14, 12, 24, 8, 0.5)), -1)   # ice grain
        p.fill(poly([(5, 8.5), (10, 6), (12, 7), (7, 9.5)]), PEARL, 4)
    p = _block(FROST, emblem)
    p.spark(29, 2, FROST)
    return p


def starcore_block():
    def emblem(p, top, left, right):
        for f, tone in ((_iso_left, 4), (_iso_right, 3)):
            cx, cy = f(0.5, 0.5)
            p.fill(inter(union(left, right), star(cx, cy, 5.5, 1.8, 4)), GOLD, tone)
            p.put(int(cx), int(cy), PEARL, 4)
        for u, v in ((0.3, 0.3), (0.7, 0.4), (0.45, 0.75), (0.75, 0.75)):
            x, y = _iso_top(u, v)
            p.put(int(x), int(y), GOLD, 4)
        for u, v in ((0.15, 0.2), (0.85, 0.8)):
            x, y = _iso_left(u, v)
            p.put(int(x), int(y), PEARL, 3)
    p = _block(NAVY, emblem)
    p.spark(29, 2, GOLD)
    return p


def sunstone_block():
    def emblem(p, top, left, right):
        for f, tone in ((_iso_left, 4), (_iso_right, 3)):
            cx, cy = f(0.5, 0.5)
            p.fill(inter(union(left, right), star(cx, cy, 6, 3, 8)), SUN, tone)
            p.fill(circle(cx, cy, 2.4), PEARL, 4 if tone == 4 else 3)
        cx, cy = _iso_top(0.5, 0.5)
        p.fill(inter(top, ellipse(cx, cy, 5, 2.5)), SUN, 4)
    p = _block(R("#6a2206", "#b04a0c", "#e47a18", "#ffae3c", "#ffe090"), emblem)
    p.spark(29, 2, SUN)
    return p


def voidglass_block():
    def emblem(p, top, left, right):
        for pts in ([(4, 12), (9, 17), (7, 22), (12, 26)], [(9, 17), (14, 18)], [(19, 27), (22, 21), (20, 17), (26, 13)], [(22, 21), (28, 20)],
                    [(9, 8), (15, 9), (19, 7), (24, 9)]):
            for a, b in zip(pts, pts[1:]):
                p.line(a[0], a[1], b[0], b[1], RIFT, 3, only=True)
        p.dots([(9, 17), (22, 21), (15, 9)], RIFT, 4)
        p.fill(poly([(4, 11), (7, 12.5), (7, 16), (4, 14.5)]), VOIDG, 4)         # the glass sheen
    p = _block(VOIDG, emblem)
    p.spark(29, 2, RIFT)
    return p


def rot_resin():
    """Rot Resin: a fat drop of marsh amber oozing from a chip of rotten bark, a bubble of rot caught inside."""
    p = Pic()
    resin = R("#3c2806", "#7a560c", "#be9018", "#e8c648", "#fff4a4")
    rot = R("#2c0a2c", "#641c5c", "#a8409a", "#dc7cd0", "#ffd0f8")
    bark = poly([(3, 3), (20, 1.5), (29, 5), (27, 11), (9, 12.5), (2, 9)])
    p.part(bark, BARK, form="round", bevel=2.5)
    for x in (8, 14, 21):                                    # the grain, and moss in it
        p.shift(inter(bark, seg(x, 2, x + 1.5, 12, 0.4)), -1)
    p.paint(inter(bark, circle(24, 5, 2.6)), OLIVE, 1), p.paint(inter(bark, circle(5, 8, 2)), OLIVE, 1)
    drop = union(circle(15, 21.5, 8.2), poly([(11, 9), (20, 9), (22.5, 18), (7.5, 18)]))
    p.part(drop, resin, form="round", bevel=7)
    p.part(path([(23, 10), (24.5, 14), (24, 17)], 1.3), resin, form="round", bevel=1.3)   # a second, thin run
    p.fill(circle(24, 18, 1.6), resin, 3)
    p.part(circle(16.5, 22.5, 3.2), rot, form="round", bevel=3.2, seam=None, grad=0.5, lift=-1)
    p.dots([(13, 25), (19, 18)], rot, 2)
    p.fill(poly([(10, 15), (13, 12.5), (12.5, 16), (10.5, 20)]), resin, 4)       # the glint
    p.shift(inter(drop, sub(circle(15, 21.5, 8.2), circle(14, 20.5, 7.6))), 1)   # light through the lower rim
    for x, y in ((28, 20), (4, 24), (27, 27)):
        p.extra[(x, y)] = rot[3]
    return p


ORE = {"pebble": pebble, "bronze_core": bronze_core, "ember_core": ember_core, "void_lens": void_lens, "chrono_sand": chrono_sand,
       "sandglass": sandglass, "thunder_quartz": thunder_quartz, "bronzite_block": bronzite_block, "frostium_block": frostium_block,
       "starcore_block": starcore_block, "sunstone_block": sunstone_block, "voidglass_block": voidglass_block}


def all_icons():
    out = {name: fn() for name, fn in MISC.items()}
    out.update({f"dungeon__{name}": fn() for name, fn in DUNGEON.items()})
    out.update({f"ore__{name}": fn() for name, fn in ORE.items()})
    return out
