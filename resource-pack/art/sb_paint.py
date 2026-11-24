#!/usr/bin/env python3
"""A small shape painter for the 32 px painted item icons (stdlib only, deterministic).

Used by minions_icons.py, skills_icons.py, economy_icons.py, skyblock_icon.py and menu_icons.py so that their sprites
stand next to the imported painted ones (item/menu, item/utility, the ore bars): 32x32, binary alpha, a near-black
outline, and per material a hue-shifted ramp of five tones lit from the top left (docs/art-references.md, rules 3-5).

    art = Art()
    art.fill(ell(15.5, 16, 10, 9), GOLD, ball(15.5, 16, 10, 9))   # a mask, a ramp, a light function
    art.tone(line(8, 20, 20, 8), +1)                              # one step up the ramp of whatever is there
    art.outline()
    art.png()

A mask is a set of (x, y) pixels: build them with rect, rrect, ell, poly, line and combine them with | & -.
A ramp is a list of colours from dark to light: ramp("c8a040") shifts the hue towards violet in the shadows and towards
yellow in the lights. A light function maps (x, y) to 0..1, quantised onto the ramp (no dithering, no noise).
"""
import colorsys
import math
import struct
import zlib

SIZE = 32
INK = (17, 11, 22)          # the outline: near black, a little violet, like the imported icons
GLINT = [(122, 52, 190), (190, 104, 255), (238, 190, 255)]   # the one enchant rim of every collection item
LIGHT = (-0.56, -0.62, 0.55)  # towards the light: top left, a little to the viewer


# ------------------------------------------------------------------ colours
def hx(value):
    if isinstance(value, str):
        value = value.lstrip("#")
        return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))
    return tuple(value[:3])


def _towards(h, target, amount):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return (h + d * amount) % 1.0


def ramp(base, n=5, spread=1.0, cool=0.72, warm=0.13):
    """n tones around `base`, dark to light. Shadows drift to `cool` (violet) and gain saturation, lights drift to
    `warm` (yellow) and lose it: a hand-painted ramp, not a plain darkening."""
    r, g, b = hx(base)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    out = []
    for i in range(n):
        t = (i - (n - 1) / 2) / max(1e-9, (n - 1) / 2) * spread
        if t < 0:
            k = -t
            hh = _towards(h, cool, 0.16 * k) if s > 0.04 else cool
            ss = min(1.0, s + (0.20 if s > 0.04 else 0.10) * k)
            vv = v * (1 - 0.58 * k)
        else:
            hh = _towards(h, warm, 0.14 * t) if s > 0.04 else warm
            ss = s * (1 - 0.50 * t) + (0.04 * t if s <= 0.04 else 0)
            vv = v + (1 - v) * 0.80 * t
        rr, gg, bb = colorsys.hsv_to_rgb(hh, max(0.0, min(1.0, ss)), max(0.0, min(1.0, vv)))
        out.append((round(rr * 255), round(gg * 255), round(bb * 255)))
    return out


def tones(*values):
    """A ramp given tone by tone (dark to light)."""
    return [hx(v) for v in values]


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def grey(colour, k=0.62):
    v = round((colour[0] * 3 + colour[1] * 6 + colour[2]) / 10 * k)
    return (v, v, min(255, v + 6))


# shared materials
GOLD = tones("7a3f14", "b86a18", "e8a322", "ffd24a", "fff3a8")
WOOD = tones("3e2218", "6a3c22", "96592e", "bd7f42", "e0ab68")
DARKWOOD = tones("26161a", "442622", "64392a", "87533a", "ab7550")
STONE = tones("38364a", "5a586a", "838190", "a9a8b0", "d2d2d4")
IRON = tones("3a4058", "66708a", "98a3b8", "c9d2de", "f4f8fb")
STEEL = tones("2a2e42", "4a526a", "717c94", "a0acc0", "d8e0ea")
PAPER = tones("7a5a48", "b89878", "e2cca0", "f6e8c4", "fffaea")
LEAF = tones("143a2e", "1f6a34", "3f9a38", "74c84a", "b8ec78")
RED = tones("4a1030", "8a1a2c", "c8302c", "f0603c", "ffa878")
BLUE = tones("1a1c58", "24408e", "3470c8", "5aa8ec", "a8e0ff")
SKIN = tones("7a4038", "b06a4c", "dc9c70", "f4c898", "ffe8c8")
CLOTH = tones("2a1e48", "46367a", "6a58b0", "9a8ade", "cdc4ff")
LEATHER = tones("3a1c1c", "66321e", "934e26", "bd7238", "dc9c5c")
WHITE = tones("5a5a80", "9498b8", "c8cce0", "eceef6", "ffffff")
BLACK = tones("16121e", "262232", "3a3648", "565266", "7c7890")
GLASS = tones("2a4a78", "4a86b8", "86c6e6", "c4ecfa", "ffffff")


# ------------------------------------------------------------------ masks
def rect(x0, y0, x1, y1):
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def rrect(x0, y0, x1, y1, r=1):
    """Rectangle with its corners cut by a quarter circle of radius r."""
    out = set()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dx = max(x0 + r - x, x - (x1 - r), 0)
            dy = max(y0 + r - y, y - (y1 - r), 0)
            if dx * dx + dy * dy <= r * r + 0.5:
                out.add((x, y))
    return out


def ell(cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    out = set()
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                out.add((x, y))
    return out


def ring(cx, cy, rx, ry=None, w=1.0):
    ry = rx if ry is None else ry
    return ell(cx, cy, rx, ry) - ell(cx, cy, rx - w, ry - w)


def poly(*pts):
    """Filled polygon, edges included, tested at the pixel centres (integer coordinates)."""
    if len(pts) == 1:
        pts = pts[0]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    out = set()
    n = len(pts)
    for y in range(math.floor(min(ys)), math.ceil(max(ys)) + 1):
        for x in range(math.floor(min(xs)), math.ceil(max(xs)) + 1):
            inside = False
            on_edge = False
            for i in range(n):
                x0, y0 = pts[i]
                x1, y1 = pts[(i + 1) % n]
                cross = (x1 - x0) * (y - y0) - (y1 - y0) * (x - x0)
                if abs(cross) < 1e-6 and min(x0, x1) - 1e-6 <= x <= max(x0, x1) + 1e-6 \
                        and min(y0, y1) - 1e-6 <= y <= max(y0, y1) + 1e-6:
                    on_edge = True
                    break
                if (y0 > y) != (y1 > y) and x < (x1 - x0) * (y - y0) / (y1 - y0) + x0:
                    inside = not inside
            if inside or on_edge:
                out.add((x, y))
    return out


def line(x0, y0, x1, y1, w=1.0):
    """A stroke of width w with round ends."""
    out = set()
    r = w / 2 + 1e-6
    dx, dy = x1 - x0, y1 - y0
    length2 = dx * dx + dy * dy
    pad = int(r) + 1
    for y in range(math.floor(min(y0, y1)) - pad, math.ceil(max(y0, y1)) + pad + 1):
        for x in range(math.floor(min(x0, x1)) - pad, math.ceil(max(x0, x1)) + pad + 1):
            t = 0 if length2 == 0 else max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / length2))
            px, py = x0 + t * dx, y0 + t * dy
            if (x - px) ** 2 + (y - py) ** 2 <= r * r:
                out.add((x, y))
    return out


def path(pts, w=1.0):
    out = set()
    for a, b in zip(pts, pts[1:]):
        out |= line(a[0], a[1], b[0], b[1], w)
    return out


def pts(*xy):
    """pts(1, 2, 3, 4) -> {(1, 2), (3, 4)}"""
    return {(xy[i], xy[i + 1]) for i in range(0, len(xy), 2)}


def rows(x, y, *spans):
    """Hand-placed rows starting at line y: each span is (x0, x1) relative to x, or None for an empty row."""
    out = set()
    for i, span in enumerate(spans):
        if span is None:
            continue
        out |= {(x + xx, y + i) for xx in range(span[0], span[1] + 1)}
    return out


def shift(mask, dx, dy):
    return {(x + dx, y + dy) for x, y in mask}


def flip(mask, axis=15.5):
    """Mirror left-right around x = axis."""
    return {(round(2 * axis - x), y) for x, y in mask}


def grow(mask, diagonal=False):
    out = set(mask)
    for x, y in mask:
        out.update(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
        if diagonal:
            out.update(((x + 1, y + 1), (x - 1, y - 1), (x - 1, y + 1), (x + 1, y - 1)))
    return out


def shrink(mask):
    return {(x, y) for x, y in mask if (x + 1, y) in mask and (x - 1, y) in mask and (x, y + 1) in mask and (x, y - 1) in mask}


def edge(mask, *dirs):
    """Pixels of the mask with nothing of the mask in one of the directions ('u', 'd', 'l', 'r')."""
    step = {"u": (0, -1), "d": (0, 1), "l": (-1, 0), "r": (1, 0)}
    return {(x, y) for x, y in mask if any((x + step[d][0], y + step[d][1]) not in mask for d in dirs)}


def bbox(mask):
    xs = [p[0] for p in mask]
    ys = [p[1] for p in mask]
    return min(xs), min(ys), max(xs), max(ys)


# ------------------------------------------------------------------ light functions: (x, y) -> 0..1
def ball(cx, cy, rx, ry=None, ambient=0.16, bounce=0.14):
    """A lit sphere (or ellipsoid): highlight top left, core shadow bottom right, a little bounce light on the rim."""
    ry = rx if ry is None else ry

    def f(x, y):
        nx, ny = (x - cx) / rx, (y - cy) / ry
        d2 = nx * nx + ny * ny
        nz = math.sqrt(max(0.0, 1 - d2))
        dot = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        value = ambient + (1 - ambient) * max(0.0, dot)
        if dot < 0 and d2 > 0.72:
            value += bounce
        return value
    return f


def grad(x0, y0, x1, y1, a=0.85, b=0.2):
    """Linear light from a at (x0, y0) to b at (x1, y1)."""
    dx, dy = x1 - x0, y1 - y0
    length2 = dx * dx + dy * dy or 1

    def f(x, y):
        t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / length2))
        return a + (b - a) * t
    return f


def vcyl(x0, x1, a=0.92, b=0.12, peak=0.3):
    """An upright cylinder between x0 and x1: brightest at `peak` of the width, darkest at the right."""
    def f(x, y):
        t = (x - x0) / max(1, x1 - x0)
        if t < peak:
            return a - (peak - t) / peak * 0.3
        return a + (b - a) * ((t - peak) / (1 - peak)) ** 0.9
    return f


def hcyl(y0, y1, a=0.92, b=0.12, peak=0.28):
    """A lying cylinder between y0 and y1."""
    inner = vcyl(y0, y1, a, b, peak)
    return lambda x, y: inner(y, x)


def flat(value):
    return lambda x, y: value


def auto(mask, hi=0.78, lo=0.26):
    """Light falling across the bounding box of the mask from the top left to the bottom right."""
    x0, y0, x1, y1 = bbox(mask)
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    return lambda x, y: hi + (lo - hi) * (0.45 * (x - x0) / w + 0.55 * (y - y0) / h)


# ------------------------------------------------------------------ the canvas
class Art:
    def __init__(self, size=SIZE):
        self.size = size
        self.px = {}   # (x, y) -> (ramp, index)

    # painting
    def fill(self, mask, tones_, light=None, rim=None, ink=True):
        """Paints the mask with the ramp. light: a light function, a number, or None (auto + rim).
        rim: how much the top-left edge is lifted and the bottom-right edge is dropped (a bevel, not pillow shading).
        ink: draws a dark line of the material where the part covers something already painted."""
        mask = {p for p in mask if 0 <= p[0] < self.size and 0 <= p[1] < self.size}
        if not mask:
            return self
        tones_ = tuple(tones_)
        if rim is None:
            rim = 0.2 if light is None else 0.0
        if light is None:
            light = auto(mask)
        elif not callable(light):
            light = flat(light)
        if ink:
            dark = mix(tones_[0], INK, 0.55)
            for p in grow(mask) - mask:
                if p in self.px:
                    self.px[p] = ((dark,), 0)
        n = len(tones_)
        for x, y in mask:
            value = light(x, y)
            if rim:
                lit = (x, y - 1) not in mask or (x - 1, y) not in mask
                dim = (x, y + 1) not in mask or (x + 1, y) not in mask
                if lit and not dim:
                    value += rim
                elif dim and not lit:
                    value -= rim
            self.px[(x, y)] = (tones_, max(0, min(n - 1, int(value * n))))
        return self

    def put(self, mask, colour):
        """Plain pixels of one colour (eyes, sparks)."""
        colour = hx(colour)
        for p in mask:
            if 0 <= p[0] < self.size and 0 <= p[1] < self.size:
                self.px[p] = ((colour,), 0)
        return self

    def tone(self, mask, delta, only=None):
        """Moves what is already painted under the mask by `delta` steps along its own ramp."""
        for p in mask:
            if p in self.px:
                tones_, i = self.px[p]
                if only is not None and tones_ is not only and tones_ != tuple(only):
                    continue
                self.px[p] = (tones_, max(0, min(len(tones_) - 1, i + delta)))
        return self

    def set_tone(self, mask, index):
        for p in mask:
            if p in self.px:
                tones_, _ = self.px[p]
                self.px[p] = (tones_, max(0, min(len(tones_) - 1, index)))
        return self

    def erase(self, mask):
        for p in mask:
            self.px.pop(p, None)
        return self

    def mask(self):
        return set(self.px)

    def stamp(self, other, dx=0, dy=0):
        for (x, y), v in other.px.items():
            if 0 <= x + dx < self.size and 0 <= y + dy < self.size:
                self.px[(x + dx, y + dy)] = v
        return self

    # finishing
    def outline(self, colour=INK):
        body = set(self.px)
        for p in grow(body) - body:
            if 0 <= p[0] < self.size and 0 <= p[1] < self.size:
                self.px[p] = ((hx(colour),), 0)
        return self

    def glint(self, tier=1, sparks=None):
        """The enchant rim: one violet line outside the outline, lighter at the top left, and corner sparks
        (violet; a compacted item, tier 2, gets gold ones as well). Call after outline()."""
        body = set(self.px)
        x0, y0, x1, y1 = bbox(body)
        w, h = max(1, x1 - x0), max(1, y1 - y0)
        for x, y in grow(body) - body:
            if 0 <= x < self.size and 0 <= y < self.size:
                t = 0.5 * (x - x0) / w + 0.5 * (y - y0) / h
                self.px[(x, y)] = ((GLINT[2] if t < 0.3 else GLINT[1] if t < 0.72 else GLINT[0]),), 0
        if sparks is None:
            n = self.size
            rimmed = set(self.px)
            corners = [(n - 4, 3), (3, n - 4), (3, 3), (n - 4, n - 4)]
            sparks = [c for c in corners
                      if not any((c[0] + dx, c[1] + dy) in rimmed for dx in range(-2, 3) for dy in range(-2, 3))]
            sparks = sparks if tier >= 2 else sparks[:2]
        gold = ((255, 214, 90), (255, 250, 214))
        for i, (sx, sy) in enumerate(sparks):
            self.spark(sx, sy, 1, gold if tier >= 2 and i < 2 else None)
        return self

    def spark(self, x, y, r=1, colours=None):
        """A four-pointed sparkle: white centre, coloured arms of length r."""
        arm, core = colours or (GLINT[2], (255, 255, 255))
        cells = {}
        for i in range(1, r + 1):
            for p in ((x + i, y), (x - i, y), (x, y + i), (x, y - i)):
                cells[p] = arm
        cells[(x, y)] = core
        for p, colour in cells.items():
            if 0 <= p[0] < self.size and 0 <= p[1] < self.size:
                self.px[p] = ((colour,), 0)
        return self

    # output
    def grid(self):
        out = [[None] * self.size for _ in range(self.size)]
        for (x, y), (tones_, i) in self.px.items():
            out[y][x] = tones_[i]
        return out

    def png(self):
        return png_bytes(self.grid())

    def greyed(self, k=0.62):
        other = Art(self.size)
        for p, (tones_, i) in self.px.items():
            colour = tones_[i]
            other.px[p] = ((colour if colour == INK else grey(colour, k),), 0)
        return other


def png_bytes(grid):
    """grid: rows of (r, g, b) or None -> RGBA PNG bytes."""
    h, w = len(grid), len(grid[0])
    raw = bytearray()
    for row in grid:
        raw.append(0)
        for c in row:
            raw.extend((c[0], c[1], c[2], 255) if c else (0, 0, 0, 0))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def sheet(grids, cols=10, scale=6, pad=2, background=(58, 58, 70)):
    """A contact sheet of the grids (nearest neighbour), for reviewing: returns PNG bytes."""
    cell = max(max(len(g) for g in grids), SIZE) + 2 * pad
    count = len(grids)
    lines = (count + cols - 1) // cols
    out = [[background] * (cols * cell * scale) for _ in range(lines * cell * scale)]
    for index, g in enumerate(grids):
        ox, oy = (index % cols) * cell + pad, (index // cols) * cell + pad
        k = 2 if len(g) <= SIZE // 2 else 1      # 16 px sprites are shown at the same size
        off = (cell - 2 * pad - len(g) * k) // 2
        for y, row in enumerate(g):
            for x, c in enumerate(row):
                if c is None:
                    continue
                for yy in range((oy + off + y * k) * scale, (oy + off + (y + 1) * k) * scale):
                    line_ = out[yy]
                    for xx in range((ox + off + x * k) * scale, (ox + off + (x + 1) * k) * scale):
                        line_[xx] = c[:3]
    return png_bytes(out)


def read_png(path):
    """Minimal PNG reader (8 bit, RGBA / RGB / palette) for the contact sheets: rows of (r, g, b) or None."""
    data = open(path, "rb").read()
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        length, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            w, h, depth, kind = struct.unpack(">IIBB", body[:10])
        elif tag == b"IDAT":
            idat += body
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[kind]
    bpp = max(1, channels * depth // 8)
    stride = (w * channels * depth + 7) // 8
    raw = zlib.decompress(idat)
    out, prev, q = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[q]
        cur = bytearray(raw[q + 1:q + 1 + stride])
        q += 1 + stride
        for i in range(stride):
            a = cur[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                cur[i] = (cur[i] + a) & 255
            elif f == 2:
                cur[i] = (cur[i] + b) & 255
            elif f == 3:
                cur[i] = (cur[i] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                cur[i] = (cur[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = cur
        row = []
        for x in range(w):
            if kind == 6:
                r, g, b, al = cur[x * 4:x * 4 + 4]
            elif kind == 2:
                r, g, b = cur[x * 3:x * 3 + 3]
                al = 255
            elif kind == 3:
                i = cur[x] if depth == 8 else (cur[x * depth // 8] >> (8 - depth - (x * depth) % 8)) & ((1 << depth) - 1)
                r, g, b = palette[i * 3:i * 3 + 3]
                al = trns[i] if trns and i < len(trns) else 255
            elif kind == 4:
                r = g = b = cur[x * 2]
                al = cur[x * 2 + 1]
            else:
                r = g = b = cur[x]
                al = 255
            row.append((r, g, b) if al >= 128 else None)
        out.append(row)
    return out
