package win.vampyuyu.survival.boss.slayer;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import win.vampyuyu.survival.api.Stat;

public class SlayerMathTest {
    private static final List<Long> XP = List.of(5L, 15L, 200L, 1000L, 5000L, 20000L, 100000L, 400000L, 1000000L);

    @Test
    public void levels() {
        assertEquals(0, SlayerMath.level(0, XP));
        assertEquals(0, SlayerMath.level(4, XP));
        assertEquals(1, SlayerMath.level(5, XP));
        assertEquals(3, SlayerMath.level(999, XP));
        assertEquals(9, SlayerMath.level(1_000_000, XP));
        assertEquals(9, SlayerMath.level(Long.MAX_VALUE, XP));
    }

    @Test
    public void toNextAndProgress() {
        assertEquals(5, SlayerMath.toNext(0, XP));
        assertEquals(10, SlayerMath.toNext(5, XP));
        assertEquals(0, SlayerMath.toNext(2_000_000, XP));
        assertEquals(0.5, SlayerMath.levelProgress(10, XP), 1e-9);
        assertEquals(1.0, SlayerMath.levelProgress(2_000_000, XP), 0);
        assertEquals(0.0, SlayerMath.levelProgress(0, XP), 0);
    }

    @Test
    public void perksAccumulateByLevel() {
        List<SlayerDefinition.Perk> perks = List.of(new SlayerDefinition.Perk(1, Stat.HEALTH, 2),
            new SlayerDefinition.Perk(2, Stat.HEALTH, 3), new SlayerDefinition.Perk(3, Stat.DEFENSE, 1));
        assertEquals(Map.of(), SlayerMath.perkTotals(perks, 0));
        assertEquals(5.0, SlayerMath.perkTotals(perks, 2).get(Stat.HEALTH), 0);
        assertNull(SlayerMath.perkTotals(perks, 2).get(Stat.DEFENSE));
        assertEquals(1.0, SlayerMath.perkTotals(perks, 9).get(Stat.DEFENSE), 0);
    }

    @Test
    public void progressIsCappedAndIgnoresBadInput() {
        assertEquals(10, SlayerMath.addProgress(8, 10, 5), 0);
        assertEquals(8, SlayerMath.addProgress(8, 10, -3), 0);
        assertEquals(8, SlayerMath.addProgress(8, 10, Double.NaN), 0);
        assertEquals(9, SlayerMath.addProgress(8, 10, 1), 0);
    }

    @Test
    public void barHasFixedWidth() {
        assertEquals("||||......", SlayerMath.bar(0.4, 10));
        assertEquals("..........", SlayerMath.bar(-2, 10));
        assertEquals("||||||||||", SlayerMath.bar(7, 10));
    }

    @Test
    public void romanAndIds() {
        assertEquals("V", SlayerDefinition.roman(5));
        assertEquals("slayer_zombie_3", SlayerDefinition.bossId("zombie", 3));
    }
}
