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
        world("world"); // the first world is where MockBukkit spawns new players: a plain survival world
        islands.hubWorld = world("sb_hub");
        flow = new JoinFlow(rt, source, p -> arrived.add(p.getName()));
        core.listen(flow);
        // The tests below inject their own keys after MockBukkit has already fired a keyless join for the new player,
        // so they run in the opt-in "stay" mode; the shipped default (hub) has its own tests.
        useConfig("join:\n  no-key: stay\n");
    }

    private void useDefaults() {
        rt.reload(shipped());
    }

    /** Applies a config; unless it sets {@code join:} itself, joins stay in the opt-in "stay" mode. */
    private void useConfig(String yamlText) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(yamlText.contains("join:") ? yamlText : "join:\n  no-key: stay\n" + yamlText);
            rt.reload(new MenuConfig(yaml));
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String chat(PlayerMock player) {
        StringBuilder text = new StringBuilder();
        net.kyori.adventure.text.Component message;
        while ((message = player.nextComponentMessage()) != null) {
            text.append(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return text.toString();
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
        String text = chat(player);
        assertTrue(text, text.contains("CREATE YOUR ISLAND") || text.contains("Island creation is not available"));
    }

    @Test
    public void noKeyStayModeDoesNotMoveAnyone() {
        PlayerMock withIsland = joinWithKey(null, true);
        PlayerMock without = joinWithKey(null, false);
        assertTrue(islands.teleports.isEmpty());
        assertTrue(arrived.contains(withIsland.getName()));
        assertTrue(arrived.contains(without.getName()));
    }

    @Test
    public void shippedDefaultSendsAKeylessJoinerToTheHub() {
        assertEquals(JoinRouter.NoKey.HUB, shipped().noKey);
        useDefaults(); // all defaults
        PlayerMock player = server.addPlayer(); // MockBukkit fires the join: no key in Redis
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(30);
        assertTrue(islands.teleports.contains("hub:" + player.getName()));
    }

    @Test
    public void shippedDefaultNeverMovesAPlayerWhoAlreadyStandsInSkyblock() {
        useDefaults();
        PlayerMock player = playerIn("sb_world");
        flow.route(player, Optional.empty());
        server.getScheduler().performTicks(30);
        assertTrue(islands.teleports.isEmpty());
        assertTrue(arrived.contains(player.getName()));
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
        useConfig("join:\n  no-key: hub\n");
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

    private org.bukkit.event.player.PlayerRespawnEvent respawn(PlayerMock player, Location spawn, boolean bed) {
        var event = new org.bukkit.event.player.PlayerRespawnEvent(player, spawn, bed, false, false,
            org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performTicks(5);
        return event;
    }

    @Test
    public void respawnIsLeftToTheIslandFeatureByDefault() {
        assertEquals("none", shipped().respawnRoute);
        PlayerMock player = playerIn("sb_world");
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 500, 90, 500));
        var event = respawn(player, new Location(world("world"), 3, 64, 3), false);
        assertTrue(islands.teleports.isEmpty());
        assertEquals(3, event.getRespawnLocation().getX(), 0);
    }

    @Test
    public void islandRouteTeleportsHomeAfterTheRespawnWithoutEditingTheLocation() {
        useConfig("respawn:\n  route: island\n");
        PlayerMock player = playerIn("sb_world");
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 500, 90, 500));
        var event = respawn(player, new Location(world("world"), 3, 64, 3), false);
        assertEquals(List.of("home:" + player.getName()), islands.teleports);
        assertEquals("the respawn location is not edited", 3, event.getRespawnLocation().getX(), 0);
    }

    @Test
    public void islandRouteFallsBackToTheHubOnceAndHubRouteGoesStraightThere() {
        useConfig("respawn:\n  route: island\n");
        PlayerMock homeless = playerIn("sb_world");
        respawn(homeless, new Location(world("world"), 3, 64, 3), false);
        assertEquals(List.of("home:" + homeless.getName(), "hub:" + homeless.getName()), islands.teleports);
        islands.teleports.clear();
        useConfig("respawn:\n  route: hub\n");
        PlayerMock other = playerIn("sb_world");
        respawn(other, new Location(world("world"), 3, 64, 3), false);
        assertEquals(List.of("hub:" + other.getName()), islands.teleports);
    }

    @Test
    public void respawnAtABedOrOutsideSkyblockIsLeftAlone() {
        useConfig("respawn:\n  route: island\n");
        PlayerMock player = playerIn("sb_world");
        islands.give(player.getUniqueId(), new Location(world("sb_world"), 500, 90, 500));
        Location bed = new Location(world("world"), 7, 64, 7);
        var bedEvent = respawn(player, bed, true);
        assertEquals(7, bedEvent.getRespawnLocation().getX(), 0);
        PlayerMock survivor = playerIn("world");
        respawn(survivor, bed, false);
        assertTrue(islands.teleports.isEmpty());
    }

    // ------------------------------------------------------------ hub still generating, create-island prompt

    @Test
    public void aHubThatIsNotReadyIsRetriedEveryTwentyTicks() {
        useDefaults();
        islands.hubFailures = 3;
        PlayerMock player = server.addPlayer();
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(JoinFlow.RETRY_TICKS * 6);
        assertEquals("three failures and the fourth attempt succeeds", 4,
            islands.teleports.stream().filter(("hub:" + player.getName())::equals).count());
        assertTrue("the arrival hooks run again once the player really arrived",
            arrived.stream().filter(player.getName()::equals).count() >= 2);
    }

    @Test
    public void aHubThatNeverComesUpIsGivenUpAfterTenRetries() {
        useDefaults();
        islands.hubFailures = 1000;
        PlayerMock player = server.addPlayer();
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(JoinFlow.RETRY_TICKS * 30);
        assertEquals(1 + JoinFlow.RETRY_ATTEMPTS,
            islands.teleports.stream().filter(("hub:" + player.getName())::equals).count());
    }

    @Test
    public void retriesStopWhenThePlayerLeaves() {
        useDefaults();
        islands.hubFailures = 1000;
        PlayerMock player = server.addPlayer();
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(JoinFlow.RETRY_TICKS * 2);
        long before = islands.teleports.size();
        player.disconnect();
        server.getScheduler().performTicks(JoinFlow.RETRY_TICKS * 10);
        assertEquals(before, islands.teleports.size());
    }

    @Test
    public void aHomelessPlayerInTheHubGetsThePromptEvenWhenNothingMovedThem() {
        core.commands().command("island").executor((s, c, l, a) -> true).register();
        useConfig("join:\n  no-key: stay\n");
        PlayerMock player = playerIn("sb_hub");
        flow.route(player, Optional.empty());
        server.getScheduler().performTicks(30);
        assertTrue(islands.teleports.isEmpty());
        assertTrue(chat(player).contains("CREATE YOUR ISLAND"));
    }

    @Test
    public void thePromptIsNotShownToPlayersWithAnIslandOrOutsideTheHub() {
        core.commands().command("island").executor((s, c, l, a) -> true).register();
        useConfig("join:\n  no-key: stay\n");
        PlayerMock owner = playerIn("sb_hub");
        islands.give(owner.getUniqueId(), new Location(world("sb_world"), 0, 64, 0));
        PlayerMock elsewhere = playerIn("sb_world");
        flow.route(owner, Optional.empty());
        flow.route(elsewhere, Optional.empty());
        server.getScheduler().performTicks(30);
        assertFalse(chat(owner).contains("CREATE YOUR ISLAND"));
        assertFalse(chat(elsewhere).contains("CREATE YOUR ISLAND"));
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
