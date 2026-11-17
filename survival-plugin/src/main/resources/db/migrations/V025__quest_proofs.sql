-- A quest can prove others: completing it marks them done too (a built Stone generator proves its ring, a placed
-- Fountain proves the crafted one). Filled by the quests seed (scripts/gen_quests.py, proves=).
CREATE TABLE quest_proof (
    quest_id TEXT    NOT NULL REFERENCES quest (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    proves   TEXT    NOT NULL,
    PRIMARY KEY (quest_id, proves)
);
