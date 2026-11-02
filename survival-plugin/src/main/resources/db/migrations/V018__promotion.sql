-- Class promotions (GDD 11.5): Novice -> Adept -> Master. A promotion is a chain of 3 to 5 quest steps picked by the Master
-- NPC of the class from the player's profile (promotion_chain, one per rank and profile); the Adept ends on the player's
-- choice between the two specializations of the class (promotion_spec); the Master gets a third capacity (promotion_ability).
-- Scalars (the unlock conditions) are 'promotion.*' settings.

CREATE TABLE promotion_spec (
    id           TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    class        TEXT NOT NULL CHECK (class IN ('VANGUARD', 'DUELIST', 'CHANNELER', 'RANGER')),
    position     INTEGER NOT NULL,
    name         TEXT NOT NULL,
    summary      TEXT NOT NULL,
    vitality     REAL NOT NULL DEFAULT 0,       -- added to the class bonus, all of them stay inside the meta caps (GDD 8)
    might        REAL NOT NULL DEFAULT 0,
    dexterity    REAL NOT NULL DEFAULT 0,
    attunement   REAL NOT NULL DEFAULT 0,
    cooldown_pct INTEGER NOT NULL DEFAULT 0 CHECK (cooldown_pct BETWEEN -80 AND 200),   -- on the active
    cost_pct     INTEGER NOT NULL DEFAULT 0 CHECK (cost_pct BETWEEN -80 AND 200),
    power_pct    INTEGER NOT NULL DEFAULT 0 CHECK (power_pct BETWEEN -80 AND 300),      -- duration / dash / blast radius / arrow damage
    origin       TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE promotion_ability (
    class            TEXT PRIMARY KEY CHECK (class IN ('VANGUARD', 'DUELIST', 'CHANNELER', 'RANGER')),
    name             TEXT NOT NULL,
    summary          TEXT NOT NULL,
    aether_cost      REAL NOT NULL CHECK (aether_cost >= 0),
    cooldown_seconds INTEGER NOT NULL CHECK (cooldown_seconds >= 1),
    radius           REAL NOT NULL DEFAULT 0,   -- Vanguard / Channeler: blocks. Ranger: degrees between two arrows
    power            REAL NOT NULL DEFAULT 0,   -- Duelist: hits that are Surges. Channeler: percent of the weapon damage. Ranger: arrows
    seconds          REAL NOT NULL DEFAULT 0,   -- Vanguard: duration of the effects. Duelist: how long the hits are kept
    origin           TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE promotion_chain (
    id      TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    rank    TEXT NOT NULL CHECK (rank IN ('ADEPT', 'MASTER')),     -- the rank it leads to
    profile TEXT NOT NULL CHECK (profile IN ('COMBAT', 'CRAFT', 'EXPLORE', 'COOP', 'BOSS')),
    title   TEXT NOT NULL,
    intro   TEXT NOT NULL,                                          -- what the Master says when offering it
    origin  TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    UNIQUE (rank, profile)
);

CREATE TABLE promotion_step (
    chain_id TEXT    NOT NULL REFERENCES promotion_chain (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    kind     TEXT    NOT NULL CHECK (kind IN ('KILL', 'CRAFT', 'CAST', 'COOP', 'REALM', 'BOSS_SOLO', 'BOSS_GROUP', 'BOSS_ANY', 'BASTION')),
    target   TEXT    NOT NULL DEFAULT 'any',                        -- 'any', a boss id (BOSS_*) or a Realm id (REALM)
    amount   INTEGER NOT NULL CHECK (amount >= 1),
    text     TEXT    NOT NULL,
    PRIMARY KEY (chain_id, position)
);
