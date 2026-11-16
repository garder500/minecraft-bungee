#!/usr/bin/env python3
"""ValoBlock weapon sprites, item models and item definitions, stdlib only (no Pillow, no Chrome).

    python3 resource-pack/art/valoblock_items.py

The single source is arena-plugin/src/main/resources/valoblock/weapons.json: each weapon names a
24x10 silhouette ("texture") and a metal palette ("metal"), copied from the design sheets
(VB-Weapons / VB-Armory). Every item gets, under assets/vampyuyu/ (the key the plugin sets with
setItemModel is vampyuyu:valoblock/<id>):

    textures/item/valoblock/<id>.png         32x32, the 24x10 art centred, nothing scaled
    textures/item/valoblock/<id>_locked.png  the same at 35 % alpha (too expensive in the Armory)
    models/item/valoblock/<id>[_locked].json item/generated
    items/valoblock/<id>[_locked].json       item definition

The cyan pixel is the muzzle or the scope lens (design note). PNGs are written with zlib and struct,
rows unfiltered, so the output is reproducible byte for byte.
"""
import json
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / "pack/assets/vampyuyu"
DATA = ROOT.parent / "arena-plugin/src/main/resources/valoblock/weapons.json"

TEMPLATES = {
    "pistol": ["........................", ".ooooooooooooo..........", ".occcccccccdceo.........", ".obbbbbbbbbbbbo.........", ".oaoooggggoooo..........",
               "...o.oggggo.............", "...ooogggo..............", ".....oggao..............", ".....oggao..............", ".....ooooo.............."],
    "revolver": ["........................", ".oooooooooooooooo.......", ".occcccdcccccccceo......", ".obbaaaabbbbbbbbbo......", ".obaeeaboooooooo........",
                ".oobaaboo...............", "...oogggo...............", "....ogggo...............", "....oggao...............", "....ooooo..............."],
    "smg": ["........................", "....oooooooooooooo......", "oooocccccccdcccccceo....", "obbbbbbbbbbbbbbbbbbo....", "oboooooggggoobboooo.....",
            "oo...oggggo.obbo........", ".....ogggo..obbo........", "......ogao..obbo........", "......oooo..oaao........", "............oooo........"],
    "shotgun": ["........................", "........................", "ooo....ooooooooooooooo..", "oggooooccccccccccccccceo", "oggbbbbbbbbbbbbbbbbbbbbo",
                "ogggooooogggooaaaaaaaao.", "oooo....oggo.oaaaaaaao..", "........ogao............", "........oooo............", "........................"],
    "rifle": ["........................", "..........oooo..........", "oooo..ooooooooooooooo...", "oggoooccccccdcccccccceoo", "oggbbbbbbbbbbbbbbbbbbbbo",
              "ogggoooooggggoobboooooo.", "oooo....ogggo.obbo......", ".........ogao.obbo......", ".........oooo.oaao......", "..............oooo......"],
    "sniper": ["........oooooooo........", "........ocdddde.o.......", "oooo.....oooooo.........", "oggoooocccccccccccccccoo", "oggbbbbbbbbbbbbbbbbbbbbo",
               "ogggooooogggooboooooooo.", "oooo....ogggo.obo.......", ".........ogao.oo........", ".........oooo...........", "........................"],
    "lmg": ["........................", ".......oooooo...........", "oooo.ooccccccoooooooooo.", "oggoocccccccdccccccccceo", "oggbbbbbbbbbbbbbbbbbbbbo",
            "ogggooogggoooooooo.o.o..", "oooo..ogggooaaaao.......", "......ogao.oaeeao.......", "......oooo.oaaaao.......", "............oooo........"],
    "knife": ["........................", "........................", "........................", "oooooo.ooooooooooooo....", "oggggoocdddddddddddoo...",
              "oggggoobbbbbbbbbbbbbbo..", "oooooo.oooooooooooooo...", "........................", "........................", "........................"],
    "ward": ["........................", "........oooooooo........", ".......occddddcco.......", "......ocbbbeebbbco......", "......obbbeeeebbbo......",
             "......obbbeeeebbbo......", ".......obbbeebbbo.......", "........obbbbbbo........", ".........oaaaao.........", "..........oooo.........."],
}

METALS = {
    "bronze": {"a": "4a2a12", "b": "7a4a1e", "c": "c98442", "d": "f2c14e"},
    "sun": {"a": "7a3a12", "b": "c8641e", "c": "f29a3a", "d": "ffd36a"},
    "frost": {"a": "1f3557", "b": "3b6fa8", "c": "7fc8e8", "d": "e6f7ff"},
    "void": {"a": "05030a", "b": "1e1432", "c": "3a1a6a", "d": "8a4aff"},
    "bone": {"a": "5a5070", "b": "a8a2c4", "c": "d8d4e8", "d": "ffffff"},
    "marsh": {"a": "2a2a1a", "b": "4e5a22", "c": "8a9a2e", "d": "c9d36a"},
    "storm": {"a": "1e2440", "b": "3a4a8a", "c": "7a8ee0", "d": "f0f4ff"},
    "star": {"a": "140c2a", "b": "24164a", "c": "4a3a8a", "d": "ffe9a0"},
}
COMMON = {"o": "06070a", "g": "5a3a24", "e": "4af0ff"}
LOCKED_ALPHA = 0.35


def rgb(hex6):
    return tuple(int(hex6[i:i + 2], 16) for i in (0, 2, 4))


def sprite(template, metal, alpha=1.0, size=32):
    """size x size RGBA rows: the 24x10 template centred, '.' transparent."""
    pal = dict(COMMON)
    pal.update(METALS[metal])
    rows = TEMPLATES[template]
    ox, oy = (size - 24) // 2, (size - 10) // 2
    img = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for y, row in enumerate(rows):
        assert len(row) == 24, (template, y, len(row))
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            r, g, b = rgb(pal[ch])
            img[oy + y][ox + x] = (r, g, b, round(255 * alpha))
    return img


def png(rows):
    """Encodes RGBA rows as a PNG (8 bit, colour type 6, filter 0 on every row)."""
    h, w = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in rows)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_item(name, rows):
    tex = PACK / f"textures/item/valoblock/{name}.png"
    model = PACK / f"models/item/valoblock/{name}.json"
    definition = PACK / f"items/valoblock/{name}.json"
    for f in (tex, model, definition):
        f.parent.mkdir(parents=True, exist_ok=True)
    tex.write_bytes(png(rows))
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/valoblock/{name}"}}, indent=1) + "\n")
    definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"vampyuyu:item/valoblock/{name}"}}, indent=1) + "\n")


def weapons():
    return json.loads(DATA.read_text())["weapons"]


def main():
    n = 0
    for w in weapons():
        write_item(w["id"], sprite(w["texture"], w["metal"]))
        write_item(w["id"] + "_locked", sprite(w["texture"], w["metal"], LOCKED_ALPHA))
        n += 2
    print(f"wrote {n} items under {PACK}")


if __name__ == "__main__":
    main()
