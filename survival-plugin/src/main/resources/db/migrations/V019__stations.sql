-- Stations (GDD 10.1, 17): Alembic brews, Ranch products and the contracts of the Contract Hunter. The Crucible and the
-- scalars are 'station.*' settings. Edit in the panel.

-- An Alembic recipe: three families (tonic = temporary resistance, oil = temporary element on the weapon, draught = utility).
CREATE TABLE alembic_recipe (
    id               TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    position         INTEGER NOT NULL,
    family           TEXT NOT NULL CHECK (family IN ('tonic', 'oil', 'draught')),
    name             TEXT NOT NULL,
    seconds          INTEGER NOT NULL CHECK (seconds >= 1),         -- brewing time: a queue, not a wall
    effect           TEXT NOT NULL,                                 -- a potion effect key (resistance...), or 'oil' (needs element)
    amplifier        INTEGER NOT NULL DEFAULT 0 CHECK (amplifier BETWEEN 0 AND 4),
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds BETWEEN 5 AND 3600),
    element          TEXT,                                          -- oils only: earth, frost, ember, void, astral
    min_tier         INTEGER NOT NULL DEFAULT 1 CHECK (min_tier BETWEEN 1 AND 5),   -- generator tier needed to start it
    origin           TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE alembic_ingredient (
    recipe_id TEXT NOT NULL REFERENCES alembic_recipe (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    item      TEXT NOT NULL,                                        -- a Material name or custom:<id>
    amount    INTEGER NOT NULL CHECK (amount >= 1),
    PRIMARY KEY (recipe_id, position)
);

-- What the Ranch makes. One unit every 'seconds' (before the tier bonus) once the generator tier reaches min_tier.
CREATE TABLE ranch_product (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    position INTEGER NOT NULL,
    item     TEXT NOT NULL,                                         -- a Material name or custom:<id>
    min_tier INTEGER NOT NULL DEFAULT 1 CHECK (min_tier BETWEEN 1 AND 5),
    seconds  INTEGER NOT NULL CHECK (seconds >= 10),
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A contract of the Contract Hunter: kill N mobs, deliver N items, or defeat a boss.
CREATE TABLE contract (
    id          TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    position    INTEGER NOT NULL,
    kind        TEXT NOT NULL CHECK (kind IN ('kill', 'deliver', 'boss')),
    title       TEXT NOT NULL,
    target      TEXT NOT NULL,                                      -- an EntityType name, an item key (Material or custom:<id>) or a boss id
    amount      INTEGER NOT NULL CHECK (amount >= 1),
    min_tier    INTEGER NOT NULL DEFAULT 1 CHECK (min_tier BETWEEN 1 AND 5),
    coins       INTEGER NOT NULL DEFAULT 0 CHECK (coins >= 0),
    shards      INTEGER NOT NULL DEFAULT 0 CHECK (shards >= 0),     -- Memory Shards
    reward_item TEXT,                                               -- optional item key
    reward_amount INTEGER NOT NULL DEFAULT 1 CHECK (reward_amount >= 1),
    origin      TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
