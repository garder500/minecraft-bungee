package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class LevelTableTest {
    @Test
    public void formulaGivesAGrowingSmoothCurve() {
        LevelTable table = LevelTable.build(null, 60, 50, 1.2, 2);
        assertEquals(60, table.maxLevel());
        assertEquals(50, table.cost(1), 0);
        for (int level = 2; level <= 60; level++) {
            double ratio = table.cost(level) / table.cost(level - 1);
            assertTrue("level " + level + " ratio " + ratio, ratio > 1.0 && ratio < 1.45);
        }
        // roughly a few thousand xp for level 10 and millions of xp for the last level
        assertTrue(table.xpFor(10) > 1000 && table.xpFor(10) < 2000);
        assertTrue(table.xpFor(60) > 5_000_000);
    }

    @Test
    public void levelBoundariesAreExact() {
        LevelTable table = new LevelTable(new double[] {100, 200, 300});
        assertEquals(0, table.levelOf(0, 3));
        assertEquals(0, table.levelOf(99.9, 3));
        assertEquals(1, table.levelOf(100, 3));
        assertEquals(1, table.levelOf(299.9, 3));
        assertEquals(2, table.levelOf(300, 3));
        assertEquals(3, table.levelOf(600, 3));
        assertEquals(3, table.levelOf(1e12, 3));
        assertEquals(2, table.levelOf(1e12, 2)); // cap below the table length
        assertEquals(600, table.xpFor(3), 0);
        assertEquals(0, table.xpFor(0), 0);
        assertEquals(600, table.xpFor(99), 0);
        assertEquals(0, table.cost(0), 0);
        assertEquals(0, table.cost(4), 0);
    }

    @Test
    public void fractionalLevelShowsProgress() {
        LevelTable table = new LevelTable(new double[] {100, 200});
        assertEquals(0.5, table.fractionalLevel(50, 2), 1e-9);
        assertEquals(1.5, table.fractionalLevel(200, 2), 1e-9);
        assertEquals(2.0, table.fractionalLevel(5000, 2), 1e-9);
    }

    @Test
    public void explicitTableWinsWhenLongEnough() {
        LevelTable table = LevelTable.build(List.of(10.0, 20.0, 30.0), 3, 50, 1.2, 2);
        assertEquals(10, table.cost(1), 0);
        assertEquals(30, table.cost(3), 0);
        LevelTable fallback = LevelTable.build(List.of(10.0), 3, 50, 1.2, 2);
        assertEquals(50, fallback.cost(1), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveCosts() {
        new LevelTable(new double[] {10, 0});
    }

    @Test
    public void significantDigitsRounding() {
        assertEquals(1500, LevelTable.roundSignificant(1493.2, 2), 0);
        assertEquals(50, LevelTable.roundSignificant(50, 2), 0);
        assertEquals(1, LevelTable.roundSignificant(0.2, 2), 0);
    }
}
