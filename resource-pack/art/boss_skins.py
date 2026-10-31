#!/usr/bin/env python3
"""Custom looks of the bosses: one cuboid item model per boss, drawn here with the stdlib only.

    python3 resource-pack/art/boss_skins.py

For every boss it writes
  pack/assets/vampyuyu/models/item/boss/skin/<id>.json      (a model made of boxes, 16 units = 1 block)
  pack/assets/vampyuyu/textures/item/boss/skin/<id>.png     (16x16 atlas: 16 materials of 4x4 pixels)
  pack/assets/vampyuyu/items/boss/skin/<id>.json            (item model vampyuyu:boss/skin/<id>)
The plugin (BossSkinService) hides the vanilla mob and shows this model on an item display that follows it, so only
the Skyblock bosses change: vanilla zombies, spiders and dragons of Survival keep their look. The model's front is -z.
The output is deterministic.
"""
import json
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


def magma_warden():
    s = Skin("magma_warden", [("rock", (58, 46, 44), "plain"), ("lava", (255, 120, 20), "glow"),
                              ("obs", (28, 20, 40), "plain"), ("crack", (70, 40, 36), "crack"),
                              ("eye", (255, 230, 90), "glow"), ("ember", (210, 60, 20), "glow")], 11)
    s.mirror_x((3, 0, 5), (7, 7, 11), "rock")                       # legs
    s.mirror_x((3, 0, 4.5), (7, 1.5, 11.5), "lava")                 # glowing soles
    s.box((3, 7, 5), (13, 9, 11), "obs")                            # hips
    s.box((2, 9, 4), (14, 20, 12), "crack", top="rock")             # torso with lava cracks
    s.box((5, 11, 3.5), (11, 17, 4), "lava")                        # chest core
    s.box((4.5, 10.5, 3.7), (11.5, 11, 4.2), "ember")
    s.box((5, 12, 11.8), (11, 18, 12.2), "ember")                   # back vent
    s.mirror_x((-1.5, 16, 3), (3.5, 21, 13), "obs", top="rock")     # pauldrons
    s.mirror_x((-1, 21, 6), (1.5, 27, 10), "rock", rot=("z", 22.5, [0, 21, 8]))   # shoulder spikes
    s.mirror_x((-2, 6, 5), (2, 17, 11), "rock")                     # arms
    s.mirror_x((-3, 1, 4), (3, 7.5, 12), "lava")                    # molten fists
    s.mirror_x((-2.5, 7.5, 4.5), (2.5, 9, 11.5), "obs")             # wrists
    s.box((5, 20, 5), (11, 26, 11), "obs", top="rock")              # head
    s.mirror_x((6, 22, 4.6), (7.8, 23.8, 5), "eye")                 # eyes
    s.box((5.5, 26, 5.5), (10.5, 27.5, 10.5), "lava")               # molten crown
    s.mirror_x((3.5, 25, 7), (6, 32, 9.5), "rock", rot=("z", 22.5, [5, 25, 8]))   # horns
    s.mirror_x((6.5, 27.5, 5.5), (8, 31, 7), "ember")               # crown flames
    s.write()


def frost_revenant():
    s = Skin("frost_revenant", [("silver", (150, 170, 190), "plain"), ("ice", (140, 210, 255), "frost"),
                                ("deep", (50, 90, 170), "plain"), ("eye", (120, 255, 255), "glow"),
                                ("cloak", (30, 40, 90), "plain"), ("blade", (210, 240, 255), "frost")], 22)
    s.mirror_x((5, 0, 6), (7.7, 10, 10), "deep")                    # legs
    s.mirror_x((4.6, 0, 5.4), (8, 3.5, 10.6), "silver")             # boots
    s.box((4, 10, 5), (12, 22, 11), "silver", top="ice")            # torso
    s.box((5, 13, 4.6), (11, 20, 5), "ice")                         # chest crystal
    s.box((6.5, 14, 4.3), (9.5, 18, 4.7), "eye")
    s.mirror_x((0.5, 19, 4.5), (5, 24, 11.5), "ice", rot=("z", 22.5, [3, 21, 8]))   # pauldron crystals
    s.mirror_x((-1, 9, 6), (3, 20, 10), "deep")                     # arms
    s.mirror_x((-1.4, 8, 5.6), (3.4, 11, 10.4), "silver")           # gauntlets
    s.box((5.5, 22, 5.5), (10.5, 28, 10.5), "silver", top="ice")    # helm
    s.box((6, 24, 5.2), (10, 25.4, 5.6), "eye")                     # visor glow
    for i, h in enumerate((2.5, 4, 3, 4, 2.5)):                       # crown of icicles
        x = 5.8 + i * 1.0
        s.box((x, 28, 7), (x + 0.8, 28 + h, 8.4), "ice")
    s.box((15, 3, 7.4), (16.6, 24, 8.6), "blade")                   # greatsword
    s.box((13, 10.5, 6.8), (18.6, 12, 9.2), "silver")
    s.box((15.4, 24, 7.6), (16.2, 29, 8.4), "blade")
    s.box((4, 6, 10.5), (12, 24, 11.4), "cloak")                    # cloak
    s.box((3.5, 4, 11.2), (12.5, 20, 11.8), "deep")
    s.write()


def sylvan_matriarch():
    s = Skin("sylvan_matriarch", [("chitin", (30, 70, 40), "plain"), ("leaf", (60, 140, 50), "spots"),
                                  ("bark", (80, 60, 36), "plain"), ("eye", (190, 255, 80), "glow"),
                                  ("bloom", (255, 120, 190), "glow"), ("web", (225, 235, 225), "frost")], 33)
    s.box((5, 6, 2), (11, 12, 8), "chitin", top="leaf")             # head and thorax
    s.box((4, 6, 8), (12, 16, 17), "leaf", top="leaf")              # leafy abdomen
    s.box((5, 15.5, 9), (11, 18, 15), "bark")
    s.mirror_x((5.5, 9, 1.5), (7, 10.5, 2.3), "eye")                # eyes
    s.mirror_x((6.4, 7.5, 1.5), (7.2, 8.6, 2.3), "eye")
    s.mirror_x((7, 5, 2), (7.9, 7, 3.2), "web")                     # fangs
    for i, z in enumerate((3, 5.5, 8, 10.5)):                       # four legs a side
        reach = 5 + (i % 2) * 1.5
        s.mirror_x((11, 8.5, z), (11 + reach, 10, z + 1.4), "bark", rot=("z", 22.5, [11, 9, z]))
        s.mirror_x((11 + reach - 0.4, 0, z), (11 + reach + 1.2, 9.4, z + 1.4), "chitin")
    s.mirror_x((5, 14.5, 10), (8, 16.5, 13), "bloom")               # flowers
    s.box((7, 17.5, 12), (9.5, 19.5, 14.5), "bloom")
    s.mirror_x((3.5, 8, 12), (5, 12, 15), "leaf")
    s.write()


def abyssal_leviathan():
    s = Skin("abyssal_leviathan", [("scale", (20, 80, 100), "spots"), ("belly", (90, 170, 170), "plain"),
                                   ("eye", (90, 255, 255), "glow"), ("fin", (80, 60, 170), "frost"),
                                   ("tooth", (235, 240, 235), "plain"), ("deep", (10, 40, 60), "plain")], 44)
    s.box((4, 8, 0), (12, 16, 10), "scale", top="deep")             # head
    s.box((4.5, 5, 1), (11.5, 8, 10), "belly")                      # lower jaw
    for i in range(4):                                              # teeth
        x = 4.8 + i * 1.9
        s.box((x, 6.6, 0.4), (x + 0.9, 8.6, 1.3), "tooth")
        s.box((x, 7.4 - 1.0, 0.2), (x + 0.9, 8, 1.0), "tooth")
    s.mirror_x((3.2, 11, 2.8), (4, 14.5, 6.2), "eye")              # eyes
    s.mirror_x((1.5, 11, 6.5), (4.4, 21, 12), "fin", rot=("z", -22.5, [4, 12, 9]))   # side fins
    s.box((7, 16, 1), (9, 19, 9), "fin")                            # head crest
    s.box((5, 6, 10), (11, 14, 18), "scale", top="deep")            # body
    s.box((5.5, 4.5, 10), (10.5, 6, 18), "belly")
    s.box((7, 14, 11), (9, 17.5, 17), "fin")
    s.box((5.5, 5, 18), (10.5, 12, 25), "scale")                    # tail
    s.box((7, 12, 18.5), (9, 15, 24), "fin")
    s.box((6.2, 4, 25), (9.8, 10, 29), "scale")
    s.box((7.2, 1, 28), (8.8, 16, 31.5), "fin")                     # tail fin
    s.write()


def crypt_lord():
    s = Skin("crypt_lord", [("bone", (225, 220, 195), "plain"), ("dark", (140, 130, 110), "plain"),
                            ("cape", (110, 20, 30), "plain"), ("gold", (240, 190, 50), "glow"),
                            ("eye", (110, 255, 120), "glow"), ("void", (20, 20, 30), "plain")], 55)
    s.mirror_x((5, 0, 6.5), (7, 11, 9.5), "dark")                   # legs
    s.mirror_x((4.6, 0, 5.6), (7.4, 1.6, 10), "bone")
    s.box((5, 11, 6), (11, 13, 10), "dark")                         # pelvis
    for i in range(5):                                              # ribs
        y = 13.2 + i * 1.7
        s.box((4.4, y, 5.4), (11.6, y + 0.9, 10.6), "bone")
    s.box((7.4, 13, 7.6), (8.6, 21.5, 8.4), "dark")                 # spine
    s.mirror_x((0, 19, 6), (4.5, 22, 10), "gold")                   # gilded pauldrons
    s.mirror_x((0.5, 8, 7), (2.4, 19.5, 9), "bone")                 # arms
    s.box((5.2, 22, 5.2), (10.8, 28, 10.8), "bone", top="dark")     # skull
    s.mirror_x((6, 24.5, 4.8), (7.4, 26, 5.3), "eye")               # eyes
    s.box((6.2, 22.4, 4.9), (9.8, 23.4, 5.3), "void")               # jaw gap
    for i, h in enumerate((2.5, 4, 2.5, 4, 2.5)):                   # crown
        s.box((5.4 + i * 1.1, 28, 7), (6.2 + i * 1.1, 28 + h, 8.4), "gold")
    s.box((5, 28, 5.2), (11, 29, 10.8), "gold")
    s.box((4, 5, 10.6), (12, 24, 11.4), "cape")                     # cape
    s.box((3.4, 3, 11.2), (12.6, 19, 11.8), "cape")
    s.box((14, 4, 7.4), (15.2, 24, 8.6), "dark")                    # scepter
    s.box((13.6, 24, 7), (15.6, 27, 9), "eye")
    s.write()


def voidwyrm():
    s = Skin("voidwyrm", [("scale", (62, 34, 108), "spots"), ("belly", (140, 100, 200), "plain"),
                          ("eye", (255, 110, 255), "glow"), ("wing", (84, 40, 150), "frost"),
                          ("bone", (228, 218, 244), "plain"), ("void", (12, 6, 26), "plain"),
                          ("vein", (190, 90, 255), "glow")], 66)
    # body, chest core and belly plates
    s.box((5, 9, 6), (11, 15, 22), "scale", top="void")
    s.box((5.4, 7.6, 6.4), (10.6, 9, 21.6), "belly")
    s.box((6.4, 10, 5.5), (9.6, 13.4, 6.1), "vein")                 # glowing chest core
    # neck in three bent segments, then the head
    s.box((6, 10.5, 2.5), (10, 14.5, 7), "scale")
    s.box((6.3, 11, -0.5), (9.7, 14.5, 3), "scale", top="void")
    s.box((5.4, 10.8, -6), (10.6, 15.6, -0.5), "scale", top="void") # head
    s.box((6, 9.2, -6.6), (10, 10.8, -1), "bone")                   # lower jaw
    for k in range(4):                                              # teeth
        s.box((6.2 + k * 1.0, 10.6, -6.4), (6.7 + k * 1.0, 11.8, -5.9), "bone")
    s.mirror_x((5.2, 13.4, -5.4), (7, 14.9, -4.6), "eye")           # burning eyes
    s.mirror_x((5.4, 15.4, -2.5), (7.2, 22, -0.5), "bone", rot=("x", -22.5, [6, 15.4, -1.5]))  # horns
    s.mirror_x((4.6, 14.5, -0.5), (6, 20, 1.2), "void", rot=("x", -22.5, [5, 14.5, 0]))
    s.mirror_x((5.2, 8.8, -4.5), (6.4, 10, -2.5), "vein")           # throat glow
    # wings: arm bone, three fingers and glowing veins in the membrane
    s.mirror_x((11, 13, 8), (22, 14.6, 10), "bone", rot=("z", 22.5, [11, 13.8, 9]))
    s.mirror_x((11, 13.6, 7), (31, 14.2, 12.5), "wing", rot=("z", 22.5, [11, 13.9, 9.75]))
    s.mirror_x((11, 13.4, 12), (30, 14, 17.5), "wing", rot=("z", 22.5, [11, 13.7, 14.75]))
    s.mirror_x((11, 13.2, 15), (27, 13.8, 22), "wing", rot=("z", 22.5, [11, 13.5, 18.5]))
    s.mirror_x((11, 13.9, 8.4), (31.5, 14.6, 9.2), "bone", rot=("z", 22.5, [11, 14.2, 8.8]))
    s.mirror_x((11, 13.7, 12.6), (29.5, 14.4, 13.2), "bone", rot=("z", 22.5, [11, 14, 12.9]))
    s.mirror_x((11, 13.5, 16.8), (26.5, 14.2, 17.4), "bone", rot=("z", 22.5, [11, 13.8, 17.1]))
    s.mirror_x((14, 14.0, 9), (25, 14.5, 9.8), "vein", rot=("z", 22.5, [11, 14.2, 9.4]))
    # tail with a ridge of spikes and a glowing tip
    s.box((5.6, 9.4, 22), (10.4, 13.6, 27), "scale")
    s.box((6.1, 9.6, 27), (9.9, 12.6, 31), "scale")
    s.box((7, 9.8, 31), (9, 11.8, 32), "vein")
    for k, z in enumerate((9, 12.5, 16, 19.5, 23, 27)):             # dorsal spikes
        h = 4.2 - k * 0.45
        s.box((7.4, 15, z), (8.6, 15 + h, z + 1.6), "bone", rot=("x", 22.5, [8, 15, z + 0.8]))
    s.mirror_x((5.2, 3.5, 9), (7.2, 9.2, 12), "scale")              # legs and claws
    s.mirror_x((5.2, 3.5, 16), (7.2, 9.2, 19), "scale")
    s.mirror_x((4.6, 2.6, 8.4), (7.4, 3.8, 12.6), "bone")
    s.mirror_x((4.6, 2.6, 15.4), (7.4, 3.8, 19.6), "bone")
    s.write()


BOSSES = {"magma_warden": magma_warden, "frost_revenant": frost_revenant, "sylvan_matriarch": sylvan_matriarch,
          "abyssal_leviathan": abyssal_leviathan, "crypt_lord": crypt_lord, "voidwyrm": voidwyrm}


def main():
    for build in BOSSES.values():
        build()
    print(f"wrote {len(BOSSES)} boss skins")


if __name__ == "__main__":
    main()
