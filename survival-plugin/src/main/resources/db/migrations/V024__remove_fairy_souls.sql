-- Fairy souls were a borrowed mechanic and are gone (the hub is the last anchored fragment of Aethelgard, not a collectathon).
-- Drops the soul tables and their settings, sounds and the one daily quest that asked for a soul. Nothing else is touched:
-- players' profiles, quests and stats stay as they are (an old "fairy_souls" profile section is simply never read again).
DROP TABLE IF EXISTS menu_fairy_soul;
DROP TABLE IF EXISTS menu_fairy_stat;
DELETE FROM setting WHERE id GLOB 'menu.fairy-souls.*';
DELETE FROM setting_item WHERE setting_id GLOB 'menu.fairy-souls.*';
DELETE FROM menu_sound WHERE event IN ('soul-found', 'milestone');
DELETE FROM setting_item WHERE setting_id = 'menu.quests.daily.ignore-coin-reasons' AND value = 'fairy';
DELETE FROM quest_reward_xp WHERE quest_id IN (SELECT id FROM quest WHERE type = 'COLLECT_SOUL');
DELETE FROM quest WHERE type = 'COLLECT_SOUL';
