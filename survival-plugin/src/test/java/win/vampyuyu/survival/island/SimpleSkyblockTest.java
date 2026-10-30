package win.vampyuyu.survival.island;

import static org.junit.Assert.*;
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import java.nio.file.Files;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.ModeTravel;
import win.vampyuyu.survival.core.SkyblockWorlds;

public class SimpleSkyblockTest {
    ServerMock server;
    SurvivalPlugin plugin;
    IslandService islands;
    PlayerMock player;
    World survival, sky;
    Island personal;

    @Before public void setup() {
        server = MockBukkit.mock();
        survival = server.addSimpleWorld("world");
        plugin = MockBukkit.load(SurvivalPlugin.class);
        islands = (IslandService) plugin.core().require(IslandApi.class);
        sky = server.getWorld(SkyblockWorlds.WORLD);
        player = server.addPlayer("Owner");
        personal = islands.registry().create(player.getUniqueId(), player.getName(), "classic", 1);
        personal.state(Island.State.READY);
        personal.platformsReady = true;
        floor(islands.defaultSpawn(personal));
    }
    @After public void cleanup() { MockBukkit.unmock(); }
    private void floor(Location at) {
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) at.getBlock().getRelative(x,-1,z).setType(Material.STONE);
    }
    private Location bed(World world, int x) {
        Location at = new Location(world,x,65,0);
        floor(at);
        Bed foot = (Bed) Material.RED_BED.createBlockData();
        foot.setPart(Bed.Part.FOOT); foot.setFacing(BlockFace.EAST);
        at.getBlock().setBlockData(foot);
        Bed head = (Bed) foot.clone(); head.setPart(Bed.Part.HEAD);
        at.getBlock().getRelative(BlockFace.EAST).setBlockData(head);
        return at;
    }
    private void save(Location at, PlayerSetSpawnEvent.Cause cause) {
        plugin.core().modes().onSetSpawn(new PlayerSetSpawnEvent(player,cause,at,false,false,Component.empty()));
    }
    @Test public void inventoriesAreSeparatePerMode() {
        player.teleport(survival.getSpawnLocation());
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND,3));
        player.getEnderChest().addItem(new org.bukkit.inventory.ItemStack(Material.GOLD_INGOT,2));
        player.setLevel(7);
        assertTrue(plugin.core().modes().enterIsland(player));
        assertEquals(sky,player.getWorld());
        assertFalse(player.getInventory().contains(Material.DIAMOND));
        assertFalse(player.getEnderChest().contains(Material.GOLD_INGOT));
        assertEquals(0,player.getLevel());
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIRT,5));
        assertTrue(plugin.core().modes().enterSurvival(player));
        assertTrue(player.getInventory().contains(Material.DIAMOND));
        assertFalse(player.getInventory().contains(Material.DIRT));
        assertTrue(player.getEnderChest().contains(Material.GOLD_INGOT));
        assertEquals(7,player.getLevel());
        assertTrue(plugin.core().modes().enterIsland(player));
        assertTrue(player.getInventory().contains(Material.DIRT));
        assertFalse(player.getInventory().contains(Material.DIAMOND));
    }
    @Test public void survivalPositionIsRemembered() {
        Location spot = new Location(survival,300,70,300);
        floor(spot);
        player.teleport(spot);
        assertTrue(plugin.core().modes().enterIsland(player));
        assertTrue(plugin.core().modes().enterSurvival(player));
        assertEquals(survival,player.getWorld());
        assertTrue(player.getLocation().distance(spot) < 4);
    }
    @Test public void onlyTwoSkyblockWorldsAndNoProgressionFeatures() {
        assertNull(server.getWorld(SkyblockWorlds.HUB));
        assertNull(server.getWorld(SkyblockWorlds.END));
        assertNotNull(server.getWorld(SkyblockWorlds.NETHER));
        assertEquals(java.util.List.of("utility","island","wings","menu"),plugin.features().enabledIds());
        assertNotNull(server.getCommandMap().getCommand("sbmenu"));
        assertNull(server.getCommandMap().getCommand("dungeon"));
    }
    @Test public void bedPointsNeverOverwriteEachOtherAndSurviveReloadingProfiles() throws Exception {
        Location sb = bed(sky,islands.grid().centerOf(personal.cellX));
        Location normal = bed(survival,20);
        assertTrue(plugin.core().modes().enter(player,sb.clone().add(0,0,2)));
        save(sb,PlayerSetSpawnEvent.Cause.BED);
        assertTrue(plugin.core().modes().enterSurvival(player));
        save(normal,PlayerSetSpawnEvent.Cause.BED);
        assertEquals(sky,plugin.core().modes().respawn(player,true).getWorld());
        assertEquals(survival,plugin.core().modes().respawn(player,false).getWorld());
        plugin.core().profiles().shutdown();
        var reloaded = new win.vampyuyu.survival.core.PlayerProfiles(plugin.core().store(),plugin.getLogger());
        var points = reloaded.section(player.getUniqueId(),"mode_respawns",ModeTravel.Points.class,ModeTravel.Points::new);
        assertEquals(sky.getName(),points.skyblock.position.world);
        assertEquals(survival.getName(),points.survival.position.world);
        reloaded.shutdown();
    }
    @Test public void brokenBedFallsBackWithinMode() {
        Location sb=bed(sky,islands.grid().centerOf(personal.cellX));
        plugin.core().modes().enter(player,sb.clone().add(0,0,2));
        save(sb,PlayerSetSpawnEvent.Cause.BED);
        sb.getBlock().setType(Material.AIR);
        assertEquals(islands.overworldHome(personal),plugin.core().modes().respawn(player,true));
        plugin.core().modes().enterSurvival(player);
        Location normal=bed(survival,30);
        save(normal,PlayerSetSpawnEvent.Cause.BED);
        normal.getBlock().setType(Material.AIR);
        assertEquals(survival.getSpawnLocation(),plugin.core().modes().respawn(player,false));
    }
    @Test public void blockedBedAndDestroyedIslandLandingAreRepairedSafely() {
        Location sb=bed(sky,islands.grid().centerOf(personal.cellX)+10);
        plugin.core().modes().enter(player,sb.clone().add(0,0,2));
        save(sb,PlayerSetSpawnEvent.Cause.BED);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=1;y<=3;y++)
            sb.getBlock().getRelative(x,y,z).setType(Material.STONE);
        Location spawn=islands.defaultSpawn(personal);
        spawn.getBlock().getRelative(0,-1,0).setType(Material.AIR);
        Location at=plugin.core().modes().respawn(player,true);
        assertEquals(sky,at.getWorld());
        assertTrue(win.vampyuyu.survival.utility.SafeSpot.isSafe(at));
    }
    @Test public void allSurvivalDimensionsUseSurvivalPointAndSkyNetherUsesSkyPoint() {
        Location sb=islands.defaultSpawn(personal), normal=new Location(survival,30,65,0); floor(normal);
        plugin.core().modes().enter(player,sb); save(sb,PlayerSetSpawnEvent.Cause.PLUGIN);
        plugin.core().modes().enterSurvival(player); save(normal,PlayerSetSpawnEvent.Cause.PLUGIN);
        for(String name: java.util.List.of("world","world_nether","world_the_end",SkyblockWorlds.NETHER)) {
            World w=server.getWorld(name); if(w==null)w=server.addSimpleWorld(name);
            plugin.core().modes().enter(player,new Location(w,0,70,0));
            PlayerRespawnEvent event=new PlayerRespawnEvent(player,sky.getSpawnLocation(),true);
            plugin.core().modes().onRespawn(event);
            assertEquals(name.equals(SkyblockWorlds.NETHER)?sky:survival,event.getRespawnLocation().getWorld());
        }
    }
    @Test public void warpsPortalsPearlsAndOperatorsCannotCrossModes() {
        plugin.core().modes().enterIsland(player);
        player.setOp(true);
        for(var cause: java.util.List.of(PlayerTeleportEvent.TeleportCause.PLUGIN,PlayerTeleportEvent.TeleportCause.COMMAND,
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,PlayerTeleportEvent.TeleportCause.END_PORTAL,
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL,PlayerTeleportEvent.TeleportCause.END_GATEWAY)) {
            PlayerTeleportEvent event=new PlayerTeleportEvent(player,player.getLocation(),survival.getSpawnLocation(),cause);
            plugin.core().modes().onTeleport(event); assertTrue(cause.name(),event.isCancelled());
        }
        assertTrue(plugin.core().modes().enterSurvival(player));
        assertFalse(player.teleport(islands.defaultSpawn(personal)));
        assertTrue(plugin.core().modes().enterIsland(player));
    }
    @Test public void invitationsPreserveBothPersonalIslandsAndPersistMembership() {
        PlayerMock guest=server.addPlayer("Guest");
        Island theirs=islands.registry().create(guest.getUniqueId(),guest.getName(),"classic",1);
        theirs.state(Island.State.READY); floor(islands.defaultSpawn(theirs));
        plugin.core().modes().enterIsland(player); plugin.core().modes().enterIsland(guest);
        assertTrue(islands.invite(player,guest.getUniqueId()).ok());
        assertTrue(islands.accept(guest,player.getName()).ok());
        assertSame(theirs,islands.registry().ownedBy(guest.getUniqueId()).orElseThrow());
        assertTrue(personal.members.containsKey(guest.getUniqueId()));
        var restored=IslandRegistry.fromJson(islands.registry().toJson(),plugin.getLogger(),null);
        assertEquals(theirs.id,restored.ownedBy(guest.getUniqueId()).orElseThrow().id);
        assertTrue(restored.byId(personal.id).orElseThrow().members.containsKey(guest.getUniqueId()));
        assertTrue(islands.teleportHome(guest));
        assertEquals(theirs.id,islands.islandAtLocation(guest.getLocation()).id);
    }
    @Test public void legacyConfigCannotEnableEndAndTemplatesContainNoEndPortal() {
        assertNull(PortalMapper.route(SkyblockWorlds.WORLD,PlayerTeleportEvent.TeleportCause.END_PORTAL,true,true));
        for(var template:islands.config().enabledTemplates()) {
            BlockPlan plan=IslandTemplates.overworld(template.id(),new java.util.Random(1),java.util.List.of(),"",true);
            assertEquals(0,plan.count(Material.END_PORTAL));
            assertEquals(0,plan.count(Material.END_PORTAL_FRAME));
        }
    }
    @Test public void registeredPortalListenersKeepBothNetherRoutesSeparateAndBlockTheSkyblockEnd() {
        plugin.core().modes().enterIsland(player);
        World vanillaNether = server.addSimpleWorld("world_nether");
        var sbPortal = new org.bukkit.event.player.PlayerPortalEvent(player, player.getLocation(),
            vanillaNether.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        server.getPluginManager().callEvent(sbPortal);
        assertFalse(sbPortal.isCancelled());
        assertEquals(SkyblockWorlds.NETHER, sbPortal.getTo().getWorld().getName());
        plugin.core().modes().enterSurvival(player);
        for (String dimension : java.util.List.of("world_nether", "world_the_end")) {
            World to = server.getWorld(dimension); if (to == null) to = server.addSimpleWorld(dimension);
            var event = new org.bukkit.event.player.PlayerPortalEvent(player, player.getLocation(),to.getSpawnLocation(),
                dimension.endsWith("end") ? PlayerTeleportEvent.TeleportCause.END_PORTAL : PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
            server.getPluginManager().callEvent(event);
            assertFalse(event.isCancelled());
            assertEquals(to,event.getTo().getWorld());
        }
        var incoming = new org.bukkit.event.player.PlayerPortalEvent(player,player.getLocation(),sky.getSpawnLocation(),
            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        server.getPluginManager().callEvent(incoming);
        assertTrue(incoming.isCancelled());
    }
    @Test public void persistedModeRecoversWhenVanillaFallsBackToTheWrongWorld() {
        var points=plugin.core().profiles().section(player.getUniqueId(),"mode_respawns",ModeTravel.Points.class,ModeTravel.Points::new);
        points.mode="skyblock";
        plugin.core().modes().onJoin(new org.bukkit.event.player.PlayerJoinEvent(player,Component.empty()));
        assertEquals(sky,player.getWorld());
        points.mode="survival";
        plugin.core().modes().onJoin(new org.bukkit.event.player.PlayerJoinEvent(player,Component.empty()));
        assertEquals(survival,player.getWorld());
    }
    @Test public void vanillaClearingItsSingleSpawnSlotCannotEraseEitherSavedPoint() {
        Location sb=islands.defaultSpawn(personal), normal=new Location(survival,30,65,0); floor(normal);
        plugin.core().modes().enterIsland(player); save(sb,PlayerSetSpawnEvent.Cause.PLUGIN);
        plugin.core().modes().enterSurvival(player); save(normal,PlayerSetSpawnEvent.Cause.PLUGIN);
        save(null,PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN);
        assertEquals(sky,plugin.core().modes().respawn(player,true).getWorld());
        assertEquals(normal,plugin.core().modes().respawn(player,false));
    }

    @Test public void changingModesRestoresTheNativeSlotWithoutChangingSavedBedKinds() {
        Location sb=bed(sky,islands.grid().centerOf(personal.cellX)), normal=bed(survival,30);
        plugin.core().modes().enterIsland(player); save(sb,PlayerSetSpawnEvent.Cause.BED);
        plugin.core().modes().enterSurvival(player); save(normal,PlayerSetSpawnEvent.Cause.BED);
        plugin.core().modes().enterIsland(player);
        assertEquals(sky,player.getRespawnLocation().getWorld());
        plugin.core().modes().enterSurvival(player);
        assertEquals(survival,player.getRespawnLocation().getWorld());
        var points=plugin.core().profiles().section(player.getUniqueId(),"mode_respawns",ModeTravel.Points.class,ModeTravel.Points::new);
        assertEquals("BED",points.skyblock.kind); assertEquals("BED",points.survival.kind);
    }

    @Test public void nativeAnchorRespawnRemainsValidAfterItsLastChargeWasConsumed() {
        World nether=server.addSimpleWorld("world_nether");
        Location anchor=new Location(nether,30,65,0); floor(anchor);
        anchor.getBlock().setType(Material.RESPAWN_ANCHOR);
        plugin.core().modes().enter(player,anchor.clone().add(2,0,0));
        save(anchor,PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR);
        Location nativeTarget=anchor.clone().add(-1,0,0);
        PlayerRespawnEvent event=new PlayerRespawnEvent(player,nativeTarget,false,true);
        plugin.core().modes().onRespawn(event);
        assertEquals(nativeTarget,event.getRespawnLocation());
    }

    @Test public void legacyBedImportUsesTheBedBlockAndStillDetectsItsDestruction() {
        Location sb=bed(sky,islands.grid().centerOf(personal.cellX)+10);
        plugin.core().modes().enterIsland(player);
        // MockBukkit's boolean getter currently always returns null. Supply a raw legacy bed position.
        PlayerMock legacy = new PlayerMock(server,player.getName(),player.getUniqueId()) {
            @Override public Location getRespawnLocation(boolean resolve) { return resolve ? null : sb; }
        };
        var points=plugin.core().profiles().section(player.getUniqueId(),"mode_respawns",ModeTravel.Points.class,ModeTravel.Points::new);
        points.skyblock=null; points.imported=false; points.mode=null;
        plugin.core().modes().onJoin(new org.bukkit.event.player.PlayerJoinEvent(legacy,Component.empty()));
        assertEquals("BED",points.skyblock.kind);
        assertEquals(sb.getBlockX(),points.skyblock.position.toLocation().getBlockX());
        sb.getBlock().setType(Material.AIR);
        assertEquals(islands.overworldHome(personal),plugin.core().modes().respawn(player,true));
    }

}
