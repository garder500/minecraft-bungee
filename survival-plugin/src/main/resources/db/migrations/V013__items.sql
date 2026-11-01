-- The items feature: combat numbers, item definitions, armor sets, mobs, vanilla-ingredient recipes and the loot tables.
-- The scalars of the former items.yml (stats engine, combat, mana, HUD, fortune, costs, Tempering Rune, enchant and reforge
-- tables, accessories, pets, abilities, set parameters) are 'setting' rows 'items.<path>'.

-- A custom item. owner says which feature registers it ('items' = the catalog, 'quarry' = the ore ladder, 'boss' = the
-- boss weapons). damage = the weapon damage (NULL = not a weapon). Stats, abilities, lore and extra PDC values hang off it.
CREATE TABLE item_def (
    id          TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    owner       TEXT NOT NULL DEFAULT 'items' CHECK (owner IN ('items', 'quarry', 'boss')),
    position    INTEGER NOT NULL,
    name        TEXT NOT NULL,                  -- without colour codes: the rarity colours it
    rarity      TEXT NOT NULL CHECK (rarity IN ('COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC', 'DIVINE', 'SPECIAL')),
    material    TEXT NOT NULL,
    item_model  TEXT,                           -- resource pack item_model key
    type_label  TEXT,                           -- SWORD, CHESTPLATE, WAND...
    damage      REAL CHECK (damage IS NULL OR damage >= 0),
    unbreakable INTEGER NOT NULL DEFAULT 0 CHECK (unbreakable IN (0, 1)),
    hide_flags  INTEGER NOT NULL DEFAULT 1 CHECK (hide_flags IN (0, 1)),
    glint       INTEGER NOT NULL DEFAULT 0 CHECK (glint IN (0, 1)),
    max_stack   INTEGER CHECK (max_stack IS NULL OR max_stack BETWEEN 1 AND 99),
    equip_slot  TEXT CHECK (equip_slot IS NULL OR equip_slot IN ('HEAD', 'CHEST', 'LEGS', 'FEET', 'BODY')),
    equip_asset TEXT,                           -- equipment asset key of the worn model
    origin      TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Base stats shown on the item. stat = a Stat key (vitality, ward, might...); an old name is still understood.
CREATE TABLE item_stat (
    item_id TEXT NOT NULL REFERENCES item_def (id) ON DELETE CASCADE,
    stat    TEXT NOT NULL,
    value   REAL NOT NULL,
    PRIMARY KEY (item_id, stat)
);

-- Ability ids of the item, in the order they are listed.
CREATE TABLE item_ability (
    item_id  TEXT    NOT NULL REFERENCES item_def (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    ability  TEXT    NOT NULL,
    PRIMARY KEY (item_id, position)
);

-- Description lines (MiniMessage).
CREATE TABLE item_lore (
    item_id  TEXT    NOT NULL REFERENCES item_def (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    line     TEXT    NOT NULL,
    PRIMARY KEY (item_id, position)
);

-- Other values written on the stack when it is created (set, tier, family, apply, fuel...).
CREATE TABLE item_pdc (
    item_id TEXT NOT NULL REFERENCES item_def (id) ON DELETE CASCADE,
    key     TEXT NOT NULL CHECK (key GLOB '[a-z0-9_]*'),
    kind    TEXT NOT NULL CHECK (kind IN ('string', 'long', 'double')),
    value   TEXT NOT NULL,
    PRIMARY KEY (item_id, key)
);

-- Armor sets (four pieces in three tiers, the full-set bonus). The pieces themselves are item_def rows.
CREATE TABLE armor_set (
    id         TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position   INTEGER NOT NULL,
    name       TEXT NOT NULL,
    material   TEXT NOT NULL,                   -- material prefix of the pieces (IRON, LEATHER...)
    bonus_name TEXT NOT NULL,
    origin     TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- role 'piece' = stats of a tier 1 chestplate, 'bonus' = stats of the full set bonus at tier 1.
CREATE TABLE armor_set_stat (
    set_id TEXT NOT NULL REFERENCES armor_set (id) ON DELETE CASCADE,
    role   TEXT NOT NULL CHECK (role IN ('piece', 'bonus')),
    stat   TEXT NOT NULL,
    value  REAL NOT NULL,
    PRIMARY KEY (set_id, role, stat)
);

CREATE TABLE armor_set_line (
    set_id   TEXT    NOT NULL REFERENCES armor_set (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    line     TEXT    NOT NULL,
    PRIMARY KEY (set_id, position)
);

-- Natural mobs of the Skyblock worlds: level shown, absolute max health and damage (0 = vanilla x the multipliers).
CREATE TABLE item_mob (
    entity TEXT PRIMARY KEY,                    -- vanilla EntityType name
    level  INTEGER NOT NULL CHECK (level >= 0),
    health REAL    NOT NULL DEFAULT 0 CHECK (health >= 0),
    damage REAL    NOT NULL DEFAULT 0 CHECK (damage >= 0),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Crafting-table recipes of the starting gear (shape rows and symbol -> material).
CREATE TABLE item_recipe (
    id     TEXT PRIMARY KEY,
    result TEXT    NOT NULL,                    -- custom item id
    amount INTEGER NOT NULL DEFAULT 1 CHECK (amount >= 1),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE item_recipe_row (
    recipe_id TEXT    NOT NULL REFERENCES item_recipe (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    shape     TEXT    NOT NULL,
    PRIMARY KEY (recipe_id, position)
);

CREATE TABLE item_recipe_ingredient (
    recipe_id TEXT NOT NULL REFERENCES item_recipe (id) ON DELETE CASCADE,
    symbol    TEXT NOT NULL CHECK (length(symbol) = 1),
    material  TEXT NOT NULL,
    PRIMARY KEY (recipe_id, symbol)
);

-- Loot rolls of boss kills, natural mob kills and fishing. chance is a percent per roll. kind item: item = catalog id
-- (@armor:N = a random armor piece of tier N); book: item = enchant id (@ultimate), level or max_level; pet: item = species.
CREATE TABLE item_loot (
    id        TEXT PRIMARY KEY,
    position  INTEGER NOT NULL,
    kind      TEXT    NOT NULL CHECK (kind IN ('item', 'book', 'pet')),
    item      TEXT    NOT NULL,
    level     INTEGER NOT NULL DEFAULT 1 CHECK (level >= 1),
    max_level INTEGER NOT NULL DEFAULT 0 CHECK (max_level >= 0),
    rarity    TEXT    NOT NULL DEFAULT '',
    amount    INTEGER NOT NULL DEFAULT 1 CHECK (amount >= 1),
    chance    REAL    NOT NULL CHECK (chance >= 0),
    origin    TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Where a loot row rolls: boss, boss:<id>, mob, mob:<ENTITY_TYPE>, fishing.
CREATE TABLE item_loot_source (
    loot_id  TEXT    NOT NULL REFERENCES item_loot (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    source   TEXT    NOT NULL,
    PRIMARY KEY (loot_id, position)
);
