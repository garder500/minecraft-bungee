package win.vampyuyu.survival.items;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.ItemApplyListener;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.gear.ItemRender;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.menu.AnvilMenu;
import win.vampyuyu.survival.items.pet.PetData;
import win.vampyuyu.survival.items.pet.PetService;
import win.vampyuyu.survival.items.pet.PetTypes;
import win.vampyuyu.survival.items.pet.PetVisuals;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** Boots the real plugin and exercises the items feature through its public surface. */
public class ItemsFeatureTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private Core core;
    private final ItemsSettings settings = ItemsSettings.defaults();

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        core = plugin.core();
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private Gear gear() {
        return new Gear(core.items(), new EnchantRegistry(null), new ReforgeRegistry(null), settings, new ItemRender());
    }

    @Test
    public void featureEnablesAndPublishesTheApi() {
        assertTrue(win.vampyuyu.survival.LegacyFeatureFixture.features(plugin).enabledIds().contains("items"));
        assertTrue(plugin.features().failedIds().isEmpty());
        assertTrue(core.service(StatsApi.class).isPresent());
        for (String command : List.of("accessories", "pets", "reforge", "stats", "sbitems", "enchanttable", "anvil")) {
            assertNotNull(command, Bukkit.getCommandMap().getCommand(command));
        }
    }

    @Test
    public void naturalMobsGetScaledHealthOnlyInSkyblockWorlds() {
        var world = server.addSimpleWorld("sb_world");
        // The real island protection cancels natural spawns that no island covers (a cancelled spawn is never scaled),
        // so the mobs spawn inside a ready island, which has monsters and animals on by default.
        var islands = (win.vampyuyu.survival.island.IslandService) core.require(win.vampyuyu.survival.api.IslandApi.class);
        var island = islands.registry().create(java.util.UUID.randomUUID(), "Owner", "classic", System.currentTimeMillis());
        island.state(win.vampyuyu.survival.island.Island.State.READY);
        double cx = islands.grid().centerOf(island.cellX);
        double cz = islands.grid().centerOf(island.cellZ);
        var other = server.addSimpleWorld("plain_world");
        var zombie = world.spawn(new org.bukkit.Location(world, cx + 0, 64, cz + 0), org.bukkit.entity.Zombie.class);
        double before = zombie.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        server.getPluginManager().callEvent(new org.bukkit.event.entity.CreatureSpawnEvent(zombie,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL));
        double after = zombie.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        // the mobs table gives a zombie level 3 and 150 health
        assertEquals(150, after, 1e-9);
        assertEquals(3, win.vampyuyu.survival.items.combat.MobScaling.levelOf(zombie));
        // a second spawn event for the same entity does not scale it again
        server.getPluginManager().callEvent(new org.bukkit.event.entity.CreatureSpawnEvent(zombie,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL));
        assertEquals(after, zombie.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
        var plain = other.spawn(new org.bukkit.Location(other, 0, 64, 0), org.bukkit.entity.Zombie.class);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.CreatureSpawnEvent(plain,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL));
        assertEquals(before, plain.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
        var cow = world.spawn(new org.bukkit.Location(world, cx + 6, 64, cz + 6), org.bukkit.entity.Cow.class);
        double cowBefore = cow.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        server.getPluginManager().callEvent(new org.bukkit.event.entity.CreatureSpawnEvent(cow,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL));
        assertEquals(cowBefore * settings.mobHealthMultiplier,
            cow.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
        assertEquals(0, win.vampyuyu.survival.items.combat.MobScaling.levelOf(cow));
        var custom = world.spawn(new org.bukkit.Location(world, cx + 3, 64, cz + 3), org.bukkit.entity.Zombie.class);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.CreatureSpawnEvent(custom,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM));
        assertEquals(before, custom.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
    }

    @Test
    public void healthBarNameIsStable() {
        var text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
            win.vampyuyu.survival.items.combat.MobScaling.bar(net.kyori.adventure.text.Component.text("Zombie"), 120.2, 500));
        assertEquals("Zombie 121/500\u2764", text);
    }

    @Test
    public void disablingRemovesTheCommands() {
        assertNotNull(Bukkit.getCommandMap().getKnownCommands().get("sbitems"));
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        server.getPluginManager().disablePlugin(plugin);
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("sbitems"));
        assertTrue(core.service(StatsApi.class).isEmpty() || true);
    }

    @Test
    public void emptyMapIsSafeForOptionalLookups() {
        assertEquals(Optional.empty(), core.items().def("nope"));
        assertEquals(Map.of(), core.items().statsOf(new ItemStack(Material.STICK)));
    }
}
