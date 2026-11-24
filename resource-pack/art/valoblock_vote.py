#!/usr/bin/env python3
"""The ValoBlock map vote window art (stdlib only, deterministic): one background with the three maps as cards (a cap in the map's
Realm colour, the floor plan as a thumbnail, a well for the tally under it), and the same background with a gold frame round the
map the player voted for. The frame system is valoblock_menukit.py. Called by valoblock_ui.menu_font."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import valoblock_menukit as kit  # noqa: E402
import valoblock_tacmap as tac  # noqa: E402
import vb_layout as vb  # noqa: E402

TILE = 18 * vb.VOTE_COLS - 2      # 52 GUI px: three slots and their two gaps
EDGE = (TILE - vb.VOTE_THUMB) // 2


def thumb(j):
    """The map's plan painted straight at the thumbnail's size (every pixel classified from the plan: no filtering, no smear)."""
    return tac.overview(j, vb.VOTE_THUMB)


def tile_xy(k):
    x, y = vb.slot_xy(vb.VOTE_COLS * k, vb.VOTE_ROW)
    return x, y


def background(ui, picked=None):
    accent = kit.ACCENT_VOTE
    img = ui.window_base(accent)
    # the two rows over the cards: a quiet banner, three pips for the three choices between angled bars
    x, y, w, h = ui.slot_box(0, 0, 9, 1)
    kit.tray(img, x, y + 5, w, 22)
    kit.slashes(img, x + 4, y + 9, 46, 14, accent, 6, 3, (0.7, 0.0))
    kit.slashes(img, x + w - 50, y + 9, 46, 14, accent, 6, 3, (0.0, 0.7))
    for k, mid in enumerate(vb.TAC_MAPS):
        tac.diamond(img, x + w // 2 - 14 + 14 * k, y + 16, 3, kit.col(vb.VOTE_REALM[mid]))
    for k, mid in enumerate(vb.TAC_MAPS):
        x, y = tile_xy(k)
        realm = kit.col(vb.VOTE_REALM[mid])
        chosen = picked == k
        kit.bar(img, x + 3, y - 6, 22, 4, realm)                                    # the cap: an angled tab in the Realm colour
        kit.bar(img, x + 28, y - 6, 4, 4, kit.mix(realm, kit.BG_BOTTOM, 0.45))
        kit.bar(img, x + 35, y - 6, 4, 4, kit.mix(realm, kit.BG_BOTTOM, 0.7))
        kit.rect(img, x, y, TILE, TILE, kit.INK)
        kit.bevel(img, x + 1, y + 1, TILE - 2, TILE - 2, kit.mix(realm, kit.WHITE, 0.25), kit.mix(realm, kit.BLACK, 0.4))
        kit.rect(img, x + 2, y + 2, TILE - 4, TILE - 4, kit.mix(realm, kit.BLACK, 0.72))
        ui.blit(img, thumb(tac.load(mid)), x + EDGE, y + EDGE)
        kit.brackets(img, x + 1, y + 1, TILE - 2, TILE - 2, kit.mix(realm, kit.WHITE, 0.6), 7)
        tx, ty, tw, th = ui.slot_box(vb.VOTE_COLS * k + vb.VOTE_COLS // 2, vb.VOTE_ROW + vb.VOTE_ROWS)   # = MapVoteMenu.tallySlot
        kit.well(img, tx, ty, tw, th, glow=realm)
        kit.rect(img, tx - 14, ty + 8, 11, 2, kit.mix(realm, kit.BG_BOTTOM, 0.55))
        kit.rect(img, tx + tw + 3, ty + 8, 11, 2, kit.mix(realm, kit.BG_BOTTOM, 0.55))
        if chosen:   # a gold ring in the two px gap round the tile
            gold = kit.col(vb.VOTE_PICK)
            kit.rect(img, x - 2, y - 2, TILE + 4, 2, gold)
            kit.rect(img, x - 2, y + TILE, TILE + 4, 2, gold)
            kit.rect(img, x - 2, y, 2, TILE, gold)
            kit.rect(img, x + TILE, y, 2, TILE, gold)
            kit.well(img, tx, ty, tw, th, glow=gold)
    return img


def providers(ui):
    out = ui.cut_background("vb_vote", vb.VOTE_BASES[0], background(ui))
    for k in range(len(vb.TAC_MAPS)):
        out += ui.cut_background(f"vb_vote{k}", vb.VOTE_BASES[k + 1], background(ui, k))
    return out
