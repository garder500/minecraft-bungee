"""Offline preview of the boss item models (front, 3/4, side, back), z-buffered and textured, stdlib only.

    python3 resource-pack/art/preview_boss_skins.py out.png magma_warden voidwyrm ...

About 12 s a boss instead of booting the game; it matches the in-game render. Run boss_skins.py first.
"""
import json, math, pathlib, struct, sys, zlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import boss_skins as bs
PACK = bs.PACK

def read_png(path):
    d = path.read_bytes(); pos = 8; idat = b""; w = h = 0
    while pos < len(d):
        ln = struct.unpack(">I", d[pos:pos+4])[0]; tag = d[pos+4:pos+8]; body = d[pos+8:pos+8+ln]
        if tag == b"IHDR": w, h = struct.unpack(">II", body[:8])
        if tag == b"IDAT": idat += body
        pos += 12 + ln
    raw = zlib.decompress(idat); stride = w * 4; px = []; prev = bytearray(stride); i = 0
    for y in range(h):
        f = raw[i]; i += 1; line = bytearray(raw[i:i+stride]); i += stride
        for x in range(stride):
            a = line[x-4] if x >= 4 else 0; b = prev[x]; c = prev[x-4] if x >= 4 else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        px.append([tuple(line[k:k+4]) for k in range(0, stride, 4)]); prev = line
    return px, w, h

def rot(p, axis, ang, o):
    a = math.radians(ang); x, y, z = p[0]-o[0], p[1]-o[1], p[2]-o[2]; c, s = math.cos(a), math.sin(a)
    if axis == "x": y, z = y*c - z*s, y*s + z*c
    elif axis == "y": x, z = x*c + z*s, -x*s + z*c
    else: x, y = x*c - y*s, x*s + y*c
    return (x+o[0], y+o[1], z+o[2])

def render(boss, yaw, S=10, W=520, H=520):
    model = json.loads((PACK/"models/item/boss/skin"/f"{boss}.json").read_text())
    tex, tw, th = read_png(PACK/"textures/item/boss/skin"/f"{boss}.png")
    img = [[(150, 180, 215)] * W for _ in range(H)]; zb = [[1e9] * W for _ in range(H)]
    cy = math.cos(math.radians(yaw)); sy = math.sin(math.radians(yaw))
    light = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
    for el in model["elements"]:
        f, t = el["from"], el["to"]; r = el.get("rotation")
        def P(x, y, z):
            p = (x, y, z)
            if r: p = rot(p, r["axis"], r["angle"], r["origin"])
            x, y, z = p[0]-8, p[1], p[2]-8   # view: camera looks toward +z from -z (the front is -z)
            vx = x*cy - z*sy; vz = x*sy + z*cy
            return (W/2 + vx*S, H - 40 - y*S, vz)
        for face, fd in el["faces"].items():
            u0, v0, u1, v1 = [c * tw / 16 for c in fd["uv"]]
            # face param (a,b) in [0,1]^2 -> 3D point; a along u (left->right as seen from outside), b along v (top->bottom)
            if face == "north": fn = lambda a, b: (t[0]-(t[0]-f[0])*a, t[1]-(t[1]-f[1])*b, f[2])
            elif face == "south": fn = lambda a, b: (f[0]+(t[0]-f[0])*a, t[1]-(t[1]-f[1])*b, t[2])
            elif face == "west": fn = lambda a, b: (f[0], t[1]-(t[1]-f[1])*b, f[2]+(t[2]-f[2])*a)
            elif face == "east": fn = lambda a, b: (t[0], t[1]-(t[1]-f[1])*b, t[2]-(t[2]-f[2])*a)
            elif face == "up": fn = lambda a, b: (f[0]+(t[0]-f[0])*a, t[1], f[2]+(t[2]-f[2])*b)
            else: fn = lambda a, b: (f[0]+(t[0]-f[0])*a, f[1], t[2]-(t[2]-f[2])*b)
            ext = max(abs(t[i]-f[i]) for i in range(3)); n = max(2, int(ext * S * 1.6))
            L = light[face]
            for i in range(n+1):
                for j in range(n+1):
                    a, b = i/n, j/n
                    sx, sy_, z = P(*fn(a, b)); ix, iy = int(sx), int(sy_)
                    if 0 <= ix < W and 0 <= iy < H and z < zb[iy][ix]:
                        tu = min(tw-1, max(0, int(u0 + (u1-u0)*a - 1e-6))); tv = min(th-1, max(0, int(v0 + (v1-v0)*b - 1e-6)))
                        c = tex[tv][tu]
                        zb[iy][ix] = z; img[iy][ix] = tuple(int(v*L) for v in c[:3])
    return img

if __name__ == "__main__":
    out = pathlib.Path(sys.argv[1]); bosses = sys.argv[2:]
    rows = []
    for b in bosses:
        views = [render(b, y) for y in (0, 35, 90, 180)]
        rows.append([sum((v[r] for v in views), []) for r in range(len(views[0]))])
    allrows = [r for rr in rows for r in rr]
    out.write_bytes(bs.png([[(c[0], c[1], c[2], 255) for c in row] for row in allrows]))
    print("wrote", out)
