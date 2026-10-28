package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.menu.SoulLogic.SoulDef;
import win.vampyuyu.survival.menu.SoulLogic.SoulSpot;
import win.vampyuyu.survival.menu.SoulLogic.StatStep;

public class SoulLogicTest {
    private static final java.util.function.Function<String, String> WORLDS = token -> switch (token) {
        case "hub" -> "sb_hub";
        case "world" -> "sb_world";
        default -> token;
    };

    private final List<SoulDef> fixed = List.of(new SoulDef("hub_1", "hub", 10, 70, 10, false),
        new SoulDef("hub_2", "hub", -20, 80, 5, false), new SoulDef("nether_1", "nether", 0, 64, 0, false));
    private final List<SoulDef> offsets = List.of(new SoulDef("island_1", "", 5, 2, 0, true),
        new SoulDef("island_2", "", -6, 1, 3, true));

    @Test
    public void visibilityIsOffOnceFound() {
        Set<String> found = new LinkedHashSet<>(List.of("hub_1"));
        assertFalse(SoulLogic.visibleTo(found, "hub_1"));
        assertTrue(SoulLogic.visibleTo(found, "hub_2"));
        assertTrue(SoulLogic.visibleTo(Set.of(), "hub_1"));
    }

    @Test
    public void spotsFollowTheWorldAndTheIslandCentre() {
        List<SoulSpot> hub = SoulLogic.spotsFor("sb_hub", null, fixed, offsets, WORLDS);
        assertEquals(List.of("hub_1", "hub_2"), hub.stream().map(SoulSpot::id).toList());
        List<SoulSpot> island = SoulLogic.spotsFor("sb_world", new double[] {1000, 100, 2000}, fixed, offsets, WORLDS);
        assertEquals(2, island.size());
        assertEquals(1005, island.get(0).x(), 0);
        assertEquals(102, island.get(0).y(), 0);
        assertEquals(2003, island.get(1).z(), 0);
        assertTrue(SoulLogic.spotsFor("sb_world", null, fixed, offsets, WORLDS).isEmpty());
        assertTrue(SoulLogic.spotsFor("somewhere", null, fixed, offsets, WORLDS).isEmpty());
    }

    @Test
    public void nearestIgnoresFoundSouls() {
        List<SoulSpot> spots = SoulLogic.spotsFor("sb_hub", null, fixed, offsets, WORLDS);
        assertEquals("hub_1", SoulLogic.nearest(spots, Set.of(), 8, 70, 8).orElseThrow().id());
        assertEquals("hub_2", SoulLogic.nearest(spots, Set.of("hub_1"), 8, 70, 8).orElseThrow().id());
        assertTrue(SoulLogic.nearest(spots, Set.of("hub_1", "hub_2"), 8, 70, 8).isEmpty());
        assertTrue(SoulLogic.nearest(List.of(), Set.of(), 0, 0, 0).isEmpty());
    }

    @Test
    public void milestonesEveryFive() {
        assertEquals(0, SoulLogic.milestones(4, 5));
        assertEquals(1, SoulLogic.milestones(5, 5));
        assertEquals(3, SoulLogic.milestones(17, 5));
        assertEquals(0, SoulLogic.milestones(99, 0));
        assertEquals(1, SoulLogic.untilNext(4, 5));
        assertEquals(5, SoulLogic.untilNext(5, 5));
    }

    @Test
    public void statCycleAccumulates() {
        List<StatStep> cycle = List.of(new StatStep(Stat.HEALTH, 2), new StatStep(Stat.DEFENSE, 1),
            new StatStep(Stat.STRENGTH, 1));
        assertTrue(SoulLogic.milestoneStats(0, cycle).isEmpty());
        Map<Stat, Double> five = SoulLogic.milestoneStats(5, cycle);
        assertEquals(4.0, five.get(Stat.HEALTH), 0); // milestones 1 and 4
        assertEquals(2.0, five.get(Stat.DEFENSE), 0); // 2 and 5
        assertEquals(1.0, five.get(Stat.STRENGTH), 0);
        assertTrue(SoulLogic.milestoneStats(3, List.of()).isEmpty());
    }

    @Test
    public void directions() {
        assertEquals("north", SoulLogic.direction(0, -10));
        assertEquals("east", SoulLogic.direction(10, 0));
        assertEquals("south", SoulLogic.direction(0, 10));
        assertEquals("west", SoulLogic.direction(-10, 0));
        assertEquals("north-east", SoulLogic.direction(10, -10));
        assertEquals("south-west", SoulLogic.direction(-10, 10));
    }

    @Test
    public void badConfigEntriesAreReportedNotThrown() {
        List<String> problems = new ArrayList<>();
        List<SoulDef> parsed = SoulLogic.parse(List.of(Map.of("id", "ok_1", "world", "hub", "x", 1, "y", 2, "z", 3),
            Map.of("id", "Bad Id", "world", "hub", "x", 1, "y", 2, "z", 3), Map.of("id", "no_z", "world", "hub",
                "x", 1, "y", 2)), false, problems);
        assertEquals(1, parsed.size());
        assertEquals(2, problems.size());
        List<String> stepProblems = new ArrayList<>();
        List<StatStep> steps = SoulLogic.parseCycle(List.of(Map.of("stat", "health", "amount", 2),
            Map.of("stat", "MANA", "amount", 1)), stepProblems);
        assertEquals(1, steps.size());
        assertEquals(1, stepProblems.size());
    }
}
