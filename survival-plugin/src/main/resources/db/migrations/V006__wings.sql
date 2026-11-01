-- The Wings. Their single values are 'setting' rows 'wings.<path>' (energy, combat, abilities...).

-- The per level table (a short table repeats its last row up to wings.max-level).
CREATE TABLE wings_level (
    level            INTEGER PRIMARY KEY CHECK (level >= 1),
    capacity_bonus   REAL    NOT NULL,                  -- added to the Flight Energy stat while worn
    regen_multiplier REAL    NOT NULL,                  -- multiplies energy regeneration
    drain_multiplier REAL    NOT NULL,                  -- multiplies energy drain (lower is better)
    fly_speed        REAL    NOT NULL,                  -- Bukkit fly speed (0.1 is vanilla), clamped to 0.02..1.0
    trail_particle   TEXT    NOT NULL,                  -- a Bukkit particle without extra data
    trail_count      INTEGER NOT NULL DEFAULT 1 CHECK (trail_count BETWEEN 0 AND 20),
    trail_spread     REAL CHECK (trail_spread IS NULL OR trail_spread BETWEEN 0 AND 2),  -- NULL = 0.15
    origin           TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Extra stats granted while the Wings are worn (stat source "wings"), one value per level and Stat name.
CREATE TABLE wings_level_stat (
    level INTEGER NOT NULL REFERENCES wings_level (level) ON DELETE CASCADE,
    stat  TEXT    NOT NULL,
    value REAL    NOT NULL,
    PRIMARY KEY (level, stat)
);

-- Cost of going from level N to N+1: core tier, number of cores, coins (0 = free).
CREATE TABLE wings_upgrade (
    level  INTEGER PRIMARY KEY CHECK (level >= 1),
    core   INTEGER NOT NULL CHECK (core BETWEEN 1 AND 5),
    amount INTEGER NOT NULL CHECK (amount >= 1),
    coins  REAL    NOT NULL DEFAULT 0 CHECK (coins >= 0),
    origin TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Display name of the Wing Core of each tier (items wings_core_1 .. wings_core_5).
CREATE TABLE wings_core (
    tier   INTEGER PRIMARY KEY CHECK (tier >= 1),
    name   TEXT NOT NULL,
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A static no-fly box.
CREATE TABLE wings_region (
    id     TEXT PRIMARY KEY,
    world  TEXT NOT NULL,
    min_x  INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
    max_x  INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL,
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
