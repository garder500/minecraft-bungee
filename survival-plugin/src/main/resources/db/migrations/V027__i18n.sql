-- Translations of player-facing content text (item names and descriptions, quests, quest categories). English is the
-- text of the content tables themselves; a row here is its translation for one language (fr, de...).
-- key: item.<id>.name (also the enchanted items), item.<id>.lore.<position>, quest.<id>.name, quest.<id>.desc,
-- category.<id>.name, category.<id>.desc. source = the English text that was translated: when the English row is edited
-- (panel), the stale translation is ignored and the player reads the new English until the row is translated again.
-- Filled by the i18n seed (scripts/i18n_seed.py from survival-plugin/i18n/<locale>/*.json).
CREATE TABLE i18n_text (
    id     TEXT PRIMARY KEY,                    -- <locale>:<key>
    locale TEXT NOT NULL CHECK (locale GLOB '[a-z][a-z]'),
    key    TEXT NOT NULL,
    source TEXT NOT NULL,
    text   TEXT NOT NULL CHECK (text <> ''),
    origin TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel')),
    UNIQUE (locale, key)
);
