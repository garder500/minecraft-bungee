#!/usr/bin/env python3
"""The two ValoBlock shield icons (32 x 32, painted with paint32: relief lit from the top left, coloured outline). Stdlib only.

    python3 resource-pack/art/valoblock_shields.py      (writes a preview sheet to resource-pack/dist/shields-preview.png)

valoblock_items.py writes them as the shields' item sprites (the Armory cards, the worn chestplate's icon) and their 35 % _locked
twins. Two silhouettes, so the price difference reads at a glance:

  light_ward   Light Ward (+25, frost): a small heater shield, a silver rim round a frosted face, one ice crystal in the middle
  heavy_ward   Heavy Ward (+50, void): a tall tower plate, a thick dark-steel rim with gold rivets, a cross of ribs over a
               voidglass face and a violet core set in gold where the ribs meet
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from paint32 import AQUA, DARKSTEEL, GOLD, IRON, PEARL, R, SAPPHIRE, SILVER, Pic, circle, inter, poly, rect, sub, sym  # noqa: E402

FROST = R("#16345a", "#2a6aa4", "#4ea4dc", "#94d8f6", "#e4f8ff")
ICE = R("#2a5a8a", "#5aa0d4", "#9ad8f4", "#d4f4ff", "#ffffff")
VOIDGLASS = R("#1c0f38", "#3a1a6e", "#6232b0", "#9a66e6", "#d4b8ff")
CORE = R("#3a0e6a", "#7a2ad0", "#b45cff", "#e0a8ff", "#ffffff")


def light_ward():
    p = Pic()
    outer = sym([(16, 4), (10, 4.6), (7, 6.5), (6.6, 14), (8.2, 20), (11.4, 24.6), (16, 28.4)])
    face = sym([(16, 6.6), (10.8, 7), (9, 8.4), (8.8, 14), (10.2, 19.2), (13, 23), (16, 25.6)])
    p.part(outer, SILVER, bevel=2.2, grad=0.35)
    p.part(face, FROST, form="plate", bevel=1.6, grad=0.6, seam="line", lift=-1)
    # a frost sheen: two pale streaks across the upper left of the face
    p.line(10, 11, 13, 8, FROST, 4)
    p.line(10, 13, 11, 12, FROST, 3)
    # the crystal: a cut ice diamond in a silver setting, with a short spine above and below
    p.facet([(16, 9.2), (19.6, 14.6), (16, 21.4), (12.4, 14.6)], ICE, frame=SILVER)
    p.line(16, 7, 16, 8, SILVER, 3)
    p.line(16, 23, 16, 24, SILVER, 2)
    p.put(15, 12, ICE, 4)
    p.put(16, 11, ICE, 4)
    # rim studs
    for x, y in ((9, 6), (22, 6), (8, 15), (23, 15)):
        p.put(x, y, PEARL, 4)
    p.spark(21, 10, ICE)
    return p.rows()


def heavy_ward():
    p = Pic()
    outer = sym([(16, 1.6), (13.6, 2.8), (7, 2.8), (4.2, 4.4), (4.2, 22.4), (6.6, 26.2), (11, 29), (16, 30.6)])
    face = sym([(16, 4.4), (13.4, 5.4), (7.6, 5.4), (6.8, 6.4), (6.8, 21.6), (8.6, 24.6), (12, 26.8), (16, 28.2)])
    p.part(outer, DARKSTEEL, bevel=2.6, grad=0.3, lift=1)
    p.part(face, VOIDGLASS, form="round", bevel=5.0, grad=0.5, seam="line")
    # the voidglass shimmer: two pale streaks across the upper face
    p.line(8, 11, 12, 7, VOIDGLASS, 4)
    p.line(9, 13, 11, 11, VOIDGLASS, 3)
    # the cross of ribs, steel, over the face
    p.part(inter(face, rect(14.6, 4, 17.4, 29)), IRON, bevel=1.2, grad=0.2)
    p.part(inter(face, rect(6, 12.6, 26, 15.4)), IRON, bevel=1.2, grad=0.2)
    # the core where the ribs meet: a violet cabochon in a gold setting, glowing
    p.gem(16, 14, 3.4, CORE, frame=GOLD, fw=1.2)
    p.spark(21, 9, CORE)
    # gold rivets round the rim and on the ribs
    for x, y in ((6, 4), (25, 4), (5, 13), (26, 13), (5, 21), (26, 21), (16, 3), (16, 28)):
        p.gem(x + 0.5, y + 0.5, 0.9, GOLD)
    for x, y in ((16, 7), (16, 22), (9, 14), (23, 14)):
        p.put(x, y, GOLD, 3)
    # cracks of light in the lower face
    p.line(10, 19, 12, 23, CORE, 3)
    p.line(21, 18, 20, 22, CORE, 3)
    return p.rows()


SHIELDS = {"light_ward": light_ward, "heavy_ward": heavy_ward}


def sprite(weapon_id):
    """32 x 32 RGBA rows of a shield (transparent where nothing is painted)."""
    rows = SHIELDS[weapon_id]()
    return [[px if px else (0, 0, 0, 0) for px in row] for row in rows]


if __name__ == "__main__":
    import valoblock_items as vi
    out = HERE.parent / "dist" / "shields-preview.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    k, gap = 8, 8
    sheet = [[(40, 46, 60, 255)] * (2 * 32 * k + 3 * gap) for _ in range(32 * k + 2 * gap)]
    for i, name in enumerate(SHIELDS):
        rows = sprite(name)
        for y in range(32 * k):
            for x in range(32 * k):
                c = rows[y // k][x // k]
                if c[3]:
                    sheet[gap + y][gap + i * (32 * k + gap) + x] = c
    out.write_bytes(vi.png(sheet))
    print("wrote", out)
