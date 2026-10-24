#!/usr/bin/env python3
"""Draws the role and HUD icons (SVG -> PNG) and declares them as the vampyuyu:icons font.

    python3 resource-pack/art/icons.py

Needs ImageMagick with its librsvg delegate (MAGICK=/path/to/magick, else `magick` on PATH, else
built through nix). The SVGs are kept in art/icons/ so they can be edited and redrawn.
"""
import json, os, pathlib, shutil, subprocess, sys

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
sys.path.insert(0, str(ART.parent))
import layout  # noqa: E402

OUTLINE = "#0e0b18"

WOLF = f"""
<polygon points="4,2 12,9 20,9 28,2 29,16 23,26 16,30 9,26 3,16" fill="#ff5a5a" stroke="{OUTLINE}" stroke-width="1.6" stroke-linejoin="round"/>
<polygon points="7,5 11,9 8,12" fill="#ffb0b0"/>
<polygon points="25,5 21,9 24,12" fill="#ffb0b0"/>
<polygon points="7,14 13,16 7,18" fill="#ffe27a" stroke="{OUTLINE}" stroke-width="1" stroke-linejoin="round"/>
<polygon points="25,14 19,16 25,18" fill="#ffe27a" stroke="{OUTLINE}" stroke-width="1" stroke-linejoin="round"/>
<polygon points="13,22 19,22 16,26" fill="{OUTLINE}"/>
<polygon points="12,26 14.5,26 13.2,29.5" fill="#fff"/>
<polygon points="17.5,26 20,26 18.8,29.5" fill="#fff"/>
"""

SVG = {
    "wolf": WOLF,
    "villager": f"""
<circle cx="16" cy="19" r="9" fill="#e9d99b" stroke="{OUTLINE}" stroke-width="1.6"/>
<rect x="5" y="11" width="22" height="3.5" rx="1" fill="#b98a4a" stroke="{OUTLINE}" stroke-width="1.4"/>
<polygon points="9.5,11 12,3.5 20,3.5 22.5,11" fill="#b98a4a" stroke="{OUTLINE}" stroke-width="1.4" stroke-linejoin="round"/>
<rect x="11" y="18" width="3" height="3" fill="{OUTLINE}"/><rect x="18" y="18" width="3" height="3" fill="{OUTLINE}"/>
<rect x="13.5" y="24" width="5" height="1.6" fill="{OUTLINE}"/>""",
    "seer": f"""
<polygon points="8,29 24,29 21,24 11,24" fill="#6a56a8" stroke="{OUTLINE}" stroke-width="1.4" stroke-linejoin="round"/>
<circle cx="16" cy="14" r="11" fill="#b892ff" stroke="{OUTLINE}" stroke-width="1.6"/>
<path d="M6.5 14 Q16 6.5 25.5 14 Q16 21.5 6.5 14Z" fill="#fff6d8" stroke="{OUTLINE}" stroke-width="1.2" stroke-linejoin="round"/>
<circle cx="16" cy="14" r="3.4" fill="#5b3fb0"/><circle cx="16" cy="14" r="1.4" fill="{OUTLINE}"/>""",
    "witch": f"""
<polygon points="16,1.5 22,17 28,21 28,26 4,26 4,21 10,17" fill="#6fdc8c" stroke="{OUTLINE}" stroke-width="1.6" stroke-linejoin="round"/>
<polygon points="9.5,18.5 22.5,18.5 23.5,22 8.5,22" fill="#3a2a5a" stroke="{OUTLINE}" stroke-width="1.2" stroke-linejoin="round"/>
<rect x="14" y="19" width="4" height="2.5" fill="#ffd36e"/>
<polygon points="2,26 30,26 28,29 4,29" fill="#2f9a58" stroke="{OUTLINE}" stroke-width="1.2" stroke-linejoin="round"/>""",
    "hunter": f"""
<circle cx="16" cy="16" r="10.5" fill="none" stroke="{OUTLINE}" stroke-width="5"/>
<circle cx="16" cy="16" r="10.5" fill="none" stroke="#f0a050" stroke-width="2.6"/>
<rect x="14.7" y="1.5" width="2.6" height="9" fill="#f0a050" stroke="{OUTLINE}" stroke-width="1"/>
<rect x="14.7" y="21.5" width="2.6" height="9" fill="#f0a050" stroke="{OUTLINE}" stroke-width="1"/>
<rect x="1.5" y="14.7" width="9" height="2.6" fill="#f0a050" stroke="{OUTLINE}" stroke-width="1"/>
<rect x="21.5" y="14.7" width="9" height="2.6" fill="#f0a050" stroke="{OUTLINE}" stroke-width="1"/>
<circle cx="16" cy="16" r="2.2" fill="#ff5a5a" stroke="{OUTLINE}" stroke-width="1"/>""",
    "cupid": f"""
<path d="M16 28 C3 19 4 7 11 7 C14 7 16 10 16 11.5 C16 10 18 7 21 7 C28 7 29 19 16 28Z" fill="#ff8fc0" stroke="{OUTLINE}" stroke-width="1.6" stroke-linejoin="round"/>
<path d="M11 10 Q8 10 8 13" fill="none" stroke="#ffe0ee" stroke-width="1.8" stroke-linecap="round"/>
<line x1="4" y1="28" x2="27" y2="5" stroke="{OUTLINE}" stroke-width="4" stroke-linecap="round"/>
<line x1="4" y1="28" x2="27" y2="5" stroke="#ffd36e" stroke-width="2" stroke-linecap="round"/>
<polygon points="27,2.5 30,5 27.5,8 24.5,5.5" fill="#ffd36e" stroke="{OUTLINE}" stroke-width="1.2" stroke-linejoin="round"/>
<polygon points="3,24 8,24.5 4,29" fill="#ffffff" stroke="{OUTLINE}" stroke-width="1" stroke-linejoin="round"/>""",
    "guard": f"""
<path d="M16 2.5 L28 7 V15 C28 22 23 26.5 16 30 C9 26.5 4 22 4 15 V7 Z" fill="#6cc6ff" stroke="{OUTLINE}" stroke-width="1.8" stroke-linejoin="round"/>
<path d="M16 6 L25 9 V15 C25 20 21.5 23.5 16 26.5Z" fill="#a8e0ff"/>
<rect x="14.6" y="9" width="2.8" height="14" fill="#1f5f95"/><rect x="9.5" y="13.6" width="13" height="2.8" fill="#1f5f95"/>""",
    "dead": f"""
<path d="M7 15 A9 9 0 0 1 25 15 V20 H22 V26 H10 V20 H7Z" fill="#8f84b3" stroke="{OUTLINE}" stroke-width="1.6" stroke-linejoin="round"/>
<rect x="10.5" y="14" width="4" height="4.5" fill="{OUTLINE}"/><rect x="17.5" y="14" width="4" height="4.5" fill="{OUTLINE}"/>
<polygon points="16,19.5 14.5,22 17.5,22" fill="{OUTLINE}"/>
<rect x="13.4" y="23" width="1.2" height="3" fill="{OUTLINE}"/><rect x="17.4" y="23" width="1.2" height="3" fill="{OUTLINE}"/>""",
    "moon": f"""
<path d="M22 3 A13 13 0 1 0 29 22 A10.5 10.5 0 0 1 22 3Z" fill="#b8b0ff" stroke="{OUTLINE}" stroke-width="1.6" stroke-linejoin="round"/>
<circle cx="12" cy="17" r="2" fill="#8a82d8"/><circle cx="16" cy="23" r="1.4" fill="#8a82d8"/>""",
    "sun": f"""
<g stroke="{OUTLINE}" stroke-width="4.2" stroke-linecap="round"><line x1="16" y1="2.5" x2="16" y2="6.5"/><line x1="16" y1="25.5" x2="16" y2="29.5"/><line x1="2.5" y1="16" x2="6.5" y2="16"/><line x1="25.5" y1="16" x2="29.5" y2="16"/><line x1="6.5" y1="6.5" x2="9.3" y2="9.3"/><line x1="22.7" y1="22.7" x2="25.5" y2="25.5"/><line x1="25.5" y1="6.5" x2="22.7" y2="9.3"/><line x1="6.5" y1="25.5" x2="9.3" y2="22.7"/></g>
<g stroke="#ffd36e" stroke-width="2.2" stroke-linecap="round"><line x1="16" y1="2.5" x2="16" y2="6.5"/><line x1="16" y1="25.5" x2="16" y2="29.5"/><line x1="2.5" y1="16" x2="6.5" y2="16"/><line x1="25.5" y1="16" x2="29.5" y2="16"/><line x1="6.5" y1="6.5" x2="9.3" y2="9.3"/><line x1="22.7" y1="22.7" x2="25.5" y2="25.5"/><line x1="25.5" y1="6.5" x2="22.7" y2="9.3"/><line x1="6.5" y1="25.5" x2="9.3" y2="22.7"/></g>
<circle cx="16" cy="16" r="7" fill="#ffd36e" stroke="{OUTLINE}" stroke-width="1.6"/>""",
    "vote": f"""
<rect x="4" y="12" width="24" height="16" rx="2" fill="#ffd36e" stroke="{OUTLINE}" stroke-width="1.6"/>
<rect x="10" y="15" width="12" height="3" fill="{OUTLINE}"/>
<rect x="9" y="3" width="14" height="14" fill="#fff6d8" stroke="{OUTLINE}" stroke-width="1.4" transform="rotate(-6 16 10)"/>
<polyline points="12,10 15,13 21,6" fill="none" stroke="#2f9a58" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>""",
    "timer": f"""
<polygon points="7,3 25,3 25,6 19,16 25,26 25,29 7,29 7,26 13,16 7,6" fill="#cdbfe3" stroke="{OUTLINE}" stroke-width="1.6" stroke-linejoin="round"/>
<polygon points="10,6 22,6 16,15" fill="#ffd36e"/><polygon points="11,26 21,26 16,19" fill="#ffd36e"/>""",
    "alive": f"""
<circle cx="11" cy="10" r="4.5" fill="#e9d99b" stroke="{OUTLINE}" stroke-width="1.4"/>
<path d="M2.5 26 C2.5 18 19.5 18 19.5 26Z" fill="#e9d99b" stroke="{OUTLINE}" stroke-width="1.4" stroke-linejoin="round"/>
<circle cx="22" cy="12" r="4" fill="#cdbfe3" stroke="{OUTLINE}" stroke-width="1.4"/>
<path d="M15 27 C15 19.5 29.5 19.5 29.5 27Z" fill="#cdbfe3" stroke="{OUTLINE}" stroke-width="1.4" stroke-linejoin="round"/>""",
    "alpha": WOLF + f"""
<polygon points="9,3 12,7 16,1.5 20,7 23,3 22.5,9 9.5,9" fill="#ffd36e" stroke="{OUTLINE}" stroke-width="1.2" stroke-linejoin="round"/>""",
    "infector": WOLF + f"""
<path d="M24 17 C28 22 29 25 26.5 28 C24 30.5 20.5 29 20.5 26 C20.5 23 22.5 21 24 17Z" fill="#9cf09c" stroke="{OUTLINE}" stroke-width="1.3" stroke-linejoin="round"/>""",
    "cub": f"""<g transform="translate(5.5 6) scale(0.66)">{WOLF}</g>
<circle cx="25" cy="25" r="4" fill="#ffd36e" stroke="{OUTLINE}" stroke-width="1.2"/><rect x="24.2" y="22.8" width="1.6" height="3.4" fill="{OUTLINE}"/>""",
    "scapegoat": f"""
<path d="M7 12 C2 9 3 3 9 3 C9 7 11 9 13 10Z" fill="#e8e0d0" stroke="{OUTLINE}" stroke-width="1.3" stroke-linejoin="round"/>
<path d="M25 12 C30 9 29 3 23 3 C23 7 21 9 19 10Z" fill="#e8e0d0" stroke="{OUTLINE}" stroke-width="1.3" stroke-linejoin="round"/>
<polygon points="8,11 24,11 25,20 20,28 16,29 12,28 7,20" fill="#f3ecdc" stroke="{OUTLINE}" stroke-width="1.6" stroke-linejoin="round"/>
<rect x="11" y="15" width="3" height="2.4" fill="{OUTLINE}"/><rect x="18" y="15" width="3" height="2.4" fill="{OUTLINE}"/>
<polygon points="13,23 19,23 16,29" fill="#d9cfb8" stroke="{OUTLINE}" stroke-width="1" stroke-linejoin="round"/>
<polygon points="14,29 16,32 18,29" fill="#e8e0d0"/>""",
    "bear": f"""
<circle cx="7" cy="8" r="4.5" fill="#9a6a3a" stroke="{OUTLINE}" stroke-width="1.4"/><circle cx="25" cy="8" r="4.5" fill="#9a6a3a" stroke="{OUTLINE}" stroke-width="1.4"/>
<circle cx="16" cy="17" r="12" fill="#b98250" stroke="{OUTLINE}" stroke-width="1.6"/>
<ellipse cx="16" cy="21" rx="6" ry="5" fill="#e0b98a" stroke="{OUTLINE}" stroke-width="1.2"/>
<rect x="10" y="13" width="3" height="3" fill="{OUTLINE}"/><rect x="19" y="13" width="3" height="3" fill="{OUTLINE}"/>
<polygon points="14,19 18,19 16,22" fill="{OUTLINE}"/>""",
}


def magick():
    found = os.environ.get("MAGICK") or shutil.which("magick")
    if found:
        return found
    out = subprocess.run(["nix", "--extra-experimental-features", "nix-command flakes", "build",
                          "nixpkgs#imagemagick", "--no-link", "--print-out-paths"],
                         capture_output=True, text=True, check=True).stdout.split()[-1]
    return str(pathlib.Path(out) / "bin" / "magick")


def main():
    assert sorted(SVG) == sorted(layout.BASE_ICONS), "art/icons.py and layout.BASE_ICONS must list the same icons"
    tool = magick()
    (ART / "icons").mkdir(exist_ok=True)
    out = PACK / "textures" / "font" / "icon"
    out.mkdir(parents=True, exist_ok=True)
    providers = []
    for index, name in enumerate(layout.ICONS):
        svg = ART / "icons" / f"{name}.svg"
        if name in SVG:  # the base icons are drawn here; the other roles' SVGs are files of their own
            svg.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">{SVG[name]}</svg>\n')
        elif not svg.exists():
            svg = ART / "icons" / "villager.svg"
        subprocess.run([tool, "-background", "none", "-density", str(72 * layout.ICON_PX // 32),
                        str(svg), "-resize", f"{layout.ICON_PX}x{layout.ICON_PX}", "-strip",
                        f"PNG32:{out / (name + '.png')}"], check=True)
        providers.append({"type": "bitmap", "file": f"vampyuyu:font/icon/{name}.png",
                          "ascent": layout.ICON_ASCENT, "height": layout.ICON_HEIGHT,
                          "chars": [chr(layout.ICON_BASE + index)]})
    (PACK / "font").mkdir(parents=True, exist_ok=True)
    (PACK / "font" / "icons.json").write_text(json.dumps({"providers": providers}, indent=1) + "\n")
    names = ART.parent.parent / "arena-plugin" / "src" / "main" / "resources" / "icon-names.txt"
    names.write_text("\n".join(layout.ICONS) + "\n")
    print("drew", len(providers), "icons")


if __name__ == "__main__":
    main()
