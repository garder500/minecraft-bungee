#!/usr/bin/env python3
"""Custom looks of the bosses: one cuboid item model per boss, drawn here with the stdlib only.

    python3 resource-pack/art/boss_skins.py

For every boss it writes (with Painted: one painted texture region per face, TEXELS per model unit)
  pack/assets/vampyuyu/models/item/boss/skin/<id>.json      (a model made of boxes, 16 units = 1 block)
  pack/assets/vampyuyu/textures/item/boss/skin/<id>.png     (16x16 atlas: 16 materials of 4x4 pixels)
  pack/assets/vampyuyu/items/boss/skin/<id>.json            (item model vampyuyu:boss/skin/<id>)
The plugin (BossSkinService) hides the vanilla mob and shows this model on an item display that follows it, so only
the Skyblock bosses change: vanilla zombies, spiders and dragons of Survival keep their look. The model's front is -z.
The output is deterministic.
"""
import json
import math
import pathlib
import random
import struct
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"


def png(pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def clamp(v):
    return max(0, min(255, int(round(v))))


class Skin:
    """A model made of boxes. Materials are 4x4 pixel tiles of one 16x16 atlas: (name, rgb, style)."""

    def __init__(self, boss, materials, seed):
        self.boss = boss
        self.materials = materials
        self.names = [m[0] for m in materials]
        self.elements = []
        self.rnd = random.Random(seed)

    def tile(self, name):
        i = self.names.index(name)
        return (i % 4) * 4, (i // 4) * 4

    def box(self, a, b, mat, top=None, rot=None):
        for i in range(3):
            if not (-16 <= a[i] <= 32 and -16 <= b[i] <= 32):
                raise ValueError(f"{self.boss}: element out of the model range {a} {b}")
            if abs(b[i] - a[i]) < 0.05:
                raise ValueError(f"{self.boss}: flat element {a} {b}")
        u, v = self.tile(mat)
        tu, tv = self.tile(top) if top else (u, v)
        faces = {f: {"uv": [u, v, u + 4, v + 4], "texture": "#0"} for f in ("north", "east", "south", "west", "down")}
        faces["up"] = {"uv": [tu, tv, tu + 4, tv + 4], "texture": "#0"}
        el = {"from": list(a), "to": list(b), "faces": faces}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
        self.elements.append(el)

    def mirror_x(self, a, b, mat, top=None, rot=None):
        """Draws a box and its mirror image across the middle (x = 8)."""
        self.box(a, b, mat, top, rot)
        ma, mb = [16 - b[0], a[1], a[2]], [16 - a[0], b[1], b[2]]
        mrot = None
        if rot:
            axis, angle, origin = rot
            mrot = (axis, -angle if axis != "x" else angle, [16 - origin[0], origin[1], origin[2]])
        self.box(ma, mb, mat, top, mrot)

    def atlas(self):
        px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
        for i, (name, rgb, style) in enumerate(self.materials):
            u, v = (i % 4) * 4, (i // 4) * 4
            for y in range(4):
                for x in range(4):
                    r, g, b = rgb
                    j = self.rnd.uniform(-0.12, 0.12)
                    if style == "glow":
                        j = 0.25 - 0.08 * (x + y) / 6 + self.rnd.uniform(-0.04, 0.04)
                    elif style == "crack":
                        j = 0.55 if (x + 2 * y) % 5 == 0 else j * 0.5
                    elif style == "frost":
                        j = 0.30 if (x * 3 + y) % 4 == 0 else j
                    elif style == "spots":
                        j = 0.3 if (x == 1 and y == 1) or (x == 3 and y == 3) else j
                    px[v + y][u + x] = (clamp(r * (1 + j)), clamp(g * (1 + j)), clamp(b * (1 + j)), 255)
        return px

    def write(self):
        model = {"textures": {"0": f"vampyuyu:item/boss/skin/{self.boss}", "particle": f"vampyuyu:item/boss/skin/{self.boss}"},
                 "elements": self.elements}
        for path, data in (
                (PACK / "models" / "item" / "boss" / "skin" / f"{self.boss}.json", model),
                (PACK / "items" / "boss" / "skin" / f"{self.boss}.json",
                 {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/boss/skin/{self.boss}"}})):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
        tex = PACK / "textures" / "item" / "boss" / "skin" / f"{self.boss}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        tex.write_bytes(png(self.atlas()))


# ---------------------------------------------------------------------------------------------------------------- painted

TEXELS = 2  # texels per model unit: at the boss scales (2.4 to 4.2) a texel is close to a vanilla pixel


def _hash(*k):
    h = 2166136261
    for v in k:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) / 0xFFFFFFFF


def _noise(x, y, scale, seed):
    """Value noise in [0, 1], smooth over `scale` texels."""
    fx, fy = x / scale, y / scale
    x0, y0 = int(fx // 1), int(fy // 1)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b = _hash(x0, y0, seed), _hash(x0 + 1, y0, seed)
    c, d = _hash(x0, y0 + 1, seed), _hash(x0 + 1, y0 + 1, seed)
    return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty


def _fbm(x, y, seed, scale=6.0):
    return 0.6 * _noise(x, y, scale, seed) + 0.3 * _noise(x, y, scale / 2.3, seed + 7) + 0.1 * _noise(x, y, 1.3, seed + 13)


def _mix(c1, c2, t):
    return tuple(c1[i] + (c2[i] - c1[i]) * t for i in range(3))


def _scale(c, f):
    return tuple(v * f for v in c)


class Painted(Skin):
    """A boss model whose every face has its own painted texture at TEXELS per unit: no stretched flat colour.

    Materials are (name, rgb, style) or (name, rgb, style, accent rgb). Styles: rock, lavarock, plate, glow, ice, cloth,
    bone, scale, leaf, bark, chitin, membrane, gold, void, web, eye. Faces are shaded like a lit sculpture: lighter top,
    darker underside, a soft occlusion toward the bottom of the sides and a one-texel bevel on the edges.
    """

    GLOWS = ("glow", "eye")

    def __init__(self, boss, materials, seed):
        super().__init__(boss, [m[:3] for m in materials], seed)
        self.mats = {m[0]: (m[1], m[2], m[3] if len(m) > 3 else None) for m in materials}
        self.faces = []
        self.seed = seed

    def box(self, a, b, mat, top=None, rot=None):
        for i in range(3):
            if not (-16 <= a[i] <= 32 and -16 <= b[i] <= 32):
                raise ValueError(f"{self.boss}: element out of the model range {a} {b}")
            if abs(b[i] - a[i]) < 0.05:
                raise ValueError(f"{self.boss}: flat element {a} {b}")
        if mat not in self.mats or (top and top not in self.mats):
            raise ValueError(f"{self.boss}: unknown material {mat} / {top}")
        dx, dy, dz = (abs(b[i] - a[i]) for i in range(3))
        el = {"from": list(a), "to": list(b), "faces": {}}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
        dims = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
        for face, (w, h) in dims.items():
            self.faces.append((len(self.elements), face, w, h, top if face == "up" and top else mat, a, b))
        self.elements.append(el)

    # -------------------------------------------------------------- painting

    def paint(self, mat, w, h, face, key):
        rgb, style, accent = self.mats[mat]
        accent = accent or _scale(rgb, 1.6)
        sd = int(_hash(*key) * 1e6)
        px = [[rgb] * w for _ in range(h)]
        rnd = random.Random(sd)
        for y in range(h):
            for x in range(w):
                n = _fbm(x, y, sd)
                c = rgb
                if style == "rock":
                    c = _scale(rgb, 0.78 + 0.42 * n)
                    if _hash(x, y, sd, 1) < 0.04:
                        c = _scale(c, 0.7)
                elif style == "lavarock":
                    c = _scale(rgb, 0.75 + 0.45 * n)
                elif style == "plate":
                    c = _scale(rgb, 0.9 + 0.16 * _fbm(x, y, sd, 3.0))
                    if h >= 8 and y % 7 == 6:
                        c = _scale(rgb, 0.62)
                    elif h >= 8 and y % 7 == 0 and y > 0:
                        c = _scale(rgb, 1.22)
                elif style in self.GLOWS:
                    cx, cy = (w - 1) / 2, (h - 1) / 2
                    d = min(1.0, ((x - cx) ** 2 / max(1, cx * cx) + (y - cy) ** 2 / max(1, cy * cy)) ** 0.5)
                    hot = (1 - d) * (0.55 if style == "eye" else 0.35)
                    c = _mix(_scale(rgb, 0.85 + 0.3 * n), (255, 250, 225), hot)
                elif style == "ice":
                    band = ((x + y) // 4) % 3
                    c = _scale(rgb, (0.88, 1.0, 1.12)[band] + 0.1 * (n - 0.5))
                    if _hash(x, y, sd, 2) < 0.025:
                        c = (245, 252, 255)
                elif style == "cloth":
                    fold = 0.82 + 0.2 * (0.5 + 0.5 * math.sin(x * 0.85 + _hash(sd) * 6.3)) + 0.08 * (n - 0.5)
                    c = _scale(rgb, fold * (0.72 if y >= h - max(1, h // 7) else 1.0))
                elif style == "bone":
                    c = _scale(rgb, 0.9 + 0.12 * _noise(x, y * 3, 4, sd) + 0.05 * n)
                    if _hash(x, y, sd, 3) < 0.03:
                        c = _scale(rgb, 0.62)
                elif style == "scale":
                    s_ = 4                                   # rounded scales in offset rows, not bricks
                    off = (y // s_ % 2) * (s_ // 2)
                    lx, ly = (x + off) % s_, y % s_
                    shade = 1.1 - 0.08 * ly + 0.06 * n
                    if ly == 0 and lx in (1, 2):
                        shade *= 1.14                        # the lit crown of the scale
                    elif ly == s_ - 1 and lx in (0, s_ - 1):
                        shade *= 0.72                        # the shadow between two scales
                    c = _scale(rgb, shade)
                elif style == "leaf":
                    c = _mix(_scale(rgb, 0.6), _scale(rgb, 1.3), n)
                    if _hash(x, y, sd, 4) < 0.05:
                        c = _scale(rgb, 1.5)
                elif style == "bark":
                    g = _noise(x * 3, y * 0.4, 3, sd)
                    c = _scale(rgb, 0.7 + 0.45 * g)
                    if g < 0.22:
                        c = _scale(rgb, 0.5)
                elif style == "chitin":
                    gloss = max(0.0, 1 - abs((x - y * 0.6) - w * 0.3) / 2.5)
                    c = _mix(_scale(rgb, 0.85 + 0.2 * n), _scale(rgb, 2.0), 0.35 * gloss)
                elif style == "membrane":
                    c = _scale(rgb, 0.8 + 0.3 * n)
                    if int(x + y * 0.5) % 9 == 0:
                        c = _mix(c, accent, 0.55)
                elif style == "gold":
                    c = _scale(rgb, 1.18 - 0.4 * (y / max(1, h - 1)) + 0.1 * n)
                    if y == 1:
                        c = _mix(c, (255, 245, 200), 0.5)
                    if (x + 2 * y) % 6 == 0 and h > 3:
                        c = _scale(c, 0.78)
                elif style == "void":
                    c = _scale(rgb, 0.8 + 0.4 * n)
                    if _hash(x, y, sd, 5) < 0.02:
                        c = accent
                elif style == "web":
                    c = _scale(rgb, 0.95 + 0.08 * n)
                    if (x + y) % 5 == 0 or (x - y) % 5 == 0:
                        c = _scale(rgb, 0.78)
                else:
                    c = _scale(rgb, 0.88 + 0.24 * n)
                px[y][x] = c
        if style == "lavarock" and w * h > 12:          # glowing cracks wandering over the rock
            for _ in range(max(1, w * h // 70)):
                x, y = rnd.randrange(w), rnd.randrange(h)
                for _ in range(rnd.randint(4, 10)):
                    px[y][x] = _mix(accent, (255, 240, 170), 0.3)
                    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        if 0 <= nx < w and 0 <= ny < h and px[ny][nx] != accent:
                            px[ny][nx] = _mix(px[ny][nx], accent, 0.35)
                    x = max(0, min(w - 1, x + rnd.choice((-1, 0, 1))))
                    y = max(0, min(h - 1, y + rnd.choice((0, 1, 1))))
        if style == "plate" and w >= 8 and h >= 6:       # rivets
            for rx, ry in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
                px[ry][rx] = _mix(accent, (255, 255, 255), 0.25)
        glow = style in self.GLOWS
        for y in range(h):
            for x in range(w):
                f = 1.0
                if not glow:
                    if face == "up":
                        f = 1.1
                    elif face == "down":
                        f = 0.58
                    else:
                        f = 1.08 - 0.26 * (y / max(1, h - 1))
                    if w >= 6 and h >= 6:                    # a soft bevel on large faces only: a hard one outlined
                        if x == 0 or y == 0:                 # every box like a toy brick
                            f *= 1.06
                        elif x == w - 1 or y == h - 1:
                            f *= 0.9
                px[y][x] = tuple(clamp(v * f) for v in px[y][x])
        return px

    def write(self):
        sized = []
        for i, (el, face, w, h, mat, a, b) in enumerate(self.faces):
            tw, th = max(1, round(w * TEXELS)), max(1, round(h * TEXELS))
            sized.append((th, tw, i))
        sized.sort(reverse=True)
        size = 64
        while True:
            x = y = row = 0
            spots = {}
            ok = True
            for th, tw, i in sized:
                if x + tw > size:
                    x, y, row = 0, y + row, 0
                if y + th > size or tw > size:
                    ok = False
                    break
                spots[i] = (x, y)
                x += tw
                row = max(row, th)
            if ok:
                break
            size *= 2
        img = [[(0, 0, 0, 0)] * size for _ in range(size)]
        k = 16 / size
        for th, tw, i in sized:
            el, face, w, h, mat, a, b = self.faces[i]
            x0, y0 = spots[i]
            painted = self.paint(mat, tw, th, face, (self.seed, i, round(a[0] * 4), round(a[1] * 4), round(a[2] * 4)))
            for yy in range(th):
                for xx in range(tw):
                    r, g, bb = painted[yy][xx]
                    img[y0 + yy][x0 + xx] = (r, g, bb, 255)
            self.elements[el]["faces"][face] = {"uv": [round(x0 * k, 4), round(y0 * k, 4), round((x0 + tw) * k, 4),
                                                       round((y0 + th) * k, 4)], "texture": "#0"}
        model = {"textures": {"0": f"vampyuyu:item/boss/skin/{self.boss}", "particle": f"vampyuyu:item/boss/skin/{self.boss}"},
                 "elements": self.elements}
        for path, data in (
                (PACK / "models" / "item" / "boss" / "skin" / f"{self.boss}.json", model),
                (PACK / "items" / "boss" / "skin" / f"{self.boss}.json",
                 {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/boss/skin/{self.boss}"}})):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
        tex = PACK / "textures" / "item" / "boss" / "skin" / f"{self.boss}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        tex.write_bytes(png(img))


def magma_warden():
    """The Lava Colossus: a knuckle-walking basalt brute, fists planted ahead, its heart showing only through fissures."""
    s = Painted("magma_warden", [("rock", (56, 46, 44), "rock"), ("lava", (255, 112, 24), "glow"),
                                 ("obs", (36, 28, 46), "plate", (255, 140, 40)), ("crack", (62, 42, 36), "lavarock", (255, 120, 30)),
                                 ("eye", (255, 220, 90), "eye"), ("ember", (205, 72, 26), "glow")], 11)
    lean = ("x", -22.5, [8, 15, 10])                                # the upper body leans forward
    s.mirror_x((4.8, 0, 8), (6.8, 3.5, 12), "crack")                # short shins
    s.mirror_x((4.6, 3.5, 8.5), (7.2, 8, 12.5), "crack")            # thighs, narrower than the arms
    s.mirror_x((4.6, 0, 7.8), (7, 0.5, 12.2), "lava")               # a thin glow under the feet
    s.mirror_x((4.4, 2.8, 7.6), (7.4, 5, 9), "obs")                 # knee guards
    s.box((3.6, 7.5, 7.5), (12.4, 10.5, 13.5), "obs")               # hips
    s.box((7, 8, 7.1), (9, 10, 7.6), "ember")                       # belt stone
    s.box((2.5, 9.5, 6.5), (13.5, 16, 14), "crack")                 # belly
    s.box((1, 15, 4.5), (15, 24, 13.5), "crack", top="rock", rot=lean)   # chest
    for x, h in ((6.2, 3), (7.6, 5), (9.0, 3.5)):                   # the molten heart, seen through three fissures
        s.box((x, 19.5 - h / 2, 4.1), (x + 0.8, 19.5 + h / 2, 4.6), "lava", rot=lean)
    s.mirror_x((1.6, 15.5, 3.8), (6.2, 23.5, 4.6), "obs", rot=lean)  # obsidian plates over the chest
    for k, y in enumerate((16, 18.5, 21)):                          # lava running in the belly cracks
        s.box((3 + k * 3.2, 10.5, 6.2), (3.8 + k * 3.2, y - 3, 6.6), "lava")
    s.mirror_x((-3.5, 18, 5.5), (3.6, 24.5, 13), "rock", top="rock")    # boulder shoulders
    s.mirror_x((-3, 24, 6.5), (2.6, 25.6, 12), "obs")
    s.mirror_x((-2.6, 24.5, 7), (0.4, 29.5, 10), "rock", rot=("z", 22.5, [-1.1, 24.5, 8.5]))   # one shard a shoulder
    s.mirror_x((-3.5, 13, 3), (1.5, 21, 9), "crack", rot=("x", 22.5, [-1, 21, 8]))     # upper arms reaching forward
    s.mirror_x((-5, 3.5, -1), (2.5, 13, 6.5), "crack", rot=("x", 22.5, [-1.25, 13, 2.75]))   # huge forearms
    s.mirror_x((-5.5, 0, -4), (3, 4, 5), "crack")                   # fists planted ahead of the body
    s.mirror_x((-5.8, 0, -4.3), (3.3, 0.6, 5.3), "lava")            # molten seam where they touch the ground
    s.mirror_x((-5.8, 2.6, -4.2), (3.3, 4.2, -2), "obs")            # knuckle plates
    s.box((5.5, 20, 1.2), (10.5, 25, 6.5), "rock", top="obs")       # head, low and thrust forward
    s.box((5, 23.6, 0.8), (11, 25.2, 3), "obs")                     # heavy brow
    s.box((6, 22.6, 0.9), (10, 23.2, 1.3), "ember")                 # one smouldering slit under it
    for k, (z, y0, h) in enumerate(((6.8, 22.6, 6), (9.4, 23.8, 4), (10.6, 21.4, 3.5))):   # basalt crest down the back
        s.box((6, y0, z), (10, y0 + h, z + 1.6), "rock" if k != 1 else "crack", rot=("x", 22.5, [8, y0, z + 0.8]))
    s.write()


def frost_revenant():
    """The Boreal Watcher: a gaunt knight, both hands on the pommel of the greatsword planted before him."""
    s = Painted("frost_revenant", [("silver", (112, 126, 150), "plate", (210, 240, 255)), ("ice", (150, 208, 238), "ice"),
                                   ("deep", (42, 66, 120), "plate", (150, 208, 238)), ("eye", (130, 255, 255), "eye"),
                                   ("cloak", (30, 38, 74), "cloth"), ("blade", (205, 236, 250), "ice"),
                                   ("fur", (206, 210, 216), "bone")], 22)
    s.mirror_x((5.2, 1.5, 6.6), (7.6, 12, 9.4), "deep")             # long legs
    s.mirror_x((4.9, 0, 5), (7.8, 2.2, 10), "silver")               # pointed sabatons
    s.mirror_x((5, 2, 6.2), (7.8, 8, 7.0), "silver")                # greaves
    s.mirror_x((4.8, 7.6, 6), (8, 9.4, 9.8), "silver")              # knee cops
    s.box((5.2, 11, 6.2), (10.8, 15, 9.8), "deep")                  # narrow waist
    s.box((4.8, 11.6, 5.8), (11.2, 12.8, 10.2), "silver")           # belt
    s.box((5.4, 7.5, 5.4), (10.6, 12, 6), "ice", rot=("x", 22.5, [8, 12, 5.7]))   # tassets, front
    s.mirror_x((4.2, 7.5, 6.4), (4.8, 12, 9.6), "ice", rot=("z", -22.5, [4.5, 12, 8]))
    s.box((4.2, 15, 5.2), (11.8, 22, 10.6), "silver", top="ice")    # chest
    s.box((6, 16.5, 4.8), (10, 20.5, 5.3), "ice")                   # chest crystal
    s.box((7, 17.5, 4.5), (9, 19.5, 4.9), "eye")
    s.box((3.6, 20, 5), (12.4, 23.4, 12), "fur")                    # fur mantle, all round
    for k, (dx, dy) in enumerate(((0, 0), (1.2, 1.6), (2.2, 3.0))):  # three layered pauldron plates a side
        s.mirror_x((0.8 + dx, 19 + dy, 5 + k * 0.3), (5 + dx * 0.3, 21.4 + dy, 11 - k * 0.3),
                   "silver" if k != 1 else "ice", rot=("z", 22.5, [3, 20 + dy, 8]))
    s.mirror_x((1.2, 23, 6.6), (2.4, 28.5, 7.8), "ice", rot=("z", 22.5, [1.8, 23, 7.2]))  # one icicle a shoulder
    s.mirror_x((1.8, 14.5, 4.2), (4.2, 20, 7.2), "deep", rot=("x", 22.5, [3, 20, 7.5]))   # arms reaching forward
    s.mirror_x((4, 14.4, 0.5), (7.4, 17.5, 3.4), "silver")          # gauntlets on the pommel
    s.box((5.5, 22, 5.6), (10.5, 29, 10.4), "silver", top="ice")    # tall helm
    s.box((5.3, 25.6, 5.3), (10.7, 26.4, 10.7), "deep")             # helm band
    s.box((6, 24, 5.3), (10, 25.2, 5.7), "eye")                     # visor slit
    s.box((7.4, 21.6, 5.2), (8.6, 25.6, 5.6), "deep")               # nose guard
    for x, h in ((6.2, 1.2), (7.6, 2.9), (9.0, 1.8)):               # a crown of three uneven icicles
        s.box((x, 29, 7.2), (x + 0.8, 29 + h, 8.4), "ice")
    s.box((6.7, 0, 1.3), (9.3, 13, 2.1), "blade")                   # the greatsword, point in the ground
    s.box((7.7, 0.5, 0.9), (8.3, 13, 1.35), "deep")                 # its fuller
    s.box((3.5, 13, 1), (12.5, 14.4, 2.4), "silver")                # crossguard
    s.box((7.4, 14.4, 1.3), (8.6, 18, 2.1), "deep")                 # grip
    s.box((7, 18, 0.9), (9, 19.8, 2.5), "eye")                      # pommel crystal
    s.box((6, 3, 10.4), (10, 22, 11.2), "cloak")                    # cloak in three panels
    s.mirror_x((3.6, 3, 10.4), (6, 22, 11.2), "cloak", rot=("y", 22.5, [6, 12, 10.8]))
    s.mirror_x((3.0, 8, 8), (3.8, 21, 11.2), "cloak")               # wrapping round the sides
    s.box((7.4, 4, 11.2), (8.6, 20, 11.6), "ice")                   # frost down the spine
    for i in range(5):                                              # torn hem, flaring back
        s.box((3.6 + i * 1.76, 0, 10.6), (5.0 + i * 1.76, 3.4 + (i % 2) * 1.2, 11.2), "cloak",
              rot=("x", -22.5, [8, 3.4, 10.9]))
    s.write()


def sylvan_matriarch():
    """The Mycelial Queen: a pale-faced woman of bark and moss rising from a mushroom-grown spider on splayed legs."""
    s = Painted("sylvan_matriarch", [("chitin", (36, 54, 40), "chitin"), ("leaf", (66, 104, 52), "leaf"),
                                     ("bark", (76, 58, 40), "bark"), ("eye", (205, 255, 120), "eye"),
                                     ("bloom", (196, 92, 150), "glow"), ("web", (212, 216, 200), "web"),
                                     ("cap", (138, 62, 52), "scale"), ("gill", (220, 200, 170), "bone")], 33)
    s.box((4.5, 5, 3), (11.5, 10, 9.5), "chitin", top="leaf")      # spider body
    s.box((3, 5, 9), (13, 13, 16), "leaf", top="leaf")              # teardrop abdomen
    s.box((4, 6, 16), (12, 12, 20), "leaf", top="leaf")
    s.box((5.5, 7, 20), (10.5, 10.5, 23), "leaf")
    s.box((3.4, 4.2, 9.4), (12.6, 5.2, 19.6), "chitin")             # under-plates
    s.box((7, 13, 11.5), (9, 16, 13.5), "gill")                     # one great mushroom, overhanging
    s.box((4.5, 16, 9), (11.5, 18.4, 16), "cap", top="cap")
    s.box((5.5, 18.4, 10), (10.5, 19.2, 15), "cap")
    s.mirror_x((4.6, 12, 17), (5.6, 14, 18), "gill")                # two small ones
    s.mirror_x((3.6, 14, 16), (6.6, 15.2, 19), "cap")
    s.mirror_x((2.6, 7, 12), (3.4, 10, 15), "bloom")                # glowing spore pods on the flanks
    s.mirror_x((3.6, 1.5, 12), (5.6, 4.5, 15), "web")               # silk sacs
    for i, z in enumerate((2, 6.5, 12, 17)):                        # eight legs, fanned front to back
        s.mirror_x((11, 8, z), (18, 9.8, z + 1.8), "bark", rot=("z", 45, [11, 8.9, z]))   # femur, rising
        tilt = ("x", 22.5, [16.2, 13, z + 0.9]) if i == 0 else ("x", -22.5, [16.2, 13, z + 0.9]) if i == 3 \
            else ("z", 22.5, [16.2, 13, z])
        s.mirror_x((15.6, 4, z + 0.2), (16.9, 13, z + 1.5), "chitin", rot=tilt)            # tibia
        s.mirror_x((15.6, -1, z + 0.2), (16.9, 4, z + 1.5), "bark", rot=tilt)              # its bark-shod foot
        s.mirror_x((15.4, 11.8, z), (17.1, 13.4, z + 1.8), "bark")                         # knee
    s.box((6.6, 9.5, 3.6), (9.4, 14, 6.6), "chitin")                # the woman's narrow waist
    s.box((4.8, 9.5, 2.8), (11.2, 11.5, 7.6), "leaf")               # a moss skirt where she meets the spider
    s.box((5.2, 14, 3.2), (10.8, 19.5, 7), "bark", top="leaf")      # torso of bark
    s.mirror_x((5.4, 15, 2.9), (7.6, 17.4, 3.3), "leaf")            # moss over the breast
    s.box((4.6, 18.6, 3.4), (11.4, 20, 7.2), "leaf")                # mossy collar
    s.mirror_x((2.6, 12, 4.4), (4.4, 19, 6), "gill", rot=("z", -22.5, [3.5, 19, 5.2]))   # pale arms held out
    s.mirror_x((0.6, 10, 4), (2.6, 12.4, 6.4), "bloom")             # a spore glow in each hand
    s.box((6.2, 20, 3.8), (9.8, 24, 6.8), "chitin")                 # head
    s.box((6.6, 20.4, 3.5), (9.4, 23.6, 3.9), "gill")               # pale face
    s.mirror_x((6.9, 22.2, 3.35), (7.7, 22.7, 3.6), "eye")          # slit eyes
    s.mirror_x((5.2, 16, 3.8), (6.2, 24, 7), "leaf")                # moss hair falling to the shoulders
    s.box((5.8, 24, 3.4), (10.2, 25, 7.2), "bark")                  # circlet
    for k, (x, h) in enumerate(((5.6, 3), (7.2, 4.6), (8.8, 3.6))):  # crown of little mushrooms
        s.box((x + 0.3, 25, 4.6), (x + 1.1, 25 + h * 0.7, 5.4), "gill")
        s.box((x - 0.4, 25 + h * 0.7, 4), (x + 1.8, 25 + h * 0.7 + 1.2, 6), "cap")
    s.mirror_x((5, 22, 4.5), (6.2, 30, 5.7), "bark", rot=("z", 22.5, [5.6, 22, 5.1]))   # branch antlers
    s.mirror_x((3, 26, 4.5), (4, 31, 5.5), "bark", rot=("z", 45, [3.5, 26, 5]))
    s.box((6, 13, 6.8), (10, 23, 7.8), "leaf")                      # moss down her back
    s.write()


def abyssal_leviathan():
    """The Chained Leviathan: a sea serpent rising in an S out of its coil, jaws wide, its collar's chain broken."""
    s = Painted("abyssal_leviathan", [("scale", (26, 72, 92), "scale"), ("belly", (104, 150, 140), "plate", (170, 230, 220)),
                                      ("eye", (90, 255, 240), "eye"), ("fin", (62, 56, 128), "membrane", (110, 220, 255)),
                                      ("tooth", (226, 228, 214), "bone"), ("deep", (12, 30, 44), "void", (80, 220, 230)),
                                      ("chain", (58, 60, 66), "plate", (120, 120, 126))], 44)
    for a, b in (((0, 0, 12), (16, 4, 16)), ((0, 0, 24), (16, 4, 28)), ((0, 0, 16), (4, 4, 24)), ((12, 0, 16), (16, 4, 24))):
        s.box(a, b, "scale", top="deep")                            # the lower ring of the coil
    for a, b in (((3.5, 3.5, 13.5), (14.5, 7, 17)), ((3.5, 3.5, 21), (14.5, 7, 24.5)), ((3.5, 3.5, 17), (7, 7, 21)),
                 ((11, 3.5, 17), (14.5, 7, 21))):
        s.box(a, b, "scale", top="deep")                            # a smaller ring on top
    s.box((7, 4, 24), (11, 13, 27), "scale", rot=("x", -22.5, [9, 4, 25.5]))   # tail rising behind
    s.box((8, 12, 22), (10, 21, 24.5), "fin", rot=("x", -22.5, [9, 12, 23]))   # tail fan
    s.box((4.5, 4, 10), (11.5, 10, 16.5), "scale", rot=("x", 22.5, [8, 4, 13]))   # the S: lower body leaning back
    s.box((5, 9, 9.5), (11, 15, 15), "scale")                       # upright
    s.box((5.5, 9, 9.1), (10.5, 15, 9.6), "belly")
    s.box((5.6, 14, 6), (10.4, 20, 11.5), "scale", rot=("x", -22.5, [8, 14, 9]))   # narrower neck, leaning forward
    s.box((6.1, 14, 5.6), (9.9, 20, 6.1), "belly", rot=("x", -22.5, [8, 14, 9]))
    s.box((5, 15.5, 5), (11, 17, 12), "chain", rot=("x", -22.5, [8, 14, 9]))   # the collar
    for k in range(4):                                              # its broken chain down to the coil
        y, z = 14 - k * 2.4, 4.6 + k * 1.1
        if k % 2 == 0:
            s.box((6.5, y - 2.4, z), (8.5, y, z + 0.8), "chain")
        else:
            s.box((7.1, y - 2.4, z - 0.6), (7.9, y, z + 1.4), "chain")
    s.box((12.5, 7, 14), (14.5, 7.8, 16.4), "chain")                # two loose links on the coil
    s.box((1, 4, 18), (3.4, 4.8, 20), "chain")
    s.box((4, 18, -1), (12, 23, 7), "scale", top="deep")            # head, wider than the neck
    s.box((3.6, 22.2, -1.6), (12.4, 24, 3.5), "deep")               # brow overhanging the eyes
    jaw = ("x", -22.5, [8, 17, 6])
    s.box((4.5, 14.5, -1.5), (11.5, 17, 6), "belly", rot=jaw)       # lower jaw, gaping
    for i in range(5):                                              # teeth, upper and lower
        x = 4.5 + i * 1.5
        s.box((x, 16.6, -0.8), (x + 0.8, 18.4, 0), "tooth")
        s.box((x + 0.4, 16.2, -0.6), (x + 1.1, 17.6, 0.2), "tooth", rot=jaw)
    s.mirror_x((4.6, 15.5, -1.6), (5.4, 17.9, -0.8), "tooth")       # outer fangs
    s.box((5, 17.2, -0.7), (11, 18, 4), "deep")                     # the throat
    s.mirror_x((4.2, 20.4, -1.3), (6, 21.6, -0.9), "eye")           # eyes looking forward
    for k in range(3):                                              # three swept fins a side
        s.mirror_x((3.4, 19 + k * 1.5, 5), (4, 20 + k * 1.5, 10), "fin", rot=("x", 45, [3.7, 19.5 + k * 1.5, 5]))
    s.box((7.4, 21, 2), (8.6, 28, 9), "fin", rot=("x", 22.5, [8, 21, 5]))   # crest
    for k in range(3):                                              # dorsal sail down the neck and body
        s.box((7.4, 14 - k * 3, 10 + k * 2), (8.6, 20 - k * 3, 14 + k * 2), "fin", rot=("x", 22.5, [8, 14 - k * 3, 12 + k * 2]))
    s.write()


def crypt_lord():
    """The Judge of Lost Souls: a lich floating in a deep hood, the scales of judgement held out on one arm."""
    s = Painted("crypt_lord", [("bone", (190, 182, 160), "bone"), ("dark", (110, 100, 86), "bone"),
                               ("robe", (58, 16, 30), "cloth"), ("gold", (150, 118, 50), "gold"),
                               ("eye", (120, 255, 150), "eye"), ("void", (16, 14, 22), "void", (120, 255, 150)),
                               ("hood", (36, 28, 40), "cloth")], 55)
    s.box((5.4, 5, 6), (10.6, 8, 10.6), "robe")                     # robe narrowing to nothing: he floats
    for i, end in enumerate((2, 3.5, 1.5, 4, 2.5)):
        s.box((5.4 + i * 1.04, end, 6.2), (6.3 + i * 1.04, 5, 10.4), "robe")
    s.box((4.6, 8, 5.6), (11.4, 12.5, 10.6), "robe")
    s.box((7.4, 5, 5.3), (8.6, 12.5, 5.7), "gold")                  # gilded stripe
    s.box((5, 12.5, 6), (11, 20, 10.4), "robe")                     # torso
    for i in range(4):                                              # ribs through the open robe
        y = 13.2 + i * 1.6
        s.box((6, y, 5.6), (10, y + 0.9, 6.1), "bone")
    s.box((7.5, 13, 5.5), (8.5, 19.6, 6.0), "dark")                 # sternum
    s.mirror_x((5.2, 12.5, 5.5), (6, 20, 6.2), "gold")              # robe edges
    s.box((7, 17.6, 5.2), (9, 19.6, 5.7), "eye")                    # medallion eye
    s.box((2.4, 18.5, 5.2), (13.6, 21, 11), "hood")                 # wide mantle: he tapers downward
    s.box((11.6, 12, 5.6), (14.8, 20, 10), "robe")                  # right sleeve, hanging
    s.box((11.2, 10.6, 5.2), (15.2, 12.8, 10.4), "gold")
    s.box((13.2, 9, 6.6), (15.2, 10.6, 8.6), "bone")                # the hand on the sceptre
    s.box((-1.4, 16, 5.8), (4.4, 19, 9.8), "robe")                  # left sleeve, held out
    s.box((-1.8, 15.6, 5.4), (-0.6, 19.4, 10.2), "gold")
    s.box((-3, 16.5, 6.6), (-1.4, 18, 8.6), "bone")                 # the hand holding the scales
    s.box((6.4, 21, 6.4), (9.6, 26.6, 9.8), "bone", top="dark")     # skull, deep in the hood
    s.box((6.2, 25, 6.0), (9.8, 25.8, 6.7), "dark")                 # brow
    s.mirror_x((6.5, 23, 6.1), (7.9, 25, 6.5), "void")              # wide sockets, with the eyes inside
    s.mirror_x((6.8, 23.6, 6.0), (7.6, 24.4, 6.3), "eye")
    s.box((6.8, 21.4, 6.2), (9.2, 22.2, 6.5), "void")               # jaw gap
    s.box((4.6, 20.5, 7.4), (11.4, 28.5, 11.8), "hood")             # deep hood around the skull
    s.mirror_x((4.6, 20.5, 4.6), (5.6, 28, 7.4), "hood")
    s.box((4.6, 27.2, 4.6), (11.4, 28.5, 7.4), "hood")
    s.box((5.4, 28.5, 6), (10.6, 29.6, 10.6), "hood")
    s.box((6, 24, 11.8), (10, 27.5, 13.8), "hood", rot=("x", 22.5, [8, 27.5, 11.8]))    # drooping hood tip
    for i, h in enumerate((2.2, 3.4, 2.2, 3.4, 2.2)):               # crown through the hood
        s.box((5.6 + i * 1.0, 28.2, 5.0), (6.3 + i * 1.0, 28.2 + h, 5.8), "gold")
    s.box((5.2, 27.6, 4.3), (10.8, 28.4, 5.8), "gold")
    s.box((4.6, 5, 10.6), (11.4, 22, 11.4), "robe")                 # the robe's back
    s.box((7, 6, 11.4), (9, 20, 11.8), "gold")                      # stole
    s.box((14, 8, 7.4), (15.2, 25, 8.6), "dark")                    # sceptre
    s.box((13.8, 25, 7.2), (15.4, 26, 8.8), "gold")
    s.box((13.2, 26, 6.6), (16, 28.8, 9.4), "eye")                  # the orb: the glowing weak spot
    for dx, dz in ((-0.5, -0.5), (2.7, -0.5), (-0.5, 2.7), (2.7, 2.7)):   # golden cage around it
        s.box((13.2 + dx, 25.6, 6.6 + dz), (13.6 + dx, 29.6, 7.0 + dz), "gold")
    s.box((13, 29.4, 6.4), (16.2, 30, 9.6), "gold")
    s.box((-2.6, 15.6, 7.6), (-1.8, 16.5, 8.4), "dark")             # the scales hang from his hand
    s.box((-7, 14.6, 7.5), (2, 15.6, 8.5), "gold")                  # beam
    s.box((-6, 11.8, 7.8), (-5.4, 14.6, 8.4), "dark")               # chains
    s.box((0.4, 13.2, 7.8), (1, 14.6, 8.4), "dark")
    s.box((-7.2, 11, 6.5), (-4.2, 11.8, 9.5), "gold")               # pans at two heights: one soul outweighs
    s.box((-0.8, 12.4, 6.5), (2.2, 13.2, 9.5), "gold")
    s.box((-6.4, 11.8, 7.2), (-5, 12.8, 8.6), "eye")                # the soul being weighed
    s.write()


def voidwyrm():
    """The Echo of the Rift: a dragon on a rising neck, crowned with swept horns, jointed wings, a tail tipped with bone."""
    s = Painted("voidwyrm", [("scale", (54, 30, 92), "scale"), ("belly", (122, 92, 168), "plate", (200, 150, 255)),
                             ("eye", (255, 120, 255), "eye"), ("wing", (64, 32, 112), "membrane", (190, 100, 255)),
                             ("bone", (220, 210, 236), "bone"), ("void", (12, 6, 24), "void", (210, 150, 255)),
                             ("vein", (190, 92, 255), "glow"), ("horn", (150, 140, 170), "bone")], 66)
    s.box((5, 9, 6), (11, 15, 20), "scale", top="void")             # body
    s.box((5.4, 7.6, 6.4), (10.6, 9, 19.6), "belly")
    s.box((6.4, 10, 5.5), (9.6, 13.4, 6.1), "vein")                 # glowing chest core
    for a, b in (((6, 11, 2), (10, 16.5, 7)), ((6.2, 14.5, -1), (9.8, 19.5, 3.5)), ((6.4, 18, -3.5), (9.6, 23, 0.5))):
        s.box(a, b, "scale", top="void")                            # the neck rising high
    for y, z in ((16.5, 3.5), (19.5, 0.2), (23, -2.6)):             # spikes along the neck
        s.box((7.5, y, z), (8.5, y + 2.2, z + 1.1), "bone", rot=("x", 22.5, [8, y, z + 0.5]))
    s.box((5.4, 21, -9), (10.6, 25.6, -2.5), "scale", top="void")   # head
    s.box((5.9, 21.6, -12.5), (10.1, 24.6, -9), "scale")            # snout
    jaw = ("x", -22.5, [8, 21, -4])
    s.box((6.1, 19.4, -12.2), (9.9, 21, -4), "bone", rot=jaw)       # lower jaw, open
    for k in range(4):                                              # teeth
        s.box((6.3 + k * 1.0, 20.6, -12.2), (6.8 + k * 1.0, 21.8, -11.7), "bone")
    s.box((6.4, 20.6, -11.6), (9.6, 21.4, -5), "vein")              # the glow in its throat
    s.mirror_x((5.2, 23.6, -8.6), (6.8, 24.8, -7.4), "eye")         # burning eyes
    s.box((5.2, 25.2, -9.2), (10.8, 26.2, -4), "void")              # brow plate
    s.mirror_x((5.4, 25.6, -6), (6.3, 31, -5.1), "horn", rot=("x", 45, [5.85, 25.6, -5.55]))   # two long horns
    s.mirror_x((6.6, 25.6, -4.5), (7.5, 28.6, -3.6), "horn", rot=("x", 45, [7.05, 25.6, -4.05]))   # and two short
    s.mirror_x((4.6, 22, -5), (5.4, 25, -3), "bone", rot=("z", 45, [5, 23.5, -4]))   # cheek spikes
    # jointed wings: the arm rises to a wrist, three panels droop from it with bone fingers
    s.mirror_x((11, 13.5, 8), (20, 15, 9.6), "bone", rot=("z", 45, [11, 14.2, 8.8]))   # arm to the wrist
    s.mirror_x((11, 13.9, 9.6), (20, 14.5, 16), "wing", rot=("z", 45, [11, 14.2, 12.8]))   # inner membrane
    s.mirror_x((17, 21, 7.4), (18.6, 23.5, 8.6), "bone", rot=("z", -22.5, [17.8, 21, 8]))   # wrist claw
    for (z0, z1, length) in ((7, 12, 14), (12, 17, 11), (17, 22, 8)):
        s.mirror_x((17, 20, z0), (17 + length, 20.6, z1), "wing", rot=("z", -22.5, [17, 20.3, (z0 + z1) / 2]))
        s.mirror_x((17, 20.6, z0 + 1), (17.5 + length, 21.3, z0 + 1.8), "bone", rot=("z", -22.5, [17, 20.9, z0 + 1.4]))
    s.mirror_x((18, 20.7, 9), (29, 21.1, 9.8), "vein", rot=("z", -22.5, [17, 20.9, 9.4]))
    # tail tapering and dipping, tipped with a bone arrowhead
    for (w, z0, z1, dy) in ((2.6, 20, 23.5, 0), (2.2, 23.5, 26, -0.8), (1.7, 26, 28, -2)):
        s.box((8 - w, 10.5 - w * 0.6 + dy, z0), (8 + w, 10.5 + w * 0.9 + dy, z1), "scale")
    s.box((7.6, 8, 27.5), (8.4, 10, 32), "bone", rot=("x", 45, [8, 9, 28]))
    s.box((7.6, 8, 27.5), (8.4, 10, 32), "bone", rot=("x", -45, [8, 9, 28]))
    s.box((7.8, 8.6, 28), (8.2, 9.4, 31.6), "vein")
    for k, z in enumerate((8, 11.5, 15, 18.5, 22)):                 # dorsal spikes
        y = 15 if z < 20 else 12.5
        s.box((7.4, y, z), (8.6, y + 4.6 - k * 0.6, z + 1.6), "bone", rot=("x", 22.5, [8, y, z + 0.8]))
    s.mirror_x((5.1, 4, 8.5), (6.9, 9.4, 12), "scale")              # front legs
    s.mirror_x((4.7, 2.6, 7.4), (7.3, 4, 12), "bone")
    s.mirror_x((4.2, 6, 14.5), (7.4, 12, 20), "scale", rot=("x", 22.5, [5.8, 12, 17.25]))   # rear haunches
    s.mirror_x((4.8, 2.5, 17), (6.8, 7.5, 19.5), "scale", rot=("x", 22.5, [5.8, 7.5, 18.25]))
    for k in range(3):                                              # three toes
        s.mirror_x((4.4 + k * 0.9, 1.6, 15.2), (5.2 + k * 0.9, 2.6, 17.4), "bone")
    s.write()


BOSSES = {"magma_warden": magma_warden, "frost_revenant": frost_revenant, "sylvan_matriarch": sylvan_matriarch,
          "abyssal_leviathan": abyssal_leviathan, "crypt_lord": crypt_lord, "voidwyrm": voidwyrm}


def main():
    for build in BOSSES.values():
        build()
    print(f"wrote {len(BOSSES)} boss skins")


if __name__ == "__main__":
    main()
