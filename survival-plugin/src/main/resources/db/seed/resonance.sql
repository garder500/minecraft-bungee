-- Memory Shards (GDD 15.3-15.5) and Resonance ranks of the Bastions (GDD 12.4). Edit in the panel.
-- Shard amounts and the weekly forge limit are guesses to measure in play (GDD 16.2); the rank multipliers are fixed by the GDD.
INSERT INTO setting (id, type, value, note) VALUES ('resonance.bastions', 'list', '', 'Bastions as bossId|realmId|componentItem|baseShards (realm empty for the Echo of the Rift). baseShards is paid for a clear at rank I.');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 0, 'voidwyrm||voidwyrm_scale|50');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 1, 'magma_warden|ashes|warden_heart|30');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 2, 'sylvan_matriarch|grove|matriarch_silk|25');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 3, 'frost_revenant|heights|frost_shard|30');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 4, 'abyssal_leviathan|depths|leviathan_tooth|35');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 5, 'crypt_lord|necropolis|lord_bone|25');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 6, 'temporal_pharaoh|citadel|pharaoh_linen|28');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 7, 'thunder_dragon|archipelago|thunder_core|40');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.shards.rank-bonus', 'real', '0.25', 'Extra Shards per rank above I, as a fraction of the base (rank V pays x2).');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.shards.first-clear-multiplier', 'real', '2.0', 'First clear of the day pays this multiple of Shards (GDD 15.5). No permanent streak bonus.');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.shards.defeat-fraction', 'real', '0.2', 'A late defeat pays this fraction of a clear, scaled by the progress made (GDD 15.4).');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.shards.min-share', 'real', '0.05', 'A participant must deal this share of the damage to earn Shards.');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.forge.cost', 'int', '100', 'Memory Shards for one component (GDD 15.3).');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.forge.weekly-limit', 'int', '5', 'Components a player may forge with Shards per ISO week.');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.pity.runs', 'int', '4', 'The component is guaranteed within this many runs without a drop (GDD 15.2).');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.pity.runs-high-rank', 'int', '3', 'Same guarantee from the rank below on.');
INSERT INTO setting (id, type, value, note) VALUES ('resonance.pity.high-rank-from', 'int', '3', 'Resonance rank from which the shorter guarantee applies.');
INSERT INTO setting_item (setting_id, position, value) VALUES ('resonance.bastions', 14, 'mire_abomination|marshes|mire_ichor|30');
