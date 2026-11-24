"""The eight pet icons (32 px, painted with paint32): the creature itself, head or whole body, lit from the top left.
items_icons.py writes them (ids pet_<species>)."""
from paint32 import (AMBER, AMETHYST, BONE, EMERALD, PEARL, RUBY, Pic, R, both, circle, ellipse, inter, mirror, move, path,
                     poly, rect, ring, seg, sub, sym, taper, union)

SHADE = R("#08060c", "#120e18", "#1e1826", "#2e2638", "#44384e")
PINK = R("#5a1c30", "#a04058", "#e07888", "#ffaab0", "#ffdcdc")
WOLF = R("#2a2c3c", "#52586c", "#868ea2", "#bcc4d2", "#eef2f8")
CREAM = R("#5a4a44", "#9a887a", "#d2c4b0", "#f0e8d8", "#ffffff")
ENDER = R("#07050c", "#130e1e", "#221a32", "#352a4a", "#514470")
RIFT = R("#4a0a50", "#961496", "#e040d8", "#ff8cf4", "#ffe2ff")
FIRE = R("#6a1208", "#c0300c", "#f4701c", "#ffb43c", "#fff2a0")
EMBERRED = R("#3c0810", "#84141a", "#cc2c1c", "#f4602c", "#ffa860")
TIGER = R("#5a2206", "#a84a0c", "#ec841c", "#ffb048", "#ffe0a0")
STRIPE = R("#0c0808", "#1c1412", "#2e2420", "#443630", "#5c4c44")
ELEPH = R("#2a2c36", "#50545e", "#7e848e", "#aab0b8", "#d6dce0")
SHELL = R("#1c2a10", "#3a5418", "#5e8224", "#8cb03c", "#c6e070")
TURTLE = R("#0c3a2c", "#167254", "#2ca87a", "#62d8a0", "#b6f8d0")
DRAGON = R("#0a0714", "#1a122c", "#2e2248", "#483868", "#6c5a94")
DRAGONBELLY = R("#1c1430", "#3a2a5a", "#5c4688", "#8468b4", "#b49ae0")


def eye(p, x, y, iris, w=3, glow=False):
    """An eye w x 2 at (x, y): a dark socket line above, the iris, a pupil and a glint."""
    for i in range(w):
        p.put(x + i, y, iris, 3 if glow else 2)
        p.put(x + i, y + 1, iris, 2 if glow else 1)
    p.put(x + w // 2, y, SHADE, 0)
    p.put(x + w // 2, y + 1, SHADE, 0)
    p.put(x, y, iris, 4)


def wolf():
    p = Pic()
    ear = poly([(5, 13), (4.5, 1), (12.5, 8)])
    p.part(both(ear), WOLF, form="round", bevel=2)
    p.fill(both(poly([(6.5, 10), (6.3, 4.5), (10, 8.5)])), PINK, 1)
    head = sym([(16, 6.5), (10, 7.5), (5, 12), (2.5, 19), (5.5, 21.5), (8, 25), (12, 27), (13, 30), (16, 31)])
    p.part(head, WOLF, form="round", bevel=5)
    p.part(both(poly([(2.5, 19), (5.5, 16), (9, 22), (8, 25), (5.5, 21.5)])), WOLF, form="round", bevel=1.5, lift=1)   # cheek ruff
    brow = sym([(16, 7), (12.5, 8), (13, 14), (16, 18)])
    p.paint(brow, WOLF, -1)                                  # the dark blaze
    muzzle = sym([(16, 16), (12.5, 18), (11, 24), (12.5, 29.5), (16, 30.5)])
    p.part(muzzle, CREAM, form="round", bevel=3, seam=None)
    p.fill(sym([(16, 21.5), (13.3, 22), (14, 25), (16, 26)]), SHADE, 1)          # the nose
    p.put(14, 22, SHADE, 4)
    p.line(15, 26, 15, 28, SHADE, 1), p.line(16, 26, 16, 28, SHADE, 1)
    p.line(13, 28, 14, 29, SHADE, 1), p.line(18, 28, 17, 29, SHADE, 1)
    for x in (8, 21):
        p.line(x - 1, 14, x + 3, 14, SHADE, 1)
        eye(p, x, 15, AMBER)
    p.dots([(7, 13), (24, 13)], WOLF, 4)
    return p


def rabbit():
    p = Pic()
    for sx, lean in ((10.5, -1), (21.5, 1)):
        ear = poly([(sx - 3, 14), (sx - 3.5 + lean, 3), (sx + lean * 1.5, 0.5), (sx + 3.5 + lean, 3), (sx + 3, 14)])
        p.part(ear, CREAM, form="round", bevel=2.5)
        p.fill(poly([(sx - 1.2, 12), (sx - 1.4 + lean, 4.5), (sx + lean * 1.3, 3), (sx + 1.6 + lean, 4.5), (sx + 1.4, 12)]), PINK, 2)
        p.shift(rect(int(sx + 1), 4, int(sx + 1), 12), -1)
    head = union(ellipse(16, 20, 11.5, 9.5), ellipse(16, 24, 9.5, 6.5))
    p.part(head, CREAM, form="round", bevel=6)
    p.part(both(ellipse(12.2, 24.5, 4, 3.2)), CREAM, form="round", bevel=3, seam=None, lift=1)   # cheeks
    p.fill(poly([(14.5, 21.5), (17.5, 21.5), (16, 23.6)]), PINK, 2)
    p.put(15, 21, PINK, 3)
    p.line(15, 24, 15, 25, CREAM, 0), p.line(16, 24, 16, 25, CREAM, 0)
    p.fill(rect(15, 26, 16, 28), PEARL, 4), p.put(16, 27, PEARL, 2), p.put(16, 28, PEARL, 2)   # the teeth
    for x in (8, 21):
        p.fill(rect(x, 16, x + 2, 19), SHADE, 1)
        p.put(x, 16, PEARL, 4), p.put(x + 2, 19, AMETHYST, 2)
    p.dots([(5, 24), (4, 22), (27, 24), (28, 22)], CREAM, 1)                     # whisker spots
    p.dots([(11, 22), (20, 22)], PINK, 3)
    return p


def enderman():
    p = Pic()
    p.part(sym([(16, 21), (13, 21), (12.5, 25), (4, 27), (2.5, 31.5), (16, 31.5)]), ENDER, form="round", bevel=2.5)   # neck and shoulders
    head = sym([(16, 2), (6.5, 2), (5.5, 4), (5.5, 20), (7, 22.5), (16, 22.5)])
    p.part(head, ENDER, form="round", bevel=3.5, lift=1)
    p.shift(rect(6, 16, 25, 16), -1)                                             # the jaw line
    p.shift(rect(11, 19, 20, 19), -2)
    for x in (7, 18):                                                            # long glowing eyes
        p.fill(rect(x, 10, x + 6, 12), RIFT, 2)
        p.fill(rect(x + 1, 10, x + 5, 11), RIFT, 3)
        p.fill(rect(x + 2, 10, x + 4, 11), RIFT, 4)
        p.fill(rect(x + 3, 10, x + 3, 12), RIFT, 0)
        p.shift(rect(x, 9, x + 6, 9), -2), p.shift(rect(x, 13, x + 6, 13), -1)
    p.dots([(8, 4), (9, 4), (8, 5)], ENDER, 4)
    for x, y in ((2, 8), (29, 13), (27, 3), (3, 19)):                            # drifting motes
        p.extra[(x, y)] = RIFT[3]
        p.extra[(x, y + 1)] = RIFT[1]
    p.spark(28, 24, RIFT)
    return p


def phoenix():
    p = Pic()
    for tx, ty, w in ((3, 30, 2.6), (8, 31.5, 2.6), (13, 31, 2.4)):               # tail plumes
        p.part(taper(14, 20, 2.6, tx, ty, 0.6), EMBERRED, form="round", bevel=1.6)
        p.put(tx, int(ty) - 1, FIRE, 4)
    for sign, (ax, ay) in ((1, (20, 14)), (-1, (10, 15))):                       # wings raised, flame feathers
        tips = ((30.5, 2, 3.0), (31, 9, 2.8), (29, 15, 2.6)) if sign == 1 else ((1, 2, 3.0), (0.5, 9, 2.8), (2.5, 15, 2.4))
        for tx, ty, w in tips:
            p.part(taper(ax, ay, w, tx, ty, 0.5), FIRE, form="round", bevel=1.8)
            p.paint(circle(tx, ty, 3.2), FIRE, 1)
        p.paint(circle(ax, ay, 4.5), EMBERRED)
    body = union(ellipse(15, 19, 6, 6.5), ellipse(16.5, 12.5, 4, 5))
    p.part(body, EMBERRED, form="round", bevel=4)
    p.part(ellipse(15, 20.5, 3.6, 4.2), FIRE, form="round", bevel=3, seam=None)  # the glowing breast
    p.part(circle(18, 8, 4.3), FIRE, form="round", bevel=4)                      # the head
    for tx, ty in ((13, 0.5), (16.5, 0.2), (20, 1)):                             # the crest
        p.part(taper(17.5, 5.5, 1.3, tx, ty, 0.4), FIRE, form="flat", lift=2)
    p.part(poly([(21, 7), (26, 9.5), (21, 11)]), AMBER, bevel=1, lift=1)         # the beak
    p.put(22, 10, AMBER, 0)
    p.put(18, 7, SHADE, 0), p.put(19, 7, SHADE, 0), p.put(18, 6, PEARL, 4)
    for x in (13, 17):                                                           # talons
        p.line(x, 26, x, 28, AMBER, 1), p.put(x - 1, 28, AMBER, 1), p.put(x + 1, 28, AMBER, 2)
    p.spark(27, 24, FIRE, big=True), p.spark(5, 23, FIRE)
    return p


def ender_dragon():
    p = Pic()
    p.part(poly([(0.5, 14), (7, 10), (11, 26), (3, 31.5), (0.5, 31.5)]), DRAGON, form="round", bevel=3)   # the neck
    for y in (12, 17, 22):                                                       # neck spines
        p.part(poly([(1, y + 1), (0.5, y - 3.5), (4.5, y - 0.5)]), ELEPH, bevel=1)
    p.shift(seg(3, 20, 8, 17, 0.5), -1), p.shift(seg(3, 26, 9, 22, 0.5), -1)
    p.part(taper(10, 9, 2.4, 2.5, 1.2, 0.5), BONE, form="round", bevel=1.6)      # horns
    p.part(taper(14.5, 7.5, 2.0, 10.5, 0.6, 0.4), BONE, form="round", bevel=1.4)
    jaw = poly([(9, 21), (27, 22.5), (29.5, 25), (26, 27.5), (16, 28.5), (10, 27.5), (7, 24)])
    p.part(jaw, DRAGON, form="round", bevel=2)
    skull = poly([(6, 10), (12, 6.5), (19, 8), (24, 12.5), (30.5, 15.5), (31.5, 19), (30, 22.5), (20, 22.5), (13, 23.5), (7.5, 23), (5, 17)])
    p.part(skull, DRAGON, form="round", bevel=3.5, lift=1)
    p.part(poly([(13, 9), (19, 9.5), (22, 13), (17.5, 13.5)]), DRAGONBELLY, bevel=1.2)   # the brow ridge
    p.fill(poly([(15, 14), (21.5, 14.5), (20.5, 17.5), (16, 16.5)]), SHADE, 0)   # the socket
    p.fill(rect(17, 15, 20, 16), RIFT, 3), p.put(18, 15, RIFT, 4), p.put(19, 15, SHADE, 0), p.put(19, 16, SHADE, 0)
    p.part(ellipse(28.5, 16.5, 2.4, 1.8), DRAGONBELLY, form="round", bevel=1.5, seam=None)   # the nostril bump
    p.put(29, 17, SHADE, 0), p.put(28, 17, SHADE, 0)
    p.fill(poly([(12, 21.5), (30, 21.5), (29, 24), (13, 23.5)]), SHADE, 0)       # the open mouth
    for x in (14, 17, 20, 23, 26):                                               # teeth
        p.put(x, 22, PEARL, 4), p.put(x, 23, PEARL, 2)
    for x in (16, 19, 22, 25):
        p.put(x, 24, PEARL, 3)
    p.put(28, 22, PEARL, 4)
    for x, y in ((9, 13), (11, 16), (9, 19), (13, 19), (24, 18), (22, 20)):      # scales
        p.put(x, y, DRAGON, 4), p.put(x + 1, y + 1, DRAGON, 1)
    p.fill(rect(27, 23, 30, 23), RIFT, 2), p.put(28, 23, RIFT, 4)                # breath in the throat
    p.spark(29, 5, RIFT, big=True), p.spark(24, 30, RIFT)
    return p


def tiger():
    p = Pic()
    p.part(both(circle(6.5, 6.5, 4.2)), TIGER, form="round", bevel=2.5)
    p.fill(both(circle(7, 7.2, 2.1)), PINK, 1)
    head = union(ellipse(16, 16.5, 12.5, 11.5), ellipse(16, 23, 10, 7.5))
    p.part(head, TIGER, form="round", bevel=6)
    p.part(both(poly([(3.5, 17), (1.5, 22.5), (4.5, 22), (3, 26), (8, 26.5), (9, 19)])), CREAM, form="round", bevel=1.8)   # cheek ruffs
    muzzle = union(both(ellipse(13, 24, 4.4, 3.6)), ellipse(16, 27, 4, 2.8))
    p.part(muzzle, CREAM, form="round", bevel=3, seam=None)
    p.paint(both(ellipse(10, 14.5, 3.2, 2.2)), CREAM)                            # pale brows
    for a, b in (((16, 5.5), (16, 10)), ((12.5, 6), (13.5, 10)), ((19.5, 6), (18.5, 10))):   # forehead stripes
        p.part(taper(a[0], a[1], 1.0, b[0], b[1], 0.4), STRIPE, form="flat", seam=None, lift=-1)
    for y, x0, x1 in ((13, 3.5, 7), (17, 3.5, 7.5), (21, 5, 8.5)):               # cheek stripes
        p.part(both(taper(x0, y, 1.0, x1, y + 1, 0.3)), STRIPE, form="flat", seam=None, lift=-1)
    p.fill(poly([(14, 20.5), (18, 20.5), (16, 23)]), PINK, 2), p.put(14, 20, PINK, 3)
    p.line(15, 23, 15, 25, STRIPE, 1), p.line(16, 23, 16, 25, STRIPE, 1)
    p.line(13, 26, 14, 27, STRIPE, 1), p.line(18, 26, 17, 27, STRIPE, 1)
    p.dots([(11, 23), (11, 25), (20, 23), (20, 25)], STRIPE, 2)
    for x in (9, 20):
        p.line(x - 1, 14, x + 3, 14, STRIPE, 0)
        eye(p, x, 15, EMERALD)
    return p


def elephant():
    p = Pic()
    for sign in (1, -1):                                                         # the ears
        cx = 16 - sign * 9.5
        p.part(ellipse(cx, 13, 6.5, 9.5), ELEPH, form="round", bevel=3)
        p.part(ellipse(cx - sign * 0.5, 13.5, 3.8, 6.5), PINK, form="round", bevel=3, seam=None, lift=-1)
    head = union(ellipse(16, 12.5, 8.5, 9), ellipse(16, 18, 6.5, 6))
    p.part(head, ELEPH, form="round", bevel=5, lift=1)
    trunk = path([(16, 17), (16, 24), (15, 28), (11.5, 29.5)], 2.7)
    p.part(trunk, ELEPH, form="round", bevel=2.6, lift=1)
    for y in (19, 22, 25):                                                       # trunk creases
        p.shift(rect(14, y, 18, y), -1)
    p.put(10, 29, SHADE, 1), p.put(10, 30, PINK, 1)
    for sign in (1, -1):                                                         # tusks
        x0 = 16 + sign * 5
        p.part(taper(x0, 20, 1.9, x0 + sign * 3.5, 28, 0.6), BONE, form="round", bevel=1.6, lift=1)
    for x in (11, 19):
        p.put(x, 13, SHADE, 0), p.put(x + 1, 13, SHADE, 0), p.put(x, 14, SHADE, 0), p.put(x + 1, 14, SHADE, 1), p.put(x, 13, PEARL, 4)
        p.shift(rect(x - 1, 11, x + 2, 11), -1)
    p.shift(seg(13, 6, 19, 6, 0.5), 1)
    p.dots([(14, 8), (18, 9)], ELEPH, 1)
    return p


def turtle():
    p = Pic()
    for cx, cy, a in ((6.5, 10.5, -1), (22.5, 7.5, 1), (5.5, 24.5, -1), (22, 27, 1)):   # flippers
        p.part(taper(14 + a * 3, 17 + (cy - 17) * 0.4, 3.0, cx - a * 0 + (a * 2 if cy > 17 else 0), cy, 1.6), TURTLE, form="round", bevel=1.8)
    p.part(taper(4, 18, 1.6, 0.5, 19.5, 0.4), TURTLE, form="round", bevel=1)     # tail
    p.part(seg(23, 14.5, 27, 12.5, 2.4), TURTLE, form="round", bevel=2)          # neck
    p.part(ellipse(27.5, 11.5, 4, 3.4), TURTLE, form="round", bevel=3, lift=1)   # head
    p.put(28, 10, SHADE, 0), p.put(29, 10, SHADE, 0), p.put(28, 11, SHADE, 0), p.put(28, 10, PEARL, 4)
    p.line(29, 13, 31, 13, TURTLE, 0)
    p.dots([(26, 9), (25, 12)], TURTLE, 4)
    rim = ellipse(14, 17.5, 11.2, 9.2)
    p.part(rim, BONE, form="round", bevel=2, lift=-1)
    shell = ellipse(13.6, 16.6, 9.6, 7.6)
    p.part(shell, SHELL, form="round", bevel=6.5, seam="line")
    hexa = [(13.5, 16.5, 3.2), (8, 14, 2.6), (19, 14, 2.6), (9, 20.5, 2.5), (18.5, 20.5, 2.5), (13.5, 10.8, 2.3), (13.5, 22.3, 2.0)]
    for cx, cy, r in hexa:                                                       # scutes: a dark seam, a lit dome
        scute = poly([(cx - r, cy), (cx - r * 0.5, cy - r * 0.8), (cx + r * 0.5, cy - r * 0.8), (cx + r, cy), (cx + r * 0.5, cy + r * 0.8),
                      (cx - r * 0.5, cy + r * 0.8)])
        grown = poly([(cx - r - 1, cy), (cx - r * 0.5 - 0.6, cy - r * 0.8 - 1), (cx + r * 0.5 + 0.6, cy - r * 0.8 - 1), (cx + r + 1, cy),
                      (cx + r * 0.5 + 0.6, cy + r * 0.8 + 1), (cx - r * 0.5 - 0.6, cy + r * 0.8 + 1)])
        p.shift(inter(shell, sub(grown, scute)), -1)
        p.shift(inter(shell, poly([(cx - r, cy), (cx - r * 0.5, cy - r * 0.8), (cx + r * 0.3, cy - r * 0.8), (cx - r * 0.4, cy + r * 0.2)])), 1)
    for x, y in ((4, 17), (8, 24), (14, 26), (21, 23), (24, 17)):                # rim plates
        p.put(x, y, BONE, 0)
    return p


PETS = {"wolf": wolf, "rabbit": rabbit, "enderman": enderman, "phoenix": phoenix, "ender_dragon": ender_dragon, "tiger": tiger,
        "elephant": elephant, "turtle": turtle}


def all_icons():
    return {f"pet_{name}": fn() for name, fn in PETS.items()}
