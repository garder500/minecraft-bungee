-- Paragon (GDD 11.5 and 19.1): the Transcendence ranks of the players who survived the Astral Interior, and the ultimate
-- capacity of each class. A rank is reached with Paragon points (the raid, Hell Gao, later challenges) and gives one reward:
-- a title, an aura, a server utility or a lore fragment. No rank gives a stat (GDD 9.2: no new explosion of statistics).
-- Scalars are 'paragon.*' settings.

CREATE TABLE paragon_rank (
    rank         INTEGER PRIMARY KEY CHECK (rank BETWEEN 1 AND 20),
    name         TEXT    NOT NULL,
    points       INTEGER NOT NULL CHECK (points >= 1),         -- total Paragon points needed (strictly increasing with the rank)
    reward_kind  TEXT    NOT NULL CHECK (reward_kind IN ('TITLE', 'AURA', 'UTILITY', 'LORE')),
    reward_key   TEXT    NOT NULL,                              -- TITLE: the title text. AURA: aura id. UTILITY: perk key. LORE: fragment id
    reward_value INTEGER NOT NULL DEFAULT 0,                    -- UTILITY: the amount added to the perk
    reward_text  TEXT    NOT NULL,                              -- what the player reads
    origin       TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE paragon_ultimate (
    class            TEXT PRIMARY KEY CHECK (class IN ('VANGUARD', 'DUELIST', 'CHANNELER', 'RANGER')),
    name             TEXT NOT NULL,
    summary          TEXT NOT NULL,
    aether_cost      REAL NOT NULL CHECK (aether_cost >= 0),
    cooldown_seconds INTEGER NOT NULL CHECK (cooldown_seconds >= 1),
    radius           REAL NOT NULL DEFAULT 0,   -- Vanguard / Channeler: blocks. Ranger: degrees between two arrows
    power            REAL NOT NULL DEFAULT 0,   -- Duelist: hits that are Surges. Channeler: percent of the weapon damage. Ranger: arrows
    seconds          REAL NOT NULL DEFAULT 0,   -- Vanguard: duration of the effects. Duelist: how long the hits are kept
    origin           TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
