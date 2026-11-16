#!/usr/bin/env python3
"""ValoBlock agent skins and the worn outfit, stdlib only, deterministic (no Pillow, no random module).

    python3 resource-pack/art/valoblock_skins.py

The single source is arena-plugin/src/main/resources/valoblock/agents/*.json (the colour plan of the VB-Agents
sheet: face rows and palette = hood and mask, colors = torso, trim = Realm trim, glow = the glow mark). Per agent:

    textures/entity/valoblock/skin_<id>.png                      64x64 player skin (classic arms). Bots: the mannequin
                                                                  profile texture override points at vampyuyu:entity/valoblock/skin_<id>.
    textures/entity/equipment/humanoid/valoblock/<id>.png        64x32 armour layer cut from the same skin (overlays merged):
    textures/entity/equipment/humanoid_leggings/valoblock/<id>.png  the worn outfit of the human players.
    equipment/valoblock_<id>.json                                the equipment asset the outfit items name (equippable asset id).

Pure art, no lettering. Output is byte for byte reproducible.
"""
import json
import pathlib
import sys
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import valoblock_items as vi  # noqa: E402

ASSETS = vi.PACK
AGENTS = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/agents"

CLEAR = (0, 0, 0, 0)
SHADE = {"top": 1.14, "front": 1.0, "right": 0.9, "left": 0.94, "back": 0.84, "bottom": 0.68}


def col(hex6):
    r, g, b = vi.rgb(hex6.lstrip("#"))
    return (r, g, b, 255)


def shade(c, f, noise=0):
    return tuple(max(0, min(255, round(v * f) + noise)) for v in c[:3]) + (c[3],)


def noise(agent_id, x, y):
    """A deterministic -5..+5 grain per pixel (crc32 of agent and position)."""
    return zlib.crc32(f"{agent_id}:{x}:{y}".encode()) % 11 - 5


class Skin:
    def __init__(self, agent_id, h=64):
        self.id = agent_id
        self.px = [[CLEAR] * 64 for _ in range(h)]

    def box(self, u, v, w, h, d, fn):
        """Paints a box's six faces. fn(face, x, y) -> colour or None (x, y inside the face; face in SHADE)."""
        rects = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
                 "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
        for face, (x0, y0, fw, fh) in rects.items():
            for y in range(fh):
                for x in range(fw):
                    c = fn(face, x, y)
                    if c is not None:
                        self.px[y0 + y][x0 + x] = c


def plan(a):
    c = a["colors"]
    pal = {k: col(v) for k, v in a["face"]["palette"].items()}
    rows = a["face"]["rows"]
    return {"torso": col(c["torso"]), "trim": col(c["trim"]), "glow": col(c["glow"]), "arm": col(c["arm"]), "legs": col(c["legs"]),
            "boots": col(c["boots"]), "hood": pal["h"], "skin": pal["s"], "pal": pal, "rows": rows}


def paint(a):
    p = plan(a)
    i = a["id"]
    s = Skin(i)

    def g(c, face, x, y, px, py, grain=True):
        return shade(c, SHADE[face], noise(i, px + x, py + y) if grain else 0)

    # head (0,0) and hood overlay (32,0)
    def head(face, x, y):
        if face == "front":
            return p["pal"][p["rows"][y][x]]
        if face == "top":
            return g(p["hood"], face, x, y, 8, 0)
        if face == "bottom":
            return g(p["skin"], face, x, y, 16, 0)
        if face in ("right", "left"):
            edge = p["rows"][y][0 if face == "right" else 7]
            return g(p["pal"][edge], face, x, y, 0 if face == "right" else 16, 8)
        # back of the head: hood, a trim stripe low and a 2x2 glow mark
        if y >= 6:
            return g(p["trim"], face, x, y, 24, 8)
        if 2 <= y <= 3 and 3 <= x <= 4:
            return g(p["glow"], face, x, y, 24, 8, False)
        return g(p["hood"], face, x, y, 24, 8)

    s.box(0, 0, 8, 8, 8, head)

    def hood(face, x, y):
        if face == "front":
            return shade(p["trim"], 1.0) if y == 0 else None  # the Realm trim at the hood's brim
        if face == "bottom":
            return None
        if face in ("right", "left") and y >= 6:
            return None
        if face == "back" and y >= 6:
            return None
        return g(p["hood"], face, x, y, 32 + x, 0 + y)

    s.box(32, 0, 8, 8, 8, lambda f, x, y: hood(f, x, y))

    # body (16,16): torso, collar trim, belt, the glow mark on chest and back
    def body(face, x, y):
        if face in ("top", "bottom"):
            return g(p["torso"], face, x, y, 20, 16)
        if y == 0:
            return g(p["trim"], face, x, y, 20, 20)  # collar
        if y == 9:
            return g(p["trim"], face, x, y, 20, 20)  # belt
        if face == "front" and ((x in (3, 4) and y in (3, 4, 5)) or (x in (2, 5) and y == 4)):
            return g(p["glow"], face, x, y, 20, 20, False)
        if face == "front" and x in (3, 4) and y == 9:
            return g(p["glow"], face, x, y, 20, 20, False)  # buckle
        if face == "back" and x in (3, 4) and y in range(2, 8):
            return g(p["trim"], face, x, y, 32, 20)  # spine stripe
        return g(p["torso"], face, x, y, 20, 20)

    s.box(16, 16, 8, 12, 4, body)
    # jacket (16,32): a Realm-trim hem
    s.box(16, 32, 8, 12, 4, lambda f, x, y: g(p["trim"], f, x, y, 0, 0, False) if f in ("front", "back", "left", "right") and y >= 11 else None)

    def arm(face, x, y):
        if face in ("top", "bottom"):
            return g(p["torso"], face, x, y, 44, 16)
        if y <= 1:
            return g(p["torso"], face, x, y, 40, 20)  # shoulder pad
        if y == 2:
            return g(p["trim"], face, x, y, 40, 20)
        if y == 9:
            return g(p["trim"], face, x, y, 40, 20)  # cuff
        if y >= 10:
            return g(p["boots"], face, x, y, 40, 20)  # glove
        return g(p["arm"], face, x, y, 40, 20)

    s.box(40, 16, 4, 12, 4, arm)  # right arm
    s.box(32, 48, 4, 12, 4, arm)  # left arm
    s.box(40, 32, 4, 12, 4, lambda f, x, y: shade(p["trim"], SHADE[f]) if f in ("front", "back", "left", "right") and y == 11 else None)
    s.box(48, 48, 4, 12, 4, lambda f, x, y: shade(p["trim"], SHADE[f]) if f in ("front", "back", "left", "right") and y == 11 else None)

    def leg(face, x, y):
        if face in ("top", "bottom"):
            return g(p["legs"], face, x, y, 4, 16)
        if y == 7:
            return g(p["trim"], face, x, y, 0, 16)  # boot top
        if y >= 8:
            return g(p["boots"], face, x, y, 0, 16)
        if face == "front" and x == 1 and y == 3:
            return g(p["glow"], face, x, y, 0, 16, False)  # knee mark
        return g(p["legs"], face, x, y, 0, 16)

    s.box(0, 16, 4, 12, 4, leg)  # right leg
    s.box(16, 48, 4, 12, 4, leg)  # left leg
    return s


def over(dst, src):
    """Alpha-composites one pixel (opaque or clear only: that is all the overlays use)."""
    return src if src[3] else dst


def armour_layer(skin):
    """64x32 humanoid layer: the base skin's top half with the overlays merged in (hood, jacket hem, sleeves, pants)."""
    out = [row[:] for row in skin.px[:32]]
    for y in range(16):  # hat -> head
        for x in range(32):
            out[y][x] = over(out[y][x], skin.px[y][x + 32])
    for (ox, oy, w, h) in ((16, 16, 16, 16), (40, 16, 16, 16), (0, 16, 16, 16)):  # body, arm, leg: overlay sits 16 rows lower
        for y in range(h):
            for x in range(w):
                out[oy + y][ox + x] = over(out[oy + y][ox + x], skin.px[oy + 16 + y][ox + x])
    return out


def agents():
    return [json.loads(f.read_text()) for f in sorted(AGENTS.glob("*.json")) if f.name != "index.json"]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def main():
    n = 0
    for a in agents():
        i = a["id"]
        s = paint(a)
        write(ASSETS / f"textures/entity/valoblock/skin_{i}.png", vi.png(s.px))
        layer = vi.png(armour_layer(s))
        write(ASSETS / f"textures/entity/equipment/humanoid/valoblock/{i}.png", layer)
        write(ASSETS / f"textures/entity/equipment/humanoid_leggings/valoblock/{i}.png", layer)
        eq = {"layers": {"humanoid": [{"texture": f"vampyuyu:valoblock/{i}"}], "humanoid_leggings": [{"texture": f"vampyuyu:valoblock/{i}"}]}}
        write(ASSETS / f"equipment/valoblock_{i}.json", (json.dumps(eq, indent=1) + "\n").encode())
        n += 1
    print(f"wrote {n} skins and outfits under {ASSETS}")


if __name__ == "__main__":
    main()
