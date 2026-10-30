package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;

public class NpcManagerTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private NpcManager npcs;
    private WorldMock world;
    private PlayerMock alice;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        EconomyService eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        ItemKeys keys = new ItemKeys(plugin.core());
        ShopService shops = new ShopService(plugin.core(), eco, keys, new PricePressure());
        shops.reload(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
            getClass().getResourceAsStream("/economy.yml"), java.nio.charset.StandardCharsets.UTF_8)));
        npcs = new NpcManager(plugin.core(), shops, new ShopUi(plugin.core(), eco, keys, shops));
        npcs.enable();
        world = new WorldMock();
        server.addWorld(world);
        alice = server.addPlayer("Alice");
    }

    @After
    public void tearDown() {
        npcs.disable();
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    @Test
    public void createdNpcIsAnInvulnerableTaggedEntityAndPersistsInTheRegistry() throws Exception {
        var npc = npcs.create("farmer", "", new Location(world, 10, 64, 10));
        assertEquals("npc-1", npc.id);
        assertEquals(1, npcs.liveCount());
        Entity e = world.getEntities().stream().filter(x -> x.getType() == EntityType.VILLAGER).findFirst().orElseThrow();
        assertTrue(e.isInvulnerable());
        assertFalse(e.isPersistent());
        String json = plugin.core().store().readText("economy/npcs.json");
        assertTrue(json.contains("farmer"));
        EntityDamageEvent damage = new EntityDamageEvent(e, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0);
        server.getPluginManager().callEvent(damage);
        assertTrue(damage.isCancelled());
    }

    @Test
    public void rightClickOpensTheShopOnlyForTheMainHand() {
        npcs.create("miner", "", new Location(world, 0, 64, 0));
        Entity e = world.getEntities().stream().filter(x -> x.getType() == EntityType.VILLAGER).findFirst().orElseThrow();
        var off = new PlayerInteractEntityEvent(alice, e, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(off);
        assertTrue(off.isCancelled());
        var main = new PlayerInteractEntityEvent(alice, e, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(main);
        assertTrue(main.isCancelled());
        assertTrue(alice.getOpenInventory().getTopInventory().getHolder() instanceof win.vampyuyu.survival.core.Gui);
    }

    @Test
    public void removeDespawnsAndForgets() {
        var npc = npcs.create("farmer", "", new Location(world, 1, 64, 1));
        assertTrue(npcs.remove(npc.id));
        assertEquals(0, npcs.liveCount());
        assertTrue(world.getEntities().stream().noneMatch(x -> x.getType() == EntityType.VILLAGER));
        assertFalse(npcs.remove(npc.id));
        assertTrue(npcs.nearest(new Location(world, 1, 64, 1), 5).isEmpty());
    }

    @Test
    public void unknownEntityTypeFallsBackToVillager() {
        assertEquals(EntityType.VILLAGER, NpcManager.typeOf("definitely_not_a_type"));
        assertEquals(EntityType.VILLAGER, NpcManager.typeOf("player"));
    }

    @Test
    public void idsDoNotCollideAfterARemoval() {
        var a = npcs.create("farmer", "", new Location(world, 0, 64, 0));
        var b = npcs.create("farmer", "", new Location(world, 5, 64, 0));
        npcs.remove(a.id);
        var c = npcs.create("miner", "", new Location(world, 9, 64, 0));
        assertNotEquals(b.id, c.id);
    }
}
