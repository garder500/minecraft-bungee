#!/usr/bin/env python3
"""ValoBlock agent portraits: painted pixel-art busts, stdlib only, deterministic.

    python3 resource-pack/art/valoblock_portraits.py --sheet out.png     (a contact sheet; the files are written by
                                                                          valoblock_ui.py and valoblock_agents.py)

One original character per agent, built from the colour plan of arena-plugin/src/main/resources/valoblock/agents/<id>.json
(the same plan as the agent's in-game skin: hood or hair, skin, eyes, mask, torso, trim, glow), so the bust, the small
head and the body in the match are the same person. Each has its own silhouette and one signature colour:

    cinder   ember mohawk, war paint, a respirator with glowing vents          orange
    gale     windswept hair, goggles pushed up, a scarf flying off the shoulder  yellow
    thorn    leaf hair, two branch antlers, a mantle of leaves                  green
    rime     white bob, an ice visor, a fur collar                              ice blue
    vesper   a pointed cowl, silver locks, glowing eyes                         mint
    nacre    a brass diving helmet with a round teal faceplate                  pink
    sahrin   a wrapped turban with a jewel, a veil with coins                   gold
    murk     a round hood, a gas mask with two lit lenses and a filter          toxic green

How a bust is painted (Bust below): every material is a mask (ellipse, polygon) filled from a 5 tone ramp whose hue shifts
from a cold shadow to a warm light, the tone picked by a simple lit volume (sphere, cylinder, slope) with the light at the
top left; cast shadows are laid over that (under a hood, a chin, a collar); details are placed by hand (eyes, seams,
rivets, glints); then the right-hand edge takes a rim light in the signature colour and the silhouette a dark outline of
its own colour. No noise, no pillow shading.

    bust(agent)        48 x 64 RGBA rows: the agent-select portrait (vb_layout.PORTRAIT_W/H); None = nobody picked yet
    tile(agent, ...)   32 x 32: the head of the bust on the agent's menu colour (the roster item agent_<id>)
    head(agent, state) 10 x 10: the 8 x 8 face of the top bar and the kill feed in its frame (alive, dead, none)
"""
import json
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import valoblock_items as vi  # noqa: E402

AGENTS = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/agents"
W, H = 48, 64
X0, X1 = 5, 43          # the art lives in columns 5..42: with its outline it spans 4..43, the width the menu was laid out for
CLEAR = (0, 0, 0, 0)
SHADOW = (26, 16, 52)   # what a shadow mixes towards: a cold violet
LIGHT = (255, 244, 214)  # what a light mixes towards: a warm white
INK = (10, 9, 18)


def rgb(hex6):
    return vi.rgb(hex6.lstrip("#"))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(base, shadow=SHADOW, light=LIGHT, spread=1.0):
    """Five tones of a material, dark to light, the hue sliding from `shadow` to `light`."""
    c = rgb(base) if isinstance(base, str) else base
    k = spread

    def gain(f):   # brighter by multiplying: a dark material stays dark and saturated in its lights
        return tuple(min(255, round(v * f + 6 * (f - 1))) for v in c)
    return [mix(mix(c, (0, 0, 0), 0.56 * k), shadow, 0.28), mix(mix(c, (0, 0, 0), 0.29 * k), shadow, 0.14), c,
            mix(gain(1 + 0.24 * k), light, 0.14 * k), mix(gain(1 + 0.48 * k), light, 0.32 * k)]


# ------------------------------------------------------------------ masks (pixel centres, y down)

def E(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1


def R(x0, y0, x1, y1):
    return lambda x, y: x0 <= x < x1 and y0 <= y < y1


def P(pts):
    def inside(x, y):
        c = False
        j = len(pts) - 1
        for i in range(len(pts)):
            (xi, yi), (xj, yj) = pts[i], pts[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                c = not c
            j = i
        return c
    return inside


def any_of(*ms):
    return lambda x, y: any(m(x, y) for m in ms)


def all_of(*ms):
    return lambda x, y: all(m(x, y) for m in ms)


def but(m, *nots):
    return lambda x, y: m(x, y) and not any(n(x, y) for n in nots)


def mirror(m, cx=24.0):
    return lambda x, y: m(2 * cx - x, y)


def face_mask(cx=24.0, cy=21.0, rx=9.5, top=11.5, bottom=12.0, jaw=0.56):
    """A head: an ellipse above the eye line, cheeks that narrow to a rounded chin below it."""
    def inside(x, y):
        if y <= cy:
            return ((x - cx) / rx) ** 2 + ((y - cy) / top) ** 2 <= 1
        t = (y - cy) / bottom
        if t > 1:
            return False
        hw = rx * (1 - jaw * t * t)
        if t > 0.74:
            hw *= math.sqrt(max(0.0, (1.05 - t) / 0.31))
        return abs(x - cx) <= hw
    return inside


# ------------------------------------------------------------------ light (0 dark .. 1 lit), the light at the top left

def clamp(v):
    return 0.0 if v < 0 else 0.999 if v > 0.999 else v


def sph(cx, cy, rx, ry, gain=1.0, bias=0.0):
    def f(x, y):
        dx, dy = (x - cx) / rx, (y - cy) / ry
        dz = math.sqrt(max(0.04, 1 - dx * dx - dy * dy))
        return clamp(((-0.50 * dx - 0.62 * dy + 0.60 * dz) + 0.28) / 1.22 * gain + bias)
    return f


def cyl(cx, rx, bias=0.0):
    """An upright cylinder."""
    def f(x, y):
        dx = max(-1.0, min(1.0, (x - cx) / rx))
        return clamp((-0.55 * dx + 0.62 * math.sqrt(1 - dx * dx) + 0.2) / 1.2 + bias)
    return f


def lin(x0, y0, x1, y1, hi=0.95, lo=0.05):
    """hi at (x0, y0), lo at (x1, y1)."""
    dx, dy = x1 - x0, y1 - y0
    ll = dx * dx + dy * dy

    def f(x, y):
        t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / ll))
        return clamp(hi + (lo - hi) * t)
    return f


def flat(v):
    return lambda x, y: v


# ------------------------------------------------------------------ the canvas

class Bust:
    def __init__(self, w=W, h=H):
        self.w, self.h = w, h
        self.c = [[None] * w for _ in range(h)]

    def fill(self, mask, tones, shade=flat(0.5), on=False):
        """Paints the mask from a ramp. on: only over pixels already painted."""
        n = len(tones)
        for y in range(self.h):
            for x in range(X0, X1):
                if (not on or self.c[y][x] is not None) and mask(x + 0.5, y + 0.5):
                    self.c[y][x] = tones[int(clamp(shade(x + 0.5, y + 0.5)) * n)]
        return self

    def dark(self, mask, a=0.35, colour=SHADOW):
        """A cast shadow over what is painted."""
        for y in range(self.h):
            for x in range(X0, X1):
                if self.c[y][x] is not None and mask(x + 0.5, y + 0.5):
                    self.c[y][x] = mix(self.c[y][x], colour, a)
        return self

    def lite(self, mask, a=0.35, colour=LIGHT):
        return self.dark(mask, a, colour)

    def px(self, pts, colour):
        for x, y in pts:
            if X0 <= x < X1 and 0 <= y < self.h:
                self.c[y][x] = colour
        return self

    def hline(self, x0, x1, y, colour):
        return self.px([(x, y) for x in range(x0, x1 + 1)], colour)

    def vline(self, x, y0, y1, colour):
        return self.px([(x, y) for y in range(y0, y1 + 1)], colour)

    def stamp(self, x0, y0, rows, pal):
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch in pal:
                    self.px([(x0 + dx, y0 + dy)], pal[ch])
        return self

    def erase(self, mask):
        for y in range(self.h):
            for x in range(self.w):
                if mask(x + 0.5, y + 0.5):
                    self.c[y][x] = None
        return self

    def finish(self, rim):
        """Rim light on the edge facing away from the light, a lit top-left edge, then the outline. RGBA rows."""
        c, w, h = self.c, self.w, self.h

        def empty(x, y):
            return not (0 <= x < w and 0 <= y < h) or c[y][x] is None

        lit = {}
        for y in range(h):
            for x in range(w):
                if c[y][x] is None:
                    continue
                if empty(x + 1, y) and x + 1 < w:
                    lit[(x, y)] = mix(c[y][x], rim, 0.64)
                elif empty(x + 2, y) and x + 2 < w and not empty(x + 1, y):
                    lit[(x, y)] = mix(c[y][x], rim, 0.22)
                elif (empty(x - 1, y) or (empty(x, y - 1) and y > 0)) and not empty(x + 1, y):
                    lit[(x, y)] = mix(c[y][x], LIGHT, 0.16)
        for (x, y), v in lit.items():
            c[y][x] = v
        rows = [[CLEAR] * w for _ in range(h)]
        for y in range(h):
            for x in range(w):
                if c[y][x] is not None:
                    rows[y][x] = c[y][x] + (255,)
                    continue
                near = [c[yy][xx] for xx, yy in ((x - 1, y), (x, y - 1), (x + 1, y), (x, y + 1)) if 0 <= xx < w and 0 <= yy < h and c[yy][xx] is not None]
                if near:
                    r, g, b = (sum(v[i] for v in near) // len(near) for i in range(3))
                    rows[y][x] = mix((r, g, b), INK, 0.80) + (255,)
        return rows


# ------------------------------------------------------------------ shared anatomy

CX = 24.0
TORSO = P([(19, 35), (29, 35), (31, 38), (39, 41), (43, 47), (43, 65), (5, 65), (5, 47), (9, 41), (17, 38)])


def body_light(x, y):
    """The chest as a broad curved slab: lit on its upper left, turning away to the right and down."""
    return clamp(0.60 - (x - 24) / 46 - (y - 40) / 70)


def torso(b, tones, mask=TORSO):
    b.fill(mask, tones, body_light)
    b.dark(all_of(mask, R(0, 60, 48, 64)), 0.22)          # the bust sinks into shadow at the bottom
    return b


def neck(b, tones, x0=20, x1=28, y1=39):
    b.fill(R(x0, 30, x1, y1), tones, cyl(CX, (x1 - x0) / 2 + 1, -0.08))
    b.dark(P([(x0, 31), (x1, 31), (x1, 35.5), (CX + 1, 37.5), (x0, 34)]), 0.42)   # the jaw's shadow, thrown down and right
    return b


def pauldron(b, tones, cx, cy=44, rx=7.5, ry=6.5):
    m = all_of(E(cx, cy, rx, ry), R(0, 0, 48, cy + 3))
    b.fill(m, tones, sph(cx, cy, rx, ry))
    b.dark(all_of(E(cx, cy + 4, rx, 3), TORSO, lambda x, y: y >= cy + 3), 0.38)
    return b


def face(b, tones, cy=21.0, **kw):
    m = face_mask(cy=cy, **kw)
    b.fill(m, tones, sph(CX, cy, 12, 14, 1.0, 0.04))
    return m


def eye_pair(b, y, white, iris, lash, xl=18, glow=None, pupil=None):
    """Two 3 x 2 eyes at rows y..y+1, the left one starting at column xl, mirrored on the face's centre line."""
    xr = 47 - xl - 2
    for x0, flip in ((xl, False), (xr, True)):
        cols = [x0, x0 + 1, x0 + 2]
        if flip:
            cols.reverse()
        if glow:   # a lit eye: no white, a bright core
            b.px([(cols[0], y), (cols[2], y), (cols[0], y + 1), (cols[2], y + 1)], iris)
            b.px([(cols[1], y), (cols[1], y + 1)], glow)
        else:
            b.px([(cols[0], y), (cols[0], y + 1)], white)
            b.px([(cols[1], y), (cols[2], y)], iris).px([(cols[1], y + 1)], pupil or mix(iris, INK, 0.55)).px([(cols[2], y + 1)], mix(white, SHADOW, 0.35))
        b.hline(min(cols), max(cols), y - 1, lash)
    return b


def nose_mouth(b, sk, ny=24, my=29, mouth=None, smirk=False):
    b.px([(25, ny), (25, ny + 1), (25, ny + 2), (24, ny + 2)], sk[1]).px([(23, ny + 1), (23, ny)], sk[4]).px([(26, ny + 2)], sk[0])
    m = mouth or mix(sk[0], (120, 40, 50), 0.35)
    b.hline(22, 26, my, m).hline(23, 25, my + 1, sk[3])
    if smirk:
        b.px([(27, my - 1)], m).px([(21, my)], sk[1])
    return b


# ------------------------------------------------------------------ the eight agents

def cinder(b, a):
    sk, hair, metal = ramp("c48a6a"), ramp("3a2440"), ramp("5a4a66")
    suit, plate = ramp(a["colors"]["torso"]), ramp("4a3350")
    ember = ramp(a["colors"]["trim"], shadow=(120, 20, 30), light=(255, 240, 150))
    torso(b, suit)
    # a high armoured collar and two heavy pauldrons with an ember edge
    pauldron(b, plate, 11, 45, 8, 7)
    pauldron(b, plate, 37, 45, 8, 7)
    for cx in (11, 37):
        b.fill(all_of(E(cx, 45, 8, 7), but(R(0, 46, 48, 48), E(cx, 45, 6.6, 9))), ember, flat(0.5))
        b.fill(all_of(E(cx, 45, 8, 7), R(0, 46, 48, 48)), ember, lin(4, 0, 44, 0, 0.9, 0.3))
    b.fill(P([(16, 39), (32, 39), (30, 62), (24, 64), (18, 62)]), plate, lin(14, 38, 34, 64))
    b.fill(P([(16, 39), (19, 39), (24, 47), (29, 39), (32, 39), (24, 51)]), metal, lin(14, 38, 34, 50))
    # the ember core in the chest and the cracks that run from it
    b.fill(P([(24, 51), (27.5, 55), (24, 60), (20.5, 55)]), ember, lin(22, 52, 27, 59, 0.99, 0.35))
    b.px([(23, 54), (24, 54), (23, 55)], (255, 244, 190))
    b.px([(19, 53), (18, 52), (17, 52), (29, 57), (30, 58), (31, 58), (20, 58), (19, 59), (28, 52), (29, 51)], ember[2])
    neck(b, hair, 19, 29, 39)
    b.fill(P([(18, 34), (30, 34), (32, 40), (24, 43), (16, 40)]), plate, lin(16, 34, 32, 42))   # the collar
    b.hline(17, 31, 40, ember[2]).hline(19, 29, 41, ember[1])
    m = face(b, sk)
    # shaved sides: a band of stubble that follows the skull
    b.fill(all_of(m, lambda x, y: y < 12.6 + 0.045 * (x - 24) ** 2), hair, sph(CX, 21, 12, 14, 0.62, 0.0))
    b.px([(15, 17), (15, 18), (32, 17), (32, 18)], sk[1])
    # war paint: a dark band across the eyes, the eyes burning through it
    b.fill(all_of(m, P([(13, 19), (35, 19), (35, 22.5), (31, 24), (17, 24), (13, 22.5)])), hair, lin(14, 19, 34, 24, 0.55, 0.15))
    eye_pair(b, 21, None, ember[2], hair[0], glow=(255, 236, 160))
    b.px([(17, 21), (30, 21), (17, 22), (30, 22)], ember[1])
    b.px([(17, 19), (18, 19), (19, 20), (30, 19), (29, 19), (28, 20)], INK)      # brows drawn down to the middle
    # the respirator: a muzzle with two side filters and three glowing vents
    muzzle = P([(14.5, 24.5), (33.5, 24.5), (33.5, 29), (29.5, 34.2), (18.5, 34.2), (14.5, 29)])
    b.fill(muzzle, metal, lin(14, 24, 33, 35, 0.9, 0.1))
    b.fill(P([(20, 25.5), (28, 25.5), (29, 31), (26.5, 34.2), (21.5, 34.2), (19, 31)]), plate, lin(19, 25, 29, 34, 0.75, 0.2))   # the snout
    for y in (27, 29, 31):
        b.hline(21, 26, y, ember[3] if y == 27 else ember[2]).px([(21, y)], (255, 240, 170))
    for s_ in (-1, 1):                                          # a filter can on each cheek, pointing down and out
        cx = CX + s_ * 7.6
        b.fill(P([(cx - 2, 27), (cx + 2, 27), (cx + 2 + s_ * 0.8, 33.2), (cx - 2 + s_ * 0.8, 33.2)]), metal, cyl(cx + s_ * 0.4, 2.6, 0.1 if s_ < 0 else -0.1))
        b.hline(int(cx - 1.5 + s_ * 0.5), int(cx + 1.5 + s_ * 0.5), 31, metal[0]).hline(int(cx - 1.5), int(cx + 1.5), 27, metal[4] if s_ < 0 else metal[3])
    b.hline(15, 32, 24, metal[4]).hline(15, 32, 25, metal[1])
    # the mohawk: a crest leaning back, its tips still burning
    crest = P([(19.5, 13), (17.5, 7), (19.5, 2.5), (22, 5.5), (24.5, 1.5), (27, 5), (30, 2), (31.5, 7.5), (30, 13)])
    b.fill(crest, hair, lin(18, 5, 32, 13, 0.7, 0.1))
    b.fill(all_of(crest, lambda x, y: y < 6.5), ember, lin(19, 1, 31, 7, 0.99, 0.35))
    b.fill(all_of(crest, lambda x, y: 6.5 <= y < 8.5 and (int(x) + int(y)) % 2 == 0), ember, flat(0.3))
    b.px([(21, 9), (21, 10), (21, 11), (25, 8), (25, 9), (25, 10), (25, 11), (28, 9), (28, 10), (28, 11)], hair[0])
    return rgb(a["colors"]["trim"])


def gale(b, a):
    sk, hair = ramp("e0ae8c"), ramp("4658b4", light=(210, 236, 255))
    jacket, sleeve = ramp(a["colors"]["torso"]), ramp(a["colors"]["arm"])
    gold = ramp(a["colors"]["trim"], shadow=(150, 80, 20), light=(255, 255, 230))
    scarf = ramp("e4ecfa", shadow=(70, 80, 150), light=(255, 255, 255), spread=0.8)
    glass = ramp("9ad0f4", light=(255, 255, 255))
    torso(b, jacket)
    b.fill(all_of(TORSO, any_of(R(0, 0, 15, 64), R(34, 0, 48, 64))), sleeve, body_light)   # lighter sleeves
    b.dark(any_of(R(14, 44, 15, 64), R(34, 44, 35, 64)), 0.4)
    b.vline(24, 44, 63, gold[1]).vline(23, 44, 63, gold[3])                               # the zip
    b.fill(P([(17, 40), (23, 44), (23, 52), (15, 46)]), jacket, flat(0.75))                # lapels
    b.fill(P([(31, 40), (25, 44), (25, 52), (33, 46)]), jacket, flat(0.3))
    b.hline(15, 22, 46, gold[2]).hline(26, 33, 46, gold[1])
    b.stamp(27, 51, ["#.....#", "##.#.##", ".#####.", "...#..."], {"#": gold[3]})        # a pilot's wing pin
    b.hline(5, 12, 52, gold[2]).hline(5, 12, 53, gold[1]).hline(36, 42, 52, gold[1]).hline(36, 42, 53, gold[0])
    neck(b, sk)
    # the scarf: wound twice round the neck, one end thrown out to the right by the wind
    tail = P([(30, 37), (36, 33), (43, 30), (43, 36), (38, 37), (43, 41), (36, 42), (31, 42)])
    b.fill(tail, scarf, lin(30, 32, 43, 42, 0.8, 0.2))
    b.fill(P([(15, 36), (33, 35), (34, 42), (24, 45), (14, 42)]), scarf, lin(14, 35, 34, 45))
    b.dark(P([(15, 39.5), (33, 38.5), (34, 40), (15, 41)]), 0.3).dark(R(36, 37, 43, 38), 0.3)
    m = face(b, sk)
    eye_pair(b, 21, (246, 244, 238), hair[2], mix(hair[0], INK, 0.4))
    b.hline(17, 20, 19, hair[1]).hline(27, 30, 19, hair[1]).px([(16, 20), (31, 18)], hair[1])   # one brow raised
    nose_mouth(b, sk, smirk=True)
    b.px([(19, 25), (21, 26), (28, 26), (30, 25)], sk[1])                                       # freckles
    # hair blown to the right in three locks, one of them bleached yellow
    mass = any_of(all_of(E(24, 13, 11.5, 9.5), lambda x, y: y < 17 - 0.02 * (x - 24) ** 2 + (2.5 if x < 17 or x > 31 else 0)),
                  P([(26, 5), (34, 1.5), (31, 6), (39, 3.5), (34, 9), (41, 9), (34, 14), (30, 12)]),
                  P([(14, 9), (17, 3), (22, 2), (28, 3), (27, 8), (15, 13)]))
    b.fill(mass, hair, sph(22, 11, 16, 12, 1.0, 0.05))
    b.fill(all_of(mass, P([(27, 4), (34, 1.5), (31, 6), (39, 3.5), (34, 8), (29, 8)])), gold, lin(27, 2, 39, 8, 0.95, 0.3))
    for pts in (((16, 9), (20, 5), (25, 4)), ((18, 12), (23, 8), (29, 7)), ((30, 11), (35, 10), (38, 9))):   # strand lines
        for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
            n = max(abs(xb - xa), abs(yb - ya))
            b.px([(round(xa + (xb - xa) * i / n), round(ya + (yb - ya) * i / n)) for i in range(n + 1)], hair[1])
    b.px([(17, 6), (18, 5), (19, 5), (20, 4), (22, 4)], hair[4])
    # goggles pushed up on the forehead
    b.fill(R(13, 13, 35, 16), gold, lin(13, 13, 35, 16, 0.6, 0.2))
    for cx in (19.5, 28.5):
        b.fill(E(cx, 14.5, 3.6, 3.1), gold, sph(cx, 14.5, 3.6, 3.1))
        b.fill(E(cx, 14.5, 2.5, 2.0), glass, lin(cx - 2, 12.5, cx + 2, 16.5))
        b.px([(int(cx - 1.5), 13)], (255, 255, 255))
    b.dark(all_of(m, R(14, 17, 34, 18)), 0.3)
    return rgb(a["colors"]["trim"])


def thorn(b, a):
    sk, leaf = ramp("b88a62"), ramp("5c7c30", shadow=(20, 40, 50), light=(236, 250, 150))
    bark, lime = ramp("7a5230", shadow=(40, 20, 40)), ramp(a["colors"]["trim"], shadow=(20, 60, 50), light=(240, 255, 180))
    glow = rgb(a["colors"]["glow"])
    torso(b, bark)
    for x in (9, 14, 19, 29, 34, 39):                      # bark grain on the chest plate
        b.vline(x, 47 + (x % 3), 63, bark[1])
    b.px([(11, 52), (12, 53), (31, 55), (32, 56), (36, 50), (21, 58)], bark[0])
    # a mantle of leaves: three rows of scales over the shoulders
    for row, (y, r) in enumerate(((47, 4.2), (43.5, 4.0), (40, 3.6))):
        for i in range(-5, 6):
            cx = CX + i * 4.4 + (2.2 if row % 2 else 0)
            if abs(cx - CX) < (6.5 if row == 0 else 4.5 if row == 1 else 0):
                continue
            mk = all_of(P([(cx - r, y - 3), (cx + r, y - 3), (cx + r, y), (cx, y + r + 1), (cx - r, y)]), TORSO)
            b.fill(mk, lime if (i + row) % 3 == 0 else leaf, lin(cx - r, y - 3, cx + r, y + r))
            b.dark(all_of(P([(cx - r, y), (cx, y + r + 1), (cx + r, y), (cx + r, y + 1.4), (cx, y + r + 2.4), (cx - r, y + 1.4)]), TORSO), 0.4)
    b.fill(all_of(TORSO, R(0, 36, 48, 40.5), but(lambda x, y: True, R(19, 0, 29, 64))), leaf, body_light)
    neck(b, sk)
    b.fill(P([(18, 36), (30, 36), (28, 41), (24, 43), (20, 41)]), bark, lin(18, 36, 30, 43))   # a wooden gorget
    b.fill(E(24, 47, 2.4, 2.8), lime, sph(24, 47, 2.4, 2.8, 1.2, 0.1))                          # the seed pendant
    b.px([(23, 46)], (250, 255, 220)).vline(24, 43, 44, bark[0])
    m = face(b, sk)
    eye_pair(b, 21, (240, 236, 214), leaf[2], bark[0])
    b.hline(17, 21, 19, bark[0]).hline(26, 30, 19, bark[0])
    nose_mouth(b, sk)
    b.fill(all_of(m, P([(14, 24), (20, 25), (20, 26.2), (14, 25.6)])), lime, flat(0.5))        # a stripe of sap under the left eye
    # a short beard of moss
    b.fill(all_of(m, lambda x, y: y > 30.6 or (y > 24.5 and abs(x - 24) > 7.0 - (y - 24.5) * 0.62)), leaf, sph(CX, 26, 12, 9, 0.8, -0.05))
    b.px([(18, 29), (20, 31), (27, 31), (29, 29), (23, 32), (25, 32)], lime[2])
    # leaf hair: a crown of pointed leaves
    crown = any_of(all_of(E(24, 13, 12.4, 10), lambda x, y: y < 17.4 - 0.03 * (x - 24) ** 2 + (5.5 if abs(x - 24) > 8.6 else 0)),
                   *[P([(cx - 3, 10), (cx + lean, top), (cx + 3, 10)]) for cx, top, lean in ((14, 4.5, -2.5), (19, 2.5, -1.5), (24.5, 1.5, 0.5), (29.5, 2.5, 2), (34, 4.5, 3))])
    b.fill(crown, leaf, sph(22, 11, 15, 12, 1.0, 0.02))
    for cx, y, s_ in ((15, 9, -1), (20, 6, -1), (25, 5, 1), (30, 7, 1), (21, 11, -1), (27, 11, 1), (33, 11, 1), (16, 14, -1)):   # single leaves, lit
        b.px([(cx, y), (cx + s_, y), (cx + s_, y + 1), (cx + 2 * s_, y + 1)], lime[3]).px([(cx, y - 1), (cx, y)], lime[4]).px([(cx + s_, y + 2), (cx + 2 * s_, y + 2)], leaf[0])
    for cx, y, s_ in ((11, 12, -1), (11, 17, -1), (36, 12, 1), (36, 17, 1)):                    # leaves that stick out at the temples
        b.px([(cx, y), (cx + s_, y - 1), (cx, y + 1)], leaf[2] if s_ < 0 else leaf[1]).px([(cx, y)], lime[3] if s_ < 0 else leaf[2])
    b.dark(all_of(m, R(14, 16, 34, 18)), 0.3)
    # two branch antlers
    for s in (-1, 1):
        def pt(dx, y):
            return (round(CX - 0.5 + s * dx), y)
        stem = [pt(9, 9), pt(10, 8), pt(11, 7), pt(12, 6), pt(13, 5), pt(14, 4), pt(15, 3), pt(15, 2), pt(16, 1)]
        fork = [pt(12, 5), pt(12, 4), pt(12, 3), pt(11, 2), pt(14, 5), pt(15, 5), pt(16, 4), pt(17, 4)]
        b.px([(x, y + 1) for x, y in stem + fork] + [(x - s, y) for x, y in stem], bark[1] if s < 0 else bark[0])
        b.px(stem + fork, bark[3] if s < 0 else bark[2])
        b.px([pt(16, 0), pt(11, 1), pt(18, 3)], glow)
    return rgb(a["colors"]["trim"])


def rime(b, a):
    sk = ramp("ecd0c2", shadow=(90, 80, 150))
    hair = ramp("dcecfa", shadow=(70, 90, 170), light=(255, 255, 255), spread=0.85)
    coat, plate = ramp(a["colors"]["torso"], light=(220, 244, 255)), ramp(a["colors"]["arm"], light=(255, 255, 255))
    fur = ramp("eef6ff", shadow=(80, 100, 170), light=(255, 255, 255), spread=0.75)
    ice = ramp("7fc8e8", shadow=(40, 50, 140), light=(255, 255, 255))
    glow = rgb(a["colors"]["glow"])
    torso(b, coat)
    pauldron(b, plate, 10, 47, 7, 6)
    pauldron(b, plate, 38, 47, 7, 6)
    b.vline(24, 46, 63, coat[0]).vline(23, 46, 63, coat[3])
    for y in (51, 56, 61):                                   # frogged fastenings
        b.hline(20, 27, y, fur[2]).px([(20, y), (27, y)], fur[4])
    b.fill(P([(24, 45), (27, 48.5), (24, 52), (21, 48.5)]), ramp(glow), lin(22, 46, 26, 51, 0.99, 0.3))   # the brooch
    b.px([(23, 48)], (255, 255, 255))
    neck(b, sk)
    # the fur collar: a thick roll of tufts
    tufts = [(8, 42, 4.5), (13, 40, 4.6), (18.5, 41, 4.4), (24, 42.5, 4.4), (29.5, 41, 4.4), (35, 40, 4.6), (40, 42, 4.5),
             (11, 44.5, 3.6), (37, 44.5, 3.6), (16, 37.5, 3.4), (32, 37.5, 3.4), (21, 44.5, 3.0), (27, 44.5, 3.0)]
    for cx, cy, r in tufts:
        b.fill(E(cx, cy, r, r * 0.9), fur, sph(cx, cy, r, r * 0.9, 1.0, 0.1))
    b.dark(all_of(any_of(*[E(cx, cy + 3.6, r, 2.2) for cx, cy, r in tufts[:9]]), TORSO, lambda x, y: y > 44), 0.32)
    b.dark(P([(19, 36), (29, 36), (30, 40), (24, 42), (18, 40)]), 0.25)
    m = face(b, sk)
    nose_mouth(b, sk, mouth=(176, 104, 124))
    # the bob: a smooth cap, long on the left where it falls past the jaw, tucked on the right
    cap = any_of(all_of(E(24, 14, 12, 11), lambda x, y: y < 17.5 or x < 17.5 or x > 31.5),
                 P([(12, 14), (17.5, 14), (18, 22), (17, 30), (15, 35), (12, 34), (11.5, 24)]),
                 P([(31.5, 14), (36, 14), (36.5, 22), (35, 27), (33, 28), (33, 20)]))
    part = P([(18, 17.5), (21, 12), (27, 10.5), (31.5, 13), (31.5, 17.5), (28, 14.5), (23, 15)])
    b.fill(cap, hair, sph(22, 14, 15, 16, 1.0, 0.06))
    b.fill(all_of(m, part, lambda x, y: y > 15.5), hair, flat(0.3))                              # the fringe swept to the right
    b.dark(all_of(m, R(17, 17.5, 32, 19)), 0.3)
    for pts in (((14, 16), (13, 22), (13, 28), (14, 33)), ((16, 14), (16, 20), (15, 28)), ((34, 15), (35, 20), (34, 26)), ((17, 8), (21, 5), (27, 4.5))):
        for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
            n = max(abs(xb - xa), abs(yb - ya))
            b.px([(round(xa + (xb - xa) * i / n), round(ya + (yb - ya) * i / n)) for i in range(int(n) + 1)], hair[1])
    b.px([(19, 6), (20, 5), (21, 5), (22, 4), (15, 11), (15, 12)], (255, 255, 255))
    # the visor: one blade of ice across the eyes
    visor = P([(13, 19), (35, 19), (36, 21), (33, 24), (26, 23), (24, 24.5), (22, 23), (15, 24), (12, 21)])
    b.fill(visor, ice, lin(13, 18, 35, 25, 0.9, 0.25))
    b.hline(14, 34, 19, ice[4]).px([(15, 20), (16, 20), (17, 21), (18, 21), (27, 20), (28, 21)], (255, 255, 255))
    b.hline(15, 21, 23, glow).hline(27, 32, 23, glow)
    return rgb(a["colors"]["arm"])


def vesper(b, a):
    sk = ramp("a39dca", shadow=(50, 40, 110), light=(240, 240, 255))
    hood = ramp("544e84", shadow=(20, 14, 50), light=(200, 190, 255))
    cloak, silver = ramp(a["colors"]["arm"], shadow=(10, 8, 30), light=(170, 160, 230), spread=1.1), ramp("c0bcec", shadow=(60, 50, 140), light=(255, 255, 255), spread=0.9)
    mint = ramp(a["colors"]["trim"], shadow=(20, 80, 120), light=(235, 255, 250))
    torso(b, ramp(a["colors"]["torso"]))
    b.fill(all_of(TORSO, any_of(P([(5, 44), (17, 38), (22, 64), (5, 64)]), P([(43, 44), (31, 38), (26, 64), (43, 64)]))), cloak, body_light)
    for s in (-1, 1):                                        # the cloak's hem in mint, and its clasp chain
        pts = [(CX + s * (7 + i * 0.19 * (0 if i < 0 else 1)), 39 + i) for i in range(0, 25)]
        b.px([(int(CX + s * (6.2 + (i ** 0.9) * -0.16) - (0 if s > 0 else 1)), 39 + i) for i in range(25)], mint[2] if s < 0 else mint[1])
    b.fill(E(24, 46, 2.6, 2.6), mint, sph(24, 46, 2.6, 2.6, 1.2, 0.1)).px([(23, 45)], (255, 255, 255))
    b.hline(18, 21, 45, silver[1]).hline(27, 30, 45, silver[0])
    for y in (52, 56, 60):
        b.px([(24, y), (23, y + 1), (24, y + 1), (24, y + 2)], mint[1])
    neck(b, sk)
    # the cowl: a tall point, wide wings falling to the shoulders, the inside dark
    outer = P([(24, 0.5), (29, 3), (34, 8), (37.5, 16), (38, 26), (36, 33), (40, 40), (33, 42), (28, 37), (20, 37), (15, 42), (8, 40), (12, 33), (10, 26), (10.5, 16), (14, 8), (19, 3)])
    inner = any_of(face_mask(rx=10.6, top=12.4, bottom=14.5, jaw=0.3), R(18, 30, 30, 38))
    b.fill(but(outer, inner), hood, sph(22, 18, 17, 24, 1.05, 0.02))
    b.fill(all_of(outer, inner), cloak, flat(0.1))
    b.dark(but(outer, inner, lambda x, y: x < 24 + (y - 2) * 0.1), 0.18)
    for pts in (((23, 2), (19, 8), (15, 16), (13, 26)), ((27, 4), (31, 10), (34, 18), (35, 27)), ((13, 33), (11, 38)), ((35, 33), (37, 38))):   # folds
        for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
            n = max(abs(xb - xa), abs(yb - ya))
            b.px([(round(xa + (xb - xa) * i / n), round(ya + (yb - ya) * i / n)) for i in range(int(n) + 1)], hood[1])
    b.px([(21, 3), (20, 4), (19, 5), (18, 6), (17, 8), (16, 9)], hood[4])
    m = face_mask(cy=22.0, rx=8.0, top=9.5, bottom=11.0)
    b.fill(m, sk, sph(CX, 22, 10, 13, 1.1, -0.04))
    b.dark(all_of(m, lambda x, y: y < 16.5 + 0.03 * (x - 22) ** 2), 0.45)                    # the cowl's shadow on the brow
    # silver locks falling out of the hood on both sides
    for s in (-1, 1):
        lock = P([(CX + s * 7.6, 15), (CX + s * 9.6, 18), (CX + s * 9.4, 25), (CX + s * 10.8, 31), (CX + s * 10.0, 37), (CX + s * 8.2, 38), (CX + s * 9.0, 31), (CX + s * 8.0, 25), (CX + s * 8.0, 19)])
        b.fill(lock, silver, lin(14, 14, 34, 38, 0.78 if s < 0 else 0.5, 0.15))
    # eyes with no pupil, lit from inside, a tear line under each
    eye_pair(b, 22, None, mint[2], hood[0], glow=(240, 255, 250))
    b.px([(18, 24), (18, 25), (29, 24), (29, 25), (18, 27), (29, 27)], mint[1])
    b.px([(17, 22), (30, 22), (21, 23), (26, 23)], mix(sk[2], mint[2], 0.5))
    nose_mouth(b, sk, ny=25, my=30, mouth=(96, 84, 150))
    b.fill(P([(24, 10.5), (26, 13), (24, 15.5), (22, 13)]), mint, lin(22.5, 11, 25.5, 15, 0.99, 0.35))   # the diadem's stone
    b.px([(20, 13), (21, 13), (27, 13), (28, 13)], silver[2]).px([(23, 12)], (255, 255, 255))
    return rgb(a["colors"]["trim"])


def nacre(b, a):
    brass = ramp("c98442", shadow=(70, 24, 60), light=(255, 236, 170))
    suit, hose = ramp("1a587a", shadow=(10, 10, 50), light=(150, 230, 240)), ramp(a["colors"]["arm"], light=(190, 250, 250))
    glass = ramp("1e7486", shadow=(8, 20, 60), light=(170, 250, 250))
    pink = ramp(a["colors"]["glow"], shadow=(120, 20, 100), light=(255, 230, 240))
    torso(b, suit)
    for x in (9, 39):                                       # ribbed sleeves
        for y in range(48, 64, 3):
            b.hline(x - 4, x + 3, y, suit[1])
    # the corselet: a brass breastplate that carries the helmet
    plate = P([(13, 35), (35, 35), (41, 43), (38, 49), (30, 52), (18, 52), (10, 49), (7, 43)])
    b.fill(plate, brass, lin(8, 34, 40, 54))
    b.fill(but(P([(15, 38), (33, 38), (37, 44), (35, 47), (29, 49.5), (19, 49.5), (13, 47), (11, 44)]), ), brass, lin(4, 30, 44, 60, 0.75, 0.25))
    b.px([(11, 44), (15, 49), (24, 51), (33, 49), (37, 44), (18, 37), (30, 37)], brass[0]).px([(10, 43), (14, 48), (23, 50), (17, 36), (29, 36)], brass[4])
    b.fill(E(24, 44.5, 3.2, 3.2), pink, sph(24, 44.5, 3.2, 3.2, 1.15, 0.1)).px([(23, 43)], (255, 255, 255))   # the chest lamp
    b.fill(but(E(24, 44.5, 4.2, 4.2), E(24, 44.5, 3.2, 3.2)), brass, flat(0.2))
    b.dark(all_of(TORSO, P([(10, 49), (18, 52), (30, 52), (38, 49), (38, 51.5), (30, 54.5), (18, 54.5), (10, 51.5)])), 0.4)
    # the air hose from the back of the helmet to the chest
    for i in range(12):
        x, y = 37 + round(2.6 * math.sin(i / 11 * math.pi)), 31 + i * 2
        b.fill(E(x + 0.5, y + 0.5, 2.1, 1.6), hose, sph(x + 0.5, y + 0.5, 2.1, 1.6, 1.0, 0.05 if i % 2 else -0.15))
    # the helmet: a brass sphere, a bolted collar ring, a round faceplate, two side ports, a valve on top
    b.fill(R(21, 2, 27, 7), brass, cyl(24, 3.5))
    b.hline(20, 27, 2, brass[4]).hline(20, 27, 3, brass[1])
    b.fill(E(24, 20, 14, 14.5), brass, sph(24, 20, 14, 14.5, 1.05, 0.0))
    b.fill(P([(12, 32), (36, 32), (38, 37), (10, 37)]), brass, lin(10, 32, 38, 37, 0.85, 0.2))
    b.hline(11, 36, 32, brass[4])
    for x in (13, 18, 24, 30, 35):
        b.px([(x, 35)], brass[0]).px([(x, 34)], brass[4])
    b.dark(R(10, 37, 38, 38), 0.4)
    for cx, k in ((11.5, 1.0), (36.5, 0.7)):                # side ports
        b.fill(E(cx, 21, 2.6, 4.2), brass, flat(0.15)).fill(E(cx, 21, 1.6, 3.2), glass, lin(cx, 17, cx, 25, 0.8 * k, 0.2))
    ring = but(E(24, 21, 10.6, 10.6), E(24, 21, 8.4, 8.4))
    b.fill(ring, brass, lin(14, 11, 34, 31, 0.99, 0.3))
    b.dark(but(E(25, 22, 11.4, 11.4), E(24, 21, 10.6, 10.6)), 0.35)
    for ang in range(0, 360, 45):
        x, y = round(23.5 + 9.5 * math.cos(math.radians(ang + 22))), round(20.5 + 9.5 * math.sin(math.radians(ang + 22)))
        b.px([(x, y)], brass[0])
    pane = E(24, 21, 8.4, 8.4)
    b.fill(pane, glass, lin(17, 14, 31, 29, 0.72, 0.05))
    b.dark(all_of(pane, but(lambda x, y: True, E(25.2, 22.2, 8.4, 8.4))), 0.4)                  # the rim's shadow inside
    # the diver behind the glass: only two pale eyes and the line of a brow
    b.px([(19, 20), (20, 20), (21, 20), (27, 20), (28, 20), (29, 20)], (150, 222, 226))
    b.px([(19, 21), (20, 21), (21, 21), (27, 21), (28, 21), (29, 21)], (214, 250, 248)).px([(20, 21), (28, 21)], (255, 255, 255))
    b.px([(18, 19), (19, 18), (20, 18), (21, 18), (27, 18), (28, 18), (29, 18), (30, 19)], glass[0])
    for i in range(6):                                      # the glint across the glass
        b.px([(18 + i, 18 - i + 0), (18 + i, 17 - i + 0)], glass[4] if all_of(pane)(18.5 + i, 17.5 - i) else brass[4])
    b.px([(17, 20), (17, 21), (18, 19), (19, 15), (20, 14), (21, 14)], glass[4]).px([(19, 16), (18, 17)], (240, 255, 255))
    b.fill(E(24, 8.5, 2.2, 1.6), pink, sph(24, 8.5, 2.2, 1.6, 1.2, 0.15))                       # the helmet lamp
    return rgb(a["colors"]["glow"])


def sahrin(b, a):
    sk = ramp("a8744a", shadow=(60, 20, 60))
    cloth = ramp("ece0c4", shadow=(110, 70, 90), light=(255, 255, 244), spread=0.85)
    robe, gold = ramp(a["colors"]["torso"], shadow=(70, 20, 50)), ramp(a["colors"]["trim"], shadow=(140, 70, 30), light=(255, 252, 214))
    teal = ramp(a["colors"]["glow"], shadow=(10, 50, 90), light=(220, 255, 244))
    torso(b, robe)
    b.fill(all_of(TORSO, any_of(R(0, 0, 13, 64), R(36, 0, 48, 64))), cloth, body_light)         # cream sleeves
    for x in (8, 11, 38, 41):
        b.vline(x, 49, 63, cloth[1])
    for cx in (10.5, 37.5):                                  # gold shoulder caps
        b.fill(all_of(E(cx, 45, 7.5, 5.5), R(0, 0, 48, 47), TORSO), gold, sph(cx, 45, 7.5, 5.5, 1.05))
        b.hline(int(cx - 6), int(cx + 6), 47, gold[0])
    b.fill(P([(18, 52), (30, 52), (31, 57), (17, 57)]), teal, lin(17, 52, 31, 57, 0.85, 0.3))   # the sash
    b.hline(17, 31, 52, teal[4]).hline(17, 31, 57, gold[2])
    b.vline(24, 57, 63, gold[1]).vline(23, 57, 63, gold[3])
    neck(b, sk)
    m = face(b, sk)
    # eyes lined with kohl, a gold mark between the brows
    eye_pair(b, 21, (244, 240, 226), teal[2], INK)
    b.px([(16, 20), (17, 20), (30, 20), (31, 20), (21, 22), (26, 22)], INK)
    b.hline(17, 21, 18, mix(sk[0], INK, 0.5)).hline(26, 30, 18, mix(sk[0], INK, 0.5))
    b.px([(23, 17), (24, 17), (23, 18), (24, 18)], gold[3]).px([(23, 17)], gold[4])
    b.px([(25, 24), (25, 25)], sk[1])
    # the veil: from the bridge of the nose down over the chest, weighted with a row of coins
    b.fill(P([(18, 36), (30, 36), (33, 43), (24, 47), (15, 43)]), gold, lin(15, 36, 33, 47, 0.85, 0.2))   # a broad gold collar
    b.px([(18, 41), (21, 43), (24, 44), (27, 43), (30, 41)], teal[2]).px([(18, 40), (21, 42), (24, 43), (27, 42), (30, 40)], teal[4])
    silk = ramp("ece0c4", shadow=(40, 110, 120), light=(255, 255, 244), spread=0.8)                 # the veil: silk, its shadows teal
    veil = P([(14.5, 24.5), (33.5, 24.5), (35.5, 38), (12.5, 38)])
    b.fill(veil, silk, lin(12, 24, 36, 38, 0.95, 0.2))
    b.dark(all_of(veil, P([(21, 26), (24.5, 25), (27, 29.5), (25, 30.5)])), 0.12, (40, 110, 120))      # the nose under the cloth
    b.dark(all_of(veil, P([(29, 25), (34, 25), (36, 38), (31, 38)])), 0.16, (40, 110, 120))
    b.px([(15, 28), (15, 29), (14, 31), (14, 32), (14, 33), (32, 30), (32, 31), (33, 33), (33, 34)], silk[1])
    b.hline(15, 33, 25, gold[2]).hline(15, 33, 24, gold[3]).px([(15, 24), (16, 24)], gold[4])
    b.hline(13, 35, 37, gold[2]).hline(13, 35, 36, gold[3]).hline(13, 20, 36, gold[4])
    for x in range(13, 36, 3):                                                                    # coins hanging from the hem
        b.px([(x, 38), (x + 1, 38), (x, 39), (x + 1, 39)], gold[3] if x < 24 else gold[2]).px([(x, 38)], gold[4]).px([(x + 1, 39)], gold[1])
    # the turban: a broad wrap of crossing bands, a jewel set in gold at the front, one end hanging on the left
    b.fill(P([(11, 15), (15.5, 15), (16, 26), (14, 36), (10, 37), (9.5, 26)]), cloth, lin(9, 15, 17, 37, 0.8, 0.25))
    b.px([(12, 20), (12, 21), (12, 22), (11, 28), (11, 29), (12, 30), (13, 33)], cloth[1])
    wrap = any_of(E(24, 10.5, 14.4, 9.2), E(24, 13.5, 13, 5.5))
    b.fill(wrap, cloth, sph(22, 11, 17, 12, 1.0, 0.04))
    for k, (xa, ya, xb, yb) in enumerate(((10, 12, 24, 3), (12, 16, 30, 3), (17, 18, 36, 6), (24, 18, 38, 11), (38, 13, 24, 2), (36, 17, 16, 3))):
        n = int(max(abs(xb - xa), abs(yb - ya)))
        pts = [(round(xa + (xb - xa) * i / n), round(ya + (yb - ya) * i / n)) for i in range(n + 1)]
        b.px([p for p in pts if wrap(p[0] + 0.5, p[1] + 0.5) and (k < 4) == (p[0] < 24 + (0 if k < 4 else 2))], cloth[1] if k < 4 else cloth[0])
    b.dark(all_of(m, R(14, 17, 34, 18.2)), 0.35).dark(all_of(wrap, lambda x, y: y > 17.2), 0.25)
    b.fill(E(24, 12.5, 3.6, 4.0), gold, sph(24, 12.5, 3.6, 4.0, 1.1))
    b.fill(P([(24, 9.5), (26.6, 12.5), (24, 15.5), (21.4, 12.5)]), teal, lin(22, 10, 26, 15, 0.99, 0.3)).px([(23, 11)], (255, 255, 255))
    b.px([(24, 7), (23, 6), (24, 5), (24, 6), (25, 6)], gold[3])
    return rgb(a["colors"]["trim"])


def murk(b, a):
    hood = ramp("5c6a2a", shadow=(20, 30, 40), light=(226, 236, 150))
    sk, rubber = ramp("8a9a6a", shadow=(30, 50, 50)), ramp("3a3a2c", shadow=(14, 12, 30), light=(190, 200, 150), spread=1.1)
    suit = ramp(a["colors"]["torso"], shadow=(20, 30, 40), light=(226, 236, 150))
    lens = ramp("d060c0", shadow=(70, 10, 90), light=(255, 220, 250))
    vial = ramp(a["colors"]["glow"], shadow=(60, 90, 30), light=(250, 255, 220))
    torso(b, suit)
    b.fill(all_of(TORSO, P([(13, 38), (18, 38), (36, 64), (30, 64)])), rubber, body_light)       # a bandolier across the chest
    for i, (x, y) in enumerate(((17, 44), (21, 49), (25, 54), (29, 59))):                      # glowing vials in it
        b.fill(R(x - 1, y - 2, x + 2, y + 3), vial, lin(x - 1, y - 2, x + 2, y + 3, 0.99, 0.3)).hline(x - 1, x + 1, y - 3, rubber[3])
        b.px([(x - 1, y - 1)], (255, 255, 230))
    b.fill(all_of(TORSO, any_of(R(0, 55, 12, 57), R(37, 55, 48, 57))), rubber, flat(0.5))       # sleeve seals
    b.hline(5, 11, 55, rubber[3])
    neck(b, rubber)
    # the hood: round and heavy, its tip slumped to the left, a ragged hem on the shoulders
    outer = P([(15, 4), (12, 1.5), (17, 2.5), (24, 2), (31, 4), (36, 9), (38.5, 17), (38.5, 27), (36, 34), (39, 39), (35, 43), (32, 40), (29, 43), (26, 39),
               (22, 39), (19, 43), (16, 40), (13, 43), (9, 39), (12, 34), (9.5, 27), (9.5, 17), (11, 9)])
    inner = any_of(face_mask(rx=10.8, top=11.5, bottom=14.5, jaw=0.25), R(19, 30, 29, 39))
    b.fill(but(outer, inner), hood, sph(22, 18, 17, 23, 1.05, 0.02))
    b.fill(all_of(outer, inner), rubber, flat(0.1))
    for pts in (((14, 4), (12, 10), (11, 18)), ((30, 5), (34, 11), (36, 20)), ((12, 30), (11, 37)), ((36, 30), (37, 37)), ((17, 3), (22, 4))):
        for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
            n = max(abs(xb - xa), abs(yb - ya))
            b.px([(round(xa + (xb - xa) * i / n), round(ya + (yb - ya) * i / n)) for i in range(int(n) + 1)], hood[1])
    b.px([(15, 6), (14, 8), (13, 11), (18, 4), (19, 4)], hood[4])
    b.px([(20, 9), (21, 10), (20, 11), (28, 8), (29, 9)], hood[0])                             # stitches of a patch
    # a strip of sick skin over the mask
    m = face_mask(cy=22.0, rx=9.0, top=9.0, bottom=12.5, jaw=0.3)
    b.fill(all_of(m, lambda x, y: y < 18), sk, sph(CX, 22, 11, 13))
    b.dark(all_of(m, lambda x, y: y < 15.5), 0.45)
    # the gas mask: a rubber face, two big lit lenses, a filter can on the muzzle, a hose down to the chest
    b.fill(all_of(m, lambda x, y: y >= 17), rubber, sph(CX, 25, 11, 12, 1.0, 0.08))
    b.hline(15, 32, 17, rubber[4])
    for cx, k in ((19.5, 0.0), (28.5, -0.15)):
        b.fill(E(cx, 22, 4.2, 4.2), rubber, sph(cx, 22, 4.2, 4.2, 1.2, 0.2 + k))
        b.fill(E(cx, 22, 3.1, 3.1), lens, sph(cx, 22, 3.6, 3.6, 1.1, 0.12 + k))
        b.px([(int(cx - 1.5), 20), (int(cx - 2.5), 21)], (255, 240, 255))
    b.fill(E(24, 30.5, 4.6, 4.2), rubber, sph(24, 30.5, 4.6, 4.2, 1.25, 0.18))
    b.fill(E(24, 30.5, 3.0, 2.7), rubber, flat(0.1))
    b.px([(22, 30), (24, 30), (26, 30), (23, 32), (25, 32), (23, 29), (25, 29)], lens[2]).px([(22, 30)], lens[4])
    b.dark(but(E(25, 32, 5.2, 4.6), E(24, 30.5, 4.6, 4.2)), 0.35)
    for i in range(8):
        x, y = 28 + i, 33 + i * 2 - (i * i) // 8
        b.fill(E(x + 0.5, y + 0.5, 1.9, 1.6), rubber, sph(x + 0.5, y + 0.5, 1.9, 1.6, 1.2, 0.3 if i % 2 else 0.05))
    return rgb(a["colors"]["glow"])


def nobody(b):
    """Nobody picked yet: an unlit figure, only its shape."""
    tone = ramp("3a4052", shadow=(14, 14, 30), light=(150, 160, 190), spread=0.7)
    b.fill(TORSO, tone, lambda x, y: clamp(0.42 - (x - 24) / 60 - (y - 40) / 90))
    b.fill(R(20, 30, 28, 39), tone, flat(0.2))
    b.fill(face_mask(), tone, sph(CX, 21, 12, 14, 0.7, 0.0))
    b.fill(all_of(E(24, 13, 10.5, 8.5), lambda x, y: y < 15), tone, flat(0.3))
    return (0x7a, 0x84, 0xa0)


PAINTERS = {"cinder": cinder, "gale": gale, "thorn": thorn, "rime": rime, "vesper": vesper, "nacre": nacre, "sahrin": sahrin, "murk": murk}
_BUSTS = {}


def bust(agent):
    """48 x 64 RGBA rows of the agent's portrait (agent None: the unlit figure)."""
    key = agent["id"] if agent else None
    if key not in _BUSTS:
        b = Bust()
        rim = PAINTERS[key](b, agent) if agent else nobody(b)
        rows = b.finish(rim)
        assert all(px[3] == 0 for row in rows for px in row[44:]) and any(row[43][3] for row in rows), "the portrait spans columns ..43"
        _BUSTS[key] = rows
    return [list(r) for r in _BUSTS[key]]


# ------------------------------------------------------------------ the Blockbench skins (2026-11-24)
# The agents are real Minecraft skins painted in Blockbench (resource-pack/art/valoblock_skins_src/<id>.png and .bbmodel), and
# the portraits are 3D renders of them made there: portrait_<id>.png at 4x (192 x 256, drawn as a 48 x 64 glyph, so it is
# pixel for pixel at GUI scale 4, like the 4x menu tiles), portrait48_<id>.png rendered at 48 x 64 (the roster tile crop) and
# head_flat_<id>.png, the face with its hat layer at exactly 8x (the 8 x 8 top-bar head). The painted busts below remain the
# fallback of an agent without renders.
SRC = HERE / "valoblock_skins_src"
HD = 4


def _src(name):
    f = SRC / name
    if not f.exists():
        return None
    import valoblock_models as vm
    return [[tuple(px) for px in row] for row in vm.read_png(f.read_bytes())]


def bust_hd(agent):
    """The 4x portrait glyph: the render with nothing right of column 44 x 4 (the title text starts at a fixed advance) and
    something in the last four columns before it, so the glyph is exactly as wide as the 48 x 64 bust it replaces."""
    rows = _src(f"portrait_{agent['id'] if agent else 'none'}.png")
    if rows is None:
        return bust(agent)
    if (len(rows[0]), len(rows)) != (W * HD, H * HD):
        raise ValueError(f"portrait render must be {W * HD} x {H * HD}")
    edge = 44 * HD
    for row in rows:
        for x in range(edge, W * HD):
            row[x] = (0, 0, 0, 0)
    if not any(row[x][3] for row in rows for x in range(edge - HD, edge)):
        r, g, b, _ = rows[-1][edge - 1]
        rows[-1][edge - 1] = (r, g, b, 1)   # invisible, but the font counts it: the glyph keeps its 44 px advance
    return rows


def _bust48(agent):
    rows = _src(f"portrait48_{agent['id']}.png") if agent else None
    return rows if rows and (len(rows[0]), len(rows)) == (W, H) else bust(agent)


def _head8(agent):
    rows = _src(f"head_flat_{agent['id']}.png") if agent else None
    if not rows or len(rows) % 8 or len(rows[0]) != len(rows):
        return None
    k = len(rows) // 8
    return [[rows[y * k + k // 2][x * k + k // 2] for x in range(8)] for y in range(8)]


# ------------------------------------------------------------------ the roster tile (32 x 32 item)

TILE = 32
CROP = (8, 1)   # the top-left of the 32 x 32 window on the bust: the whole head and the collar


def tile(agent, alpha=1.0, ring=None):
    """The head of the bust on the agent's menu colour, with a floor of the signature colour. alpha < 1: taken or filtered out."""
    base = rgb(agent["menuColor"])
    rows = _bust48(agent)
    rim = signature(agent)
    out = []
    for y in range(TILE):
        row = []
        for x in range(TILE):
            t = round(y / (TILE - 1) * 4) / 4
            c = mix(mix(base, LIGHT, 0.10), mix(base, INK, 0.45), t)
            if y >= TILE - 2:
                c = mix(rim, INK, 0.25 if y == TILE - 2 else 0.5)
            if x == 0 or y == 0:
                c = mix(c, LIGHT, 0.14)
            elif x == TILE - 1:
                c = mix(c, INK, 0.3)
            px = rows[CROP[1] + y][CROP[0] + x]
            if px[3] and y < TILE - 2:
                c = px[:3]
            if ring and (min(x, y) < 2 or max(x, y) >= TILE - 2):
                c = ring if min(x, y) != 1 and max(x, y) != TILE - 2 else mix(ring, INK, 0.35)
            row.append(c + (round(255 * alpha),))
        out.append(row)
    return out


def signature(agent):
    b = Bust()
    return PAINTERS[agent["id"]](b, agent)


# ------------------------------------------------------------------ the 8 x 8 head (top bar, kill feed)

# The same people at 8 x 8: what identifies each is in the top rows and on the left (the Seed badge covers the lower right).
HEADS = {
    "cinder": ([".hoOOoh.", ".hhHhhh.", "hSSsssdh", "SSsssssd", "pEEppEEp", "MmVVVVmd", "MmmmmmMd", ".mVVVVd."],
               {"h": "2c1a34", "H": "4a3058", "O": "ffd36a", "o": "ff8a2a", "s": "c48a6a", "S": "e0aa88", "d": "94604c", "p": "241428", "E": "ffe08a",
                "m": "5a4a66", "M": "8a7a98", "V": "ff9a2e", "v": "c8581c"}),
    "gale": (["bBbbbyY.", "BbbbbbyY", "ggGggGgb", "bgLggLgb", "sssssssd", "swessews", "sssssssd", "WWWWWWww"],
             {"b": "4658b4", "B": "7a8ee0", "y": "ffe94a", "Y": "fff6a8", "g": "f0c63a", "G": "fff2a0", "L": "c8ecff", "s": "e0ae8c", "d": "b8866e", "w": "ffffff", "e": "3a4a8a", "W": "f0f4ff"}),
    "thorn": (["t.lLLl.t", "tlLlllLt", "lLllLlll", "lsssssdl", "swessewd", "sssssssd", "gssmmsdg", "gggggggg"],
              {"t": "8a5a34", "l": "5c7c30", "L": "c4e070", "s": "b88a62", "d": "946c4c", "w": "f1e7c9", "e": "3c5a1e", "m": "6e4630", "g": "4a6628"}),
    "rime": (["hHHHHHh.", "HHHHHHhh", "HHsssshh", "HVVVVVVv", "HvvIvvIv", "Hsssssdh", "hssmmsd.", "FFFFFFFf"],
             {"h": "c4d8f0", "H": "f4faff", "s": "ecd0c2", "d": "c8a8a4", "V": "aee4fa", "v": "5fb2dc", "I": "ffffff", "m": "b06c80", "F": "f4faff", "f": "b8c8e8"}),
    "vesper": (["...Hh...", "..Hhhd..", ".HhGGhd.", "Hhsssshd", "HlEssEld", "hlsssslD", "hhlmmlhD", "hhhhhhdD"],
               {"h": "544e84", "H": "8a84c0", "d": "3c3768", "D": "2a2650", "G": "6affd8", "l": "c0bcec", "s": "a39dca", "E": "6affd8", "m": "6a6498"}),
    "nacre": ([".bBPPbb.", "bBBbbbbd", "BbGGGGbd", "BGggggGd", "BGEggEGd", "bGggggGd", "bbGGGGdd", ".bbbddd."],
              {"b": "c98442", "B": "f0c078", "d": "8a4e30", "P": "ff6ab0", "G": "e8a860", "g": "1e7486", "E": "d6faf8"}),
    "sahrin": ([".CCCCcc.", "CCcGcdcd", "CcdTdccd", "dCCccddd", "sweSsews", "yYYyyyyg", "vVVvvvvd", ".vVvvvd."],
               {"c": "ece0c4", "C": "fffaec", "d": "c0ac8c", "G": "f2c14e", "T": "3fd0be", "s": "8a5c3a", "S": "a8744a", "w": "f4f0e2", "e": "2a9a8c",
                "y": "f2c14e", "Y": "fff0a8", "g": "b88a2e", "v": "dcd0b4", "V": "f4ecd8"}),
    "murk": (["hH......", "hHHhhhh.", "hHhhhhhd", "hrrrrrrd", "hPprrPpd", "hpprrppd", "hrrFFrrd", "hhrFfrdd"],
             {"h": "5c6a2a", "H": "8a9a3e", "d": "3a441c", "r": "3a3a2c", "P": "ffb0f0", "p": "d060c0", "F": "6a6a54", "f": "c9d36a"}),
}
HEAD = 10
FRAME = (0x06, 0x07, 0x0a)


def head(agent, state="alive"):
    """10 x 10 RGBA rows: the 8 x 8 face in a dark frame. state: alive, dead (grey, crossed out in red), none (nobody yet)."""
    img = [[FRAME + (255,)] * HEAD for _ in range(HEAD)]
    if state == "none" or agent is None:
        rows = ["........", "..aAAa..", ".aAaaab.", ".aaaaab.", ".aaaaab.", "..aaab..", ".aaaaab.", "aaaaaaab"]
        pal = {".": "1c1f29", "a": "4a5062", "A": "6a7186", "b": "343948"}
    else:
        rows, pal = HEADS[agent["id"]]
        pal = dict(pal)
        pal["."] = mix(rgb(agent["colors"]["bg"]), (0, 0, 0), 0.25)
    face = None if state == "none" or agent is None else _head8(agent)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            c = pal[ch] if isinstance(pal[ch], tuple) else rgb(pal[ch])
            if face is not None:
                c = face[y][x][:3] if face[y][x][3] else (pal["."] if isinstance(pal["."], tuple) else rgb(pal["."]))
            if state == "dead":
                v = (c[0] * 30 + c[1] * 59 + c[2] * 11) // 100
                c = mix(mix((v, v, v), c, 0.22), (20, 20, 28), 0.42)
            img[1 + y][1 + x] = c + (255,)
    if state == "dead":
        for i in range(1, 9):   # one red slash: the face stays recognisable under it
            img[i][9 - i] = (0xff, 0x4a, 0x4a, 255)
    return img


# ------------------------------------------------------------------ contact sheet

def agents():
    index = json.loads((AGENTS / "index.json").read_text())
    return [json.loads((AGENTS / f"{i}.json").read_text()) for i in index["agents"]]


def sheet(path, k=4):
    """Every bust (x k), and under each its roster tile and its head, alive and dead."""
    ags = agents() + [None]
    pad = 6
    cw = W * k + pad
    img = [[(32, 36, 48, 255)] * (cw * len(ags) + pad) for _ in range(H * k + TILE * 2 + HEAD * 6 + pad * 5)]

    def put(rows, ox, oy, s):
        for y, row in enumerate(rows):
            for x, px in enumerate(row):
                if px[3]:
                    for dy in range(s):
                        for dx in range(s):
                            img[oy + y * s + dy][ox + x * s + dx] = px[:3] + (255,)

    for i, a in enumerate(ags):
        ox = pad + i * cw
        put(bust(a), ox, pad, k)
        if a:
            put(tile(a), ox, pad * 2 + H * k, 2)
            put(tile(a, ring=(0xff, 0xe9, 0x4a)), ox + TILE * 2 + 4, pad * 2 + H * k, 2)
            put(tile(a), ox + TILE * 4 + 8, pad * 2 + H * k, 1)
        y = pad * 3 + H * k + TILE * 2
        put(head(a, "alive" if a else "none"), ox, y, 6)
        if a:
            put(head(a, "dead"), ox + HEAD * 6 + 4, y, 6)
            put(head(a, "alive"), ox + HEAD * 12 + 8, y, 2)
            put(head(a, "alive"), ox + HEAD * 14 + 12, y, 1)
    pathlib.Path(path).write_bytes(vi.png(img))


if __name__ == "__main__":
    if "--sheet" in sys.argv:
        i = sys.argv.index("--sheet")
        sheet(sys.argv[i + 1], int(sys.argv[i + 2]) if len(sys.argv) > i + 2 else 4)
    else:
        print(__doc__)
