#!/usr/bin/env python3
"""Draws the 16x16 pixel-art item icons of the Skyblock minions feature (stdlib only, deterministic).

    python3 resource-pack/art/minions_icons.py

Writes, for every name below, three files that all contain "minions" in their path:
  assets/vampyuyu/textures/item/minions/<name>.png
  assets/vampyuyu/models/item/minions/<name>.json
  assets/vampyuyu/items/minions/<name>.json       (item model "vampyuyu:minions/<name>")
Names: one per minion type (the tier is shown by the item name and rarity colour), fuel_<id> for the custom fuels and
upgrade_<id> for the upgrade items. Keep the lists in step with survival-plugin/src/main/resources/minions.yml
(survival-plugin tests read the same lists).
"""
import json, pathlib, struct, sys, zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SIZE = 16
OUTLINE = (22, 17, 30, 255)
SKIN = (238, 202, 154, 255)
SKIN_D = (205, 164, 118, 255)
EYE = (30, 24, 40, 255)
WOOD = (140, 98, 52, 255)
WOOD_D = (98, 66, 34, 255)
METAL = (190, 196, 205, 255)
METAL_D = (130, 136, 148, 255)

# type id -> (body colour, hat colour, tool kind, tool head colour)
TYPES = {
    "cobblestone": ((138, 138, 138), (96, 96, 104), "pick", (160, 160, 160)),
    "coal": ((58, 58, 62), (40, 40, 44), "pick", (60, 60, 64)),
    "iron": ((216, 196, 182), (170, 150, 136), "pick", (226, 226, 230)),
    "gold": ((250, 214, 70), (214, 168, 30), "pick", (255, 222, 80)),
    "diamond": ((90, 226, 220), (40, 170, 176), "pick", (110, 240, 235)),
    "redstone": ((210, 40, 36), (150, 22, 22), "pick", (230, 60, 50)),
    "lapis": ((50, 84, 200), (30, 54, 150), "pick", (70, 110, 230)),
    "emerald": ((52, 214, 100), (24, 150, 66), "pick", (70, 232, 120)),
    "clay": ((160, 168, 190), (118, 126, 150), "shovel", (178, 186, 206)),
    "sand": ((228, 210, 150), (190, 170, 110), "shovel", (236, 220, 164)),
    "wheat": ((226, 190, 70), (170, 130, 40), "hoe", (240, 210, 100)),
    "carrot": ((240, 140, 40), (60, 150, 50), "hoe", (250, 160, 60)),
    "potato": ((196, 150, 90), (150, 106, 56), "hoe", (214, 170, 110)),
    "pumpkin": ((226, 120, 24), (150, 70, 16), "hoe", (240, 140, 40)),
    "melon": ((90, 180, 60), (40, 120, 40), "hoe", (110, 200, 80)),
    "oak": ((150, 112, 62), (68, 140, 56), "axe", (176, 136, 80)),
    "spruce": ((96, 68, 40), (40, 98, 62), "axe", (120, 88, 52)),
    "birch": ((222, 218, 200), (110, 170, 80), "axe", (236, 232, 214)),
    "cow": ((236, 236, 236), (60, 50, 50), "bucket", (190, 190, 200)),
    "chicken": ((250, 250, 244), (220, 40, 40), "feather", (255, 255, 255)),
    "pig": ((244, 170, 176), (214, 120, 130), "carrot", (250, 150, 50)),
    "sheep": ((246, 246, 240), (200, 200, 196), "shears", (200, 200, 210)),
    "fishing": ((56, 130, 220), (30, 84, 160), "rod", (240, 240, 240)),
    "zombie": ((76, 146, 80), (50, 100, 58), "sword", (190, 196, 205)),
    "skeleton": ((220, 220, 214), (150, 150, 146), "bow", (150, 100, 56)),
    "slime": ((110, 220, 90), (70, 170, 60), "sword", (150, 240, 130)),
}
FUELS = ["enchanted_coal", "enchanted_charcoal", "enchanted_lava_bucket", "hamster_wheel", "foul_flesh", "catalyst",
         "hyper_catalyst"]
UPGRADES = ["auto_smelter", "compactor", "super_compactor", "diamond_spreading", "minion_expander", "flycatcher",
            "mithril_infusion"]


class Canvas:
    def __init__(self):
        self.px = [[None] * SIZE for _ in range(SIZE)]

    def set(self, x, y, c):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.px[y][x] = c if len(c) == 4 else (c[0], c[1], c[2], 255)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            self.set(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), c)

    def disc(self, cx, cy, r, c):
        for y in range(SIZE):
            for x in range(SIZE):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.set(x, y, c)

    def shade(self, light=0.18, dark=0.22):
        """Lighter top-left rim and darker bottom-right rim on every opaque pixel with a transparent neighbour."""
        src = [row[:] for row in self.px]
        for y in range(SIZE):
            for x in range(SIZE):
                c = src[y][x]
                if c is None or c == OUTLINE:
                    continue
                up = y > 0 and src[y - 1][x] is None
                left = x > 0 and src[y][x - 1] is None
                down = y < SIZE - 1 and src[y + 1][x] is None
                right = x < SIZE - 1 and src[y][x + 1] is None
                if up or left:
                    self.px[y][x] = mix(c, (255, 255, 255, 255), light)
                elif down or right:
                    self.px[y][x] = mix(c, (0, 0, 0, 255), dark)

    def outline(self):
        src = [row[:] for row in self.px]
        for y in range(SIZE):
            for x in range(SIZE):
                if src[y][x] is not None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < SIZE and 0 <= ny < SIZE and src[ny][nx] is not None and src[ny][nx] != OUTLINE:
                        self.px[y][x] = OUTLINE
                        break

    def png(self):
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for c in row:
                raw.extend(c if c is not None else (0, 0, 0, 0))
        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def rgba(c):
    return (c[0], c[1], c[2], 255)


# ---------------------------------------------------------------- minion figure

def tool(cv, kind, head):
    """The tool held on the right (x 12..15)."""
    head = rgba(head)
    if kind == "pick":
        cv.line(13, 13, 14, 6, WOOD)
        cv.line(11, 5, 15, 5, head)
        cv.set(11, 6, head), cv.set(15, 6, head)
    elif kind == "shovel":
        cv.line(14, 14, 14, 7, WOOD)
        cv.rect(13, 3, 15, 6, head)
    elif kind == "hoe":
        cv.line(14, 14, 14, 5, WOOD)
        cv.line(12, 4, 15, 4, head)
        cv.set(12, 5, head)
    elif kind == "axe":
        cv.line(14, 14, 14, 4, WOOD)
        cv.rect(12, 3, 13, 6, head)
        cv.set(14, 3, head)
    elif kind == "bucket":
        cv.rect(12, 8, 15, 11, rgba(head))
        cv.line(12, 7, 15, 7, METAL_D)
        cv.rect(13, 8, 14, 9, (250, 250, 250, 255))
    elif kind == "feather":
        cv.line(12, 10, 15, 3, head)
        cv.set(14, 5, (240, 200, 60, 255)), cv.set(13, 8, (240, 200, 60, 255))
    elif kind == "carrot":
        cv.line(12, 10, 15, 6, head)
        cv.set(15, 5, (70, 170, 60, 255)), cv.set(14, 5, (70, 170, 60, 255))
    elif kind == "shears":
        cv.line(12, 4, 15, 11, head)
        cv.line(15, 4, 12, 11, METAL_D)
        cv.set(12, 12, (220, 60, 60, 255)), cv.set(15, 12, (220, 60, 60, 255))
    elif kind == "rod":
        cv.line(12, 14, 15, 3, WOOD)
        cv.line(15, 3, 15, 9, rgba(head))
        cv.set(15, 10, (220, 50, 50, 255))
    elif kind == "sword":
        cv.line(14, 12, 14, 3, rgba(head))
        cv.line(12, 12, 15, 12, WOOD_D)
        cv.set(14, 13, WOOD)
    elif kind == "bow":
        cv.line(13, 3, 15, 5, rgba(head))
        cv.line(15, 5, 15, 10, rgba(head))
        cv.line(15, 10, 13, 12, rgba(head))
        cv.line(13, 3, 13, 12, (230, 230, 230, 255))


def minion(type_id):
    body, hat, kind, head = TYPES[type_id]
    body, hat = rgba(body), rgba(hat)
    cv = Canvas()
    tool(cv, kind, head)
    cv.rect(5, 12, 6, 14, mix(body, (0, 0, 0, 255), 0.45))     # legs
    cv.rect(9, 12, 10, 14, mix(body, (0, 0, 0, 255), 0.45))
    cv.rect(4, 7, 11, 11, body)                                 # torso
    cv.rect(2, 7, 3, 10, SKIN_D)                                # arms
    cv.rect(12, 7, 12, 10, SKIN_D)
    cv.rect(4, 2, 11, 6, SKIN)                                  # head
    cv.rect(3, 1, 12, 2, hat)                                   # hat with brim
    cv.rect(4, 0, 11, 1, hat)
    cv.rect(5, 4, 6, 4, EYE)                                    # eyes
    cv.rect(9, 4, 10, 4, EYE)
    cv.rect(7, 6, 8, 6, SKIN_D)                                 # mouth
    cv.rect(6, 8, 9, 8, mix(body, (255, 255, 255, 255), 0.3))   # collar
    cv.shade()
    cv.outline()
    return cv


# ---------------------------------------------------------------- fuels

def fuel(name):
    cv = Canvas()
    if name == "enchanted_coal":
        for (x, y, r) in ((8, 8, 5), (5, 6, 2)):
            cv.disc(x, y, r, (44, 44, 50, 255))
        cv.rect(6, 6, 7, 7, (80, 80, 90, 255))
        for (x, y) in ((11, 4), (4, 11), (12, 11)):
            cv.set(x, y, (190, 120, 255, 255))
    elif name == "enchanted_charcoal":
        cv.disc(8, 8, 5, (70, 54, 46, 255))
        cv.line(4, 6, 12, 10, (46, 34, 30, 255))
        cv.line(5, 11, 11, 5, (96, 76, 64, 255))
        for (x, y) in ((12, 3), (3, 12), (13, 10)):
            cv.set(x, y, (255, 170, 80, 255))
    elif name == "enchanted_lava_bucket":
        cv.rect(3, 6, 12, 13, METAL)
        cv.rect(4, 7, 11, 8, (255, 140, 30, 255))
        cv.rect(4, 9, 11, 12, (230, 90, 20, 255))
        cv.line(3, 5, 12, 5, METAL_D)
        cv.line(4, 3, 11, 3, METAL_D)
        cv.set(4, 4, METAL_D), cv.set(11, 4, METAL_D)
        cv.set(7, 8, (255, 230, 100, 255)), cv.set(9, 10, (255, 230, 100, 255))
    elif name == "hamster_wheel":
        ring = (190, 150, 80, 255)
        for y in range(SIZE):
            for x in range(SIZE):
                d = (x - 7.5) ** 2 + (y - 7.5) ** 2
                if 17 <= d <= 36:
                    cv.set(x, y, ring)
        cv.line(2, 7, 13, 8, WOOD)
        cv.line(7, 2, 8, 13, WOOD)
        cv.rect(7, 7, 8, 8, (255, 210, 80, 255))
        cv.rect(1, 13, 14, 14, METAL_D)
    elif name == "foul_flesh":
        cv.rect(3, 5, 12, 11, (150, 96, 70, 255))
        cv.rect(4, 4, 10, 4, (150, 96, 70, 255))
        cv.rect(5, 12, 11, 12, (150, 96, 70, 255))
        for (x, y) in ((5, 7), (9, 6), (7, 10), (11, 9)):
            cv.set(x, y, (96, 150, 70, 255))
        cv.line(4, 9, 8, 8, (190, 70, 60, 255))
        cv.set(12, 3, (120, 200, 80, 255)), cv.set(3, 13, (120, 200, 80, 255))
    elif name == "catalyst":
        cv.line(8, 1, 8, 14, (255, 150, 30, 255))
        cv.line(1, 8, 14, 8, (255, 150, 30, 255))
        cv.line(3, 3, 13, 13, (255, 190, 60, 255))
        cv.line(13, 3, 3, 13, (255, 190, 60, 255))
        cv.disc(8, 8, 3, (255, 214, 90, 255))
        cv.rect(7, 7, 8, 8, (255, 255, 220, 255))
    elif name == "hyper_catalyst":
        cv.line(8, 0, 8, 15, (255, 230, 60, 255))
        cv.line(0, 8, 15, 8, (255, 230, 60, 255))
        cv.line(2, 2, 14, 14, (255, 240, 120, 255))
        cv.line(14, 2, 2, 14, (255, 240, 120, 255))
        cv.disc(8, 8, 4, (255, 246, 150, 255))
        cv.disc(8, 8, 2, (255, 255, 255, 255))
        for (x, y) in ((3, 4), (12, 11), (12, 4), (3, 12)):
            cv.set(x, y, (255, 255, 255, 255))
    cv.shade(0.14, 0.2)
    cv.outline()
    return cv


# ---------------------------------------------------------------- upgrades

def upgrade(name):
    cv = Canvas()
    plate = (88, 92, 108, 255)
    cv.rect(1, 1, 14, 14, plate)
    cv.rect(2, 2, 13, 13, (60, 64, 80, 255))
    if name == "auto_smelter":
        cv.rect(3, 3, 12, 12, (120, 120, 128, 255))
        cv.rect(5, 7, 10, 11, (30, 24, 24, 255))
        cv.rect(6, 9, 9, 11, (255, 140, 30, 255))
        cv.rect(7, 10, 8, 11, (255, 220, 90, 255))
        cv.rect(5, 4, 10, 5, (70, 70, 78, 255))
    elif name in ("compactor", "super_compactor"):
        big = name == "super_compactor"
        a = (230, 190, 70, 255) if big else (180, 190, 205, 255)
        cv.rect(3, 3, 12, 5, a)
        cv.rect(3, 10, 12, 12, a)
        cv.rect(6, 7, 9, 8, (250, 250, 250, 255) if big else (220, 225, 235, 255))
        cv.line(4, 6, 4, 6, a), cv.line(11, 6, 11, 6, a)
        cv.set(5, 6, plate), cv.set(10, 6, plate)
        cv.set(5, 9, plate), cv.set(10, 9, plate)
        if big:
            cv.set(8, 6, (255, 240, 150, 255)), cv.set(7, 9, (255, 240, 150, 255))
    elif name == "diamond_spreading":
        d = (110, 240, 235, 255)
        cv.line(8, 3, 12, 8, d), cv.line(12, 8, 8, 13, d), cv.line(8, 13, 4, 8, d), cv.line(4, 8, 8, 3, d)
        cv.rect(7, 6, 9, 10, (150, 250, 245, 255))
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            cv.set(x, y, (200, 255, 250, 255))
    elif name == "minion_expander":
        a = (120, 230, 130, 255)
        cv.rect(7, 7, 8, 8, (250, 250, 250, 255))
        for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            cv.line(8 + dx * 2, 8 + dy * 2, 8 + dx * 5, 8 + dy * 5, a)
        cv.rect(7, 3, 8, 3, a), cv.rect(7, 12, 8, 12, a), cv.rect(3, 7, 3, 8, a), cv.rect(12, 7, 12, 8, a)
    elif name == "flycatcher":
        cv.rect(7, 6, 8, 11, (40, 40, 48, 255))
        cv.rect(4, 4, 7, 7, (170, 220, 255, 255))
        cv.rect(8, 4, 11, 7, (170, 220, 255, 255))
        cv.set(7, 5, (255, 255, 255, 255)), cv.set(8, 5, (255, 255, 255, 255))
        cv.set(6, 6, (230, 120, 230, 255)), cv.set(9, 6, (230, 120, 230, 255))
        cv.set(7, 12, (255, 70, 70, 255)), cv.set(8, 12, (255, 70, 70, 255))
    elif name == "mithril_infusion":
        m = (60, 190, 190, 255)
        cv.line(8, 2, 12, 7, m), cv.line(12, 7, 10, 13, m), cv.line(10, 13, 6, 13, m)
        cv.line(6, 13, 4, 7, m), cv.line(4, 7, 8, 2, m)
        cv.rect(6, 6, 9, 10, (110, 230, 225, 255))
        cv.rect(7, 7, 8, 8, (220, 255, 255, 255))
    cv.shade(0.12, 0.2)
    cv.outline()
    return cv


def entries():
    for type_id in TYPES:
        yield type_id, minion(type_id)
    for name in FUELS:
        yield "fuel_" + name, fuel(name)
    for name in UPGRADES:
        yield "upgrade_" + name, upgrade(name)


def write(path, data, mode="wb"):
    path.parent.mkdir(parents=True, exist_ok=True)
    if mode == "wb":
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def main():
    count = 0
    for name, cv in entries():
        write(PACK / "textures" / "item" / "minions" / f"{name}.png", cv.png())
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/minions/{name}"}}
        write(PACK / "models" / "item" / "minions" / f"{name}.json", json.dumps(model, indent=2) + "\n", "w")
        item = {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/minions/{name}"}}
        write(PACK / "items" / "minions" / f"{name}.json", json.dumps(item, indent=2) + "\n", "w")
        count += 1
    print(f"wrote {count} minion icons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
