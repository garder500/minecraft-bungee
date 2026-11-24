"""Shared geometry of the painted menu backgrounds (used by the renderer and the tests).

A background covers a whole container window (176 GUI px wide). Minecraft packs bitmap-font glyphs
into 256x256 atlas pages, so each background is cut into COLS x rows tiles of at most 176x222
texture pixels (4 texture px per GUI px) and drawn as one title string of tile characters.
"""
GUI_W = 176
COLS = 4
PX_PER_GUI = 4
CSS_PER_GUI = 3          # the art is authored at 3 CSS px per GUI px
ASCENT = 13              # baseline of the vanilla title: the window top sits 13 px above it

# name -> (first tile codepoint, window height in GUI px, tile rows)
BACKGROUNDS = {
    "menu_lobby":   (0xE100, 222, 6),   # 54-slot window: worlds on rows 0-2, games on rows 3-5
    "menu_setup":   (0xE200, 222, 6),   # 54-slot window
    "menu_list":    (0xE300, 222, 6),
    "menu_detail":  (0xE400, 168, 4),
    "menu_confirm": (0xE500, 168, 4),
    "menu_compass": (0xE600, 222, 6),
    "menu_profile": (0xE700, 222, 6),
    "menu_alembic": (0xE800, 222, 6),   # witch potion phase
    # Werewolf role menus (design guide "Les menus de rôle"); plugin: WerewolfMenus.Kind
    "menu_vote":    (0xE900, 222, 6),
    "menu_hunt":    (0xEA00, 222, 6),
    "menu_vision":  (0xEB00, 222, 6),
    "menu_potions": (0xEC00, 222, 6),
    "menu_guard":   (0xED00, 222, 6),
    "menu_lovers":  (0xEE00, 222, 6),
    "menu_shot":    (0xEF00, 222, 6),
    "menu_village": (0xF000, 222, 6),   # overview when no role menu applies: WerewolfMenus.VILLAGE
    # Skyblock (survival-plugin core/MenuArt.Kind): sky and island art instead of glass panes
    "sb_main":      (0xF100, 222, 6),   # the Skyblock menu: buttons on SB_MAIN
    "sb_panel":     (0xF200, 222, 6),   # any 6-row Skyblock menu
    "sb_small":     (0xF300, 168, 4),   # any 3-row Skyblock menu
    "sb_forge":     (0xF400, 222, 6),   # the Forge
}

# The Skyblock menu's button slots (chest indexes) and the player's island in their middle. MainMenu.java places its
# buttons on exactly these slots, in this order: quests, skills, collections, forge, island, minions, exchange,
# market, bank, bosses, crypt, hunts, leaderboards, profile, warps, settings.
SB_MAIN = [11, 13, 15, 19, 22, 25, 29, 31, 33, 37, 40, 43, 7, 45, 49, 53]
SB_MAIN_CENTRE = 22

# Role cards drawn as glyphs of the vampyuyu:hud font (plugin: WerewolfHud.glyph): the back and
# the seven roles from 0xE000, their grey "dead" versions 0x10 further.
CARDS = ["back", "villager", "werewolf", "seer", "cupid", "hunter", "witch", "guard"]
CARD_BASE = 0xE000
CARD_DEAD = 0x10
CARD_HEIGHT = 32         # GUI px
CARD_ASCENT = 30         # the card hangs above the sidebar title line

TILE_W = GUI_W // COLS   # 44 GUI px
SHIFT_BACK = 0xE000      # -8: from the title origin to the window's left edge
SHIFT_FORWARD = 0xE001   # back to x=44 where the dynamic title text starts
ROW_BACK = 0xE002        # -(176 + 1): return to the left edge after a tile row
SEAM = 0xE003            # -1: cancels the +1 advance every bitmap glyph adds
ADVANCES = {SHIFT_BACK: -8, SHIFT_FORWARD: -(GUI_W + 1 - 44), ROW_BACK: -(GUI_W + 1), SEAM: -1}

def tile_height(gui_h, rows):
    assert gui_h % rows == 0
    return gui_h // rows

def tile_file(name, r, c):
    return f"{name}_{r}{c}"


import json
import pathlib
import re

# Every werewolf role is described by one JSON file of the plugin (RoleInfo.java reads the same):
# its name, team, colour, HUD palette, icon, the words the HUD shows for it, the call it owns.
ROLES_DIR = pathlib.Path(__file__).resolve().parent.parent / "arena-plugin/src/main/resources/roles"


def key(word):
    """Sprite key of a HUD word; the plugin does the same (CustomHud.word)."""
    return re.sub(r"[^a-z0-9]+", "_", word.lower()).strip("_")


def roles():
    """role id -> its JSON, in file-name order."""
    return {f.stem: json.loads(f.read_text()) for f in sorted(ROLES_DIR.glob("*.json"))}


def team_word(role):
    if "teamWord" in role:
        return role["teamWord"]
    return {"wolves": "WOLF PACK", "vampires": "VAMPIRES", "cult": "THE CULT", "solo": "ALONE"}.get(
        role["team"], "VILLAGE")


def unique(items, first=()):
    out = list(first)
    for item in items:
        if item not in out:
            out.append(item)
    return out


# Role and HUD icons: glyphs of the vampyuyu:icons font, from ICON_BASE in this order
# (art/icons.py draws them; plugin: WerewolfHud.icon reads icon-names.txt). The first ones are
# drawn by art/icons.py itself; every other role's icon is its own SVG in art/icons/.
BASE_ICONS = ["wolf", "villager", "seer", "witch", "hunter", "cupid", "guard", "dead", "moon", "sun",
              "vote", "timer", "alive", "alpha", "infector", "cub", "scapegoat", "bear"]
ICONS = unique((r["icon"] for r in roles().values()), BASE_ICONS)
ICON_BASE = 0xE000
ICON_PX = 64             # texture size of one icon
ICON_HEIGHT = 12         # drawn height in GUI px
ICON_ASCENT = 11


# ---------------------------------------------------------------------------------------------
# Custom HUD (font vampyuyu:hud2, drawn by the text core shader; art/hud.py draws the sprites).
#
# The plugin writes the HUD as glyphs of the action bar, each one coloured (78, 100 + element,
# parameter). The core shader (rendertype_text, see art/fx_shaders.py) moves such a glyph to its
# element's box, anchored to the top centre or the right middle of the screen, in GUI pixels:
# the sprite is drawn 1 texel per GUI pixel, whatever the GUI scale. Every sprite is followed by
# its negative-advance glyph so the rest of the action bar text stays centred.

HUD_PALETTES = {  # name -> role colour
    "villager": 0xe9d99b, "wolf": 0xff5a5a, "seer": 0xb892ff, "witch": 0x6fdc8c,
    "hunter": 0xf0a050, "cupid": 0xff8fc0, "guard": 0x6cc6ff, "dead": 0x8f84b3,
    "vote": 0xffd36e, "sleep": 0x6a5fa0, "danger": 0xff4040,
    "ghost": 0xbfe9ff, "vampire": 0xd8456f, "cult": 0xd46aff, "solo": 0x9aa6b8,
}

# element -> (anchor, x, y, w, h); anchor 0 = top centre, 1 = right middle. The element's id is its
# index here. "fill" is stretched to the width given by the colour's blue channel (0-255).
# Sizes (2026-11): the top plate is 188 x 34 (was 250 x 44), the role panel 96 x 78 (was 128 x 108), the vote
# plate 96 x 26 (was 128 x 30): about a quarter smaller, repainted, never scaled.
HUD_ELEMENTS = [
    ("frame", 0, -94, 4, 188, 34),
    ("icon", 0, -86, 11, 20, 20),       # the phase medallion's slot
    ("round", 0, -68, 26, 7, 9),
    ("fill", 0, -57, 27, 104, 3),
    ("t1", 0, 56, 14, 9, 13),
    ("tcolon", 0, 64, 14, 4, 13),
    ("t3", 0, 67, 14, 9, 13),
    ("t4", 0, 75, 14, 9, 13),
    ("pframe", 1, -102, -39, 96, 78),
    ("picon", 1, -94, -31, 24, 24),     # the role medallion's slot
] + [(f"m{i}", 1, -94 + 4 * i, 26, 4, 7) for i in range(20)] + [
    (f"b{i}", 1, -63 + 13 * i, -19, 13, 13) for i in range(4)
] + [
    ("vframe", 1, -102, 43, 96, 26),    # "votes against you", under the panel during the day vote
    ("v1", 1, -30, 49, 9, 13),
    ("v2", 1, -22, 49, 9, 13),
]

# Text: the HUD's words are written one glyph per character (art/hud_font.py draws the fonts, the plugin lays the
# line out in the player's language). A text element is the origin of a line and the cell of its font; the blue
# channel of a glyph's colour is how far right of the origin its cell starts, in GUI pixels. They come last in
# HUD_ELEMENTS, from HUD_TEXT_FIRST on: the shader tells them by their index.
HUD_FONT_CELLS = {"h": (10, 14), "b": (9, 12), "m": (7, 12)}   # font -> cell (w, h); see art/hud_font.py
HUD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,:/-'!?&+ÀÂÄÇÈÉÊËÎÏÔÖÙÛÜ"
# element -> (anchor, x, y, font, align, widest line in px)
HUD_TEXTS = [
    ("label", 0, -58, 9, "h", "left", 106),     # the phase, as a headline
    ("label2", 0, -58, 10, "b", "left", 106),   # the same in bold when the headline is too wide
    ("pcount", 1, -54, -34, "b", "left", 43),   # alive/total, next to the panel's head-count pictogram
    ("prole", 1, -96, 2, "b", "left", 84),
    ("pteam", 1, -96, 12, "m", "left", 84),
    ("vcap1", 1, -96, 45, "b", "left", 62),
    ("vcap2", 1, -96, 54, "m", "left", 62),
]
HUD_TEXT = {t[0]: (t[4], t[5], t[6]) for t in HUD_TEXTS}
HUD_TEXT_FIRST = len(HUD_ELEMENTS)
HUD_ELEMENTS += [(name, anchor, x, y, *HUD_FONT_CELLS[font]) for name, anchor, x, y, font, _, _ in HUD_TEXTS]
HUD_PANEL_SCALE = 1.0    # the right-hand role panel: a non-integer scale mangles its pixel-art text (tried 0.85 and 0.75)
HUD_ID_BASE = 100        # the green channel of an element's colour is HUD_ID_BASE + its index
HUD_SPRITE_BASE = 0xE000
HUD_NEG_BASE = 0xF000    # negative-advance twin of every sprite: sprite index + HUD_NEG_BASE

HUD_TOP_ICONS = ["moon", "sun", "vote"]
HUD_ROLE_ICONS = unique((r["icon"] for r in roles().values()), []) + ["dead"]
HUD_SEATS = 20          # seats of the table strip; the first ones are drawn on bigger tables
HUD_DOTS = ["alive", "dead", "me", "ballot", "mate"]   # what a seat of the strip can show
HUD_BADGES = ["lover", "life", "death", "infect", "guard", "mayor", "silenced"]   # status badges (4 fit the panel)


def hud_sprites():
    """(name, width, height) of every sprite, in glyph order."""
    box = {e[0]: (e[4], e[5]) for e in HUD_ELEMENTS}
    out = []
    pals = list(HUD_PALETTES)
    out += [(f"frame_{p}", *box["frame"]) for p in pals]
    out += [(f"fill_{p}", 8, 3) for p in pals]
    out += [(f"icon_{n}", *box["icon"]) for n in HUD_TOP_ICONS]
    out += [(f"d{d}", *box["t1"]) for d in "0123456789"]
    out += [(f"x{d}", *box["t1"]) for d in "0123456789"]
    out += [("dcolon", *box["tcolon"]), ("xcolon", *box["tcolon"])]
    out += [(f"s{d}", *box["round"]) for d in "0123456789"]
    out += [(f"pframe_{p}", *box["pframe"]) for p in pals]
    out += [(f"picon_{n}", *box["picon"]) for n in HUD_ROLE_ICONS]
    out += [(f"dot_{n}", *box["m0"]) for n in HUD_DOTS]
    out += [(f"badge_{n}", *box["b0"]) for n in HUD_BADGES]
    out += [(f"vframe_{n}", *box["vframe"]) for n in ("calm", "hot")]
    # the fonts: one sprite per character, named by its code point (file names are [a-z0-9_])
    out += [(f"f{font}_{ord(ch):04x}", *cell) for font, cell in HUD_FONT_CELLS.items() for ch in HUD_CHARS]
    return out
