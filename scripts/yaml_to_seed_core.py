#!/usr/bin/env python3
"""One-off conversion of the plugin's config.yml (debug flag, feature toggles) into db/seed/core.sql ('core.*' settings).

    /tmp/v/bin/python scripts/yaml_to_seed_core.py <yaml dir>
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from seed_settings import comment_notes, setting_rows  # noqa: E402
from sqlseed import write  # noqa: E402

NOTES = {"debug": "Log extra [debug] lines to the console.",
         "features": "Turn a feature off with features.<id>.enabled = false (restart needed)."}

if __name__ == "__main__":
    src = pathlib.Path(sys.argv[1])
    text = (src / "config.yml").read_text(encoding="utf-8")
    statements = setting_rows("core", yaml.safe_load(text), {**comment_notes(text), **NOTES})
    out = write("core", ["Plugin level settings: the debug flag and which features are on.",
                         "Converted from the former config.yml by scripts/yaml_to_seed_core.py; edit it in the panel."], statements)
    print(f"core: {len(statements)} rows -> {out}")
