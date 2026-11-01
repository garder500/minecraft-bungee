package win.vampyuyu.survival.items;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.ability.BreakListener;
import win.vampyuyu.survival.items.accessory.AccessoryService;
import win.vampyuyu.survival.items.catalog.SetBonuses;
import win.vampyuyu.survival.items.catalog.SetEffects;
import win.vampyuyu.survival.items.combat.MobScaling;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.ItemRender;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.hud.HudFormat;
import win.vampyuyu.survival.items.hud.XpProgress;
import win.vampyuyu.survival.items.obtain.ItemRecipes;
import win.vampyuyu.survival.items.obtain.LootEntry;
import win.vampyuyu.survival.items.obtain.LootRoller;
import win.vampyuyu.survival.items.obtain.LootService;
import win.vampyuyu.survival.items.obtain.RecipeDef;
import win.vampyuyu.survival.items.pet.PetListener;
import win.vampyuyu.survival.items.pet.PetService;
import win.vampyuyu.survival.items.pet.PetVisuals;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;
import win.vampyuyu.survival.items.stats.AttributeApplier;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** One test per fixed review issue. */
public class ReviewFixesTest {
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

    private StatsEngine engine() {
        return (StatsEngine) core.service(win.vampyuyu.survival.api.StatsApi.class).orElseThrow();
    }

    // ---------------------------------------------------------------- 1 timber

    @Test
    public void timberStacksAreDroppedWhenTheSetIsNotWorn() {
        assertTrue(SetEffects.shouldDropTimber("", System.currentTimeMillis() + 5000, System.currentTimeMillis()));
        assertTrue(SetEffects.shouldDropTimber("miner", System.currentTimeMillis() + 5000, System.currentTimeMillis()));
        assertTrue(SetEffects.shouldDropTimber("lumberjack", 1000L, 2000L));
        assertFalse(SetEffects.shouldDropTimber("lumberjack", 5000L, 2000L));
        assertFalse(SetEffects.shouldDropTimber("", null, 2000L));

        PlayerMock player = server.addPlayer();
        StatsEngine engine = engine();
        SetEffects effects = new SetEffects(engine, new SetBonuses(gear(), settings));
        engine.recompute(player);
        double base = engine.stat(player, Stat.MIGHT);
        effects.addTimber(player);
        effects.addTimber(player);
        assertEquals(base + 20, engine.stat(player, Stat.MIGHT), 1e-9);
        // no lumberjack set is worn: the next tick removes the bonus (it used to stay until the player quit)
        effects.tick(player);
        assertEquals(base, engine.stat(player, Stat.MIGHT), 1e-9);
        assertFalse(effects.hasTimber(player.getUniqueId()));
    }

    // ---------------------------------------------------------------- 2 cooldowns

    @Test
    public void reviveCooldownSurvivesALogout() {
        long now = 1_000_000L;
        assertTrue(PetListener.keepRevive(now + 1, now));
        assertFalse(PetListener.keepRevive(now, now));
        PlayerMock player = server.addPlayer();
        PetListener listener = new PetListener(core, new PetService(core, gear(), engine(),
            new PetVisuals(core, false), settings), engine());
        long ready = System.currentTimeMillis() + 60_000;
        listener.reviveCooldown(player.getUniqueId(), ready);
        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(ready, listener.reviveReadyAt(player.getUniqueId()));
        listener.reviveCooldown(player.getUniqueId(), System.currentTimeMillis() - 1);
        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(0, listener.reviveReadyAt(player.getUniqueId()));
    }

    // ---------------------------------------------------------------- 3 placed blocks

    @Test
    public void placedPumpkinsAndCaneAreTrackedButPlantedWheatIsNot() {
        assertTrue(BreakListener.tracksPlacement(settings, Bukkit.createBlockData(Material.PUMPKIN)));
        assertTrue(BreakListener.tracksPlacement(settings, Bukkit.createBlockData(Material.MELON)));
        assertTrue(BreakListener.tracksPlacement(settings, Bukkit.createBlockData(Material.SUGAR_CANE)));
        assertTrue(BreakListener.tracksPlacement(settings, Bukkit.createBlockData(Material.DIAMOND_ORE)));
        assertTrue(BreakListener.tracksPlacement(settings, Bukkit.createBlockData(Material.OAK_LOG)));
        // seeds are planted by the player but the crop must still give fortune once it has grown
        assertFalse(BreakListener.tracksPlacement(settings, Bukkit.createBlockData(Material.WHEAT)));
        assertFalse(BreakListener.tracksPlacement(settings, Bukkit.createBlockData(Material.DIRT)));
        var young = (org.bukkit.block.data.Ageable) Bukkit.createBlockData(Material.WHEAT);
        assertFalse(BreakListener.mature(young));
        young.setAge(young.getMaximumAge());
        assertTrue(BreakListener.mature(young));
        assertTrue(BreakListener.mature(Bukkit.createBlockData(Material.PUMPKIN)));
    }

    // ---------------------------------------------------------------- 4 drill fuel

    // ---------------------------------------------------------------- 5 accessory bag

    // ---------------------------------------------------------------- 6 obtainability

    @Test
    public void lootOddsFollowMagicFindAndCaps() {
        assertEquals(1.0, LootRoller.chance(1, 0, 1, 100), 1e-9);
        assertEquals(2.0, LootRoller.chance(1, 100, 1, 100), 1e-9);
        assertEquals(1.0, LootRoller.chance(1, -50, 1, 100), 1e-9);
        assertEquals(100, LootRoller.chance(60, 100, 1, 100), 1e-9);
        assertEquals(1.5, LootRoller.chance(1, 0, 1.5, 100), 1e-9);
        LootEntry mob = new LootEntry("item", "pet_candy", 1, 0, "", 1, 1.0, List.of("mob"));
        LootEntry pet = new LootEntry("pet", "wolf", 1, 0, "COMMON", 1, 1.0, List.of("mob:ZOMBIE"));
        List<LootEntry> table = List.of(mob, pet);
        // roll 1.5%: nothing at base odds, the candy with +100 Magic Find, the pet only with Pet Luck and a zombie
        assertTrue(LootRoller.pick(table, "mob", "ZOMBIE", 0, 0, 1, 100, () -> 0.015).isEmpty());
        assertEquals(List.of(mob), LootRoller.pick(table, "mob", "ZOMBIE", 100, 0, 1, 100, () -> 0.015));
        assertEquals(List.of(mob, pet), LootRoller.pick(table, "mob", "ZOMBIE", 100, 100, 1, 100, () -> 0.015));
        assertEquals(List.of(mob), LootRoller.pick(table, "mob", "SPIDER", 100, 100, 1, 100, () -> 0.015));
        assertTrue(LootRoller.pick(table, "boss", "grave_king", 1000, 1000, 1, 100, () -> 0).isEmpty());
        assertTrue(LootService.eligible(true, true, true, false));
        assertFalse(LootService.eligible(false, true, true, false));
        assertFalse(LootService.eligible(true, true, false, false));
        assertFalse(LootService.eligible(true, true, true, true));
    }

    // ---------------------------------------------------------------- 7 health

    @Test
    public void relogKeepsTheHealthRatio() {
        assertEquals(500, AttributeApplier.restoredHealth(-1, 500), 1e-9);
        assertEquals(125, AttributeApplier.restoredHealth(0.25, 500), 1e-9);
        assertEquals(1, AttributeApplier.restoredHealth(0.0, 500), 1e-9);
        assertEquals(500, AttributeApplier.restoredHealth(3, 500), 1e-9);

        var world = server.addSimpleWorld("sb_world");
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        StatsEngine engine = engine();
        engine.recompute(player);
        double cap = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        assertEquals(cap, player.getHealth(), 1e-9);
        player.setHealth(cap * 0.25);
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty(),
            PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(0.25, engine.rememberedHealth(player.getUniqueId()), 1e-6);
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
        engine.recompute(player);
        double capAfter = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        assertEquals(capAfter * 0.25, player.getHealth(), 1e-6);
        assertEquals(-1, engine.rememberedHealth(player.getUniqueId()), 1e-9);
    }

    // ---------------------------------------------------------------- 8 catalog count

    // ---------------------------------------------------------------- 10 xp popup

    @Test
    public void xpPopupShowsProgressWhenTheCurveIsKnown() {
        assertEquals("+3.2 Mining (412/1,000)", HudFormat.xpProgressPopup("Mining", 3.2, 412.7, 1000));
        XpProgress curve = new XpProgress(true, 100, 1.0);
        assertEquals(100, curve.need(5), 1e-9);
        assertEquals(500, curve.cumulative(5), 1e-9);
        assertEquals(5, curve.levelOf(500, 50));
        assertEquals(4, curve.levelOf(499, 50));
        assertEquals(50, curve.levelOf(1e9, 50));
        var progress = XpProgress.progress(curve::cumulative, 4, 50, 450).orElseThrow();
        assertEquals(50, progress.into(), 1e-9);
        assertEquals(100, progress.needed(), 1e-9);
        // inconsistent data (xp outside the level), max level and a broken reach function give no progress
        assertTrue(XpProgress.progress(curve::cumulative, 4, 50, 900).isEmpty());
        assertTrue(XpProgress.progress(curve::cumulative, 50, 50, 5000).isEmpty());
        assertTrue(XpProgress.progress(l -> Double.NaN, 4, 50, 450).isEmpty());
    }

    // ---------------------------------------------------------------- 11 mobs

    @Test
    public void optInMobsAreScaledOnTheirFirstHit() {
        assertEquals(1, MobScaling.leveledHealth(20, 1, 0, 0.15) / 20, 1e-9);
        assertEquals(20 * 5 * 2.5, MobScaling.leveledHealth(20, 5, 10, 0.15), 1e-9);
        assertEquals(3 * 2.0, MobScaling.leveledDamage(3, 10, 0.10), 1e-9);
        assertEquals("[Lv12] Zombie 5/10❤", PlainTextComponentSerializer.plainText().serialize(
            MobScaling.bar(Component.text("Zombie"), 5, 10, 12)));
        assertEquals("Zombie 5/10❤", PlainTextComponentSerializer.plainText().serialize(
            MobScaling.bar(Component.text("Zombie"), 5, 10, 0)));

        var world = server.addSimpleWorld("sb_world");
        var mob = world.spawn(new Location(world, 0, 64, 0), org.bukkit.entity.Zombie.class);
        double vanilla = mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        // a dungeon style mob: CUSTOM spawn reason is never scaled by itself
        server.getPluginManager().callEvent(new CreatureSpawnEvent(mob, CreatureSpawnEvent.SpawnReason.CUSTOM));
        assertEquals(vanilla, mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
        // the other feature opts in with the documented key
        mob.getPersistentDataContainer().set(MobScaling.LEVEL, PersistentDataType.INTEGER, 10);
        server.getPluginManager().callEvent(new EntityDamageEvent(mob, EntityDamageEvent.DamageCause.CUSTOM, 1.0));
        assertEquals(MobScaling.leveledHealth(vanilla, settings.mobHealthMultiplier, 10, settings.mobHealthPerLevel),
            mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
        double scaled = mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        server.getPluginManager().callEvent(new EntityDamageEvent(mob, EntityDamageEvent.DamageCause.CUSTOM, 1.0));
        assertEquals(scaled, mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
    }

    // ---------------------------------------------------------------- config parity

}
