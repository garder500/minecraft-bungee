#!/usr/bin/env python3
"""Contact sheet of every state of the custom HUD, composited from the real sprites.

    python3 resource-pack/art/hud_sheet.py [--scale 3] [--mode auto|large|medium|small] [--out DIR]
    make hud-sheet [SCALE=2] [MODE=small]

Writes resource-pack/dist/hud-sheet/: one PNG per state (top plate and right panel crops, plus a few
full 1920x1080 scenes), `index.html` (gallery, PNGs embedded as data URIs, self-contained) and
`states.json`. Pure Python (no Pillow): sprites are read with a tiny PNG decoder.

Two things are ported here as pure functions so the sheet shows what the game shows:
  * CustomHud.of()/seats()/badges()/render()  (arena-plugin/.../CustomHud.java): `state_of`,
    `seats`, `badges`, `render` below. scripts/test_hud_sheet.py checks them against sequences
    derived by hand from the Java.
  * the core shader's placement of a glyph (art/fx_shaders.py COMMON_VS): `place`.
Nothing here draws text: the words are painted in the sprites by art/hud.py.
"""
import argparse, base64, hashlib, html, json, math, pathlib, struct, sys, zlib
from dataclasses import dataclass, field

ART = pathlib.Path(__file__).resolve().parent
ROOT = ART.parent.parent
sys.path.insert(0, str(ART.parent))
import layout  # noqa: E402

SPRITES = ART.parent / "pack" / "assets" / "vampyuyu" / "textures" / "font" / "hud"
SCREEN_W, SCREEN_H = 1920, 1080
ELEMENTS = {e[0]: (i, *e[1:]) for i, e in enumerate(layout.HUD_ELEMENTS)}   # name -> (id, anchor, x, y, w, h)
SPRITE_NAMES = {n for n, _, _ in layout.hud_sprites()}
ROLES = layout.roles()
BY_PHASE = {r["call"]["phase"]: (rid, r) for rid, r in ROLES.items() if "call" in r}
DANGER_SECONDS = 5
SEATS, BADGES = layout.HUD_SEATS, 4
NIGHT_PHASES = {"CUPID", "GUARD", "SEER", "NIGHT", "WITCH", "MINION", "AMULET", "HUNTER_SHOT"}


# ------------------------------------------------------------------------------------------
# Port of CustomHud.java (pure functions)

@dataclass(frozen=True)
class State:
    """CustomHud.State; `fraction` is the draining bar (1 = full turn left)."""
    palette: str
    top_icon: str
    round: int
    label: str
    seconds: int
    timed: bool
    show_role: bool
    role_icon: str
    role_word: str
    team_word: str
    role_palette: str
    alive: int
    total: int
    seats: tuple
    badges: tuple
    against: int
    fraction: float = 1.0


@dataclass
class Facts:
    """What CustomHud.of() asks the WerewolfGame about one viewer."""
    phase: str = "DISCUSSION"
    role: str = "VILLAGER"           # selfRoleName(viewer)
    dead: bool = False               # !alive().contains(viewer)
    actor: bool = False              # actors().contains(viewer)
    night: bool = False
    exiled: bool = False             # exiled(viewer): sent away for the day
    reveal: str = "FULL"             # the arena's reveal setting
    round: int = 2
    seconds: int = 87
    alive: int = 10
    total: int = 12
    minion: bool = False             # isMinion(viewer)
    face_up: bool = True
    tally_visible: bool = True
    votes_against: int = 0           # votes(viewer).getOrDefault(viewer, 0)
    seats: tuple = ()
    badges: tuple = ()


def word(text):
    """CustomHud.word == layout.key."""
    return layout.key(text)


def mode(size):
    return {"large": 79, "medium": 80, "small": 81}.get(size or "auto", 78)


def turn_role(phase):
    """WerewolfPresentation.turnRole."""
    fixed = {"CUPID": "CUPID", "GUARD": "GUARD", "SEER": "SEER", "NIGHT": "WEREWOLF", "MINION": "WEREWOLF",
             "WITCH": "WITCH", "HUNTER_SHOT": "HUNTER"}
    if phase in fixed:
        return fixed[phase]
    return BY_PHASE[phase][0] if phase in BY_PHASE else None


def boss_dim_and_timer(f):
    """WerewolfPresentation.boss(): (colour is INK_DIM, timer shown). Only these two matter to the HUD."""
    if f.phase == "FINISHED":
        return False, False
    if f.exiled and not f.night:
        return True, True
    if f.night:
        if not f.actor:
            return True, f.reveal == "FULL"
        return False, True
    return False, True


def role_info(role):
    return ROLES.get(role) or ROLES["VILLAGER"]


def state_of(f):
    """CustomHud.of()."""
    dim, timed = boss_dim_and_timer(f)
    turn = turn_role(f.phase)
    neutral_pause = f.phase == "MARTYR" and not f.actor
    if f.dead:
        palette = "dead"
    elif dim:
        palette = "sleep"
    elif neutral_pause:
        palette = "vote"
    elif turn is not None:
        palette = role_info(turn)["palette"]
    elif f.phase in ("DAY_VOTE", "TRIAL", "ELECTION"):
        palette = "vote"
    else:
        palette = "villager"
    if f.phase == "FINISHED":
        label = "game over"
    elif f.phase == "HUNTER_SHOT":
        label = "sleeping" if f.night and not f.actor else "last shot"
    elif not f.night:
        label = {"DAY_VOTE": "vote", "ELECTION": "election", "DEFENSE": "defense", "TRIAL": "trial"}.get(f.phase, "debate")
    elif not f.actor:
        label = "sleeping"
    else:
        fixed = {"CUPID": "cupid awake", "GUARD": "guard awake", "SEER": "seer awake", "NIGHT": "wolves hunt",
                 "WITCH": "witch awake", "AMULET": "amulet"}
        label = fixed.get(f.phase) or (BY_PHASE[f.phase][1]["call"]["label"] if f.phase in BY_PHASE else "waiting")
    if f.phase == "MARTYR":
        label = ROLES["MARTYR"]["call"]["label"] if f.actor else "vote"
    if f.exiled and not f.night and f.phase != "FINISHED":
        label = "away"
    info = role_info(f.role)
    return State(
        palette=palette,
        top_icon="vote" if f.phase == "DAY_VOTE" else "moon" if f.night else "sun",
        round=f.round,
        label=label,
        seconds=f.seconds,
        timed=timed,
        show_role=f.face_up,
        role_icon="dead" if f.dead else info["icon"],
        role_word="fallen" if f.dead else info["word"],
        team_word="out" if f.dead else "wolf side" if f.minion else layout.team_word(info),
        role_palette="dead" if f.dead else info["palette"],
        alive=f.alive,
        total=f.total,
        seats=tuple(f.seats),
        badges=tuple(f.badges),
        against=f.votes_against if f.phase == "DAY_VOTE" and not f.dead and f.tally_visible else -1,
    )


def seats(order, alive, viewer, ballot=None, lover=None, pack=()):
    """CustomHud.seats(): dead, themselves, their ballot, their pack mates and lover, the rest."""
    out = []
    for p in order:
        out.append("dead" if p not in alive else "me" if p == viewer else "ballot" if p == ballot
                   else "mate" if p in pack or p == lover else "alive")
    return out


def badges(alive=True, in_love=False, mayor=False, silenced=False, exiled=False, believed="VILLAGER",
           life=False, death=False, infect_left=False, last_protected=False):
    """CustomHud.badges(): what the viewer carries."""
    out = []
    if not alive:
        return out
    if in_love: out.append("lover")
    if mayor: out.append("mayor")
    if silenced and not exiled: out.append("silenced")
    if believed == "WITCH" and life: out.append("life")
    if believed == "WITCH" and death: out.append("death")
    if believed == "INFECTOR" and infect_left: out.append("infect")
    if believed == "GUARD" and last_protected: out.append("guard")
    return out


def render(s, md=78, sprites=None, elements=None, strict=False):
    """CustomHud.render(): the glyphs of the action bar, in order, as (mode, element, sprite, parameter).

    A glyph whose element or sprite is unknown renders as nothing, as in Java (`Component.empty()`);
    with strict=True it raises instead, so the sheet never hides a missing sprite."""
    sprites = SPRITE_NAMES if sprites is None else sprites
    elements = ELEMENTS if elements is None else elements
    out = []

    def glyph(element, sprite, parameter=0):
        if element in elements and sprite in sprites:
            out.append((md, element, sprite, max(0, min(255, parameter))))
        elif strict:
            raise KeyError(f"no sprite {sprite!r} for element {element!r}")

    danger = s.timed and s.seconds <= DANGER_SECONDS
    t = max(0, min(9 * 60 + 59, s.seconds))
    plate = "danger" if danger and t % 2 == 0 else s.palette
    kind = "x" if danger else "d"
    glyph("frame", "frame_" + plate)
    glyph("icon", "icon_" + s.top_icon)
    glyph("round", "s" + str(max(0, min(9, s.round))))
    glyph("label", "label_" + word(s.label))
    if s.timed:
        glyph("fill", "fill_" + plate, int(round(max(0.0, min(1.0, s.fraction)) * 255)))
        clock = "%d%02d" % (t // 60, t % 60)
        glyph("t1", kind + clock[0])
        glyph("tcolon", kind + "colon")
        glyph("t3", kind + clock[1])
        glyph("t4", kind + clock[2])
    if s.show_role:
        glyph("pframe", "pframe_" + s.role_palette)
        glyph("picon", "picon_" + s.role_icon)
        glyph("plabel", "plabel_" + word(s.role_word))
        glyph("pteam", "pteam_" + word(s.team_word))
        alive, total = "%2d" % min(99, s.alive), "%2d" % min(99, s.total)
        for el, c in (("a1", alive[0]), ("a2", alive[1])):
            glyph(el, "dblank" if c == " " else "d" + c)
        glyph("aslash", "dslash")
        for el, c in (("a3", total[0]), ("a4", total[1])):
            glyph(el, "dblank" if c == " " else "d" + c)
        for i in range(min(SEATS, len(s.seats))):
            glyph("m%d" % i, "dot_" + s.seats[i])
        for i in range(min(BADGES, len(s.badges))):
            glyph("b%d" % i, "badge_" + s.badges[i])
        if s.against >= 0:
            hot = s.against > 0
            digits = "%2d" % min(99, s.against)
            glyph("vframe", "vframe_" + ("hot" if hot else "calm"))
            for i in range(2):
                c = digits[i]
                glyph("v%d" % (i + 1), "dblank" if c == " " else ("x" if hot else "d") + c)
    return out


def glyph_codes(glyphs):
    """The (r, g, b) colour the plugin gives each glyph: (mode, 100 + element id, parameter)."""
    return [(m, layout.HUD_ID_BASE + ELEMENTS[e][0], p) for m, e, _, p in glyphs]


# ------------------------------------------------------------------------------------------
# Port of the core shader's placement (art/fx_shaders.py COMMON_VS)

def gui_size(scale):
    return SCREEN_W // scale, SCREEN_H // scale


def factor(md, scale):
    gx, gy = gui_size(scale)
    fit = min(gx / 640.0, gy / 360.0)
    return {79: 1.0, 80: 0.75, 81: 0.5}.get(md) or (1.0 if fit >= 0.9 else 0.75 if fit >= 0.65 else 0.5)


def rnd(v):
    return math.floor(v + 0.5)


def place(element, md, scale):
    """Screen box (x0, y0, x1, y1) in GUI px of an element: anchor + round((box + corner) * f)."""
    _, anchor, x, y, w, h = ELEMENTS[element]
    gx, gy = gui_size(scale)
    f = factor(md, scale) * (layout.HUD_PANEL_SCALE if anchor == 1 else 1.0)
    ax, ay = (gx // 2, 0) if anchor == 0 else (gx, gy // 2)
    return ax + rnd(x * f), ay + rnd(y * f), ax + rnd((x + w) * f), ay + rnd((y + h) * f)


# ------------------------------------------------------------------------------------------
# PNG in / out (8-bit, no dependencies)

def png_read(path):
    """-> (w, h, rows of RGBA bytes). Handles colour types 2 and 6, 8 bit, non-interlaced."""
    data = pathlib.Path(path).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    pos, idat, w = 8, b"", 0
    while pos < len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h, depth, ctype, _, _, inter = struct.unpack(">IIBBBBB", body)
            assert depth == 8 and ctype in (2, 6) and inter == 0, f"{path}: unsupported PNG"
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = 4 if ctype == 6 else 3
    raw, stride, rows, prev = zlib.decompress(idat), w * bpp, [], bytearray(w * bpp)
    for y in range(h):
        ft, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if ft == 1: line[i] = (line[i] + a) & 255
            elif ft == 2: line[i] = (line[i] + b) & 255
            elif ft == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        if bpp == 3:
            rgba = bytearray(w * 4)
            for x in range(w):
                rgba[x * 4:x * 4 + 3] = line[x * 3:x * 3 + 3]
                rgba[x * 4 + 3] = 255
            rows.append(bytes(rgba))
        else:
            rows.append(bytes(line))
    return w, h, rows


def png_write_rgb(rows, w):
    """Rows of RGB bytes -> PNG bytes (filter 0, deflate 9: the scenes are blocky and shrink well)."""
    raw = b"".join(b"\x00" + bytes(r) for r in rows)

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, len(rows), 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


_sprite_cache = {}


def sprite(name):
    if name not in _sprite_cache:
        _sprite_cache[name] = png_read(SPRITES / (name + ".png"))
    return _sprite_cache[name]


# ------------------------------------------------------------------------------------------
# Scene: a plausible dark Minecraft screen, drawn at GUI resolution (1 px = 1 GUI px)

def _hash(*v):
    return int(hashlib.md5(repr(v).encode()).hexdigest()[:8], 16)


def background(scale, night=True):
    """Rows of RGB bytes (GUI resolution): sky, blocky hills with trees, hotbar, crosshair, chat box."""
    gx, gy = gui_size(scale)
    sky_top, sky_bot = ((9, 12, 32), (34, 40, 74)) if night else ((70, 110, 170), (150, 185, 215))
    grass, dirt = ((34, 66, 40), (52, 40, 30)) if night else ((82, 140, 60), (110, 80, 56))
    rows = []
    horizon = int(gy * 0.62)
    # terrain height per 8 px column: gentle blocky hills
    heights = [horizon + int(9 * math.sin(c * 0.21) + 6 * math.sin(c * 0.067 + 1)) // 4 * 4 for c in range(gx // 8 + 2)]
    for y in range(gy):
        row = bytearray()
        t = min(1.0, y / max(1, horizon))
        sky = tuple(int(sky_top[i] + (sky_bot[i] - sky_top[i]) * t) for i in range(3))
        for c in range(0, gx, 8):
            top = heights[c // 8]
            if y < top:
                px = sky
                if night and _hash(c, y // 8) % 53 == 0 and y < horizon * 0.7:
                    px = (200, 205, 230)        # a star
            elif y < top + 4:
                px = grass
            else:
                n = _hash(c // 16, y // 8) % 5
                px = tuple(max(0, v - 6 * n) for v in dirt)
            row += bytes(px) * min(8, gx - c)
        rows.append(row)
    # trees: trunk + a leaf block, on some columns
    for c in range(2, gx // 8, 7):
        if _hash(c) % 3 == 0:
            continue
        base = heights[c] - 1
        for y in range(max(0, base - 24), base):
            rows[y][c * 8 * 3:c * 8 * 3 + 8 * 3] = bytes((60, 44, 28) if night else (90, 64, 40)) * 8
        for y in range(max(0, base - 40), max(0, base - 22)):
            x0, x1 = max(0, (c - 1) * 8), min(gx, (c + 2) * 8)
            rows[y][x0 * 3:x1 * 3] = bytes((24, 56, 30) if night else (60, 110, 44)) * (x1 - x0)

    def box(x0, y0, x1, y1, color, a=255):
        for y in range(max(0, y0), min(gy, y1)):
            r = rows[y]
            for x in range(max(0, x0), min(gx, x1)):
                for k in range(3):
                    r[x * 3 + k] = (color[k] * a + r[x * 3 + k] * (255 - a)) // 255

    # hotbar (182 x 22), selected slot, crosshair, chat box
    hx, hy = gx // 2 - 91, gy - 22
    box(hx, hy, hx + 182, hy + 22, (20, 20, 20), 150)
    for i in range(9):
        box(hx + 3 + i * 20, hy + 3, hx + 3 + i * 20 + 16, hy + 19, (70, 70, 70), 120)
    box(hx + 3 - 1, hy - 1 + 3 - 2, hx + 3 + 20 - 3, hy + 24 - 2, (210, 210, 210), 255)
    box(hx + 3, hy + 3, hx + 3 + 16, hy + 19, (90, 90, 90), 255)
    cx, cy = gx // 2, gy // 2
    box(cx - 4, cy, cx + 5, cy + 1, (230, 230, 230), 200)
    box(cx, cy - 4, cx + 1, cy + 5, (230, 230, 230), 200)
    box(2, gy - 58, 2 + min(gx // 3, 160), gy - 30, (0, 0, 0), 100)
    return rows


def blit(rows, spr, x0, y0, x1, y1):
    """Alpha-blend a sprite stretched (nearest) over the box [x0, x1) x [y0, y1)."""
    sw, sh, srows = spr
    bw, bh = x1 - x0, y1 - y0
    if bw <= 0 or bh <= 0:
        return
    gy, gx = len(rows), len(rows[0]) // 3
    for dy in range(bh):
        y = y0 + dy
        if not 0 <= y < gy:
            continue
        src, dst = srows[dy * sh // bh], rows[y]
        for dx in range(bw):
            x = x0 + dx
            if not 0 <= x < gx:
                continue
            i = (dx * sw // bw) * 4
            a = src[i + 3]
            if a == 0:
                continue
            j = x * 3
            if a == 255:
                dst[j:j + 3] = src[i:i + 3]
            else:
                for k in range(3):
                    dst[j + k] = (src[i + k] * a + dst[j + k] * (255 - a)) // 255


def compose(glyphs, scale, base):
    """Draw glyphs (as `render` returns them) over a copy of a background; returns rows at GUI resolution."""
    rows = [bytearray(r) for r in base]
    for md, element, name, _ in glyphs:
        blit(rows, sprite(name), *place(element, md, scale))
    return rows


def crop_upscale(rows, scale, x0, y0, x1, y1):
    """Cut a GUI-pixel box out and enlarge it x scale (nearest), as the screen does."""
    gy, gx = len(rows), len(rows[0]) // 3
    x0, y0, x1, y1 = max(0, x0), max(0, y0), min(gx, x1), min(gy, y1)
    out = []
    for y in range(y0, y1):
        r = rows[y]
        wide = b"".join(bytes(r[x * 3:x * 3 + 3]) * scale for x in range(x0, x1))
        out.extend([wide] * scale)
    return out, (x1 - x0) * scale


def png_of(rows, scale, box=None):
    if box is None:
        box = (0, 0, len(rows[0]) // 3, len(rows))
    cropped, w = crop_upscale(rows, scale, *box)
    return png_write_rgb(cropped, w)


def crops(scale, md, state):
    """Crop boxes (GUI px) of the top plate and the right panel for a state."""
    gx, gy = gui_size(scale)
    f = factor(md, scale)
    top = (gx // 2 - math.ceil(132 * f), 0, gx // 2 + math.ceil(132 * f), math.ceil(52 * f))
    low = math.ceil((92 if state.against >= 0 else 60) * f)
    panel = (gx - math.ceil(140 * f), gy // 2 - math.ceil(60 * f), gx, gy // 2 + low)
    return top, panel


# ------------------------------------------------------------------------------------------
# The states

def default_seats(n=12, me=3, dead=(1, 7), ballot=None, mates=(), lover=None):
    return tuple(seats(range(n), {i for i in range(n) if i not in dead}, me, ballot, lover, mates))


def fx(**kw):
    """Facts with consistent seats/badges (12 players, you are seat 3 unless told) and totals."""
    seat_kw = {k: kw.pop(k) for k in ("n", "me", "dead", "ballot", "mates", "lover") if k in kw}
    n = seat_kw.get("n", 12)
    dead = seat_kw.get("dead", (1, 7) if n > 7 else ())
    f = Facts(**kw)
    assert f.seats or ("alive" not in kw and "total" not in kw), "fx(): alive/total are derived from n/dead unless seats= is given"
    if not f.seats:
        f.seats = default_seats(n, seat_kw.get("me", 3), dead, seat_kw.get("ballot"), seat_kw.get("mates", ()),
                                seat_kw.get("lover"))
        f.total = n
        f.alive = n - len(dead)   # a table built here has these totals; pass `seats=` to choose your own
    return f


@dataclass
class Scenario:
    category: str
    sid: str
    title: str
    note: str
    facts: Facts
    scene: bool = False        # also a full-screen image
    night: bool = None         # background; default follows facts.night
    mode: int = None
    scale: int = None


def scenarios():
    S = []

    def add(cat, sid, title, note, f, **kw):
        S.append(Scenario(cat, sid, title, note, f, **kw))

    # --- night calls: the viewer is the actor of each call
    calls = [("CUPID", "CUPID"), ("GUARD", "GUARD"), ("SEER", "SEER"), ("NIGHT", "WEREWOLF"), ("WITCH", "WITCH"),
             ("MINION", "WEREWOLF"), ("AMULET", "VILLAGER")]
    for phase, role in calls:
        extra = {}
        if role == "WITCH":
            extra["badges"] = ("life", "death")
        if role == "GUARD":
            extra["badges"] = ("guard",)
        add("Night calls", "night-" + phase.lower(), f"Night call: {phase}", f"you are the {role}, you are called",
            fx(phase=phase, role=role, night=True, actor=True, seconds=24, **extra), scene=phase in ("SEER", "NIGHT"))
    for phase, (rid, r) in sorted(BY_PHASE.items(), key=lambda kv: kv[1][0]):
        if phase in ("MARTYR", "MINION"):
            continue
        f = fx(phase=phase, role=rid, night=True, actor=True, seconds=30)
        if rid == "OLD_HAG":
            f.badges = ()
        add("Night calls", "night-" + phase.lower(), f"Night call: {phase}", f"you are {r['name']}; label {r['call']['label']}", f)
    add("Night calls", "night-sleeping", "Asleep, full reveal", "not called: clock shown (reveal FULL)",
        fx(phase="SEER", role="VILLAGER", night=True, actor=False, seconds=41), scene=True)
    add("Night calls", "night-sleeping-hidden", "Asleep, hidden reveal", "not called, reveal != FULL: no clock at all",
        fx(phase="SEER", role="VILLAGER", night=True, actor=False, reveal="TEAM"))
    add("Night calls", "night-wolf-asleep", "Werewolf, not their turn", "wolf asleep during the seer's call",
        fx(phase="SEER", role="WEREWOLF", night=True, mates=(5, 9)))
    add("Night calls", "night-minion-viewer", "Minion asleep", "the picked minion: team word 'WOLF SIDE'",
        fx(phase="SEER", role="MINION", night=True, minion=True, mates=(5, 9)))

    # --- day phases
    day = [("DISCUSSION", "Debate", "VILLAGER", {}), ("DAY_VOTE", "Vote (no ballots against)", "VILLAGER", {"votes_against": 0}),
           ("DAY_VOTE", "Vote (3 ballots against)", "SEER", {"votes_against": 3}),
           ("DAY_VOTE", "Vote (12 ballots against)", "HUNTER", {"votes_against": 12}),
           ("ELECTION", "Mayor election", "VILLAGER", {}), ("DEFENSE", "Nominee's defense", "VILLAGER", {}),
           ("TRIAL", "Trial: thumbs up or down", "GUARD", {}), ("FINISHED", "Game over", "VILLAGER", {"seconds": 0})]
    for i, (phase, title, role, kw) in enumerate(day):
        add("Day phases", f"day-{phase.lower()}-{i}", title, f"phase {phase}, you are {role}",
            fx(phase=phase, role=role, **{"seconds": 75, **kw}), scene=i in (0, 2, 7))
    add("Day phases", "day-vote-hidden", "Vote, tally hidden (secret ballots)", "no 'votes against you' plate",
        fx(phase="DAY_VOTE", role="VILLAGER", tally_visible=False, ballot=5))
    add("Day phases", "day-vote-ballot", "Vote with a ballot cast", "seat 5 shows the ballot dot",
        fx(phase="DAY_VOTE", role="WITCH", ballot=5, votes_against=2, badges=("life",)))
    add("Day phases", "day-martyr-actor", "Martyr pause (you are the martyr)", "label MARTYR, hunter palette",
        fx(phase="MARTYR", role="MARTYR", actor=True, seconds=14))
    add("Day phases", "day-martyr-other", "Martyr pause (everybody else)", "label VOTE, neutral vote palette",
        fx(phase="MARTYR", role="VILLAGER", seconds=14))
    add("Day phases", "day-hunter-shot", "Hunter's last shot (by day, you)", "label LAST SHOT",
        fx(phase="HUNTER_SHOT", role="HUNTER", actor=True, seconds=12, dead=(1, 7, 3)))
    add("Day phases", "day-hunter-others", "Hunter's last shot (by day, others)", "viewer is not the actor: still day label",
        fx(phase="HUNTER_SHOT", role="VILLAGER", seconds=12))
    add("Day phases", "night-hunter-asleep", "Hunter's shot at night, you sleep", "label SLEEPING",
        fx(phase="HUNTER_SHOT", role="VILLAGER", night=True, seconds=12))
    add("Day phases", "night-hunter-actor", "Hunter's shot at night, you shoot", "label LAST SHOT, moon icon",
        fx(phase="HUNTER_SHOT", role="HUNTER", night=True, actor=True, seconds=12))
    add("Day phases", "day-away", "Away from the village", "sent away for the day: label AWAY, sleep palette",
        fx(phase="DISCUSSION", role="VILLAGER", exiled=True))
    add("Day phases", "day-waiting", "Waiting call (unknown phase)", "night actor of a phase without a label",
        fx(phase="UNKNOWN", role="VILLAGER", night=True, actor=True))
    add("Day phases", "day-round9", "Round 9 / round 12 clamp", "the round digit is clamped to 0-9",
        fx(phase="DISCUSSION", round=12))

    # --- badges
    one = [("lover", {"in_love": True}), ("life", {"believed": "WITCH", "life": True}),
           ("death", {"believed": "WITCH", "death": True}), ("infect", {"believed": "INFECTOR", "infect_left": True}),
           ("guard", {"believed": "GUARD", "last_protected": True}), ("mayor", {"mayor": True}),
           ("silenced", {"silenced": True})]
    for name, kw in one:
        b = badges(**kw)
        role = kw.get("believed", "VILLAGER")
        add("Badges", "badge-" + name, "Badge: " + name, "worn alone", fx(role=role, badges=tuple(b), phase="DISCUSSION"))
    add("Badges", "badge-four", "Four badges (the panel's maximum)", "lover + mayor + silenced + life; the 5th is dropped",
        fx(role="WITCH", badges=tuple(badges(in_love=True, mayor=True, silenced=True, believed="WITCH", life=True, death=True))))
    add("Badges", "badge-away-silenced", "Silenced and sent away", "silenced is hidden while exiled",
        fx(role="VILLAGER", exiled=True, badges=tuple(badges(silenced=True, exiled=True))))
    add("Badges", "badge-dead", "Dead: no badges", "badges() returns nothing for the dead",
        fx(role="WITCH", dead=(1, 7, 3), badges=()))

    # --- danger flash
    for secs, label in [(7, "7 s: calm"), (5, "5 s: odd, plate stays calm"), (4, "4 s: even, plate flashes red"),
                        (3, "3 s: odd, calm plate, red clock"), (0, "0 s: red")]:
        add("Danger flash", f"danger-{secs}", "Danger flash: " + label, "clock turns red at <= 5 s; the plate blinks on even seconds",
            fx(phase="SEER", role="SEER", night=True, actor=True, seconds=secs))
    add("Danger flash", "danger-vote", "Danger on the vote plate", "vote with 4 s left, 'votes against' goes red when hot",
        fx(phase="DAY_VOTE", role="VILLAGER", seconds=4, votes_against=5))
    add("Danger flash", "danger-clock-max", "Clock bounds", "9:59 is the maximum shown",
        fx(phase="DISCUSSION", seconds=5000))

    # --- dead
    add("Dead player", "dead-day", "Dead, by day", "palette dead, icon dead, word FALLEN, team OUT",
        fx(phase="DISCUSSION", role="WEREWOLF", dead=(1, 7, 3)), scene=True)
    add("Dead player", "dead-night", "Dead, at night", "label SLEEPING (not an actor)",
        fx(phase="SEER", role="SEER", night=True, dead=(1, 7, 3)))
    add("Dead player", "dead-vote", "Dead during the vote", "no 'votes against' plate for the dead",
        fx(phase="DAY_VOTE", role="GUARD", dead=(1, 7, 3), votes_against=4))
    add("Dead player", "dead-finished", "Dead at game over", "", fx(phase="FINISHED", role="VILLAGER", dead=(1, 7, 3), seconds=0))

    # --- seat strips
    for n in (4, 6, 8, 12, 16, 20, 24):
        add("Seat strips", f"seats-{n}", f"Table of {n}", "seat dots: you, dead, mates, ballot" if n != 24 else "more than 20: the first 20 dots only",
            fx(phase="DAY_VOTE", role="WEREWOLF", n=n, me=2, dead=tuple(i for i in (1, 5, 9) if i < n), ballot=min(n - 1, 4),
               mates=(0, 6) if n > 6 else (0,), lover=3 if n > 4 else None, votes_against=1))
    add("Seat strips", "seats-allalive", "Everybody alive", "", fx(role="VILLAGER", n=10, dead=()))
    add("Seat strips", "seats-nearlyall-dead", "Nearly everybody dead", "",
        fx(role="VILLAGER", n=10, dead=tuple(i for i in range(10) if i not in (3, 8))))

    # --- sizes (full scenes)
    for md, name in ((79, "large"), (80, "medium"), (81, "small")):
        for sc in (2, 3):
            add("Sizes", f"size-{name}-gui{sc}", f"Mode {name}, GUI scale {sc}", f"red channel {md}",
                fx(phase="DAY_VOTE", role="SEER", votes_against=3, badges=("mayor",)), scene=True, mode=md, scale=sc)
    for sc in (2, 3, 4):
        add("Sizes", f"size-auto-gui{sc}", f"Mode auto (78), GUI scale {sc}", "the HUD follows the window",
            fx(phase="DAY_VOTE", role="SEER", votes_against=3, badges=("mayor",)), scene=True, mode=78, scale=sc)
    return S


def role_scenarios():
    """Every role: its panel in the debate, plus its call label if any."""
    out = []
    for rid, r in ROLES.items():
        b = tuple(badges(believed=rid, life=True, death=True, infect_left=True, last_protected=True))
        out.append(Scenario("Roles", "role-" + rid.lower(), f"{r['name']} ({rid})",
                            f"palette {r['palette']} · icon {r['icon']} · word {r['word']} · team {layout.team_word(r)}",
                            fx(phase="DISCUSSION", role=rid, badges=b, mates=(5, 9) if r["team"] == "wolves" else ()), ))
    return out


def palette_scenarios():
    out = []
    for p in layout.HUD_PALETTES:
        f = fx(phase="DISCUSSION", role="VILLAGER")
        out.append((p, f))
    return out


# ------------------------------------------------------------------------------------------
# Output

def render_all(scale, md, outdir, log=print):
    outdir.mkdir(parents=True, exist_ok=True)
    (outdir / "states").mkdir(exist_ok=True)
    (outdir / "scenes").mkdir(exist_ok=True)
    bgs = {}

    def bg(sc, night):
        if (sc, night) not in bgs:
            bgs[(sc, night)] = background(sc, night)
        return bgs[(sc, night)]

    items = []
    blobs = {}   # sha -> png bytes

    def keep(name, png, folder):
        sha = hashlib.sha1(png).hexdigest()[:12]
        blobs[sha] = png
        (outdir / folder / name).write_bytes(png)
        return sha

    for sc_ in scenarios() + role_scenarios():
        sc = sc_.scale or scale
        m = sc_.mode or md
        st = state_of(sc_.facts)
        glyphs = render(st, m, strict=True)
        night = sc_.facts.night if sc_.night is None else sc_.night
        rows = compose(glyphs, sc, bg(sc, night))
        top_box, panel_box = crops(sc, m, st)
        item = {"category": sc_.category, "id": sc_.sid, "title": sc_.title, "note": sc_.note,
                "palette": st.palette, "label": st.label, "role_palette": st.role_palette,
                "mode": m, "scale": sc, "factor": factor(m, sc), "state": st.__dict__ | {"seats": list(st.seats), "badges": list(st.badges)},
                "glyphs": [f"{e}:{n}" for _, e, n, _ in glyphs]}
        if sc_.category != "Sizes":
            item["top"] = keep(f"{sc_.sid}-top.png", png_of(rows, sc, top_box), "states")
            if st.show_role:
                item["panel"] = keep(f"{sc_.sid}-panel.png", png_of(rows, sc, panel_box), "states")
        if sc_.scene:
            item["scene"] = keep(f"{sc_.sid}.png", png_of(rows, sc), "scenes")
        items.append(item)

    swatches = []
    for p, f in palette_scenarios():
        s = state_of(f)
        s = State(**{**s.__dict__, "palette": p, "role_palette": p, "against": -1, "timed": True, "seconds": 61, "label": "debate"})
        rows = compose(render(s, md, strict=True), scale, bg(scale, True))
        top_box, panel_box = crops(scale, md, s)
        swatches.append({"palette": p, "color": "#%06x" % layout.HUD_PALETTES[p],
                         "top": keep(f"palette-{p}-top.png", png_of(rows, scale, top_box), "states"),
                         "panel": keep(f"palette-{p}-panel.png", png_of(rows, scale, panel_box), "states")})
    return items, swatches, blobs


CSS = """
:root{--bg:#14121f;--card:#1e1b2e;--ink:#ece7f7;--dim:#9a93b5;--line:#352f52;--acc:#ffd36e}
@media (prefers-color-scheme:light){:root{--bg:#f3f1f8;--card:#fff;--ink:#1d1830;--dim:#605a78;--line:#d7d2e6;--acc:#a8650a}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.45 system-ui,sans-serif}
header,section{max-width:1500px;margin:0 auto;padding:16px}h1{margin:0 0 4px;font-size:22px}h2{margin:28px 0 4px;font-size:18px;color:var(--acc);border-bottom:1px solid var(--line);padding-bottom:4px}
p.meta{color:var(--dim);margin:2px 0 10px}nav a{color:var(--acc);margin-right:12px;text-decoration:none}
.grid{display:flex;flex-wrap:wrap;gap:12px}.card{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:10px;max-width:100%}
.card h3{margin:0;font-size:14px}.card .n{color:var(--dim);font-size:12px;margin-bottom:6px}.card .tags{font-size:12px;margin:4px 0}
.tag{display:inline-block;border:1px solid var(--line);border-radius:10px;padding:0 7px;margin-right:4px;color:var(--dim)}
.row{display:flex;gap:10px;align-items:flex-start;flex-wrap:wrap}img{image-rendering:pixelated;max-width:100%;height:auto;display:block;border-radius:4px}
img.scene{width:100%;max-width:960px}details{font-size:11px;color:var(--dim);margin-top:4px}details code{display:block;white-space:normal;word-break:break-all}
.sw{display:inline-block;width:10px;height:10px;border-radius:2px;vertical-align:middle;margin-right:4px}
"""


def build_html(items, swatches, blobs, scale, md, path):
    def uri(sha):
        return "data:image/png;base64," + base64.b64encode(blobs[sha]).decode()

    cats = []
    for it in items:
        if it["category"] not in cats:
            cats.append(it["category"])
    out = [f"<!doctype html><html lang=en><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
           f"<title>HUD Sheet</title><style>{CSS}</style></head><body><header><h1>Custom HUD contact sheet</h1>"
           f"<p class=meta>Every state of the werewolf HUD, composited from the real sprites with the core shader's placement "
           f"(GUI scale {scale}, mode {md}; 1 sprite texel = 1 GUI pixel, 1920x1080 screen). {len(items)} states, {len(swatches)} palettes. "
           f"Each caption gives the state id so adjustments can be listed by id.</p><nav>"]
    out += [f"<a href='#{html.escape(c.lower().replace(' ', '-'))}'>{html.escape(c)}</a>" for c in cats + ["Palettes"]]
    out.append("</nav></header>")
    for c in cats:
        out.append(f"<section id='{html.escape(c.lower().replace(' ', '-'))}'><h2>{html.escape(c)}</h2><div class=grid>")
        for it in [i for i in items if i["category"] == c]:
            out.append("<div class=card>")
            out.append(f"<h3>{html.escape(it['title'])}</h3><div class=n>{html.escape(it['id'])} — {html.escape(it['note'])}</div>")
            out.append(f"<div class=tags><span class=tag>plate: {it['palette']}</span><span class=tag>label: {html.escape(it['label'])}</span>"
                       f"<span class=tag>panel: {it['role_palette']}</span><span class=tag>mode {it['mode']}·GUI {it['scale']}·x{it['factor']}</span></div>")
            if "scene" in it and c == "Sizes":
                out.append(f"<img class=scene alt='{html.escape(it['id'])}' src='{uri(it['scene'])}'>")
            else:
                out.append("<div class=row>")
                if "top" in it:
                    out.append(f"<img alt='top plate' src='{uri(it['top'])}'>")
                if "panel" in it:
                    out.append(f"<img alt='right panel' src='{uri(it['panel'])}'>")
                out.append("</div>")
                if "scene" in it:
                    out.append(f"<details><summary>full screen</summary><img class=scene alt='scene' src='{uri(it['scene'])}'></details>")
            out.append(f"<details><summary>glyphs ({len(it['glyphs'])})</summary><code>{html.escape(' '.join(it['glyphs']))}</code></details></div>")
        out.append("</div></section>")
    out.append("<section id=palettes><h2>Palettes</h2><p class=meta>The 15 role/phase palettes (HUD_PALETTES): top plate and right panel.</p><div class=grid>")
    for s in swatches:
        out.append(f"<div class=card><h3><span class=sw style='background:{s['color']}'></span>{s['palette']} {s['color']}</h3><div class=row>"
                   f"<img src='{uri(s['top'])}' alt=''><img src='{uri(s['panel'])}' alt=''></div></div>")
    out.append("</div></section></body></html>")
    text = "".join(out)
    path.write_text(text)
    return len(text)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--scale", type=int, default=3, choices=(1, 2, 3, 4), help="GUI scale of the 1920x1080 screen")
    ap.add_argument("--mode", default="auto", choices=("auto", "large", "medium", "small"))
    ap.add_argument("--out", default=str(ROOT / "resource-pack" / "dist" / "hud-sheet"))
    a = ap.parse_args(argv)
    if not (SPRITES / "frame_wolf.png").exists():
        sys.exit("HUD sprites missing: run python3 resource-pack/art/hud.py first")
    outdir = pathlib.Path(a.out)
    items, swatches, blobs = render_all(a.scale, mode(a.mode), outdir)
    size = build_html(items, swatches, blobs, a.scale, mode(a.mode), outdir / "index.html")
    (outdir / "states.json").write_text(json.dumps({"scale": a.scale, "mode": a.mode, "states": items, "palettes": swatches}, indent=1))
    print(f"{len(items)} states, {len(swatches)} palettes, {len(blobs)} images; {outdir / 'index.html'} = {size / 1e6:.2f} MB")
    if size > 8e6:
        sys.exit("index.html exceeds 8 MB")


if __name__ == "__main__":
    main()
