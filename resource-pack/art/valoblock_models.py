#!/usr/bin/env python3
"""ValoBlock 3D weapon models v2, stdlib only, deterministic (boxes on a 32x32 atlas, no side-view extrusion any more).

    python3 resource-pack/art/valoblock_models.py        (also run at the end of valoblock_items.py)

Every weapon class (pistol, revolver, smg, shotgun, rifle, sniper, lmg, knife) is an original hand-placed list of
boxes in model pixels, built along +x (muzzle at the high x, grip near the middle): receiver, slide or shroud, rails,
iron sights, trigger guard, trigger, magazine, stock, muzzle as separate parts (about 20-30 boxes each). The metal of
the weapon (Bronzite, Sunstone, Frostium, Voidglass, bone, marsh iron, storm glass, starcore) only recolours it.

Why the old guns rendered black: the outline colour "o" (06070a) and Voidglass "a" (05030a) were near-black tiles,
and vanilla shades the down and side faces (0.5-0.8) on top. Now every atlas tile is lifted to a minimum luminance
(FLOOR, 31% grey), the side tiles are baked a little darker than the top tiles, and the elements are `shade: false`
so the engine does not darken them again: no face is below 25% grey (checked by scripts/test_valoblock_models.py).

Atlas: 16 tiles of 8x8 pixels (uv 4x4), two per material: lit (top faces) and side (all others).

Per weapon (shields stay flat sprites, written by valoblock_items.py), under the ValoBlock pack:
  models/item/valoblock/<id>_3d.json       the gun held (hip pose: lower right third, about 1.4x the v1 size)
  models/item/valoblock/<id>_fire.json     muzzle flash and a slight kick (shown 2 ticks after a shot)
  models/item/valoblock/<id>_reload.json   the magazine (or pump) out, gun tilted
  models/item/valoblock/<id>_aim.json      same gun, centred under the crosshair (sneaking)
  models/item/valoblock/<id>_inspect.json  the gun turned to show its side (1 s after a weapon swap)
  textures/item/valoblock/<id>_3d.png      32x32 atlas
  items/valoblock/<id>.json                select on display_context: gui -> flat sprite (the hotbar, the Armory),
                                           anything else -> select on custom_model_data string 0
                                           ("fire" | "reload" | "aim" | "inspect"), fallback the held model
and the sniper scope overlay textures/misc/scope.png (equippable camera_overlay, see VbWeaponService).
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import boss_skins as bs  # noqa: E402
import valoblock_items as vi  # noqa: E402

PACK = vi.PACK

# Material order: two atlas tiles each (lit, side). f = muzzle flash (never darkened).
MATS = ["o", "a", "b", "c", "d", "g", "e", "f"]
FLASH = (255, 222, 120)
FLOOR = 80          # minimum luminance (0-255) of any lit tile pixel: 31% grey
SIDE_FLOOR = 72     # ... of any side pixel: 28% grey (the spec says 25%, 64)
SIDE_K = 0.82       # side tiles are the lit ones a little darker
RAMP = {"o": 80, "a": 94, "b": 114, "c": 142, "d": 175, "g": 96}   # per material minimum luminance


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def lift(c, floor):
    """Raises a colour to a minimum luminance, keeping a tint of its hue (a plain multiply blows up near-black colours)."""
    l = lum(c)
    if l >= floor:
        return tuple(c)
    m = max(c) or 1
    add = floor - l
    return tuple(min(255, round(v + add * (0.6 + 0.4 * v / m))) for v in c)


def atlas_materials(metal):
    pal = dict(vi.COMMON)
    pal.update(vi.METALS[metal])
    out = {}
    for ch in MATS:
        if ch == "f":
            out[ch] = FLASH
        elif ch == "e":
            out[ch] = vi.rgb(pal["e"])
        elif ch == "o":   # the dark steel of small parts: the metal's own shadow colour, lifted
            out[ch] = lift(tuple(int(v * 0.8) for v in vi.rgb(pal["a"])), RAMP["o"])
        else:
            out[ch] = lift(vi.rgb(pal[ch]), RAMP[ch])
    return out


def atlas(metal, seed):
    """32x32 RGBA rows: 4x4 tiles of 8x8, material i has its lit tile at index 2i and its side tile at 2i + 1."""
    import random
    rnd = random.Random(seed)
    px = [[(0, 0, 0, 255)] * 32 for _ in range(32)]
    for i, ch in enumerate(MATS):
        base = atlas_materials(metal)[ch]
        for side in (0, 1):
            t = 2 * i + side
            u, v = (t % 4) * 8, (t // 4) * 8
            for y in range(8):
                for x in range(8):
                    k = 1.0 + rnd.uniform(-0.05, 0.05)
                    if ch in ("e", "f"):
                        k = 1.12 - 0.05 * (x + y) / 7 + rnd.uniform(-0.03, 0.03)
                    else:
                        if y == 0 or x == 0:
                            k *= 1.08            # bevel catching the light
                        if y == 7 or x == 7:
                            k *= 0.9
                        if ch in ("b", "c") and y == 4:
                            k *= 0.94            # panel line
                        if ch == "g" and (x + y) % 4 == 0:
                            k *= 0.9             # grip checkering
                        if side and ch not in ("e", "f"):
                            k *= SIDE_K
                    c = tuple(min(255, round(a * k)) for a in base)
                    if ch not in ("e", "f"):
                        c = lift(c, SIDE_FLOOR if side else FLOOR)
                    px[v + y][u + x] = (c[0], c[1], c[2], 255)
    return px


class Gun:
    """Boxes of one gun. box() works in model pixels, z is centred on 8."""

    def __init__(self, name, ident):
        self.name = name
        self.elements = []

    def tile(self, mat, side):
        i = 2 * MATS.index(mat) + (1 if side else 0)
        return (i % 4) * 4, (i // 4) * 4

    def box(self, x0, x1, y0, y1, mat, z=2.0, rot=None, dx=0.0, dy=0.0, zc=8.0):
        a = (x0 + dx, y0 + dy, zc - z / 2)
        b = (x1 + dx, y1 + dy, zc + z / 2)
        for i in range(3):
            if not (-16 <= a[i] <= 32 and -16 <= b[i] <= 32):
                raise ValueError(f"{self.name}: element out of the model range {a} {b}")
            if abs(b[i] - a[i]) < 0.05:
                raise ValueError(f"{self.name}: flat element {a} {b}")
        faces = {}
        for f in ("north", "east", "south", "west", "down", "up"):
            u, v = self.tile(mat, f != "up")
            faces[f] = {"uv": [u, v, u + 4, v + 4], "texture": "#0"}
        el = {"from": [round(n, 3) for n in a], "to": [round(n, 3) for n in b], "shade": False, "faces": faces}
        if rot:
            angle, origin = rot
            el["rotation"] = {"angle": angle, "axis": "z", "origin": [origin[0] + dx, origin[1] + dy, 8]}
        self.elements.append(el)


# Each part: (x0, x1, y0, y1, material, depth z, options). Options: "mag" (moves in the reload variant),
# "rot" (angle about z around the part's top centre, a raked grip or a curved magazine).
def P(x0, x1, y0, y1, mat, z=2.0, **o):
    return (x0, x1, y0, y1, mat, z, o)


PARTS = {
    "pistol": [
        P(3, 14, 8.2, 10.6, "c", 2.2),                    # slide
        P(4, 13, 10.6, 11.1, "d", 0.9),                   # slide rib
        P(5, 9, 9.0, 9.7, "e", 2.3),                      # glow strip in the ejection port
        P(9.5, 10.2, 8.4, 10.4, "o", 2.3),                # serrations
        P(10.8, 11.5, 8.4, 10.4, "o", 2.3),
        P(14, 15.6, 8.5, 10.1, "o", 1.4),                 # barrel and muzzle
        P(15.2, 15.8, 8.8, 9.8, "a", 1.0),
        P(3, 12, 6.8, 8.2, "b", 2.4),                     # frame
        P(11, 13, 7.0, 7.9, "a", 1.6),                    # rail
        P(8.8, 9.6, 5.2, 6.9, "a", 1.8),                  # trigger guard front
        P(6.0, 9.6, 5.0, 5.7, "a", 1.8),                  # guard bottom
        P(7.2, 7.8, 5.6, 7.0, "o", 0.8),                  # trigger
        P(3.4, 6.7, 1.8, 6.8, "g", 2.4, rot=(-12.5, (5, 6.8))),   # raked grip
        P(3.0, 6.4, 1.2, 1.9, "a", 2.7, rot=(-12.5, (5, 6.8))),   # magazine base plate
        P(3, 4.2, 10.6, 11.7, "o", 1.2),                  # rear sight
        P(13.2, 14.2, 10.6, 11.5, "o", 0.9),              # front sight
        P(2.4, 3.2, 8.6, 10.2, "a", 1.8),                 # hammer
    ],
    "revolver": [
        P(7, 15.5, 8.4, 10.2, "c", 2.0),                  # barrel shroud
        P(7, 15.5, 10.2, 10.7, "d", 0.9),                 # top rib
        P(8, 15, 7.2, 8.4, "b", 1.6),                     # under lug
        P(15.2, 16, 8.7, 9.9, "a", 1.2),                  # muzzle crown
        P(3.8, 8, 6.6, 10.6, "b", 3.2),                   # cylinder
        P(4.4, 5.0, 6.8, 10.4, "o", 3.4), P(5.8, 6.4, 6.8, 10.4, "o", 3.4), P(7.2, 7.8, 6.8, 10.4, "o", 3.4),   # flutes
        P(5.2, 6.6, 7.8, 9.0, "e", 3.5),                  # glow chamber
        P(2.4, 5.6, 6.6, 11.0, "c", 2.4),                 # frame
        P(1.6, 2.8, 10.2, 12.0, "a", 1.2),                # hammer
        P(5.5, 8.4, 5.0, 5.7, "a", 1.8), P(8.0, 8.7, 5.2, 6.8, "a", 1.8),   # trigger guard
        P(5.6, 6.2, 5.6, 7.0, "o", 0.8),                  # trigger
        P(0.8, 4.2, 1.6, 6.6, "g", 2.5, rot=(-22.5, (2.5, 6.6))),    # grip
        P(14.6, 15.4, 10.6, 11.6, "o", 0.9),              # front sight
        P(2.6, 3.6, 11.0, 11.7, "o", 1.2),                # rear sight
    ],
    "smg": [
        P(2.5, 13, 6.4, 10.0, "b", 2.6),                  # body
        P(3, 12.5, 10.0, 10.7, "c", 2.0),                 # top cover
        P(4, 12, 10.7, 11.2, "d", 0.9),                   # rail
        P(13, 16, 7.2, 9.6, "o", 2.1),                    # shroud
        P(13.6, 14.0, 7.5, 9.3, "a", 2.3), P(14.8, 15.2, 7.5, 9.3, "a", 2.3),   # vents
        P(15.6, 16.0, 7.8, 9.0, "a", 1.2),                # muzzle
        P(0, 2.5, 7.0, 9.6, "a", 1.4),                    # stock stub
        P(0, 0.8, 6.6, 10.0, "o", 2.0),                   # butt plate
        P(7.2, 9.6, 1.4, 6.4, "a", 1.9, mag=True),        # magazine
        P(7.0, 9.8, 1.0, 1.6, "o", 2.2, mag=True),
        P(4.2, 6.4, 2.4, 6.4, "g", 2.2, rot=(-12.5, (5.3, 6.4))),     # grip
        P(10.2, 12.4, 4.4, 6.4, "g", 2.0),                # foregrip
        P(6.9, 9.8, 5.0, 5.6, "a", 1.8), P(9.3, 9.9, 5.2, 6.4, "a", 1.8),   # trigger guard
        P(7.3, 7.8, 5.6, 6.5, "o", 0.8),                  # trigger
        P(4, 5, 11.2, 12.0, "o", 1.2), P(12, 12.8, 11.2, 11.9, "o", 0.9),    # sights
        P(5.5, 8.5, 8.2, 8.9, "e", 2.7),                  # glow strip
    ],
    "shotgun": [
        P(6, 16, 9.0, 10.6, "c", 2.0),                    # barrel
        P(6, 16, 10.6, 11.0, "d", 0.9),                   # rib
        P(6, 15.4, 7.6, 9.0, "o", 1.6),                   # magazine tube
        P(15.4, 16.0, 7.6, 9.0, "a", 1.8),                # tube cap
        P(8.6, 13, 6.6, 8.4, "g", 2.7, mag=True),         # pump
        P(9.6, 10.0, 6.8, 8.2, "o", 2.9, mag=True), P(11.2, 11.6, 6.8, 8.2, "o", 2.9, mag=True),   # pump grooves
        P(3, 8, 6.8, 10.8, "b", 2.6),                     # receiver
        P(4.4, 6.6, 9.2, 10.4, "e", 2.8),                 # glow port
        P(0, 3.4, 6.0, 9.8, "g", 2.2, rot=(-22.5, (3.4, 9.8))),      # stock
        P(0, 0.9, 5.4, 9.2, "a", 2.4),                    # recoil pad
        P(3.4, 5.4, 3.8, 6.8, "g", 2.0),                  # pistol grip
        P(5.2, 8.2, 5.0, 5.7, "a", 1.8), P(7.6, 8.3, 5.2, 6.8, "a", 1.8),   # trigger guard
        P(5.8, 6.4, 5.6, 6.9, "o", 0.8),                  # trigger
        P(15.4, 16.0, 11.0, 11.8, "d", 0.8),              # bead sight
        P(6.5, 7.5, 11.0, 11.6, "o", 1.0),
    ],
    "rifle": [
        P(4, 10, 7.0, 10.4, "b", 2.6),                    # receiver
        P(10, 14.2, 7.4, 9.9, "c", 2.4),                  # handguard
        P(10.6, 11.0, 7.6, 9.7, "o", 2.6), P(12.0, 12.4, 7.6, 9.7, "o", 2.6), P(13.2, 13.6, 7.6, 9.7, "o", 2.6),   # vents
        P(14.2, 16, 8.4, 9.4, "o", 1.4),                  # barrel
        P(15.0, 16, 7.9, 9.9, "a", 1.9),                  # muzzle brake
        P(3, 14, 10.4, 11.0, "d", 1.0),                   # top rail
        P(3.4, 4.6, 11.0, 12.0, "o", 1.2), P(13.2, 14.0, 11.0, 12.2, "o", 0.9),   # sights
        P(0, 4, 6.0, 9.8, "g", 2.2, rot=(-22.5, (4, 9.8))),         # stock
        P(0, 0.9, 5.6, 9.4, "a", 2.4),                    # butt plate
        P(5.2, 7.4, 3.4, 7.0, "g", 2.2, rot=(-22.5, (6.3, 7.0))),   # grip
        P(8.2, 10.6, 3.0, 7.0, "a", 1.9, rot=(22.5, (9.4, 7.0)), mag=True),   # curved magazine
        P(6.6, 9.8, 5.9, 6.5, "a", 1.8),                  # trigger guard
        P(7.0, 7.5, 6.3, 7.0, "o", 0.8),                  # trigger
        P(5.5, 8.5, 8.4, 9.2, "e", 2.8),                  # glow strip
    ],
    "sniper": [
        P(4.5, 10, 6.8, 9.8, "b", 2.4),                   # receiver
        P(10, 16, 8.0, 9.2, "o", 1.4),                    # long barrel
        P(15.0, 16, 7.6, 9.6, "a", 1.8),                  # muzzle brake
        P(10, 13.5, 7.0, 8.0, "c", 1.8),                  # fore-end
        P(0, 4.5, 6.0, 9.6, "g", 2.2, rot=(-22.5, (4.5, 9.6))),     # stock
        P(0, 0.9, 5.6, 9.2, "a", 2.4),
        P(2.0, 3.8, 6.0, 7.2, "g", 1.2),                  # cheek riser block
        P(5.0, 7.0, 3.6, 6.8, "g", 2.0),                  # grip
        P(7.6, 9.2, 4.6, 6.8, "a", 1.8, mag=True),        # magazine
        P(5.8, 8.6, 5.7, 6.3, "a", 1.8),                  # trigger guard
        P(6.2, 6.7, 6.0, 6.9, "o", 0.8),
        P(5.4, 7.2, 9.8, 10.6, "o", 3.0),                 # bolt handle base
        P(7.0, 8.6, 9.9, 10.5, "o", 4.4), P(8.4, 9.2, 9.7, 10.7, "a", 4.8),   # bolt handle and knob
        P(4.6, 5.4, 9.8, 11.2, "o", 1.4), P(10.0, 10.8, 9.8, 11.2, "o", 1.4),   # scope mounts
        P(4.0, 11.4, 11.2, 12.6, "a", 2.4),               # scope tube
        P(4.2, 11.2, 12.6, 13.0, "d", 0.9),               # scope highlight
        P(3.4, 4.2, 10.9, 12.9, "o", 3.2),                # eyepiece
        P(11.4, 12.6, 10.8, 13.0, "o", 3.4),              # objective bell
        P(12.5, 12.9, 11.2, 12.6, "e", 2.6),              # lens
        P(7.0, 8.4, 11.9, 13.4, "o", 1.4),                # turret
        P(13.0, 14.0, 4.4, 8.0, "o", 0.9), P(14.8, 15.6, 4.4, 8.0, "o", 0.9),   # bipod legs
    ],
    "lmg": [
        P(3, 11, 6.8, 10.4, "b", 2.8),                    # body
        P(3.5, 10.5, 10.4, 11.0, "c", 2.2),
        P(4.4, 5.0, 11.0, 11.8, "o", 1.2),                # rear sight
        P(6.0, 7.0, 11.0, 12.4, "o", 1.0), P(9.0, 10.0, 11.0, 12.4, "o", 1.0), P(6.0, 10.0, 12.0, 12.6, "d", 1.0),   # carry handle
        P(11, 15.2, 7.4, 10.0, "o", 3.0),                 # heat-shield sleeve
        P(11.6, 12.0, 7.6, 9.8, "a", 3.2), P(12.8, 13.2, 7.6, 9.8, "a", 3.2), P(14.0, 14.4, 7.6, 9.8, "a", 3.2),   # vents
        P(15.2, 16, 8.0, 9.4, "o", 1.6), P(15.4, 16, 7.8, 9.6, "a", 2.0),   # barrel tip and flash hider
        P(0, 3, 6.4, 9.8, "g", 2.4),                      # stock
        P(0, 0.8, 6.0, 9.6, "a", 2.6),
        P(6.4, 10.0, 3.2, 6.8, "a", 3.4, mag=True),       # drum magazine
        P(6.8, 9.6, 3.6, 6.4, "b", 3.6, mag=True),        # drum cap
        P(7.6, 8.8, 4.4, 5.6, "e", 3.7, mag=True),        # drum glow
        P(3.4, 5.4, 3.4, 6.8, "g", 2.2),                  # grip
        P(5.2, 6.4, 5.6, 6.2, "a", 1.8),                  # trigger guard
        P(4.6, 5.0, 5.8, 6.8, "o", 0.8),                  # trigger
        P(14.2, 14.9, 4.2, 7.4, "o", 0.9), P(15.4, 16, 4.2, 7.4, "o", 0.9),     # bipod legs
        P(13.8, 15.6, 7.0, 7.5, "a", 1.8),                # bipod mount
    ],
    "knife": [
        P(7.4, 13, 7.9, 10.0, "c", 1.4),                  # blade
        P(13, 15.6, 8.4, 9.9, "c", 1.2),                  # tapered tip
        P(15.4, 16, 9.0, 9.9, "d", 0.9),                  # point
        P(7.4, 15.6, 9.9, 10.3, "d", 0.8),                # spine and edge highlight
        P(8.0, 14, 8.9, 9.3, "a", 1.5),                   # fuller
        P(5.8, 7.4, 6.6, 11.4, "o", 3.2),                 # crossguard
        P(6.0, 6.8, 8.2, 9.6, "e", 3.4),                  # guard gem
        P(1.4, 5.8, 7.6, 9.8, "g", 2.0),                  # grip
        P(2.2, 2.7, 7.4, 10.0, "o", 2.2), P(3.4, 3.9, 7.4, 10.0, "o", 2.2), P(4.6, 5.1, 7.4, 10.0, "o", 2.2),   # wraps
        P(0.2, 1.4, 7.0, 10.4, "b", 2.6),                 # pommel
    ],
}
# Muzzle (x, y centre) of each class for the flash.
MUZZLE = {"pistol": (15.8, 9.3), "revolver": (16, 9.3), "smg": (16, 8.4), "shotgun": (16, 9.8), "rifle": (16, 8.9),
          "sniper": (16, 8.6), "lmg": (16, 8.7)}
# How the magazine part leaves in the reload variant: (dx, dy).
MAG_MOVE = {"pistol": (0, -4), "revolver": (0, -4), "smg": (0, -4), "shotgun": (-3.5, 0), "rifle": (0, -4),
            "sniper": (0, -4), "lmg": (0, -4.5)}

# Display transforms. v1 scale was 0.8 in first person: x1.4 = 1.12. Model +x points forward through the y -90 turn
# (the vanilla handheld convention); the translation (hand space, 1/16 block) pushes the gun forward so the stock
# is not in the camera and keeps it in the lower right.
SC = 1.12


def mirror(d):
    """Left-hand twin: x translation and y/z rotation negated, as vanilla does for the other hand."""
    out = {}
    for k, v in d.items():
        if "righthand" in k:
            lk = k.replace("righthand", "lefthand")
            v2 = dict(v)
            if "translation" in v2:
                t = v2["translation"]
                v2["translation"] = [-t[0], t[1], t[2]]
            if "rotation" in v2:
                r = v2["rotation"]
                v2["rotation"] = [r[0], -r[1], -r[2]]
            out[lk] = v2
        out[k] = v
    return out


def display(fp_t, fp_r, tp_t=(0, 2.5, 1.5), tp_r=(0, -90, 0)):
    d = {
        "firstperson_righthand": {"rotation": list(fp_r), "translation": list(fp_t), "scale": [SC] * 3},
        "thirdperson_righthand": {"rotation": list(tp_r), "translation": list(tp_t), "scale": [1.0] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [0.7] * 3},
        "fixed": {"rotation": [0, 180, 0], "scale": [1.1] * 3},
        "gui": {"rotation": [20, 220, 0], "scale": [1.0] * 3},
    }
    return mirror(d)


HIP = display((1.0, 0.0, -6.0), (0, -90, 0))
# Aim: sights under the crosshair (about 9 units left of the hand origin, 5 up).
AIM = display((-8.0, 3.0, -6.0), (0, -90, 0), tp_t=(0, 3.2, 2.0))
# Reload: the gun tilts muzzle-up and comes toward the middle so the magazine drop is visible.
RELOAD = display((-1.0, 1.5, -5.0), (0, -90, 28), tp_r=(0, -90, 20))
# Inspect: the gun turns to show its side for a second after a swap.
INSPECT = display((-3.0, 3.0, -5.0), (12, -35, -8), tp_r=(0, -50, 0))


def with_kick(d):
    """The recoil turn: the muzzle rises about 6 degrees (z rotation is the pitch of a +x model in hand)."""
    out = {}
    for k, v in d.items():
        v = dict(v)
        if "firstperson" in k or "thirdperson" in k:
            rot = list(v.get("rotation", [0, 0, 0]))
            rot[2] += 6 if "right" in k else -6
            v["rotation"] = rot
        out[k] = v
    return out


def build(w, state="base"):
    t = w["texture"]
    g = Gun(w["id"], w["id"])
    mdx, mdy = MAG_MOVE.get(t, (0, 0))
    for x0, x1, y0, y1, mat, z, o in PARTS[t]:
        dx = dy = 0.0
        if state == "reload" and o.get("mag"):
            dx, dy = mdx, mdy
        if state == "fire":
            dx = -0.8
        g.box(x0, x1, y0, y1, mat, z, rot=o.get("rot"), dx=dx, dy=dy)
    if state == "fire" and t in MUZZLE:
        flash(g, *MUZZLE[t])
    return g


def flash(g, mx, my):
    """A 2-tick muzzle flash: a core and four spikes in front of the muzzle."""
    x = mx + 0.2
    for (x0, x1, y0, y1, z) in ((x, x + 1.8, my - 0.9, my + 0.9, 2.0), (x + 1.8, x + 4.0, my - 0.3, my + 0.3, 0.6),
                                (x + 0.3, x + 1.1, my + 0.9, my + 2.6, 0.6), (x + 0.3, x + 1.1, my - 2.6, my - 0.9, 0.6)):
        g.box(x0, x1, y0, y1, "f", z)
    g.box(x + 0.3, x + 1.1, my - 0.3, my + 0.3, "f", 2.0 + 2.8, zc=8.0)


def model_json(g, disp, name):
    tex = f"vampyuyu:item/valoblock/{name}_3d"
    return {"textures": {"0": tex, "particle": tex}, "gui_light": "front", "display": disp, "elements": g.elements}


def definition(name):
    ref = lambda m: {"type": "minecraft:model", "model": f"vampyuyu:item/valoblock/{m}"}  # noqa: E731
    return {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": ref(name)}],
        "fallback": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": "fire", "model": ref(f"{name}_fire")},
                      {"when": "reload", "model": ref(f"{name}_reload")},
                      {"when": "inspect", "model": ref(f"{name}_inspect")},
                      {"when": "aim", "model": ref(f"{name}_aim")},
                      {"when": "scope", "model": {"type": "minecraft:empty"}}],  # scoped sniper: nothing in view
            "fallback": ref(f"{name}_3d")}}}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def write_weapon(w):
    name = w["id"]
    d = PACK / "models/item/valoblock"
    base = build(w)
    dump(d / f"{name}_3d.json", model_json(base, HIP, name))
    dump(d / f"{name}_aim.json", model_json(base, AIM, name))
    dump(d / f"{name}_inspect.json", model_json(base, INSPECT, name))
    if w["texture"] == "knife":   # no reload, no flash: the states fall back to the held model
        for state in ("fire", "reload"):
            dump(d / f"{name}_{state}.json", model_json(base, HIP, name))
    else:
        dump(d / f"{name}_fire.json", model_json(build(w, "fire"), with_kick(HIP), name))
        dump(d / f"{name}_reload.json", model_json(build(w, "reload"), RELOAD, name))
    dump(PACK / "items/valoblock" / f"{name}.json", definition(name))
    tex = PACK / "textures/item/valoblock" / f"{name}_3d.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    tex.write_bytes(bs.png(atlas(w["metal"], sum(ord(c) * (i + 1) for i, c in enumerate(name)))))


RANGE_MARKS = (22, 40, 58)   # rows below the centre, one pixel high


def scope_overlay(width=512, height=288):
    """Full-screen scope: black outside a circle, thin crosshair, a ring. 16:9 because the overlay is stretched."""
    cx, cy, rad = width / 2, height / 2, height * 0.46
    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            dx, dy = (x + 0.5 - cx), (y + 0.5 - cy)
            r = (dx * dx + dy * dy) ** 0.5   # 16:9 like the usual window, so the stretched circle stays round
            px = (0, 0, 0, 255)
            if r < rad:
                px = (0, 0, 0, 0)
                if (abs(dx) < 0.5 or abs(dy) < 0.5) and r > 6:
                    px = (8, 10, 14, 225)                              # 1 px crosshair lines (hollow at the centre)
                elif abs(dx) < 3 and (y - height // 2) in RANGE_MARKS:
                    px = (8, 10, 14, 200)                              # 1 px range marks below the centre
                elif abs(r - (rad - 3)) < 0.8:
                    px = (8, 10, 14, 255)                              # thin rim of the tube
                elif r < 1.6:
                    px = (74, 240, 255, 255)                           # cyan dot
            row.append(px)
        rows.append(row)
    return rows


def main():
    n = 0
    for w in vi.weapons():
        if w["texture"] == "ward":   # shields are the flat sprite
            continue
        write_weapon(w)
        n += 1
    out = PACK / "textures/misc/scope.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(vi.png(scope_overlay()))
    print(f"wrote {n} 3D weapons and the scope overlay under {PACK}")


if __name__ == "__main__":
    main()
