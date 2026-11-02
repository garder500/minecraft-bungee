package win.vampyuyu.survival.promotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.classes.ClassBudget;
import win.vampyuyu.survival.api.PlayerClass;

class PromotionRulesTest {
    @Test
    void ranksGoNoviceAdeptMasterThenStop() {
        assertEquals("ADEPT", PromotionRules.nextRank("NOVICE"));
        assertEquals("ADEPT", PromotionRules.nextRank(null));
        assertEquals("MASTER", PromotionRules.nextRank("ADEPT"));
        assertNull(PromotionRules.nextRank("MASTER"));
    }

    @Test
    void anEmptyHistoryStartsWithCombat() {
        assertEquals(Profile.COMBAT, PromotionRules.ranking(Map.of(), 0).get(0));
    }

    @Test
    void theProfileFollowsWhatThePlayerDid() {
        assertEquals(Profile.CRAFT, PromotionRules.ranking(Map.of(PromotionRules.CRAFTS, 400, PromotionRules.KILLS, 100), 0).get(0));
        assertEquals(Profile.COOP, PromotionRules.ranking(Map.of(PromotionRules.COOP, 300, PromotionRules.KILLS, 300), 0).get(0));
        assertEquals(Profile.BOSS, PromotionRules.ranking(Map.of(PromotionRules.BOSS_SOLO, 3, PromotionRules.KILLS, 100), 0).get(0));
        assertEquals(Profile.EXPLORE, PromotionRules.ranking(Map.of(PromotionRules.ENTRIES, 30), 2).get(0));
    }

    @Test
    void theRankingListsEveryProfileOnce() {
        assertEquals(Profile.values().length, PromotionRules.ranking(Map.of(PromotionRules.KILLS, 10), 1).stream().distinct().count());
    }

    @Test
    void theAdeptNeedsTheEchoAndTheBastions() {
        assertEquals(2, PromotionRules.missing("ADEPT", 0, 0, 0, 2, 2).size());
        assertEquals(1, PromotionRules.missing("ADEPT", 1, 1, 1, 2, 2).size());
        assertTrue(PromotionRules.missing("ADEPT", 1, 2, 1, 2, 2).isEmpty());
    }

    @Test
    void theMasterNeedsDifferentGuardians() {
        assertEquals(1, PromotionRules.missing("MASTER", 1, 9, 1, 2, 2).size());
        assertTrue(PromotionRules.missing("MASTER", 1, 9, 2, 2, 2).isEmpty());
    }

    @Test
    void aStepCountsItsKindAndTarget() {
        var any = new PromotionConfig.Step(StepKind.BOSS_ANY, "any", 1, "x");
        var one = new PromotionConfig.Step(StepKind.BOSS_ANY, "voidwyrm", 1, "x");
        assertTrue(PromotionRules.matches(any, StepKind.BOSS_ANY, "magma_warden"));
        assertTrue(PromotionRules.matches(one, StepKind.BOSS_ANY, "voidwyrm"));
        assertFalse(PromotionRules.matches(one, StepKind.BOSS_ANY, "magma_warden"));
        assertFalse(PromotionRules.matches(any, StepKind.KILL, "any"));
    }

    @Test
    void specializationBonusesStayInsideTheMetaCap() {
        ClassBudget.Bonus b = ClassBudget.with(PlayerClass.DUELIST, 500, 500, 500);
        assertEquals(ClassBudget.META_VITALITY, b.vitality(), 1e-9);
        assertEquals(ClassBudget.META_MIGHT, b.might(), 1e-9);
        assertEquals(ClassBudget.META_DEXTERITY, b.dexterity(), 1e-9);
        ClassBudget.Bonus plain = ClassBudget.with(PlayerClass.DUELIST, 0, 0, 0);
        assertEquals(ClassBudget.of(PlayerClass.DUELIST).dexterity(), plain.dexterity(), 1e-9);
        assertEquals(0, ClassBudget.with(null, 5, 5, 5).might(), 1e-9);
    }

    @Test
    void stepKindsAndProfilesParseAnyCase() {
        assertEquals(StepKind.BOSS_SOLO, StepKind.parse("boss_solo"));
        assertNull(StepKind.parse("nope"));
        assertEquals(Profile.COOP, Profile.parse("Coop"));
        assertEquals(List.of(), PromotionRules.missing("NOVICE", 0, 0, 0, 2, 2));
    }
}
