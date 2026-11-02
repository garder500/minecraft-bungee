package win.vampyuyu.survival.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

class StationConfigTest {
    private static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.QUARRY, ContentDomains.RESONANCE, ContentDomains.BOSS, ContentDomains.STATIONS), null);
        return db;
    }

    @Test
    void theShippedSeedLoadsWithoutAProblem() {
        StationConfig cfg = StationConfig.load(seeded());
        assertEquals(List.of(), cfg.problems());
        assertTrue(cfg.enabled);
        assertEquals(3, cfg.brews().values().stream().map(StationConfig.Brew::family).distinct().count(),
            "tonics, oils and draughts");
        assertEquals(List.of("wool", "leather", "marrow"), List.copyOf(cfg.products().keySet()));
        assertEquals(0.6, cfg.returnRate, 1e-9);
    }

    @Test
    void everyOilNamesAnElementAndEveryEffectIsKnown() {
        StationConfig cfg = StationConfig.load(seeded());
        for (StationConfig.Brew b : cfg.brews().values()) {
            if ("oil".equals(b.family())) {
                assertEquals("oil", b.effect());
                assertNotNull(Element.parse(b.element()), b.id());
            } else {
                assertTrue(StationConfig.EFFECTS.contains(b.effect()), b.id() + " uses " + b.effect());
            }
            assertFalse(b.cost().isEmpty(), b.id());
        }
    }

    @Test
    void everyItemOfTheStationsExistsInTheSeedsOfTheOtherFeatures() {
        StationConfig cfg = StationConfig.load(seeded());
        for (String component : cfg.components()) {
            assertTrue(isComponentOfAResonanceBastion(component), component);
        }
        // the Forge makes the bronze cleaver out of bronzite: the Crucible gives 60 percent of the bronzite back
        assertEquals(7, StationRules.refund(cfg.forgeCost("bronze_cleaver"), cfg.returnRate, cfg.components()).get("custom:bronzite"));
    }

    private static boolean isComponentOfAResonanceBastion(String item) {
        try (Db db = seeded()) {
            return db.read(c -> Db.query(c, "SELECT COUNT(*) FROM setting_item WHERE setting_id = 'resonance.bastions' AND value LIKE ?",
                rs -> rs.getInt(1), "%|" + item + "|%").get(0)) > 0;
        }
    }

    @Test
    void aBrokenRowIsSkippedAndListed() {
        Db db = seeded();
        db.write(c -> Db.update(c, "UPDATE alembic_recipe SET element = NULL WHERE id = 'frost_oil'"));
        db.write(c -> Db.update(c, "UPDATE alembic_recipe SET effect = 'levitation_of_doom' WHERE id = 'owl_draught'"));
        StationConfig cfg = StationConfig.load(db);
        assertFalse(cfg.brews().containsKey("frost_oil"));
        assertFalse(cfg.brews().containsKey("owl_draught"));
        assertEquals(2, cfg.problems().size());
    }

    @Test
    void everyContractTargetsSomethingThatExists() {
        StationConfig cfg = StationConfig.load(seeded());
        assertEquals(12, cfg.contracts().size());
        for (StationConfig.Contract c : cfg.contracts().values()) {
            if ("boss".equals(c.kind())) {
                try (Db db = seeded()) {
                    long n = db.read(conn -> Db.query(conn, "SELECT COUNT(*) FROM boss WHERE id = ?", rs -> rs.getLong(1), c.target()).get(0));
                    assertEquals(1, n, c.id());
                }
            }
            if ("kill".equals(c.kind())) {
                assertEquals(c.target().toUpperCase(java.util.Locale.ROOT), c.target(), c.id() + " names an entity type in capitals");
            }
        }
    }
}
