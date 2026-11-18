#!/usr/bin/env python3
"""The worn look of the ladder armour sets and the three Starcore sidegrades, as 128x64 pixel art.

    python3 resource-pack/art/armor_layers.py [--preview out.png]        (stdlib only, deterministic)

A worn armour is two textures in the vanilla player-skin layout at twice its resolution (the armour model's UVs are
proportional, so a body face is 16x24 pixels instead of 8x12), humanoid/<set>.png (chestplate on body and arms, boots
on the feet) and humanoid_leggings/<set>.png (belt and legs). The helmet is a 3D model (helmet_models.py), so the head
of the layer stays empty.

Every face is a small character bitmap (DRAWINGS): a letter is one plate, and paint() bevels each plate by itself (light
from the top left), so the drawing is a list of shapes, not of colours. The geometry of the armour model is a fixed
box: the silhouette comes from transparency (pauldrons that end in points, a bare ring between pauldron and bracer,
cut hems and cuffs). Each ladder set has its own chest, pauldron and tasset drawing, taken from its inventory icon
(Bronze: riveted round plates, Ember: sun disc and flame tongues, Rime: slanted ice shards, Void: black diamond socket,
Starcore: gold-rimmed ivory plates and feathered shoulders); the other faces are shared (DEFAULT).

The colours are read from the set's imported chestplate sprite (palette()), so the armour a player wears is the metal
of the icon in the inventory. Faces are not darkened by side: the game already shades them. The three Starcore
sidegrades keep the Starcore drawing with their Realm's trim and gem colour.

armor_tiers.py skips these files (imported.txt: equipment/<set>) and keeps the equipment/<set>.json layers this script
writes.
"""
import json
import pathlib
import sys

from helmet_models import png, two_metals

ART = pathlib.Path(__file__).resolve().parent
ASSETS = ART.parent / "pack/assets/vampyuyu"
S = 2                       # pixels per pixel of the vanilla 64x32 layout
W, H = 64 * S, 32 * S


def rgb(hexes):
    return tuple(tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes.split())


def lighter(c, t=0.4):
    return tuple(int(v + (255 - v) * t) for v in c)


def darker(c, t=0.45):
    return tuple(int(v * (1 - t)) for v in c)


def mix(a, b, t):
    return tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))


def trim4(hexes):
    dk, md, lt = rgb(hexes)
    return (dk, md, lt, lighter(lt))


# plate and trim tones are (shadow, body, light, highlight); glow is (rim, glow, core)
import colorsys  # noqa: E402

import sprite_models  # noqa: E402



def sprite_pixels(tier, piece):
    """The painted pixels of an item sprite without its dark ink outline."""
    _, _, px = sprite_models.read_png(ASSETS / "textures/item/armor" / f"{tier}_{piece}.png")
    pixels = sorted((p[:3] for row in px for p in row if p[3] >= 128), key=lum)
    return pixels[len(pixels) // 6:]    # the darkest sixth is the outline and the deepest shadows


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def average(pixels):
    return tuple(sum(p[k] for p in pixels) // len(pixels) for k in range(3))


TWO_METALS = ("starcore",)     # elsewhere the second colour of the sprite is only its shadow: no dark hoops


def palette(tier):
    """The colours of a set, read from its chestplate sprite so the worn armour is the metal of the inventory icon:
    plate = four tones (shadow, body, light, highlight), trim = the same metal a step darker (a lip between plates, not
    a black hoop) or the sprite's second metal when it has one, glow = the accent of the sprite (the pixels whose hue is furthest from the metal's), or its highlight
    when the sprite has no accent."""
    pixels, second = two_metals(sprite_pixels(tier, "chestplate"))
    n = len(pixels)
    plate = tuple(average(pixels[int(n * a):int(n * b)]) for a, b in ((0.12, 0.38), (0.5, 0.75), (0.78, 0.93), (0.95, 1.0)))
    trim = tuple(mix(plate[i], plate[max(0, i - 1)], 0.55) for i in range(4))   # the same metal, half a step darker
    if second and tier in TWO_METALS:  # white plates, gold trim: the trim is the second metal
        m = len(second)
        trim = tuple(average(second[int(m * a):int(m * b)]) for a, b in ((0.0, 0.3), (0.3, 0.6), (0.6, 0.9), (0.9, 1.0)))
        # the pale metal is judged by its lit side: the mid tones of an ivory sprite are putty once the game shades them
        light = average(pixels[int(n * 0.92):])
        plate = (average(pixels[int(n * 0.3):int(n * 0.55)]), mix(average(pixels[int(n * 0.7):int(n * 0.92)]), light, 0.4), light,
                 lighter(light, 0.5))

    def hue(c):
        return colorsys.rgb_to_hsv(*(v / 255 for v in c))

    base = hue(plate[1])[0]
    accents = [c for c in pixels if hue(c)[1] > 0.35 and hue(c)[2] > 0.45
               and min(abs(hue(c)[0] - base), 1 - abs(hue(c)[0] - base)) > 0.12]
    if len(accents) >= 6:
        accents.sort(key=lum)
        third = len(accents) // 3
        glow = (average(accents[:third]), average(accents[third:2 * third]), lighter(average(accents[2 * third:]), 0.35))
    else:
        glow = (plate[1], plate[3], lighter(plate[3], 0.5))
    return dict(plate=plate, trim=trim, glow=glow, art=tier)


SETS = {tier: palette(tier) for tier in ("bronze", "ember", "rime", "void", "starcore")}
for realm, trim, glow in (("cinder", "#5a1a10 #c8461e #ff8a2a", "#c8461e #ff8a2a #ffe08a"),
                          ("grove", "#2c3a1e #4f6b2a #8fb84a", "#8fb84a #d6f08a #f1e7c9"),
                          ("frost", "#1f3557 #3b6fa8 #7fc8e8", "#7a68d8 #b6a8ff #f0ecff")):
    SETS[f"{realm}_starcore"] = dict(SETS["starcore"], trim=trim4(trim), glow=rgb(glow), realm=True)

# face rectangles of the vanilla layout, in texture pixels: (u, v, width, height, depth)
BOXES = {name: tuple(n * S for n in box)
         for name, box in {"body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4), "leg": (0, 16, 4, 12, 4)}.items()}


def faces(box):
    u, v, w, h, d = BOXES[box]
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


# ---------------------------------------------------------------------------------------------------------- the art
# One bitmap per face, 8 columns. Characters:
#   A-H   a plate (each letter is bevelled as its own shape)      T-Z   a trim piece (the set's second metal)
#   '     a rivet or glint on a plate        ,   a dent           x     a glint on trim
#   #     cloth under the plates             :   ink (sockets, seams, soles)
#   o g * the gem: rim, glow, core           .   nothing (the player shows through: this is what cuts the silhouette)
# CHEST and BACK are the left half of the torso (column 7 is the middle; the right half is its mirror). On a limb,
# OUT is the outer face (column 7 touches the front), FB the front (column 0 is the outer side; the back is its
# mirror), IN the inner face. SIDE is the flank of the torso (column 7 touches the front). The boots are the last
# eleven rows of the leg.
DEFAULT = {
    "CHEST": """
CCCCC###
CCCCCC##
TTTTTTT#
AAAAAAAT
A'AAAAAT
AAAAAAAT
AAAAAAAT
AAAAAATo
AAAAAATg
AAAAAATo
A'AAAAAT
#AAAAAAT
##AAAAAT
#DDDDDDD
#DDDDDDD
##DDDDDD
#EEEEEEE
#EEEEEEE
##EEEEEE
TTTTTTTT
TTTTTTx:
TTTTTTTT
FFFFFF..
.FFFF...""",
    "BACK": """
CCCCCCCC
CCCCCCCC
TTTTTTTT
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
#AAAAAT:
##AAAATx
#DDDDDDD
#DDDDDDD
##DDDDDD
#EEEEEEE
#EEEEEEE
##EEEEEE
TTTTTTTT
TTTTTTTT
TTTTTTTT
FFFFFFFF
.FFFFFF.""",
    "SIDE": """
CCCCCCCC
CCCCCCCC
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
########
########
DDDDDDDD
DDDDDDDD
DDDDDDDD
EEEEEEEE
EEEEEEEE
EEEEEEEE
TTTTTTTT
TTTTTTTT
TTTTTTTT
........
........""",
    "ARM_OUT": """
AAAAAAAA
AAAAAAAA
A'AAAA'A
AAAAAAAA
BBBBBBBB
BBBBBBBB
BBBBBBBB
CCCCCCCC
CCCCCCCC
CCCCCCCC
.CCCCCC.
........
........
........
TTTTTTTT
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
D'DDDD'D
TTTTTTTT
........
........""",
    "ARM_FB": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAA#
BBBBBBB#
BBBBBB##
BBBBBB##
CCCCC###
CCCCC###
CCCC####
.CC####.
........
........
........
TTTTTTTT
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
TTTTTTTT
........
........""",
    "ARM_IN": """
########
########
########
########
########
########
########
########
########
.######.
..####..
........
........
........
TTTTTTTT
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
TTTTTTTT
........
........""",
    "ARM_TOP": """
AAAAAAAA
A'AAAA'A
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
A'AAAA'A
AAAAAAAA""",
    "LEG_FB": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
A'AAAAAA
AAAAAAAA
AAAAAAA#
#AAAAA##
##AAA###
########
########
#TTTTTT#
TBBBBBBT
TB'BBBBT
TBBBBBBT
#TTTTTT#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
    "LEG_BACK": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
BBBBBBBB
BBBBBBBB
BBBBBBB#
#BBBBB##
#CCCCC##
##CCCC##
########
########
########
EEEEEEEE
EEEEEEEE
EEEEEEEE
EEEEEEEE
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
    "LEG_OUT": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
A'AAAA'A
AAAAAAAA
AAAAAAAA
.AAAAAA.
..AAAA..
...##...
########
#TTTTTT#
TBBBBBBT
TBBBBBBT
TBBBBBBT
#TTTTTT#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
    "LEG_IN": """
########
########
########
########
########
########
########
########
########
########
########
########
########
########
########
########
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
    "HIP": """
........
........
........
........
........
........
........
........
........
........
........
........
........
........
........
........
TTTTTTTT
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA""",
    "BOOT_FB": """
..TTTT..
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
BAAAAAAB
BBAAAABB
B'BBBBBB
BBBBBBBB
::::::::""",
    "BOOT_BACK": """
........
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
TTTTTTTT
BBBBBBBB
BBBBBBBB
BBBBBBBB
::::::::""",
    "BOOT_SIDE": """
........
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
TTTTTTTT
BBBBBBBB
BBBBBBBB
BBBBBBBB
::::::::""",
}

DRAWINGS = {
    "bronze": {},        # the default drawing is the Bronze one: round riveted plates and a buckle
    "ember": {           # a sun disc on the chest, scales on the belly, hems cut in flame tongues
        "CHEST": """
CCCC####
CCCCC###
TTTTTT##
AAAAAAT#
AAAAAAAT
AAAAAAAo
AAAAAoog
AAAAAog*
AAAAoog*
AAAAAog*
AAAAAoog
AAAAAAAo
#AAAAAAT
#DDDDDDD
#DDDDDDD
##DD#DDD
#EEEEEEE
#EEEEEEE
##EE#EEE
TTTTTTTT
TTTTTTxg
HHHHHHHH
.HH..HH.
..H..H..""",
        "BACK": """
CCCCCCCC
CCCCCCCC
TTTTTTTT
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
#AAAAAT:
#DDDDDDD
#DDDDDDD
##DD#DDD
#EEEEEEE
#EEEEEEE
##EE#EEE
TTTTTTTT
TTTTTTTT
HHHHHHHH
HHHHHHHH
.HH..HH.
..H..H..""",
        "ARM_OUT": """
AAAAAAAA
AAAAAAAA
AAAooAAA
AAog*oAA
AAAooAAA
BBBBBBBB
BBBBBBBB
BBBBBBBB
CCCCCCCC
CCCCCCCC
.CC..CC.
..C..C..
........
........
TTTTTTTT
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
.DD..DD.
..D..D..
........
........""",
        "ARM_FB": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAA#
AAAAAAA#
BBBBBB##
BBBBBB##
BBBBBB##
CCCCC###
CCCCC###
.CC.C##.
..C..#..
........
........
TTTTTTTT
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
.DD..DD.
..D..D..
........
........""",
        "LEG_FB": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
BBBBBBBB
BBBBBBBB
BBBBBBB#
.BB.BB##
..B..B##
########
########
#TTTTTT#
TBBBBBBT
TB'BBBBT
TBBBBBBT
#TTTTTT#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "LEG_OUT": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
BBBBBBBB
BBBBBBBB
BBBBBBBB
.BB..BB.
..B..B..
...##...
########
#TTTTTT#
TBBBBBBT
TBBBBBBT
TBBBBBBT
#TTTTTT#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "BOOT_FB": """
.T.TT.T.
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
TTTTTTTT
BBBBBBBB
B'BBBBBB
BBBBBBBB
::::::::""",
    },
    "rime": {            # slanted ice shards, long points at every hem, no dark trim
        "CHEST": """
CCC#####
CCCCC###
CCCCCCC#
AAAACCCC
AAAAAACC
AAAAAAAA
AAAAAAAA
AAAAAAAo
AAAAAAog
AAAAAAAo
AAAAAAAA
BBAAAAAA
BBBBAAAA
#BBBBBAA
#BBBBBBB
##BBBBBB
#BBBBBBB
#DDBBBBB
##DDDDBB
TTTTTTTT
TTTTTTTT
FFF.FFFF
.F...FFF
......FF""",
        "BACK": """
CCCCCCCC
CCCCCCCC
CCCCCCCC
AAAACCCC
AAAAAACC
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
BBAAAAAA
BBBBAAAA
#BBBBBAA
#BBBBBBB
##BBBBBB
#BBBBBBB
#DDBBBBB
##DDDDBB
TTTTTTTT
TTTTTTTT
FFF.FFFF
.F...FFF
......FF""",
        "ARM_OUT": """
AAAAAAAA
AAAAAAAA
AAAAAABB
AAAABBBB
AABBBBBB
BBBBBBCC
BBBBCCCC
BBCCCCCC
CCCCCCCC
CCC.CCC.
CC..CC..
C...C...
........
........
DDDDDDDD
DDDDDDEE
DDDDEEEE
DDEEEEEE
EEEEEEEE
EEEEEEEE
EEE.EEE.
EE..EE..
........
........""",
        "ARM_FB": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
BBAAAAA#
BBBBAAA#
BBBBBB##
CCBBBB##
CCCCB###
CCCCC###
CC.CC###
C..C.##.
...C....
........
........
DDDDDDDD
EEDDDDDD
EEEEDDDD
EEEEEEDD
EEEEEEEE
EEEEEEEE
EE.EEE.E
E..EE...
........
........""",
        "LEG_FB": """
AAAAAAAA
AAAAAAAA
BBAAAAAA
BBBBAAAA
BBBBBBAA
BBBBBBBB
BBBBBBB#
BB.BBB##
B..BB###
...B####
########
#CCCCCC#
CCCCCCCC
CCC'CCCC
.CCCCCC.
#.CCCC.#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "LEG_OUT": """
AAAAAAAA
AAAAAAAA
AAAAAABB
AAAABBBB
AABBBBBB
BBBBBBBB
BBBBBBBB
BBB.BBB.
BB..BB..
B..#B...
########
#CCCCCC#
CCCCCCCC
CCCCCCCC
.CCCCCC.
#.CCCC.#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "BOOT_FB": """
A..AA..A
AA.AA.AA
AAAAAAAA
AAAAAAAA
BBAAAAAA
BBBBAAAA
BBBBBBAA
BBBBBBBB
B'BBBBBB
BBBBBBBB
::::::::""",
        "BOOT_SIDE": """
A...A...
AA..AA..
AAAAAAAA
AAAAAAAA
AAAAAABB
AAAABBBB
AABBBBBB
BBBBBBBB
BBBBBBBB
BBBBBBBB
::::::::""",
        "BOOT_BACK": """
A...A...
AA..AA..
AAAAAAAA
AAAAAAAA
AAAAAABB
AAAABBBB
AABBBBBB
BBBBBBBB
BBBBBBBB
BBBBBBBB
::::::::""",
    },
    "void": {            # angular plates around a black diamond socket with a lit rim
        "CHEST": """
CCCC####
CCCCC###
TTTTTT##
AAAAAAT#
AAAAAAAo
AAAAAAo:
AAAAAo::
AAAAo::g
AAAAo::g
AAAAAo::
AAAAAAo:
#AAAAAAo
##AAAAAT
#DDDDDDT
#DDDDDDD
##DDDDDD
#EEEEEEE
#EEEEEEE
##EEEEEE
TTTTTTTT
TTTTTTo:
TTTTTTTT
.FFFFFF.
..FFFF..""",
        "BACK": """
CCCCCCCC
CCCCCCCC
TTTTTTTT
AAAAAAT:
AAAAAAT:
AAAAAAo:
AAAAAAT:
AAAAAAT:
AAAAAAo:
AAAAAAT:
AAAAAAT:
#AAAAAo:
##AAAAT:
#DDDDDDD
#DDDDDDD
##DDDDDD
#EEEEEEE
#EEEEEEE
##EEEEEE
TTTTTTTT
TTTTTTTT
TTTTTTTT
.FFFFFF.
..FFFF..""",
        "ARM_OUT": """
AAAAAAAA
AAAAAAAA
AAAooAAA
AAo::oAA
AAAooAAA
BBBBBBBB
BBBBBBBB
BBBBBBBB
CCCCCCCC
.CCCCCC.
..CCCC..
...CC...
........
........
TTTTTTTT
DDDDDDDD
DDDDDDDD
DDDooDDD
DDDDDDDD
.DDDDDD.
..DDDD..
...DD...
........
........""",
        "ARM_FB": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAA#
AAAAAAA#
BBBBBB##
BBBBBB##
BBBBBB##
CCCCC###
.CCCC###
..CC.##.
...C....
........
........
TTTTTTTT
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
.DDDDDD.
..DDDD..
...DD...
........
........""",
        "LEG_FB": """
AAAAAAAA
AAAAAAAA
AAAooAAA
AAo::oAA
AAAooAAA
AAAAAAAA
.AAAAAA#
#.AAAA##
##.AA###
########
########
#TTTTTT#
TBBBBBBT
TBBooBBT
.TBBBBT.
#.TTTT.#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "LEG_OUT": """
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
.AAAAAA.
..AAAA..
...AA...
...##...
########
#TTTTTT#
TBBBBBBT
TBBBBBBT
.TBBBBT.
#.TTTT.#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "BOOT_FB": """
...TT...
T.TTTT.T
AAAAAAAA
AAAooAAA
AAo::oAA
AAAooAAA
TTTTTTTT
BBBBBBBB
B'BBBBBB
BBBBBBBB
::::::::""",
    },
    "starcore": {        # ivory plates rimmed in gold, a diamond gem, feathered shoulders
        "CHEST": """
TTTT####
CCCTT###
CCCCTT##
TTTTTTT#
AAAAAATT
AAAAAAAT
AAAAAATg
AAAAATg*
AAAAATg*
AAAAAATg
AAAAAAAT
TAAAAATT
#TTAAAAT
#DDTTTTT
#DDDDDDD
#TDDDDDD
##TTTTTT
#EEEEEEE
#EEEEEEE
TTTTTTTT
TTTTTTxg
TTTTTTTT
FFFFF.FF
.FFF...F""",
        "BACK": """
TTTTTTTT
CCCCCCCC
CCCCCCCC
TTTTTTTT
AAAAAATx
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
AAAAAAT:
AAAAAATx
TAAAAAT:
#TTAAATx
#DDTTTTT
#DDDDDDD
#TDDDDDD
##TTTTTT
#EEEEEEE
#EEEEEEE
TTTTTTTT
TTTTTTTT
TTTTTTTT
FFFFF.FF
.FFF...F""",
        "ARM_OUT": """
TTTTTTTT
TxTTTTxT
AABBAABB
AABBAABB
AABBAABB
DCCDDCCD
DCCDDCCD
DCCDDCCD
AABBAABB
AABBAABB
AABBAABB
A.B.A.B.
........
........
.EEEEEE.
EEEEEEEE
EEEggEEE
EEEggEEE
EEEEEEEE
EEEEEEEE
EEEEEEEE
.EEEEEE.
........
........""",
        "ARM_FB": """
TTTTTTTT
TTTTTTTT
AABBAABB
AABBAAB#
AABBAAB#
DCCDDC##
DCCDDC##
DCCDDC##
AABBA###
AABBA###
AABB.##.
A.B..#..
........
........
.EEEEEE.
EEEEEEEE
EEEEEEEE
EEEEEEEE
EEEEEEEE
EEEEEEEE
EEEEEEEE
.EEEEEE.
........
........""",
        "ARM_TOP": """
TTTTTTTT
TAAAAAAT
TAAAAAAT
TAA''AAT
TAA''AAT
TAAAAAAT
TAAAAAAT
TTTTTTTT""",
        "LEG_FB": """
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
TAAAAAAT
#TAAAAT#
##TAAT##
###TT###
########
########
#TTTTTT#
TBBggBBT
TBg**gBT
TBBggBBT
#TTTTTT#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "LEG_OUT": """
TTTTTTTT
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
TAAAAAAT
.TAAAAT.
..TAAT..
...TT...
...##...
########
#TTTTTT#
TBBBBBBT
TBBBBBBT
TBBBBBBT
#TTTTTT#
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD
DDDDDDDD""",
        "BOOT_FB": """
..TTTT..
TTTTTTTT
AAAAAAAA
AAAggAAA
AAAggAAA
AAAAAAAA
BAAAAAAB
BBAAAABB
B'BBBBBB
TTTTTTTT
::::::::""",
        "BOOT_SIDE": """
B.B.....
BABAB.B.
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
AAAAAAAA
BBBBBBBB
BBBBBBBB
TTTTTTTT
::::::::""",
    },
}

PLATE, TRIM, WILD = "ABCDEFGH", "TUVWXYZ", "',x"


def bitmap(s, name):
    rows = (DRAWINGS[s["art"]].get(name) or DEFAULT[name]).strip().split("\n")
    assert all(len(r) == 8 for r in rows), (s["art"], name)
    return rows


def mirror(rows):
    return [r[::-1] for r in rows]


def whole(rows):
    """A torso face from its left half."""
    return [r + r[::-1] for r in rows]


def paint(rows, s):
    """The colours of a bitmap (None = nothing). A plate or trim piece is lit on its top and left edges and shaded
    on its bottom and right ones; the edge of the face is not an edge of the piece (it goes on around the box)."""
    h, w = len(rows), len(rows[0])
    plate, trim, glow = s["plate"], s["trim"], s["glow"]
    cloth, ink = darker(plate[0], 0.3), darker(plate[0], 0.6)
    flat = {"o": glow[0], "g": glow[1], "*": glow[2], ":": ink, "'": plate[3], ",": plate[0], "x": trim[3]}

    def edge(x, y, ch):
        if not (0 <= x < w and 0 <= y < h):
            return False
        other = rows[y][x]
        return other != ch and other not in WILD

    out = [[None] * w for _ in range(h)]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch in flat:
                out[y][x] = flat[ch]
            elif ch == "#":
                above = rows[y - 1][x] if y else "#"
                out[y][x] = darker(cloth, 0.3) if above in PLATE + TRIM else cloth
            else:
                tones = plate if ch in PLATE else trim
                if ch in PLATE[1::2]:       # every other plate sits a little in the shade, so neighbours read as facets
                    tones = (tones[0], mix(tones[1], tones[0], 0.28), mix(tones[2], tones[1], 0.4), tones[2])
                top, left = edge(x, y - 1, ch), edge(x - 1, y, ch)
                low, right = edge(x, y + 1, ch), edge(x + 1, y, ch)
                if top and left:
                    out[y][x] = tones[2]
                elif top or left:
                    out[y][x] = mix(tones[1], tones[2], 0.6)
                elif low or right:
                    out[y][x] = mix(tones[1], tones[0], 0.6)
                else:
                    out[y][x] = tones[1]
    return out


def blit(tex, colours, x, y):
    for j, row in enumerate(colours):
        for i, c in enumerate(row):
            if c is not None:
                tex[y + j][x + i] = c


def limb(tex, s, box, out, fb, back, inner, y=0):
    """The four sides of an arm or a leg. The back continues the front around the outer side, so it is mirrored."""
    f = faces(box)
    for name, rows in (("right", out), ("front", fb), ("left", inner), ("back", mirror(back))):
        blit(tex, paint(rows, s), f[name][0], f[name][1] + y)


def solid(tex, rect, c):
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            tex[y + j][x + i] = c


def worn(s):
    """Chestplate (torso and arms) and boots."""
    tex = [[None] * W for _ in range(H)]
    body, arm, leg = faces("body"), faces("arm"), faces("leg")
    blit(tex, paint(whole(bitmap(s, "CHEST")), s), *body["front"][:2])
    blit(tex, paint(whole(bitmap(s, "BACK")), s), *body["back"][:2])
    side = bitmap(s, "SIDE")
    blit(tex, paint(side, s), *body["right"][:2])
    blit(tex, paint(mirror(side), s), *body["left"][:2])
    solid(tex, body["top"], s["trim"][1])
    limb(tex, s, "arm", bitmap(s, "ARM_OUT"), bitmap(s, "ARM_FB"), bitmap(s, "ARM_FB"), bitmap(s, "ARM_IN"))
    blit(tex, paint(bitmap(s, "ARM_TOP"), s), *arm["top"][:2])
    boot_side = bitmap(s, "BOOT_SIDE")
    limb(tex, s, "leg", boot_side, bitmap(s, "BOOT_FB"), bitmap(s, "BOOT_BACK"), mirror(boot_side),
         y=BOXES["leg"][3] - len(boot_side))
    solid(tex, leg["bottom"], darker(s["plate"][0], 0.6))
    return tex


def leggings(s):
    """Belt and legs."""
    tex = [[None] * W for _ in range(H)]
    body, leg = faces("body"), faces("leg")
    hip = bitmap(s, "HIP")
    for name in ("front", "back"):
        blit(tex, paint(whole(hip), s), *body[name][:2])
    for name in ("right", "left"):
        blit(tex, paint(hip, s), *body[name][:2])
    limb(tex, s, "leg", bitmap(s, "LEG_OUT"), bitmap(s, "LEG_FB"), bitmap(s, "LEG_BACK"), bitmap(s, "LEG_IN"))
    solid(tex, leg["top"], s["plate"][1])
    return tex


def to_png(tex):
    return png(W, H, [[c + (255,) if c else (0, 0, 0, 0) for c in row] for row in tex])


def preview(path, scale=5):
    """A flat front and back view of every set on a bare body (leggings under boots under the chestplate): the
    composition only, the game adds the volume and its own shading."""
    skin, bg = (196, 150, 120), (58, 60, 72)
    u = 4 * S                                           # the width of a limb
    cw, ch = 4 * u, 8 * S + 24 * S
    views = []
    for s in SETS.values():
        layers = (leggings(s), worn(s))
        for side in ("front", "back"):
            canvas = [[bg] * cw for _ in range(ch)]
            for j in range(8 * S):
                for i in range(2 * u):
                    canvas[j][u + i] = skin
            parts = (("leg", u, False), ("leg", 2 * u, True), ("arm", 0, False), ("arm", 3 * u, True), ("body", u, False))
            for part, ox, flip in parts:
                fx, fy, fw, fh = faces(part)[side]
                oy = 8 * S + (12 * S if part == "leg" else 0)
                if side == "back":
                    ox = cw - ox - fw
                for j in range(fh):
                    for i in range(fw):
                        c = skin
                        for layer in layers:
                            c = layer[fy + j][fx + (fw - 1 - i if flip else i)] or c
                        canvas[oy + j][ox + i] = c
            views.append(canvas)
    gap = 4
    img = [[bg] * ((cw + gap) * len(views)) for _ in range(ch + 2 * gap)]
    for k, canvas in enumerate(views):
        for y in range(ch):
            for x in range(cw):
                img[gap + y][gap // 2 + k * (cw + gap) + x] = canvas[y][x]
    big = [[c for c in row for _ in range(scale)] for row in img for _ in range(scale)]
    pathlib.Path(path).write_bytes(png(len(big[0]), len(big), [[c + (255,) for c in row] for row in big]))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def main():
    for name, s in SETS.items():
        for folder, tex in (("humanoid", worn(s)), ("humanoid_leggings", leggings(s))):
            path = ASSETS / "textures/entity/equipment" / folder / f"{name}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(to_png(tex))
        asset = ASSETS / "equipment" / f"{name}.json"
        layers = {"humanoid": [{"texture": f"vampyuyu:{name}"}], "humanoid_leggings": [{"texture": f"vampyuyu:{name}"}]}
        write_json(asset, {"layers": layers})
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | {f"equipment/{n}" for n in SETS})) + "\n")
    print(f"painted {2 * len(SETS)} worn armor layers at {W}x{H}")
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
