-- Realm resources (GDD 3.2): one local resource per Realm, found only there (Cinderheart, Heartwood...).

-- The Realm each world belongs to (a world name can be added when a Realm zone is built).
CREATE TABLE realm_world (
    world  TEXT PRIMARY KEY,
    realm  TEXT NOT NULL,                           -- api/Realm id: ashes, grove...
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A local resource: the item, the Realm it comes from and its pity curve (GDD 15.1: p0, soft, dur counted in drops attempted).
CREATE TABLE realm_resource (
    id       TEXT PRIMARY KEY CHECK (id GLOB '[a-z]*'),
    realm    TEXT NOT NULL,
    item     TEXT NOT NULL,                         -- custom item id
    p0       REAL NOT NULL CHECK (p0 > 0 AND p0 <= 1),
    soft     INTEGER NOT NULL CHECK (soft >= 0),
    dur      INTEGER NOT NULL CHECK (dur > soft),
    amount_min INTEGER NOT NULL DEFAULT 1 CHECK (amount_min >= 1),
    amount_max INTEGER NOT NULL DEFAULT 1 CHECK (amount_max >= amount_min),
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- What gives the resource, inside its Realm: a block that is broken or a mob that dies. Each kind has its own pity counter.
CREATE TABLE realm_resource_source (
    resource_id TEXT NOT NULL REFERENCES realm_resource (id) ON DELETE CASCADE,
    kind        TEXT NOT NULL CHECK (kind IN ('block', 'mob')),
    key         TEXT NOT NULL,                      -- a Material name or an EntityType name
    PRIMARY KEY (resource_id, kind, key)
);
