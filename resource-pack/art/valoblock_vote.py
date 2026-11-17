#!/usr/bin/env python3
"""The ValoBlock map vote window art (stdlib only, deterministic): one background with the three maps' plan thumbnails in Realm-coloured
frames, and the same background with a gold frame round the map the player voted for. Called by valoblock_ui.menu_font."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import valoblock_tacmap as tac  # noqa: E402
import vb_layout as vb  # noqa: E402

TILE = 18 * vb.VOTE_COLS - 2      # 52 GUI px: three slots and their two gaps


def thumb(j):
    """The map's plan, 2 x 2 box filtered down to 45 x 45."""
    big = tac.overview(j)
    out = tac.canvas(vb.VOTE_THUMB, vb.VOTE_THUMB)
    for y in range(vb.VOTE_THUMB):
        for x in range(vb.VOTE_THUMB):
            px = [big[2 * y + b][2 * x + a] for b in range(2) for a in range(2)]
            out[y][x] = tuple(sum(p[c] for p in px) // 4 for c in range(4))
    return out


def tile_xy(k):
    x, y = vb.slot_xy(vb.VOTE_COLS * k, vb.VOTE_ROW)
    return x, y


def background(ui, picked=None):
    img = ui.window_base()
    for k, mid in enumerate(vb.TAC_MAPS):
        x, y = tile_xy(k)
        ui.frame(img, x, y, TILE, TILE, tac.col("07080b"), tac.col(vb.VOTE_REALM[mid]), 3)
        ui.blit(img, thumb(tac.load(mid)), x + 3, y + 3)
        if picked == k:   # a gold ring in the two px gap round the tile
            gold = tac.col(vb.VOTE_PICK)
            ui.rect(img, x - 2, y - 2, TILE + 4, 2, gold)
            ui.rect(img, x - 2, y + TILE, TILE + 4, 2, gold)
            ui.rect(img, x - 2, y, 2, TILE, gold)
            ui.rect(img, x + TILE, y, 2, TILE, gold)
    return img


def providers(ui):
    out = ui.cut_background("vb_vote", vb.VOTE_BASES[0], background(ui))
    for k in range(len(vb.TAC_MAPS)):
        out += ui.cut_background(f"vb_vote{k}", vb.VOTE_BASES[k + 1], background(ui, k))
    return out
