#!/usr/bin/env python3
"""Draws the 16x16 icons of the boss feature (summon items, boss drops, slayer drops) with the stdlib only.

    python3 resource-pack/art/boss_items.py            # writes textures, models and item definitions
    python3 resource-pack/art/boss_items.py --sheet F  # also writes a 10x contact sheet PNG to F (not committed)

For every item id of BossItems.table() it writes
  pack/assets/vampyuyu/textures/item/boss/<id>.png
  pack/assets/vampyuyu/models/item/boss/<id>.json      (parent item/generated)
  pack/assets/vampyuyu/items/boss/<id>.json            (item model vampyuyu:boss/<id>)
The output is deterministic. Style: one dark outline, three tone shading (light top-left), rarity coloured accents.
"""
import json
import math
import pathlib
import struct
import sys
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
OUT = (0x0E, 0x0B, 0x18, 255)

# rarity accents (match core Rarity colours): epic purple, legendary gold, mythic pink, uncommon green
EPIC = (0xB8, 0x6B, 0xFF)
LEGENDARY = (0xFF, 0xB0, 0x2E)
MYTHIC = (0xFF, 0x7A, 0xD9)
UNCOMMON = (0x5E, 0xE0, 0x7A)


def shade(rgb, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in rgb) + (255,)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


class Canvas:
    def __init__(self):
        self.p = [[None] * 16 for _ in range(16)]

    def set(self, x, y, color):
        x, y = int(x), int(y)
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[y][x] = color

    def get(self, x, y):
        return self.p[y][x] if 0 <= x < 16 and 0 <= y < 16 else None

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def line(self, x0, y0, x1, y1, color):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), color)

    def fill_shape(self, inside, rgb, light=1.25, dark=0.7, mid=1.0):
        """Fills every pixel where inside(x, y) is true, shaded by the position in the shape."""
        pts = [(x, y) for y in range(16) for x in range(16) if inside(x + 0.5, y + 0.5)]
        if not pts:
            return
        for x, y in pts:
            up_left = not inside(x - 0.5, y - 0.5) or not inside(x + 0.5, y - 1.5) or not inside(x - 1.5, y + 0.5)
            down_right = not inside(x + 1.5, y + 0.5) or not inside(x + 0.5, y + 1.5)
            if up_left and not down_right:
                f = light
            elif down_right and not up_left:
                f = dark
            else:
                f = mid
            self.set(x, y, shade(rgb, f))

    def outline(self):
        out = [row[:] for row in self.p]
        for y in range(16):
            for x in range(16):
                if self.p[y][x] is not None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if self.get(x + dx, y + dy) is not None:
                        out[y][x] = OUT
                        break
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

        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# ---------------------------------------------------------------- shape helpers

def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1


def diamond(cx, cy, rx, ry):
    return lambda x, y: abs(x - cx) / rx + abs(y - cy) / ry <= 1


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


def union(*shapes):
    return lambda x, y: any(s(x, y) for s in shapes)


def sparkle(c, x, y, rgb):
    c.set(x, y, shade(rgb, 1.5))
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.set(x + dx, y + dy, shade(rgb, 1.15))


# ---------------------------------------------------------------- icon painters

def gem(c, rgb, accent):
    c.fill_shape(poly([(8, 1), (14, 6), (11, 15), (5, 15), (2, 6)]), rgb)
    c.line(2, 6, 14, 6, shade(rgb, 0.8))
    c.line(5, 6, 8, 1, shade(rgb, 1.35))
    c.line(11, 6, 8, 1, shade(rgb, 0.95))
    c.line(5, 6, 8, 14, shade(rgb, 1.0))
    c.line(11, 6, 8, 14, shade(rgb, 0.78))
    sparkle(c, 5, 4, accent)


def orb(c, rgb, accent, core=None):
    c.fill_shape(circle(8, 8, 6.3), rgb)
    if core:
        c.fill_shape(circle(8, 8, 3.0), core, light=1.2, dark=0.85)
    c.set(5, 5, shade((255, 255, 255), 1.0))
    c.set(6, 5, shade(accent, 1.4))
    c.set(5, 6, shade(accent, 1.3))


def heart(c, rgb, accent):
    c.fill_shape(union(circle(5.2, 5.6, 3.6), circle(10.8, 5.6, 3.6), poly([(1.7, 7), (14.3, 7), (8, 14.8)])), rgb)
    c.set(4, 4, shade(accent, 1.6))
    c.set(5, 4, shade(accent, 1.4))
    c.set(4, 5, shade(accent, 1.3))


def egg(c, rgb, accent):
    c.fill_shape(ellipse(8, 8.5, 5.2, 6.8), rgb)
    for x, y in ((6, 6), (10, 8), (7, 11), (9, 4), (5, 9)):
        c.rect(x, y, x + 1, y + 1, shade(accent, 0.75))
    c.set(6, 4, shade((255, 255, 255), 1.0))


def scale(c, rgb, accent):
    c.fill_shape(poly([(3, 2), (13, 2), (14, 9), (8, 15), (2, 9)]), rgb)
    for i, y in enumerate((5, 8, 11)):
        c.line(5 - (1 if i == 2 else 0), y, 11 + (1 if i == 2 else 0), y, shade(rgb, 0.78))
    c.line(8, 2, 8, 13, shade(accent, 1.0))
    c.set(4, 3, shade((255, 255, 255), 1.0))


def shard(c, rgb, accent):
    c.fill_shape(poly([(9, 1), (13, 9), (10, 15), (4, 11), (5, 5)]), rgb)
    c.line(9, 1, 8, 13, shade(rgb, 1.35))
    c.line(5, 5, 13, 9, shade(rgb, 0.8))
    sparkle(c, 6, 6, accent)


def fang(c, rgb, accent):
    c.fill_shape(poly([(4, 1), (12, 1), (11, 5), (9, 11), (8, 15), (7, 11), (5, 5)]), rgb)
    c.line(5, 2, 8, 13, shade(rgb, 1.3))
    c.line(4, 1, 12, 1, shade(accent, 1.0))
    c.set(7, 3, shade((255, 255, 255), 1.0))


def sword(c, blade, accent, grip=(0x6B, 0x45, 0x25)):
    # diagonal blade from the bottom left grip to the top right tip
    for i in range(10):
        x, y = 4 + i, 11 - i
        c.set(x, y, shade(blade, 1.2))
        c.set(x + 1, y, shade(blade, 0.9))
        c.set(x, y - 1, shade(blade, 1.0))
    c.set(14, 1, shade(blade, 1.5))
    c.set(13, 1, shade(blade, 1.2))
    c.set(14, 2, shade(blade, 1.0))
    c.line(2, 9, 7, 14, shade(accent, 1.0))  # guard
    c.line(3, 9, 7, 13, shade(accent, 0.75))
    c.line(1, 14, 4, 11, shade(grip, 1.0))
    c.set(1, 14, shade(accent, 1.2))
    sparkle(c, 10, 5, accent)


def dagger(c, blade, accent):
    for i in range(7):
        x, y = 6 + i, 9 - i
        c.set(x, y, shade(blade, 1.2))
        c.set(x + 1, y, shade(blade, 0.85))
    c.set(13, 2, shade(blade, 1.5))
    c.line(3, 9, 8, 12, shade(accent, 1.0))
    c.line(2, 13, 5, 10, shade((0x5A, 0x3A, 0x20), 1.0))
    for x, y in ((9, 6), (10, 5)):
        c.set(x, y, shade(accent, 1.4))


def trident(c, rgb, accent):
    c.line(8, 4, 8, 15, shade((0x7A, 0x5A, 0x38), 1.0))
    for x in (4, 8, 12):
        c.line(x, 1, x, 5, shade(rgb, 1.15))
        c.set(x, 0, shade(rgb, 1.5))
    c.line(4, 5, 12, 5, shade(rgb, 0.9))
    c.line(5, 6, 11, 6, shade(rgb, 0.7))
    c.set(4, 2, shade(accent, 1.5))
    c.set(8, 2, shade(accent, 1.5))


def scepter(c, rgb, accent):
    c.line(3, 14, 10, 7, shade((0x5A, 0x4A, 0x66), 1.0))
    c.line(4, 14, 11, 7, shade((0x3C, 0x30, 0x48), 1.0))
    c.fill_shape(circle(11.5, 4.5, 3.4), rgb)
    c.fill_shape(circle(11.5, 4.5, 1.5), accent, light=1.3, dark=0.9)
    for x, y in ((8, 1), (15, 1), (15, 8), (8, 8)):
        c.set(x, y, shade(accent, 1.0))
    c.set(10, 3, shade((255, 255, 255), 1.0))


def feather(c, rgb, accent):
    c.fill_shape(poly([(13, 1), (14, 6), (11, 11), (5, 14), (3, 13), (4, 8), (8, 3)]), rgb)
    c.line(3, 14, 12, 3, shade(accent, 1.3))
    for i in range(4):
        c.set(6 + i, 11 - i - 2, shade(rgb, 0.7))
        c.set(8 + i, 8 - i, shade(rgb, 1.3))
    c.set(11, 4, shade((255, 255, 255), 1.0))


def bone(c, rgb, accent):
    c.line(4, 12, 11, 5, shade(rgb, 1.0))
    c.line(5, 12, 12, 5, shade(rgb, 0.8))
    for cx, cy in ((3, 11), (5, 13), (11, 3), (13, 5)):
        c.rect(cx - 1, cy - 1, cx, cy, shade(rgb, 1.15))
    c.set(11, 4, shade(accent, 1.4))


def web(c, rgb, accent):
    cx = cy = 7.5
    for ang in range(0, 360, 45):
        a = math.radians(ang)
        c.line(7, 7, 7 + round(math.cos(a) * 7), 7 + round(math.sin(a) * 7), shade(rgb, 1.0))
    for r in (2.5, 4.5, 6.5):
        for ang in range(0, 360, 45):
            a1, a2 = math.radians(ang), math.radians(ang + 45)
            c.line(round(7 + math.cos(a1) * r), round(7 + math.sin(a1) * r),
                   round(7 + math.cos(a2) * r), round(7 + math.sin(a2) * r), shade(rgb, 0.8))
    c.set(7, 7, shade(accent, 1.4))


def flesh(c, rgb, accent):
    c.fill_shape(poly([(2, 5), (7, 2), (13, 4), (14, 10), (9, 14), (3, 12)]), rgb)
    c.line(5, 5, 9, 9, shade(accent, 0.9))
    for i in range(3):
        c.set(5 + i * 2, 4 + i * 2, shade((0xE8, 0xE0, 0xC8), 1.0))
    c.set(11, 6, shade(accent, 0.7))
    c.set(10, 11, shade(accent, 0.7))


def pelt(c, rgb, accent):
    c.fill_shape(poly([(2, 3), (6, 2), (10, 2), (14, 3), (13, 8), (14, 13), (10, 14), (6, 13), (3, 14), (3, 8)]), rgb)
    for x, y in ((5, 5), (9, 5), (7, 8), (11, 9), (5, 11), (9, 12)):
        c.set(x, y, shade(rgb, 0.7))
        c.set(x + 1, y, shade(rgb, 0.7))
    c.set(4, 3, shade(accent, 1.5))


def flame(c, rgb, accent):
    c.fill_shape(poly([(8, 1), (11, 5), (13, 9), (12, 13), (8, 15), (4, 13), (3, 9), (5, 6), (6, 8)]), rgb)
    c.fill_shape(poly([(8, 7), (10, 10), (9, 13), (7, 13), (6, 10)]), accent, light=1.3, dark=0.95)
    c.set(8, 3, shade(accent, 1.6))


def colossus(c, rgb, accent):
    # an iron cube with a glowing cross
    c.fill_shape(poly([(2, 4), (8, 1), (14, 4), (14, 11), (8, 15), (2, 11)]), rgb)
    c.line(8, 1, 8, 15, shade(rgb, 0.7))
    c.line(2, 4, 8, 8, shade(rgb, 0.8))
    c.line(14, 4, 8, 8, shade(rgb, 1.25))
    c.fill_shape(circle(8, 8, 1.6), accent, light=1.4, dark=1.0)
    sparkle(c, 4, 5, accent)


def pearl(c, rgb, accent):
    orb(c, rgb, accent, core=shade((0x10, 0x08, 0x20), 1.0)[:3])
    c.set(8, 8, shade(accent, 1.6))


def charm(c, rgb, accent):
    c.line(8, 0, 4, 4, shade((0x40, 0x34, 0x28), 1.0))
    c.line(8, 0, 12, 4, shade((0x40, 0x34, 0x28), 1.0))
    c.fill_shape(circle(8, 9, 5.2), rgb)
    for x, y in ((6, 8), (10, 8)):
        c.rect(x, y, x + 1, y + 1, shade(accent, 1.4))
    c.rect(5, 6, 5, 6, shade(accent, 1.1))
    c.rect(11, 6, 11, 6, shade(accent, 1.1))
    c.line(6, 12, 10, 12, shade(rgb, 0.6))


def catalyst(c, rgb, accent):
    c.fill_shape(poly([(8, 1), (13, 8), (11, 14), (5, 14), (3, 8)]), rgb)
    c.fill_shape(circle(8, 9, 2.2), accent, light=1.3, dark=0.9)
    c.set(6, 5, shade((255, 255, 255), 1.0))


def conduit(c, rgb, accent):
    c.fill_shape(diamond(8, 8, 6, 7), rgb)
    c.line(8, 1, 8, 15, shade(accent, 1.0))
    c.line(2, 8, 14, 8, shade(accent, 0.8))
    c.fill_shape(circle(8, 8, 1.8), accent, light=1.5, dark=1.0)


def sigil(c, rgb, accent):
    # a shard with a glowing rune
    c.fill_shape(poly([(8, 0), (13, 5), (11, 15), (5, 15), (3, 5)]), rgb)
    c.line(8, 3, 8, 12, shade(accent, 1.4))
    c.line(6, 6, 10, 6, shade(accent, 1.2))
    c.line(6, 9, 10, 9, shade(accent, 1.0))
    sparkle(c, 5, 3, accent)


def wing_core(c, rgb, accent):
    orb(c, rgb, accent, core=mix(rgb, (255, 255, 255), 0.5))
    # little wing marks
    c.line(1, 9, 3, 6, shade(accent, 1.3))
    c.line(1, 11, 4, 8, shade(accent, 1.0))
    c.line(14, 9, 12, 6, shade(accent, 1.3))
    c.line(14, 11, 11, 8, shade(accent, 1.0))


def ingot_like(c, rgb, accent):
    gem(c, rgb, accent)


# id -> (painter, base colour, accent)
ICONS = {
    "void_sigil": (sigil, (0x6A, 0x2E, 0xC8), EPIC),
    "magma_core": (orb, (0xD8, 0x3A, 0x10), (0xFF, 0xB0, 0x20)),
    "frost_heart": (heart, (0x6F, 0xC4, 0xF4), (0xE8, 0xFA, 0xFF)),
    "matriarch_egg": (egg, (0x4E, 0x8A, 0x2E), (0xB8, 0xE0, 0x60)),
    "leviathan_scale": (scale, (0x1E, 0x8C, 0xA8), (0x7A, 0xF0, 0xE0)),
    "wing_core": (wing_core, (0xB8, 0x6B, 0xFF), MYTHIC),
    "voidwyrm_scale": (scale, (0x4A, 0x1E, 0x8C), EPIC),
    "void_fang": (fang, (0xD8, 0xC8, 0xFF), LEGENDARY),
    "warden_heart": (heart, (0xC8, 0x30, 0x10), (0xFF, 0xA0, 0x30)),
    "ember_blade": (sword, (0xFF, 0x7A, 0x1E), (0xFF, 0xD0, 0x40)),
    "frost_shard": (shard, (0x8A, 0xD8, 0xFF), (0xFF, 0xFF, 0xFF)),
    "glacial_edge": (sword, (0xB0, 0xE8, 0xFF), (0x4F, 0x9B, 0xFF)),
    "matriarch_silk": (web, (0xE0, 0xF0, 0xD0), (0x9A, 0xCD, 0x32)),
    "venom_dagger": (dagger, (0x9A, 0xE0, 0x3C), (0x2E, 0x8B, 0x57)),
    "leviathan_tooth": (fang, (0xC8, 0xF0, 0xE8), (0x1E, 0x8C, 0xA8)),
    "tidal_trident": (trident, (0x46, 0xE0, 0xD0), (0xFF, 0xFF, 0xFF)),
    "lord_bone": (bone, (0xE0, 0xDC, 0xC8), (0xB8, 0x6B, 0xFF)),
    "necro_scepter": (scepter, (0x4A, 0x2E, 0x6A), (0x7A, 0xFF, 0x9A)),
    "colossus_core": (colossus, (0xA8, 0xA8, 0xB0), LEGENDARY),
    "roc_feather": (feather, (0x7F, 0xDC, 0xFF), (0xFF, 0xF2, 0x7F)),
    "revenant_flesh": (flesh, (0x5E, 0x8A, 0x4A), (0x8A, 0x3A, 0x3A)),
    "revenant_catalyst": (catalyst, (0x4E, 0xB8, 0x6A), (0xE0, 0xFF, 0x80)),
    "tarantula_web": (web, (0xF0, 0xF0, 0xF0), UNCOMMON),
    "tarantula_charm": (charm, (0x3C, 0x2A, 0x4A), (0xFF, 0x4A, 0x4A)),
    "ravager_tooth": (fang, (0xF0, 0xE8, 0xD0), (0x9A, 0x7A, 0x5A)),
    "ravager_pelt": (pelt, (0x8A, 0x6A, 0x4A), (0xFF, 0xE0, 0xA0)),
    "null_sphere": (pearl, (0x2C, 0x8A, 0x78), (0x7A, 0xF0, 0xD8)),
    "void_conduit": (conduit, (0x3A, 0x1E, 0x7A), (0xC8, 0x7A, 0xFF)),
    "demon_ember": (flame, (0xFF, 0x7A, 0x1E), (0xFF, 0xE0, 0x60)),
    "demon_core": (orb, (0x8A, 0x10, 0x10), (0xFF, 0x60, 0x20)),
}


def draw(item_id):
    painter, rgb, accent = ICONS[item_id]
    c = Canvas()
    painter(c, rgb, accent)
    c.outline()
    return c


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def sheet(path):
    scale_up = 10
    cols = 6
    ids = list(ICONS)
    rows = (len(ids) + cols - 1) // cols
    w, h = cols * 18 * scale_up, rows * 18 * scale_up
    drawn = {i: draw(i).p for i in ids}
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            col, row = x // (18 * scale_up), y // (18 * scale_up)
            idx = row * cols + col
            px = None
            if idx < len(ids):
                ix, iy = (x % (18 * scale_up)) // scale_up - 1, (y % (18 * scale_up)) // scale_up - 1
                if 0 <= ix < 16 and 0 <= iy < 16:
                    px = drawn[ids[idx]][iy][ix]
            raw.extend(px if px else ((60, 60, 70, 255) if (x // 10 + y // 10) % 2 else (70, 70, 80, 255)))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    pathlib.Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                                   + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def main():
    tex = PACK / "textures" / "item" / "boss"
    tex.mkdir(parents=True, exist_ok=True)
    for item_id in ICONS:
        (tex / f"{item_id}.png").write_bytes(draw(item_id).png())
        write_json(PACK / "models" / "item" / "boss" / f"{item_id}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/boss/{item_id}"}})
        write_json(PACK / "items" / "boss" / f"{item_id}.json",
                   {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/boss/{item_id}"}})
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1])
    print(f"wrote {len(ICONS)} boss icons")


if __name__ == "__main__":
    main()
