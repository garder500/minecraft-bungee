-- Ethereal Necropolis (GDD 3.1, issue #46): the Bastion table learns a third arena palette ('necro'). SQLite cannot change a
-- CHECK in place, so the table is rebuilt with its rows (panel edits included).

ALTER TABLE bastion RENAME TO bastion_old;

CREATE TABLE bastion (
    id                 TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    position           INTEGER NOT NULL,
    realm              TEXT    NOT NULL,                  -- the Realm it belongs to, shown in the menu
    name               TEXT    NOT NULL,
    tagline            TEXT    NOT NULL,                  -- shown when the party arrives
    guardian           TEXT    NOT NULL,                  -- a boss id
    key_item           TEXT    NOT NULL,                  -- custom item id; one is spent by the party leader
    palette            TEXT    NOT NULL CHECK (palette IN ('cinder', 'grove', 'necro')),
    radius             INTEGER NOT NULL CHECK (radius BETWEEN 12 AND 48),
    min_players        INTEGER NOT NULL DEFAULT 1 CHECK (min_players BETWEEN 1 AND 5),
    max_players        INTEGER NOT NULL DEFAULT 5 CHECK (max_players BETWEEN 1 AND 5),
    time_limit_seconds INTEGER NOT NULL CHECK (time_limit_seconds >= 120),   -- the whole instance, safety net over the boss's own limit
    deaths_allowed     INTEGER NOT NULL DEFAULT 3 CHECK (deaths_allowed BETWEEN 0 AND 10),
    origin             TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    CHECK (max_players >= min_players)
);

INSERT INTO bastion (id, position, realm, name, tagline, guardian, key_item, palette, radius, min_players, max_players,
                     time_limit_seconds, deaths_allowed, origin)
    SELECT id, position, realm, name, tagline, guardian, key_item, palette, radius, min_players, max_players,
           time_limit_seconds, deaths_allowed, origin FROM bastion_old;

DROP TABLE bastion_old;
