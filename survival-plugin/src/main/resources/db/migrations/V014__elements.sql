-- Elements and power tiers. Earth (Bronzite), Frost (Frostium), Ember (Sunstone), Void (Voidglass), Astral (Starcore).
-- A weapon has an element; a boss, slayer, dungeon floor or natural mob can be weak to one (x1.5 + Attunement) and
-- resist another (x0.6). The tier ties health to the power curve (items/combat/PowerCurve).

ALTER TABLE item_def ADD COLUMN element TEXT CHECK (element IS NULL OR element IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));
ALTER TABLE item_def ADD COLUMN tier INTEGER CHECK (tier IS NULL OR tier BETWEEN 1 AND 5);

ALTER TABLE item_mob ADD COLUMN weakness TEXT CHECK (weakness IS NULL OR weakness IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));
ALTER TABLE item_mob ADD COLUMN resistance TEXT CHECK (resistance IS NULL OR resistance IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));

-- hp_auto = 1: the health is derived at load time from the tier (target seconds x the tier's expected damage per second);
-- the health column is then only a manual fallback. 0 keeps the written health.
ALTER TABLE boss ADD COLUMN hp_auto INTEGER NOT NULL DEFAULT 0 CHECK (hp_auto IN (0, 1));
ALTER TABLE boss ADD COLUMN weakness TEXT CHECK (weakness IS NULL OR weakness IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));
ALTER TABLE boss ADD COLUMN resistance TEXT CHECK (resistance IS NULL OR resistance IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));

-- A slayer is weak to / resists one element for all of its tiers; the tier n boss uses power tier n.
ALTER TABLE slayer ADD COLUMN hp_auto INTEGER NOT NULL DEFAULT 0 CHECK (hp_auto IN (0, 1));
ALTER TABLE slayer ADD COLUMN weakness TEXT CHECK (weakness IS NULL OR weakness IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));
ALTER TABLE slayer ADD COLUMN resistance TEXT CHECK (resistance IS NULL OR resistance IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));

-- A floor's monsters: power tier (their health follows the curve) and the element they are weak to / resist.
ALTER TABLE dungeon_floor ADD COLUMN tier INTEGER NOT NULL DEFAULT 1 CHECK (tier BETWEEN 1 AND 5);
ALTER TABLE dungeon_floor ADD COLUMN weakness TEXT CHECK (weakness IS NULL OR weakness IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));
ALTER TABLE dungeon_floor ADD COLUMN resistance TEXT CHECK (resistance IS NULL OR resistance IN ('EARTH', 'FROST', 'EMBER', 'VOID', 'ASTRAL'));
