#!/usr/bin/env python3
"""Boss ability sound effects (generated, never vanilla). Stdlib only, plus ffmpeg for `import`.

    python3 resource-pack/art/boss_sfx.py import <cue> <file.mp3>   # trim, mono, normalise -> sounds/fx/<cue>.ogg
    python3 resource-pack/art/boss_sfx.py sounds                    # register every recorded cue in sounds.json
    python3 resource-pack/art/boss_sfx.py check                     # exit 1 when a cue the plugin plays has no clip

Every sound a boss fight plays goes through `Fx.sfx(world, at, "<cue>", volume, pitch)` (survival-plugin boss/Fx.java), which
plays `vampyuyu:fx.<cue>` on the HOSTILE channel. The clips are generated with ElevenLabs text to sound (eleven_text_to_sound_v3)
from the prompts below, one clip per cue, then cut to at most `seconds` with a short fade, made mono (positional in game),
peak-normalised and stored as Ogg Vorbis in pack/assets/vampyuyu/sounds/fx/<cue>.ogg. `check` reads the cues used in the
plugin sources, so a new cue without a clip fails `make test-pack`.
"""
import json
import pathlib
import re
import shutil
import subprocess
import sys

ART = pathlib.Path(__file__).resolve().parent
ROOT = ART.parent.parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SOURCES = ROOT / "survival-plugin" / "src" / "main" / "java"

# cue -> (ElevenLabs prompt, longest clip in seconds)
CUES = {
    # telegraphs and casts
    "power_up": ("Arcane energy charging up, rising magical hum with crackling sparks", 2.5),
    "void_stare": ("Deep unsettling void drone with a reversed whisper swell", 2.5),
    "laser_charge": ("Sci-fi energy beam charging, rising high-pitched whine building tension", 2.0),
    "laser_beam": ("Sustained searing energy beam, intense buzzing hum with sizzling", 4.0),
    "storm_rise": ("Rolling thunder building in the distance, ominous storm rumble", 3.0),
    "thunder_charge": ("Static electricity crackling and building, sharp electric buzz", 2.0),
    "thunder_strike": ("Violent lightning strike, sharp cracking thunderclap, close", 2.5),
    "slam_windup": ("Giant stone creature heaving up, heavy grinding effort grunt and rumble", 1.5),
    "ground_slam": ("Massive ground slam shockwave, deep booming sub-bass impact, cracking stone, rumbling debris tail", 3.5),
    "fire_burst": ("Burst of roaring flame igniting, whoosh with crackle", 1.5),
    "fireball_launch": ("Fireball launched, fiery whoosh flying away", 1.2),
    "explosion": ("Magical explosion, deep boom with shimmering crackle tail", 2.5),
    "fire_wall": ("Wall of fire roaring and spreading outward, intense crackling flames", 3.0),
    "lava_bubble": ("Thick molten lava bubbling and hissing, heavy glops", 2.5),
    "ice_shatter": ("Huge ice spikes erupting from the ground, crystalline cracking and shattering", 2.0),
    "blizzard_wind": ("Howling freezing blizzard wind with icy gusts", 4.0),
    "toxic_hiss": ("Toxic gas cloud hissing out with sickly bubbling", 2.5),
    "wither_moan": ("Dark withering mist, low ghostly moan with dry rattling", 2.5),
    "breath_roar": ("Dragon exhaling a corrosive breath, rushing hiss with a guttural growl", 2.5),
    "void_open": ("Rift tearing open in space, deep warping whoosh and low hum", 2.5),
    "web_snare": ("Thick sticky webs snapping tight, creaking strands", 1.5),
    "bone_rattle": ("Bones rattling and clattering together, eerie dry clicking", 2.0),
    "bone_burst": ("Bone bars bursting up from the ground, splintering crack", 1.5),
    "soul_drain": ("Ghostly soul being drained, eerie whispering suction with a low drone", 3.0),
    "summon_call": ("Necromantic summoning chant swelling, ominous choir hum", 2.5),
    "summon_rise": ("Creatures clawing up out of the earth, dirt crumbling and groans", 2.0),
    "vortex_pull": ("Swirling vortex sucking air inward, rising wind roar", 3.0),
    "abyss_call": ("Deep sea leviathan call, huge distant underwater moan", 3.0),
    "wave_rush": ("Huge tidal wave crashing and rushing forward, roaring water", 3.5),
    "blink": ("Teleport blink, quick reality-warping zap with a soft pop", 1.0),
    "slash": ("Giant blade slash cutting the air, sharp swoosh with metallic ring", 1.0),
    "swoop": ("Huge flying creature swooping down fast, heavy rushing wind", 1.5),
    "wing_flap": ("Giant leathery wings flapping, powerful whoomp", 1.5),
    "charge_roar": ("Monstrous beast roaring as it charges, aggressive bellow", 2.0),
    "roar": ("Colossal monster roar, deep and terrifying, echoing", 3.0),
    "gravity_fail": ("Gravity distortion, deep falling pitch drone with warbling", 2.5),
    "crystal_hit": ("Crystal struck and cracking, bright glassy chime", 1.0),
    "shield_block": ("Magic shield absorbing a hit, dull resonant thud with shimmer", 0.8),
    "shield_break": ("Magic crystal shield shattering apart, big glassy burst", 2.0),
    "chains_snap": ("Heavy iron chains snapping apart, metallic clank and whip", 1.5),
    "boss_hurt": ("Heavy blow landing on a huge creature, deep meaty thud with a short pained growl", 1.2),
    "boss_rise": ("Ancient boss awakening, deep rising rumble with an ominous chord", 3.5),
    "boss_death": ("Colossal creature collapsing and imploding, deep fading boom and debris", 4.0),
}


def used_cues():
    """The cues the plugin plays: every Fx.sfx(..., "<cue>", ...) and every zone preset new Zone(..., "<cue>")."""
    cues = set()
    for path in SOURCES.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        cues.update(re.findall(r'Fx\.sfx\([^;]*?"([a-z_]+)"', text))
        cues.update(re.findall(r'new Zone\((?:[^;]|\n)*?,\s*"([a-z_]+)"\)', text))   # the zone presets' cue (last argument)
    return cues


def ffmpeg():
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    found = sorted(pathlib.Path("/nix/store").glob("*-ffmpeg-*/bin/ffmpeg"))
    return str(found[-1]) if found else None


def import_clip(cue, src):
    if cue not in CUES:
        sys.exit(f"unknown cue {cue}")
    out = PACK / "sounds" / "fx" / f"{cue}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    seconds = CUES[cue][1]
    fade = min(0.6, seconds / 3)
    # trailing silence off, cut to the cue's length with a fade, mono for positional sound, peaks at -1 dB
    filt = (f"silenceremove=stop_periods=-1:stop_duration=0.3:stop_threshold=-55dB,atrim=0:{seconds},"
            f"afade=t=out:st={max(0.0, seconds - fade)}:d={fade},alimiter=limit=0.89")
    subprocess.run([ffmpeg(), "-y", "-loglevel", "error", "-i", str(src), "-af", filt, "-ac", "1", "-ar", "32000",
                    "-c:a", "libvorbis", "-q:a", "1", str(out)], check=True)
    print(f"{cue}: {out.relative_to(ART.parent)}")


def write_sounds():
    path = PACK / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    sounds = {k: v for k, v in sounds.items() if not k.startswith("fx.")}
    added = 0
    for cue in sorted(CUES):
        if (PACK / "sounds" / "fx" / f"{cue}.ogg").exists():
            sounds[f"fx.{cue}"] = {"sounds": [{"name": f"vampyuyu:fx/{cue}"}], "subtitle": f"subtitles.vampyuyu.fx.{cue}"}
            added += 1
    path.write_text(json.dumps(sounds, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{added}/{len(CUES)} boss sound effects registered in {path}")


def check():
    sounds = json.loads((PACK / "sounds.json").read_text(encoding="utf-8"))
    problems = []
    for cue in sorted(used_cues()):
        if cue not in CUES:
            problems.append(f"{cue}: played by the plugin but not listed in boss_sfx.CUES")
        elif not (PACK / "sounds" / "fx" / f"{cue}.ogg").exists():
            problems.append(f"{cue}: no clip sounds/fx/{cue}.ogg (generate it, then boss_sfx.py import)")
        elif f"fx.{cue}" not in sounds:
            problems.append(f"{cue}: not in sounds.json (boss_sfx.py sounds)")
    for p in problems:
        print("FAIL", p)
    print(f"boss sfx: {len(used_cues())} cues played, {len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    if cmd == "import":
        import_clip(sys.argv[2], sys.argv[3])
    elif cmd == "sounds":
        write_sounds()
    else:
        sys.exit(check())
