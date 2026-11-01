-- The menu feature (join routing, sidebar, warps, fairy souls, quest tuning). Its single values are 'setting' rows
-- 'menu.<path>'. The quest plan and the daily pool are in V002__quests.sql.

-- A guide command and its aliases (the profile head, the menu buttons).
CREATE TABLE menu_command (
    name     TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE menu_command_alias (
    command  TEXT    NOT NULL REFERENCES menu_command (name) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    alias    TEXT    NOT NULL,                          -- a leading '/' is ignored
    PRIMARY KEY (command, position)
);

-- Where the Warps menu sends a player in the Nether or the End. No row = the world's spawn.
CREATE TABLE menu_warp (
    world  TEXT PRIMARY KEY CHECK (world IN ('nether', 'end')),
    x      REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
    yaw    REAL NOT NULL DEFAULT 0,
    pitch  REAL NOT NULL DEFAULT 0,
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Name shown in the sidebar for a world outside every zone (world token: hub, world, nether, end).
CREATE TABLE menu_default_zone (
    world    TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    name     TEXT NOT NULL,
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A named region shown in the sidebar; the first match in position order wins.
CREATE TABLE menu_zone (
    id       TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    name     TEXT NOT NULL,
    world    TEXT NOT NULL,                             -- a world token (hub, world, nether, end) or a world name
    min_x    REAL NOT NULL, min_y REAL NOT NULL, min_z REAL NOT NULL,
    max_x    REAL NOT NULL, max_y REAL NOT NULL, max_z REAL NOT NULL,
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A sound by event name (welcome, quest-complete, quest-progress, soul-found, milestone, teleport, click).
CREATE TABLE menu_sound (
    event    TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    sound    TEXT NOT NULL,                             -- a Minecraft sound key
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- The stat granted at each fairy soul milestone, in turn (the cycle starts over after the last).
CREATE TABLE menu_fairy_stat (
    id       TEXT PRIMARY KEY,
    position INTEGER NOT NULL,
    stat     TEXT NOT NULL,
    amount   REAL NOT NULL,
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- A fairy soul: 'fixed' = absolute position, 'island' = relative to the island's centre.
CREATE TABLE menu_fairy_soul (
    id       TEXT PRIMARY KEY,
    kind     TEXT NOT NULL CHECK (kind IN ('fixed', 'island')),
    position INTEGER NOT NULL,
    world    TEXT NOT NULL,                             -- a world token (hub, world, nether, end)
    x        REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
    origin   TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
