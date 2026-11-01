-- The economy: NPC merchants and their offers, bank tiers, Bazaar products, sell-pressure pools and the trade bans.
-- The scalar options (limits, taxes, cooldowns...) are 'economy.*' rows of the shared setting table.
-- Item keys: UPPER CASE = vanilla material, lower case = custom item id registered by another feature.

-- An NPC merchant (/sbnpc create <id>). The offers are in merchant_offer.
CREATE TABLE merchant (
    id          TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position    INTEGER NOT NULL,                       -- order in the menus
    name        TEXT NOT NULL,
    npc_name    TEXT NOT NULL,                          -- MiniMessage
    icon        TEXT NOT NULL DEFAULT 'CHEST',          -- vanilla material
    profession  TEXT NOT NULL DEFAULT 'NONE',           -- villager profession
    npc_type    TEXT NOT NULL DEFAULT 'VILLAGER',       -- entity type of the NPC
    description TEXT NOT NULL DEFAULT '',
    origin      TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- One line of a merchant: buy = what the player pays, sell = what the merchant pays (coins, 0 = not for sale / not bought).
-- An offer is its own root so that editing one price does not freeze the whole merchant against plugin updates.
-- merchant is a plain column (like quest.category): an offer of an unknown merchant is skipped and logged.
CREATE TABLE merchant_offer (
    id       TEXT PRIMARY KEY,                          -- '<merchant>:<item_key>'
    merchant TEXT NOT NULL,
    position INTEGER NOT NULL,                          -- order inside the merchant
    item_key TEXT NOT NULL,
    buy      REAL NOT NULL DEFAULT 0 CHECK (buy >= 0),
    sell     REAL NOT NULL DEFAULT 0 CHECK (sell >= 0),
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    UNIQUE (merchant, item_key),
    CHECK (buy > 0 OR sell > 0)
);

-- A bank tier. A player's saved tier is an index into the tiers ordered by position: never renumber them.
CREATE TABLE bank_tier (
    id           TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*'),
    position     INTEGER NOT NULL,
    name         TEXT NOT NULL,
    capacity     REAL NOT NULL CHECK (capacity > 0),    -- most coins the bank can hold
    rate         REAL NOT NULL CHECK (rate BETWEEN 0 AND 100),  -- percent per interest period
    max_interest REAL NOT NULL CHECK (max_interest >= 0),       -- cap per period
    cost         REAL NOT NULL CHECK (cost >= 0),       -- price of reaching this tier, from the purse
    origin       TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A product of the Bazaar. A key listed in two categories keeps the first one (category order: farming, mining, woods,
-- combat, fishing, misc).
CREATE TABLE bazaar_product (
    id       TEXT PRIMARY KEY,                          -- '<category>:<item_key>'
    category TEXT NOT NULL CHECK (category IN ('farming', 'mining', 'woods', 'combat', 'fishing', 'misc')),
    position INTEGER NOT NULL,                          -- order inside the category
    item_key TEXT NOT NULL,
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    UNIQUE (category, item_key)
);

-- Compacted items pool their sell pressure with the raw item: key prefix -> how many raw items one compacted item counts for.
CREATE TABLE price_pressure_prefix (
    prefix TEXT PRIMARY KEY,
    ratio  REAL NOT NULL CHECK (ratio >= 1),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Never bought or sold by a merchant or in the Bazaar (the built-in list of ultra rare items always applies on top).
-- A trailing * matches a prefix.
CREATE TABLE market_never_traded (
    pattern TEXT PRIMARY KEY CHECK (pattern <> ''),
    origin  TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Items that can never be listed in the Auction House (the Wings are always refused).
CREATE TABLE auction_blocked_item (
    id     TEXT PRIMARY KEY,                            -- '<kind>:<value>'
    kind   TEXT NOT NULL CHECK (kind IN ('material', 'item')),   -- vanilla material or custom item id
    value  TEXT NOT NULL CHECK (value <> ''),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    UNIQUE (kind, value)
);
