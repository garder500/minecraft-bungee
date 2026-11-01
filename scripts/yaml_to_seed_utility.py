#!/usr/bin/env python3
"""One-off conversion of utility.yml into db/seed/utility.sql (V005__utility.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_utility.py <yaml dir>
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from seed_settings import comment_notes, setting_rows  # noqa: E402
from sqlseed import insert, write  # noqa: E402


def convert(src):
    text = (src / "utility.yml").read_text(encoding="utf-8")
    y = yaml.safe_load(text)
    rows = [insert("utility_portable", {"name": name, "public": bool(v["public"])}) for name, v in y["portable"].items()]
    return rows + setting_rows("utility", y, comment_notes(text), ("portable",))


if __name__ == "__main__":
    statements = convert(pathlib.Path(sys.argv[1]))
    out = write("utility", ["The utility feature (teleport rules, homes, tpa, rtp, sit, trash, portable blocks).",
                            "Converted from the former utility.yml by scripts/yaml_to_seed_utility.py; edit it in the panel."], statements)
    print(f"utility: {len(statements)} rows -> {out}")
