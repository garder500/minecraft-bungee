package win.vampyuyu.survival.breath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.breath.BreathEvent.Type;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The Celestial Breath: parsing, the draw, the factors, the banner, the fragments and the shipped seed. */
public class BreathRulesTest {
    private static final String FROZEN = "frozen_breath|Frozen Breath|3|45|yield:stasis_crystal:30;machine:20;fracture:necropolis:3;invade:hub";

    private static void rejects(String row) {
        try {
            BreathRules.parse(row);
            fail("should be refused: " + row);
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void theExampleOfTheGddParsesAndIsAChoice() {
        BreathEvent e = BreathRules.parse(FROZEN);
        assertEquals(4, e.effects().size());
        assertTrue(e.isChoice());
        assertTrue(e.has(Type.FRACTURE));
        assertTrue(e.has(Type.INVADE));
        assertEquals(1.3, BreathRules.factor(e, Type.YIELD, "stasis_crystal"), 1e-9);
        assertEquals(1.0, BreathRules.factor(e, Type.YIELD, "cinderheart"), 1e-9);
        assertEquals(1.2, BreathRules.factor(e, Type.MACHINE, "*"), 1e-9);
        assertEquals(1.0, BreathRules.factor(e, Type.MONSTER, "*"), 1e-9);
        assertEquals(1.0, BreathRules.factor(null, Type.MACHINE, "*"), 1e-9);
    }

    @Test
    public void theBannerListsEveryEffect() {
        List<String> lines = BreathRules.banner(BreathRules.parse(FROZEN));
        assertEquals("Stasis Crystal yield +30 %", lines.get(0));
        assertEquals("Machine Flux consumption +20 %", lines.get(1));
        assertEquals("A Fracture has opened in the Ethereal Necropolis (/breath fracture)", lines.get(2));
        assertEquals("Stellar immune cells invade the Hub", lines.get(3));
    }

    @Test
    public void badRowsAreRefused() {
        rejects("x|T|1|10");
        rejects("Bad|T|1|10|machine:-10;monster:10");
        rejects("a|T|0|10|machine:-10;monster:10");
        rejects("a|T|1|10|machine:ten");
        rejects("a|T|1|10|route:nowhere;machine:-10");
        rejects("a|T|1|10|fracture:ashes:0;route:grove");
        rejects("a|T|1|10|yield:*:900;route:grove");
        rejects("a|T|1|10|teleport:ashes");
        rejects("a|T|1|10|machine:-10");           // a gift with no cost is no choice
        rejects("a|T|1|10|route:grove;monster:50"); // a cost with no gain is no choice
    }

    @Test
    public void theDrawIsWeightedAndNeverRepeats() {
        BreathEvent a = BreathRules.parse("a|A|1|10|machine:-10;monster:10");
        BreathEvent b = BreathRules.parse("b|B|9|10|machine:-10;monster:10");
        Random rng = new Random(7);
        int bs = 0;
        for (int i = 0; i < 2000; i++) {
            if (BreathRules.pick(List.of(a, b), "", rng) == b) {
                bs++;
            }
        }
        assertTrue("weight 9 against 1 wins about nine times out of ten: " + bs, bs > 1650 && bs < 1950);
        for (int i = 0; i < 50; i++) {
            assertEquals(a, BreathRules.pick(List.of(a, b), "b", rng));
        }
        assertEquals("a lone event repeats", a, BreathRules.pick(List.of(a), "a", rng));
        assertNull(BreathRules.pick(List.of(), "", rng));
    }

    @Test
    public void aFragmentIsOnePerPulsationAndKeysAssemble() {
        assertTrue(BreathRules.mayFindFragment(0, 1));
        assertFalse(BreathRules.mayFindFragment(4, 4));
        assertTrue(BreathRules.mayFindFragment(4, 5));
        assertFalse("before the first pulsation nothing falls", BreathRules.mayFindFragment(0, 0));
        assertEquals(1, BreathRules.assemble(5, 5)[0]);
        assertEquals(0, BreathRules.assemble(5, 5)[1]);
        assertEquals(0, BreathRules.assemble(4, 5)[0]);
        assertEquals(2, BreathRules.assemble(12, 5)[1]);
    }

    @Test
    public void theShippedSeedLoadsWithoutAProblem() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, List.of(ContentDomains.BREATH), null);
            BreathConfig cfg = BreathConfig.load(db);
            assertEquals(List.of(), cfg.problems());
            assertEquals(10, cfg.events().size());
            assertTrue(cfg.events().values().stream().allMatch(BreathEvent::isChoice));
            assertTrue("the three kinds of GDD 19.2 exist", cfg.events().values().stream().anyMatch(e -> e.has(Type.FRACTURE))
                && cfg.events().values().stream().anyMatch(e -> e.has(Type.INVADE))
                && cfg.events().values().stream().anyMatch(e -> e.has(Type.MACHINE) || e.has(Type.MONSTER) || e.has(Type.ROUTE)));
            Set<String> floorOk = new HashSet<>();
            for (BreathEvent e : cfg.events().values()) {
                e.effects().stream().filter(x -> x.type() == Type.FRACTURE).forEach(x -> floorOk.add(x.realm().id() + x.floor()));
            }
            assertFalse(floorOk.isEmpty());
            assertEquals(5, cfg.fragmentsPerKey());
            for (Realm r : Realm.values()) {
                assertNotNull(BreathConfig.worldOf(r));
            }
            assertEquals("sb_hub", BreathConfig.worldOfTarget("hub"));
        }
    }
}
