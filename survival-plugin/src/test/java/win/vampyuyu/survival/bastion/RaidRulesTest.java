package win.vampyuyu.survival.bastion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The Raid of the Devourer: seed, prerequisites, collapse and first-clear coins. */
class RaidRulesTest {
    private static BastionConfig cfg() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BASTION), null);
        return BastionConfig.load(db);
    }

    @Test
    void theRaidIsABastionWithItsOwnKeyAndTheEightGuardiansAsPrerequisites() {
        BastionConfig cfg = cfg();
        assertEquals(List.of(), cfg.problems());
        assertTrue(cfg.isRaid("devourer_raid"));
        assertFalse(cfg.isRaid("ashes_bastion"));
        BastionConfig.Bastion raid = cfg.get("devourer_raid").orElseThrow();
        assertEquals("devourer", raid.guardian());
        assertEquals("devourer_sigil", raid.keyItem());
        assertEquals("devourer_raid", cfg.ofKey("devourer_sigil").orElseThrow().id());
        assertEquals(5, raid.maxPlayers());
        assertEquals(8, cfg.raidRequires.size());
        for (String id : cfg.raidRequires) {
            assertTrue(cfg.get(id).isPresent(), id);
            assertFalse(cfg.isRaid(id));
        }
        assertTrue(cfg.raidFirstClearCoins <= 5000);
    }

    @Test
    void aMemberMustHaveClearedEveryRequiredBastion() {
        BastionConfig cfg = cfg();
        BastionData data = new BastionData();
        assertEquals(cfg.raidRequires, RaidRules.missing(cfg, data));
        for (String id : cfg.raidRequires.subList(0, 7)) {
            data.recordClear(id, 1000);
        }
        assertEquals(List.of("tempest_bastion"), RaidRules.missing(cfg, data));
        assertEquals("Tempest Bastion", RaidRules.names(cfg, RaidRules.missing(cfg, data)));
        data.recordDefeat("tempest_bastion");
        assertEquals(1, RaidRules.missing(cfg, data).size(), "a defeat does not count");
        data.recordClear("tempest_bastion", 1000);
        assertTrue(RaidRules.missing(cfg, data).isEmpty());
    }

    @Test
    void theArenaOnlyFallsInTheRaidFromTheLastPhase() {
        BastionConfig cfg = cfg();
        assertFalse(RaidRules.collapsing(cfg, "devourer_raid", 0));
        assertFalse(RaidRules.collapsing(cfg, "devourer_raid", 1));
        assertTrue(RaidRules.collapsing(cfg, "devourer_raid", 2));
        assertFalse(RaidRules.collapsing(cfg, "ashes_bastion", 2));
    }

    @Test
    void theCollapseStopsAtTheCore() {
        int r = 40;
        int rings = 0;
        while (r > RaidRules.CORE_RADIUS) {
            r = RaidRules.nextRadius(r);
            rings++;
        }
        assertEquals(31, rings);
        assertEquals(RaidRules.CORE_RADIUS, RaidRules.nextRadius(RaidRules.CORE_RADIUS));
    }

    @Test
    void everyColumnOfTheDiscBelongsToExactlyOneRing() {
        int radius = 12;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                long expected = Math.round(Math.sqrt((double) x * x + (double) z * z));
                int hits = 0;
                for (int ring = 1; ring <= radius; ring++) {
                    if (BastionArena.inRing(x, z, ring)) {
                        hits++;
                    }
                }
                assertEquals(expected >= 1 && expected <= radius ? 1 : 0, hits, x + "," + z);
            }
        }
    }

    @Test
    void firstClearCoinsArePaidOnceAndCapped() {
        BastionConfig cfg = cfg();
        assertEquals(cfg.raidFirstClearCoins, RaidRules.firstClearCoins(cfg, false));
        assertEquals(0, RaidRules.firstClearCoins(cfg, true));
        cfg.raidFirstClearCoins = 99999;
        assertEquals(5000, RaidRules.firstClearCoins(cfg, false));
    }
}
