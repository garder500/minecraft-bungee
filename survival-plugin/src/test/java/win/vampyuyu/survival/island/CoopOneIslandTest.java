package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.event.IslandEnterEvent;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.IslandService.Upgrade;

/** One island per player: accepting a coop invitation, /island delete, the legacy double player. */
public class CoopOneIslandTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private IslandService service;
    private World overworld;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        service = (IslandService) plugin.core().require(IslandApi.class);
        overworld = Bukkit.getWorld(SkyblockWorlds.WORLD);
        assertNotNull("sb_world is created at enable", overworld);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- helpers

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Everything the player was told since the last call. */
    private static String said(PlayerMock player) {
        StringBuilder out = new StringBuilder();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            out.append(plain(next)).append('\n');
        }
        return out.toString();
    }

    private PlayerMock player(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(false);
        return player;
    }

    /** A finished island, as if generation had completed. */
    private Island readyIsland(PlayerMock owner) {
        Island island = service.registry().create(owner.getUniqueId(), owner.getName(), "classic", System.currentTimeMillis());
        island.state(Island.State.READY);
        island.platformsReady = true;
        island.endReady = true;
        return island;
    }

    private Location center(Island island) {
        return new Location(overworld, service.grid().centerOf(island.cellX) + 0.5, 65,
            service.grid().centerOf(island.cellZ) + 0.5);
    }

    private void join(PlayerMock owner, PlayerMock guest) {
        owner.performCommand("island invite " + guest.getName());
        said(owner);
        said(guest);
    }

    @Test
    public void acceptWithASoloIslandWarnsThenDeletesItAndJoins() {
        PlayerMock owner = player("Boss");
        PlayerMock guest = player("Pal");
        Island coop = readyIsland(owner);
        Island solo = readyIsland(guest);
        int count = service.registry().count();
        join(owner, guest);
        guest.performCommand("island accept");
        String warning = said(guest);
        assertTrue(warning, warning.contains("deletes your own island"));
        assertTrue(warning, warning.contains("again within"));
        assertEquals(count, service.registry().count());
        assertTrue(service.registry().ownedBy(guest.getUniqueId()).isPresent());
        assertEquals(IslandRole.VISITOR, coop.roleOf(guest.getUniqueId()));

        guest.performCommand("island accept");
        assertTrue(said(guest).contains("You joined"));
        assertEquals(count - 1, service.registry().count());
        assertTrue(service.registry().ownedBy(guest.getUniqueId()).isEmpty());
        assertTrue(service.registry().byId(solo.id).isEmpty());
        assertTrue(service.registry().byCell(solo.cellX, solo.cellZ).isEmpty());
        assertEquals(IslandRole.MEMBER, coop.roleOf(guest.getUniqueId()));
        assertSame(coop, service.registry().ofPlayer(guest.getUniqueId()).orElseThrow());
        // the cell stays parked until its wipe finishes (the wipe itself is covered with scripted jobs elsewhere)
        assertTrue(service.registry().isDirty(solo.cellX, solo.cellZ));
    }

    @Test
    public void acceptIsRefusedWhenTheOwnIslandHasMembers() {
        PlayerMock owner = player("Boss");
        PlayerMock guest = player("Pal");
        PlayerMock friend = player("Friend");
        Island coop = readyIsland(owner);
        Island mine = readyIsland(guest);
        service.registry().addMember(mine, friend.getUniqueId(), IslandRole.MEMBER);
        join(owner, guest);
        guest.performCommand("island accept");
        String said = said(guest);
        assertTrue(said, said.contains("Transfer your island (/island transfer <player>)"));
        guest.performCommand("island accept");
        assertTrue(service.registry().byId(mine.id).isPresent());
        assertEquals(IslandRole.VISITOR, coop.roleOf(guest.getUniqueId()));
    }

    @Test
    public void acceptWithoutAnIslandJoinsAtOnce() {
        PlayerMock owner = player("Boss");
        PlayerMock guest = player("Pal");
        Island coop = readyIsland(owner);
        join(owner, guest);
        guest.performCommand("island accept");
        assertEquals(IslandRole.MEMBER, coop.roleOf(guest.getUniqueId()));
    }

    @Test
    public void deleteIsRefusedWithMembers() {
        PlayerMock owner = player("Boss");
        PlayerMock guest = player("Pal");
        Island island = readyIsland(owner);
        service.registry().addMember(island, guest.getUniqueId(), IslandRole.MEMBER);
        owner.performCommand("island delete");
        assertTrue(said(owner).contains("Transfer the island or remove its members first."));
        owner.performCommand("island delete confirm");
        assertTrue(service.registry().byId(island.id).isPresent());
    }

    @Test
    public void deleteNeedsAConfirmationAndTheOwnerCanCreateAgain() {
        PlayerMock owner = player("Boss");
        Island island = readyIsland(owner);
        owner.performCommand("island delete");
        assertTrue(said(owner).contains("/island delete confirm"));
        assertTrue(service.registry().byId(island.id).isPresent());
        owner.performCommand("island delete confirm");
        assertTrue(said(owner).contains("was deleted"));
        assertTrue(service.registry().byId(island.id).isEmpty());
        assertFalse(service.registry().hasIsland(owner.getUniqueId()));
        server.getScheduler().performTicks(400);
        owner.performCommand("island create classic");
        assertTrue(said(owner), service.registry().hasIsland(owner.getUniqueId()));
    }

    @Test
    public void deleteConfirmWithoutArmingDeletesNothing() {
        PlayerMock owner = player("Boss");
        Island island = readyIsland(owner);
        owner.performCommand("island delete confirm");
        assertTrue(service.registry().byId(island.id).isPresent());
    }

    @Test
    public void aLegacyDoublePlayerGetsTheCoopAndChoosesKeepCoop() {
        PlayerMock owner = player("Boss");
        PlayerMock legacy = player("Old");
        Island coop = readyIsland(owner);
        Island solo = readyIsland(legacy);
        service.registry().addMember(coop, legacy.getUniqueId(), IslandRole.MEMBER);
        assertSame(coop, service.registry().ofPlayer(legacy.getUniqueId()).orElseThrow());
        assertEquals(coop.id, service.islandOf(legacy.getUniqueId()).orElseThrow().id());
        assertTrue(service.legacyDouble(legacy.getUniqueId()));

        service.notifyLegacy(legacy);
        String told = said(legacy);
        assertTrue(told, told.contains("/island keep-coop") && told.contains("/island leave"));
        service.notifyLegacy(legacy);
        assertEquals("", said(legacy));

        legacy.performCommand("island keep-coop");
        assertTrue(said(legacy).contains("again within"));
        assertTrue(service.registry().byId(solo.id).isPresent());
        legacy.performCommand("island keep-coop");
        assertTrue(service.registry().byId(solo.id).isEmpty());
        assertTrue(service.registry().ownedBy(legacy.getUniqueId()).isEmpty());
        assertEquals(IslandRole.MEMBER, coop.roleOf(legacy.getUniqueId()));
    }

    @Test
    public void aLegacyDoublePlayerCanLeaveTheCoopAndKeepTheIsland() {
        PlayerMock owner = player("Boss");
        PlayerMock legacy = player("Old");
        Island coop = readyIsland(owner);
        Island solo = readyIsland(legacy);
        service.registry().addMember(coop, legacy.getUniqueId(), IslandRole.MEMBER);
        legacy.performCommand("island leave");
        legacy.performCommand("island leave");
        assertEquals(IslandRole.VISITOR, coop.roleOf(legacy.getUniqueId()));
        assertSame(solo, service.registry().ownedBy(legacy.getUniqueId()).orElseThrow());
        assertSame(solo, service.registry().ofPlayer(legacy.getUniqueId()).orElseThrow());
    }

    @Test
    public void aLegacyDoubleWithMembersGetsTheTransferMessageAndLosesNothing() {
        PlayerMock owner = player("Boss");
        PlayerMock legacy = player("Old");
        PlayerMock friend = player("Friend");
        Island coop = readyIsland(owner);
        Island solo = readyIsland(legacy);
        service.registry().addMember(coop, legacy.getUniqueId(), IslandRole.MEMBER);
        service.registry().addMember(solo, friend.getUniqueId(), IslandRole.MEMBER);
        service.notifyLegacy(legacy);
        assertTrue(said(legacy).contains("/island transfer <player>"));
        legacy.performCommand("island keep-coop");
        legacy.performCommand("island keep-coop");
        assertTrue(said(legacy).contains("Transfer your island"));
        assertTrue(service.registry().byId(solo.id).isPresent());
    }

    @Test
    public void nothingIsDeletedByLoggingIn() {
        PlayerMock owner = player("Boss");
        PlayerMock legacy = player("Old");
        Island coop = readyIsland(owner);
        Island solo = readyIsland(legacy);
        service.registry().addMember(coop, legacy.getUniqueId(), IslandRole.MEMBER);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(legacy, Component.empty()));
        server.getScheduler().performTicks(400);
        assertTrue(service.registry().byId(solo.id).isPresent());
        assertTrue(said(legacy).contains("keep-coop"));
    }
}
