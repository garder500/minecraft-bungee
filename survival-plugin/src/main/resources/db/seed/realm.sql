-- Realm resources: Cinderheart (Cracked Ashes) and Heartwood (Whispering Grove). The pity numbers follow the shape of
-- GDD 15.1 (Sunstone is 8 % / 12 / 36): the GDD gives no row for these two, so they are proposals to tune in the panel.
INSERT INTO realm_world (world, realm) VALUES ('sb_ashes', 'ashes');
INSERT INTO realm_world (world, realm) VALUES ('sb_grove', 'grove');
INSERT INTO realm_resource (id, realm, item, p0, soft, dur, amount_min, amount_max) VALUES ('cinderheart', 'ashes', 'cinderheart', 0.06, 12, 36, 1, 1);
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('cinderheart', 'block', 'MAGMA_BLOCK');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('cinderheart', 'block', 'GILDED_BLACKSTONE');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('cinderheart', 'block', 'NETHER_QUARTZ_ORE');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('cinderheart', 'mob', 'MAGMA_CUBE');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('cinderheart', 'mob', 'BLAZE');
INSERT INTO realm_resource (id, realm, item, p0, soft, dur, amount_min, amount_max) VALUES ('heartwood', 'grove', 'heartwood', 0.06, 12, 36, 1, 1);
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('heartwood', 'block', 'DARK_OAK_LOG');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('heartwood', 'block', 'MANGROVE_LOG');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('heartwood', 'block', 'MOSS_BLOCK');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('heartwood', 'mob', 'BOGGED');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('heartwood', 'mob', 'SPIDER');
