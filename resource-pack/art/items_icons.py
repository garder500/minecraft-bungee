#!/usr/bin/env python3
"""Draws the icons of the Skyblock `items` feature (stdlib only, deterministic): the 16x16 weapons and tools below and
the painted 32x32 class armour (armor32.py), accessories (trinkets32.py), pets (creatures32.py) and misc items
(goods32.py), all drawn with paint32.py.

    python3 resource-pack/art/items_icons.py            # writes textures, models and item definitions
    python3 resource-pack/art/items_icons.py --sheet out.png   # also writes an 8x contact sheet to look at

Outputs (all paths contain the feature name, see docs/skyblock-design.md section 8):
  pack/assets/vampyuyu/textures/item/items/<id>.png
  pack/assets/vampyuyu/models/item/items/<id>.json
  pack/assets/vampyuyu/items/items/<id>.json          -> item_model "vampyuyu:items/<id>"
Style of the 16 px sprites: the Skyblock lore sprites (docs/skyblock-notes/lore.md, lore_sprites.py): one Realm palette per item
(shadow, base, light, highlight, glow), light from the top left, no anti-aliasing, the family silhouette stays and the
glow pixels (one to four, see MOTIFS) mark the Realm motif.
The id list must match survival-plugin ItemCatalog + the eight pet species (items.pet_<id> models).
"""
import json
import pathlib
import struct
import sys
import zlib

import armor32
import creatures32
import goods32
import trinkets32
from imported import imported

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
W = 16
OUTLINE = (0x14, 0x0f, 0x18, 255)  # the one outline colour of the lore sprites (#140F18)


def rgb(text):
    text = text.lstrip("#")
    return (int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16), 255)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def ramp(base):
    """(light, base, dark) of a colour."""
    c = rgb(base) if isinstance(base, str) else base
    return shade(c, 1.28), c, shade(c, 0.68)


class Img:
    def __init__(self):
        self.p = [[None] * W for _ in range(W)]

    def set(self, x, y, c):
        if 0 <= x < W and 0 <= y < W:
            self.p[y][x] = c

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def disc(self, cx, cy, r, c):
        for yy in range(W):
            for xx in range(W):
                if (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r:
                    self.set(xx, yy, c)

    def art(self, rows, palette, ox=0, oy=0):
        """ASCII sprite: '.' is transparent, every other character is looked up in the palette."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "." and ch != " ":
                    self.set(x + ox, y + oy, palette[ch])

    def outline(self):
        out = [row[:] for row in self.p]
        for y in range(W):
            for x in range(W):
                if self.p[y][x] is None:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < W and 0 <= ny < W and self.p[ny][nx] is not None:
                            out[y][x] = OUTLINE
                            break
        self.p = out
        return self

    def png(self):
        raw = b""
        for row in self.p:
            raw += b"\x00" + b"".join(bytes(c) if c else b"\x00\x00\x00\x00" for c in row)

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, W, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# --------------------------------------------------------------------------------------------- palettes
RARITY = {
    "common": "#e8e8ee", "uncommon": "#55e06a", "rare": "#5577ff", "epic": "#a74bd6",
    "legendary": "#ffb52b", "mythic": "#ff6bd6",
}
METAL = {
    "wood": "#9b6b3a", "stone": "#8c8f96", "iron": "#d3d6dc", "gold": "#ffd24a", "diamond": "#4de3e3",
    "netherite": "#4a4350", "void": "#7b3fd0", "ice": "#9fe8ff", "ember": "#ff7a2a", "wither": "#2a2a35",
}


# Realm palettes: shadow, base, light, highlight, glow (docs/skyblock-notes/lore.md, section 2).
REALMS = {
    "ashes": ("#120a16", "#2a1a2e", "#5a2a2a", "#c8461e", "#ff8a2a"),
    "grove": ("#2c3a1e", "#4f6b2a", "#8fb84a", "#d6f08a", "#f1e7c9"),
    "heights": ("#1f3557", "#3b6fa8", "#7fc8e8", "#e6f7ff", "#b6a8ff"),
    "citadel": ("#5a3a12", "#a8742a", "#f2c14e", "#fff1b0", "#3fb6a8"),
    "marsh": ("#2a2a1a", "#4e5a22", "#8a9a2e", "#c9d36a", "#b04aa0"),
    "storm": ("#1e2440", "#3a4a8a", "#7a8ee0", "#f0f4ff", "#ffe94a"),
    "depths": ("#0a1a2a", "#12405a", "#2a8a9a", "#bff0f0", "#ff6ab0"),
    "necro": ("#241f3a", "#4a4570", "#9c95c8", "#e8e4ff", "#6affd8"),
    "astral": ("#0e0a1a", "#3a1a6a", "#8a4aff", "#e0c8ff", "#ff4a8a"),
    "flux": ("#4a2a12", "#7a4a1e", "#c98442", "#f2c14e", "#4af0ff"),
}
# The glow pixels that mark the Realm motif, in drawing order; an item takes the first N of them.
MOTIFS = {
    "ashes": [(0, 0), (1, 1), (-1, -1), (2, 1)],            # a jagged crack, brightest where it is widest
    "grove": [(0, 0), (2, -1), (-2, 1), (1, 2)],            # drifting spores
    "heights": [(0, 0), (1, 0), (-1, 0), (0, 1)],           # a single unblinking eye
    "citadel": [(0, -1), (0, 1), (0, 0), (1, 0)],           # hourglass, turquoise inlay
    "marsh": [(0, 0), (2, -1), (-1, 2), (2, 2)],            # bubbles in a bruise-purple core
    "storm": [(0, -1), (-1, 0), (0, 1), (-1, 2)],           # a two-step zigzag bolt
    "depths": [(0, 0), (1, 0), (0, 1), (1, 1)],             # pearl sheen
    "necro": [(0, 0), (0, -1), (0, 1), (1, 0)],             # a soul flame
    "astral": [(0, 0), (0, 1), (0, -1), (1, 0)],            # a rift that looks back
    "flux": [(0, 0), (1, 0), (2, 0), (3, 0)],               # a Flux window
}
WHITE = (255, 255, 255, 255)


def realm_colors(realm):
    s, b, l, h, e = (rgb(c) for c in REALMS[realm])
    return s, b, l, h, e


def pal(realm):
    """Letters of the shared item shapes: a body, b lit edge, c shaded edge, d/e/f grip, g/h gem, i/j/k guard."""
    s, b, l, h, e = realm_colors(realm)
    return {"a": l, "b": h, "c": b, "d": b, "e": l, "f": s, "g": e, "h": h, "i": b, "j": l, "k": s, "w": h}


def stamp(img, realm, n, anchor):
    """Paints the first n glow pixels of the Realm motif around the anchor (only on pixels the sprite already has)."""
    e = rgb(REALMS[realm][4])
    ax, ay = nearest(img, anchor)
    for dx, dy in MOTIFS[realm][:n]:
        if 0 <= ax + dx < W and 0 <= ay + dy < W and img.p[ay + dy][ax + dx] is not None:
            img.set(ax + dx, ay + dy, e)


def nearest(img, point):
    x0, y0 = point
    best = min(((x - x0) ** 2 + (y - y0) ** 2, y, x) for y in range(W) for x in range(W) if img.p[y][x] is not None)
    return best[2], best[1]


def find(rows, letters):
    return [(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in letters]


def centre(points):
    return (round(sum(p[0] for p in points) / len(points)), round(sum(p[1] for p in points) / len(points)))


def draw(rows, palette):
    img = Img()
    img.art(rows, palette)
    return img


def realm_item(rows, realm, n=1, anchor=None):
    """Item of a shape in a Realm palette. A small gem ('g', up to 5 pixels) is the glow itself; a big one is lit stone
    and the glow pixels of the motif sit in its middle. Without a gem the motif goes on the head of the tool."""
    palette = pal(realm)
    gems = find(rows, "g")
    if len(gems) > 5:
        palette["g"] = rgb(REALMS[realm][1])
        palette["h"] = rgb(REALMS[realm][2])
        point = anchor or centre(gems)
    elif gems:
        point = None
    else:
        body = find(rows, "a") or find(rows, "bdw")
        point = anchor or centre(body)
    img = draw(rows, palette)
    if point is not None:
        stamp(img, realm, n, point)
    return img.outline()


# --------------------------------------------------------------------------------------------- sprites
SWORD = [
    "..............bb",
    ".............baa",
    "............baac",
    "...........baac.",
    "..........baac..",
    ".........baac...",
    "....i...baac....",
    ".....i.baac.....",
    "......ijac......",
    ".......iik......",
    "......dik.......",
    ".....dd.i.......",
    "....dd..........",
    "...ee...........",
    "..gg............",
    "................",
]
AXE = [
    "................",
    "........bbbb....",
    ".......baaaaac..",
    "......baaaaaac..",
    "......baaac.dc..",
    ".......bac.d....",
    "..........d.....",
    ".........d......",
    "........d.......",
    ".......d........",
    "......d.........",
    ".....d..........",
    "....dd..........",
    "...ee...........",
    "................",
    "................",
]
PICKAXE = [
    "................",
    "....bbbbbbb.....",
    "..bbaaaaaaabb...",
    ".baaac...caaab..",
    ".baac.....caab..",
    "......d.........",
    ".....d..........",
    "....d...........",
    "...d............",
    "..d.............",
    ".dd.............",
    ".e..............",
    "................",
    "................",
    "................",
    "................",
]
SHOVEL = [
    ".............bb.",
    "............baac",
    "...........baaac",
    "..........ba.ac.",
    ".........d.c....",
    "........d.......",
    ".......d........",
    "......d.........",
    ".....d..........",
    "....bb..........",
    "...baac.........",
    "..baaac.........",
    "..baaac.........",
    "...bac..........",
    "................",
    "................",
]
HOE = [
    "................",
    "......bbbbb.....",
    ".....baaaaac....",
    "......caaaac....",
    ".........d......",
    "........d.......",
    ".......d........",
    "......d.........",
    ".....d..........",
    "....d...........",
    "...d............",
    "..dd............",
    ".ee.............",
    "................",
    "................",
    "................",
]
DRILL = [
    "................",
    "..........bbb...",
    "......bbbbaaac..",
    ".....baaaaaaac..",
    "....baiiiiiaac..",
    "...baaaaaaaaac..",
    "...bgggaaaaac...",
    "..bbgggaaacc....",
    "bbaaaaacccc.....",
    "baaac.dd........",
    ".bac..dd........",
    "......dd........",
    ".....ddd........",
    ".....dd.........",
    "................",
    "................",
]
ROD = [
    "............bbbb",
    "...........bd..b",
    "..........bd...b",
    ".........bd....b",
    "........bd.....b",
    ".......bd......g",
    "......bd........",
    ".....bd.........",
    "....bd..........",
    "...bd...........",
    "..dd............",
    ".dd.............",
    ".ii.............",
    "................",
    "................",
    "................",
]
BOW = [
    "................",
    "......bbbbb.....",
    ".....bdd.....w..",
    "....bd......w...",
    "....bd.....w....",
    "...bd.....w.....",
    "...bd....w......",
    "...bd....w......",
    "...bd.....w.....",
    "....bd.....w....",
    "....bd......w...",
    ".....bdd.....w..",
    "......bbbbb.....",
    "................",
    "................",
    "................",
]
WAND = [
    "..........bbg...",
    ".........bgghg..",
    ".........ggghg..",
    "..........ggg...",
    ".........d......",
    "........d.......",
    ".......d........",
    "......d.........",
    ".....d..........",
    "....d...........",
    "...d............",
    "..ee............",
    "................",
    "................",
    "................",
    "................",
]
STAFF = [
    "......g.g.......",
    ".....ggggg......",
    "....gghhhgg.....",
    ".....ggggg......",
    "......bdb.......",
    ".......d........",
    ".......d........",
    ".......d........",
    ".......d........",
    ".......d........",
    ".......d........",
    ".......d........",
    ".......e........",
    "................",
    "................",
    "................",
]
SCYTHE = [
    "....bbbbbb......",
    "..bbaaaaaaab....",
    ".baaac...caaab..",
    ".bac.......cab..",
    "............d...",
    "...........d....",
    "..........d.....",
    ".........d......",
    "........d.......",
    ".......d........",
    "......d.........",
    ".....d..........",
    "....ee..........",
    "................",
    "................",
    "................",
]
# --------------------------------------------------------------------------------------------- catalog
# Armor sets, accessory families and pets each take the palette and motif of one Realm.
# (shape, realm, glow pixels) of the 16 px weapons and tools; the motif goes on the gem, else on the head of the tool.
ITEMS = {
    "rookie_sword": (SWORD, "ashes", 1), "warriors_blade": (SWORD, "citadel", 1), "leaping_sword": (SWORD, "storm", 2),
    "frozen_scythe": (SCYTHE, "heights", 2), "void_blade": (SWORD, "astral", 2), "wither_blade": (SWORD, "necro", 2),
    "ember_rod": (WAND, "ashes", 2), "healing_wand": (WAND, "grove", 2),
    "staff_of_the_volcano": (STAFF, "ashes", 3), "hunters_shortbow": (BOW, "marsh", 1),
    "voidpiercer_bow": (BOW, "astral", 2),
    "treecapitator": (AXE, "grove", 2), "jungle_axe": (AXE, "marsh", 1), "diggers_shovel": (SHOVEL, "citadel", 1),
    "miners_pickaxe": (PICKAXE, "ashes", 2), "miners_drill": (DRILL, "flux", 2), "titan_drill": (DRILL, "flux", 4),
    "farmers_hoe": (HOE, "grove", 1), "harvest_hoe": (HOE, "citadel", 2), "rod_of_legends": (ROD, "depths", 3),
}


def build():
    """The 16 px weapons and tools (Img) and the painted 32 px icons (paint32.Pic): class armour, accessories, pets, misc."""
    icons = {}
    for name, (rows, realm, n) in ITEMS.items():
        icons[name] = realm_item(rows, realm, n)
    for module in (armor32, trinkets32, creatures32):
        icons.update(module.all_icons())
    icons.update({name: draw() for name, draw in goods32.MISC.items()})
    return icons


def write(icons):
    tex = PACK / "textures" / "item" / "items"
    mdl = PACK / "models" / "item" / "items"
    itm = PACK / "items" / "items"
    for folder in (tex, mdl, itm):
        folder.mkdir(parents=True, exist_ok=True)
    for name, img in sorted(icons.items()):
        if imported(f"items/{name}"):  # the sprite comes from a concept sheet (import_textures.py)
            continue
        (tex / f"{name}.png").write_bytes(img.png())
        (mdl / f"{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/items/{name}"}}, indent=2) + "\n")
        (itm / f"{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/items/{name}"}}, indent=2) + "\n")


def sheet(icons, path, scale=8, columns=16):
    names = sorted(icons)
    rows = (len(names) + columns - 1) // columns
    scale = scale // 2          # one cell holds a 32 px icon; a 16 px one is drawn twice as large
    cell = 32 * scale + 4
    width, height = columns * cell, rows * cell
    bg = (0x3a, 0x3a, 0x44, 255)
    pixels = [[bg] * width for _ in range(height)]
    for i, name in enumerate(names):
        ox, oy = (i % columns) * cell + 2, (i // columns) * cell + 2
        grid = icons[name].p
        k = scale * 32 // len(grid)
        for y, row in enumerate(grid):
            for x, c in enumerate(row):
                if c:
                    for sy in range(k):
                        for sx in range(k):
                            pixels[oy + y * k + sy][ox + x * k + sx] = c
    raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in pixels)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    pathlib.Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                                   + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    icons = build()
    for name, img in icons.items():
        grid = img.p
        assert len(grid) in (W, 32) and all(len(row) == len(grid) for row in grid), name
    write(icons)
    if "--sheet" in sys.argv:
        sheet(icons, sys.argv[sys.argv.index("--sheet") + 1])
    print(f"{len(icons)} item icons written")


if __name__ == "__main__":
    main()
