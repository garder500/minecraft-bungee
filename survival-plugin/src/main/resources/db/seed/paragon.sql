-- Paragon (GDD 11.5, 19.1): ten Transcendence ranks and one ultimate per class. First guesses, to tune with play.
-- A rank gives a title, an aura, a server utility or a lore fragment, never a stat (GDD 9.2).
INSERT INTO setting (id, type, value, note) VALUES ('paragon.enabled', 'bool', 'true', 'Paragon ranks and ultimates on or off.');
INSERT INTO setting (id, type, value, note) VALUES ('paragon.astral-clear-points', 'int', '1', 'Paragon points paid the first time a player survives the Astral Interior (it makes them a Paragon).');
INSERT INTO setting (id, type, value, note) VALUES ('paragon.aura-seconds', 'int', '2', 'Seconds between two puffs of a Paragon aura.');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (1, 'Awakened', 1, 'TITLE', 'Paragon', 0, 'The title Paragon, and the ultimate capacity of your class (/class ultimate).');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (2, 'Ether-touched', 3, 'AURA', 'ether', 0, 'The Ether aura: pale sparks around you (/paragon aura ether).');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (3, 'Steward', 6, 'UTILITY', 'island_members', 1, 'One more co-op slot on your island.');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (4, 'Transcendent', 10, 'TITLE', 'Transcendent', 0, 'The title Transcendent.');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (5, 'Starlit', 15, 'AURA', 'starlight', 0, 'The Starlight aura: motes of the Astral Interior.');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (6, 'Witness', 21, 'LORE', 'witness', 0, 'A memory of the Devourer''s first hunger: "It did not want the world. It wanted to be told that it was loved."');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (7, 'Keeper of the Pulse', 28, 'UTILITY', 'island_members', 1, 'One more co-op slot on your island.');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (8, 'Elder Paragon', 36, 'TITLE', 'Elder Paragon', 0, 'The title Elder Paragon.');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (9, 'Between Pulses', 45, 'AURA', 'interpulse', 0, 'The Interpulse aura: the stillness between two breaths of the sky.');
INSERT INTO paragon_rank (rank, name, points, reward_kind, reward_key, reward_value, reward_text) VALUES (10, 'Paragon of the Breath', 55, 'TITLE', 'Paragon of the Breath', 0, 'The title Paragon of the Breath.');
INSERT INTO paragon_ultimate (class, name, summary, aether_cost, cooldown_seconds, radius, power, seconds) VALUES ('VANGUARD', 'Unbroken Bastion', 'You and the allies within 14 blocks gain Resistance II and 8 Absorption hearts for 12 seconds.', 90, 300, 14, 0, 12);
INSERT INTO paragon_ultimate (class, name, summary, aether_cost, cooldown_seconds, radius, power, seconds) VALUES ('DUELIST', 'Thousand Edges', 'Your next 8 hits within 12 seconds are all Surges.', 80, 300, 0, 8, 12);
INSERT INTO paragon_ultimate (class, name, summary, aether_cost, cooldown_seconds, radius, power, seconds) VALUES ('CHANNELER', 'Celestial Collapse', 'The sky falls on everything within 12 blocks: 400% of your weapon damage.', 100, 300, 12, 400, 0);
INSERT INTO paragon_ultimate (class, name, summary, aether_cost, cooldown_seconds, radius, power, seconds) VALUES ('RANGER', 'Starfall Volley', 'A wide fan of 15 arrows, each 40% of your bow damage.', 85, 300, 6, 15, 0);
