#!/usr/bin/env python3
"""32x32 sprites of the Realm resources that were placeholders: Soul Ash (Necropolis), Pressure Pearl (Depths) and the
Putrid Marshes items (Rot Resin, Mire Cyst, Mire Core, Mire Ichor, Recycler Module). Stdlib only, deterministic.

    python3 resource-pack/art/realm32.py             # writes ore/soul_ash.png and ore/pressure_pearl.png
    python3 resource-pack/art/realm32.py --sheet F   # also writes a contact sheet PNG to F (not committed)

marsh_items.py writes the five Marsh sprites (and their models) through draw(); the two Realm sprites are listed in
imported.txt so quarry_items.py leaves them alone (its models and item definitions stay valid: same ids).
Palettes are the Realm palettes of boss_items.REALMS.
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
    """A heap of pale ash with embers of soul light, and a wisp of soul flame rising from it."""
    c = s.Canvas()
    ramp = _ramp("#241f3a", "#4a4570", "#7e78aa", "#b4aed8", "#e8e4ff")
    glow, glow_hi = H("#6affd8"), H("#e8fff8")

    def top(x):
        return 29 - 10.5 * max(0.0, 1 - ((x - 15.5) / 12.5) ** 2) ** 0.8
    for x in range(3, 28):
        for y in range(int(top(x + 0.5)), 29):
            t = (y - top(x + 0.5)) / max(1.0, 29 - top(x + 0.5))
            lit = 1.0 - (x - 3) / 25.0
            v = 0.5 * lit + 0.5 * (1 - t) + (s.noise(x, y, 5) - 0.5) * 0.45
            c.set(x, y, ramp[max(0, min(3, int(v * 4.2)))])
    for x, y in ((8, 26), (13, 27), (19, 27), (23, 25), (11, 24), (21, 23)):
        c.set(x, y, ramp[0])
    for x, y in ((10, 22), (15, 24), (19, 21), (22, 24), (13, 20)):   # embers still lit in the ash
        c.set(x, y, glow)
    c.set(15, 24, glow_hi)
    # the wisp: a tilted teardrop of soul flame with a white heart
    wisp = s.poly([(17, 2), (19.5, 7), (21, 12), (19.5, 16.5), (16, 18.5), (12.5, 16.5), (11.5, 12.5), (13.5, 9), (15.5, 6.5)])
    for y in range(s.N):
        for x in range(s.N):
            if wisp(x + 0.5, y + 0.5):
                d = math.hypot(x + 0.5 - 16.3, y + 0.5 - 13.2)
                c.set(x, y, glow_hi if d < 2.2 else glow if d < 4.4 else H("#37b8a0"))
    for x, y in ((17, 3), (18, 5)):
        c.set(x, y, glow)
    s.sparkle(c, 7, 12, H("#37b8a0"), glow)
    c.set(25, 9, glow)
    c.set(24, 15, H("#37b8a0"))
    c.outline()
    return c


def pressure_pearl():
    """A deep-sea pearl: teal body, white sheen, a pink rim light, bubbles and a pressure ring around it."""
    c = s.Canvas()
    ramp = _ramp("#0a1a2a", "#12405a", "#2a8a9a", "#8fd8d8", "#e8ffff")
    s.sphere(c, 14.5, 18, 10.8, ramp, rim=H("#c868a8"))
    for x, y in ((10, 11), (11, 10), (10, 10), (12, 9)):          # the specular highlight
        c.set(x, y, H("#ffffff"))
    c.set(8, 13, H("#ffffff"))
    for (x, y) in ((19, 21), (20, 20), (21, 19)):                  # a pressure line scored on the pearl
        c.set(x, y, ramp[1])
    for (cx, cy, r) in ((26, 6, 2.4), (21, 3, 1.5), (29, 12, 1.3)):   # bubbles rising
        for y in range(s.N):
            for x in range(s.N):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r - 0.9 <= d <= r + 0.3 and c.get(x, y) is None:
                    c.set(x, y, ramp[3])
        c.set(round(cx - r * 0.4), round(cy - r * 0.4), ramp[4])
    c.outline()
    return c


def rot_resin():
    """A drop of amber-green resin gone rotten: faceted, with a purple spore trapped inside and a drip."""
    c = s.Canvas()
    ramp = _ramp("#2a2a10", "#4e5a1a", "#8a9a2e", "#c9d36a", "#f0f4a8")
    body = s.poly([(15, 2), (22, 12), (25, 20), (21, 28), (10, 28), (6, 21), (9, 12)])
    for y in range(s.N):
        for x in range(s.N):
            if body(x + 0.5, y + 0.5):
                u = (x - 6) / 19.0
                v = (y - 2) / 26.0
                tone = 3 - 3.2 * u + 0.6 * (1 - v) * 0 - 0.8 * v
                tone += (s.noise(x, y, 2) - 0.5) * 0.5
                c.set(x, y, ramp[max(0, min(4, round(tone)))])
    for y in range(4, 27):   # a bright facet down the left and a dark one on the right
        c.set(10 + (y - 4) // 5, y, ramp[3])
        c.set(19 - (y < 12) * 0 + (y - 14) // 6 if y > 14 else 17, y, ramp[1])
    spore = s.circle(15.5, 18, 3.3)
    for y in range(s.N):
        for x in range(s.N):
            if spore(x + 0.5, y + 0.5):
                d = math.hypot(x + 0.5 - 14.8, y + 0.5 - 17.2)
                c.set(x, y, H("#f2a0e8") if d < 1.2 else H("#c860b8") if d < 2.4 else H("#7e2e78"))
    c.set(14, 16, H("#ffffff"))
    for x, y in ((16, 8), (17, 9)):
        c.set(x, y, ramp[4])
    c.rect(26, 22, 27, 25, ramp[2])   # a drip
    c.set(26, 22, ramp[3])
    c.set(27, 26, ramp[1])
    c.outline()
    return c


def mire_cyst():
    """A swollen cyst: olive skin with veins and warts, a stalk on top and a magenta pustule ringed by a vein."""
    c = s.Canvas()
    ramp = _ramp("#2a2a10", "#4e5a1a", "#8a9a2e", "#c9d36a", "#f0f4a8")
    s.sphere(c, 15.5, 18, 11.2, ramp)
    c.rect(14, 3, 17, 7, ramp[2])
    c.rect(14, 3, 14, 7, ramp[3])
    c.rect(17, 3, 17, 7, ramp[1])
    c.set(15, 2, ramp[3])
    c.set(16, 2, ramp[2])
    for x, y in ((7, 13), (8, 12), (9, 11), (21, 11), (22, 12), (24, 16), (23, 17)):   # veins
        c.set(x, y, ramp[1])
    for x, y in ((9, 23), (10, 23), (22, 22), (21, 23), (8, 18), (24, 20)):          # warts
        c.set(x, y, ramp[3])
        c.set(x + 1, y + 1, ramp[0])
    p = s.circle(17, 18, 4.4)
    for y in range(s.N):
        for x in range(s.N):
            if p(x + 0.5, y + 0.5):
                d = math.hypot(x + 0.5 - 16, y + 0.5 - 17)
                c.set(x, y, H("#f2a0e8") if d < 1.3 else H("#c860b8") if d < 2.9 else H("#7e2e78"))
    c.set(15, 16, H("#ffffff"))
    c.outline()
    return c


def mire_core():
    """The heart of the Abomination: a dark rotten sphere held in a cage of bone spikes, a magenta crack of light."""
    c = s.Canvas()
    ramp = _ramp("#1c1c10", "#3e4a1a", "#6e8026", "#a8b848", "#d4e08a")
    s.sphere(c, 16, 16, 9.8, ramp)
    for x, y in ((13, 11), (14, 12), (15, 13), (15, 14), (16, 15), (17, 16), (17, 17), (18, 18), (19, 19), (20, 20)):
        c.set(x, y, H("#d070c8"))     # the crack
        c.set(x + 1, y, H("#7e2e78"))
    for x, y in ((14, 11), (16, 14), (18, 17), (19, 20)):
        c.set(x, y, H("#f6b8f0"))
    bone = _ramp("#4a4858", "#7a7888", "#aaa8b8")
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (-0.7, -0.7), (0.7, -0.7), (-0.7, 0.7), (0.7, 0.7)):
        for k in range(10, 15):
            x, y = round(16 + dx * k - 0.5), round(16 + dy * k - 0.5)
            if c.get(x, y) is None:
                c.set(x, y, bone[2] if (dx + dy) < 0 else bone[1])
        x, y = round(16 + dx * 15 - 0.5), round(16 + dy * 15 - 0.5)
        c.set(x, y, bone[0])
    c.outline()
    return c


def mire_ichor():
    """A flask of marsh ichor: glass, a cork, olive liquid with purple clots and bubbles."""
    c = s.Canvas()
    liquid = _ramp("#2a2a10", "#4e5a1a", "#8a9a2e", "#c9d36a")
    glass = _ramp("#5a7078", "#a8c8d0", "#e4f4f8")
    c.rect(13, 2, 18, 4, H("#7a522e"))
    c.rect(13, 2, 18, 2, H("#a87444"))
    c.rect(13, 4, 18, 4, H("#4a2f18"))
    body = s.union(s.rect(13, 5, 18, 12), s.ellipse(15.5, 20, 11.2, 9.6), s.poly([(13, 11), (18, 11), (24, 17), (7, 17)]))
    for y in range(s.N):
        for x in range(s.N):
            if body(x + 0.5, y + 0.5):
                c.set(x, y, glass[1])
    inner = s.union(s.rect(14, 8, 17, 12), s.ellipse(15.5, 20.5, 9.2, 7.6), s.poly([(14, 11), (17, 11), (22, 17), (9, 17)]))
    for y in range(s.N):
        for x in range(s.N):
            if inner(x + 0.5, y + 0.5) and y >= 15:
                t = (x - 6) / 20.0 + (y - 15) / 14.0
                c.set(x, y, liquid[max(0, min(3, 3 - int(t * 3.2) + (1 if s.noise(x, y, 9) > 0.82 else 0)))])
    for x in range(8, 24):                       # the surface of the liquid
        if c.get(x, 15) is not None and inner(x + 0.5, 15.5):
            c.set(x, 15, liquid[3])
    for x, y in ((18, 22), (19, 22), (18, 23), (12, 25), (13, 25), (21, 25)):   # purple clots
        c.set(x, y, H("#c860b8"))
    c.set(19, 22, H("#f2a0e8"))
    for x, y in ((11, 18), (21, 19), (14, 20)):  # bubbles
        c.set(x, y, liquid[3])
    for y in range(6, 12):                       # glass glint down the neck and the shoulder
        c.set(14, y, glass[2])
    for x, y in ((8, 20), (8, 21), (9, 23), (10, 25), (9, 19)):
        c.set(x, y, glass[2])
    c.outline()
    return c


def recycler_module():
    """A Flux module: copper casing, dark screen, three cyan arrows chasing each other, gold pins."""
    c = s.Canvas()
    cu = _ramp("#4a2a12", "#7a4a1e", "#c98442", "#f2c14e", "#fff1b0")
    c.rect(5, 3, 26, 24, cu[2])
    c.rect(5, 3, 26, 4, cu[3])
    c.rect(5, 3, 6, 24, cu[3])
    c.rect(25, 4, 26, 24, cu[1])
    c.rect(5, 23, 26, 24, cu[1])
    for x, y in ((7, 5), (24, 5), (7, 22), (24, 22)):    # rivets
        c.set(x, y, cu[4])
        c.set(x + 1, y + 1, cu[0])
    c.rect(8, 7, 23, 20, H("#1a1622"))
    c.rect(8, 7, 23, 7, H("#0e0b14"))
    cx, cy, rr = 15.5, 13.5, 4.8
    cyan, cyan_hi, cyan_dim = H("#4af0ff"), H("#d8ffff"), H("#2a8a9a")
    for k in range(3):
        a0 = math.radians(20 + k * 120)
        a1 = math.radians(100 + k * 120)
        for i in range(21):
            a = a0 + (a1 - a0) * i / 20
            c.set(math.floor(cx + rr * math.cos(a)), math.floor(cy + rr * math.sin(a)), cyan)
            c.set(math.floor(cx + (rr - 1) * math.cos(a)), math.floor(cy + (rr - 1) * math.sin(a)), cyan_dim)
        px, py = cx + rr * math.cos(a1), cy + rr * math.sin(a1)
        tx, ty = -math.sin(a1), math.cos(a1)
        nx, ny = math.cos(a1), math.sin(a1)
        tip = s.poly([(px + tx * 3.2, py + ty * 3.2), (px - tx * 0.4 + nx * 2.6, py - ty * 0.4 + ny * 2.6),
                      (px - tx * 0.4 - nx * 2.6, py - ty * 0.4 - ny * 2.6)])
        for y in range(s.N):
            for x in range(s.N):
                if tip(x + 0.5, y + 0.5) and 8 <= x <= 23 and 7 <= y <= 20:
                    c.set(x, y, cyan_hi)
    for x in (9, 13, 17, 21):                            # gold pins under the casing
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
        s.contact_sheet(sys.argv[sys.argv.index("--sheet") + 1], cells, 4, scale=6)
    print(f"wrote {len(REALM)} realm sprites (the marsh ones are written by marsh_items.py)")


if __name__ == "__main__":
    main()
