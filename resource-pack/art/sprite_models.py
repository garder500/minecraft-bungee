"""A 3D model grown out of a flat sprite: the same drawing, with volume.

The 32x32 GUI sprite is the texture. Every opaque pixel is extruded on both sides of the sprite plane, deeper the further
it is from the silhouette edge (LEVELS terraces: a rim and a raised body), and the pixels of a terrace are merged into
as few boxes as possible. Seen from the front the model is the sprite; turned, it is a bevelled slab like a vanilla
item, a little thicker.
Stdlib only, deterministic.
"""
import math
import struct
import zlib

LEVELS = 2          # terraces from the rim to the thickest part: a rim and a raised body, like a bevelled vanilla item
ROUND_PX = 2.0      # pixels from the edge at which the full thickness is reached
INSET = 2           # pixels inside the edge whose colour paints the sides
RIM = 0.5           # half thickness at the rim, in model units (a vanilla flat item is 0.5)


def read_png(path):
    """Pixels [y][x] = (r, g, b, a) of an 8-bit RGBA PNG without interlacing."""
    data = path.read_bytes()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if (depth, colour, interlace) != (8, 6, 0):
                raise ValueError(f"{path.name}: expected 8-bit RGBA, not interlaced")
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    raw, stride, rows, prev = zlib.decompress(idat), w * 4, [], bytearray(w * 4)
    for y in range(h):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b, c = prev[i], (prev[i - 4] if i >= 4 else 0)
            if kind == 1:
                line[i] = (line[i] + a) & 255
            elif kind == 2:
                line[i] = (line[i] + b) & 255
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([tuple(line[x * 4:x * 4 + 4]) for x in range(w)])
        prev = line
    return w, h, rows


def edge_distance(solid):
    """For every opaque pixel, the distance in pixels to the nearest transparent one (outside the sprite counts)."""
    h, w = len(solid), len(solid[0])
    holes = [(x, y) for y in range(-1, h + 1) for x in range(-1, w + 1)
             if not (0 <= x < w and 0 <= y < h and solid[y][x])]
    return [[min(math.hypot(x - hx, y - hy) for hx, hy in holes) if solid[y][x] else 0.0 for x in range(w)]
            for y in range(h)]


def rectangles(mask):
    """Covers the set pixels of mask with rectangles (x0, y0, x1, y1), exclusive ends: row runs, merged downwards."""
    out, open_runs = [], {}
    for y, row in enumerate(list(mask) + [[False] * len(mask[0])]):
        runs, x = set(), 0
        while x < len(row):
            if row[x]:
                x0 = x
                while x < len(row) and row[x]:
                    x += 1
                runs.add((x0, x))
            else:
                x += 1
        for run in list(open_runs):
            if run not in runs:
                out.append((run[0], open_runs.pop(run), run[1], y))
        for run in runs:
            open_runs.setdefault(run, y)
    return sorted(out, key=lambda r: (r[1], r[0]))


def elements(path, thickness, texture="#0"):
    """The boxes of the sprite at path; thickness = the half depth of the thickest part, in model units."""
    w, h, px = read_png(path)
    if w != h or w not in (16, 32):
        raise ValueError(f"{path.name}: a square 16 or 32 px sprite is expected")
    unit = 16 / w
    solid = [[p[3] >= 128 for p in row] for row in px]
    dist = edge_distance(solid)

    def level(d):  # 1 at the rim .. LEVELS in the middle
        return min(LEVELS, 1 + int(min(d - 1, ROUND_PX) / ROUND_PX * LEVELS)) if d > 0 else 0

    def half(lv):  # a quarter circle from RIM to thickness
        t = (lv - 1) / (LEVELS - 1)
        return round(RIM + (thickness - RIM) * math.sqrt(1 - (1 - t) ** 2), 3)

    def ok(x, y):
        return 0 <= x < w and 0 <= y < h and solid[y][x]

    def inside(start, step, opaque):  # the furthest line up to INSET pixels inside that is opaque all along
        best = start
        for k in range(1, INSET + 1):
            if not opaque(start + k * step):
                break
            best = start + k * step
        return best

    out = []
    for lv in range(1, LEVELS + 1):
        mask = [[level(d) >= lv for d in row] for row in dist]
        z0, z1 = round(8 - half(lv), 3), round(8 + half(lv), 3)
        for x0, y0, x1, y1 in rectangles(mask):
            u0, v0, u1, v1 = x0 * unit, y0 * unit, x1 * unit, y1 * unit
            # the sides take their colour a little inside the drawing: the rim pixels are the dark outline of the
            # sprite and would turn every edge black
            col = lambda x: [x * unit, v0, (x + 1) * unit, v1]   # the pixel column that borders a side
            line = lambda y: [u0, y * unit, u1, (y + 1) * unit]  # the pixel row that borders the top or bottom
            faces = {
                "south": [u0, v0, u1, v1], "north": [u1, v0, u0, v1],
                "west": col(inside(x0, 1, lambda x: all(ok(x, y) for y in range(y0, y1)))),
                "east": col(inside(x1 - 1, -1, lambda x: all(ok(x, y) for y in range(y0, y1)))),
                "up": line(inside(y0, 1, lambda y: all(ok(x, y) for x in range(x0, x1)))),
                "down": line(inside(y1 - 1, -1, lambda y: all(ok(x, y) for x in range(x0, x1)))),
            }
            out.append({"from": [u0, 16 - v1, z0], "to": [u1, 16 - v0, z1],
                        "faces": {f: {"uv": uv, "texture": texture} for f, uv in faces.items()}})
    return out


# ---- a sprite drawn from a box model: the items that never had a painted sprite (axes, shovels, bows) get one, so they
# ---- can be grown into the same bevelled slab as the others and show the same drawing in the inventory.

_LIGHT = (-0.35, 0.75, 0.56)
_AXES = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}


def _turn(p, rot, back=False, about=True):
    """p turned by an element rotation (or turned back); about=False turns a direction (no origin)."""
    if not rot:
        return list(p)
    i, j = _AXES[rot["axis"]]
    a = math.radians(-rot["angle"] if back else rot["angle"])
    o = rot["origin"] if about else (0, 0, 0)
    u, v = p[i] - o[i], p[j] - o[j]
    q = list(p)
    q[i], q[j] = o[i] + u * math.cos(a) - v * math.sin(a), o[j] + u * math.sin(a) + v * math.cos(a)
    return q


def _corners(el):
    f, t = el["from"], el["to"]
    return [_turn((x, y, z), el.get("rotation")) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]


def _hit(el, origin, direction):
    """(distance, face, local point) where the ray enters the box, or None."""
    rot = el.get("rotation")
    o, d = _turn(origin, rot, back=True), _turn(direction, rot, back=True, about=False)
    near, far, face = -1e9, 1e9, None
    for k, (lo_face, hi_face) in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
        lo, hi = el["from"][k], el["to"][k]
        if abs(d[k]) < 1e-9:
            if not lo <= o[k] <= hi:
                return None
            continue
        t0, t1 = (lo - o[k]) / d[k], (hi - o[k]) / d[k]
        enter = lo_face if t0 < t1 else hi_face
        t0, t1 = min(t0, t1), max(t0, t1)
        if t0 > near:
            near, face = t0, enter
        far = min(far, t1)
    if face is None or near > far or far < 0:
        return None
    return near, face, [o[k] + d[k] * near for k in range(3)]


def render(model_elements, atlas, size=32, outline=True, frame=None):
    """The model seen from the front (its south side), as size x size pixels [y][x] = (r, g, b, a) with a dark outline.
    A model wider than the 16 units of a sprite is shrunk to fit. Returns (pixels, fit) where fit = (scale, dx, dy) maps
    a model point to sprite units: x * scale + dx. A transparent texel lets the ray through. frame = (x0, y0, x1, y1)
    shows that part of the model space instead of fitting the model (previews), outline=False leaves the edge bare."""
    pts = [c for el in model_elements for c in _corners(el)]
    lo = [min(p[k] for p in pts) for k in range(2)]
    hi = [max(p[k] for p in pts) for k in range(2)]
    room = 16 - 2 * 16 / size  # one pixel kept free all around for the outline
    scale = min(1.0, room / max(hi[0] - lo[0], hi[1] - lo[1]))
    shift = []
    for k in range(2):  # stay where it is when it fits, else slide into the frame
        a, b = lo[k] * scale, hi[k] * scale
        edge = 16 / size
        shift.append(edge - a if a < edge else (16 - edge - b if b > 16 - edge else 0.0))
    norm = math.sqrt(sum(c * c for c in _LIGHT))
    px = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for j in range(size):
        for i in range(size):
            if frame:
                x = frame[0] + (i + 0.5) * (frame[2] - frame[0]) / size
                y = frame[3] - (j + 0.5) * (frame[3] - frame[1]) / size
            else:
                x = ((i + 0.5) * 16 / size - shift[0]) / scale
                y = (16 - (j + 0.5) * 16 / size - shift[1]) / scale
            hits = sorted(((hit, k) for k, el in enumerate(model_elements)
                           for hit in [_hit(el, (x, y, 64.0), (0.0, 0.0, -1.0))] if hit), key=lambda h: h[0][0])
            for (_, face, local), k in hits:
                el = model_elements[k]
                f, t = el["from"], el["to"]
                a, b = {"south": (0, 1), "north": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}[face]
                fu = min(0.999, max(0.0, (local[a] - f[a]) / (t[a] - f[a])))
                fv = min(0.999, max(0.0, (t[b] - local[b]) / (t[b] - f[b])))
                u0, v0, u1, v1 = el["faces"][face]["uv"]
                r, g, bl, alpha = atlas[int(v0 + fv * (v1 - v0))][int(u0 + fu * (u1 - u0))]
                if not alpha:
                    continue
                n = {"south": (0, 0, 1), "north": (0, 0, -1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
                     "down": (0, -1, 0)}[face]
                n = _turn(n, el.get("rotation"), about=False)
                lit = 0.72 + 0.34 * max(0.0, sum(n[k] * _LIGHT[k] for k in range(3)) / norm)
                px[j][i] = (min(255, int(r * lit)), min(255, int(g * lit)), min(255, int(bl * lit)), 255)
                break
    out = [row[:] for row in px]
    for j in range(size if outline else 0):
        for i in range(size):
            if px[j][i][3]:
                continue
            near = [px[y][x] for x, y in ((i - 1, j), (i + 1, j), (i, j - 1), (i, j + 1))
                    if 0 <= x < size and 0 <= y < size and px[y][x][3]]
            if near:
                r, g, bl = (min(c[k] for c in near) for k in range(3))
                out[j][i] = (int(r * 0.3), int(g * 0.3), int(bl * 0.32), 255)
    return out, (scale, shift[0], shift[1])
