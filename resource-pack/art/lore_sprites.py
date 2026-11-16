#!/usr/bin/env python3
"""Hand-drawn 16x16 sprites of the guardian cores, keys, realm materials, passive generators and compression blocks.

The pixel maps are ported from the approved design (Items-Guardians, Items-Realm and Items-Flux): rows of characters
resolved through a palette, base shapes merged with per-row overlays ('_' in an overlay leaves the base pixel).
Stdlib only, deterministic. The owning scripts call draw(): boss_items.py (boss/*), quarry_items.py (machine/* and
ore/*), marsh_items.py (the Putrid Marshes items). Ids listed in imported.txt (cinderheart, heartwood,
stasis_crystal) keep their imported sprite: the callers skip them, they are kept here only for the design check.

    python3 resource-pack/art/lore_sprites.py [--sheet F]   # only a contact sheet (the owning scripts write the PNGs)
"""
import pathlib
import struct
import sys
import zlib

OUTLINE = "#140f18"


def merge(base, overlay):
    out = list(base)
    for y, text in (overlay or {}).items():
        row = list((out[y] if y < len(out) else "").ljust(16, "."))
        for x, ch in enumerate(text[:16]):
            if ch != "_":
                row[x] = ch
        out[y] = "".join(row)
    return out


def rgb(hexcolor):
    if hexcolor == "transparent":
        return None
    # RGBA: boss_items.Canvas.png() writes colour type 6, four bytes per pixel.
    return tuple(int(hexcolor[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def paint(rows, pal, base_pal):
    full = dict(base_pal)
    full.update(pal)
    full["."] = "transparent"
    cells = []
    for y in range(16):
        row = (rows[y] if y < len(rows) else "").ljust(16, ".")
        for x in range(16):
            if row[x] not in full:
                raise ValueError(f"unknown palette char {row[x]!r} at {x},{y}")
            cells.append(rgb(full[row[x]]))
    return cells


# ------------------------------------------------------------------ Guardians (boss/*)
_G_BASE = {"o": OUTLINE, "m": "#2a2632", "n": "#6e6878"}
_G = {
    "ashes": dict(a="#120a16", b="#2a1a2e", c="#5a2a2a", d="#c8461e", e="#ff8a2a", f="#ffd36a"),
    "grove": dict(a="#2c3a1e", b="#4f6b2a", c="#8fb84a", d="#d6f08a", e="#f1e7c9"),
    "frost": dict(a="#1f3557", b="#3b6fa8", c="#7fc8e8", d="#e6f7ff", e="#b6a8ff"),
    "citadel": dict(a="#5a3a12", b="#a8742a", c="#f2c14e", d="#fff1b0", e="#3fb6a8"),
    "marsh": dict(a="#2a2a1a", b="#4e5a22", c="#8a9a2e", d="#c9d36a", e="#b04aa0"),
    "necro": dict(a="#241f3a", b="#4a4570", c="#9c95c8", d="#e8e4ff", e="#6affd8"),
    "storm": dict(a="#1e2440", b="#3a4a8a", c="#7a8ee0", d="#f0f4ff", e="#ffe94a"),
    "astral": dict(a="#0e0a1a", b="#3a1a6a", c="#8a4aff", d="#e0c8ff", e="#ff4a8a"),
}
_ORB = [
    "................", "................", "......oooo......", "....oocddcoo....",
    "...ocddcbbbco...", "...ocdcbbbbbo...", "..ocbbbbbbbbao..", "..obbbbbbbbbao..",
    "..obbbbbbbbaao..", "..obbbbbbbaaao..", "...obbbbbaaao...", "...oabbaaaaao...",
    "....ooaaaaoo....", "......oooo......", "................", "................",
]
_CAGE = {1: "_______nn", 3: "___n________n", 7: "_n____________n", 8: "_m____________m",
         12: "___n________n", 14: "_______mm"}
_KEY = [
    "................", "..oooo..........", ".ocddco.........", "ocoeeoco........",
    "oboeeocbo.......", ".obbcbbo........", "..ooobbo........", "......obo.......",
    ".......obo......", "........obo.....", ".........obo....", "........oobbo...",
    ".......ocboobo..", "........oo..obo.", ".............oo.", "................",
]


def _core(motif):
    return merge(merge(_ORB, _CAGE), motif)


_GUARDIANS = {
    "lava_core": ("ashes", _core({6: "_________e", 7: "________ef", 8: "_______ee", 9: "______fe", 10: "_____ee"}), {}),
    "mycelial_core": ("grove", _core({1: "____e", 6: "_______e___e", 7: "______ede", 8: "_______d", 9: "______ddd", 10: "_____d___"}), {}),
    "frost_core": ("frost", _core({7: "______eee", 8: "_____eaade", 9: "______eee"}), {}),
    "temporal_core": ("citadel", _core({6: "_____eeeee", 7: "______eee", 8: "_______d", 9: "______eee", 10: "_____eeeee"}), {}),
    "mire_core": ("marsh", _core({5: "______e", 7: "_________e", 8: "_____ed", 9: "_______ee", 10: "_______ee"}), {}),
    "judge_core": ("necro", _core({6: "_______e", 7: "______ee", 8: "______ede", 9: "_____eaaae", 10: "______eee"}), {}),
    "thunder_core": ("storm", _core({6: "________ee", 7: "_______ee", 8: "______eeee", 9: "_______ee", 10: "______ee"}), {}),
    "pharaoh_scarab": ("citadel", [
        ".o....o.........", "..o..o..........", "..oooo..........", ".occcco.........", "ocdeaedo........",
        "ocdeaedco.......", "obceaecbo.......", ".obbabbo........", "..ooooobo.......", "........obo.....",
        ".........obo....", "........oobbo...", ".......ocboobo..", "........oo..obo.", ".............oo.",
        "................"], {}),
    "mire_cyst": ("marsh", [
        "................", "......oo........", ".....obbo.......", "....oobboo......", "...occddcco.....",
        "..ocdddccbco....", ".ocddcccbbbco...", ".occcceccbbbo...", "occcceaecbbbbo..", "occcbeaaebbbao..",
        "obccbbeebbbaao..", ".obbbbbbbbaao...", ".oabbbbbbaaao...", "..ooaaaaaaoo....", "....oooooo......",
        "................"], {}),
    "judge_seal": ("necro", [
        "................", "......oooo......", "...oooccccooo...", "..obccddddccbo..", ".obcddddeddddco.",
        ".obcdddeedddcbo.", "obccddeeedddccbo", "obcdddeaeeddccbo", "obcddeeaaeedccbo", "obcddeaaaeedcbbo",
        ".obcddeeeedccbo.", ".obbcddddddcbbo.", "..obbccccccbbo..", "...ooobbbbooo...", "....o.oooo.o....",
        "................"], {}),
    "tempest_key": ("storm", merge(_KEY, {3: "___ee", 4: "___ee"}), {}),
    "interpulse_key": ("astral", merge(_KEY, {0: "_o", 3: "___ea", 4: "___ae", 13: "___________e", 14: "__________e"}), {}),
    "mire_ichor": ("marsh", [
        "................", "......oooo......", "......okko......", "......oggo......", "......oggo......",
        ".....oggggo.....", "....oggggggo....", "...ogcccccdgo...", "...occeccccco...", "...obbcbbebbo...",
        "...obebbbbbbo...", "...oabbbebbao...", "....oaaaaaao....", ".....oooooo.....", "................",
        "................"], dict(k="#7a522e", g="#cfe8f0")),
    "pharaoh_linen": ("citadel", [
        "................", "................", "...ooooooooo....", "..occdcdcdcdco..", ".ocdbcbcbcbcbdo.",
        ".obeeeeeeeeeefo.", ".ocbcbcbcbcbcbo.", ".obcbcbcbcbcbao.", "..oaaaaaaaaaaao.", "...oooooooooocdo",
        "............ocdo", "...........ocdo.", "..........ocdo..", "..........ooo...", "................",
        "................"], dict(a="#8a7a5a", b="#c9b88f", c="#e8dcc0", d="#fff8e6", e="#f2c14e", f="#3fb6a8")),
}

# ------------------------------------------------------------------ Realm materials (ore/*)
_R_BASE = {"o": OUTLINE}
_R = {
    "ashes": dict(a="#120a16", b="#2a1a2e", c="#4a2f52", d="#8a6a9a", e="#ff8a2a", f="#ffd36a"),
    "grove": dict(a="#3a2414", b="#6a4422", c="#a87444", d="#d8a86a", e="#d6f08a", g="#8fb84a"),
    "frost": dict(a="#1f3557", b="#3b6fa8", c="#7fc8e8", d="#e6f7ff", e="#b6a8ff"),
    "citadel": dict(a="#5a3a12", b="#a8742a", c="#f2c14e", d="#fff1b0", e="#3fb6a8", g="#e8f4f0"),
    "marsh": dict(a="#5a3a12", b="#8a6a1a", c="#b0a02e", d="#e0d870", e="#b04aa0"),
    "necro": dict(a="#3a3550", b="#6a6488", c="#a8a2c4", d="#e8e4ff", e="#6affd8"),
    "storm": dict(a="#1e2440", b="#3a4a8a", c="#7a8ee0", d="#f0f4ff", e="#ffe94a"),
    "depths": dict(a="#2a6a7a", b="#8ac8d0", c="#d0f0f0", d="#ffffff", e="#ff6ab0"),
}
_HEART = [
    "................", "................", "..oooo..oooo....", ".obccbooobbbbo..",
    "obcddcbobbbbbao.", "obcdcbbbbbbbbao.", "obccbbbbbbbbbao.", "obcbbbbbbbbbaao.",
    ".obbbbbbbbbaao..", "..obbbbbbbaao...", "...obbbbbaao....", "....obbbaao.....",
    ".....obaao......", "......oao.......", ".......o........", "................",
]
_PILE = ["................"] * 8 + [
    "......oooo......", "....oocddcoo....", "...occdccccbo...", "..ocdcccbcbbbo..",
    ".ocddccbbbbbbao.", ".obbbbbbbbbaaao.", "..oooooooooooo..", "................",
]
_REALM = {
    "cinderheart": ("ashes", merge(_HEART, {4: "_____e", 5: "______e", 6: "______ee__e", 7: "_______efe", 8: "______e__e", 9: "_______e", 10: "_______e"})),
    "heartwood": ("grove", [
        "..........oo....", ".........oggo...", "........oggo....", ".....ooooogo....", "...oobbbbbooo...",
        "..obccccccbbo...", ".obcdddddccbbo..", ".obcdccccdcbbo..", "obcdcdeedcdcbbo.", "obcdcdeedcdcbao.",
        ".obcdccccdcbao..", ".obccddddccbao..", "..obbccccbbaao..", "...ooaaaaaaoo...", ".....oooooo.....",
        "................"]),
    "stasis_crystal": ("frost", [
        ".......oo.......", "......odco......", ".....odccbo.....", ".....odcbbo.....", "....odccbbbo....",
        "....odcebbbo....", "...odcceebbbo...", "...odceeaebbo...", "...odcceebbbo...", "...odccbbbbao...",
        "..oodccbbbaaoo..", ".ocodcbbbbaoco..", ".obodcbbbaaobo..", "..oodcbbbaaoo...", "....oooooooo....",
        "................"]),
    "chrono_sand": ("citadel", merge(_PILE, {1: "_______d", 2: "____d______e", 3: "________d", 4: "______c____d", 5: "___c______c", 6: "_________c", 7: "______c"})),
    "sandglass": ("citadel", [
        "................", "..oooooooooooo..", "..oebbbbbbbbeo..", "...ogccccccgo...", "....ogccccgo....",
        ".....ogccgo.....", "......ogco......", "......ocgo......", ".....oggcgo.....", "....oggggcgo....",
        "...oggggcccgo...", "...occcccccco...", "..oebbbbbbbbeo..", "..oooooooooooo..", "................",
        "................"]),
    "rot_resin": ("marsh", [
        "................", ".......oo.......", "......occo......", "......ocdo......", ".....occddo.....",
        ".....ocdddo.....", "....occddcbo....", "...occdcccbbo...", "...ocdccecbbo...", "..occcceaebbbo..",
        "..occcceeebbao..", "..obcccccbbbao..", "...obbcbbbbao...", "....oobbbaaoo...", "......oooo......",
        "................"]),
    "soul_ash": ("necro", merge(_PILE, {1: "________e", 2: "_______ed", 3: "________ed", 4: "_______de", 5: "______ed", 6: "_______e"})),
    "pressure_pearl": ("depths", merge(_ORB, {0: "_____________o", 1: "____________odo", 2: "_____________o", 9: "___aaa", 10: "_______e", 11: "__________e"})),
    "thunder_quartz": ("storm", merge([
        "................", ".......o........", "......odo.......", "......odbo......", "..o...odcbo..o..",
        ".odo..odcbo.odo.", ".odbo.odcbo.odbo", ".odcbo.odcbodbo.", ".odcbo.odcbodbo.", ".odcbooodcbodbo.",
        "..ocbbbocbbcbo..", "..oabbbbbbbbao..", "...oaaaaaaaao...", "....oooooooo....", "................",
        "................"], {0: "____e______e", 3: "__e", 2: "__________e"})),
}

# ------------------------------------------------------------------ Flux machines and blocks (machine/*, ore/*)
_FLUX = dict(a="#4a2a12", b="#7a4a1e", c="#c98442", d="#f2c14e", g="#fff1b0", w="#1a1622", h="#d9773a")
_CASING = [
    "................", ".oooooooooooooo.", ".odgcccccccgcbo.", ".ocoooooooooobo.",
    ".ocowwwwwwwwobo.", ".ocowwwwwwwwobo.", ".ocowwwwwwwwobo.", ".ocowwwwwwwwobo.",
    ".ocowwwwwwwwobo.", ".ocowwwwwwwwobo.", ".ocowwwwwwwwobo.", ".ocowwwwwwwwobo.",
    ".ocoooooooooobo.", ".obbbbbbbbbbbao.", ".oooooooooooooo.", "................",
]
_BLOCK = [
    "................", ".oooooooooooooo.", ".odddddddddddco.", ".odcccccccccbao.",
    ".odcccccccccbao.", ".odcccccccccbao.", ".odcccccccccbao.", ".odcccccccccbao.",
    ".odcccccccccbao.", ".odcccccccccbao.", ".odcccccccccbao.", ".odcccccccccbao.",
    ".odcccccccccbao.", ".ocbbbbbbbbbaao.", ".oooooooooooooo.", "................",
]


def _machine(lines, glow):
    return merge(_CASING, {4 + i: "____" + line for i, line in enumerate(lines)}), dict(_FLUX, **glow)


_FLUXES = {
    "spring_wheel": _machine(["e__dd__e", "_e_dd_e_", "__edde__", "dddeeddd", "dddeeddd", "__edde__", "_e_dd_e_", "e__dd__e"], dict(e="#2a8a9a")),
    "heat_tap": _machine(["________", "_eeeeee_", "________", "_efeefe_", "________", "_effffe_", "________", "_eeeeee_"], dict(e="#ff6a1a", f="#ffd36a")),
    "void_reactor": _machine(["__eeee__", "_e____e_", "e__ee__e", "e_e__e_e", "e_e_fe_e", "e__e___e", "_e____e_", "__eeee__"], dict(e="#8a4aff", f="#ff4a8a", g="#b48cff")),
    "astral_lattice": _machine(["e___e___", "_e_e_e_e", "__e___e_", "_e_fff_e", "e__fff__", "_e_e_e_e", "__e___e_", "_e_e_e_e"], dict(e="#e0c8ff", f="#ffffff", w="#2a1a4a", g="#e0c8ff")),
    "lightning_rod": ([
        "......e.........", ".......e.e......", "......eogoe.....", ".......odo......", ".......odo......",
        "......ohhho.....", ".......obo......", "......ohhho.....", ".......obo......", "......ohhho.....",
        ".......obo......", ".....oooooo.....", "....obbbbbbo....", "....oaaaaaao....", ".....oooooo.....",
        "................"], dict(_FLUX, e="#ffe94a", d="#f0f4ff")),
    "recycler_module": ([
        "................", "................", "...oooooooooo...", "...odccccccbo...", "...ocwwwwwwbo...",
        "...ocweeeewbo...", "...ocewwwwebo...", "...ocewwwwebo...", "...ocweeeewbo...", "...ocwwwwwwbo...",
        "...ocbbbbbbbo...", "...oaaaaaaaao...", "....g.g.g.g.....", "................", "................",
        "................"], dict(_FLUX, e="#4af0ff", g="#f2c14e")),
}


def _blk(overlay, pal):
    return merge(_BLOCK, overlay), pal


_BLOCKS = {
    "bronzite_block": _blk({4: "___e________e", 6: "________b", 7: "________b", 8: "________b", 9: "________b", 11: "___e________e"},
                           dict(a="#4a2a12", b="#7a4a1e", c="#c98442", d="#f2c14e", e="#fff1b0")),
    "sunstone_block": _blk({4: "________e", 5: "________e", 6: "______eeeee", 7: "____ee_efe_ee", 8: "______eeeee", 9: "________e", 10: "________e"},
                           dict(a="#7a3a12", b="#c8641e", c="#f29a3a", d="#ffd36a", e="#fff1b0", f="#ffffff")),
    "voidglass_block": _blk({4: "___d", 5: "____d", 6: "_____d___e", 7: "______d___e", 8: "__________e", 9: "___e", 10: "____e"},
                            dict(a="#05030a", b="#0e0a1a", c="#1e1432", d="#8a4aff", e="#5a2aaa")),
    "frostium_block": _blk({4: "________e", 5: "_____e__e__e", 6: "______e_e_e", 7: "____eeeefeeee", 8: "______e_e_e", 9: "_____e__e__e", 10: "________e"},
                           dict(a="#1f3557", b="#3b6fa8", c="#7fc8e8", d="#e6f7ff", e="#ffffff", f="#b6a8ff")),
    "starcore_block": _blk({4: "____d_______d", 5: "________e", 6: "_______eee", 7: "_____eeefeee", 8: "_______eee", 9: "___d____e", 11: "___________d"},
                           dict(a="#05030a", b="#140c2a", c="#24164a", d="#b6a8ff", e="#ffe9a0", f="#ffffff")),
}


def _build():
    out = {}
    for name, (realm, rows, extra) in _GUARDIANS.items():
        out[name] = paint(rows, dict(_G[realm], **extra), _G_BASE)
    for name, (realm, rows) in _REALM.items():
        out[name] = paint(rows, _R[realm], _R_BASE)
    for name, (rows, pal) in {**_FLUXES, **_BLOCKS}.items():
        out[name] = paint(rows, pal, _R_BASE)
    return out


PIXELS = _build()
IDS = frozenset(PIXELS)


def draw(item_id):
    """A boss_items.Canvas holding the sprite (already outlined by the map: do not call outline() on it)."""
    import boss_items as b
    c = b.Canvas()
    for i, px in enumerate(PIXELS[item_id]):
        if px:
            c.p[i // 16][i % 16] = px
    return c


def png(item_id):
    return draw(item_id).png()


def sheet(path, ids):
    """A 10x contact sheet (RGB PNG, checkerboard background) for a visual check."""
    scale, cols = 10, 8
    rows = (len(ids) + cols - 1) // cols
    cell = 18 * scale
    w, h = cols * cell, rows * cell
    data = bytearray()
    for y in range(h):
        data.append(0)
        for x in range(w):
            idx = (y // cell) * cols + x // cell
            ix, iy = (x % cell) // scale - 1, (y % cell) // scale - 1
            px = PIXELS[ids[idx]][iy * 16 + ix] if idx < len(ids) and 0 <= ix < 16 and 0 <= iy < 16 else None
            data.extend(px[:3] if px else ((60, 60, 70) if (x // 10 + y // 10) % 2 else (70, 70, 80)))

    def chunk(tag, body):
        blob = tag + body
        return struct.pack(">I", len(body)) + blob + struct.pack(">I", zlib.crc32(blob) & 0xFFFFFFFF)

    pathlib.Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                                   + chunk(b"IDAT", zlib.compress(bytes(data), 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1], sorted(PIXELS))
    print(f"{len(PIXELS)} sprites defined")
