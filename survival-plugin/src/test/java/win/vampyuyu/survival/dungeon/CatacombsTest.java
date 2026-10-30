package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import org.junit.Test;

public class CatacombsTest {
    private static final DungeonConfig CFG = DungeonConfig.parse(null);

    @Test
    public void levelsFollowTheQuadraticCurve() {
        assertEquals(0, Catacombs.level(CFG, 0));
        assertEquals(0, Catacombs.level(CFG, 399));
        assertEquals(1, Catacombs.level(CFG, 400));
        assertEquals(1, Catacombs.level(CFG, 1599));
        assertEquals(2, Catacombs.level(CFG, 1600));
        assertEquals(10, Catacombs.level(CFG, 40_000));
        assertEquals(0, Catacombs.level(CFG, -50));
    }

    @Test
    public void levelIsCappedAndNeverOverflows() {
        assertEquals(CFG.catacombsMaxLevel, Catacombs.level(CFG, Long.MAX_VALUE / 2));
        assertEquals(CFG.catacombsMaxLevel, Catacombs.level(CFG, Catacombs.xpForLevel(CFG, CFG.catacombsMaxLevel)));
        assertEquals(CFG.catacombsMaxLevel - 1, Catacombs.level(CFG, Catacombs.xpForLevel(CFG, CFG.catacombsMaxLevel) - 1));
    }

    @Test
    public void levelAndThresholdAgreeAtEveryLevel() {
        for (int n = 1; n <= CFG.catacombsMaxLevel; n++) {
            long need = Catacombs.xpForLevel(CFG, n);
            assertEquals(n, Catacombs.level(CFG, need));
            assertEquals(n - 1, Catacombs.level(CFG, need - 1));
        }
    }

    @Test
    public void gainIsAShareOfTheCombatXpAndBonusIsLinear() {
        assertEquals(325, Catacombs.gain(CFG, 650));
        assertEquals(0, Catacombs.gain(CFG, -5));
        assertEquals(0.0, Catacombs.bonusPercent(CFG, 0), 0.0);
        assertEquals(10.0, Catacombs.bonusPercent(CFG, 10), 0.0);
        assertEquals(0.0, Catacombs.bonusPercent(CFG, -3), 0.0);
    }
}
