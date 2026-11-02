package win.vampyuyu.survival.paragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

class ParagonConfigTest {
    private static ParagonConfig seeded() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.PARAGON), null);
        return ParagonConfig.load(db);
    }

    @Test
    void theShippedSeedIsComplete() {
        ParagonConfig cfg = seeded();
        assertEquals(List.of(), cfg.problems());
        assertEquals(10, cfg.ranks().size());
        for (PlayerClass c : PlayerClass.values()) {
            assertTrue(cfg.ultimate(c).isPresent(), c + " has an ultimate");
        }
        assertTrue(cfg.enabled);
    }

    @Test
    void theFirstClearMakesTheFirstRank() {
        ParagonConfig cfg = seeded();
        assertEquals(1, ParagonRules.rankFor(cfg.ranks(), cfg.astralClearPoints));
    }

    @Test
    void anUltimateIsSlowerThanAMasterCapacity() {
        // Master capacities recharge in 60 to 90 seconds.
        ParagonConfig cfg = seeded();
        for (PlayerClass c : PlayerClass.values()) {
            assertTrue(cfg.ultimate(c).orElseThrow().cooldownSeconds() >= 240, c.name());
        }
    }

    @Test
    void ranksFollowThePoints() {
        List<ParagonConfig.Rank> ranks = seeded().ranks();
        assertEquals(0, ParagonRules.rankFor(ranks, 0));
        assertEquals(1, ParagonRules.rankFor(ranks, 2));
        assertEquals(2, ParagonRules.rankFor(ranks, 3));
        assertEquals(10, ParagonRules.rankFor(ranks, 10_000));
        assertEquals(3, ParagonRules.next(ranks, 2).number());
        assertNull(ParagonRules.next(ranks, 10));
    }

    @Test
    void perksAddUpOverTheReachedRanks() {
        List<ParagonConfig.Rank> ranks = seeded().ranks();
        assertEquals(0, ParagonRules.perk(ranks, 2, "island_members"));
        assertEquals(1, ParagonRules.perk(ranks, 3, "island_members"));
        assertEquals(1, ParagonRules.perk(ranks, 6, "island_members"));
        assertEquals(2, ParagonRules.perk(ranks, 10, "island_members"));
        assertEquals(0, ParagonRules.perk(ranks, 10, "nothing"));
    }

    @Test
    void everyAuraOfTheSeedCanBeDrawn() {
        for (ParagonConfig.Rank r : seeded().ranks()) {
            if (r.kind() == ParagonConfig.Kind.AURA) {
                assertTrue(ParagonConfig.AURAS.contains(r.key()), r.key());
            }
        }
    }

    @Test
    void aBadRowIsSkippedAndReported() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.PARAGON), null);
        db.write(c -> {
            Db.update(c, "UPDATE paragon_rank SET reward_key = 'nope' WHERE rank = 2");
            return null;
        });
        ParagonConfig cfg = ParagonConfig.load(db);
        assertEquals(9, cfg.ranks().size());
        assertTrue(cfg.problems().stream().anyMatch(p -> p.contains("unknown aura")));
    }
}
