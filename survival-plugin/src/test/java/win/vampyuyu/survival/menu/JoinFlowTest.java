package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.api.IslandApi;

public class JoinFlowTest extends MenuTestBase {
    private final Map<UUID, String> keys = new ConcurrentHashMap<>();
    private final List<String> arrived = new ArrayList<>();
    private FakeIslands islands;
    private JoinFlow flow;
    private boolean failing;

    @Before
    public void setUp() {
        islands = new FakeIslands();
        core.register(IslandApi.class, islands);
        HandoffSource source = id -> {
            if (failing) {
                throw new java.io.IOException("redis down");
            }
            return keys.remove(id);
        };
        flow = new JoinFlow(rt, source, p -> arrived.add(p.getName()));
        core.listen(flow);
    }

    private PlayerMock joinWithKey(String raw, boolean island) {
        PlayerMock probe = server.addPlayer("Probe" + System.nanoTime() % 1000);
        // the key is addressed to the next player: pre-create the uuid by adding the player first is not possible,
        // so fire the join manually for a player that is already online
        if (raw != null) {
            keys.put(probe.getUniqueId(), raw);
        }
        if (island) {
            islands.give(probe.getUniqueId(), new Location(world("sb_world"), 100, 80, 100));
        }
        flow.onJoin(new org.bukkit.event.player.PlayerJoinEvent(probe, net.kyori.adventure.text.Component.empty()));
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(30);
        return probe;
    }

    @Test
    public void skyblockKeyWithIslandTeleportsHome() {
        PlayerMock player = joinWithKey("{\"dest\":\"skyblock\"}", true);
        assertTrue(islands.teleports.contains("home:" + player.getName()));
        assertFalse(islands.teleports.contains("hub:" + player.getName()));
        assertTrue(arrived.contains(player.getName()));
    }

    @Test
    public void skyblockKeyWithoutIslandGoesToHubWithPrompt() {
        PlayerMock player = joinWithKey("skyblock", false);
        assertTrue(islands.teleports.contains("hub:" + player.getName()));
        String text = "";
        net.kyori.adventure.text.Component message;
        while ((message = player.nextComponentMessage()) != null) {
            text += PlainTextComponentSerializer.plainText().serialize(message);
        }
        assertTrue(text, text.contains("CREATE YOUR ISLAND") || text.contains("Island creation is not available"));
    }

    @Test
    public void noKeyDoesNotMoveAnyone() {
        PlayerMock withIsland = joinWithKey(null, true);
        PlayerMock without = joinWithKey(null, false);
        assertTrue(islands.teleports.isEmpty());
        assertTrue(arrived.contains(withIsland.getName()));
        assertTrue(arrived.contains(without.getName()));
    }

    @Test
    public void keyIsConsumedOnce() {
        PlayerMock player = joinWithKey("skyblock", true);
        assertNull(keys.get(player.getUniqueId()));
    }

    @Test
    public void redisFailureNeverBlocksOrMovesTheJoin() {
        failing = true;
        PlayerMock player = joinWithKey("skyblock", true);
        assertTrue(islands.teleports.isEmpty());
        assertTrue("player is still settled", arrived.contains(player.getName()));
    }

    @Test
    public void noKeyHubModeMovesPlainJoinersToHub() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("join:\n  no-key: hub\n");
        rt.reload(new MenuConfig(yaml));
        PlayerMock player = joinWithKey(null, true);
        assertTrue(islands.teleports.contains("hub:" + player.getName()));
    }

    @Test
    public void dungeonPlayersAreNeverMoved() {
        core.register(DungeonApi.class, p -> true);
        PlayerMock player = joinWithKey("skyblock", true);
        assertTrue(islands.teleports.isEmpty());
        assertTrue(arrived.contains(player.getName()));
    }

    @Test
    public void islandFeatureMissingMeansNoMove() {
        core.stop();
        core = new win.vampyuyu.survival.core.Core(plugin);
        core.start();
        rt = new MenuRuntime(core, shipped());
        flow = new JoinFlow(rt, id -> "skyblock", p -> arrived.add(p.getName()));
        PlayerMock player = server.addPlayer();
        flow.route(player, Optional.of(HandoffPayload.parse("skyblock").orElseThrow()));
        server.getScheduler().performTicks(30);
        assertTrue(islands.teleports.isEmpty());
        assertTrue(arrived.contains(player.getName()));
    }

    @Test
    public void playerWhoLeftDuringTheLookupIsIgnored() {
        PlayerMock player = server.addPlayer();
        player.disconnect();
        flow.route(player, Optional.of(HandoffPayload.parse("skyblock").orElseThrow()));
        server.getScheduler().performTicks(30);
        assertTrue(islands.teleports.isEmpty());
    }

    @Test
    public void respawnInSkyblockGoesToTheIslandSpawn() {
        PlayerMock player = playerIn("sb_world");
        Location center = new Location(world("sb_world"), 500, 90, 500);
        islands.give(player.getUniqueId(), center);
        var event = new org.bukkit.event.player.PlayerRespawnEvent(player, new Location(world("world"), 0, 64, 0), false,
            false, false, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH);
        server.getPluginManager().callEvent(event);
        assertEquals(500, event.getRespawnLocation().getX(), 0);
        assertEquals("sb_world", event.getRespawnLocation().getWorld().getName());
    }

    @Test
    public void respawnAtABedOrOutsideSkyblockIsLeftAlone() {
        PlayerMock player = playerIn("sb_world");
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 500, 90, 500));
        Location bed = new Location(world("world"), 7, 64, 7);
        var bedEvent = new org.bukkit.event.player.PlayerRespawnEvent(player, bed, true, false, false,
            org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH);
        server.getPluginManager().callEvent(bedEvent);
        assertEquals(7, bedEvent.getRespawnLocation().getX(), 0);
        PlayerMock survivor = playerIn("world");
        var plain = new org.bukkit.event.player.PlayerRespawnEvent(survivor, bed, false, false, false,
            org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH);
        server.getPluginManager().callEvent(plain);
        assertEquals(7, plain.getRespawnLocation().getX(), 0);
    }

    @Test
    public void firstArrivalWelcomeIsShownOnce() {
        PlayerMock player = playerIn("sb_hub");
        flow.welcome(player);
        assertTrue(rt.prefs(player.getUniqueId()).welcomed);
        int before = drain(player);
        flow.welcome(player);
        assertEquals(0, drain(player));
        assertTrue(before > 0);
    }

    @Test
    public void welcomeIsNotShownOutsideSkyblock() {
        PlayerMock player = playerIn("world");
        flow.welcome(player);
        assertFalse(rt.prefs(player.getUniqueId()).welcomed);
    }

    private static int drain(PlayerMock player) {
        int n = 0;
        while (player.nextComponentMessage() != null) {
            n++;
        }
        return n;
    }
}
