package win.vampyuyu.survival.promotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.boss.BossTestData;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

class PromotionConfigTest {
    private static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.PROMOTION), null);
        return db;
    }

    @Test
    void theShippedSeedIsComplete() {
        PromotionConfig cfg = PromotionConfig.load(seeded());
        assertEquals(List.of(), cfg.problems());
        for (PlayerClass c : PlayerClass.values()) {
            assertEquals(2, cfg.specsOf(c).size(), c + " has two specializations");
            assertTrue(cfg.ability(c).isPresent(), c + " has a Master capacity");
        }
        assertEquals(10, cfg.chains().size());
        for (String rank : List.of("ADEPT", "MASTER")) {
            for (Profile p : Profile.values()) {
                assertTrue(cfg.chainFor(rank, p).isPresent(), rank + " " + p);
            }
        }
    }

    @Test
    void everyChainHasThreeToFiveSteps() {
        for (PromotionConfig.Chain chain : PromotionConfig.load(seeded()).chains().values()) {
            assertTrue(chain.steps().size() >= 3 && chain.steps().size() <= 5, chain.id());
            chain.steps().forEach(s -> assertTrue(s.amount() >= 1, chain.id()));
        }
    }

    @Test
    void theSettingsAreRead() {
        PromotionConfig cfg = PromotionConfig.load(seeded());
        assertTrue(cfg.enabled);
        assertEquals("voidwyrm", cfg.echoBoss);
        assertEquals(2, cfg.adeptBastionClears);
        assertEquals(2, cfg.masterGuardians);
    }

    @Test
    void theEchoIsABossOfTheSeed() {
        Db boss = BossTestData.seeded();
        String echo = PromotionConfig.load(seeded()).echoBoss;
        long n = boss.read(c -> Db.query(c, "SELECT COUNT(*) FROM boss WHERE id = ?", rs -> rs.getLong(1), echo).get(0));
        assertEquals(1, n);
    }

    @Test
    void aClassWithOneSpecializationIsReported() {
        Db db = seeded();
        db.write(c -> {
            Db.update(c, "DELETE FROM promotion_spec WHERE id = 'ranger_deadeye'");
            return null;
        });
        PromotionConfig cfg = PromotionConfig.load(db);
        assertFalse(cfg.problems().isEmpty());
        assertTrue(cfg.problems().get(0).contains("RANGER"));
    }

    @Test
    void aChainWithTwoStepsIsSkipped() {
        Db db = seeded();
        db.write(c -> {
            Db.update(c, "DELETE FROM promotion_step WHERE chain_id = 'adept_boss' AND position = 2");
            return null;
        });
        PromotionConfig cfg = PromotionConfig.load(db);
        assertTrue(cfg.chain("adept_boss").isEmpty());
        assertTrue(cfg.problems().stream().anyMatch(p -> p.contains("adept_boss")));
    }

    @Test
    void modifiersFactorsStayPositive() {
        var m = new win.vampyuyu.survival.api.PromotionApi.Mods(0, 0, 0, 0, -100, -100, -100);
        assertEquals(0.1, m.cooldownFactor(), 1e-9);
        assertEquals(1.5, new win.vampyuyu.survival.api.PromotionApi.Mods(0, 0, 0, 0, 0, 0, 50).powerFactor(), 1e-9);
    }
}
