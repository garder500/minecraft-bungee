-- Skills: the eight skills with their level rewards, the experience each activity gives, sea creatures, collections
-- and their tiers, the "Enchanted" compacted items and the collection-gated recipes. The scalars (level curve, coin
-- rewards, anti-farm numbers, leaderboard, switches, lists of worlds / entities / spawn reasons) are `setting` rows
-- 'skills.<path>'.

-- One row per skill (id = the skill's lower case key: combat, mining...).
CREATE TABLE skill (
    id         TEXT PRIMARY KEY CHECK (id GLOB '[a-z]*'),
    max_level  INTEGER NOT NULL CHECK (max_level >= 1),
    multiplier REAL    NOT NULL DEFAULT 1 CHECK (multiplier >= 0),   -- extra experience factor of this skill
    origin     TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Stat bonus given per level: every level from_level..to_level adds `amount` of the stat. Stat names are plain text
-- (the stats engine owns the list). `position` orders the ranges of a skill, stats in the order they first appear.
CREATE TABLE skill_stat_range (
    skill_id   TEXT    NOT NULL REFERENCES skill (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    stat       TEXT    NOT NULL,
    from_level INTEGER NOT NULL CHECK (from_level >= 1),
    to_level   INTEGER NOT NULL CHECK (to_level >= from_level),
    amount     REAL    NOT NULL,
    PRIMARY KEY (skill_id, position)
);

-- The line shown when a level is reached.
CREATE TABLE skill_unlock (
    skill_id TEXT    NOT NULL REFERENCES skill (id) ON DELETE CASCADE,
    level    INTEGER NOT NULL CHECK (level >= 1),
    text     TEXT    NOT NULL,
    PRIMARY KEY (skill_id, level)
);

-- Experience per block broken (blocks placed by players give nothing).
CREATE TABLE skill_block_xp (
    material     TEXT PRIMARY KEY,
    position     INTEGER NOT NULL,
    skill        TEXT    NOT NULL CHECK (skill IN ('mining', 'foraging', 'farming')),
    xp           REAL    NOT NULL CHECK (xp >= 0),
    mature_only  INTEGER NOT NULL DEFAULT 0 CHECK (mature_only IN (0, 1)),   -- crops: only pay when fully grown
    column_plant INTEGER NOT NULL DEFAULT 0 CHECK (column_plant IN (0, 1)),  -- grows upwards: a break pays the grown blocks above too
    origin       TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Combat experience per kill, by entity type (other hostile mobs give max health * the health factor setting).
CREATE TABLE skill_mob_xp (
    entity   TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    xp       REAL    NOT NULL CHECK (xp >= 0),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Fishing experience per caught item.
CREATE TABLE skill_catch_xp (
    material TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    xp       REAL    NOT NULL CHECK (xp >= 0),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Fishing experience per kill of a tagged sea creature, by entity type.
CREATE TABLE skill_sea_xp (
    entity   TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    xp       REAL    NOT NULL CHECK (xp >= 0),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Alchemy experience per potion changed in a brewing stand, by the ingredient used.
CREATE TABLE skill_brew_xp (
    material TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    xp       REAL    NOT NULL CHECK (xp >= 0),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Carpentry experience per crafted item (vanilla recipes).
CREATE TABLE skill_craft_xp (
    material TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    xp       REAL    NOT NULL CHECK (xp >= 0),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A catch can summon a tagged vanilla mob instead of (in addition to) the loot.
CREATE TABLE sea_creature (
    id        TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position  INTEGER NOT NULL,
    entity    TEXT    NOT NULL,                   -- vanilla entity type
    name      TEXT    NOT NULL,
    min_level INTEGER NOT NULL DEFAULT 0 CHECK (min_level >= 0),  -- Fishing level needed
    weight    REAL    NOT NULL DEFAULT 1 CHECK (weight >= 0),     -- relative odds
    health    REAL    NOT NULL DEFAULT 20 CHECK (health >= 1),    -- max health
    origin    TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A collection ladder shared by several collections: how many items per tier and the coins each tier pays once.
CREATE TABLE collection_preset (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE collection_preset_tier (
    preset_id TEXT    NOT NULL REFERENCES collection_preset (id) ON DELETE CASCADE,
    tier      INTEGER NOT NULL CHECK (tier >= 1),
    amount    INTEGER NOT NULL CHECK (amount >= 1),   -- items gathered to reach the tier
    coins     REAL    NOT NULL DEFAULT 0 CHECK (coins >= 0),
    PRIMARY KEY (preset_id, tier)
);

-- Counters of items gathered (block drops, mob drops, fishing).
CREATE TABLE collection (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[A-Z0-9_]*'),
    position INTEGER NOT NULL,
    name     TEXT,                                -- NULL: the id, capitalised
    category TEXT    NOT NULL,                    -- skill name: MINING, FARMING, FORAGING, COMBAT, FISHING...
    preset   TEXT    NOT NULL DEFAULT 'common',   -- collection_preset.id
    icon     TEXT,                                -- NULL: the first material
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- The items that count in a collection (the first one is the default icon).
CREATE TABLE collection_material (
    collection_id TEXT    NOT NULL REFERENCES collection (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,
    material      TEXT    NOT NULL,
    PRIMARY KEY (collection_id, position)
);

-- Compacted resources ("Enchanted X"). tier 1 = enchanted, tier 2 = compacted.
CREATE TABLE enchanted_item (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    name     TEXT    NOT NULL,
    material TEXT    NOT NULL,
    rarity   TEXT    NOT NULL DEFAULT 'UNCOMMON',
    model    TEXT    NOT NULL,                    -- item model key in the resource pack
    tier     INTEGER NOT NULL DEFAULT 1 CHECK (tier >= 1),
    origin   TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE enchanted_item_lore (
    item_id  TEXT    NOT NULL REFERENCES enchanted_item (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    line     TEXT    NOT NULL,
    PRIMARY KEY (item_id, position)
);

-- Shapeless recipes: any arrangement of exactly the listed number of slots, each slot needing `per_slot` items.
CREATE TABLE skill_recipe (
    id            TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position      INTEGER NOT NULL,
    result_custom TEXT,                           -- custom item id, or...
    result_item   TEXT,                           -- ...a vanilla material
    amount        INTEGER NOT NULL DEFAULT 1 CHECK (amount BETWEEN 1 AND 64),
    xp_skill      TEXT,                           -- optional experience per craft
    xp_amount     REAL    NOT NULL DEFAULT 0 CHECK (xp_amount >= 0),
    origin        TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    CHECK (result_custom IS NOT NULL OR result_item IS NOT NULL)
);

-- At most 9 slots in total over a recipe (checked when it is loaded).
CREATE TABLE skill_recipe_ingredient (
    recipe_id TEXT    NOT NULL REFERENCES skill_recipe (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    item      TEXT,                               -- a vanilla material, or...
    custom    TEXT,                               -- ...a custom item id
    slots     INTEGER NOT NULL CHECK (slots BETWEEN 1 AND 9),
    per_slot  INTEGER NOT NULL CHECK (per_slot BETWEEN 1 AND 64),
    PRIMARY KEY (recipe_id, position),
    CHECK ((item IS NULL) <> (custom IS NULL))
);

-- The crafter needs every listed collection tier; until then the result shows "Recipe locked".
CREATE TABLE skill_recipe_requirement (
    recipe_id  TEXT    NOT NULL REFERENCES skill_recipe (id) ON DELETE CASCADE,
    collection TEXT    NOT NULL,                  -- collection.id
    tier       INTEGER NOT NULL CHECK (tier >= 1),
    PRIMARY KEY (recipe_id, collection)
);
