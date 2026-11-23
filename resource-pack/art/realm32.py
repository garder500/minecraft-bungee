#!/usr/bin/env python3
"""32x32 sprites of the Realm resources that were placeholders: Soul Ash (Necropolis), Pressure Pearl (Depths) and the
Putrid Marshes items (Rot Resin, Mire Cyst, Mire Core, Mire Ichor, Recycler Module). Stdlib only, deterministic.

    python3 resource-pack/art/realm32.py             # writes ore/soul_ash.png and ore/pressure_pearl.png
    python3 resource-pack/art/realm32.py --sheet F   # also writes a contact sheet PNG to F (not committed)

marsh_items.py writes the five Marsh sprites (and their models) through draw(); the two Realm sprites are listed in
imported.txt so quarry_items.py leaves them alone (its models and item definitions stay valid: same ids).
Every sprite keeps inside 1..30 so its outline is never clipped.
"""
import math
import pathlib
import sys

import sprite32 as s

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack/assets/vampyuyu"
H = s.hexrgb


def _ramp(*hexes):
    return [H(v) for v in hexes]


def soul_ash():
    """A low, wide heap of pale ash with embers of soul light, and a tall wisp of soul flame rising from it."""
    c = s.Canvas()
    ramp = _ramp("#2a2440", "#4a4570", "#7e78aa", "#b4aed8", "#e8e4ff")
    glow, glow_hi, glow_dim = H("#6affd8"), H("#e8fff8"), H("#37b8a0")

    def top(x):
        return 29 - 7.5 * max(0.0, 1 - ((x - 15.5) / 13.5) ** 2) ** 0.8
    for x in range(2, 30):
        for y in range(int(top(x + 0.5)), 29):
            t = (y - top(x + 0.5)) / max(1.0, 29 - top(x + 0.5))
            lit = 1.0 - (x - 2) / 27.0
            v = 0.5 * lit + 0.5 * (1 - t) + (s.noise(x, y, 5) - 0.5) * 0.45
            c.set(x, y, ramp[max(0, min(3, int(v * 4.2)))])
    for x, y in ((7, 27), (13, 28), (20, 28), (25, 26), (10, 25), (22, 24)):
        c.set(x, y, ramp[0])
    for x, y in ((9, 24), (15, 25), (19, 23), (24, 26), (12, 22)):   # embers still lit in the ash
        c.set(x, y, glow)
    c.set(15, 25, glow_hi)
    wisp = s.poly([(17, 1.5), (19, 6), (20.5, 11), (19.5, 16), (17.5, 19.5), (15, 21), (13, 19), (12.4, 15), (13.6, 10.5),
                   (15.4, 6.5)])
    for y in range(s.N):
        for x in range(s.N):
            if wisp(x + 0.5, y + 0.5):
                d = math.hypot((x + 0.5 - 16.4) * 1.6, y + 0.5 - 15.5)
                c.set(x, y, glow_hi if d < 2.4 else glow if d < 5.0 else glow_dim)
    for x, y in ((17, 3), (18, 5)):
        c.set(x, y, glow)
    s.sparkle(c, 7, 11, glow_dim, glow)
    c.set(25, 8, glow)
    c.set(24, 14, glow_dim)
    c.outline()
    return c


def pressure_pearl():
    """A deep-sea pearl: teal body in hard steps, a white sheen, a violet-pink rim light, filled bubbles."""
    c = s.Canvas()
    ramp = _ramp("#0c1428", "#12405a", "#2a8a9a", "#8fd8d8", "#e8ffff")
    s.sphere(c, 14.5, 18, 10.8, ramp, rim=H("#9a5ab8"), dither=False)
    for (x, y) in ((9, 12), (10, 11), (9, 11), (11, 10)):          # the specular highlight
        c.set(x, y, H("#ffffff"))
    c.set(8, 13, H("#ffffff"))
    for (x, y) in ((19, 21), (20, 20), (21, 19)):                  # a pressure line scored on the pearl
        c.set(x, y, ramp[1])
    for (cx, cy, r) in ((26.5, 6.5, 2.2), (21.5, 3.5, 1.5), (29.2, 12.2, 1.4)):   # bubbles rising, filled, with a glint
        for y in range(s.N):
            for x in range(s.N):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r and c.get(x, y) is None:
                    c.set(x, y, ramp[2] if d > r * 0.6 else ramp[3])
        if c.get(round(cx - r * 0.45), round(cy - r * 0.45)) is not None:
            c.set(round(cx - r * 0.45), round(cy - r * 0.45), ramp[4])
    c.outline()
    return c


def rot_resin():
    """A teardrop of amber resin gone bad: an olive rot streak through it and a long drip, no spore."""
    c = s.Canvas()
    amber = _ramp("#3a1c08", "#7a4414", "#c47a24", "#f0b04a", "#fff0a0")
    olive = _ramp("#2a2a10", "#4e5a1a", "#8a9a2e", "#c9d36a")
    body = s.poly([(14, 2.5), (19, 9), (23.5, 17), (22.5, 23.5), (18.5, 26.5), (13, 26.5), (9, 23), (8.5, 17), (11, 9.5)])
    for y in range(s.N):
        for x in range(s.N):
            if body(x + 0.5, y + 0.5):
                u, v = (x - 8) / 16.0, (y - 2) / 25.0
                tone = 3.6 - 3.4 * u - 0.7 * v + (s.noise(x, y, 2) - 0.5) * 0.45
                c.set(x, y, amber[max(0, min(4, round(tone)))])
                k = (x - 9) - 0.62 * (y - 9)          # a diagonal streak of rot
                if -0.3 < k < 2.4:
                    c.set(x, y, olive[max(0, min(3, round(1.0 + 1.6 * (1 - u) + (s.noise(x, y, 4) - 0.5))))])
    for y in range(4, 22):   # the bright facet down the left
        c.set(10 + (y - 4) // 6, y, amber[4] if y < 12 else amber[3])
    c.set(16, 5, amber[4])
    c.set(15, 6, amber[4])
    c.rect(15, 26, 16, 29, amber[2])   # a long drip with a bead on the end
    c.set(15, 27, amber[3])
    c.set(16, 29, amber[1])
    c.outline()
    return c


def mire_cyst():
    """A swollen cyst: pink-grey membrane, olive veins, two lumpy lobes, an off-centre pustule, no stalk."""
    c = s.Canvas()
    skin = _ramp("#3a2c38", "#7a5a6c", "#b08a98", "#d8b8c0", "#f0dce0")
    s.lobes(c, [(11.5, 19, 8.8), (20.5, 16.5, 9.6), (16, 24, 6.0)], skin)
    vein = _ramp("#2e3410", "#5a6a1c", "#8a9a2e")
    for (x0, y0, x1, y1) in ((21, 17, 13, 12), (21, 17, 26, 22), (21, 17, 17, 25), (13, 12, 8, 14), (13, 12, 12, 7),
                             (26, 22, 27, 18), (17, 25, 11, 27)):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            x, y = round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)
            if c.get(x, y) is not None:
                c.set(x, y, vein[1] if i % 3 else vein[2])
    for (x, y) in ((8, 22), (9, 23), (24, 12), (25, 13), (14, 27)):     # warts
        if c.get(x, y) is not None:
            c.set(x, y, skin[4])
            c.set(x + 1, y + 1, skin[0])
    p = s.circle(21.4, 16.4, 3.9)
    for y in range(s.N):
        for x in range(s.N):
            if p(x + 0.5, y + 0.5):
                d = math.hypot(x + 0.5 - 20.6, y + 0.5 - 15.6)
                c.set(x, y, H("#f6c8f0") if d < 1.1 else H("#c860b8") if d < 2.6 else H("#7a2a70"))
    c.outline()
    return c


def mire_core():
    """The heart of the Abomination: a near-black green sphere gripped by five thick bone claws, a glowing fissure."""
    c = s.Canvas()
    ramp = _ramp("#080c08", "#141c10", "#243016", "#3a4c1e", "#566e2a")
    s.sphere(c, 16, 16, 10.4, ramp, dither=False)
    magenta, pink, glow_dim = H("#d070c8"), H("#f6b8f0"), H("#7a2a70")
    for (x, y) in ((11.5, 9), (14, 12), (13, 15), (17.5, 18), (19, 22)):   # the fissure, 2 px wide
        s.brush(c, x, y, glow_dim)
    for (x, y) in ((11.5, 9), (14, 12), (13, 15), (17.5, 18), (19, 22)):
        c.set(math.floor(x), math.floor(y), magenta)
        c.set(math.floor(x) + 1, math.floor(y), pink)
    for (x0, y0, x1, y1) in ((11, 9, 14, 12), (14, 12, 13, 15), (13, 15, 17, 18), (17, 18, 19, 22)):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            x, y = round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)
            s.brush(c, x, y, glow_dim)
            c.set(x, y, magenta)
    bone = _ramp("#3a384a", "#7a788a", "#b4b2a4", "#e4e0cc")
    for k in range(5):                         # five claws, each a curve from outside onto the sphere
        a = math.radians(-70 + k * 72)
        p0 = (16 + 13.2 * math.cos(a) - 1, 16 + 13.2 * math.sin(a) - 1)
        p1 = (16 + 11.8 * math.cos(a + 0.35) - 1, 16 + 11.8 * math.sin(a + 0.35) - 1)
        p2 = (16 + 7.4 * math.cos(a + 0.75) - 1, 16 + 7.4 * math.sin(a + 0.75) - 1)
        pts = []
        for i in range(15):
            t = i / 14
            pts.append(((1 - t) ** 2 * p0[0] + 2 * t * (1 - t) * p1[0] + t * t * p2[0],
                        (1 - t) ** 2 * p0[1] + 2 * t * (1 - t) * p1[1] + t * t * p2[1]))
        for (x, y) in pts:
            s.brush(c, x + 1, y + 1, bone[0])
        for i, (x, y) in enumerate(pts):
            s.brush(c, x, y, bone[1] if i < 5 else bone[2])
            c.set(math.floor(x), math.floor(y), bone[3] if i > 4 else bone[2])
    c.outline()
    return c


def mire_ichor():
    """A squat clay-and-glass jar sealed with wax, filled with purple-black ichor with one olive glint."""
    c = s.Canvas()
    ich = _ramp("#120a1c", "#201230", "#34204a", "#4c3068")
    glass = _ramp("#4a5868", "#8aa0ac", "#cfe4ea")
    body = s.union(s.rect(7, 12, 24, 27), s.rect(6, 15, 25, 24), s.rect(10, 9, 21, 12))
    for y in range(s.N):
        for x in range(s.N):
            if body(x + 0.5, y + 0.5):
                c.set(x, y, glass[1])
    inner = s.union(s.rect(9, 14, 22, 26), s.rect(8, 16, 23, 24), s.rect(11, 10, 20, 13))
    for y in range(s.N):
        for x in range(s.N):
            if inner(x + 0.5, y + 0.5) and y >= 15:
                t = (x - 8) / 15.0 + (y - 15) / 12.0
                c.set(x, y, ich[max(0, min(3, 3 - int(t * 2.2) + (1 if s.noise(x, y, 9) > 0.86 else 0)))])
    for y in range(12, 15):        # the neck stays glass over the liquid
        for x in range(11, 21):
            c.set(x, y, glass[1] if y == 12 else ich[1])
    c.rect(9, 15, 22, 15, ich[3])  # the surface
    for (x, y) in ((11, 18), (12, 18), (11, 19)):   # the one olive glint
        c.set(x, y, H("#8a9a2e"))
    c.set(11, 18, H("#c9d36a"))
    for (x, y) in ((18, 22), (19, 21), (14, 24)):
        c.set(x, y, ich[3])
    for y in range(17, 25):        # glass glint on the left wall
        c.set(7, y, glass[2])
    for x in range(8, 11):
        c.set(x, 13, glass[2])
    c.rect(9, 5, 22, 9, H("#6a4a38"))    # the clay lid
    c.rect(9, 5, 22, 5, H("#9a7452"))
    c.rect(9, 9, 22, 9, H("#3a2a20"))
    for (cx, cy, r) in ((16, 8, 3.4),):  # the wax seal, with a drip on the glass
        for y in range(s.N):
            for x in range(s.N):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    c.set(x, y, H("#a02a58") if d > 1.8 else H("#d0508a"))
    c.rect(18, 10, 18, 12, H("#a02a58"))
    c.set(18, 13, H("#7a1a40"))
    c.set(15, 7, H("#f08ab8"))
    c.outline()
    return c


def recycler_module():
    """A Flux module: copper casing with a notch and side connectors, a dark screen with two chasing cyan arrows."""
    c = s.Canvas()
    cu = _ramp("#3a2010", "#7a4a1e", "#c98442", "#f2c14e", "#fff1b0")
    c.rect(5, 3, 26, 22, cu[2])
    c.rect(5, 3, 26, 4, cu[3])
    c.rect(5, 3, 6, 22, cu[3])
    c.rect(25, 4, 26, 22, cu[1])
    c.rect(5, 21, 26, 24, cu[1])         # a 2 px darker bottom face under the casing
    c.rect(5, 23, 26, 24, cu[0])
    for x in range(14, 18):              # the notch in the top edge
        c.p[3][x] = None
        c.p[4][x] = cu[0]
    for y in (9, 10, 11, 12, 16, 17, 18):   # side connectors
        c.rect(2, y, 4, y, cu[3] if y in (10, 17) else cu[1])
        c.rect(27, y, 29, y, cu[3] if y in (10, 17) else cu[1])
    for x, y in ((7, 5), (24, 5), (7, 19), (24, 19)):    # rivets
        c.set(x, y, cu[4])
        c.set(x + 1, y + 1, cu[0])
    c.rect(8, 7, 23, 20, H("#1a1622"))
    c.rect(8, 7, 23, 7, H("#0e0b14"))
    cx, cy, rr = 15.5, 13.5, 4.6
    cyan, cyan_hi, cyan_dim = H("#4af0ff"), H("#d8ffff"), H("#2a8a9a")
    for k in range(2):                   # two thick arcs, each ending in an arrowhead
        a0, a1 = math.radians(205 + k * 180), math.radians(335 + k * 180)
        for y in range(s.N):
            for x in range(s.N):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                ang = (math.atan2(dy, dx) - a0) % (2 * math.pi)
                if abs(math.hypot(dx, dy) - rr) <= 1.0 and ang <= a1 - a0:
                    c.set(x, y, cyan if ang > 0.25 * (a1 - a0) else cyan_dim)
        px, py = cx + rr * math.cos(a1), cy + rr * math.sin(a1)
        tx, ty = -math.sin(a1), math.cos(a1)
        nx, ny = math.cos(a1), math.sin(a1)
        tip = s.poly([(px + tx * 3.6, py + ty * 3.6), (px + nx * 2.9, py + ny * 2.9), (px - nx * 2.9, py - ny * 2.9)])
        for y in range(8, 20):
            for x in range(9, 23):
                if tip(x + 0.5, y + 0.5):
                    c.set(x, y, cyan_hi)
    for x in (9, 13, 17, 21):            # gold pins under the casing
        c.rect(x, 25, x + 1, 28, cu[3])
        c.set(x, 25, cu[4])
        c.set(x + 1, 28, cu[1])
    c.outline()
    return c


MARSH = {"rot_resin": rot_resin, "mire_cyst": mire_cyst, "mire_core": mire_core, "mire_ichor": mire_ichor,
         "recycler_module": recycler_module}
REALM = {"soul_ash": soul_ash, "pressure_pearl": pressure_pearl}


def draw(name):
    return {**MARSH, **REALM}[name]()


def main():
    for name in REALM:
        tex = PACK / "textures/item/ore" / f"{name}.png"
        tex.write_bytes(draw(name).png())
    if "--sheet" in sys.argv:
        cells = [draw(n) for n in list(REALM) + list(MARSH)]
        s.contact_sheet(sys.argv[sys.argv.index("--sheet") + 1], cells, 4, scale=8)
    print(f"wrote {len(REALM)} realm sprites (the marsh ones are written by marsh_items.py)")


if __name__ == "__main__":
    main()
