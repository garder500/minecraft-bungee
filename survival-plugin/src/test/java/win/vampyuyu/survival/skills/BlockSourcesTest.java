package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;

public class BlockSourcesTest extends SkillsBase {
    private Block block(int x, int y, int z, Material type) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(type);
        return b;
    }

    private void breakIt(Block block) {
        server.getPluginManager().callEvent(new BlockBreakEvent(block, player));
    }

    private void place(Block block) {
        server.getPluginManager().callEvent(new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0),
            new ItemStack(block.getType()), player, true, EquipmentSlot.HAND));
    }

    private double mining() {
        return service.xp(player.getUniqueId(), Skill.MINING);
    }

    @Test
    public void naturalOreGivesExperience() {
        breakIt(block(1, 64, 1, Material.DIAMOND_ORE));
        assertEquals(18, mining(), 1e-9);
        breakIt(block(2, 64, 1, Material.OAK_LOG));
        assertEquals(6, service.xp(player.getUniqueId(), Skill.FORAGING), 1e-9);
    }

    @Test
    public void placedBlocksGiveNothingWhenBroken() {
        Block ore = block(3, 64, 3, Material.DIAMOND_ORE);
        place(ore);
        breakIt(ore);
        assertEquals(0, mining(), 0);
        // the mark is consumed by the break: the same position holds a natural block afterwards
        ore.setType(Material.DIAMOND_ORE);
        breakIt(ore);
        assertEquals(18, mining(), 1e-9);
    }

    @Test
    public void placingAnUntrackedBlockClearsOldMarks() {
        Block spot = block(4, 64, 4, Material.STONE);
        place(spot);
        spot.setType(Material.DIRT);
        place(spot); // dirt is not tracked: the stale mark disappears
        spot.setType(Material.STONE);
        breakIt(spot);
        assertEquals(1, mining(), 1e-9);
    }

    @Test
    public void cropsNeedToBeFullyGrown() {
        Block wheat = block(5, 64, 5, Material.WHEAT);
        Ageable young = (Ageable) wheat.getBlockData();
        young.setAge(3);
        wheat.setBlockData(young);
        breakIt(wheat);
        assertEquals(0, service.xp(player.getUniqueId(), Skill.FARMING), 0);
        Block ripe = block(6, 64, 5, Material.WHEAT);
        Ageable full = (Ageable) ripe.getBlockData();
        full.setAge(full.getMaximumAge());
        ripe.setBlockData(full);
        breakIt(ripe);
        assertEquals(3, service.xp(player.getUniqueId(), Skill.FARMING), 1e-9);
    }

    @Test
    public void breakingTheSameSpotRepeatedlyDiminishes() {
        double total = 0;
        double last = 1;
        for (int i = 0; i < 10; i++) {
            double before = mining();
            breakIt(block(7, 64, 7, Material.STONE));
            last = mining() - before;
            total += last;
        }
        assertTrue("total " + total, total < 7);
        assertTrue("last " + last, last <= 0.4 + 1e-9);
        // a different spot is untouched
        double before = mining();
        breakIt(block(20, 64, 20, Material.STONE));
        assertEquals(1, mining() - before, 1e-9);
    }

    @Test
    public void creativeAndWrongWorldsEarnNothing() {
        player.setGameMode(GameMode.CREATIVE);
        breakIt(block(8, 64, 8, Material.DIAMOND_ORE));
        player.setGameMode(GameMode.SURVIVAL);
        org.bukkit.World other = server.addSimpleWorld("plain_world");
        Block far = other.getBlockAt(1, 64, 1);
        far.setType(Material.DIAMOND_ORE);
        breakIt(far);
        assertEquals(0, mining(), 0);
    }

    private BlockDropItemEvent drops(Block block, Material type, int amount) {
        var state = block.getState();
        Item item = world.dropItem(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(type, amount));
        return new BlockDropItemEvent(block, state, player, new java.util.ArrayList<>(List.of(item)));
    }

    @Test
    public void dropsFeedCollectionsOnlyForNaturalBlocks() {
        Block natural = block(9, 64, 9, Material.STONE);
        BlockDropItemEvent event = drops(natural, Material.COBBLESTONE, 3);
        breakIt(natural);
        server.getPluginManager().callEvent(event);
        assertEquals(3, service.data(player.getUniqueId()).collected("COBBLESTONE"));

        Block fake = block(10, 64, 9, Material.STONE);
        place(fake);
        BlockDropItemEvent placedDrop = drops(fake, Material.COBBLESTONE, 5);
        breakIt(fake);
        server.getPluginManager().callEvent(placedDrop);
        assertEquals(3, service.data(player.getUniqueId()).collected("COBBLESTONE"));

        // a drop event without a matching break (another plugin broke the block) gives nothing
        Block alien = block(11, 64, 9, Material.STONE);
        server.getPluginManager().callEvent(drops(alien, Material.COBBLESTONE, 9));
        assertEquals(3, service.data(player.getUniqueId()).collected("COBBLESTONE"));
    }

    @Test
    public void immatureCropDropsAreNotCollected() {
        Block wheat = block(12, 64, 9, Material.WHEAT);
        BlockDropItemEvent event = drops(wheat, Material.WHEAT, 1);
        breakIt(wheat); // age 0
        server.getPluginManager().callEvent(event);
        assertEquals(0, service.data(player.getUniqueId()).collected("WHEAT"));
    }

    /** Rebuilds the config from the shipped skills.yml after an edit. */
    private void configure(java.util.function.Consumer<org.bukkit.configuration.file.YamlConfiguration> edit) {
        try (var in = getClass().getClassLoader().getResourceAsStream("skills.yml")) {
            var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            edit.accept(yaml);
            service.config(new SkillsConfig(yaml));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void fortuneIsOffByDefaultBecauseTheItemsFeatureOwnsIt() {
        assertFalse(service.config().fortuneEnabled);
        stats.flat.put(Stat.MINING_FORTUNE, 100.0);
        Block stone = block(15, 64, 9, Material.STONE);
        BlockDropItemEvent event = drops(stone, Material.COBBLESTONE, 4);
        breakIt(stone);
        server.getPluginManager().callEvent(event);
        assertEquals(4, service.data(player.getUniqueId()).collected("COBBLESTONE"));
        assertEquals(1, world.getEntitiesByClass(Item.class).stream()
            .filter(i -> i.getItemStack().getType() == Material.COBBLESTONE).count());
    }

    @Test
    public void fortuneAlsoAppliesToBlocksThatDropTheirOwnMaterial() {
        configure(y -> y.set("fortune.enabled", true));
        stats.flat.put(Stat.FORAGING_FORTUNE, 100.0);
        Block log = block(16, 64, 9, Material.OAK_LOG);
        BlockDropItemEvent event = drops(log, Material.OAK_LOG, 1);
        breakIt(log);
        server.getPluginManager().callEvent(event);
        assertEquals(2, service.data(player.getUniqueId()).collected("OAK_LOG"));
    }

    @Test
    public void silkTouchToolsGetNoFortune() {
        configure(y -> y.set("fortune.enabled", true));
        stats.flat.put(Stat.MINING_FORTUNE, 100.0);
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        pick.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1);
        player.getInventory().setItemInMainHand(pick);
        Block stone = block(17, 64, 9, Material.STONE);
        BlockDropItemEvent event = drops(stone, Material.STONE, 3);
        breakIt(stone);
        server.getPluginManager().callEvent(event);
        assertEquals(3, world.getEntitiesByClass(Item.class).stream()
            .filter(i -> i.getItemStack().getType() == Material.STONE).mapToInt(i -> i.getItemStack().getAmount()).sum());
    }

    @Test
    public void miningFortuneAddsDropsAndCollections() {
        configure(y -> y.set("fortune.enabled", true));
        stats.flat.put(Stat.MINING_FORTUNE, 100.0);
        Block stone = block(13, 64, 9, Material.STONE);
        BlockDropItemEvent event = drops(stone, Material.COBBLESTONE, 4);
        breakIt(stone);
        server.getPluginManager().callEvent(event);
        assertEquals(8, service.data(player.getUniqueId()).collected("COBBLESTONE"));
        int extra = world.getEntitiesByClass(Item.class).stream()
            .filter(i -> i.getItemStack().getType() == Material.COBBLESTONE).mapToInt(i -> i.getItemStack().getAmount()).sum();
        assertEquals(8, extra); // the original 4 plus 4 more on the ground
    }

    @Test
    public void plantedCropsPayWhenTheyAreRipe() {
        // the real flow: plant (BlockPlaceEvent), wait, harvest. Planting must not mark the crop.
        Block wheat = block(50, 64, 50, Material.WHEAT);
        place(wheat);
        Ageable crop = (Ageable) wheat.getBlockData();
        crop.setAge(crop.getMaximumAge());
        wheat.setBlockData(crop);
        BlockDropItemEvent event = drops(wheat, Material.WHEAT, 1);
        breakIt(wheat);
        server.getPluginManager().callEvent(event);
        assertEquals(3, service.xp(player.getUniqueId(), Skill.FARMING), 1e-9);
        assertEquals(1, service.data(player.getUniqueId()).collected("WHEAT"));

        Block wart = block(51, 64, 50, Material.NETHER_WART);
        place(wart);
        Ageable ripe = (Ageable) wart.getBlockData();
        ripe.setAge(ripe.getMaximumAge());
        wart.setBlockData(ripe);
        breakIt(wart);
        assertEquals(7, service.xp(player.getUniqueId(), Skill.FARMING), 1e-9);
    }

    @Test
    public void plantingAndBreakingASeedlingStillGivesNothing() {
        Block wheat = block(52, 64, 50, Material.WHEAT);
        place(wheat);
        breakIt(wheat); // age 0
        assertEquals(0, service.xp(player.getUniqueId(), Skill.FARMING), 0);
    }

    @Test
    public void trackedTypesExcludeCropsButKeepResourceBlocks() {
        var tracked = service.config().trackedPlaced;
        assertFalse(tracked.contains(Material.WHEAT));
        assertFalse(tracked.contains(Material.NETHER_WART));
        assertFalse(tracked.contains(Material.COCOA));
        assertTrue(tracked.contains(Material.STONE));
        assertTrue(tracked.contains(Material.SUGAR_CANE));
        assertTrue(tracked.contains(Material.PUMPKIN));
    }

    @Test
    public void caneColumnPaysForGrownBlocksButNotThePlacedBase() {
        Block base = block(60, 64, 60, Material.SUGAR_CANE);
        place(base); // hand-placed base is marked
        block(60, 65, 60, Material.SUGAR_CANE);
        block(60, 66, 60, Material.SUGAR_CANE);
        breakIt(base);
        // two grown blocks above: 2 xp each and one collection count each, the base gives nothing
        assertEquals(4, service.xp(player.getUniqueId(), Skill.FARMING), 1e-9);
        assertEquals(2, service.data(player.getUniqueId()).collected("SUGAR_CANE"));
    }

    @Test
    public void naturalCaneBaseAlsoPaysItself() {
        Block base = block(61, 64, 60, Material.SUGAR_CANE);
        block(61, 65, 60, Material.SUGAR_CANE);
        BlockDropItemEvent event = drops(base, Material.SUGAR_CANE, 1);
        breakIt(base);
        server.getPluginManager().callEvent(event);
        assertEquals(4, service.xp(player.getUniqueId(), Skill.FARMING), 1e-9);
        assertEquals(2, service.data(player.getUniqueId()).collected("SUGAR_CANE"));
    }

    /** Stands in for Telekinesis: takes the drops away at HIGH, before skills reads them at MONITOR. */
    public static final class Vacuum implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
        public void onDrops(BlockDropItemEvent event) {
            event.getItems().clear();
        }
    }

    @Test
    public void collectionsStillCountWhenAnotherFeatureTookTheDrops() {
        server.getPluginManager().registerEvents(new Vacuum(), plugin);
        Block ore = block(18, 64, 9, Material.DIAMOND_ORE);
        BlockDropItemEvent event = drops(ore, Material.DIAMOND, 2);
        breakIt(ore);
        server.getPluginManager().callEvent(event);
        assertTrue(event.getItems().isEmpty());
        assertEquals(2, service.data(player.getUniqueId()).collected("DIAMOND"));
    }

    /** Stands in for the items feature's own fortune: adds to the stack in place at HIGH. */
    public static final class ExtraDrops implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
        public void onDrops(BlockDropItemEvent event) {
            event.getItems().forEach(i -> {
                ItemStack s = i.getItemStack();
                s.setAmount(s.getAmount() * 2);
                i.setItemStack(s);
            });
        }
    }

    @Test
    public void extrasAddedInPlaceByAnotherFeatureAreCounted() {
        server.getPluginManager().registerEvents(new ExtraDrops(), plugin);
        Block ore = block(19, 64, 9, Material.DIAMOND_ORE);
        BlockDropItemEvent event = drops(ore, Material.DIAMOND, 2);
        breakIt(ore);
        server.getPluginManager().callEvent(event);
        assertEquals(4, service.data(player.getUniqueId()).collected("DIAMOND"));
    }

    @Test
    public void marksAreWrittenToTheChunkRightAway() {
        Block ore = block(70, 64, 70, Material.DIAMOND_ORE);
        var pdc = world.getChunkAt(ore).getPersistentDataContainer(); // the chunk is loaded while a player builds in it
        place(ore);
        assertTrue("the mark reaches the chunk's PDC without waiting for a timer",
            pdc.has(ChunkPdcBackend.KEY, org.bukkit.persistence.PersistentDataType.LONG_ARRAY));
        breakIt(ore);
        assertFalse(pdc.has(ChunkPdcBackend.KEY, org.bukkit.persistence.PersistentDataType.LONG_ARRAY)
            && java.util.BitSet.valueOf(pdc.get(ChunkPdcBackend.KEY, org.bukkit.persistence.PersistentDataType.LONG_ARRAY))
                .get(PlacedBlocks.index(world.getMinHeight(), 70, 64, 70)));
    }

    @Test
    public void unmarkingHappensEvenWhenTheBreakEarnsNothing() {
        Block ore = block(14, 64, 9, Material.DIAMOND_ORE);
        place(ore);
        player.setGameMode(GameMode.CREATIVE);
        breakIt(ore);
        player.setGameMode(GameMode.SURVIVAL);
        ore.setType(Material.DIAMOND_ORE);
        breakIt(ore);
        assertEquals(18, mining(), 1e-9);
    }

    @Test
    public void pistonsCarryTheMarkWithTheBlock() throws Exception {
        useUnprotectedWorld();
        Block piston = block(30, 64, 30, Material.PISTON);
        org.bukkit.block.data.Directional facing = (org.bukkit.block.data.Directional) piston.getBlockData();
        facing.setFacing(org.bukkit.block.BlockFace.EAST);
        piston.setBlockData(facing);
        Block ore = block(31, 64, 30, Material.DIAMOND_ORE);
        place(ore);
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockPistonExtendEvent(piston, List.of(ore),
            org.bukkit.block.BlockFace.EAST));
        ore.setType(Material.AIR);
        Block moved = block(32, 64, 30, Material.DIAMOND_ORE);
        breakIt(moved);
        assertEquals("the moved block is still a placed block", 0, mining(), 0);
        // the old position no longer carries a mark
        breakIt(block(31, 64, 30, Material.DIAMOND_ORE));
        assertEquals(18, mining(), 1e-9);
    }

    @Test
    public void burnedBlocksLoseTheirMark() throws Exception {
        useUnprotectedWorld();
        Block log = block(40, 64, 40, Material.OAK_LOG);
        place(log);
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBurnEvent(log, null));
        log.setType(Material.OAK_LOG);
        breakIt(log);
        assertEquals(6, service.xp(player.getUniqueId(), Skill.FORAGING), 1e-9);
    }
}
