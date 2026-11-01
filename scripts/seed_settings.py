"""Shared by scripts/yaml_to_seed_<domain>.py: turns the scalar part of an old YAML file into `setting` rows.

A scalar (or a flat list of scalars) at path a.b.c of <domain>.yml becomes the row 'domain.a.b.c' (a list gets one
setting_item per entry). The comment lines right above a key become the row's note. Collections that have their own
tables are passed in `skip` and converted by the domain script itself.
"""
import re

from sqlseed import insert


def comment_notes(text):
    """Path -> the comment block directly above the key, for block style YAML (keys inside flow mappings are ignored)."""
    notes, stack, pending = {}, [], []
    for raw in text.splitlines():
        line = raw.rstrip()
        stripped = line.strip()
        if not stripped:
            pending = []
            continue
        if stripped.startswith("#"):
            pending.append(stripped.lstrip("#").strip())
            continue
        match = re.match(r"^(\s*)([A-Za-z0-9_.\-]+):(\s|$)", line)
        if not match:
            pending = []
            continue
        indent = len(match.group(1))
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, match.group(2)))
        if pending:
            notes[".".join(k for _, k in stack)] = " ".join(p for p in pending if p)
        pending = []
    return notes


def setting_rows(domain, tree, notes=None, skip=()):
    """INSERT statements for every scalar / scalar list under `tree`, except the paths in `skip` (prefix match)."""
    notes = notes or {}
    rows = []

    def walk(path, value):
        if any(path == s or path.startswith(s + ".") for s in skip):
            return
        if isinstance(value, dict):
            for key, child in value.items():
                walk(f"{path}.{key}" if path else str(key), child)
            return
        note = notes.get(path, "")
        sid = f"{domain}.{path}"
        if isinstance(value, list):
            rows.append(insert("setting", {"id": sid, "type": "list", "value": "", "note": note}))
            for pos, item in enumerate(value):
                rows.append(insert("setting_item", {"setting_id": sid, "position": pos, "value": str(item)}))
            return
        if isinstance(value, bool):
            kind, text = "bool", "true" if value else "false"
        elif isinstance(value, int):
            kind, text = "int", str(value)
        elif isinstance(value, float):
            kind, text = "real", repr(value)
        else:
            kind, text = "text", "" if value is None else str(value)
        rows.append(insert("setting", {"id": sid, "type": kind, "value": text, "note": note}))

    walk("", tree)
    return rows
