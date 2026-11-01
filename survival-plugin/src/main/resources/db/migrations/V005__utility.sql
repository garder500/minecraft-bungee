-- The utility feature (homes, teleports, tpa, rtp...). Its single values are 'setting' rows 'utility.<path>'.

-- Portable blocks (/ec, /workbench...): public = everybody has the permission, otherwise operators only.
CREATE TABLE utility_portable (
    name   TEXT PRIMARY KEY CHECK (name IN ('ec', 'workbench', 'anvil', 'grindstone', 'stonecutter', 'loom')),
    public INTEGER NOT NULL CHECK (public IN (0, 1)),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);
