#!/usr/bin/env python3
"""Read Blockbench projects (.bbmodel, format "Java Block/Item") and turn them into Java model elements. Stdlib only.

The Skyblock 3D assets (helmets, worn wings, bosses, items, blocks) are modelled and painted in Blockbench and saved
under resource-pack/art/blockbench/<family>/<asset>.bbmodel: one embedded palette texture, cubes whose faces point at
swatches of it, every element shade: false (the light is painted into the swatches: up, side, down, east/west), and
outliner groups that the converters may read (a group name tags its cubes). The converter scripts (helmet_models.py,
wings_art.py, ...) read them with Source and write the pack files; edit the shapes in Blockbench, never in Python.

Rules the checks enforce (scripts/test_blockbench_models.py): every face textured, every shown texel above 25 % grey
(the old "renders black" bug), face uvs inside the texture, one rotation axis per element, coordinates within -16..32, and no two overlapping boxes
with the same rotation sharing a face plane (they would flicker).
"""
import base64
import json
import math
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parent / "blockbench"
FACES = ("north", "east", "south", "west", "up", "down")
MIN_LUM = 0.25 * 255


def read_png(data):
    """PNG bytes (8 bit RGB/RGBA, any filter, as Blockbench saves them) to rows of RGBA tuples."""
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


def png(rows):
    """RGBA rows to PNG bytes (fixed compression: reproducible)."""
    h, w = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


class Source:
    """One .bbmodel: its cubes (with the name of their top-level group) and its first texture."""

    def __init__(self, path):
        path = pathlib.Path(path)
        data = json.loads(path.read_text(encoding="utf-8"))
        self.path, self.name = path, path.stem
        self.res = data["resolution"]["width"], data["resolution"]["height"]
        tex = data["textures"][0]["source"]
        self.png = base64.b64decode(tex.split(",", 1)[1])
        self.tex = read_png(self.png)
        # every texture by name (a second one is a twin painted on the same layout, e.g. a cable's lit copper)
        self.pngs = {t["name"].rsplit(".", 1)[0]: base64.b64decode(t["source"].split(",", 1)[1]) for t in data["textures"]}
        names = {g["uuid"]: g["name"] for g in data.get("groups", [])}   # Blockbench 5 keeps group names apart
        group_of, self.bone_of = {}, {}
        # the Generic Model format (a rigged boss, bbrig.py) centres the model on x = z = 0: back to the Java frame
        self.free = data.get("meta", {}).get("model_format") == "free"
        self.shift = (8.0, 0.0, 8.0) if self.free else (0.0, 0.0, 0.0)

        def walk(nodes, tag, bone):
            for node in nodes:
                if isinstance(node, str):
                    group_of[node] = tag
                    self.bone_of[node] = bone
                else:
                    name = node.get("name") or names.get(node["uuid"], "")
                    walk(node.get("children", []), tag or name, name)
        walk(data["outliner"], None, None)
        self.elements = [(group_of.get(el["uuid"]) or "", el) for el in data["elements"]
                         if el.get("type", "cube") == "cube" and el.get("export") is not False]

    def groups(self):
        return sorted({tag for tag, _ in self.elements})

    def java(self, texture="0", only=None, skip=(), offset=(0, 0, 0), mirror_x=False, bone=None):
        """Elements in Java model form (uv 0..16). only/skip filter by group name; offset moves every box;
        mirror_x reflects the model across x = 8 (z and y rotations reversed, east and west faces swapped)."""
        su, sv = 16 / self.res[0], 16 / self.res[1]
        out = []
        offset = [offset[i] + self.shift[i] for i in range(3)]
        for tag, el in self.elements:
            if (only is not None and tag not in only) or tag in skip:
                continue
            if bone is not None and self.bone_of.get(el["uuid"]) != bone:
                continue
            a = [el["from"][i] + offset[i] for i in range(3)]
            b = [el["to"][i] + offset[i] for i in range(3)]
            o = [el.get("origin", [8, 8, 8])[i] + offset[i] for i in range(3)]
            rot = list(el.get("rotation", [0, 0, 0]))
            faces = dict(el["faces"])
            if mirror_x:
                a[0], b[0] = 16 - b[0], 16 - a[0]
                o[0] = 16 - o[0]
                rot = [rot[0], -rot[1], -rot[2]]
                faces["east"], faces["west"] = el["faces"].get("west"), el["faces"].get("east")
            j = {"from": [round(v, 4) for v in a], "to": [round(v, 4) for v in b], "shade": False, "faces": {}}
            if el.get("light_emission"):
                j["light_emission"] = el["light_emission"]
            axes = [(i, ang) for i, ang in enumerate(rot) if ang]
            if len(axes) > 1:
                raise ValueError(f"{self.name}: {el['name']} turns about more than one axis")
            if axes:
                i, ang = axes[0]
                j["rotation"] = {"angle": round(ang, 4), "axis": "xyz"[i], "origin": [round(v, 4) for v in o]}
            for f in FACES:
                face = faces.get(f)
                if not face or face.get("texture") is None:
                    continue
                u0, v0, u1, v1 = face["uv"]
                if mirror_x:          # the reflected face shows its texture flipped left to right
                    u0, u1 = u1, u0
                jf = {"uv": [round(u0 * su, 4), round(v0 * sv, 4), round(u1 * su, 4), round(v1 * sv, 4)],
                      "texture": "#" + texture}
                if face.get("rotation"):
                    jf["rotation"] = face["rotation"]
                if isinstance(face.get("tint"), int) and face["tint"] >= 0:
                    jf["tintindex"] = face["tint"]   # recoloured by the item's tint (the boss VFX primitives)
                j["faces"][f] = jf
            out.append(j)
        return out


GLOW_ALPHA = 251   # the alpha that marks a glowing texel for the item core shader (fx_shaders.py, item.fsh)
HALO_ALPHA = 96    # the alpha of a halo shell texel: translucent haze the item shader makes breathe


def glow_png(src, everything=False):
    """The source's texture with every texel of a glowing face (a cube with light_emission) marked by alpha GLOW_ALPHA,
    or every opaque texel when {everything} (the VFX primitives glow whole). The item core shader makes the marked texels
    pulse and burn white-hot; to the eye the alpha is opaque. Deterministic, the source is not touched."""
    rows = [list(r) for r in src.tex]
    h, w = len(rows), len(rows[0])
    su, sv = w / src.res[0], h / src.res[1]
    rects = []
    if not everything:
        for _, el in src.elements:
            if not el.get("light_emission"):
                continue
            for face in el["faces"].values():
                if face and face.get("texture") is not None:
                    u0, v0, u1, v1 = face["uv"]
                    rects.append((int(min(u0, u1) * su), int(min(v0, v1) * sv),
                                  max(int(min(u0, u1) * su) + 1, math.ceil(max(u0, u1) * su - 1e-6)),
                                  max(int(min(v0, v1) * sv) + 1, math.ceil(max(v0, v1) * sv - 1e-6))))
    for y in range(h):
        for x in range(w):
            r, g, b, a = rows[y][x]
            if a < 200:
                continue
            if everything or any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in rects):
                rows[y][x] = (r, g, b, GLOW_ALPHA)
    return png(rows)


def halo_png(src):
    """The source's texture as a halo shell: every opaque texel lifted toward white and given alpha HALO_ALPHA (a
    translucent haze drawn round the effect by the item shader). Deterministic."""
    rows = []
    for row in src.tex:
        rows.append([(min(255, r + (255 - r) // 3), min(255, g + (255 - g) // 3), min(255, b + (255 - b) // 3),
                      HALO_ALPHA if a >= 200 else 0) for r, g, b, a in row])
    return png(rows)


def states(src):
    """The element sets of a source that are shown together: a source with groups p0, p1, ... (the draw stages of a
    bow) is one state per such group plus the shared groups; any other source is one state."""
    groups = src.groups()
    stages = sorted(g for g in groups if len(g) > 1 and g[0] == "p" and g[1:].isdigit())
    if not stages:
        return [(src.name, src.java())]
    shared = [g for g in groups if g not in stages]
    return [(f"{src.name}:{g}", src.java(only=shared + [g])) for g in stages]


def corners(el):
    """The eight corners of a Java element after its rotation (rescale ignored)."""
    a, b = el["from"], el["to"]
    pts = [(x, y, z) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
    r = el.get("rotation")
    if not r:
        return pts
    ang, ax, o = math.radians(r["angle"]), "xyz".index(r["axis"]), r["origin"]
    i, k = [(1, 2), (2, 0), (0, 1)][ax]          # the plane turned by a rotation about that axis
    c, s = math.cos(ang), math.sin(ang)
    out = []
    for p in pts:
        p = list(p)
        di, dk = p[i] - o[i], p[k] - o[k]
        p[i], p[k] = o[i] + di * c - dk * s, o[k] + di * s + dk * c
        out.append(tuple(p))
    return out


def problems(label, elements, tex):
    """The checks every Blockbench-sourced model passes (see the module docstring)."""
    out = []
    h, w = len(tex), len(tex[0])
    key = lambda e: json.dumps(e.get("rotation"), sort_keys=True)  # noqa: E731
    for j, q in enumerate(elements):
        if set(q["faces"]) != set(FACES):
            out.append(f"{label}: element {q['from']}-{q['to']} has untextured faces")
        if any(not -16 <= v <= 32 for v in q["from"] + q["to"]):
            out.append(f"{label}: element {q['from']}-{q['to']} leaves -16..32")
        for f in q["faces"].values():
            u0, v0, u1, v1 = f["uv"]
            if min(u0, v0, u1, v1) < -1e-6 or max(u0, v0, u1, v1) > 16 + 1e-6:
                # the client refuses the whole model once its texture has translucent texels (the glow marker)
                out.append(f"{label}: element {q['from']}-{q['to']} has a face whose uv leaves the texture")
            for y in range(int(min(v0, v1) * h / 16), max(int(min(v0, v1) * h / 16) + 1, math.ceil(max(v0, v1) * h / 16 - 1e-6))):
                for x in range(int(min(u0, u1) * w / 16), max(int(min(u0, u1) * w / 16) + 1, math.ceil(max(u0, u1) * w / 16 - 1e-6))):
                    r, g, b, a = tex[min(y, h - 1)][min(x, w - 1)]
                    if a and 0.2126 * r + 0.7152 * g + 0.0722 * b < MIN_LUM:
                        out.append(f"{label}: texel {x},{y} shown on a face is darker than 25% grey")
                        break
                else:
                    continue
                break
        for p in elements[:j]:
            if key(p) != key(q):
                continue
            if not all(p["from"][i] < q["to"][i] - 1e-6 and q["from"][i] < p["to"][i] - 1e-6 for i in range(3)):
                continue
            for i in range(3):
                if abs(p["from"][i] - q["from"][i]) < 1e-4 or abs(p["to"][i] - q["to"][i]) < 1e-4:
                    out.append(f"{label}: boxes {p['from']}-{p['to']} and {q['from']}-{q['to']} share a face plane on {'xyz'[i]}")
                    break
    return sorted(set(out))
