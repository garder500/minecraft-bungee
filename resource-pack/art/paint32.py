"""A 32x32 shape painter for the painted Skyblock item icons (stdlib only, deterministic, no noise).

A sprite is a stack of parts. A part is a shape (a function of x, y tested at pixel centres), a material ramp of five
hand-picked tones (deep shadow, shadow, base, light, highlight, hue-shifted) and a form that gives it a height field:
  plate  a flat top with a chamfer of `bevel` pixels      round  the same with a rounded shoulder (bevel = radius: a dome)
  flat   no relief                                        hf=    any height function of (x, y)
The tone of a pixel is the height field lit from the top left plus a gentle top-left to bottom-right gradient over the
part, so a face is never shaded by its borders alone (no pillow shading). A part drops a one pixel shadow on what lies
under it to the right and below. Details are placed by hand afterwards (put, line, shift, paint, gem). The outline is the
deep shadow of the material it touches, pushed towards the ink of the other sprites: dark and coloured.
armor32.py, trinkets32.py, creatures32.py and goods32.py draw with it.
"""
import math
import struct
import zlib

N = 32
INK = (0x14, 0x0F, 0x18)
LIGHT = (-0.47, -0.60, 0.64)   # unit vector to the light: top left, towards the viewer


def hx(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def R(*hexes):
    """A material ramp, dark to light: five tones."""
    assert len(hexes) == 5, hexes
    return tuple(hx(h) for h in hexes)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


# ------------------------------------------------------------------------------------------------ shapes
def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1


def rect(x0, y0, x1, y1):
    """The pixels x0..x1, y0..y1 (inclusive)."""
    return lambda x, y: x0 <= x < x1 + 1 and y0 <= y < y1 + 1


def poly(points):
    pts = list(points)

    def inside(x, y):
        hit = False
        j = len(pts) - 1
        for i in range(len(pts)):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit
    return inside


def sym(points, axis=16):
    """A polygon mirrored on the vertical axis: give the left half from top to bottom."""
    pts = list(points)
    return poly(pts + [(2 * axis - x, y) for x, y in reversed(pts)])


def seg(x0, y0, x1, y1, t):
    """A thick segment: every point within t of it."""
    dx, dy = x1 - x0, y1 - y0
    ln = dx * dx + dy * dy or 1.0

    def inside(x, y):
        u = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / ln))
        return (x - (x0 + u * dx)) ** 2 + (y - (y0 + u * dy)) ** 2 <= t * t
    return inside


def path(points, t):
    """A thick polyline."""
    return union(*[seg(a[0], a[1], b[0], b[1], t) for a, b in zip(points, points[1:])])


def taper(x0, y0, r0, x1, y1, r1):
    """A segment whose thickness goes from r0 to r1 (a horn, a tail, a claw)."""
    dx, dy = x1 - x0, y1 - y0
    ln = dx * dx + dy * dy or 1.0

    def inside(x, y):
        u = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / ln))
        r = r0 + (r1 - r0) * u
        return (x - (x0 + u * dx)) ** 2 + (y - (y0 + u * dy)) ** 2 <= r * r
    return inside


def union(*shapes):
    return lambda x, y: any(s(x, y) for s in shapes)


def inter(*shapes):
    return lambda x, y: all(s(x, y) for s in shapes)


def sub(a, *cut):
    return lambda x, y: a(x, y) and not any(s(x, y) for s in cut)


def mirror(shape, axis=16):
    return lambda x, y: shape(2 * axis - x, y)


def both(shape, axis=16):
    return union(shape, mirror(shape, axis))


def move(shape, dx, dy):
    return lambda x, y: shape(x - dx, y - dy)


def ring(cx, cy, r_out, r_in, sy=1.0):
    """A band between two circles; sy squashes it into an ellipse seen at an angle."""
    return lambda x, y: r_in * r_in < (x - cx) ** 2 + ((y - cy) / sy) ** 2 <= r_out * r_out


def star(cx, cy, r_out, r_in, n=4, rot=0.0):
    pts = []
    for i in range(n * 2):
        a = rot + math.pi * i / n - math.pi / 2
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return poly(pts)


# ------------------------------------------------------------------------------------------------ the canvas
def _tone(i):
    return 0 if i < 0.24 else 1 if i < 0.53 else 2 if i < 0.76 else 3 if i < 0.95 else 4


class Pic:
    def __init__(self):
        self.px = {}       # (x, y) -> [ramp, tone index]
        self.extra = {}    # sparks drawn outside the silhouette, after the outline

    # -- parts
    def mask(self, shape):
        return {(x, y) for y in range(N) for x in range(N) if shape(x + 0.5, y + 0.5)}

    def part(self, shape, ramp, form="plate", bevel=2.0, grad=0.30, seam="shadow", hf=None, lift=0, only=None):
        """Paint one part. seam: "shadow" (one pixel under it, right and below), "line" (a dark line all around), None.
        lift shifts every tone of the part; only=shape keeps the part inside what is already painted there."""
        mask = self.mask(shape)
        if only is not None:
            keep = self.mask(only)
            mask = {p for p in mask if p in keep and p in self.px}
        if not mask:
            return mask
        if seam == "shadow":
            for (x, y) in sorted(mask):
                for q in ((x + 1, y), (x, y + 1)):
                    if q not in mask and q in self.px:
                        self.px[q] = [self.px[q][0], max(0, min(self.px[q][1], 2) - 1)]
        elif seam == "line":
            for (x, y) in sorted(mask):
                for q in ((x + 1, y), (x, y + 1), (x - 1, y), (x, y - 1)):
                    if q not in mask and q in self.px:
                        self.px[q] = [self.px[q][0], 0]
        if hf is None:
            dist = self._dist(mask)
            if form == "plate":
                h = {p: min(d, bevel) for p, d in dist.items()}
            elif form == "round":
                h = {p: bevel * math.sqrt(1 - (1 - min(d, bevel) / bevel) ** 2) for p, d in dist.items()}
            else:
                h = {p: 0.0 for p in mask}
        else:
            h = {(x, y): hf(x + 0.5, y + 0.5) for (x, y) in mask}
        xs = [p[0] for p in mask]
        ys = [p[1] for p in mask]
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        span = max(max(xs) - min(xs), max(ys) - min(ys), 4)
        for (x, y) in mask:
            here = h[(x, y)]
            if hf is None:
                gx = (h.get((x + 1, y), 0.0) - h.get((x - 1, y), 0.0)) / 2
                gy = (h.get((x, y + 1), 0.0) - h.get((x, y - 1), 0.0)) / 2
            else:   # an analytic field: one-sided at the rim so the edge is not a cliff
                gx = (h.get((x + 1, y), here) - h.get((x - 1, y), here)) / (2 if (x + 1, y) in h and (x - 1, y) in h else 1)
                gy = (h.get((x, y + 1), here) - h.get((x, y - 1), here)) / (2 if (x, y + 1) in h and (x, y - 1) in h else 1)
            ln = math.sqrt(gx * gx + gy * gy + 1)
            i = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / ln
            i += grad * ((cx - x) + (cy - y)) / span
            self.px[(x, y)] = [ramp, max(0, min(4, _tone(i) + lift))]
        return mask

    @staticmethod
    def _dist(mask):
        """Chamfer distance to the outside, 1 on the edge pixels."""
        big = 99.0
        d = {p: big for p in mask}
        for y in range(N):
            for x in range(N):
                if (x, y) in d:
                    d[(x, y)] = min(d.get((x - 1, y), 0.0) + 1, d.get((x, y - 1), 0.0) + 1,
                                    d.get((x - 1, y - 1), 0.0) + 1.41, d.get((x + 1, y - 1), 0.0) + 1.41)
        for y in range(N - 1, -1, -1):
            for x in range(N - 1, -1, -1):
                if (x, y) in d:
                    d[(x, y)] = min(d[(x, y)], d.get((x + 1, y), 0.0) + 1, d.get((x, y + 1), 0.0) + 1,
                                    d.get((x + 1, y + 1), 0.0) + 1.41, d.get((x - 1, y + 1), 0.0) + 1.41)
        return d

    # -- details
    def put(self, x, y, ramp, tone=2):
        if 0 <= x < N and 0 <= y < N:
            self.px[(x, y)] = [ramp, tone]

    def dots(self, points, ramp, tone=2):
        for x, y in points:
            self.put(x, y, ramp, tone)

    def line(self, x0, y0, x1, y1, ramp, tone=2, only=False):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            p = (round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n))
            if not only or p in self.px:
                self.put(p[0], p[1], ramp, tone)

    def fill(self, shape, ramp, tone=2, only=False):
        """Flat pixels of one tone (a glow core, a hole, an eye)."""
        for p in self.mask(shape):
            if not only or p in self.px:
                self.px[p] = [ramp, tone]

    def shift(self, shape, delta):
        """Lighten or darken what is painted inside the shape (a fold, a seam, a stripe)."""
        for p in self.mask(shape):
            if p in self.px:
                self.px[p] = [self.px[p][0], max(0, min(4, self.px[p][1] + delta))]

    def paint(self, shape, ramp, delta=0):
        """Change the material inside the shape and keep the shading (a trim, a band, a stripe of plaid)."""
        for p in self.mask(shape):
            if p in self.px:
                self.px[p] = [ramp, max(0, min(4, self.px[p][1] + delta))]

    def erase(self, shape):
        for p in self.mask(shape):
            self.px.pop(p, None)

    def has(self, x, y):
        return (x, y) in self.px

    def gem(self, cx, cy, r, ramp, frame=None, fw=1.0):
        """A cabochon with a top-left glint; frame = the ramp of its setting."""
        if frame is not None:
            self.part(circle(cx, cy, r + fw), frame, bevel=1.0, seam="shadow")
        self.part(circle(cx, cy, r), ramp, form="round", bevel=max(1.0, r), seam=None, grad=0.5)
        self.put(int(cx - r * 0.45), int(cy - r * 0.45), ramp, 4)
        if r >= 2:
            self.put(int(cx + r * 0.4), int(cy + r * 0.4), ramp, 0)

    def facet(self, points, ramp, frame=None):
        """A cut gem from a polygon: light upper-left facet, dark lower-right, a glint."""
        shape = poly(points)
        if frame is not None:
            grown = union(*[move(shape, dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))])
            self.part(grown, frame, bevel=1.0)
        m = self.part(shape, ramp, form="plate", bevel=1.5, seam=None, grad=0.6)
        if m:
            x, y = min(m, key=lambda p: p[0] + p[1] * 1.01)
            self.put(x, y, ramp, 4)

    def spark(self, x, y, ramp, big=False):
        """A sparkle outside the silhouette (a glow that stays opaque pixels): drawn after the outline, on free pixels."""
        self.extra[(x, y)] = ramp[4]
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            self.extra.setdefault((x + dx, y + dy), ramp[3] if big else ramp[2])
        if big:
            for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                self.extra.setdefault((x + dx, y + dy), ramp[2])

    # -- output
    def rows(self):
        """Rows of RGBA tuples or None: the fill, the coloured outline, then the sparks on free pixels."""
        out = [[None] * N for _ in range(N)]
        for (x, y), (ramp, tone) in self.px.items():
            out[y][x] = ramp[tone] + (255,)
        for y in range(N):
            for x in range(N):
                if (x, y) in self.px:
                    continue
                near = [self.px[q][0][0] for q in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)) if q in self.px]
                if near:
                    out[y][x] = mix(min(near, key=sum), INK, 0.62) + (255,)
        for (x, y), color in self.extra.items():
            if 0 <= x < N and 0 <= y < N and out[y][x] is None:
                out[y][x] = color + (255,)
        return out

    @property
    def p(self):
        return self.rows()

    def png(self):
        return png(self.rows())


def png(rows):
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for px in row:
            raw.extend(px if px else (0, 0, 0, 0))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# ------------------------------------------------------------------------------------------------ shared materials
GOLD = R("#6a3a0c", "#b0701a", "#e6a82e", "#ffd75e", "#fff4b8")
BRONZE = R("#3c2110", "#74431e", "#a86a30", "#d39650", "#f4c88a")
COPPER = R("#43180e", "#84381e", "#bd6034", "#e4905a", "#ffc79a")
SILVER = R("#2a3044", "#56627c", "#8c9ab2", "#c4cfe0", "#f4f8ff")
IRON = R("#22242e", "#474c5c", "#737a8c", "#a4acbc", "#d8dee8")
DARKSTEEL = R("#16161e", "#2c2e3c", "#474a5c", "#6a6e84", "#9a9eb4")
STONE = R("#23202a", "#44404e", "#6c6878", "#9894a4", "#c8c4d2")
BONE = R("#4a3c38", "#8a7a6a", "#c2b49c", "#e6dcc4", "#fffaec")
WOOD = R("#2c180e", "#54301a", "#7c4c28", "#a66e3c", "#cf9a5c")
LEATHER = R("#2e1810", "#55301e", "#7e4a2a", "#a66c40", "#d09a62")
ROPE = R("#4a3418", "#7c5c2c", "#ac8a4a", "#d2b672", "#f0dea4")
RUBY = R("#4a0a1c", "#96142c", "#dc2c3c", "#ff6a5c", "#ffc2b0")
EMERALD = R("#0c3a24", "#147a3c", "#2cbc54", "#6ee882", "#ccffc8")
SAPPHIRE = R("#101c5a", "#1c3ea4", "#3a74e4", "#72b0ff", "#ccecff")
AMBER = R("#6a2a04", "#b85a0c", "#f0921c", "#ffc24a", "#fff0a4")
AMETHYST = R("#2c0f5a", "#5a26a4", "#9450e4", "#c48cff", "#f0dcff")
AQUA = R("#0a3a4a", "#12788a", "#28bcc4", "#72ecea", "#d4fffa")
PEARL = R("#5a5470", "#9a94b0", "#d0cce0", "#f0eef8", "#ffffff")
