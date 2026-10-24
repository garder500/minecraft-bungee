#!/usr/bin/env python3
"""Turns the werewolf sounds generated with ElevenLabs into pack sounds.

    python3 resource-pack/art/sounds.py            # every sound
    python3 resource-pack/art/sounds.py ambience   # only the synthesised ambience loops

Sources are the chosen takes in art/werewolf/sounds/*.mp3 (ElevenLabs flow
"Vampyuyu · Loup-Garou pixel-art", Sound Effects v2 and Music v2.5). Each one
is levelled, trimmed, encoded as mono Ogg Vorbis and declared in sounds.json
as vampyuyu:werewolf.<name>, which is the key the arena plugin plays.

The ambience beds in AMBIENCE have no recording: ffmpeg synthesises them (noise
and sine drones, lavfi), so the loop lengths are exact and the files stay small.
Their length must match loopMillis in werewolf/ambience.json (checked by test_pack.py).
"""
import json, pathlib, subprocess, sys

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"

# name: (pack volume, English subtitle). The music loops in the plugin every 75 s.
SOUNDS = {
    "music": (0.35, "Night music plays"),
    "night": (0.8, "Church bell tolls"),
    "dawn": (0.7, "Rooster crows"),
    "howl": (0.7, "Wolf howls"),
    "vote": (0.8, "Drums beat"),
    "death": (0.9, "Death knell"),
    "shot": (0.9, "Hunter fires"),
    "potion_life": (0.8, "Life potion drunk"),
    "potion_death": (0.8, "Poison bubbles"),
    "vision": (0.8, "Crystal ball shimmers"),
    "bind": (0.8, "Lovers bound"),
    "protect": (0.7, "Shield hums"),
    "game_over": (0.8, "Fanfare plays"),
}

# Each bed is periodic: its tones complete whole cycles in the loop and its noise is crossfaded
# into the loop head (see _loop), so the seam has no gap or click. Lengths are the loop lengths.
CROSS = 2  # seconds of crossfade at the loop seam


def _loop(seconds, mix):
    """Turns a mix of seconds + CROSS (labelled [mix]) into a seamless loop of seconds (labelled [out])."""
    return (
        f"{mix};[mix]asplit=2[p][q];"
        f"[p]atrim=0:{seconds},asetpts=PTS-STARTPTS[a];"
        f"[q]atrim={seconds}:{seconds + CROSS},asetpts=PTS-STARTPTS[b];"
        f"[b][a]acrossfade=d={CROSS}:c1=qsin:c2=qsin[out]"
    )


# name: (pack volume, English subtitle, loop length in seconds, lavfi mix lasting length + CROSS).
# Tremolo rates are k / length and tone frequencies are multiples of 1 / length, so tones repeat.
# Beds are mixed quietly; the plugin sets the real level per phase in ambience.json.
AMBIENCE = {
    # calm night: brown wind and a slow breathing drone (4 and 5 cycles per loop)
    "ambience_night": (
        1.0, "Night wind murmurs", 32,
        "anoisesrc=d=34:c=brown:r=44100:a=0.6,lowpass=f=420[wind];"
        "sine=f=55:d=34:r=44100,tremolo=f=0.125:d=0.4,volume=0.35[low];"
        "sine=f=82.5:d=34:r=44100,tremolo=f=0.15625:d=0.5,volume=0.15[fifth];"
        "[wind][low][fifth]amix=inputs=3:normalize=0[mix]"),
    # brighter day: airy pink wind over an open major pad (6 and 4 cycles per loop)
    "ambience_day": (
        1.0, "Daylight breeze drifts", 32,
        "anoisesrc=d=34:c=pink:r=44100:a=1.0,bandpass=f=900:width_type=h:w=800[air];"
        "sine=f=392:d=34:r=44100,tremolo=f=0.1875:d=0.5,volume=0.5[g];"
        "sine=f=587:d=34:r=44100,tremolo=f=0.125:d=0.5,volume=0.4[d];"
        "[air][g][d]amix=inputs=3:normalize=0[mix]"),
    # tense: a 2 Hz pulse on a tritone, dark rumble underneath (votes and trials)
    "ambience_vote": (
        1.0, "Tension builds", 16,
        "sine=f=45:d=18:r=44100,tremolo=f=2:d=0.9,volume=0.7[pulse];"
        "sine=f=77.8125:d=18:r=44100,tremolo=f=2:d=0.9,volume=0.25[tritone];"
        "anoisesrc=d=18:c=brown:r=44100:a=0.3,lowpass=f=200[rumble];"
        "[pulse][tritone][rumble]amix=inputs=3:normalize=0[mix]"),
    # finale: a slow rising major chord (6 cycles per loop)
    "ambience_finale": (
        1.0, "Triumphant chords swell", 24,
        "sine=f=220:d=26:r=44100,tremolo=f=0.25:d=0.3,volume=0.9[a];"
        "sine=f=277.125:d=26:r=44100,tremolo=f=0.25:d=0.3,volume=0.6[c];"
        "sine=f=330:d=26:r=44100,tremolo=f=0.25:d=0.3,volume=0.75[e];"
        "[a][c][e]amix=inputs=3:normalize=0[mix]"),
}


def encode(name):
    music = name == "music"
    source = ART / "werewolf" / "sounds" / f"{name}.mp3"
    target = PACK / "sounds" / "werewolf" / f"{name}.ogg"
    target.parent.mkdir(parents=True, exist_ok=True)
    # Same loudness for every effect; the music sits lower and fades at both ends so the loop
    # seam is soft. Effects lose their trailing silence and end on a short fade.
    chain = (
        "loudnorm=I=-23:TP=-2,afade=t=in:d=2,areverse,afade=t=in:d=2,areverse"
        if music
        else "loudnorm=I=-16:TP=-1,areverse,silenceremove=start_periods=1:start_threshold=-50dB,"
        "afade=t=in:d=0.15,areverse"
    )
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source), "-af", chain,
         "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "2" if music else "4",
         "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(target)],
        check=True,
    )


def synthesise(name):
    _, _, seconds, mix = AMBIENCE[name]
    graph = _loop(seconds, mix)
    target = PACK / "sounds" / "werewolf" / f"{name}.ogg"
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-filter_complex", graph,
         "-map", "[out]", "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "1",
         "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(target)],
        check=True,
    )


def main(only_ambience=False):
    definitions, subtitles = {}, {}
    if not only_ambience:
        for name in SOUNDS:
            encode(name)
    for name in AMBIENCE:
        synthesise(name)
    for name, (volume, subtitle) in SOUNDS.items():
        key = f"subtitles.vampyuyu.werewolf.{name}"
        sound = {"name": f"vampyuyu:werewolf/{name}", "volume": volume}
        if name == "music":
            sound["stream"] = True
        definitions[f"werewolf.{name}"] = {"sounds": [sound], "subtitle": key}
        subtitles[key] = subtitle
    for name, (volume, subtitle, _, _) in AMBIENCE.items():
        key = f"subtitles.vampyuyu.werewolf.{name}"
        # Beds are short loops: the plugin sets their real level per phase in ambience.json.
        sound = {"name": f"vampyuyu:werewolf/{name}", "volume": volume}
        definitions[f"werewolf.{name}"] = {"sounds": [sound], "subtitle": key}
        subtitles[key] = subtitle
    (PACK / "sounds.json").write_text(json.dumps(definitions, indent=2) + "\n")
    (PACK / "lang").mkdir(exist_ok=True)
    (PACK / "lang" / "en_us.json").write_text(json.dumps(subtitles, indent=2) + "\n")


if __name__ == "__main__":
    if sys.argv[1:] not in ([], ["ambience"]):
        sys.exit("usage: python3 resource-pack/art/sounds.py [ambience]")
    main(only_ambience=sys.argv[1:] == ["ambience"])
