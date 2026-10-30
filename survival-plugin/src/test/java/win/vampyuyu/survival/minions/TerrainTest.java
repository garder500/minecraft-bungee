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
    public void miningNeedsAirOnSolidGroundOrItsOwnPlacedBlock() {
        Type cobble = cfg.types.get("cobblestone");
        floor(Material.STONE);
        assertEquals(Status.WORKING, Terrain.scan(area(2), cobble, cfg));
        layer(Material.DIRT);
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cobble, cfg));
        // A cobblestone block that is just there (a player's wall) does not make the terrain workable ...
        world.getBlockAt(1, 64, 1).setType(Material.COBBLESTONE);
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cobble, cfg));
        // ... the block the minion itself placed for the cycle in progress does.
        MinionData d = new MinionData();
        Terrain.track(d, world.getBlockAt(1, 64, 1));
        assertEquals(Status.WORKING, Terrain.scan(area(2), cobble, cfg, d));
    }

    @Test
    public void miningNeverFillsLiquidsAndEveryMiningTypeNeedsGroundUnderTheBlock() {
        layer(Material.WATER);
        assertEquals(Status.NO_SPACE, Terrain.scan(area(2), cfg.types.get("cobblestone"), cfg));
        layer(Material.AIR);
        floor(Material.AIR);
        // Air but no ground to put the block on (a pillar over the void): the terrain is invalid, not just crowded.
        assertEquals(Status.INVALID_TERRAIN, Terrain.scan(area(2), cfg.types.get("sand"), cfg));
        assertEquals(Status.INVALID_TERRAIN, Terrain.scan(area(2), cfg.types.get("cobblestone"), cfg));
        assertEquals(Status.INVALID_TERRAIN, Terrain.scan(area(2), cfg.types.get("diamond"), cfg));
        assertNull(Terrain.place(area(2), cfg.types.get("cobblestone"), new MinionData(), new MinionRuntime(), random, 0.7));
        floor(Material.STONE);
        assertEquals(Status.WORKING, Terrain.scan(area(2), cfg.types.get("sand"), cfg));
        assertEquals(Status.WORKING, Terrain.scan(area(2), cfg.types.get("cobblestone"), cfg));
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
            Terrain.track(d, spot);
            assertTrue(d.placed);
            Terrain.harvest(area(2), coal, d, rt, random);
            assertEquals(Material.AIR, spot.getType());
            assertFalse(d.placed);
        }
        assertEquals(Material.CHEST, world.getBlockAt(1, 64, 0).getType());
        assertEquals(Material.OAK_PLANKS, world.getBlockAt(-1, 64, 0).getType());
        assertEquals(0, count(Material.COAL_ORE, 2));
    }

    @Test
    public void harvestRemovesOnlyTheBlockTheMinionPlacedNeverAnotherBlockOfTheSameKind() {
        Type cobble = cfg.types.get("cobblestone");
        layer(Material.COBBLESTONE); // a player's cobblestone wall all around the minion
        MinionRuntime rt = new MinionRuntime();
        MinionData d = new MinionData();
        // Nothing placed (a restart before the minion placed anything): the harvest touches nothing, wall included.
        Terrain.harvest(area(2), cobble, d, rt, random);
        assertEquals(24, count(Material.COBBLESTONE, 2));
        // The recorded block is removed, the rest of the wall stays.
        Terrain.track(d, world.getBlockAt(2, 64, 2));
        Terrain.harvest(area(2), cobble, d, rt, random);
        assertEquals(Material.AIR, world.getBlockAt(2, 64, 2).getType());
        assertEquals(23, count(Material.COBBLESTONE, 2));
        assertFalse(d.placed);
    }

    @Test
    public void aTrackedBlockThatAPlayerReplacedIsLeftAlone() {
        Type cobble = cfg.types.get("cobblestone");
        MinionData d = new MinionData();
        world.getBlockAt(1, 64, 1).setType(Material.COBBLESTONE);
        Terrain.track(d, world.getBlockAt(1, 64, 1));
        world.getBlockAt(1, 64, 1).setType(Material.OAK_PLANKS);
        Terrain.harvest(area(2), cobble, d, new MinionRuntime(), random);
        assertEquals(Material.OAK_PLANKS, world.getBlockAt(1, 64, 1).getType());
        assertFalse(d.placed);
    }

    @Test
    public void aTrackedBlockInAnUnloadedChunkIsKeptForLater() {
        MinionData d = new MinionData();
        d.placed = true;
        d.spotX = 400;
        d.spotY = 64;
        d.spotZ = 400;
        d.spotMaterial = "COAL_ORE";
        assertFalse(Terrain.removeTracked(world, d, false));
        assertTrue(d.placed);
    }

    @Test
    public void cleanupRemovesThePlacedBlockSilently() {
        Type coal = cfg.types.get("coal");
        floor(Material.STONE);
        MinionData d = new MinionData();
        MinionRuntime rt = new MinionRuntime();
        Block spot = Terrain.place(area(2), coal, d, rt, random, 0.7);
        Terrain.track(d, spot);
        Terrain.cleanup(world, d, rt);
        assertEquals(Material.AIR, spot.getType());
        assertFalse(d.placed);
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
    public void foragingNeedsSoilAndGrowsALogNotASapling() {
        Type oak = cfg.types.get("oak");
        floor(Material.STONE);
        assertEquals(Status.INVALID_TERRAIN, Terrain.scan(area(2), oak, cfg));
        floor(Material.GRASS_BLOCK);
        assertEquals(Status.WORKING, Terrain.scan(area(2), oak, cfg));
        Block spot = Terrain.place(area(2), oak, new MinionData(), new MinionRuntime(), random, 0.7);
        assertEquals(Material.OAK_LOG, spot.getType());
        MinionData d = new MinionData();
        Terrain.track(d, spot);
        // A player's own oak log or sapling in the area is not touched by the harvest.
        world.getBlockAt(-2, 64, -2).setType(Material.OAK_LOG);
        world.getBlockAt(-2, 64, -1).setType(Material.OAK_SAPLING);
        Terrain.harvest(area(2), oak, d, new MinionRuntime(), random);
        assertEquals(Material.AIR, spot.getType());
        assertEquals(Material.OAK_LOG, world.getBlockAt(-2, 64, -2).getType());
        assertEquals(Material.OAK_SAPLING, world.getBlockAt(-2, 64, -1).getType());
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
