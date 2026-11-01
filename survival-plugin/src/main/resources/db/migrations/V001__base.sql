-- Bookkeeping of the content database. Shipped migrations are never edited: add a new file instead.

-- Hash of the jar seed each domain was last brought up to date with (data/Seeds.java).
CREATE TABLE seed_state (
    domain TEXT PRIMARY KEY,
    hash   TEXT NOT NULL
);

-- Every change made from the panel: who, when, what it was before and after.
CREATE TABLE data_audit (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    at         INTEGER NOT NULL,            -- epoch milliseconds
    actor      TEXT    NOT NULL,
    action     TEXT    NOT NULL,            -- put, delete, reset
    table_name TEXT    NOT NULL,
    row_key    TEXT    NOT NULL,
    before     TEXT,                        -- the row as text, null when it did not exist
    after      TEXT
);

-- Single tuning values of a domain (a cooldown, a rate, a message): id = '<domain>.<path>', e.g. 'economy.bank.interest'.
-- Collections (shops, recipes, bosses...) have their own tables; this is only for the scalars around them.
CREATE TABLE setting (
    id     TEXT PRIMARY KEY CHECK (id GLOB '[a-z]*.*'),
    type   TEXT NOT NULL CHECK (type IN ('int', 'real', 'bool', 'text', 'list')),
    value  TEXT NOT NULL DEFAULT '',          -- '' for a list: its entries are in setting_item
    note   TEXT NOT NULL DEFAULT '',          -- what it does, shown in the panel
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE setting_item (
    setting_id TEXT    NOT NULL REFERENCES setting (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    value      TEXT    NOT NULL,
    PRIMARY KEY (setting_id, position)
);
