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
HUD_ELEMENTS = (
    [("hp_plate", BL, 4, -42, 88, 36, 0), ("hp_bar", BL, 10, -21, 50, 4, 1), ("sh_bar", BL, 10, -13, 50, 3, 1)]
    + [(f"hp{i + 1}", BL, 10 + 12 * i, -38, 11, 15, 0) for i in range(3)]
    + [(f"sh{i + 1}", BL, 64 + 7 * i, -37, 6, 8, 0) for i in range(3)]
    + [(f"tile{i}", BC, -64 + 32 * i, -72, 30, 38, 0) for i in range(4)]
    + [(f"icon{i}", BC, -57 + 32 * i, -68, 16, 16, 0) for i in range(4)]
    + [(f"pip{i}_{j}", BC, -58 + 32 * i + 7 * j, -48, 5, 3, 0) for i in range(4) for j in range(3)]
    + [("am_plate", BR, -92, -42, 88, 36, 0)]
    + [(f"am{i + 1}", BR, -84 + 12 * i, -38, 11, 15, 0) for i in range(2)]
    + [(f"rs{i + 1}", BR, -52 + 7 * i, -34, 6, 8, 0) for i in range(3)]
    + [("xh", CE, -10, -10, 21, 21, 0), ("hit", CE, -10, -10, 21, 21, 0)]
    # The Rift Seed (appended: the ids of the elements above keep their numbers). The icon sits right of the health plate and only
    # the carrier gets it; the planting bar (a track and a stretched fill) sits under the crosshair while the carrier holds.
    + [("seed", BL, 94, -38, 16, 16, 0), ("plant_track", CE, -26, 22, 52, 7, 0), ("plant_bar", CE, -24, 24, 48, 3, 1)]
)
HUD_COUNT = len(HUD_ELEMENTS)
BIG, SMALL = (11, 15), (6, 8)
TILE, ICON, PIP, PLATE, CROSS = (30, 38), (16, 16), (5, 3), (88, 36), (21, 21)
SEED, PLANT_TRACK = (16, 16), (52, 7)
HEAD = 10             # inline agent head, px

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
