-- The Artisan and Warden trades (GDD 11.3). The base effects come from the GDD; the experience thresholds, the ability
-- levels and cooldowns are guesses to measure in play (GDD 16.2). Edit in the panel.
INSERT INTO setting (id, type, value, note) VALUES ('trades.level-xp', 'list', '', 'Experience for levels 2 to 5 (level 1 is the moment the trade is taken). Artisan: 5 per Forge job collected, 1 per Miner second that ran. Warden: 3 per Mend, and the Ranch and Alembic when they exist.');
INSERT INTO setting_item (setting_id, position, value) VALUES ('trades.level-xp', 0, '100');
INSERT INTO setting_item (setting_id, position, value) VALUES ('trades.level-xp', 1, '500');
INSERT INTO setting_item (setting_id, position, value) VALUES ('trades.level-xp', 2, '1500');
INSERT INTO setting_item (setting_id, position, value) VALUES ('trades.level-xp', 3, '4000');
INSERT INTO setting (id, type, value, note) VALUES ('trades.second-trade-at-level', 'int', '5', 'Level of a trade (the Master rank) from which a second trade can be taken; 0 = never (open question 4 of the GDD, proposal kept).');
INSERT INTO setting (id, type, value, note) VALUES ('trades.artisan.forge-duration', 'real', '0.85', 'Multiplier of a Forge job duration (-15 %).');
INSERT INTO setting (id, type, value, note) VALUES ('trades.artisan.miner-energy', 'real', '0.9', 'Multiplier of the Flux a Miner draws (-10 %).');
INSERT INTO setting (id, type, value, note) VALUES ('trades.artisan.overclock-level', 'int', '1', 'Artisan level that unlocks Overclock.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.artisan.overclock-speed', 'real', '2.0', 'Cycle speed of an overclocked Miner (it draws that much more Flux per second).');
INSERT INTO setting (id, type, value, note) VALUES ('trades.artisan.overclock-seconds', 'int', '30', 'Duration of Overclock.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.artisan.overclock-cooldown-seconds', 'int', '300', 'Cooldown of Overclock.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.artisan.overclock-range', 'real', '8', 'Blocks within which Overclock finds your Miner.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.warden.yield', 'real', '1.2', 'Multiplier of the yield of a Ranch or an Alembic (+20 %). Neither station exists yet: they read TradeApi.yieldFactor.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.warden.mend-fraction', 'real', '0.15', 'Fraction of the ally''s maximum health that Mend restores.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.warden.mend-level', 'int', '1', 'Warden level that unlocks Mend.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.warden.mend-range', 'real', '8', 'Blocks within which Mend finds an ally.');
INSERT INTO setting (id, type, value, note) VALUES ('trades.warden.mend-cooldown-seconds', 'int', '30', 'Cooldown of Mend.');
