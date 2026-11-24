"""The 36 accessory icons (32 px, painted with paint32): one trinket per stat, four tiers.

speed a wing, strength a horned signet ring, crit a fang pendant, vitality a heart locket, guard a shield brooch, mind an
eye amulet, harvest a sickle charm, prospect a pickaxe charm, fortune a clover coin. The tier shows in the metal (copper,
silver, gold, then gold with a second ornament), the cut of the gem and the glow. items_icons.py writes them
(ids acc_<stat>_<tier>).
"""
import math

from paint32 import (AMBER, AMETHYST, AQUA, BONE, COPPER, EMERALD, GOLD, IRON, LEATHER, PEARL, ROPE, RUBY, SAPPHIRE, SILVER,
                     WOOD, Pic, R, both, circle, ellipse, inter, mirror, move, path, poly, rect, ring, seg, star, sub, sym,
                     taper, union)

TOPAZ = R("#6a5204", "#b8920c", "#f0d21c", "#fff26a", "#ffffd0")
ROSE = R("#56081e", "#a8183a", "#ec3c5c", "#ff8090", "#ffd2d4")
INDIGO = R("#140f4a", "#2c2a96", "#5a54e0", "#928cff", "#dcd8ff")
SHADE = R("#08060c", "#120e18", "#1e1826", "#2e2638", "#44384e")
ENAMEL = R("#0e1c44", "#1c3a80", "#3460bc", "#6290e4", "#a8c8ff")


def metal(t):
    return (COPPER, SILVER, GOLD, GOLD)[t - 1]


def bail(p, x, y, m, r=2.7):
    """The little ring a pendant hangs from."""
    p.part(ring(x, y, r, r - 1.6), m, bevel=1)


def cord(p, pts, ramp=LEATHER):
    p.part(path(pts, 0.7), ramp, form="flat")


def leaf_shape(x0, y0, x1, y1, w, at=0.55):
    dx, dy = x1 - x0, y1 - y0
    ln = (dx * dx + dy * dy) ** 0.5
    nx, ny = -dy / ln * w, dx / ln * w
    mx, my = x0 + dx * at, y0 + dy * at
    qx, qy = x0 + dx * 0.2, y0 + dy * 0.2
    return poly([(x0, y0), (qx + nx * 0.7, qy + ny * 0.7), (mx + nx, my + ny), (x1, y1), (mx - nx, my - ny),
                 (qx - nx * 0.7, qy - ny * 0.7)])


def plume(p, x0, y0, x1, y1, w, ramp, tip=None):
    shape = leaf_shape(x0, y0, x1, y1, w)
    p.part(shape, ramp, form="round", bevel=2)
    if tip:
        p.paint(inter(shape, circle(x1, y1, w * 1.5)), tip)
    p.line(round(x0), round(y0), round(x0 + (x1 - x0) * 0.85), round(y0 + (y1 - y0) * 0.85), ramp, 4, only=True)


# ---------------------------------------------------------------------------------------------- speed: a wing
def speed(t):
    p = Pic()
    m = metal(t)
    if t == 1:                                               # one gull feather on a cord
        cord(p, [(9, 25), (5, 21), (4, 15), (7, 11)])
        plume(p, 8, 26, 27, 3, 4.6, PEARL, IRON)
        for i in range(4):                                   # barbs
            p.shift(seg(14 + i * 3, 21 - i * 4, 17 + i * 3, 21 - i * 4, 0.5), -1)
        p.part(circle(8.5, 25.5, 2.2), COPPER, form="round", bevel=2)
        return p
    hubs = [(8, 23)] if t < 4 else [(16, 23)]
    fans = {2: [(27, 4, 3.4), (29.5, 12, 3.2), (27, 20, 2.8)], 3: [(24, 2, 3.4), (29, 7, 3.4), (30, 14, 3.2), (27, 21, 2.8)],
            4: [(26, 2, 3.0), (30, 8, 3.0), (30, 15, 2.8), (26, 21, 2.4)]}[t]
    hx_, hy = hubs[0]
    for sign in ((1,) if t < 4 else (1, -1)):
        for i, (tx, ty, w) in enumerate(reversed(fans)):
            x1 = tx if sign == 1 else 32 - tx
            plume(p, hx_ + sign * 1.5, hy - 1, x1, ty, w, PEARL, AQUA if t >= 3 else None)
    p.part(circle(hx_, hy, 4.2 if t < 4 else 4.8), m, form="round", bevel=1.6)
    if t == 2:
        p.part(circle(hx_, hy, 2.2), SILVER, form="round", bevel=2, lift=1)
    else:
        p.gem(hx_, hy, 2.6 if t == 3 else 3.0, TOPAZ)
    if t == 4:
        p.part(poly([(13.5, 27), (18.5, 27), (16, 31.5)]), GOLD, bevel=1.2)   # a drop under the hub
        p.spark(16, 5, TOPAZ, big=True), p.spark(3, 28, TOPAZ), p.spark(29, 28, TOPAZ)
    elif t == 3:
        p.spark(4, 6, TOPAZ, big=True)
    return p


# ---------------------------------------------------------------------------------------------- strength: a signet ring
def strength(t):
    p = Pic()
    m = (IRON, SILVER, GOLD, GOLD)[t - 1]
    band = ring(16, 20, 10.2, 6.4)
    p.part(band, m, form="round", bevel=2)
    p.shift(inter(band, rect(0, 26, 31, 31)), -1)
    if t >= 3:                                               # horns either side of the stone
        horn = poly([(8.5, 13), (5, 9), (3.5, 3) if t == 3 else (2.5, 1), (8, 6.5), (12, 11)])
        p.part(both(horn), BONE, form="round", bevel=1.6)
        p.shift(both(seg(5, 8, 7, 7, 0.5)), -1)
    if t == 4:
        horn2 = poly([(10, 9), (9, 4), (11, 0.5), (12.5, 5), (13.5, 9)])
        p.part(both(horn2), BONE, form="round", bevel=1.3)
    seat = poly([(9, 9), (23, 9), (24.5, 15.5), (16, 18.5), (7.5, 15.5)])
    p.part(seat, m, bevel=1.6)
    if t == 1:                                               # a rough garnet
        p.part(poly([(11, 7), (16, 5), (21, 7.5), (20, 13), (15, 14.5), (11.5, 12)]), RUBY, bevel=1.5, lift=-1)
        p.dots([(13, 8), (17, 11)], RUBY, 3)
        p.dots([(6, 20), (25, 22)], IRON, 0)                 # nicks
    else:
        pts = [(16, 3.5), (21.5, 6.5), (21.5, 12), (16, 15.5), (10.5, 12), (10.5, 6.5)] if t >= 3 else \
              [(11.5, 6), (20.5, 6), (20.5, 13.5), (11.5, 13.5)]
        p.facet(pts, RUBY)
        p.shift(seg(16, 4, 16, 15, 0.5), 1 if t >= 3 else 0)
        p.shift(inter(poly(pts), rect(17, 0, 31, 31)), -1)
        p.put(13, 8, RUBY, 4), p.put(12, 9, RUBY, 4)
        for x in (9, 22):                                    # claws
            p.put(x, 9, m, 4), p.put(x, 14, m, 3)
    if t >= 3:
        p.spark(27, 16, RUBY, big=(t == 4))
    if t == 4:
        p.dots([(8, 23), (23, 23)], RUBY, 3), p.dots([(11, 28), (20, 28), (16, 29)], GOLD, 4)
        p.fill(circle(16, 9.5, 1.3), RUBY, 4)
        p.spark(16, 1, RUBY), p.spark(3, 28, GOLD)
    return p


# ---------------------------------------------------------------------------------------------- crit: a fang
def _fang(p, x, y, lean, ramp=BONE, length=17):
    """A curved fang hanging from (x, y), its tip swept to the side lean (+1 right)."""
    pts = [(x - 3.5, y), (x + 3.5, y), (x + 3.2 + lean * 1.5, y + length * 0.55), (x + lean * 5.5, y + length),
           (x - 2.5 + lean * 1.0, y + length * 0.6)]
    p.part(poly(pts), ramp, form="round", bevel=2.4)
    p.shift(seg(x + 1.5 + lean, y + 3, x + 1 + lean * 3.5, y + length * 0.8, 0.5), -1)


def crit(t):
    p = Pic()
    m = metal(t)
    if t < 4:
        cord(p, [(16, 9), (11, 5), (9, 1)]), cord(p, [(16, 9), (21, 5), (23, 1)])
        _fang(p, 16, 11, -1, length=19)
        p.part(rect(11, 7, 20, 12), m if t > 1 else LEATHER, form="round", bevel=2)   # the cap
        if t == 1:
            p.shift(rect(11, 9, 20, 9), -1)
            p.dots([(13, 20), (14, 22)], BONE, 1)            # a crack
        else:
            p.dots([(12, 12), (15, 12), (18, 12)], m, 1)
            if t == 2:
                p.shift(rect(11, 9, 20, 9), -2)
            else:
                p.gem(15.5, 9.5, 1.8, AMETHYST)
                p.paint(inter(circle(11, 29, 3), rect(0, 26, 31, 31)), GOLD)   # a gilded tip
                p.spark(26, 20, AMETHYST, big=True)
    else:                                                    # two crossed fangs under a jewelled clasp
        cord(p, [(16, 8), (11, 4), (9, 0)], GOLD), cord(p, [(16, 8), (21, 4), (23, 0)], GOLD)
        _fang(p, 12, 11, -1, length=19)
        _fang(p, 20, 11, 1, length=19)
        p.part(poly([(7, 7), (25, 7), (26, 12), (16, 15.5), (6, 12)]), GOLD, bevel=1.6)
        p.facet([(16, 6), (19.5, 9.5), (16, 13.5), (12.5, 9.5)], AMETHYST)
        p.dots([(9, 10), (23, 10)], AMETHYST, 3)
        p.paint(inter(circle(6.5, 29, 3), rect(0, 26, 31, 31)), GOLD), p.paint(inter(circle(25.5, 29, 3), rect(0, 26, 31, 31)), GOLD)
        p.spark(16, 22, AMETHYST, big=True), p.spark(3, 4, AMETHYST), p.spark(29, 4, AMETHYST)
    return p


# ---------------------------------------------------------------------------------------------- vitality: a heart locket
def _heart(cx, cy, s):
    return union(circle(cx - s * 0.5, cy - s * 0.28, s * 0.56), circle(cx + s * 0.5, cy - s * 0.28, s * 0.56),
                 poly([(cx - s * 1.02, cy - s * 0.08), (cx + s * 1.02, cy - s * 0.08), (cx, cy + s * 1.08)]))


def vitality(t):
    p = Pic()
    m = metal(t)
    if t == 4:                                               # little wings behind the heart
        for i, (tx, ty) in enumerate(((1, 6), (0.5, 12), (2.5, 18))):
            p.part(both(leaf_shape(8, 16, tx, ty, 2.4)), PEARL, form="round", bevel=1.6)
    bail(p, 16, 4.2, m)
    heart = _heart(16, 16.5, 10.2)
    p.part(heart, m, form="round", bevel=3)
    if t == 1:                                               # beaten copper, a stitched seam
        for y in range(11, 26, 3):
            p.put(16, y, COPPER, 0), p.put(17, y + 1, COPPER, 4)
        p.dots([(9, 12), (22, 18)], COPPER, 0)
    else:
        inner = _heart(16, 16.5, 6.6 if t < 4 else 7.2)
        p.part(inner, ROSE, form="round", bevel=3, seam="line", grad=0.5, lift=0 if t == 2 else 0)
        p.put(12, 13, ROSE, 4), p.put(13, 12, ROSE, 4), p.put(11, 14, ROSE, 4)
        if t >= 3:
            p.shift(seg(16, 12, 16, 23, 0.5), 1)             # the cut of the gem
            p.shift(inter(inner, rect(17, 0, 31, 31)), -1)
            for x, y in ((7, 13), (25, 13), (16, 26), (10, 21), (22, 21)):
                p.put(x, y, GOLD, 4)
        if t == 3:
            p.spark(27, 5, ROSE, big=True)
        if t == 4:
            p.part(poly([(11.5, 6.5), (13.5, 3), (16, 5.5), (18.5, 3), (20.5, 6.5), (20, 8.5), (12, 8.5)]), GOLD, bevel=1)   # a crown
            p.dots([(13, 4), (18, 4)], ROSE, 3)
            p.spark(16, 30, ROSE), p.spark(28, 26, ROSE, big=True), p.spark(4, 27, GOLD)
    return p


# ---------------------------------------------------------------------------------------------- guard: a shield brooch
def guard(t):
    p = Pic()
    m = (IRON, SILVER, GOLD, GOLD)[t - 1]
    if t == 4:                                               # laurels either side
        for i in range(4):
            y = 8 + i * 5
            p.part(both(leaf_shape(4.5, y + 4, 1.5 + i * 0.6, y - 1.5, 1.9)), GOLD, form="round", bevel=1.4)
    shield = sym([(16, 2.5), (6, 4.5), (6, 16), (9.5, 24), (16, 30)]) if t < 4 else sym([(16, 2.5), (7, 4.5), (7, 16), (10, 24), (16, 30)])
    p.part(shield, m, form="round", bevel=2.2)
    face = sym([(16, 5), (8.5, 6.5), (8.5, 15.5), (11.3, 22.5), (16, 27)]) if t < 4 else sym([(16, 5), (9.5, 6.5), (9.5, 15.5), (12, 22.5), (16, 27)])
    p.part(face, WOOD if t == 1 else ENAMEL, bevel=1.5, seam="line")
    if t == 1:
        for x in (12, 16, 20):                               # planks
            p.shift(inter(face, rect(x, 0, x, 31)), -1)
        p.part(circle(16, 14.5, 3), IRON, form="round", bevel=2.5)
        p.dots([(10, 8), (22, 8), (16, 24)], IRON, 4)
        p.dots([(19, 20), (20, 19)], WOOD, 0)                # a notch
    else:
        chevron = inter(face, poly([(8, 10), (16, 17), (24, 10), (24, 14.5), (16, 21.5), (8, 14.5)]))
        p.paint(chevron, m)
        if t == 2:
            p.part(circle(16, 10.5, 2.2), SILVER, form="round", bevel=2, lift=1)
        else:
            p.facet([(16, 6.5), (19.5, 10.5), (16, 14.5), (12.5, 10.5)], SAPPHIRE, GOLD)
            p.dots([(10, 7), (22, 7), (16, 26)], GOLD, 4)
            p.spark(27, 26, SAPPHIRE, big=(t == 3))
        if t == 4:
            p.part(poly([(11, 3.5), (12.5, 0.5), (16, 3), (19.5, 0.5), (21, 3.5), (20.5, 5), (11.5, 5)]), GOLD, bevel=1)
            p.put(16, 1, SAPPHIRE, 3)
            p.spark(16, 23, SAPPHIRE), p.spark(3, 29, GOLD), p.spark(29, 2, SAPPHIRE, big=True)
    return p


# ---------------------------------------------------------------------------------------------- mind: an eye amulet
def mind(t):
    p = Pic()
    m = metal(t)
    if t == 4:                                               # rays around the eye
        for i in range(8):
            a = math.pi * i / 4 + math.pi / 8
            x0, y0 = 16 + 9 * math.cos(a), 17 + 6 * math.sin(a)
            x1, y1 = 16 + 15.5 * math.cos(a), 17 + 13 * math.sin(a)
            p.part(taper(x0, y0, 1.8, x1, y1, 0.4), GOLD, form="flat")
    bail(p, 16, 6.4, m)
    frame = inter(circle(16, 23.2, 13.8), circle(16, 10.8, 13.8))
    p.part(frame, m, form="round", bevel=2)
    white = inter(circle(16, 22, 11.2), circle(16, 12, 11.2))
    if t == 1:                                               # a glass bead painted with an eye
        p.part(white, R("#1c3a5a", "#356a92", "#62a0c4", "#a6d4ea", "#e8fafc"), form="round", bevel=3, seam="line")
        p.fill(circle(16, 17, 3.2), SHADE, 1)
        p.put(14, 15, PEARL, 4)
        p.dots([(6, 17), (25, 17)], COPPER, 0)
    else:
        p.part(white, PEARL, form="round", bevel=3, seam="line")
        iris = INDIGO if t < 4 else AMETHYST
        p.part(circle(16, 17, 4.8 if t < 4 else 5.2), iris, form="round", bevel=4, seam="line", grad=0.5)
        p.fill(circle(16, 17, 2.0), SHADE, 0)
        p.put(14, 15, iris, 4), p.put(13, 15, PEARL, 4)
        if t >= 3:
            for x, y in ((5, 17), (26, 17)):
                p.put(x, y, GOLD, 4)
            p.fill(ring(16, 17, 3.6, 2.6), iris, 3)
            p.put(18, 19, iris, 4)
        if t == 3:
            p.spark(27, 7, INDIGO, big=True)
        if t == 4:
            p.spark(3, 3, AMETHYST, big=True), p.spark(29, 30, AMETHYST)
    return p


# ---------------------------------------------------------------------------------------------- harvest: a sickle charm
WHEAT = R("#6a4612", "#a67626", "#d6a840", "#f0cf68", "#fff0a8")


def _ear(p, x, y, dx, dy, n=5):
    """A wheat ear: n pairs of grains climbing from (x, y) by the unit step (dx, dy)."""
    for i in range(n):
        cx, cy = x + dx * i * 2.2, y + dy * i * 2.2
        p.part(circle(cx - dy * 1.5, cy + dx * 1.5, 1.7), WHEAT, form="round", bevel=1.6, seam=None, lift=-1)
        p.part(circle(cx + dy * 1.5, cy - dx * 1.5, 1.7), WHEAT, form="round", bevel=1.6, seam=None)
    p.part(circle(x + dx * n * 2.2, y + dy * n * 2.2, 1.4), WHEAT, form="flat", seam=None, lift=1)


def harvest(t):
    p = Pic()
    m = (IRON, SILVER, GOLD, GOLD)[t - 1]
    if t >= 3:                                               # a wheat ear behind the blade
        p.line(9, 27, 17, 15, WHEAT, 1)
        _ear(p, 17, 15, 0.62, -0.78, 5)
    if t == 4:
        p.line(4, 25, 5, 15, WHEAT, 1)
        _ear(p, 5, 15, 0.1, -1.0, 4)
    blade = sub(circle(15, 13.5, 11.5), circle(17.8, 16.6, 10.4), rect(0, 22, 13, 31))
    p.part(blade, m, form="round", bevel=1.8)
    p.shift(inter(blade, ring(15, 13.5, 10.2, 9.4)), 1)      # the ground edge
    grip = seg(19.5, 23, 26, 29, 1.9)
    p.part(grip, WOOD, form="round", bevel=1.8)
    for i in range(3):
        p.shift(seg(20.5 + i * 2, 25.5 + i * 2 - 2.6, 22.2 + i * 2 - 2.6, 25.5 + i * 2, 0.4), -1)
    p.part(circle(18.5, 22, 2.4), m if t > 1 else LEATHER, form="round", bevel=2)   # the ferrule
    if t == 1:
        p.dots([(6, 9), (9, 5)], IRON, 0)                    # nicks in the blade
    if t == 2:
        p.part(circle(27, 29.5, 1.8), SILVER, form="round", bevel=1.5)
    if t >= 3:
        p.gem(18.5, 22, 1.6, AMBER)
        p.part(circle(27, 29.5, 1.9), GOLD, form="round", bevel=1.5)
        p.spark(3, 3, AMBER, big=(t == 4))
    if t == 4:
        p.dots([(5, 13), (8, 7), (13, 3), (19, 3)], AMBER, 4)   # runes along the blade
        p.spark(29, 17, AMBER, big=True), p.spark(13, 30, GOLD)
    return p


# ---------------------------------------------------------------------------------------------- prospect: a pickaxe charm
def prospect(t):
    p = Pic()
    m = (IRON, SILVER, GOLD, GOLD)[t - 1]
    bail(p, 26.5, 4.5, m)
    haft = seg(24, 7.5, 6, 27, 1.7)
    p.part(haft, WOOD, form="round", bevel=1.7)
    head = sub(circle(21, 22, 19.8), circle(22.4, 24, 17.2), rect(0, 20, 31, 31), rect(28, 0, 31, 31))
    p.part(head, m, form="round", bevel=1.8)
    p.part(rect(19, 6, 24, 11), m, bevel=1.4, lift=1)        # the eye of the head
    if t == 1:
        p.dots([(5, 14), (12, 7)], IRON, 0)
        p.shift(seg(9, 22, 11, 24, 0.5), -1), p.shift(seg(13, 18, 15, 20, 0.5), -1)   # cord wraps
    else:
        p.paint(inter(haft, circle(7, 26, 3)), m)            # the butt cap
        for i in range(3):
            p.paint(inter(haft, seg(11 + i * 2, 19 - i * 2.2, 13.5 + i * 2, 21.5 - i * 2.2, 0.45)), LEATHER, -1)
        if t == 2:
            p.put(21, 8, SILVER, 4), p.put(22, 9, SILVER, 1)
        else:
            p.gem(21.5, 8.5, 1.7, AQUA)
            p.dots([(3, 17), (4, 16)], AQUA, 4), p.dots([(15, 3)], AQUA, 4)   # tips that bite
            p.spark(27, 22, AQUA, big=True)
    if t == 4:                                               # a nugget cluster at the foot
        for cx, cy, r, ramp in ((22, 24, 3.2, AQUA), (26, 27, 2.6, GOLD), (19.5, 28, 2.4, AQUA)):
            p.facet([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], ramp)
        p.spark(3, 3, AQUA, big=True), p.spark(12, 30, GOLD)
    return p


# ---------------------------------------------------------------------------------------------- fortune: a clover coin
def fortune(t):
    p = Pic()
    m = metal(t)
    cy = 17.5
    if t >= 2:                                               # a red tassel through the coin
        cord(p, [(16, 6), (16, 1)], RUBY)
        p.part(ring(16, 5.2, 2.7, 1.1), m, bevel=1)
    coin = circle(16, cy, 11.4 if t < 4 else 11.8)
    p.part(coin, m, form="round", bevel=2)
    p.part(circle(16, cy, 8.8), m, bevel=1.2, seam="line", lift=-1 if t == 1 else 0)
    leaf_c = (COPPER, SILVER, EMERALD, EMERALD)[t - 1]
    for dx, dy in ((-2.9, -2.9), (2.9, -2.9), (-2.9, 2.9), (2.9, 2.9)):
        p.part(circle(16 + dx, cy + dy, 2.9), leaf_c, form="round", bevel=2.6, seam="shadow" if t > 2 else None, lift=1 if t < 3 else 0)
        if t >= 3:
            p.put(int(16 + dx - 1), int(cy + dy - 1), EMERALD, 4)
    p.put(15, 17, leaf_c, 1), p.put(16, 17, leaf_c, 1)
    p.line(17, 20, 20, 24, leaf_c, 1 if t > 2 else 3)        # the stem
    if t == 1:
        p.dots([(7, 12), (24, 22), (9, 24)], COPPER, 0)      # worn rim
    else:
        n = 8 if t < 4 else 12
        for i in range(n):                                   # rim studs
            a = 2 * math.pi * i / n + 0.39
            p.put(int(16 + 10.2 * math.cos(a)), int(cy + 10.2 * math.sin(a)), m if t < 4 else EMERALD, 4 if t < 4 else 3)
    if t == 3:
        p.spark(28, 5, EMERALD, big=True)
    if t == 4:
        for sx in (12, 20):                                  # the tassel ends
            p.part(seg(sx, 29, sx - (16 - sx) * 0.2, 31, 0.9), RUBY, form="flat")
        p.spark(3, 5, EMERALD, big=True), p.spark(29, 6, GOLD), p.spark(29, 29, EMERALD)
    return p


STATS = {"speed": speed, "strength": strength, "crit": crit, "vitality": vitality, "guard": guard, "mind": mind,
         "harvest": harvest, "prospect": prospect, "fortune": fortune}


def all_icons():
    return {f"acc_{stat}_{t}": fn(t) for stat, fn in STATS.items() for t in (1, 2, 3, 4)}
