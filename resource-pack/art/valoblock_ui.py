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
import valoblock_ability_icons as icons  # noqa: E402
import valoblock_items as vi  # noqa: E402
import valoblock_menukit as kit  # noqa: E402
import valoblock_portraits as portraits  # noqa: E402
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
# 3 x 5 digits for the small figures (shield, reserve) and the ability keys.
TINY = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"], "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "011", "001", "111"], "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"], "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"], "U": ["101", "101", "101", "101", "111"],
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


def glyph(ch, color, k=1, shadow=None, font=None):
    """A 5x7 digit (scaled k; font=TINY for 3x5) with an optional 1 px drop shadow, returned as (w*k+1) x (h*k+1) rows."""
    rows = (font or DIGITS)[ch]
    img = canvas(len(rows[0]) * k + 1, len(rows) * k + 1)
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
    # The minimap is a map held in the off hand, which the client draws big: no paper behind it, so only the small framed plan
    # MiniMapMath paints in the map's top-left corner shows (the rest of the map is the transparent colour).
    for name in vb.HIDDEN_MAP_TEXTURES:
        write_png(PACK / f"assets/minecraft/textures/map/{name}.png", canvas(64, 64))
    return len(vb.hidden_sprites()) + len(vb.HIDDEN_MAP_TEXTURES)


# ------------------------------------------------------------------ menu backgrounds

def window_base(accent=kit.STEEL):
    """The 176 x 222 window of the shared frame system (valoblock_menukit.py): panel, title strip, the player's inventory wells."""
    return kit.window(accent)


def slot_box(col_, row, w=1, h=1):
    """(x, y, width, height) of the well around a block of chest slots."""
    x, y = vb.slot_xy(col_, row)
    return x - 1, y - 1, 18 * w, 18 * h


def slot_well(img, col_, row, w=1, h=1, **kw):
    kit.well(img, *slot_box(col_, row, w, h), **kw)


def select_background():
    """Agent select: the lit portrait bay, the roster tiles (the head sits in the left slot of each), Random, the role filters,
    the team's seats and the Lock in button. The sprites of Random and Lock in are icons only: the buttons are painted here."""
    accent = kit.ACCENT_SELECT
    img = window_base(accent)
    kit.bay(img, *slot_box(0, 0, vb.SEL_PORTRAIT_COLS, vb.SEL_PORTRAIT_ROWS), accent)
    for tr in range(vb.SEL_TILE_ROWS):
        for tc in range(vb.SEL_TILE_COLS):
            x, y, w, h = slot_box(vb.SEL_ROSTER_COL + 2 * tc, tr, 2, 1)
            kit.well(img, x, y, w, h)
            kit.slashes(img, x + 20, y + 2, 14, 14, kit.mix(kit.WELL, accent, 0.22), 6, 2, (0.9, 0.15))   # the name side of the tile
    x, y, w, h = slot_box(0, 4, 2, 1)                                     # Random: a steel button, the die on its left
    kit.raised(img, x, y, w, h, col("56607a"))
    shuffle(img, x + 20, y + 5, col("dfe4ee"), col("2a3144"))
    kit.tray(img, *grow(slot_box(3, 4, 4, 1), 1))                         # the role filters share a rail
    for c in (3, 4, 5, 6):
        slot_well(img, c, 4)
    x, y, w, h = slot_box(7, 4, 2, 1)
    kit.slashes(img, x + 4, y + 4, w - 6, 10, accent, 6, 3, (0.75, 0.1))
    for c in range(5):
        slot_well(img, c, 5, fill=col("121622"), light=kit.mix(kit.WELL_LIGHT, accent, 0.35))   # the team
    x, y, w, h = slot_box(5, 5, 4, 1)                                     # Lock in: a green button, the padlock on its left
    kit.raised(img, x, y, w, h, col("2fa858"))
    for k in range(3):                                                    # three chevrons pointing at the match
        chevron(img, x + 30 + 12 * k, y + 5, kit.mix(col("2fa858"), kit.WHITE, 0.35 + 0.2 * k), col("14532b"))
    return img


def grow(box, by):
    x, y, w, h = box
    return x - by, y - by, w + 2 * by, h + 2 * by


def chevron(img, x, y, c, shadow):
    """A 5 x 8 chevron pointing right, with a 1 px shadow under it."""
    for k in range(4):
        for yy in (y + k, y + 7 - k):
            kit.rect(img, x + k, yy + 1, 2, 1, shadow)
    for k in range(4):
        for yy in (y + k, y + 7 - k):
            kit.rect(img, x + k, yy, 2, 1, c)


def shuffle(img, x, y, c, shadow):
    """Two crossing arrows (12 x 8): shuffle. Painted on the Random button."""
    rows = ["........##..",
            "##.....####.",
            "###...##.##.",
            "..##.##.....",
            "...###......",
            "..##.##.....",
            "###...##.##.",
            "##.....####.",
            "........##.."]
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == "#":
                kit.put(img, x + dx + 1, y + dy, shadow)
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == "#":
                kit.put(img, x + dx, y + dy - 1, c)


def card_well(img, col_, row, glow=None):
    """The bay of a 2 x 2 card: the card item (items(): card_frame*) lays its state edge and the weapon over it."""
    x, y, w, h = slot_box(col_, row, 2, 2)
    kit.well(img, x, y, w, h, fill=col("10141e"), glow=glow)
    kit.glow(img, x + 2, y + 2, w - 4, h - 4, x + w / 2, y + h / 2, w * 0.6, h * 0.5, glow or col("5a6788"), 0.16)
    kit.rect(img, x + 6, y + h - 8, w - 12, 1, kit.mix(kit.WELL, glow or col("5a6788"), 0.4))    # the shelf the weapon lies on


def armory_background():
    """Armory: the class tabs in a rail down the left, eight card bays, the two Ward bays and the agent's two ability buys."""
    accent = kit.ACCENT_ARMORY
    img = window_base(accent)
    kit.tray(img, *grow(slot_box(vb.ARM_TAB_COL, 0, 1, 6), 0))
    for r in range(6):
        slot_well(img, vb.ARM_TAB_COL, r, fill=col("121622"))
    for k in range(vb.ARM_CARD_COLS):
        for j in range(vb.ARM_CARD_ROWS):
            card_well(img, vb.ARM_CARD_COL + 2 * k, 2 * j)
    for k in range(2):
        card_well(img, vb.ARM_CARD_COL + 2 * k, vb.ARM_SHIELD_ROW, kit.BLUE)
    # the abilities: a teal bay over the last four columns of rows 4 and 5, one well per buy
    x, y, w, h = slot_box(5, vb.ARM_SHIELD_ROW, 4, 2)
    kit.tray(img, x, y, w, h, kit.mix(col("0b0d14"), kit.TEAL, 0.05))
    kit.slashes(img, x + 4, y + 23, w - 8, 9, kit.TEAL, 6, 3, (0.5, 0.05))
    for s in vb.ARM_ABILITY_SLOTS:
        bx, by, bw, bh = slot_box(s % 9, s // 9)
        kit.well(img, bx, by, bw, bh, glow=kit.TEAL)
        kit.brackets(img, bx - 1, by - 1, bw + 2, bh + 2, kit.mix(kit.TEAL, kit.BLACK, 0.25), 4)
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
    """The agent's portrait glyph (valoblock_portraits.bust_hd: the Blockbench render at 4x, drawn 48 x 64); None or silhouette:
    the unlit figure of an empty seat."""
    return portraits.bust_hd(None if silhouette else agent)


def menu_font():
    providers = [{"type": "space", "advances": {chr(k): v for k, v in vb.ADVANCES.items()}}]
    providers += cut_background("vb_select", vb.SELECT_BASE, select_background())
    providers += cut_background("vb_armory", vb.ARMORY_BASE, armory_background())
    import valoblock_tacmap as tac
    providers += tac.providers(sys.modules[__name__])
    import valoblock_vote as vote
    providers += vote.providers(sys.modules[__name__])
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
    """16 x 16 ability icon of the agent's slot 4-7 ability (valoblock_ability_icons.py: the hotbar item's glyph drawn at 16 px)."""
    ab = next(x for x in agent["abilities"] if x["slot"] == slot)
    return icons.icon(agent, ab["id"], slot, "on" if on else "off")


def tile_sprite(slot, on):
    """20 x 24: the 16 px icon sits at (2, 2), the key at the bottom left, the three charge pips right of it (vb_layout)."""
    w, h = vb.TILE
    img = canvas(w, h)
    edge = col("ffd36a") if slot == 7 and on else col("c9a8ff") if on else col("3a3f4c")
    frame(img, 0, 0, w, h, col("0b0d12", 217), edge)
    key = "U" if slot == 7 else str(slot)
    blit(img, glyph(key, col("ffffff") if on else col("7a7f8c"), 1, None, TINY), 2, 18)
    return img


def plate_sprite(kind):
    """The 60 x 22 plate under the digits. Coordinates are plate-relative: element x/y minus the plate's own (see vb_layout)."""
    w, h = vb.PLATE
    img = canvas(w, h)
    frame(img, 0, 0, w, h, col("0b0d12", 217), col("2a2f3c"))
    if kind == "hp":
        rect(img, 4, 14, 34, 3, col("232733"))                       # the health track (hp_bar: x 8, y -12)
        rect(img, 4, 19, 34, 2, col("1f3557"))                       # the shield track (sh_bar: x 8, y -7)
        rect(img, 41, 3, 1, 16, col("2a2f3c"))                       # the divider before the shield figures
        rect(img, 46, 12, 5, 4, col("7fc8e8"))                       # a small shield mark under the figures
        rect(img, 47, 16, 3, 1, col("7fc8e8"))
        rect(img, 48, 17, 1, 1, col("7fc8e8"))
    else:
        for k in range(9):                                           # the slash between the magazine and its size
            rect(img, 21 + (8 - k) // 3, 4 + k, 1, 1, col("9aa3b5"))
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
        out += [(f"bd_{tag}_{d}", pad(glyph(d, color, 1, col("000000", 170)), *vb.BIG)) for d in "0123456789"]
    out.append(("bd_blank", canvas(*vb.BIG)))
    for tag, color in (("w", col("ffffff")), ("b", col("7fc8e8")), ("g", col("9aa3b5"))):
        out += [(f"sd_{tag}_{d}", pad(glyph(d, color, 1, col("000000", 170), TINY), *vb.SMALL)) for d in "0123456789"]
    out.append(("sd_blank", canvas(*vb.SMALL)))
    out += [("hp_bar", canvas(8, 3, col("5ad07a"))), ("hp_bar_low", canvas(8, 3, col("ff5a5a"))), ("sh_bar", canvas(8, 2, col("7fc8e8")))]
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
    """The 8 x 8 face in a 10 x 10 frame (valoblock_portraits.py): alive, dead (grey, struck through in red) or not chosen yet."""
    return portraits.head(agent, state)


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
        rows = [list(r) for r in rows]
        # The font drops a clear last column from the advance; the glyph that takes the advance back assumes the full width, so
        # a run would drift off the middle of the GUI. A pixel of alpha 1 (the shader discards below 0.1) keeps the column.
        if all(px[3] == 0 for r in rows for px in r[-1:]):
            rows[0][-1] = (0, 0, 0, 1)
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

# Which corner of its glyph quad a vertex is. NOT gl_VertexIndex % 4: the GUI draws share one vertex buffer and a draw starts
# wherever the previous draw ended (in vertices, of a different vertex format), so after a spyglass scope, a tooltip... the
# index is rotated and every glyph came out turned ('100' read 'H00'). The corner is read from where the quad was laid out.
VB_CORNER = """// 0 top left, 1 bottom left, 2 bottom right, 3 top right of a HUD glyph quad. Every glyph of a HUD run is followed by one that
// takes its advance back (see hud_fonts: the sprites have a non-clear last column so the advance is exact), so each quad starts at
// the pen, the middle of the GUI, and its bottom sits on the action bar's baseline (GUI height - 65).
int vbCorner(vec2 gui) {
    bool right = Position.x - gui.x * 0.5 > 2.0;
    bool bottom = Position.y > gui.y - 66.5;
    return right ? (bottom ? 2 : 3) : (bottom ? 1 : 0);
}

"""


def shader_sources():
    """The base pack's text shader with a second HUD branch: red 84, green 160 + element, blue the bar width."""
    anchors = ", ".join(str(e[1]) for e in vb.HUD_ELEMENTS)
    boxes = ",\n    ".join(f"vec4({e[2]}.0, {e[3]}.0, {e[4]}.0, {e[5]}.0)" for e in vb.HUD_ELEMENTS)
    stretch = ", ".join(str(e[6]) for e in vb.HUD_ELEMENTS)
    table = (f"const int VB_COUNT = {vb.HUD_COUNT};\nconst int VB_ANCHOR[VB_COUNT] = int[]({anchors});\n"
             f"const int VB_STRETCH[VB_COUNT] = int[]({stretch});\nconst vec4 VB_BOX[VB_COUNT] = vec4[](\n    {boxes});\n\n"
             + VB_CORNER)
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
        int k = vbCorner(gui);
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


def card_frame(state):
    """The state edge of an Armory card, 32 x 32 drawn over 2 x 2 slots (36 GUI px: 1.125 px a texel, so nothing here is thinner
    than 2 texels). Mostly clear: the bay under it is painted in the background at 1 px per GUI px. A colour bar down the left
    edge and a corner flag say the state: gold = for sale, green = owned (the flag is filled), dim red = too expensive."""
    colour = {"": col("ffd36a"), "_owned": col("5ad07a"), "_locked": col("8a4650")}[state]
    img = canvas(32, 32)
    if state == "_locked":
        kit.rect(img, 1, 1, 30, 30, col("05060a", 96))
    elif state == "_owned":
        kit.rect(img, 1, 1, 30, 30, (colour[0], colour[1], colour[2], 44))   # well over the 0.1 alpha the item shader discards
    for y in range(1, 31):                                       # the bar: bright at the top, deeper at the bottom
        kit.rect(img, 1, y, 3, 1, kit.mix(kit.mix(colour, kit.WHITE, 0.25), kit.mix(colour, kit.BLACK, 0.3), y / 30))
    kit.rect(img, 4, 1, 1, 30, col("05060a", 150))
    n = 9
    for k in range(n):                                           # the corner flag: a wedge, filled when owned, an angled bar otherwise
        kit.rect(img, 31 - n + k, 1 + k, n - k if state == "_owned" else min(3, n - k), 1, colour)
    kit.rect(img, 4, 29, 27, 2, kit.mix(colour, kit.BLACK, 0.3))                                        # the edge turns along the bottom
    return img


def tab_texture(weapon, selected):
    """A class tab, 32 x 32 for one slot (2 texels a GUI px): the class's weapon over the rail's well; the open tab is lit gold."""
    img = canvas(32, 32)
    if selected:
        gold = kit.ACCENT_ARMORY
        for y in range(32):
            kit.rect(img, 0, y, 32, 1, kit.mix(col("3a2f18"), col("1c1a18"), y / 31))
        kit.rect(img, 0, 0, 32, 2, gold)
        kit.rect(img, 0, 30, 32, 2, kit.mix(gold, kit.BLACK, 0.45))
        kit.rect(img, 0, 0, 4, 32, gold)
        kit.rect(img, 30, 0, 2, 32, kit.mix(gold, kit.BLACK, 0.3))
        kit.rect(img, 4, 2, 2, 28, col("05060a", 140))
    blit(img, vi.sprite(weapon["texture"], weapon["metal"]), 1 if selected else 0, 0)
    return img


CLASSES = ["Sidearms", "SMGs", "Shotguns", "Rifles", "Snipers", "Heavy"]
CLASS_REPRESENTATIVE = ["gravebell", "mistral", "scrapper", "flux_rifle", "watchers_eye", "grinder"]
CARD_SCALE, CARD_SHIFT = 2.25, 9


def items():
    n = 0
    states = ("", "_owned", "_locked")
    for suffix in states:
        write_png(NS / f"textures/item/valoblock/card_frame{suffix}.png", card_frame(suffix))
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
            img = tab_texture(w, sel)
            tag = f"tab_{name.lower()}" + ("_sel" if sel else "")
            write_png(NS / f"textures/item/valoblock/{tag}.png", img)
            item(tag, {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/valoblock/{tag}"}})
            n += 1
    write_json(NS / "items/valoblock/blank.json", {"model": {"type": "minecraft:empty"}})
    import valoblock_tacmap as tac
    return n + tac.items(sys.modules[__name__])


def main():
    print(f"hid {hide_vanilla()} vanilla HUD sprites")
    print(f"menu font: {menu_font()} providers")
    shader, inline = hud_fonts()
    print(f"HUD font: {shader} shader sprites, {inline} inline sprites, {vb.HUD_COUNT} elements")
    write_shaders()
    print(f"items: {items()}")


if __name__ == "__main__":
    main()
