#!/usr/bin/env python3
"""Draws the custom HUD sprites and declares them as the vampyuyu:hud2 font.

    python3 resource-pack/art/hud.py

Sprites are drawn 1 texel per GUI pixel (the core shader scales them with the GUI). Role and
phase icons come from art/icons.py's SVGs, drawn through ImageMagick. Also writes
arena-plugin/src/main/resources/hud-sprites.txt, which tells the plugin the glyph of each sprite.
"""
import json, math, pathlib, struct, subprocess, sys, zlib

ART = pathlib.Path(__file__).resolve().parent
ROOT = ART.parent.parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
sys.path.insert(0, str(ART.parent))
sys.path.insert(0, str(ART))
import layout  # noqa: E402
import icons  # noqa: E402

INK = (12, 9, 22)
LIGHT = (250, 240, 218)
DANGER = (255, 88, 72)
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
    ":": "00000 00100 00000 00000 00000 00100 00000", ".": "00000 00000 00000 00000 00000 01100 01100", "/": "00001 00010 00010 00100 01000 01000 10000",
    "-": "00000 00000 00000 11111 00000 00000 00000", " ": "00000 00000 00000 00000 00000 00000 00000",
}


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rgb(hexcolor):
    return ((hexcolor >> 16) & 255, (hexcolor >> 8) & 255, hexcolor & 255)


def lift(c, k):
    return mix(c, (255, 255, 255), k)


def dim(c, k):
    return tuple(int(v * k) for v in c)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.w and 0 <= y < self.h else (0, 0, 0, 0)

    def set(self, x, y, c, a=255):
        """Alpha-blends colour c at (x, y); a is the colour's own opacity."""
        if not (0 <= x < self.w and 0 <= y < self.h):
            return
        a = c[3] if len(c) == 4 else a
        if a <= 0:
            return
        base = self.px[y][x]
        ba = base[3] / 255
        oa = a / 255 + ba * (1 - a / 255)
        if oa <= 0:
            return
        out = tuple(round((c[i] * (a / 255) + base[i] * ba * (1 - a / 255)) / oa) for i in range(3))
        self.px[y][x] = (*out, round(oa * 255))

    def rect(self, x, y, w, h, c, a=255):
        for j in range(y, y + h):
            for i in range(x, x + w):
                self.set(i, j, c, a)

    def vgrad(self, x, y, w, h, top, bottom, a=255, mask=None):
        for j in range(h):
            t = j / max(1, h - 1)
            c = mix(top, bottom, t)
            for i in range(w):
                if mask is None or mask(x + i, y + j):
                    self.set(x + i, y + j, c, a)

    def circle(self, cx, cy, r, c, a=255, fill=True, width=1):
        for j in range(int(cy - r - 1), int(cy + r + 2)):
            for i in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(i + 0.5 - cx, j + 0.5 - cy)
                if (fill and d <= r) or (not fill and r - width < d <= r):
                    self.set(i, j, c, a)

    def text(self, x, y, word, color, scale=1, outline=True, shadow=True):
        """Pixel text with an outline; (x, y) is the top left of the outlined box."""
        cell = []
        cx = 0
        for ch in word:
            for r, row in enumerate(FONT[ch].split()):
                for c, bit in enumerate(row):
                    if bit == "1":
                        cell.append((cx + c, r))
            cx += 6
        pts = {(px * scale + dx, py * scale + dy) for px, py in cell for dx in range(scale) for dy in range(scale)}
        o = 1  # outline thickness, whatever the scale
        if outline:
            for px, py in pts:
                for dx in range(-o, o + 1):
                    for dy in range(-o, o + 1):
                        self.set(x + o + px + dx, y + o + py + dy, INK)
        if shadow:
            for px, py in pts:
                self.set(x + o + px, y + o + py + 1, mix(INK, color, 0.35))
        bottom = mix(color, (0, 0, 0), 0.22)
        for px, py in pts:
            self.set(x + o + px, y + o + py, mix(color, bottom, py / (7 * scale)))

    def png(self, path):
        raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in self.px)

        def chunk(tag, data):
            body = struct.pack(">I", len(data)) + tag + data
            return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
        path.write_bytes(data)


def rounded(w, h, r):
    """Mask of a rounded rectangle w x h at the origin."""
    def inside(x, y):
        if x < 0 or y < 0 or x >= w or y >= h:
            return False
        cx = r - x - 0.5 if x < r else (x - (w - r) + 0.5 if x >= w - r else 0)
        cy = r - y - 0.5 if y < r else (y - (h - r) + 0.5 if y >= h - r else 0)
        return cx * cx + cy * cy <= r * r
    return inside


def plate(w, h, color, radius=5):
    """A glassy dark plate: gradient, role-coloured rim, inner glow, corner brackets and a top gleam."""
    c = Canvas(w, h)
    inside = rounded(w, h, radius)
    c.vgrad(0, 0, w, h, (30, 24, 52), (11, 8, 22), 232, inside)
    for y in range(h):  # soft inner glow near the edges in the role colour
        for x in range(w):
            if inside(x, y):
                edge = min(x, y, w - 1 - x, h - 1 - y)
                if 1 <= edge <= 5:
                    c.set(x, y, color, int(46 * (1 - (edge - 1) / 5)))
    for y in range(h):  # rim
        for x in range(w):
            if inside(x, y) and not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1)):
                c.set(x, y, color, 255)
    for y in range(h):  # second, darker rim just inside
        for x in range(w):
            if inside(x, y) and not (inside(x - 2, y) and inside(x + 2, y) and inside(x, y - 2) and inside(x, y + 2)) \
                    and c.get(x, y)[3] and (x, y) != (-1, -1):
                if inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1):
                    c.set(x, y, dim(color, 0.42), 255)
    for x in range(radius, w - radius):  # gleam
        c.set(x, 2, (255, 255, 255), 26)
    bright = lift(color, 0.45)
    for k in range(4):  # corner brackets
        for (sx, sy, dx, dy) in ((3, 3, 1, 1), (w - 4, 3, -1, 1), (3, h - 4, 1, -1), (w - 4, h - 4, -1, -1)):
            c.set(sx + dx * k, sy, bright, 255)
            c.set(sx, sy + dy * k, bright, 255)
    return c


def medallion(c, cx, cy, r, color):
    c.circle(cx, cy, r, (8, 6, 16), 255)
    c.circle(cx, cy, r - 1, mix((26, 20, 46), color, 0.18), 255)
    c.circle(cx, cy, r, color, 255, fill=False, width=2)
    c.circle(cx, cy, r - 3, dim(color, 0.45), 200, fill=False, width=1)
    c.circle(cx - r * 0.35, cy - r * 0.45, r * 0.18, (255, 255, 255), 70)


def top_plate(color):
    c = plate(250, 44, color)
    medallion(c, 22, 22, 15, color)
    # round badge (the digit sprite sits on it)
    c.circle(33, 33, 6, (8, 6, 16), 255)
    c.circle(33, 33, 6, color, 255, fill=False, width=1)
    # groove of the progress bar, ticks every tenth
    c.rect(43, 30, 146, 7, (6, 4, 12), 255)
    c.rect(44, 31, 144, 5, dim(color, 0.2), 255)
    for k in range(1, 10):
        c.rect(45 + k * 140 // 10, 31, 1, 5, (0, 0, 0), 90)
    # timer well
    c.rect(188, 6, 54, 26, (6, 4, 12), 180)
    c.rect(188, 6, 54, 1, color, 120)
    c.rect(188, 31, 54, 1, color, 60)
    return c


def right_panel(color):
    c = plate(128, 108, color)
    medallion(c, 28, 28, 18, color)
    # divider and the static caption
    for x in range(8, 120):
        c.set(x, 52, dim(color, 0.55), 255)
        c.set(x, 53, (255, 255, 255), 14)
    c.text(5, 56, "ALIVE", mix(color, (255, 255, 255), 0.35), shadow=False)
    return c


def vote_plate(color):
    """The "votes against you" plate: caption on the left, the big digits are separate sprites."""
    c = plate(128, 30, color)
    c.text(7, 6, "VOTES", mix(color, (255, 255, 255), 0.35), shadow=False)
    c.text(7, 16, "AGAINST YOU", LIGHT, shadow=False)
    c.rect(84, 6, 40, 20, (6, 4, 12), 170)
    return c


def fill_bar(color, w=8, h=4):
    c = Canvas(w, h)
    c.vgrad(0, 0, w, h, lift(color, 0.5), dim(color, 0.62), 255)
    c.rect(0, 0, w, 1, lift(color, 0.75), 255)
    return c


def dot(kind):
    """One seat of the table strip: alive, dead, the viewer, their ballot, a pack mate / lover."""
    c = Canvas(5, 8)
    fill, edge = {
        "alive": ((255, 246, 214), INK),
        "dead": ((52, 44, 78), (0, 0, 0)),
        "me": ((120, 235, 255), (10, 60, 90)),
        "ballot": ((255, 211, 110), (110, 60, 0)),
        "mate": ((255, 110, 120), (90, 10, 30)),
    }[kind]
    c.rect(0, 1, 4, 6, edge, 150 if kind == "dead" else 255)
    c.rect(1, 2, 2, 4, fill, 255)
    if kind in ("me", "ballot", "mate"):  # a pointer over the seat so it stands out
        c.rect(1, 0, 2, 1, fill, 255)
    return c


BADGES = {
    "lover": ((255, 143, 192), ["01100110", "11111111", "11111111", "01111110", "00111100", "00011000"]),
    "life": ((111, 220, 140), ["00111100", "00011000", "00111100", "01111110", "01111110", "00111100"]),
    "death": ((190, 120, 255), ["00111100", "00011000", "00111100", "01111110", "01111110", "00111100"]),
    "infect": ((150, 190, 90), ["00111100", "01111110", "11011011", "11111111", "01100110", "00111100"]),
    "guard": ((108, 198, 255), ["11111111", "11111111", "11111111", "01111110", "00111100", "00011000"]),
    "mayor": ((255, 211, 110), ["10100101", "11111111", "11111111", "01111110", "01111110", "01111110"]),
    "silenced": ((190, 178, 220), ["01111110", "11011011", "11111111", "10000001", "11111111", "01111110"]),
}


def badge(kind):
    """A 12x12 status badge: a dark chip with a small bitmap in the status colour."""
    color, rows = BADGES[kind]
    c = Canvas(12, 12)
    c.rect(1, 0, 10, 12, (8, 6, 16), 255)
    c.rect(0, 1, 12, 10, (8, 6, 16), 255)
    c.rect(1, 1, 10, 10, mix((26, 20, 46), color, 0.25), 255)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "1":
                c.set(2 + x, 3 + y, color, 255)
    if kind == "death":  # the death potion is the same bottle in purple with a pale cross
        c.rect(5, 6, 2, 1, (255, 255, 255), 255)
    return c


def main():
    out = PACK / "textures" / "font" / "hud"
    out.mkdir(parents=True, exist_ok=True)
    sprites = layout.hud_sprites()
    magick = icons.magick()
    for old in out.glob("*.png"):
        old.unlink()
    for name, w, h in sprites:
        c = Canvas(w, h)
        kind, _, rest = name.partition("_")
        if name.startswith("frame_"):
            c = top_plate(rgb(layout.HUD_PALETTES[rest]))
        elif name.startswith("vframe_"):
            c = vote_plate(rgb(layout.HUD_PALETTES["vote" if rest == "calm" else "danger"]))
        elif name.startswith("fill_"):
            c = fill_bar(rgb(layout.HUD_PALETTES[rest]), w, h)
        elif name.startswith("pframe_"):
            c = right_panel(rgb(layout.HUD_PALETTES[rest]))
        elif name.startswith(("icon_", "picon_")):
            svg = ART / "icons" / f"{rest}.svg"
            if not svg.exists():  # a role whose icon is not drawn yet shows the villager's
                print(f"warning: no icon art/icons/{rest}.svg, using the villager's")
                svg = ART / "icons" / "villager.svg"
            subprocess.run([magick, "-background", "none", "-density", str(72 * w * 2 // 32), str(svg),
                            "-resize", f"{w}x{h}", "-strip", f"PNG32:{out / (name + '.png')}"], check=True)
            continue
        elif name.startswith("label_"):
            c.text(0, 0, rest.replace("_", " ").upper(), LIGHT, scale=2)
        elif name.startswith(("plabel_", "pteam_")):
            tint = LIGHT if name.startswith("plabel_") else (176, 164, 210)
            c.text(0, 0, rest.replace("_", " ").upper(), tint)
        elif name[0] in "dx" and name[1:].isdigit():
            c.text(0, 0, name[1:], DANGER if name[0] == "x" else LIGHT, scale=2)
        elif name == "dcolon":
            c.text(-1, 0, ":", LIGHT, scale=2)
        elif name == "xcolon":
            c.text(-1, 0, ":", DANGER, scale=2)
        elif name == "dslash":
            c.text(-1, 0, "/", (190, 178, 220), scale=2)
        elif name[0] == "s" and name[1:].isdigit():
            c.text(0, 0, name[1:], LIGHT)
        elif name.startswith("dot_"):
            c = dot(rest)
        elif name.startswith("badge_"):
            c = badge(rest)
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
