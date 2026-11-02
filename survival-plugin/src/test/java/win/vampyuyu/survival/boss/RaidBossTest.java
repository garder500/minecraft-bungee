package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.util.Vector;

/** The Devourer (GDD 4.6, 8.7, 12.5): the shipped boss rows and the movement rules of its abilities. */
public class RaidBossTest {
    @Before
    public void setUp() {
        MockBukkit.mock();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private static BossDefinition devourer() {
        return BossTestData.shipped().bosses().get("devourer");
    }

    @Test
    public void theShippedConfigHasNoErrorWithTheDevourer() {
        assertEquals(List.of(), BossTestData.shipped().errors());
    }

    @Test
    public void threePhasesAtTheGddThresholds() {
        BossDefinition def = devourer();
        assertNotNull(def);
        assertEquals(3, def.phases().size());
        assertEquals("Event Horizon", def.phases().get(0).name());
        assertEquals("Metamorphic Assimilation", def.phases().get(1).name());
        assertEquals("Naked Singularity", def.phases().get(2).name());
        assertEquals(1.0, def.phases().get(0).at(), 1e-9);
        assertTrue(def.phases().get(1).at() < 1 && def.phases().get(2).at() < def.phases().get(1).at());
        assertEquals(5, def.tier());
        assertNull("only the Raid spawns it", def.summon());
    }

    @Test
    public void readabilityCapsOfSection87() {
        BossDefinition def = devourer();
        assertEquals("about 52 000 health at tier 5", 52000, def.health(), 1);
        assertEquals("x(1 + 0.5 (p - 1))", 0.5, def.arena().scalePerPlayer(), 1e-9);
        assertEquals("capped at x3", 3.0, def.arena().scaleCap(), 1e-9);
        assertEquals("enrage after 9 minutes", 540, def.arena().enrageSeconds());
        double previous = 1.0;
        for (BossDefinition.Phase phase : def.phases()) {
            assertTrue(phase.name() + " never multiplies damage by more than x1.4", phase.damageMult() <= 1.4 + 1e-9);
            assertTrue("phases only get harder", phase.damageMult() >= previous);
            previous = phase.damageMult();
            for (AbilitySpec a : all(phase)) {
                assertTrue(phase.name() + " " + a.type() + " damage", a.damage() <= 2.0);
                assertTrue(a.type() + " telegraphs", a.telegraphTicks() > 0 || a.type() == AbilityType.CHAIN_ANCHORS);
            }
        }
    }

    @Test
    public void phaseOneBendsTimeAndDistance() {
        BossDefinition.Phase p = devourer().phases().get(0);
        Set<AbilityType> types = all(p).stream().map(AbilitySpec::type).collect(Collectors.toSet());
        assertTrue(types.contains(AbilityType.TIME_ECHO));
        assertTrue(types.contains(AbilityType.VACUUM));
    }

    @Test
    public void phaseTwoWearsTheEightGuardians() {
        BossDefinition.Phase p = devourer().phases().get(1);
        Set<AbilityType> types = all(p).stream().map(AbilitySpec::type).collect(Collectors.toSet());
        assertTrue(types.containsAll(Set.of(AbilityType.FIRE_RING, AbilityType.WEB_TRAPS, AbilityType.BONE_CAGE,
            AbilityType.ICE_SPIKES, AbilityType.TELEPORT_BEHIND, AbilityType.POISON_CLOUD, AbilityType.TIDAL_WAVE,
            AbilityType.LIGHTNING_STRIKES)));
    }

    @Test
    public void phaseThreeIsWeightlessWithACounterToTheSingularity() {
        BossDefinition.Phase p = devourer().phases().get(2);
        assertTrue("gravity fails as soon as the phase starts",
            p.enter().stream().anyMatch(a -> a.type() == AbilityType.WEIGHTLESS));
        for (AbilitySpec a : p.abilities()) {
            if (a.type() == AbilityType.WEIGHTLESS) {
                assertTrue(a.seconds("duration", 0) <= 200);
                assertTrue("capped drift", a.num("current", 0) <= Gravity.DRIFT_CAP);
                assertTrue(a.num("ceiling", 0) <= 12);
            }
            if (a.type() == AbilityType.CHAIN_ANCHORS) {
                assertTrue("a pull can be resisted: anchors break", a.integer("hits", 0) >= 1 && a.integer("seconds", 0) >= 15);
                assertTrue(a.num("strength", 0) <= 0.3);
            }
        }
        assertTrue(p.abilities().stream().anyMatch(a -> a.type() == AbilityType.CHAIN_ANCHORS));
    }

    @Test
    public void rewardsCarryNoStats() {
        BossDefinition def = devourer();
        assertTrue("boss coins stay under the GDD cap of 5 000 with the first-clear bonus", def.loot().coins() + 4000 <= 5000);
        for (BossDefinition.LootEntry e : def.loot().entries()) {
            assertTrue(e.item(), e.item().startsWith("custom:"));
            String id = e.item().substring(7);
            BossItems.Spec spec = BossItems.table().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
            assertTrue(id + " has no stat", spec.stats().isEmpty());
            assertNull(id + " is not a weapon", spec.damage());
        }
        assertTrue(BossItems.tableIds().contains("devourer_sigil"));
    }

    // ---------------------------------------------------------------- movement helpers

    @Test
    public void theFutureMarkIsAheadOfThePlayerAndNeverAbsurd() {
        Vector at = new Vector(10, 64, 0);
        Vector ahead = Gravity.leadPoint(at, new Vector(1, 0.5, 0), 7);
        assertEquals(17, ahead.getX(), 1e-9);
        assertEquals(0, ahead.getZ(), 1e-9);
        assertEquals("flat", 64, ahead.getY(), 1e-9);
        assertEquals("looking straight up: under the feet", at, Gravity.leadPoint(at, new Vector(0, 1, 0), 7));
    }

    @Test
    public void marksStayInsideTheArena() {
        Vector centre = new Vector(0, 100, 0);
        Vector far = Gravity.clampToArena(new Vector(60, 100, 0), centre, 40);
        assertEquals(40, far.getX(), 1e-9);
        assertEquals(new Vector(5, 100, 5), Gravity.clampToArena(new Vector(5, 100, 5), centre, 40));
    }

    @Test
    public void levitationStopsAtTheCeiling() {
        assertTrue(Gravity.mayRise(101, 100, 9));
        assertFalse(Gravity.mayRise(109, 100, 9));
    }

    private static List<AbilitySpec> all(BossDefinition.Phase phase) {
        List<AbilitySpec> out = new java.util.ArrayList<>(phase.enter());
        out.addAll(phase.abilities());
        return out;
    }
}
