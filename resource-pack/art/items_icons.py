#!/usr/bin/env python3
"""Draws the 16x16 pixel-art icons of the Skyblock `items` feature (stdlib only, deterministic).

    python3 resource-pack/art/items_icons.py            # writes textures, models and item definitions
    python3 resource-pack/art/items_icons.py --sheet out.png   # also writes an 8x contact sheet to look at

Outputs (all paths contain the feature name, see docs/skyblock-design.md section 8):
  pack/assets/vampyuyu/textures/item/items/<id>.png
  pack/assets/vampyuyu/models/item/items/<id>.json
  pack/assets/vampyuyu/items/items/<id>.json          -> item_model "vampyuyu:items/<id>"
The id list must match survival-plugin ItemCatalog + the eight pet species (items.pet_<id> models).
"""
import json
import pathlib
import struct
import sys
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
W = 16
OUTLINE = (0x15, 0x11, 0x1f, 255)


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


def pal(blade, grip="#7a4b24", gem="#ff5a5a", guard="#c9a13b"):
    b = ramp(blade)
    g = ramp(grip)
    gd = ramp(guard)
    ge = ramp(gem)
    return {"a": b[1], "b": b[0], "c": b[2], "d": g[1], "e": g[0], "f": g[2], "g": ge[1], "h": ge[0], "i": gd[1],
            "j": gd[0], "k": gd[2], "w": (255, 255, 255, 255)}


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
HOOK = [
    "......bbbb......",
    ".....ba..ac.....",
    ".....b....c.....",
    ".....ba..ac.....",
    "......bacc......",
    ".......d........",
    ".......d........",
    ".......d........",
    ".......d........",
    ".......d........",
    "......idi.......",
    "......iii.......",
    ".......g........",
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
CRYSTAL = [
    "................",
    ".......bb.......",
    "......bhhb......",
    ".....bhgggb.....",
    "....bhgggggc....",
    "....bgggggggc...",
    "....bgggggggc...",
    "....bgggggggc...",
    ".....bgggggc....",
    ".....bgggggc....",
    "......bgggc.....",
    ".......bgc......",
    "........c.......",
    "................",
    "................",
    "................",
]

HELMET = [
    "................",
    "................",
    "....bbbbbbbb....",
    "...baaaaaaaac...",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..bahhaaaahhac..",
    "..baaaaaaaaaac..",
    "..baaa....aaac..",
    "..baac....caac..",
    "..bac......cac..",
    "................",
    "................",
    "................",
    "................",
    "................",
]
CHEST = [
    "................",
    "..bb........bb..",
    ".baac.bbbb.baac.",
    ".baaabaaaacbaac.",
    ".baaaaaaaaaaaac.",
    "..baaaaaaaaaac..",
    "..baaahhhhaaac..",
    "..baaahggghaac..",
    "..baaahhhhaaac..",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..bcaaaaaaaacc..",
    "...cccccccccc...",
    "................",
    "................",
    "................",
]
LEGS = [
    "................",
    "...bbbbbbbbbb...",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..bahhaaaahhac..",
    "..baaaa..aaaac..",
    "..baaac..caaac..",
    "..baaac..caaac..",
    "..baaac..caaac..",
    "..baaac..caaac..",
    "..baacc..ccaac..",
    "..bcc......ccc..",
    "................",
    "................",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "..bbbb....bbbb..",
    "..baac....baac..",
    "..baac....baac..",
    "..baac....baac..",
    "..bahc....bahc..",
    ".baaac...baaac..",
    ".baaaac.baaaac..",
    ".bhhaacbbhhaac..",
    ".bcccccbcccccc..",
    "................",
    "................",
    "................",
    "................",
]

RING = [
    "................",
    "................",
    ".......gg.......",
    "......ghhg......",
    ".......gg.......",
    "......bbbb......",
    ".....baaaac.....",
    "....baa..aac....",
    "....ba....ac....",
    "....ba....ac....",
    "....baa..aac....",
    ".....baaaac.....",
    "......cccc......",
    "................",
    "................",
    "................",
]
CHARM = [
    "................",
    "........bb......",
    ".......b..b.....",
    ".......b..b.....",
    "......bbbbbb....",
    ".....baaaaaac...",
    "....baaggggaac..",
    "....baghhhgaac..",
    "....bagggggaac..",
    "....baaaggaaac..",
    ".....baaaaaac...",
    "......cccccc....",
    "................",
    "................",
    "................",
    "................",
]
TALISMAN = [
    "................",
    ".......bb.......",
    "......b..b......",
    "......bbbb......",
    ".....baaaac.....",
    "....baggggac....",
    "...baagggggac...",
    "...baghhhggac...",
    "...baagggggac...",
    "....baaggaac....",
    ".....baaaac.....",
    "......cccc......",
    "................",
    "................",
    "................",
    "................",
]
ARTIFACT = [
    "................",
    ".......ww.......",
    "......bwwb......",
    ".....baggab.....",
    "..bb.baggab.bb..",
    ".baab.bgga.baab.",
    ".baaabbgggbbaab.",
    "..baaaaggaaaab..",
    "...baaaggaaab...",
    "....baaaaaab....",
    ".....baaaaab....",
    "......baaab.....",
    ".......bab......",
    "........c.......",
    "................",
    "................",
]

BOOK = [
    "................",
    "...bbbbbbbbb....",
    "..baaaaaaaaac...",
    "..baaiiiiiaac...",
    "..baaaaaaaaac...",
    "..baaaghgaaac...",
    "..baaagggaaac...",
    "..baaaaaaaaac...",
    "..baaiiiiiaac...",
    "..baaaaaaaaac...",
    "..bddddddddc....",
    "..bwwwwwwwwc....",
    "...cccccccc.....",
    "................",
    "................",
    "................",
]
STAR = [
    "................",
    ".......bb.......",
    ".......bb.......",
    "......bhhb......",
    "..bbbbbhhbbbbb..",
    "...baaahhaaac...",
    "....baaaaaac....",
    ".....baaaac.....",
    "....baaaaaac....",
    "...baaac.baac...",
    "..baac.....baa..",
    "..bc.........c..",
    "................",
    "................",
    "................",
    "................",
]
POTATO_BOOK = BOOK
LOG = [
    "................",
    "................",
    "..bbbbbbbbbbbb..",
    ".baaaaaaaaaaaac.",
    ".baddddddddddac.",
    ".badeeeeeeeedac.",
    ".badeffffffedac.",
    ".badeffggffedac.",
    ".badeffffffedac.",
    ".badeeeeeeeedac.",
    ".baddddddddddac.",
    ".baaaaaaaaaaaac.",
    "..cccccccccccc..",
    "................",
    "................",
    "................",
]
BLOCK = [
    "................",
    "................",
    "..bbbbbbbbbbbb..",
    ".baaaaaaaaaaaac.",
    ".baaaaaaaaaaaac.",
    ".baagggaaaggaac.",
    ".baaghgaaaghaac.",
    ".baaagaaaaagaac.",
    ".baaaaaaaaaaaac.",
    ".baaaggaaaaaaac.",
    ".baaaghaaaaaaac.",
    ".baaaaaaaaaaaac.",
    "..cccccccccccc..",
    "................",
    "................",
    "................",
]
LUMP = [
    "................",
    "................",
    "................",
    "......bbbb......",
    "....bbaaaabb....",
    "...baaaaaaaac...",
    "..baaaghaaaaac..",
    "..baaaaaaaaaac..",
    "..baaaaaagaaac..",
    "...baaaaaaaac...",
    "....baaaaacc....",
    ".....cccccc.....",
    "................",
    "................",
    "................",
    "................",
]
BARREL = [
    "................",
    "....bbbbbbbb....",
    "...baaaaaaaac...",
    "..iiiiiiiiiiii..",
    "..baaaaaaaaaac..",
    "..baaahhhhaaac..",
    "..baaahggghaac..",
    "..baaahhhhaaac..",
    "..baaaaaaaaaac..",
    "..iiiiiiiiiiii..",
    "..baaaaaaaaaac..",
    "...baaaaaaaac...",
    "....cccccccc....",
    "................",
    "................",
    "................",
]
PEARL = [
    "................",
    "................",
    ".....bbbbbb.....",
    "...bbaaaaaabb...",
    "..baahhaaaaaac..",
    "..bahhaaaaaaac..",
    ".baahaaaaaggaac.",
    ".baaaaaaaggggac.",
    ".baaaaaaaaggaac.",
    "..baaaaaaaaaac..",
    "..bcaaaaaaacc...",
    "...bccaaacc.....",
    ".....cccc.......",
    "................",
    "................",
    "................",
]
SHARD = [
    "................",
    "...........b....",
    "..........bhb...",
    ".........bhgb...",
    "........bhggb...",
    ".......bhgggb...",
    "......bhgggc....",
    ".....bhgggc.....",
    "....bhgggc......",
    "...bhggcc.......",
    "..bhgcc.........",
    "..bgcc..........",
    "..ccc...........",
    "................",
    "................",
    "................",
]
EGG = [
    "................",
    "................",
    ".......bb.......",
    "......baac......",
    ".....baaaac.....",
    "....baagagac....",
    "....baaaaaac....",
    "...baagggaaac...",
    "...baaagaaaac...",
    "...baaaaaagac...",
    "....baaaaaac....",
    ".....bcccac.....",
    "......cccc......",
    "................",
    "................",
    "................",
]
BERRY = [
    "................",
    "......dd........",
    ".....d..d.......",
    "....bbbbbb......",
    "...baaaaaac.....",
    "..baahaaaaac....",
    "..baaaaagaac....",
    "..baaaaaaaac....",
    "...baaaaaac.....",
    "....bcccc.......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
BONE = [
    "................",
    "..........bb.bb.",
    ".........baaabac",
    "........baaaac.c",
    ".......baaac....",
    "......baaac.....",
    ".....baaac......",
    "....baaac.......",
    "...baaac........",
    ".bbaaac.........",
    "baaab...........",
    ".cc.c...........",
    "................",
    "................",
    "................",
    "................",
]
SCUTE = [
    "................",
    "................",
    "....bbbbbbbb....",
    "...baaaaaaaac...",
    "..baahhaahhaac..",
    "..baahhaahhaac..",
    "..baaaaaaaaaac..",
    "..baahhaahhaac..",
    "...baaaaaaaac...",
    "....bccccccc....",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
CLOVER = [
    "................",
    "....bb....bb....",
    "...baac..baac...",
    "...baaabbaaac...",
    "....baaaaaac....",
    "...bbaaaaaabb...",
    "..baaabaabaaac..",
    "..baaac..baaac..",
    "...bcc.d.bcc....",
    "........d.......",
    ".......d........",
    "......dd........",
    "................",
    "................",
    "................",
    "................",
]
BOTTLE = [
    "................",
    ".......ii.......",
    ".......dd.......",
    "......wddw......",
    ".......bb.......",
    "......baac......",
    ".....baaaac.....",
    "....baahaaac....",
    "....baaaaaac....",
    "....baaagaac....",
    "....baaaaaac....",
    ".....bccccc.....",
    "................",
    "................",
    "................",
    "................",
]
GEAR = [
    "................",
    ".......bb.......",
    "....bb.bb.bb....",
    "...baabaabaac...",
    "....baaaaaac....",
    "..bbaac..caabb..",
    "..baaac..caaac..",
    "..bbaac..caabb..",
    "....baaaaaac....",
    "...baabaabaac...",
    "....bc.bb.cc....",
    ".......cc.......",
    "................",
    "................",
    "................",
    "................",
]

# ---- pets (16x16 mini portraits)
PET_WOLF = [
    "................",
    "..bb........bb..",
    "..baab....baab..",
    "..baaabbbbaaab..",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..baakaaaakaac..",
    "...baaaaaaaac...",
    "...baaabbaaac...",
    "....baabbaac....",
    "....baadkaac....",
    ".....bacccc.....",
    "......cccc......",
    "................",
    "................",
    "................",
]
PET_RABBIT = [
    "..bb......bb....",
    ".baab....baab...",
    ".baab....baab...",
    ".baab....baab...",
    ".bdab....bdab...",
    "..bbbbbbbbbb....",
    "..baaaaaaaaac...",
    ".baakaaaakaaac..",
    ".baaaaaaaaaaac..",
    ".baaaaadaaaaac..",
    "..baaaaaaaaac...",
    "...bacccccac....",
    "....cccccccc....",
    "................",
    "................",
    "................",
]
PET_ENDERMAN = [
    "................",
    "...bbbbbbbbbb...",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..bgggaaaagggc..",
    "..bghgaaaaghgc..",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "...bcccccccac...",
    "....cccccccc....",
    "................",
    "................",
    "................",
    "................",
]
PET_PHOENIX = [
    "................",
    "...........bb...",
    "..bb......baab..",
    ".baab....baaac..",
    ".baaab..baaac...",
    "..baaabbaaac....",
    "...baaaaaaac.i..",
    "....baagaaaaii..",
    "...bbaaaaaaac...",
    "..baaabaaaac....",
    ".baaab.baac.....",
    ".baab...bac.....",
    "..bb....bb......",
    "................",
    "................",
    "................",
]
PET_DRAGON = [
    "................",
    ".bb..........bb.",
    ".baab......baab.",
    "..baaabbbbaaab..",
    "..baaaaaaaaaac..",
    "..baaaaaaaaaac..",
    "..bgggaaaagggc..",
    "..bghgaaaaghgc..",
    "...baaaaaaaaac..",
    "...baadaaadaac..",
    "....baaaaaaac...",
    ".....baddddac...",
    "......cccccc....",
    "................",
    "................",
    "................",
]
PET_TIGER = [
    "................",
    "..bb........bb..",
    "..baab....baab..",
    "..baaabbbbaaab..",
    "..baakaaaakaac..",
    "..bakaaaaaakac..",
    "..baakaaaakaac..",
    "...baaaaaaaaac..",
    "...baawwddwaac..",
    "....baaddddaac..",
    "....baadkkdaac..",
    ".....bacccccc...",
    "......cccc......",
    "................",
    "................",
    "................",
]
PET_ELEPHANT = [
    "................",
    "..bbb......bbb..",
    ".baaabbbbbbaaab.",
    ".baaaaaaaaaaaac.",
    ".baaaaaaaaaaaac.",
    ".baaakaaaakaaac.",
    "..baaaaaaaaaac..",
    "..bwwaaaaaawwc..",
    "...bwaaaaaawc...",
    "....baaaaaac....",
    ".....baaaac.....",
    ".....baaac......",
    "......bac.......",
    "......cc........",
    "................",
    "................",
]
PET_TURTLE = [
    "................",
    "................",
    "................",
    "....bbbbbbbb....",
    "...baaaaaaaac...",
    "..baahhaahhaac..",
    ".bbaahhaahhaabb.",
    "bdbaaaaaaaaaabdb",
    "bddbaahhhhaabddb",
    ".bddbaaaaaabddb.",
    "..bdb.cccc.bdb..",
    "...bb.b..b.bb...",
    "................",
    "................",
    "................",
    "................",
]


def draw(rows, palette):
    img = Img()
    img.art(rows, palette)
    return img.outline()


def metal_item(rows, metal, grip="#7a4b24", gem="#ff5a5a", guard="#c9a13b"):
    return draw(rows, pal(METAL.get(metal, metal), grip, gem, guard))


# --------------------------------------------------------------------------------------------- catalog
SETS = {
    "miner": ("#9aa0aa", "#ff9a3a"),
    "lumberjack": ("#8a5a2e", "#4fae4f"),
    "farmer": ("#e8c545", "#6bbf4a"),
    "hunter": ("#3f7a4a", "#e0453a"),
    "void_walker": ("#5c3a9c", "#d36bff"),
    "frostborne": ("#8fd8f5", "#ffffff"),
}
FAMILIES = {
    "speed": "#ffffff", "strength": "#ff5a3a", "crit": "#6a8aff", "vitality": "#ff4a6a", "guard": "#a8b0b8",
    "mind": "#39b8ff", "harvest": "#e8c545", "prospect": "#d8884a", "fortune": "#7be07b",
}
TIER_FRAME = ["#cfd3d9", "#55c868", "#4f6fe8", "#a74bd6"]
TIER_SHAPE = [TALISMAN, CHARM, RING, ARTIFACT]
PETS = {
    "wolf": (PET_WOLF, "#b8bcc6", "#ffffff", "#2a2a35"),
    "rabbit": (PET_RABBIT, "#e8d6b8", "#ff9aa8", "#2a2a35"),
    "enderman": (PET_ENDERMAN, "#1c1624", "#d36bff", "#d36bff"),
    "phoenix": (PET_PHOENIX, "#ff7a2a", "#ffd24a", "#ffd24a"),
    "ender_dragon": (PET_DRAGON, "#2a2236", "#d36bff", "#7b3fd0"),
    "tiger": (PET_TIGER, "#f2992e", "#ffffff", "#3a2a1a"),
    "elephant": (PET_ELEPHANT, "#9ea3ad", "#ffffff", "#2a2a35"),
    "turtle": (PET_TURTLE, "#4fae4f", "#9be07b", "#6a8a3a"),
}


def build():
    icons = {}
    # weapons
    icons["rookie_sword"] = metal_item(SWORD, "stone", gem="#cfd3d9")
    icons["warriors_blade"] = metal_item(SWORD, "iron", gem="#55c868")
    icons["leaping_sword"] = metal_item(SWORD, "gold", gem="#4de3e3")
    icons["frozen_scythe"] = metal_item(SCYTHE, "ice", gem="#ffffff")
    icons["void_blade"] = metal_item(SWORD, "void", grip="#2a1a40", gem="#d36bff", guard="#4a2a7a")
    icons["wither_blade"] = metal_item(SWORD, "wither", grip="#1a1a22", gem="#ff5a3a", guard="#7a1a1a")
    icons["ember_rod"] = metal_item(WAND, "ember", grip="#5a3a1a", gem="#ffb02a")
    icons["healing_wand"] = metal_item(WAND, "#e8f0ff", grip="#d3a8ff", gem="#7be07b")
    icons["overflux_capacitor"] = metal_item(CRYSTAL, "#2aa5c8", gem="#39e8ff")
    icons["staff_of_the_volcano"] = metal_item(STAFF, "ember", grip="#3a2a20", gem="#ff7a2a")
    icons["hunters_shortbow"] = metal_item(BOW, "wood", grip="#9b6b3a", gem="#cfd3d9")
    icons["voidpiercer_bow"] = metal_item(BOW, "void", grip="#4a2a7a", gem="#d36bff")
    # tools
    icons["treecapitator"] = metal_item(AXE, "gold", grip="#6a4020")
    icons["jungle_axe"] = metal_item(AXE, "iron", grip="#4a7a30")
    icons["diggers_shovel"] = metal_item(SHOVEL, "iron", grip="#7a4b24")
    icons["miners_pickaxe"] = metal_item(PICKAXE, "diamond", grip="#7a4b24")
    icons["miners_drill"] = metal_item(DRILL, "#d3a24a", grip="#4a4a52", gem="#ff5a3a", guard="#8c8f96")
    icons["titan_drill"] = metal_item(DRILL, "netherite", grip="#3a3a44", gem="#39e8ff", guard="#d3a24a")
    icons["farmers_hoe"] = metal_item(HOE, "iron", grip="#7a4b24")
    icons["harvest_hoe"] = metal_item(HOE, "diamond", grip="#6a4020")
    icons["rod_of_legends"] = metal_item(ROD, "#ffd24a", grip="#6a4020", gem="#39e8ff")
    icons["grappling_hook"] = metal_item(HOOK, "iron", grip="#9b6b3a", gem="#cfd3d9")
    # armor
    slots = {"helmet": HELMET, "chestplate": CHEST, "leggings": LEGS, "boots": BOOTS}
    for set_id, (main, trim) in SETS.items():
        for slot, rows in slots.items():
            for tier in (1, 2, 3):
                m = ramp(main)
                t = ramp(trim)
                if tier == 3:
                    t = ramp("#ffd24a")
                palette = {"a": m[1], "b": m[0], "c": m[2], "h": t[1] if tier > 1 else m[0], "g": t[0], "k": t[2],
                           "d": t[2], "i": t[1], "w": (255, 255, 255, 255)}
                img = draw(rows, palette)
                if tier == 3:
                    img.set(1, 1, rgb("#ffd24a"))
                    img.set(14, 1, rgb("#ffd24a"))
                icons[f"{set_id}_{slot}_t{tier}"] = img
    # accessories
    for family, gem in FAMILIES.items():
        for tier in range(4):
            f = ramp(TIER_FRAME[tier])
            g = ramp(gem)
            palette = {"a": f[1], "b": f[0], "c": f[2], "g": g[1], "h": g[0], "w": (255, 255, 255, 255)}
            icons[f"acc_{family}_{tier + 1}"] = draw(TIER_SHAPE[tier], palette)
    # utility
    icons["recombobulator"] = metal_item(STAR, "#ff6bd6", gem="#ffffff")
    icons["hot_potato_book"] = metal_item(BOOK, "#d99a3a", grip="#e8e0c8", gem="#ff5a3a", guard="#ffd24a")
    icons["wood_singularity"] = metal_item(LOG, "#6a4b2a", grip="#b98a4a", gem="#6a4020")
    icons["stone_singularity"] = metal_item(BLOCK, "#8c8f96", gem="#cfd3d9")
    icons["hay_singularity"] = metal_item(BLOCK, "#e8c545", gem="#b9922a")
    icons["drill_fuel_cell"] = metal_item(LUMP, "#2a2a35", gem="#8c8f96")
    icons["drill_fuel_volta"] = metal_item(BLOCK, "#d63a3a", gem="#ff9a8a")
    icons["drill_fuel_oil"] = metal_item(BARREL, "#2a2a35", gem="#a74bd6", guard="#8c8f96")
    icons["warp_pearl"] = metal_item(PEARL, "#1f8a7a", gem="#39e8c8")
    for stone, color in (("fabled", "#ffd24a"), ("withered", "#4a4350"), ("ancient", "#d3a24a"), ("refined", "#39e8ff")):
        icons[f"reforge_stone_{stone}"] = metal_item(SHARD, color, gem=color)
    icons["enchanted_book"] = metal_item(BOOK, "#6a3fb0", grip="#e8e0c8", gem="#d36bff", guard="#b892ff")
    icons["pet_egg"] = metal_item(EGG, "#f0e8d0", gem="#7bb8ff")
    icons["pet_candy"] = metal_item(BERRY, "#d63a6a", gem="#ff9ab0", grip="#4a7a30")
    icons["pet_tier_boost"] = metal_item(SHARD, "#c06bff", gem="#c06bff")
    icons["pet_xp_share"] = metal_item(BOTTLE, "#7be07b", grip="#9b6b3a", gem="#c0ffc0")
    icons["pet_big_teeth"] = metal_item(BONE, "#f2f2e8")
    icons["pet_hardened_scales"] = metal_item(SCUTE, "#4fae4f", gem="#9be07b")
    icons["pet_lucky_clover"] = metal_item(CLOVER, "#3fc86a", grip="#2a7a30")
    # pets
    for pet, (rows, main, accent, dark) in PETS.items():
        m, a, d = ramp(main), ramp(accent), ramp(dark)
        palette = {"a": m[1], "b": m[0], "c": m[2], "g": a[1], "h": a[0], "i": a[1], "d": a[2], "k": d[1],
                   "w": (255, 255, 255, 255)}
        icons[f"pet_{pet}"] = draw(rows, palette)
    return icons


def write(icons):
    tex = PACK / "textures" / "item" / "items"
    mdl = PACK / "models" / "item" / "items"
    itm = PACK / "items" / "items"
    for folder in (tex, mdl, itm):
        folder.mkdir(parents=True, exist_ok=True)
    for name, img in sorted(icons.items()):
        (tex / f"{name}.png").write_bytes(img.png())
        (mdl / f"{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/items/{name}"}}, indent=2) + "\n")
        (itm / f"{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/items/{name}"}}, indent=2) + "\n")


def sheet(icons, path, scale=8, columns=16):
    names = sorted(icons)
    rows = (len(names) + columns - 1) // columns
    cell = W * scale + 4
    width, height = columns * cell, rows * cell
    bg = (0x3a, 0x3a, 0x44, 255)
    pixels = [[bg] * width for _ in range(height)]
    for i, name in enumerate(names):
        ox, oy = (i % columns) * cell + 2, (i // columns) * cell + 2
        for y in range(W):
            for x in range(W):
                c = icons[name].p[y][x]
                if c:
                    for sy in range(scale):
                        for sx in range(scale):
                            pixels[oy + y * scale + sy][ox + x * scale + sx] = c
    raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in pixels)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    pathlib.Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                                   + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    icons = build()
    for name, img in icons.items():
        assert all(len(row) == W for row in img.p), name
    write(icons)
    if "--sheet" in sys.argv:
        sheet(icons, sys.argv[sys.argv.index("--sheet") + 1])
    print(f"{len(icons)} item icons written")


if __name__ == "__main__":
    main()
