-- The quest plan (categories and their quests) and the daily pool.

CREATE TABLE quest_category (
    id          TEXT PRIMARY KEY,
    position    INTEGER NOT NULL,
    name        TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    main        INTEGER NOT NULL DEFAULT 0 CHECK (main IN (0, 1)),
    icon        TEXT    NOT NULL DEFAULT 'PAPER',
    unlock      TEXT    NOT NULL DEFAULT '',   -- quest id that opens the category, '' = open from the start
    origin      TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE quest (
    id           TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    pool         TEXT    NOT NULL CHECK (pool IN ('plan', 'daily')),
    position     INTEGER NOT NULL,
    category     TEXT    NOT NULL DEFAULT '',
    name         TEXT    NOT NULL,
    description  TEXT    NOT NULL DEFAULT '',
    type         TEXT    NOT NULL,             -- menu/QuestType
    target       TEXT    NOT NULL DEFAULT '',
    amount       INTEGER NOT NULL CHECK (amount >= 1),
    icon         TEXT    NOT NULL DEFAULT 'PAPER',
    reward_coins REAL    NOT NULL DEFAULT 0 CHECK (reward_coins >= 0),
    origin       TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE quest_requirement (
    quest_id TEXT    NOT NULL REFERENCES quest (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    requires TEXT    NOT NULL,
    PRIMARY KEY (quest_id, requires)
);

CREATE TABLE quest_reward_xp (
    quest_id TEXT NOT NULL REFERENCES quest (id) ON DELETE CASCADE,
    skill    TEXT NOT NULL,
    amount   REAL NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (quest_id, skill)
);

CREATE TABLE quest_reward_item (
    quest_id TEXT    NOT NULL REFERENCES quest (id) ON DELETE CASCADE,
    item     TEXT    NOT NULL,
    amount   INTEGER NOT NULL CHECK (amount >= 1),
    PRIMARY KEY (quest_id, item)
);

CREATE INDEX quest_by_pool ON quest (pool, position);
