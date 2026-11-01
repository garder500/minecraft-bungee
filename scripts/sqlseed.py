"""Helpers shared by the scripts that write the Skyblock seed files (survival-plugin/src/main/resources/db/seed/*.sql).

A seed is plain SQL: one INSERT per row, applied by win.vampyuyu.survival.data.Seeds into the server's skyblock.db.
"""
import pathlib

SEED_DIR = pathlib.Path(__file__).resolve().parent.parent / "survival-plugin/src/main/resources/db/seed"


def lit(value):
    """An SQL literal: NULL, 0/1 for booleans, numbers as is, text in single quotes."""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return repr(value) if isinstance(value, float) else str(value)
    return "'" + str(value).replace("'", "''") + "'"


def insert(table, row):
    cols = ", ".join(row)
    vals = ", ".join(lit(v) for v in row.values())
    return f"INSERT INTO {table} ({cols}) VALUES ({vals});"


def write(name, header, statements):
    SEED_DIR.mkdir(parents=True, exist_ok=True)
    out = SEED_DIR / f"{name}.sql"
    text = "".join(f"-- {line}\n" for line in header) + "\n".join(statements) + "\n"
    out.write_text(text, encoding="utf-8")
    return out
