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

    @Test
    public void miningFortuneAddsDropsAndCollections() {
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
    public void pistonsCarryTheMarkWithTheBlock() {
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
    public void burnedBlocksLoseTheirMark() {
        Block log = block(40, 64, 40, Material.OAK_LOG);
        place(log);
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBurnEvent(log, null));
        log.setType(Material.OAK_LOG);
        breakIt(log);
        assertEquals(6, service.xp(player.getUniqueId(), Skill.FORAGING), 1e-9);
    }
}
