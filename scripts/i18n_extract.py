#!/usr/bin/env python3
"""Writes the English source strings to translate, in chunks: survival-plugin/i18n/en/<chunk>.json ({key: english}).

    python3 scripts/i18n_extract.py

The source is the seed itself (migrations + db/seed/*.sql in an in-memory SQLite). Keys: item.<id>.name,
item.<id>.lore.<position> (enchanted items are items too). Quest and quest category text has its own files
(scripts/i18n/quests.<lang>.json, scripts/gen_quest_i18n.py, see docs/i18n.md).
Translators write survival-plugin/i18n/<locale>/<chunk>.json with the same keys; scripts/i18n_seed.py turns them into
db/seed/i18n.sql.
"""
import json
import pathlib
import sqlite3

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "survival-plugin/src/main/resources/db"
OUT = ROOT / "survival-plugin/i18n"


def database():
    db = sqlite3.connect(":memory:")
    for f in sorted((RES / "migrations").glob("V*.sql")):
        if f.stem.split("__")[1] == "i18n":
            continue
        db.executescript(f.read_text(encoding="utf-8"))
    for f in sorted((RES / "seed").glob("*.sql")):
        if f.stem != "i18n":
            db.executescript(f.read_text(encoding="utf-8"))
    return db


def sources(db):
    """{chunk: {key: english}} in a stable order."""
    items = {}
    for iid, name in db.execute("SELECT id, name FROM item_def ORDER BY position, id"):
        items[f"item.{iid}.name"] = name
        for pos, line in db.execute("SELECT position, line FROM item_lore WHERE item_id = ? ORDER BY position", (iid,)):
            items[f"item.{iid}.lore.{pos}"] = line
    for iid, name in db.execute("SELECT id, name FROM enchanted_item ORDER BY id"):
        items[f"item.{iid}.name"] = name
    return {"items": items}


def main():
    chunks = sources(database())
    (OUT / "en").mkdir(parents=True, exist_ok=True)
    for old in (OUT / "en").glob("*.json"):
        old.unlink()
    for name, rows in chunks.items():
        (OUT / "en" / f"{name}.json").write_text(json.dumps(rows, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{sum(len(c) for c in chunks.values())} strings in {len(chunks)} chunks")


if __name__ == "__main__":
    main()
