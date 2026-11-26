#!/usr/bin/env python3
"""ValoBlock 3D weapon models v4: Blockbench sources to pack models, stdlib only, deterministic.

    python3 resource-pack/art/valoblock_models.py        (also run at the end of valoblock_items.py)

The source of every gun and of the knife is a Blockbench project, resource-pack/art/valoblock_bb/<id>.bbmodel (format
"Java Block/Item", one embedded texture, modelled and painted in Blockbench: edit them there, then make art-valoblock).
Outliner groups: "body", "mag dx=.. dy=.. dz=.." (those boxes move by that offset in the reload state) and "flash"
(only in the fire state). Rotations are about z only (the Java format allows one axis per element).

Per weapon (shields stay flat sprites, written by valoblock_items.py), under the ValoBlock pack:
  models/item/valoblock/<id>_3d.json       the gun held (hip pose: lower right third)
  models/item/valoblock/<id>_fire.json     muzzle flash and a slight kick (shown 2 ticks after a shot)
  models/item/valoblock/<id>_reload.json   the "mag" group out (the pump back, the revolver's cylinder swung out), gun tilted
  models/item/valoblock/<id>_aim.json      same gun, centred under the crosshair (sneaking)
  models/item/valoblock/<id>_inspect.json  the gun turned to show its side (1 s after a weapon swap)
  textures/item/valoblock/<id>_3d.png      the Blockbench texture, byte for byte
  textures/item/valoblock/<id>.png         the hotbar and Armory sprite: a side view of the model (+ the 35 % _locked twin)
  items/valoblock/<id>.json                select on display_context: gui -> flat sprite (the hotbar, the Armory),
                                           anything else -> select on custom_model_data string 0
                                           ("fire" | "reload" | "aim" | "inspect"), fallback the held model
and the sniper scope overlay textures/misc/scope.png (equippable camera_overlay, see VbWeaponService), plus the same
reticle as a square, assets/minecraft/textures/misc/spyglass_scope.png: the HX-50 Sovereign (watchers_eye) is a spyglass, so the client
draws that vanilla overlay while it is held up (this pack only, Skyblock spyglasses keep the vanilla one).

Why the v1 guns rendered black: near-black texels shaded again by the engine. The texture keeps every texel above
25 % grey and every element is shade: false (scripts/test_valoblock_models.py checks both).
"""
import base64
import json
import math
import pathlib
import struct
import sys
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import valoblock_items as vi  # noqa: E402

PACK = vi.PACK
SOURCES = pathlib.Path(__file__).resolve().parent / "valoblock_bb"
KICK = -0.8          # the whole gun slides back this much in the fire state


# Display transforms. v1 scale was 0.8 in first person: x1.4 = 1.12. First person turns y +90: the game's +y rotation sends
# the model's +x (the muzzle) to -z, away from the camera (y -90, used up to v3, pointed every gun at the shooter: checked
# on real-client screenshots, 2026-11-24). The grip stays where it was, so the translation moved 4 units further out.
# The translation is in hand space (1/16 block) and keeps the gun in the lower right, the stock out of the camera.
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


HIP = display((1.0, 0.0, -10.0), (0, 90, 0))
# Aim: sights under the crosshair (about 9 units left of the hand origin, 5 up).
AIM = display((-8.0, 3.0, -10.0), (0, 90, 0), tp_t=(0, 1.0, -1.5))
# Reload: the gun tilts muzzle-up and comes toward the middle so the magazine drop is visible.
RELOAD = display((-1.0, 1.5, -9.0), (0, 90, 28), tp_r=(0, 90, 110))
# Inspect: the gun turns to show its side for a second after a swap.
INSPECT = display((-3.0, 3.0, -9.0), (12, 35, -8), tp_r=(0, 90, 100))


# ------------------------------------------------------------------ the two-handed hold (2026-11-26)
# Every gun but the knife and the spyglass sniper is a charged CROSSBOW (WeaponItems.carried): the client then poses a player or a
# bot holding it with both arms raised forward (the CROSSBOW_HOLD arm pose, the only two-armed pose a vanilla item can ask for). Two
# side effects are undone here so the gun keeps the place it was tuned for:
#   first person  a charged crossbow in the main hand is moved 0.641864 block toward the middle and turned 10 degrees about y before
#                 its display transform (FirstPersonHandsAndItemsRenderer, client 26.3): crossbow_fp() applies the inverse;
#   third person  the arm points forward (x rotation -90 + 5.7 degrees) instead of hanging at -18: crossbow_tp() turns the gun back
#                 by the difference, in the hand's frame, levels it (TP_LEVEL) and moves it so the grip stays in the fist.
# Matrices are 3 x 3 row lists; rotations follow the game: a display "rotation" is rotationXYZ (Rx . Ry . Rz), a model part ZYX.


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def _t(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def _rx(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def _ry(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def _rz(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _xyz(r):
    return _mul(_mul(_rx(r[0]), _ry(r[1])), _rz(r[2]))


def _euler_xyz(m):
    """(x, y, z) degrees with Rx(x) . Ry(y) . Rz(z) = m."""
    y = math.degrees(math.asin(max(-1.0, min(1.0, m[0][2]))))
    if abs(m[0][2]) < 0.99999:
        x = math.degrees(math.atan2(-m[1][2], m[2][2]))
        z = math.degrees(math.atan2(-m[0][1], m[0][0]))
    else:  # gimbal lock: put it all on x
        x, z = math.degrees(math.atan2(m[2][1], m[1][1])), 0.0
    return [round(x, 2), round(y, 2), round(z, 2)]


FP_SHIFT, FP_TURN = 0.641864 * 16, 10.0          # the charged-crossbow nudge, in display units and degrees (right hand)
ARM_ITEM, ARM_HOLD = math.degrees(-math.pi / 10), math.degrees(-math.pi / 2 + 0.1)   # the arm's x rotation: ITEM, CROSSBOW_HOLD
HAND_FRAME = _mul(_rx(-90), _ry(180))            # ItemInHandLayer: after the arm, before the display transform
# The arm-pitch undo alone left the muzzle about 35 degrees high: the hip pose it preserves was a low carry, not level. Measured on
# a row of mannequins holding the same rifle turned -90..+60 degrees about the hand's x axis (real client, 2026-11-26): -35 is level.
TP_LEVEL = -35.0


def crossbow_fp(entry):
    """A firstperson_righthand entry for a charged crossbow that lands where `entry` landed for a plain item.
    The game does T(-shift, 0, 0) . Ry(turn) . T(t) . R(r): so t' = Ry(-turn)(t + shift x), R' = Ry(-turn) . R."""
    t = list(entry.get("translation", [0, 0, 0]))
    r = entry.get("rotation", [0, 0, 0])
    back = _ry(-FP_TURN)
    t2 = _apply(back, [t[0] + FP_SHIFT, t[1], t[2]])
    out = dict(entry)
    out["translation"] = [round(v, 3) for v in t2]
    out["rotation"] = _euler_xyz(_mul(back, _xyz(r)))
    return out


def crossbow_tp(entry, grip):
    """A thirdperson_righthand entry for the CROSSBOW_HOLD arm: the same orientation in the world as `entry` on the ITEM arm (the
    raised arm's pitch is undone in the hand frame), the translation moved so the grip point (model units) stays in the fist."""
    r = entry.get("rotation", [0, 0, 0])
    t = list(entry.get("translation", [0, 0, 0]))
    s = entry.get("scale", [1, 1, 1])[0]
    undo = _mul(_mul(_t(HAND_FRAME), _mul(_rx(-ARM_HOLD), _rx(ARM_ITEM))), HAND_FRAME)
    r1, r2 = _xyz(r), _mul(_rx(TP_LEVEL), _mul(undo, _xyz(r)))
    g = [(grip[i] - 8) * s for i in range(3)]
    a, b = _apply(r1, g), _apply(r2, g)
    out = dict(entry)
    out["rotation"] = _euler_xyz(r2)
    out["translation"] = [round(max(-80, min(80, t[i] + a[i] - b[i])), 3) for i in range(3)]
    return out


def two_handed(d, grip):
    """A display block (both hands) for a gun held as a charged crossbow."""
    out = dict(d)
    out["firstperson_righthand"] = crossbow_fp(d["firstperson_righthand"])
    out["thirdperson_righthand"] = crossbow_tp(d["thirdperson_righthand"], grip)
    for k in [k for k in out if "lefthand" in k]:
        del out[k]
    return mirror(out)


# ------------------------------------------------------------------ the reload animation (2026-11-26)
# The plugin shows reload_0 .. reload_5 one after the other over the reload time (GunVisual.reloadFrame). Each frame is
# (tilt, magazine, hand, jolt, side): tilt 0 = the hip pose, 1 = the reload pose (blended); magazine = the factor of the "mag" group's
# offset; hand = how far the support hand follows the magazine (first person); jolt = degrees the muzzle kicks (the slap).
RELOAD_FRAMES = {
    "magazine": [(0.55, 0.0, 0.0, 0, 0), (1.0, 1.0, 0.0, 0, -1.5), (1.0, 2.2, 0.0, 0, -5.0), (1.0, 1.2, 1.0, 0, -3.5), (1.0, 0.0, 0.0, -6, 0),
                 (0.45, 0.0, 0.0, 0, 0)],
    "pump": [(0.4, 0.0, 0.0, 0, 0), (0.8, 1.0, 1.0, 0, 0), (0.8, 0.0, 0.0, -5, 0), (0.8, 1.0, 1.0, 0, 0), (0.8, 0.0, 0.0, -5, 0), (0.35, 0.0, 0.0, 0, 0)],
    "cylinder": [(0.55, 0.0, 0.0, 0, 0), (1.0, 1.0, 0.0, 0, 0), (1.0, 1.0, 0.6, 3, 0), (1.0, 1.0, 0.6, 0, 0), (1.0, 0.0, 0.0, -6, 0), (0.45, 0.0, 0.0, 0, 0)],
}
# (the fifth number: how far, in model units, the magazine also moves to the gun's left, the side the camera sees)
RELOAD_KIND = {"scrapper": "pump", "gravebell": "cylinder"}
RELOAD_FRAME_COUNT = 6   # = GunVisual.RELOAD_FRAMES
RELOAD_ROLL = {"magazine": -24.0, "cylinder": -16.0}   # degrees the gun turns about the line of sight while the magazine is out


def _lerp(a, b, t):
    return [round(a[i] + (b[i] - a[i]) * t, 3) for i in range(3)]


def reload_frame(tilt, jolt, roll=0.0):
    """The display block of a reload frame: HIP blended toward RELOAD by `tilt`, the muzzle kicked by `jolt` degrees, rolled `roll`
    degrees about the line of sight (first person only)."""
    out = {}
    for k, hip in HIP.items():
        rel = RELOAD.get(k, hip)
        e = dict(hip)
        for key in ("translation", "rotation"):
            if key in hip or key in rel:
                e[key] = _lerp(hip.get(key, [0, 0, 0]), rel.get(key, [0, 0, 0]), tilt)
        if jolt and ("firstperson" in k or "thirdperson" in k):
            e["rotation"] = [e["rotation"][0], e["rotation"][1], e["rotation"][2] + (jolt if "right" in k else -jolt)]
        if roll and k == "firstperson_righthand":   # turned about the line of sight: the magazine well faces the support hand
            e["rotation"] = _euler_xyz(_mul(_rz(roll), _xyz(e["rotation"])))
            e["translation"] = [round(e["translation"][0] - 0.06 * abs(roll), 3), round(e["translation"][1] + 0.05 * abs(roll), 3), e["translation"][2]]
        out[k] = e
    return mirror({k: v for k, v in out.items() if "lefthand" not in k})


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



def definition(name, hands=False, frames=False):
    """gui: the flat sprite; first person (with hands): the *_hands_* twins; anything else: the states by custom_model_data."""
    ref = lambda m: {"type": "minecraft:model", "model": f"vampyuyu:item/valoblock/{m}"}  # noqa: E731

    def states(prefix):
        return {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": "fire", "model": ref(f"{prefix}_fire")},
                      {"when": "reload", "model": ref(f"{prefix}_reload")},
                      *[{"when": f"reload_{k}", "model": ref(f"{prefix}_reload_{k}")} for k in range(RELOAD_FRAME_COUNT if frames else 0)],
                      {"when": "inspect", "model": ref(f"{prefix}_inspect")},
                      {"when": "aim", "model": ref(f"{prefix}_aim")},
                      {"when": "scope", "model": {"type": "minecraft:empty"}}],  # scoped sniper: nothing in view
            "fallback": ref(f"{prefix}_hip" if prefix.endswith("_hands") else f"{prefix}_3d")}

    cases = [{"when": "gui", "model": ref(name)}]
    if hands:
        cases.append({"when": ["firstperson_righthand", "firstperson_lefthand"], "model": states(f"{name}_hands")})
    # hand_animation_on_swap false: every shot changes the stack (the rounds are its count, the look its custom_model_data) and the
    # client would lower and raise the gun each time (FirstPersonHandsAndItems: a count change is a swap unless the model opts out).
    return {"model": {"type": "minecraft:select", "property": "minecraft:display_context", "cases": cases, "fallback": states(name)},
            "hand_animation_on_swap": False}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")




def read_png(data):
    """PNG bytes (8 bit RGBA or RGB, any filter, as Blockbench saves them) to RGBA rows."""
    pos, idat, w, h, ctype = 8, b"", 0, 0, 6
    while pos < len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
            if depth != 8 or ctype not in (2, 6) or body[12] != 0:
                raise ValueError("texture must be 8 bit RGB/RGBA, not interlaced")
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = 4 if ctype == 6 else 3
    raw, stride = zlib.decompress(idat), w * bpp
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b, c = prev[i], prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([tuple(line[x * bpp:x * bpp + bpp]) + ((255,) if bpp == 3 else ()) for x in range(w)])
        prev = line
    return rows


class Source:
    """One .bbmodel: elements with their group, the texture."""

    def __init__(self, path):
        data = json.loads(path.read_text(encoding="utf-8"))
        self.name = path.stem
        self.res = data["resolution"]["width"], data["resolution"]["height"]
        tex = data["textures"][0]["source"]
        self.png = base64.b64decode(tex.split(",", 1)[1])
        self.tex = read_png(self.png)
        group_of = {}
        names = {g["uuid"]: g["name"] for g in data.get("groups", [])}   # Blockbench 5 keeps group names apart

        def walk(nodes, tag):
            for node in nodes:
                if isinstance(node, str):
                    group_of[node] = tag
                else:
                    walk(node.get("children", []), tag or node.get("name") or names.get(node["uuid"], "body"))
        walk(data["outliner"], None)
        self.elements = []
        for el in data["elements"]:
            if el.get("type", "cube") != "cube" or el.get("export") is False:
                continue
            self.elements.append((group_of.get(el["uuid"], "body"), el))

    def move(self, tag):
        """(dx, dy, dz) of a "mag dx=.. dy=.. dz=.." group name."""
        out = [0.0, 0.0, 0.0]
        for part in tag.split()[1:]:
            k, _, v = part.partition("=")
            if k in ("dx", "dy", "dz"):
                out["xyz".index(k[1])] = float(v)
        return out

    def mag_move(self):
        """The (dx, dy, dz) of the "mag" group (0, 0, 0 without one)."""
        return next((self.move(tag) for tag, _ in self.elements if tag.startswith("mag")), [0.0, 0.0, 0.0])

    def java(self, state="base", mag=1.0, side=0.0):
        """The element list of one state, in Java model form (uv in 0..16). In the reload state the "mag" group is moved by its
        offset times `mag` (0 = in place, 1 = the offset of the group name, 3 = dropped well clear)."""
        su, sv = 16 / self.res[0], 16 / self.res[1]
        out = []
        for tag, el in self.elements:
            if tag == "flash" and state != "fire":
                continue
            d = [0.0, 0.0, 0.0]
            if state == "reload" and tag.startswith("mag"):
                d = [v * mag for v in self.move(tag)]
                d[2] += side
            if state == "fire":
                d[0] += KICK
            j = {"from": [round(el["from"][i] + d[i], 3) for i in range(3)],
                 "to": [round(el["to"][i] + d[i], 3) for i in range(3)], "shade": False, "faces": {}}
            rot = el.get("rotation", [0, 0, 0])
            axes = [(i, a) for i, a in enumerate(rot) if a]
            if len(axes) > 1:
                raise ValueError(f"{self.name}: {el['name']} turns about more than one axis")
            if axes:
                i, a = axes[0]
                o = el.get("origin", [8, 8, 8])
                j["rotation"] = {"angle": a, "axis": "xyz"[i], "origin": [round(o[k] + d[k], 3) for k in range(3)]}
            for f, face in el["faces"].items():
                if face.get("texture") is None:
                    continue
                u0, v0, u1, v1 = face["uv"]
                jf = {"uv": [round(u0 * su, 4), round(v0 * sv, 4), round(u1 * su, 4), round(v1 * sv, 4)], "texture": "#0"}
                if face.get("rotation"):
                    jf["rotation"] = face["rotation"]
                j["faces"][f] = jf
            out.append(j)
        return out

    def face_colour(self, el, face):
        """Average colour of a face's texture region."""
        u0, v0, u1, v1 = el["faces"][face]["uv"]
        sx, sy = len(self.tex[0]) / self.res[0], len(self.tex) / self.res[1]
        xs = range(int(min(u0, u1) * sx), max(int(min(u0, u1) * sx) + 1, int(max(u0, u1) * sx)))
        ys = range(int(min(v0, v1) * sy), max(int(min(v0, v1) * sy) + 1, int(max(v0, v1) * sy)))
        px = [self.tex[y][x] for y in ys for x in xs if y < len(self.tex) and x < len(self.tex[0])]
        return tuple(sum(p[i] for p in px) // len(px) for i in range(3))

    def sprite(self, size=32, margin=1, ss=4):
        """Side view (from +z) of the base state as size x size RGBA: the nearest box face wins, a dark outline."""
        boxes = []
        for tag, el in self.elements:
            if tag == "flash":
                continue
            rot = el.get("rotation", [0, 0, 0])[2]
            o = el.get("origin", [8, 8, 8])
            boxes.append((el["from"], el["to"], rot, o, self.face_colour(el, "south"), el["to"][2]))
        corners = []
        for a, b, rot, o, _, _ in boxes:
            for x in (a[0], b[0]):
                for y in (a[1], b[1]):
                    corners.append(turn(x, y, rot, o))
        x0, x1 = min(c[0] for c in corners), max(c[0] for c in corners)
        y0, y1 = min(c[1] for c in corners), max(c[1] for c in corners)
        span = size - 2 * margin - 2
        scale = min(span / (x1 - x0), span / (y1 - y0), 1.6)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        boxes.sort(key=lambda b: b[5])                    # back to front
        img = [[(0, 0, 0, 0)] * size for _ in range(size)]
        for py in range(size):
            for px in range(size):
                acc, hits = [0, 0, 0], 0
                for sy in range(ss):
                    for sx in range(ss):
                        mx = cx + (px + (sx + 0.5) / ss - size / 2) / scale
                        my = cy - (py + (sy + 0.5) / ss - size / 2) / scale
                        col = None
                        for a, b, rot, o, c, _ in boxes:
                            qx, qy = turn(mx, my, -rot, o)
                            if a[0] <= qx <= b[0] and a[1] <= qy <= b[1]:
                                col = c
                        if col:
                            hits += 1
                            for i in range(3):
                                acc[i] += col[i]
                if hits * 2 >= ss * ss:
                    img[py][px] = (acc[0] // hits, acc[1] // hits, acc[2] // hits, 255)
        out = [row[:] for row in img]
        for py in range(size):                            # 1 px dark outline around the silhouette
            for px in range(size):
                if img[py][px][3]:
                    continue
                if any(0 <= py + dy < size and 0 <= px + dx < size and img[py + dy][px + dx][3]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[py][px] = (14, 12, 22, 255)
        return out


def turn(x, y, deg, o):
    """(x, y) turned deg degrees about z around origin o (counter-clockwise seen from +z, as the game does)."""
    if not deg:
        return x, y
    r = math.radians(deg)
    dx, dy = x - o[0], y - o[1]
    return o[0] + dx * math.cos(r) - dy * math.sin(r), o[1] + dx * math.sin(r) + dy * math.cos(r)


def model_json(elements, disp, name, hands=None):
    tex = f"vampyuyu:item/valoblock/{name}_3d"
    textures = {"0": tex, "particle": tex}
    if hands:
        textures["1"] = HANDS_TEXTURE
    return {"textures": textures, "gui_light": "front", "display": disp, "elements": elements + (hands or [])}


# ------------------------------------------------------------------ first-person hands
# The game draws no arm next to a held item, so the gloved hands are part of the first-person models: resource-pack/art/valoblock_bb/
# fp_hands.bbmodel (edit it in Blockbench) holds three groups, each built round its group pivot in the gun's frame (+x to the muzzle,
# +y up, +z the gun's right): "right" (the firing hand round a vertical pistol grip), "left" (the support hand under a handguard) and
# "left_pistol" (the support hand cupping the firing hand). Each gun puts them on its own parts, found by name in its .bbmodel:
# the firing hand on "grip" (turned with it), the support hand under the first fore part (or round a "vertical grip", or cupping
# the firing hand on a pistol). Shown only in first person: the item definition picks the *_hands_* models for firstperson_*.
HANDS_SOURCE = SOURCES / "fp_hands.bbmodel"
HANDS_TEXTURE = "vampyuyu:item/valoblock/fp_hands"
GRIP_PARTS = ("grip", "grip frame")
FORE_PARTS = ("handguard", "lower handguard", "fore end", "forend", "pump")
PISTOLS = ("pip", "tickhand", "gravebell")
EPS = 0.013
HAND_SCALE = 1.3                              # the gloves are drawn 1.3 x the size they are modelled at, round their pivot
FORE_AT = {"grinder": (16.6, 7.25, 8.0)}      # the LMG has no fore part: the hand goes under the barrels, behind the first clamp


class Hands:
    def __init__(self, path=HANDS_SOURCE):
        data = json.loads(path.read_text(encoding="utf-8"))
        self.res = data["resolution"]["width"], data["resolution"]["height"]
        self.png = base64.b64decode(data["textures"][0]["source"].split(",", 1)[1])
        by_uuid = {e["uuid"]: e for e in data["elements"]}
        meta = {g["uuid"]: g for g in data.get("groups", [])}
        self.parts = {}
        for node in data["outliner"]:
            g = meta.get(node["uuid"], node)
            self.parts[g["name"]] = (g["origin"], [by_uuid[c] for c in node.get("children", []) if isinstance(c, str)])

    def place(self, group, at, angle=0.0, mirror_z=False, k=HAND_SCALE):
        """The group's boxes scaled k about its pivot (a gloved hand reads bigger than a bare one), moved from the pivot to `at`
        (model units) and turned `angle` degrees about z there."""
        pivot, els = self.parts[group]
        su, sv = 16 / self.res[0], 16 / self.res[1]
        out = []
        for el in els:
            rot = el.get("rotation", [0, 0, 0])
            if rot[0] or (rot[1] and rot[2]):
                raise ValueError(f"fp_hands {group}/{el['name']}: turn about z (follows the grip) or y (a forearm), not both, not x")
            frm = [(el["from"][i] - pivot[i]) * k for i in range(3)]
            to = [(el["to"][i] - pivot[i]) * k for i in range(3)]
            o = [(el.get("origin", pivot)[i] - pivot[i]) * k for i in range(3)]
            a, yaw = rot[2], rot[1]
            if mirror_z:
                frm[2], to[2], o[2] = -to[2], -frm[2], -o[2]
                a, yaw = a, -yaw
            # a point p of the box ends at  at + Rz(angle) . (o + Rz(a) . (p - o)):  a box about O = at + Rz(angle) . o, turned angle + a
            c, s_ = math.cos(math.radians(angle)), math.sin(math.radians(angle))
            big_o = [at[0] + c * o[0] - s_ * o[1], at[1] + s_ * o[0] + c * o[1], at[2] + o[2]]
            # every glove box is pulled in by EPS so none of its faces lies in the plane of a gun face (they would flicker)
            j = {"from": [round(big_o[i] + frm[i] - o[i] + EPS, 3) for i in range(3)], "to": [round(big_o[i] + to[i] - o[i] - EPS, 3) for i in range(3)],
                 "shade": False, "faces": {}}
            if yaw:   # a forearm swung out about y: it keeps its own axis (one axis per element), only its pivot follows the grip
                j["rotation"] = {"angle": round(yaw, 3), "axis": "y", "origin": [round(v, 3) for v in big_o]}
            elif angle + a:
                j["rotation"] = {"angle": round(angle + a, 3), "axis": "z", "origin": [round(v, 3) for v in big_o]}
            for f, face in el["faces"].items():
                if face.get("texture") is None:
                    continue
                if mirror_z and f in ("north", "south"):
                    f = "south" if f == "north" else "north"
                u0, v0, u1, v1 = face["uv"]
                j["faces"][f] = {"uv": [round(u0 * su, 4), round(v0 * sv, 4), round(u1 * su, 4), round(v1 * sv, 4)], "texture": "#1"}
            out.append(j)
        return out

    def on(self, src, follow=(0.0, 0.0, 0.0)):
        """Both hands placed on one gun (a Source), or [] for a weapon held one-handed (the knife). `follow` moves the support hand
        (model units): in the reload frames it carries the magazine."""
        if src.name == "shard_knife":
            return []
        named = {}
        for _, el in src.elements:
            named.setdefault(el["name"], el)
        grip = next((named[n] for n in GRIP_PARTS if n in named), None)
        if grip is None:
            raise ValueError(f"{src.name}: no grip part for the firing hand")
        g_at, g_angle = part_centre(grip), grip.get("rotation", [0, 0, 0])[2]
        out = self.place("right", g_at, g_angle)
        f = list(follow)

        def moved(at):
            return [at[i] + f[i] for i in range(3)]

        if src.name in PISTOLS:
            return out + self.place("left_pistol", moved(g_at), g_angle)
        if "vertical grip" in named:       # a fist round the vertical grip: the firing hand's shape, mirrored to the left
            v = named["vertical grip"]
            return out + self.place("right", moved(part_centre(v)), v.get("rotation", [0, 0, 0])[2], mirror_z=True)
        if src.name in FORE_AT:
            return out + self.place("left", moved(FORE_AT[src.name]))
        fore = next((named[n] for n in FORE_PARTS if n in named), None)
        if fore is None:
            raise ValueError(f"{src.name}: no fore part for the support hand (add one to FORE_AT)")
        return out + self.place("left", moved([(fore["from"][0] + fore["to"][0]) / 2, fore["from"][1], (fore["from"][2] + fore["to"][2]) / 2]))


def part_centre(el):
    """The middle of a box after its own z rotation."""
    c = [(el["from"][i] + el["to"][i]) / 2 for i in range(3)]
    rot = el.get("rotation", [0, 0, 0])[2]
    x, y = turn(c[0], c[1], rot, el.get("origin", [8, 8, 8]))
    return [x, y, c[2]]


HANDS_LIFT = (-1.0, 2.5, -1.0)   # first person with hands: the gun a little higher, nearer the middle and further out, so both gloves show


def lifted(d):
    """A display block with the first-person translation moved by HANDS_LIFT (before the crossbow compensation)."""
    out = dict(d)
    e = dict(d["firstperson_righthand"])
    e["translation"] = [round(e["translation"][i] + HANDS_LIFT[i], 3) for i in range(3)]
    out["firstperson_righthand"] = e
    return mirror({k: v for k, v in out.items() if "lefthand" not in k})


def held_two_handed(w):
    """Whether the plugin hands this weapon out as a charged crossbow (weapons.json "base"): it is posed with both arms."""
    return w.get("base", "STICK").upper() == "CROSSBOW"


def write_weapon(w, hands=None):
    name = w["id"]
    src = Source(SOURCES / f"{name}.bbmodel")
    d = PACK / "models/item/valoblock"
    base = src.java()
    two = held_two_handed(w)
    grip = None
    if two:
        named = {el["name"]: el for _, el in reversed(src.elements)}
        grip = part_centre(next(named[n] for n in GRIP_PARTS if n in named))

    def disp(x):
        return two_handed(x, grip) if two else x

    states = {"3d": (base, HIP), "aim": (base, AIM), "inspect": (base, INSPECT)}
    if w["texture"] == "knife":   # no reload, no flash: the states fall back to the held model
        states.update({"fire": (base, HIP), "reload": (base, HIP)})
    else:
        states.update({"fire": (src.java("fire"), with_kick(HIP)), "reload": (src.java("reload"), RELOAD)})
    on = hands.on(src) if hands and two else []
    for state, (els, dsp) in states.items():
        dump(d / f"{name}_{state}.json", model_json(els, disp(dsp), name))
        if on:   # the first-person twin with the gloved hands, <id>_hands_<hip|aim|inspect|fire|reload> (not *_3d: that is a gun)
            held = dsp if state == "aim" else lifted(dsp)   # the aiming pose keeps the sights under the crosshair
            dump(d / f"{name}_hands_{'hip' if state == '3d' else state}.json", model_json(els, disp(held), name, on))
    if w["texture"] != "knife":   # the reload animation: reload_0 .. reload_5
        frames = RELOAD_FRAMES[RELOAD_KIND.get(name, "magazine")]
        move = src.mag_move()
        for k, (tilt, mag, hand, jolt, side) in enumerate(frames):
            els, dsp = src.java("reload", mag, side), reload_frame(tilt, jolt, RELOAD_ROLL.get(RELOAD_KIND.get(name, "magazine"), 0) * (tilt >= 1.0))
            dump(d / f"{name}_reload_{k}.json", model_json(els, disp(dsp), name))
            if on:
                follow = [move[0] * hand, move[1] * hand, move[2] * hand + side * (1 if hand else 0)]
                dump(d / f"{name}_hands_reload_{k}.json", model_json(els, disp(lifted(dsp)), name, hands.on(src, follow)))
    dump(PACK / "items/valoblock" / f"{name}.json", definition(name, bool(on), w["texture"] != "knife"))
    tex = PACK / "textures/item/valoblock"
    tex.mkdir(parents=True, exist_ok=True)
    (tex / f"{name}_3d.png").write_bytes(src.png)
    sprite = src.sprite()
    (tex / f"{name}.png").write_bytes(vi.png(sprite))
    locked = [[(r, g, b, round(a * vi.LOCKED_ALPHA)) for r, g, b, a in row] for row in sprite]
    (tex / f"{name}_locked.png").write_bytes(vi.png(locked))


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
    hands = Hands()
    tex = PACK / "textures/item/valoblock"
    tex.mkdir(parents=True, exist_ok=True)
    (tex / "fp_hands.png").write_bytes(hands.png)
    for w in vi.weapons():
        if w["texture"] == "ward":   # shields are the flat sprite
            continue
        write_weapon(w, hands)
        n += 1
    out = PACK / "textures/misc/scope.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(vi.png(scope_overlay()))
    # Vanilla draws the spyglass overlay as a square scaled up to 1.125 x the window height: a 512 square, circle a bit smaller.
    spy = PACK.parent / "minecraft" / "textures" / "misc" / "spyglass_scope.png"
    spy.parent.mkdir(parents=True, exist_ok=True)
    spy.write_bytes(vi.png(scope_overlay(512, 512, 0.44, cross=True)))
    print(f"wrote {n} 3D weapons from {SOURCES.name}/ and the scope overlay under {PACK}")


if __name__ == "__main__":
    main()
