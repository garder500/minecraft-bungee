-- Stations (GDD 10.1, 17, 22). Every number is a guess to measure in play (GDD 23): coins are on the x1/100 scale of #18.
INSERT INTO setting (id, type, value, note) VALUES ('station.enabled', 'bool', 'true', 'Turns the Alembic, the Crucible, the Ranch and the contracts on or off.');
INSERT INTO setting (id, type, value, note) VALUES ('station.alembic.slots', 'int', '2', 'Brews that run at the same time per player.');
INSERT INTO setting (id, type, value, note) VALUES ('station.alembic.queue', 'int', '6', 'Brews a player can have in the queue (running and waiting).');
INSERT INTO setting (id, type, value, note) VALUES ('station.crucible.return-rate', 'real', '0.6', 'Share of the elemental materials the Crucible gives back (GDD 10.1: 60 percent).');
INSERT INTO setting (id, type, value, note) VALUES ('station.crucible.components', 'list', '', 'Boss components: given back at 100 percent, and a duplicate turns into Memory Shards. One item id per line.');
INSERT INTO setting_item (setting_id, position, value) VALUES ('station.crucible.components', 0, 'warden_heart');
INSERT INTO setting_item (setting_id, position, value) VALUES ('station.crucible.components', 1, 'matriarch_silk');
INSERT INTO setting_item (setting_id, position, value) VALUES ('station.crucible.components', 2, 'frost_shard');
INSERT INTO setting_item (setting_id, position, value) VALUES ('station.crucible.components', 3, 'leviathan_tooth');
INSERT INTO setting_item (setting_id, position, value) VALUES ('station.crucible.components', 4, 'lord_bone');
INSERT INTO setting_item (setting_id, position, value) VALUES ('station.crucible.components', 5, 'voidwyrm_scale');
INSERT INTO setting (id, type, value, note) VALUES ('station.crucible.duplicate-shards', 'int', '25', 'Memory Shards for one duplicate component (the player must already own the component once).');
INSERT INTO setting (id, type, value, note) VALUES ('station.ranch.storage-hours', 'int', '12', 'The Ranch stops producing when this many hours of output wait to be collected.');
INSERT INTO setting (id, type, value, note) VALUES ('station.ranch.tier-bonus', 'real', '0.5', 'Extra output per generator tier above I (tier IV makes x2.5).');
INSERT INTO setting (id, type, value, note) VALUES ('station.contracts.per-day', 'int', '3', 'Contracts offered to a player every UTC day.');
INSERT INTO setting (id, type, value, note) VALUES ('station.contracts.daily-coin-cap', 'int', '3000', 'Coins the contracts can pay one player per UTC day (a guess, same idea as the quest cap).');

-- Tonics: temporary resistances.
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('ember_tonic', 0, 'tonic', 'Ember Tonic', 120, 'fire_resistance', 0, 300, NULL, 1);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('ember_tonic', 0, 'BLAZE_POWDER', 2);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('ember_tonic', 1, 'GLASS_BOTTLE', 1);
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('stone_tonic', 1, 'tonic', 'Stone Tonic', 180, 'resistance', 0, 240, NULL, 2);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('stone_tonic', 0, 'custom:bronzite', 4);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('stone_tonic', 1, 'GLASS_BOTTLE', 1);
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('tide_tonic', 2, 'tonic', 'Tide Tonic', 150, 'water_breathing', 0, 420, NULL, 1);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('tide_tonic', 0, 'PUFFERFISH', 1);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('tide_tonic', 1, 'GLASS_BOTTLE', 1);

-- Oils: a temporary element on the weapon in hand (the weapon keeps its own element once the oil is spent).
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('frost_oil', 3, 'oil', 'Frost Oil', 240, 'oil', 0, 300, 'frost', 2);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('frost_oil', 0, 'custom:frostium', 2);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('frost_oil', 1, 'custom:marrow', 2);
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('ember_oil', 4, 'oil', 'Ember Oil', 240, 'oil', 0, 300, 'ember', 3);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('ember_oil', 0, 'custom:sunstone', 2);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('ember_oil', 1, 'custom:marrow', 2);
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('earth_oil', 5, 'oil', 'Earth Oil', 180, 'oil', 0, 300, 'earth', 1);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('earth_oil', 0, 'custom:bronzite', 3);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('earth_oil', 1, 'custom:marrow', 1);

-- Draughts: regeneration and utility.
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('mending_draught', 6, 'draught', 'Mending Draught', 200, 'regeneration', 1, 30, NULL, 1);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('mending_draught', 0, 'WHEAT', 6);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('mending_draught', 1, 'SWEET_BERRIES', 6);
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('keen_draught', 7, 'draught', 'Keen Draught', 200, 'haste', 1, 240, NULL, 2);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('keen_draught', 0, 'custom:marrow', 2);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('keen_draught', 1, 'GLOWSTONE_DUST', 4);
INSERT INTO alembic_recipe (id, position, family, name, seconds, effect, amplifier, duration_seconds, element, min_tier) VALUES ('owl_draught', 8, 'draught', 'Owl Draught', 150, 'night_vision', 0, 600, NULL, 1);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('owl_draught', 0, 'GOLDEN_CARROT', 1);
INSERT INTO alembic_ingredient (recipe_id, position, item, amount) VALUES ('owl_draught', 1, 'GLASS_BOTTLE', 1);

-- The Ranch: biological resources, unlocked by the generator tier.
INSERT INTO ranch_product (id, position, item, min_tier, seconds) VALUES ('wool', 0, 'WHITE_WOOL', 1, 240);
INSERT INTO ranch_product (id, position, item, min_tier, seconds) VALUES ('leather', 1, 'LEATHER', 1, 360);
INSERT INTO ranch_product (id, position, item, min_tier, seconds) VALUES ('marrow', 2, 'custom:marrow', 2, 900);

-- Contracts of the Contract Hunter (rewards in coins on the x1/100 scale, Memory Shards for the hard ones).
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_zombies', 0, 'kill', 'Clear the night', 'ZOMBIE', 20, 1, 300, 0);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_skeletons', 1, 'kill', 'Rattle the bones', 'SKELETON', 20, 1, 300, 0);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_spiders', 2, 'kill', 'Web cutter', 'SPIDER', 15, 1, 250, 0);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_blazes', 3, 'kill', 'Fire watch', 'BLAZE', 12, 3, 700, 5);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_wool', 4, 'deliver', 'Wool for the Hub', 'WHITE_WOOL', 32, 1, 250, 0);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_leather', 5, 'deliver', 'Tanner order', 'LEATHER', 24, 1, 250, 0);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_marrow', 6, 'deliver', 'Marrow for the Alembic', 'custom:marrow', 6, 2, 500, 3);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_bronzite', 7, 'deliver', 'Bronzite shipment', 'custom:bronzite', 24, 1, 400, 0);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_cinderheart', 8, 'deliver', 'A heart that still burns', 'custom:cinderheart', 2, 2, 900, 10);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_heartwood', 9, 'deliver', 'Living wood', 'custom:heartwood', 2, 2, 900, 10);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_warden', 10, 'boss', 'Break the Magma Warden', 'magma_warden', 1, 3, 1500, 15);
INSERT INTO contract (id, position, kind, title, target, amount, min_tier, coins, shards) VALUES ('c_matriarch', 11, 'boss', 'Silence the Matriarch', 'sylvan_matriarch', 1, 3, 1500, 15);
