#!/usr/bin/env python3
"""Draws the menu and werewolf icons (item models vampyuyu:icon/*), among them the Skyblock icon: a floating island.

    python3 resource-pack/art/skyblock_icon.py

Standard library only (a hand-written PNG encoder), deterministic output, committed under pack/.
16x16 pixel art, one outline colour (#140f18), light from the top left, no anti-aliasing. Each icon keeps its
family silhouette and takes the five-tone palette and the motif of its Realm (docs/skyblock-notes/lore.md); the
glow tone is the only saturated pixel and marks the motif. render.py calls write_icons().
"""
import json, pathlib, struct, zlib

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"


# ------------------------------------------------------------------ Menu and werewolf icons (icon/*)
# Palette keys: o outline, m/n neutral greys, a b c d e = the Realm's shadow, base, light, highlight, glow.
# Light from the top left; each icon keeps its family silhouette and takes its Realm's colours and motif.
REALMS = {
    "ashes": ("#120a16", "#2a1a2e", "#5a2a2a", "#c8461e", "#ff8a2a"),
    "grove": ("#2c3a1e", "#4f6b2a", "#8fb84a", "#d6f08a", "#f1e7c9"),
    "frost": ("#1f3557", "#3b6fa8", "#7fc8e8", "#e6f7ff", "#b6a8ff"),
    "citadel": ("#5a3a12", "#a8742a", "#f2c14e", "#fff1b0", "#3fb6a8"),
    "marsh": ("#2a2a1a", "#4e5a22", "#8a9a2e", "#c9d36a", "#b04aa0"),
    "necro": ("#241f3a", "#4a4570", "#9c95c8", "#e8e4ff", "#6affd8"),
    "storm": ("#1e2440", "#3a4a8a", "#7a8ee0", "#f0f4ff", "#ffe94a"),
    "abyss": ("#0a1a2a", "#12405a", "#2a8a9a", "#bff0f0", "#ff6ab0"),
    "astral": ("#0e0a1a", "#3a1a6a", "#8a4aff", "#e0c8ff", "#ff4a8a"),
}
NEUTRAL = {"o": "#140f18", "m": "#2a2632", "n": "#6e6878"}

ICON_MAPS = {
    "star": ("astral", [
        "................",
        ".......oo.......",
        "......odco......",
        "......odco......",
        ".ooooodcbcooooo.",
        ".odddddccccbbbo.",
        "..oddcccceebbo..",
        "...occcceebbo...",
        "...ocbbeeebao...",
        "..ocbbbooobbao..",
        "..ocbboo.oobao..",
        ".ocbbo.....obao.",
        ".ocbo.......oao.",
        ".ooo.........oo.",
        "................",
        "................"]),
    "vial": ("marsh", [
        "................",
        "....ooooooo.....",
        ".....oddco......",
        ".....ocbbo......",
        ".....ocbbo......",
        ".....ocbbo......",
        "....odcbbbo.....",
        "...odcceebbo....",
        "...occeeeebo....",
        "..ocbeeeeebbo...",
        "..ocbbeeebbao...",
        "..obbbbbbbbaao..",
        "..obbbbbbbaaao..",
        "...oaaaaaaaao...",
        "....oooooooo....",
        "................"]),
    "book": ("necro", [
        "................",
        "..oooooooooo....",
        ".odddddcccbbo...",
        ".odccccccbbbbo..",
        ".oc.eee.cbbbao..",
        ".ocbeeeebbbbao..",
        ".ocb.ee.bbbbao..",
        ".ocbbeebbbbbao..",
        ".ocbbbbbbbbbao..",
        ".ocbbbbbbbbbao..",
        ".ocbbbbbbbbbao..",
        ".oaaaaaaaaaaao..",
        ".ocdddddddddao..",
        "..oooooooooooo..",
        "................",
        "................"]),
    "clock": ("citadel", [
        "................",
        "....oooooooo....",
        "...oddccccbbo...",
        "..odcdaaaabbbo..",
        ".odcdaaaeaabbbo.",
        ".ocdaaaaeaaabbo.",
        ".ocaaaaaeaaabbo.",
        ".ocaaaaaeeeabbo.",
        ".ocaaaaaaaaabbo.",
        ".ocaaaaaaaaabbo.",
        ".ocdaaaaaaabbbo.",
        "..ocdaaaaabbbo..",
        "...oabbbbbbao...",
        "....oooooooo....",
        "................",
        "................"]),
    "exit": ("ashes", [
        "................",
        "..oooooooo......",
        "..occcccbbo.....",
        "..ocbbbbbbo.....",
        "..ocbbbbbbo..o..",
        "..ocbbbbbbo..do.",
        "..ocbbbebbooodo.",
        "..ocbbbebbdddddo",
        "..ocbbbbbbdddddo",
        "..ocbbbbbbo..do.",
        "..ocbbbbbbo..do.",
        "..oaaaaaaao..o..",
        "..oaaaaaaao.....",
        "..oooooooooo....",
        "................",
        "................"]),
    "play": ("grove", [
        "................",
        "...oo...........",
        "...odoo.........",
        "...oddcoo.......",
        "...odcccboo.....",
        "...odcccbbboo...",
        "...occcbbbbbboo.",
        "...occbbbbbbbbbo",
        "...ocbbbbbbbaoo.",
        "...ocbbbbbaaoo..",
        "...ocbbbaaaoo...",
        "...ocbaaaaoo....",
        "...oaaaaoo......",
        "...ooooo........",
        "................",
        "................"]),
    "stop": ("ashes", [
        "................",
        "................",
        "..oooooooooooo..",
        "..ocdddddddddo..",
        "..odeccccccbbo..",
        "..odcccccccbbo..",
        "..occcccccbbbo..",
        "..occcccbbbbbo..",
        "..occcbbbbbbbo..",
        "..occbbbbbbbbo..",
        "..ocbbbbbbbaao..",
        "..obbbbbbbaaaao.",
        "..oaaaaaaaaaaao.",
        "..oooooooooooo..",
        "................",
        "................"]),
    "back": ("frost", [
        "................",
        "................",
        "......o.........",
        ".....odo........",
        "....odco........",
        "...odcooooooooo.",
        "..odcddddddddco.",
        ".odcdccccccccbo.",
        ".oabbbbbbbbbbbo.",
        "..oabooooooooo..",
        "...oabo.........",
        "....oabo........",
        ".....oao........",
        "......o.........",
        "................",
        "................"]),
    "next": ("frost", [
        "................",
        "................",
        ".........o......",
        "........odo.....",
        "........odco....",
        ".ooooooodcbo....",
        ".oddddddddcbo...",
        ".occcccccccdbo..",
        ".obbbbbbbbbbbao.",
        ".ooooooooobabo..",
        ".........obbao..",
        "..........obao..",
        "...........oao..",
        "............o...",
        "................",
        "................"]),
    "axe": ("grove", [
        "................",
        "......ooooo.....",
        ".....odddcco....",
        "....oddeccbbo...",
        "...oddecccbbbo..",
        "..oddecccbbbao..",
        "..ocdcccbbbao...",
        "..occcccbboo....",
        "...ocbbbbo.m....",
        "....oooboonmo...",
        ".......obonmo...",
        "......oabo.nmo..",
        ".....oabo..nmo..",
        "....oabo...omo..",
        "....ooo.....oo..",
        "................"]),
    "map": ("citadel", [
        "................",
        "................",
        ".oooooooooooooo.",
        ".odddcoccdddcco.",
        ".odcccobbcccbbo.",
        ".occbbocebcbbbo.",
        ".ocbbeobbbbbbbo.",
        ".ocbbbocbbbbbbo.",
        ".ocbbbocbbebbbo.",
        ".ocbbbobbbbbbao.",
        ".oabbbobbbbbaao.",
        ".oaaaaoaaaaaaao.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................"]),
    "compass": ("storm", [
        "................",
        "....oooooooo....",
        "...odcccccbbo...",
        "..odcbbebbbbbo..",
        ".odcbbbeebbbbbo.",
        ".ocbbbbeeebbbbo.",
        ".ocbbbbeeeebbbo.",
        ".ocbbbbooooobbo.",
        ".ocbbbooddbbbbo.",
        ".ocbbooddbbbbbo.",
        ".ocbbodbbbbbbbo.",
        "..oabbbbbbbbbo..",
        "...oaaaaaaaao...",
        "....oooooooo....",
        "................",
        "................"]),
    "bed": ("necro", [
        "................",
        "................",
        "................",
        "..oo............",
        "..ocoooooooooo..",
        "..ocoddddcccdo..",
        "..ocoeeeeccbbo..",
        "..ocoeeeeccbbbo.",
        "..ococcccbbbbbo.",
        "..ocobbbbbbbbbo.",
        "..ocoaaaaaaaaao.",
        "..ocooooooooooo.",
        "..oao.......oao.",
        "..ooo.......ooo.",
        "................",
        "................"]),
    "bone": ("necro", [
        "................",
        "............oo..",
        "..........ooddoo",
        "..........odddco",
        "..........odcbo.",
        ".........odcbo..",
        "........odcbbo..",
        ".......odcbbo...",
        "......odcbbo....",
        ".....odcbbo.....",
        "....odcbbo......",
        "..oodcbbo.......",
        ".odddcao........",
        ".occcoo.........",
        "..oo............",
        "................"]),
    "sun": ("citadel", [
        "................",
        ".......oo.......",
        "...o...dc...o...",
        "..odo..oo..odo..",
        "...ooooddooooo..",
        "....odddddcco...",
        "...odddcccccbo..",
        ".oodddcceecbbboo",
        ".odcccceeebbbbbo",
        ".oocccccccbbbooo",
        "...occcbbbbbbo..",
        "..odooabbbaoodo.",
        "...o..oooo...o..",
        "......dc..dc....",
        "................",
        "................"]),
    "lever": ("citadel", [
        "................",
        "............oo..",
        "...........odeo.",
        "...........odeo.",
        "...........ocbo.",
        "..........odbo..",
        ".........odbo...",
        "........odbo....",
        "..ooooooobooooo.",
        ".oddddddcbbbcco.",
        ".occccccbbbbbbo.",
        ".obbbbbbbbbbbao.",
        ".oaaaaaaaaaaaao.",
        ".oooooooooooooo.",
        "................",
        "................"]),
    "coin": ("citadel", [
        "................",
        "....oooooooo....",
        "...oddcccccbo...",
        "..odcbbbbbbbbo..",
        ".odcbbbeebbbbbo.",
        ".ocbbbeeebbbbbo.",
        ".ocbbbbeeebbbbo.",
        ".ocbbbbbeebbbbo.",
        ".ocbbbeeebbbbao.",
        ".ocbbbeebbbbbao.",
        ".odbbbbbbbbbaao.",
        "..oabbbbbbbaao..",
        "...oaaaaaaaao...",
        "....oooooooo....",
        "................",
        "................"]),
    "door": ("ashes", [
        "................",
        "...oooooooooo...",
        "...occcccccbo...",
        "...ocbbbbbbbo...",
        "...ocbbbbbbbo...",
        "...ocbbbbbbbo...",
        "...ocbbbbbbbo...",
        "...ocbbbbdeo....",
        "...ocbbbbbeeo...",
        "...ocbbbbbbbo...",
        "...ocbbbbbbbo...",
        "...oaaaaaaaao...",
        "...oaaaaaaaao...",
        "...oooooooooo...",
        "................",
        "................"]),
    "pearl": ("abyss", [
        "................",
        ".....oooooo.....",
        "....oddcccbbo...",
        "...oddcccbbbbo..",
        "..oddceebbbbbbo.",
        "..odcceebbbbbbo.",
        "..occcbbbbbbbbo.",
        "..ocbbbbbbbbbbo.",
        "..ocbbbbbbbbbao.",
        "..ocbbbbbbbbaao.",
        "..oabbbbbbbaaao.",
        "...oabbbbbaaao..",
        "....oaaaaaaao...",
        ".....oooooo.....",
        "................",
        "................"]),
    "plus": ("grove", [
        "................",
        "................",
        ".....oooooo.....",
        ".....odddco.....",
        ".....odccbo.....",
        ".....occbbo.....",
        ".oooooccbbooooo.",
        ".odddcceebbbbco.",
        ".occcccceebbbbo.",
        ".oooooobbaaoooo.",
        ".....obbbao.....",
        ".....obbaao.....",
        ".....oaaaao.....",
        ".....oooooo.....",
        "................",
        "................"]),
    "pencil": ("citadel", [
        "................",
        "............oo..",
        "...........oddo.",
        "..........odcbo.",
        ".........odcbo..",
        "........odcbo...",
        ".......odcbo....",
        "......odcbo.....",
        ".....odcbo......",
        "....oeccbo......",
        "...oeeocao......",
        "..oeeoooo.......",
        "..oeeo..........",
        "..ooo...........",
        "................",
        "................"]),
    "trash": ("marsh", [
        "................",
        "......oooo......",
        "...ooooddooooo..",
        "..odddddcccco...",
        "..oooooooooooo..",
        "...odcbbbbbbo...",
        "...ocbbebbbbo...",
        "...ocbbebbbbo...",
        "...ocbbebbbbo...",
        "...ocbbbbbbao...",
        "...ocbbebbbao...",
        "...obbbbbbbao...",
        "....oaaaaaao....",
        ".....oooooo.....",
        "................",
        "................"]),
    "warn": ("ashes", [
        "................",
        ".......oo.......",
        "......odco......",
        "......odco......",
        ".....odccbo.....",
        ".....ocebbo.....",
        "....oceebbbo....",
        "....ocebbbbo....",
        "...ocbebbbbbo...",
        "...ocbbbbbbbo...",
        "..ocbbbebbbbbo..",
        "..ocbbbbbbbbao..",
        ".ocbbbbbbbbbaao.",
        ".oaaaaaaaaaaaao.",
        ".oooooooooooooo.",
        "................"]),
    "tag": ("citadel", [
        "................",
        "................",
        "......oooo......",
        ".....oddccoo....",
        "....oddccbbbo...",
        "...oddccebbbbo..",
        "..oddccceebbbbo.",
        ".odcccbbbbbbbbo.",
        ".ocbbbbbbbbbbao.",
        "..ocbbbbbbbbao..",
        "...ocbbbbbbao...",
        "....oabbbbao....",
        ".....oaaaao.....",
        "......oooo......",
        "................",
        "................"]),
    "ruler": ("citadel", [
        "................",
        "................",
        ".............oo.",
        "...........oodo.",
        "..........odcdo.",
        ".........odcdbo.",
        "........odeobo..",
        ".......odcdbo...",
        "......odcobo....",
        ".....odcebo.....",
        "....odcdbo......",
        "...odcobo.......",
        "..odddbo........",
        "..oddbo.........",
        "...oo...........",
        "................"]),
    "sword": ("frost", [
        "................",
        "..............oo",
        ".............odo",
        "............odco",
        "...........odco.",
        "..........odco..",
        ".........odco...",
        "...oo...odco....",
        "...oeo.odco.....",
        "....oeodco......",
        ".....oecbo......",
        "....oeeboo......",
        "...obaoeo.......",
        "..obao.oo.......",
        "..ooo...........",
        "................"]),
    "chest": ("citadel", [
        "................",
        "................",
        "..oooooooooooo..",
        ".odddccccccbbbo.",
        ".odcbbbbbbbbbbo.",
        ".ocbbbbbbbbbbbo.",
        ".oooooooooooooo.",
        ".ocaaaaeeaaaabo.",
        ".ocbbbbeebbbbbo.",
        ".ocbbbbbbbbbbbo.",
        ".ocbbbbbbbbbbao.",
        ".obbbbbbbbbbaao.",
        ".oaaaaaaaaaaaao.",
        ".oooooooooooooo.",
        "................",
        "................"]),
    "note": ("storm", [
        "................",
        "........ooooo...",
        "........oddddo..",
        "........ocbbbbo.",
        "........ocbbbeo.",
        "........ocbo.oo.",
        "........ocbo....",
        "........ocbo....",
        "...oooo.ocbo....",
        "..oddcco.cbo....",
        "..ocbbbboocbo...",
        "..ocbbebbobbo...",
        "..oabbbbao.oo...",
        "...oaaaao.......",
        "....oooo........",
        "................"]),
    "heart": ("ashes", [
        "................",
        "................",
        "...ooo....ooo...",
        "..odddo..odddo..",
        ".odceeco.occcco.",
        ".odeecccoccccco.",
        ".occccccccccbbo.",
        ".occccccccbbbbo.",
        "..occcccbbbbbo..",
        "...occbbbbbbao..",
        "....obbbbbbao...",
        ".....oabbbao....",
        "......oaabo.....",
        ".......ooo......",
        "................",
        "................"]),
    "lock": ("frost", [
        "................",
        ".....oooooo.....",
        "....oddddcco....",
        "....odo..obo....",
        "....odo..obo....",
        "....odo..obo....",
        "..oooooooooooo..",
        "..odddccccbbbo..",
        "..occbbbbbbbbo..",
        "..ocbbbeebbbbo..",
        "..ocbbbeebbbbo..",
        "..ocbbbbbbbbao..",
        "..oaaaaaaaaaao..",
        "..oooooooooooo..",
        "................",
        "................"]),
    "moon": ("necro", [
        "................",
        "......oooooo....",
        "....ooddddcco...",
        "...oddcccbbo....",
        "..oddcbbbbo.....",
        "..odcbbbbo...e..",
        ".odcbbbbo.......",
        ".odcbbbbo.......",
        ".ocbbbbbo.......",
        ".ocbbbbbbo..o...",
        "..oabbbbbboo....",
        "..oaabbbbbbboo..",
        "...oaaaaaaaaao..",
        "....oooooooooo..",
        "................",
        "................"]),
    "potion-death": ("marsh", [
        "................",
        ".....ooooo......",
        ".....oddco......",
        ".....ocbbo......",
        "......obo.......",
        "....oooboooo....",
        "...odccbbbbbo...",
        "..odcceeebbbbo..",
        "..ocbeeoeebbbo..",
        "..ocbeeeeebbbo..",
        "..ocbbeoeebbao..",
        "..obbbbeebbaao..",
        "...oaabbbaaao...",
        "....oooooooo....",
        "................",
        "................"]),
    "potion-life": ("grove", [
        "................",
        ".....ooooo......",
        ".....oddco......",
        ".....ocbbo......",
        "......obo.......",
        "....oooboooo....",
        "...oddccbbbbo...",
        "..oddcceeebbbo..",
        "..odcceeeeebbo..",
        "..ocbbbeeebbbo..",
        "..ocbbbbebbbao..",
        "..obbbbbbbbaao..",
        "...oaabbbaaao...",
        "....oooooooo....",
        "................",
        "................"]),
    "shield": ("frost", [
        "................",
        "..oooooooooooo..",
        "..oddddcccccbo..",
        "..odcccccccbbo..",
        "..occcbebbbbbo..",
        "..occceeebbbbo..",
        "..ocbbbebbbbbo..",
        "..ocbbbbbbbbao..",
        "..ocbbbbbbbbao..",
        "...ocbbbbbbao...",
        "....obbbbbbo....",
        ".....oabbao.....",
        "......oaao......",
        ".......oo.......",
        "................",
        "................"]),
    "target": ("ashes", [
        "................",
        "....oooooooo....",
        "...oddccccbbo...",
        "..odcbbbbbbbbo..",
        ".odcbooooooobbo.",
        ".ocbooddccoobbo.",
        ".ocbodbbbbcobbo.",
        ".ocbocbeebbobbo.",
        ".ocbocbeebbobao.",
        ".ocbooabbaoobao.",
        ".oabbooooooaaao.",
        "..oabbbbbbbaao..",
        "...oaaaaaaaao...",
        "....oooooooo....",
        "................",
        "................"]),
    "timer": ("citadel", [
        "................",
        "......oooo......",
        ".....oddco......",
        "....oooooooo....",
        "...oddccccbbo...",
        "..odcaaeaaabbo..",
        "..ocaaaeaaaabo..",
        "..ocaaaeeaaabo..",
        "..ocaaaaaaaabo..",
        "..ocaaaaaaaabo..",
        "..ocdaaaaaabbo..",
        "...oabbbbbbao...",
        "....oooooooo....",
        "................",
        "................",
        "................"]),
    "skip": ("necro", [
        "................",
        "..oooooooooooo..",
        "..odddddddddco..",
        "..occcccccceeo..",
        "..occcccccceeeo.",
        "..oc.....c.eeeo.",
        "..occccc.eeecco.",
        "..oc..eeeeec.co.",
        "..occeeeccccbbo.",
        "..oceeecccbbbo..",
        "..oeeecbbbbbbo..",
        ".oeeebbbbbbbao..",
        ".oeebaaaaaaaao..",
        "..oooooooooooo..",
        "................",
        "................"]),
    "skyblock": ("grove", [
        "................",
        ".....oooooo.....",
        "....odddcco.....",
        "...oddcccbbo..e.",
        "...ocbbebbbo....",
        "....oabbbbo.....",
        "......oboo......",
        "..oooooboooooo..",
        ".oddcccbbbbbbbo.",
        ".occccbbbbbbbbo.",
        ".onnnnnmnnnmmmo.",
        "..onnmnnnnnmmo..",
        "...onnnnnmmmo...",
        "....oonnmmoo..d.",
        "......oomoo.dd..",
        "........oo......"]),
}
CARD_ROLES = {"back": "astral", "cupid": "storm", "guard": "frost", "hunter": "marsh",
              "seer": "abyss", "villager": "grove", "werewolf": "ashes", "witch": "necro"}

def chunk(kind, data):
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def png(rows, palette=None):
    palette = palette or PALETTE
    raw = b"".join(b"\x00" + b"".join(bytes(palette[ch]) for ch in row) for row in rows)
    header = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def _hex(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def realm_palette(realm):
    pal = {k: _hex(v) for k, v in NEUTRAL.items()}
    pal.update(zip("abcde", map(_hex, REALMS[realm])))
    pal["."] = (0, 0, 0, 0)
    return pal


PALETTE = realm_palette("grove")  # the Skyblock icon is the Grove's island
ROWS = ICON_MAPS["skyblock"][1]

_CARD = (["................", "...oooooooooo..."] + ["...occcccccco..."] + ["...ocbbbbbbao..."] * 10
         + ["...oaaaaaaaao...", "...oooooooooo...", "................"])
# eight-by-eight motifs, drawn in the card's middle ('.' keeps the card face)
_MOTIFS = {
    "back": ["...dd...", "..dccd..", ".dceecd.", "dceeeecd", "dceeeecd", ".dceecd.", "..dccd..", "...dd..."],
    "cupid": ["........", ".dd..dd.", "dddddddd", "ddeeedd.", ".deeeed.", "..dddd..", "...dd...", "e.e.e.e."],
    "guard": ["dddddddd", "dccceccd", "dcceeccd", "dccceccd", ".dcccd..", ".dcccd..", "..dcd...", "...d...."],
    "hunter": ["...dd...", "..d..d..", ".d.ee.d.", "dd.ee.dd", "dd.ee.dd", ".d.ee.d.", "..d..d..", "...dd..."],
    "seer": ["........", "..dddd..", ".dccccd.", "dcceecd.", "dcceecdd", ".dccccd.", "..dddd..", "........"],
    "villager": ["...dd...", "..dccd..", ".dccccd.", "dddddddd", ".cccccc.", ".cceecc.", ".cceecc.", ".cccccc."],
    "werewolf": ["d......d", "dd....dd", "dccddccd", ".cceecc.", ".cdeedc.", "..cddc..", "..edde..", "...ee..."],
    "witch": ["...dd...", "..dccd..", "..dccd..", ".dccccd.", "dddddddd", "..eccc..", "..cddc..", "...dd..."],
}


def card_rows(role):
    rows = list(_CARD)
    for i, motif in enumerate(_MOTIFS[role]):
        y = 4 + i
        row = list(rows[y])
        for x, ch in enumerate(motif):
            if ch != ".":
                row[4 + x] = ch
        rows[y] = "".join(row)
    return rows


def all_icons():
    """name -> (rows, palette) for every icon of the pack, derived icons (greyed, renamed) included."""
    icons = {n: (rows, realm_palette(realm)) for n, (realm, rows) in ICON_MAPS.items()}
    for role, realm in CARD_ROLES.items():
        icons[f"card-{role}"] = (card_rows(role), realm_palette(realm))

    def grey(name):
        rows, pal = icons[name]
        out = {}
        for k, (r, g, b, a) in pal.items():
            v = int((r * 3 + g * 6 + b) / 10 * 0.6) if k not in "o." else None
            out[k] = (r, g, b, a) if v is None else (v, v, v, a)
        return rows, out
    icons["potion-life-off"] = grey("potion-life")
    icons["potion-death-off"] = grey("potion-death")
    icons["mark-last"] = grey("shield")
    icons["mark-wolf"] = grey("card-werewolf")
    return icons


def write_one(name, rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), f"{name}: the icon is a 16x16 grid"
    assert set("".join(rows)) <= set(pal), f"{name}: unknown palette key"
    for sub in ("textures/item/icon", "models/item/icon", "items/icon"):
        (PACK / sub).mkdir(parents=True, exist_ok=True)
    (PACK / "textures/item/icon" / f"{name}.png").write_bytes(png(rows, pal))
    (PACK / "models/item/icon" / f"{name}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/icon/{name}"}}, indent=2) + "\n")
    (PACK / "items/icon" / f"{name}.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/icon/{name}"}}, indent=2) + "\n")


def write_icons():
    icons = all_icons()
    for name, (rows, pal) in icons.items():
        write_one(name, rows, pal)
    print("wrote", len(icons), "icons")



def write():
    write_one("skyblock", ROWS, PALETTE)
    print("wrote the skyblock icon")


def preview(scale=16):
    """Nearest-neighbour enlargement for reviewers (docs/skyblock-notes/netmenu-icon-preview.png)."""
    big = ["".join(ch * scale for ch in row) for row in ROWS for _ in range(scale)]
    return png(big)


if __name__ == "__main__":
    write_icons()
    if "--preview" in __import__("sys").argv:
        out = pathlib.Path(__file__).resolve().parents[2] / "docs" / "skyblock-notes" / "netmenu-icon-preview.png"
        out.write_bytes(preview())
        print("wrote", out)
