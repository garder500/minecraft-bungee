package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.SkillsApi;

/** Co-op quests: the main chain is shared by the island, the rewards go to the members who helped. */
public class QuestCoopTest extends MenuTestBase {
    private FakeEconomy economy;
    private FakeIslands islands;
    private QuestService quests;
    private PlayerMock alice;
    private PlayerMock bob;
    private IslandInfo island;

    private static QuestDef quest(String id, QuestType type, String target, int amount, List<String> requires,
            double coins, String category) {
        return new QuestDef(id, id, id, type, target, amount, Material.PAPER, requires,
            new QuestDef.Rewards(coins, Map.of(), Map.of()), category);
    }

    @Before
    public void setUp() {
        Map<String, QuestCatalog.Category> categories = new LinkedHashMap<>();
        categories.put("main", new QuestCatalog.Category("main", "Main", "", true, Material.PAPER, ""));
        categories.put("side", new QuestCatalog.Category("side", "Side", "", false, Material.PAPER, ""));
        Map<String, QuestDef> plan = new LinkedHashMap<>();
        plan.put("logs", quest("logs", QuestType.BREAK_BLOCK, "OAK_LOG", 20, List.of(), 250, "main"));
        plan.put("stone", quest("stone", QuestType.BREAK_BLOCK, "STONE", 5, List.of("logs"), 250, "main"));
        plan.put("iron", quest("iron", QuestType.BREAK_BLOCK, "IRON_ORE", 1, List.of("stone"), 250, "main"));
        plan.put("tutorial", quest("tutorial", QuestType.PLACE_BLOCK, "DIRT", 1, List.of("logs"), 10, "side"));
        // an island quest that waits for a personal one (the Hub waits for the Bronze Armor of the Forging side quests)
        plan.put("hub", quest("hub", QuestType.EVENT, "hub_npc:archivist", 1, List.of("tutorial"), 150, "main"));
        MenuConfig config;
        try (win.vampyuyu.survival.data.Db db = win.vampyuyu.survival.data.TestDb.seeded()) {
            config = new MenuConfig(win.vampyuyu.survival.data.Settings.load(db, "menu"), MenuRepo.rows(db),
                QuestCatalog.of(categories, plan, Map.of(), List.of()));
        }
        rt = new MenuRuntime(core, config);
        economy = new FakeEconomy();
        islands = new FakeIslands();
        core.replace(EconomyApi.class, economy);
        core.replace(SkillsApi.class, new FakeSkills());
        core.replace(IslandApi.class, islands);
        quests = new QuestService(rt);
        alice = playerIn("sb_world");
        bob = playerIn("sb_world");
        island = new IslandInfo(UUID.randomUUID(), alice.getUniqueId(), Set.of(alice.getUniqueId(), bob.getUniqueId()),
            "sb_world", 0, 0, new Location(world("sb_world"), 100, 80, 100), 50);
        islands.byPlayer.put(alice.getUniqueId(), island);
        islands.byPlayer.put(bob.getUniqueId(), island);
        quests.sync(alice);
        quests.sync(bob);
        // bob has been on the island long enough
        quests.islandRecord(island).seconds.put(bob.getUniqueId().toString(), 30 * 60);
    }

    private QuestData data(PlayerMock p) {
        return rt.quests(p.getUniqueId());
    }

    @Test
    public void progressIsOneCounterForTheIslandAndEveryContributorIsPaidInFull() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 10);
        quests.record(bob, QuestType.BREAK_BLOCK, "OAK_LOG", 10);
        assertTrue(data(alice).completed.contains("logs"));
        assertTrue("the quest is ticked for the other member as well", data(bob).completed.contains("logs"));
        assertEquals(250, economy.coins(alice.getUniqueId()), 0);
        assertEquals("not divided: bob gets the full reward too", 250, economy.coins(bob.getUniqueId()), 0);
    }

    @Test
    public void progressOfTheChainShowsForEveryMember() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 12);
        assertEquals(12, data(bob).progress.get("logs").intValue());
        assertEquals(12, quests.islandRecord(island).quests.progress.get("logs").intValue());
    }

    @Test
    public void aMemberWhoDidNothingGetsTheQuestTickedButNoReward() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        assertTrue(data(bob).completed.contains("logs"));
        assertEquals(250, economy.coins(alice.getUniqueId()), 0);
        assertEquals(0, economy.coins(bob.getUniqueId()), 0);
    }

    @Test
    public void aMemberNeedsTenPercentOfTheQuest() {
        quests.record(bob, QuestType.BREAK_BLOCK, "OAK_LOG", 1); // 1 of 20 is below 10 %
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 19);
        assertEquals(0, economy.coins(bob.getUniqueId()), 0);
        assertEquals(250, economy.coins(alice.getUniqueId()), 0);
    }

    @Test
    public void aSmallQuestPaysAnyoneWhoDidAtLeastOneAction() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        economy.balances.clear();
        quests.islandRecord(island).coinsToday = 0;
        quests.record(bob, QuestType.BREAK_BLOCK, "STONE", 1);
        quests.record(alice, QuestType.BREAK_BLOCK, "STONE", 4);
        assertTrue(data(bob).completed.contains("stone"));
        assertTrue("1 action is enough when the quest asks 10 or less", economy.reasons.contains("quest:stone"));
        assertEquals(250, economy.coins(bob.getUniqueId()), 0);
    }

    @Test
    public void aMemberWhoJustArrivedIsNotPaidBeforeThirtyMinutes() {
        quests.islandRecord(island).seconds.put(bob.getUniqueId().toString(), 29 * 60);
        quests.record(bob, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        assertTrue(data(bob).completed.contains("logs"));
        assertEquals(0, economy.coins(bob.getUniqueId()), 0);
        assertEquals("the owner is never held back", 0, economy.coins(alice.getUniqueId()), 0);
    }

    @Test
    public void theIslandPaysAtMostItsDailyCoinCapAcrossQuests() {
        double cap = rt.config().coopDailyCoinCap; // 600 by default; the three quests pay 250 each
        assertEquals(600, cap, 0);
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        quests.record(alice, QuestType.BREAK_BLOCK, "STONE", 5);
        quests.record(alice, QuestType.BREAK_BLOCK, "IRON_ORE", 1);
        assertEquals(cap, economy.coins(alice.getUniqueId()), 0);
    }

    @Test
    public void aRewardForAMemberWhoIsAwayWaitsForTheirNextVisit() {
        UUID away = UUID.randomUUID();
        IslandInfo big = new IslandInfo(island.id(), alice.getUniqueId(),
            Set.of(alice.getUniqueId(), bob.getUniqueId(), away), "sb_world", 0, 0, island.center(), 50);
        islands.byPlayer.put(alice.getUniqueId(), big);
        IslandQuests shared = quests.islandRecord(big);
        shared.seconds.put(away.toString(), 60 * 60);
        shared.contributions.computeIfAbsent("logs", k -> new java.util.HashMap<>()).put(away.toString(), 10);
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        assertEquals(Set.of("logs"), shared.owed.get(away.toString()));
        assertEquals(0, economy.coins(away), 0);
    }

    @Test
    public void aNewMemberSeesTheFinishedQuestsTickedAndGetsAKitButNoCoins() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        PlayerMock carol = playerIn("sb_world");
        IslandInfo three = new IslandInfo(island.id(), alice.getUniqueId(),
            Set.of(alice.getUniqueId(), bob.getUniqueId(), carol.getUniqueId()), "sb_world", 0, 0, island.center(), 50);
        islands.byPlayer.put(alice.getUniqueId(), three);
        islands.byPlayer.put(carol.getUniqueId(), three);
        quests.sync(carol);
        assertTrue(data(carol).completed.contains("logs"));
        assertEquals(0, economy.coins(carol.getUniqueId()), 0);
        assertTrue("a starter kit", carol.getInventory().contains(Material.STONE_PICKAXE));
        quests.sync(carol);
        assertEquals("the kit is given once", 1, java.util.Arrays.stream(carol.getInventory().getContents())
            .filter(s -> s != null && s.getType() == Material.STONE_PICKAXE).count());
    }

    @Test
    public void aFanOutEventIsCountedOnceAndCreditsEveryPlayerItReached() {
        Set<UUID> both = Set.of(alice.getUniqueId(), bob.getUniqueId());
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 15, 7, both);
        quests.record(bob, QuestType.BREAK_BLOCK, "OAK_LOG", 15, 7, both);
        assertEquals(15, quests.islandRecord(island).quests.progress.get("logs").intValue());
        assertEquals(15, quests.islandRecord(island).contributions.get("logs").get(bob.getUniqueId().toString()).intValue());
    }

    @Test
    public void personalQuestsStayPersonalAndWaitForTheMainOneTheyNeed() {
        assertFalse(rt.engine().isActive(data(bob), rt.engine().catalog().tutorialQuest("tutorial").get()));
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        assertTrue(rt.engine().isActive(data(bob), rt.engine().catalog().tutorialQuest("tutorial").get()));
        quests.record(bob, QuestType.PLACE_BLOCK, "DIRT", 1);
        assertTrue(data(bob).completed.contains("tutorial"));
        assertFalse("alice did not place dirt", data(alice).completed.contains("tutorial"));
    }

    @Test
    public void progressSurvivesInTheIslandFile() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        quests.flush();
        assertTrue(core.store().exists("islandquests/" + island.id() + ".json"));
    }

    @Test
    public void anIslandResetStartsTheSharedQuestsOver() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        quests.record(bob, QuestType.BREAK_BLOCK, "STONE", 2);
        assertTrue(data(bob).completed.contains("logs"));
        // what IslandService does on /island reset: the owner's own sections go, then the event
        core.listen(new QuestListener(rt, quests));
        core.profiles().removeSections(alice.getUniqueId(), "quests", "quarry");
        org.bukkit.Bukkit.getPluginManager().callEvent(new win.vampyuyu.survival.api.event.IslandResetEvent(island.id(),
            island.members()));
        quests.sync(alice);
        quests.sync(bob);
        assertTrue(quests.islandRecord(island).quests.completed.isEmpty());
        assertTrue(quests.islandRecord(island).quests.progress.isEmpty());
        assertFalse("not rebuilt from the members' old copies", data(alice).completed.contains("logs"));
        assertFalse(data(bob).completed.contains("logs"));
        assertFalse(data(bob).progress.containsKey("stone"));
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        assertTrue("the chain is played again", data(alice).completed.contains("logs"));
    }

    @Test
    public void anIslandQuestWaitingForAPersonalQuestCompletesOnceAMemberHasDoneIt() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        quests.record(alice, QuestType.PLACE_BLOCK, "DIRT", 1);
        assertTrue(data(alice).completed.contains("tutorial"));
        quests.record(alice, QuestType.EVENT, "hub_npc:archivist", 1);
        assertTrue("the island quest opened by alice's personal quest completes", data(alice).completed.contains("hub"));
        assertTrue(data(bob).completed.contains("hub"));
        assertFalse("bob's own side quest is left alone", data(bob).completed.contains("tutorial"));
    }

    @Test
    public void anIslandQuestStuckAtFullProgressCompletesAtTheNextSync() {
        quests.record(alice, QuestType.BREAK_BLOCK, "OAK_LOG", 20);
        // counted while the island record could not see the personal requirement: 1/1, never completed
        quests.record(alice, QuestType.EVENT, "hub_npc:archivist", 1);
        assertFalse(data(alice).completed.contains("hub"));
        double before = economy.coins(alice.getUniqueId());
        data(alice).completed.add("tutorial");
        quests.sync(alice);
        assertTrue(data(alice).completed.contains("hub"));
        assertEquals("alice, who clicked, is paid when it settles", before + 150, economy.coins(alice.getUniqueId()), 0);
        assertTrue(quests.islandRecord(island).quests.completed.contains("hub"));
    }

    @Test
    public void theShippedHubQuestsCompleteOnAnIslandOnceAMemberWearsBronze() {
        MenuConfig config;
        try (win.vampyuyu.survival.data.Db db = win.vampyuyu.survival.data.TestDb.seeded()) {
            config = new MenuConfig(win.vampyuyu.survival.data.Settings.load(db, "menu"), MenuRepo.rows(db), QuestRepo.load(db));
        }
        rt = new MenuRuntime(core, config);
        QuestService shipped = new QuestService(rt);
        IslandInfo other = new IslandInfo(UUID.randomUUID(), alice.getUniqueId(), Set.of(alice.getUniqueId(), bob.getUniqueId()),
            "sb_world", 0, 0, new Location(world("sb_world"), 100, 80, 100), 50);
        islands.byPlayer.put(alice.getUniqueId(), other);
        islands.byPlayer.put(bob.getUniqueId(), other);
        QuestData own = rt.quests(alice.getUniqueId());
        own.completed.add("q145"); // Bronze Armor, a Forging side quest: personal
        shipped.sync(alice);
        double before = economy.coins(alice.getUniqueId());
        shipped.record(alice, QuestType.EVENT, "hub_npc:archivist", 1);
        assertTrue(shipped.islandRecord(other).quests.completed.contains("hub_archivist"));
        assertTrue(own.completed.contains("hub_archivist"));
        assertTrue("the Archivist pays its coins (and the Echo Key with them)", economy.coins(alice.getUniqueId()) > before);
    }
}
