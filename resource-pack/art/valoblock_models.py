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

    def java(self, state="base"):
        """The element list of one state, in Java model form (uv in 0..16)."""
        su, sv = 16 / self.res[0], 16 / self.res[1]
        out = []
        for tag, el in self.elements:
            if tag == "flash" and state != "fire":
                continue
            d = [0.0, 0.0, 0.0]
            if state == "reload" and tag.startswith("mag"):
                d = self.move(tag)
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


def model_json(elements, disp, name):
    tex = f"vampyuyu:item/valoblock/{name}_3d"
    return {"textures": {"0": tex, "particle": tex}, "gui_light": "front", "display": disp, "elements": elements}


def write_weapon(w):
    name = w["id"]
    src = Source(SOURCES / f"{name}.bbmodel")
    d = PACK / "models/item/valoblock"
    base = src.java()
    dump(d / f"{name}_3d.json", model_json(base, HIP, name))
    dump(d / f"{name}_aim.json", model_json(base, AIM, name))
    dump(d / f"{name}_inspect.json", model_json(base, INSPECT, name))
    if w["texture"] == "knife":   # no reload, no flash: the states fall back to the held model
        for state in ("fire", "reload"):
            dump(d / f"{name}_{state}.json", model_json(base, HIP, name))
    else:
        dump(d / f"{name}_fire.json", model_json(src.java("fire"), with_kick(HIP), name))
        dump(d / f"{name}_reload.json", model_json(src.java("reload"), RELOAD, name))
    dump(PACK / "items/valoblock" / f"{name}.json", definition(name))
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
    print(f"wrote {n} 3D weapons from {SOURCES.name}/ and the scope overlay under {PACK}")


if __name__ == "__main__":
    main()
