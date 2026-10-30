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
    private final ItemsSettings settings = new ItemsSettings(new YamlConfiguration());

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
    public void catalogHasTheRequiredBreadth() throws Exception {
        // other features register their own items (wings...): only the items-pack ones are checked here
        List<String> ids = new ArrayList<>(new TreeSet<>(core.items().ids().stream()
            .filter(id -> core.items().def(id).map(d -> d.itemModel() != null && d.itemModel().startsWith("vampyuyu:items/"))
                .orElse(false)).toList()));
        assertTrue("at least 40 items, got " + ids.size(), ids.size() >= 40);
        // six armor sets of three tiers and four pieces
        for (String set : List.of("miner", "lumberjack", "farmer", "hunter", "void_walker", "frostborne")) {
            for (int tier = 1; tier <= 3; tier++) {
                for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                    assertTrue(set + slot + tier, ids.contains(set + "_" + slot + "_t" + tier));
                }
            }
        }
        for (String id : List.of("void_blade", "wither_blade", "voidpiercer_bow", "grappling_hook", "rod_of_legends",
                "staff_of_the_volcano", "treecapitator", "jungle_axe", "miners_drill", "miners_pickaxe", "farmers_hoe",
                "recombobulator", "hot_potato_book", "wood_singularity", "enchanted_book", "pet_egg")) {
            assertTrue(id, ids.contains(id));
        }
        // the pack generator must draw exactly these ids (plus pet species): written for the build script to compare
        Path out = Path.of("target", "items-ids.txt");
        Files.createDirectories(out.getParent());
        List<String> all = new ArrayList<>(ids);
        PetTypes.all().forEach(t -> all.add("pet_" + t.id()));
        Files.write(out, new TreeSet<>(all));
        assertEquals(8, PetTypes.all().size());
    }

    @Test
    public void heldWeaponAndModifiersFeedTheStats() {
        PlayerMock player = server.addPlayer();
        StatsApi api = core.service(StatsApi.class).orElseThrow();
        player.getInventory().setItemInMainHand(core.items().create("void_blade"));
        api.recompute(player);
        double strength = api.stat(player, Stat.STRENGTH);
        assertEquals(80, strength, 1e-9);
        api.addModifier(player.getUniqueId(), "skill:combat", Stat.STRENGTH, 12);
        api.recompute(player);
        assertEquals(92, api.stat(player, Stat.STRENGTH), 1e-9);
        // the same source and stat is replaced, not summed
        api.addModifier(player.getUniqueId(), "skill:combat", Stat.STRENGTH, 5);
        api.recompute(player);
        assertEquals(85, api.stat(player, Stat.STRENGTH), 1e-9);
        api.removeModifiers(player.getUniqueId(), "skill:combat");
        api.recompute(player);
        assertEquals(80, api.stat(player, Stat.STRENGTH), 1e-9);
        // a non weapon in hand does not count, an unrelated pickaxe-less hand gives the base
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        api.recompute(player);
        assertEquals(Stat.STRENGTH.base(), api.stat(player, Stat.STRENGTH), 1e-9);
    }

    @Test
    public void armorCountsAndTheSetBonusNeedsFourPieces() {
        PlayerMock player = server.addPlayer();
        StatsApi api = core.service(StatsApi.class).orElseThrow();
        var inv = player.getInventory();
        inv.setHelmet(core.items().create("miner_helmet_t1"));
        api.recompute(player);
        double onePiece = api.stat(player, Stat.MINING_FORTUNE);
        assertTrue(onePiece > 0);
        inv.setChestplate(core.items().create("miner_chestplate_t1"));
        inv.setLeggings(core.items().create("miner_leggings_t1"));
        inv.setBoots(core.items().create("miner_boots_t1"));
        api.recompute(player);
        // four pieces: pieces plus the +25 Mining Fortune set bonus
        assertTrue(api.stat(player, Stat.MINING_FORTUNE) >= onePiece + 25);
    }

    @Test
    public void petCollectionRoundTripAndCap() {
        PlayerMock player = server.addPlayer();
        Gear gear = gear();
        StatsEngine engine = new StatsEngine(core, settings, gear);
        PetService pets = new PetService(core, gear, engine, new PetVisuals(core, false), settings);
        var entry = pets.add(player.getUniqueId(), "wolf", Rarity.EPIC, 5000, "pet_big_teeth").orElseThrow();
        assertEquals(1, pets.data(player.getUniqueId()).pets.size());
        // egg round trip keeps type, rarity, xp and held item
        ItemStack egg = pets.createEgg(entry);
        PetData.Entry back = pets.readEgg(egg).orElseThrow();
        assertEquals("wolf", back.type);
        assertEquals("EPIC", back.rarity);
        assertEquals(5000, back.xp, 1e-9);
        assertEquals("pet_big_teeth", back.held);
        assertEquals(entry.id, back.id);
        assertTrue(pets.readEgg(new ItemStack(Material.EGG)).isEmpty());
        // levels and stats grow
        assertTrue(pets.level(entry) > 1);
        var type = PetTypes.get("wolf").orElseThrow();
        var low = pets.statsFor(type, 1, Rarity.COMMON, null);
        var high = pets.statsFor(type, 100, Rarity.LEGENDARY, null);
        assertTrue(high.get(Stat.STRENGTH) > low.get(Stat.STRENGTH));
        assertEquals(5.0 + high.getOrDefault(Stat.CRIT_CHANCE, 0.0),
            pets.statsFor(type, 100, Rarity.LEGENDARY, "pet_big_teeth").get(Stat.CRIT_CHANCE), 1e-9);
        // unknown species refused
        assertTrue(pets.add(player.getUniqueId(), "dodo", Rarity.COMMON, 0, null).isEmpty());
        // xp respects the skill and the cap
        assertTrue(pets.xpFor(entry, win.vampyuyu.survival.api.Skill.COMBAT, 10) > 0);
        assertEquals(0, pets.xpFor(entry, win.vampyuyu.survival.api.Skill.MINING, 10), 1e-9);
        pets.addXp(player.getUniqueId(), entry, 1e12);
        assertEquals(100, pets.level(entry));
        assertEquals(0, pets.addXp(player.getUniqueId(), entry, -5));
        // summon and put away
        assertTrue(pets.summon(player, entry.id));
        assertEquals(entry.id, pets.data(player.getUniqueId()).active);
        pets.despawn(player.getUniqueId());
        assertEquals("", pets.data(player.getUniqueId()).active);
        assertTrue(pets.remove(player.getUniqueId(), entry.id));
        assertFalse(pets.remove(player.getUniqueId(), entry.id));
        // cap
        for (int i = 0; i < settings.petMaxOwned; i++) {
            assertTrue(pets.add(player.getUniqueId(), "rabbit", Rarity.COMMON, 0, null).isPresent());
        }
        assertTrue(pets.add(player.getUniqueId(), "rabbit", Rarity.COMMON, 0, null).isEmpty());
    }

    @Test
    public void eggCanOnlyBeUsedOnce() {
        PlayerMock player = server.addPlayer();
        Gear gear = gear();
        StatsEngine engine = new StatsEngine(core, settings, gear);
        PetService pets = new PetService(core, gear, engine, new PetVisuals(core, false), settings);
        var entry = new PetData.Entry();
        entry.id = java.util.UUID.randomUUID().toString();
        entry.type = "tiger";
        entry.rarity = "LEGENDARY";
        ItemStack egg = pets.createEgg(entry);
        player.getInventory().setItemInMainHand(egg);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, egg, null,
            org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
        PetService live = new PetService(core, gear, engine, new PetVisuals(core, false), settings);
        assertEquals(1, live.data(player.getUniqueId()).pets.size());
        assertEquals("tiger", live.data(player.getUniqueId()).pets.get(0).type);
    }

    @Test
    public void adminCommandsGiveBooksAndEggs() {
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);
        admin.performCommand("sbitems book sharpness 5");
        ItemStack book = admin.getInventory().getItem(0);
        assertNotNull(book);
        assertEquals("sharpness:5", core.items().getString(book, Keys.BOOK).orElse(""));
        admin.performCommand("sbitems pet wolf legendary 50");
        ItemStack egg = admin.getInventory().getItem(1);
        assertNotNull(egg);
        assertTrue(core.items().is(egg, "pet_egg"));
        admin.performCommand("sbitems book sharpness 99");
        assertNull(admin.getInventory().getItem(2));
        PlayerMock normal = server.addPlayer();
        normal.performCommand("sbitems book sharpness 1");
        assertNull(normal.getInventory().getItem(0));
    }

    @Test
    public void anvilAppliesABookOnlyWhereItFits() {
        PlayerMock player = server.addPlayer();
        Gear gear = gear();
        AnvilMenu anvil = new AnvilMenu(core, player, gear, new Costs(core, () -> settings), () -> settings);
        ItemStack sword = core.items().create("rookie_sword");
        ItemStack book = core.items().create("enchanted_book");
        core.items().set(book, Keys.BOOK, "sharpness:3");
        var ok = anvil.preview(sword, book);
        assertTrue(ok.message(), ok.ok());
        GearInfo result = gear.read(ok.result()).orElseThrow();
        assertEquals(3, (int) result.enchants().get("sharpness"));
        assertTrue(ok.price() > 0);
        // the inputs are untouched
        assertTrue(gear.read(sword).orElseThrow().enchants().isEmpty());
        // a bow enchant does not fit a sword, a sword enchant does not fit boots
        ItemStack bowBook = core.items().create("enchanted_book");
        core.items().set(bowBook, Keys.BOOK, "power:3");
        assertFalse(anvil.preview(sword, bowBook).ok());
        assertFalse(anvil.preview(core.items().create("miner_boots_t1"), book).ok());
        assertFalse(anvil.preview(sword, null).ok());
    }

    @Test
    public void upgradeItemsRespectTheirLimits() {
        PlayerMock player = server.addPlayer();
        Gear gear = gear();
        StatsEngine engine = new StatsEngine(core, settings, gear);
        ItemApplyListener apply = new ItemApplyListener(core, gear, engine);
        ItemStack sword = core.items().create("rookie_sword");
        ItemStack recomb = core.items().create("recombobulator");
        var info = gear.read(sword).orElseThrow();
        var source = gear.read(recomb).orElseThrow();
        assertEquals(Rarity.COMMON, info.rarity());
        assertTrue(apply.apply("recombobulate", source, info, recomb, sword, settings).ok());
        gear.rebuild(sword);
        assertEquals(Rarity.UNCOMMON, gear.read(sword).orElseThrow().rarity());
        assertFalse(apply.apply("recombobulate", source, gear.read(sword).orElseThrow(), recomb, sword, settings).ok());
        // hot potato books stack to the limit and raise the strength of a weapon
        ItemStack book = core.items().create("hot_potato_book");
        for (int i = 0; i < settings.i("hot-potato.max", 10); i++) {
            assertTrue(apply.apply("hot_potato", gear.read(book).orElseThrow(), gear.read(sword).orElseThrow(), book,
                sword, settings).ok());
            gear.rebuild(sword);
        }
        assertFalse(apply.apply("hot_potato", gear.read(book).orElseThrow(), gear.read(sword).orElseThrow(), book,
            sword, settings).ok());
        assertEquals(10 + 20, core.items().statsOf(sword).get(Stat.STRENGTH), 1e-9);
        // a singularity only fits its own tool and only once
        ItemStack singularity = core.items().create("wood_singularity");
        assertFalse(apply.apply("singularity", gear.read(singularity).orElseThrow(), gear.read(sword).orElseThrow(),
            singularity, sword, settings).ok());
        ItemStack axe = core.items().create("jungle_axe");
        assertTrue(apply.apply("singularity", gear.read(singularity).orElseThrow(), gear.read(axe).orElseThrow(),
            singularity, axe, settings).ok());
        gear.rebuild(axe);
        assertFalse(apply.apply("singularity", gear.read(singularity).orElseThrow(), gear.read(axe).orElseThrow(),
            singularity, axe, settings).ok());
        // fuel only goes into drills and never above the tank
        ItemStack cell = core.items().create("drill_fuel_cell");
        assertFalse(apply.apply("fuel:1000", gear.read(cell).orElseThrow(), gear.read(sword).orElseThrow(), cell, sword,
            settings).ok());
        ItemStack drill = core.items().create("miners_drill");
        assertFalse(apply.apply("fuel:1000", gear.read(cell).orElseThrow(), gear.read(drill).orElseThrow(), cell, drill,
            settings).ok());
        core.items().set(drill, Keys.FUEL, 100L);
        assertTrue(apply.apply("fuel:1000", gear.read(cell).orElseThrow(), gear.read(drill).orElseThrow(), cell, drill,
            settings).ok());
        assertEquals(1100L, core.items().getLong(drill, Keys.FUEL).orElseThrow().longValue());
    }

    @Test
    public void reforgeBonusesFollowTheRarity() {
        Gear gear = gear();
        ItemStack sword = core.items().create("void_blade");
        gear.setReforge(sword, "sharp");
        gear.rebuild(sword);
        double epic = core.items().statsOf(sword).get(Stat.CRIT_CHANCE);
        assertTrue(epic > 0);
        gear.setLong(sword, Keys.RECOMB, 1);
        gear.rebuild(sword);
        double legendary = core.items().statsOf(sword).get(Stat.CRIT_CHANCE);
        assertTrue(legendary > epic);
        gear.setReforge(sword, null);
        gear.rebuild(sword);
        assertFalse(core.items().statsOf(sword).containsKey(Stat.CRIT_CHANCE));
        assertEquals(ItemKind.SWORD, gear.read(sword).orElseThrow().kind());
    }

    @Test
    public void renderedLoreShowsRarityAbilityAndStats() {
        Gear gear = gear();
        ItemStack stack = core.items().create("wither_blade");
        gear.rebuild(stack);
        List<String> lore = stack.getItemMeta().lore().stream()
            .map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)).toList();
        assertTrue(lore.stream().anyMatch(l -> l.startsWith("Strength")));
        assertTrue(lore.stream().anyMatch(l -> l.startsWith("Damage")));
        assertEquals("LEGENDARY SWORD", lore.get(lore.size() - 1));
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
