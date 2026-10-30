package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.menu.SettingsGui.Setting;

public class SettingsAndSidebarTest extends MenuTestBase {
    private FakeEconomy economy;
    private FakeIslands islands;
    private QuestService quests;
    private SidebarService sidebar;
    private SettingsGui settings;
    private PlayerMock player;
    private final List<String> changed = new java.util.ArrayList<>();

    @Before
    public void setUp() {
        economy = new FakeEconomy();
        islands = new FakeIslands();
        core.register(EconomyApi.class, economy);
        core.register(IslandApi.class, islands);
        quests = new QuestService(rt);
        sidebar = new SidebarService(rt, quests);
        settings = new SettingsGui(rt, p -> changed.add(p.getName()));
        player = playerIn("sb_hub");
    }

    // ------------------------------------------------------------ settings

    @Test
    public void defaultsAreAllOn() {
        MenuData data = rt.prefs(player.getUniqueId());
        assertTrue(data.sidebar && data.questTracker && data.damageIndicators && data.sounds);
        assertFalse(data.welcomed);
    }

    @Test
    public void togglingFlipsStoresAndNotifies() {
        assertFalse(settings.toggle(player, Setting.SIDEBAR));
        assertFalse(rt.prefs(player.getUniqueId()).sidebar);
        assertTrue(settings.toggle(player, Setting.SIDEBAR));
        assertFalse(settings.toggle(player, Setting.DAMAGE_INDICATORS));
        assertFalse(settings.toggle(player, Setting.SOUNDS));
        assertFalse(settings.toggle(player, Setting.QUEST_TRACKER));
        assertEquals(5, changed.size());
    }

    @Test
    public void settingsSurviveASaveAndReloadOfTheProfile() {
        settings.toggle(player, Setting.SIDEBAR);
        settings.toggle(player, Setting.SOUNDS);
        rt.prefs(player.getUniqueId()).welcomed = true;
        rt.quests(player.getUniqueId()).tracked = "break_wood";
        rt.fairy(player.getUniqueId()).found.add("hub_1");
        core.profiles().playerQuit(player.getUniqueId());
        core.profiles().awaitWrites();
        core.profiles().playerJoined(player.getUniqueId(), player.getName());
        MenuData data = rt.prefs(player.getUniqueId());
        assertFalse(data.sidebar);
        assertFalse(data.sounds);
        assertTrue(data.questTracker);
        assertTrue(data.welcomed);
        assertEquals("break_wood", rt.quests(player.getUniqueId()).tracked);
        assertTrue(rt.fairy(player.getUniqueId()).found.contains("hub_1"));
    }

    @Test
    public void settingKeysAreCommandFriendly() {
        assertEquals("quest-tracker", Setting.QUEST_TRACKER.key());
        assertEquals("damage-indicators", Setting.DAMAGE_INDICATORS.key());
    }

    @Test
    public void settingsGuiOpensWithOneIconPerSetting() {
        settings.open(player);
        var top = player.getOpenInventory().getTopInventory();
        int icons = 0;
        for (int slot : new int[] {10, 12, 14, 16}) {
            if (top.getItem(slot) != null) {
                icons++;
            }
        }
        assertEquals(4, icons);
    }

    // ------------------------------------------------------------ sidebar

    @Test
    public void sidebarContentFollowsTheGameState() {
        economy.add(player.getUniqueId(), 1234, "test");
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 0, 64, 0));
        player.teleport(new Location(world("sb_world"), 2, 64, 2));
        quests.sync(player);
        List<String> lines = sidebar.build(player);
        assertTrue(lines.toString(), lines.contains("<white>Purse: <gold>1,234"));
        assertTrue(lines.toString(), lines.contains("<white>Members: <green>1/1"));
        assertTrue(lines.toString(), lines.stream().anyMatch(l -> l.contains("Your Island")));
        assertTrue(lines.toString(), lines.contains("<aqua>Objective"));
        assertEquals("<yellow>vampyuyu.win", lines.get(lines.size() - 1));
    }

    @Test
    public void questTrackerSettingHidesTheObjective() {
        settings.toggle(player, Setting.QUEST_TRACKER);
        assertFalse(sidebar.build(player).contains("<aqua>Objective"));
    }

    @Test
    public void zoneNamesComeFromTheConfig() {
        assertEquals("Hub Spawn", sidebar.zone(player));
        player.teleport(new Location(world("sb_hub"), 60, 64, 60));
        assertEquals("Hub Village", sidebar.zone(player));
        player.teleport(new Location(world("sb_hub"), 500, 64, 500));
        assertEquals("Hub", sidebar.zone(player));
        player.teleport(new Location(world("sb_world"), 5, 64, 5));
        assertEquals("Island", sidebar.zone(player));
    }

    @Test
    public void foreignIslandsAreNamedAfterTheirOwner() {
        PlayerMock owner = server.addPlayer("Alice");
        islands.give(owner.getUniqueId(), new Location(world("sb_world"), 900, 80, 900));
        player.teleport(new Location(world("sb_world"), 905, 80, 900));
        assertEquals("Alice's Island", sidebar.zone(player));
    }

    @Test
    public void refreshGivesAPrivateScoreboardOnlyInSkyblockAndRestoresTheMainOne() {
        var main = Bukkit.getScoreboardManager().getMainScoreboard();
        sidebar.refresh(player);
        assertNotSame(main, player.getScoreboard());
        var objective = player.getScoreboard().getObjective(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        assertNotNull(objective);
        player.teleport(new Location(world("world"), 0, 64, 0));
        sidebar.refresh(player);
        assertSame(main, player.getScoreboard());
    }

    @Test
    public void switchingTheSidebarOffRestoresTheMainScoreboard() {
        sidebar.refresh(player);
        settings.toggle(player, Setting.SIDEBAR);
        sidebar.refresh(player);
        assertSame(Bukkit.getScoreboardManager().getMainScoreboard(), player.getScoreboard());
    }

    @Test
    public void repeatedRefreshesDoNotRebuildTheScoreboard() {
        sidebar.refresh(player);
        var first = player.getScoreboard();
        sidebar.refresh(player);
        sidebar.refresh(player);
        assertSame(first, player.getScoreboard());
    }

    @Test
    public void stopRestoresEveryone() {
        sidebar.refresh(player);
        sidebar.stop();
        assertSame(Bukkit.getScoreboardManager().getMainScoreboard(), player.getScoreboard());
    }
}
