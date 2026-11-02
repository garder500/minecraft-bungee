package win.vampyuyu.survival.realm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** The pure rules of the Tempestuous Archipelago: Lightning Rod surges, gusts, moving platforms, propulsion pads. */
public class StormMathTest {
    @Test
    public void surgeIsDeterministicAndOnlyUsesTheDeclaredFactors() {
        Set<Double> seen = new HashSet<>();
        for (long second = 0; second < 5000; second++) {
            double a = StormMath.surge(StormMath.seedOf(3, 64, -9), second);
            assertEquals(a, StormMath.surge(StormMath.seedOf(3, 64, -9), second), 0);
            seen.add(a);
        }
        Set<Double> allowed = new HashSet<>();
        for (double f : StormMath.SURGE_FACTOR) {
            allowed.add(f);
        }
        assertEquals("every factor shows up, none other", allowed, seen);
    }

    @Test
    public void surgeAveragesItsMeanAndTwoRodsDoNotBeatTogether() {
        double sum = 0;
        int differ = 0;
        int n = 100_000;
        for (long second = 0; second < n; second++) {
            double a = StormMath.surge(StormMath.seedOf(1, 2, 3), second);
            sum += a;
            if (a != StormMath.surge(StormMath.seedOf(4, 5, 6), second)) {
                differ++;
            }
        }
        assertEquals(StormMath.SURGE_MEAN, sum / n, 0.03);
        assertEquals(1.275, StormMath.SURGE_MEAN, 1e-9);
        assertTrue("two rods are not in lockstep", differ > n / 4);
    }

    @Test
    public void theSharesAddUpToOneAndSomeSecondsAreDead() {
        double total = 0;
        for (double share : StormMath.SURGE_SHARE) {
            total += share;
        }
        assertEquals(1.0, total, 1e-9);
        assertEquals("the rod is unstable: there are dead seconds", 0.0, StormMath.SURGE_FACTOR[0], 0);
        assertTrue("and very strong ones", StormMath.SURGE_FACTOR[StormMath.SURGE_FACTOR.length - 1] >= 4);
    }

    @Test
    public void aGustIsAnnouncedThreeSecondsAheadAndBlowsTwo() {
        int calm = 0;
        int warning = 0;
        int blowing = 0;
        StormMath.Gust previous = null;
        for (long t = 0; t < StormMath.GUST_CYCLE_TICKS; t++) {
            StormMath.Gust gust = StormMath.gust(t);
            switch (gust) {
                case CALM -> calm++;
                case WARNING -> warning++;
                case BLOWING -> blowing++;
            }
            assertTrue("the warning always comes before the blow", previous != StormMath.Gust.CALM || gust != StormMath.Gust.BLOWING);
            previous = gust;
        }
        assertEquals(60, warning);
        assertEquals(40, blowing);
        assertEquals(StormMath.GUST_CYCLE_TICKS - 100, calm);
        assertEquals(StormMath.gust(10), StormMath.gust(10 + StormMath.GUST_CYCLE_TICKS * 7));
    }

    @Test
    public void aGustHasAUnitDirectionPerCycleAndNeverPushesFasterThanTheCap() {
        double[] a = StormMath.gustDirection(500);
        double[] b = StormMath.gustDirection(530);
        assertEquals(1.0, Math.hypot(a[0], a[1]), 1e-9);
        assertEquals("a cycle blows one way", a[0], b[0], 0);
        double vx = 0;
        double vz = 0;
        for (int i = 0; i < 200; i++) {
            double[] v = StormMath.pushed(vx, vz, a);
            vx = v[0];
            vz = v[1];
            assertTrue(Math.hypot(vx, vz) <= StormMath.GUST_MAX_SPEED + 1e-9);
        }
        assertEquals(StormMath.GUST_MAX_SPEED, Math.hypot(vx, vz), 1e-9);
    }

    // ---------------------------------------------------------------- platforms

    @Test
    public void aPlatformWaitsAtBothEndsAndNeverJumpsMoreThanOneBlockPerTick() {
        int steps = 22;
        int dwell = StormPlan.PLATFORM_DWELL_TICKS;
        int previous = PlatformMath.index(0, steps, 4, dwell);
        assertEquals(0, previous);
        int atStart = 0;
        int atEnd = 0;
        for (long t = 0; t < 4000; t++) {
            int index = PlatformMath.index(t, steps, 4, dwell);
            assertTrue("index " + index + " at tick " + t, index >= 0 && index <= steps);
            assertTrue("tick " + t, Math.abs(index - previous) <= 1);
            previous = index;
            if (index == 0) {
                atStart++;
            } else if (index == steps) {
                atEnd++;
            }
        }
        assertTrue("it waits at the start", atStart >= 2 * dwell);
        assertTrue("and at the end", atEnd >= 2 * dwell);
        assertEquals("the same time always gives the same place", PlatformMath.index(777, steps, 4, dwell),
            PlatformMath.index(777, steps, 4, dwell));
    }

    @Test
    public void aTrackWalksFromOneEndToTheOtherInUnitSteps() {
        PlatformMath.Track track = new PlatformMath.Track(10, 2, -4, 40, 8, 6);
        assertEquals(30, track.steps());
        assertEquals(java.util.List.of(10, 2, -4), java.util.Arrays.stream(track.at(0)).boxed().toList());
        assertEquals(java.util.List.of(40, 8, 6), java.util.Arrays.stream(track.at(30)).boxed().toList());
        int[] last = track.at(0);
        for (int i = 1; i <= 30; i++) {
            int[] now = track.at(i);
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(Math.abs(now[axis] - last[axis]) <= 1);
            }
            last = now;
        }
    }

    @Test
    public void theFootprintHoldsItsFiveByFiveBlocks() {
        assertTrue(PlatformMath.onFootprint(10, 10, 2, 8.5, 12.5));
        assertTrue(PlatformMath.onFootprint(10, 10, 2, 12.9, 8.0));
        assertTrue(!PlatformMath.onFootprint(10, 10, 2, 13.6, 10.5));
        assertTrue(!PlatformMath.onFootprint(10, 10, 2, 10.5, 7.5));
    }

    // ---------------------------------------------------------------- pads

    @Test
    public void aLaunchLandsWhereItIsAimedWithinTheSpeedCaps() {
        double[][] cases = {{16, 0, 0}, {14, 5, -8}, {12, -6, 9}, {0, 4, 17}, {-15, 2, -3}};
        for (double[] c : cases) {
            PadMath.Launch launch = PadMath.launch(c[0], c[1], c[2]);
            assertTrue(java.util.Arrays.toString(c), launch != null);
            double[] end = PadMath.landing(launch);
            assertEquals(c[0], end[0], 0.01);
            assertEquals(c[1], end[1], 0.01);
            assertEquals(c[2], end[2], 0.01);
            assertTrue(Math.hypot(launch.vx(), launch.vz()) <= PadMath.MAX_HORIZONTAL + 1e-9);
            assertTrue(launch.vy() <= PadMath.MAX_VERTICAL + 1e-9);
            assertTrue(launch.ticks() >= PadMath.MIN_TICKS && launch.ticks() <= PadMath.MAX_TICKS);
        }
    }

    @Test
    public void aTargetOutOfReachGivesNoLaunch() {
        assertEquals(null, PadMath.launch(80, 0, 0));
        assertEquals(null, PadMath.launch(10, 60, 0));
    }
}
