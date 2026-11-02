-- Runes (GDD 10) and the runes of the first two Realms (GDD 3.2). Edit in the panel; the list is read at start, the numbers on a data reload.
-- Rows: id|name|spec|target|component. spec = stat:<STAT> or realm. component = custom item spent for rank II (stat rune) or the Realm resource (Realm rune).
INSERT INTO setting (id, type, value, note) VALUES ('runes.list', 'list', '', 'Runes as id|name|spec|target|component (at most 6: the Runecraft menu has 6 rows). spec is stat:<STAT> or realm; target is armor, weapon or any.');
INSERT INTO setting_item (setting_id, position, value) VALUES ('runes.list', 0, 'vitality|Vitality|stat:vitality|armor|bronzite');
INSERT INTO setting_item (setting_id, position, value) VALUES ('runes.list', 1, 'ward|Ward|stat:ward|armor|frostium');
INSERT INTO setting_item (setting_id, position, value) VALUES ('runes.list', 2, 'might|Might|stat:might|weapon|sunstone');
INSERT INTO setting_item (setting_id, position, value) VALUES ('runes.list', 3, 'aether|Aether|stat:aether|any|voidglass');
INSERT INTO setting_item (setting_id, position, value) VALUES ('runes.list', 4, 'scorch|Scorch|realm|weapon|cinderheart');
INSERT INTO setting_item (setting_id, position, value) VALUES ('runes.list', 5, 'verdant|Verdant|realm|armor|heartwood');
INSERT INTO setting (id, type, value, note) VALUES ('runes.value.rank1', 'int', '2', 'Stat points of a rank I rune (GDD 10).');
INSERT INTO setting (id, type, value, note) VALUES ('runes.value.rank2', 'int', '4', 'Stat points of a rank II rune (GDD 10).');
INSERT INTO setting (id, type, value, note) VALUES ('runes.value.rank3', 'int', '6', 'Stat points of a rank III rune (GDD 10). A Realm rune is worth the same.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.slots.tier1', 'int', '1', 'Rune slots of a tier 1 item (GDD 10).');
INSERT INTO setting (id, type, value, note) VALUES ('runes.slots.tier2', 'int', '2', 'Rune slots of a tier 2 item.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.slots.tier3', 'int', '3', 'Rune slots of a tier 3 item.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.slots.tier4', 'int', '4', 'Rune slots of a tier 4 item.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.slots.tier5', 'int', '4', 'Rune slots of a tier 5 item.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.cost.powder', 'int', '8', 'Rune Powder for a rank I rune (GDD 10).');
INSERT INTO setting (id, type, value, note) VALUES ('runes.cost.fuse', 'int', '3', 'Runes of the rank below spent for rank II and III (GDD 10).');
INSERT INTO setting (id, type, value, note) VALUES ('runes.cost.grind-output', 'int', '4', 'Rune Powder from one Amethyst Shard at the Runecraft table. A guess: the GDD does not say where the powder comes from.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.realm.powder', 'int', '8', 'Rune Powder for a Realm rune. A guess, to measure in play.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.realm.resource', 'int', '4', 'Realm resource (Cinderheart, Heartwood) for a Realm rune. A guess, to measure against the pity curve.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.realm.diamonds', 'int', '1', 'Diamonds for a Realm rune (like a rank III).');
INSERT INTO setting (id, type, value, note) VALUES ('runes.scorch.burn-ticks', 'int', '80', 'Scorch: ticks the target burns after a Surge (20 = 1 second).');
INSERT INTO setting (id, type, value, note) VALUES ('runes.verdant.regen-ticks', 'int', '100', 'Verdant: ticks of Regeneration I after taking a hit.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.verdant.cooldown-seconds', 'int', '12', 'Verdant: seconds before it can trigger again.');
INSERT INTO setting (id, type, value, note) VALUES ('runes.station-block', 'text', 'GRINDSTONE', 'Vanilla block that opens the Runecraft table in Skyblock worlds (craft and place it). /runecraft opens it from anywhere.');
