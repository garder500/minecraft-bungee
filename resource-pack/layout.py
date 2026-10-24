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
    "menu_lobby":   (0xE100, 168, 4),   # 27-slot window
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
}

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


# Role and HUD icons: glyphs of the vampyuyu:icons font, from ICON_BASE in this order
# (art/icons.py draws them; plugin: WerewolfHud.icon).
ICONS = ["wolf", "villager", "seer", "witch", "hunter", "cupid", "guard", "dead", "moon", "sun",
         "vote", "timer", "alive", "alpha", "infector", "cub", "scapegoat", "bear"]
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
    "vote": 0xffd36e, "sleep": 0x6a5fa0,
}

# element -> (anchor, x, y, w, h); anchor 0 = top centre, 1 = right middle. The element's id is its
# index here. "fill" is stretched to the width given by the colour's blue channel (0-255).
HUD_ELEMENTS = [
    ("frame", 0, -95, 3, 190, 26),
    ("icon", 0, -89, 7, 16, 16),
    ("round", 0, -70, 10, 7, 9),
    ("label", 0, -61, 11, 84, 9),
    ("t1", 0, 30, 10, 7, 9),
    ("t2", 0, 38, 10, 7, 9),
    ("tcolon", 0, 46, 10, 3, 9),
    ("t3", 0, 50, 10, 7, 9),
    ("t4", 0, 58, 10, 7, 9),
    ("fill", 0, -89, 22, 178, 3),
    ("pframe", 1, -104, -30, 100, 60),
    ("picon", 1, -98, -25, 24, 24),
    ("plabel", 1, -68, -24, 62, 9),
    ("pteam", 1, -68, -12, 62, 9),
    ("a1", 1, -60, 6, 7, 9),
    ("a2", 1, -52, 6, 7, 9),
    ("aslash", 1, -44, 6, 5, 9),
    ("a3", 1, -38, 6, 7, 9),
    ("a4", 1, -30, 6, 7, 9),
]
HUD_ID_BASE = 100        # the green channel of an element's colour is HUD_ID_BASE + its index
HUD_SPRITE_BASE = 0xE000
HUD_NEG_BASE = 0xF000    # negative-advance twin of every sprite: sprite index + HUD_NEG_BASE

HUD_PHASE_WORDS = ["DEBATE", "VOTE", "VILLAGE SLEEPS", "CUPID AWAKE", "GUARD AWAKE", "SEER AWAKE",
                   "WOLVES HUNT", "WITCH AWAKE", "LAST SHOT", "GAME OVER", "WAITING"]
HUD_ROLE_WORDS = ["WEREWOLF", "VILLAGER", "SEER", "WITCH", "HUNTER", "CUPID", "GUARD", "ALPHA WOLF",
                  "INFECTOR", "WOLF CUB", "SCAPEGOAT", "BEAR TAMER", "FALLEN"]
HUD_TEAM_WORDS = ["VILLAGE", "WOLF PACK", "OUT"]
HUD_TOP_ICONS = ["moon", "sun", "vote"]
HUD_ROLE_ICONS = ["wolf", "villager", "seer", "witch", "hunter", "cupid", "guard", "dead", "alpha",
                  "infector", "cub", "scapegoat", "bear"]


def hud_sprites():
    """(name, element-class width, height) of every sprite, in glyph order."""
    box = {e[0]: (e[4], e[5]) for e in HUD_ELEMENTS}
    out = []
    for pal in HUD_PALETTES:
        out.append((f"frame_{pal}", *box["frame"]))
    for pal in HUD_PALETTES:
        out.append((f"fill_{pal}", 8, 3))
    for name in HUD_TOP_ICONS:
        out.append((f"icon_{name}", *box["icon"]))
    for word in HUD_PHASE_WORDS:
        out.append((f"label_{word.lower().replace(' ', '_')}", *box["label"]))
    for d in "0123456789":
        out.append((f"d{d}", *box["t1"]))
    out.append(("dblank", *box["t1"]))
    out.append(("dcolon", *box["tcolon"]))
    out.append(("dslash", *box["aslash"]))
    for pal in HUD_PALETTES:
        out.append((f"pframe_{pal}", *box["pframe"]))
    for name in HUD_ROLE_ICONS:
        out.append((f"picon_{name}", *box["picon"]))
    for word in HUD_ROLE_WORDS:
        out.append((f"plabel_{word.lower().replace(' ', '_')}", *box["plabel"]))
    for word in HUD_TEAM_WORDS:
        out.append((f"pteam_{word.lower().replace(' ', '_')}", *box["pteam"]))
    return out
