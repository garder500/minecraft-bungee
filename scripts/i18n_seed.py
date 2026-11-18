#!/usr/bin/env python3
"""Checks the translations (survival-plugin/i18n/<locale>/*.json) against the English source and writes db/seed/i18n.sql.

    python3 scripts/i18n_seed.py [--check]

Run scripts/i18n_extract.py first when the English content changed. A translation whose English source changed since
is reported and left out (the game shows English for it). Also writes the item text as client lang files
(resource-pack/pack/assets/vy_i18n/lang/<client locale>.json): rebuild the pack afterwards. --check writes nothing and
fails on any problem.
"""
import hashlib
import json
import pathlib
import re
import sys

import i18n_extract
from sqlseed import insert, write

DIR = i18n_extract.OUT
LOCALES = ("fr", "de")
TAG = re.compile(r"</?[a-z_#0-9:]+>")
NUMBER = re.compile(r"\d+")
THOUSANDS = re.compile(r"(?<=\d)[ ,.\u00a0\u202f](?=\d{3}(?!\d))")  # 2,000 = 2 000 = 2.000
COMMAND = re.compile(r"/[a-z]+(?: [a-z]+)?")
LORE_MAX, NAME_MAX = 48, 40
# Item text is translated by the client (win.vampyuyu.survival.core.Lang): one lang file per client locale.
PACK_LANG = i18n_extract.ROOT / "resource-pack/pack/assets/vy_i18n/lang"
CLIENT_LOCALES = {"fr": ("fr_fr", "fr_ca"), "de": ("de_de", "de_at", "de_ch")}


def pack_entries(key, english, text):
    """The lang file entries of one item key, as Lang.name and Lang.line ask for them: the key ends with a hash of the
    English text, and a description line is one entry per piece of text between two tags. % is doubled (a lang value is
    a format string)."""
    base = f"vy.{key}." + hashlib.sha1(english.encode("utf-8")).hexdigest()[:8]
    if key.endswith(".name"):
        return {base: text.replace("%", "%%")}
    return {f"{base}.{i}": piece.replace("%", "%%")
            for i, (source, piece) in enumerate(zip(TAG.split(english), TAG.split(text))) if source}


def visible(text):
    return TAG.sub("", text)


def problems_of(key, english, text):
    out = []
    if not isinstance(text, str) or not text.strip():
        return ["empty"]
    if TAG.findall(english) != TAG.findall(text):
        out.append(f"tags differ: {TAG.findall(english)} vs {TAG.findall(text)}")
    numbers = lambda s: sorted(NUMBER.findall(THOUSANDS.sub("", visible(s))))
    if numbers(english) != numbers(text):
        out.append(f"numbers differ: {numbers(english)} vs {numbers(text)}")
    for cmd in COMMAND.findall(english):
        if cmd.split()[0] not in text:
            out.append(f"command {cmd.split()[0]} missing")
    limit = max(LORE_MAX, int(len(visible(english)) * 1.3))  # a long English line allows a long translation
    if ".lore." in key and len(visible(text)) > limit:
        out.append(f"line of {len(visible(text))} characters (max {limit})")
    if key.endswith(".name") and len(text) > NAME_MAX:
        out.append(f"name of {len(text)} characters (max {NAME_MAX})")
    return out


def load(locale, english):
    """({key: text}, [problem]) of one locale."""
    rows, problems = {}, []
    for chunk, source in english.items():
        path = DIR / locale / f"{chunk}.json"
        if not path.exists():
            problems.append(f"{locale}/{chunk}.json is missing")
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for key in data.keys() - source.keys():
            problems.append(f"{locale}/{chunk}: unknown key {key}")
        for key, text in source.items():
            if key not in data:
                problems.append(f"{locale}/{chunk}: {key} is not translated")
                continue
            bad = problems_of(key, text, data[key])
            if bad:
                problems.extend(f"{locale}/{chunk}: {key}: {b}" for b in bad)
            else:
                rows[key] = data[key]
    return rows, problems


def main():
    english = i18n_extract.sources(i18n_extract.database())
    flat = {k: v for chunk in english.values() for k, v in chunk.items()}
    statements, problems = [], []
    for locale in LOCALES:
        rows, bad = load(locale, english)
        problems += bad
        if "--check" not in sys.argv:
            lang = {}
            for key, text in rows.items():
                if key.startswith("item."):
                    lang.update(pack_entries(key, flat[key], text))
            PACK_LANG.mkdir(parents=True, exist_ok=True)
            for client in CLIENT_LOCALES[locale]:
                (PACK_LANG / f"{client}.json").write_text(
                    json.dumps(lang, indent=1, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        statements += [insert("i18n_text", {"id": f"{locale}:{key}", "locale": locale, "key": key, "source": flat[key],
                                            "text": text}) for key, text in rows.items()]
    for p in problems:
        print("PROBLEM", p)
    if "--check" in sys.argv:
        sys.exit(1 if problems else 0)
    write("i18n", ["Translations of item text (fr, de). Written by scripts/i18n_seed.py from",
                   "survival-plugin/i18n/<locale>/*.json: do not edit by hand."], statements)
    print(f"{len(statements)} translations written, {len(problems)} problems")


if __name__ == "__main__":
    main()
