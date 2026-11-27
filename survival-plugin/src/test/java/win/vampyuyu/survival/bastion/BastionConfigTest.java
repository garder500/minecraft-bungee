package win.vampyuyu.survival.bastion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.boss.BossTestData;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

class BastionConfigTest {
    private static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BASTION), null);
        return db;
    }

    @Test
    void theShippedSeedHasTheBastions() {
        BastionConfig cfg = BastionConfig.load(seeded());
        assertEquals(List.of(), cfg.problems());
        assertEquals(List.of("echo_rift", "ashes_bastion", "grove_bastion", "necropolis_bastion", "heights_bastion", "citadel_bastion", "tempest_bastion", "marsh_bastion", "depths_bastion", "devourer_raid", "hell_gao_bastion"), List.copyOf(cfg.bastions().keySet()));
        assertEquals("voidwyrm", cfg.get("echo_rift").orElseThrow().guardian());
        assertEquals("echo_rift", cfg.ofKey("echo_key").orElseThrow().id());
        assertEquals("thunder_dragon", cfg.get("tempest_bastion").orElseThrow().guardian());
        assertEquals("storm", cfg.get("tempest_bastion").orElseThrow().palette());
        assertEquals("tempest_bastion", cfg.ofKey("tempest_key").orElseThrow().id());
        assertEquals("magma_warden", cfg.get("ashes_bastion").orElseThrow().guardian());
        assertEquals("sylvan_matriarch", cfg.get("grove_bastion").orElseThrow().guardian());
        assertEquals("ashes_bastion", cfg.ofKey("magma_core").orElseThrow().id());
        assertEquals("grove_bastion", cfg.ofKey("matriarch_egg").orElseThrow().id());
        assertEquals("crypt_lord", cfg.get("necropolis_bastion").orElseThrow().guardian());
        assertEquals("necro", cfg.get("necropolis_bastion").orElseThrow().palette());
        assertEquals("necropolis_bastion", cfg.ofKey("judge_seal").orElseThrow().id());
        assertEquals("frost_revenant", cfg.get("heights_bastion").orElseThrow().guardian());
        assertEquals("frost", cfg.get("heights_bastion").orElseThrow().palette());
        assertEquals("mire_abomination", cfg.get("marsh_bastion").orElseThrow().guardian());
        assertEquals("marsh", cfg.get("marsh_bastion").orElseThrow().palette());
        assertEquals("marsh_bastion", cfg.ofKey("mire_cyst").orElseThrow().id());
        assertEquals("heights_bastion", cfg.ofKey("frost_heart").orElseThrow().id());
        assertEquals("abyssal_leviathan", cfg.get("depths_bastion").orElseThrow().guardian());
        assertEquals("abyss", cfg.get("depths_bastion").orElseThrow().palette());
        assertEquals("depths_bastion", cfg.ofKey("leviathan_scale").orElseThrow().id());
        assertTrue(cfg.get("nope").isEmpty());
        assertEquals(4, cfg.maxConcurrent);
    }

    @Test
    void aRealmsBastionIsFoundBehindItsSeal() {
        BastionConfig cfg = BastionConfig.load(seeded());
        assertEquals("heights", cfg.realmOf("heights_bastion"));
        assertEquals("archipelago", cfg.realmOf("tempest_bastion"));
        assertEquals("necropolis", cfg.realmOf("necropolis_bastion"));
        // no seal opens these: they keep their own rules (Hub altar, Interpulse, the Raid's eight clears)
        assertEquals(null, cfg.realmOf("echo_rift"));
        assertEquals(null, cfg.realmOf("devourer_raid"));
        assertEquals(null, cfg.realmOf("hell_gao_bastion"));
    }

    /** The Gates are a straight chain: the Guardian of Realm N (its Bastion's boss) lights Gate N+1. */
    @Test
    void eachGuardianLightsTheNextGate() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BASTION, ContentDomains.ISLAND, ContentDomains.REALM), null);
        BastionConfig cfg = BastionConfig.load(db);
        win.vampyuyu.survival.data.Settings island = win.vampyuyu.survival.data.Settings.load(db, "island");
        win.vampyuyu.survival.data.Settings realm = win.vampyuyu.survival.data.Settings.load(db, "realm");
        java.util.Map<win.vampyuyu.survival.api.Realm, String> gateBoss = new java.util.EnumMap<>(win.vampyuyu.survival.api.Realm.class);
        gateBoss.put(win.vampyuyu.survival.api.Realm.GROVE, island.getString("grove.gate-boss", ""));
        gateBoss.put(win.vampyuyu.survival.api.Realm.HEIGHTS, island.getString("frost.gate-boss", ""));
        gateBoss.put(win.vampyuyu.survival.api.Realm.CITADEL, island.getString("citadel.gate-boss", ""));
        gateBoss.put(win.vampyuyu.survival.api.Realm.MARSHES, island.getString("marsh.gate-boss", ""));
        gateBoss.put(win.vampyuyu.survival.api.Realm.ARCHIPELAGO, island.getString("storm.gate-boss", ""));
        gateBoss.put(win.vampyuyu.survival.api.Realm.DEPTHS, realm.getString("depths.gate-boss", ""));
        gateBoss.put(win.vampyuyu.survival.api.Realm.NECROPOLIS, island.getString("necropolis.gate-boss", ""));
        win.vampyuyu.survival.api.Realm[] order = win.vampyuyu.survival.api.Realm.values();
        for (int i = 1; i < order.length; i++) {
            String previousGuardian = cfg.get(cfg.seals.get(order[i - 1].id())).orElseThrow().guardian();
            assertEquals(previousGuardian, gateBoss.get(order[i]), "Gate " + (i + 1) + " " + order[i].id());
        }
    }

    @Test
    void everyGuardianIsABossOfTheSeed() {
        Db boss = BossTestData.seeded();
        BastionConfig cfg = BastionConfig.load(seeded());
        for (BastionConfig.Bastion b : cfg.bastions().values()) {
            long n = boss.read(c -> Db.query(c, "SELECT COUNT(*) FROM boss WHERE id = ?", rs -> rs.getLong(1), b.guardian()).get(0));
            assertEquals(1, n, "boss " + b.guardian());
            assertNotNull(b.keyItem());
        }
    }

    @Test
    void twoBastionsCannotShareAKey() {
        Db db = seeded();
        db.write(c -> {
            try (var st = c.createStatement()) {
                st.execute("INSERT INTO bastion (id, position, realm, name, tagline, guardian, key_item, palette, radius, time_limit_seconds) "
                    + "VALUES ('twin', 9, 'k', 'Twin', 't', 'magma_warden', 'magma_core', 'cinder', 20, 600)");
            }
            return null;
        });
        BastionConfig cfg = BastionConfig.load(db);
        assertFalse(cfg.get("twin").isPresent());
        assertEquals(1, cfg.problems().size());
    }

    @Test
    void anEmptyTableIsReported() {
        BastionConfig cfg = BastionConfig.load(Db.memory());
        assertTrue(cfg.bastions().isEmpty());
        assertFalse(cfg.problems().isEmpty());
    }
}
