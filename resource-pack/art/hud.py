#!/usr/bin/env python3
"""Draws the custom HUD sprites and declares them as the vampyuyu:hud2 font.

    python3 resource-pack/art/hud.py

Sprites are drawn 1 texel per GUI pixel (the core shader scales them with the GUI). Role and
phase icons come from art/icons.py's SVGs, drawn through ImageMagick. No word is painted here: the
fonts of art/hud_font.py become one sprite per character and the plugin writes the text in the
player's language. Also writes arena-plugin/src/main/resources/hud-sprites.txt, which tells the
plugin the glyph of each sprite, the advance of each character and where each line of text goes.
"""
import json, math, pathlib, struct, subprocess, sys, zlib

ART = pathlib.Path(__file__).resolve().parent
ROOT = ART.parent.parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
sys.path.insert(0, str(ART.parent))
sys.path.insert(0, str(ART))
import layout  # noqa: E402
import icons  # noqa: E402
import hud_font  # noqa: E402
import hud_sheet  # noqa: E402  (its PNG reader)

INK = (12, 9, 22)
LIGHT = (250, 240, 218)
MUTED = (176, 164, 210)
DANGER = (255, 88, 72)
FONT_TINT = {"h": LIGHT, "b": LIGHT, "m": MUTED}


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

    def ink(self, x, y, rows, color, shadow=True, tall=None):
        """Draws a bitmap ("0"/"1" rows) at (x, y) with a dark outline all around, a soft shadow under it and a
        slight top-to-bottom shade. `tall` is the height the shade runs over (the capital's, for a glyph with an
        accent)."""
        pts = [(c, r) for r, row in enumerate(rows) for c, bit in enumerate(row) if bit == "1"]
        for px, py in pts:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    self.set(x + px + dx, y + py + dy, INK)
        if shadow:
            for px, py in pts:
                self.set(x + px, y + py + 1, mix(INK, color, 0.35))
        bottom = mix(color, (0, 0, 0), 0.22)
        tall = tall or len(rows)
        for px, py in pts:
            self.set(x + px, y + py, mix(color, bottom, max(0, min(1, py / tall))))

    def art(self, x, y, rows, tones):
        """Draws a small pixel drawing: one letter per pixel, `tones` maps the letters to colours ('.' is empty)."""
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch != ".":
                    self.set(x + c, y + r, tones[ch])

    def anchor(self):
        """Makes the sprite as wide as its file for the game: a bitmap glyph advances by its ink, cut at the last
        column that is not empty, so a nearly transparent pixel (the shader drops it) goes in the last column.
        The negative advance after every sprite is then exactly -(width + 1) and the action bar stays centred."""
        if not any(self.px[y][self.w - 1][3] for y in range(self.h)):
            self.px[self.h - 1][self.w - 1] = (0, 0, 0, 1)

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


def plate(w, h, color, radius=4):
    """A glassy dark plate: gradient, role-coloured rim, inner glow, corner brackets and a top gleam."""
    c = Canvas(w, h)
    inside = rounded(w, h, radius)
    c.vgrad(0, 0, w, h, (30, 24, 52), (11, 8, 22), 232, inside)
    for y in range(h):  # soft inner glow near the edges in the role colour
        for x in range(w):
            if inside(x, y):
                edge = min(x, y, w - 1 - x, h - 1 - y)
                if 1 <= edge <= 4:
                    c.set(x, y, color, int(46 * (1 - (edge - 1) / 4)))
    for y in range(h):  # rim
        for x in range(w):
            if inside(x, y) and not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1)):
                c.set(x, y, color, 255)
    for y in range(h):  # second, darker rim just inside
        for x in range(w):
            if inside(x, y) and not (inside(x - 2, y) and inside(x + 2, y) and inside(x, y - 2) and inside(x, y + 2)):
                if inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1):
                    c.set(x, y, dim(color, 0.42), 255)
    for x in range(radius, w - radius):  # gleam
        c.set(x, 2, (255, 255, 255), 26)
    bright = lift(color, 0.45)
    for k in range(3):  # corner brackets
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


def well(c, x, y, w, h, color):
    """A dark recess with a lit top edge: the clock's, the ballots' and the time bar's track."""
    c.rect(x, y, w, h, (6, 4, 12), 180)
    c.rect(x, y, w, 1, color, 120)
    c.rect(x, y + h - 1, w, 1, color, 60)


def box_of(element):
    """(x, y) of an element inside its plate: the top plate, the role panel or the vote plate."""
    boxes = {e[0]: e for e in layout.HUD_ELEMENTS}
    owner = "frame" if boxes[element][1] == 0 else "vframe" if element[0] == "v" else "pframe"
    return boxes[element][2] - boxes[owner][2], boxes[element][3] - boxes[owner][3]


def top_plate(color):
    """Phase medallion with the round on its rim, the label over the time bar, the clock in its well."""
    c = plate(188, 34, color)
    ix, iy = box_of("icon")
    medallion(c, ix + 10, iy + 10, 13, color)
    rx, ry = box_of("round")   # the round's digit sits on a small disc
    c.circle(rx + 3.5, ry + 4.5, 5.5, (8, 6, 16), 255)
    c.circle(rx + 3.5, ry + 4.5, 5.5, color, 255, fill=False, width=1)
    fx, fy = box_of("fill")
    c.rect(fx - 1, fy - 1, 106, 5, (6, 4, 12), 200)
    c.rect(fx, fy, 104, 3, dim(color, 0.28), 255)
    tx, _ = box_of("t1")
    well(c, tx - 4, 6, 36, 22, color)
    return c


PERSON = ["..aaa..", "..aba..", "..aaa..", ".......", ".aaaaa.", "aabaaaa", "aaaaaaa"]


def right_panel(color):
    """Role medallion, the head count and the badge row beside it; under the divider the role, the team and
    the table strip."""
    c = plate(96, 78, color)
    ix, iy = box_of("picon")
    medallion(c, ix + 12, iy + 12, 14, color)
    tone = lift(color, 0.35)
    c.art(39, 8, PERSON, {"a": tone, "b": lift(color, 0.75)})   # "players alive", without a word
    for x in range(6, 90):
        c.set(x, 38, dim(color, 0.55), 255)
        c.set(x, 39, (255, 255, 255), 14)
    return c


def vote_plate(color):
    """The "votes against you" plate: the caption is text, the big digits are separate sprites."""
    c = plate(96, 26, color)
    vx, vy = box_of("v1")
    well(c, vx - 2, vy - 1, 21, 16, color)
    return c


def fill_bar(color, w=8, h=3):
    c = Canvas(w, h)
    c.vgrad(0, 0, w, h, lift(color, 0.5), dim(color, 0.62), 255)
    c.rect(0, 0, w, 1, lift(color, 0.75), 255)
    return c


def dot(kind):
    """One seat of the table strip (3 px wide, 1 px apart): alive, dead, the viewer, their ballot, a pack mate
    or lover."""
    c = Canvas(4, 7)
    fill = {"alive": (255, 246, 214), "dead": (74, 64, 104), "me": (120, 235, 255), "ballot": (255, 211, 110),
            "mate": (255, 110, 120)}[kind]
    if kind == "dead":       # a short dim stub
        c.rect(0, 4, 3, 3, fill, 255)
        c.rect(0, 6, 3, 1, dim(fill, 0.6), 255)
        return c
    top = 0 if kind in ("me", "ballot", "mate") else 2   # the seats that matter to the viewer stand taller
    c.rect(0, top, 3, 7 - top, fill, 255)
    c.rect(0, top, 3, 1, lift(fill, 0.5), 255)
    c.rect(0, 6, 3, 1, dim(fill, 0.6), 255)
    c.rect(2, top + 1, 1, 5 - top, dim(fill, 0.82), 255)
    return c


# Status badges: a 13 px chip like the medallions (dark disc, ring in the status colour) around a 7 x 7 drawing.
# Letters: a the status colour, b its light, c its shade, w white, r red.
BADGES = {
    "lover": ((255, 143, 192), [".aa.aa.", "abaaaaa", "abaaaaa", "aaaaaac", ".aaaac.", "..aac..", "...c..."]),
    "life": ((111, 220, 140), ["..www..", "...b...", "..aaa..", ".aawaa.", ".awwwa.", ".aawac.", "..ccc.."]),
    "death": ((190, 120, 255), ["..www..", "...b...", "..aaa..", ".awawa.", ".aawaa.", ".awawc.", "..ccc.."]),
    "infect": ((150, 200, 90), ["...a...", ".a.a.a.", "..aba..", "aabbbaa", "..aba..", ".a.a.a.", "...a..."]),
    "guard": ((108, 198, 255), ["aaaaaaa", "abawaac", "awwwwwc", "aaawaac", ".aawac.", "..awc..", "...c..."]),
    "mayor": ((255, 211, 110), [".......", "a..b..a", "a.aba.a", "abababa", "aaaaaaa", "awawawa", "ccccccc"]),
    "silenced": ((190, 178, 220), [".aaaaa.", "abaaaaa", "aaaaaaa", "aaaaaac", ".aaaac.", ".ac....", ".c....."]),
}


def badge(kind):
    color, rows = BADGES[kind]
    c = Canvas(13, 13)
    c.circle(6.5, 6.5, 6.5, (8, 6, 16), 255)
    c.circle(6.5, 6.5, 5.5, mix((26, 20, 46), color, 0.2), 255)
    c.circle(6.5, 6.5, 6.5, color, 255, fill=False, width=1)
    c.art(3, 3, rows, {"a": color, "b": lift(color, 0.6), "c": dim(color, 0.6), "w": (255, 255, 255)})
    if kind == "silenced":   # struck through, like a no-entry sign
        for k in range(9):
            c.set(10 - k, 2 + k, (255, 72, 72))
            c.set(9 - k, 2 + k, (150, 24, 40))
    return c


def char_cell(font, ch):
    """One character in its font's cell: the ink starts one pixel in (the outline) and two rows down (the accents)."""
    above, rows, below = hud_font.glyph(font, ch)
    c = Canvas(*layout.HUD_FONT_CELLS[font])
    assert len(rows[0]) + 2 <= c.w and len(rows) + hud_font.ABOVE + 3 == c.h, f"{font} {ch!r} does not fit its cell"
    c.ink(1, 1, above + rows + [below], FONT_TINT[font], tall=len(rows) + hud_font.ABOVE)
    return c


def clock_cell(ch, color, w, h):
    c = Canvas(w, h)
    c.ink(1, 1, hud_font.CLOCK[ch].split(), color)
    return c


def round_digit(d):
    c = Canvas(7, 9)
    c.ink(1, 1, hud_font.REGULAR[d].split(), LIGHT, shadow=False)
    return c


def medal_rows(icon, w, h):
    """w x h RGBA rows of a role's medallion, area-averaged (premultiplied) from its 256 px head render; None for the
    icons that are not a role's (moon, sun...), which stay drawn from their SVG."""
    import werewolf_art
    role = werewolf_art.icon_roles().get(icon)
    src = werewolf_art.SRC / f"medal_{role}.png" if role else None
    if not src or not src.exists():
        return None
    rows = werewolf_art.load(src)
    sh, sw = len(rows), len(rows[0])
    out = []
    for y in range(h):
        y0, y1 = y * sh / h, (y + 1) * sh / h
        line = []
        for x in range(w):
            x0, x1 = x * sw / w, (x + 1) * sw / w
            r = g = b = a = n = 0
            for yy in range(int(y0), min(sh, int(y1 + 0.999))):
                for xx in range(int(x0), min(sw, int(x1 + 0.999))):
                    pr, pg, pb, pa = rows[yy][xx]
                    r += pr * pa
                    g += pg * pa
                    b += pb * pa
                    a += pa
                    n += 1
            line.append((0, 0, 0, 0) if a == 0 else (round(r / a), round(g / a), round(b / a), 255 if a / n >= 128 else 0))
        out.append(line)
    return out


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
        elif name.startswith(("icon_", "picon_")) and medal_rows(rest, w, h):
            c.px = medal_rows(rest, w, h)   # a role: its Minecraft-skin head render (werewolf_art, from Blockbench)
        elif name.startswith(("icon_", "picon_")):
            svg = ART / "icons" / f"{rest}.svg"
            if not svg.exists():  # a role whose icon is not drawn yet shows the villager's
                print(f"warning: no icon art/icons/{rest}.svg, using the villager's")
                svg = ART / "icons" / "villager.svg"
            subprocess.run([magick, "-background", "none", "-density", str(72 * w * 2 // 32), str(svg),
                            "-resize", f"{w}x{h}", "-strip", f"PNG32:{out / (name + '.png')}"], check=True)
            pw, ph, rows = hud_sheet.png_read(out / f"{name}.png")
            assert (pw, ph) == (w, h), name
            if not any(row[(w - 1) * 4 + 3] for row in rows):   # same anchor pixel as the drawn sprites
                c.px = [[tuple(row[x * 4:x * 4 + 4]) for x in range(w)] for row in rows]
                c.anchor()
                c.png(out / f"{name}.png")
            continue
        elif kind in ("fh", "fb", "fm"):
            c = char_cell(kind[1], chr(int(rest, 16)))
        elif name in ("dcolon", "xcolon"):
            c = clock_cell(":", DANGER if name[0] == "x" else LIGHT, w, h)
        elif name[0] in "dx" and name[1:].isdigit():
            c = clock_cell(name[1:], DANGER if name[0] == "x" else LIGHT, w, h)
        elif name[0] == "s" and name[1:].isdigit():
            c = round_digit(name[1:])
        elif name.startswith("dot_"):
            c = dot(rest)
        elif name.startswith("badge_"):
            c = badge(rest)
        assert (c.w, c.h) == (w, h), name
        c.anchor()
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
    # the text: each character's advance, each font's space, each line's font, alignment and widest width
    for font in layout.HUD_FONT_CELLS:
        lines.append(f"space {hud_font.SPACE[font]} {font}")
        for ch in layout.HUD_CHARS:
            lines.append(f"advance {hud_font.advance(font, ch)} {hud_font.sprite(font, ch)}")
    for name, (font, align, limit) in layout.HUD_TEXT.items():
        lines.append(f"text {name} {font} {align} {limit}")
    res = ROOT / "arena-plugin" / "src" / "main" / "resources" / "hud-sprites.txt"
    res.write_text("\n".join(lines) + "\n")
    print("drew", len(sprites), "HUD sprites")


if __name__ == "__main__":
    main()
