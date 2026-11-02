-- Realm resources: Cinderheart (Cracked Ashes), Heartwood (Whispering Grove) and Chrono Sand (Citadel of Golden Sands). The pity numbers follow the shape of
-- GDD 15.1 (Sunstone is 8 % / 12 / 36): the GDD gives no row for these, so they are proposals to tune in the panel.
INSERT INTO realm_world (world, realm) VALUES ('sb_ashes', 'ashes');
INSERT INTO realm_world (world, realm) VALUES ('sb_grove', 'grove');
INSERT INTO realm_world (world, realm) VALUES ('sb_necropolis', 'necropolis');
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
INSERT INTO realm_resource (id, realm, item, p0, soft, dur, amount_min, amount_max) VALUES ('soul_ash', 'necropolis', 'soul_ash', 0.06, 12, 36, 1, 1);
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('soul_ash', 'block', 'SOUL_SAND');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('soul_ash', 'block', 'SOUL_SOIL');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('soul_ash', 'block', 'BONE_BLOCK');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('soul_ash', 'mob', 'SKELETON');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('soul_ash', 'mob', 'WITHER_SKELETON');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('soul_ash', 'mob', 'ZOMBIE');
INSERT INTO realm_world (world, realm) VALUES ('sb_heights', 'heights');
INSERT INTO realm_resource (id, realm, item, p0, soft, dur, amount_min, amount_max) VALUES ('stasis_crystal', 'heights', 'stasis_crystal', 0.06, 12, 36, 1, 1);
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('stasis_crystal', 'block', 'BLUE_ICE');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('stasis_crystal', 'block', 'PACKED_ICE');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('stasis_crystal', 'block', 'AMETHYST_CLUSTER');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('stasis_crystal', 'mob', 'STRAY');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('stasis_crystal', 'mob', 'POLAR_BEAR');

-- Putrid Marshes (issue 43): Rot Resin, same pity shape as the other local resources (proposal, tune in the panel).
INSERT INTO realm_world (world, realm) VALUES ('sb_marsh', 'marshes');
INSERT INTO realm_resource (id, realm, item, p0, soft, dur, amount_min, amount_max) VALUES ('rot_resin', 'marshes', 'rot_resin', 0.06, 12, 36, 1, 1);
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('rot_resin', 'block', 'MUDDY_MANGROVE_ROOTS');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('rot_resin', 'block', 'SLIME_BLOCK');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('rot_resin', 'block', 'SCULK');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('rot_resin', 'mob', 'SLIME');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('rot_resin', 'mob', 'WITCH');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('rot_resin', 'mob', 'DROWNED');
INSERT INTO realm_world (world, realm) VALUES ('sb_citadel', 'citadel');
INSERT INTO realm_resource (id, realm, item, p0, soft, dur, amount_min, amount_max) VALUES ('chrono_sand', 'citadel', 'chrono_sand', 0.06, 12, 36, 1, 1);
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('chrono_sand', 'block', 'SUSPICIOUS_SAND');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('chrono_sand', 'block', 'SAND');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('chrono_sand', 'block', 'GOLD_BLOCK');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('chrono_sand', 'mob', 'HUSK');
INSERT INTO realm_resource_source (resource_id, kind, key) VALUES ('chrono_sand', 'mob', 'SILVERFISH');
