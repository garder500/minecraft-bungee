-- Living NPCs (GDD 18, issue #48): what an NPC remembers about a player (facts, relation), its local state, and the
-- deterministic reactions that read them. Only npc_reaction is content (seeded, editable in the panel); the other three
-- are runtime data and are never seeded. The rules engine decides everything, nothing here depends on an AI.

CREATE TABLE npc_reaction (
    id        TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    npc       TEXT NOT NULL DEFAULT '*',                       -- an NPC id (shop id, 'master_vanguard'...) or '*' for every NPC
    kind      TEXT NOT NULL CHECK (kind IN ('GREETING', 'PRICE')),
    fact      TEXT NOT NULL DEFAULT '',                        -- fact key the player must have ('' = none), e.g. 'defeated.magma_warden'
    min_value INTEGER NOT NULL DEFAULT 1,                      -- ...with at least this value
    min_rep   INTEGER NOT NULL DEFAULT -1000,                  -- relation window, both ends included
    max_rep   INTEGER NOT NULL DEFAULT 1000,
    priority  INTEGER NOT NULL DEFAULT 0,                      -- GREETING: the highest priority wins (ties: smallest id)
    price_pct INTEGER NOT NULL DEFAULT 0 CHECK (price_pct BETWEEN -50 AND 50),   -- PRICE: summed, then clamped by settings
    line      TEXT NOT NULL DEFAULT '',                        -- GREETING text, {player} is replaced
    origin    TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE npc_fact (
    player TEXT    NOT NULL,
    key    TEXT    NOT NULL,
    value  INTEGER NOT NULL DEFAULT 1,
    at     INTEGER NOT NULL,
    PRIMARY KEY (player, key)
);

CREATE TABLE npc_relation (
    player    TEXT    NOT NULL,
    npc       TEXT    NOT NULL,
    rep       INTEGER NOT NULL DEFAULT 0,
    visits    INTEGER NOT NULL DEFAULT 0,
    last_seen INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player, npc)
);

CREATE TABLE npc_state (
    npc   TEXT NOT NULL,
    key   TEXT NOT NULL,
    value TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (npc, key)
);
