-- The Catacombs dungeon: floors, grades (score thresholds and payouts), the reward item table and the Crypt Forge trades.
-- Its scalar rules (run limits, party, generation, entrance pad, ghosts, mobs, shop, scoring, Catacombs level) are
-- 'setting' rows 'dungeon.*'.

-- A grade: the minimum score to get it and what a clear of that grade pays.
CREATE TABLE dungeon_grade (
    id        TEXT PRIMARY KEY CHECK (id IN ('D', 'C', 'B', 'A', 'S', 'S+')),
    position  INTEGER NOT NULL,                 -- 0 = best grade
    min_score INTEGER NOT NULL CHECK (min_score >= 0),   -- D is always 0; each grade needs more than the next lower one
    coins     REAL    NOT NULL CHECK (coins >= 0),       -- multiplied by the floor reward multiplier
    xp        REAL    NOT NULL CHECK (xp >= 0),          -- combat xp, same multiplier
    rolls     INTEGER NOT NULL CHECK (rolls >= 0),       -- item rolls of the reward table
    chest     TEXT    NOT NULL,                          -- name of the reward chest
    origin    TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- One line of the item reward table: picked by weight among the lines whose min_floor and min_grade are reached.
-- item = a custom item id or 'mc:MATERIAL'.
CREATE TABLE dungeon_reward (
    id         TEXT PRIMARY KEY,
    position   INTEGER NOT NULL,
    item       TEXT    NOT NULL,
    weight     INTEGER NOT NULL CHECK (weight >= 1),
    min_amount INTEGER NOT NULL DEFAULT 1 CHECK (min_amount >= 1),
    max_amount INTEGER NOT NULL DEFAULT 1 CHECK (max_amount >= min_amount),
    min_floor  INTEGER NOT NULL DEFAULT 1 CHECK (min_floor >= 1),
    min_grade  TEXT    NOT NULL DEFAULT 'D' CHECK (min_grade IN ('D', 'C', 'B', 'A', 'S', 'S+')),
    origin     TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A Crypt Forge trade: spends its cost and gives exactly ONE outcome: an item (give + amount), cache rolls of a grade,
-- or coins.
CREATE TABLE dungeon_trade (
    id          TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    position    INTEGER NOT NULL,
    name        TEXT    NOT NULL,
    icon        TEXT    NOT NULL DEFAULT 'CHEST',
    give        TEXT    NOT NULL DEFAULT '',            -- custom item id, '' = no item outcome
    amount      INTEGER NOT NULL DEFAULT 0 CHECK (amount >= 0 AND amount <= 2304),
    cache_rolls INTEGER NOT NULL DEFAULT 0 CHECK (cache_rolls >= 0 AND cache_rolls <= 20),
    cache_grade TEXT    NOT NULL DEFAULT 'D' CHECK (cache_grade IN ('D', 'C', 'B', 'A', 'S', 'S+')),
    coins       REAL    NOT NULL DEFAULT 0 CHECK (coins >= 0),
    origin      TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    CHECK ((give <> '') + (cache_rolls > 0) + (coins > 0) = 1)
);

-- What a trade costs: item id (custom id or 'mc:MATERIAL') and amount.
CREATE TABLE dungeon_trade_cost (
    trade_id TEXT    NOT NULL REFERENCES dungeon_trade (id) ON DELETE CASCADE,
    item     TEXT    NOT NULL,
    amount   INTEGER NOT NULL CHECK (amount >= 1 AND amount <= 100000),
    PRIMARY KEY (trade_id, item)
);

-- A floor. number is 1, 2, 3... without gaps; each floor has its own palette, size and difficulty.
CREATE TABLE dungeon_floor (
    id                      TEXT PRIMARY KEY,           -- the number as text
    number                  INTEGER NOT NULL UNIQUE CHECK (number BETWEEN 1 AND 20),
    name                    TEXT    NOT NULL,
    palette                 TEXT    NOT NULL CHECK (palette IN ('stone_brick', 'mossy', 'deepslate', 'blackstone', 'nether', 'end', 'crimson')),
    grid                    INTEGER NOT NULL CHECK (grid BETWEEN 3 AND 7),     -- cells per side
    rooms                   INTEGER NOT NULL CHECK (rooms BETWEEN 4 AND 49),   -- rooms actually built
    puzzles                 INTEGER NOT NULL CHECK (puzzles BETWEEN 0 AND 12),
    traps                   INTEGER NOT NULL CHECK (traps BETWEEN 0 AND 12),
    secret_rooms            INTEGER NOT NULL CHECK (secret_rooms BETWEEN 0 AND 12),
    mini_bosses             INTEGER NOT NULL CHECK (mini_bosses BETWEEN 0 AND 8),
    shop                    INTEGER NOT NULL CHECK (shop IN (0, 1)),
    fairy                   INTEGER NOT NULL CHECK (fairy IN (0, 1)),
    wither_doors            INTEGER NOT NULL CHECK (wither_doors BETWEEN 0 AND 8),   -- doors that need a Wither Key
    loops                   INTEGER NOT NULL CHECK (loops BETWEEN 0 AND 12),
    hp                      REAL    NOT NULL CHECK (hp >= 0.1),        -- mob health multiplier
    damage                  REAL    NOT NULL CHECK (damage >= 0.1),    -- mob damage multiplier
    mob_level               INTEGER NOT NULL CHECK (mob_level >= 1),   -- level shown on the mobs
    par_seconds             INTEGER NOT NULL CHECK (par_seconds >= 60),   -- time for a full speed score
    secrets_needed_percent  INTEGER NOT NULL CHECK (secrets_needed_percent BETWEEN 0 AND 100),
    reward                  REAL    NOT NULL CHECK (reward >= 0),      -- multiplier of coins and xp
    min_combat_level        INTEGER NOT NULL DEFAULT 0 CHECK (min_combat_level >= 0),   -- 0 = no check
    origin                  TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
