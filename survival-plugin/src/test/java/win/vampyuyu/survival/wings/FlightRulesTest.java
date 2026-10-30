package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;
import static win.vampyuyu.survival.wings.FlightRules.Verdict.*;

import org.junit.Test;

public class FlightRulesTest {
    private static FlightRules.Input ok() {
        return new FlightRules.Input(true, false, true, true, false, 0, false);
    }

    @Test
    public void everythingGoodMeansFlight() {
        assertEquals(OK, FlightRules.decide(ok()));
    }

    @Test
    public void eachRuleBlocks() {
        assertEquals(NOT_WEARING, FlightRules.decide(new FlightRules.Input(false, false, true, true, false, 0, false)));
        assertEquals(DEAD, FlightRules.decide(new FlightRules.Input(true, true, true, true, false, 0, false)));
        assertEquals(GAME_MODE, FlightRules.decide(new FlightRules.Input(true, false, false, true, false, 0, false)));
        assertEquals(WORLD, FlightRules.decide(new FlightRules.Input(true, false, true, false, false, 0, false)));
        assertEquals(ZONE, FlightRules.decide(new FlightRules.Input(true, false, true, true, true, 0, false)));
        assertEquals(COMBAT, FlightRules.decide(new FlightRules.Input(true, false, true, true, false, 1, false)));
        assertEquals(EXHAUSTED, FlightRules.decide(new FlightRules.Input(true, false, true, true, false, 0, true)));
    }

    @Test
    public void permanentCausesWinOverTemporaryOnes() {
        assertEquals(WORLD, FlightRules.decide(new FlightRules.Input(true, false, true, false, true, 5000, true)));
        assertEquals(ZONE, FlightRules.decide(new FlightRules.Input(true, false, true, true, true, 5000, true)));
        assertEquals(COMBAT, FlightRules.decide(new FlightRules.Input(true, false, true, true, false, 5000, true)));
    }

    @Test
    public void expiredCombatTagDoesNotBlock() {
        assertEquals(OK, FlightRules.decide(new FlightRules.Input(true, false, true, true, false, -4000, false)));
    }

    @Test
    public void reasonsMapToTheStopReasons() {
        assertEquals(WingsSettings.Reason.ENERGY, EXHAUSTED.reason());
        assertEquals(WingsSettings.Reason.COMBAT, COMBAT.reason());
        assertEquals(WingsSettings.Reason.WORLD, WORLD.reason());
        assertNull(OK.reason());
    }
}
