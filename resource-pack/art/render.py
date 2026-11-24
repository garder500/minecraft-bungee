#!/usr/bin/env python3
"""Paints the menu backgrounds and icons into PNG textures (developer tool).

Needs headless Google Chrome (or Chromium) and network access for Google Fonts.
The resulting PNGs are committed under ../pack/; the build (scripts/build_pack.py)
needs only the standard library.

    python3 resource-pack/art/render.py [name ...]
"""
import concurrent.futures, json, os, pathlib, shutil, subprocess, sys, tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import layout

HERE = pathlib.Path(__file__).resolve().parent
PACK = HERE.parent / "pack" / "assets" / "vampyuyu"
SCALE = layout.PX_PER_GUI / layout.CSS_PER_GUI  # css px -> texture px (4 texture px per GUI px)

CHROME = os.environ.get("CHROME") or next(
    (c for c in ("google-chrome", "chromium", "chromium-browser") if shutil.which(c)), None)

FONTS = ('<link href="https://fonts.googleapis.com/css2?family=Cinzel:wght@500;700&'
         'family=Pixelify+Sans:wght@400;600;700&display=swap" rel="stylesheet">')

def stars(h, n, seed):
    import random
    r = random.Random(seed)
    return "".join(
        f'<circle cx="{r.randint(8, 520)}" cy="{r.randint(6, h)}" r="{r.choice([1, 1.1, 1.3, 1.5])}" '
        f'fill="#fff" opacity="{r.choice([.4, .6, .8, .9])}"/>' for _ in range(n))

def text(x, y, w, t, size=14, color="#fff", serif=False, align="center", ls=0, extra=""):
    ff = "font-family:'Cinzel',serif;font-weight:700;" if serif else ""
    return (f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;text-align:{align};{ff}'
            f'font-size:{size}px;letter-spacing:{ls}px;color:{color};text-shadow:0 2px 6px rgba(5,5,30,.95);{extra}">{t}</div>')

def title(name=""):
    """Painted brand prefix; the plugin appends the dynamic part of the title at GUI x=44."""
    tail = f' <span style="color:#e9d99b">{name}</span>' if name else ""
    return ('<div style="position:absolute;left:24px;top:15px;font-size:20px;text-shadow:0 2px 6px rgba(5,5,30,.9)">'
            '<span style="color:#ff9aa0;font-weight:600">Vampyuyu</span> '
            f'<span style="color:#c5a69b">·</span>{tail}</div>')

def frame(w, h, svg, body):
    """Square-cornered full-bleed GUI texture (the container bitmap sits over the vanilla panel)."""
    return f'''<!doctype html><html><head><meta charset="utf-8">{FONTS}
<style>html,body{{margin:0;background:#120b2a}}</style></head><body>
<div style="position:relative;width:{w}px;height:{h}px;overflow:hidden;font-family:'Pixelify Sans',monospace;background:#120b2a">
<svg style="position:absolute;left:0;top:0" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{svg}
<rect x="1.5" y="1.5" width="{w-3}" height="{h-3}" fill="none" stroke="#14121f" stroke-width="3"/>
<rect x="4.5" y="4.5" width="{w-9}" height="{h-9}" fill="none" stroke="#fff" stroke-opacity=".28" stroke-width="1.5"/></svg>
{body}</div></body></html>'''

def lobby():
    """/menu: 54-slot container, 176x222 GUI px. Slots: rows y=51+54r, cols x=21+54c.
    Rows 0-2 are the worlds (WorldSelector.CREATIVE_AREA...), rows 3-5 the games (WEREWOLF_AREA, VALOBLOCK_AREA)."""
    W, H = 528, 666
    svg = f'''<defs>
<linearGradient id="dusk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#150e2e"/><stop offset=".22" stop-color="#46246a"/><stop offset=".36" stop-color="#8a4a7a"/><stop offset=".55" stop-color="#1b1038"/><stop offset="1" stop-color="#0c0720"/></linearGradient>
<linearGradient id="cs" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7a4fa8"/><stop offset=".6" stop-color="#ee9a7e"/><stop offset="1" stop-color="#ffd6a0"/></linearGradient>
<linearGradient id="ss" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0f2638"/><stop offset=".65" stop-color="#2f6b5c"/><stop offset="1" stop-color="#8ccf8a"/></linearGradient>
<radialGradient id="gl"><stop offset="0" stop-color="#ffe9b0" stop-opacity=".9"/><stop offset="1" stop-color="#ffb454" stop-opacity="0"/></radialGradient>
<radialGradient id="st"><stop offset="0" stop-color="#e7f1ff" stop-opacity=".95"/><stop offset=".4" stop-color="#a9c4ff" stop-opacity=".35"/><stop offset="1" stop-color="#7a8cff" stop-opacity="0"/></radialGradient>
<clipPath id="cc"><path d="M21 183 V132 a81 81 0 0 1 162 0 V183z"/></clipPath>
<clipPath id="sc"><path d="M345 183 V132 a81 81 0 0 1 162 0 V183z" transform="translate(-12 0)"/></clipPath></defs>
<rect width="{W}" height="{H}" fill="url(#dusk)"/>{stars(150, 18, 3)}
<g clip-path="url(#cc)"><rect x="21" y="51" width="162" height="132" fill="url(#cs)"/>
<circle cx="102" cy="140" r="40" fill="#fff0c8" opacity=".75"/>
<g fill="#4a2468"><rect x="30" y="150" width="26" height="33"/><rect x="58" y="136" width="22" height="47"/><rect x="84" y="160" width="30" height="23"/><rect x="116" y="128" width="24" height="55"/><rect x="142" y="148" width="30" height="35"/></g>
<g fill="#ffd27a"><rect x="36" y="160" width="5" height="6"/><rect x="45" y="172" width="5" height="6"/><rect x="64" y="148" width="5" height="6"/><rect x="64" y="164" width="5" height="6"/><rect x="92" y="170" width="5" height="6"/><rect x="122" y="140" width="5" height="6"/><rect x="122" y="160" width="5" height="6"/><rect x="150" y="160" width="5" height="6"/></g>
<g fill="#fff" opacity=".7"><rect x="52" y="104" width="10" height="10" transform="rotate(18 57 109)"/><rect x="128" y="96" width="8" height="8" transform="rotate(-14 132 100)"/><rect x="92" y="82" width="7" height="7"/></g></g>
<path d="M21 183 V132 a81 81 0 0 1 162 0 V183" fill="none" stroke="#ffd7a8" stroke-width="2.5"/><path d="M27 183 V132 a75 75 0 0 1 150 0 V183" fill="none" stroke="#fff" stroke-opacity=".25"/>
<g transform="translate(12 0)"><g clip-path="url(#sc)"><rect x="333" y="51" width="162" height="132" fill="url(#ss)"/>
<circle cx="446" cy="86" r="12" fill="#f2f4d8" opacity=".9"/>
<path d="M333 183 V147 L370 107 L398 140 L428 95 L495 155 V183z" fill="#10303a"/>
<path d="M333 183 V165 L360 147 L388 167 L420 140 L495 172 V183z" fill="#0b2227"/>
<g fill="#07161a"><path d="M352 183 l10 -30 l10 30z"/><path d="M372 183 l12 -38 l12 38z"/><path d="M468 183 l10 -32 l10 32z"/></g>
<circle cx="426" cy="175" r="20" fill="url(#gl)"/><path d="M420 182 q6 -18 12 0z" fill="#ffb454"/><path d="M423 182 q3 -11 6 0z" fill="#fff0a8"/></g>
<path d="M333 183 V132 a81 81 0 0 1 162 0 V183" fill="none" stroke="#bfe6b0" stroke-width="2.5"/><path d="M339 183 V132 a75 75 0 0 1 150 0 V183" fill="none" stroke="#fff" stroke-opacity=".25"/></g>
{skyblock_card()}
{games_band()}
{lower_panel()}'''
    body = (title("Worlds")
            + text(21, 183, 162, "CREATIVE", 18, "#fff1e0", True, "center", 4)
            + text(21, 203, 162, "Build freely. Dream bigger.", 11, "#ffd7d2", extra="white-space:nowrap")
            + text(345, 183, 162, "SURVIVAL", 18, "#f2ffe8", True, "center", 4)
            + text(333, 203, 186, "Gather. Explore. Make it yours.", 11, "#d6f0c8", extra="white-space:nowrap")
            + text(195, 124, 138, "SKYBLOCK", 15, "#fffbe8", True, "center", 3)
            + text(195, 143, 138, "Build your island.", 11, "#cfe9ff", extra="white-space:nowrap")
            + text(195, 182, 138, "← Back to the lobby", 13, "#fff", extra="white-space:nowrap")
            + text(21, 333, 216, "WEREWOLF", 17, "#ffe6e0", True, "center", 4)
            + text(21, 353, 216, "Who hunts tonight?", 11, "#ffc2b8", extra="white-space:nowrap")
            + text(291, 333, 216, "VALOBLOCK", 17, "#e6fbff", True, "center", 4)
            + text(291, 353, 216, "Plant the Rift Seed.", 11, "#a8eeff", extra="white-space:nowrap"))
    return W, H, frame(W, H, svg, body)

def games_band():
    """Rows 3-5 of /menu: the Werewolf card on columns 0-3, ValoBlock on columns 5-8, a bat between them.
    Each card is a scene over a dark caption band (y 326-372) like the Skyblock card."""
    ww_x0, ww_x1, vb_x0, vb_x1, y0, y1, band = 21, 237, 291, 507, 226, 372, 326
    village = "".join(
        f'<rect x="{x}" y="{326 - h}" width="{w}" height="{h}" fill="#2a1430"/>'
        f'<path d="M{x - 3} {326 - h} l{w / 2 + 3:.0f} -12 l{w / 2 + 3:.0f} 12z" fill="#1c0c22"/>'
        f'<rect x="{x + w / 2 - 3:.0f}" y="{326 - h + 7}" width="6" height="7" fill="#ffc46a"/>'
        for x, w, h in [(132, 22, 22), (160, 28, 28), (194, 20, 18), (216, 18, 24)])
    wolf = ("M44 322 L50 314 L56 300 L66 289 L72 276 L76 266 L78 256 L82 262 L86 254 L96 249 L97 254 "
            "L88 264 L86 276 L90 296 L93 322 L86 322 L82 308 L70 314 L66 322Z")
    grid = "".join(f'<path d="M{vb_x0 + 18 * i} {y0} L{vb_x0 - 60 + 30 * i} {band}" stroke="#7fe8ff" stroke-opacity=".10"/>'
                   for i in range(15))
    grid += "".join(f'<path d="M{vb_x0} {y} H{vb_x1}" stroke="#7fe8ff" stroke-opacity="{.05 + .02 * k}"/>'
                    for k, y in enumerate([268, 284, 298, 310, 320]))
    return f'''<defs>
{grad('wwk', [(0, '#120a2e'), (.55, '#4a1a46'), (1, '#a8404a')])}
{grad('vbk', [(0, '#06121f'), (.6, '#0f3048'), (1, '#1a5a6e')])}
<radialGradient id="wwm"><stop offset="0" stop-color="#fff2d6" stop-opacity=".9"/><stop offset="1" stop-color="#ff9a8a" stop-opacity="0"/></radialGradient>
<radialGradient id="vbg"><stop offset="0" stop-color="#7fe8ff" stop-opacity=".6"/><stop offset="1" stop-color="#7fe8ff" stop-opacity="0"/></radialGradient>
<radialGradient id="vbr"><stop offset="0" stop-color="#ff5fa2" stop-opacity=".85"/><stop offset="1" stop-color="#ff5fa2" stop-opacity="0"/></radialGradient>
<clipPath id="wwc"><rect x="{ww_x0}" y="{y0}" width="{ww_x1 - ww_x0}" height="{y1 - y0}" rx="22"/></clipPath>
<clipPath id="vbc"><rect x="{vb_x0}" y="{y0}" width="{vb_x1 - vb_x0}" height="{y1 - y0}" rx="22"/></clipPath></defs>
<g clip-path="url(#wwc)"><rect x="{ww_x0}" y="{y0}" width="{ww_x1 - ww_x0}" height="{y1 - y0}" fill="url(#wwk)"/>
<g fill="#fff" opacity=".7"><circle cx="40" cy="240" r="1"/><circle cx="118" cy="236" r="1.2"/><circle cx="96" cy="262" r="1"/><circle cx="214" cy="244" r="1"/></g>
<circle cx="168" cy="268" r="46" fill="url(#wwm)"/><circle cx="168" cy="268" r="24" fill="#fff0d2"/>
<g fill="#e8cfa6" fill-opacity=".55"><circle cx="160" cy="262" r="5"/><circle cx="176" cy="276" r="6"/><circle cx="172" cy="256" r="2.5"/></g>
<g fill="#0c0612"><path d="M118 248 q-7 -7 -15 0 q7 1 9 7 q3 -6 6 -7z"/><path d="M206 238 q-6 -6 -13 0 q6 1 8 6 q2 -5 5 -6z"/></g>
<path d="M{ww_x0} 326 V306 Q60 296 110 310 T{ww_x1} 302 V326z" fill="#1a0a20"/>
{village}
<path d="M30 326 Q40 316 70 318 Q100 316 108 326z" fill="#0c0612"/>
<path d="{wolf}" fill="#0c0612"/>
<g fill="#ff4a4a"><circle cx="85" cy="259" r="1.3"/></g>
<rect x="{ww_x0}" y="{band}" width="{ww_x1 - ww_x0}" height="{y1 - band}" fill="#0a0820" fill-opacity=".62"/></g>
<rect x="{ww_x0}" y="{y0}" width="{ww_x1 - ww_x0}" height="{y1 - y0}" rx="22" fill="none" stroke="#f08a80" stroke-width="2.5"/>
<rect x="{ww_x0 + 6}" y="{y0 + 6}" width="{ww_x1 - ww_x0 - 12}" height="{y1 - y0 - 12}" rx="17" fill="none" stroke="#fff" stroke-opacity=".25"/>
<g clip-path="url(#vbc)"><rect x="{vb_x0}" y="{y0}" width="{vb_x1 - vb_x0}" height="{y1 - y0}" fill="url(#vbk)"/>
{grid}
<circle cx="452" cy="270" r="44" fill="url(#vbg)"/>
<circle cx="452" cy="270" r="22" fill="none" stroke="#bff6ff" stroke-width="2.5"/>
<circle cx="452" cy="270" r="13" fill="none" stroke="#bff6ff" stroke-opacity=".5" stroke-dasharray="3 4"/>
<path d="M452 240 V254 M452 286 V300 M422 270 H436 M468 270 H482" stroke="#bff6ff" stroke-width="2.5" stroke-linecap="round"/>
<circle cx="452" cy="270" r="2.5" fill="#ff5fa2"/>
<path d="M300 326 L326 296 L352 314 L378 288 L404 312 L436 300 L{vb_x1} 318 V326z" fill="#0a2232"/>
<g transform="translate(330 262)">
<circle cx="0" cy="22" r="26" fill="url(#vbr)"/>
<path d="M0 6 L14 22 L0 38 L-14 22Z" fill="#2a0f2a" stroke="#ff8cc0" stroke-width="2"/>
<path d="M0 13 L7 22 L0 31 L-7 22Z" fill="#ff5fa2"/>
<path d="M0 -2 V4 M-18 22 H-24 M18 22 H24" stroke="#ff8cc0" stroke-width="1.6" stroke-linecap="round"/></g>
<g fill="#cfe9f2" stroke="#06121f" stroke-width="1.2" stroke-linejoin="round">
<path d="M372 316 h62 v-8 h14 v-6 h-22 v-4 h-46 l-8 4 v6 h-8 v8z"/>
<path d="M386 316 l-6 10 h10 l4 -10z" fill="#9fc3cf"/></g>
<rect x="{vb_x0}" y="{band}" width="{vb_x1 - vb_x0}" height="{y1 - band}" fill="#0a0820" fill-opacity=".62"/></g>
<rect x="{vb_x0}" y="{y0}" width="{vb_x1 - vb_x0}" height="{y1 - y0}" rx="22" fill="none" stroke="#7fe8ff" stroke-width="2.5"/>
<rect x="{vb_x0 + 6}" y="{y0 + 6}" width="{vb_x1 - vb_x0 - 12}" height="{y1 - y0 - 12}" rx="17" fill="none" stroke="#fff" stroke-opacity=".25"/>
<path d="M264 236 V284 M264 314 V362" stroke="#e9d99b" stroke-opacity=".35" stroke-width="1.5"/>
<path d="M264 296 q-6 -8 -16 -4 q6 2 7 8 q4 -3 9 -2 q5 -1 9 2 q1 -6 7 -8 q-10 -4 -16 4z" fill="#e9d99b" fill-opacity=".8"/>'''

def skyblock_card():
    """Centre of /menu (slots 3-5 of rows 0-1: x 183..345, y 51..159): a floating island with a tree in a
    dawn sky, a dark glass band under it for the static caption. Mirrors the arches' double outline."""
    x0, x1, y0, y1 = 195, 333, 51, 159
    clouds = "".join(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="#fff" fill-opacity="{o}"/>'
                     for cx, cy, rx, ry, o in [(222, 74, 13, 5, .85), (232, 72, 9, 5, .85), (312, 96, 12, 4.5, .7),
                                                (304, 94, 8, 4.5, .7), (214, 112, 10, 3.5, .55), (252, 116, 18, 4, .6),
                                                (290, 114, 14, 3.5, .5)])
    sparks = "".join(f'<path d="M{x} {y - s}l{s * .35:.1f} {s * .65:.1f} {s * .65:.1f} {s * .35:.1f}-{s * .65:.1f} {s * .35:.1f}-{s * .35:.1f} {s * .65:.1f}-{s * .35:.1f}-{s * .65:.1f}-{s * .65:.1f}-{s * .35:.1f} {s * .65:.1f}-{s * .35:.1f}z" fill="#fff"/>'
                     for x, y, s in [(309, 64, 4), (216, 94, 3), (322, 84, 2.5)])
    return f'''<defs>
<linearGradient id="skd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2d4fa8"/><stop offset=".55" stop-color="#78b4ee"/><stop offset="1" stop-color="#ffd9b0"/></linearGradient>
<radialGradient id="skg"><stop offset="0" stop-color="#fff3c4" stop-opacity=".95"/><stop offset="1" stop-color="#ffd27a" stop-opacity="0"/></radialGradient>
<clipPath id="skc"><rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" rx="22"/></clipPath></defs>
<g clip-path="url(#skc)"><rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="url(#skd)"/>
<circle cx="310" cy="76" r="30" fill="url(#skg)"/><circle cx="310" cy="76" r="8.5" fill="#fff6d0"/>
{clouds}{sparks}
<g transform="translate(264 92) scale(.78) translate(-264 -92)">
<path d="M224 93 H304 C302 104 292 111 282 117 C276 121 270 125 264 128 C258 125 252 121 246 117 C236 111 226 104 224 93Z" fill="#8a5a3a" stroke="#3a2415" stroke-width="1.6" stroke-linejoin="round"/>
<path d="M236 102 H292 M246 111 H282" stroke="#a87650" stroke-width="2" stroke-linecap="round"/>
<g fill="#8f8fa0"><rect x="244" y="104" width="8" height="5"/><rect x="274" y="108" width="7" height="5"/><rect x="258" y="116" width="9" height="5"/></g>
<path d="M220 93 C220 87 228 85 264 85 C300 85 308 87 308 93 C308 97 304 98 300 96 L290 98 L280 96 L270 99 L258 96 L246 98 L236 96 C226 99 220 97 220 93Z" fill="#6bbf4e" stroke="#2f6f2f" stroke-width="1.6" stroke-linejoin="round"/>
<path d="M228 89 C250 86 278 86 300 89" stroke="#a3e87a" stroke-width="2" fill="none" stroke-linecap="round"/>
<rect x="261" y="68" width="7" height="19" fill="#6b4423" stroke="#3a2415" stroke-width="1.4"/>
<g stroke="#1f5f2a" stroke-width="1.5"><circle cx="252" cy="68" r="10" fill="#3f9e3f"/><circle cx="276" cy="68" r="10" fill="#3f9e3f"/><circle cx="264" cy="60" r="12" fill="#4fb84a"/><circle cx="264" cy="71" r="9" fill="#3f9e3f"/></g>
<g fill="#8ee06e"><circle cx="259" cy="56" r="3.5"/><circle cx="249" cy="64" r="2.6"/><circle cx="272" cy="63" r="2.6"/></g>
<g fill="#e84a5f"><circle cx="270" cy="72" r="1.7"/><circle cx="256" cy="66" r="1.7"/></g>
</g>
<rect x="{x0}" y="122" width="{x1 - x0}" height="{y1 - 122}" fill="#0a0820" fill-opacity=".55"/></g>
<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" rx="22" fill="none" stroke="#bfe6ff" stroke-width="2.5"/>
<rect x="{x0 + 6}" y="{y0 + 6}" width="{x1 - x0 - 12}" height="{y1 - y0 - 12}" rx="17" fill="none" stroke="#fff" stroke-opacity=".25"/>
{glass(195, 165, 138, 38, 19, .35)}'''

def grad(i, stops):
    return f'<linearGradient id="{i}" x1="0" y1="0" x2="0" y2="1">' + "".join(
        f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops) + "</linearGradient>"

def glass(x, y, w, h, r=14, op=.30):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="#0a0820" fill-opacity="{op}" '
            'stroke="#fff" stroke-opacity=".28"/>')

def pines(xs, y, h, fill):
    return "".join(f'<path d="M{x} {y} l{h*.28:.0f} {h} h{-h*.56:.0f} z" fill="{fill}"/>'
                   f'<path d="M{x} {y+h*.35:.0f} l{h*.34:.0f} {h*.75:.0f} h{-h*.68:.0f} z" fill="{fill}"/>' for x in xs)

def village(top, bottom, seed=5):
    """Lit night village behind the inventory half, so the lower window is scenery, not a blank panel."""
    import random
    r = random.Random(seed)
    base = top + (bottom - top) * .62
    out = f'<rect x="0" y="{top}" width="528" height="{bottom - top}" fill="#1a1030"/>'
    out += f'<ellipse cx="264" cy="{base}" rx="300" ry="60" fill="#ffb454" fill-opacity=".10"/>'
    x = 6
    while x < 520:
        w, h = r.randint(40, 64), r.randint(44, 84)
        out += (f'<rect x="{x}" y="{base - h:.0f}" width="{w}" height="{h}" fill="#34205a"/>'
                f'<path d="M{x - 5} {base - h:.0f} l{w / 2 + 5:.0f} -26 l{w / 2 + 5:.0f} 26z" fill="#22143f"/>')
        for wx in range(x + 9, x + w - 12, 18):
            for wy in range(int(base - h + 16), int(base - 14), 24):
                if r.random() < .7:
                    out += f'<rect x="{wx}" y="{wy}" width="9" height="12" fill="#ffd27a" fill-opacity=".9"/>'
        x += w + r.randint(8, 20)
    out += f'<rect x="0" y="{base:.0f}" width="528" height="{bottom - base:.0f}" fill="#120a24"/>'
    for _ in range(14):
        out += (f'<circle cx="{r.randint(10, 518)}" cy="{r.randint(int(top + 20), int(bottom - 20))}" r="2" '
                'fill="#fff0a8" fill-opacity=".8"/>')
    return out

def lower_panel(claw=False):
    """Painted bottom half of the 54-slot window: the player-inventory area (GUI y 140-222).
    Faint slot outlines sit exactly under the vanilla slots (x=8+18c, y=140+18r, hotbar y=198)."""
    slots = "".join(
        f'<rect x="{24 + 54 * c}" y="{420 + 54 * r}" width="48" height="48" rx="7" fill="#0a0820" fill-opacity=".16" '
        'stroke="#e9d99b" stroke-opacity=".3" stroke-width="1.5"/>'
        for r in range(3) for c in range(9))
    hotbar = "".join(
        f'<rect x="{24 + 54 * c}" y="594" width="48" height="48" rx="7" fill="{"#c8453a" if claw and c == 8 else "#0a0820"}" fill-opacity="{.16}" '
        f'stroke="{"#f08a80" if claw and c == 8 else "#e9d99b"}" stroke-opacity="{.7 if claw and c == 8 else .22}" stroke-width="1.5"/>'
        for c in range(9))
    return (village(402, 666) + glass(15, 408, 498, 174, 16, .12) + slots + glass(15, 582, 498, 66, 16, .16) + hotbar)

def hills27():
    """Night hills and pines behind the inventory half of the 27-slot windows."""
    return village(236, 504, 9)

def lower_panel27():
    """Bottom half of the 27-slot window (176x168): inventory rows at GUI y=84/102/120, hotbar y=142."""
    slots = "".join(
        f'<rect x="{24 + 54 * c}" y="{252 + 54 * r}" width="48" height="48" rx="7" fill="#0a0820" fill-opacity=".16" '
        'stroke="#e9d99b" stroke-opacity=".3" stroke-width="1.5"/>'
        for r in range(3) for c in range(9))
    hotbar = "".join(
        f'<rect x="{24 + 54 * c}" y="426" width="48" height="48" rx="7" fill="#0a0820" fill-opacity=".16" '
        'stroke="#e9d99b" stroke-opacity=".3" stroke-width="1.5"/>' for c in range(9))
    return glass(15, 240, 498, 174, 16, .12) + slots + glass(15, 414, 498, 72, 16, .16) + hotbar

def setup():
    """/arena setup: 54-slot container (176x222). Rows y=51+54r; strips group the controls."""
    W, H = 528, 666
    svg = f"""<defs>{grad('sk', [(0, '#0b0e2e'), (.42, '#2b2260'), (.62, '#7a3a7a'), (.7, '#1a0f33'), (1, '#0c0720')])}
<radialGradient id="mg"><stop offset="0" stop-color="#fff6d8" stop-opacity=".8"/><stop offset="1" stop-color="#8aa0ff" stop-opacity="0"/></radialGradient>
<radialGradient id="gg"><stop offset="0" stop-color="#8fd16a" stop-opacity=".55"/><stop offset="1" stop-color="#8fd16a" stop-opacity="0"/></radialGradient>
<radialGradient id="rg"><stop offset="0" stop-color="#e6564a" stop-opacity=".55"/><stop offset="1" stop-color="#e6564a" stop-opacity="0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sk)"/>{stars(330, 26, 5)}
<circle cx="484" cy="28" r="60" fill="url(#mg)"/><circle cx="484" cy="28" r="14" fill="#fff4d6"/>
<path d="M0 420 C110 380 200 430 300 410 S450 390 528 420 V{H} H0z" fill="#1b1442"/>
{pines([20, 70, 130, 200, 300, 380, 440, 500], 430, 80, '#0f0a2a')}
<path d="M0 520 C120 500 230 530 340 515 S480 505 528 525 V{H} H0z" fill="#0b0720"/>
{''.join(glass(21, 53 + 54 * r, 486, 50, 14, .55) for r in range(4))}
<circle cx="{21 + 7 * 54 + 27}" cy="78" r="30" fill="url(#gg)"/><circle cx="{21 + 8 * 54 + 27}" cy="78" r="30" fill="url(#rg)"/>
<circle cx="{21 + 8 * 54 + 27}" cy="{51 + 4 * 54 + 27}" r="30" fill="url(#rg)"/>{lower_panel()}""" 
    caps = ["ARENA", "VILLAGE", "LIMITS", "RULES"]
    body = title() + "".join(text(28, 51 + 54 * i + 18, 50, c, 11, "#e9d99b", True, "left", 1) for i, c in enumerate(caps))
    return W, H, frame(W, H, svg, body)

def listing():
    """Paged list (warps, arenas, houses, doors): entries rows 0-3, nav + create on row 5."""
    W, H = 528, 666
    svg = f"""<defs>{grad('sk', [(0, '#1d1244'), (.4, '#7a3f7e'), (.58, '#f0a07a'), (.64, '#2a1448'), (1, '#0c0720')])}
<radialGradient id="sg"><stop offset="0" stop-color="#fff0c8" stop-opacity=".9"/><stop offset="1" stop-color="#ffb454" stop-opacity="0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sk)"/>{stars(180, 20, 9)}
<circle cx="264" cy="380" r="150" fill="url(#sg)"/>
<path d="M0 410 L70 340 L130 390 L210 310 L300 400 L380 330 L528 410 V{H} H0z" fill="#3a1f5a"/>
<path d="M0 470 L90 400 L170 460 L260 390 L350 470 L440 400 L528 460 V{H} H0z" fill="#241240"/>
<path d="M0 520 Q264 480 528 520 V{H} H0z" fill="#150a2c"/>
{glass(21, 51, 486, 219, 18)}
<rect x="171" y="326" width="186" height="42" rx="21" fill="#e9d99b" fill-opacity=".18" stroke="#e9d99b" stroke-opacity=".8" stroke-width="2"/>{lower_panel()}"""
    body = title() + text(171, 335, 186, "Create", 15, "#fff6d8")
    return W, H, frame(W, H, svg, body)

def detail():
    """Warp/house detail: 27 slots. Teleport portal on slot 13, side actions 11 and 15, back on 18."""
    W, H = 528, 504
    svg = f"""<defs>{grad('sk', [(0, '#0c2438'), (.3, '#2f6b7c'), (.5, '#f3c58c'), (.51, '#10303a'), (1, '#07161a')])}
<radialGradient id="pg"><stop offset="0" stop-color="#d8fff4" stop-opacity=".95"/><stop offset=".45" stop-color="#6fe0c4" stop-opacity=".5"/><stop offset="1" stop-color="#3fb59a" stop-opacity="0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sk)"/>{stars(120, 16, 4)}
<path d="M0 230 L100 170 L190 215 L280 150 L380 215 L460 170 L528 230 V{H} H0z" fill="#10303a"/>
<circle cx="264" cy="132" r="96" fill="url(#pg)"/>
<circle cx="264" cy="132" r="62" fill="none" stroke="#d8fff4" stroke-width="3"/><circle cx="264" cy="132" r="50" fill="none" stroke="#fff" stroke-opacity=".35" stroke-dasharray="3 6"/>
<circle cx="156" cy="132" r="32" fill="#fff" fill-opacity=".1" stroke="#fff" stroke-opacity=".4"/>
<circle cx="372" cy="132" r="32" fill="#f08a80" fill-opacity=".14" stroke="#f08a80" stroke-opacity=".6"/>{hills27()}{lower_panel27()}"""
    body = title()
    return W, H, frame(W, H, svg, body)

def confirm():
    """Destructive confirmation: 27 slots. Cancel on 19-21, confirm on 23-25. Same moonlit panel as the role
    menus, under an ember sky. No word is painted: the title names the action, the buttons' tooltips say
    Cancel / Confirm and that it cannot be undone (ArenaAdmin.confirm, CommunityFeatures)."""
    W, H = 528, 504
    n = NIGHT
    red, pale = "#e2584a", "#cdbfe3"
    svg = f"""<defs>{grad('sk', [(0, '#150a1c'), (.45, '#5a1626'), (.8, '#c9563f'), (1, '#e58a52')])}
<radialGradient id="eg"><stop offset="0" stop-color="#ffb070" stop-opacity=".75"/><stop offset="1" stop-color="#ff6a2c" stop-opacity="0"/></radialGradient>
<radialGradient id="vig" cx=".5" cy=".46" r=".72"><stop offset=".55" stop-color="{n["ink"]}" stop-opacity="0"/><stop offset="1" stop-color="{n["ink"]}" stop-opacity=".8"/></radialGradient>
<linearGradient id="sink" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{n["deep"]}" stop-opacity="0"/><stop offset="1" stop-color="{n["deep"]}"/></linearGradient>
<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#15112f"/><stop offset=".5" stop-color="{n["deep"]}"/><stop offset="1" stop-color="#07050f"/></linearGradient>
<linearGradient id="rail" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{n["gilt"]}"/><stop offset=".8" stop-color="{n["gold"]}" stop-opacity=".7"/><stop offset="1" stop-color="{n["gold"]}" stop-opacity="0"/></linearGradient>
<radialGradient id="aura"><stop offset="0" stop-color="{red}" stop-opacity=".32"/><stop offset="1" stop-color="{red}" stop-opacity="0"/></radialGradient>
<clipPath id="scene"><rect x="18" y="51" width="492" height="186"/></clipPath></defs>
<rect width="{W}" height="{H}" fill="url(#bg)"/>
<ellipse cx="264" cy="330" rx="250" ry="110" fill="url(#aura)"/>
<g clip-path="url(#scene)"><rect x="18" y="51" width="492" height="186" fill="url(#sk)"/>{stars(110, 14, 8)}
<circle cx="264" cy="104" r="110" fill="url(#eg)"/>
<path d="M18 237V196L70 160L118 190L176 150L236 186H292L352 150L410 190L458 160L510 196V237z" fill="#2a0d1c"/>
<path d="M18 237V214L96 190L170 212L264 196L358 212L432 190L510 214V237z" fill="#16081a"/>
<path d="M264 57l42 72h-84z" fill="#1a0a14" fill-opacity=".55" stroke="#f0b26a" stroke-width="3" stroke-linejoin="round"/><path d="M264 81v24M264 115.5v1.5" stroke="#f0b26a" stroke-width="4.5" stroke-linecap="round"/>
<rect x="18" y="51" width="492" height="186" fill="url(#vig)"/><rect x="18" y="204" width="492" height="33" fill="url(#sink)"/></g>
<path d="M16.5 237V49.5H511.5V237" fill="none" stroke="{n["ink"]}" stroke-width="3"/>
<path d="M19.5 237V52.5H508.5V237" fill="none" stroke="url(#rail)" stroke-width="1.5"/>
{_plaque(63, 141, 186, 90, pale, .85)}{_plaque(279, 141, 186, 90, red, .85)}
{''.join(_well(24 + 54 * c, 162, pale, .8) for c in (1, 2, 3))}{''.join(_well(24 + 54 * c, 162, red, .8) for c in (5, 6, 7))}
{_plaque(15, 243, 498, 168, None, .5)}
{_flourish(48, 480, 276, n["gilt"], red)}
<path d="M96 327H222M306 327H432" stroke="{n["gold"]}" stroke-width="1.5" stroke-opacity=".6"/>
<circle cx="264" cy="327" r="30" fill="none" stroke="{n["gold"]}" stroke-width="1.5" stroke-opacity=".7"/><circle cx="264" cy="327" r="24" fill="#110e28" stroke="{n["gilt"]}" stroke-opacity=".3" stroke-width="1.5"/>
{_crescent(264, 327, 15, "#110e28")}{_gem(87, 327, red)}{_gem(441, 327, red)}{_gem(231, 327, n["gilt"], 3)}{_gem(297, 327, n["gilt"], 3)}
{_flourish(48, 480, 378, n["gilt"], red)}
{_plaque(15, 420, 498, 72, None, .5)}{_flourish(48, 480, 456, n["gold"], red)}
{_shell(W, H, red)}"""
    return W, H, _page(W, H, svg)

def render_icons():
    """The menu and werewolf icons are 16x16 pixel art (stdlib, no Chrome): see skyblock_icon.py."""
    import skyblock_icon
    skyblock_icon.write_icons()

def compass():
    """In-game arena compass: heads on a council ring under a howling-wolf moon, controls on the bottom row."""
    W, H = 528, 666
    svg = f"""<defs>{grad('sk', [(0, '#080a24'), (.4, '#241c58'), (.58, '#5c3070'), (.66, '#120b2a'), (1, '#0a0618')])}
<radialGradient id="mg"><stop offset="0" stop-color="#fff6d8" stop-opacity=".8"/><stop offset="1" stop-color="#8aa0ff" stop-opacity="0"/></radialGradient>
<radialGradient id="fg"><stop offset="0" stop-color="#ffcf70" stop-opacity=".95"/><stop offset=".5" stop-color="#ff8a3c" stop-opacity=".35"/><stop offset="1" stop-color="#ff6a2c" stop-opacity="0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sk)"/>{stars(300, 22, 3)}
<circle cx="484" cy="28" r="60" fill="url(#mg)"/><circle cx="484" cy="28" r="14" fill="#fff4d6"/>
<g fill="#140d33"><path d="M0 330 V220 h30 v-26 q14 -26 28 0 v26 h36 v-40 q16 -30 32 0 v40 h20 V330z"/><path d="M528 330 V206 h-34 v-30 q-16 -28 -32 0 v30 h-40 v-24 q-14 -24 -28 0 v24 h-18 V330z"/></g>
<ellipse cx="264" cy="186" rx="236" ry="132" fill="#0a0820" fill-opacity=".45" stroke="#e9d99b" stroke-opacity=".55" stroke-width="2"/>
<ellipse cx="264" cy="186" rx="226" ry="122" fill="none" stroke="#fff" stroke-opacity=".12" stroke-dasharray="3 7"/>
<circle cx="264" cy="170" r="96" fill="url(#mg)"/><circle cx="264" cy="170" r="58" fill="#fff4d6"/>
<g fill="#e3d3a4" fill-opacity=".55"><circle cx="246" cy="152" r="9"/><circle cx="280" cy="186" r="12"/><circle cx="268" cy="140" r="5"/></g>
<path d="M214 252 Q264 214 314 252 V262 H214z" fill="#0b0720"/>
<path d="M246 238 l4 -22 l-8 -10 l12 2 l8 -12 l6 12 l12 -2 l-8 10 l4 22 l-9 -6 h-12z" fill="#05030f"/>
<g fill="#05030f"><path d="M150 96 q-10 -10 -22 0 q10 2 14 10 q4 -8 8 -10z"/><path d="M400 120 q-10 -10 -22 0 q10 2 14 10 q4 -8 8 -10z"/><path d="M360 70 q-8 -8 -18 0 q8 2 11 8 q3 -6 7 -8z"/></g>
<g fill="#ffcf70"><circle cx="60" cy="300" r="5"/><circle cx="468" cy="300" r="5"/></g>
<circle cx="60" cy="300" r="26" fill="url(#fg)"/><circle cx="468" cy="300" r="26" fill="url(#fg)"/>
<g fill="#05030f"><rect x="57" y="302" width="6" height="26"/><rect x="465" y="302" width="6" height="26"/></g>
<g fill="#ffd27a" fill-opacity=".9"><rect x="30" y="236" width="8" height="10"/><rect x="48" y="236" width="8" height="10"/><rect x="108" y="226" width="8" height="10"/><rect x="420" y="222" width="8" height="10"/><rect x="474" y="236" width="8" height="10"/></g>
<path d="M0 470 Q132 440 264 465 T528 450 V{H} H0z" fill="#0b0720"/>{pines([30, 90, 160, 260, 350, 430, 490], 470, 90, '#080518')}
<path d="M0 560 Q132 535 264 555 T528 545 V{H} H0z" fill="#06040f"/>{lower_panel(True)}"""
    body = title()
    return W, H, frame(W, H, svg, body)

def alembic():
    """Witch potion phase: same grid as the compass, a glowing flask instead of the campfire."""
    W, H = 528, 666
    svg = f"""<defs>{grad('sk', [(0, '#0b1030'), (.4, '#2b1f5e'), (.58, '#4a2a78'), (.66, '#120b2a'), (1, '#0a0618')])}
<radialGradient id="mg"><stop offset="0" stop-color="#fff6d8" stop-opacity=".8"/><stop offset="1" stop-color="#8aa0ff" stop-opacity="0"/></radialGradient>
<radialGradient id="pg"><stop offset="0" stop-color="#b78cff" stop-opacity=".7"/><stop offset="1" stop-color="#8a5ad8" stop-opacity="0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sk)"/>{stars(300, 22, 7)}
<circle cx="484" cy="28" r="60" fill="url(#mg)"/><circle cx="484" cy="28" r="14" fill="#fff4d6"/>
<ellipse cx="264" cy="186" rx="236" ry="132" fill="#0a0820" fill-opacity=".45" stroke="#b78cff" stroke-opacity=".6" stroke-width="2"/>
<circle cx="264" cy="190" r="70" fill="url(#pg)"/>
<path d="M240 150h48M246 150v36l-26 46a14 14 0 0 0 12 20h64a14 14 0 0 0 12-20l-26-46v-36" fill="none" stroke="#e9d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M232 214h64l12 22a8 8 0 0 1-7 12h-74a8 8 0 0 1-7-12z" fill="#b78cff" fill-opacity=".55"/>
<circle cx="252" cy="200" r="4" fill="#e9d4ff"/><circle cx="276" cy="190" r="3" fill="#e9d4ff"/>
<path d="M0 470 Q132 440 264 465 T528 450 V{H} H0z" fill="#0b0720"/>{pines([30, 90, 160, 260, 350, 430, 490], 470, 90, '#080518')}
<path d="M0 560 Q132 535 264 555 T528 545 V{H} H0z" fill="#06040f"/>{lower_panel(True)}"""
    return W, H, frame(W, H, svg, title())

def profile():
    """/profil: balance medallion on slot 4, event history card on rows 1-4, paging on row 5."""
    W, H = 528, 666
    svg = f"""<defs>{grad('sk', [(0, '#2d1b4e'), (.3, '#8a4a7a'), (.5, '#f5c06a'), (.56, '#3a1f5a'), (1, '#120a24')])}
<radialGradient id="cg"><stop offset="0" stop-color="#ffe9a8" stop-opacity=".95"/><stop offset="1" stop-color="#ffb454" stop-opacity="0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sk)"/>{stars(160, 22, 6)}
<circle cx="264" cy="78" r="70" fill="url(#cg)"/><circle cx="264" cy="78" r="30" fill="#e9b23a" fill-opacity=".35" stroke="#ffe9a8" stroke-width="2.5"/><circle cx="264" cy="78" r="24" fill="none" stroke="#fff" stroke-opacity=".4" stroke-dasharray="3 5"/>
{glass(21, 105, 486, 218, 16, .5)}
<path d="M0 450 Q264 400 528 450 V{H} H0z" fill="#3a1f5a"/><path d="M0 520 Q264 480 528 520 V{H} H0z" fill="#1a0e34"/>{lower_panel()}"""
    body = (title() + text(24, 66, 190, "EVENT HISTORY", 11, "#e9d99b", True, "left", 2)
            + text(318, 70, 190, "Global balance", 12, "#fff6d8", True, "left", 2))
    return W, H, frame(W, H, svg, body)

WEREWOLF = HERE / "werewolf"   # illustrations of the werewolf design guide (decors, icons, cards)

# ---------------------------------------------------------------------------------------------
# Moonlit dark-fantasy panel: the frame of the Werewolf role menus and of the confirmation window.
# No word is painted: the window title and the item tooltips carry every text (the language files of the
# plugins). All coordinates are CSS px (3 per GUI px); lines sit on multiples of 1.5 so they stay
# crisp at 4 texture px per GUI px.
NIGHT = {"ink": "#05040c", "deep": "#0b0920", "stone": "#15122e", "gold": "#b8935a", "gilt": "#e9d99b",
         "moon": "#e9e4c8"}

def _well(x, y, accent, floor=.78):
    """A bevelled slot well for the 48x48 item area whose top left corner is (x, y)."""
    n = NIGHT
    return (f'<rect x="{x - 3}" y="{y - 3}" width="54" height="54" fill="{n["ink"]}" fill-opacity=".6"/>'
            f'<rect x="{x}" y="{y}" width="48" height="48" fill="#090718" fill-opacity="{floor}"/>'
            f'<path d="M{x} {y}h48v3h-45v45h-3z" fill="#000" fill-opacity=".55"/>'
            f'<path d="M{x + 48} {y}v48h-48v-1.5h46.5v-46.5z" fill="#fff" fill-opacity=".16"/>'
            f'<rect x="{x - 2.25}" y="{y - 2.25}" width="52.5" height="52.5" fill="none" stroke="{accent}" stroke-width="1.5" stroke-opacity=".9"/>'
            + "".join(f'<rect x="{cx}" y="{cy}" width="3" height="3" fill="{n["gilt"]}"/>'
                      for cx in (x - 3, x + 48) for cy in (y - 3, y + 48)))

def _gem(x, y, color, r=4.5):
    return (f'<path d="M{x} {y - r}L{x + r} {y}L{x} {y + r}L{x - r} {y}Z" fill="{color}" stroke="{NIGHT["ink"]}" stroke-width="1.5"/>'
            f'<path d="M{x} {y - r + 1.5}L{x - r + 1.5} {y}" stroke="#fff" stroke-opacity=".7" stroke-width="1.5"/>')

def _plaque(x, y, w, h, tone=None, fill=.62):
    """A carved stone plaque: dark outline, a thin gilt (or tone) line, a bevel."""
    n = NIGHT
    line = tone or n["gold"]
    body = f"color-mix(in srgb,{tone} 18%,{n['stone']})" if tone else n["stone"]
    return (f'<rect x="{x + 1.5}" y="{y + 1.5}" width="{w - 3}" height="{h - 3}" fill="none" stroke="{n["ink"]}" stroke-width="3"/>'
            f'<rect x="{x + 3}" y="{y + 3}" width="{w - 6}" height="{h - 6}" fill="{n["ink"]}" fill-opacity="{fill}"/>'
            f'<rect x="{x + 3}" y="{y + 3}" width="{w - 6}" height="{h - 6}" style="fill:{body}" fill-opacity="{fill}"/>'
            f'<path d="M{x + 3} {y + 3}h{w - 6}v1.5h-{w - 7.5}v{h - 7.5}h-1.5z" fill="#fff" fill-opacity=".13"/>'
            f'<path d="M{x + w - 3} {y + 3}v{h - 6}h-{w - 6}v-1.5h{w - 7.5}v-{h - 7.5}z" fill="#000" fill-opacity=".45"/>'
            f'<rect x="{x + 6.75}" y="{y + 6.75}" width="{w - 13.5}" height="{h - 13.5}" fill="none" stroke="{line}" stroke-width="1.5" stroke-opacity=".75"/>')

def _flourish(x0, x1, y, color, accent):
    """A thin engraved line between x0 and x1 with a gem in its middle and a dot at each end."""
    mid = (x0 + x1) / 2
    return (f'<path d="M{x0 + 6} {y}H{mid - 12}M{mid + 12} {y}H{x1 - 6}" stroke="{color}" stroke-width="1.5" stroke-opacity=".6"/>'
            f'<rect x="{x0 + 1.5}" y="{y - 1.5}" width="3" height="3" fill="{color}"/><rect x="{x1 - 4.5}" y="{y - 1.5}" width="3" height="3" fill="{color}"/>'
            + _gem(mid, y, accent))

def _crescent(x, y, r, back):
    return (f'<circle cx="{x}" cy="{y}" r="{r}" fill="{NIGHT["moon"]}"/>'
            f'<circle cx="{x + r * .45:.2f}" cy="{y - r * .25:.2f}" r="{r * .82:.2f}" fill="{back}"/>')

def _shell(W, H, accent):
    """Opaque night stone to the window edge, the thin ornate frame and the title cartouche: a moon
    sigil where the old wordmark was, then a dark plate under the title text (GUI x=44, y=6..14)."""
    n = NIGHT
    corner = (f'<path d="M6.75 36V6.75H36" fill="none" stroke="{n["gilt"]}" stroke-width="3"/>'
              f'<path d="M13.5 28.5v-15h15" fill="none" stroke="{n["gold"]}" stroke-width="1.5"/>'
              + _gem(13.5, 13.5, accent, 6))
    corners = "".join(f'<g transform="translate({tx} {ty}) scale({sx} {sy})">{corner}</g>'
                      for tx, ty, sx, sy in [(0, 0, 1, 1), (W, 0, -1, 1), (0, H, 1, -1), (W, H, -1, -1)])
    return f'''<rect x="1.5" y="1.5" width="{W - 3}" height="{H - 3}" fill="none" stroke="{n["ink"]}" stroke-width="3"/>
<rect x="6.75" y="6.75" width="{W - 13.5}" height="{H - 13.5}" fill="none" stroke="{n["gold"]}" stroke-width="1.5"/>
<rect x="9.75" y="9.75" width="{W - 19.5}" height="{H - 19.5}" fill="none" stroke="{n["gilt"]}" stroke-opacity=".22" stroke-width="1.5"/>
{corners}
<rect x="123" y="12" width="{W - 123 - 18}" height="33" fill="{n["ink"]}"/>
<rect x="126" y="15" width="{W - 123 - 24}" height="27" fill="#0d0a22"/>
<path d="M126 15h{W - 147}v3h-{W - 150}v24h-3z" fill="#000" fill-opacity=".5"/>
<path d="M{W - 21} 15v27h-{W - 147}v-1.5h{W - 148.5}v-25.5z" fill="{accent}" fill-opacity=".45"/>
<path d="M36 28.5H57M84 28.5H114" stroke="{n["gold"]}" stroke-width="1.5"/>
{_crescent(70.5, 28.5, 10.5, n["deep"])}{_gem(99, 28.5, accent)}{_gem(42, 28.5, n["gilt"], 3)}'''

def _page(W, H, svg):
    """Like frame(), without its border, fonts or text layer: these windows paint no word."""
    return f'''<!doctype html><html><head><meta charset="utf-8">
<style>html,body{{margin:0;background:{NIGHT["deep"]}}}</style></head><body>
<svg style="display:block" width="{W}" height="{H}" viewBox="0 0 {W} {H}">{svg}</svg></body></html>'''

# name -> (decor, role colour, bottom plaques as (first column, width, tone), columns of inventory row 1 where an
# item can sit (WerewolfMenus.bottom / village), hotbar columns (card, timer, lock; the village's lock moves to 7
# while the compass still sits in 8))
ROLE_MENUS = {
    "menu_vote":    ("vote", "#ffd36e", [(0, 3, None), (3, 3, None), (6, 3, "#8f84b3")], [1, 4, 7], [0, 4, 8]),
    "menu_hunt":    ("werewolf", "#ff5a5a", [(0, 4, None), (5, 4, None)], [1, 6, 7, 8], [0, 4, 8]),
    "menu_vision":  ("seer", "#b892ff", [(0, 9, None)], [1, 2, 4, 5, 7, 8], [0, 4, 8]),
    "menu_potions": ("witch", "#6fdc8c", [(0, 3, "#ffd36e"), (3, 3, "#6fdc8c"), (6, 3, "#8f84b3")], [1, 4, 7], [0, 4, 8]),
    "menu_guard":   ("guard", "#6cc6ff", [(0, 4, None), (5, 4, "#6cc6ff")], [1, 6], [0, 4, 8]),
    "menu_lovers":  ("cupid", "#ff8fc0", [(0, 5, None), (6, 3, "#ff8fc0")], [1, 2, 3, 7], [0, 4, 8]),
    "menu_shot":    ("hunter", "#f0a050", [(0, 5, None), (6, 3, "#f0a050")], [1, 7], [0, 4, 8]),
    "menu_village": ("village", "#e9d99b", [(0, 4, None), (5, 4, None)], [1, 6, 7, 8], [0, 4, 7, 8]),
}
RING = [2, 3, 4, 5, 6, 16, 25, 34, 43, 51, 50, 49, 48, 47, 37, 28, 19, 10]  # plugin: WerewolfMenus.RING

def role_menu(name):
    """A werewolf role menu, drawn on the real 54-slot grid (slot items at x=24+54c; chest rows y=54+54r,
    inventory rows y=420+54r, hotbar y=594): the role's scene behind the chest with the 18 ring wells for
    the heads, bleeding down into its own dark reflection; a carved panel whose plaques hold the wells of
    the bottom buttons; the hotbar's card, timer and lock wells. No word is painted: the title and the
    items' tooltips say everything (WerewolfMenus, lang/menus/*.yml)."""
    decor, color, boxes, wells, hotbar = ROLE_MENUS[name]
    W, H = 528, 666
    n = NIGHT
    picture = (WEREWOLF / "decors" / f"{decor}.png").as_uri()
    svg = [f'''<defs>
<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#15112f"/><stop offset=".5" stop-color="{n["deep"]}"/><stop offset="1" stop-color="#07050f"/></linearGradient>
<radialGradient id="vig" cx=".5" cy=".46" r=".72"><stop offset=".55" stop-color="{n["ink"]}" stop-opacity="0"/><stop offset="1" stop-color="{n["ink"]}" stop-opacity=".8"/></radialGradient>
<linearGradient id="sink" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{n["deep"]}" stop-opacity="0"/><stop offset="1" stop-color="{n["deep"]}"/></linearGradient>
<linearGradient id="pool" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{n["deep"]}" stop-opacity=".5"/><stop offset=".12" stop-color="{n["deep"]}" stop-opacity=".2"/><stop offset=".7" stop-color="#07050f" stop-opacity=".55"/><stop offset="1" stop-color="#07050f" stop-opacity=".85"/></linearGradient>
<linearGradient id="rail" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{n["gilt"]}"/><stop offset=".8" stop-color="{n["gold"]}" stop-opacity=".7"/><stop offset="1" stop-color="{n["gold"]}" stop-opacity="0"/></linearGradient>
<radialGradient id="aura"><stop offset="0" stop-color="{color}" stop-opacity=".30"/><stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>
<filter id="blur" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation="4.5"/></filter>
<clipPath id="low"><rect x="12" y="381" width="504" height="273"/></clipPath></defs>
<rect width="{W}" height="{H}" fill="url(#bg)"/>
<g clip-path="url(#low)"><image href="{picture}" x="18" y="381" width="492" height="330" preserveAspectRatio="xMidYMid slice" transform="translate(0 1092) scale(1 -1)" filter="url(#blur)"/>
<rect x="12" y="381" width="504" height="273" fill="url(#pool)"/><ellipse cx="264" cy="498" rx="250" ry="90" fill="url(#aura)"/></g>
<image href="{picture}" x="18" y="51" width="492" height="330" preserveAspectRatio="xMidYMid slice"/>
<rect x="18" y="51" width="492" height="330" fill="url(#vig)"/>
<rect x="18" y="330" width="492" height="51" fill="url(#sink)"/>
<path d="M16.5 381V49.5H511.5V381" fill="none" stroke="{n["ink"]}" stroke-width="3"/>
<path d="M19.5 381V52.5H508.5V381" fill="none" stroke="url(#rail)" stroke-width="1.5"/>''']
    for slot in RING:
        svg.append(_well(24 + 54 * (slot % 9), 54 + 54 * (slot // 9), color, .66))
    # the carved panel: a frieze over each plaque, the plaques with their wells, a closing flourish
    svg.append(_plaque(15, 399, 498, 174, None, .3))
    for col, width, tone in boxes:
        x = 21 + 54 * col
        svg.append(_flourish(x + 6, x + width * 54 - 6, 438, n["gilt"], tone or color))
        svg.append(_plaque(x, 462, width * 54, 72, tone or None, .8))
    for col in wells:
        tone = next((t for c, w, t in boxes if c <= col < c + w), None)
        svg.append(_well(24 + 54 * col, 474, tone or color, .85))
    svg.append(f'<path d="M150 552H243M285 552H378" stroke="{n["gold"]}" stroke-width="1.5" stroke-opacity=".6"/>'
               + _crescent(264, 552, 9, "#110e28") + _gem(141, 552, color, 3) + _gem(387, 552, color, 3))
    # the hotbar rail: card, timer, lock
    svg.append(_plaque(15, 582, 498, 72, None, .45))
    marks = sorted(hotbar)
    for a, b in zip(marks, marks[1:]):
        if b - a > 1:
            svg.append(_flourish(24 + 54 * a + 54, 24 + 54 * b - 6, 618, n["gold"], color))
    for col in hotbar:
        svg.append(_well(24 + 54 * col, 594, color, .85))
    svg.append(_shell(W, H, color))
    return W, H, _page(W, H, "".join(svg))

def render_werewolf():
    """Werewolf items and HUD cards. The 16x16 item sprites come from skyblock_icon.py; the role cards (HUD glyphs and
    the card items of the roles that have no sprite) and the role medallions come from the renders in
    werewolf/skins_src through werewolf_art.py (stdlib only, no Pillow)."""
    for sub in ("textures/item/icon", "models/item/icon", "items/icon", "textures/font/card", "font"):
        (PACK / sub).mkdir(parents=True, exist_ok=True)
    import skyblock_icon
    skyblock_icon.write_icons()  # icon/* are hand-drawn 16x16 sprites
    import werewolf_art
    werewolf_art.main()

import sky_menus  # noqa: E402  (Skyblock backgrounds; same frame and tiling)

def sb_title():
    return ""

ART = {"sb_main": lambda: sky_menus.main_menu(frame, sb_title), "sb_panel": lambda: sky_menus.panel54(frame, sb_title),
       "sb_small": lambda: sky_menus.panel27(frame, sb_title), "sb_forge": lambda: sky_menus.forge(frame, sb_title),
       "menu_lobby": lobby, "menu_setup": setup, "menu_list": listing, "menu_detail": detail, "menu_confirm": confirm, "menu_compass": compass, "menu_profile": profile, "menu_alembic": alembic,
       **{name: (lambda n=name: role_menu(n)) for name in ROLE_MENUS}}

# Backgrounds saved as palette PNGs (the pack has a size budget: scripts/test_pack.py). Only fully opaque ones.
PALETTED = {*ROLE_MENUS, "menu_confirm"}

def _shot(html, w, h, out):
    with tempfile.TemporaryDirectory() as tmp:
        page = pathlib.Path(tmp) / "art.html"
        page.write_text(html, encoding="utf-8")
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-sandbox",
                        f"--force-device-scale-factor={SCALE}", f"--window-size={w},{h}",
                        "--virtual-time-budget=8000", f"--screenshot={out}", page.as_uri()],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def render(name):
    """Cuts one background into the tiles listed in layout.BACKGROUNDS and rewrites the font.
    The page is shot once, with spare window height (headless Chrome may reserve some of it), then
    cropped to the window and cut into tiles."""
    from PIL import Image
    if not CHROME:
        sys.exit("Chrome/Chromium is required to render art")
    w, h, html = ART[name]()
    base, gui_h, rows = layout.BACKGROUNDS[name]
    assert (w, h) == (layout.GUI_W * layout.CSS_PER_GUI, gui_h * layout.CSS_PER_GUI), (name, w, h)
    tile_w, tile_h = layout.TILE_W * layout.PX_PER_GUI, layout.tile_height(gui_h, rows) * layout.PX_PER_GUI
    outdir = PACK / "textures" / "font"
    outdir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        shot = pathlib.Path(tmp) / "full.png"
        _shot(html, w, h + 300, shot)
        full = Image.open(shot).convert("RGBA").crop((0, 0, layout.GUI_W * layout.PX_PER_GUI, gui_h * layout.PX_PER_GUI))
    for r in range(rows):
        for c in range(layout.COLS):
            tile = full.crop((c * tile_w, r * tile_h, (c + 1) * tile_w, (r + 1) * tile_h))
            if name in PALETTED:  # opaque art with soft gradients: 256 colours per tile, a third of the bytes
                tile = tile.convert("RGB").quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
            tile.save(outdir / f"{layout.tile_file(name, r, c)}.png", optimize=True)
    print("wrote", rows * layout.COLS, "tiles for", name)

def write_font():
    providers = [{"type": "space", "advances": {chr(k): v for k, v in layout.ADVANCES.items()}}]
    for name, (base, gui_h, rows) in layout.BACKGROUNDS.items():
        th = layout.tile_height(gui_h, rows)
        for r in range(rows):
            for c in range(layout.COLS):
                providers.append({"type": "bitmap", "file": f"vampyuyu:font/{layout.tile_file(name, r, c)}.png",
                                  "ascent": layout.ASCENT - r * th, "height": th, "chars": [chr(base + r * layout.COLS + c)]})
    (PACK / "font").mkdir(parents=True, exist_ok=True)
    (PACK / "font" / "menus.json").write_text(json.dumps({"providers": providers}, indent=1) + "\n")
    print("wrote font with", len(providers) - 1, "tiles")

if __name__ == "__main__":
    wanted = sys.argv[1:] or [*ART, "icons", "werewolf"]
    for n in wanted:
        if n == "skyblock_icon":
            render_icons()  # kept as an alias: the Skyblock icon is drawn with the others
        else:
            render_icons() if n == "icons" else render_werewolf() if n == "werewolf" else render(n)
    write_font()
