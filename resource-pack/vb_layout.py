"""Geometry shared by the ValoBlock pack art (art/valoblock_ui.py), the pack test and the plugin.

Three things live here, each mirrored by a Java class that a test keeps in step:
  - the menu backgrounds (VbMenuSkin.java): tile glyphs, the portrait and the space glyphs that place the title text;
  - the HUD elements the core shader moves (VbGlyphs.java reads the sprite table the art script writes);
  - the vanilla HUD sprites the ValoBlock pack blanks.
"""
import layout

# ---------------------------------------------------------------- menus (font vampyuyu:vbmenus)
FONT_MENUS = "vbmenus"
SELECT_BASE, ARMORY_BASE = 0xE100, 0xE200       # first tile of each 4 x 6 background
PORTRAIT_BASE = 0xE300                          # + the agent's index in agents/index.json
PORTRAIT_NONE = 0xE3FF                          # nobody picked yet
BACKGROUNDS = {"vb_select": SELECT_BASE, "vb_armory": ARMORY_BASE}
GUI_H, ROWS = 222, 6                            # 54-slot window
PORTRAIT_W, PORTRAIT_H = 48, 64                 # texture px = GUI px
PORTRAIT_X, PORTRAIT_Y = 10, 21                 # top-left of the portrait in the window
TEXT_X = 8                                      # where the dynamic title text starts
SHIFT_BACK, ROW_BACK, SEAM = 0xE000, 0xE001, 0xE002
TO_PORTRAIT, FROM_PORTRAIT, TO_TEXT = 0xE010, 0xE011, 0xE012
ROW_W = layout.COLS * (layout.TILE_W + 1) + (layout.COLS - 1) * -1   # 177: where the cursor is after a tile row
# Tactical map (TacMapMenu.java, TacGrid.java): one shared 4 x 6 chrome, then ONE plan glyph drawn over it (the portrait technique):
# the whole map (TAC_PLAN_BASE + map index) or a zoomed zone (TAC_ZONE_BASE + map * 25 + zone, zone = row * 5 + col). The 30 x 30 plan is
# square, so it takes the 5 x 5 middle slots of the chest (90 GUI px, 1 texture px per GUI px); the side columns and the bottom row are
# controls. A zone is a 6 x 6 cell block shown at 15 px per cell, with the fine grid of 5 x 5 slots over it (1.2 cells a slot).
TAC_CHROME_BASE, TAC_PLAN_BASE, TAC_ZONE_BASE = 0xE400, 0xE500, 0xE600
TAC_MAPS = ("hourfall", "rootvault", "stormbreak")   # = VbData.MAP_IDS
TAC_GRID = 5                                    # zones across and down; fine slots across and down
TAC_PLAN_X, TAC_PLAN_Y, TAC_PLAN_PX = 43, 17, 90
TO_PLAN, FROM_PLAN = 0xE013, 0xE014
# Map vote (MapVoteMenu.java): four full 4 x 6 backgrounds, the three maps' plan thumbnails in realm-coloured frames baked in; the 2nd to 4th
# add a gold frame round map 0, 1 or 2 (the voter's choice), the 1st has none. Tile k is 3 x 3 slots at column 3 * k, row 2 (VOTE_ROW), centred between the title and the bottom edge.
VOTE_BASES = (0xE700, 0xE800, 0xE900, 0xEA00)
VOTE_COLS, VOTE_ROWS, VOTE_ROW = 3, 3, 2
VOTE_THUMB = 46                                 # px: the plan painted at the tile's size (52 px tile, 3 px frame)
VOTE_REALM = {"hourfall": "d9b23c", "rootvault": "46b866", "stormbreak": "4aa3d8"}   # the Realm colour of each map's frame (= MapVoteMenu.REALM)
VOTE_PICK = "ffe94a"
ADVANCES = {
    SHIFT_BACK: -8,
    ROW_BACK: -ROW_W,
    SEAM: -1,
    TO_PORTRAIT: PORTRAIT_X - ROW_W,
    FROM_PORTRAIT: TEXT_X - (PORTRAIT_X + PORTRAIT_W + 1),
    TO_TEXT: TEXT_X - ROW_W,
    TO_PLAN: TAC_PLAN_X - ROW_W,
    FROM_PLAN: TEXT_X - (TAC_PLAN_X + TAC_PLAN_PX + 1),
}

def slot_xy(col, row):
    """Top-left GUI pixel of the 16x16 item area of a chest slot."""
    return 8 + 18 * col, 18 + 18 * row

# Agent select (slots = row * 9 + col): VbSelectLayout.java
SEL_PORTRAIT_COLS, SEL_PORTRAIT_ROWS = 3, 4     # the panel at cols 0-2, rows 0-3
SEL_ROSTER_COL, SEL_TILE_COLS, SEL_TILE_ROWS = 3, 3, 4   # 2-slot tiles from col 3, 3 per row, 4 rows
# Armory: VbArmoryLayout.java
ARM_TAB_COL = 0
ARM_CARD_COL, ARM_CARD_COLS, ARM_CARD_ROWS = 1, 4, 2     # 2 x 2 slot cards, 4 across, 2 high
ARM_SHIELD_ROW = 4
ARM_ABILITY_SLOTS = (42, 44)

# ---------------------------------------------------------------- HUD (font vampyuyu:vbhud, shader branch)
FONT_HUD, FONT_INLINE = "vbhud", "vbinline"
HUD_R = 84            # red channel of a ValoBlock HUD glyph (the base pack uses 78-81; 84 / 4 = 21 exactly for the shadow)
HUD_ID_BASE = 160     # green channel = HUD_ID_BASE + element; the blue channel is the parameter (bar width 0-255)
SPRITE_BASE, NEG_BASE = 0xE000, 0xF000
TL, TC, TR, BL, BC, BR, CE = range(7)
# name, anchor, x, y, w, h, stretch (the blue channel sets the width). y counts from the anchor: negative = up from the bottom.
# v3 (2026-11-24, "the HUD is huge"): every panel repainted about 2/3 of its old size, never scaled: the plates went from 88 x 36
# to 60 x 22, the big digits from 10 x 14 to 5 x 7 px, the small ones from 5 x 7 to 3 x 5, the ability tiles from 30 x 38 to 20 x 24.
HUD_ELEMENTS = (
    [("hp_plate", BL, 4, -26, 60, 22, 0), ("hp_bar", BL, 8, -12, 34, 3, 1), ("sh_bar", BL, 8, -7, 34, 2, 1)]
    + [(f"hp{i + 1}", BL, 8 + 7 * i, -23, 7, 9, 0) for i in range(3)]
    + [(f"sh{i + 1}", BL, 49 + 4 * i, -23, 4, 6, 0) for i in range(3)]
    + [(f"tile{i}", BC, -44 + 22 * i, -50, 20, 24, 0) for i in range(4)]
    + [(f"icon{i}", BC, -42 + 22 * i, -48, 16, 16, 0) for i in range(4)]
    + [(f"pip{i}_{j}", BC, -36 + 22 * i + 4 * j, -30, 3, 2, 0) for i in range(4) for j in range(3)]
    + [("am_plate", BR, -64, -26, 60, 22, 0)]
    + [(f"am{i + 1}", BR, -58 + 7 * i, -23, 7, 9, 0) for i in range(2)]
    + [(f"rs{i + 1}", BR, -36 + 4 * i, -20, 4, 6, 0) for i in range(3)]
    + [("xh", CE, -10, -10, 21, 21, 0), ("hit", CE, -10, -10, 21, 21, 0)]
    # The Rift Seed (appended: the ids of the elements above keep their numbers). The icon sits right of the health plate and only
    # the carrier gets it; the planting bar (a track and a stretched fill) sits under the crosshair while the carrier holds.
    + [("seed", BL, 66, -24, 16, 16, 0), ("plant_track", CE, -26, 22, 52, 7, 0), ("plant_bar", CE, -24, 24, 48, 3, 1)]
    # The minimap (appended, 2026-11-26): the framed plan of the map in the top-left corner, like a shooter's radar. Its markers are
    # not elements: they carry their own position in the colour (see MM_* below).
    + [("minimap", TL, 6, 6, 80, 80, 0)]
)
HUD_COUNT = len(HUD_ELEMENTS)
BIG, SMALL = (7, 9), (4, 6)
TILE, ICON, PIP, PLATE, CROSS = (20, 24), (16, 16), (3, 2), (60, 22), (21, 21)
SEED, PLANT_TRACK = (16, 16), (52, 7)
HEAD = 10             # inline agent head, px

# Minimap markers (MiniMapGlyphs.java): a glyph coloured (MM_R_SMALL or MM_R_BIG, x, y) is centred on the point (x, y) / 255 of the
# plan inside the "minimap" element (MM_INSET GUI px in from its edge), MM_SMALL or MM_BIG GUI px square. Red 86 and 87 are no named
# chat colour (those use 0x55 = 85); the markers are sent with no text shadow. Every minimap texture is drawn at MM_TEX texels a GUI px.
MM_R_SMALL, MM_R_BIG = 86, 87
MM_SMALL, MM_BIG = 8, 16
MM_INSET = 4
MM_TEX = 2
MM_HEADINGS = 16      # the self arrow is drawn turned to 16 headings (22.5 degrees apart), heading 0 = north (up)

# Map backgrounds the pack blanks (textures/map/<name>.png, 64 x 64). The minimap is no longer a held map (it is the "minimap" HUD
# element); the blanks stay so a map item held during a match draws no paper.
HIDDEN_MAP_TEXTURES = ("map_background", "map_background_checkerboard")

# Vanilla HUD sprites the pack hides: (path under textures/gui/sprites/, width, height)
def hidden_sprites():
    out = [("hud/crosshair", 15, 15), ("hud/crosshair_attack_indicator_background", 16, 4), ("hud/crosshair_attack_indicator_progress", 16, 4),
           ("hud/crosshair_attack_indicator_full", 16, 16), ("hud/experience_bar_background", 182, 5), ("hud/experience_bar_progress", 182, 5)]
    out += [(f"hud/{n}", 9, 9) for n in ("armor_empty", "armor_full", "armor_half", "food_empty", "food_empty_hunger", "food_full",
                                         "food_full_hunger", "food_half", "food_half_hunger")]
    for style in ("", "absorbing_", "frozen_", "poisoned_", "withered_"):
        for hardcore in ("", "hardcore_"):
            for part in ("full", "half"):
                for blink in ("", "_blinking"):
                    out.append((f"hud/heart/{style}{hardcore}{part}{blink}", 9, 9))
    for name in ("container", "container_blinking", "container_hardcore", "container_hardcore_blinking",
                 "vehicle_container", "vehicle_full", "vehicle_half"):
        out.append((f"hud/heart/{name}", 9, 9))
    return sorted(set(out))
