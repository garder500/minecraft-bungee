#!/usr/bin/env python3
"""The frame system every ValoBlock menu is built from (stdlib only, deterministic, no file output of its own).

One look for the agent select, the Armory, the tactical map and the map vote: a dark tactical panel with a soft vertical
gradient, angled bars in the menu's accent colour, bevelled slot wells (inner shadow top-left, highlight bottom-right) where
items sit, raised buttons and a framed bay lit from behind. Everything is drawn at 1 px per GUI px; valoblock_ui.cut_background
scales it for the font. Shapes only: no letters (every word of a menu is a window title or an item tooltip).

The second half paints 32 x 32 item glyphs from masks (role glyphs, the empty seat, the padlock of Lock in, the die of Random,
the back arrow and the cancel cross): strokes are at least 2 texels wide, so they hold at GUI scale 1 to 4.
"""
import math

CLEAR = (0, 0, 0, 0)


def col(hex6, a=255):
    hex6 = hex6.lstrip("#")
    return (int(hex6[0:2], 16), int(hex6[2:4], 16), int(hex6[4:6], 16), a)


def mix(a, b, t):
    """a towards b by t (0..1); the result is opaque."""
    return (round(a[0] + (b[0] - a[0]) * t), round(a[1] + (b[1] - a[1]) * t), round(a[2] + (b[2] - a[2]) * t), 255)


BLACK, WHITE = col("000000"), col("ffffff")
INK = col("05060a")                               # the darkest line: outlines and the shadow edge of a well
BG_TOP, BG_BOTTOM = col("1c2231"), col("0e1118")  # the panel, top to bottom
TITLE_TOP, TITLE_BOTTOM = col("090b11"), col("10141d")
RIM_LIGHT, RIM_DARK = col("3a4460"), col("090b10")
WELL, WELL_LIGHT = col("0b0d14"), col("2e374b")   # a slot well and the highlight on its bottom-right edge
STEEL = col("9aa3b5")

# The accent of each menu.
ACCENT_SELECT, ACCENT_ARMORY, ACCENT_TAC, ACCENT_VOTE = col("c9a8ff"), col("ffd36a"), col("7fc8e8"), col("c9a8ff")
GREEN, RED, TEAL, BLUE = col("5ad07a"), col("e8636f"), col("6affd8"), col("7fc8e8")


def canvas(w, h, fill=CLEAR):
    return [[fill] * w for _ in range(h)]


def put(img, x, y, c):
    """One pixel, alpha blended over what is there (the result keeps the destination's opacity when it had one)."""
    if not (0 <= y < len(img) and 0 <= x < len(img[0])) or c[3] == 0:
        return
    d = img[y][x]
    if c[3] == 255 or d[3] == 0:
        img[y][x] = c
        return
    t = c[3] / 255
    img[y][x] = (round(d[0] + (c[0] - d[0]) * t), round(d[1] + (c[1] - d[1]) * t), round(d[2] + (c[2] - d[2]) * t), max(d[3], c[3]))


def rect(img, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            put(img, xx, yy, c)


def vgrad(img, x, y, w, h, top, bottom):
    for k in range(h):
        c = mix(top, bottom, k / max(1, h - 1))
        for xx in range(x, x + w):
            put(img, xx, y + k, c)


def tint(img, x, y, w, h, c, t):
    """Mixes a box towards a colour."""
    for yy in range(max(0, y), min(len(img), y + h)):
        for xx in range(max(0, x), min(len(img[0]), x + w)):
            img[yy][xx] = mix(img[yy][xx], c, t)


def bevel(img, x, y, w, h, top_left, bottom_right):
    """A 1 px ring: one colour on the top and left edges, another on the bottom and right, their mix in the two other corners."""
    for k in range(w):
        put(img, x + k, y, top_left)
        put(img, x + k, y + h - 1, bottom_right)
    for k in range(h):
        put(img, x, y + k, top_left)
        put(img, x + w - 1, y + k, bottom_right)
    corner = mix(top_left, bottom_right, 0.5)
    put(img, x + w - 1, y, corner)
    put(img, x, y + h - 1, corner)


def well(img, x, y, w=18, h=18, fill=WELL, light=WELL_LIGHT, glow=None):
    """A recessed slot: dark floor, an inner shadow under the top and left edges, a highlight on the bottom and right ones.
    The 16 x 16 item area of a chest slot is the inside of an 18 x 18 well. `glow` tints the floor (a state or a team colour)."""
    floor = fill if glow is None else mix(fill, glow, 0.16)
    vgrad(img, x + 1, y + 1, w - 2, h - 2, floor, mix(floor, WHITE, 0.05))
    shade = mix(floor, BLACK, 0.45)
    for k in range(1, w - 1):
        put(img, x + k, y + 1, shade)
    for k in range(1, h - 1):
        put(img, x + 1, y + k, shade)
    bevel(img, x, y, w, h, INK, light if glow is None else mix(light, glow, 0.45))


def raised(img, x, y, w, h, colour, cut=2):
    """A button: a plate lit from the top (bright top edge, body gradient, dark bottom edge) with cut corners."""
    top, bottom = mix(colour, WHITE, 0.12), mix(colour, BLACK, 0.45)
    backdrop = [row[x:x + w] for row in img[y:y + h]]
    vgrad(img, x, y, w, h, top, bottom)
    for k in range(w):
        put(img, x + k, y, INK)
        put(img, x + k, y + h - 1, INK)
        put(img, x + k, y + 1, mix(colour, WHITE, 0.55))
        put(img, x + k, y + h - 2, mix(colour, BLACK, 0.68))
    for k in range(h):
        put(img, x, y + k, INK)
        put(img, x + w - 1, y + k, INK)
    for k in range(2, h - 2):
        put(img, x + 1, y + k, mix(colour, WHITE, 0.25))
        put(img, x + w - 2, y + k, mix(colour, BLACK, 0.55))
    for dy in range(cut):                      # the corners go back to what was under the button, with a dark diagonal
        for dx in range(cut - dy):
            for cx, cy in ((dx, dy), (w - 1 - dx, dy), (dx, h - 1 - dy), (w - 1 - dx, h - 1 - dy)):
                img[y + cy][x + cx] = backdrop[cy][cx]
    if cut:
        for k in range(cut + 1):
            for cx, cy in ((k, cut - k), (w - 1 - k, cut - k), (k, h - 1 - cut + k), (w - 1 - k, h - 1 - cut + k)):
                put(img, x + cx, y + cy, INK)


def slashes(img, x, y, w, h, c, step=6, thick=3, fade=None):
    """Angled bars ( / / / ) clipped to a box. fade = (alpha at the left, alpha at the right), 0..1 each."""
    for yy in range(h):
        for xx in range(w):
            if (xx + yy) % step < thick:
                a = 1.0 if fade is None else fade[0] + (fade[1] - fade[0]) * xx / max(1, w - 1)
                put(img, x + xx, y + yy, (c[0], c[1], c[2], round(c[3] * a)))


def bar(img, x, y, w, h, c, lean=1):
    """One angled bar: a parallelogram whose top is `lean` px per row further right than its bottom."""
    for yy in range(h):
        off = (h - 1 - yy) * lean
        for xx in range(w):
            put(img, x + off + xx, y + yy, c)


def glow(img, x, y, w, h, cx, cy, rx, ry, c, strength):
    """A soft light inside a box: strongest at (cx, cy), gone at the ellipse (rx, ry)."""
    for yy in range(max(0, y), min(len(img), y + h)):
        for xx in range(max(0, x), min(len(img[0]), x + w)):
            d = math.hypot((xx - cx) / rx, (yy - cy) / ry)
            if d < 1:
                img[yy][xx] = mix(img[yy][xx], c, strength * (1 - d) ** 2)


def brackets(img, x, y, w, h, c, n=6):
    """Four corner brackets on the ring of a box."""
    for k in range(n):
        for px, py in ((x + k, y), (x, y + k), (x + w - 1 - k, y), (x + w - 1, y + k),
                       (x + k, y + h - 1), (x, y + h - 1 - k), (x + w - 1 - k, y + h - 1), (x + w - 1, y + h - 1 - k)):
            put(img, px, py, c)


def window(accent, w=176, h=222):
    """The 176 x 222 window every menu starts from: the panel, the title strip with its accent, the player's inventory tray.
    Fully opaque (the vanilla chest texture is under it). The title text starts at x = 8 and its length varies, so the strip
    keeps its accent at the far left and the last 16 px; the vanilla 'Inventory' label (dark grey, y 128) sits on the dark band."""
    img = canvas(w, h, BG_BOTTOM)
    vgrad(img, 0, 0, w, h, BG_TOP, BG_BOTTOM)
    bevel(img, 0, 0, w, h, INK, INK)
    bevel(img, 1, 1, w - 2, h - 2, RIM_LIGHT, RIM_DARK)
    # title strip
    vgrad(img, 2, 2, w - 4, 13, TITLE_TOP, TITLE_BOTTOM)
    rect(img, 2, 2, 3, 13, accent)
    rect(img, 5, 2, 1, 13, mix(accent, BLACK, 0.6))
    slashes(img, w - 20, 4, 16, 9, accent, 6, 3, (0.2, 0.9))     # short: the longest title ends near x = 155
    for k in range(w - 4):                                       # the accent line under the title fades out to the right
        t = max(0.0, 1 - k / (w - 40))
        put(img, 2 + k, 15, mix(mix(TITLE_BOTTOM, BLACK, 0.4), accent, t))
    rect(img, 2, 16, w - 4, 1, mix(BG_TOP, BLACK, 0.35))
    # the band over the inventory: a dim trio of bars at its right end
    slashes(img, w - 31, 129, 24, 6, mix(accent, BG_BOTTOM, 0.55), 6, 3, (0.0, 0.8))
    # the inventory tray: 27 wells, a seam, the 9 hotbar wells
    tray(img, 5, 137, 166, 80)
    for r in range(3):
        for c in range(9):
            well(img, 7 + 18 * c, 139 + 18 * r)
    for c in range(9):
        well(img, 7 + 18 * c, 197)
    rect(img, 9, 194, 158, 1, mix(accent, BG_BOTTOM, 0.7))
    return img


def tray(img, x, y, w, h, fill=None):
    """A shallow recess that groups wells."""
    fill = fill or mix(BG_BOTTOM, BLACK, 0.35)
    vgrad(img, x + 1, y + 1, w - 2, h - 2, fill, mix(fill, BLACK, 0.2))
    bevel(img, x, y, w, h, INK, mix(RIM_LIGHT, BG_BOTTOM, 0.45))


def bay(img, x, y, w, h, accent):
    """A framed bay lit from behind (the portrait of the agent select): accent frame with bright corners, a glow where the head is."""
    bevel(img, x, y, w, h, INK, INK)
    frame = mix(accent, BG_BOTTOM, 0.5)
    bevel(img, x + 1, y + 1, w - 2, h - 2, frame, mix(frame, BLACK, 0.3))
    brackets(img, x + 1, y + 1, w - 2, h - 2, accent, 8)
    ix, iy, iw, ih = x + 2, y + 2, w - 4, h - 4
    vgrad(img, ix, iy, iw, ih, mix(accent, BLACK, 0.78), mix(accent, BLACK, 0.93))
    glow(img, ix, iy, iw, ih, x + w / 2 - 0.5, y + h * 0.38, w * 0.62, h * 0.52, accent, 0.62)
    for yy in range(iy, iy + ih, 2):                             # faint scan lines: a screen, not a wall
        tint(img, ix, yy, iw, 1, BLACK, 0.10)
    vgrad(img, ix, iy + ih - 10, iw, 10, mix(accent, BLACK, 0.86), mix(accent, BLACK, 0.95))
    rect(img, ix, iy + ih - 11, iw, 1, mix(accent, BLACK, 0.45))  # the floor line behind the figure
    for k in range(iw):                                          # inner shadow under the top edge
        put(img, ix + k, iy, mix(img[iy][ix + k], BLACK, 0.5))
    for k in range(ih):
        put(img, ix, iy + k, mix(img[iy + k][ix], BLACK, 0.4))


# ------------------------------------------------------------------ item glyphs (32 x 32)

SIZE = 32


def poly(pts):
    """Mask of a polygon (even-odd), sampled at pixel centres."""
    def inside(x, y):
        x, y, hit = x + 0.5, y + 0.5, False
        for i in range(len(pts)):
            (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % len(pts)]
            if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
                hit = not hit
        return hit
    return inside


def disc(cx, cy, r, ry=None):
    ry = r if ry is None else ry
    return lambda x, y: ((x + 0.5 - cx) / r) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1


def ring(cx, cy, r_out, r_in):
    return lambda x, y: r_in ** 2 < (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r_out ** 2


def box(x0, y0, x1, y1):
    return lambda x, y: x0 <= x < x1 and y0 <= y < y1


def union(*ms):
    return lambda x, y: any(m(x, y) for m in ms)


def both(*ms):
    return lambda x, y: all(m(x, y) for m in ms)


def minus(m, *nots):
    return lambda x, y: m(x, y) and not any(n(x, y) for n in nots)


def fill(img, mask, colour, shade=True):
    """Paints a mask: lit from the top (a lighter top, a darker bottom), a bright texel on its upper edge, a dark one on its lower."""
    size = len(img)
    for y in range(size):
        for x in range(len(img[0])):
            if not mask(x, y):
                continue
            c = colour
            if shade:
                c = mix(mix(colour, WHITE, 0.22), mix(colour, BLACK, 0.30), y / (size - 1))
                if not mask(x, y - 1):
                    c = mix(c, WHITE, 0.45)
                elif not mask(x, y + 1):
                    c = mix(c, BLACK, 0.35)
            img[y][x] = c


def outline(img, c=INK):
    """A 1 texel outline on the transparent texels round the art, corners included, so a glyph holds on any background."""
    h, w = len(img), len(img[0])
    add = [(y, x) for y in range(h) for x in range(w) if img[y][x][3] == 0 and any(
        0 <= y + dy < h and 0 <= x + dx < w and img[y + dy][x + dx][3] == 255 and img[y + dy][x + dx] != c
        for dy in (-1, 0, 1) for dx in (-1, 0, 1))]
    for y, x in add:
        img[y][x] = c
    return img


def glyph(mask, colour, size=SIZE):
    img = canvas(size, size)
    fill(img, mask, colour)
    return outline(img)


def role_mask(name):
    """The shape of a role, by the role's name in agents/index.json; an unknown role gets a plain diamond."""
    if name == "vanguard":   # opens the site: two rank chevrons pointing forward
        return union(poly([(16, 3), (28, 15), (28, 21), (16, 9), (4, 21), (4, 15)]),
                     poly([(16, 13), (26, 23), (26, 29), (16, 19), (6, 29), (6, 23)]))
    if name == "warden":     # holds ground: a shield with a diamond boss cut out of it (no letter-like cut)
        shield = poly([(5, 4), (27, 4), (27, 16), (16, 29), (5, 16)])
        return minus(shield, poly([(16, 8), (22, 15), (16, 22), (10, 15)]))
    if name == "seer":       # finds enemies: an open eye under three rays
        lens = both(disc(16, 8, 18), disc(16, 32, 18))
        eye = minus(lens, minus(disc(16, 20, 5.4), disc(16, 20, 2.6)))
        return union(eye, box(15, 3, 17, 10), poly([(6, 6), (8, 5), (11, 11), (9, 12)]), poly([(26, 6), (24, 5), (21, 11), (23, 12)]))
    if name == "weaver":     # cuts sightlines with smoke: three woven rings
        return union(ring(16, 10.5, 7.5, 4.5), ring(10, 20.5, 7.5, 4.5), ring(22, 20.5, 7.5, 4.5))
    return poly([(16, 4), (28, 16), (16, 28), (4, 16)])


def role_glyph(name, colour):
    return glyph(role_mask(name), colour)


def seat_glyph(colour=col("4a5368")):
    """An empty seat: the unlit bust of nobody."""
    head = disc(16, 11, 5.5)
    shoulders = both(disc(16, 29, 11, 10), box(0, 0, SIZE, 28))
    return glyph(union(head, shoulders), colour)


def lock_glyph(colour=col("f2fff5")):
    """Lock in: a closed padlock. Drawn pale: it sits on the green button the background paints."""
    shackle = both(ring(16, 13, 7.5, 4.5), box(0, 0, SIZE, 14))
    legs = union(box(8, 13, 12, 17), box(20, 13, 24, 17))   # 8.5..11.5 and 20.5..23.5: the ring's two ends
    body = poly([(6, 16), (26, 16), (26, 27), (24, 29), (8, 29), (6, 27)])
    keyhole = union(disc(16, 21, 2.2), box(15, 21, 17, 26))
    img = canvas(SIZE, SIZE)
    fill(img, union(shackle, legs), mix(colour, col("5ad07a"), 0.25))
    fill(img, body, colour)
    fill(img, both(body, keyhole), col("14532b"), shade=False)
    return outline(img, col("0c3a1e"))


def die_glyph():
    """Random: a die seen from a corner, three faces (one, two and three pips)."""
    top = poly([(16, 3), (27, 9), (16, 15), (5, 9)])
    left = poly([(5, 9), (16, 15), (16, 29), (5, 23)])
    right = poly([(27, 9), (27, 23), (16, 29), (16, 15)])
    img = canvas(SIZE, SIZE)
    fill(img, top, col("f4f6fb"), shade=False)
    fill(img, left, col("c0c7d6"), shade=False)
    fill(img, right, col("8891a5"), shade=False)
    for k in range(11):                             # the three ridges from the near corner
        put(img, 16 - k, 15 - (k * 6 + 5) // 11, WHITE)
        put(img, 16 + k, 15 - (k * 6 + 5) // 11, mix(col("f4f6fb"), BLACK, 0.12))
    rect(img, 16, 16, 1, 13, mix(col("c0c7d6"), WHITE, 0.5))
    pip = col("1a1f2b")
    for cx, cy in ((15, 8),):                       # top: one
        rect(img, cx, cy, 3, 2, pip)
    for cx, cy in ((8, 15), (12, 23)):              # left: two
        rect(img, cx, cy, 2, 3, pip)
    for cx, cy in ((19, 23), (21, 18), (24, 14)):   # right: three
        rect(img, cx, cy, 2, 3, pip)
    return outline(img)


def arrow_glyph(colour=col("e6eaf2"), size=16):
    """Back: an arrow pointing left (16 x 16)."""
    m = union(poly([(2, 8), (8, 2), (8, 6), (14, 6), (14, 10), (8, 10), (8, 14)]))
    return glyph(m, colour, size)


def cross_glyph(colour=col("ff7480"), size=16):
    """Cancel: a cross (16 x 16)."""
    m = union(poly([(3, 5), (5, 3), (13, 11), (11, 13)]), poly([(11, 3), (13, 5), (5, 13), (3, 11)]))
    return glyph(m, colour, size)
