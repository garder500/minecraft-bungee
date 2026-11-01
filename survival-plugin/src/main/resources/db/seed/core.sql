-- Plugin level settings: the debug flag and which features are on.
-- Converted from the former config.yml by scripts/yaml_to_seed_core.py; edit it in the panel.
INSERT INTO setting (id, type, value, note) VALUES ('core.debug', 'bool', 'false', 'Log extra [debug] lines to the console.');
INSERT INTO setting (id, type, value, note) VALUES ('core.features.utility.enabled', 'bool', 'true', '');
INSERT INTO setting (id, type, value, note) VALUES ('core.features.island.enabled', 'bool', 'true', '');
INSERT INTO setting (id, type, value, note) VALUES ('core.features.wings.enabled', 'bool', 'true', '');
INSERT INTO setting (id, type, value, note) VALUES ('core.features.menu.enabled', 'bool', 'true', '');
