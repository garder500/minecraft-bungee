#!/usr/bin/env python3
"""ValoBlock interface v2 art (stdlib only, deterministic): menu backgrounds, portraits, HUD glyphs, shader, vanilla HUD blanks.

    python3 resource-pack/art/valoblock_ui.py

Everything lands in resource-pack/valoblock (the pack that is only loaded during a match) and, for the glyph table the
plugin reads, arena-plugin/src/main/resources/valoblock/vb-hud-sprites.txt. The art carries frames, colour and images:
every word of a menu is a window title or an item tooltip. Digits on the HUD are numbers, not lettering.

  font vampyuyu:vbmenus   full-window backgrounds for the agent select and the Armory (the MenuSkin tile technique),
                          one portrait per agent and the space glyphs that place the title text
  font vampyuyu:vbhud     glyphs the core shader moves to their place (health, abilities, ammo, crosshair, hit marker)
  font vampyuyu:vbinline  glyphs that flow inside text (agent heads and weapon icons of the top bar and the kill feed)
  textures/gui/sprites/hud/*   transparent copies of the vanilla hearts, hunger, armour, XP bar and crosshair
  shaders/core/text.*     the base pack's text shader plus the ValoBlock HUD branch
  items: card frames and weapon cards, class tabs, blank filler, wide Lock in
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import fx_shaders  # noqa: E402
import layout  # noqa: E402
import valoblock_items as vi  # noqa: E402
import valoblock_seed as vs  # noqa: E402
import vb_layout as vb  # noqa: E402

PACK = vi.ROOT / "valoblock"
NS = PACK / "assets/vampyuyu"
TEXTURES = NS / "textures/font"
SPRITES_TXT = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/vb-hud-sprites.txt"
AGENTS = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/agents"
CLEAR = (0, 0, 0, 0)

DIGITS = {
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"], "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"], "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"], "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"], "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"], "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
}


def col(hex6, a=255):
    r, g, b = vi.rgb(hex6.lstrip("#"))
    return (r, g, b, a)


def canvas(w, h, fill=CLEAR):
    return [[fill] * w for _ in range(h)]


def rect(img, x, y, w, h, c):
    for yy in range(max(0, y), min(len(img), y + h)):
        for xx in range(max(0, x), min(len(img[0]), x + w)):
            img[yy][xx] = c


def frame(img, x, y, w, h, fill, border, t=1):
    rect(img, x, y, w, h, border)
    rect(img, x + t, y + t, w - 2 * t, h - 2 * t, fill)


def outline(img, c=col("06070a")):
    """1 px outline around every opaque pixel (on transparent neighbours)."""
    h, w = len(img), len(img[0])
    add = []
    for y in range(h):
        for x in range(w):
            if img[y][x][3] == 0 and any(0 <= y + dy < h and 0 <= x + dx < w and img[y + dy][x + dx][3] > 0
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                add.append((y, x))
    for y, x in add:
        img[y][x] = c


def scale(img, k):
    return [[px for px in row for _ in range(k)] for row in img for _ in range(k)]


def blit(dst, src, ox, oy):
    for y, row in enumerate(src):
        for x, px in enumerate(row):
            if px[3] and 0 <= oy + y < len(dst) and 0 <= ox + x < len(dst[0]):
                dst[oy + y][ox + x] = px


def glyph(ch, color, k=1, shadow=None):
    """A 5x7 digit (scaled k) with an optional 1 px drop shadow, returned as (5k+1) x (7k+1) rows."""
    rows = DIGITS[ch]
    img = canvas(5 * k + 1, 7 * k + 1)
    for y, row in enumerate(rows):
        for x, bit in enumerate(row):
            if bit == "1":
                if shadow:
                    rect(img, x * k + 1, y * k + 1, k, k, shadow)
    for y, row in enumerate(rows):
        for x, bit in enumerate(row):
            if bit == "1":
                rect(img, x * k, y * k, k, k, color)
    return img


def agents():
    index = json.loads((AGENTS / "index.json").read_text())
    return [json.loads((AGENTS / f"{a}.json").read_text()) for a in index["agents"]]


def write_png(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(vi.png(rows))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n")


# ------------------------------------------------------------------ vanilla HUD blanks

def hide_vanilla():
    base = PACK / "assets/minecraft/textures/gui/sprites"
    for name, w, h in vb.hidden_sprites():
        write_png(base / f"{name}.png", canvas(w, h))
    return len(vb.hidden_sprites())


# ------------------------------------------------------------------ menu backgrounds

BG, EDGE, PANEL, SLOT = col("12151d"), col("2a2f3c"), col("1c1730"), col("232733")


def window_base():
    """The 176 x 222 window: dark plate, title strip, the player's inventory as 36 empty slot frames."""
    img = canvas(176, vb.GUI_H, BG)
    frame(img, 0, 0, 176, vb.GUI_H, BG, col("0b0d12"), 2)
    rect(img, 2, 2, 172, 14, col("0d0f15"))
    for r in range(3):
        for c in range(9):
            frame(img, 7 + 18 * c, 139 + 18 * r, 18, 18, col("171a22"), col("0d0f15"))
    for c in range(9):
        frame(img, 7 + 18 * c, 197, 18, 18, col("171a22"), col("0d0f15"))
    return img


def slot_frame(img, col_, row, w=1, h=1, fill=SLOT, border=EDGE):
    x, y = vb.slot_xy(col_, row)
    frame(img, x - 1, y - 1, 18 * w, 18 * h, fill, border)


def select_background():
    img = window_base()
    x, y = vb.slot_xy(0, 0)
    frame(img, x - 1, y - 1, 18 * vb.SEL_PORTRAIT_COLS, 18 * vb.SEL_PORTRAIT_ROWS, PANEL, col("c9a8ff"), 2)
    rect(img, x + 4, y + 18 * vb.SEL_PORTRAIT_ROWS - 9, 18 * vb.SEL_PORTRAIT_COLS - 10, 4, col("120e22"))  # the floor under the portrait
    for tr in range(vb.SEL_TILE_ROWS):
        for tc in range(vb.SEL_TILE_COLS):
            slot_frame(img, vb.SEL_ROSTER_COL + 2 * tc, tr, 2, 1)
            sx, sy = vb.slot_xy(vb.SEL_ROSTER_COL + 2 * tc + 1, tr)
            rect(img, sx - 1, sy - 1, 1, 18, col("171a22"))
    slot_frame(img, 0, 4, 2, 1, col("2a2a36"), col("9aa3b5"))            # Random
    for c in (3, 4, 5, 6):
        slot_frame(img, c, 4)                                              # role filters
    for c in range(5):
        slot_frame(img, c, 5, 1, 1, col("1e2230"), col("3a3f4c"))          # the team
    slot_frame(img, 5, 5, 4, 1, col("1f5a35"), col("5ad07a"))              # Lock in
    return img


def armory_background():
    img = window_base()
    for r in range(6):
        slot_frame(img, vb.ARM_TAB_COL, r, 1, 1, col("232733"), col("3a3f4c"))
    for k in range(vb.ARM_CARD_COLS):
        for j in range(vb.ARM_CARD_ROWS):
            slot_frame(img, vb.ARM_CARD_COL + 2 * k, 2 * j, 2, 2, col("1d212c"))
    for k in range(2):
        slot_frame(img, vb.ARM_CARD_COL + 2 * k, vb.ARM_SHIELD_ROW, 2, 2, col("1f3557"), col("7fc8e8"))
    for s in vb.ARM_ABILITY_SLOTS:
        slot_frame(img, s % 9, s // 9, 1, 1, col("232733"), col("6affd8"))
    return img


def cut_background(name, base, img):
    """Cuts the window into COLS x ROWS tiles at 4 texture px per GUI px and returns the font providers."""
    big = scale(img, layout.PX_PER_GUI)
    th = layout.tile_height(vb.GUI_H, vb.ROWS)
    providers = []
    for r in range(vb.ROWS):
        for c in range(layout.COLS):
            tile = [row[c * layout.TILE_W * 4:(c + 1) * layout.TILE_W * 4] for row in big[r * th * 4:(r + 1) * th * 4]]
            file = f"{name}_{r}{c}"
            write_png(TEXTURES / f"{file}.png", tile)
            providers.append({"type": "bitmap", "file": f"vampyuyu:font/{file}.png", "ascent": layout.ASCENT - r * th, "height": th,
                              "chars": [chr(base + r * layout.COLS + c)]})
    return providers


# ------------------------------------------------------------------ portraits

def portrait(agent, silhouette=False):
    """A 48 x 64 full-body figure from the agent's colour plan: the 8 x 8 face scaled x3, torso, arms, legs, a glow mark."""
    c = agent["colors"] if agent else {}
    pal = agent["face"]["palette"] if agent else {}
    grey = col("2a2f3c")

    def pick(hex6):
        return grey if silhouette else col(hex6)

    img = canvas(vb.PORTRAIT_W, vb.PORTRAIT_H)
    if agent:
        face = agent["face"]
        for y, row in enumerate(face["rows"]):
            for x, ch in enumerate(row):
                rect(img, 12 + x * 3, 2 + y * 3, 3, 3, pick(pal[ch]))
        rect(img, 20, 26, 8, 3, pick(pal["s"]))
        rect(img, 12, 29, 24, 20, pick(c["torso"]))
        rect(img, 12, 29, 24, 3, pick(c["trim"]))
        rect(img, 12, 46, 24, 3, pick(c["trim"]))
        rect(img, 22, 36, 4, 6, pick(c["glow"]))
        rect(img, 5, 29, 7, 18, pick(c["arm"]))
        rect(img, 36, 29, 7, 18, pick(c["arm"]))
        rect(img, 5, 47, 7, 4, pick(pal["s"]))
        rect(img, 36, 47, 7, 4, pick(pal["s"]))
        rect(img, 13, 49, 10, 11, pick(c["legs"]))
        rect(img, 25, 49, 10, 11, pick(c["legs"]))
        rect(img, 13, 60, 10, 4, pick(c["boots"]))
        rect(img, 25, 60, 10, 4, pick(c["boots"]))
        rect(img, 5, 29, 7, 2, pick(c["trim"]))
        rect(img, 36, 29, 7, 2, pick(c["trim"]))
    else:  # nobody picked yet: a plain silhouette
        rect(img, 12, 2, 24, 24, grey)
        rect(img, 12, 29, 24, 20, grey)
        rect(img, 5, 29, 7, 22, grey)
        rect(img, 36, 29, 7, 22, grey)
        rect(img, 13, 49, 10, 15, grey)
        rect(img, 25, 49, 10, 15, grey)
    outline(img)
    return img


def menu_font():
    providers = [{"type": "space", "advances": {chr(k): v for k, v in vb.ADVANCES.items()}}]
    providers += cut_background("vb_select", vb.SELECT_BASE, select_background())
    providers += cut_background("vb_armory", vb.ARMORY_BASE, armory_background())
    figures = [(vb.PORTRAIT_BASE + i, a) for i, a in enumerate(agents())] + [(vb.PORTRAIT_NONE, None)]
    for code, a in figures:
        name = f"vb_portrait_{a['id'] if a else 'none'}"
        write_png(TEXTURES / f"{name}.png", portrait(a))
        providers.append({"type": "bitmap", "file": f"vampyuyu:font/{name}.png", "ascent": layout.ASCENT - vb.PORTRAIT_Y, "height": vb.PORTRAIT_H,
                          "chars": [chr(code)]})
    write_json(NS / f"font/{vb.FONT_MENUS}.json", {"providers": providers})
    return len(providers)


# ------------------------------------------------------------------ HUD sprites

def agent_icon(agent, slot, on):
    """16 x 16 ability icon: a disc, a diamond, a ring and a star for slots 4 to 7, in the agent's own colours."""
    c = agent["colors"]
    main, glow, dark = col(c["trim"] if slot != 7 else c["glow"]), col(c["glow"]), col("1a1c24")
    if not on:
        main, glow = col("5a5f6c"), col("7a7f8c")
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            d = (dx * dx + dy * dy) ** 0.5
            hit = {4: d <= 6.5, 5: dx + dy <= 7.5, 6: 4.2 <= d <= 6.8, 7: dx * dy <= 6.0 and dx + dy <= 8.5}[slot]
            if hit:
                img[y][x] = main
            if hit and d <= 2.2:
                img[y][x] = glow
    outline(img, dark)
    return img


def tile_sprite(slot, on):
    img = canvas(*vb.TILE)
    edge = col("ffd36a") if slot == 7 and on else col("c9a8ff") if on else col("3a3f4c")
    frame(img, 0, 0, 30, 38, col("0b0d12", 217), edge)
    key = "U" if slot == 7 else str(slot)
    blit(img, glyph(key, col("ffffff") if on else col("7a7f8c")), 12, 28)
    return img


def plate_sprite(kind):
    """The 88 x 36 plate under the digits. Coordinates are plate-relative: element x/y minus the plate's own (see vb_layout)."""
    img = canvas(*vb.PLATE)
    frame(img, 0, 0, 88, 36, col("0b0d12", 217), col("2a2f3c"))
    if kind == "hp":
        rect(img, 6, 21, 50, 4, col("232733"))                       # the health track (hp_bar: x 10, y -21)
        rect(img, 6, 29, 50, 3, col("1f3557"))                       # the shield track (sh_bar: x 10, y -13)
        rect(img, 57, 4, 1, 28, col("2a2f3c"))                       # the divider before the shield figures
        rect(img, 61, 17, 6, 5, col("7fc8e8"))                       # a small shield mark under the figures
        rect(img, 62, 22, 4, 2, col("7fc8e8"))
        rect(img, 63, 24, 2, 1, col("7fc8e8"))
    else:
        for k in range(13):                                          # the slash between the magazine and its size
            rect(img, 36 + (12 - k) // 3, 6 + k, 2, 1, col("9aa3b5"))
    return img


def cross_sprite(level):
    """Four ticks around the middle; the gap grows with the spread (0 tight .. 3 wide)."""
    img = canvas(*vb.CROSS)
    gap, white = 2 + 2 * level, col("ffffff")
    for i in range(gap, gap + 4):
        for x, y in ((10 + i, 10), (10 - i, 10), (10, 10 + i), (10, 10 - i)):
            if 0 <= x < 21 and 0 <= y < 21:
                img[y][x] = white
    img[10][10] = col("ffffff", 160)
    return img


def hit_sprite(kind):
    img = canvas(*vb.CROSS)
    c = {"w": col("ffffff"), "h": col("ffe94a"), "k": col("ff4a4a")}[kind]
    for i in range(3, 8):
        for x, y in ((10 + i, 10 + i), (10 - i, 10 + i), (10 + i, 10 - i), (10 - i, 10 - i)):
            img[y][x] = c
            if kind == "k":
                img[y][x - 1 if x > 10 else x + 1] = c
    return img


def hud_sprites():
    """(name, rows) of every shader-positioned sprite, in glyph order. A sprite has the size of the element it fills."""
    out = [("hp_plate", plate_sprite("hp")), ("am_plate", plate_sprite("am"))]
    for slot in (4, 5, 6, 7):
        out += [(f"tile_{slot}_on", tile_sprite(slot, True)), (f"tile_{slot}_off", tile_sprite(slot, False))]
    for a in agents():
        for slot in (4, 5, 6, 7):
            out += [(f"ic_{a['id']}_{slot}_on", agent_icon(a, slot, True)), (f"ic_{a['id']}_{slot}_off", agent_icon(a, slot, False))]
    pip_on, pip_off = canvas(*vb.PIP, col("ffd36a")), canvas(*vb.PIP, col("3a3f4c"))
    out += [("pip_on", pip_on), ("pip_off", pip_off)]
    for tag, color in (("w", col("ffffff")), ("r", col("ff5a5a"))):
        out += [(f"bd_{tag}_{d}", pad(glyph(d, color, 2, col("000000", 170)), *vb.BIG)) for d in "0123456789"]
    out.append(("bd_blank", canvas(*vb.BIG)))
    for tag, color in (("w", col("ffffff")), ("b", col("7fc8e8")), ("g", col("9aa3b5"))):
        out += [(f"sd_{tag}_{d}", pad(glyph(d, color, 1, col("000000", 170)), *vb.SMALL)) for d in "0123456789"]
    out.append(("sd_blank", canvas(*vb.SMALL)))
    out += [("hp_bar", canvas(8, 4, col("5ad07a"))), ("hp_bar_low", canvas(8, 4, col("ff5a5a"))), ("sh_bar", canvas(8, 3, col("7fc8e8")))]
    out += [(f"xh_{i}", cross_sprite(i)) for i in range(4)]
    out += [(f"hit_{k}", hit_sprite(k)) for k in "whk"]
    # The Rift Seed: the carrier's icon (two frames, it pulses in a site) and the planting track and fill (appended: older ids stay).
    track = canvas(*vb.PLANT_TRACK)
    frame(track, 0, 0, 52, 7, col("0b0d12", 217), col("3a1a6a"))
    out += [("seed_on", vs.seed_pixels(16)), ("seed_glow", vs.seed_pixels(16, glow=True)), ("plant_track", track),
            ("plant_bar", canvas(8, 3, col("ff4a8a")))]
    return out


def pad(img, w, h):
    out = canvas(w, h)
    blit(out, img, 0, 0)
    return out


def head_sprite(agent, state):
    """The 8 x 8 face in a 10 x 10 frame: alive, dead (grey with a red cross) or not chosen yet."""
    img = canvas(vb.HEAD, vb.HEAD, col("06070a"))
    if state == "none":
        rect(img, 1, 1, 8, 8, col("3a3f4c"))
        rect(img, 4, 4, 2, 2, col("5a5f6c"))
        return img
    pal, rows = agent["face"]["palette"], agent["face"]["rows"]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            r, g, b, a = col(pal[ch])
            if state == "dead":
                v = (r + g + b) // 6
                r = g = b = v + 20
            img[1 + y][1 + x] = (r, g, b, a)
    if state == "dead":
        for i in range(1, 9):
            img[i][i] = col("ff4a4a")
            img[i][9 - i] = col("ff4a4a")
    return img


def skull_sprite():
    rows = ["..1111..", ".111111.", "11011011", "11011011", ".111111.", "..1111..", "..1111..", "..1.1.1."]
    return [[col("ffe94a") if ch == "1" else CLEAR for ch in row] for row in rows]


def inline_sprites():
    """(name, rows, ascent) of the glyphs that flow inside text. Weapon icons are the 24 x 10 art of the Armory sprites."""
    out = [("head_none", head_sprite(None, "none"), 8)]
    for a in agents():
        out += [(f"head_{a['id']}", head_sprite(a, "alive"), 8), (f"head_{a['id']}_dead", head_sprite(a, "dead"), 8)]
    for w in vi.weapons():
        out.append((f"gun_{w['id']}", vi.sprite(w["texture"], w["metal"], 1.0, 24)[7:17], 8))
    out.append(("skull", skull_sprite(), 7))
    # A squad head with the seed badge in its corner: what a teammate sees on the Seed carrier (the enemy never does).
    for a in agents():
        head = head_sprite(a, "alive")
        blit(head, vs.badge_pixels(), 5, 5)
        out.append((f"head_{a['id']}_seed", head, 8))
    head = head_sprite(None, "none")
    blit(head, vs.badge_pixels(), 5, 5)
    out.append(("head_none_seed", head, 8))
    return out


def hud_fonts():
    shader = hud_sprites()
    providers, negs, lines = [], {}, []
    for i, (name, rows) in enumerate(shader):
        write_png(TEXTURES / f"vbhud_{name}.png", rows)
        w, h = len(rows[0]), len(rows)
        providers.append({"type": "bitmap", "file": f"vampyuyu:font/vbhud_{name}.png", "ascent": h, "height": h, "chars": [chr(vb.SPRITE_BASE + i)]})
        negs[chr(vb.NEG_BASE + i)] = -(w + 1)
        lines.append(f"sprite {i} {name}")
    providers.insert(0, {"type": "space", "advances": negs})
    write_json(NS / f"font/{vb.FONT_HUD}.json", {"providers": providers})
    lines += [f"element {i} {e[0]}" for i, e in enumerate(vb.HUD_ELEMENTS)]
    inline = inline_sprites()
    providers = []
    for i, (name, rows, ascent) in enumerate(inline):
        write_png(TEXTURES / f"vbinline_{name}.png", rows)
        providers.append({"type": "bitmap", "file": f"vampyuyu:font/vbinline_{name}.png", "ascent": ascent, "height": len(rows),
                          "chars": [chr(vb.SPRITE_BASE + i)]})
        lines.append(f"inline {i} {name}")
    write_json(NS / f"font/{vb.FONT_INLINE}.json", {"providers": providers})
    SPRITES_TXT.parent.mkdir(parents=True, exist_ok=True)
    SPRITES_TXT.write_text("\n".join(lines) + "\n")
    return len(shader), len(inline)


# ------------------------------------------------------------------ shader

def shader_sources():
    """The base pack's text shader with a second HUD branch: red 84, green 160 + element, blue the bar width."""
    anchors = ", ".join(str(e[1]) for e in vb.HUD_ELEMENTS)
    boxes = ",\n    ".join(f"vec4({e[2]}.0, {e[3]}.0, {e[4]}.0, {e[5]}.0)" for e in vb.HUD_ELEMENTS)
    stretch = ", ".join(str(e[6]) for e in vb.HUD_ELEMENTS)
    table = (f"const int VB_COUNT = {vb.HUD_COUNT};\nconst int VB_ANCHOR[VB_COUNT] = int[]({anchors});\n"
             f"const int VB_STRETCH[VB_COUNT] = int[]({stretch});\nconst vec4 VB_BOX[VB_COUNT] = vec4[](\n    {boxes});\n\n")
    branch = f"""    }} else if (rgb.r == {vb.HUD_R} && rgb.g >= {vb.HUD_ID_BASE} && rgb.g < {vb.HUD_ID_BASE} + VB_COUNT) {{
        // A ValoBlock HUD glyph: the quad goes to its element's box in GUI pixels, counted from one of 7 anchors
        // (0-2 top left, centre, right; 3-5 bottom; 6 screen centre). The blue channel stretches a bar.
        int e = rgb.g - {vb.HUD_ID_BASE};
        vec2 gui = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
        int a = VB_ANCHOR[e];
        float ax = (a == 0 || a == 3) ? 0.0 : (a == 2 || a == 5) ? gui.x : floor(gui.x * 0.5);
        float ay = a <= 2 ? 0.0 : a <= 5 ? gui.y : floor(gui.y * 0.5);
        vec4 box = VB_BOX[e];
        float w = VB_STRETCH[e] == 1 ? max(1.0, floor(box.z * float(rgb.b) / 255.0 + 0.5)) : box.z;
        int k = gl_VertexIndex % 4;
        vec2 corner = vec2((k == 2 || k == 3) ? w : 0.0, (k == 1 || k == 2) ? box.w : 0.0);
        clip = ProjMat * ModelViewMat * vec4(vec2(ax, ay) + box.xy + corner, Position.z, 1.0);
        fx = 10;
    }} else if (rgb.r == {vb.HUD_R // 4} && rgb.g >= {vb.HUD_ID_BASE // 4 - 1} && rgb.g <= {(vb.HUD_ID_BASE + vb.HUD_COUNT) // 4 + 1}) {{
        clip = vec4(2.0, 2.0, 2.0, 1.0); // the text shadow of a HUD glyph (colour / 4)
"""
    vsh = fx_shaders.VSH_263
    marker = "    } else if (rgb.r == 78 && rgb.g == 92"
    assert vsh.count(marker) == 1 and vsh.count("void main() {") == 1
    vsh = vsh.replace(marker, branch + marker).replace("void main() {", table + "void main() {")
    return vsh, fx_shaders.FSH_263


def write_shaders():
    out = PACK / "assets/minecraft/shaders/core"
    out.mkdir(parents=True, exist_ok=True)
    vsh, fsh = shader_sources()
    (out / "text.vsh").write_text(vsh)
    (out / "text.fsh").write_text(fsh)


# ------------------------------------------------------------------ items

def item(name, model, extra=None):
    d = {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/valoblock/{name}"}}
    d.update(extra or {})
    write_json(NS / f"items/valoblock/{name}.json", d)
    write_json(NS / f"models/item/valoblock/{name}.json", model)


def frame_texture(border, fill):
    img = canvas(32, 32)
    frame(img, 0, 0, 32, 32, fill, border, 1)
    return img


CLASSES = ["Sidearms", "SMGs", "Shotguns", "Rifles", "Snipers", "Heavy"]
CLASS_REPRESENTATIVE = ["gravebell", "mistral", "scrapper", "flux_rifle", "watchers_eye", "grinder"]
CARD_SCALE, CARD_SHIFT = 2.25, 9


def items():
    n = 0
    states = {"": (col("3a3f4c"), col("1d212c")), "_owned": (col("5ad07a"), col("17301f")), "_locked": (col("2a2f3c"), col("14161c"))}
    for suffix, (border, fill) in states.items():
        write_png(NS / f"textures/item/valoblock/card_frame{suffix}.png", frame_texture(border, fill))
    display = {"gui": {"scale": [CARD_SCALE] * 3, "translation": [CARD_SHIFT, -CARD_SHIFT, 0]}}
    for w in vi.weapons():
        for suffix in states:
            sprite = w["id"] + ("_locked" if suffix == "_locked" else "")
            item(f"card_{w['id']}{suffix}", {"parent": "minecraft:item/generated", "display": display,
                                              "textures": {"layer0": f"vampyuyu:item/valoblock/card_frame{suffix}", "layer1": f"vampyuyu:item/valoblock/{sprite}"}},
                 {"oversized_in_gui": True})
            n += 1
    reps = {w["id"]: w for w in vi.weapons()}
    for i, name in enumerate(CLASSES):
        w = reps[CLASS_REPRESENTATIVE[i]]
        for sel in (False, True):
            img = canvas(32, 32)
            frame(img, 0, 0, 32, 32, col("2a2622") if sel else col("1e222c"), col("ffd36a") if sel else col("3a3f4c"), 2)
            blit(img, vi.sprite(w["texture"], w["metal"]), 0, 0)
            tag = f"tab_{name.lower()}" + ("_sel" if sel else "")
            write_png(NS / f"textures/item/valoblock/{tag}.png", img)
            item(tag, {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/valoblock/{tag}"}})
            n += 1
    write_json(NS / "items/valoblock/blank.json", {"model": {"type": "minecraft:empty"}})
    return n


def main():
    print(f"hid {hide_vanilla()} vanilla HUD sprites")
    print(f"menu font: {menu_font()} providers")
    shader, inline = hud_fonts()
    print(f"HUD font: {shader} shader sprites, {inline} inline sprites, {vb.HUD_COUNT} elements")
    write_shaders()
    print(f"items: {items()}")


if __name__ == "__main__":
    main()
