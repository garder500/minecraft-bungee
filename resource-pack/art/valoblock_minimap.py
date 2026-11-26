#!/usr/bin/env python3
"""The ValoBlock minimap art (stdlib only, deterministic): one framed plan per map and the markers laid over it.

Called by valoblock_ui.py, which puts every sprite in the vampyuyu:vbhud font. The minimap is the "minimap" HUD element of
vb_layout.py (top-left corner, 80 x 80 GUI px); the core shader stretches the plan glyph over that box and centres each marker glyph
on the point its colour carries (red 86 small / 87 big, green = x, blue = y, see vb_layout.MM_*). Textures are drawn at
vb.MM_TEX texels a GUI px, so the plan stays sharp at GUI scale 2 and 4.

  mm_<map>      the panel: a dark glass plate, a steel rim, corner brackets in the map's Realm colour and the plan of
                valoblock_tacmap.paint (the tactical map's painter: floor, walls, sites with their pips, spawns) in it
  mm_ally       a squadmate: a green disc               mm_enemy   a revealed enemy: a red diamond
  mm_dead       a fallen squadmate: a grey cross        mm_seed    the Rift Seed (planted, or dropped for the Seedbearers)
  mm_self_<k>   you: a white arrow and a soft view cone, turned k x 22.5 degrees clockwise from north (k = 0..15)
"""
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import valoblock_tacmap as tac  # noqa: E402
import vb_layout as vb  # noqa: E402

CLEAR = (0, 0, 0, 0)
INK = (5, 6, 10, 255)


def col(hex6, a=255):
    return tac.col(hex6, a)


def canvas(w, h, fill=CLEAR):
    return [[fill] * w for _ in range(h)]


def over(dst, x, y, c):
    """Alpha-blends the colour c over the pixel (x, y)."""
    if not (0 <= y < len(dst) and 0 <= x < len(dst[0])) or c[3] == 0:
        return
    r, g, b, a = dst[y][x]
    sa = c[3] / 255
    na = sa + a / 255 * (1 - sa)
    if na <= 0:
        return
    mixc = [round((c[i] * sa + (r, g, b)[i] * a / 255 * (1 - sa)) / na) for i in range(3)]
    dst[y][x] = (mixc[0], mixc[1], mixc[2], round(na * 255))


def shape(size, inside, colour, ss=4):
    """A size x size layer: each pixel gets colour with the alpha of its covered fraction (ss x ss samples of inside(x, y))."""
    img = canvas(size, size)
    for y in range(size):
        for x in range(size):
            n = sum(inside(x + (i + 0.5) / ss, y + (j + 0.5) / ss) for i in range(ss) for j in range(ss))
            if n:
                img[y][x] = colour[:3] + (round(colour[3] * n / (ss * ss)),)
    return img


def stack(*layers):
    out = canvas(len(layers[0][0]), len(layers[0]))
    for layer in layers:
        for y, row in enumerate(layer):
            for x, c in enumerate(row):
                over(out, x, y, c)
    return out


# ------------------------------------------------------------------ the panel


def panel(mid):
    """The framed plan of one map, (80 x MM_TEX) texels square."""
    k = vb.MM_TEX
    size = 80 * k
    inset = vb.MM_INSET * k
    realm = col(vb.VOTE_REALM.get(mid, "9aa3b5"))
    img = canvas(size, size)
    glass, rim, rim_hi = (9, 11, 18, 205), (52, 58, 74, 255), (130, 140, 162, 255)
    cut = 3                                         # clipped corners: the plate reads as a device, not a square of paint
    for y in range(size):
        for x in range(size):
            if min(x, size - 1 - x) + min(y, size - 1 - y) < cut:
                continue
            edge = min(x, y, size - 1 - x, size - 1 - y)
            img[y][x] = INK if edge < 1 else rim if edge < 3 else rim_hi if edge < 4 else glass
    plan = tac.paint(tac.load(mid), 0, 0, len(tac.load(mid)["plan"]), size - 2 * inset)
    void = tac.VOID
    for y, row in enumerate(plan):
        for x, c in enumerate(row):
            img[inset + y][inset + x] = (12, 15, 24, 150) if c == void else c   # the dark outside the walls lets the world show through
    # corner brackets in the Realm colour, over the rim
    arm, t = 14, 3
    bright = tuple(min(255, round(v * 1.15)) for v in realm[:3]) + (255,)
    for cx, cy, sx, sy in ((0, 0, 1, 1), (size - 1, 0, -1, 1), (0, size - 1, 1, -1), (size - 1, size - 1, -1, -1)):
        for i in range(arm):
            for j in range(t):
                for x, y in ((cx + sx * (i + 1), cy + sy * (j + 1)), (cx + sx * (j + 1), cy + sy * (i + 1))):
                    img[y][x] = bright if j == 1 else realm
    # the north tick: a small notch on the top rim
    for dy in range(5):
        for dx in range(-4 + dy, 5 - dy):
            img[dy + 1][size // 2 + dx] = bright if dy < 4 else realm
    return img


# ------------------------------------------------------------------ markers


def disc(size, r, colour, cx=None, cy=None):
    cx = size / 2 if cx is None else cx
    cy = size / 2 if cy is None else cy
    return shape(size, lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r, colour)


def diamond(size, r, colour):
    c = size / 2
    return shape(size, lambda x, y: abs(x - c) + abs(y - c) <= r, colour)


def ally():
    s = vb.MM_SMALL * vb.MM_TEX
    return stack(disc(s, 6.6, INK), disc(s, 5.2, col("4cc76d")), disc(s, 2.2, col("b9f5c8"), s / 2 - 1.4, s / 2 - 1.4))


def enemy():
    s = vb.MM_SMALL * vb.MM_TEX
    return stack(diamond(s, 7.6, INK), diamond(s, 6.0, col("ff3b3b")), diamond(s, 2.4, col("ffb0a8")))


def dead():
    s = vb.MM_SMALL * vb.MM_TEX
    c = s / 2

    def bar(w):
        return lambda x, y: (abs((x - c) - (y - c)) <= w or abs((x - c) + (y - c)) <= w) and abs(x - c) <= 5.5 and abs(y - c) <= 5.5

    return stack(shape(s, lambda x, y: bar(2.6)(x, y) or (abs(x - c) <= 6.6 and abs(y - c) <= 6.6 and bar(2.6)(x, y)), INK),
                 shape(s, bar(1.2), col("c8ccd8")))


def seed():
    s = vb.MM_SMALL * vb.MM_TEX
    return stack(diamond(s, 7.8, INK), diamond(s, 6.2, col("ff4a8a")), diamond(s, 3.4, col("ffd0e2")), diamond(s, 1.2, col("ffffff")))


def self_arrow(k):
    """You, heading k: the arrow points k x 22.5 degrees clockwise from up, with a faint 70 degree view cone ahead of it."""
    s = vb.MM_BIG * vb.MM_TEX
    c = s / 2
    a = math.radians(k * 360 / vb.MM_HEADINGS)
    fx, fy = math.sin(a), -math.cos(a)          # forward on screen (y down)

    def local(x, y):
        dx, dy = x - c, y - c
        return dx * fx + dy * fy, dx * -fy + dy * fx   # (along, across)

    def cone(x, y):
        u, v = local(x, y)
        return u > 0 and math.hypot(u, v) <= c - 0.5 and abs(math.atan2(v, u)) <= math.radians(35)

    def arrow(grow):
        tip, back, wing, notch = 8.0 + grow, -5.0 - grow * 0.6, 4.6 + grow, -1.6 + grow * 0.4

        def inside(x, y):
            u, v = local(x, y)
            if u > tip or u < back:
                return False
            # a chevron: inside the two edges from the tip to the wing points, and ahead of the notched back edge
            # (at the middle the back is at `notch`, at the wing points at `back`)
            half = (tip - u) / (tip - back) * wing
            if abs(v) > half:
                return False
            return u >= notch - abs(v) * (notch - back) / wing

        return inside

    cone_layer = canvas(s, s)
    for y in range(s):
        for x in range(s):
            u, v = local(x + 0.5, y + 0.5)
            if cone(x + 0.5, y + 0.5):
                fade = max(0.0, 1 - math.hypot(u, v) / (c - 0.5))
                cone_layer[y][x] = (230, 240, 255, round(24 + 70 * fade))
    return stack(cone_layer, shape(s, arrow(1.6), INK), shape(s, arrow(0), col("ffffff")))


def sprites(maps=vb.TAC_MAPS):
    """(name, rows) of every minimap sprite, in the order the font lists them."""
    out = [(f"mm_{m}", panel(m)) for m in maps]
    out += [("mm_ally", ally()), ("mm_enemy", enemy()), ("mm_dead", dead()), ("mm_seed", seed())]
    out += [(f"mm_self_{k}", self_arrow(k)) for k in range(vb.MM_HEADINGS)]
    return out


if __name__ == "__main__":
    import valoblock_ui as ui
    out = HERE.parent / "dist" / "minimap-preview"
    out.mkdir(parents=True, exist_ok=True)
    for name, rows in sprites():
        ui.write_png(out / f"{name}.png", rows)
    print(f"wrote {len(sprites())} previews to {out}")
