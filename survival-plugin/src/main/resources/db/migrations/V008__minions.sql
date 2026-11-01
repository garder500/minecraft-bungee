-- Minions: the 11 tiers, the minion types and their drops, fuels, upgrade items, hats, crafting recipes and the
-- Auto Smelter table. The single tuning values are `setting` rows 'minions.<name>'.

-- One row per tier (tier I first). The tier-up columns are the cost of going from this tier to the next one.
CREATE TABLE minion_tier (
    id             TEXT PRIMARY KEY,                      -- tier1 .. tier11
    tier           INTEGER NOT NULL UNIQUE CHECK (tier BETWEEN 1 AND 11),
    action_factor  REAL    NOT NULL CHECK (action_factor > 0),  -- the type's action time is multiplied by this
    storage_slots  INTEGER NOT NULL CHECK (storage_slots >= 1), -- a slot holds one stack
    radius         INTEGER NOT NULL CHECK (radius >= 1),        -- working radius: 2 = 5x5, 3 = 7x7
    rarity         TEXT    NOT NULL DEFAULT 'COMMON',           -- rarity of the minion item
    chestplate     TEXT    NOT NULL,                            -- material the body wears
    tier_up_item   TEXT CHECK (tier_up_item IN ('compact', 'super')),  -- compacted form paid for the next tier, NULL on the last
    tier_up_amount INTEGER CHECK (tier_up_amount >= 1),
    origin         TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE minion_type (
    id             TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position       INTEGER NOT NULL,
    name           TEXT    NOT NULL,
    kind           TEXT    NOT NULL CHECK (kind IN ('MINING', 'FARMING', 'FORAGING', 'ANIMAL', 'COMBAT', 'FISHING')),
    action_seconds REAL    NOT NULL CHECK (action_seconds > 0),  -- tier I time between two actions
    hand           TEXT    NOT NULL,                             -- item the body holds
    hat            TEXT    NOT NULL,                             -- item the body wears
    block          TEXT,                                         -- MINING: placed and broken, FARMING: the crop, FORAGING: the log
    sapling        TEXT,                                         -- FORAGING only
    ageable        INTEGER NOT NULL DEFAULT 0 CHECK (ageable IN (0, 1)),  -- FARMING crops that have an age
    support        INTEGER NOT NULL DEFAULT 0 CHECK (support IN (0, 1)),  -- needs a solid block below the spot (sand)
    mob            TEXT,                                         -- ANIMAL / COMBAT creature spawned and killed
    mode           TEXT    NOT NULL DEFAULT 'each' CHECK (mode IN ('each', 'weighted')),  -- each drop rolls its own chance, or one drop picked by weight
    color          TEXT    NOT NULL DEFAULT '#888888',           -- hex colour of the icon
    recipe_ingredient TEXT NOT NULL,                             -- tier I recipe: 8 of these around the center item
    recipe_center  TEXT    NOT NULL,
    origin         TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE minion_drop (
    type_id    TEXT    NOT NULL REFERENCES minion_type (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,                  -- the first drop is the resource a tier-up is paid in
    item       TEXT    NOT NULL,
    min_amount INTEGER NOT NULL CHECK (min_amount >= 0),
    max_amount INTEGER NOT NULL CHECK (max_amount >= min_amount),
    chance     REAL    NOT NULL DEFAULT 1 CHECK (chance >= 0),  -- a probability (mode each) or a weight (mode weighted)
    PRIMARY KEY (type_id, position)
);

-- Fuels, burned only while the minion works. Speeds of fuel and upgrades add up.
CREATE TABLE minion_fuel (
    id         TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position   INTEGER NOT NULL,
    name       TEXT,                              -- NULL: the material's name
    rarity     TEXT    NOT NULL DEFAULT 'COMMON',
    material   TEXT    NOT NULL,                  -- base item
    custom     INTEGER NOT NULL DEFAULT 1 CHECK (custom IN (0, 1)),  -- 0: the plain vanilla item is the fuel
    speed      REAL    NOT NULL DEFAULT 0.05 CHECK (speed >= 0),     -- +fraction of speed (0.25 = 25 percent faster)
    hours      REAL    NOT NULL DEFAULT 1 CHECK (hours > 0),         -- how long one item lasts
    compressed TEXT,                              -- the fuel IS the Enchanted item made from this resource
    origin     TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- The kinds of minion a fuel works on; no row = every kind.
CREATE TABLE minion_fuel_kind (
    fuel_id TEXT NOT NULL REFERENCES minion_fuel (id) ON DELETE CASCADE,
    kind    TEXT NOT NULL CHECK (kind IN ('MINING', 'FARMING', 'FORAGING', 'ANIMAL', 'COMBAT', 'FISHING')),
    PRIMARY KEY (fuel_id, kind)
);

CREATE TABLE minion_upgrade (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    name     TEXT    NOT NULL,
    rarity   TEXT    NOT NULL DEFAULT 'UNCOMMON',
    material TEXT    NOT NULL,
    effect   TEXT    NOT NULL CHECK (effect IN ('SMELT', 'COMPACT', 'SUPER_COMPACT', 'SPREAD', 'EXPAND', 'SPEED')),
    speed    REAL    NOT NULL DEFAULT 0 CHECK (speed >= 0),    -- +fraction of speed
    burn     REAL    NOT NULL DEFAULT 1 CHECK (burn >= 0.1),   -- fuel burn rate multiplier (0.8 = fuel lasts 25 percent longer)
    radius   INTEGER NOT NULL DEFAULT 0 CHECK (radius >= 0),   -- extra working radius (EXPAND)
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE minion_upgrade_lore (
    upgrade_id TEXT    NOT NULL REFERENCES minion_upgrade (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    line       TEXT    NOT NULL,
    PRIMARY KEY (upgrade_id, position)
);

-- Cosmetic hats (the head item of the minion body).
CREATE TABLE minion_skin (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    name     TEXT    NOT NULL,
    rarity   TEXT    NOT NULL DEFAULT 'UNCOMMON',
    head     TEXT    NOT NULL,
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Shaped crafting recipes of upgrades, hats and custom fuels. Specs: MATERIAL (that plain vanilla item),
-- enchanted:MATERIAL, super:MATERIAL, upgrade:<id>, skin:<id>, fuel:<id>.
CREATE TABLE minion_recipe (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    result   TEXT    NOT NULL,                    -- upgrade:<id>, skin:<id> or fuel:<id>
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- The rows of the shape (1 to 3 rows of 1 to 3 characters).
CREATE TABLE minion_recipe_row (
    recipe_id TEXT    NOT NULL REFERENCES minion_recipe (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL CHECK (position BETWEEN 0 AND 2),
    pattern   TEXT    NOT NULL CHECK (length(pattern) BETWEEN 1 AND 3),
    PRIMARY KEY (recipe_id, position)
);

CREATE TABLE minion_recipe_ingredient (
    recipe_id TEXT NOT NULL REFERENCES minion_recipe (id) ON DELETE CASCADE,
    letter    TEXT NOT NULL CHECK (length(letter) = 1 AND letter <> ' '),
    spec      TEXT NOT NULL,
    PRIMARY KEY (recipe_id, letter)
);

-- What the Auto Smelter turns an item into.
CREATE TABLE minion_smelt (
    id       TEXT PRIMARY KEY,                    -- the raw item
    position INTEGER NOT NULL,
    result   TEXT    NOT NULL,
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
