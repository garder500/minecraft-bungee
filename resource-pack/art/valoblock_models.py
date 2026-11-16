#!/usr/bin/env python3
"""ValoBlock 3D weapon models, stdlib only, deterministic (same method as sword_models.py: boxes on a small atlas).

    python3 resource-pack/art/valoblock_models.py        (also run at the end of valoblock_items.py)

Source: the 24x10 side views of valoblock_items.TEMPLATES (the VB-Weapons design sheet) and the metals of
valoblock_items.METALS (Bronzite, Sunstone, Frostium, Voidglass, bone, marsh iron, storm glass, starcore).
Every gun is built along +x (muzzle at the high x, the vanilla `handheld` convention: +x points forward in the
hand), grip at the centre of the model. Each pixel run of the side view becomes one box, extruded in depth by
its part (frame, metal, grip, glow), plus per-class parts (scope tube, muzzle brake, rear sight...). The cyan
pixel stays cyan (muzzle or scope lens).

Per weapon (shields stay flat sprites, written by valoblock_items.py), under the ValoBlock pack:
  models/item/valoblock/<id>_3d.json      the gun held
  models/item/valoblock/<id>_fire.json    muzzle flash and a slight kick (shown 2 ticks after a shot)
  models/item/valoblock/<id>_reload.json  the magazine (or pump, or cylinder) out
  models/item/valoblock/<id>_aim.json    same gun, tighter hold (sneaking)
  textures/item/valoblock/<id>_3d.png     16x16 atlas of 4x4 materials
  items/valoblock/<id>.json               select on display_context: gui -> flat sprite (the hotbar, the Armory),
                                          anything else -> select on custom_model_data string 0
                                          ("fire" | "reload" | "aim"), fallback the held model
and the sniper scope overlay textures/misc/scope.png (equippable camera_overlay, see VbWeaponService).
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import boss_skins as bs  # noqa: E402
import valoblock_items as vi  # noqa: E402

PACK = vi.PACK
S = 0.62  # model pixels per column / row of the 24x10 side view

# Material tile order of the atlas.
MATS = ["o", "a", "b", "c", "d", "g", "e", "f", "m"]  # f = muzzle flash, m = dark metal (details)
FLASH = (255, 222, 120)

# Rows (0-9) and columns of the parts that move in the reload variant: (c0, c1, r0, r1, dx, dy) in pixels.
MAGAZINE = {
    "pistol": (5, 9, 5, 9, 0, 4),
    "revolver": (3, 8, 3, 5, 0, 3),
    "smg": (12, 15, 5, 9, 0, 4),
    "shotgun": (13, 23, 5, 6, -4, 0),   # the pump slides back
    "rifle": (14, 17, 5, 9, 0, 4),
    "sniper": (14, 16, 5, 7, 0, 4),
    "lmg": (11, 16, 6, 9, 0, 4),
}
MUZZLE_ROW = {"pistol": 2, "revolver": 2, "smg": 2, "shotgun": 3, "rifle": 3, "sniper": 3, "lmg": 3}
# Depth (model pixels along z) of each pixel class.
DEPTH = {"o": 1.5, "a": 1.9, "b": 2.0, "c": 2.2, "d": 2.3, "g": 1.8, "e": 2.4}

DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [1.5, 1.5, -1], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, 90, 0], "translation": [1.5, 1.5, -1], "scale": [0.8, 0.8, 0.8]},
    "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [20, 220, 0], "scale": [0.9, 0.9, 0.9]},
}
# Aiming: the gun comes closer to the middle of the screen and up, a little less spread out.
AIM_DISPLAY = dict(DISPLAY)
AIM_DISPLAY["firstperson_righthand"] = {"rotation": [0, -90, 0], "translation": [0.2, 2.2, -1.5], "scale": [0.8, 0.8, 0.8]}
AIM_DISPLAY["firstperson_lefthand"] = {"rotation": [0, 90, 0], "translation": [0.2, 2.2, -1.5], "scale": [0.8, 0.8, 0.8]}
AIM_DISPLAY["thirdperson_righthand"] = {"rotation": [0, -90, 0], "translation": [0, 3.4, 1.5], "scale": [0.9, 0.9, 0.9]}
AIM_DISPLAY["thirdperson_lefthand"] = {"rotation": [0, 90, 0], "translation": [0, 3.4, 1.5], "scale": [0.9, 0.9, 0.9]}


class Gun(bs.Skin):
    """Boxes of one gun; add() works in side-view pixels (columns, rows), z is the depth."""

    def px(self, c0, c1, r0, r1, ch, dx=0.0, dy=0.0, depth=None, grow=0.0):
        """A box covering columns c0..c1 and rows r0..r1 inclusive of the 24x10 side view (dx, dy in view pixels)."""
        d = DEPTH.get(ch, 1.8) if depth is None else depth
        x0 = 8 + (c0 + dx - 7) * S
        x1 = 8 + (c1 + dx + 1 - 7) * S
        y1 = 8 + (6 - (r0 + dy)) * S
        y0 = 8 + (6 - (r1 + dy + 1)) * S
        self.box((x0 - grow, y0 - grow, 8 - d / 2 - grow), (x1 + grow, y1 + grow, 8 + d / 2 + grow), ch)

    def add_rows(self, rows, skip=None, shift=None, only=None):
        """One box per run of the same character in a row. `skip` / `only`: (c0, c1, r0, r1) of pixels left out / alone."""
        def drawn(c, r):
            if only is not None and not (only[0] <= c <= only[1] and only[2] <= r <= only[3]):
                return False
            return not (skip is not None and skip[0] <= c <= skip[1] and skip[2] <= r <= skip[3])

        for r, row in enumerate(rows):
            c = 0
            while c < len(row):
                if row[c] == "." or not drawn(c, r):
                    c += 1
                    continue
                end = c
                while end + 1 < len(row) and row[end + 1] == row[c] and drawn(end + 1, r):
                    end += 1
                dx, dy = shift or (0, 0)
                self.px(c, end, r, r, row[c], dx, dy)
                c = end + 1


def atlas_materials(metal):
    pal = dict(vi.COMMON)
    pal.update(vi.METALS[metal])
    mats = []
    for ch in MATS:
        if ch == "f":
            mats.append(("f", FLASH, "glow"))
        elif ch == "m":
            mats.append(("m", tuple(int(v * 0.7) for v in vi.rgb(pal["a"])), "plain"))
        elif ch == "e":
            mats.append(("e", vi.rgb(pal["e"]), "glow"))
        else:
            mats.append((ch, vi.rgb(pal[ch]), "plain"))
    return mats


def details(g, template):
    g.px = lambda *a, **k: Gun.px(g, *a, **{"grow": 0.05, **k})  # parts overlapping the silhouette never share a plane
    """Per-class parts that the flat side view cannot show."""
    rows = vi.TEMPLATES[template]
    mr = MUZZLE_ROW[template] if template in MUZZLE_ROW else None
    if mr is None:
        return
    muzzle = max(i for i, ch in enumerate(rows[mr]) if ch != ".")
    g.px(muzzle, muzzle, mr, mr, "m", dx=0.6, depth=1.2)                 # muzzle ring
    g.px(muzzle - 1, muzzle - 1, mr - 1 if mr else mr, mr - 1 if mr else mr, "m", depth=0.8)  # front sight
    if template == "sniper":                                              # scope tube: long, round-ish (two stacked boxes), lens
        g.px(7, 15, 0, 1, "m", depth=2.6)
        g.px(8, 14, 0, 0, "m", depth=3.0)
        g.px(5, 6, 1, 2, "m", depth=1.0)
        g.px(16, 17, 1, 2, "m", depth=1.0)
        g.px(15, 15, 0, 1, "e", dx=0.9, depth=2.4)                       # lens
    if template in ("rifle", "lmg", "smg"):                               # top rail
        g.px(8, 19, 1, 1, "m", depth=1.0)
    if template == "lmg":                                                 # heat-shield sleeve and bipod
        g.px(18, 21, 4, 4, "m", depth=2.8)
        g.px(19, 19, 5, 8, "m", depth=0.8)
        g.px(21, 21, 5, 8, "m", depth=0.8)
    if template == "shotgun":                                             # magazine tube under the barrel
        g.px(7, 21, 5, 5, "m", depth=1.6)


def build(w, state="base"):
    """The elements of one weapon in one state: base | fire | reload."""
    t = w["texture"]
    g = Gun(w["id"], atlas_materials(w["metal"]), hash_seed(w["id"]))
    rows = vi.TEMPLATES[t]
    if t == "knife":
        build_knife(g, rows, state)
        return g
    mag = MAGAZINE[t]
    magbox = (mag[0], mag[1], mag[2], mag[3])
    if state == "reload":
        g.add_rows(rows, skip=magbox)
        g.add_rows(rows, only=magbox, shift=(mag[4], mag[5]))   # the magazine drops away
    else:
        g.add_rows(rows)
    details(g, t)
    if state == "fire":
        kick(g)
        flash(g, rows, t)
    return g


def hash_seed(name):
    return sum(ord(c) * (i + 1) for i, c in enumerate(name))


def build_knife(g, rows, state):
    """A shard blade (the flat side view) with a guard and a wrapped grip; `fire` / `reload` do not apply."""
    g.add_rows(rows)
    g.px(5, 6, 3, 6, "m", depth=2.8)    # crossguard
    g.px(0, 4, 4, 5, "g", depth=1.8)    # grip wrap


def kick(g):
    """Recoil: the whole gun slides back 0.8 pixel (the muzzle rise is a z turn of the hand transform, see with_kick)."""
    for el in g.elements:
        el["from"][0] -= 0.8
        el["to"][0] -= 0.8


def flash(g, rows, template):
    """A 2-tick muzzle flash: a core and four spikes in front of the muzzle."""
    mr = MUZZLE_ROW[template]
    muzzle = max(i for i, ch in enumerate(rows[mr]) if ch != ".")
    x = 8 + (muzzle + 1 - 7) * S + 0.4
    y = 8 + (6 - mr - 0.5) * S
    g.box((x, y - 0.7, 7.3), (x + 1.4, y + 0.7, 8.7), "f")
    g.box((x + 1.4, y - 0.25, 7.75), (x + 3.2, y + 0.25, 8.25), "f")
    g.box((x + 0.2, y + 0.7, 7.75), (x + 0.9, y + 2.0, 8.25), "f")
    g.box((x + 0.2, y - 2.0, 7.75), (x + 0.9, y - 0.7, 8.25), "f")
    g.box((x + 0.2, y - 0.25, 8.7), (x + 0.9, y + 0.25, 9.8), "f")
    g.box((x + 0.2, y - 0.25, 6.2), (x + 0.9, y + 0.25, 7.3), "f")


def model_json(g, display, name):
    tex = f"vampyuyu:item/valoblock/{name}_3d"
    return {"textures": {"0": tex, "particle": tex}, "gui_light": "front", "display": display, "elements": g.elements}


def with_kick(display):
    """The recoil turn: the muzzle rises about 6 degrees (z rotation is the pitch of a +x model in hand)."""
    out = {}
    for k, v in display.items():
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
                      {"when": "aim", "model": ref(f"{name}_aim")}],
            "fallback": ref(f"{name}_3d")}}}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def write_weapon(w):
    name = w["id"]
    base = build(w)
    dump(PACK / "models/item/valoblock" / f"{name}_3d.json", model_json(base, DISPLAY, name))
    dump(PACK / "models/item/valoblock" / f"{name}_aim.json", model_json(base, AIM_DISPLAY, name))
    if w["texture"] == "knife":   # no reload, no flash: the states fall back to the held model
        for state in ("fire", "reload"):
            dump(PACK / "models/item/valoblock" / f"{name}_{state}.json", model_json(base, DISPLAY, name))
    else:
        dump(PACK / "models/item/valoblock" / f"{name}_fire.json", model_json(build(w, "fire"), with_kick(DISPLAY), name))
        dump(PACK / "models/item/valoblock" / f"{name}_reload.json", model_json(build(w, "reload"), DISPLAY, name))
    dump(PACK / "items/valoblock" / f"{name}.json", definition(name))
    tex = PACK / "textures/item/valoblock" / f"{name}_3d.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    tex.write_bytes(bs.png(base.atlas()))


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
                if (abs(dx) < 0.6 or abs(dy) < 0.6) and r > 6:
                    px = (8, 10, 14, 235)                              # crosshair lines (hollow at the centre)
                elif abs(r - (rad - 3)) < 1.5:
                    px = (8, 10, 14, 255)                              # rim of the tube
                elif r < 2.2:
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
