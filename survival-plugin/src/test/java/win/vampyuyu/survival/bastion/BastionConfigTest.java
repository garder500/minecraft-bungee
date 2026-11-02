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
        assertEquals(List.of("ashes_bastion", "grove_bastion", "necropolis_bastion", "heights_bastion", "citadel_bastion", "marsh_bastion", "depths_bastion"), List.copyOf(cfg.bastions().keySet()));
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
