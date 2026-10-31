package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SidebarLogicTest {
    @Test
    public void calendarDate() {
        assertEquals("Early Spring 1st", SkyCalendar.date(0));
        assertEquals("Early Spring 2nd", SkyCalendar.date(24000));
        assertEquals("Early Spring 3rd", SkyCalendar.date(2 * 24000L));
        assertEquals("Early Spring 4th", SkyCalendar.date(3 * 24000L));
        assertEquals("Early Spring 11th", SkyCalendar.date(10 * 24000L));
        assertEquals("Spring 1st", SkyCalendar.date(31 * 24000L));
        assertEquals("Early Spring 1st", SkyCalendar.date(372 * 24000L));
        assertEquals("Late Winter 31st", SkyCalendar.date((372 * 24000L) - 1));
        assertEquals(2, SkyCalendar.year(372 * 24000L));
        assertEquals("Early Spring 1st", SkyCalendar.date(-5));
    }

    @Test
    public void calendarClock() {
        assertEquals("6:00am", SkyCalendar.clock(0));
        assertEquals("12:00pm", SkyCalendar.clock(6000));
        assertEquals("7:00pm", SkyCalendar.clock(13000));
        assertEquals("12:00am", SkyCalendar.clock(18000));
        assertEquals("6:10am", SkyCalendar.clock(190));   // 11 min rounds down to 10
        assertEquals("5:50am", SkyCalendar.clock(23999));
        assertEquals("7:00pm", SkyCalendar.clock(13000 + 24000 * 5));
        assertTrue(SkyCalendar.isDay(0));
        assertFalse(SkyCalendar.isDay(14000));
        assertTrue(SkyCalendar.isDay(23500));
    }

    @Test
    public void zonesMatchByWorldAndBoxFirstWins() {
        List<String> problems = new ArrayList<>();
        Zones zones = Zones.parse(List.of(
            Map.of("name", "Market", "world", "hub", "min", List.of(0, 0, 0), "max", List.of(10, 100, 10)),
            Map.of("name", "Village", "world", "hub", "min", List.of(-50, 0, -50), "max", List.of(50, 100, 50)),
            Map.of("name", "Bad", "world", "hub", "min", List.of(0, 0), "max", List.of(1, 1, 1))),
            token -> token.equals("hub") ? "sb_hub" : token, problems);
        assertEquals(1, problems.size());
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
        assertEquals(List.of("<gray>Late Summer 15th", "<gray>7:10pm <blue>☽", "", "<gray>⏣ <aqua>Your Island", "",
            "<white>Purse: <gold>1,234", "<white>Members: <green>2/4", "", "<aqua>Objective",
            "<yellow>Break wood <gray>(2/5)", "<gray>Type <green>/guide <gray>for help", "", "<white>Server: <green>Skyblock",
            "<yellow>play.example"), lines);
    }

    @Test
    public void layoutDropsMissingBlocksAndTrailingBlank() {
        List<String> lines = SidebarLines.build(new SidebarLines.View("d", "t", true, "z", null, null, null, null, "", ""));
        assertEquals(List.of("<gray>d", "<gray>t <yellow>☀", "", "<gray>⏣ <aqua>z"), lines);
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
