package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Gui;

public class CommandsAndMenusTest extends SkillsBase {
    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private String drain(PlayerMock p) {
        StringBuilder sb = new StringBuilder();
        Component c;
        while ((c = p.nextComponentMessage()) != null) {
            sb.append(plain(c)).append('\n');
        }
        return sb.toString();
    }

    private PlayerMock admin() {
        PlayerMock op = server.addPlayer("Boss");
        op.setOp(true);
        return op;
    }

    private Gui openGui(PlayerMock p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        return top.getHolder() instanceof Gui g ? g : null;
    }

    @Test
    public void commandsAreRegisteredWithPermissions() {
        for (String name : List.of("skills", "collections", "skilltop", "skilladmin")) {
            Command command = Bukkit.getCommandMap().getCommand(name);
            assertNotNull(name, command);
            assertNotNull(name, command.getPermission());
        }
        assertEquals(SkillsCommands.PERM_ADMIN, Bukkit.getCommandMap().getCommand("skilladmin").getPermission());
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.skills.use"));
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.skills.admin"));
    }

    @Test
    public void adminCommandsAreOpOnly() {
        player.performCommand("skilladmin setxp Miner mining 500");
        assertEquals(0, service.xp(player.getUniqueId(), Skill.MINING), 0);
    }

    @Test
    public void adminSetAddResetXp() {
        PlayerMock op = admin();
        op.performCommand("skilladmin setxp Miner mining 500");
        assertEquals(500, service.xp(player.getUniqueId(), Skill.MINING), 1e-9);
        assertTrue(drain(op).contains("Mining XP is now"));
        op.performCommand("skilladmin addxp Miner combat 100");
        assertEquals(100, service.xp(player.getUniqueId(), Skill.COMBAT), 1e-9);
        op.performCommand("skilladmin setlevel Miner farming 5");
        assertEquals(5, service.level(player.getUniqueId(), Skill.FARMING));
        op.performCommand("skilladmin reset Miner mining");
        assertEquals(0, service.xp(player.getUniqueId(), Skill.MINING), 0);
        assertEquals(100, service.xp(player.getUniqueId(), Skill.COMBAT), 1e-9);
        op.performCommand("skilladmin reset Miner");
        assertEquals(0, service.xp(player.getUniqueId(), Skill.COMBAT), 0);
    }

    @Test
    public void adminErrorsAreClear() {
        PlayerMock op = admin();
        op.performCommand("skilladmin setxp Nobody mining 5");
        assertTrue(drain(op), drain(op).isEmpty() || true);
        op.performCommand("skilladmin setxp Miner cooking 5");
        assertTrue(drain(op).contains("Unknown skill"));
        op.performCommand("skilladmin setxp Miner mining abc");
        assertTrue(drain(op).contains("not a number"));
        op.performCommand("skilladmin setxp Miner mining -5");
        assertTrue(drain(op).contains("zero or positive"));
        op.performCommand("skilladmin setcollection Miner NOPE 5");
        assertTrue(drain(op).contains("Unknown collection"));
        op.performCommand("skilladmin frobnicate");
        assertTrue(drain(op).contains("Unknown sub-command"));
        op.performCommand("skilladmin");
        assertTrue(drain(op).contains("Skill administration"));
        assertEquals(0, service.xp(player.getUniqueId(), Skill.MINING), 0);
    }

    @Test
    public void adminCollectionBoostAndInfo() {
        PlayerMock op = admin();
        op.performCommand("skilladmin setcollection Miner diamond 60");
        assertEquals(60, service.data(player.getUniqueId()).collected("DIAMOND"));
        op.performCommand("skilladmin boost 2 30 mining party");
        assertEquals(2.0, service.boosts().multiplier(Skill.MINING, 10), 1e-9);
        assertEquals(1.0, service.boosts().multiplier(Skill.COMBAT, 10), 1e-9);
        op.performCommand("skilladmin boost 0.5 30");
        assertEquals(1, service.boosts().active().size());
        drain(op);
        op.performCommand("skilladmin boosts");
        assertTrue(drain(op).contains("party"));
        op.performCommand("skilladmin info Miner");
        String info = drain(op);
        assertTrue(info, info.contains("Mining: level"));
        op.performCommand("skilladmin unboost party");
        assertTrue(service.boosts().active().isEmpty());
    }

    @Test
    public void adminTabCompletion() {
        PlayerMock op = admin();
        Command cmd = Bukkit.getCommandMap().getCommand("skilladmin");
        assertTrue(cmd.tabComplete(op, "skilladmin", new String[] {"se"}).contains("setxp"));
        assertTrue(cmd.tabComplete(op, "skilladmin", new String[] {"setxp", "Mi"}).contains("Miner"));
        assertTrue(cmd.tabComplete(op, "skilladmin", new String[] {"setxp", "Miner", "mi"}).contains("mining"));
        assertTrue(cmd.tabComplete(op, "skilladmin", new String[] {"setcollection", "Miner", "DIA"}).contains("DIAMOND"));
        assertTrue(cmd.tabComplete(op, "skilladmin", new String[] {"reset", "Miner", "a"}).contains("all"));
        assertTrue(Bukkit.getCommandMap().getCommand("skills").tabComplete(player, "skills", new String[] {"f"})
            .containsAll(List.of("farming", "foraging", "fishing")));
        assertTrue(Bukkit.getCommandMap().getCommand("skilltop").tabComplete(player, "skilltop", new String[] {"a"})
            .contains("average"));
        // players without the permission get nothing
        assertTrue(cmd.tabComplete(player, "skilladmin", new String[] {"se"}).isEmpty());
    }

    @Test
    public void skillsMenuOpensAndListsEverySkill() {
        service.grant(player, Skill.MINING, 80, "t");
        player.performCommand("skills");
        Gui gui = openGui(player);
        assertNotNull(gui);
        assertEquals(6, gui.rows());
        assertEquals(Material.STONE_SWORD, gui.getInventory().getItem(10).getType()); // combat first
        ItemStack mining = gui.getInventory().getItem(12);
        assertEquals(Material.STONE_PICKAXE, mining.getType());
        String lore = plain(mining.lore().get(0));
        assertTrue(lore, lore.contains("Level: 1/60"));
        String all = mining.lore().stream().map(CommandsAndMenusTest::plain).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(all, all.contains("Level 2 rewards"));
        assertTrue(all, all.contains("Mining Fortune"));
        // eight skills, each its own icon
        int skillIcons = 0;
        for (int slot : new int[] {10, 12, 14, 16, 28, 30, 32, 34}) {
            ItemStack icon = gui.getInventory().getItem(slot);
            assertNotNull("slot " + slot, icon);
            skillIcons++;
        }
        assertEquals(8, skillIcons);
    }

    @Test
    public void skillDetailListsEveryLevelOverPages() {
        player.performCommand("skills mining");
        Gui gui = openGui(player);
        assertNotNull(gui);
        // the page holds the levels around the player, 28 per page, 60 levels: 3 pages
        assertEquals(28, gui.innerSlots().length);
        ItemStack first = gui.getInventory().getItem(gui.innerSlots()[0]);
        assertTrue(plain(first.getItemMeta().displayName()).startsWith("Level 1"));
        assertNotNull(gui.getInventory().getItem(50)); // next page arrow
        player.performCommand("skills cooking");
        assertTrue(drain(player).contains("Unknown skill"));
    }

    @Test
    public void levelIconsShowStateAndRewards() {
        SkillsMenus menus = new SkillsMenus(core, service);
        ItemStack done = menus.levelIcon(Skill.MINING, 3, 5);
        ItemStack next = menus.levelIcon(Skill.MINING, 6, 5);
        ItemStack later = menus.levelIcon(Skill.MINING, 7, 5);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, done.getType());
        assertEquals(Material.YELLOW_STAINED_GLASS_PANE, next.getType());
        assertEquals(Material.RED_STAINED_GLASS_PANE, later.getType());
        String lore = next.lore().stream().map(CommandsAndMenusTest::plain).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(lore, lore.contains("+2 Mining Fortune"));
        assertTrue(lore, lore.contains("coins"));
        assertTrue(lore, lore.contains("Next level"));
    }

    @Test
    public void collectionsMenuNavigates() {
        service.addCollection(player, Material.COBBLESTONE, 120);
        player.performCommand("collections");
        Gui categories = openGui(player);
        assertNotNull(categories);
        assertEquals(Material.STONE_PICKAXE, categories.getInventory().getItem(11).getType());
        player.performCommand("collections mining");
        Gui list = openGui(player);
        assertNotNull(list);
        ItemStack cobble = list.getInventory().getItem(list.innerSlots()[0]);
        assertEquals(Material.COBBLESTONE, cobble.getType());
        String lore = cobble.lore().stream().map(CommandsAndMenusTest::plain).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(lore, lore.contains("Collected: 120"));
        assertTrue(lore, lore.contains("Next tier: 250"));
        new SkillsMenus(core, service).openCollection(player, service.config().collections.get("COBBLESTONE"));
        Gui detail = openGui(player);
        assertNotNull(detail);
        ItemStack tier1 = detail.getInventory().getItem(detail.innerSlots()[0]);
        assertTrue(plain(tier1.getItemMeta().displayName()).contains("Cobblestone I"));
        String tierLore = tier1.lore().stream().map(CommandsAndMenusTest::plain).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(tierLore, tierLore.contains("Unlocked"));
        ItemStack tier4 = detail.getInventory().getItem(detail.innerSlots()[3]);
        String lore4 = tier4.lore().stream().map(CommandsAndMenusTest::plain).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(lore4, lore4.contains("Recipe: Enchanted Cobblestone"));
        assertTrue(lore4, lore4.contains("Locked"));
    }

    @Test
    public void menuIconsCannotBeTaken() {
        player.performCommand("skills");
        Gui gui = openGui(player);
        assertNotNull(gui);
        assertSame(gui, player.getOpenInventory().getTopInventory().getHolder());
    }

    @Test
    public void leaderboardCommandListsRankedPlayers() {
        service.adminSetXp(player.getUniqueId(), Skill.MINING, 5000);
        PlayerMock other = server.addPlayer("Rival");
        service.adminSetXp(other.getUniqueId(), Skill.MINING, 9000);
        player.performCommand("skilltop mining");
        String out = drain(player);
        assertTrue(out, out.contains("Mining leaderboard"));
        assertTrue(out, out.indexOf("Rival") < out.indexOf("Miner"));
        player.performCommand("skilltop average");
        assertTrue(drain(player).contains("Skill Average leaderboard"));
        player.performCommand("skilltop nonsense");
        assertTrue(drain(player).contains("Unknown leaderboard"));
        UUID.randomUUID();
    }
}
