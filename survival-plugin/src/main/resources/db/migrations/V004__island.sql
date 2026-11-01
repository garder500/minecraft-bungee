-- The island feature: worlds, upgrade tiers, creation templates, chests, level weights. Its single values are
-- 'setting' rows 'island.<path>' (grid, generation, protection, border, portals, invites, reset, level, warps...).

-- One row per Skyblock world key (hub, world, nether, end).
CREATE TABLE island_world (
    key           TEXT PRIMARY KEY CHECK (key IN ('hub', 'world', 'nether', 'end')),
    time          TEXT    NOT NULL DEFAULT 'CYCLE',   -- DAY, NOON, SUNSET, NIGHT, MIDNIGHT or CYCLE (vanilla day/night)
    border        REAL    NOT NULL DEFAULT 60000000 CHECK (border > 0),
    weather_clear INTEGER NOT NULL DEFAULT 0 CHECK (weather_clear IN (0, 1)),
    origin        TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A game rule of a world: lower-case name of org.bukkit.GameRules, value true/false or a whole number.
CREATE TABLE island_world_gamerule (
    world    TEXT    NOT NULL REFERENCES island_world (key) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    rule     TEXT    NOT NULL,
    value    TEXT    NOT NULL,
    PRIMARY KEY (world, rule)
);

-- One step of an upgrade ladder. The first step of each kind is the starting value (its cost is forced to 0).
CREATE TABLE island_upgrade (
    id       TEXT PRIMARY KEY,                         -- e.g. 'members.2'
    kind     TEXT    NOT NULL CHECK (kind IN ('radius', 'members', 'minions')),
    position INTEGER NOT NULL,
    value    INTEGER NOT NULL CHECK (value >= 0),      -- border radius, member slots or minion slots reached
    cost     REAL    NOT NULL DEFAULT 0 CHECK (cost >= 0),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- An island type offered by /is create, in menu order.
CREATE TABLE island_template (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    enabled  INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    name     TEXT    NOT NULL,
    icon     TEXT    NOT NULL DEFAULT 'GRASS_BLOCK',
    biome    TEXT    NOT NULL DEFAULT '',              -- Bukkit biome key, '' = leave as is
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE island_template_line (
    template_id TEXT    NOT NULL REFERENCES island_template (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    text        TEXT    NOT NULL,                      -- one line of the description
    PRIMARY KEY (template_id, position)
);

CREATE TABLE island_template_item (
    template_id TEXT    NOT NULL REFERENCES island_template (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    material    TEXT    NOT NULL,
    amount      INTEGER NOT NULL DEFAULT 1 CHECK (amount >= 1),
    PRIMARY KEY (template_id, position)
);

-- The other chests: the one on every island after the first, and the Nether and End platform chests.
CREATE TABLE island_chest (
    id     TEXT PRIMARY KEY CHECK (id IN ('starter-repeat', 'nether', 'end')),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE island_chest_item (
    chest_id TEXT    NOT NULL REFERENCES island_chest (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    material TEXT    NOT NULL,
    amount   INTEGER NOT NULL DEFAULT 1 CHECK (amount >= 1),
    PRIMARY KEY (chest_id, position)
);

-- Value of ONE block of a material for the island level.
CREATE TABLE island_level_weight (
    material TEXT PRIMARY KEY,
    weight   REAL NOT NULL CHECK (weight >= 0),
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Default of an island flag, copied into every NEW island.
CREATE TABLE island_flag_default (
    flag   TEXT PRIMARY KEY,
    value  INTEGER NOT NULL CHECK (value IN (0, 1)),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
