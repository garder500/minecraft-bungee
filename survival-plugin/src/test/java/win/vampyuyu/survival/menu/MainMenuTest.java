package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.IslandApi;

/** The Skyblock Menu doors, the warps menu and the commands that forward to other features. */
public class MainMenuTest extends MenuTestBase {
    private FakeIslands islands;
    private final List<String> ran = new ArrayList<>();
    private MainMenu menu;
    private WarpsGui warpsGui;
    private LeaderboardsGui boards;
    private MenuCommands commands;
    private PlayerMock player;

    @Before
    public void setUp() {
        islands = new FakeIslands();
        core.register(IslandApi.class, islands);
        QuestService quests = new QuestService(rt);
        QuestsGui questsGui = new QuestsGui(rt, quests);
        warpsGui = new WarpsGui(rt, new WarpService(rt));
        SettingsGui settings = new SettingsGui(rt, p -> { });
        FairySouls fairy = new FairySouls(rt, quests, new FairySoulsTest.FakeDisplays());
        boards = new LeaderboardsGui(rt);
        menu = new MainMenu(rt, quests, questsGui, warpsGui, settings, new FairyGui(rt, fairy), boards);
        warpsGui.runWith(menu::run);
        boards.runWith(menu::run);
        commands = new MenuCommands(rt, menu, questsGui, quests, warpsGui, new WarpService(rt), settings, fairy,
            () -> true);
        player = playerIn("sb_hub");
    }

    private void command(String name, String... aliases) {
        core.commands().command(name).aliases(aliases).executor((s, c, l, a) -> {
            ran.add(name + (a.length == 0 ? "" : " " + String.join(" ", a)));
            return true;
        }).register();
    }

    private static String name(ItemStack item) {
        return item == null || item.getItemMeta() == null || item.getItemMeta().displayName() == null ? ""
            : PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private List<String> names(Inventory inventory) {
        List<String> out = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !name(item).isBlank()) {
                out.add(name(item));
            }
        }
        return out;
    }

    private void click(int slot) {
        var view = player.getOpenInventory();
        server.getPluginManager().callEvent(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot,
            ClickType.LEFT, InventoryAction.PICKUP_ALL));
        server.getScheduler().performTicks(3);
    }

    @Test
    public void doorsOfFeaturesThatAreNotInstalledAreHiddenNotGreyed() {
        command("skills", "skill");
        command("pets", "pet");
        menu.open(player);
        List<String> names = names(player.getOpenInventory().getTopInventory());
        assertTrue(names.toString(), names.contains("Skills"));
        assertTrue(names.contains("Pets"));
        assertFalse(names.toString(), names.contains("Bazaar"));
        assertFalse(names.toString(), names.stream().anyMatch(n -> n.contains("unavailable")));
        assertFalse(names.contains("Recipe Book"));
        assertFalse(names.contains("Leaderboards"));
        // menu-owned entries are always there
        for (String own : List.of("Quests", "Fairy Souls", "Warps", "Settings", "Close")) {
            assertTrue(own, names.contains(own));
        }
    }

    @Test
    public void realCommandsOfTheSiblingFeaturesOpenTheirDoors() {
        command("accessories", "acc", "bag");
        command("pets", "pet");
        command("bank", "sbbank");
        command("minion", "minions");
        command("boss");
        command("slayer");
        command("dungeon", "dg", "catacombs");
        command("auctionhouse", "ah", "auction");
        command("stats", "mystats");
        menu.open(player);
        List<String> names = names(player.getOpenInventory().getTopInventory());
        for (String door : List.of("Accessories", "Pets", "Bank", "Minions", "Bosses", "Slayer", "Dungeons", "Auction House")) {
            assertTrue(door + " in " + names, names.contains(door));
        }
        // doors fill the slots in order without gaps
        Inventory top = player.getOpenInventory().getTopInventory();
        for (int slot : new int[] {19, 20, 21, 22, 23, 24, 25, 28}) {
            assertNotNull("slot " + slot, top.getItem(slot));
            assertFalse("slot " + slot, name(top.getItem(slot)).isBlank());
        }
        assertTrue("no gap, nothing after the eighth door", name(top.getItem(29)).isBlank());
    }

    @Test
    public void aDoorWhoseCommandIsForbiddenIsGreyedButStillShown() {
        core.commands().command("bank").permission("vampyuyu.test.bank", PermissionDefault.FALSE)
            .executor((s, c, l, a) -> true).register();
        menu.open(player);
        assertTrue(names(player.getOpenInventory().getTopInventory()).toString(),
            names(player.getOpenInventory().getTopInventory()).contains("Bank (unavailable)"));
    }

    @Test
    public void clickingADoorRunsTheCommandAsThePlayer() {
        command("skills", "skill");
        menu.open(player);
        click(19);
        assertEquals(List.of("skills"), ran);
    }

    @Test
    public void profileHeadRunsStats() {
        command("stats", "mystats");
        menu.open(player);
        click(4);
        assertEquals(List.of("stats"), ran);
    }

    @Test
    public void thePlayerWithoutAnIslandSeesCreateIslandInsteadOfIsland() {
        command("island", "is");
        menu.open(player);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertTrue(names(top).toString(), names(top).contains("Island") || names(top).contains("Create Island"));
        // "island create" resolves to the island command, which exists in this test
        assertTrue(names(top).toString(), names(top).contains("Create Island"));
        click(19);
        assertEquals(List.of("island create"), ran);
        // with an island the door opens the island menu
        ran.clear();
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 0, 64, 0));
        menu.open(player);
        assertTrue(names(player.getOpenInventory().getTopInventory()).contains("Island"));
        click(19);
        assertEquals(List.of("island"), ran);
    }

    @Test
    public void leaderboardsOpenTheMenuOwnedSubmenuWithTheRealBoards() {
        command("baltop", "topcoins");
        command("skilltop", "skillboard");
        menu.open(player);
        assertTrue(names(player.getOpenInventory().getTopInventory()).contains("Leaderboards"));
        click(19);
        server.getScheduler().performTicks(3);
        Inventory board = player.getOpenInventory().getTopInventory();
        assertTrue(names(board).toString(), names(board).contains("Richest Players"));
        assertTrue(names(board).contains("Skill Leaders"));
        click(11);
        assertEquals(List.of("baltop"), ran);
    }

    @Test
    public void aMissingLeaderboardIsGreyedInTheSubmenu() {
        command("baltop");
        boards.open(player);
        List<String> names = names(player.getOpenInventory().getTopInventory());
        assertTrue(names.toString(), names.contains("Richest Players"));
        assertTrue(names.toString(), names.contains("Skill Leaders (unavailable)"));
    }

    @Test
    public void aRealLeaderboardsCommandWinsOverTheSubmenu() {
        command("leaderboards", "lb");
        menu.open(player);
        click(19);
        assertEquals(List.of("leaderboards"), ran);
    }

    // ------------------------------------------------------------ warps and island commands

    @Test
    public void publicIslandsAndVisitsAreDelegatedToTheIslandFeature() {
        command("island", "is");
        warpsGui.open(player);
        assertEquals(Material.SPYGLASS, player.getOpenInventory().getTopInventory().getItem(15).getType());
        click(15);
        assertEquals(List.of("island warp"), ran);
    }

    @Test
    public void withoutTheIslandFeatureThePublicIslandButtonsAreGreyed() {
        warpsGui.open(player);
        List<String> names = names(player.getOpenInventory().getTopInventory());
        assertTrue(names.toString(), names.contains("Public Islands (unavailable)"));
        assertTrue(names.toString(), names.contains("Visit a Player (unavailable)"));
        click(15);
        assertTrue(ran.isEmpty());
    }

    @Test
    public void sbwarpsVisitForwardsToIslandVisitAndNoDuplicateRegistryIsKept() {
        command("island", "is");
        commands.register();
        player.performCommand("sbwarps visit Alice");
        player.performCommand("sbwarps visit");
        player.performCommand("sbwarps public on hello");
        server.getScheduler().performTicks(3);
        assertEquals(List.of("island visit Alice", "island warp", "island warp"), ran);
        assertFalse("no menu-owned public warp file", core.store().exists("menu/public_warps.json"));
    }

    @Test
    public void theMenuDoesNotRegisterHubBecauseTheIslandFeatureOwnsIt() {
        commands.register();
        assertNull(org.bukkit.Bukkit.getCommandMap().getCommand("hub"));
        assertFalse(MenuFeature.commandNames().contains("hub"));
    }
}
