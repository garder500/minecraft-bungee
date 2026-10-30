package win.vampyuyu.survival.dungeon.gen;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** Empty world for dungeon runs. The island feature has its own void generator; this one depends on nothing. */
public final class DungeonVoidGenerator extends ChunkGenerator {
    private final int spawnX;
    private final int spawnY;
    private final int spawnZ;

    public DungeonVoidGenerator(int spawnX, int spawnY, int spawnZ) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
    }

    @Override
    public boolean shouldGenerateNoise() { return false; }

    @Override
    public boolean shouldGenerateSurface() { return false; }

    @Override
    public boolean shouldGenerateBedrock() { return false; }

    @Override
    public boolean shouldGenerateCaves() { return false; }

    @Override
    public boolean shouldGenerateDecorations() { return false; }

    @Override
    public boolean shouldGenerateMobs() { return false; }

    @Override
    public boolean shouldGenerateStructures() { return false; }

    @Override
    public boolean canSpawn(World world, int x, int z) { return true; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, spawnX + 0.5, spawnY, spawnZ + 0.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.THE_VOID);
            }
        };
    }
}
