#!/usr/bin/env python3
"""32x32 sprites of the rune system (RuneItems.java): Rune Powder, the stat runes at ranks I to III and the Realm runes.

    python3 resource-pack/art/rune_items.py             # writes textures, models and item definitions
    python3 resource-pack/art/rune_items.py --sheet F   # also writes a contact sheet PNG to F (not committed)

Item models (set with CustomItemDef.itemModel in RuneItems.java):
  vampyuyu:rune/powder              Rune Powder
  vampyuyu:rune/<stat>_<rank>       vitality, ward, might, aether at rank 1, 2, 3 (Rune.itemId)
  vampyuyu:rune/<realm rune id>     scorch, verdant, stasis, rewind, blight, tempest, undertow, soulbind
Written to pack/assets/vampyuyu/{textures/item,models/item,items}/rune/. Stdlib only, deterministic.

Look: a carved tablet with one glyph. A stat rune keeps one silhouette per stat (vitality round, ward a shield, might a
tablet, aether a lozenge) and its colour; the rank adds a metal rim (stone, silver, gold), a brighter glyph, a halo and
pips (one to three). A Realm rune is a tall hexagonal crystal with a double rim and four sparks, in the colours of its
Realm. Colour alone never tells two runes apart (Ward and Stasis are both blue): the silhouette and the glyph do.
"""
import json
import pathlib
import sys

import sprite32 as s

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack/assets/vampyuyu"
H = s.hexrgb

# ---------------------------------------------------------------- glyphs, 12x12 shapes tested at pixel centres
def _heart():
    return s.union(s.circle(3.6, 3.9, 3.5), s.circle(8.4, 3.9, 3.5), s.poly([(0.4, 5.2), (11.6, 5.2), (6, 11.8)]))


def _algiz():  # the protection rune: a stem with two raised arms
    return s.union(s.seg(6, 0.5, 6, 11.5, 1.1), s.seg(6, 6.2, 1.2, 1.0, 1.0), s.seg(6, 6.2, 10.8, 1.0, 1.0))


def _tiwaz():  # the war rune: an arrow
    return s.poly([(6, 0), (11.4, 5.6), (7.2, 5.6), (7.2, 12), (4.8, 12), (4.8, 5.6), (0.6, 5.6)])


def _star():   # aether: a four-point star
    return s.poly([(6, 0), (7.2, 4.8), (12, 6), (7.2, 7.2), (6, 12), (4.8, 7.2), (0, 6), (4.8, 4.8)])


def _flame():
    outer = s.poly([(6.5, 0), (8.2, 3), (10.4, 5.2), (11, 8), (9.6, 10.8), (6, 12), (2.4, 10.8), (1, 8), (2.4, 5.4),
                    (4.6, 6.4), (5, 3.4)])
    return s.minus(outer, s.poly([(6.2, 6.2), (8, 8.6), (7.4, 10.4), (4.8, 10.4), (4.4, 8.4)]))


def _leaf():
    body = s.poly([(0.6, 11.4), (1.4, 5.4), (5.6, 1.4), (11.4, 0.4), (11.2, 5.6), (7.6, 9.8), (2.4, 11.6)])
    return s.minus(body, s.seg(2.4, 10.2, 9.4, 2.6, 0.45))


def _snow():
    arms = []
    for dx, dy in ((0, 1), (0.866, 0.5), (0.866, -0.5)):
        arms.append(s.seg(6 - dx * 5.6, 6 - dy * 5.6, 6 + dx * 5.6, 6 + dy * 5.6, 0.95))
    return s.union(*arms, s.circle(6, 6, 2.0))


def _hourglass():
    return s.union(s.rect(0.5, 0, 11.5, 1.8), s.rect(0.5, 10.2, 11.5, 12), s.poly([(1.6, 1.8), (10.4, 1.8), (6.8, 6)]),
                   s.poly([(1.6, 10.2), (10.4, 10.2), (6.8, 6)]), s.poly([(5.2, 6), (1.6, 1.8), (10.4, 1.8)]),
                   s.poly([(5.2, 6), (1.6, 10.2), (10.4, 10.2)]))


def _skull():
    body = s.union(s.circle(6, 5.2, 5.4), s.rect(2.6, 6.4, 9.4, 11.8))
    return s.minus(body, s.circle(4, 5.4, 1.5), s.circle(8, 5.4, 1.5), s.poly([(6, 6.8), (7, 8.2), (5, 8.2)]),
                   s.rect(4.6, 9.4, 5.1, 12), s.rect(6.9, 9.4, 7.4, 12))


def _bolt():
    return s.poly([(7.6, 0), (1.6, 6.6), (5.4, 6.6), (3.6, 12), (10.6, 4.8), (6.6, 4.8), (9.4, 0)])


def _waves():
    def inside(x, y):
        for base in (2.0, 6.0, 10.0):
            if abs(y - (base + 1.3 * __import__("math").sin(x * 0.95 + 0.4))) <= 0.95:
                return True
        return False
    return inside


def _ankh():
    ring = s.minus(s.circle(6, 3.9, 3.7), s.circle(6, 3.9, 1.7))
    return s.union(ring, s.rect(4.9, 6.6, 7.1, 12), s.rect(1.2, 7.6, 10.8, 9.4))


# stat: silhouette, glyph, glyph origin, stone (recess, base, light, hi), glyph colour (normal, bright)
STATS = {
    "vitality": (s.circle(16, 16, 13.2), _heart, (10, 8), ("#2a0e16", "#6c2034", "#b0405a", "#f08a9c"), ("#ff5470", "#ffc2cc")),
    "ward": (s.poly([(4, 3), (28, 3), (28, 15), (23, 24), (16, 30), (9, 24), (4, 15)]), _algiz, (10, 6),
             ("#101c30", "#2f527f", "#6a9ad0", "#c4e2ff"), ("#7ccaff", "#e4f6ff")),
    "might": (s.poly([(8, 2), (24, 2), (28, 6), (28, 26), (24, 30), (8, 30), (4, 26), (4, 6)]), _tiwaz, (10, 7),
              ("#2c1608", "#80441a", "#cc842a", "#ffcf76"), ("#ffa62e", "#ffe6a0")),
    "aether": (s.poly([(16, 1), (30, 16), (16, 31), (2, 16)]), _star, (10, 9), ("#190f30", "#46288a", "#8664dc", "#dccaff"),
               ("#c79cff", "#f2e6ff")),
}
# rank: (rim dark, rim mid, rim light)
RIMS = {1: ("#4a4658", "#6e6878", "#a09aae"), 2: ("#6a7484", "#a6b0c0", "#e6eef8"), 3: ("#8a5a10", "#d89a22", "#ffe38a")}

REALM_RUNES = {  # id: (Realm palette, glyph, stone, glyph colour (normal, bright))
    "scorch": ("ashes", _flame, ("#1a0f20", "#3b2640", "#6a4258", "#a86a58"), ("#ff8a2a", "#ffd36a")),
    "verdant": ("grove", _leaf, ("#1c2812", "#3f5a22", "#6f9a3a", "#a8cc5a"), ("#a8dc4a", "#e8f8b0")),
    "stasis": ("frost", _snow, ("#142640", "#2f5a94", "#6aaad8", "#c8ecff"), ("#bfe8ff", "#ffffff")),
    "rewind": ("citadel", _hourglass, ("#3a2408", "#8a5e1e", "#d49a34", "#f6d070"), ("#fff0a8", "#ffffff")),
    "blight": ("marsh", _skull, ("#1c1c10", "#3e4a1a", "#6e8026", "#a8b848"), ("#d070c8", "#f6b8f0")),
    "tempest": ("storm", _bolt, ("#141a30", "#2e3e7c", "#6a7ed0", "#b4c0f4"), ("#ffe94a", "#fffbd0")),
    "undertow": ("depths", _waves, ("#06141e", "#0e3a50", "#22788a", "#5ac0c8"), ("#3fe0d0", "#c8fff8")),
    "soulbind": ("necro", _ankh, ("#1a1630", "#3e3a64", "#7e78aa", "#bcb6e0"), ("#6affd8", "#e8fff8")),
}
HEX = s.poly([(16, 0.5), (28, 7.5), (28, 24.5), (16, 31.5), (4, 24.5), (4, 7.5)])


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


def carve(inside, stone, rim, glyph, origin, colors, rank_look, pips=0, sparks=()):
    """One tablet: rim, stone bevel, an optional second ring, the recess with its glyph, halo, pips and sparks."""
    c = s.Canvas()
    mask = c.mask(inside)
    dist = depth_map(mask)
    recess, base, light, hi = (H(v) for v in stone)
    rdark, rmid, rlight = (H(v) for v in rim)
    ring, glow, bright_glyph = rank_look
    for (x, y), d in dist.items():
        up = (x - d, y) not in mask or (x, y - d) not in mask or (x - 1, y - d + 1) not in mask
        down = (x + d, y) not in mask or (x, y + d) not in mask or (x + 1, y + d - 1) not in mask
        tone = 1 if up and not down else -1 if down and not up else 0
        if d == 1:
            c.set(x, y, (rlight, rmid, rdark)[1 - tone] if tone else rmid)
        elif d <= 3:
            grain = s.noise(x, y, 7)
            c.set(x, y, light if tone > 0 else s.mix(base, recess, 0.45) if tone < 0 else (light if grain > 0.82 else base))
        elif d == 4 and ring:
            c.set(x, y, rmid if tone >= 0 else rdark)
        else:
            c.set(x, y, recess if s.noise(x, y, 3) > 0.1 else s.mix(recess, base, 0.35))
    gcol, gbright = H(colors[0]), H(colors[1])
    dim = gcol if bright_glyph != 0 else s.mix(gcol, recess, 0.45)
    shape = glyph()
    ox, oy = origin
    pixels = {(ox + x, oy + y) for y in range(12) for x in range(12) if shape(x + 0.5, y + 0.5)}
    if glow:  # a halo of the glyph colour on the recess around it
        for (x, y) in pixels:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in pixels and dist.get(q, 0) >= 4 + (1 if ring else 0):
                    c.set(q[0], q[1], s.mix(recess, gcol, 0.32))
    for (x, y) in pixels:  # carved: a shadow under and to the right, then the glyph, its top edge lighter
        if (x + 1, y + 1) not in pixels and dist.get((x + 1, y + 1), 0) >= 4:
            c.set(x + 1, y + 1, s.mix(recess, (0, 0, 0), 0.5))
    for (x, y) in pixels:
        c.set(x, y, gbright if (bright_glyph == 2 and (x, y - 1) not in pixels) else (gcol if bright_glyph else dim))
    for i in range(pips):  # rank pips under the glyph
        px = 16 + round((i - (pips - 1) / 2) * 3.0) - 1
        c.rect(px, 22, px + 1, 23, gcol if bright_glyph else dim)
        c.set(px, 22, gbright)
    for (x, y) in sparks:
        s.sparkle(c, x, y, gcol, gbright)
    c.outline()
    return c


def stat_rune(stat, rank):
    inside, glyph, origin, stone, colors = STATS[stat]
    look = (rank >= 2, rank >= 3, rank - 1)   # (second ring, halo, glyph level 0 dim / 1 normal / 2 bright)
    sparks = ((3, 3), (28, 28)) if rank == 3 else ()
    return carve(inside, stone, RIMS[rank], glyph, origin, colors, look, pips=rank, sparks=sparks)


def realm_rune(rune_id):
    realm, glyph, stone, colors = REALM_RUNES[rune_id]
    rim = (stone[0], stone[2], stone[3])
    return carve(HEX, stone, rim, glyph, (10, 10), colors, (True, True, 2), sparks=((2, 3), (29, 5), (3, 27), (28, 29)))


def powder():
    """Rune Powder: a heap of ground amethyst with glints, a few motes lifting off it."""
    c = s.Canvas()
    ramp = [H(v) for v in ("#2a1650", "#5a36a0", "#9468e0", "#d2b4ff", "#f4ecff")]

    def top(x):
        return 28 - 12.5 * max(0.0, 1 - ((x - 16) / 12.5) ** 2) ** 0.75
    for x in range(4, 29):
        for y in range(int(top(x + 0.5)), 28):
            t = (y - top(x + 0.5)) / max(1.0, 28 - top(x + 0.5))
            lit = 1.0 - (x - 4) / 24.0
            v = 0.55 * lit + 0.45 * (1 - t) + (s.noise(x, y, 11) - 0.5) * 0.5
            c.set(x, y, ramp[max(0, min(3, int(v * 4)))])
    for (x, y) in ((10, 25), (14, 27), (20, 26), (24, 24), (17, 23)):  # darker grains at the foot
        c.set(x, y, ramp[0])
    for (x, y) in ((12, 19), (18, 17), (21, 21), (15, 22), (9, 23)):   # glints
        c.set(x, y, ramp[4])
    s.sparkle(c, 21, 9, ramp[3], ramp[4])
    s.sparkle(c, 11, 6, ramp[2], ramp[3])
    c.set(25, 13, ramp[3])
    c.set(8, 12, ramp[3])
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
