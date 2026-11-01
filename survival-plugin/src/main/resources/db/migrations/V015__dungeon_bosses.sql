-- A boss of its own for every dungeon floor: who it is, the attacks it warns about, what its phases ask of the party.

CREATE TABLE dungeon_boss (
    id         TEXT PRIMARY KEY,                       -- the floor number as text, like dungeon_floor
    floor      INTEGER NOT NULL UNIQUE CHECK (floor BETWEEN 1 AND 7),
    name       TEXT NOT NULL,
    entity     TEXT NOT NULL,                          -- a Bukkit EntityType
    weapon     TEXT NOT NULL,                          -- a Bukkit Material
    color      TEXT NOT NULL,                          -- MiniMessage colour of the name
    tagline    TEXT NOT NULL,                          -- shown under the name when the fight starts
    phase_kind TEXT NOT NULL CHECK (phase_kind IN ('ADDS', 'ANCHORS')),
    phase_goal TEXT NOT NULL,                          -- what the boss bar asks of the party while it is shielded
    origin     TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

-- Its attacks, in the order it uses them. warning is shown as it starts; hint teaches it when the fight starts.
CREATE TABLE dungeon_boss_attack (
    boss_id  TEXT    NOT NULL REFERENCES dungeon_boss(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    kind     TEXT    NOT NULL CHECK (kind IN ('SLAM', 'NOVA', 'MARKS', 'CHARGE')),
    warning  TEXT    NOT NULL,
    hint     TEXT    NOT NULL,
    PRIMARY KEY (boss_id, position)
);
