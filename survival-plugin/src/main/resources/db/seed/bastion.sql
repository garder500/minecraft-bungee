-- Phase 2 Bastions (GDD 12.1): one per Realm. The Guardian fights are the existing boss rows; the keys are the former
-- summon items (GDD 12.3).
INSERT INTO bastion (id, position, realm, name, tagline, guardian, key_item, palette, radius, min_players, max_players, time_limit_seconds, deaths_allowed) VALUES ('ashes_bastion', 0, 'Realm of the Cracked Ashes', 'Ashes Bastion', 'The Lava Colossus pulses with the Devourer''s heartbeat.', 'magma_warden', 'magma_core', 'cinder', 30, 1, 5, 1200, 3);
INSERT INTO bastion (id, position, realm, name, tagline, guardian, key_item, palette, radius, min_players, max_players, time_limit_seconds, deaths_allowed) VALUES ('grove_bastion', 1, 'Whispering Grove', 'Grove Bastion', 'The Mycelial Queen guards the genes of a world that is gone.', 'sylvan_matriarch', 'matriarch_egg', 'grove', 30, 1, 5, 1200, 3);
INSERT INTO setting (id, type, value, note) VALUES ('bastion.enabled', 'bool', 'true', 'Bastions can be entered');
INSERT INTO setting (id, type, value, note) VALUES ('bastion.max-concurrent', 'int', '4', 'Bastion instances alive at once on this server');
INSERT INTO setting (id, type, value, note) VALUES ('bastion.countdown-seconds', 'int', '8', 'Time between the arrival of the party and the Guardian waking');
INSERT INTO setting (id, type, value, note) VALUES ('bastion.exit-delay-seconds', 'int', '15', 'Time the party can stay after the end of the fight');
