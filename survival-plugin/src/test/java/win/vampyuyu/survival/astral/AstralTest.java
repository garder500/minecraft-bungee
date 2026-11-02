package win.vampyuyu.survival.astral;

import static org.junit.Assert.*;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.island.BlockPlan;

/** The Astral Interiority: layout, movement and pull rules, access, quest progress and the block plan. */
public class AstralTest {
    @Before
    public void setUp() {
        MockBukkit.mock();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- access

    @Test
    public void theInteriorityOpensOnlyOnceEveryGuardianIsDefeated() {
        assertEquals(List.of(Realm.values()), AstralMath.missing(EnumSet.noneOf(Realm.class)));
        Set<Realm> seven = EnumSet.allOf(Realm.class);
        seven.remove(Realm.NECROPOLIS);
        assertEquals(List.of(Realm.NECROPOLIS), AstralMath.missing(seven));
        assertTrue(AstralMath.missing(EnumSet.allOf(Realm.class)).isEmpty());
    }

    // ---------------------------------------------------------------- the pull

    @Test
    public void thePullGrowsTowardTheCentreAndStopsOutOfReach() {
        double max = AstralMath.DEFAULT_PULL_MAX;
        assertEquals(0.0, AstralMath.pull(AstralLayout.PULL_RADIUS, false, false, max), 0.0);
        assertEquals(0.0, AstralMath.pull(AstralLayout.PULL_RADIUS + 10, true, false, max), 0.0);
        assertTrue(AstralMath.pull(30, false, false, max) < AstralMath.pull(20, false, false, max));
        assertTrue(AstralMath.pull(20, true, false, max) > AstralMath.pull(20, false, false, max));
    }

    @Test
    public void crouchingHalvesThePullAndNothingExceedsTheCeiling() {
        double max = AstralMath.DEFAULT_PULL_MAX;
        assertEquals(AstralMath.pull(20, false, false, max) / 2, AstralMath.pull(20, false, true, max), 1.0e-9);
        for (double dist = AstralLayout.HORIZON; dist < AstralLayout.PULL_RADIUS; dist += 0.5) {
            assertTrue(AstralMath.pull(dist, true, false, 99.0) <= AstralMath.PULL_CEILING);
        }
    }

    @Test
    public void aSurgeIsAlwaysAnnouncedAndTheCounterExists() {
        boolean warned = false;
        for (long t = 0; t < AstralMath.SURGE_PERIOD; t++) {
            if (AstralMath.surging(t)) {
                assertFalse(AstralMath.warning(t));
            }
            if (AstralMath.warning(t)) {
                warned = true;
            }
        }
        assertTrue(warned);
        // the tick before the first surge is a warning tick
        long start = AstralMath.SURGE_PERIOD - AstralMath.SURGE_LENGTH;
        assertTrue(AstralMath.warning(start - 1));
        assertFalse(AstralMath.surging(start - 1));
        assertTrue(AstralMath.surging(start));
        assertTrue(AstralMath.SURGE_WARNING >= 20);
    }

    @Test
    public void aLitAnchorShelters() {
        AstralLayout.Anchor anchor = AstralLayout.ANCHORS.get(1);
        assertNull(AstralMath.shelter(anchor.x(), anchor.y(), anchor.z(), id -> false));
        assertEquals(anchor, AstralMath.shelter(anchor.x() + 2, anchor.y(), anchor.z(), id -> id.equals(anchor.id())));
        assertNull(AstralMath.shelter(anchor.x() + AstralLayout.ANCHOR_SHELTER + 2, anchor.y(), anchor.z(), id -> true));
    }

    @Test
    public void aLostPlayerComesBackToTheLastLitAnchorOrTheNexus() {
        int[] none = AstralMath.checkpoint(id -> false);
        assertEquals(AstralLayout.NEXUS[0], none[0]);
        AstralLayout.Anchor last = AstralLayout.ANCHORS.get(3);
        Set<String> lit = new HashSet<>();
        for (int i = 0; i <= 3; i++) {
            lit.add(AstralLayout.ANCHORS.get(i).id());
        }
        assertEquals(last.x(), AstralMath.checkpoint(lit::contains)[0]);
    }

    @Test
    public void everyAnchorStandsOnTheCausewayOutsideTheHorizon() {
        for (AstralLayout.Anchor anchor : AstralLayout.ANCHORS) {
            double d = AstralLayout.horizonDistance(anchor.x(), anchor.z());
            assertTrue(anchor.id(), d > AstralLayout.HORIZON + 5);
            assertTrue(anchor.id(), d < AstralLayout.PULL_RADIUS);
            assertTrue(anchor.id(), anchor.z() <= 0);
        }
        // the unsheltered stretches between two Anchors are short enough to cross in a surge
        for (int i = 1; i < AstralLayout.ANCHORS.size(); i++) {
            AstralLayout.Anchor a = AstralLayout.ANCHORS.get(i - 1);
            AstralLayout.Anchor b = AstralLayout.ANCHORS.get(i);
            double gap = Math.hypot(a.x() - b.x(), a.z() - b.z()) - 2 * AstralLayout.ANCHOR_SHELTER;
            assertTrue("gap " + i + " " + gap, gap <= 10);
        }
    }

    // ---------------------------------------------------------------- the synapse lifts

    @Test
    public void everyLiftIsAShortGuidedClimbThatMissesTheTrunk() {
        assertEquals(AstralLayout.PLATFORMS + 1, AstralLayout.SYNAPSES.size());
        for (AstralLayout.Synapse s : AstralLayout.SYNAPSES) {
            double len = Math.sqrt(Math.pow(s.toX() - s.x(), 2) + Math.pow(s.toY() - s.y(), 2) + Math.pow(s.toZ() - s.z(), 2));
            assertTrue(s.id() + " takes too long", len / AstralMath.LIFT_SPEED < AstralMath.LIFT_MAX_TICKS - 10);
            assertTrue(s.id(), s.toY() > s.y());
            assertTrue(s.id() + " cuts through the trunk",
                segmentDistance(s.x(), s.z(), s.toX(), s.toZ(), AstralLayout.TREE[0], AstralLayout.TREE[2]) > AstralLayout.TRUNK_RADIUS + 1.5);
            // the lift lands away from the next pad, so the player chooses when to go on
            int next = AstralLayout.SYNAPSES.indexOf(s) + 1;
            if (next < AstralLayout.SYNAPSES.size()) {
                AstralLayout.Synapse n = AstralLayout.SYNAPSES.get(next);
                assertTrue(Math.hypot(n.x() - s.toX(), n.z() - s.toZ()) > AstralLayout.SYNAPSE_TRIGGER + 1.5);
            }
        }
    }

    private static double segmentDistance(double x0, double z0, double x1, double z1, double px, double pz) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double t = Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / (dx * dx + dz * dz)));
        return Math.hypot(x0 + t * dx - px, z0 + t * dz - pz);
    }

    @Test
    public void theLiftStepIsBoundedAndEnds() {
        double[] v = AstralMath.lift(new double[] {0, 0, 0}, new double[] {30, 40, 0}, AstralMath.LIFT_SPEED);
        assertEquals(AstralMath.LIFT_SPEED, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1.0e-9);
        double[] near = AstralMath.lift(new double[] {0, 0, 0}, new double[] {0.1, 0, 0}, AstralMath.LIFT_SPEED);
        assertEquals(0.1, near[0], 1.0e-9);
        assertTrue(AstralMath.arrived(new double[] {0, 0, 0}, new double[] {0.8, 0.3, 0}));
        assertFalse(AstralMath.arrived(new double[] {0, 0, 0}, new double[] {5, 0, 0}));
        AstralLayout.Synapse first = AstralLayout.SYNAPSES.get(0);
        assertEquals(first, AstralMath.synapseAt(first.x() + 0.5, first.y(), first.z() + 0.5));
        assertNull(AstralMath.synapseAt(first.x() + 8, first.y(), first.z()));
    }

    // ---------------------------------------------------------------- progress

    @Test
    public void everyRewardIsPaidOnce() {
        AstralProgress progress = new AstralProgress();
        UUID id = UUID.randomUUID();
        assertEquals(AstralProgress.DISCOVER_COINS, progress.discover(id, "library"));
        assertEquals(-1, progress.discover(id, "library"));
        assertEquals(AstralProgress.SYNAPSE_COINS, progress.fire(id, "synapse:0"));
        assertEquals(-1, progress.fire(id, "synapse:0"));
        assertEquals(AstralProgress.ANCHOR_COINS, progress.light(id, "anchor:0"));
        assertEquals(-1, progress.light(id, "anchor:0"));
    }

    @Test
    public void finishingAQuestLineAddsItsBonusOnce() {
        AstralProgress progress = new AstralProgress();
        UUID id = UUID.randomUUID();
        int explore = 0;
        for (AstralLayout.Landmark mark : AstralLayout.LANDMARKS) {
            explore += progress.discover(id, mark.id());
        }
        assertEquals(AstralProgress.DISCOVER_COINS * AstralLayout.LANDMARKS.size() + AstralProgress.EXPLORE_BONUS, explore);
        assertTrue(progress.exploreDone(id));
        int climb = 0;
        for (AstralLayout.Synapse s : AstralLayout.SYNAPSES) {
            climb += progress.fire(id, s.id());
        }
        assertEquals(AstralProgress.SYNAPSE_COINS * AstralLayout.SYNAPSES.size() + AstralProgress.CLIMB_BONUS, climb);
        assertTrue(progress.climbDone(id));
    }

    @Test
    public void theThroneNeedsTheCrownAndEveryAnchorAndMakesASurvivorOnce() {
        AstralProgress progress = new AstralProgress();
        UUID id = UUID.randomUUID();
        assertFalse(progress.survived(id));
        assertEquals(-1, progress.survive(id));
        for (AstralLayout.Anchor anchor : AstralLayout.ANCHORS) {
            progress.light(id, anchor.id());
        }
        assertEquals("the Crown is also needed", -1, progress.survive(id));
        progress.discover(id, "crown");
        assertTrue(progress.readyForThrone(id));
        assertEquals(AstralProgress.SURVIVE_COINS, progress.survive(id));
        assertTrue(progress.survived(id));
        assertEquals(-1, progress.survive(id));
        progress.reset(id);
        assertFalse(progress.survived(id));
    }

    @Test
    public void progressSurvivesASaveAndALoad() {
        AstralProgress progress = new AstralProgress();
        UUID id = UUID.randomUUID();
        progress.discover(id, "crown");
        progress.light(id, "anchor:2");
        AstralProgress back = AstralProgress.fromDoc(progress.toDoc());
        assertTrue(back.discovered(id, "crown"));
        assertTrue(back.lit(id, "anchor:2"));
        assertFalse(back.lit(id, "anchor:1"));
        assertEquals(0, AstralProgress.fromDoc(null).of(id).discovered.size());
    }

    // ---------------------------------------------------------------- layout and plan

    @Test
    public void landmarksAreUniqueAndAllHaveALore() {
        Set<String> ids = new HashSet<>();
        for (AstralLayout.Landmark mark : AstralLayout.LANDMARKS) {
            assertTrue(mark.id(), ids.add(mark.id()));
            assertFalse(mark.lore().isBlank());
        }
        assertTrue(ids.contains("crown"));
        assertTrue(ids.contains("dais"));
    }

    @Test
    public void thePlanBuildsEveryPlaceWithinTheCap() {
        BlockPlan plan = AstralPlan.build();
        assertTrue("plan " + plan.size(), plan.size() < AstralLayout.CAP);
        assertEquals(Material.GILDED_BLACKSTONE, plan.get(0, 0, 0));
        assertNotNull(plan.anchor("spawn"));
        for (AstralLayout.Anchor anchor : AstralLayout.ANCHORS) {
            assertEquals(anchor.id(), Material.LODESTONE, plan.get(anchor.x(), anchor.y(), anchor.z()));
            assertNotNull(plan.get(anchor.x(), anchor.y() - 1, anchor.z()));
        }
        for (AstralLayout.Synapse s : AstralLayout.SYNAPSES) {
            assertEquals(s.id(), Material.SEA_LANTERN, plan.get(s.x(), s.y() - 1, s.z()));
            assertNotNull(s.id() + " target has a floor", plan.get(s.toX(), s.toY() - 1, s.toZ()));
        }
        for (int i = 1; i <= AstralLayout.PLATFORMS; i++) {
            int[] p = AstralLayout.platform(i);
            assertNotNull("platform " + i, plan.get(p[0], p[1], p[2]));
        }
        assertEquals(Material.QUARTZ_STAIRS, plan.get(AstralLayout.THRONE[0], AstralLayout.THRONE[1] + 1, AstralLayout.THRONE[2]));
        assertEquals(Material.BLACK_CONCRETE, plan.get(AstralLayout.SINGULARITY[0], AstralLayout.SINGULARITY[1], AstralLayout.SINGULARITY[2]));
        for (AstralLayout.Landmark mark : AstralLayout.LANDMARKS) {
            assertNotNull(mark.id(), plan.anchor("landmark:" + mark.id()));
        }
    }
}
