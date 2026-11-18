-- Translated names and descriptions of quests and quest categories (fr, de). English stays in quest / quest_category:
-- a quest without a row for a language reads English. Filled by the quests_i18n seed (scripts/gen_quest_i18n.py).
-- No foreign key on purpose: re-seeding the quests must not wipe the translations (they have their own seed).
CREATE TABLE quest_text (
    id          TEXT PRIMARY KEY,                  -- '<quest id>:<lang>'
    quest_id    TEXT NOT NULL,
    lang        TEXT NOT NULL CHECK (lang IN ('fr', 'de')),
    name        TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    origin      TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE TABLE quest_category_text (
    id          TEXT PRIMARY KEY,                  -- '<category id>:<lang>'
    category_id TEXT NOT NULL,
    lang        TEXT NOT NULL CHECK (lang IN ('fr', 'de')),
    name        TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    origin      TEXT NOT NULL DEFAULT 'seed' CHECK (origin IN ('seed', 'panel'))
);

CREATE INDEX quest_text_by_lang ON quest_text (lang);
CREATE INDEX quest_category_text_by_lang ON quest_category_text (lang);
