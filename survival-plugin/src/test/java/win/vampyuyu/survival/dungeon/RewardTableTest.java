package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class RewardTableTest {
    private static final DungeonConfig CFG = DungeonConfig.parse(null);

    @Test
    public void eligibilityFollowsFloorAndGrade() {
        var low = RewardTable.eligible(CFG, 1, Grade.D);
        assertTrue(low.stream().allMatch(e -> e.minFloor() <= 1 && e.minGrade() == Grade.D));
        assertTrue(low.stream().noneMatch(e -> e.item().equals("dungeon_dark_scroll")));
        var top = RewardTable.eligible(CFG, 7, Grade.S_PLUS);
        assertTrue(top.stream().anyMatch(e -> e.item().equals("dungeon_dark_scroll")));
        assertTrue(top.size() > low.size());
        // grade gates
        assertTrue(RewardTable.eligible(CFG, 7, Grade.C).stream().noneMatch(e -> e.item().equals("dungeon_relic")));
    }

    @Test
    public void rollsHonourTheTierAndRanges() {
        for (Grade g : Grade.values()) {
            int rolls = CFG.tiers.get(g).rolls();
            for (long seed = 0; seed < 100; seed++) {
                List<RewardTable.Drop> drops = RewardTable.roll(CFG, 7, g, new Random(seed));
                assertFalse(drops.isEmpty());
                assertTrue(drops.size() <= rolls);
                for (RewardTable.Drop d : drops) {
                    assertTrue(d.amount() >= 1);
                    var entry = CFG.rewardItems.stream().filter(e -> e.item().equals(d.item())).findFirst().orElseThrow();
                    assertTrue(d.amount() >= entry.min());
                    assertTrue("merged amounts stay within rolls * max", d.amount() <= entry.max() * rolls);
                    assertTrue(g.atLeast(entry.minGrade()));
                }
            }
        }
    }

    @Test
    public void rollsAreDeterministicPerSeed() {
        assertEquals(RewardTable.roll(CFG, 5, Grade.A, new Random(8)), RewardTable.roll(CFG, 5, Grade.A, new Random(8)));
    }

    @Test
    public void betterGradesPayMore() {
        DungeonConfig.Floor f = CFG.floor(3);
        double previous = -1;
        for (Grade g : Grade.values()) {
            double c = RewardTable.coins(CFG, f, g);
            assertTrue(c > previous);
            previous = c;
            assertTrue(RewardTable.xp(CFG, f, g) > 0);
        }
        assertEquals(Math.floor(400 * f.reward()), RewardTable.coins(CFG, f, Grade.D), 0.001);
        assertTrue(RewardTable.coins(CFG, CFG.floor(7), Grade.S) > RewardTable.coins(CFG, CFG.floor(1), Grade.S));
    }

    @Test
    public void weightsAreRespected() {
        int bones = 0;
        int scrolls = 0;
        Random rng = new Random(1);
        for (int i = 0; i < 4000; i++) {
            for (var d : RewardTable.roll(CFG, 7, Grade.S_PLUS, rng)) {
                if (d.item().equals("dungeon_bone_fragment")) {
                    bones++;
                } else if (d.item().equals("dungeon_dark_scroll")) {
                    scrolls++;
                }
            }
        }
        assertTrue(bones > scrolls * 10);
        assertTrue(scrolls > 0);
    }
}
