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
HUD_ELEMENTS = [
    ("frame", 0, -125, 4, 250, 44),
    ("icon", 0, -115, 14, 24, 24),
    ("round", 0, -96, 32, 7, 9),
    ("label", 0, -81, 8, 136, 17),
    ("fill", 0, -80, 32, 140, 4),
    ("t1", 0, 65, 8, 12, 17),
    ("tcolon", 0, 78, 8, 6, 17),
    ("t3", 0, 85, 8, 12, 17),
    ("t4", 0, 98, 8, 12, 17),
    ("pframe", 1, -134, -54, 128, 108),
    ("picon", 1, -122, -42, 32, 32),
    ("plabel", 1, -80, -42, 68, 9),
    ("pteam", 1, -80, -30, 68, 9),
    ("a1", 1, -84, 2, 12, 17),
    ("a2", 1, -72, 2, 12, 17),
    ("aslash", 1, -60, 2, 8, 17),
    ("a3", 1, -52, 2, 12, 17),
    ("a4", 1, -40, 2, 12, 17),
] + [(f"m{i}", 1, -126 + 5 * i, 24, 5, 8) for i in range(20)] + [
    (f"b{i}", 1, -126 + 14 * i, 38, 12, 12) for i in range(4)
] + [
    ("vframe", 1, -134, 58, 128, 30),   # "votes against you", under the panel during the day vote
    ("v1", 1, -44, 64, 12, 17),
    ("v2", 1, -31, 64, 12, 17),
]
HUD_PANEL_SCALE = 1.0    # the right-hand role panel: a non-integer scale mangles its pixel-art text (tried 0.85 and 0.75)
HUD_ID_BASE = 100        # the green channel of an element's colour is HUD_ID_BASE + its index
HUD_SPRITE_BASE = 0xE000
HUD_NEG_BASE = 0xF000    # negative-advance twin of every sprite: sprite index + HUD_NEG_BASE

HUD_PHASE_WORDS = unique(
    (r["call"]["label"] for r in roles().values() if "call" in r),
    ["DEBATE", "VOTE", "SLEEPING", "CUPID AWAKE", "GUARD AWAKE", "SEER AWAKE",
     "WOLVES HUNT", "WITCH AWAKE", "LAST SHOT", "GAME OVER", "WAITING", "DEFENSE", "TRIAL", "AMULET", "ELECTION", "AWAY"])
HUD_ROLE_WORDS = unique((r["word"] for r in roles().values()), []) + ["FALLEN"]
HUD_TEAM_WORDS = unique((team_word(r) for r in roles().values()), []) + ["OUT"]
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
    out += [(f"fill_{p}", 8, 4) for p in pals]
    out += [(f"icon_{n}", *box["icon"]) for n in HUD_TOP_ICONS]
    out += [(f"label_{key(w)}", *box["label"]) for w in HUD_PHASE_WORDS]
    out += [(f"d{d}", *box["t1"]) for d in "0123456789"]
    out += [(f"x{d}", *box["t1"]) for d in "0123456789"]
    out += [("dblank", *box["t1"]), ("dcolon", *box["tcolon"]), ("xcolon", *box["tcolon"]),
            ("dslash", *box["aslash"])]
    out += [(f"s{d}", *box["round"]) for d in "0123456789"]
    out += [(f"pframe_{p}", *box["pframe"]) for p in pals]
    out += [(f"picon_{n}", *box["picon"]) for n in HUD_ROLE_ICONS]
    out += [(f"plabel_{key(w)}", *box["plabel"]) for w in HUD_ROLE_WORDS]
    out += [(f"pteam_{key(w)}", *box["pteam"]) for w in HUD_TEAM_WORDS]
    out += [(f"dot_{n}", *box["m0"]) for n in HUD_DOTS]
    out += [(f"badge_{n}", *box["b0"]) for n in HUD_BADGES]
    out += [(f"vframe_{n}", *box["vframe"]) for n in ("calm", "hot")]
    return out
