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
    private QuestsGui questsGui;
    private SettingsGui settings;
    private FairyGui fairyGui;

    @Before
    public void setUp() {
        islands = new FakeIslands();
        core.replace(IslandApi.class, islands);
        QuestService quests = new QuestService(rt);
        questsGui = new QuestsGui(rt, quests);
        warpsGui = new WarpsGui(rt, new WarpService(rt));
        settings = new SettingsGui(rt, p -> { });
        FairySouls fairy = new FairySouls(rt, quests, new FairySoulsTest.FakeDisplays());
        boards = new LeaderboardsGui(rt);
        fairyGui = new FairyGui(rt, fairy);
        menu = new MainMenu(rt, quests, questsGui, warpsGui, settings, fairyGui, boards);
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
        assertFalse(names.toString(), names.contains("Sky Exchange"));
        assertFalse(names.toString(), names.stream().anyMatch(n -> n.contains("unavailable")));
        assertFalse(names.contains("Leaderboards"));
        // a missing feature leaves its slot empty, it does not shift the others
        assertNull(player.getOpenInventory().getTopInventory().getItem(MainMenu.SB_MAIN[6]));
        // menu-owned entries are always there
        for (String own : List.of("Quests", "Wisps", "Profile", "Warps", "Settings")) {
            assertTrue(own, names.contains(own));
        }
        assertFalse("no Close button", names.contains("Close"));
    }

    @Test
    public void allSeventeenButtonsSitExactlyOnTheConstellationSlots() {
        command("skills", "skill");
        command("collections", "collection");
        command("forge");
        command("island", "is");
        command("minion", "minions");
        command("bazaar", "bz");
        command("auctionhouse", "ah", "auction");
        command("bank", "sbbank");
        command("boss");
        command("dungeon", "dg", "catacombs");
        command("slayer");
        command("leaderboards", "lb");
        menu.open(player);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertEquals(54, top.getSize());
        List<String> order = List.of("Quests", "Skills", "Collections", "Forge", "Create Island", "Minions", "Sky Exchange",
            "Drift Market", "Bank", "Bosses", "Crypt Depths", "Hunts", "Wisps", "Leaderboards", "Profile", "Warps", "Settings");
        assertEquals(17, MainMenu.SB_MAIN.length);
        java.util.Set<Integer> expected = new java.util.HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            expected.add(MainMenu.SB_MAIN[i]);
            assertEquals("slot " + MainMenu.SB_MAIN[i], order.get(i), name(top.getItem(MainMenu.SB_MAIN[i])));
        }
        for (int slot = 0; slot < top.getSize(); slot++) {
            if (!expected.contains(slot)) {
                assertNull("slot " + slot + " must stay empty", top.getItem(slot));
            }
        }
        // the layout mirrors resource-pack/layout.py SB_MAIN
        assertArrayEquals(new int[] {11, 13, 15, 19, 22, 25, 29, 31, 33, 37, 40, 43, 1, 7, 45, 49, 53}, MainMenu.SB_MAIN);
    }

    @Test
    public void realCommandsOfTheSiblingFeaturesOpenTheirDoors() {
        command("bank", "sbbank");
        command("minion", "minions");
        command("boss");
        command("slayer");
        command("dungeon", "dg", "catacombs");
        command("auctionhouse", "ah", "auction");
        menu.open(player);
        List<String> names = names(player.getOpenInventory().getTopInventory());
        for (String door : List.of("Bank", "Minions", "Bosses", "Hunts", "Crypt Depths", "Drift Market")) {
            assertTrue(door + " in " + names, names.contains(door));
        }
    }

    @Test
    public void petsAccessoriesAndTheRecipeBookAreReachedFromTheProfile() {
        command("accessories", "acc", "bag");
        command("pets", "pet");
        command("stats", "mystats");
        menu.open(player);
        click(45);
        Inventory page = player.getOpenInventory().getTopInventory();
        assertEquals(27, page.getSize());
        List<String> names = names(page);
        for (String entry : List.of("Stats", "Accessories", "Pets", "Back")) {
            assertTrue(entry + " in " + names, names.contains(entry));
        }
        assertFalse(names.contains("Recipe Book"));
        assertEquals("Back", name(page.getItem(18)));
        click(10);
        assertEquals(List.of("stats"), ran);
        // the back button returns to the Isles
        menu.openProfile(player);
        click(18);
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
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
        click(13);
        assertEquals(List.of("skills"), ran);
    }

    @Test
    public void thePlayerWithoutAnIslandSeesCreateIslandInsteadOfIsland() {
        command("island", "is");
        menu.open(player);
        Inventory top = player.getOpenInventory().getTopInventory();
        // "island create" resolves to the island command, which exists in this test
        assertEquals("Create Island", name(top.getItem(22)));
        click(22);
        assertEquals(List.of("island create"), ran);
        // with an island the door opens the island menu
        ran.clear();
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 0, 64, 0));
        menu.open(player);
        assertEquals("Island", name(player.getOpenInventory().getTopInventory().getItem(22)));
        click(22);
        assertEquals(List.of("island"), ran);
    }

    @Test
    public void everyMenuOfTheMenuFeatureFollowsTheHouseRules() {
        command("skills", "skill");
        command("island", "is");
        command("baltop", "topcoins");
        command("skilltop", "skillboard");
        command("stats", "mystats");
        command("pets", "pet");
        for (int i = 0; i < 3; i++) {
            player.closeInventory();
            switch (i) {
                case 0 -> menu.open(player);
                case 1 -> menu.openProfile(player);
                default -> warpsGui.open(player);
            }
            win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "menu " + i);
        }
        boards.open(player);
        win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "boards");
        settings.open(player);
        win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "settings");
        questsGui.open(player);
        win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "quests");
        questsGui.categories(player);
        win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "quest categories");
        questsGui.daily(player);
        win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "daily");
        questsGui.tutorial(player);
        win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "tutorial");
        fairyGui.open(player);
        win.vampyuyu.survival.core.MenuRules.check(player.getOpenInventory().getTopInventory(), "wisps");
    }

    @Test
    public void tooltipsFollowTheHouseStyle() {
        command("skills", "skill");
        menu.open(player);
        ItemStack skills = player.getOpenInventory().getTopInventory().getItem(13);
        List<String> lore = skills.lore().stream().map(l -> PlainTextComponentSerializer.plainText().serialize(l)).toList();
        assertEquals(lore.toString(), 2, lore.size());
        assertEquals("▸ Open", lore.get(1));
        assertTrue(skills.getItemMeta().hasItemModel());
        assertEquals("menu/skills", skills.getItemMeta().getItemModel().getKey());
    }

    @Test
    public void leaderboardsOpenTheMenuOwnedSubmenuWithTheRealBoards() {
        command("baltop", "topcoins");
        command("skilltop", "skillboard");
        menu.open(player);
        assertTrue(names(player.getOpenInventory().getTopInventory()).contains("Leaderboards"));
        click(7);
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
        click(7);
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
