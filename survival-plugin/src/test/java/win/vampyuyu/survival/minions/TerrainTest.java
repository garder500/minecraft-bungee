package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.SplittableRandom;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import win.vampyuyu.survival.minions.MinionConfig.Type;

public class TerrainTest {
    private WorldMock world;
    private MinionConfig cfg;
    private final SplittableRandom random = new SplittableRandom(5);

    @Before
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("sb_world");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        cfg = TestConfig.load();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private Terrain.Area area(int radius) {
        return new Terrain.Area(world, 0, 64, 0, radius);
    }

    private void floor(Material m) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                world.getBlockAt(x, 63, z).setType(m);
            }
        }
    }

    private void layer(Material m) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x != 0 || z != 0) {
                    world.getBlockAt(x, 64, z).setType(m);
                }
            }
        }
    }

    private long count(Material m, int radius) {
        long n = 0;
        for (Block b : Terrain.blocks(area(radius))) {
            if (b.getType() == m) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void theAreaIsTheLayerWithoutTheMinionsOwnColumn() {
        assertEquals(24, Terrain.blocks(area(2)).size());
        assertEquals(48, Terrain.blocks(area(3)).size());
        assertTrue(area(2).contains(2, 64, 2));
        assertFalse(area(2).contains(0, 64, 0));
        assertFalse(area(2).contains(3, 64, 0));
        assertFalse(area(2).contains(1, 65, 1));
        assertEquals(4, Terrain.corners(area(2)).size());
    }

    @Test
    public void miningNeedsAirOrItsOwnBlock() {
        Type cobble = cfg.types.get("cobblestone");
        floor(Material.STONE);
        assertEquals(Status.WORKING, Terrain.scan(area(2), cobble, cfg));
        layer(Material.DIRT);
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cobble, cfg));
        world.getBlockAt(1, 64, 1).setType(Material.COBBLESTONE);
        assertEquals(Status.WORKING, Terrain.scan(area(2), cobble, cfg));
    }

    @Test
    public void miningNeverFillsLiquidsAndSandNeedsSupport() {
        layer(Material.WATER);
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cfg.types.get("cobblestone"), cfg));
        layer(Material.AIR);
        floor(Material.AIR);
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cfg.types.get("sand"), cfg));
        floor(Material.STONE);
        assertEquals(Status.WORKING, Terrain.scan(area(2), cfg.types.get("sand"), cfg));
    }

    @Test
    public void placeThenHarvestMakesTheBlockAppearAndGoAndTouchesNothingElse() {
        Type coal = cfg.types.get("coal");
        floor(Material.STONE);
        world.getBlockAt(1, 64, 0).setType(Material.CHEST);
        world.getBlockAt(-1, 64, 0).setType(Material.OAK_PLANKS);
        MinionRuntime rt = new MinionRuntime();
        MinionData d = new MinionData();
        d.id = "x";
        for (int i = 0; i < 30; i++) {
            Block spot = Terrain.place(area(2), coal, d, rt, random, 0.7);
            assertNotNull(spot);
            assertEquals(Material.COAL_ORE, spot.getType());
            assertTrue(spot.getX() + "," + spot.getY() + "," + spot.getZ(), area(2).contains(spot.getX(), spot.getY(), spot.getZ()));
            rt.spotX = spot.getX();
            rt.spotY = spot.getY();
            rt.spotZ = spot.getZ();
            rt.placed = true;
            Terrain.harvest(area(2), coal, rt, random);
            assertEquals(Material.AIR, spot.getType());
            rt.placed = false;
        }
        assertEquals(Material.CHEST, world.getBlockAt(1, 64, 0).getType());
        assertEquals(Material.OAK_PLANKS, world.getBlockAt(-1, 64, 0).getType());
        assertEquals(0, count(Material.COAL_ORE, 2));
    }

    @Test
    public void harvestOnlyRemovesTheMinionsOwnKind() {
        Type coal = cfg.types.get("coal");
        layer(Material.IRON_ORE);
        MinionRuntime rt = new MinionRuntime();
        Terrain.harvest(area(2), coal, rt, random);
        assertEquals(24, count(Material.IRON_ORE, 2));
        world.getBlockAt(2, 64, 2).setType(Material.COAL_ORE);
        Terrain.harvest(area(2), coal, rt, random);
        assertEquals(Material.AIR, world.getBlockAt(2, 64, 2).getType());
        assertEquals(23, count(Material.IRON_ORE, 2));
    }

    @Test
    public void farmingNeedsFarmlandAndPlantsFullyGrownCrops() {
        Type wheat = cfg.types.get("wheat");
        floor(Material.GRASS_BLOCK);
        assertEquals(Status.INVALID_TERRAIN, Terrain.scan(area(2), wheat, cfg));
        floor(Material.FARMLAND);
        assertEquals(Status.WORKING, Terrain.scan(area(2), wheat, cfg));
        MinionRuntime rt = new MinionRuntime();
        Block spot = Terrain.place(area(2), wheat, new MinionData(), rt, random, 0.7);
        assertNotNull(spot);
        assertEquals(Material.WHEAT, spot.getType());
        var age = (org.bukkit.block.data.Ageable) spot.getBlockData();
        assertEquals(age.getMaximumAge(), age.getAge());
    }

    @Test
    public void pumpkinsAcceptPlainSoilButCropsDoNot() {
        floor(Material.GRASS_BLOCK);
        assertEquals(Status.WORKING, Terrain.scan(area(2), cfg.types.get("pumpkin"), cfg));
        assertEquals(Status.INVALID_TERRAIN, Terrain.scan(area(2), cfg.types.get("carrot"), cfg));
    }

    @Test
    public void foragingNeedsSoilForSaplings() {
        Type oak = cfg.types.get("oak");
        floor(Material.STONE);
        assertEquals(Status.INVALID_TERRAIN, Terrain.scan(area(2), oak, cfg));
        floor(Material.GRASS_BLOCK);
        assertEquals(Status.WORKING, Terrain.scan(area(2), oak, cfg));
        Block spot = Terrain.place(area(2), oak, new MinionData(), new MinionRuntime(), random, 0.7);
        assertEquals(Material.OAK_SAPLING, spot.getType());
    }

    @Test
    public void fishingNeedsWaterInTheLayerBelow() {
        Type fishing = cfg.types.get("fishing");
        assertEquals(Status.NO_WATER, Terrain.scan(area(2), fishing, cfg));
        world.getBlockAt(1, 63, 1).setType(Material.WATER);
        world.getBlockAt(1, 63, 2).setType(Material.WATER);
        assertEquals(Status.NO_WATER, Terrain.scan(area(2), fishing, cfg));
        world.getBlockAt(2, 63, 1).setType(Material.WATER);
        assertEquals(Status.WORKING, Terrain.scan(area(2), fishing, cfg));
    }

    @Test
    public void animalsNeedAFreeTwoHighSpotOnSolidGround() {
        Type cow = cfg.types.get("cow");
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cow, cfg));
        floor(Material.GRASS_BLOCK);
        assertEquals(Status.WORKING, Terrain.scan(area(2), cow, cfg));
        layer(Material.STONE);
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cow, cfg));
    }

    @Test
    public void workOnlyStartsWhenEveryChunkOfTheAreaIsLoaded() {
        assertTrue(Terrain.loaded(area(2)));
        assertFalse(Terrain.loaded(new Terrain.Area(world, 400, 64, 400, 2)));
    }
}
