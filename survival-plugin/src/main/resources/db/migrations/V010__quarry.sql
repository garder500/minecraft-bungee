-- The Quarry: the ore table (cobble draw and the ring generators' blocks), the Forge recipes and the numbers around them.

-- One ore that a broken generator block can give. position = order of the table (rarest first: the first ore that hits wins).
CREATE TABLE ore_roll (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z]*'),
    position INTEGER NOT NULL,
    item     TEXT    NOT NULL,                      -- a vanilla material or custom:<item id>
    source   TEXT    NOT NULL DEFAULT 'cobble' CHECK (source IN ('cobble', 'frost', 'ember')),
                                                    -- cobble = the island generator; frost / ember = the blocks of those ring generators
    pity     INTEGER NOT NULL DEFAULT 0 CHECK (pity >= 0),  -- blocks without this ore after which the next one is guaranteed (0 = none)
    geode    INTEGER NOT NULL DEFAULT 0 CHECK (geode IN (0, 1)),  -- a geode of the generator is made of the first geode ore
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Percent per block for each generator tier (1 = Stone ... 4 = Void). No row or 0 = not available at that tier.
CREATE TABLE ore_roll_chance (
    ore_roll_id TEXT    NOT NULL REFERENCES ore_roll (id) ON DELETE CASCADE,
    tier        INTEGER NOT NULL CHECK (tier >= 1),
    chance      REAL    NOT NULL CHECK (chance >= 0 AND chance <= 100),
    PRIMARY KEY (ore_roll_id, tier)
);

-- Optional: the ore only drops from these block types of its source (empty = every block of the source).
CREATE TABLE ore_roll_block (
    ore_roll_id TEXT NOT NULL REFERENCES ore_roll (id) ON DELETE CASCADE,
    block       TEXT NOT NULL,                      -- a Material name
    PRIMARY KEY (ore_roll_id, block)
);

-- A Forge job: what it makes, for how long, and what it costs.
CREATE TABLE forge_recipe (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    result   TEXT    NOT NULL,                      -- custom item id
    amount   INTEGER NOT NULL DEFAULT 1 CHECK (amount >= 1),
    seconds  INTEGER NOT NULL DEFAULT 60 CHECK (seconds >= 1),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE forge_recipe_ingredient (
    forge_recipe_id TEXT    NOT NULL REFERENCES forge_recipe (id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,
    item            TEXT    NOT NULL,               -- a vanilla material or custom:<item id>
    amount          INTEGER NOT NULL CHECK (amount >= 1),
    PRIMARY KEY (forge_recipe_id, item)
);

-- The shapeless crafting recipe of the Forge block itself (at most 9 items in all).
CREATE TABLE forge_block_ingredient (
    item     TEXT PRIMARY KEY,                      -- a vanilla material
    position INTEGER NOT NULL,
    amount   INTEGER NOT NULL CHECK (amount >= 1),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
