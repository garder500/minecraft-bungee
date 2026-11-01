package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SidebarLogicTest {
    @Test
    public void calendarDate() {
        assertEquals("Dawn Moon · Day 1", SkyCalendar.date(0));
        assertEquals("Dawn Moon · Day 2", SkyCalendar.date(24000));
        assertEquals("Dawn Moon · Day 24", SkyCalendar.date(23 * 24000L));
        assertEquals("Gale Moon · Day 1", SkyCalendar.date(24 * 24000L));
        assertEquals("Star Moon · Day 24", SkyCalendar.date(192 * 24000L - 1));
        assertEquals("Dawn Moon · Day 1", SkyCalendar.date(192 * 24000L));
        assertEquals(2, SkyCalendar.year(192 * 24000L));
        assertEquals("Dawn Moon · Day 1", SkyCalendar.date(-5));
    }

    @Test
    public void partOfTheDay() {
        assertEquals("Dawn", SkyCalendar.clock(0));
        assertEquals("Morning", SkyCalendar.clock(1000));
        assertEquals("Noon", SkyCalendar.clock(6000));
        assertEquals("Afternoon", SkyCalendar.clock(9000));
        assertEquals("Dusk", SkyCalendar.clock(12000));
        assertEquals("Night", SkyCalendar.clock(18000));
        assertEquals("Dawn", SkyCalendar.clock(23500));
        assertEquals("Dusk", SkyCalendar.clock(12000 + 24000 * 5));
        assertTrue(SkyCalendar.isDay(0));
        assertFalse(SkyCalendar.isDay(14000));
        assertTrue(SkyCalendar.isDay(23500));
    }

    @Test
    public void zonesMatchByWorldAndBoxFirstWins() {
        Zones zones = new Zones(List.of(new Zones.Zone("Market", "sb_hub", 0, 0, 0, 10, 100, 10),
            new Zones.Zone("Village", "sb_hub", -50, 0, -50, 50, 100, 50)));
        assertEquals(2, zones.size());
        assertEquals("Market", zones.find("sb_hub", 5, 64, 5).orElseThrow());
        assertEquals("Village", zones.find("sb_hub", 20, 64, 20).orElseThrow());
        assertTrue(zones.find("sb_hub", 80, 64, 0).isEmpty());
        assertTrue(zones.find("sb_world", 5, 64, 5).isEmpty());
    }

    @Test
    public void layoutWithEverything() {
        List<String> lines = SidebarLines.build(new SidebarLines.View("Late Summer 15th", "7:10pm", false, "Your Island",
            "1,234", "2/4", "Break wood", "(2/5)", "Skyblock", "<yellow>play.example"));
        assertEquals(List.of("<#8FD3FF>Late Summer 15th", "<gray>7:10pm <blue>☽", "", "<#F6D58E>✧ <aqua>Your Island", "",
            "<white>Coins: <gold>1,234", "<white>Crew: <green>2/4", "", "<aqua>Next step",
            "<yellow>Break wood <gray>(2/5)", "<gray>Type <green>/guide <gray>for help", "", "<white>Server: <green>Skyblock",
            "<yellow>play.example"), lines);
    }

    @Test
    public void layoutDropsMissingBlocksAndTrailingBlank() {
        List<String> lines = SidebarLines.build(new SidebarLines.View("d", "t", true, "z", null, null, null, null, "", ""));
        assertEquals(List.of("<#8FD3FF>d", "<gray>t <yellow>☀", "", "<#F6D58E>✧ <aqua>z"), lines);
    }

    @Test
    public void serverNameLineIsShownEscapedAndOmittedWhenBlank() {
        List<String> with = SidebarLines.build(new SidebarLines.View("d", "t", true, "z", null, null, null, null,
            "<red>Evil", ""));
        assertEquals("<white>Server: <green>\\<red>Evil", with.get(with.size() - 1));
        List<String> without = SidebarLines.build(new SidebarLines.View("d", "t", true, "z", null, null, null, null,
            "", ""));
        assertTrue(without.stream().noneMatch(l -> l.contains("Server:")));
    }

    @Test
    public void dynamicTextCannotInjectMarkup() {
        List<String> lines = SidebarLines.build(new SidebarLines.View("d", "t", true, "<red>evil", null, null,
            "<click:run_command:/op>x", null, "", ""));
        assertTrue(lines.get(3).contains("\\<red>"));
        assertTrue(lines.stream().noneMatch(l -> l.matches("(?s).*(?<!\\\\)<click.*")));
    }

    @Test
    public void longQuestNamesAreCut() {
        String cut = SidebarLines.cut("A very very long quest name indeed", SidebarLines.QUEST_WIDTH);
        assertEquals(SidebarLines.QUEST_WIDTH, cut.length());
        assertTrue(cut.endsWith("…"));
        assertEquals("short", SidebarLines.cut("short", 26));
    }
}
