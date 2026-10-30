package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.CoinsChangeEvent;
import win.vampyuyu.survival.api.event.SkillLevelUpEvent;

public class QuestServiceTest extends MenuTestBase {
    private FakeEconomy economy;
    private FakeSkills skills;
    private FakeIslands islands;
    private QuestService quests;
    private PlayerMock player;

    @Before
    public void setUp() {
        economy = new FakeEconomy();
        skills = new FakeSkills();
        islands = new FakeIslands();
        core.register(EconomyApi.class, economy);
        core.register(SkillsApi.class, skills);
        core.register(IslandApi.class, islands);
        quests = new QuestService(rt);
        core.listen(new QuestListener(rt, quests));
        player = playerIn("sb_world");
    }

    private QuestData data() {
        return rt.quests(player.getUniqueId());
    }

    private void giveIslandAndSync() {
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 100, 80, 100));
        quests.sync(player);
    }

    @Test
    public void shippedConfigLoadsCleanWithTheExpectedQuestCounts() {
        MenuConfig config = shipped();
        assertEquals(List.of(), config.problems());
        assertEquals(25, config.catalog.dailyPool().size());
        assertTrue(config.catalog.tutorial().size() >= 20);
        assertEquals(3, config.dailyCount);
    }

    @Test
    public void ownershipOfAnIslandCompletesTheFirstQuestOnceAndPaysOnce() {
        giveIslandAndSync();
        assertTrue(data().completed.contains("create_island"));
        assertEquals(100, economy.coins(player.getUniqueId()), 0);
        quests.sync(player);
        quests.sync(player);
        assertEquals("a quest is never paid twice", 100, economy.coins(player.getUniqueId()), 0);
        assertTrue(economy.reasons.contains("quest:create_island"));
    }

    @Test
    public void rewardsInCoinsAndSkillXp() {
        giveIslandAndSync();
        quests.record(player, QuestType.BREAK_BLOCK, "OAK_LOG", 8);
        assertTrue(data().completed.contains("break_wood"));
        assertEquals(150, economy.coins(player.getUniqueId()), 0);
        assertEquals(20, skills.xpGiven.get(Skill.FORAGING), 0);
    }

    @Test
    public void nothingCountsOutsideSkyblockOrInCreative() {
        giveIslandAndSync();
        player.setGameMode(GameMode.CREATIVE);
        quests.record(player, QuestType.BREAK_BLOCK, "OAK_LOG", 8);
        assertFalse(data().completed.contains("break_wood"));
        player.setGameMode(GameMode.SURVIVAL);
        PlayerMock elsewhere = playerIn("world");
        elsewhere.setGameMode(GameMode.SURVIVAL);
        quests.record(elsewhere, QuestType.BREAK_BLOCK, "OAK_LOG", 8);
        assertTrue(rt.quests(elsewhere.getUniqueId()).completed.isEmpty());
    }

    @Test
    public void rewardsWaitWhenEconomyAndSkillsAreMissingAndArePaidExactlyOnce() {
        core.stop();
        core = new win.vampyuyu.survival.core.Core(plugin);
        core.start();
        rt = new MenuRuntime(core, shipped());
        quests = new QuestService(rt);
        core.register(IslandApi.class, islands);
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 100, 80, 100));
        quests.sync(player);
        quests.record(player, QuestType.BREAK_BLOCK, "OAK_LOG", 8);
        assertEquals(150, data().pendingCoins, 0);
        assertEquals(20, data().pendingXp.get("FORAGING"), 0);
        // the features come back
        FakeEconomy late = new FakeEconomy();
        FakeSkills lateSkills = new FakeSkills();
        core.register(EconomyApi.class, late);
        core.register(SkillsApi.class, lateSkills);
        quests.flushPending(player);
        quests.flushPending(player);
        assertEquals(150, late.coins(player.getUniqueId()), 0);
        assertEquals(20, lateSkills.xpGiven.get(Skill.FORAGING), 0);
        assertEquals(0, data().pendingCoins, 0);
        assertTrue(data().pendingXp.isEmpty());
    }

    @Test
    public void allDailiesDoneGivesTheStreakBonusOnce() {
        giveIslandAndSync();
        QuestData d = data();
        d.dailyDay = quests.today().toString();
        d.dailyIds = new java.util.ArrayList<>(List.of("earn_small"));
        d.dailyDone.clear();
        d.completed.add("earn_coins"); // keep the tutorial quest of the same type out of the arithmetic
        double before = economy.coins(player.getUniqueId());
        quests.record(player, QuestType.COINS_EARN, "ANY", 2000);
        assertTrue(d.dailyDone.contains("earn_small"));
        assertEquals(1, d.streak);
        assertTrue(d.dailyBonusPaid);
        // earn_small pays 100, the bonus pays 150 + 50 * 1
        assertEquals(before + 100 + 200, economy.coins(player.getUniqueId()), 0);
        quests.record(player, QuestType.COINS_EARN, "ANY", 2000);
        assertEquals(before + 300, economy.coins(player.getUniqueId()), 0);
        assertEquals(1, d.dailyTotal);
    }

    @Test
    public void trackingValidatesTheQuest() {
        giveIslandAndSync();
        assertTrue(quests.track(player, "break_wood"));
        assertEquals("break_wood", quests.tracked(player).orElseThrow().def().id());
        assertFalse("locked quest", quests.track(player, "craft_pickaxe"));
        assertFalse("unknown quest", quests.track(player, "nope"));
        assertFalse("done quest", quests.track(player, "create_island"));
        assertEquals("break_wood", data().tracked);
    }

    // ------------------------------------------------------------ listener

    private Block block(Material type, int x) {
        Block block = world("sb_world").getBlockAt(x, 70, 0);
        block.setType(type);
        return block;
    }

    private void breakIt(Block block) {
        server.getPluginManager().callEvent(new BlockBreakEvent(block, player));
    }

    @Test
    public void breakingBlocksCountsButPlaceAndBreakLoopsDoNot() {
        giveIslandAndSync();
        for (int i = 0; i < 3; i++) {
            breakIt(block(Material.OAK_LOG, i));
        }
        assertEquals(3, data().progress.get("break_wood").intValue());
        // a log placed by a player does not count when broken
        Block placed = block(Material.OAK_LOG, 10);
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockPlaceEvent(placed, placed.getState(),
            placed, new org.bukkit.inventory.ItemStack(Material.OAK_LOG), player, true,
            org.bukkit.inventory.EquipmentSlot.HAND));
        breakIt(placed);
        assertEquals(3, data().progress.get("break_wood").intValue());
        // the same spot again (not placed this time) counts
        breakIt(block(Material.OAK_LOG, 10));
        assertEquals(4, data().progress.get("break_wood").intValue());
    }

    @Test
    public void onlyFullyGrownCropsCount() {
        giveIslandAndSync();
        data().completed.add("create_island");
        Block young = block(Material.WHEAT, 20);
        Ageable age = (Ageable) young.getBlockData();
        age.setAge(2);
        young.setBlockData(age);
        breakIt(young);
        assertNull(data().progress.get("farm_crops"));
        Block grown = block(Material.WHEAT, 21);
        age = (Ageable) grown.getBlockData();
        age.setAge(age.getMaximumAge());
        grown.setBlockData(age);
        breakIt(grown);
        assertEquals(1, data().progress.get("farm_crops").intValue());
    }

    @Test
    public void coinGainsCountButIgnoredReasonsAndSpendsDoNot() {
        giveIslandAndSync();
        UUID id = player.getUniqueId();
        server.getPluginManager().callEvent(new CoinsChangeEvent(id, 400, 400, "shop:sell"));
        server.getPluginManager().callEvent(new CoinsChangeEvent(id, 5000, 5400, "quest:break_wood"));
        server.getPluginManager().callEvent(new CoinsChangeEvent(id, 5000, 5400, "bank:withdraw"));
        server.getPluginManager().callEvent(new CoinsChangeEvent(id, -300, 100, "shop:buy"));
        assertEquals(400, data().progress.get("earn_coins").intValue());
    }

    @Test
    public void skillLevelUpsBossKillsAndCommandsFeedQuests() {
        giveIslandAndSync();
        data().completed.addAll(List.of("break_wood", "craft_table", "craft_pickaxe", "mine_stone", "earn_coins"));
        server.getPluginManager().callEvent(new SkillLevelUpEvent(player, Skill.MINING, 2, 3));
        assertTrue(data().completed.contains("mining_3"));

        data().completed.addAll(List.of("first_blood", "combat_3"));
        server.getPluginManager().callEvent(new BossKillEvent("grave_king", player,
            List.of(new BossKillEvent.Damager(player.getUniqueId(), 99)), player.getLocation()));
        assertTrue(data().completed.contains("first_boss"));

        core.commands().command("bazaar").aliases("bz").executor((s, c, l, a) -> true).register();
        server.getPluginManager().callEvent(new PlayerCommandPreprocessEvent(player, "/bz"));
        assertTrue("an alias counts as the command", data().completed.contains("open_bazaar"));
    }

    @Test
    public void absoluteQuestsAreSatisfiedByExistingProgress() {
        skills.levels.put("MINING", 7);
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 1, 80, 1));
        data().completed.addAll(List.of("break_wood", "craft_table", "craft_pickaxe"));
        quests.sync(player);
        assertTrue(data().completed.contains("mine_stone") == false);
        data().progress.put("mine_stone", 23);
        quests.record(player, QuestType.BREAK_BLOCK, "STONE", 1);
        assertTrue("mining 3 completes on unlock because the player is level 7", data().completed.contains("mining_3"));
    }
}
