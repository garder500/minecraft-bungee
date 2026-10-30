package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import org.junit.Test;
import win.vampyuyu.survival.dungeon.ScoreCalculator.Inputs;

public class ScoreCalculatorTest {
    private static final DungeonConfig CFG = DungeonConfig.parse(null);

    private static Inputs perfect() {
        return new Inputs(3, true, 300, 720, 11, 11, 5, 5, 2, 2, 60, 60, 8, 8, 50, 0, 1);
    }

    @Test
    public void aPerfectRunIsSPlus() {
        var s = ScoreCalculator.compute(perfect(), CFG);
        assertEquals(100, s.speed(), 0.001);
        assertEquals(100, s.exploration(), 0.001);
        assertEquals(100, s.skill(), 0.001);
        assertEquals(10 + 3 + 3 + 3, s.bonus(), 0.001);
        assertEquals(319, s.total());
        assertEquals(Grade.S_PLUS, s.grade());
    }

    @Test
    public void speedLosesOnePointPerDecayInterval() {
        Inputs in = new Inputs(3, true, 720 + 60, 720, 11, 11, 5, 5, 2, 2, 60, 60, 8, 8, 50, 0, 1);
        assertEquals(90, ScoreCalculator.compute(in, CFG).speed(), 0.001); // 60s / 6s
        Inputs slow = new Inputs(3, true, 720 + 5000, 720, 11, 11, 5, 5, 2, 2, 60, 60, 8, 8, 50, 0, 1);
        assertEquals(0, ScoreCalculator.compute(slow, CFG).speed(), 0.001);
    }

    @Test
    public void deathsCostSkillAndTheNoDeathBonus() {
        var clean = ScoreCalculator.compute(perfect(), CFG);
        Inputs dead = new Inputs(3, true, 300, 720, 11, 11, 5, 5, 2, 2, 60, 60, 8, 8, 50, 3, 1);
        var s = ScoreCalculator.compute(dead, CFG);
        assertEquals(94, s.skill(), 0.001);
        assertEquals(clean.bonus() - 10, s.bonus(), 0.001);
        assertTrue(s.total() < clean.total() - 15);
    }

    @Test
    public void secretsNeededShareCapsExploration() {
        // 50 percent of 8 secrets = 4 needed
        Inputs half = new Inputs(3, true, 300, 720, 11, 11, 5, 5, 2, 2, 60, 60, 8, 4, 50, 0, 1);
        assertEquals(100, ScoreCalculator.compute(half, CFG).exploration(), 0.001);
        Inputs none = new Inputs(3, true, 300, 720, 11, 11, 5, 5, 2, 2, 60, 60, 8, 0, 50, 0, 1);
        assertEquals(60, ScoreCalculator.compute(none, CFG).exploration(), 0.001);
    }

    @Test
    public void nothingToDoCountsAsDone() {
        Inputs in = new Inputs(1, true, 100, 420, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0, 40, 0, 0);
        var s = ScoreCalculator.compute(in, CFG);
        assertEquals(100, s.skill(), 0.001);
        assertEquals(100, s.exploration(), 0.001);
    }

    @Test
    public void gradeBoundaries() {
        assertEquals(Grade.D, ScoreCalculator.gradeFor(0, CFG));
        assertEquals(Grade.D, ScoreCalculator.gradeFor(99, CFG));
        assertEquals(Grade.C, ScoreCalculator.gradeFor(100, CFG));
        assertEquals(Grade.B, ScoreCalculator.gradeFor(160, CFG));
        assertEquals(Grade.A, ScoreCalculator.gradeFor(230, CFG));
        assertEquals(Grade.S, ScoreCalculator.gradeFor(270, CFG));
        assertEquals(Grade.S, ScoreCalculator.gradeFor(299, CFG));
        assertEquals(Grade.S_PLUS, ScoreCalculator.gradeFor(300, CFG));
        assertEquals(Grade.S_PLUS, ScoreCalculator.gradeFor(325, CFG));
    }

    @Test
    public void totalNeverLeavesTheRange() {
        for (int deaths = 0; deaths < 40; deaths += 5) {
            Inputs in = new Inputs(2, false, 99999, 600, 9, 0, 3, 0, 2, 0, 40, 0, 6, 0, 45, deaths, 0);
            var s = ScoreCalculator.compute(in, CFG);
            assertTrue(s.total() >= 0 && s.total() <= 325);
            assertEquals(Grade.D, s.grade());
            assertTrue(s.skill() >= 0);
        }
    }

    @Test
    public void gradeParsing() {
        assertEquals(Grade.S_PLUS, Grade.parse("S+").orElseThrow());
        assertEquals(Grade.S_PLUS, Grade.parse("s_plus").orElseThrow());
        assertEquals(Grade.A, Grade.parse(" a ").orElseThrow());
        assertTrue(Grade.parse("Z").isEmpty());
        assertTrue(Grade.parse(null).isEmpty());
    }
}
