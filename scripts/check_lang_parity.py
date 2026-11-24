#!/usr/bin/env python3
"""Checks the language files: network-plugin and arena-plugin lang/*.yml (full parity with en_US) and claims-plugin langs/*.yml
(partial translations allowed: only unknown keys and changed placeholders / colour codes are errors).

    python3 scripts/check_lang_parity.py           # exit 1 on any error
    python3 scripts/check_lang_parity.py --gaps    # also list the keys a language still lacks
"""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
SETS = [
    # (folder, flat keys must be complete?)
    (ROOT / "network-plugin/src/main/resources/lang", True),
    (ROOT / "arena-plugin/src/main/resources/lang", True),     # the werewolf HUD's words (one key: "TEXT" per line)
    (ROOT / "arena-plugin/src/main/resources/lang", True),         # the HUD words
    (ROOT / "arena-plugin/src/main/resources/lang/menus", True),   # menu titles and tooltips (ArenaLang.java)
    (ROOT / "claims-plugin/src/main/resources/langs", False),
]
LANGS = ["fr_FR", "de_DE"]


def plugin_of(folder):
    """Name of the plugin a language folder belongs to."""
    return folder.relative_to(ROOT).parts[0]


def flat(data, prefix=""):
    out = {}
    for key, value in (data or {}).items():
        path = f"{prefix}{key}"
        if isinstance(value, dict):
            out.update(flat(value, path + "."))
        else:
            out[path] = "" if value is None else str(value)
    return out


def markers(text):
    """Things a translation must keep: %placeholders%, {braces}, <tags> that are not colours, § codes in order."""
    found = re.findall(r"%[A-Za-z0-9_-]+%|\{[^}]*\}|<[a-z_]+>", text)
    colours = {"<red>", "<green>", "<gray>", "<white>", "<yellow>", "<gold>", "<aqua>", "<bold>", "<italic>", "<reset>"}
    found = [m for m in found if m not in colours]
    return sorted(found), re.findall(r"§[0-9a-fk-or]", text)


def main():
    gaps = "--gaps" in sys.argv
    errors = 0
    for folder, complete in SETS:
        en = flat(yaml.safe_load((folder / "en_US.yml").read_text(encoding="utf-8")))
        for lang in LANGS:
            file = folder / f"{lang}.yml"
            if not file.exists():
                print(f"ERROR {file.relative_to(ROOT)}: missing")
                errors += 1
                continue
            tr = flat(yaml.safe_load(file.read_text(encoding="utf-8")))
            for key in sorted(set(tr) - set(en)):
                print(f"ERROR {lang} {plugin_of(folder)}: unknown key {key}")
                errors += 1
            missing = sorted(set(en) - set(tr))
            if missing and (complete or gaps):
                level = "ERROR" if complete else "gap"
                print(f"{level} {lang} {plugin_of(folder)}: {len(missing)} key(s) not translated"
                      + (f": {', '.join(missing[:8])}..." if gaps or complete else ""))
                errors += len(missing) if complete else 0
            for key in sorted(set(tr) & set(en)):
                if markers(en[key])[0] != markers(tr[key])[0]:
                    print(f"ERROR {lang} {key}: placeholders differ: {markers(en[key])[0]} vs {markers(tr[key])[0]}")
                    errors += 1
            if not complete:
                print(f"{lang} {plugin_of(folder)}: {len(tr)}/{len(en)} keys translated")
    print("OK" if not errors else f"{errors} error(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
