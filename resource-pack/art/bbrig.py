#!/usr/bin/env python3
"""Rigged, animated Blockbench models (format "Generic Model") baked for the plugin. Stdlib only, deterministic.

A rigged boss is a Blockbench project in the Generic Model format (bosses/<id>.bbmodel): its outliner groups are the bones
(nested groups are child bones, the group origin is the joint), the cubes directly inside a group belong to that bone, and
its animations (Animate mode) move the bones. Blockbench's rules, measured on the app (blockbench/rig_fixture.json, checked
by scripts/test_bbrig.py): a bone's world transform is parent * T(origin + position) * Rz * Ry * Rx * S(scale) * T(-origin),
rotations in degrees added to a zero rest pose, positions in model pixels, keyframes interpolated per channel. The Generic
format centres the model on x = z = 0, so every coordinate is shifted by (8, 0, 8) to the Java item model frame.

What is refused (the bake would not match the app): a bone with a rest rotation, a non-numeric (Molang) keyframe, an
interpolation other than linear or step, a non-uniform scale.

bake() samples every animation every STEP ticks into, per bone, the affine that maps the Java model space at rest to the
animated pose, stored as a translation (model units, applied after the rotation about the model centre 8, 8, 8), a rotation
quaternion and a uniform scale: the plugin turns it into an item display transformation (BossRig.java).
"""
import json
import math

TICKS_PER_SECOND = 20
STEP = 2                     # ticks between two baked frames (the display interpolates in between)
JAVA_SHIFT = (8.0, 0.0, 8.0)
CENTRE = (8.0, 8.0, 8.0)


# ---------------------------------------------------------------------------------------------------- 4x4 matrices
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(v):
    return [[1, 0, 0, v[0]], [0, 1, 0, v[1]], [0, 0, 1, v[2]], [0, 0, 0, 1]]


def scale(k):
    return [[k, 0, 0, 0], [0, k, 0, 0], [0, 0, k, 0], [0, 0, 0, 1]]


def rot_xyz_deg(r):
    """Rz * Ry * Rx (THREE.js Euler order "ZYX", the Generic format's), angles in degrees."""
    x, y, z = (math.radians(v) for v in r)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    ry = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    rz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return mat_mul(rz, mat_mul(ry, rx))


def quat_of(m, k):
    """Unit quaternion (x, y, z, w) of the rotation part of m whose uniform scale is k."""
    r = [[m[i][j] / k for j in range(3)] for i in range(3)]
    tr = r[0][0] + r[1][1] + r[2][2]
    if tr > 0:
        s = math.sqrt(tr + 1.0) * 2
        w, x, y, z = 0.25 * s, (r[2][1] - r[1][2]) / s, (r[0][2] - r[2][0]) / s, (r[1][0] - r[0][1]) / s
    elif r[0][0] > r[1][1] and r[0][0] > r[2][2]:
        s = math.sqrt(1.0 + r[0][0] - r[1][1] - r[2][2]) * 2
        w, x, y, z = (r[2][1] - r[1][2]) / s, 0.25 * s, (r[0][1] + r[1][0]) / s, (r[0][2] + r[2][0]) / s
    elif r[1][1] > r[2][2]:
        s = math.sqrt(1.0 + r[1][1] - r[0][0] - r[2][2]) * 2
        w, x, y, z = (r[0][2] - r[2][0]) / s, (r[0][1] + r[1][0]) / s, 0.25 * s, (r[1][2] + r[2][1]) / s
    else:
        s = math.sqrt(1.0 + r[2][2] - r[0][0] - r[1][1]) * 2
        w, x, y, z = (r[1][0] - r[0][1]) / s, (r[0][2] + r[2][0]) / s, (r[1][2] + r[2][1]) / s, 0.25 * s
    n = math.sqrt(x * x + y * y + z * z + w * w)
    q = [x / n, y / n, z / n, w / n]
    return [-v for v in q] if q[3] < 0 else q


# ---------------------------------------------------------------------------------------------------- the rig
class Rig:
    """Bones and animations of a Generic-format .bbmodel (already parsed JSON)."""

    def __init__(self, data, name="model"):
        self.name = name
        if data.get("meta", {}).get("model_format") != "free":
            raise ValueError(f"{name}: a rig must be in the Generic Model format (free), not {data.get('meta', {}).get('model_format')}")
        groups = {g["uuid"]: g for g in data.get("groups", [])}
        self.bones, self.parent, self.origin, self.cubes = [], {}, {}, {}

        def walk(nodes, parent):
            for node in nodes:
                if isinstance(node, str):
                    if parent is None:
                        raise ValueError(f"{name}: a cube outside every bone (put it in a group)")
                    self.cubes.setdefault(parent, []).append(node)
                    continue
                g = groups.get(node["uuid"], node)
                bone = g["name"]
                if bone in self.origin:
                    raise ValueError(f"{name}: two bones named {bone}")
                if any(abs(v) > 1e-6 for v in g.get("rotation", [0, 0, 0])):
                    raise ValueError(f"{name}: bone {bone} has a rest rotation (rotate its cubes instead)")
                self.bones.append(bone)
                self.parent[bone] = parent
                self.origin[bone] = list(g.get("origin", [0, 0, 0]))
                self.uuid_bone = getattr(self, "uuid_bone", {})
                self.uuid_bone[node["uuid"]] = bone
                walk(node.get("children", []), bone)
        walk(data["outliner"], None)
        self.animations = {}
        for anim in data.get("animations", []):
            channels = {}
            for uuid, animator in (anim.get("animators") or {}).items():
                bone = self.uuid_bone.get(uuid)
                if bone is None:
                    continue
                for kf in animator.get("keyframes", []):
                    ch = kf["channel"]
                    if ch not in ("rotation", "position", "scale"):
                        continue
                    interp = kf.get("interpolation", "linear")
                    if interp not in ("linear", "step"):
                        raise ValueError(f"{name}/{anim['name']}/{bone}: interpolation {interp} (use linear or step)")
                    p = kf["data_points"][0]
                    try:
                        v = [float(p[a]) for a in "xyz"]
                    except (TypeError, ValueError):
                        raise ValueError(f"{name}/{anim['name']}/{bone}: non-numeric keyframe {p}") from None
                    if ch == "scale" and not (abs(v[0] - v[1]) < 1e-6 and abs(v[1] - v[2]) < 1e-6):
                        raise ValueError(f"{name}/{anim['name']}/{bone}: non-uniform scale {v}")
                    channels.setdefault((bone, ch), []).append((float(kf["time"]), v, interp))
            for key in channels:
                channels[key].sort(key=lambda k: k[0])
            self.animations[anim["name"]] = {"length": float(anim.get("length", 0)), "loop": anim.get("loop", "once"),
                                             "channels": channels}

    def value(self, anim, bone, channel, t):
        keys = self.animations[anim]["channels"].get((bone, channel))
        default = [1.0, 1.0, 1.0] if channel == "scale" else [0.0, 0.0, 0.0]
        if not keys:
            return default
        before = [k for k in keys if k[0] <= t + 1e-9]
        after = [k for k in keys if k[0] > t + 1e-9]
        if not before:
            return list(after[0][1])
        if not after:
            return list(before[-1][1])
        a, b = before[-1], after[0]
        if a[2] == "step":
            return list(a[1])
        f = (t - a[0]) / (b[0] - a[0])
        return [a[1][i] + (b[1][i] - a[1][i]) * f for i in range(3)]

    def matrix(self, anim, bone, t):
        """World matrix of a bone in the Generic (Blockbench) frame at time t (seconds)."""
        o = self.origin[bone]
        pos = self.value(anim, bone, "position", t) if anim else [0, 0, 0]
        rot = self.value(anim, bone, "rotation", t) if anim else [0, 0, 0]
        k = self.value(anim, bone, "scale", t)[0] if anim else 1.0
        local = mat_mul(translate([o[i] + pos[i] for i in range(3)]), mat_mul(rot_xyz_deg(rot), mat_mul(scale(k), translate([-v for v in o]))))
        parent = self.parent[bone]
        return mat_mul(self.matrix(anim, parent, t), local) if parent else local

    def java_frame(self, anim, bone, t):
        """[tx, ty, tz, qx, qy, qz, qw, k] of the bone in the Java model frame: a model point v (units) goes to
        k * R * (v - C) + C + (tx, ty, tz), C = the model centre (8, 8, 8)."""
        m = mat_mul(translate(JAVA_SHIFT), mat_mul(self.matrix(anim, bone, t), translate([-v for v in JAVA_SHIFT])))
        k = math.sqrt(sum(m[i][0] ** 2 for i in range(3)))
        q = quat_of(m, k)
        # m(v) = A v + b; A C + b - C is the offset after rotating about C
        t3 = [sum(m[i][j] * CENTRE[j] for j in range(3)) + m[i][3] - CENTRE[i] for i in range(3)]
        return [round(v, 4) for v in t3] + [round(v, 6) for v in q] + [round(k, 5)]

    def bake(self, model_of):
        """The plugin's rig JSON. model_of(bone) -> item model id."""
        anims = {}
        for name, a in sorted(self.animations.items()):
            ticks = max(STEP, int(round(a["length"] * TICKS_PER_SECOND)))
            frames = []
            for tick in range(0, ticks + 1, STEP):
                t = min(tick / TICKS_PER_SECOND, a["length"])
                frames.append([self.java_frame(name, bone, t) for bone in self.bones])
            anims[name] = {"loop": a["loop"] == "loop", "hold": a["loop"] == "hold", "ticks": ticks, "step": STEP, "frames": frames}
        return {"bones": [{"name": b, "parent": self.parent[b], "model": model_of(b)} for b in self.bones],
                "rest": [self.java_frame(None, b, 0) for b in self.bones], "animations": anims}


def load(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return Rig(data, str(path)), data
