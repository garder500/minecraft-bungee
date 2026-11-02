-- The Celestial Breath (GDD 4.7, 19.2-19.4): world events, Fractures, immune cells, Interpulsation Fragments. Edit in the panel.
-- Every number is a guess to measure in play (GDD 16.2). Shards of the Fractures and of the immune cells are Memory Shards.
INSERT INTO setting (id, type, value, note) VALUES ('breath.enabled', 'bool', 'true', 'Turns the events off without touching the rest.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.interval-minutes', 'int', '180', 'Minutes between two pulsations (an event starts every interval, never closer than a minute after the last one ended).');
INSERT INTO setting (id, type, value, note) VALUES ('breath.first-delay-minutes', 'int', '10', 'Minutes before the first event on a server that never had one.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.events', 'list', '', 'Events as id|title|weight|minutes|effects. Effects, separated by ;: yield:<resource|*>:<percent>, machine:<percent> (Flux use), monster:<percent> (damage), route:<realm> (Sky Gate shut), fracture:<realm>:<floor>, invade:<hub|realm>. An event must give something and cost something (GDD 19.3).');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 0, 'frozen_breath|Frozen Breath|3|45|yield:stasis_crystal:30;machine:20;fracture:necropolis:3;invade:hub');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 1, 'void_surge|Void Surge|3|60|yield:thunder_quartz:35;yield:chrono_sand:25;machine:20');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 2, 'hollow_calm|Hollow Calm|3|60|machine:-25;monster:30');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 3, 'ash_gale|Ash Gale|3|50|yield:cinderheart:40;monster:15;route:marshes');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 4, 'deep_pull|Deep Pull|3|50|yield:pressure_pearl:35;machine:15;route:citadel');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 5, 'grove_fracture|Fracture of the Grove|2|40|fracture:grove:2;monster:20');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 6, 'marsh_fracture|Fracture of the Marshes|2|40|fracture:marshes:4;yield:rot_resin:25;route:grove');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 7, 'immune_swarm|Immune Swarm|3|30|invade:hub;yield:chrono_sand:30');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 8, 'ashen_siege|Ashen Siege|2|30|invade:ashes;yield:cinderheart:50');
INSERT INTO setting_item (setting_id, position, value) VALUES ('breath.events', 9, 'abyssal_siege|Abyssal Siege|2|30|invade:depths;machine:-20');
INSERT INTO setting (id, type, value, note) VALUES ('breath.fragments.per-key', 'int', '5', 'Interpulsation Fragments that assemble into one key to Hell Gao (GDD 19.4). At most one Fragment per player per pulsation.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.fracture.fragment-chance', 'real', '0.2', 'Chance of a Fragment when a player clears the Fracture dungeon.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.fracture.shards-per-floor', 'int', '15', 'Memory Shards for clearing a Fracture, per floor of the dungeon (once per player and Fracture).');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.mob', 'text', 'ZOMBIE', 'Entity type of an immune cell (a hostile mob). They never break blocks, doors or explode.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.name', 'text', 'Immune Cell', 'Name shown over an immune cell.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.per-player', 'int', '4', 'Immune cells kept near each player in an invaded world.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.max', 'int', '30', 'Immune cells alive at once on the server.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.respawn-seconds', 'int', '20', 'Seconds between two top-ups of the immune cells.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.health', 'real', '40', 'Health of an immune cell.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.shards-per-kill', 'int', '2', 'Memory Shards for killing an immune cell.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.shards-cap', 'int', '40', 'Most Memory Shards a player earns from immune cells during one pulsation.');
INSERT INTO setting (id, type, value, note) VALUES ('breath.invasion.fragment-chance', 'real', '0.03', 'Chance of an Interpulsation Fragment when a player kills an immune cell.');
