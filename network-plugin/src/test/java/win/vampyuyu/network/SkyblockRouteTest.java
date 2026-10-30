package win.vampyuyu.network;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The click flow of the Skyblock card: key first, then connect (or the local handoff), never twice. */
public class SkyblockRouteTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private SkyblockHandoffTest.FakeStore store;
    private final List<String> events = new ArrayList<>();

    /** A player that records the BungeeCord messages instead of dropping them. */
    private final class Recorder extends PlayerMock {
        Recorder(ServerMock owner, String name) {
            super(owner, name);
        }

        @Override
        public void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
                events.add(channel + ":" + in.readUTF() + ":" + in.readUTF());
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        }
    }

    @Before
    public void boot() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        store = new SkyblockHandoffTest.FakeStore();
        store.timeline = events;
    }

    @After
    public void stop() {
        MockBukkit.unmock();
    }

    private Recorder join(String name) {
        Recorder player = new Recorder(server, name);
        server.addPlayer(player);
        return player;
    }

    /** Async tasks start on a pool thread and post back to the main thread: tick and wait until it is quiet. */
    private void settle() {
        for (int round = 0; round < 40; round++) {
            server.getScheduler().waitAsyncTasksFinished();
            server.getScheduler().performOneTick();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performOneTick();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    public void fromTheLobbyTheKeyIsWrittenBeforeTheConnect() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        route.go(player);
        settle();
        assertEquals(List.of("SETEX", "BungeeCord:Connect:survival"), events);
        assertEquals(1, store.calls.size());
        assertTrue(store.calls.get(0).startsWith("SETEX sb:transfer:" + player.getUniqueId() + " 30 {\"dest\":\"skyblock\",\"ts\":"));
    }

    @Test
    public void whenRedisIsDownThePlayerIsStillConnectedAndToldWhy() {
        store.failing = true;
        SkyblockRoute route = new SkyblockRoute(plugin, "creative", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        route.go(player);
        settle();
        assertEquals(List.of("BungeeCord:Connect:survival"), events);
        boolean told = false;
        Component message;
        while ((message = player.nextComponentMessage()) != null) told |= plain(message).contains("routing is unavailable");
        assertTrue(told);
    }

    @Test
    public void withoutAnyRedisConfigurationItIsAPlainConnect() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, null, "not configured"));
        route.go(join("alex"));
        settle();
        assertEquals(List.of("BungeeCord:Connect:survival"), events);
    }

    @Test
    public void spamClickingSendsOneRequest() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        for (int i = 0; i < 5; i++) route.go(player);
        settle();
        assertEquals(List.of("SETEX", "BungeeCord:Connect:survival"), events);
        route.go(player); // the request is finished: a later click is a new request
        settle();
        assertEquals(events.toString(), 4, events.size());
    }

    @Test
    public void aPlayerWhoLeavesMidRequestIsNotConnectedAndTheKeyIsDropped() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        UUID id = player.getUniqueId();
        route.go(player);
        server.getScheduler().waitAsyncTasksFinished();
        player.disconnect();
        settle();
        assertFalse(events.contains("BungeeCord:Connect:survival"));
        assertTrue(store.calls.contains("DEL sb:transfer:" + id));
    }

    @Test
    public void onSurvivalTheRequestGoesToTheChannelListenerWithTheNonceAndNoRedis() {
        List<String> received = new ArrayList<>();
        PluginMessageListener listener = (channel, player, message) -> {
            String body = new String(message, StandardCharsets.UTF_8);
            boolean trusted = player.getMetadata(SkyblockHandoff.CHANNEL).stream()
                    .anyMatch(value -> body.contains("\"nonce\":\"" + value.asString() + "\""));
            received.add(channel + "|" + player.getName() + "|" + trusted + "|" + body);
        };
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, SkyblockHandoff.CHANNEL, listener);
        SkyblockRoute route = new SkyblockRoute(plugin, "survival", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        route.go(player);
        assertEquals(1, received.size());
        assertTrue(received.get(0), received.get(0).startsWith("vampyuyu:skyblock|alex|true|{\"dest\":\"skyblock\",\"ts\":"));
        assertTrue(received.get(0), received.get(0).contains("\"nonce\":\""));
        assertFalse("a same-server connect would do nothing", events.contains("BungeeCord:Connect:survival"));
        assertTrue("no Redis on the same server", store.calls.isEmpty());
        assertTrue("the metadata only lives during the dispatch", player.getMetadata(SkyblockHandoff.CHANNEL).isEmpty());
    }

    @Test
    public void aPayloadForgedByAClientHasNoMetadataSoAListenerCanRejectIt() {
        List<String> trusted = new ArrayList<>();
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, SkyblockHandoff.CHANNEL, (channel, player, message) -> {
            String body = new String(message, StandardCharsets.UTF_8);
            if (player.getMetadata(SkyblockHandoff.CHANNEL).stream().anyMatch(v -> body.contains(v.asString()))) trusted.add(body);
        });
        Recorder cheater = join("mallory");
        // exactly what Paper does with a packet a modified client sent on the channel
        Bukkit.getMessenger().dispatchIncomingMessage(cheater, SkyblockHandoff.CHANNEL,
                SkyblockHandoff.channelPayload(System.currentTimeMillis(), "island", "guess").getBytes(StandardCharsets.UTF_8));
        assertTrue(trusted.isEmpty());
        SkyblockRoute route = new SkyblockRoute(plugin, "survival", new SkyblockHandoff(plugin, store, null));
        route.go(join("alex"));
        assertEquals("only the real request is trusted", 1, trusted.size());
    }

    @Test
    public void theLandingIsCarriedToTheChannelAndTheGatewayOnlyServesSkyblockWide() {
        List<String> used = new ArrayList<>();
        server.getServicesManager().register(SkyblockGateway.class, player -> used.add("gateway") || true,
                plugin, ServicePriority.Normal);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, SkyblockHandoff.CHANNEL,
                (channel, player, message) -> used.add(new String(message, StandardCharsets.UTF_8)));
        SkyblockRoute route = new SkyblockRoute(plugin, "survival", new SkyblockHandoff(plugin, store, null));
        route.go(join("alex"), SkyblockRoute.Dest.HUB);
        route.go(join("bea"), SkyblockRoute.Dest.SKYBLOCK);
        assertEquals(2, used.size());
        assertTrue(used.get(0), used.get(0).startsWith("{\"dest\":\"hub\","));
        assertEquals("gateway", used.get(1));
    }

    @Test
    public void onSurvivalAGatewayWinsOverTheChannel() {
        List<String> used = new ArrayList<>();
        server.getServicesManager().register(SkyblockGateway.class, player -> used.add("gateway:" + player.getName()),
                plugin, ServicePriority.Normal);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, SkyblockHandoff.CHANNEL,
                (channel, player, message) -> used.add("channel"));
        SkyblockRoute route = new SkyblockRoute(plugin, "survival", new SkyblockHandoff(plugin, store, null));
        route.go(join("alex"));
        assertEquals(List.of("gateway:alex"), used);
    }

    @Test
    public void aGatewayThatDeclinesFallsThroughToTheChannel() {
        List<String> used = new ArrayList<>();
        server.getServicesManager().register(SkyblockGateway.class, player -> false, plugin, ServicePriority.Normal);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, SkyblockHandoff.CHANNEL,
                (channel, player, message) -> used.add("channel"));
        SkyblockHandoff handoff = new SkyblockHandoff(plugin, store, null);
        assertEquals(SkyblockHandoff.Local.CHANNEL, handoff.notifyLocal(join("alex"), "skyblock", 1));
        assertEquals(List.of("channel"), used);
    }

    @Test
    public void aCrashingGatewayDoesNotBreakTheMenu() {
        server.getServicesManager().register(SkyblockGateway.class, player -> {
            throw new IllegalStateException("boom");
        }, plugin, ServicePriority.Normal);
        SkyblockHandoff handoff = new SkyblockHandoff(plugin, store, null);
        assertEquals(SkyblockHandoff.Local.NONE, handoff.notifyLocal(join("alex"), "skyblock", 1));
    }

    @Test
    public void onSurvivalWithNobodyListeningThePlayerStaysPutAndIsToldWithoutTouchingRedis() {
        SkyblockRoute route = new SkyblockRoute(plugin, "survival", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        route.go(player);
        assertFalse(events.contains("BungeeCord:Connect:survival"));
        assertTrue(store.calls.isEmpty());
        boolean told = false;
        Component message;
        while ((message = player.nextComponentMessage()) != null) told |= plain(message).contains("not ready");
        assertTrue(told);
    }

    @Test
    public void onSurvivalRedisBeingDownDoesNotStopTheLocalHandoff() {
        store.failing = true;
        List<String> received = new ArrayList<>();
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, SkyblockHandoff.CHANNEL,
                (channel, player, message) -> received.add(player.getName()));
        SkyblockRoute route = new SkyblockRoute(plugin, "survival", new SkyblockHandoff(plugin, store, null));
        route.go(join("alex"));
        assertEquals(List.of("alex"), received);
    }

    // ---- stale keys: a plain card clears a pending Skyblock key before it connects ----------------------------------

    @Test
    public void survivalCardDeletesTheKeyBeforeTheConnect() {
        store.timeline = events;
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        route.go(player);
        settle();
        route.leave(player, "survival", "Survival");
        settle();
        assertEquals(List.of("SETEX", "BungeeCord:Connect:survival", "DEL", "BungeeCord:Connect:survival"), events);
        assertTrue(store.calls.contains("DEL sb:transfer:" + player.getUniqueId()));
    }

    @Test
    public void leavingWithRedisKnownDownConnectsAtOnceWithoutADelete() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, null, "no redis"));
        route.leave(join("alex"), "creative", "Creative");
        assertEquals("no waiting for a timeout", List.of("BungeeCord:Connect:creative"), events);
    }

    @Test
    public void leavingStillConnectsWhenTheDeleteFails() {
        store.failing = true;
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null));
        route.leave(join("alex"), "survival", "Survival");
        settle();
        assertEquals(List.of("BungeeCord:Connect:survival"), events);
    }

    @Test
    public void skyblockThenSurvivalClicksNeverRunTwoRequestsAtOnce() {
        store.timeline = events;
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        route.go(player);
        route.leave(player, "survival", "Survival"); // the first request is still in flight: ignored
        settle();
        assertEquals("no DEL may overtake the SETEX", List.of("SETEX", "BungeeCord:Connect:survival"), events);
        assertTrue(store.calls.stream().noneMatch(call -> call.startsWith("DEL")));
    }

    @Test
    public void aKnownDownRedisSkipsTheWriteSoThePlayerDoesNotWaitForATimeout() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, null, "no redis"));
        route.go(join("alex"));
        assertEquals(List.of("BungeeCord:Connect:survival"), events);
    }

    @Test
    public void theCooldownAppliesAfterTheRequestFinishes() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null), 60_000);
        Recorder player = join("alex");
        route.go(player);
        settle();
        route.go(player);
        route.leave(player, "survival", "Survival");
        settle();
        assertEquals(List.of("SETEX", "BungeeCord:Connect:survival"), events);
        route.forget(player.getUniqueId()); // a relog starts fresh
        route.go(player);
        settle();
        assertEquals(4, events.size());
    }

    @Test
    public void theLandingIsWrittenIntoTheKey() {
        SkyblockRoute route = new SkyblockRoute(plugin, "lobby", new SkyblockHandoff(plugin, store, null));
        Recorder player = join("alex");
        route.go(player, SkyblockRoute.Dest.ISLAND);
        settle();
        assertTrue(store.calls.get(0), store.calls.get(0).contains("{\"dest\":\"island\",\"ts\":"));
    }

    // ---- click types and the /menu arguments ---------------------------------------------------------------------

    @Test
    public void clickTypesMapToLandings() {
        assertEquals(SkyblockRoute.Dest.SKYBLOCK, WorldSelector.destFor(org.bukkit.event.inventory.ClickType.LEFT));
        assertEquals(SkyblockRoute.Dest.ISLAND, WorldSelector.destFor(org.bukkit.event.inventory.ClickType.SHIFT_LEFT));
        assertEquals(SkyblockRoute.Dest.ISLAND, WorldSelector.destFor(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT));
        assertEquals(SkyblockRoute.Dest.HUB, WorldSelector.destFor(org.bukkit.event.inventory.ClickType.RIGHT));
        assertNull(WorldSelector.destFor(org.bukkit.event.inventory.ClickType.NUMBER_KEY));
        assertNull(WorldSelector.destFor(org.bukkit.event.inventory.ClickType.DROP));
    }

    @Test
    public void menuArgumentsParse() {
        assertEquals(WorldSelector.Target.SKYBLOCK, WorldSelector.Target.parse("Skyblock"));
        assertEquals(WorldSelector.Target.LOBBY, WorldSelector.Target.parse("lobby"));
        assertNull(WorldSelector.Target.parse("nether"));
        assertEquals(SkyblockRoute.Dest.HUB, WorldSelector.parseDest("HUB"));
        assertNull(WorldSelector.parseDest("moon"));
    }

    @Test
    public void theCardWarnsOnlyWhileRoutingIsOffline() {
        String online = tooltip(WorldSelector.Menu.skyblockCard(true));
        String offline = tooltip(WorldSelector.Menu.skyblockCard(false));
        assertFalse(online.contains("offline"));
        assertTrue(offline.contains("Skyblock routing is offline"));
        assertTrue(offline.contains("Survival server instead"));
        assertTrue(online.contains("SKYBLOCK") && online.contains("Left-click to enter"));
        assertTrue(online.contains("Shift-click: go to my island") && online.contains("Right-click: visit the hub"));
        assertEquals(Material.GRASS_BLOCK, WorldSelector.Menu.skyblockCard(true).getType());
    }

    @Test
    public void theCardShowsTheLiveOnlineCountOnlyWhenKnown() {
        assertFalse(tooltip(WorldSelector.Menu.skyblockCard(true, -1)).contains("online"));
        assertFalse(tooltip(WorldSelector.Menu.skyblockCard(true, 0)).contains("online"));
        assertTrue(tooltip(WorldSelector.Menu.skyblockCard(true, 1)).contains("1 player online on the network"));
        assertTrue(tooltip(WorldSelector.Menu.skyblockCard(true, 12)).contains("12 players online on the network"));
    }

    @Test
    public void theMenuShowsTheCardInThePlainAndThePaintedLayouts() {
        var plain = new WorldSelector.Menu(false, true).getInventory();
        assertEquals(Material.GRASS_BLOCK, plain.getItem(13).getType());
        var painted = new WorldSelector.Menu(true, false).getInventory();
        for (int slot : new int[] {3, 4, 5, 12, 13, 14}) {
            ItemStack card = painted.getItem(slot);
            assertNotNull("slot " + slot, card);
            assertTrue(tooltip(card).contains("Skyblock routing is offline"));
        }
        assertNull("the arches stay separate cards", painted.getItem(6) == null ? null : (tooltip(painted.getItem(6)).contains("SKYBLOCK") ? "x" : null));
    }

    @Test
    public void theConnectMessageIsTheBungeeCordFormat() throws Exception {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(WorldSelector.connectMessage("survival")))) {
            assertEquals("Connect", in.readUTF());
            assertEquals("survival", in.readUTF());
            assertEquals(0, in.available());
        }
    }

    private static String tooltip(ItemStack item) {
        StringBuilder text = new StringBuilder(plain(item.getItemMeta().displayName()));
        for (Component line : item.getItemMeta().lore()) text.append('\n').append(plain(line));
        return text.toString();
    }
}
