#!/usr/bin/env python3
"""One-off conversion of the Skyblock's former YAML configuration into the SQL seeds of the content database.

    python3 -m venv /tmp/v && /tmp/v/bin/pip install pyyaml
    /tmp/v/bin/python scripts/yaml_to_seed.py <yaml dir> [domain...]

<yaml dir> holds the old files (git show <commit>:survival-plugin/src/main/resources/<name>.yml). Each converter writes
survival-plugin/src/main/resources/db/seed/<domain>.sql with one INSERT per row of the typed tables (see
db/migrations). The YAML files are gone from the plugin: this script is kept to document how the seeds were made.
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sqlseed import insert, write  # noqa: E402

CONVERTERS = {}


def converter(name):
    def wrap(fn):
        CONVERTERS[name] = fn
        return fn
    return wrap


def load(src, name):
    with open(src / f"{name}.yml", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


@converter("quests_daily")
def quests_daily(src):
    pool = load(src, "menu")["quests"]["daily"]["pool"]
    rows = []
    for pos, (qid, q) in enumerate(pool.items()):
        rewards = q.get("rewards") or {}
        rows.append(insert("quest", {"id": qid, "pool": "daily", "position": pos, "category": "", "name": q.get("name", qid),
                                     "description": q.get("description", ""), "type": q["type"].upper(),
                                     "target": str(q.get("target", "") or ""), "amount": int(q.get("amount", 1)),
                                     "icon": q.get("icon", "PAPER"), "reward_coins": float(rewards.get("coins", 0))}))
        for skill, xp in (rewards.get("skill-xp") or {}).items():
            rows.append(insert("quest_reward_xp", {"quest_id": qid, "skill": skill.upper(), "amount": float(xp)}))
        for item, amount in (rewards.get("items") or {}).items():
            rows.append(insert("quest_reward_item", {"quest_id": qid, "item": item, "amount": int(amount)}))
    return ["The daily quest pool (3 are drawn per player and per day)."], rows


def main():
    src = pathlib.Path(sys.argv[1])
    names = sys.argv[2:] or list(CONVERTERS)
    for name in names:
        header, rows = CONVERTERS[name](src)
        out = write(name, header + ["Converted from the former YAML by scripts/yaml_to_seed.py; edit it in the panel."], rows)
        print(f"{name}: {len(rows)} rows -> {out}")


if __name__ == "__main__":
    main()
