package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * A structure described as data: positions relative to an origin, applied later in per-tick batches by
 * {@link PlanJob}. Setting the same position twice keeps the LAST block (but the FIRST insertion order), so layered
 * building ("fill, then carve") is safe. Pure Java, testable without a server.
 */
public final class BlockPlan {
    /** A block type with optional block-data properties, e.g. {@code age=3} (no brackets). */
    public record Blk(Material material, String props) {
        public static Blk of(Material material) {
            return new Blk(material, null);
        }
    }

    public record ChestFill(int x, int y, int z, List<IslandConfig.ItemSpec> items) {}

    public record SignText(int x, int y, int z, List<String> lines) {}

    public record BiomeBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String biome) {}

    private final LinkedHashMap<Long, Blk> blocks = new LinkedHashMap<>();
    private final List<ChestFill> chests = new ArrayList<>();
    private final List<SignText> signs = new ArrayList<>();
    private final List<BiomeBox> biomes = new ArrayList<>();
    private final Map<String, int[]> anchors = new HashMap<>();
    private final int cap;

    public BlockPlan() {
        this(Integer.MAX_VALUE);
    }

    /** A plan that silently stops accepting NEW positions once {@code cap} blocks are set. */
    public BlockPlan(int cap) {
        this.cap = cap;
    }

    // ---------------------------------------------------------------- keys

    public static long key(int x, int y, int z) {
        return ((long) (x + 0x80000) & 0xFFFFF) << 32 | ((long) (z + 0x80000) & 0xFFFFF) << 12 | ((long) (y + 0x800) & 0xFFF);
    }

    public static int keyX(long key) {
        return (int) ((key >>> 32) & 0xFFFFF) - 0x80000;
    }

    public static int keyZ(long key) {
        return (int) ((key >>> 12) & 0xFFFFF) - 0x80000;
    }

    public static int keyY(long key) {
        return (int) (key & 0xFFF) - 0x800;
    }

    // ---------------------------------------------------------------- building

    public BlockPlan set(int x, int y, int z, Material material) {
        return set(x, y, z, new Blk(material, null));
    }

    public BlockPlan set(int x, int y, int z, Material material, String props) {
        return set(x, y, z, new Blk(material, props));
    }

    public BlockPlan set(int x, int y, int z, Blk blk) {
        long k = key(x, y, z);
        if (blocks.size() >= cap && !blocks.containsKey(k)) {
            return this;
        }
        blocks.put(k, blk);
        return this;
    }

    public BlockPlan air(int x, int y, int z) {
        return set(x, y, z, Material.AIR);
    }

    public BlockPlan fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, material);
                }
            }
        }
        return this;
    }

    public BlockPlan chest(int x, int y, int z, String facing, List<IslandConfig.ItemSpec> items) {
        set(x, y, z, Material.CHEST, "facing=" + facing);
        chests.add(new ChestFill(x, y, z, List.copyOf(items)));
        return this;
    }

    /** A standing or wall sign with up to four lines of plain text. */
    public BlockPlan sign(int x, int y, int z, Material signMaterial, String props, String... lines) {
        set(x, y, z, signMaterial, props);
        signs.add(new SignText(x, y, z, List.of(lines)));
        return this;
    }

    public BlockPlan biome(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String biome) {
        if (biome != null && !biome.isBlank()) {
            biomes.add(new BiomeBox(minX, minY, minZ, maxX, maxY, maxZ, biome));
        }
        return this;
    }

    public BlockPlan anchor(String name, int x, int y, int z) {
        anchors.put(name, new int[] {x, y, z});
        return this;
    }

    // ---------------------------------------------------------------- reading

    /** Material at a position, null when the plan does not touch it. */
    public Material get(int x, int y, int z) {
        Blk blk = blocks.get(key(x, y, z));
        return blk == null ? null : blk.material();
    }

    public Blk blk(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    /** True when the plan places a non-air block there. */
    public boolean solidAt(int x, int y, int z) {
        Material m = get(x, y, z);
        return m != null && !m.isAir();
    }

    public int size() {
        return blocks.size();
    }

    public Map<Long, Blk> blocks() {
        return blocks;
    }

    public List<ChestFill> chests() {
        return chests;
    }

    public List<SignText> signs() {
        return signs;
    }

    public List<BiomeBox> biomes() {
        return biomes;
    }

    /** The named point, or null. */
    public int[] anchor(String name) {
        return anchors.get(name);
    }

    public Map<String, int[]> anchors() {
        return anchors;
    }

    /** Number of blocks of one material (tests). */
    public int count(Material material) {
        int n = 0;
        for (Blk blk : blocks.values()) {
            if (blk.material() == material) {
                n++;
            }
        }
        return n;
    }
}
