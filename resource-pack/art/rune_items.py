#!/usr/bin/env python3
"""32x32 sprites of the rune system (RuneItems.java): Rune Powder, the stat runes at ranks I to III and the Realm runes.

    python3 resource-pack/art/rune_items.py                    # writes textures, models and item definitions
    python3 resource-pack/art/rune_items.py --sheet F [--all]  # also writes a contact sheet PNG to F (not committed);
                                                               # --all adds the Realm resources of realm32.py

Item models (set with CustomItemDef.itemModel in RuneItems.java):
  vampyuyu:rune/powder              Rune Powder
  vampyuyu:rune/<stat>_<rank>       vitality, ward, might, aether at rank 1, 2, 3 (Rune.itemId)
  vampyuyu:rune/<realm rune id>     scorch, verdant, stasis, rewind, blight, tempest, undertow, soulbind
Written to pack/assets/vampyuyu/{textures/item,models/item,items}/rune/. Stdlib only, deterministic.

Look: a carved tablet with one glyph. A stat rune keeps one silhouette per stat (vitality round, ward a shield, might a
tablet, aether a lozenge) and its colour. The rank reads from the rim, not from the glyph: rank I a worn dark stone rim,
II a 2 px silver rim, III a 2 px gold rim (electrum on Might), and one to three gem studs set into the bottom rim.
A Realm rune is a tall hexagon whose silhouette is changed by a crest or a notch that says what the rune is about
(flame-licked top, leaf sprig, icicles, pinched waist, bitten corner, cracked rim, wavy foot, ring at the top).
"""
import json
import math
import pathlib
import sys

import sprite32 as s

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack/assets/vampyuyu"
H = s.hexrgb
GB = 14  # the glyph box, in pixels


# ---------------------------------------------------------------- glyphs, shapes in a 14x14 box tested at pixel centres
def _big(shape):  # a glyph drawn on a 12 box, stretched to 14
    return lambda x, y: shape(x * 12 / GB, y * 12 / GB)


def _heart():
    return s.union(s.circle(4.2, 4.8, 4.1), s.circle(9.8, 4.8, 4.1), s.poly([(0.2, 6.2), (13.8, 6.2), (7, 13.6)]))


def _algiz():  # protection: a stem with two raised arms
    return s.union(s.seg(7, 0.8, 7, 13.4, 1.0), s.seg(7, 7.4, 1.6, 1.4, 1.0), s.seg(7, 7.4, 12.4, 1.4, 1.0))


def _tiwaz():  # the war rune: a 2 px stem under two 45 degree strokes
    return s.union(s.seg(7, 1.4, 7, 13.4, 1.0), s.seg(7, 1.4, 2.0, 6.4, 1.0), s.seg(7, 1.4, 12.0, 6.4, 1.0))


def _star():   # aether: an eight-point star, long axis rays, short diagonal rays, a narrow waist
    pts = []
    for k in range(16):
        a = math.radians(k * 22.5 - 90)
        r = 7.0 if k % 4 == 0 else 5.0 if k % 4 == 2 else 2.0
        pts.append((7 + r * math.cos(a), 7 + r * math.sin(a)))
    return s.poly(pts)


def _flame():
    outer = s.poly([(6.5, 0), (8.2, 3), (10.4, 5.2), (11, 8), (9.6, 10.8), (6, 12), (2.4, 10.8), (1, 8), (2.4, 5.4),
                    (4.6, 6.4), (5, 3.4)])
    return _big(s.minus(outer, s.poly([(6.2, 6.2), (8, 8.6), (7.4, 10.4), (4.8, 10.4), (4.4, 8.4)])))


def _leaf():
    body = s.poly([(0.6, 11.4), (1.4, 5.4), (5.6, 1.4), (11.4, 0.4), (11.2, 5.6), (7.6, 9.8), (2.4, 11.6)])
    return _big(s.minus(body, s.seg(2.4, 10.2, 9.4, 2.6, 0.45)))


def _snow():
    arms = [s.seg(6 - dx * 5.6, 6 - dy * 5.6, 6 + dx * 5.6, 6 + dy * 5.6, 0.95)
            for dx, dy in ((0, 1), (0.866, 0.5), (0.866, -0.5))]
    return _big(s.union(*arms, s.circle(6, 6, 2.0)))


def _hourglass():
    return _big(s.union(s.rect(0.5, 0, 11.5, 1.8), s.rect(0.5, 10.2, 11.5, 12), s.poly([(1.6, 1.8), (10.4, 1.8), (6.8, 6)]),
                        s.poly([(1.6, 10.2), (10.4, 10.2), (6.8, 6)]), s.poly([(5.2, 6), (1.6, 1.8), (10.4, 1.8)]),
                        s.poly([(5.2, 6), (1.6, 10.2), (10.4, 10.2)])))


def _skull():  # bone: a wide cranium over a narrow jaw, angular sockets, a nose slit, gaps between the teeth
    bone = s.union(s.poly([(1.6, 4.4), (3.6, 0.6), (10.4, 0.6), (12.4, 4.4), (12, 8.2), (10, 9.6), (4, 9.6), (2, 8.2)]),
                   s.rect(4, 9, 10, 13.6))
    return s.minus(bone, _sockets(), s.poly([(7, 8.8), (6.2, 10.2), (7.8, 10.2)]),
                   s.rect(6.1, 10.8, 6.9, 14), s.rect(8.1, 10.8, 8.9, 14))


def _sockets():
    return s.union(s.poly([(2.6, 4.6), (6, 5.4), (5.4, 8.2), (3.2, 7.6)]), s.poly([(11.4, 4.6), (8, 5.4), (8.6, 8.2), (10.8, 7.6)]))


def _bolt():
    return _big(s.poly([(7.6, 0), (1.6, 6.6), (5.4, 6.6), (3.6, 12), (10.6, 4.8), (6.6, 4.8), (9.4, 0)]))


def _waves():  # two thick bands with a curled crest at the right end
    def band(base, phase):
        return lambda x, y: abs(y - (base + 1.8 * math.sin(x * 0.75 + phase))) <= 1.3
    curl = s.minus(s.circle(11.4, 3.2, 2.3), s.circle(11.4, 3.4, 0.9))
    return s.union(band(4.8, 0.2), band(10.4, 1.0), curl)


def _ankh():
    ring = s.minus(s.circle(6, 3.9, 3.7), s.circle(6, 3.9, 1.7))
    return _big(s.union(ring, s.rect(4.9, 6.6, 7.1, 12), s.rect(1.2, 7.6, 10.8, 9.4)))


# ---------------------------------------------------------------- stat runes
# stat: silhouette, glyph, glyph origin, stone (recess, base, light, hi), glyph colour (normal, bright)
STATS = {
    "vitality": (s.circle(16, 16, 13.4), _heart, (9, 6), ("#2a0e16", "#6c2034", "#b0405a", "#f08a9c"), ("#ff5470", "#ffc2cc")),
    "ward": (s.poly([(4, 3), (28, 3), (28, 15), (23, 24), (16, 30), (9, 24), (4, 15)]), _algiz, (9, 5),
             ("#101c30", "#2f527f", "#6a9ad0", "#c4e2ff"), ("#7ccaff", "#e4f6ff")),
    "might": (s.poly([(8, 2), (24, 2), (28, 6), (28, 26), (24, 30), (8, 30), (4, 26), (4, 6)]), _tiwaz, (9, 5),
              ("#24120a", "#5a3414", "#8a5a2a", "#c89a5a"), ("#ffa62e", "#ffe6a0")),
    "aether": (s.poly([(16, 1), (30, 16), (16, 31), (2, 16)]), _star, (9, 7), ("#190f30", "#46288a", "#8664dc", "#dccaff"),
               ("#c79cff", "#f2e6ff")),
}
# rank: (rim dark, rim mid, rim light, rim width)
RIMS = {1: ("#3a3644", "#585264", "#7c7688", 1), 2: ("#6a7484", "#a6b0c0", "#e6eef8", 2), 3: ("#8a5a10", "#d89a22", "#ffe38a", 2)}
STUD_X = {1: (15,), 2: (13, 17), 3: (11, 15, 19)}

# ---------------------------------------------------------------- Realm runes
_HEX = [(16, 1.5), (28, 8), (28, 24), (16, 30.5), (4, 24), (4, 8)]


def _sil_scorch():  # a flame-licked crown
    return s.poly([(4, 24), (4, 8), (5.5, 2.5), (9, 6.5), (11.5, 1.5), (14, 6), (16, 3), (18, 6), (20.5, 1.5), (23, 6.5),
                   (26.5, 2.5), (28, 8), (28, 24), (16, 30.5)])


def _sil_verdant():  # a leaf sprig off the top right
    return s.union(s.poly(_HEX), s.poly([(22.5, 6.5), (24.5, 2.2), (30, 1.6), (29.2, 6.4), (25, 9)]))


def _sil_stasis():  # icicles hanging from the lower edges
    return s.union(s.poly(_HEX), s.poly([(6, 24.6), (9.4, 26.6), (7.6, 31)]), s.poly([(10.4, 27.2), (13.6, 29.2), (11.8, 31)]),
                   s.poly([(26, 24.6), (22.6, 26.6), (24.4, 31)]), s.poly([(21.6, 27.2), (18.4, 29.2), (20.2, 31)]))


def _sil_rewind():  # a pinched waist
    return s.poly([(16, 1.5), (28, 8), (28, 12), (25, 16), (28, 20), (28, 24), (16, 30.5), (4, 24), (4, 20), (7, 16),
                   (4, 12), (4, 8)])


def _sil_blight():  # corroded: bites out of the upper right and the right side
    return s.minus(s.poly(_HEX), s.circle(25.8, 6.6, 2.4), s.circle(21.6, 4.6, 1.6), s.circle(28, 14, 1.9),
                   s.circle(27.4, 19.6, 1.2))


def _sil_tempest():  # a crack through the rim
    return s.minus(s.poly(_HEX), s.seg(22.4, 2.4, 24.0, 5.8, 0.6), s.seg(24.0, 5.8, 22.8, 8.2, 0.6))


def _sil_undertow():  # a wavy foot
    wave = lambda x, y: y <= 28.6 + 1.8 * math.sin(x * 0.7)
    return lambda x, y: s.poly([(16, 1.5), (28, 8), (28, 30), (4, 30), (4, 8)])(x, y) and wave(x, y)


def _sil_soulbind():  # a hollow ring at the top vertex
    return s.union(s.poly([(16, 7.5), (28, 11), (28, 24), (16, 30.5), (4, 24), (4, 11)]),
                   s.minus(s.circle(16, 5.0, 3.5), s.circle(16, 5.0, 1.3)))


# id: (silhouette, glyph, glyph origin, stone (recess, base, light, hi), glyph colour (normal, bright), sockets colour)
REALM_RUNES = {
    "scorch": (_sil_scorch, _flame, (9, 9), ("#2a0c0a", "#7a2414", "#c8501e", "#ff9a4a"), ("#ffb04a", "#fff0a0"), None),
    "verdant": (_sil_verdant, _leaf, (9, 8), ("#1c2812", "#3f5a22", "#6f9a3a", "#a8cc5a"), ("#a8dc4a", "#e8f8b0"), None),
    "stasis": (_sil_stasis, _snow, (9, 8), ("#142640", "#2f5a94", "#6aaad8", "#c8ecff"), ("#bfe8ff", "#ffffff"), None),
    "rewind": (_sil_rewind, _hourglass, (9, 8), ("#3a2408", "#8a5e1e", "#d49a34", "#f6d070"), ("#fff0a8", "#ffffff"), None),
    "blight": (_sil_blight, _skull, (9, 8), ("#1c1c10", "#3e4a1a", "#6e8026", "#a8b848"), ("#d8dcb0", "#f8f8e0"), "#d070c8"),
    "tempest": (_sil_tempest, _bolt, (9, 8), ("#1c1830", "#4a4470", "#8e88b8", "#c8c4e8"), ("#ffe94a", "#fffbd0"), None),
    "undertow": (_sil_undertow, _waves, (9, 7), ("#06141e", "#0e3a50", "#22788a", "#5ac0c8"), ("#3fe0d0", "#c8fff8"), None),
    "soulbind": (_sil_soulbind, _ankh, (9, 10), ("#1a1a1e", "#4a483e", "#85816c", "#c4c0a4"), ("#6affd8", "#e8fff8"), None),
}


def depth_map(mask):
    """Distance (4-neighbour steps) of every pixel of the mask to the outside: 1 on the border."""
    dist = {}
    frontier = [p for p in mask if any((p[0] + dx, p[1] + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    d = 1
    while frontier:
        nxt = []
        for p in frontier:
            if p in dist:
                continue
            dist[p] = d
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (p[0] + dx, p[1] + dy)
                if q in mask and q not in dist:
                    nxt.append(q)
        frontier, d = nxt, d + 1
    return dist


COOL = (26, 16, 56)  # the cool purple the shadow steps lean to


def carve(inside, stone, rim, glyph, origin, colors, rank, studs=(), halo=False, sockets=None):
    """One tablet: rim, stone bevel, inner shadow line, the recess with its glyph, halo, studs and sparks."""
    c = s.Canvas()
    mask = c.mask(inside)
    dist = depth_map(mask)
    recess, base, light, hi = (H(v) for v in stone)
    rdark, rmid, rlight = (H(v) for v in rim[:3])
    rw = rim[3]
    shadow = s.mix(recess, COOL, 0.35)
    fill = s.mix(recess, base, 0.5)   # the recess keeps a tint of the stone
    for (x, y), d in dist.items():
        up = (x - d, y) not in mask or (x, y - d) not in mask or (x - 1, y - d + 1) not in mask
        down = (x + d, y) not in mask or (x, y + d) not in mask or (x + 1, y + d - 1) not in mask
        tone = 1 if up and not down else -1 if down and not up else 0
        if d <= rw:
            col = rlight if tone > 0 else rdark if tone < 0 else rmid
            c.set(x, y, s.mix(col, rdark, 0.35) if d == 2 and rw == 2 and tone <= 0 else col)
        elif d <= 2:   # the bevel of a worn rank I stone
            c.set(x, y, light if tone > 0 else s.mix(base, COOL, 0.4) if tone < 0 else base)
        elif d == 3:   # the inner shadow line: the recess sinks under the rim
            c.set(x, y, shadow)
        else:
            c.set(x, y, fill)
    if rank == 1:      # a few chipped pixels in the worn rim
        rimpx = sorted(p for p, d in dist.items() if d == 1)
        for i in (3, len(rimpx) // 2 + 1, len(rimpx) - 5):
            c.set(rimpx[i][0], rimpx[i][1], s.mix(rdark, (0, 0, 0), 0.4))
    for p in ((7, 12), (24, 21), (21, 9)):  # two or three deliberate chisel marks in the recess
        if dist.get(p, 0) >= 4:
            c.set(p[0], p[1], s.mix(fill, base, 0.5))
    gcol, gbright = H(colors[0]), H(colors[1])
    shape = glyph()
    ox, oy = origin
    pixels = {(ox + x, oy + y) for y in range(GB) for x in range(GB) if shape(x + 0.5, y + 0.5)}
    if halo:  # a faint halo of the glyph colour on the recess around it
        for (x, y) in pixels:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in pixels and dist.get(q, 0) >= 4:
                    c.set(q[0], q[1], s.mix(fill, gcol, 0.3))
    for (x, y) in pixels:  # carved: a shadow under and to the right, then the glyph
        if (x + 1, y + 1) not in pixels and dist.get((x + 1, y + 1), 0) >= 4:
            c.set(x + 1, y + 1, s.mix(fill, (0, 0, 0), 0.5))
    for (x, y) in pixels:
        c.set(x, y, gbright if (rank != 1 and (x, y - 1) not in pixels) else gcol)
    if sockets:  # a second colour inside the glyph (the eye sockets of the skull)
        sk = _sockets()
        for y in range(GB):
            for x in range(GB):
                if sk(x + 0.5, y + 0.5):
                    c.set(ox + x, oy + y, H(sockets))
    for x in studs:  # gem studs set into the bottom rim, at a 4 px pitch
        yb = max(y for (px, y) in mask if px == x)
        for dx in range(-1, 3):
            for dy in range(-2, 1):
                if (x + dx, yb + dy) in mask:
                    c.set(x + dx, yb + dy, H("#14101a"))
        for dx in (0, 1):
            for dy in (-1, 0):
                c.set(x + dx, yb + dy, gcol)
        c.set(x, yb - 1, gbright)
    c.outline()
    for sx, sy in ((29, 3), (2, 28)):  # sparks, drawn after the outline on free pixels only, never the bottom right
        if rank == 3 or rank == 0:
            if c.get(sx, sy) is None:
                c.set(sx, sy, gbright)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if c.get(sx + dx, sy + dy) is None:
                        c.set(sx + dx, sy + dy, gcol)
    return c


def stat_rune(stat, rank):
    inside, glyph, origin, stone, colors = STATS[stat]
    return carve(inside, stone, RIMS[rank], glyph, origin, colors, rank, studs=STUD_X[rank], halo=rank == 3)


def realm_rune(rune_id):
    sil, glyph, origin, stone, colors, sockets = REALM_RUNES[rune_id]
    rim = (stone[0], stone[2], stone[3], 2)
    return carve(sil(), stone, rim, glyph, origin, colors, 0, halo=True, sockets=sockets)


def powder():
    """Rune Powder: a stone mortar heaped with glittering amethyst dust, a pestle leaning on it, loose crystal shards."""
    c = s.Canvas()
    ramp = [H(v) for v in ("#2a1650", "#5a36a0", "#9468e0", "#d2b4ff", "#f4ecff")]
    stone = [H(v) for v in ("#26242e", "#4a4656", "#7a7688", "#aaa6ba")]
    for y in range(15, 30):   # the bowl, lit from the upper left
        half = 12.2 - max(0.0, (y - 21)) * 0.95
        for x in range(32):
            if abs(x + 0.5 - 15) <= half:
                t = (x + 0.5 - (15 - half)) / (2 * half)
                tone = 2 - int(t * 2.4) - (1 if y > 25 else 0)
                c.set(x, y, stone[max(0, min(3, tone + 1))])
    c.rect(3, 15, 27, 16, stone[3])             # the lip
    c.rect(3, 17, 27, 17, stone[1])
    c.rect(12, 29, 18, 29, stone[0])            # the foot
    for x in range(5, 26):                      # the heap above the lip
        top = 15 - 8.5 * max(0.0, 1 - ((x - 15) / 10.5) ** 2) ** 0.8
        for y in range(int(top), 16):
            lit = 1.0 - (x - 4) / 22.0
            v = 0.55 * lit + 0.45 * (y - top) / max(1.0, 15 - top) * -1 + 0.45 + (s.noise(x, y, 11) - 0.5) * 0.5
            c.set(x, y, ramp[max(0, min(3, int(v * 4)))])
    for (x, y) in ((11, 11), (15, 9), (19, 12), (13, 14), (22, 14), (9, 14)):   # glints
        c.set(x, y, ramp[4])
    for i in range(14):
        c.set(21 + i // 2, 12 - i, H("#8a5a2a") if i % 2 else H("#6a4220"))
        c.set(22 + i // 2, 12 - i, H("#b4834a"))
    c.rect(27, 0, 29, 2, H("#b4834a"))
    s.sparkle(c, 6, 8, ramp[2], ramp[4])
    c.set(26, 20, ramp[3])
    c.outline()
    return c


STAT_IDS = ["vitality", "ward", "might", "aether"]
REALM_IDS = list(REALM_RUNES)


def all_sprites():
    out = {"powder": powder()}
    for stat in STAT_IDS:
        for rank in (1, 2, 3):
            out[f"{stat}_{rank}"] = stat_rune(stat, rank)
    for rid in REALM_IDS:
        out[rid] = realm_rune(rid)
    return out


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    sprites = all_sprites()
    for name, canvas in sprites.items():
        tex = PACK / "textures/item/rune" / f"{name}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        tex.write_bytes(canvas.png())
        write_json(PACK / "models/item/rune" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/rune/{name}"}})
        write_json(PACK / "items/rune" / f"{name}.json",
                   {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/rune/{name}"}})
    if "--sheet" in sys.argv:
        cells = [sprites["powder"]] + [None] * 3
        for st in STAT_IDS:
            cells += [sprites[f"{st}_{r}"] for r in (1, 2, 3)] + [None]
        cells += [sprites[r] for r in REALM_IDS]
        if "--all" in sys.argv:  # also the Realm resources (realm32.py): Soul Ash, Pressure Pearl, the Marsh items
            import realm32
            cells += [realm32.draw(n) for n in list(realm32.REALM) + list(realm32.MARSH)]
        s.contact_sheet(sys.argv[sys.argv.index("--sheet") + 1], cells, 4, scale=8)
    print(f"wrote {len(sprites)} rune sprites")


if __name__ == "__main__":
    main()
