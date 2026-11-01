-- Bosses, slayers, summon recipes and the world boss events. Scalars (global boss knobs, slayer switches, world boss
-- timing) are 'setting' rows 'boss.settings.*', 'boss.slayer.*' and 'boss.world.*'.

-- A boss fought with a summon item or spawned by code. One row = everything about it; the other tables hang off it.
CREATE TABLE boss (
    id                   TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    kind                 TEXT NOT NULL DEFAULT 'boss' CHECK (kind IN ('boss', 'world')),
    tier                 INTEGER CHECK (tier IS NULL OR tier BETWEEN 1 AND 5),   -- power tier, NULL = not rated
    name                 TEXT NOT NULL,                  -- MiniMessage
    description          TEXT NOT NULL DEFAULT '',
    entity               TEXT NOT NULL,                  -- vanilla EntityType of the body
    health               REAL NOT NULL CHECK (health > 0),   -- virtual HP
    damage               REAL NOT NULL DEFAULT 6 CHECK (damage >= 0),   -- vanilla HP per hit, 2 = one heart
    armor                REAL NOT NULL DEFAULT 0,
    scale                REAL NOT NULL DEFAULT 1,
    speed                REAL NOT NULL DEFAULT 1,
    knockback_resistance REAL NOT NULL DEFAULT 1,
    bar_color            TEXT NOT NULL DEFAULT 'PURPLE',
    bar_overlay          TEXT NOT NULL DEFAULT 'PROGRESS',
    -- arena rules
    arena_radius                REAL    NOT NULL DEFAULT 60,    -- players farther than this cannot hurt it
    arena_leash                 REAL    NOT NULL DEFAULT 70,
    arena_projectile_range      REAL    NOT NULL DEFAULT 64,
    arena_no_fly                INTEGER NOT NULL DEFAULT 0 CHECK (arena_no_fly IN (0, 1)),
    arena_no_build              INTEGER NOT NULL DEFAULT 1 CHECK (arena_no_build IN (0, 1)),
    arena_no_teleport           INTEGER NOT NULL DEFAULT 1 CHECK (arena_no_teleport IN (0, 1)),
    arena_min_players           INTEGER NOT NULL DEFAULT 1,
    arena_scale_per_player      REAL    NOT NULL DEFAULT 0.25,
    arena_scale_cap             REAL    NOT NULL DEFAULT 3,
    arena_enrage_seconds        INTEGER NOT NULL DEFAULT 600,
    arena_enrage_damage         REAL    NOT NULL DEFAULT 1.5,
    arena_enrage_growth         REAL    NOT NULL DEFAULT 0.25,
    arena_idle_despawn_seconds  INTEGER NOT NULL DEFAULT 60,
    arena_time_limit_seconds    INTEGER NOT NULL DEFAULT 0,
    arena_max_minions           INTEGER NOT NULL DEFAULT 12,
    -- entity options (NULL = the entity's default)
    option_size           INTEGER,          -- slime size
    option_baby           INTEGER CHECK (option_baby IN (0, 1)),
    option_glow           INTEGER CHECK (option_glow IN (0, 1)),
    option_contact_radius REAL,             -- melee reach used by the hidden body
    -- loot
    loot_coins            REAL NOT NULL DEFAULT 0 CHECK (loot_coins >= 0),   -- split by damage share
    loot_combat_xp        REAL NOT NULL DEFAULT 0 CHECK (loot_combat_xp >= 0),
    loot_min_share        REAL NOT NULL DEFAULT 0.01 CHECK (loot_min_share BETWEEN 0 AND 1),   -- below it: nothing
    loot_share_floor      REAL NOT NULL DEFAULT 0.25 CHECK (loot_share_floor BETWEEN 0 AND 1), -- part split equally
    loot_wings_min_share  REAL NOT NULL DEFAULT -1,     -- damage share that grants the wings, negative = never
    loot_announce_below   REAL NOT NULL DEFAULT 0.01,   -- drops at or under this chance are announced
    origin                TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE boss_equipment (
    boss_id  TEXT NOT NULL REFERENCES boss (id) ON DELETE CASCADE,
    slot     TEXT NOT NULL CHECK (slot IN ('head', 'chest', 'legs', 'feet', 'mainhand', 'offhand')),
    material TEXT NOT NULL,
    color    INTEGER,                                   -- RGB of dyed leather, NULL = not dyed
    PRIMARY KEY (boss_id, slot)
);

-- A phase starts when the health fraction drops to 'at' (the first is always 1).
CREATE TABLE boss_phase (
    boss_id    TEXT    NOT NULL REFERENCES boss (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    name       TEXT    NOT NULL,
    at         REAL    NOT NULL,
    title      TEXT    NOT NULL DEFAULT '',
    subtitle   TEXT    NOT NULL DEFAULT '',
    damage     REAL    NOT NULL DEFAULT 1,              -- damage multiplier of the phase
    speed      REAL    NOT NULL DEFAULT 1,
    PRIMARY KEY (boss_id, position)
);

-- role 'enter' = cast once when the phase starts, 'cast' = cast on cooldown while it lasts.
CREATE TABLE boss_ability (
    boss_id   TEXT    NOT NULL REFERENCES boss (id) ON DELETE CASCADE,
    phase     INTEGER NOT NULL,
    role      TEXT    NOT NULL CHECK (role IN ('enter', 'cast')),
    position  INTEGER NOT NULL,
    type      TEXT    NOT NULL,                         -- boss/AbilityType
    cooldown  REAL    NOT NULL DEFAULT 12,              -- seconds
    telegraph REAL    NOT NULL DEFAULT 0,               -- seconds of warning, 0 = the ability's own
    damage    REAL    NOT NULL DEFAULT 1,               -- multiplier of the boss damage
    target    TEXT    NOT NULL DEFAULT 'nearest' CHECK (target IN ('nearest', 'random', 'top', 'far', 'all')),
    from_tier INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (boss_id, phase, role, position)
);

-- The ability-specific numbers and words (radius, count, duration, entity, weapon...): one of num / text is set.
CREATE TABLE boss_ability_param (
    boss_id  TEXT    NOT NULL REFERENCES boss (id) ON DELETE CASCADE,
    phase    INTEGER NOT NULL,
    role     TEXT    NOT NULL,
    ability  INTEGER NOT NULL,                          -- position of the ability
    name     TEXT    NOT NULL,
    num      REAL,
    text     TEXT,
    PRIMARY KEY (boss_id, phase, role, ability, name),
    CHECK ((num IS NULL) <> (text IS NULL))
);

-- Chance multiplier of rank 1, 2, 3...
CREATE TABLE boss_rank_bonus (
    boss_id    TEXT    NOT NULL REFERENCES boss (id) ON DELETE CASCADE,
    rank       INTEGER NOT NULL CHECK (rank >= 1),
    multiplier REAL    NOT NULL CHECK (multiplier >= 0),
    PRIMARY KEY (boss_id, rank)
);

-- Item drops. item = custom:<id> or vanilla:<MATERIAL>. Pity: every dry kill adds pity to the chance, up to pity_cap,
-- and the loot maths never lets base + pity pass 1.4 x base.
CREATE TABLE boss_drop (
    boss_id    TEXT    NOT NULL REFERENCES boss (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    item       TEXT    NOT NULL,
    chance     REAL    NOT NULL CHECK (chance BETWEEN 0 AND 1),
    min_amount INTEGER NOT NULL DEFAULT 1 CHECK (min_amount >= 1),
    max_amount INTEGER NOT NULL DEFAULT 1 CHECK (max_amount >= min_amount),
    min_share  REAL    NOT NULL DEFAULT 0,              -- damage share needed
    max_rank   INTEGER NOT NULL DEFAULT 0,              -- worst rank allowed, 0 = anyone
    magic_find INTEGER NOT NULL DEFAULT 1 CHECK (magic_find IN (0, 1)),
    pity       REAL    NOT NULL DEFAULT 0 CHECK (pity >= 0),       -- chance added per dry kill
    pity_cap   REAL    NOT NULL DEFAULT 0.015 CHECK (pity_cap >= 0),   -- largest pity bonus
    from_tier  INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (boss_id, position)
);

-- How the boss is summoned; no row = spawned by code only.
CREATE TABLE boss_summon (
    boss_id                TEXT PRIMARY KEY REFERENCES boss (id) ON DELETE CASCADE,
    item                   TEXT    NOT NULL,            -- custom item that summons it
    mode                   TEXT    NOT NULL CHECK (mode IN ('altar', 'free')),
    altar_world            TEXT,                        -- the altar columns are set for the altar mode only
    altar_x                REAL,
    altar_y                REAL,
    altar_z                REAL,
    altar_radius           REAL,
    altar_cell_size        REAL,                        -- the altar repeats on this grid (island cells), 0 = single
    max_spawn_distance     REAL    NOT NULL DEFAULT 0,  -- from the world spawn, 0 = no limit
    cooldown_seconds       INTEGER NOT NULL DEFAULT 60 CHECK (cooldown_seconds >= 0),
    global_cooldown_seconds INTEGER NOT NULL DEFAULT 0 CHECK (global_cooldown_seconds >= 0),
    require_island_member  INTEGER NOT NULL DEFAULT 1 CHECK (require_island_member IN (0, 1)),
    require_water          INTEGER NOT NULL DEFAULT 0 CHECK (require_water IN (0, 1)),
    max_concurrent         INTEGER NOT NULL DEFAULT 1 CHECK (max_concurrent >= 1),
    global_max_concurrent  INTEGER NOT NULL DEFAULT 6,
    refund_on_despawn      INTEGER NOT NULL DEFAULT 1 CHECK (refund_on_despawn IN (0, 1))
);

CREATE TABLE boss_summon_world (
    boss_id  TEXT    NOT NULL REFERENCES boss (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    world    TEXT    NOT NULL,
    PRIMARY KEY (boss_id, position)
);

-- A slayer line (Zombie, Spider...): quest, five tiers, perks. Each tier becomes a personal boss 'slayer_<id>_<tier>'.
CREATE TABLE slayer (
    id                 TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    position           INTEGER NOT NULL,
    name               TEXT NOT NULL,
    icon               TEXT NOT NULL DEFAULT 'ROTTEN_FLESH',
    description        TEXT NOT NULL DEFAULT '',
    entity             TEXT NOT NULL,                   -- body of the personal boss
    default_kill_xp    REAL NOT NULL DEFAULT 5 CHECK (default_kill_xp > 0),   -- quest progress of a target without its own value
    bar_color          TEXT NOT NULL DEFAULT 'RED',
    bar_overlay        TEXT NOT NULL DEFAULT 'PROGRESS',
    option_size           INTEGER,
    option_baby           INTEGER CHECK (option_baby IN (0, 1)),
    option_glow           INTEGER CHECK (option_glow IN (0, 1)),
    option_contact_radius REAL,
    -- frenzy phase from tier frenzy_from_tier; NULL = none
    frenzy_from_tier   INTEGER,
    frenzy_at          REAL,
    frenzy_damage      REAL,
    frenzy_speed       REAL,
    frenzy_title       TEXT,
    frenzy_subtitle    TEXT,
    loot_min_share     REAL NOT NULL DEFAULT 0.01 CHECK (loot_min_share BETWEEN 0 AND 1),
    loot_share_floor   REAL NOT NULL DEFAULT 0.25 CHECK (loot_share_floor BETWEEN 0 AND 1),
    loot_announce_below REAL NOT NULL DEFAULT 0.01,
    origin             TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Mobs whose kills fill the quest; kill_xp NULL = the slayer's default_kill_xp.
CREATE TABLE slayer_target (
    slayer_id TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    entity    TEXT    NOT NULL,
    kill_xp   REAL,
    PRIMARY KEY (slayer_id, position)
);

CREATE TABLE slayer_world (
    slayer_id TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    world     TEXT    NOT NULL,
    PRIMARY KEY (slayer_id, position)
);

-- Slayer experience needed for level 1..9 (cumulative, strictly increasing).
CREATE TABLE slayer_level (
    slayer_id TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    level     INTEGER NOT NULL CHECK (level BETWEEN 1 AND 9),
    xp        INTEGER NOT NULL CHECK (xp >= 0),
    PRIMARY KEY (slayer_id, level)
);

CREATE TABLE slayer_perk (
    slayer_id TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    level     INTEGER NOT NULL CHECK (level BETWEEN 1 AND 9),
    stat      TEXT    NOT NULL,                         -- api/Stat
    amount    REAL    NOT NULL,
    PRIMARY KEY (slayer_id, position)
);

CREATE TABLE slayer_tier (
    slayer_id    TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    tier         INTEGER NOT NULL CHECK (tier BETWEEN 1 AND 5),
    cost         REAL    NOT NULL CHECK (cost > 0),      -- coins to start the quest
    required_xp  REAL    NOT NULL CHECK (required_xp > 0),   -- quest progress to spawn the boss
    health       REAL    NOT NULL CHECK (health > 0),
    damage       REAL    NOT NULL CHECK (damage > 0),
    slayer_xp    REAL    NOT NULL CHECK (slayer_xp > 0),     -- slayer experience for a win
    time_limit   REAL    NOT NULL CHECK (time_limit > 0),    -- seconds to kill the boss
    coins        REAL    NOT NULL DEFAULT 0 CHECK (coins >= 0),
    combat_xp    REAL    NOT NULL DEFAULT 0 CHECK (combat_xp >= 0),
    speed        REAL    NOT NULL DEFAULT 1 CHECK (speed >= 0),
    scale        REAL    NOT NULL DEFAULT 1 CHECK (scale >= 0),
    armor        REAL    NOT NULL DEFAULT 0 CHECK (armor >= 0),
    unlock_level INTEGER NOT NULL DEFAULT 0 CHECK (unlock_level >= 0),   -- slayer level needed to start it
    PRIMARY KEY (slayer_id, tier)
);

CREATE TABLE slayer_equipment (
    slayer_id TEXT NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    slot      TEXT NOT NULL CHECK (slot IN ('head', 'chest', 'legs', 'feet', 'mainhand', 'offhand')),
    material  TEXT NOT NULL,
    color     INTEGER,
    PRIMARY KEY (slayer_id, slot)
);

-- Abilities of every tier from from_tier on.
CREATE TABLE slayer_ability (
    slayer_id TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    type      TEXT    NOT NULL,
    cooldown  REAL    NOT NULL DEFAULT 12,
    telegraph REAL    NOT NULL DEFAULT 0,
    damage    REAL    NOT NULL DEFAULT 1,
    target    TEXT    NOT NULL DEFAULT 'nearest' CHECK (target IN ('nearest', 'random', 'top', 'far', 'all')),
    from_tier INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (slayer_id, position)
);

CREATE TABLE slayer_ability_param (
    slayer_id TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    ability   INTEGER NOT NULL,
    name      TEXT    NOT NULL,
    num       REAL,
    text      TEXT,
    PRIMARY KEY (slayer_id, ability, name),
    CHECK ((num IS NULL) <> (text IS NULL))
);

CREATE TABLE slayer_rank_bonus (
    slayer_id  TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    rank       INTEGER NOT NULL CHECK (rank >= 1),
    multiplier REAL    NOT NULL CHECK (multiplier >= 0),
    PRIMARY KEY (slayer_id, rank)
);

-- Slayer drops, same columns as boss_drop; from_tier is the first tier that drops it.
CREATE TABLE slayer_drop (
    slayer_id  TEXT    NOT NULL REFERENCES slayer (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    item       TEXT    NOT NULL,
    chance     REAL    NOT NULL CHECK (chance BETWEEN 0 AND 1),
    min_amount INTEGER NOT NULL DEFAULT 1 CHECK (min_amount >= 1),
    max_amount INTEGER NOT NULL DEFAULT 1 CHECK (max_amount >= min_amount),
    min_share  REAL    NOT NULL DEFAULT 0,
    max_rank   INTEGER NOT NULL DEFAULT 0,
    magic_find INTEGER NOT NULL DEFAULT 1 CHECK (magic_find IN (0, 1)),
    pity       REAL    NOT NULL DEFAULT 0 CHECK (pity >= 0),
    pity_cap   REAL    NOT NULL DEFAULT 0.015 CHECK (pity_cap >= 0),
    from_tier  INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (slayer_id, position)
);

-- Crafting recipe of a summon item (ships disabled).
CREATE TABLE boss_recipe (
    item    TEXT PRIMARY KEY,                           -- custom item id it crafts
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    origin  TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE boss_recipe_row (
    recipe   TEXT    NOT NULL REFERENCES boss_recipe (item) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    pattern  TEXT    NOT NULL,                          -- 1-3 characters, space = empty
    PRIMARY KEY (recipe, position)
);

CREATE TABLE boss_recipe_ingredient (
    recipe   TEXT NOT NULL REFERENCES boss_recipe (item) ON DELETE CASCADE,
    symbol   TEXT NOT NULL CHECK (length(symbol) = 1),
    material TEXT NOT NULL,
    PRIMARY KEY (recipe, symbol)
);

-- Extra drops of vanilla mobs (a summon item as a drop). None ship: the items are forged.
CREATE TABLE boss_mob_drop (
    id            TEXT PRIMARY KEY,
    item          TEXT NOT NULL,
    chance        REAL NOT NULL CHECK (chance > 0 AND chance <= 1),
    magic_find    INTEGER NOT NULL DEFAULT 1 CHECK (magic_find IN (0, 1)),
    allow_spawner INTEGER NOT NULL DEFAULT 0 CHECK (allow_spawner IN (0, 1)),
    origin        TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE boss_mob_drop_entity (
    drop_id TEXT NOT NULL REFERENCES boss_mob_drop (id) ON DELETE CASCADE,
    entity  TEXT NOT NULL,
    PRIMARY KEY (drop_id, entity)
);

CREATE TABLE boss_mob_drop_world (
    drop_id TEXT NOT NULL REFERENCES boss_mob_drop (id) ON DELETE CASCADE,
    world   TEXT NOT NULL,
    PRIMARY KEY (drop_id, world)
);

-- Display override of a boss item (name and lore, MiniMessage). No row = the built-in text.
CREATE TABLE boss_item_text (
    item   TEXT PRIMARY KEY,
    name   TEXT,
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE boss_item_text_lore (
    item     TEXT    NOT NULL REFERENCES boss_item_text (item) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    line     TEXT    NOT NULL,
    PRIMARY KEY (item, position)
);

-- Where world boss events happen. y = -999 means x and z are offsets from the world spawn.
CREATE TABLE boss_world_spot (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position INTEGER NOT NULL,
    world    TEXT NOT NULL,
    x        REAL NOT NULL,
    y        REAL NOT NULL,
    z        REAL NOT NULL,
    name     TEXT NOT NULL,
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Bosses a world boss event can pick (weighted).
CREATE TABLE boss_world_pool (
    boss_id TEXT PRIMARY KEY,
    weight  REAL NOT NULL DEFAULT 1 CHECK (weight > 0),
    origin  TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
