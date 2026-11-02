-- Frozen Heights (GDD 3.1, issue #41): the two tables below had a CHECK that listed the values known when they were
-- written. SQLite cannot alter a CHECK, so both are rebuilt with an open one (a lower-case word): the Realms add worlds
-- ('grove', 'frost'...) and Bastion palettes ('frost'...), and the code ignores an unknown palette's blocks by falling back
-- to the Cinder floor.

CREATE TABLE island_world_tmp AS SELECT * FROM island_world;
CREATE TABLE island_world_gamerule_tmp AS SELECT * FROM island_world_gamerule;
DROP TABLE island_world_gamerule;
DROP TABLE island_world;

CREATE TABLE island_world (
    key           TEXT PRIMARY KEY CHECK (key GLOB '[a-z]*' AND key NOT GLOB '*[^a-z_]*'),
    time          TEXT    NOT NULL DEFAULT 'CYCLE',   -- DAY, NOON, SUNSET, NIGHT, MIDNIGHT or CYCLE (vanilla day/night)
    border        REAL    NOT NULL DEFAULT 60000000 CHECK (border > 0),
    weather_clear INTEGER NOT NULL DEFAULT 0 CHECK (weather_clear IN (0, 1)),
    origin        TEXT    NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
CREATE TABLE island_world_gamerule (
    world    TEXT    NOT NULL REFERENCES island_world (key) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    rule     TEXT    NOT NULL,
    value    TEXT    NOT NULL,
    PRIMARY KEY (world, rule)
);
INSERT INTO island_world (key, time, border, weather_clear, origin) SELECT key, time, border, weather_clear, origin FROM island_world_tmp;
INSERT INTO island_world_gamerule (world, position, rule, value) SELECT world, position, rule, value FROM island_world_gamerule_tmp;
DROP TABLE island_world_tmp;
DROP TABLE island_world_gamerule_tmp;

CREATE TABLE bastion_tmp AS SELECT * FROM bastion;
DROP TABLE bastion;
CREATE TABLE bastion (
    id                 TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9_]*' AND id NOT GLOB '*[^a-z0-9_]*'),
    position           INTEGER NOT NULL,
    realm              TEXT    NOT NULL,
    name               TEXT    NOT NULL,
    tagline            TEXT    NOT NULL,
    guardian           TEXT    NOT NULL,
    key_item           TEXT    NOT NULL,
    palette            TEXT    NOT NULL CHECK (palette GLOB '[a-z]*' AND palette NOT GLOB '*[^a-z_]*'),
    radius             INTEGER NOT NULL CHECK (radius BETWEEN 12 AND 48),
    min_players        INTEGER NOT NULL DEFAULT 1 CHECK (min_players BETWEEN 1 AND 5),
    max_players        INTEGER NOT NULL DEFAULT 5 CHECK (max_players BETWEEN 1 AND 5),
    time_limit_seconds INTEGER NOT NULL CHECK (time_limit_seconds >= 120),
    deaths_allowed     INTEGER NOT NULL DEFAULT 3 CHECK (deaths_allowed BETWEEN 0 AND 10),
    origin             TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    CHECK (max_players >= min_players)
);
INSERT INTO bastion SELECT id, position, realm, name, tagline, guardian, key_item, palette, radius, min_players, max_players,
    time_limit_seconds, deaths_allowed, origin FROM bastion_tmp;
DROP TABLE bastion_tmp;
