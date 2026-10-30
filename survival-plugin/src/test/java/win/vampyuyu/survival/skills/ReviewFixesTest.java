package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Test;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.event.SkillLevelUpEvent;

/** Fixes from the review round: guards on enchanted items, sea creatures, quick craft, level-up order, config. */
public class ReviewFixesTest extends SkillsBase {
    private ItemStack enchanted(String id, int amount) {
        return core.items().create(id, amount);
    }

    // ---------------------------------------------------------------- enchanted guard

    @Test
    public void enchantedItemsCannotBePlacedOrPlanted() {
        EnchantedGuard guard = new EnchantedGuard(core, service);
        Block spot = world.getBlockAt(5, 64, 5);
        spot.setType(Material.POTATOES);
        BlockPlaceEvent plant = new BlockPlaceEvent(spot, spot.getState(), spot.getRelative(0, -1, 0),
            enchanted("enchanted_potato", 1), player, true, EquipmentSlot.HAND);
        guard.onPlace(plant);
        assertTrue(plant.isCancelled());

        BlockPlaceEvent plain = new BlockPlaceEvent(spot, spot.getState(), spot.getRelative(0, -1, 0),
            new ItemStack(Material.POTATO), player, true, EquipmentSlot.HAND);
        guard.onPlace(plain);
        assertFalse(plain.isCancelled());
    }

    @Test
    public void enchantedFoodCannotBeEaten() {
        EnchantedGuard guard = new EnchantedGuard(core, service);
        PlayerItemConsumeEvent eat = new PlayerItemConsumeEvent(player, enchanted("enchanted_bread", 1), EquipmentSlot.HAND);
        guard.onConsume(eat);
        assertTrue(eat.isCancelled());
        PlayerItemConsumeEvent plain = new PlayerItemConsumeEvent(player, new ItemStack(Material.BREAD), EquipmentSlot.HAND);
        guard.onConsume(plain);
        assertFalse(plain.isCancelled());
    }

    @Test
    public void enchantedCoalIsNotFuelAndDispensersRefuseIt() {
        EnchantedGuard guard = new EnchantedGuard(core, service);
        Block furnace = world.getBlockAt(6, 64, 6);
        furnace.setType(Material.FURNACE);
        FurnaceBurnEvent burn = new FurnaceBurnEvent(furnace, enchanted("enchanted_coal", 1), 1600);
        guard.onBurn(burn);
        assertTrue(burn.isCancelled());
        FurnaceBurnEvent plain = new FurnaceBurnEvent(furnace, new ItemStack(Material.COAL), 1600);
        guard.onBurn(plain);
        assertFalse(plain.isCancelled());

        Block dispenser = world.getBlockAt(7, 64, 6);
        dispenser.setType(Material.DISPENSER);
        BlockDispenseEvent shot = new BlockDispenseEvent(dispenser, enchanted("enchanted_ender_pearl", 1),
            new org.bukkit.util.Vector(0, 1, 0));
        guard.onDispense(shot);
        assertTrue(shot.isCancelled());
    }

    @Test
    public void rightClickingWithAnEnchantedItemDeniesTheItemUseButNotTheBlock() {
        EnchantedGuard guard = new EnchantedGuard(core, service);
        Block chest = world.getBlockAt(8, 64, 6);
        chest.setType(Material.CHEST);
        PlayerInteractEvent click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            enchanted("enchanted_eye_of_ender", 1), chest, BlockFace.UP);
        guard.onInteract(click);
        assertEquals(org.bukkit.event.Event.Result.DENY, click.useItemInHand());
        assertNotEquals(org.bukkit.event.Event.Result.DENY, click.useInteractedBlock());
        assertNull("opening a chest with the item in hand is not scolded", player.nextMessage());

        Block composter = world.getBlockAt(9, 64, 6);
        composter.setType(Material.COMPOSTER);
        PlayerInteractEvent compost = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            enchanted("enchanted_carrot", 1), composter, BlockFace.UP);
        guard.onInteract(compost);
        assertEquals(org.bukkit.event.Event.Result.DENY, compost.useInteractedBlock());

        PlayerInteractEvent plainClick = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.ENDER_PEARL), chest, BlockFace.UP);
        guard.onInteract(plainClick);
        assertNotEquals(org.bukkit.event.Event.Result.DENY, plainClick.useItemInHand());
    }

    @Test
    public void theRegisteredListenerCancelsPlacingThroughTheRealEventBus() {
        Block spot = world.getBlockAt(12, 64, 12);
        spot.setType(Material.COCOA);
        BlockPlaceEvent plant = new BlockPlaceEvent(spot, spot.getState(), spot.getRelative(0, -1, 0),
            enchanted("enchanted_cocoa", 1), player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(plant);
        assertTrue("EnchantedGuard is registered by the feature", plant.isCancelled());
        PlayerItemConsumeEvent eat = new PlayerItemConsumeEvent(player, enchanted("enchanted_baked_potato", 1),
            EquipmentSlot.HAND);
        server.getPluginManager().callEvent(eat);
        assertTrue(eat.isCancelled());
    }

    @Test
    public void theGuardCanBeSwitchedOff() {
        EnchantedGuard guard = new EnchantedGuard(core, service);
        assertTrue(guard.isEnchanted(enchanted("enchanted_diamond", 1)));
        assertFalse(guard.isEnchanted(new ItemStack(Material.DIAMOND)));
        assertFalse(guard.isEnchanted(null));
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("enchanted_guard.enabled", false);
        service.config(new SkillsConfig(yaml));
        assertFalse(guard.isEnchanted(enchanted("enchanted_diamond", 1)));
    }

    // ---------------------------------------------------------------- result lore and quick craft

    @Test
    public void theResultItemStatesItsRealRecipe() {
        ItemStack diamond = enchanted("enchanted_diamond", 1);
        String lore = diamond.lore().stream().map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(c)).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(lore, lore.contains("5 slots x 32 Diamond"));
        assertFalse(lore, lore.toLowerCase().contains("merchant"));
        ItemStack block = enchanted("enchanted_diamond_block", 1);
        String blockLore = block.lore().stream().map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(c)).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(blockLore, blockLore.contains("5 slots x 16 Enchanted Diamond"));
    }

    private SkillRecipes recipes() {
        return new SkillRecipes(core, service);
    }

    private SkillsConfig.RecipeDef recipe(String id) {
        return service.config().recipes.get(id);
    }

    private int count(org.bukkit.inventory.PlayerInventory inv, Material type, boolean custom) {
        int total = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s != null && s.getType() == type && core.items().isCustom(s) == custom) {
                total += s.getAmount();
            }
        }
        return total;
    }

    @Test
    public void quickCraftTakesTheIngredientsAndGivesTheResult() {
        SkillRecipes recipes = recipes();
        SkillsConfig.RecipeDef def = recipe("enchanted_cobblestone");
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COBBLESTONE, 64),
            new ItemStack(Material.COBBLESTONE, 40));
        assertEquals(SkillRecipes.QuickResult.LOCKED, recipes.quickCraft(player, def));
        assertEquals(168, count(player.getInventory(), Material.COBBLESTONE, false));

        service.adminSetCollection(player.getUniqueId(), service.config().collections.get("COBBLESTONE"), 1_000_000);
        assertEquals(SkillRecipes.QuickResult.OK, recipes.quickCraft(player, def));
        assertEquals(8, count(player.getInventory(), Material.COBBLESTONE, false));
        assertEquals(1, count(player.getInventory(), Material.COBBLESTONE, true));
        assertTrue(core.items().is(player.getInventory().getStorageContents()[firstCustom()], "enchanted_cobblestone"));
        assertEquals(15, service.xp(player.getUniqueId(), Skill.ENCHANTING), 1e-9);

        // not enough left: nothing is taken
        assertEquals(SkillRecipes.QuickResult.MISSING, recipes.quickCraft(player, def));
        assertEquals(8, count(player.getInventory(), Material.COBBLESTONE, false));
        assertEquals(1, count(player.getInventory(), Material.COBBLESTONE, true));
    }

    private int firstCustom() {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && core.items().isCustom(contents[i])) {
                return i;
            }
        }
        throw new AssertionError("no custom item in the inventory");
    }

    @Test
    public void quickCraftNeverCountsEnchantedItemsAsThePlainIngredient() {
        service.adminSetCollection(player.getUniqueId(), service.config().collections.get("COBBLESTONE"), 1_000_000);
        player.getInventory().addItem(enchanted("enchanted_cobblestone", 64), enchanted("enchanted_cobblestone", 64),
            enchanted("enchanted_cobblestone", 64));
        assertEquals(SkillRecipes.QuickResult.MISSING, recipes().quickCraft(player, recipe("enchanted_cobblestone")));
        assertEquals(192, count(player.getInventory(), Material.COBBLESTONE, true));
    }

    @Test
    public void quickCraftOfTheCompactedTierUsesTheTierOneItem() {
        SkillRecipes recipes = recipes();
        service.adminSetCollection(player.getUniqueId(), service.config().collections.get("DIAMOND"), 100_000);
        var def = recipe("enchanted_diamond_block");
        assertNotNull(def);
        for (var need : def.requires().keySet()) {
            service.adminSetCollection(player.getUniqueId(), service.config().collections.get(need), 1_000_000);
        }
        player.getInventory().addItem(enchanted("enchanted_diamond", 80));
        assertEquals(SkillRecipes.QuickResult.OK, recipes.quickCraft(player, def));
        assertEquals(0, count(player.getInventory(), Material.DIAMOND, true));
        assertEquals(1, count(player.getInventory(), Material.DIAMOND_BLOCK, true));
    }

    // ---------------------------------------------------------------- recipe viewer

    @Test
    public void theRecipeViewerShowsTheGridWithRealAmounts() {
        SkillRecipes recipes = recipes();
        RecipeViewer viewer = new RecipeViewer(core, service, recipes);
        viewer.openList(player);
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(10));
        viewer.open(player, recipe("enchanted_diamond"));
        var top = player.getOpenInventory().getTopInventory();
        int slots = 0;
        for (int slot : new int[] {11, 12, 13, 20, 21, 22, 29, 30, 31}) {
            ItemStack shown = top.getItem(slot);
            if (shown != null && shown.getType() == Material.DIAMOND) {
                assertEquals(32, shown.getAmount());
                slots++;
            }
        }
        assertEquals(5, slots);
        assertEquals(Material.DIAMOND, top.getItem(24).getType());
        assertTrue(core.items().is(top.getItem(24), "enchanted_diamond"));
    }

    @Test
    public void recipesCommandOpensTheViewerAndCraftsFromTheInventory() {
        player.performCommand("recipes");
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(10));
        player.performCommand("recipes enchanted_diamond");
        assertTrue(core.items().is(player.getOpenInventory().getTopInventory().getItem(24), "enchanted_diamond"));
        service.adminSetCollection(player.getUniqueId(), service.config().collections.get("DIAMOND"), 1_000_000);
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 160));
        player.performCommand("recipes craft enchanted_diamond");
        assertEquals(1, count(player.getInventory(), Material.DIAMOND, true));
        assertEquals(0, count(player.getInventory(), Material.DIAMOND, false));
    }

    // ---------------------------------------------------------------- sea creatures

    @Test
    public void seaCreatureChanceGrowsWithLevelAndStatAndIsCapped() {
        var cfg = service.config().seaCreatures;
        assertTrue(cfg.enabled());
        double low = SeaCreatures.chance(cfg, 0, 0);
        double level = SeaCreatures.chance(cfg, 40, 0);
        double stat = SeaCreatures.chance(cfg, 40, 10);
        assertTrue(low < level && level < stat);
        assertEquals(cfg.maxChance(), SeaCreatures.chance(cfg, 60, 100_000), 1e-9);
        assertEquals(low, SeaCreatures.chance(cfg, -5, -5), 1e-9);
    }

    @Test
    public void seaCreaturePickRespectsTheLevelGate() {
        var table = service.config().seaCreatures.creatures();
        assertFalse(table.isEmpty());
        for (double roll = 0; roll < 1; roll += 0.01) {
            assertEquals("squid", SeaCreatures.pick(table, 0, roll).id());
        }
        boolean elder = false;
        for (double roll = 0; roll < 1; roll += 0.001) {
            var picked = SeaCreatures.pick(table, 60, roll);
            assertNotNull(picked);
            elder |= picked.id().equals("elder_guardian");
        }
        assertTrue(elder);
        assertNull(SeaCreatures.pick(List.of(), 60, 0.5));
    }

    @Test
    public void aSeaCreatureIsTaggedPaysFishingXpAndRespectsTheCap() {
        SeaCreatures sea = new SeaCreatures(core, service);
        Location water = new Location(world, 40.5, 63, 40.5);
        water.getBlock().setType(Material.WATER);
        assertNull("a failed chance roll spawns nothing", sea.trySpawn(player, water, () -> 0.999, () -> 0));
        LivingEntity mob = sea.trySpawn(player, water, () -> 0.0, () -> 0.0);
        assertNotNull(mob);
        var pdc = mob.getPersistentDataContainer();
        assertTrue(pdc.has(ActivitySources.SEA_CREATURE, PersistentDataType.BYTE));
        assertTrue(pdc.has(ActivitySources.FORCE_XP, PersistentDataType.BYTE));
        assertEquals(1, sea.aliveCount());

        mob.setKiller(player);
        server.getPluginManager().callEvent(new EntityDeathEvent(mob,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), new ArrayList<>()));
        assertEquals(0, service.xp(player.getUniqueId(), Skill.COMBAT), 0);
        assertTrue(service.xp(player.getUniqueId(), Skill.FISHING) > 0);

        for (int i = 0; i < 6; i++) {
            sea.trySpawn(player, water, () -> 0.0, () -> 0.0);
        }
        assertTrue(sea.aliveCount() <= service.config().seaCreatures.maxAlivePerPlayer());
        sea.clear();
        assertEquals(0, sea.aliveCount());
    }

    @Test
    public void noSeaCreatureOutsideOfWater() {
        SeaCreatures sea = new SeaCreatures(core, service);
        Location land = new Location(world, 50.5, 63, 50.5);
        land.getBlock().setType(Material.STONE);
        assertNull(sea.trySpawn(player, land, () -> 0.0, () -> 0.0));
    }

    // ---------------------------------------------------------------- level-up ordering

    @Test
    public void levelUpListenersSeeTheNewStats() {
        List<Double> seen = new ArrayList<>();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onLevel(SkillLevelUpEvent event) {
                seen.add(stats.modifier(player.getUniqueId(), "skill:mining", Stat.MINING_FORTUNE));
            }
        }, plugin);
        service.grant(player, Skill.MINING, service.config().levels.xpFor(1) + 1, "test");
        assertEquals(1, seen.size());
        assertEquals("the stat bonus of level 1 is already applied", 2.0, seen.get(0), 1e-9);
    }

    // ---------------------------------------------------------------- config

    @Test
    public void unlockTextsNoLongerPromiseMechanicsThatDoNotExist() {
        for (Skill skill : Skill.values()) {
            for (String text : service.config().skill(skill).unlocks().values()) {
                String lower = text.toLowerCase();
                assertFalse(text, lower.contains("fall faster") || lower.contains("grow stronger")
                    || lower.contains("think twice") || lower.contains("pickaxe sings"));
            }
        }
    }
}
