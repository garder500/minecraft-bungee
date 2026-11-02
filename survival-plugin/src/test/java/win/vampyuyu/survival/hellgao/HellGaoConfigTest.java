package win.vampyuyu.survival.hellgao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.bastion.BastionConfig;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

class HellGaoConfigTest {
    private static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.HELLGAO, ContentDomains.BASTION), null);
        return db;
    }

    @Test
    void theShippedSeedIsValid() {
        Db db = seeded();
        HellGaoConfig cfg = HellGaoConfig.load(db);
        assertEquals(List.of(), cfg.problems());
        assertTrue(cfg.enabled);
        assertTrue(cfg.paragonRequired);
        assertEquals(3600, cfg.pulsePeriodSeconds);
        assertEquals(300, cfg.windowSeconds);
        assertEquals(6, cfg.titles.size());
        assertEquals(7, cfg.lore.size());
        assertEquals(9, cfg.ascensionBosses.size());
        assertTrue(cfg.title(cfg.auraTitle).isPresent(), "the aura title exists");
        assertEquals(0, cfg.title("between_pulses").orElseThrow().minAscension());
        assertEquals(5, cfg.title("ascended_5").orElseThrow().minAscension());
    }

    @Test
    void theBastionIsSecretlyWiredToTheGuardianAndTheKey() {
        Db db = seeded();
        HellGaoConfig cfg = HellGaoConfig.load(db);
        BastionConfig bastions = BastionConfig.load(db);
        BastionConfig.Bastion b = bastions.get(cfg.bastionId).orElseThrow();
        assertEquals("hell_gao", b.guardian());
        assertEquals("interpulse_key", b.keyItem());
        assertEquals(2, b.minPlayers());
        assertEquals(5, b.maxPlayers());
        assertEquals("rift", b.palette());
        assertEquals(cfg.bastionId, bastions.ofKey("interpulse_key").orElseThrow().id());
    }

    @Test
    void aBadEntryIsSkippedAndListed() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.HELLGAO), null);
        db.write(conn -> {
            Db.update(conn, "INSERT INTO setting_item (setting_id, position, value) VALUES ('hellgao.titles', 99, 'broken')");
            Db.update(conn, "INSERT INTO setting_item (setting_id, position, value) VALUES ('hellgao.lore', 99, 'no-separator')");
            return null;
        });
        HellGaoConfig cfg = HellGaoConfig.load(db);
        assertEquals(2, cfg.problems().size());
        assertEquals(6, cfg.titles.size());
        assertEquals(7, cfg.lore.size());
    }
}
