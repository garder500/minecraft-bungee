#!/usr/bin/env python3
"""Draws the custom HUD sprites and declares them as the vampyuyu:hud2 font.

    python3 resource-pack/art/hud.py

Sprites are drawn 1 texel per GUI pixel (the core shader scales them with the GUI). Role and
phase icons come from art/icons.py's SVGs, drawn through ImageMagick. Also writes
arena-plugin/src/main/resources/hud-sprites.txt, which tells the plugin the glyph of each sprite.
"""
import json, pathlib, struct, subprocess, sys, zlib

ART = pathlib.Path(__file__).resolve().parent
ROOT = ART.parent.parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
sys.path.insert(0, str(ART.parent))
sys.path.insert(0, str(ART))
import layout  # noqa: E402
import icons  # noqa: E402

INK = (14, 11, 24)
LIGHT = (246, 234, 208)
FONT = {
    "A": "01110 10001 10001 11111 10001 10001 10001", "B": "11110 10001 10001 11110 10001 10001 11110",
    "C": "01110 10001 10000 10000 10000 10001 01110", "D": "11100 10010 10001 10001 10001 10010 11100",
    "E": "11111 10000 10000 11110 10000 10000 11111", "F": "11111 10000 10000 11110 10000 10000 10000",
    "G": "01110 10001 10000 10111 10001 10001 01111", "H": "10001 10001 10001 11111 10001 10001 10001",
    "I": "01110 00100 00100 00100 00100 00100 01110", "J": "00111 00010 00010 00010 00010 10010 01100",
    "K": "10001 10010 10100 11000 10100 10010 10001", "L": "10000 10000 10000 10000 10000 10000 11111",
    "M": "10001 11011 10101 10101 10001 10001 10001", "N": "10001 11001 10101 10011 10001 10001 10001",
    "O": "01110 10001 10001 10001 10001 10001 01110", "P": "11110 10001 10001 11110 10000 10000 10000",
    "Q": "01110 10001 10001 10001 10101 10010 01101", "R": "11110 10001 10001 11110 10100 10010 10001",
    "S": "01111 10000 10000 01110 00001 00001 11110", "T": "11111 00100 00100 00100 00100 00100 00100",
    "U": "10001 10001 10001 10001 10001 10001 01110", "V": "10001 10001 10001 10001 10001 01010 00100",
    "W": "10001 10001 10001 10101 10101 10101 01010", "X": "10001 10001 01010 00100 01010 10001 10001",
    "Y": "10001 10001 01010 00100 00100 00100 00100", "Z": "11111 00001 00010 00100 01000 10000 11111",
    "0": "01110 10001 10011 10101 11001 10001 01110", "1": "00100 01100 00100 00100 00100 00100 01110",
    "2": "01110 10001 00001 00010 00100 01000 11111", "3": "11110 00001 00001 01110 00001 00001 11110",
    "4": "00010 00110 01010 10010 11111 00010 00010", "5": "11111 10000 11110 00001 00001 10001 01110",
    "6": "00110 01000 10000 11110 10001 10001 01110", "7": "11111 00001 00010 00100 01000 01000 01000",
    "8": "01110 10001 10001 01110 10001 10001 01110", "9": "01110 10001 10001 01111 00001 00010 01100",
    ":": "00000 00100 00000 00000 00000 00100 00000", "/": "00001 00010 00010 00100 01000 01000 10000",
    "-": "00000 00000 00000 11111 00000 00000 00000", " ": "00000 00000 00000 00000 00000 00000 00000",
}


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c if len(c) == 4 else (*c, 255)

    def rect(self, x, y, w, h, c):
        for j in range(y, y + h):
            for i in range(x, x + w):
                self.set(i, j, c)

    def text(self, x, y, word, color, outline=True):
        """5x7 pixel text with a 1 px outline; (x, y) is the top left of the outlined box."""
        cx = x + 1
        for ch in word:
            rows = FONT[ch].split()
            for r, row in enumerate(rows):
                for c, bit in enumerate(row):
                    if bit == "1":
                        if outline:
                            for dx in (-1, 0, 1):
                                for dy in (-1, 0, 1):
                                    px, py = cx + c + dx, y + 1 + r + dy
                                    if 0 <= px < self.w and 0 <= py < self.h and self.px[py][px][3] == 0:
                                        self.set(px, py, INK)
            cx += 6
        cx = x + 1
        for ch in word:
            for r, row in enumerate(FONT[ch].split()):
                for c, bit in enumerate(row):
                    if bit == "1":
                        self.set(cx + c, y + 1 + r, color)
            cx += 6

    def png(self, path):
        raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in self.px)

        def chunk(tag, data):
            body = struct.pack(">I", len(data)) + tag + data
            return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
        path.write_bytes(data)


def rgb(hexcolor):
    return ((hexcolor >> 16) & 255, (hexcolor >> 8) & 255, hexcolor & 255)


def dim(c, k):
    return tuple(int(v * k) for v in c)


def frame(w, h, color, groove=None):
    """A dark plate with a notched border in the role colour (and a groove for a progress bar)."""
    c = Canvas(w, h)
    c.rect(0, 0, w, h, (*INK, 215))
    for x in range(w):
        c.set(x, 0, color)
        c.set(x, h - 1, color)
    for y in range(h):
        c.set(0, y, color)
        c.set(w - 1, y, color)
    for x, y in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):  # notched corners
        c.set(x, y, (0, 0, 0, 0))
    for x in range(1, w - 1):
        c.set(x, 1, dim(color, 0.45))
    if groove:
        gx, gy, gw, gh = groove
        c.rect(gx - 1, gy - 1, gw + 2, gh + 2, (0, 0, 0, 200))
        c.rect(gx, gy, gw, gh, (*dim(color, 0.25), 255))
    return c


def main():
    out = PACK / "textures" / "font" / "hud"
    out.mkdir(parents=True, exist_ok=True)
    sprites = layout.hud_sprites()
    magick = icons.magick()
    for name, w, h in sprites:
        c = Canvas(w, h)
        kind, _, rest = name.partition("_")
        if name.startswith("frame_"):
            color = rgb(layout.HUD_PALETTES[rest])
            c = frame(w, h, color, groove=(6, 19, 178, 3))
        elif name.startswith("fill_"):
            color = rgb(layout.HUD_PALETTES[rest])
            c.rect(0, 0, w, h, color)
            c.rect(0, 0, w, 1, tuple(min(255, v + 60) for v in color))
        elif name.startswith("pframe_"):
            color = rgb(layout.HUD_PALETTES[rest])
            c = frame(w, h, color)
            for x in range(4, w - 4):
                c.set(x, 33, dim(color, 0.5))
            c.text(3, 36, "ALIVE", dim(color, 1.0))
        elif name.startswith(("icon_", "picon_")):
            icon = rest
            svg = ART / "icons" / f"{icon}.svg"
            subprocess.run([magick, "-background", "none", "-density", str(72 * w // 32), str(svg),
                            "-resize", f"{w}x{h}", "-strip", f"PNG32:{out / (name + '.png')}"], check=True)
            continue
        elif name.startswith(("label_", "plabel_", "pteam_")):
            c.text(0, 0, rest.replace("_", " ").upper(), LIGHT)
        elif name.startswith("d") and name[1:].isdigit():
            c.text(0, 0, name[1:], LIGHT)
        elif name == "dcolon":
            c.text(-1, 0, ":", LIGHT)
        elif name == "dslash":
            c.text(0, 0, "/", LIGHT)
        c.png(out / f"{name}.png")
    providers, advances, lines = [], {}, []
    for index, (name, w, h) in enumerate(sprites):
        char = chr(layout.HUD_SPRITE_BASE + index)
        providers.append({"type": "bitmap", "file": f"vampyuyu:font/hud/{name}.png", "ascent": h,
                          "height": h, "chars": [char]})
        advances[chr(layout.HUD_NEG_BASE + index)] = -(w + 1)
        lines.append(f"sprite {index} {name}")
    providers.append({"type": "space", "advances": advances})
    (PACK / "font" / "hud2.json").write_text(json.dumps({"providers": providers}, indent=1) + "\n")
    for index, element in enumerate(layout.HUD_ELEMENTS):
        lines.append(f"element {index} {element[0]}")
    res = ROOT / "arena-plugin" / "src" / "main" / "resources" / "hud-sprites.txt"
    res.write_text("\n".join(lines) + "\n")
    print("drew", len(sprites), "HUD sprites")


if __name__ == "__main__":
    main()
