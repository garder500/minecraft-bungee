package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Location;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.menu.SoulLogic.SoulSpot;

public class FairySoulsTest extends MenuTestBase {
    private FakeEconomy economy;
    private FakeStats stats;
    private FakeIslands islands;
    private FairySouls fairy;
    private PlayerMock player;
    private final FakeDisplays displays = new FakeDisplays();

    /** Records spawned souls and who they are shown to. */
    static final class FakeDisplays implements SoulDisplays {
        final List<FakeHandle> spawned = new ArrayList<>();
        boolean chunksLoaded = true;

        final class FakeHandle implements Handle {
            final SoulSpot spot;
            final Set<UUID> visibleTo = new HashSet<>();
            boolean valid = true;

            final World world;

            FakeHandle(World world, SoulSpot spot) { this.world = world; this.spot = spot; }
            @Override public boolean valid() { return valid; }
            @Override public void remove() { valid = false; }
            @Override public void show(org.bukkit.entity.Player p) { visibleTo.add(p.getUniqueId()); }
            @Override public void hide(org.bukkit.entity.Player p) { visibleTo.remove(p.getUniqueId()); }
            @Override public Location location() { return new Location(world, spot.x(), spot.y(), spot.z()); }
        }

        @Override public boolean chunkLoaded(World world, SoulSpot spot) { return chunksLoaded; }
        @Override public Handle spawn(World world, SoulSpot spot) {
            FakeHandle handle = new FakeHandle(world, spot);
            spawned.add(handle);
            return handle;
        }
        long live() { return spawned.stream().filter(h -> h.valid).count(); }
    }

    @Before
    public void setUp() {
        economy = new FakeEconomy();
        stats = new FakeStats();
        islands = new FakeIslands();
        core.register(EconomyApi.class, economy);
        core.register(StatsApi.class, stats);
        core.register(IslandApi.class, islands);
        fairy = new FairySouls(rt, new QuestService(rt), displays);
        player = playerIn("sb_hub");
    }

    private static SoulSpot spot(String id) {
        return new SoulSpot(id, "sb_hub", 0, 64, 0);
    }

    @Test
    public void aSoulCanOnlyBeFoundOnce() {
        fairy.collect(player, spot("hub_1"));
        fairy.collect(player, spot("hub_1"));
        assertEquals(1, rt.fairy(player.getUniqueId()).found.size());
    }

    @Test
    public void everyFifthSoulPaysCoinsAndStatsExactlyOnce() {
        for (int i = 1; i <= 4; i++) {
            fairy.collect(player, spot("s" + i));
        }
        assertEquals(0, economy.coins(player.getUniqueId()), 0);
        assertTrue(stats.modifiers.isEmpty());
        fairy.collect(player, spot("s5"));
        assertEquals(250, economy.coins(player.getUniqueId()), 0);
        assertEquals(2.0, stats.modifiers.get(Stat.HEALTH), 0);
        assertEquals(1, rt.fairy(player.getUniqueId()).paidMilestones);
        // re-finding and re-checking never pays again
        fairy.collect(player, spot("s5"));
        fairy.payMilestones(player, rt.fairy(player.getUniqueId()));
        fairy.payMilestones(player, rt.fairy(player.getUniqueId()));
        assertEquals(250, economy.coins(player.getUniqueId()), 0);
        for (int i = 6; i <= 10; i++) {
            fairy.collect(player, spot("s" + i));
        }
        assertEquals(500, economy.coins(player.getUniqueId()), 0);
        assertEquals(2.0, stats.modifiers.get(Stat.HEALTH), 0);
        assertEquals(1.0, stats.modifiers.get(Stat.DEFENSE), 0);
    }

    @Test
    public void statsComeBackOnJoinBecauseModifiersAreNotPersisted() {
        for (int i = 1; i <= 5; i++) {
            fairy.collect(player, spot("s" + i));
        }
        stats.modifiers.clear(); // a restart
        double coins = economy.coins(player.getUniqueId());
        fairy.onArrive(player);
        assertEquals(2.0, stats.modifiers.get(Stat.HEALTH), 0);
        assertEquals("arriving must not pay the milestone again", coins, economy.coins(player.getUniqueId()), 0);
    }

    @Test
    public void milestoneCoinsWaitForTheEconomy() {
        core.stop();
        core = new win.vampyuyu.survival.core.Core(plugin);
        core.start();
        rt = new MenuRuntime(core, shipped());
        fairy = new FairySouls(rt, new QuestService(rt));
        for (int i = 1; i <= 5; i++) {
            fairy.collect(player, spot("s" + i));
        }
        FairyData data = rt.fairy(player.getUniqueId());
        assertEquals(250, data.pendingCoins, 0);
        FakeEconomy late = new FakeEconomy();
        core.register(EconomyApi.class, late);
        fairy.payMilestones(player, data);
        fairy.payMilestones(player, data);
        assertEquals(250, late.coins(player.getUniqueId()), 0);
        assertEquals(0, data.pendingCoins, 0);
    }

    @Test
    public void totalCountsFixedAdminAndIslandSouls() {
        int base = fairy.total();
        assertEquals(8, base);
        assertTrue(fairy.addAdmin(new Location(world("sb_hub"), 1, 65, 1), "fountain").isPresent());
        assertEquals(base + 1, fairy.total());
        assertTrue("duplicate ids are refused", fairy.addAdmin(new Location(world("sb_hub"), 2, 65, 2), "fountain").isEmpty());
        assertTrue("island ids are taken too", fairy.addAdmin(new Location(world("sb_hub"), 2, 65, 2), "island_1").isEmpty());
        assertTrue("bad id", fairy.addAdmin(new Location(world("sb_hub"), 2, 65, 2), "Bad Id").isEmpty());
        assertTrue("not a skyblock world", fairy.addAdmin(new Location(world("world"), 2, 65, 2), null).isEmpty());
        assertEquals("soul_1", fairy.addAdmin(new Location(world("sb_hub"), 3, 65, 3), null).orElseThrow());
        assertTrue(fairy.removeAdmin("soul_1"));
        assertFalse(fairy.removeAdmin("soul_1"));
    }

    @Test
    public void adminSoulsSurviveARestart() {
        fairy.addAdmin(new Location(world("sb_hub"), 10, 70, -4), "well");
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);
        assertTrue(core.store().exists("menu/fairy_souls.json"));
        FairySouls again = new FairySouls(rt, new QuestService(rt));
        again.start();
        try {
            assertEquals(List.of("well"), again.adminSouls().stream().map(SoulLogic.SoulDef::id).toList());
        } finally {
            again.stop();
        }
    }

    @Test
    public void soulsAreSpawnedOnlyForPlayersWhoHaveNotFoundThem() {
        Location center = new Location(world("sb_world"), 100, 80, 100);
        islands.give(player.getUniqueId(), center);
        player.teleport(center.clone().add(0, 0, -20));
        PlayerMock other = server.addPlayer();
        other.teleport(center.clone().add(2, 0, 0));
        fairy.start();
        try {
            server.getScheduler().performTicks(25);
            assertEquals("one display per island soul", 8, displays.live());
            assertTrue(displays.spawned.stream().allMatch(h -> h.visibleTo.contains(player.getUniqueId())));
            // the player finds a soul: it disappears for them only
            SoulSpot found = displays.spawned.get(0).spot;
            fairy.collect(player, found);
            server.getScheduler().performTicks(10);
            assertFalse(displays.spawned.get(0).visibleTo.contains(player.getUniqueId()));
            assertTrue(displays.spawned.get(0).visibleTo.contains(other.getUniqueId()));
            long forPlayer = displays.spawned.stream().filter(h -> h.visibleTo.contains(player.getUniqueId())).count();
            long forOther = displays.spawned.stream().filter(h -> h.visibleTo.contains(other.getUniqueId())).count();
            assertEquals(7, forPlayer);
            assertEquals(8, forOther);
            // when nobody is near any more the entities are despawned
            player.teleport(center.clone().add(0, 0, 500));
            other.teleport(center.clone().add(0, 0, -500));
            server.getScheduler().performTicks(20);
            assertEquals(0, displays.live());
        } finally {
            fairy.stop();
        }
        assertEquals("disable removes every soul", 0, displays.live());
    }

    @Test
    public void nothingIsSpawnedIntoUnloadedChunks() {
        Location center = new Location(world("sb_world"), 100, 80, 100);
        islands.give(player.getUniqueId(), center);
        player.teleport(center);
        displays.chunksLoaded = false;
        fairy.start();
        try {
            server.getScheduler().performTicks(25);
            assertEquals(0, displays.spawned.size());
        } finally {
            fairy.stop();
        }
    }

    @Test
    public void walkingOntoASoulCollectsIt() {
        Location center = new Location(world("sb_world"), 300, 80, 300);
        islands.give(player.getUniqueId(), center);
        player.teleport(new Location(center.getWorld(), center.getX() + 4.5, center.getY() + 1.5, center.getZ() + 0.5));
        fairy.start();
        try {
            server.getScheduler().performTicks(25);
            assertTrue(rt.fairy(player.getUniqueId()).found.contains("island_1"));
            assertEquals(1, rt.fairy(player.getUniqueId()).found.size());
        } finally {
            fairy.stop();
        }
    }

    @Test
    public void soulsInDungeonsOrOtherWorldsAreIgnored() {
        PlayerMock outsider = playerIn("world");
        PlayerMock dungeon = playerIn("sb_dungeon_run1");
        fairy.start();
        try {
            server.getScheduler().performTicks(25);
            assertTrue(rt.fairy(outsider.getUniqueId()).found.isEmpty());
            assertTrue(rt.fairy(dungeon.getUniqueId()).found.isEmpty());
        } finally {
            fairy.stop();
        }
    }

    @Test
    public void hintTellsTheDirectionOfTheNearestUncollectedSoul() {
        Location center = new Location(world("sb_world"), 500, 80, 500);
        islands.give(player.getUniqueId(), center);
        player.teleport(center);
        fairy.hint(player);
        StringBuilder text = new StringBuilder();
        net.kyori.adventure.text.Component message;
        while ((message = player.nextComponentMessage()) != null) {
            text.append(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message)).append('\n');
        }
        assertTrue(text.toString(), text.toString().contains("Fairy Souls: 0/8"));
        assertTrue(text.toString(), text.toString().contains("Nearest soul:"));
    }
}
