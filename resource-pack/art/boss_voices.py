#!/usr/bin/env python3
"""Recorded voices of the boss lines (Guardians, the Echo, the Devourer, Hell Gao). Stdlib only.

    python3 resource-pack/art/boss_voices.py manifest   # rewrite resource-pack/voices/manifest.json from the seed
    python3 resource-pack/art/boss_voices.py sounds     # register every recorded clip in the pack's sounds.json
    python3 resource-pack/art/boss_voices.py check      # exit 1 when a line has no clip or its text changed

The lines live in `survival-plugin/src/main/resources/db/seed/boss.sql` (`boss.guardian.<id>.<event>` and
`boss.speech.<id>.<event>` lists). The plugin plays `vampyuyu:voice.<boss>.<event>.<n>` with each line (n = position in
the list). The clips are generated with ElevenLabs (one library voice per boss, see VOICES) from the manifest's `prompt`,
converted to mono Ogg Vorbis and stored as `pack/assets/vampyuyu/sounds/voice/<boss>/<event>_<n>.ogg`. A line edited in
the panel keeps its old clip until it is regenerated: `check` lists such lines.
"""
import json
import pathlib
import re
import sys

ART = pathlib.Path(__file__).resolve().parent
ROOT = ART.parent.parent
SEED = ROOT / "survival-plugin" / "src" / "main" / "resources" / "db" / "seed" / "boss.sql"
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
MANIFEST = ART.parent / "voices" / "manifest.json"

# boss id -> (ElevenLabs library voice id, voice name, performance tag in front of every line)
VOICES = {
    "magma_warden": ("KTAbPR4QFlhaTpde6md8", "Bloodgrin", "[deep, rumbling]"),
    "frost_revenant": ("ZaovTqhD16YvmEG2nDUD", "Kall", "[cold, measured]"),
    "sylvan_matriarch": ("fhNley3gMN1oXT2fQZjN", "Grizzy", "[whispering]"),
    "crypt_lord": ("1BfrkuYXmEwp8AWqSLWk", "Declan Graves", "[raspy, solemn]"),
    "mire_abomination": ("1KFdM0QCwQn4rmn5nn9C", "Parasyte", "[gurgling, twisted]"),
    "temporal_pharaoh": ("HAvvFKatz0uu0Fv55Riy", "Ancient Sage", "[regal, ancient]"),
    "abyssal_leviathan": ("wXvR48IpOq9HACltTmt7", "Ancient Monster", "[deep, slow]"),
    "thunder_dragon": ("A921zklid24OpyVy1Elb", "Draconius", "[snarling]"),
    "voidwyrm": ("2tTjAGX0n5ajDmazDcWk", "Ezekiel Wren", "[hungry whisper]"),
    "devourer": ("d3qrd1ge6zWDvUxIeXiH", "Final Boss", "[vast, sorrowful]"),
    "hell_gao": ("QzD8JR9v8A4kqCDL8XD4", "Gorex", "[unsettlingly calm]"),
}
EVENT_TAG = {"phase2": "[intense]", "death": "[weakening]", "first-clear": "[quietly]"}
EVENTS = ("intro", "phase1", "phase2", "phase3", "death", "first-clear")


def seed_lines():
    """{(boss, event): [line, ...]} for every speaking boss of the seed."""
    text = SEED.read_text(encoding="utf-8")
    items = {}
    for m in re.finditer(r"INSERT INTO setting_item \(setting_id, position, value\) VALUES \('boss\.(?:guardian|speech)\.([a-z_]+)\.([a-z0-9-]+)', (\d+), '((?:[^']|'')*)'\);", text):
        boss, event, pos, value = m.group(1), m.group(2), int(m.group(3)), m.group(4).replace("''", "'")
        if event in EVENTS:
            items.setdefault((boss, event), {})[pos] = value
    return {k: [v[i] for i in sorted(v)] for k, v in items.items()}


def entries():
    out = []
    for (boss, event), lines in sorted(seed_lines().items()):
        if boss not in VOICES:
            continue
        voice, name, tag = VOICES[boss]
        for n, line in enumerate(lines):
            prompt = " ".join(t for t in (tag, EVENT_TAG.get(event, "")) if t) + " " + line
            out.append({"key": f"voice.{boss}.{event}.{n}", "boss": boss, "voice": voice, "voice_name": name,
                        "text": line, "prompt": prompt, "file": f"voice/{boss}/{event}_{n}.ogg"})
    return out


def write_manifest():
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    data = entries()
    MANIFEST.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(data)} lines, {sum(len(e['prompt']) for e in data)} characters -> {MANIFEST}")


def write_sounds():
    path = PACK / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    sounds = {k: v for k, v in sounds.items() if not k.startswith("voice.")}
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    added = 0
    for e in data:
        if (PACK / "sounds" / e["file"]).exists():
            sounds[e["key"]] = {"sounds": [{"name": "vampyuyu:" + e["file"][:-4], "stream": True}],
                                "subtitle": "subtitles.vampyuyu.voice"}
            added += 1
    path.write_text(json.dumps(sounds, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{added}/{len(data)} voice clips registered in {path}")


def check():
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    recorded = {e["key"]: e for e in data}
    problems = []
    for e in entries():
        old = recorded.get(e["key"])
        if old is None or old["text"] != e["text"]:
            problems.append(f"{e['key']}: text changed or new, regenerate its clip")
        elif not (PACK / "sounds" / e["file"]).exists():
            problems.append(f"{e['key']}: no clip {e['file']}")
    for p in problems:
        print(p)
    return 1 if problems else 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "manifest"
    if cmd == "manifest":
        write_manifest()
    elif cmd == "sounds":
        write_sounds()
    else:
        sys.exit(check())
