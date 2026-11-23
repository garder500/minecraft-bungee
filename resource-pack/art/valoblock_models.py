#!/usr/bin/env python3
"""ValoBlock 3D weapon models v3, stdlib only, deterministic (boxes on a 32x32 atlas, no side-view extrusion any more).

    python3 resource-pack/art/valoblock_models.py        (also run at the end of valoblock_items.py)

Every weapon class (pistol, revolver, smg, shotgun, rifle, sniper, lmg, knife) is an original hand-placed list of
boxes in model pixels, built along +x (muzzle at the high x, grip near the middle): receiver, slide or shroud, toothed
rails, notched sights, trigger guard, trigger, magazine, stepped stock, muzzle as separate parts (18 to 40 boxes, 45
with the flash). The metal of the weapon (Bronzite, Sunstone, Frostium, Voidglass, bone, marsh iron, storm glass,
starcore) recolours it, and the two weapons of one class differ by a few parts of their own (`only=`): Tickhand has a
compensator and a long magazine, Mistral a wire stock, Mire Maw a fanged choke, Watcher's Eye a bipod and a heavy
barrel... v3 (review of 2026-11): the classes have their own length (a pistol is 11 pixels long, a rifle 21, the
Watcher's Eye 26; all were 16), the grip and the sight line stayed where the display transforms expect them, the gun
only grows forward of the grip (never below x 0, which would bring the stock into the camera). Stocks, handguards and
grip panels are a furniture material that parts from the metal, and the energy cells also show on top and at the back,
which is what the shooter sees in first person.

Why the old guns rendered black: the outline colour "o" (06070a) and Voidglass "a" (05030a) were near-black tiles,
and vanilla shades the down and side faces (0.5-0.8) on top. Now every atlas tile is lifted to a minimum luminance
(FLOOR, 31% grey), the side tiles are baked a little darker than the top tiles, and the elements are `shade: false`
so the engine does not darken them again: no face is below 25% grey (checked by scripts/test_valoblock_models.py).

Atlas: 16 tiles of 8x8 pixels (uv 4x4), two per material: lit (top faces) and side (all others); the energy cell
and the muzzle flash glow and have one tile each (see TILES).

Per weapon (shields stay flat sprites, written by valoblock_items.py), under the ValoBlock pack:
  models/item/valoblock/<id>_3d.json       the gun held (hip pose: lower right third, about 1.4x the v1 size)
  models/item/valoblock/<id>_fire.json     muzzle flash and a slight kick (shown 2 ticks after a shot)
  models/item/valoblock/<id>_reload.json   the magazine out (the pump back, the revolver's cylinder swung out), gun tilted
  models/item/valoblock/<id>_aim.json      same gun, centred under the crosshair (sneaking)
  models/item/valoblock/<id>_inspect.json  the gun turned to show its side (1 s after a weapon swap)
  textures/item/valoblock/<id>_3d.png      32x32 atlas
  items/valoblock/<id>.json                select on display_context: gui -> flat sprite (the hotbar, the Armory),
                                           anything else -> select on custom_model_data string 0
                                           ("fire" | "reload" | "aim" | "inspect"), fallback the held model
and the sniper scope overlay textures/misc/scope.png (equippable camera_overlay, see VbWeaponService), plus the same
reticle as a square, assets/minecraft/textures/misc/spyglass_scope.png: the Watcher's Eye is a spyglass, so the client
draws that vanilla overlay while it is held up (this pack only, Skyblock spyglasses keep the vanilla one).
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import boss_skins as bs  # noqa: E402
import valoblock_items as vi  # noqa: E402

PACK = vi.PACK

# Materials: o dark steel, a shadow metal, b body metal, c bright metal, d highlight, g wood, k furniture (the dull
# polymer of stocks, handguards and grip panels, a colour that parts from the metal), e energy cell, f muzzle flash.
# The seven first have two atlas tiles (lit for top faces, side for the others); e and f glow, one tile each.
MATS = ["o", "a", "b", "c", "d", "g", "k", "e", "f"]
TILES = {m: (2 * i, 2 * i + 1) for i, m in enumerate(MATS[:7])}
TILES.update({"e": (14, 14), "f": (15, 15)})
# Furniture colour per metal: cool slate against the warm metals, a pale or warm grey against the cold ones.
FURNITURE = {"bronze": (58, 70, 96), "sun": (50, 74, 84), "marsh": (84, 70, 54), "frost": (150, 160, 176),
             "storm": (64, 66, 88), "void": (104, 92, 100), "bone": (70, 66, 84), "star": (70, 60, 100)}
FLASH = (255, 222, 120)
FLOOR = 80          # minimum luminance (0-255) of any lit tile pixel: 31% grey
SIDE_FLOOR = 72     # ... of any side pixel: 28% grey (the spec says 25%, 64)
SIDE_K = 0.82       # side tiles are the lit ones a little darker
RAMP = {"o": 80, "a": 94, "b": 114, "c": 142, "d": 175, "g": 96, "k": 90}   # per material minimum luminance


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
        elif ch == "k":
            out[ch] = lift(FURNITURE[metal], RAMP["k"])
        elif ch == "o":   # the dark steel of small parts: the metal's own shadow colour, lifted
            out[ch] = lift(tuple(int(v * 0.8) for v in vi.rgb(pal["a"])), RAMP["o"])
        else:
            out[ch] = lift(vi.rgb(pal[ch]), RAMP[ch])
    return out


def atlas(metal, seed):
    """32x32 RGBA rows: 4x4 tiles of 8x8, each material at its TILES indices (lit, side)."""
    import random
    rnd = random.Random(seed)
    px = [[(0, 0, 0, 255)] * 32 for _ in range(32)]
    mats = atlas_materials(metal)
    for ch in MATS:
        base = mats[ch]
        for side in range(len(set(TILES[ch]))):
            t = TILES[ch][side]
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
                        if ch == "k" and y % 2 == 1:
                            k *= 0.93            # ribbed furniture
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
        i = TILES[mat][1 if side else 0]
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


# Each part: (x0, x1, y0, y1, material, depth z, options). Options:
#   mag=True   the part leaves the gun in the reload variant (MAG_MOVE of its class)
#   rot=(angle, (x, y))   turned about z around that point (a raked grip, a curved magazine)
#   zc=8.6     centre of the box across the gun (default 8, the middle): notched sights, bipod legs, a bolt on one side
#   only="id"  the part exists on that weapon only (what tells two guns of one class apart)
# Rules that keep the game from flickering: two boxes that overlap never share a face plane (give the detail another
# depth, or let it stand 0.05 proud); touching boxes may share one. No test checks it: compare the face planes of every
# pair of overlapping boxes after an edit.
def P(x0, x1, y0, y1, mat, z=2.0, **o):
    return (x0, x1, y0, y1, mat, z, o)


def pair(x0, x1, y0, y1, mat, z, off, **o):
    """The same box on both sides of the middle (sight posts, bipod legs, fangs)."""
    return [P(x0, x1, y0, y1, mat, z, zc=8.0 - off, **o), P(x0, x1, y0, y1, mat, z, zc=8.0 + off, **o)]


GRIP_P = (-12.5, (5.0, 7.0))       # pistol grip rake
GRIP_R = (-22.5, (2.5, 6.6))       # revolver grip rake
GRIP_S = (-12.5, (5.3, 6.6))       # smg
GRIP_G = (-12.5, (4.9, 6.8))       # shotgun
GRIP_A = (-12.5, (7.0, 6.8))       # rifle
MAG_A = (12.5, (11.1, 6.8))        # rifle magazine, leaning forward
GRIP_N = (-12.5, (6.3, 7.0))       # sniper
GRIP_L = (-12.5, (5.2, 6.8))       # lmg

PARTS = {
    # Sidearm: a stepped slide over a short frame, a cell in the port and one on top, a notched rear sight.
    "pistol": [
        P(3.2, 12.4, 8.4, 10.4, "c", 2.2),                # slide
        P(3.6, 12.0, 10.4, 10.9, "c", 1.6),               # slide top, one step narrower (chamfer)
        P(4.6, 7.4, 10.9, 11.1, "d", 0.7),                # sighting rib, rear half
        P(10.4, 11.4, 10.9, 11.1, "d", 0.7),              # ... front half
        P(7.8, 10.0, 10.5, 10.96, "e", 1.0),              # cell seen from above
        P(7.6, 10.2, 9.4, 10.1, "e", 2.36),               # cell in the ejection port
        P(3.8, 4.2, 8.6, 10.2, "o", 2.4), P(4.6, 5.0, 8.6, 10.2, "o", 2.4), P(5.4, 5.8, 8.6, 10.2, "o", 2.4),   # serrations
        P(12.4, 13.0, 8.6, 10.2, "c", 1.8),               # slide nose
        P(13.0, 13.5, 8.9, 9.9, "o", 1.2),                # muzzle
        P(3.2, 11.6, 7.0, 8.4, "b", 2.5),                 # frame
        P(9.0, 12.0, 6.5, 7.0, "a", 1.6),                 # accessory rail
        P(9.6, 10.0, 6.3, 6.5, "o", 1.8), P(10.8, 11.2, 6.3, 6.5, "o", 1.8),   # rail lugs
        P(8.4, 9.0, 5.5, 7.0, "a", 1.5),                  # trigger guard front
        P(6.2, 9.0, 4.9, 5.5, "a", 1.5),                  # guard bottom
        P(7.2, 7.7, 5.6, 7.0, "o", 0.8),                  # trigger
        P(4.0, 6.0, 2.2, 6.8, "a", 1.6, rot=GRIP_P, mag=True),     # magazine (inside the grip until it drops)
        P(3.0, 6.9, 1.2, 1.9, "a", 2.7, rot=GRIP_P, mag=True),     # magazine base plate
        P(3.4, 6.7, 1.9, 7.0, "g", 2.4, rot=GRIP_P),      # raked grip
        P(3.9, 6.2, 2.7, 6.2, "k", 2.7, rot=GRIP_P),      # grip panels
        P(2.6, 3.2, 7.4, 8.4, "b", 2.0),                  # beavertail
        P(2.7, 3.2, 9.0, 10.6, "a", 1.0),                 # hammer
        P(3.05, 3.2, 8.5, 8.9, "e", 1.4),                 # charge light on the back of the slide
        *pair(3.6, 4.4, 10.9, 11.6, "o", 0.5, 0.55),      # rear sight, notched
        P(11.4, 12.0, 10.9, 11.5, "o", 0.5),              # front sight
        # Tickhand: the 18 round one, a longer magazine and a ported compensator
        P(13.5, 14.6, 8.3, 10.6, "a", 2.0, only="tickhand"),
        P(13.8, 14.2, 8.5, 10.66, "o", 2.2, only="tickhand"),
        P(2.6, 6.5, 0.4, 1.2, "o", 2.3, rot=GRIP_P, mag=True, only="tickhand"),
        P(9.4, 11.6, 5.9, 6.3, "e", 1.2, only="tickhand"),
        # Pip: the starter, a plain crown
        P(13.5, 13.8, 8.7, 10.1, "a", 1.5, only="pip"),
    ],
    # Hand cannon: a fat stepped cylinder, a vented barrel shroud with a full under lug, a long raked grip.
    "revolver": [
        P(8.6, 14.6, 8.6, 10.4, "c", 1.8),                # barrel shroud
        P(8.6, 14.2, 10.4, 10.8, "d", 0.8),               # top rib
        P(8.6, 13.6, 7.3, 8.6, "b", 1.4),                 # under lug
        P(13.6, 14.2, 7.9, 8.6, "b", 1.2),                # lug step
        P(14.6, 15.0, 8.9, 10.1, "a", 1.3),               # muzzle crown
        P(9.6, 10.1, 9.2, 9.8, "o", 2.0), P(11.0, 11.5, 9.2, 9.8, "o", 2.0), P(12.4, 12.9, 9.2, 9.8, "o", 2.0),   # shroud vents
        P(4.2, 8.0, 6.8, 10.6, "b", 2.8, mag=True),       # cylinder core
        P(4.3, 7.9, 7.5, 9.9, "b", 3.6, mag=True),        # cylinder belly (a stepped octagon seen from behind)
        P(4.8, 5.4, 7.9, 9.5, "o", 3.8, mag=True), P(6.8, 7.4, 7.9, 9.5, "o", 3.8, mag=True),   # flutes
        P(5.7, 6.5, 8.2, 9.2, "e", 3.9, mag=True),        # the loaded chamber
        P(3.0, 4.2, 6.4, 10.6, "c", 3.0),                 # recoil shield
        P(3.4, 8.6, 10.6, 11.1, "c", 1.6),                # top strap
        P(4.2, 8.6, 6.2, 6.8, "c", 2.0),                  # frame under the cylinder
        P(8.0, 8.6, 6.8, 10.6, "c", 2.2),                 # crane
        *pair(2.85, 3.0, 8.3, 9.3, "e", 0.6, 1.0),        # two lights on the back of the shield
        P(2.0, 3.0, 9.8, 11.5, "a", 1.0),                 # hammer
        P(1.2, 2.0, 10.9, 11.5, "a", 1.2),                # hammer spur
        P(5.6, 8.6, 4.6, 5.2, "a", 1.6), P(8.0, 8.6, 5.2, 6.2, "a", 1.6),   # trigger guard
        P(6.0, 6.5, 5.2, 6.2, "o", 0.8),                  # trigger
        P(0.8, 4.2, 1.6, 6.6, "g", 2.5, rot=GRIP_R),      # grip
        P(0.6, 4.4, 1.0, 1.6, "a", 2.8, rot=GRIP_R),      # butt cap
        P(1.9, 3.1, 3.8, 5.0, "d", 2.7, rot=GRIP_R),      # medallion
        P(13.6, 14.4, 10.8, 11.6, "o", 0.5),              # front sight
        *pair(3.4, 4.0, 11.1, 11.6, "o", 0.5, 0.55),      # rear notch
    ],
    # Machine pistol: a tall box receiver, a fat vented shroud, the magazine ahead of the trigger, an angled foregrip.
    "smg": [
        P(3.0, 11.5, 6.6, 9.8, "b", 2.6),                 # receiver
        P(3.2, 11.3, 9.8, 10.5, "c", 2.1),                # top cover
        P(2.9, 11.6, 6.4, 7.5, "k", 2.8),                 # lower frame, furniture, a little wider
        P(4.6, 10.8, 10.5, 10.8, "d", 1.0),               # rail
        P(5.6, 6.0, 10.55, 10.86, "o", 1.2), P(7.2, 7.6, 10.55, 10.86, "o", 1.2), P(8.8, 9.2, 10.55, 10.86, "o", 1.2),   # rail slots
        P(11.5, 14.4, 7.3, 9.5, "o", 2.2),                # barrel shroud
        P(12.1, 12.5, 7.1, 9.7, "a", 2.4), P(13.1, 13.5, 7.1, 9.7, "a", 2.4),   # cooling rings
        P(14.4, 15.0, 7.8, 9.0, "a", 1.3),                # muzzle
        P(9.4, 10.2, 9.0, 9.5, "d", 3.2),                 # charging handle, both sides
        P(5.2, 8.4, 8.1, 8.8, "e", 2.75),                 # cell
        P(2.85, 3.0, 8.75, 9.25, "e", 1.6),                 # charge light at the back
        P(8.0, 10.0, 2.6, 6.4, "a", 1.8, mag=True),       # magazine
        P(7.8, 10.2, 2.1, 2.6, "o", 2.1, mag=True),
        P(7.7, 10.3, 5.6, 6.6, "k", 2.3),                 # magazine well
        P(4.2, 6.4, 2.4, 6.6, "g", 2.2, rot=GRIP_S),      # grip
        P(6.3, 7.7, 5.0, 5.5, "a", 1.5),                  # trigger guard
        P(6.9, 7.3, 5.5, 6.6, "o", 0.8),                  # trigger
        P(11.7, 13.1, 4.6, 7.3, "k", 1.8, rot=(12.5, (12.4, 7.3))),   # foregrip, leaning forward
        *pair(3.6, 4.4, 10.5, 11.5, "o", 0.5, 0.6),       # rear sight, notched
        P(10.8, 11.3, 10.5, 11.4, "o", 0.6),              # front sight
        # Gnat: the cheap one, a stub stock and a short magazine
        P(1.6, 3.0, 7.4, 8.6, "a", 1.4, only="gnat"),
        P(1.0, 1.6, 6.8, 9.2, "o", 2.0, only="gnat"),
        # Mistral: a wire stock, a 40 round magazine with a window, a longer muzzle
        P(0.6, 3.0, 9.4, 9.8, "a", 1.0, only="mistral"),
        P(0.6, 3.0, 6.9, 7.4, "a", 1.0, only="mistral"),
        P(0.0, 0.6, 6.5, 9.9, "o", 2.0, only="mistral"),
        P(8.0, 10.0, 0.9, 2.1, "a", 1.7, mag=True, only="mistral"),
        P(7.8, 10.2, 0.4, 0.9, "o", 2.0, mag=True, only="mistral"),
        P(8.5, 9.5, 3.0, 5.2, "e", 1.9, mag=True, only="mistral"),
        P(15.0, 16.4, 7.6, 9.2, "o", 1.6, only="mistral"),
        P(15.5, 15.9, 7.4, 9.4, "d", 1.8, only="mistral"),
    ],
    # Pump gun: a barrel over a tube, a ribbed pump that slides, a stepped wooden stock.
    "shotgun": [
        P(3.4, 9.0, 6.8, 10.6, "b", 2.6),                 # receiver
        P(3.8, 8.6, 10.6, 11.0, "c", 1.8),                # receiver top
        P(5.0, 7.4, 9.0, 10.0, "e", 2.75),                # loading port cell
        P(5.2, 7.2, 10.7, 11.06, "e", 0.9),               # cell seen from above
        P(3.25, 3.4, 9.9, 10.4, "e", 1.6),                # light at the back, over the stock
        P(9.0, 18.0, 9.0, 10.5, "c", 1.7),                # barrel
        P(9.0, 17.2, 7.5, 9.0, "o", 1.5),                 # magazine tube
        P(17.2, 17.7, 7.7, 8.8, "a", 1.2),                # tube cap
        P(16.4, 17.0, 7.3, 10.7, "a", 2.0),               # barrel band
        P(9.4, 16.2, 10.5, 10.75, "d", 0.7),              # sighting rib
        P(11.4, 15.4, 6.9, 8.7, "k", 2.6, mag=True),      # pump
        P(12.0, 12.4, 7.1, 8.5, "o", 2.8, mag=True), P(13.1, 13.5, 7.1, 8.5, "o", 2.8, mag=True),
        P(14.2, 14.6, 7.1, 8.5, "o", 2.8, mag=True),      # pump ribs
        P(2.6, 3.4, 7.2, 9.6, "g", 2.0),                  # stock wrist
        P(0.6, 2.6, 5.6, 9.3, "g", 2.2),                  # butt
        P(0.6, 1.8, 4.8, 5.6, "g", 2.0),                  # toe (the stock drops in steps)
        P(0.0, 0.6, 4.6, 9.5, "k", 2.4),                  # recoil pad
        P(4.0, 5.8, 3.8, 6.8, "g", 2.0, rot=GRIP_G),      # pistol grip
        P(5.9, 8.4, 5.0, 5.6, "a", 1.5), P(7.8, 8.4, 5.6, 6.8, "a", 1.5),   # trigger guard
        P(6.6, 7.1, 5.6, 6.8, "o", 0.8),                  # trigger
        P(17.2, 17.7, 10.5, 11.1, "d", 0.6),              # bead
        *pair(4.2, 5.0, 11.0, 11.5, "o", 0.5, 0.55),      # rear groove
        # Scrapper: sawn short, spare shells strapped to the receiver, a bolted plate
        P(18.0, 18.3, 9.3, 10.3, "a", 1.3, only="scrapper"),
        P(4.2, 8.2, 7.3, 8.5, "k", 0.3, zc=9.45, only="scrapper"),
        P(4.6, 5.2, 7.1, 8.8, "d", 0.5, zc=9.65, only="scrapper"), P(5.9, 6.5, 7.1, 8.8, "d", 0.5, zc=9.65, only="scrapper"),
        P(7.2, 7.8, 7.1, 8.8, "d", 0.5, zc=9.65, only="scrapper"),
        P(4.2, 8.2, 7.3, 8.5, "a", 0.3, zc=6.55, only="scrapper"),
        # Mire Maw: a longer barrel under a banded heat shield, ending in a flared choke with four fangs
        P(18.0, 19.4, 9.1, 10.4, "c", 1.5, only="mire_maw"),
        P(10.2, 11.6, 9.6, 10.9, "o", 2.0, only="mire_maw"), P(12.2, 13.6, 9.6, 10.9, "o", 2.0, only="mire_maw"),
        P(14.2, 15.6, 9.6, 10.9, "o", 2.0, only="mire_maw"),
        P(19.4, 20.6, 8.6, 10.9, "a", 2.3, only="mire_maw"),
        *pair(20.6, 21.2, 10.3, 10.9, "d", 0.5, 0.7, only="mire_maw"),
        *pair(20.6, 21.2, 8.6, 9.2, "d", 0.5, 0.7, only="mire_maw"),
    ],
    # Rifle: a two-part receiver, a slotted handguard under a toothed rail, a gas block, a ported brake, a curved magazine.
    "rifle": [
        P(5.0, 11.6, 6.8, 8.8, "b", 2.6),                 # lower receiver
        P(4.6, 12.0, 8.8, 10.4, "c", 2.3),                # upper receiver
        P(7.0, 16.6, 10.4, 10.7, "d", 1.0),               # top rail
        P(8.0, 8.4, 10.45, 10.76, "o", 1.2), P(10.0, 10.4, 10.45, 10.76, "o", 1.2), P(12.0, 12.4, 10.45, 10.76, "o", 1.2),
        P(14.0, 14.4, 10.45, 10.76, "o", 1.2), P(16.0, 16.4, 10.45, 10.76, "o", 1.2),   # rail slots
        P(12.0, 17.0, 7.4, 10.0, "k", 2.4),               # handguard
        P(12.0, 17.0, 10.0, 10.4, "c", 1.6),              # handguard top
        P(12.8, 14.2, 8.9, 9.4, "o", 2.6), P(14.8, 16.2, 8.9, 9.4, "o", 2.6),   # cooling slots
        P(12.8, 14.2, 7.9, 8.3, "o", 2.6), P(14.8, 16.2, 7.9, 8.3, "o", 2.6),
        P(17.0, 20.0, 8.5, 9.5, "o", 1.2),                # barrel
        P(17.6, 18.4, 8.3, 10.4, "a", 1.5),               # gas block
        P(17.7, 18.3, 10.4, 11.6, "o", 0.5),              # front sight post
        P(20.0, 21.4, 8.1, 9.9, "a", 1.8),                # muzzle brake
        P(20.5, 20.9, 7.9, 10.1, "o", 2.0),               # brake port
        *pair(5.2, 6.0, 10.4, 11.6, "o", 0.5, 0.6),       # rear sight, two posts
        P(5.0, 6.8, 10.4, 10.5, "e", 0.6),                # cell glowing in the sight channel
        P(6.4, 9.6, 9.2, 9.8, "e", 2.4),                  # cell on the side
        P(10.6, 11.4, 9.3, 9.8, "d", 3.0),                # charging handle
        P(0.6, 4.6, 8.4, 9.9, "k", 2.0),                  # stock comb
        P(0.6, 2.4, 5.4, 8.4, "k", 2.0),                  # butt
        P(2.4, 3.6, 6.8, 8.4, "k", 1.6),                  # stock web
        P(0.0, 0.6, 5.2, 10.1, "o", 2.3),                 # butt pad
        P(1.6, 4.0, 9.9, 10.2, "c", 1.4),                 # cheek rest
        P(6.0, 8.0, 3.2, 6.8, "k", 2.2, rot=GRIP_A),      # grip
        P(8.0, 10.0, 5.6, 6.1, "a", 1.5),                 # trigger guard
        P(8.5, 8.9, 6.1, 6.8, "o", 0.8),                  # trigger
        P(10.0, 12.2, 2.8, 6.8, "a", 1.9, rot=MAG_A, mag=True),     # magazine, leaning forward
        P(9.8, 12.4, 2.3, 2.8, "o", 2.2, rot=MAG_A, mag=True),      # its base
        # Bronzite Carbine: a wooden vertical grip under the handguard
        P(13.6, 15.0, 5.0, 7.4, "g", 1.6, only="bronzite_carbine"),
        P(13.4, 15.2, 4.6, 5.0, "a", 1.9, only="bronzite_carbine"),
        # Flux Rifle: coil rings on the barrel, a cell under the handguard and one in the magazine
        P(18.8, 19.1, 8.1, 9.9, "e", 1.7, only="flux_rifle"), P(19.4, 19.7, 8.1, 9.9, "e", 1.7, only="flux_rifle"),
        P(12.6, 16.4, 6.9, 7.4, "a", 1.6, only="flux_rifle"),
        P(13.0, 16.0, 7.0, 7.3, "e", 1.8, only="flux_rifle"),
        P(10.6, 11.6, 3.6, 5.6, "e", 2.0, rot=MAG_A, mag=True, only="flux_rifle"),
    ],
    # Bolt rifle: a long thin barrel, a scope with a lit lens at both ends, a bolt on one side, a stepped stock.
    "sniper": [
        P(5.0, 11.5, 7.0, 9.6, "b", 2.3),                 # receiver
        P(11.5, 17.0, 6.9, 8.4, "k", 2.0),                # fore-end
        P(11.5, 14.0, 8.4, 9.5, "c", 1.6),                # barrel shank
        P(6.0, 9.0, 8.0, 8.6, "e", 2.4),                  # cell
        P(0.6, 5.0, 8.0, 9.4, "k", 2.0),                  # stock comb
        P(0.6, 2.2, 5.4, 8.0, "k", 2.0),                  # butt
        P(2.2, 3.4, 6.6, 8.0, "k", 1.6),                  # stock web
        P(0.0, 0.6, 5.2, 9.6, "o", 2.3),                  # butt pad
        P(1.4, 4.2, 9.4, 10.0, "c", 1.5),                 # cheek rest
        P(5.4, 7.2, 3.6, 7.0, "g", 2.0, rot=GRIP_N),      # grip
        P(8.4, 10.4, 4.8, 7.0, "a", 1.7, mag=True),       # magazine
        P(8.2, 10.6, 4.4, 4.8, "o", 1.9, mag=True),
        P(7.0, 8.4, 5.6, 6.1, "a", 1.5),                  # trigger guard
        P(7.6, 8.0, 6.1, 7.0, "o", 0.8),                  # trigger
        P(8.6, 9.4, 9.0, 9.5, "o", 1.8, zc=9.5),          # bolt handle, one side
        P(8.4, 9.6, 8.8, 9.7, "d", 0.8, zc=10.7),         # bolt knob
        P(6.0, 6.7, 9.6, 10.9, "o", 1.0), P(9.8, 10.5, 9.6, 10.9, "o", 1.0),   # scope rings
        P(5.2, 11.6, 10.9, 12.1, "a", 1.3),               # scope tube
        P(5.6, 11.2, 12.1, 12.3, "d", 0.6),               # tube highlight
        P(4.2, 5.2, 10.7, 12.3, "o", 1.8),                # eyepiece
        P(4.05, 4.2, 11.0, 12.0, "e", 1.2),               # rear lens (what the shooter sees)
        P(11.6, 13.4, 10.5, 12.5, "o", 2.2),              # objective bell
        P(13.4, 13.55, 10.8, 12.2, "e", 1.7),             # front lens
        P(7.8, 8.8, 12.3, 13.0, "o", 1.0),                # elevation turret
        P(7.8, 8.8, 11.0, 12.0, "o", 1.2, zc=9.0),        # windage turret
        # Longwind: the light one, a shorter thin barrel, a small brake, a sling bar
        P(14.0, 21.4, 8.5, 9.3, "o", 1.1, only="longwind"),
        P(21.4, 22.6, 8.1, 9.7, "a", 1.6, only="longwind"),
        P(21.8, 22.2, 7.9, 9.9, "o", 1.8, only="longwind"),
        P(15.6, 16.4, 6.4, 6.9, "a", 1.2, only="longwind"),
        # Watcher's Eye: a heavy fluted barrel, a big two-port brake, a bipod, a wide iris around the front lens
        P(14.0, 24.4, 8.4, 9.4, "o", 1.2, only="watchers_eye"),
        P(14.2, 19.0, 8.2, 9.6, "c", 1.5, only="watchers_eye"),
        P(15.0, 15.4, 8.0, 9.8, "a", 1.7, only="watchers_eye"), P(16.4, 16.8, 8.0, 9.8, "a", 1.7, only="watchers_eye"),
        P(17.8, 18.2, 8.0, 9.8, "a", 1.7, only="watchers_eye"),
        P(24.4, 26.0, 7.9, 9.9, "a", 1.9, only="watchers_eye"),
        P(24.8, 25.1, 7.7, 10.1, "o", 2.1, only="watchers_eye"), P(25.4, 25.7, 7.7, 10.1, "o", 2.1, only="watchers_eye"),
        P(15.4, 17.0, 6.5, 6.9, "a", 2.2, only="watchers_eye"),
        *pair(15.9, 16.5, 3.6, 6.5, "o", 0.6, 1.0, only="watchers_eye"),
        *pair(15.7, 16.7, 3.2, 3.6, "a", 0.9, 1.0, only="watchers_eye"),
        P(12.6, 13.0, 10.3, 12.7, "d", 2.5, only="watchers_eye"),
    ],
    # Machine gun: a deep body, a finned sleeve, three barrels in a cluster, a stepped drum, a carry handle, a bipod.
    "lmg": [
        P(3.6, 12.0, 6.8, 10.4, "b", 2.8),                # body
        P(4.0, 11.6, 10.4, 11.0, "c", 2.2),               # feed cover
        P(7.2, 9.6, 10.5, 11.06, "e", 1.0),               # cell seen from above
        P(3.85, 4.0, 10.5, 10.9, "e", 1.2),               # light at the back
        P(4.6, 6.6, 8.2, 9.0, "e", 2.95),                 # cell on the side
        P(6.4, 7.0, 11.0, 12.2, "o", 0.8), P(9.8, 10.4, 11.0, 12.2, "o", 0.8),   # carry handle posts
        P(6.0, 10.8, 12.2, 12.7, "d", 1.0),               # carry handle
        *pair(4.2, 5.0, 11.0, 11.8, "o", 0.5, 0.7),       # rear sight
        P(12.0, 18.6, 7.4, 10.0, "o", 2.6),               # barrel sleeve
        P(12.8, 13.4, 7.2, 10.2, "a", 2.8), P(14.2, 14.8, 7.2, 10.2, "a", 2.8), P(15.6, 16.2, 7.2, 10.2, "a", 2.8),
        P(17.0, 17.6, 7.2, 10.2, "a", 2.8),               # cooling fins
        P(17.9, 18.4, 10.0, 11.2, "o", 0.5),              # front sight
        P(18.6, 21.0, 8.9, 9.7, "a", 0.9),                # top barrel
        *pair(18.6, 21.0, 7.8, 8.6, "a", 0.8, 0.65),      # two lower barrels
        P(20.0, 20.6, 7.5, 10.0, "d", 2.4),               # barrel clamp
        P(10.2, 11.2, 9.0, 9.6, "d", 3.3),                # charging handle
        P(0.6, 3.6, 7.0, 9.8, "k", 2.2),                  # stock
        P(0.6, 1.8, 5.6, 7.0, "k", 2.0),                  # stock toe
        P(0.0, 0.6, 5.4, 10.0, "o", 2.5),                 # butt pad
        P(1.0, 3.6, 9.8, 10.1, "a", 1.2),                 # shoulder strap bar
        P(7.4, 11.6, 3.4, 5.8, "a", 3.4, mag=True),       # drum, wide part
        P(8.2, 10.8, 2.4, 6.8, "a", 3.2, mag=True),       # drum, tall part (an octagon from the side)
        P(8.3, 10.7, 3.5, 5.7, "b", 3.8, mag=True),       # drum cap
        P(8.9, 10.1, 4.0, 5.2, "e", 4.0, mag=True),       # drum cell
        P(4.2, 6.2, 3.4, 6.8, "g", 2.2, rot=GRIP_L),      # grip
        P(6.1, 7.3, 5.5, 6.0, "a", 1.5),                  # trigger guard
        P(6.5, 6.9, 6.0, 6.8, "o", 0.8),                  # trigger
        P(15.4, 17.4, 7.0, 7.4, "a", 2.0),                # bipod mount
        *pair(15.9, 16.5, 3.6, 7.0, "o", 0.6, 1.1),       # bipod legs, splayed
        *pair(15.7, 16.7, 3.2, 3.6, "a", 0.9, 1.1),       # feet
    ],
    # Shard: a blade that narrows in three steps to a point, a lit vein, a swept guard, a light in the pommel.
    "knife": [
        P(6.6, 11.0, 7.6, 10.2, "c", 1.0),                # blade, widest step
        P(11.0, 13.2, 8.2, 10.2, "c", 0.8),
        P(13.2, 14.6, 8.9, 10.2, "c", 0.6),
        P(14.6, 15.4, 9.5, 10.2, "d", 0.4),               # point
        P(6.6, 14.6, 10.2, 10.45, "d", 0.5),              # spine
        P(7.0, 11.0, 7.35, 7.6, "d", 0.4), P(11.0, 13.2, 7.95, 8.2, "d", 0.4), P(13.2, 14.6, 8.65, 8.9, "d", 0.4),   # edge
        P(7.4, 12.6, 9.0, 9.4, "e", 1.1),                 # vein
        P(5.6, 6.6, 6.6, 11.0, "a", 2.4),                 # guard
        P(6.6, 7.4, 10.5, 11.0, "a", 1.6),                # guard, swept forward
        P(5.8, 6.4, 8.2, 9.2, "e", 2.6),                  # guard gem
        P(1.4, 5.6, 7.8, 9.6, "g", 1.8),                  # grip
        P(2.0, 2.5, 7.6, 9.8, "o", 2.0), P(3.2, 3.7, 7.6, 9.8, "o", 2.0), P(4.4, 4.9, 7.6, 9.8, "o", 2.0),   # wraps
        P(0.4, 1.4, 7.4, 10.0, "b", 2.2),                 # pommel
        P(0.25, 0.4, 8.2, 9.2, "e", 1.0),                 # pommel light
    ],
}
# Muzzle (x, y centre) for the flash: per class, or per weapon when its barrel differs.
MUZZLE = {"pistol": (13.5, 9.4), "tickhand": (14.6, 9.4), "revolver": (15.0, 9.5), "smg": (15.0, 8.4),
          "mistral": (16.4, 8.4), "shotgun": (18.3, 9.8), "mire_maw": (21.0, 9.8), "rifle": (21.4, 9.0),
          "sniper": (22.6, 8.9), "watchers_eye": (26.0, 8.9), "lmg": (21.0, 8.8)}
# How the "mag" parts leave in the reload variant: (dx, dy, dz). The pistol magazine slides out along the raked grip,
# the revolver's cylinder swings out to the side, the shotgun's pump comes back.
MAG_MOVE = {"pistol": (-0.9, -3.9, 0), "revolver": (0, -0.8, 2.4), "smg": (0, -4, 0), "shotgun": (-2.2, 0, 0),
            "rifle": (0.9, -3.9, 0), "sniper": (0, -3.2, 0), "lmg": (0, -4.5, 0)}

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


# Third person is another frame than first person: on the arm, the item's +y is forward and its +z runs up the arm
# (ItemInHandLayer turns the item -90 about x and 180 about y). The gun is built along +x with its top at +y, so it
# is turned z 90 (muzzle to +y, forward) then y 90 (top to +z, up). The old y -90 alone sent the muzzle up the arm:
# the gun stood on its stock with the grip the wrong way round. A z above 90 raises the muzzle. The translation
# lowers the gun along the arm until the grip is in the fist (checked on mannequins seen from the side).
TP_T, TP_R = (0, 0, -2.5), (0, 90, 90)


def display(fp_t, fp_r, tp_t=TP_T, tp_r=TP_R):
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
AIM = display((-8.0, 3.0, -6.0), (0, -90, 0), tp_t=(0, 1.0, -1.5))
# Reload: the gun tilts muzzle-up and comes toward the middle so the magazine drop is visible.
RELOAD = display((-1.0, 1.5, -5.0), (0, -90, 28), tp_r=(0, 90, 110))
# Inspect: the gun turns to show its side for a second after a swap.
INSPECT = display((-3.0, 3.0, -5.0), (12, -35, -8), tp_r=(0, 90, 100))


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
    mdx, mdy, mdz = MAG_MOVE.get(t, (0, 0, 0))
    for x0, x1, y0, y1, mat, z, o in PARTS[t]:
        if o.get("only", w["id"]) != w["id"]:
            continue
        dx = dy = dz = 0.0
        if state == "reload" and o.get("mag"):
            dx, dy, dz = mdx, mdy, mdz
        if state == "fire":
            dx = -0.8
        g.box(x0, x1, y0, y1, mat, z, rot=o.get("rot"), dx=dx, dy=dy, zc=o.get("zc", 8.0) + dz)
    muzzle = MUZZLE.get(w["id"], MUZZLE.get(t))
    if state == "fire" and muzzle:
        flash(g, *muzzle)
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


def scope_overlay(width=512, height=288, radius=0.46, cross=False):
    """Full-screen scope: black outside a circle, thin crosshair, a ring. 16:9 because the overlay is stretched."""
    cx, cy, rad = width / 2, height / 2, height * radius
    marks = tuple(round(m * height / 288) for m in RANGE_MARKS)
    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            dx, dy = (x + 0.5 - cx), (y + 0.5 - cy)
            r = (dx * dx + dy * dy) ** 0.5   # 16:9 like the usual window, so the stretched circle stays round
            px = (0, 0, 0, 255)
            if r < rad:
                px = (0, 0, 0, 0)
                if ((x == width // 2 or y == height // 2) if cross else (abs(dx) < 0.5 or abs(dy) < 0.5)) and r > 6:
                    px = (8, 10, 14, 225)                              # 1 px crosshair lines (hollow at the centre)
                elif abs(dx) < 3 and (y - height // 2) in marks:
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
    # Vanilla draws the spyglass overlay as a square scaled up to 1.125 x the window height: a 512 square, circle a bit smaller.
    spy = PACK.parent / "minecraft" / "textures" / "misc" / "spyglass_scope.png"
    spy.parent.mkdir(parents=True, exist_ok=True)
    spy.write_bytes(vi.png(scope_overlay(512, 512, 0.44, cross=True)))
    print(f"wrote {n} 3D weapons and the scope overlay under {PACK}")


if __name__ == "__main__":
    main()
