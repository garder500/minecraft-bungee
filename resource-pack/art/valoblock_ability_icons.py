#!/usr/bin/env python3
"""ValoBlock ability icons, stdlib only (no Pillow, no Chrome), deterministic.

    python3 resource-pack/art/valoblock_ability_icons.py
    python3 resource-pack/art/valoblock_ability_icons.py --sheets <dir>   contact sheets (x8) for a look

Every one of the 32 abilities of arena-plugin/src/main/resources/valoblock/agents/*.json is a small OBJECT painted like a
Minecraft item (a slag pot, a flask, a gas canister, a clam and its pearl, a frost mine, an hourglass, a mushroom, a lantern...),
alone on a transparent ground. Nothing is copied from any game; the output is the same byte for byte.

How an object is made (OBJECTS): a function paints a 32 x 32 grid of (material, tone) cells with integer-pixel shapes
(Paint: ball, cyl, rect, poly, line, stamp...) and hand-placed pixels. A material is a ramp of five tones (RAMPS), dark to
light, with the hue shifting along it; the light comes from the top left. A dark outline tinted by the material it touches is
added around the silhouette (a material named with a trailing * is an effect - flame, gas, sparks - and gets none).
States are transforms of the same grid:

    on     full colour
    off    desaturated and darker (the HUD tile switched off)
    empty  no charge left (the item stays in the slot): the object greyed, with a red cross in its lower right corner
    ready  ultimates only: brighter, inside a two-pixel halo of the agent's glow colour

Written under resource-pack/valoblock/assets/vampyuyu/ (the item key the plugin sets is
vampyuyu:valoblock/ab_<agent>_<ability>[_empty|_ready]):

    textures/item/valoblock/ab_<agent>_<ability>[_empty|_ready].png   32x32
    models/item/valoblock/*.json, items/valoblock/*.json              item/generated model and its item definition

valoblock_ui.py asks icon(..., size=16) for the HUD (ic_<agent>_<slot>_on / _off, drawn over the HUD's own dark tile): the
same grid reduced by picking the dominant cell of each 2 x 2 block (never an average, which turns pixel art to mud), without
the outline, which the dark tile makes useless, and with the darkest tones lifted so the object does not sink into the tile.
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
SIZE = 16        # the HUD copy (vb_layout.ICON)
ITEM_SIZE = 32   # the hotbar item
N = 32           # the grid every object is painted on
INK = (0x0b, 0x0d, 0x12)

# The colour of an agent's halo (the lit ultimate): a key of its "colors", and the second ring's.
SIGNATURE = {
    "cinder": ("trim", "glow"), "gale": ("trim", "arm"), "murk": ("glow", "trim"), "nacre": ("glow", "arm"),
    "rime": ("arm", "glow"), "sahrin": ("trim", "glow"), "thorn": ("trim", "glow"), "vesper": ("trim", "boots"),
}


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def grey(c, keep=0.0):
    """c with its saturation scaled to keep (0 = grey)."""
    v = round(0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return mix((v, v, v), c, keep)


def signature(agent):
    """(main, second) RGB of the agent."""
    main, second = SIGNATURE.get(agent["id"], ("trim", "glow"))
    return vi.rgb(agent["colors"][main][1:]), vi.rgb(agent["colors"][second][1:])


# ------------------------------------------------------------------ materials: five tones, dark to light

def ramp(*hexes):
    return [vi.rgb(h) for h in hexes]


RAMPS = {
    "iron": ramp("2b2a3a", "4a4a5e", "6f7286", "9a9fb0", "d2d8e2"),
    "dark": ramp("211a28", "352b3b", "4b4052", "665a6e", "8d8296"),      # basalt, blackened iron
    "gold": ramp("6a3a14", "a2601c", "d39535", "f0c04e", "fff0a0"),
    "copper": ramp("5a2418", "8a3e22", "b8603a", "de8c58", "f8c090"),
    "wood": ramp("3a1f14", "5a321c", "7e4a26", "a26a38", "c89858"),
    "rope": ramp("5a4428", "80663a", "a88c54", "ccb478", "ecdca4"),
    "magma": ramp("8a1c10", "d2401a", "f47a1e", "ffb630", "ffee8a"),
    "glass": ramp("2c4a6a", "4a7a9c", "7ab0cc", "b0dcec", "f0fcff"),
    "ice": ramp("2a4a8c", "4a80c8", "7ab8ec", "b4e0fa", "f4fdff"),
    "frost": ramp("4a3e8c", "7a6ac8", "a898ec", "cfc4fa", "f4f0ff"),     # Rime's lavender glow
    "snow": ramp("6a82b0", "9cb4d8", "c8dcf0", "e8f4fc", "ffffff"),
    "slime": ramp("2a3a12", "4a5e1a", "76902a", "a4c040", "d8ec7a"),
    "poison": ramp("3a1440", "6a2470", "a040a0", "cc6cc4", "f0a8e4"),
    "ink": ramp("0e0e1e", "1a1a34", "2a2c50", "4a5080", "8a94c0"),
    "teal": ramp("0c3440", "14586a", "2a8a9a", "5cc0c4", "b0f0e8"),
    "pink": ramp("8a2a60", "c84890", "ff6ab0", "ffa0cc", "ffe0f0"),
    "shell": ramp("7a5a5a", "b08a80", "dcc0a8", "f4e4cc", "fffaf0"),
    "yellow": ramp("8a5a10", "c89018", "f4cc2a", "ffe94a", "fffcc0"),
    "blue": ramp("1a2248", "2c3a74", "4a5eaa", "7a8ee0", "b4c4fa"),
    "cloud": ramp("2a2e4a", "444a6e", "6a7298", "98a2c4", "ccd4ec"),
    "sand": ramp("8a5a2a", "b88a44", "dcb866", "f2d890", "fff4c4"),
    "cloth": ramp("6a5a44", "94826a", "bcae90", "e0d4b8", "f8f0dc"),
    "jade": ramp("0e4a48", "1c7a72", "3fb6a8", "7ee0cc", "d0fff0"),      # Sahrin's teal
    "leaf": ramp("1e3a14", "34601e", "5a8a2e", "8fb84a", "d6f08a"),
    "bark": ramp("2a180e", "44281a", "6a4422", "8a6032", "b08a52"),
    "mint": ramp("0e5a54", "1ca08a", "3adcb8", "6affd8", "d8fff4"),      # Vesper's soul fire
    "violet": ramp("1c1730", "2e2850", "4a4570", "6e68a0", "9c95c8"),
    "bone": ramp("6a6488", "9c95c8", "c8c2e4", "e8e4ff", "ffffff"),
    "red": ramp("5a1018", "8a1a20", "c82a2a", "f05a4a", "ffa890"),
}
OUTLINE_INK = (16, 10, 22)


def outline_of(name):
    return mix(RAMPS[name][0], OUTLINE_INK, 0.62)


# ------------------------------------------------------------------ painting on the 32 x 32 grid

LIGHT = (-0.50, -0.62, 0.60)   # from the top left, towards the viewer


def ball_tone(cx, cy, rx, ry, lift=0):
    """Tone of a point of a sphere lit from the top left: five bands, the core shadow at the bottom right."""
    def tone(x, y):
        nx, ny = (x - cx) / rx, (y - cy) / ry
        nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
        d = LIGHT[0] * nx + LIGHT[1] * ny + LIGHT[2] * nz
        return (4 if d > 0.86 else 3 if d > 0.55 else 2 if d > 0.15 else 1 if d > -0.22 else 0) + lift
    return tone


def cyl_tone(x0, x1):
    """An upright cylinder: a highlight a quarter in from the left, the shadow on the right."""
    bands = (2, 3, 4, 3, 2, 2, 1, 0)
    return lambda x, y: bands[min(7, int((x - x0) / max(1, x1 - x0) * 7.99))]


def hcyl_tone(y0, y1):
    """A lying cylinder or a rim: lit on top, dark underneath."""
    bands = (3, 4, 3, 2, 2, 1, 1, 0)
    return lambda x, y: bands[min(7, int((y - y0) / max(1, y1 - y0) * 7.99))]


class Paint:
    def __init__(self):
        self.c = [[None] * N for _ in range(N)]

    def set(self, x, y, mat, t, where=None):
        if 0 <= x < N and 0 <= y < N and (where is None or where(x, y)):
            tt = t(x, y) if callable(t) else t
            if tt is not None:
                self.c[y][x] = (mat, max(0, min(4, tt)))

    def px(self, mat, t, *pts):
        for x, y in pts:
            self.set(x, y, mat, t)
        return self

    def erase(self, *pts):
        for x, y in pts:
            self.c[y][x] = None
        return self

    def rect(self, x0, y0, x1, y1, mat, t=2, where=None):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, mat, t, where)
        return self

    def cyl(self, x0, y0, x1, y1, mat, where=None):
        return self.rect(x0, y0, x1, y1, mat, cyl_tone(x0, x1), where)

    def hcyl(self, x0, y0, x1, y1, mat, where=None):
        return self.rect(x0, y0, x1, y1, mat, hcyl_tone(y0, y1), where)

    def disc(self, cx, cy, rx, ry, mat, t=2, where=None):
        for y in range(N):
            for x in range(N):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1:
                    self.set(x, y, mat, t, where)
        return self

    def ball(self, cx, cy, rx, ry, mat, lift=0, where=None):
        return self.disc(cx, cy, rx, ry, mat, ball_tone(cx, cy, rx, ry, lift), where)

    def arc(self, cx, cy, r0, r1, a0, a1, mat, t=2, where=None):
        """Annulus between the angles a0..a1 (degrees, 0 = right, 90 = down)."""
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - cx, y - cy)
                if r0 <= d <= r1 and (a1 - a0 >= 360 or (math.degrees(math.atan2(y - cy, x - cx)) - a0) % 360 <= a1 - a0):
                    self.set(x, y, mat, t, where)
        return self

    def poly(self, pts, mat, t=2, where=None):
        """A filled polygon, its edges included (integer vertices give the pixels you expect)."""
        segs = list(zip(pts, pts[1:] + pts[:1]))
        for y in range(N):
            for x in range(N):
                inside = False
                for (xi, yi), (xj, yj) in segs:
                    if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                        inside = not inside
                if not inside:
                    inside = any(_seg_dist(x, y, a, b) <= 0.35 for a, b in segs)
                if inside:
                    self.set(x, y, mat, t, where)
        return self

    def line(self, x0, y0, x1, y1, mat, t=2, w=1.0):
        for y in range(N):
            for x in range(N):
                if _seg_dist(x, y, (x0, y0), (x1, y1)) <= w / 2:
                    self.set(x, y, mat, t)
        return self

    def path(self, pts, mat, t=2, w=1.0):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            self.line(x0, y0, x1, y1, mat, t, w)
        return self

    def stamp(self, x0, y0, rows, legend):
        """A hand-made grid: legend maps a character to (material, tone); '.' leaves the pixel, ' ' clears it."""
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch == " ":
                    if 0 <= x0 + dx < N and 0 <= y0 + dy < N:
                        self.c[y0 + dy][x0 + dx] = None
                elif ch != ".":
                    self.set(x0 + dx, y0 + dy, *legend[ch])
        return self

    def flame(self, cx, base, h, w, mat="magma", lean=1.5):
        """A tongue of fire standing on `base`: widest a quarter up, its tip leaning to the right, a hot core."""
        for i in range(h):
            f = i / max(1, h - 1)
            hw = w / 2 * min(1.0, (1 - f) * 1.6) * (0.7 + 0.3 * min(1.0, f * 4))
            mid = cx + f * f * lean
            for x in range(round(mid - hw), round(mid + hw) + 1):
                d = abs(x - mid) / max(hw, 0.5)
                self.set(x, base - i, mat, 4 if d < 0.45 and f < 0.45 else 3 if d < 0.8 and f < 0.72 else 2)
        return self

    def crystal(self, cx, top, bottom, hw, mat="ice"):
        """An upright six-sided crystal: a pointed top, a lit left face, a bright ridge, a shaded right face."""
        for y in range(top, bottom + 1):
            w = min(hw, (y - top) * 0.8 + 0.5, (bottom - y) * 1.4 + 1.5)
            for x in range(round(cx - w), round(cx + w) + 1):
                rel = (x - cx) / max(hw, 1)
                cap = (y - top) < hw * 1.1
                t = 4 if -0.25 <= rel < 0.0 else 3 if rel < -0.25 else 2 if rel < 0.6 else 1
                if cap and rel < -0.25:
                    t = 4
                if cap and rel >= 0.0:
                    t = min(3, t + 1)
                self.set(x, y, mat, t)
        return self


def _seg_dist(x, y, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    ll = dx * dx + dy * dy
    t = 0.0 if not ll else max(0.0, min(1.0, ((x - a[0]) * dx + (y - a[1]) * dy) / ll))
    return math.hypot(x - a[0] - t * dx, y - a[1] - t * dy)


BOLT = ["....##",
        "...##.",
        "..##..",
        ".###..",
        "#####.",
        "..###.",
        "..##..",
        ".##...",
        ".#....",
        "#....."]


BIG_BOLT = ["....#####.",
            "...#####..",
            "..#####...",
            ".#####....",
            "#########.",
            ".########.",
            "....#####.",
            "...####...",
            "..####....",
            "..###.....",
            ".###......",
            ".##.......",
            "##........",
            "#........."]


def bolt(p, x, y, mat="yellow", rows=BOLT):
    """A lightning bolt (6 x 10, or BIG_BOLT), lit on its left edge and shaded on its right one."""
    for dy, row in enumerate(rows):
        last = row.rindex("#")
        for dx, ch in enumerate(row):
            if ch == "#":
                p.set(x + dx, y + dy, mat, 4 if dx == row.index("#") else 2 if dx == last and len(row) > 6 else 3)


# ------------------------------------------------------------------ the 32 objects

# Cinder: fire and blackened stone

def slag_pot(p):       # a thrown pot of molten slag: a round basalt pot, brimming, cracked with heat
    p.ball(15.5, 18.5, 10.5, 9, "dark")
    p.path([(8, 16), (10, 19), (9, 22), (11, 24)], "magma", 2).px("magma", 3, (10, 19), (9, 21))
    p.path([(19, 17), (21, 20), (20, 23)], "magma", 1).px("magma", 3, (21, 20))
    p.px("magma", 2, (14, 24), (15, 25), (24, 17))
    p.disc(15.5, 9.5, 8.5, 3.5, "dark", lambda x, y: 4 if y < 9 and x < 15 else 3 if y < 10 else 1)
    p.disc(15.5, 9.5, 6.5, 2.2, "magma", lambda x, y: 4 if x < 13 else 3 if x < 19 else 2)
    p.px("magma", 4, (16, 9), (18, 10)).px("magma", 1, (20, 10), (21, 9))
    p.rect(20, 11, 21, 15, "magma", 3).px("magma", 4, (20, 12), (20, 13)).px("magma", 2, (21, 15), (20, 16), (21, 16))
    p.flame(13, 7, 5, 4).flame(17, 7, 3, 2)


def heat_shimmer(p):   # speed and silent steps: a flask of bottled heat haze
    p.ball(15.5, 19.5, 9.5, 8.5, "glass", lift=1)
    p.ball(15.5, 19.5, 8, 7, "magma", where=lambda x, y: y >= 17)
    p.rect(8, 16, 23, 16, "magma", 4, where=lambda x, y: ((x - 15.5) / 8) ** 2 + ((y - 19.5) / 7) ** 2 <= 1)
    p.px("magma", 4, (12, 20), (18, 23), (19, 19)).px("magma", 3, (13, 24), (16, 21))
    p.px("glass", 4, (9, 15), (9, 16), (10, 14), (10, 13), (11, 13))
    p.cyl(13, 7, 18, 12, "glass").hcyl(12, 6, 19, 7, "glass")
    p.rect(13, 2, 18, 5, "wood", lambda x, y: 4 if y == 2 and x < 17 else 3 if x < 15 else 2 if x < 18 else 1)
    p.px("magma*", 3, (22, 6), (23, 4), (8, 5)).px("magma*", 4, (22, 5), (9, 4)).px("magma*", 2, (24, 3), (8, 6))


def ember_dash(p):     # a dash that leaves fire: a burning coal streaking, its flames trailing behind
    p.poly([(20, 12), (12, 3), (15, 14)], "magma", 1)
    p.poly([(14, 22), (3, 17), (13, 18)], "magma", 1)
    p.poly([(17, 13), (2, 2), (12, 20)], "magma", 2)
    p.poly([(18, 15), (7, 7), (14, 20)], "magma", 3)
    p.poly([(19, 16), (11, 11), (16, 20)], "magma", 4)
    p.px("magma", 3, (4, 4), (5, 5)).px("magma", 2, (2, 2), (3, 3))
    p.ball(20.5, 20.5, 7, 7, "dark")
    p.path([(17, 18), (20, 20), (19, 23)], "magma", 3).path([(22, 17), (24, 20)], "magma", 2).px("magma", 4, (20, 20))
    p.px("magma", 2, (22, 24), (16, 22)).px("magma*", 3, (8, 24), (25, 9)).px("magma*", 2, (6, 26), (27, 7))


def colossus_step(p):  # leap and slam: a colossus greave of cracked basalt, molten inside
    p.cyl(9, 5, 19, 18, "dark")
    p.rect(9, 18, 23, 25, "dark", cyl_tone(9, 27))
    p.ball(22.5, 22, 5.5, 5, "iron", where=lambda x, y: y <= 25)
    p.px("iron", 1, (19, 20), (19, 21), (18, 22), (18, 23), (18, 24), (18, 25))
    p.hcyl(7, 3, 21, 6, "iron").px("iron", 4, (9, 3), (10, 3), (11, 3))
    p.rect(7, 26, 28, 28, "iron", lambda x, y: 1 if y == 26 else 0)
    p.px("iron", 2, (8, 26), (9, 26), (10, 26), (22, 26), (23, 26))
    p.path([(13, 8), (15, 11), (13, 14), (15, 17)], "magma", 2).px("magma", 4, (15, 11), (13, 14)).px("magma", 3, (14, 9), (14, 16))
    p.path([(11, 20), (14, 22), (16, 21)], "magma", 2).px("magma", 4, (14, 22)).px("magma", 3, (12, 21))
    p.px("iron", 4, (9, 8), (9, 13)).px("dark", 0, (9, 15), (19, 9))
    p.px("magma*", 3, (4, 24), (3, 21), (30, 23)).px("magma*", 4, (4, 23), (29, 22))


# Gale: wind, brass yellow and storm blue

def wind_pad(p):       # a launch pad: a brass-ringed plate, a feather riding its gust
    p.disc(15.5, 24.5, 12.5, 4.5, "blue", lambda x, y: 1 if x < 22 else 0)
    p.disc(15.5, 22, 12.5, 4.5, "blue", lambda x, y: 4 if y < 20 and x < 14 else 3 if y < 22 else 2)
    p.disc(15.5, 22, 9.5, 3.2, "yellow", lambda x, y: 4 if y < 21 and x < 15 else 3 if y < 23 else 2)
    p.disc(15.5, 22, 6.5, 1.9, "blue", lambda x, y: 1 if y < 22 else 2)
    p.px("yellow", 2, (4, 25), (9, 27), (22, 27), (27, 25))
    p.poly([(12, 13), (13, 8), (17, 3), (21, 1), (23, 3), (22, 8), (18, 12), (14, 15)], "snow", lambda x, y: 4 if x + y < 22 else 3 if x - y < 12 else 2)
    p.line(11, 17, 22, 2, "snow", 1)
    p.erase((16, 3), (22, 7), (13, 8)).px("snow", 1, (19, 11), (21, 8), (14, 14)).px("snow", 2, (15, 6), (17, 4))
    p.path([(24, 18), (26, 15), (25, 12)], "snow*", 3, 1).path([(6, 17), (5, 14), (7, 11)], "snow*", 3, 1).px("snow*", 4, (25, 11), (8, 10), (27, 8), (4, 8))


def static_orb(p):     # a bouncing flash orb: a storm caught in a glass ball
    p.ball(15.5, 15.5, 11, 11, "blue")
    p.ball(15.5, 15.5, 8.5, 8.5, "blue", lift=-1)
    bolt(p, 11, 9, rows=BIG_BOLT)
    p.px("blue", 4, (9, 9), (10, 8), (11, 7), (9, 10), (8, 11)).px("snow", 4, (10, 9), (11, 8))
    p.px("blue", 3, (22, 22), (21, 23), (23, 20))
    p.px("yellow*", 4, (27, 6), (4, 24), (26, 27)).px("yellow*", 3, (28, 6), (27, 5), (27, 7), (26, 6), (4, 23), (3, 24), (5, 24), (4, 25))


def charged_slide(p):  # a charged slide: a storm cell, copper caps on a glowing core
    p.cyl(11, 8, 20, 24, "glass")
    p.rect(12, 9, 19, 23, "blue", lambda x, y: 1 if x < 18 else 0)
    bolt(p, 13, 11)
    p.px("glass", 4, (11, 10), (11, 11), (11, 12), (12, 9))
    p.hcyl(9, 5, 22, 8, "copper").hcyl(9, 24, 22, 27, "copper")
    p.px("copper", 4, (10, 5), (11, 5), (12, 5), (10, 24), (11, 24)).px("copper", 0, (22, 8), (22, 27), (21, 27))
    p.cyl(13, 2, 18, 4, "iron")
    p.px("copper", 1, (14, 26), (17, 26), (14, 7), (17, 7))
    p.px("yellow*", 4, (25, 12), (6, 18)).px("yellow*", 3, (26, 11), (24, 13), (5, 19), (7, 17), (25, 20), (6, 10))


def storm_call(p):     # lightning called on three spots: a storm cloud and its bolt
    bolt(p, 10, 15, rows=BIG_BOLT)
    p.ball(15.5, 12.5, 12, 3.6, "cloud", lift=-1)
    p.ball(8.5, 10, 5.5, 5, "cloud").ball(22.5, 10, 6, 5, "cloud").ball(15.5, 7.5, 7, 6.2, "cloud")
    p.px("cloud", 1, (12, 13), (13, 14), (19, 13), (20, 14)).px("cloud", 4, (12, 3), (13, 2), (6, 7))


# Murk: bog olive and poison purple

def leech_pool(p):     # a draining puddle: a clot of bog slime, still dripping
    p.disc(15.5, 26.5, 11.5, 2.6, "slime", lambda x, y: 2 if y < 26 else 1)
    p.px("slime", 3, (8, 25), (9, 25), (10, 25), (20, 25))
    p.rect(9, 18, 11, 23, "slime", lambda x, y: 3 if x == 9 else 2).px("slime", 2, (10, 24))
    p.rect(20, 19, 21, 25, "slime", 1)
    p.ball(15.5, 13.5, 9.5, 9, "slime")
    p.ball(12, 11, 2.6, 2.6, "poison").ball(19, 15, 2, 2, "poison").px("poison", 2, (15, 18), (11, 17)).px("poison", 4, (11, 10))
    p.px("slime", 4, (9, 9), (10, 8), (11, 7)).px("slime", 0, (15, 19), (12, 18))
    p.px("poison", 3, (15, 17), (11, 16))


def rot_cloud(p):      # a poison cloud: a bog-green gas canister, leaking
    p.cyl(10, 10, 21, 27, "slime")
    p.rect(10, 16, 21, 19, "poison", cyl_tone(10, 21))
    p.rect(10, 26, 21, 27, "iron", lambda x, y: 1 if x < 18 else 0)
    p.px("slime", 1, (12, 22), (12, 23), (16, 22), (16, 23), (19, 13)).px("slime", 4, (12, 12), (12, 13))
    p.hcyl(11, 8, 20, 10, "iron").cyl(14, 5, 17, 7, "iron")
    p.px("iron", 3, (22, 8), (23, 9), (24, 10), (24, 11)).px("iron", 2, (23, 12), (22, 12), (24, 12))
    p.ball(19, 3.5, 3, 2.8, "poison*", lift=1).ball(24, 5.5, 2.4, 2.2, "poison*").ball(13, 2.5, 2, 1.8, "poison*", lift=1)
    p.px("poison*", 3, (16, 4), (27, 3), (9, 2))


def miasma_wall(p):    # a gas wall switched on and off: a three-nozzle emitter under its wall of gas
    p.rect(4, 10, 27, 18, "poison", lambda x, y: 2 if y < 14 else 1)
    p.ball(7.5, 10.5, 5, 5.5, "poison").ball(23.5, 10.5, 5, 5.5, "poison").ball(15.5, 8.5, 6, 6.5, "poison")
    p.px("poison", 1, (11, 13), (12, 14), (19, 12), (20, 13), (8, 17), (16, 17), (23, 16)).px("poison", 4, (13, 4), (6, 7))
    p.erase((4, 18), (27, 18), (4, 17), (27, 17), (11, 18), (12, 18), (19, 18), (20, 18))
    for x in (6, 14, 22):
        p.cyl(x, 19, x + 3, 22, "slime")
    p.hcyl(3, 23, 28, 27, "iron").px("iron", 4, (4, 23), (5, 23), (6, 23))
    p.px("slime", 3, (8, 25), (16, 25), (24, 25)).px("iron", 0, (3, 27), (28, 27), (28, 23))
    p.erase((3, 23), (28, 23))


def bog_tide(p):       # the marsh spreads: a black cauldron boiling over with bog
    p.rect(7, 25, 9, 28, "dark", 1).rect(22, 25, 24, 28, "dark", 0)
    p.ball(15.5, 18, 11.5, 9, "dark")
    p.disc(15.5, 10.5, 12, 3.6, "iron", lambda x, y: 4 if y < 9 and x < 14 else 3 if y < 11 else 1)
    p.disc(15.5, 10.5, 10, 2.4, "slime", lambda x, y: 3 if x < 14 else 2)
    p.ball(11.5, 8, 2.8, 2.8, "slime", lift=1).ball(19.5, 9, 2.2, 2.2, "poison", lift=1).px("slime", 4, (16, 10), (22, 11))
    p.rect(5, 11, 7, 19, "slime", lambda x, y: 3 if x == 5 else 2).px("slime", 2, (6, 20), (6, 21)).px("slime", 4, (5, 12), (5, 13))
    p.rect(23, 12, 24, 17, "slime", 2).px("slime", 1, (24, 18), (24, 16), (24, 17))
    p.rect(14, 12, 16, 15, "slime", 2).px("slime", 3, (14, 12), (14, 13), (14, 14)).px("slime", 1, (15, 16))
    p.px("poison*", 3, (25, 5), (8, 3)).px("poison*", 4, (26, 4), (7, 4))


# Nacre: sea teal, copper and pearl pink

def ink_burst(p):      # a blinding cloud of ink: a copper-capped inkwell, spilling
    p.poly([(10, 12), (21, 12), (26, 18), (26, 27), (5, 27), (5, 18)], "glass", lambda x, y: 3 if x < 9 else 2 if x < 23 else 1)
    p.poly([(11, 15), (20, 15), (24, 19), (24, 25), (7, 25), (7, 19)], "ink", lambda x, y: 3 if y == 15 else 2 if x < 12 else 1 if x < 21 else 0)
    p.px("ink", 4, (9, 19), (9, 20), (10, 18)).px("glass", 4, (6, 19), (6, 20), (7, 17), (8, 16))
    p.rect(5, 26, 26, 27, "glass", lambda x, y: 1 if x < 22 else 0)
    p.cyl(12, 8, 19, 11, "glass")
    p.hcyl(10, 4, 21, 8, "copper").px("copper", 4, (11, 4), (12, 4), (13, 4)).px("copper", 0, (21, 8))
    p.erase((10, 4), (21, 4))
    p.rect(20, 9, 22, 14, "ink", lambda x, y: 3 if x == 20 else 1).px("ink", 1, (21, 15), (21, 16), (22, 15)).px("ink", 3, (20, 10))
    p.ball(27, 8, 1.8, 1.8, "ink", lift=1).px("ink", 2, (3, 9), (2, 13), (3, 12)).px("ink", 3, (3, 8))


def pressure_bubble(p):  # a dome that pushes out: a great bubble blown from a copper valve
    p.disc(15.5, 12.5, 11, 11, "teal", 1)
    p.ball(15.5, 12.5, 10, 10, "teal", lift=1)
    p.disc(16.5, 13.5, 7, 7, "teal", 3, where=lambda x, y: (x - 14) ** 2 + (y - 11) ** 2 > 40)
    p.stamp(8, 5, [".##.", "####", "###.", ".#.."], {"#": ("snow", 4)})
    p.px("snow", 4, (22, 19), (21, 20), (20, 21)).px("teal", 4, (23, 17), (22, 20))
    p.cyl(12, 23, 19, 24, "copper")
    p.hcyl(9, 25, 22, 28, "copper").px("copper", 4, (10, 25), (11, 25), (12, 25)).px("copper", 0, (22, 28), (9, 28))
    p.ball(27, 23, 2, 2, "teal", lift=2).ball(4, 24, 1.6, 1.6, "teal", lift=2)


def sonar_pearl(p):    # a recon pearl: a pearl in its open clam
    p.ball(15.5, 13.5, 12, 10.5, "teal", lift=1, where=lambda x, y: y <= 18)
    for ex in (5, 9, 13, 18, 22, 26):
        p.line(15.5, 20, ex, 4 + abs(ex - 15.5) * 0.5, "teal", 1)
    p.disc(15.5, 20.5, 12.5, 7, "teal", lambda x, y: 4 if y == 20 else 3 if y < 23 and x < 20 else 2 if y < 25 else 1, where=lambda x, y: y >= 20)
    for ex in (8, 12, 19, 23):
        p.line(ex, 22, 15.5 + (ex - 15.5) * 0.55, 27, "teal", 1)
    p.ball(15.5, 17, 4.6, 4.6, "pink", lift=1)
    p.px("snow", 4, (13, 14), (14, 14), (13, 15))
    p.px("pink*", 4, (25, 3), (5, 2)).px("pink*", 3, (24, 3), (26, 3), (25, 2), (25, 4))


def undertow(p):       # a field that drags to its centre: a copper anchor
    p.arc(15.5, 15, 9.5, 12.6, 20, 160, "copper", lambda x, y: 3 if x < 12 else 2 if x < 21 else 1)
    p.poly([(3, 15), (8, 17), (4, 22)], "copper", 3).poly([(28, 15), (23, 17), (27, 22)], "copper", 1)
    p.px("copper", 4, (4, 16), (4, 17), (5, 17)).px("copper", 2, (27, 17), (26, 17))
    p.cyl(14, 8, 17, 27, "copper")
    p.hcyl(8, 10, 23, 12, "copper").px("copper", 4, (8, 10), (9, 10), (10, 10)).ball(7.5, 11, 1.6, 1.6, "copper").ball(23.5, 11, 1.6, 1.6, "copper", lift=-1)
    p.arc(15.5, 5, 1.6, 3.4, 0, 360, "copper", lambda x, y: 4 if y < 4 and x < 16 else 3 if x < 17 else 1)
    p.rect(13, 16, 18, 18, "teal", cyl_tone(13, 18)).px("teal", 4, (14, 16))
    p.px("teal*", 3, (22, 4), (23, 5), (24, 5), (25, 4), (7, 5), (6, 6), (5, 6)).px("teal*", 4, (26, 3), (8, 4))


# Rime: ice, steel and a lavender glow

def frost_mine(p):     # a slowing proximity mine: a low steel disc, a dome of frost at its heart
    for x, y in ((3, 22), (9, 26), (21, 26), (27, 22)):
        p.rect(x, y, x + 1, y + 3, "iron", lambda xx, yy, x=x: 3 if xx == x else 1)
    p.disc(15.5, 20.5, 12.5, 6, "iron", lambda x, y: 1 if x < 21 else 0)
    p.disc(15.5, 16.5, 12.5, 6, "iron", lambda x, y: 4 if y < 14 and x < 13 else 3 if y < 17 else 2)
    p.disc(15.5, 16.5, 9, 4, "iron", lambda x, y: 1 if y < 16 else 2)
    for x, y in ((6, 15), (24, 15), (11, 20), (20, 20), (15, 11), (16, 11)):
        p.px("iron", 4, (x, y)).px("iron", 0, (x, y + 1))
    p.ball(15.5, 14.5, 6, 5, "frost", lift=1, where=lambda x, y: y <= 17)
    p.disc(15.5, 16.5, 6, 2.2, "frost", lambda x, y: 2 if x < 18 else 1, where=lambda x, y: y > 17)
    p.stamp(13, 12, ["..#..", "#.#.#", ".###.", "#.#.#", "..#.."], {"#": ("snow", 4)})
    p.px("frost*", 4, (6, 6), (25, 4), (15, 4)).px("frost*", 3, (5, 6), (7, 6), (6, 5), (6, 7), (24, 4), (26, 4), (25, 3), (25, 5), (15, 6), (16, 2))


def stasis_pane(p):    # a pane that stops three bullets: a slab of clear ice in a steel frame
    def ice(x, y):
        s = x + y
        return 4 if s in (21, 22, 23, 28) else 2 if s > 39 else 3
    p.poly([(7, 2), (24, 5), (24, 29), (7, 26)], "iron", lambda x, y: 4 if x < 9 and y < 20 else 3 if y < 6 + (x - 7) * 0.2 else 1 if x > 22 else 2)
    p.poly([(9, 5), (22, 7), (22, 26), (9, 24)], "ice", ice)
    p.px("ice", 1, (22, 8), (22, 9), (9, 24), (10, 24), (11, 24))
    p.stamp(13, 13, ["#...#",
                     ".#.#.",
                     "..o..",
                     ".#.##",
                     "#...."], {"#": ("snow", 4), "o": ("iron", 1)})
    p.px("snow", 4, (12, 11), (19, 18), (20, 19)).px("iron", 4, (7, 3), (7, 4), (8, 3))
    p.rect(25, 12, 26, 13, "iron", 2).rect(25, 21, 26, 22, "iron", 1)


def hold_moment(p):    # freeze yourself for two seconds: a cluster of ice crystals
    p.disc(15.5, 26.5, 11.5, 2.6, "snow", lambda x, y: 4 if y < 26 and x < 14 else 3 if y < 27 else 2)
    p.crystal(7.5, 12, 26, 3, "ice")
    p.crystal(24, 9, 26, 3.5, "ice")
    p.crystal(15.5, 2, 27, 5, "ice")
    p.crystal(11, 18, 27, 2, "frost").crystal(21, 20, 27, 1.6, "frost")
    p.px("snow", 4, (13, 6), (12, 9), (6, 15), (22, 12)).px("ice", 1, (17, 14), (18, 19), (17, 22), (25, 18))
    p.px("snow*", 4, (26, 4), (4, 7)).px("snow*", 3, (25, 4), (27, 4), (26, 3), (26, 5), (3, 7), (5, 7), (4, 6), (4, 8))


def glacial_hour(p):   # a freezing dome: a snow globe
    p.disc(15.5, 13, 11.5, 11.5, "glass", 1)
    p.ball(15.5, 13, 10.5, 10.5, "ice", lift=-1)
    p.disc(15.5, 13, 10.5, 10.5, "snow", lambda x, y: 4 if x < 15 else 3, where=lambda x, y: y >= 19 + abs(x - 14) * 0.12)
    p.stamp(12, 8, ["...#...",
                    ".#.#.#.",
                    "..###..",
                    "#######",
                    "..###..",
                    ".#.#.#.",
                    "...#..."], {"#": ("snow", 4)})
    p.px("snow", 4, (9, 15), (22, 10), (20, 16), (8, 11)).stamp(7, 5, [".##", "##.", "#.."], {"#": ("snow", 4)})
    p.px("ice", 4, (23, 18), (22, 19))
    p.hcyl(7, 22, 24, 24, "iron")
    p.hcyl(5, 25, 26, 28, "iron").rect(5, 26, 26, 26, "frost", lambda x, y: 3 if x < 20 else 2)
    p.px("iron", 4, (6, 25), (7, 25), (8, 25), (8, 22), (9, 22)).px("iron", 0, (26, 28), (5, 28))


# Sahrin: gold, sand and jade

def rewind(p):         # return to your mark: a gold compass whose jade needle points home
    p.arc(15.5, 4.5, 1.6, 3.4, 0, 360, "gold", lambda x, y: 4 if x < 15 and y < 5 else 3 if x < 17 else 1)
    p.ball(15.5, 17.5, 11.5, 11.5, "gold")
    p.disc(15.5, 17.5, 8.6, 8.6, "gold", 0)
    p.disc(15.5, 17.5, 7.8, 7.8, "jade", lambda x, y: 0 if x + y < 30 else 1)
    p.px("gold", 3, (15, 10), (16, 10), (15, 25), (16, 25), (8, 17), (8, 18), (23, 17), (23, 18))
    p.poly([(21, 11), (18, 20), (13, 15)], "jade", lambda x, y: 4 if x - y < 4 else 3)
    p.poly([(10, 24), (13, 15), (18, 20)], "cloth", lambda x, y: 3 if x - y < -5 else 1)
    p.rect(15, 17, 16, 18, "gold", 4).px("gold", 2, (16, 18))
    p.px("gold", 4, (7, 11), (8, 10), (9, 9), (10, 8)).px("gold", 0, (24, 26), (25, 25))
    p.px("jade*", 4, (27, 5), (4, 27)).px("jade*", 3, (26, 5), (28, 5), (27, 4), (27, 6), (3, 27), (5, 27), (4, 26), (4, 28))


def hourglass_trap(p):  # a sand trap: an hourglass in a gold frame
    p.poly([(11, 7), (20, 7), (20, 11), (16, 15), (15, 15), (11, 11)], "glass", lambda x, y: 4 if x == 12 and y < 11 else 3 if x < 18 else 2)
    p.poly([(15, 16), (16, 16), (20, 20), (20, 24), (11, 24), (11, 20)], "glass", lambda x, y: 3 if x < 14 else 2 if x < 19 else 1)
    p.poly([(12, 10), (19, 10), (16, 14), (15, 14)], "sand", lambda x, y: 4 if y == 10 else 3 if x < 16 else 2)
    p.poly([(15, 19), (16, 19), (19, 23), (19, 24), (12, 24), (12, 23)], "sand", lambda x, y: 4 if x + y < 36 and x < 16 else 3 if x < 17 else 2)
    p.px("sand", 3, (15, 15), (15, 16), (15, 17), (15, 18)).px("sand", 4, (16, 16))
    p.cyl(8, 6, 9, 25, "gold").cyl(22, 6, 23, 25, "gold")
    p.hcyl(6, 2, 25, 6, "gold").hcyl(6, 25, 25, 29, "gold")
    p.px("gold", 4, (7, 2), (8, 2), (9, 2), (7, 25), (8, 25)).px("gold", 0, (25, 6), (25, 29), (6, 29))
    p.erase((6, 2), (25, 2), (6, 29), (25, 29))
    p.ball(15.5, 4, 1.6, 1.4, "jade", lift=1).ball(15.5, 27, 1.6, 1.4, "jade")


def sand_veil(p):      # a sand cloud that blocks sight: a pouch of fine sand, tied with a jade cord
    p.disc(21, 27, 7, 2, "sand", lambda x, y: 3 if y < 27 else 2)
    p.ball(15, 19.5, 10.5, 8.5, "cloth")
    p.poly([(9, 3), (21, 3), (19, 9), (11, 9)], "cloth", lambda x, y: 4 if x < 13 else 3 if x < 18 else 2)
    p.disc(15, 3.5, 5, 1.6, "sand", lambda x, y: 4 if x < 15 else 3)
    p.rect(11, 9, 19, 12, "cloth", cyl_tone(11, 19))
    p.rect(10, 10, 20, 11, "jade", cyl_tone(10, 20))
    p.rect(20, 12, 21, 15, "jade", 2).px("jade", 3, (20, 12), (20, 13)).px("jade", 1, (21, 16), (22, 16))
    p.px("cloth", 1, (13, 13), (13, 14), (16, 13), (17, 14), (12, 22), (13, 23), (14, 24)).px("cloth", 4, (8, 16), (9, 15), (8, 17))
    p.stamp(15, 18, ["#.#", ".#.", "#.#"], {"#": ("cloth", 1)})
    p.px("sand*", 4, (24, 6), (5, 5), (26, 12)).px("sand*", 3, (25, 5), (4, 7), (27, 10), (23, 2))


def time_tide(p):      # allies sped up: a golden clock
    p.cyl(13, 1, 18, 4, "gold").erase((13, 1), (18, 1)).px("gold", 0, (15, 2), (16, 2))
    p.ball(6, 6, 2.6, 2.6, "gold").ball(25, 6, 2.6, 2.6, "gold")
    p.rect(8, 26, 10, 28, "gold", 1).rect(21, 26, 23, 28, "gold", 0)
    p.ball(15.5, 16, 12, 12, "gold")
    p.disc(15.5, 16, 9, 9, "gold", 0)
    p.ball(15.5, 16, 8.3, 8.3, "cloth", lift=2)
    p.px("gold", 1, (15, 9), (16, 9), (15, 23), (16, 23), (8, 16), (23, 16), (8, 15), (23, 15))
    p.line(15, 16, 15, 11, "jade", 1, 1.6).line(16, 16, 20, 18, "jade", 0, 1.2)
    p.ball(15.5, 16, 1.4, 1.4, "gold", lift=1)
    p.px("gold", 4, (7, 10), (8, 9), (9, 8), (10, 7))


# Thorn: bark, leaf and pale spores

def root_snare(p):     # a hidden tripwire: a noose of thorned bramble, its ends crossed
    def twist(x, y):
        a = math.degrees(math.atan2(y - 14, x - 15.5)) % 360
        lit = math.cos(math.radians(a - 232))
        inner = math.hypot(x - 15.5, y - 14) < 9.4
        return (3 if lit > 0.45 else 2 if lit > -0.35 else 1) - (1 if inner else 0) + (1 if int((a + (6 if inner else 0)) / 24) % 2 == 0 else 0)
    p.line(13, 23, 22, 29, "bark", 1, 2.4).line(13, 22, 21, 28, "bark", 2, 1)
    p.arc(15.5, 14, 8.4, 10.8, 0, 360, "bark", twist)
    p.line(18, 23, 9, 29, "bark", 2, 2.4).line(17, 23, 8, 28, "bark", 3, 1)
    for a, n in ((20, 3.4), (95, 2.6), (150, 3.6), (205, 3.0), (262, 3.6), (330, 2.8)):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        bx, by = 15.5 + 10.4 * c, 14 + 10.4 * s
        p.poly([(round(bx - 1.3 * s), round(by + 1.3 * c)), (round(15.5 + (10.6 + n) * c), round(14 + (10.6 + n) * s)), (round(bx + 1.3 * s), round(by - 1.3 * c))], "bark", 3 if c + s < 0 else 1)
        p.px("shell", 4 if c + s < 0 else 3, (round(15.5 + (10.6 + n) * c), round(14 + (10.6 + n) * s)))
    leaf = [".##..", "####.", "#####", ".####", "..##."]
    p.stamp(19, 2, leaf, {"#": ("leaf", 3)}).px("leaf", 4, (20, 2), (19, 3), (20, 3)).px("leaf", 1, (21, 4), (22, 5), (23, 5))
    p.stamp(2, 16, leaf, {"#": ("leaf", 2)}).px("leaf", 4, (3, 16), (2, 17)).px("leaf", 1, (4, 18), (5, 19), (6, 19))
    p.stamp(23, 19, leaf, {"#": ("leaf", 3)}).px("leaf", 4, (24, 19), (23, 20)).px("leaf", 1, (25, 21), (26, 22), (27, 22))
    p.stamp(7, 4, ["##.", "###", ".##"], {"#": ("leaf", 3)}).px("leaf", 4, (7, 4))


def spore_mend(p):     # a healing spore zone: a spore-cap mushroom
    p.cyl(12, 15, 19, 26, "shell")
    p.ball(15.5, 25.5, 5.5, 3, "shell")
    p.rect(8, 15, 23, 16, "shell", lambda x, y: 1 if (x % 2 == 0 or y == 16) else 2, where=lambda x, y: abs(x - 15.5) < 8.5 - (y - 15) * 2)
    p.ball(15.5, 13, 13, 10.5, "leaf", where=lambda x, y: y <= 14)
    p.rect(3, 14, 28, 14, "leaf", lambda x, y: 1 if x < 22 else 0, where=lambda x, y: p.c[y][x] is not None)
    for cx, cy, rx, ry in ((9.5, 8, 2.2, 1.7), (17.5, 6, 2.6, 2.0), (23.5, 11, 1.6, 1.3), (14.5, 11.5, 1.6, 1.2), (5.5, 12, 1.2, 1.0)):
        p.disc(cx, cy, rx, ry, "leaf", 4)
    p.px("shell", 4, (9, 7), (17, 5), (18, 5))
    p.px("leaf*", 4, (4, 3), (27, 4), (25, 20), (5, 21)).px("leaf*", 3, (3, 3), (5, 3), (4, 2), (4, 4), (26, 20), (24, 20), (25, 19), (25, 21), (28, 4), (6, 21))


def bark_wall(p):      # a wooden wall: three oak logs lashed together
    for x0, top in ((3, 8), (20, 8), (11, 4)):
        p.cyl(x0, top, x0 + 8, 28, "bark")
        p.px("bark", 0, (x0 + 2, top + 6), (x0 + 2, top + 7), (x0 + 5, top + 14), (x0 + 5, top + 15), (x0 + 3, 25), (x0 + 6, top + 5))
        p.px("bark", 4, (x0 + 2, top + 12), (x0 + 2, top + 13))
        p.disc(x0 + 4, top, 4.4, 2.2, "wood", lambda x, y, x0=x0: 4 if x < x0 + 4 else 3)
        p.disc(x0 + 4, top, 2.4, 1.0, "wood", 2).px("wood", 1, (x0 + 4, top))
    p.hcyl(2, 15, 29, 17, "rope").hcyl(2, 22, 29, 23, "rope")
    for x in range(4, 29, 3):
        p.px("rope", 1, (x, 16), (x + 1, 17), (x, 23))
    p.px("rope", 2, (25, 18), (25, 19), (26, 20)).px("rope", 3, (24, 18), (24, 19), (24, 20), (25, 21))


def overgrowth(p):     # vines covering the site: a thorned seed pod, sprouting
    p.px("bark", 2, (12, 27), (11, 28), (19, 27), (20, 28), (15, 28), (16, 28))
    p.ball(15.5, 19, 9, 8.5, "leaf")
    for x0, bend in ((12, -1), (16, 0), (20, 1)):
        p.path([(x0, 11), (x0 + bend, 15), (x0 + bend, 23), (x0, 27)], "leaf", 1)
    p.px("leaf", 4, (10, 14), (10, 15), (11, 13), (14, 12), (14, 13))
    for (x, y), (dx, dy) in (((6, 17), (-1, 0)), ((7, 23), (-1, 1)), ((25, 17), (1, 0)), ((24, 23), (1, 1)), ((8, 12), (-1, -1)), ((23, 12), (1, -1))):
        p.px("leaf", 3 if dx < 0 else 1, (x, y), (x, y + 1)).px("shell", 4 if dx < 0 else 3, (x + dx, y + dy), (x + 2 * dx, y + dy))
    p.path([(15, 11), (15, 7), (17, 4), (20, 3)], "leaf", 2, 2.0).path([(15, 10), (15, 7), (17, 4)], "leaf", 4, 1)
    p.stamp(19, 1, [".###.",
                    "#####",
                    ".####",
                    "..##."], {"#": ("leaf", 3)}).px("leaf", 4, (20, 1), (21, 1), (19, 2)).px("leaf", 1, (22, 3), (21, 4), (23, 2))
    p.stamp(8, 5, [".##...",
                   "####..",
                   "######",
                   ".####."], {"#": ("leaf", 3)}).px("leaf", 4, (9, 5), (8, 6), (9, 6)).px("leaf", 1, (11, 8), (12, 8), (12, 7))


# Vesper: soul mint, bone and night violet

def echo_step(p):      # a ghost of you walks ahead: a pale mask trailing soul fire
    p.poly([(3, 7), (1, 5), (2, 10), (5, 12)], "violet", 3).poly([(28, 7), (30, 5), (29, 10), (26, 12)], "violet", 1)
    p.poly([(6, 6), (11, 3), (20, 3), (25, 6), (26, 14), (23, 20), (18, 26), (13, 26), (8, 20), (5, 14)], "bone", ball_tone(15.5, 13, 12.5, 14))
    for y, (a, b) in enumerate(((8, 12), (8, 13), (8, 13), (10, 14)), 11):   # slanted eye slits, burning inside
        p.rect(a, y, b, y, "violet", 0).rect(31 - b, y, 31 - a, y, "violet", 0)
    for x0, x1, y in ((8, 13, 12), (10, 13, 13)):                           # (on even rows, so the HUD copy keeps them)
        p.rect(x0, y, x1, y, "mint", 3).rect(31 - x1, y, 31 - x0, y, "mint", 3)
    p.px("mint", 4, (9, 12), (10, 12), (11, 13), (21, 12), (20, 12), (20, 13))
    p.px("bone", 4, (7, 10), (8, 10), (9, 10), (24, 10), (23, 10), (15, 15), (15, 16), (15, 17), (15, 18))
    p.px("bone", 1, (16, 15), (16, 16), (16, 17), (16, 18), (15, 19), (16, 19), (13, 22), (14, 22), (15, 22), (16, 22), (17, 22), (18, 22), (10, 18), (11, 19), (21, 18), (20, 19))
    p.px("bone", 0, (22, 4), (21, 5), (22, 6), (21, 7), (22, 8))
    p.poly([(15, 5), (16, 5), (17, 7), (16, 9), (15, 9), (14, 7)], "mint", lambda x, y: 4 if x < 16 and y < 8 else 3 if x < 17 else 2)
    p.px("mint*", 3, (10, 27), (9, 29), (21, 27), (22, 29), (13, 29), (18, 29)).px("mint*", 4, (11, 28), (20, 28), (15, 29), (16, 30))
    p.px("mint*", 2, (8, 30), (23, 30), (12, 31), (19, 31))


def judgment(p):       # a blinding soul flare: a flare stick, its head burning white
    p.cyl(14, 16, 17, 29, "violet")
    p.rect(14, 20, 17, 21, "iron", cyl_tone(14, 17)).rect(14, 26, 17, 27, "iron", cyl_tone(14, 17))
    p.hcyl(12, 14, 19, 16, "iron")
    p.poly([(15.5, 0), (18, 5), (25, 8), (18, 11), (15.5, 15), (13, 11), (6, 8), (13, 5)], "mint", lambda x, y: 3 if x + y < 24 else 2)
    p.ball(15.5, 8, 4.2, 4.2, "bone", lift=2)
    p.px("mint", 4, (15, 2), (15, 3), (9, 8), (10, 8), (15, 4), (11, 8)).px("bone", 4, (16, 3), (21, 8), (15, 12))
    p.px("mint*", 3, (9, 2), (10, 3), (22, 2), (21, 3), (9, 14), (10, 13), (22, 14), (21, 13)).px("mint*", 4, (8, 1), (23, 1))
    p.px("mint*", 2, (3, 8), (28, 8))


def soul_lantern(p):   # a lantern that pulses and reveals: an iron lantern with a soul flame
    p.arc(15.5, 4.5, 1.6, 3.2, 150, 390, "iron", lambda x, y: 4 if x < 15 and y < 4 else 3 if x < 17 else 1)
    p.poly([(9, 10), (14, 6), (17, 6), (22, 10)], "violet", lambda x, y: 4 if y == 6 else 3 if x < 14 else 2 if x < 19 else 1)
    p.rect(10, 11, 21, 23, "mint", lambda x, y: 1 if x < 18 else 0)
    p.flame(15.5, 22, 9, 6, "mint", lean=1.0)
    p.px("mint", 2, (12, 21), (19, 21), (12, 22), (19, 22), (13, 23), (18, 23))
    p.cyl(10, 11, 11, 23, "violet").rect(20, 11, 21, 23, "violet", lambda x, y: 1 if x == 20 else 0)
    p.rect(10, 11, 21, 11, "violet", 1).px("violet", 4, (10, 12), (10, 13), (10, 14))
    p.hcyl(8, 24, 23, 26, "violet").rect(10, 27, 21, 28, "violet", lambda x, y: 1 if x < 18 else 0)
    p.px("violet", 4, (9, 24), (10, 24), (11, 24)).px("violet", 0, (23, 26))
    p.px("mint*", 3, (5, 14), (26, 18), (6, 20), (25, 12)).px("mint*", 4, (5, 15), (26, 17))


def verdict(p):        # every enemy revealed: an all-seeing eye amulet
    def lens(r, k):
        return lambda x, y: (x - 15.5) ** 2 + (y - 17.5 + k) ** 2 <= r * r and (x - 15.5) ** 2 + (y - 17.5 - k) ** 2 <= r * r
    p.arc(15.5, 5, 1.6, 3.2, 0, 360, "iron", lambda x, y: 4 if x < 15 and y < 5 else 3 if x < 17 else 1)
    p.rect(14, 8, 17, 10, "violet", cyl_tone(14, 17))
    p.rect(2, 8, 29, 26, "violet", lambda x, y: 4 if y < 13 and x < 13 else 3 if y < 16 else 2 if y < 20 else 1, where=lens(15.0, 8.5))
    p.rect(2, 8, 29, 26, "bone", lambda x, y: 4 if x < 12 else 3 if x < 21 else 2, where=lens(13.1, 8.5))
    p.rect(2, 8, 29, 12, "bone", 1, where=lambda x, y: lens(13.1, 8.5)(x, y) and not lens(13.1, 8.5)(x, y - 1))
    p.ball(15.5, 17.5, 4.6, 4.6, "mint", lift=1)
    p.rect(14, 16, 17, 19, "violet", 0)
    p.px("snow", 4, (12, 15), (13, 15), (12, 16))
    p.poly([(14, 25), (17, 25), (15.5, 29)], "violet", lambda x, y: 3 if x < 16 else 1)
    p.poly([(7, 22), (10, 23), (7, 26)], "violet", 3).poly([(24, 22), (21, 23), (24, 26)], "violet", 1)
    p.px("mint*", 3, (4, 6), (27, 6), (4, 28), (27, 28)).px("mint*", 4, (5, 7), (26, 7))


OBJECTS = {
    "slag_pot": slag_pot, "heat_shimmer": heat_shimmer, "ember_dash": ember_dash, "colossus_step": colossus_step,
    "wind_pad": wind_pad, "static_orb": static_orb, "charged_slide": charged_slide, "storm_call": storm_call,
    "leech_pool": leech_pool, "rot_cloud": rot_cloud, "miasma_wall": miasma_wall, "bog_tide": bog_tide,
    "ink_burst": ink_burst, "pressure_bubble": pressure_bubble, "sonar_pearl": sonar_pearl, "undertow": undertow,
    "frost_mine": frost_mine, "stasis_pane": stasis_pane, "hold_moment": hold_moment, "glacial_hour": glacial_hour,
    "rewind": rewind, "hourglass_trap": hourglass_trap, "sand_veil": sand_veil, "time_tide": time_tide,
    "root_snare": root_snare, "spore_mend": spore_mend, "bark_wall": bark_wall, "overgrowth": overgrowth,
    "echo_step": echo_step, "judgment": judgment, "soul_lantern": soul_lantern, "verdict": verdict,
}
ICONS = GLYPHS = OBJECTS  # the old names


# ------------------------------------------------------------------ rendering

_CELLS = {}


def cells(ability_id, size=N):
    """The painted grid of (material, tone) or None; at 16 the dominant cell of each 2 x 2 block."""
    if (ability_id, size) not in _CELLS:
        if size == N:
            p = Paint()
            OBJECTS[ability_id](p)
            grid = p.c
        else:
            assert size * 2 == N, size
            full = cells(ability_id, N)
            grid = []
            for y in range(size):
                row = []
                for x in range(size):
                    block = [full[2 * y + dy][2 * x + dx] for dy in (0, 1) for dx in (0, 1)]
                    solid = [c for c in block if c is not None]
                    if len(solid) < 2:
                        row.append(None)
                        continue
                    # the most frequent cell; on a tie the lighter one (it is the detail), then the block's reading order
                    best = max(solid, key=lambda c: (solid.count(c), c[1], -block.index(c)))
                    row.append(best)
                grid.append(row)
        _CELLS[(ability_id, size)] = grid
    return _CELLS[(ability_id, size)]


def shade(mat, t, state, small):
    """RGB of a cell in a state."""
    tones = RAMPS[mat.rstrip("*")]
    c = tones[t]
    if small and t == 0:   # on the HUD's near-black tile the darkest tone would vanish
        c = mix(tones[0], tones[1], 0.6)
    if state == "ready":
        c = mix(c, tones[min(4, t + 1)], 0.55)
    elif state == "off":
        c = mix(grey(c, 0.30), INK, 0.34 if small else 0.42)
        if small:
            c = tuple(max(v, f) for v, f in zip(c, (0x26, 0x29, 0x33)))
    elif state == "empty":
        c = mix(grey(c, 0.08), (0x2c, 0x2e, 0x3a), 0.34)
    return c


CROSS = [".oo...oo.",
         "oRRo.oRRo",
         "oRRRoRRro",
         ".oRRRRro.",
         "..oRRro..",
         ".oRRrrro.",
         "oRRrorrro",
         "oRro.orro",
         ".oo...oo."]
CROSS_COLOURS = {"o": (0x1a, 0x0c, 0x12), "R": (0xf0, 0x4a, 0x42), "r": (0xb4, 0x26, 0x2c)}


def ring(rows, colour, solid, diagonal=False):
    """Paints every transparent pixel that touches `solid` (a set of (x, y), grown by the ring): one more ring around the
    object, which ignores its loose sparks."""
    size = len(rows)
    steps = ((0, 1), (1, 0), (0, -1), (-1, 0)) + (((1, 1), (1, -1), (-1, 1), (-1, -1)) if diagonal else ())
    todo = []
    for y in range(size):
        for x in range(size):
            if rows[y][x][3]:
                continue
            for dx, dy in steps:
                xx, yy = x + dx, y + dy
                if (xx, yy) in solid:
                    todo.append((x, y))
                    break
    for x, y in todo:
        rows[y][x] = colour + (255,)
    solid.update(todo)
    return rows


def icon(agent, ability_id, slot, state="on", size=SIZE):
    """size x size RGBA rows (32: the item, 16: the HUD copy), the object alone on transparent.
    state: on, off (the HUD tile switched off), empty (no charge left), ready (ultimate only, lit)."""
    small = size < N
    grid = cells(ability_id, size)
    rows = [[CLEAR] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            if grid[y][x]:
                rows[y][x] = shade(grid[y][x][0], grid[y][x][1], state, small) + (255,)
    if not small:   # the outline: every transparent pixel next to an outlined material, tinted by that material
        edge = []
        for y in range(size):
            for x in range(size):
                if grid[y][x]:
                    continue
                near = [grid[y + dy][x + dx] for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0)) if 0 <= x + dx < size and 0 <= y + dy < size]
                mats = [c[0] for c in near if c and not c[0].endswith("*")]
                if mats:
                    edge.append((x, y, mats[0]))
        for x, y, mat in edge:
            c = outline_of(mat)
            if state == "empty":
                c = grey(c, 0.1)
            elif state == "off":
                c = mix(grey(c, 0.3), INK, 0.3)
            rows[y][x] = c + (255,)
        if state == "ready":   # the lit ultimate: a bright ring of the agent's colour, then a deeper one
            sig, second = signature(agent)
            solid = {(x, y) for x, y, _ in edge} | {(x, y) for y in range(size) for x in range(size) if grid[y][x] and not grid[y][x][0].endswith("*")}
            ring(rows, mix(sig, (255, 255, 255), 0.45), solid, diagonal=True)
            ring(rows, mix(sig, second, 0.35), solid)
        if state == "empty":
            ox = oy = size - len(CROSS)
            for dy, line in enumerate(CROSS):
                for dx, ch in enumerate(line):
                    if ch != ".":
                        rows[oy + dy][ox + dx] = CROSS_COLOURS[ch] + (255,)
    return rows


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
                rows = icon(a, ab["id"], ab["slot"], st or "on", ITEM_SIZE)
                vi.write_item(item_name(a["id"], ab["id"], st), rows)
                n += 1
    print(f"wrote {n} ability items under {PACK}")


# ------------------------------------------------------------------ contact sheets (for a look, not part of the pack)

def _sheet(path, grid, size, k, ground, pad=6):
    """grid: rows of RGBA sprites (or None), each drawn x k on `ground`."""
    cell = size * k + pad
    w, h = cell * max(len(r) for r in grid) + pad, cell * len(grid) + pad
    img = [[ground + (255,)] * w for _ in range(h)]
    for r, line in enumerate(grid):
        for c, rows in enumerate(line):
            if rows is None:
                continue
            for y in range(size):
                for x in range(size):
                    if rows[y][x][3]:
                        for dy in range(k):
                            oy = pad + r * cell + y * k + dy
                            img[oy][pad + c * cell + x * k:pad + c * cell + x * k + k] = [rows[y][x]] * k
    pathlib.Path(path).write_bytes(vi.png(img))


def sheets(out, k=8):
    """all_on.png (the 32 items), <agent>.png (4 abilities x on, off, empty, ready), hud.png (the 16 px copies, on and off,
    on the HUD tile's colour)."""
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    ags = agents()
    grey_ground = (0x8b, 0x8b, 0x8b)   # the inventory slot grey the reference sheets use
    _sheet(out / "all_on.png", [[icon(a, a["abilities"][i]["id"], a["abilities"][i]["slot"], "on", N) for a in ags] for i in range(4)], N, k, grey_ground)
    for a in ags:
        grid = [[icon(a, ab["id"], ab["slot"], st, N) if st != "ready" or ab["slot"] == 7 else None for st in ("on", "off", "empty", "ready")]
                for ab in a["abilities"]]
        _sheet(out / f"{a['id']}.png", grid, N, k, grey_ground)
    hud = [[icon(a, ab["id"], ab["slot"], st, SIZE) for ab in a["abilities"] for st in ("on", "off")] for a in ags]
    _sheet(out / "hud.png", hud, SIZE, k, mix((0x40, 0x48, 0x40), INK, 0.85))


if __name__ == "__main__":
    if "--sheets" in sys.argv:
        sheets(sys.argv[sys.argv.index("--sheets") + 1])
    else:
        main()
