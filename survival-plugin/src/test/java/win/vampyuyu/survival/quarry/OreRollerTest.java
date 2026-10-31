package win.vampyuyu.survival.quarry;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class OreRollerTest {
    private static OreRoller roller() {
        return new OreRoller(List.of(
            new OreRoller.Ore("bronzite", "custom:bronzite", new double[] {3, 3, 2, 1}, 120, true),
            new OreRoller.Ore("diamond", "DIAMOND", new double[] {0, 0.5, 0.6, 0.6}, 600, false),
            new OreRoller.Ore("iron_nugget", "IRON_NUGGET", new double[] {10, 10, 8, 6}, 0, false)), 0.05, 3, 6, 300);
    }

    @Test
    public void luckMultipliesAndIsCapped() {
        OreRoller r = roller();
        assertEquals(1.0, r.multiplier(0), 1e-9);
        assertEquals(2.0, r.multiplier(100), 1e-9);
        assertEquals(4.0, r.multiplier(10_000), 1e-9);
        assertEquals(1.0, r.multiplier(-50), 1e-9);
    }

    @Test
    public void tierOneNeverGivesATierTwoOre() {
        OreRoller r = roller();
        Map<String, Integer> counters = new HashMap<>();
        Random random = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            for (OreRoller.Drop drop : r.roll(1, 0, counters, random).drops()) {
                assertNotEquals("diamond", drop.ore());
            }
        }
        assertFalse("no counter for an unavailable ore", counters.containsKey("diamond"));
    }

    @Test
    public void pityGuaranteesTheOreAndResetsTheCounter() {
        OreRoller r = roller();
        Map<String, Integer> counters = new HashMap<>();
        counters.put("bronzite", 119);
        // a random that never succeeds on its own
        Random never = new Random() {
            @Override
            public double nextDouble() {
                return 0.999999;
            }
        };
        OreRoller.Roll roll = r.roll(1, 0, counters, never);
        assertTrue(roll.pity());
        assertEquals("bronzite", roll.drops().get(0).ore());
        assertEquals(0, (int) counters.get("bronzite"));
        assertTrue("next block is not forced", r.roll(1, 0, counters, never).drops().isEmpty());
    }

    @Test
    public void ratesMatchTheTableWithinTolerance() {
        OreRoller r = roller();
        Map<String, Integer> counters = new HashMap<>();
        Random random = new Random(42);
        int blocks = 200_000;
        int bronzite = 0;
        int nuggets = 0;
        for (int i = 0; i < blocks; i++) {
            for (OreRoller.Drop drop : r.roll(1, 0, counters, random).drops()) {
                if (drop.ore().equals("bronzite") && drop.amount() == 1) bronzite++;
                if (drop.ore().equals("iron_nugget")) nuggets++;
            }
        }
        assertEquals(0.03, bronzite / (double) blocks, 0.004);
        // the nugget is only tried after bronzite failed
        assertEquals(0.10 * 0.97, nuggets / (double) blocks, 0.006);
    }

    @Test
    public void aTierFourGeodeCanAddAStarcore() {
        var ores = new java.util.ArrayList<>(roller().ores());
        ores.add(new OreRoller.Ore("starcore", "custom:starcore", new double[] {0, 0, 0, 0}, 0, false));
        OreRoller r = new OreRoller(ores, 100, 3, 6, 300, "starcore", 100, 4); // every block a geode, always a bonus
        assertTrue(r.roll(4, 0, new HashMap<>(), new Random(1)).drops().stream().anyMatch(d -> d.ore().equals("starcore")));
        assertTrue("never below tier IV", r.roll(3, 0, new HashMap<>(), new Random(1)).drops().stream()
            .noneMatch(d -> d.ore().equals("starcore")));
    }

    @Test
    public void geodesGiveSeveralOfTheRarestOre() {
        OreRoller r = new OreRoller(roller().ores(), 100, 3, 6, 300); // every block is a geode
        Map<String, Integer> counters = new HashMap<>();
        OreRoller.Roll roll = r.roll(2, 0, counters, new Random(1));
        assertTrue(roll.geode());
        assertEquals("bronzite", roll.drops().get(0).ore());
        assertTrue(roll.drops().get(0).amount() >= 3 && roll.drops().get(0).amount() <= 6);
    }
}
