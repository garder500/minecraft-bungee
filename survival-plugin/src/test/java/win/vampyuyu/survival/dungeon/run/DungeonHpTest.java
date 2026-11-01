package win.vampyuyu.survival.dungeon.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.items.combat.PowerCurve;

/** Dungeon monster health follows the power curve, and the virtual pool turns real damage into the entity's mirror. */
public class DungeonHpTest {
    private static final PowerCurve CURVE = new PowerCurve(PowerCurve.Rules.DEFAULT,
        new PowerCurve.Tier(1, 40, 20, Element.EARTH), new PowerCurve.Tier(2, 70, 28, Element.FROST));

    @Test
    public void aBasicMonsterDiesInTheTargetSecondsToTheTierSword() {
        double hp = DungeonHp.health(CURVE, 1, 2.5, 40, 40, 1.0, 1.0);
        // the tier 1 sword and armor deal 40 x 1.2 x 1.2 x 1.6 = 92.16 a second against a neutral target
        assertEquals(2.5 * 92.16, hp, 1.0);
        assertEquals(2.5, CURVE.secondsToKill(hp, 40, 20, 1.0), 0.05);
    }

    @Test
    public void toughMonstersFloorsAndPartiesScaleOnTop() {
        double basic = DungeonHp.health(CURVE, 1, 2.5, 40, 40, 1.0, 1.0);
        assertEquals(basic * 10, DungeonHp.health(CURVE, 1, 2.5, 40, 400, 1.0, 1.0), 2);
        assertEquals(basic * 1.3, DungeonHp.health(CURVE, 1, 2.5, 40, 40, 1.3, 1.0), 1);
        assertEquals(basic * 1.6, DungeonHp.health(CURVE, 1, 2.5, 40, 40, 1.0, 1.6), 1);
        assertTrue("a higher tier floor has more health", DungeonHp.health(CURVE, 2, 2.5, 40, 40, 1.0, 1.0) > basic * 1.5);
    }

    @Test
    public void withoutTheTierTheOldRuleApplies() {
        assertEquals(40 * 2.4, DungeonHp.health(CURVE, 4, 2.5, 40, 40, 2.4, 1.0), 1e-9);
        assertEquals(40 * 2.4, DungeonHp.health(null, 1, 2.5, 40, 40, 2.4, 1.0), 1e-9);
    }

    @Test
    public void thePoolMirrorsThePercentageOnTheEntity() {
        // 4000 in the pool, 100 on the entity: losing 400 of the pool is 10 of its health
        assertEquals(10.0, VirtualHp.mirrored(3600, 4000, 100, 100), 1e-9);
        // already at 60: another 400 takes it from 60 to 50
        assertEquals(10.0, VirtualHp.mirrored(2000, 4000, 100, 60), 1e-9);
        // an empty pool is lethal whatever the entity has left
        assertTrue(VirtualHp.mirrored(0, 4000, 100, 37) >= 37 + 100);
    }
}
