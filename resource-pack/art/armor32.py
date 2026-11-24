"""The 72 class armour icons of the Skyblock items (32 px, painted with paint32): six classes, four slots, three tiers.

Every class has its own silhouette and materials (straw and leather for the farmer, fur and ice for the frostborne, a
hood for the hunter, plaid and an axe for the lumberjack, a lamp helmet for the miner, plates for the void walker) and
a tier climbs in material, trim, gems and glow. items_icons.py writes them (ids <class>_<slot>_t<tier>).
"""
from paint32 import (AMBER, AMETHYST, AQUA, BONE, BRONZE, DARKSTEEL, EMERALD, GOLD, IRON, LEATHER, ROPE, RUBY, SAPPHIRE,
                     SILVER, WOOD, Pic, R, both, circle, ellipse, inter, mirror, move, path, poly, rect, ring, seg, star,
                     sub, sym, taper, union)

STRAW = R("#6a4612", "#a67626", "#d6a840", "#f0cf68", "#fff0a8")
STRAW_OLD = R("#54401c", "#86682e", "#b0904a", "#d0b672", "#eedca4")
LINEN = R("#4a4234", "#80745a", "#b4a884", "#dad0ac", "#f6f0d8")
DENIM = R("#182040", "#2c3c6c", "#465e9a", "#6c8ac2", "#a6c0e8")
REDCLOTH = R("#3a0a16", "#761824", "#b22c2c", "#dc5a40", "#f89c78")


def tier_metal(tier):
    return (BRONZE, SILVER, GOLD)[tier - 1]


# ---------------------------------------------------------------------------------------------- shared builders
def boot(p, ox, oy, body, sole, shaft=12, toe=13.5, width=9, heel=0):
    """One boot seen from the side, toe to the right, in a 14 x 20 box at (ox, oy). Returns its key boxes."""
    top = 18 - shaft - 6
    leg = poly([(ox + 1, oy + top), (ox + width, oy + top), (ox + width, oy + 12), (ox + 1, oy + 12)])
    foot = poly([(ox + 1, oy + 10), (ox + width, oy + 10), (ox + toe - 1.5, oy + 12.5), (ox + toe, oy + 15.5),
                 (ox + toe, oy + 18), (ox + 1 - heel, oy + 18), (ox + 1 - heel, oy + 13)])
    p.part(union(leg, foot), body, form="round", bevel=3.2)
    p.part(rect(ox - heel, oy + 18, ox + int(toe), oy + 19), sole, bevel=1, grad=0.4)
    return top


def wheat(p, x, y, lean=1):
    """A wheat ear standing at (x, y), leaning right (1) or left (-1): a stalk and a head of paired grains."""
    for i in range(11):
        cx = x + (i * lean) // 3
        if i < 4:
            p.put(cx, y - i, STRAW, 1)
        else:
            p.put(cx, y - i, STRAW, 4 if i % 2 else 2)
            if i < 10 and i % 2 == 0:
                p.put(cx + 1, y - i, STRAW, 3)
                p.put(cx - 1, y - i, STRAW, 1)


# ---------------------------------------------------------------------------------------------- farmer
def farmer_helmet(t):
    p = Pic()
    straw = (STRAW_OLD, STRAW, STRAW)[t - 1]
    brim = ellipse(16, 21.5, 15.2 if t == 3 else 14.4, 6.2)
    p.part(brim, straw, form="round", bevel=3)
    if t == 3:
        p.paint(sub(brim, ellipse(16, 21.2, 13.6, 5)), GOLD)
    for rx, ry in ((11.6, 4.3), (8.6, 2.8)):   # the weave of the brim
        p.shift(sub(ellipse(16, 21.5, rx, ry), ellipse(16, 21.3, rx - 0.9, ry - 0.7)), -1)
    crown = inter(ellipse(16, 13, 9, 9.6), rect(0, 0, 31, 21))
    p.part(crown, straw, form="round", bevel=5)
    for cy in (8.5, 11.5, 14.5):               # woven rows across the crown
        p.shift(inter(crown, sub(ellipse(16, cy - 4, 12, 8), ellipse(16, cy - 5, 12.6, 8))), -1)
    band = inter(crown, sub(ellipse(16, 14, 10, 8.6), ellipse(16, 10.6, 11.5, 8.2)))
    p.paint(band, (LEATHER, REDCLOTH, GOLD)[t - 1])
    if t == 1:
        p.dots([(2, 25), (1, 26), (29, 18), (30, 17)], straw, 3)           # frayed ends
        p.dots([(20, 9), (21, 9), (20, 10), (21, 10)], LINEN, 2)           # a patch
        p.put(22, 10, LINEN, 1)
    if t >= 2:
        wheat(p, 23, 17, 1)
    if t == 2:
        p.part(rect(14, 19, 17, 21), BRONZE, bevel=1)
        p.put(15, 20, LEATHER, 0), p.put(16, 20, LEATHER, 0)
    if t == 3:
        wheat(p, 26, 18, 1)
        p.gem(16, 20.5, 2, AMBER, GOLD)
        p.spark(3, 6, AMBER, big=True)
        p.spark(29, 28, GOLD)
    return p


def _shirt(p, body, sleeve):
    torso = sym([(11.5, 3), (7, 5), (7.5, 28.5), (16, 29.5)])
    p.part(torso, body, form="round", bevel=4)
    arm = poly([(8, 4.2), (1.5, 9.5), (1.5, 15.5), (6.5, 17), (8.5, 12)])
    p.part(arm, sleeve, form="round", bevel=3)
    p.part(mirror(arm), sleeve, form="round", bevel=3)
    p.erase(poly([(12.5, 2), (16, 7.5), (19.5, 2)]))


def farmer_chestplate(t):
    p = Pic()
    _shirt(p, LINEN, LINEN)
    p.line(12, 4, 15, 8, LINEN, 1), p.line(19, 4, 16, 8, LINEN, 1)        # the collar
    if t == 1:
        for x in (10, 21):                                                  # suspenders
            p.paint(rect(x, 5, x + 1, 23), LEATHER)
        p.paint(rect(7, 23, 24, 24), ROPE)
        p.dots([(15, 25), (16, 25), (15, 26), (16, 27)], ROPE, 3)          # the knot
        p.dots([(18, 14), (19, 14), (18, 15), (19, 15), (20, 14), (20, 15)], STRAW_OLD, 2)   # a patch
        p.dots([(17, 13), (21, 16)], LEATHER, 1)
        p.dots([(10, 8), (21, 8)], BRONZE, 3)
    else:
        vest = sub(sym([(11.5, 3), (7, 5), (7.5, 28.5), (16, 29.5)]), poly([(12.5, 2), (16, 9), (15, 29.5), (17, 29.5), (16, 9), (19.5, 2)]),
                   rect(14, 9, 17, 30))
        leather = LEATHER if t == 2 else R("#34140c", "#66281a", "#964426", "#c06c3a", "#e8a468")
        p.paint(vest, leather)
        p.shift(rect(13, 9, 13, 29), -1), p.shift(rect(18, 9, 18, 29), -2)
        metal = tier_metal(t)
        p.paint(rect(7, 22, 24, 24), leather, -1)
        p.part(rect(13, 21, 18, 25), metal, bevel=1)
        if t == 2:
            p.fill(rect(15, 22, 16, 24), LEATHER, 0)
            for y in (12, 16):
                p.put(12, y, BRONZE, 3), p.put(19, y, BRONZE, 3)
            p.part(rect(20, 25, 24, 28), LEATHER, bevel=1.5, lift=1)        # a seed pouch
            p.put(22, 25, BRONZE, 3)
        else:
            p.gem(15.5, 23, 1.5, AMBER)
            p.paint(rect(13, 9, 13, 20), GOLD, 1), p.paint(rect(18, 9, 18, 20), GOLD, 2)
            p.paint(rect(7, 27, 24, 29), GOLD, 1)
            for sx in (4.5, 27.5):                                          # straw-gold shoulder pads
                p.part(ellipse(sx, 8, 4.4, 3.4), STRAW, form="round", bevel=2.5)
                p.shift(sub(ellipse(sx, 8, 3, 2.2), ellipse(sx, 7.6, 2.4, 1.6)), -1)
            p.paint(rect(1, 14, 6, 16), GOLD), p.paint(rect(25, 14, 30, 16), GOLD)
            p.spark(2, 3, AMBER, big=True), p.spark(29, 26, GOLD)
    return p


def _pants(p, cloth, flare=0.0):
    left = poly([(5.5, 5), (16, 5), (16, 10.5), (14.4, 12.5), (12.4 + flare / 2, 29.5), (4.8 - flare, 29.5), (4.4, 14)])
    p.part(left, cloth, form="round", bevel=4)
    p.part(mirror(left), cloth, form="round", bevel=4)
    p.part(rect(5, 3, 26, 6), cloth, bevel=1.5, lift=0)


def farmer_leggings(t):
    p = Pic()
    _pants(p, DENIM)
    belt = rect(5, 3, 26, 5)
    if t == 1:
        p.paint(belt, ROPE)
        p.dots([(14, 6), (15, 7), (14, 8), (17, 6)], ROPE, 3)
        p.dots([(7, 16), (8, 16), (9, 16), (7, 17), (8, 17), (9, 17), (7, 18), (8, 18), (9, 18)], LINEN, 2)   # a knee patch
        p.dots([(6, 15), (10, 19), (10, 15)], LINEN, 0)
        p.dots([(21, 24), (22, 24), (21, 25), (22, 25)], STRAW_OLD, 2)
        p.shift(rect(5, 27, 13, 27), -1), p.shift(rect(19, 27, 27, 27), -1)   # rolled hems
    else:
        metal = tier_metal(t)
        leather = LEATHER
        p.paint(belt, leather)
        p.part(rect(14, 2, 18, 6), metal, bevel=1)
        for kx in (9.2, 22.8):                                              # knee pads
            p.part(ellipse(kx, 17.5, 3.8, 4), leather if t == 2 else GOLD, form="round", bevel=2.5)
            if t == 2:
                p.put(int(kx), 17, BRONZE, 3)
            else:
                p.gem(kx, 17.5, 1.4, AMBER)
        p.paint(rect(4, 26, 13, 28), leather if t == 2 else GOLD), p.paint(rect(18, 26, 27, 28), leather if t == 2 else GOLD)
        if t == 2:
            p.fill(rect(15, 3, 16, 5), LEATHER, 0)
            p.part(rect(21, 7, 24, 11), LEATHER, bevel=1.5, lift=1)           # a pocket
        else:
            p.gem(16, 4, 1.4, AMBER)
            for x in (6, 25):                                               # gold side stitching
                for y in range(8, 26, 3):
                    p.put(x, y, GOLD, 3)
            p.spark(2, 2, AMBER, big=True), p.spark(29, 30, GOLD)
    return p


def farmer_boots(t):
    p = Pic()
    leather = LEATHER if t < 3 else R("#34140c", "#66281a", "#964426", "#c06c3a", "#e8a468")
    for ox, oy in ((1, 4), (16, 9)):
        boot(p, ox, oy, leather, WOOD, shaft=11)
        p.part(rect(ox, oy + 1, ox + 10, oy + 3), STRAW if t > 1 else STRAW_OLD, form="round", bevel=1.5)   # straw stuffed in the top
        for i in range(0, 10, 2):
            p.put(ox + 1 + i, oy, STRAW if t > 1 else STRAW_OLD, 3 if i % 4 else 2)
        if t == 1:
            for y in (6, 8):                                                # rope lacing
                p.line(ox + 6, oy + y, ox + 8, oy + y, ROPE, 3)
            p.dots([(ox + 4, oy + 14), (ox + 5, oy + 14), (ox + 4, oy + 15), (ox + 5, oy + 15)], LINEN, 1)   # a patch
        else:
            metal = tier_metal(t)
            p.paint(poly([(ox + 9.5, oy + 11), (ox + 15, oy + 11), (ox + 15, oy + 18), (ox + 9.5, oy + 18)]), metal, 1)   # toe cap
            p.paint(rect(ox + 1, oy + 9, ox + 9, oy + 10), metal)           # ankle strap
            p.put(ox + 5, oy + 9, metal, 4)
            if t == 3:
                p.gem(ox + 5.5, oy + 6.5, 1.5, AMBER, GOLD)
    if t == 3:
        p.spark(15, 2, AMBER, big=True), p.spark(30, 4, GOLD)
    return p



# ---------------------------------------------------------------------------------------------- frostborne
FUR = R("#56586e", "#8e94ac", "#c6cede", "#e8f0f8", "#ffffff")
ICE = R("#12345c", "#2a6aa6", "#56aae0", "#9edcfa", "#e8fbff")
HIDE = R("#1a2236", "#32405c", "#506486", "#7890b0", "#aabed8")


def fur(p, points, r, ramp=FUR):
    """A strip of fur: one tuft per point."""
    p.part(union(*[circle(x, y, r) for x, y in points]), ramp, form="round", bevel=r)
    for x, y in points:   # a parting under each tuft
        p.shift(rect(int(x + r * 0.4), int(y + r * 0.3), int(x + r * 0.4), int(y + r * 0.8)), -1)


def shard(p, x0, y0, x1, y1, w, ramp=ICE):
    """An ice crystal from its base (x0, y0) to its tip (x1, y1): a lit face and a dark face."""
    dx, dy = x1 - x0, y1 - y0
    ln = (dx * dx + dy * dy) ** 0.5
    nx, ny = -dy / ln * w, dx / ln * w
    mx, my = x0 + dx * 0.35, y0 + dy * 0.35
    whole = poly([(x0 + nx * 0.6, y0 + ny * 0.6), (mx + nx, my + ny), (x1, y1), (mx - nx, my - ny), (x0 - nx * 0.6, y0 - ny * 0.6)])
    p.part(whole, ramp, form="flat", lift=0)
    lit = poly([(x0, y0), (mx, my), (x1, y1), (mx + nx, my + ny), (x0 + nx * 0.6, y0 + ny * 0.6)])
    dark = poly([(x0, y0), (mx, my), (x1, y1), (mx - nx, my - ny), (x0 - nx * 0.6, y0 - ny * 0.6)])
    if nx + ny >= 0:   # the face that looks at the light is the upper left one
        lit, dark = dark, lit
    p.paint(inter(whole, lit), ramp, 1)
    p.paint(inter(whole, dark), ramp, -1)
    p.put(int(x1 - dx / ln), int(y1 - dy / ln), ramp, 4)


def frostborne_helmet(t):
    p = Pic()
    if t >= 2:
        shard(p, 6, 13, 2.5, 2.5, 2.6), shard(p, 26, 13, 29.5, 2.5, 2.6)
    if t == 3:
        shard(p, 10, 9, 8, 0.5, 2.2), shard(p, 22, 9, 24, 0.5, 2.2)
    cap = inter(ellipse(16, 15.5, 10.6, 11), rect(0, 0, 31, 19))
    p.part(cap, HIDE, form="round", bevel=5)
    for x in (11, 16, 21):                                                  # stitched panels
        p.shift(inter(cap, rect(x, 5, x, 16)), -1)
    for sx in (7.2, 24.8):                                                  # ear flaps
        p.part(ellipse(sx, 22.5, 3.6, 6.6), HIDE, form="round", bevel=3)
        fur(p, [(sx, 27.5)], 2.8)
    fur(p, [(5.5, 17.5), (9.5, 18.2), (13.5, 18.6), (18.5, 18.6), (22.5, 18.2), (26.5, 17.5)], 3.0)
    if t == 1:
        p.dots([(14, 9), (15, 10), (14, 11)], HIDE, 0)                      # a mended tear
        p.dots([(7, 22), (24, 22)], BONE, 3)                                # bone toggles
    else:
        metal = tier_metal(t)
        p.part(poly([(12, 12), (20, 12), (19, 16.5), (16, 15), (13, 16.5)]), metal, bevel=1.2)   # brow plate
        if t == 2:
            p.gem(16, 13.5, 1.2, ICE)
        else:
            shard(p, 16, 9, 16, 0, 2.6)
            p.facet([(16, 11), (18.5, 13.5), (16, 16.5), (13.5, 13.5)], SAPPHIRE, GOLD)
            p.spark(4, 6, ICE, big=True), p.spark(28, 8, ICE), p.spark(29, 29, ICE)
    return p


def frostborne_chestplate(t):
    p = Pic()
    torso = sym([(10, 5), (7, 9), (8, 28.5), (16, 29.5)])
    p.part(torso, HIDE, form="round", bevel=4)
    arm = poly([(8, 6), (1.5, 10), (1.5, 17), (7, 18.5), (8.5, 12)])
    p.part(both(arm), HIDE, form="round", bevel=3)
    fur(p, [(2.8, 17.5), (5.8, 18.4), (26.2, 18.4), (29.2, 17.5)], 2.3)     # cuffs
    p.shift(rect(15, 12, 15, 28), -1), p.shift(rect(16, 12, 16, 28), 1)     # the front seam
    for y in (15, 20, 25):                                                  # toggles
        p.line(14, y, 17, y, BONE, 3)
        p.put(18, y, BONE, 1)
    if t >= 2:
        for a, b in (((6, 7), (1, 0.5)), ((26, 7), (31, 0.5))):
            shard(p, a[0], a[1], b[0], b[1], 3.0 if t == 3 else 2.4)
    if t == 3:
        shard(p, 9.5, 6, 8.5, 0.5, 2.0), shard(p, 22.5, 6, 23.5, 0.5, 2.0)
    fur(p, [(4.5, 8.2), (8, 6.2), (11.5, 7.6), (14.4, 9.6), (17.6, 9.6), (20.5, 7.6), (24, 6.2), (27.5, 8.2)], 3.1)   # the collar
    p.erase(poly([(13.5, 2), (16, 5.5), (18.5, 2)]))
    if t == 1:
        p.paint(rect(8, 22, 23, 23), LEATHER)
        p.put(12, 22, BONE, 3)
    else:
        metal = tier_metal(t)
        p.paint(rect(8, 22, 23, 24), metal)
        if t == 2:
            p.part(rect(14, 21, 17, 25), SILVER, bevel=1)
            p.gem(16, 23.5, 1.0, ICE)
        else:
            p.facet([(16, 12.5), (20, 17.5), (16, 24.5), (12, 17.5)], ICE, GOLD)
            p.put(15, 16, ICE, 4), p.put(16, 15, ICE, 4)
            p.paint(rect(8, 27, 23, 29), GOLD)
            p.spark(16, 1, ICE, big=True), p.spark(2, 27, ICE), p.spark(29, 25, ICE)
    return p


def frostborne_leggings(t):
    p = Pic()
    _pants(p, HIDE)
    for x in (9, 22):                                                       # quilting
        p.shift(rect(x, 8, x, 25), -1)
    if t >= 2:
        metal = tier_metal(t)
        for kx, flip in ((9, -1), (23, 1)):
            p.part(poly([(kx - 3.5, 14), (kx + 3.5, 14), (kx + 3.5, 19), (kx, 22.5), (kx - 3.5, 19)]), ICE, bevel=1.5, grad=0.5)
            p.paint(rect(int(kx - 4), 13, int(kx + 3), 14), metal)
            if t == 3:
                shard(p, kx + flip * 3, 15, kx + flip * 7.5, 10, 1.8)
                p.gem(kx, 17, 1.2, SAPPHIRE)
    fur(p, [(6, 4.5), (9.5, 5.2), (13, 5.4), (16, 5.6), (19, 5.4), (22.5, 5.2), (26, 4.5)], 2.5)            # waist
    fur(p, [(5.5, 28.2), (8.8, 28.8), (12, 28.2), (20, 28.2), (23.2, 28.8), (26.5, 28.2)], 2.4)            # hems
    if t == 1:
        p.dots([(15, 5), (16, 5)], BONE, 3)
    elif t == 2:
        p.gem(16, 5.5, 1.3, ICE, SILVER)
    else:
        p.facet([(16, 3), (18.5, 6), (16, 9.5), (13.5, 6)], SAPPHIRE, GOLD)
        p.spark(2, 12, ICE, big=True), p.spark(30, 22, ICE)
    return p


def frostborne_boots(t):
    p = Pic()
    for ox, oy in ((1, 4), (16, 9)):
        boot(p, ox, oy, HIDE, DARKSTEEL, shaft=10, toe=13.5)
        p.shift(rect(ox + 5, oy + 5, ox + 5, oy + 11), -1)
        if t >= 2:
            metal = tier_metal(t)
            p.paint(poly([(ox + 9.5, oy + 11), (ox + 15, oy + 11), (ox + 15, oy + 18), (ox + 9.5, oy + 18)]), ICE, 1)
            p.paint(rect(ox + 1, oy + 9, ox + 9, oy + 10), metal)
            shard(p, ox + 3, oy + 3, ox + 1 if t == 2 else ox, oy - 3.5 if t == 3 else oy - 2, 1.8)
            if t == 3:
                shard(p, ox + 7.5, oy + 3, ox + 9.5, oy - 3.5, 1.8)
                p.gem(ox + 5.5, oy + 9.5, 1.3, SAPPHIRE, GOLD)
        else:
            p.line(ox + 6, oy + 7, ox + 8, oy + 9, BONE, 3)
        fur(p, [(ox + 1.5, oy + 3.4), (ox + 4.6, oy + 4), (ox + 7.6, oy + 4), (ox + 10, oy + 3.4)], 2.4)
    if t == 3:
        p.spark(14, 29, ICE), p.spark(29, 3, ICE, big=True)
    return p


# ---------------------------------------------------------------------------------------------- hunter
FOREST = R("#0e2018", "#1c402c", "#306446", "#4c8c62", "#84c094")
SHADE = R("#08060c", "#120e18", "#1e1826", "#2e2638", "#44384e")
FEATHER = R("#4a0e10", "#8c1c1a", "#d03a24", "#f47c44", "#ffc890")


def feather(p, x0, y0, x1, y1, ramp=FEATHER):
    p.part(taper(x0, y0, 0.7, x1, y1, 1.9), ramp, form="flat", seam="shadow")
    p.line(int(x0), int(y0), int(x1), int(y1), ramp, 4)
    p.paint(circle(x1, y1, 1.4), ramp, -1)


def hunter_helmet(t):
    p = Pic()
    hood = poly([(19, 1), (10, 4.5), (5, 12), (4, 23), (7.5, 30), (24.5, 30), (28, 23), (27, 12), (23.5, 5.5)])
    p.part(hood, FOREST, form="round", bevel=5)
    p.shift(seg(19, 2, 13, 9, 0.6), -1)                                     # the fold of the peak
    p.shift(seg(22, 5, 24, 13, 0.6), -1)
    face = poly([(16, 9.5), (10, 13), (9, 22), (12.5, 28), (19.5, 28), (23, 22), (22, 13)])
    edge = union(*[move(face, dx, dy) for dx, dy in ((1.3, 0), (-1.3, 0), (0, -1.3), (0, 1.3))])
    trim = (FOREST, LEATHER, GOLD)[t - 1]
    p.part(edge, trim, bevel=1.2, lift=1 if t == 1 else 0)
    p.fill(face, SHADE, 1)
    p.fill(inter(face, move(face, 2, 2.5)), SHADE, 2)                       # a hint of the face in the dark
    p.fill(inter(face, rect(0, 25, 31, 31)), FOREST, 0)                     # the scarf
    eyes = ((13, 18), (18, 18))
    if t == 3:
        for x, y in eyes:
            p.put(x, y, EMERALD, 4), p.put(x + 1, y, EMERALD, 3)
    else:
        for x, y in eyes:
            p.put(x, y, BONE, 2 if t == 1 else 3), p.put(x + 1, y, SHADE, 3)
    if t == 1:
        p.dots([(7, 16), (7, 18), (7, 20)], FOREST, 4)                      # stitches
        p.dots([(24, 9), (25, 10)], LEATHER, 2)                             # a patch
        p.dots([(24, 10), (25, 9)], LEATHER, 3)
    else:
        metal = tier_metal(t)
        feather(p, 25, 12, 30.5, 3)
        if t == 3:
            feather(p, 24, 14, 31, 9, EMERALD)
            p.gem(16, 28.5, 2, EMERALD, GOLD)
            p.spark(3, 4, EMERALD, big=True), p.spark(2, 28, GOLD)
        else:
            p.part(circle(16, 28.5, 2), metal, form="round", bevel=1.5)
    return p


def hunter_chestplate(t):
    p = Pic()
    if t >= 2:                                                              # the quiver over the right shoulder
        p.part(poly([(22, 1.5), (27, 0.5), (29, 7), (24, 9)]), LEATHER, form="round", bevel=2)
        for x, y in ((23, 1), (25, 0), (27, 0)):
            p.put(x, y, FEATHER if t == 2 else EMERALD, 3), p.put(x, y + 1, BONE, 2)
    torso = sym([(11.5, 4), (8, 6), (8.5, 28.5), (16, 29.5)])
    leather = LEATHER if t < 3 else R("#24160e", "#46291a", "#6c4226", "#96643a", "#c4945e")
    p.part(torso, leather, form="round", bevel=4)
    for y in (15, 19, 23):                                                  # lacing down the front
        p.line(14, y, 17, y + 1, ROPE, 3), p.line(17, y, 14, y + 1, ROPE, 1)
    mantle = sym([(11.5, 3), (5, 4.5), (1.5, 10), (2, 16.5), (6, 14.5), (9.5, 13.5), (13, 15.5), (16, 12.5)])
    p.part(mantle, FOREST, form="round", bevel=3.5)
    p.shift(seg(5, 6, 4, 14, 0.6), -1), p.shift(seg(27, 6, 28, 14, 0.6), -1)   # folds
    p.erase(poly([(12.5, 2), (16, 7), (19.5, 2)]))
    p.paint(seg(22.5, 9, 9.5, 27, 1.0), LEATHER, -1 if t < 3 else 1)        # the quiver strap
    if t == 1:
        p.dots([(3, 15), (5, 14), (8, 13), (24, 13), (27, 14), (29, 15)], FOREST, 0)   # a ragged hem
        p.put(16, 11, BONE, 3)
    else:
        metal = tier_metal(t)
        p.paint(rect(8, 24, 23, 25), metal)
        p.part(circle(14.5, 19, 1.7), metal, form="round", bevel=1.5)
        if t == 2:
            p.part(circle(16, 10.5, 1.8), SILVER, form="round", bevel=1.5)
        else:
            edge = sub(mantle, move(mantle, 0, -1.3))
            p.paint(edge, GOLD, 1)
            p.gem(16, 10.5, 2.1, EMERALD, GOLD)
            p.spark(2, 2, EMERALD, big=True), p.spark(29, 27, GOLD)
    return p


def hunter_leggings(t):
    p = Pic()
    left = poly([(6, 5), (16, 5), (16, 10.5), (14.4, 12.5), (13, 21), (12.4, 29.5), (6.5, 29.5), (6.5, 21), (5, 13)])
    p.part(left, FOREST, form="round", bevel=4)
    p.part(mirror(left), FOREST, form="round", bevel=4)
    p.part(rect(5, 3, 26, 6), LEATHER, bevel=1.5)
    for y in range(22, 29, 2):                                              # bound calves
        p.paint(rect(6, y, 13, y), LEATHER), p.paint(rect(18, y, 25, y), LEATHER)
    p.part(poly([(21.5, 7), (25.5, 7), (25, 16), (23.5, 19), (22, 16)]), LEATHER, bevel=1.5, lift=1)   # the knife sheath
    p.part(rect(22, 4, 24, 7), tier_metal(t) if t > 1 else BONE, bevel=1)
    p.paint(rect(5, 14, 14, 15), LEATHER)                                   # the thigh strap
    if t == 1:
        p.dots([(15, 4), (16, 4)], BONE, 3)
        p.dots([(9, 18), (10, 19)], FOREST, 0)
    else:
        metal = tier_metal(t)
        p.part(rect(14, 2, 17, 6), metal, bevel=1)
        p.put(9, 14, metal, 4), p.put(10, 14, metal, 3)
        for kx in (9.5, 22.5):
            p.part(poly([(kx - 3, 17.5), (kx + 3, 17.5), (kx + 2.5, 21), (kx, 23), (kx - 2.5, 21)]), LEATHER if t == 2 else GOLD, bevel=1.4)
        if t == 3:
            p.gem(15.5, 4, 1.3, EMERALD)
            p.gem(9.5, 19.5, 1.0, EMERALD), p.gem(22.5, 19.5, 1.0, EMERALD)
            p.spark(2, 9, EMERALD, big=True), p.spark(29, 30, GOLD)
    return p


def hunter_boots(t):
    p = Pic()
    for ox, oy in ((1, 4), (16, 9)):
        boot(p, ox, oy, LEATHER, WOOD, shaft=9, toe=14, width=8)
        cuff = poly([(ox - 0.5, oy + 3), (ox + 10, oy + 3), (ox + 10, oy + 6.5), (ox + 6, oy + 9.5), (ox - 0.5, oy + 6.5)])
        p.part(cuff, FOREST, bevel=1.5, lift=1)                             # the folded top
        p.shift(rect(ox, oy + 6, ox + 9, oy + 6), -1)
        for x in (ox + 10, ox + 12):
            p.put(x, oy + 13, ROPE, 3)                                      # toe stitches
        if t >= 2:
            metal = tier_metal(t)
            p.paint(rect(ox + 1, oy + 10, ox + 8, oy + 11), DARKSTEEL if t == 2 else GOLD, 1)
            p.put(ox + 6, oy + 10, metal, 4)
            feather(p, ox + 2, oy + 7, ox - 0.5, oy + 12, FEATHER if t == 2 else EMERALD)
            if t == 3:
                p.gem(ox + 5.5, oy + 5, 1.3, EMERALD, GOLD)
                p.paint(rect(ox + 11, oy + 15, ox + 14, oy + 17), GOLD)
        else:
            p.line(ox + 2, oy + 9, ox + 7, oy + 11, ROPE, 2)
    if t == 3:
        p.spark(14, 2, EMERALD, big=True), p.spark(30, 5, GOLD)
    return p


# ---------------------------------------------------------------------------------------------- lumberjack
FLANNEL = R("#3a0a12", "#74161e", "#b02a26", "#da5438", "#f6966e")
CANVAS = R("#2a1c12", "#503822", "#785838", "#a07e54", "#c8aa7e")
WOOL = R("#4c4640", "#8a8278", "#beb6a8", "#e2dccc", "#fbf8ee")


def plaid(p, shape, step=4, ox=0, oy=0):
    """Dark checks on what is painted in the shape: the lines keep the shading, the crossings go one tone darker."""
    for v in range(-2, 34, step):
        p.shift(inter(shape, rect(v + ox, 0, v + ox, 31)), -1)
        p.shift(inter(shape, rect(0, v + oy, 31, v + oy)), -1)


def axe(p, x, y, metal, haft=WOOD):
    """A small axe badge, 7 x 7, its top left at (x, y): the haft runs up to the right, the bit faces left."""
    p.line(x + 1, y + 6, x + 6, y + 1, haft, 3)
    for dx, dy, tone in ((2, 0, 3), (3, 0, 4), (1, 1, 2), (2, 1, 3), (3, 1, 3), (4, 1, 2), (1, 2, 1), (2, 2, 2), (3, 2, 2),
                         (2, 3, 1), (1, 3, 1)):
        p.put(x + dx, y + dy, metal, tone)


def lumberjack_helmet(t):
    p = Pic()
    p.part(circle(16, 4.6, 3.8), WOOL if t == 3 else FLANNEL, form="round", bevel=3.6, lift=0 if t == 3 else 1)   # the pompom
    dome = inter(ellipse(16, 17, 11.5, 13), rect(0, 0, 31, 21))
    p.part(dome, FLANNEL, form="round", bevel=5.5)
    for x in (7, 10, 13, 16, 19, 22, 25):                                   # knitted ribs
        p.shift(inter(dome, rect(x, 5, x, 20)), -1)
    fold = poly([(3.5, 19), (28.5, 19), (29, 26.5), (16, 29), (3, 26.5)])
    p.part(fold, WOOL if t == 3 else FLANNEL, form="round", bevel=3, lift=0 if t == 3 else 1)
    if t == 1:
        for x in range(5, 28, 3):
            p.shift(inter(fold, rect(x, 19, x, 29)), -1)
        p.dots([(21, 11), (22, 12), (21, 13)], FLANNEL, 0)                  # a pulled thread
    else:
        if t == 2:
            plaid(p, fold, 4, 1, 1)
        else:
            for x in range(5, 28, 3):
                p.shift(inter(fold, rect(x, 19, x, 29)), -1)
            plaid(p, dome, 5, 1, 2)
        metal = tier_metal(t)
        p.part(circle(16, 23.5, 4.2), DARKSTEEL if t == 2 else GOLD, form="round", bevel=1.4)   # the badge
        p.fill(circle(16, 23.5, 2.9), SHADE, 2)
        axe(p, 13, 20, metal)
        if t == 3:
            p.put(18, 26, RUBY, 3), p.put(19, 26, RUBY, 2), p.put(18, 25, RUBY, 4)
            p.spark(3, 5, GOLD, big=True), p.spark(29, 12, RUBY)
    return p


def lumberjack_chestplate(t):
    p = Pic()
    torso = sym([(11.5, 3), (7, 5), (7, 28.5), (16, 29.5)])
    p.part(torso, FLANNEL, form="round", bevel=4)
    arm = poly([(8, 4), (2.5, 7.5), (0.8, 13), (1.2, 19), (7, 19.5), (8.5, 12)])
    p.part(both(arm), FLANNEL, form="round", bevel=3)
    plaid(p, union(torso, both(arm)), 4, 1, 0)
    for sx in (1, 25):                                                      # rolled cuffs
        p.part(rect(sx, 17, sx + 5, 19), FLANNEL, bevel=1, lift=1)
    p.erase(poly([(12.5, 2), (16, 7.5), (19.5, 2)]))
    p.line(12, 4, 15, 8, FLANNEL, 4), p.line(19, 4, 16, 8, FLANNEL, 3)      # the collar
    for y in (11, 15, 19):
        p.put(16, y, BONE, 3)                                               # buttons
    for x in (10, 20):                                                      # suspenders
        p.part(rect(x, 5, x + 1, 24), LEATHER if t < 3 else DARKSTEEL, bevel=1, lift=1 if t == 3 else 0)
    belt = rect(7, 24, 24, 26)
    p.paint(belt, LEATHER, -1)
    if t == 1:
        p.put(15, 25, BRONZE, 3), p.put(16, 25, BRONZE, 2)
    else:
        metal = tier_metal(t)
        p.part(rect(13, 23, 18, 27), metal, bevel=1)
        p.fill(rect(15, 24, 16, 26), SHADE, 1)
        pad = poly([(1, 5), (8, 3.5), (9.5, 9.5), (2, 11.5)])               # a shoulder guard, where the log rides
        p.part(pad, LEATHER if t == 2 else WOOD, form="round", bevel=2, lift=1)
        p.dots([(3, 9), (7, 6)], metal, 4)
        if t == 3:
            p.part(mirror(pad), WOOD, form="round", bevel=2, lift=1)
            p.dots([(28, 9), (24, 6)], metal, 4)
            p.paint(sub(pad, move(pad, 0, -1.2)), GOLD), p.paint(sub(mirror(pad), move(mirror(pad), 0, -1.2)), GOLD)
            p.fill(rect(12, 10, 19, 18), SHADE, 2, only=True)
            p.part(rect(12, 10, 19, 18), GOLD, bevel=1, only=rect(12, 10, 19, 18))
            p.fill(rect(13, 11, 18, 17), SHADE, 2)
            axe(p, 12, 11, GOLD)
            p.put(17, 16, RUBY, 3)
            p.spark(30, 29, GOLD), p.spark(16, 0, RUBY)
        else:
            axe(p, 20, 9, SILVER)
    return p


def lumberjack_leggings(t):
    p = Pic()
    _pants(p, CANVAS, flare=0.8)
    p.part(rect(5, 3, 26, 6), LEATHER, bevel=1.5)
    for x in (9, 22):                                                       # suspender buttons
        p.put(x, 4, BONE, 3)
    for sx in (4, 18):                                                      # turned-up cuffs, plaid inside
        p.part(rect(sx, 25, sx + 9, 28), FLANNEL, bevel=1, lift=1)
        p.shift(rect(sx + 3, 25, sx + 3, 28), -1), p.shift(rect(sx + 7, 25, sx + 7, 28), -1)
    p.part(seg(25.5, 7, 24, 13, 0.8), LEATHER, form="flat")                 # the axe loop
    if t == 1:
        p.dots([(8, 17), (9, 17), (10, 17), (8, 18), (9, 18), (10, 18)], CANVAS, 4)   # a worn knee
        p.dots([(21, 20), (22, 21), (23, 20)], CANVAS, 0)
    else:
        metal = tier_metal(t)
        p.part(rect(14, 2, 18, 6), metal, bevel=1)
        chap = poly([(5.2, 9), (12.5, 9), (12.8, 23), (5, 23)])             # leather chaps over the thighs
        p.part(both(chap), LEATHER if t == 2 else WOOD, form="round", bevel=2.5, lift=1)
        for y in (11, 21):
            p.dots([(6, y), (11, y), (20, y), (25, y)], metal, 4)
        if t == 3:
            p.paint(both(rect(5, 22, 12, 23)), GOLD)
            axe(p, 5, 12, GOLD), axe(p, 19, 12, GOLD)
            p.put(16, 4, RUBY, 3), p.put(15, 3, RUBY, 4)
            p.spark(2, 1, GOLD, big=True), p.spark(30, 30, RUBY)
        else:
            p.fill(rect(15, 3, 16, 5), SHADE, 1)
    return p


def lumberjack_boots(t):
    p = Pic()
    for ox, oy in ((1, 4), (16, 9)):
        boot(p, ox, oy, CANVAS if t == 1 else LEATHER, DARKSTEEL, shaft=11, toe=13.5, heel=0)
        p.part(rect(ox, oy + 17, ox + 13, oy + 19), DARKSTEEL, bevel=1)     # a thick lugged sole
        for x in range(ox + 1, ox + 13, 3):
            p.put(x, oy + 19, SHADE, 0)
        p.part(rect(ox, oy + 1, ox + 10, oy + 3), WOOL if t == 3 else FLANNEL, bevel=1.2, lift=0 if t == 3 else 1)   # the sock
        if t < 3:
            p.shift(rect(ox + 3, oy + 1, ox + 3, oy + 3), -1), p.shift(rect(ox + 7, oy + 1, ox + 7, oy + 3), -1)
        for y in (5, 7, 9):                                                 # red laces
            p.line(ox + 5, oy + y, ox + 8, oy + y + 1, FLANNEL, 3)
        if t >= 2:
            metal = tier_metal(t)
            p.paint(poly([(ox + 9.5, oy + 11), (ox + 15, oy + 11), (ox + 15, oy + 17), (ox + 9.5, oy + 17)]), metal, 1)
            p.put(ox + 2, oy + 6, metal, 4), p.put(ox + 2, oy + 9, metal, 4)
            if t == 3:
                p.paint(rect(ox + 1, oy + 11, ox + 8, oy + 12), GOLD)
                p.put(ox + 4, oy + 14, RUBY, 3), p.put(ox + 4, oy + 13, RUBY, 4)
    if t == 3:
        p.spark(14, 2, GOLD, big=True), p.spark(30, 5, RUBY)
    return p


# ---------------------------------------------------------------------------------------------- miner
HARDHAT = R("#5a3608", "#a06c0c", "#e0a41c", "#ffd648", "#fff4a8")
SOOT = R("#1c1816", "#3a322c", "#5c5046", "#84766a", "#b0a294")
LAMP = R("#7a3a04", "#d07a10", "#ffb82c", "#ffe478", "#fffbe0")


def miner_helmet(t):
    p = Pic()
    shell = (LEATHER, HARDHAT, HARDHAT)[t - 1]
    dome = inter(ellipse(16, 18, 12, 13.5), rect(0, 0, 31, 21))
    p.part(dome, shell, form="round", bevel=6)
    p.part(inter(dome, rect(14, 4, 17, 12)), shell, bevel=1.2, lift=1)      # the crest ridge
    for x in (9, 23):
        p.shift(inter(dome, rect(x, 8, x, 20)), -1)
    brim = poly([(2, 20), (30, 20), (31, 23.5), (27, 26), (5, 26), (1, 23.5)])
    p.part(brim, shell, form="round", bevel=2.5)
    metal = (IRON, IRON, GOLD)[t - 1]
    p.paint(rect(2, 20, 29, 21), metal)                                     # the band
    for x in (6, 11, 21, 26):
        p.put(x, 20, metal, 4)
    r = (3.2, 4.2, 4.8)[t - 1]
    p.part(circle(16, 13.5, r + 1.6), metal, form="round", bevel=1.6)       # the lamp
    p.part(circle(16, 13.5, r), LAMP if t > 1 else AMBER, form="round", bevel=r, seam=None, grad=0.5, lift=0 if t == 1 else 1)
    p.put(15, 12, LAMP, 4)
    if t == 1:
        p.dots([(22, 12), (23, 13), (22, 14)], LEATHER, 0)                  # a dent
        p.paint(rect(4, 24, 27, 25), SOOT, -1)
    if t >= 2:
        p.fill(circle(16, 13.5, r - 2), LAMP, 4)
    if t == 3:
        p.part(both(ellipse(8.5, 24.5, 3.4, 2.6)), GOLD, form="round", bevel=1.2)   # goggles pushed up on the brim
        p.fill(both(ellipse(8.5, 24.5, 2.0, 1.4)), AQUA, 3)
        p.put(8, 24, AQUA, 4), p.put(22, 24, AQUA, 4)
        p.paint(rect(12, 24, 19, 24), LEATHER)
        for a, b in (((16, 6), (16, 2)), ((10, 9), (6, 5)), ((22, 9), (26, 5))):   # rays
            p.extra[b] = LAMP[3]
            p.extra[((a[0] + b[0]) // 2, (a[1] + b[1]) // 2)] = LAMP[2]
        p.spark(3, 4, LAMP, big=True), p.spark(29, 6, LAMP)
    return p


def miner_chestplate(t):
    p = Pic()
    _shirt(p, SOOT, SOOT)
    for sx in (1, 26):
        p.shift(rect(sx, 14, sx + 5, 15), -1)                               # rolled sleeves
    bib_c = (DENIM, LEATHER, LEATHER)[t - 1]
    bib = poly([(10, 11), (22, 11), (24.5, 16), (24.5, 29), (7.5, 29), (7.5, 16)])
    p.part(inter(bib, sym([(11.5, 3), (7, 5), (7.5, 28.5), (16, 29.5)])), bib_c, form="round", bevel=3, lift=1 if t > 1 else 0)
    for x in (10, 20):                                                      # straps
        p.part(rect(x, 4, x + 1, 11), bib_c, bevel=1, lift=1)
    metal = (IRON, IRON, GOLD)[t - 1]
    p.dots([(10, 11), (21, 11)], metal, 4), p.dots([(11, 12), (20, 12)], metal, 2)   # strap buckles
    p.part(rect(12, 15, 19, 20), bib_c, bevel=1.2, lift=2 if t == 1 else 1)  # the chest pocket
    p.shift(rect(12, 16, 19, 16), -1)
    p.paint(rect(7, 23, 24, 25), LEATHER, -1)                               # the tool belt
    p.part(rect(14, 22, 17, 26), metal, bevel=1)
    if t == 1:
        p.line(14, 14, 14, 12, WOOD, 3)                                     # a pencil in the pocket
        p.dots([(9, 27), (10, 27), (9, 28)], SOOT, 1)
    else:
        p.part(rect(8, 24, 11, 28), LEATHER, bevel=1.2, lift=1)             # belt pouch
        p.put(9, 25, metal, 4)
        p.line(21, 22, 21, 28, WOOD, 3), p.dots([(20, 22), (22, 22), (20, 23)], IRON, 3)   # a hammer in the loop
        for x, y in ((9, 16), (22, 16), (9, 21), (22, 21)):                  # rivets
            p.put(x, y, metal, 4)
        if t == 3:
            p.part(circle(15.5, 17.5, 3.6), GOLD, form="round", bevel=1.4)
            p.part(circle(15.5, 17.5, 2.4), LAMP, form="round", bevel=2.4, seam=None, lift=1)
            p.put(15, 17, LAMP, 4)
            p.paint(rect(7, 28, 24, 29), GOLD)
            for sx in (4.5, 27.5):
                p.part(ellipse(sx, 8, 4.2, 3.2), IRON, form="round", bevel=2.2)
                p.paint(sub(ellipse(sx, 8, 4.2, 3.2), ellipse(sx, 7, 4.2, 3)), GOLD)
            p.spark(2, 2, LAMP, big=True), p.spark(29, 28, LAMP)
    return p


def miner_leggings(t):
    p = Pic()
    cloth = (DENIM, SOOT, SOOT)[t - 1]
    _pants(p, cloth)
    p.part(rect(5, 3, 26, 6), LEATHER, bevel=1.5)
    metal = (IRON, IRON, GOLD)[t - 1]
    p.part(rect(14, 2, 18, 6), metal, bevel=1)
    p.fill(rect(15, 3, 16, 5), SHADE, 1)
    for kx in (5, 19):                                                      # square knee pads
        p.part(rect(kx, 15, kx + 7, 21), LEATHER if t == 1 else metal if t == 3 else IRON, form="round", bevel=2)
        p.dots([(kx + 1, 16), (kx + 6, 16), (kx + 1, 20), (kx + 6, 20)], metal, 4 if t > 1 else 2)
    p.part(rect(21, 7, 26, 12), LEATHER, bevel=1.4, lift=1)                 # the hip pouch
    p.put(23, 8, metal, 4)
    if t == 1:
        p.dots([(8, 25), (9, 26), (21, 11)], cloth, 0)
        p.shift(rect(5, 27, 13, 27), -1), p.shift(rect(19, 27, 27, 27), -1)
    else:
        p.paint(rect(4, 26, 13, 28), LEATHER), p.paint(rect(18, 26, 27, 28), LEATHER)   # leather shin cuffs
        p.line(7, 7, 7, 13, WOOD, 3), p.dots([(6, 7), (8, 7), (6, 8), (8, 8)], IRON, 3)   # a chisel on the left hip
        if t == 3:
            p.gem(8.5, 18, 1.4, AMBER), p.gem(22.5, 18, 1.4, AMBER)
            p.paint(rect(4, 28, 13, 28), GOLD), p.paint(rect(18, 28, 27, 28), GOLD)
            p.spark(2, 2, LAMP, big=True), p.spark(30, 30, LAMP)
    return p


def miner_boots(t):
    p = Pic()
    body = (LEATHER, SOOT, SOOT)[t - 1]
    metal = (IRON, IRON, GOLD)[t - 1]
    for ox, oy in ((1, 4), (16, 9)):
        boot(p, ox, oy, body, DARKSTEEL, shaft=9, toe=13.5, heel=0)
        p.part(rect(ox, oy + 17, ox + 13, oy + 19), DARKSTEEL, bevel=1)
        cap = poly([(ox + 8.5, oy + 10.5), (ox + 15, oy + 10.5), (ox + 15, oy + 17), (ox + 8.5, oy + 17)])
        p.paint(cap, metal, 1)                                              # the toe cap
        p.shift(rect(ox + 9, oy + 11, ox + 9, oy + 16), -2)
        p.part(rect(ox, oy + 3, ox + 9, oy + 5), body, bevel=1, lift=1)     # the collar
        if t == 1:
            p.line(ox + 5, oy + 7, ox + 7, oy + 9, ROPE, 2)
        else:
            p.paint(rect(ox + 1, oy + 8, ox + 8, oy + 9), LEATHER, 1)
            p.part(rect(ox + 3, oy + 7, ox + 5, oy + 10), metal, bevel=1)   # the buckle
            p.put(ox + 2, oy + 13, metal, 4), p.put(ox + 2, oy + 15, metal, 4)   # heel rivets
            if t == 3:
                p.gem(ox + 12, oy + 13.5, 1.2, AMBER)
                p.paint(rect(ox, oy + 3, ox + 9, oy + 3), GOLD)
    if t == 3:
        p.spark(14, 3, LAMP, big=True), p.spark(30, 5, LAMP)
    return p


# ---------------------------------------------------------------------------------------------- void walker
VOID = R("#100a22", "#241644", "#402878", "#6646b0", "#a084e8")
VOIDLIT = R("#1a1238", "#34246a", "#5a3ca4", "#8662d8", "#c0a6ff")
RIFT = R("#4a0a50", "#961496", "#e040d8", "#ff8cf4", "#ffe2ff")


def void_trim(t):
    return (DARKSTEEL, SILVER, GOLD)[t - 1]


def void_walker_helmet(t):
    p = Pic()
    if t >= 2:                                                              # horns
        p.part(both(poly([(7, 11), (3, 2.5) if t == 2 else (1.5, 0.5), (10, 6)])), VOIDLIT, bevel=1.4)
    if t == 3:
        p.part(poly([(13.5, 5), (16, 0), (18.5, 5)]), VOIDLIT, bevel=1.2)
    helm = sym([(16, 2.5), (10, 4), (6, 9), (5, 19), (7, 25), (11.5, 30.5), (14.5, 26.5), (16, 27.5)])
    p.part(helm, VOID, form="round", bevel=5)
    cheek = poly([(5, 15), (11, 16), (12.5, 22), (11.5, 30.5), (7, 25)])
    p.part(both(cheek), VOIDLIT if t > 1 else VOID, bevel=1.6)
    p.part(poly([(14.5, 2.5), (17.5, 2.5), (18, 14), (16, 16), (14, 14)]), VOIDLIT if t > 1 else VOID, bevel=1.2, lift=1)   # the crest
    glow = RIFT if t > 1 else AMETHYST
    slit = union(poly([(8, 14.5), (15, 16.5), (15, 18.5), (8.5, 17)]), poly([(24, 14.5), (17, 16.5), (17, 18.5), (23.5, 17)]),
                 rect(15, 17, 16, 26))
    p.fill(slit, SHADE, 0)
    p.fill(both(poly([(9, 15.5), (14, 17), (14, 18), (9.5, 16.8)])), glow, 2 if t == 1 else 3)
    if t >= 2:
        p.dots([(10, 16), (21, 16)], glow, 4)
        trim = void_trim(t)
        p.paint(sub(helm, move(helm, 0, -1.3), rect(13, 24, 18, 31)), trim)
        p.dots([(7, 12), (24, 12)], trim, 4)
    if t == 3:
        p.facet([(16, 6.5), (18.5, 10), (16, 13.5), (13.5, 10)], RIFT, GOLD)
        p.fill(rect(15, 20, 16, 25), RIFT, 2)
        p.spark(3, 28, RIFT, big=True), p.spark(29, 27, RIFT), p.spark(27, 3, RIFT)
    return p


def void_walker_chestplate(t):
    p = Pic()
    torso = sym([(12, 4), (8, 6), (7, 14), (9.5, 24), (12, 29), (16, 30.5)])
    p.part(torso, VOID, form="round", bevel=4)
    pad = poly([(9.5, 3.5), (3, 4.5), (0.8, 10), (2, 15.5), (6, 13.5), (9.5, 10.5)])
    if t >= 2:                                                              # spikes on the pauldrons
        for tip in (((4, 6), (1, 0.5)), ((7, 5), (7, 0.5) if t == 3 else (6.5, 2))):
            spike = poly([(tip[0][0] - 1.8, tip[0][1]), tip[1], (tip[0][0] + 1.8, tip[0][1])])
            p.part(both(spike), VOIDLIT, bevel=1.2)
    p.part(both(pad), VOIDLIT if t > 1 else VOID, form="round", bevel=2.5)
    p.shift(both(seg(2.5, 10, 8.5, 8, 0.5)), -2)                            # lamellae
    p.erase(poly([(12.5, 2), (16, 7), (19.5, 2)]))
    plate = sym([(16, 8), (10.5, 10), (10, 17), (16, 22)])
    p.part(plate, VOIDLIT if t > 1 else VOID, bevel=1.6, lift=0)
    for y in (23.5, 26):                                                    # abdominal plates
        p.shift(inter(torso, sub(sym([(16, y), (9, y - 2.5), (9, y - 1.5), (16, y + 1)]))), -2)
    glow = RIFT if t > 1 else AMETHYST
    if t == 1:
        p.facet([(16, 12.5), (18, 15), (16, 17.5), (14, 15)], AMETHYST)
    else:
        trim = void_trim(t)
        p.paint(sub(plate, move(plate, 0, -1.3)), trim)
        p.paint(both(sub(pad, move(pad, 0, -1.3))), trim)
        p.facet([(16, 10.5), (19.5, 15), (16, 19.5), (12.5, 15)], RIFT, trim)
        p.fill(rect(15, 23, 16, 28), glow, 2), p.put(15, 23, glow, 4)
        if t == 3:
            p.fill(both(seg(11, 11.5, 8, 15, 0.5)), RIFT, 3, only=True)     # rifts across the ribs
            p.fill(both(seg(10.5, 19, 9.5, 23, 0.5)), RIFT, 2, only=True)
            p.spark(16, 1, RIFT, big=True), p.spark(2, 25, RIFT), p.spark(29, 27, RIFT)
    return p


def void_walker_leggings(t):
    p = Pic()
    left = poly([(6, 5), (16, 5), (16, 10.5), (14.4, 12.5), (12.8, 24), (13.6, 29.5), (6, 29.5), (6.5, 24), (4.5, 13)])
    p.part(both(left), VOID, form="round", bevel=4)
    p.part(rect(5, 3, 26, 6), VOIDLIT if t > 1 else VOID, bevel=1.5, lift=1 if t == 1 else 0)
    tasset = poly([(4, 6), (11.5, 6), (11, 13), (7, 17), (3.5, 12)])        # hip plates
    p.part(both(tasset), VOIDLIT if t > 1 else VOID, bevel=1.6)
    knee = poly([(6, 17), (9.5, 15) if t == 1 else (9.5, 13.5), (13, 17), (12, 22), (7, 22)])
    p.part(both(knee), VOIDLIT if t > 1 else VOID, bevel=1.5, lift=1 if t == 1 else 0)
    p.shift(both(rect(7, 25, 12, 25)), -2)                                  # greave seam
    glow = RIFT if t > 1 else AMETHYST
    p.facet([(16, 2.5), (18, 4.5), (16, 7.5), (14, 4.5)], glow, void_trim(t) if t > 1 else None)
    if t >= 2:
        trim = void_trim(t)
        p.paint(both(sub(tasset, move(tasset, 0, -1.3))), trim)
        p.paint(both(rect(5, 28, 14, 29)), trim)
        p.fill(both(rect(9, 18, 10, 20)), glow, 3), p.dots([(9, 18), (22, 18)], glow, 4)
        if t == 3:
            p.paint(both(sub(knee, move(knee, 0, -1.3))), trim)
            p.fill(both(seg(8, 8, 6.5, 12, 0.5)), RIFT, 3, only=True)
            p.fill(both(rect(9, 23, 9, 27)), RIFT, 2, only=True)
            p.spark(2, 22, RIFT, big=True), p.spark(30, 2, RIFT)
    return p


def void_walker_boots(t):
    p = Pic()
    glow = RIFT if t > 1 else AMETHYST
    for ox, oy in ((0, 4), (15, 9)):
        boot(p, ox, oy, VOID, SHADE, shaft=10, toe=15.5, width=8)
        p.part(poly([(ox + 9, oy + 11), (ox + 16.5, oy + 15.5), (ox + 16.5, oy + 18), (ox + 9, oy + 18)]), VOIDLIT if t > 1 else VOID,
               bevel=1.4, lift=1 if t == 1 else 0)                          # the pointed sabaton
        p.shift(rect(ox + 11, oy + 13, ox + 11, oy + 17), -2), p.shift(rect(ox + 13, oy + 15, ox + 13, oy + 17), -2)
        shin = poly([(ox + 0.5, oy + 2), (ox + 9.5, oy + 2) if t == 1 else (ox + 11, oy + 0.5), (ox + 8.5, oy + 9), (ox + 1.5, oy + 9)])
        p.part(shin, VOIDLIT if t > 1 else VOID, bevel=1.5, lift=1 if t == 1 else 0)
        p.put(ox + 4, oy + 12, glow, 3), p.put(ox + 5, oy + 12, glow, 2)
        if t >= 2:
            trim = void_trim(t)
            p.paint(sub(shin, move(shin, 0, -1.3)), trim)
            p.fill(rect(ox + 4, oy + 4, ox + 5, oy + 7), glow, 3), p.put(ox + 4, oy + 4, glow, 4)
            p.part(poly([(ox + 0.5, oy + 13), (ox - 2.5 if t == 3 else ox - 1.5, oy + 10.5), (ox + 1.5, oy + 16)]), VOIDLIT, bevel=1)   # a heel spur
            if t == 3:
                p.paint(rect(ox + 9, oy + 17, ox + 16, oy + 17), GOLD)
                p.fill(rect(ox + 10, oy + 14, ox + 10, oy + 16), RIFT, 3, only=True)
    if t == 3:
        p.spark(13, 2, RIFT, big=True), p.spark(30, 4, RIFT)
    return p


CLASSES = {
    "farmer": (farmer_helmet, farmer_chestplate, farmer_leggings, farmer_boots),
    "frostborne": (frostborne_helmet, frostborne_chestplate, frostborne_leggings, frostborne_boots),
    "hunter": (hunter_helmet, hunter_chestplate, hunter_leggings, hunter_boots),
    "lumberjack": (lumberjack_helmet, lumberjack_chestplate, lumberjack_leggings, lumberjack_boots),
    "miner": (miner_helmet, miner_chestplate, miner_leggings, miner_boots),
    "void_walker": (void_walker_helmet, void_walker_chestplate, void_walker_leggings, void_walker_boots),
}
SLOTS = ("helmet", "chestplate", "leggings", "boots")


def all_icons():
    return {f"{cls}_{slot}_t{t}": fn(t) for cls, fns in CLASSES.items() for slot, fn in zip(SLOTS, fns) for t in (1, 2, 3)}
