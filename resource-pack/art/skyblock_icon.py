#!/usr/bin/env python3
"""Draws the Skyblock icon (item model vampyuyu:icon/skyblock): a floating island with a tree.

    python3 resource-pack/art/skyblock_icon.py

Standard library only (a hand-written PNG encoder), deterministic output, committed under pack/.
16x16 pixel art with the same dark outline as the other icons; the palette is the island's own:
grass and leaves green, soil brown, stone grey, a white cloud, a gold spark.
"""
import json, pathlib, struct, zlib

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (20, 18, 31, 255),      # outline, as in the other icons
    "L": (140, 224, 110, 255),   # leaf highlight
    "l": (63, 158, 63, 255),     # leaf
    "k": (31, 95, 42, 255),      # leaf shade
    "t": (107, 68, 35, 255),     # trunk
    "G": (163, 232, 122, 255),   # grass highlight
    "g": (107, 191, 78, 255),    # grass
    "d": (138, 90, 58, 255),     # soil
    "D": (94, 59, 36, 255),      # soil shade
    "s": (143, 143, 160, 255),   # stone
    "w": (245, 241, 255, 255),   # cloud
    "y": (255, 211, 110, 255),   # spark
}

ROWS = [
    "......oooo......",
    ".....oLLllo.....",
    "....oLLllllo..y.",
    "....olllkllo....",
    ".....okllko.....",
    "......oootoo....",
    ".......otto.....",
    "..oooooottooooo.",
    ".oGGggggttggggo.",
    ".ogggggggggggko.",
    ".oddddddsdddDDo.",
    "..odDdsddddDDo..",
    "...odddddDDDo...",
    "....oodsDDoo..w.",
    "......ooDoo.ww..",
    "........oo......",
]


def chunk(kind, data):
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def png(rows):
    raw = b"".join(b"\x00" + b"".join(bytes(PALETTE[ch]) for ch in row) for row in rows)
    header = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write():
    assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS), "the icon is a 16x16 grid"
    assert set("".join(ROWS)) <= set(PALETTE), "unknown palette key"
    for sub in ("textures/item/icon", "models/item/icon", "items/icon"):
        (PACK / sub).mkdir(parents=True, exist_ok=True)
    (PACK / "textures/item/icon/skyblock.png").write_bytes(png(ROWS))
    (PACK / "models/item/icon/skyblock.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "vampyuyu:item/icon/skyblock"}}, indent=2) + "\n")
    (PACK / "items/icon/skyblock.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": "vampyuyu:item/icon/skyblock"}}, indent=2) + "\n")
    print("wrote the skyblock icon")


if __name__ == "__main__":
    write()
