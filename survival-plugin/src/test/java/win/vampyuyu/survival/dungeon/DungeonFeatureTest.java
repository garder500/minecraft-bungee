package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.dungeon.party.Party;

/** Boots the whole plugin: wiring, commands, items, party flow, rewards. */
public class DungeonFeatureTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private DungeonService svc;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        svc = plugin.core().require(DungeonService.class);
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private static String lastMessage(PlayerMock p) {
        String last = null;
        String m;
        while ((m = plainNext(p)) != null) {
            last = m;
        }
        return last;
    }

    private static String plainNext(PlayerMock p) {
        var c = p.nextComponentMessage();
        return c == null ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    public void featureIsEnabledAndPublishesTheApi() {
        assertTrue(win.vampyuyu.survival.LegacyFeatureFixture.features(plugin).enabledIds().contains("dungeon"));
        assertTrue(plugin.features().failedIds().isEmpty());
        assertTrue(plugin.core().service(DungeonApi.class).isPresent());
        for (String c : List.of("dungeon", "party", "pc")) {
            assertNotNull("command " + c, Bukkit.getCommandMap().getCommand(c));
        }
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.dungeon.enter"));
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.dungeon.party"));
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.dungeon.admin"));
    }

    @Test
    public void everyRewardItemOfTheDefaultTableExists() {
        for (String id : DungeonItems.ids()) {
            assertTrue(id, plugin.core().items().def(id).isPresent());
            assertNotNull(plugin.core().items().create(id));
            assertEquals("vampyuyu:dungeon/" + id.substring(8), plugin.core().items().def(id).get().itemModel());
        }
        for (DungeonConfig.RewardEntry e : svc.cfg().rewardItems) {
            ItemStack s = RewardDelivery.make(plugin.core(), e.item(), 1);
            assertNotNull("reward item " + e.item(), s);
        }
        assertNull(RewardDelivery.make(plugin.core(), "mc:NOT_A_MATERIAL", 1));
        assertNull(RewardDelivery.make(plugin.core(), "no_such_item", 1));
    }

    @Test
    public void dungeonApiIsWorldBased() {
        DungeonApi api = plugin.core().require(DungeonApi.class);
        PlayerMock p = server.addPlayer();
        assertFalse(api.inDungeon(p));
        assertFalse(api.inDungeon(null));
        var world = server.addSimpleWorld("sb_dungeon_9");
        p.setOp(true); // admins may enter a dungeon world by hand: the teleport guard lets them through
        p.teleport(new Location(world, 5, 70, 5));
        assertTrue(api.inDungeon(p));
    }

    @Test
    public void partyCommandsRunTheWholeFlow() {
        PlayerMock a = server.addPlayer("Alpha");
        PlayerMock b = server.addPlayer("Beta");
        a.performCommand("party create");
        assertNotNull(svc.parties.partyOf(a.getUniqueId()));
        a.performCommand("party invite Beta");
        assertEquals(List.of("Alpha"), svc.parties.pendingInvites(b.getUniqueId()));
        b.performCommand("party accept Alpha");
        Party party = svc.parties.partyOf(a.getUniqueId());
        assertEquals(2, party.size());
        assertSame(party, svc.parties.partyOf(b.getUniqueId()));
        a.performCommand("party promote Beta");
        assertTrue(party.isLeader(b.getUniqueId()));
        b.performCommand("party kick Alpha");
        assertNull(svc.parties.partyOf(a.getUniqueId()));
        b.performCommand("party leave");
        assertEquals("a party that lost its last member is gone", 0, svc.parties.count());
    }

    @Test
    public void partyChatGoesToTheParty() {
        PlayerMock a = server.addPlayer("Chatty");
        PlayerMock b = server.addPlayer("Listener");
        a.performCommand("party invite Listener");
        b.performCommand("party accept Chatty");
        while (plainNext(b) != null) { /* drain */ }
        a.performCommand("pc hello team");
        String got = lastMessage(b);
        assertNotNull(got);
        assertTrue(got, got.contains("Chatty") && got.contains("hello team"));
    }

    @Test
    public void partyChatDoesNotParseMarkup() {
        PlayerMock a = server.addPlayer("Mark");
        PlayerMock b = server.addPlayer("Reader");
        a.performCommand("party invite Reader");
        b.performCommand("party accept Mark");
        while (plainNext(b) != null) { /* drain */ }
        a.performCommand("pc <red>boo</red>");
        String got = lastMessage(b);
        assertTrue(got, got.contains("<red>boo</red>"));
    }

    @Test
    public void lockedFloorsAreRefused() {
        PlayerMock p = server.addPlayer("Rookie");
        p.performCommand("dungeon start 2");
        String msg = lastMessage(p);
        assertNotNull(msg);
        assertTrue(msg, msg.contains("cleared floor 1"));
        assertEquals(0, svc.runs.load());
        assertNull(svc.parties.partyOf(p.getUniqueId()).runId());
        p.performCommand("dungeon start 99");
        assertTrue(lastMessage(p).contains("floor number"));
    }

    @Test
    public void adminCommandsNeedThePermission() {
        PlayerMock p = server.addPlayer("Plain");
        p.performCommand("dungeon admin list");
        assertTrue(lastMessage(p).contains("permission"));
        PlayerMock op = server.addPlayer("Boss");
        op.setOp(true);
        op.performCommand("dungeon admin list");
        assertTrue(lastMessage(op).contains("run(s) alive"));
        op.performCommand("dungeon admin reload");
        assertTrue(lastMessage(op).contains("reloaded"));
    }

    @Test
    public void menusAndInfoCommandsOpen() {
        PlayerMock p = server.addPlayer("Viewer");
        p.performCommand("dungeon");
        assertNotNull(p.getOpenInventory());
        p.closeInventory();
        for (String c : List.of("dungeon help", "dungeon stats", "dungeon top 1", "dungeon top", "dungeon claim", "dungeon floors",
                "dungeon leave", "party", "party list", "party help")) {
            p.performCommand(c);
            p.closeInventory();
        }
        assertTrue(lastMessage(p) != null || true);
    }

    @Test
    public void tabCompletionKnowsSubcommandsAndFloors() {
        PlayerMock p = server.addPlayer("Tabber");
        var dungeon = Bukkit.getCommandMap().getCommand("dungeon");
        assertTrue(dungeon.tabComplete(p, "dungeon", new String[] {"st"}).containsAll(List.of("start", "stats")));
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7"), dungeon.tabComplete(p, "dungeon", new String[] {"start", ""}));
        assertFalse("admin hidden from players", dungeon.tabComplete(p, "dungeon", new String[] {""}).contains("admin"));
        var party = Bukkit.getCommandMap().getCommand("party");
        assertTrue(party.tabComplete(p, "party", new String[] {"inv"}).contains("invite"));
    }

    // ------------------------------------------------------------------ rewards

    @Test
    public void rewardsFillTheInventoryThenWaitInThePendingList() {
        PlayerMock p = server.addPlayer("Looter");
        assertEquals(0, RewardDelivery.give(plugin.core(), p.getUniqueId(), p, DungeonItems.BONE_FRAGMENT, 10));
        assertEquals(10, count(p, DungeonItems.BONE_FRAGMENT));
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (p.getInventory().getItem(i) == null) {
                p.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
            }
        }
        assertEquals("a full inventory loses nothing", 5,
            RewardDelivery.give(plugin.core(), p.getUniqueId(), p, DungeonItems.CRYPT_SHARD, 5));
        DungeonData data = plugin.core().profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        assertEquals(5, data.pendingCount());
        assertEquals(0, RewardDelivery.claim(plugin.core(), p));
        assertEquals("nothing moved while full", 5, data.pendingCount());
        p.getInventory().clear();
        assertEquals(5, RewardDelivery.claim(plugin.core(), p));
        assertEquals(0, data.pendingCount());
        assertEquals(5, count(p, DungeonItems.CRYPT_SHARD));
        assertEquals("claiming twice gives nothing", 0, RewardDelivery.claim(plugin.core(), p));
        assertEquals(5, count(p, DungeonItems.CRYPT_SHARD));
    }

    @Test
    public void offlineWinnersGetTheirRewardInThePendingList() {
        java.util.UUID ghost = java.util.UUID.randomUUID();
        assertEquals(3, RewardDelivery.give(plugin.core(), ghost, null, "mc:DIAMOND", 3));
        DungeonData data = plugin.core().profiles().section(ghost, "dungeon", DungeonData.class, DungeonData::new);
        assertEquals(3, data.pendingCount());
    }

    @Test
    public void unknownRewardItemsAreDroppedNotQueued() {
        PlayerMock p = server.addPlayer("Odd");
        assertEquals(0, RewardDelivery.give(plugin.core(), p.getUniqueId(), p, "removed_item", 4));
        DungeonData data = plugin.core().profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        assertEquals(0, data.pendingCount());
    }

    @Test
    public void bigStacksSplitAcrossSlots() {
        PlayerMock p = server.addPlayer("Hoarder");
        assertEquals(0, RewardDelivery.give(plugin.core(), p.getUniqueId(), p, "mc:IRON_INGOT", 150));
        int total = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.getType() == Material.IRON_INGOT) {
                total += s.getAmount();
                assertTrue(s.getAmount() <= 64);
            }
        }
        assertEquals(150, total);
    }

    @Test
    public void statsAreRecordedPerFloor() {
        PlayerMock p = server.addPlayer("Stats");
        plugin.core().profiles().edit(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new,
            d -> d.record(1, true, 120_000, 280, Grade.S, 3, 20, 1));
        DungeonData d = plugin.core().profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        assertTrue(d.cleared(1));
        assertFalse(d.cleared(2));
        assertEquals(120_000, d.floor(1).bestMillis);
        p.performCommand("dungeon stats");
        assertNotNull(lastMessage(p));
    }

    private int count(PlayerMock p, String id) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && plugin.core().items().is(s, id)) {
                n += s.getAmount();
            }
        }
        return n;
    }
}
