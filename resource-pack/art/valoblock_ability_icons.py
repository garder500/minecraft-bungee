#!/usr/bin/env python3
"""ValoBlock ability icons, stdlib only (no Pillow, no Chrome), deterministic.

    python3 resource-pack/art/valoblock_ability_icons.py [--sheet out.png]

Every one of the 32 abilities of arena-plugin/src/main/resources/valoblock/agents/*.json gets its own original
16x16 icon in its agent's Realm palette (colors.trim, glow, torso). Each icon is a short list of drawing calls
(disc, ring, polygon, line...) below, so nothing is copied from anywhere and the output is the same byte for byte.

Written under resource-pack/valoblock/assets/vampyuyu/ (the item key the plugin sets is
vampyuyu:valoblock/ab_<agent>_<ability>[_empty|_ready]):

    textures/item/valoblock/ab_<agent>_<ability>.png          16x16, the icon
    textures/item/valoblock/ab_<agent>_<ability>_empty.png    the same dimmed and grey (no charge left, kept in the slot)
    textures/item/valoblock/ab_<agent>_<ability>_ready.png    ultimates only: lit, with a gold rim and sparks
    models/item/valoblock/*.json, items/valoblock/*.json      item/generated model and its item definition

valoblock_ui.py draws the same pixels in the HUD ability tiles (icon(), ic_<agent>_<slot>_on / _off).
"""
import json
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import valoblock_items as vi  # noqa: E402

PACK = vi.PACK
AGENTS = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/agents"
CLEAR = (0, 0, 0, 0)
SIZE = 16
OUTLINE = (0x0b, 0x0d, 0x12)
READY_RIM = (0xff, 0xd3, 0x6a)  # the lit ultimate: a gold rim whatever the Realm


# ------------------------------------------------------------------ palette

def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def palette(agent, ultimate=False):
    """k outline, d dark, m main, l light, w highlight, s shade, a accent: all from the agent's Realm colours."""
    c = agent["colors"]
    trim, glow, torso = vi.rgb(c["trim"][1:]), vi.rgb(c["glow"][1:]), vi.rgb(c["torso"][1:])
    black, white = (0, 0, 0), (255, 255, 255)
    if ultimate:  # the ultimate is drawn in the glow colour, like the old placeholder
        trim, glow = glow, mix(glow, white, 0.55)
    return {"k": OUTLINE, "d": mix(trim, black, 0.5), "m": trim, "l": glow, "w": mix(glow, white, 0.6),
            "s": mix(torso, white, 0.12), "a": mix(torso, white, 0.4)}


# ------------------------------------------------------------------ drawing

class Canvas:
    """16x16 grid of palette keys (None = clear). Coordinates are continuous, pixel (x, y) is tested at its centre."""

    def __init__(self):
        self.g = [[None] * SIZE for _ in range(SIZE)]

    def fill(self, inside, key):
        for y in range(SIZE):
            for x in range(SIZE):
                if inside(x + 0.5, y + 0.5):
                    self.g[y][x] = key
        return self

    def disc(self, cx, cy, r, key):
        return self.fill(lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r, key)

    def ell(self, cx, cy, rx, ry, key):
        return self.fill(lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1, key)

    def ring(self, cx, cy, r0, r1, key, a0=0, a1=360):
        """Annulus, optionally only between the angles a0..a1 (degrees, 0 = right, 90 = down)."""
        def inside(x, y):
            d = math.hypot(x - cx, y - cy)
            if not r0 <= d <= r1:
                return False
            if (a1 - a0) >= 360:
                return True
            a = math.degrees(math.atan2(y - cy, x - cx)) % 360
            return (a - a0) % 360 <= (a1 - a0)
        return self.fill(inside, key)

    def rect(self, x0, y0, x1, y1, key):
        return self.fill(lambda x, y: x0 <= x <= x1 and y0 <= y <= y1, key)

    def poly(self, pts, key):
        def inside(x, y):
            c = False
            j = len(pts) - 1
            for i in range(len(pts)):
                (xi, yi), (xj, yj) = pts[i], pts[j]
                if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                    c = not c
                j = i
            return c
        return self.fill(inside, key)

    def line(self, x0, y0, x1, y1, key):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            x, y = int(x0 + (x1 - x0) * i / n), int(y0 + (y1 - y0) * i / n)
            if 0 <= x < SIZE and 0 <= y < SIZE:
                self.g[y][x] = key
        return self

    def dots(self, pts, key):
        for x, y in pts:
            if 0 <= x < SIZE and 0 <= y < SIZE:
                self.g[y][x] = key
        return self

    def wave(self, x0, x1, y, amp, period, key, phase=0.0):
        for x in range(x0, x1 + 1):
            self.dots([(x, y + round(amp * math.sin((x - x0) / period * 2 * math.pi + phase)))], key)
        return self

    def clear(self, pts):
        for x, y in pts:
            self.g[y][x] = None
        return self


# ------------------------------------------------------------------ the 32 icons

def slag_pot(c):
    c.poly([(3, 6), (13, 6), (12, 13), (4, 13)], "d").rect(2, 5, 13.9, 6.9, "m")
    c.ell(8, 6, 4.8, 1.2, "l").dots([(6, 5), (10, 6)], "w")
    c.poly([(4, 7), (6, 7), (5, 13), (4, 13)], "m").rect(7, 9, 8.9, 10.9, "l").dots([(10, 8), (9, 11)], "m")
    c.dots([(5, 14), (11, 14)], "l").dots([(7, 3), (9, 2), (8, 4)], "l")


def heat_shimmer(c):
    for x0, key in ((3, "m"), (7, "l"), (11, "m")):
        for y in range(2, 14):
            c.dots([(x0 + round(1.3 * math.sin((y + x0) * 0.9)), y)], key)
    c.rect(2, 13, 13.9, 13.9, "d").dots([(3, 12), (8, 12), (12, 12)], "a")


def ember_dash(c):
    c.poly([(9, 3), (15, 8), (9, 13), (11, 8)], "l").poly([(9, 5), (13, 8), (9, 11), (10.5, 8)], "m")
    c.line(1, 5, 7, 5, "d").line(0, 8, 8, 8, "m").line(1, 11, 7, 11, "d")
    c.dots([(2, 7), (4, 9), (3, 3), (6, 13), (1, 9)], "l")


def colossus_step(c):
    c.ell(8, 13, 7.4, 2.4, "d").ell(8, 13, 6, 1.6, "m")
    c.line(1, 14, 4, 12, "l").line(15, 14, 12, 12, "l").line(8, 15, 8, 13, "l")
    c.rect(6, 0.5, 9.9, 5, "s").rect(6, 0.5, 7.2, 5, "a")
    c.poly([(3, 5), (11, 5), (13, 8), (13, 10), (3, 10)], "m").rect(3, 8.5, 13, 10, "d").rect(4, 5.6, 9, 6.6, "l")
    c.dots([(1, 8), (14, 7), (2, 5), (14, 4)], "l")


def wind_pad(c):
    c.ell(8, 13, 7, 2, "d").ell(8, 12.5, 5.8, 1.5, "m").ell(8, 12.2, 3.5, 0.7, "l")
    c.poly([(8, 1), (12, 6), (9.5, 6), (9.5, 10), (6.5, 10), (6.5, 6), (4, 6)], "l")
    c.poly([(8, 3), (10, 5.5), (8.8, 5.5), (8.8, 9), (7.2, 9), (7.2, 5.5), (6, 5.5)], "w")
    c.dots([(2, 7), (3, 4), (13, 8), (14, 5)], "m")


def static_orb(c):
    c.disc(8, 8, 6, "d").disc(8, 8, 5, "s").disc(7, 7, 3, "a")
    c.poly([(9, 2), (5, 9), (8, 9), (6, 14), (11, 6.5), (8, 6.5)], "l")
    c.dots([(8, 7), (7, 9), (6, 11), (10, 5)], "w").ring(8, 8, 5, 6, "m", 200, 290)


def charged_slide(c):
    c.poly([(3, 9), (9, 5), (13, 6), (14, 10), (4, 12)], "s").poly([(5, 9), (9, 6.5), (12, 7), (12.5, 9.5), (6, 10.8)], "a")
    c.poly([(11, 1), (6, 8), (9, 8), (7, 13), (13, 5), (10, 5)], "l").dots([(9, 6), (8, 8), (10, 4)], "w")
    c.line(0, 7, 3, 7, "m").line(0, 10, 2, 10, "m").line(0, 13, 5, 13, "d")
    c.rect(1, 14, 14.9, 14.9, "d")


def bolt(c, x, y, key):
    """A two pixel wide lightning zigzag, 7 rows tall, starting at (x, y)."""
    for (x0, y0, x1, y1) in ((x + 1, y, x - 1, y + 3), (x - 1, y + 3, x + 1, y + 4), (x + 1, y + 4, x - 1, y + 7)):
        c.line(x0, y0, x1, y1, key).line(x0 + 1, y0, x1 + 1, y1, key)


def storm_call(c):
    for x, y, r in ((4.5, 4, 2.6), (8, 3, 3.2), (11.5, 4, 2.6)):
        c.disc(x, y, r, "a")
    c.rect(2.5, 4, 13.4, 6, "a").rect(2.5, 6, 13.4, 6.9, "s")
    for x in (3, 7, 11):
        bolt(c, x, 8, "l")
    c.dots([(4, 7), (8, 7), (12, 7)], "w")


def leech_pool(c):
    c.ell(8, 11.5, 7, 3, "d").ell(8, 11, 6, 2.3, "s").ell(8, 11, 4.2, 1.4, "m")
    c.wave(4, 11, 5, 1.6, 8, "l").wave(4, 11, 6, 1.6, 8, "l").wave(4, 11, 7, 1.6, 8, "m")
    c.dots([(11, 4), (12, 5)], "l").dots([(4, 4), (3, 5)], "m").disc(12, 3, 0.9, "w")
    c.dots([(5, 11), (9, 12), (11, 10)], "l")


def rot_cloud(c):
    for x, y, r in ((5, 9, 3.6), (9.5, 8, 4.2), (12, 10, 3), (7.5, 5.5, 3.2)):
        c.disc(x, y, r, "s")
    for x, y, r in ((5, 9, 2.6), (9.5, 8, 3.2), (7.5, 5.5, 2.2)):
        c.disc(x, y, r, "a")
    c.disc(7, 9, 1.1, "l").disc(11, 10, 1, "l").disc(9, 6, 0.9, "m")
    c.dots([(3, 4), (13, 5), (2, 12), (13, 13), (6, 14)], "l")


def miasma_wall(c):
    c.rect(2, 4, 13.9, 14, "s").rect(2, 4, 13.9, 5, "a").rect(2, 13, 13.9, 14, "d")
    for x in (4, 7, 10, 13):
        c.rect(x, 5, x + 0.9, 13, "d")
    c.wave(2, 13, 3, 1.1, 6, "l").wave(2, 13, 2, 1.1, 6, "m", 2)
    c.dots([(5, 8), (8, 9), (11, 7), (6, 11), (10, 11)], "l").dots([(3, 0), (9, 0), (13, 1)], "m")


def bog_tide(c):
    c.poly([(1, 8), (15, 2), (15, 14)], "d").poly([(2, 8), (14, 3.5), (14, 12.5)], "s")
    c.wave(2, 13, 6, 0.9, 5, "m").wave(2, 13, 8, 0.9, 5, "l").wave(2, 13, 10, 0.9, 5, "m", 3)
    c.disc(3.5, 8, 1.8, "l").dots([(11, 5), (12, 9), (10, 11), (8, 7)], "w")


def ink_burst(c):
    c.disc(8, 8, 3.8, "d")
    for ang in range(0, 360, 45):
        x, y = 8 + 5.4 * math.cos(math.radians(ang)), 8 + 5.4 * math.sin(math.radians(ang))
        c.disc(x, y, 1.6 if ang % 90 == 0 else 1.1, "d")
        c.line(8, 8, x, y, "d")
    c.disc(8, 8, 2.3, "s").dots([(7, 7), (6, 9)], "a").dots([(13, 3), (2, 13)], "m").disc(8.4, 7.4, 0.9, "l")


def pressure_bubble(c):
    c.disc(8, 8, 6.4, "d").disc(8, 8, 5.4, "s").disc(8, 8, 4.6, "a")
    c.ring(8, 8, 4.8, 5.8, "l", 200, 290).dots([(5, 5), (6, 4), (4, 6)], "w")
    c.disc(10.5, 10.5, 1.2, "l")
    for pts in (((1, 8), (0, 7), (0, 9)), ((15, 8), (16, 7), (16, 9))):
        c.dots(pts, "m")
    c.dots([(1, 3), (14, 3), (1, 13), (14, 13)], "m")


def sonar_pearl(c):
    c.disc(8, 8, 3.2, "d").disc(8, 8, 2.6, "l").disc(7.4, 7.4, 1.4, "w")
    c.ring(8, 8, 4.4, 5.2, "m").ring(8, 8, 6.2, 7, "m", 300, 560).ring(8, 8, 6.2, 7, "d", 100, 200)
    c.dots([(8, 2), (8, 13)], "l")


def undertow(c):
    pts = []
    for i in range(60):
        a = i * 0.34
        r = 0.4 + i * 0.105
        pts.append((8 + r * math.cos(a), 8 + r * math.sin(a)))
    for i, (x, y) in enumerate(pts):
        c.disc(x, y, 0.95 if i > 20 else 0.7, "l" if i % 11 < 6 else "m")
    c.disc(8, 8, 1.3, "d").dots([(2, 3), (13, 13), (14, 5), (2, 11)], "a")


def frost_mine(c):
    c.disc(8, 10, 4.6, "d").disc(8, 10, 3.6, "s").rect(6.5, 9.5, 9.4, 10.4, "l").rect(7.5, 8.5, 8.4, 11.4, "l")
    for x0, y0, x1, y1 in ((8, 1, 8, 5.2), (3, 3.5, 5.6, 6.4), (13, 3.5, 10.4, 6.4)):
        c.poly([(x0 - 1, y0 + (0 if y0 < 3 else 0)), (x1, y1), (x0 + 1, y0)], "l")
    c.dots([(8, 1), (8, 2), (8, 3), (4, 4), (12, 4), (5, 5), (11, 5)], "w").dots([(2, 13), (14, 13), (8, 15)], "m")


def stasis_pane(c):
    c.poly([(4, 1), (13, 1), (12, 15), (3, 15)], "d").poly([(5, 2.2), (12, 2.2), (11, 13.8), (4.5, 13.8)], "s")
    c.poly([(5.5, 3), (8.5, 3), (6.5, 13), (5, 13)], "a").line(10, 4, 8, 7, "w").line(8, 7, 10, 9, "w").line(10, 9, 9, 12, "w")
    c.dots([(7, 3), (10, 3), (11, 6)], "w").rect(4, 14, 12, 14.9, "m")


def hold_moment(c):
    c.poly([(8, 0.5), (14.5, 4), (14.5, 12), (8, 15.5), (1.5, 12), (1.5, 4)], "d")
    c.poly([(8, 2), (13, 5), (13, 11), (8, 14), (3, 11), (3, 5)], "s").poly([(8, 3), (12, 5.5), (8, 7.5), (4, 5.5)], "a")
    c.rect(5.5, 6.5, 6.9, 11.4, "l").rect(9, 6.5, 10.4, 11.4, "l").dots([(4, 4), (5, 4)], "w")


def glacial_hour(c):
    c.ell(8, 12, 7.2, 2.2, "d").ell(8, 11.5, 6.4, 1.6, "m")
    c.ring(8, 11.5, 0, 6.4, "s", 180, 360).ring(8, 11.5, 4.6, 6.3, "a", 180, 360)
    c.ring(8, 11.5, 6, 6.8, "l", 180, 360)
    c.line(8, 11, 8, 6, "w").line(8, 11, 11, 9, "w").dots([(8, 11)], "m").dots([(8, 3), (4, 5), (12, 5)], "l")


def rewind(c):
    c.ring(8, 8, 4, 6.2, "l", 20, 310).ring(8, 8, 3.4, 4.2, "d", 20, 310)
    c.poly([(10.5, 0.5), (15, 5.5), (9, 6)], "w").poly([(11, 2.4), (13.6, 5), (10.4, 5.2)], "m")
    c.line(8, 8, 8, 5, "m").line(8, 8, 10, 9, "m").dots([(8, 8)], "l")


def hourglass_trap(c):
    c.rect(3, 1, 12.9, 2.4, "m").rect(3, 13.6, 12.9, 15, "m")
    c.poly([(4, 2.4), (12, 2.4), (8.6, 8), (12, 13.6), (4, 13.6), (7.4, 8)], "a")
    c.poly([(5, 3), (11, 3), (8, 7)], "l").poly([(5.5, 13.6), (10.5, 13.6), (8, 10)], "l").line(8, 7, 8, 11, "w")
    c.dots([(2, 14), (13, 14)], "d").dots([(1, 15), (14, 15)], "m")


def sand_veil(c):
    c.poly([(2, 3), (14, 2), (13, 13), (9, 11), (6, 14), (3, 11)], "d")
    c.poly([(3, 4), (13, 3.5), (12, 11.5), (9, 9.5), (6, 12), (4, 10)], "m")
    c.wave(3, 12, 6, 1.0, 6, "l").wave(3, 12, 8, 1.0, 6, "l", 2)
    c.dots([(3, 14), (6, 15), (10, 14), (13, 15), (8, 1)], "l").dots([(11, 5), (5, 5)], "w")


def time_tide(c):
    c.ring(8, 6.5, 3.2, 4.4, "m").disc(8, 6.5, 3.2, "s").line(8, 6.5, 8, 4, "w").line(8, 6.5, 10, 7, "w")
    c.wave(1, 14, 11, 1.2, 7, "l").wave(1, 14, 12, 1.2, 7, "m").wave(1, 14, 13, 1.2, 7, "l", 2).dots([(8, 1), (12, 2), (3, 2)], "l")


def root_snare(c):
    c.rect(1.5, 4, 3.4, 14, "d").rect(12.5, 4, 14.4, 14, "d").rect(2, 3, 3, 4, "m").rect(13, 3, 14, 4, "m")
    c.line(3, 6, 7, 8, "l").line(8, 8, 12, 6, "l").line(3, 7, 7, 9, "w")
    c.ring(8, 11, 2.2, 3.6, "m", 180, 420).ring(8, 11, 0.8, 1.8, "l", 0, 270)
    c.line(5, 14, 6, 12, "d").line(11, 14, 10, 12, "d").dots([(8, 7), (8, 2), (5, 2), (11, 2)], "l")


def spore_mend(c):
    c.ell(8, 6, 6.2, 4, "d").ell(8, 6, 5.4, 3.3, "m").rect(2, 6, 13.9, 7.4, "d")
    c.dots([(5, 4), (8, 3), (11, 5), (6, 5)], "l").rect(6.5, 7.4, 9.4, 13, "a").rect(6.5, 7.4, 7.4, 13, "w").rect(5, 13, 10.9, 14, "d")
    c.rect(7.4, 9, 8.6, 12, "d").dots([(2, 10), (13, 11), (3, 13), (12, 2), (4, 1)], "l")


def bark_wall(c):
    for i, x in enumerate((1, 6, 11)):
        c.rect(x, 1, x + 3.9, 14.9, "d").rect(x + 0.9, 1.9, x + 3, 14, "s" if i % 2 else "a")
        c.rect(x + 1, 3, x + 1.9, 13, "m").dots([(x + 2, 5), (x + 2, 6), (x + 3, 9)], "d")
    c.rect(0, 5, 15.9, 5.9, "m").rect(0, 11, 15.9, 11.9, "m").dots([(2, 5), (7, 11), (12, 5)], "l")


def overgrowth(c):
    for x0 in (4, 11):
        for y in range(1, 15):
            c.dots([(x0 + round(1.4 * math.sin(y * 0.8 + x0)), y), (x0 + 1 + round(1.4 * math.sin(y * 0.8 + x0)), y)], "m")
    for x, y, flip in ((6, 3, 1), (1, 6, -1), (6, 9, 1), (1, 12, -1), (13, 4, 1), (8, 7, -1), (13, 11, 1), (8, 13, -1)):
        c.poly([(x, y), (x + 3 * flip, y - 1.5), (x + 3.4 * flip, y + 1.4)], "l")
    c.dots([(7, 4), (7, 10), (14, 5), (9, 8)], "w").dots([(0, 14), (15, 14)], "d")


def echo_step(c):
    def ghost(ox, a, b):
        c.poly([(ox + 3, 14), (ox + 3, 6), (ox + 5, 2), (ox + 9, 2), (ox + 11, 6), (ox + 11, 14), (ox + 9.5, 12), (ox + 8, 14), (ox + 6.5, 12), (ox + 5, 14)], a)
        c.dots([(ox + 6, 6), (ox + 9, 6)], b)
    ghost(-2, "d", "d")
    ghost(2, "a", "d")
    c.rect(2, 6, 3.9, 7.4, "m").dots([(2, 9), (3, 11), (1, 13)], "l")
    c.dots([(10, 6), (13, 6)], "k").dots([(10, 5), (13, 5)], "w")


def judgment(c):
    c.poly([(0.5, 8), (4, 4), (8, 2.8), (12, 4), (15.5, 8), (12, 12), (8, 13.2), (4, 12)], "d")
    c.poly([(2, 8), (5, 5), (8, 4), (11, 5), (14, 8), (11, 11), (8, 12), (5, 11)], "a")
    c.disc(8, 8, 3.2, "m").disc(8, 8, 1.8, "d").dots([(6, 6), (7, 6)], "w")
    c.line(8, 0, 8, 2, "l").line(3, 1, 4, 3, "l").line(13, 1, 12, 3, "l").dots([(8, 14), (8, 15)], "l")


def soul_lantern(c):
    c.rect(6.5, 0.5, 9.4, 1.5, "m").rect(5, 2, 10.9, 3.4, "d")
    c.poly([(4.5, 4), (11.5, 4), (12, 12), (4, 12)], "d").poly([(5.7, 4.8), (10.3, 4.8), (10.5, 11), (5.5, 11)], "s")
    c.poly([(8, 5), (10, 8.5), (8, 10.5), (6, 8.5)], "l").poly([(8, 6.5), (9, 8.5), (8, 9.7), (7, 8.5)], "w")
    c.rect(4, 12, 11.9, 13.4, "m").dots([(2, 6), (13, 6), (2, 10), (13, 10), (8, 15)], "l")


def verdict(c):
    c.poly([(2, 3), (9, 1), (11, 5), (4, 7)], "d").poly([(3, 3.4), (8.6, 2), (10, 4.8), (4.6, 6.2)], "m")
    c.line(7, 6, 12, 12, "a").line(8, 6, 13, 12, "s").rect(8, 13.5, 15, 14.9, "d")
    c.line(11, 11, 14, 14, "m")
    for p in ((1, 10), (4, 12), (0, 14), (13, 2), (14, 5)):
        c.dots([p], "l")
    c.dots([(5, 4), (6, 3)], "w")


ICONS = {
    "slag_pot": slag_pot, "heat_shimmer": heat_shimmer, "ember_dash": ember_dash, "colossus_step": colossus_step,
    "wind_pad": wind_pad, "static_orb": static_orb, "charged_slide": charged_slide, "storm_call": storm_call,
    "leech_pool": leech_pool, "rot_cloud": rot_cloud, "miasma_wall": miasma_wall, "bog_tide": bog_tide,
    "ink_burst": ink_burst, "pressure_bubble": pressure_bubble, "sonar_pearl": sonar_pearl, "undertow": undertow,
    "frost_mine": frost_mine, "stasis_pane": stasis_pane, "hold_moment": hold_moment, "glacial_hour": glacial_hour,
    "rewind": rewind, "hourglass_trap": hourglass_trap, "sand_veil": sand_veil, "time_tide": time_tide,
    "root_snare": root_snare, "spore_mend": spore_mend, "bark_wall": bark_wall, "overgrowth": overgrowth,
    "echo_step": echo_step, "judgment": judgment, "soul_lantern": soul_lantern, "verdict": verdict,
}


# ------------------------------------------------------------------ rendering

def render(grid, pal, rim=None):
    """RGBA rows from a palette-key grid, with a one pixel outline around the art (the rim colour, dark by default)."""
    rows = [[CLEAR] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if grid[y][x] is not None:
                rows[y][x] = pal[grid[y][x]] + (255,)
    edge = (rim or pal["k"]) + (255,)
    for y in range(SIZE):
        for x in range(SIZE):
            if grid[y][x] is None and any(0 <= x + dx < SIZE and 0 <= y + dy < SIZE and grid[y + dy][x + dx] is not None
                                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                rows[y][x] = edge
    return rows


def dim(rows):
    """The same icon grey and darker: no charge left, or the HUD tile switched off."""
    out = []
    for row in rows:
        out.append([CLEAR if p[3] == 0 else ((v := round((0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]) * 0.45 + 18)), v, v + 4, 255) for p in row])
    return out


def sparks(rows, pal):
    """Lit ultimate: the rim is gold (READY_RIM), here sparks in the corners."""
    spark = pal["w"] + (255,)
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (1, 1), (14, 1), (1, 14), (14, 14)):
        if rows[y][x][3] == 0:
            rows[y][x] = spark
    return rows


def icon(agent, ability_id, slot, state="on"):
    """16x16 RGBA rows. state: on (a normal icon), off / empty (dimmed), ready (ultimate only, lit)."""
    c = Canvas()
    ICONS[ability_id](c)
    pal = palette(agent, ultimate=slot == 7)
    if state in ("off", "empty"):
        return dim(render(c.g, pal))
    if state == "ready":
        return sparks(render(c.g, pal, rim=READY_RIM), pal)
    return render(c.g, pal)


def agents():
    return [json.loads(p.read_text()) for p in sorted(AGENTS.glob("*.json")) if p.name != "index.json"]


def item_name(agent_id, ability_id, state=""):
    return f"ab_{agent_id}_{ability_id}" + (f"_{state}" if state else "")


def main():
    n = 0
    for a in agents():
        for ab in a["abilities"]:
            states = ["", "empty"] + (["ready"] if ab["slot"] == 7 else [])
            for st in states:
                rows = icon(a, ab["id"], ab["slot"], st or "on")
                vi.write_item(item_name(a["id"], ab["id"], st), rows)
                n += 1
    print(f"wrote {n} ability items under {PACK}")


def sheet(path):
    """A contact sheet of every icon (on, empty, ready), 8 agents by 4 abilities, scaled x6, for a quick look."""
    k, pad = 6, 4
    ags = agents()
    cell = SIZE * k + pad
    w, h = cell * 12 * 1 + pad, cell * len(ags) + pad
    img = [[(24, 26, 34, 255)] * w for _ in range(h)]
    for r, a in enumerate(ags):
        col = 0
        for ab in a["abilities"]:
            for st in ("on", "empty") if ab["slot"] != 7 else ("on", "ready", "empty"):
                rows = icon(a, ab["id"], ab["slot"], st)
                for y in range(SIZE):
                    for x in range(SIZE):
                        if rows[y][x][3]:
                            for dy in range(k):
                                for dx in range(k):
                                    img[pad + r * cell + y * k + dy][pad + col * cell + x * k + dx] = rows[y][x]
                col += 1
    pathlib.Path(path).write_bytes(vi.png(img))


if __name__ == "__main__":
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1])
    else:
        main()
