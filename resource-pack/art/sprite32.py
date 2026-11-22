"""A 32x32 RGBA canvas and shape helpers for the 32px item sprites (runes, realm resources). Stdlib only, deterministic.

Same conventions as boss_items.Canvas (None = transparent, one dark outline pixel, shapes are tested at pixel centres)
but 32 wide, with a flat-colour PNG writer. rune_items.py and realm32.py draw with it.
"""
import math
import struct
import zlib

N = 32
OUT = (0x14, 0x0F, 0x18, 255)  # the outline of the lore sprites (boss_items.LORE_OUT)


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3)) + (255,)


def noise(x, y, seed=0):
    """Deterministic value in [0, 1) from integer coordinates (no random module, same output everywhere)."""
    v = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65536.0


class Canvas:
    def __init__(self):
        self.p = [[None] * N for _ in range(N)]

    def set(self, x, y, color):
        x, y = int(x), int(y)
        if 0 <= x < N and 0 <= y < N:
            self.p[y][x] = color

    def get(self, x, y):
        return self.p[y][x] if 0 <= x < N and 0 <= y < N else None

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def line(self, x0, y0, x1, y1, color):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), color)

    def mask(self, inside):
        return {(x, y) for y in range(N) for x in range(N) if inside(x + 0.5, y + 0.5)}

    def outline(self):
        out = [row[:] for row in self.p]
        for y in range(N):
            for x in range(N):
                if self.p[y][x] is None and any(self.get(x + dx, y + dy) is not None
                                                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = OUT
        self.p = out

    def png(self):
        raw = bytearray()
        for row in self.p:
            raw.append(0)
            for px in row:
                raw.extend(px if px else (0, 0, 0, 0))

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# ---------------------------------------------------------------- shapes (functions of x, y)

def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1


def rect(x0, y0, x1, y1):
    return lambda x, y: x0 <= x <= x1 and y0 <= y <= y1


def poly(points):
    def inside(x, y):
        hit = False
        j = len(points) - 1
        for i in range(len(points)):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
                hit = not hit
            j = i
        return hit
    return inside


def seg(x0, y0, x1, y1, t):
    """A thick segment: every point within t of the segment."""
    dx, dy = x1 - x0, y1 - y0
    ln = dx * dx + dy * dy or 1.0

    def inside(x, y):
        u = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / ln))
        return (x - (x0 + u * dx)) ** 2 + (y - (y0 + u * dy)) ** 2 <= t * t
    return inside


def union(*shapes):
    return lambda x, y: any(s(x, y) for s in shapes)


def minus(a, *cuts):
    return lambda x, y: a(x, y) and not any(s(x, y) for s in cuts)


def sphere(c, cx, cy, r, ramp, light=(-0.55, -0.65), rim=None):
    """Shades a ball with the ramp (dark to light), 2x2 ordered dither between the steps, no colour outside the ramp."""
    lx, ly = light
    lz = math.sqrt(max(0.0, 1 - lx * lx - ly * ly))
    bayer = ((0.0, 0.5), (0.75, 0.25))
    for y in range(N):
        for x in range(N):
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1:
                continue
            nz = math.sqrt(1 - d2)
            lum = max(0.0, min(1.0, 0.18 + 0.82 * (dx * lx + dy * ly + nz * lz)))
            pos = lum * (len(ramp) - 1)
            lo = int(pos)
            frac = pos - lo
            idx = lo + (1 if lo < len(ramp) - 1 and frac > bayer[y % 2][x % 2] else 0)
            col = ramp[idx]
            if rim and dx * 0.7 + dy * 0.7 > 0.86:   # a rim light on the lower right
                col = rim
            c.set(x, y, col)


def sparkle(c, x, y, color, bright=None):
    c.set(x, y, bright or color)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.set(x + dx, y + dy, color)


def contact_sheet(path, canvases, cols, scale=5, gap=2):
    """A PNG contact sheet: canvases (Canvas or None for an empty cell) on a checker, `scale` times larger."""
    rows = (len(canvases) + cols - 1) // cols
    cell = (N + gap) * scale
    w, h = cols * cell, rows * cell
    pix = [[None] * w for _ in range(h)]
    for i, cv in enumerate(canvases):
        if cv is None:
            continue
        ox, oy = (i % cols) * cell + gap * scale // 2, (i // cols) * cell + gap * scale // 2
        for y in range(N):
            for x in range(N):
                col = cv.p[y][x]
                if col:
                    for dy in range(scale):
                        for dx in range(scale):
                            pix[oy + y * scale + dy][ox + x * scale + dx] = col
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(pix[y][x][:3] if pix[y][x] else ((44, 44, 54) if (x // 8 + y // 8) % 2 else (36, 36, 46)))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
