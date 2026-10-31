package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.bukkit.Material;

/**
 * The multi-block generators. A Fountain in the middle of a 3x3 ring (same height) becomes a generator: the ring
 * material says which one, the south side holds the water cell (a Spring Stone) or, for the Ember generator, a magma
 * block. Pure: the caller says what is at each offset, so it is tested without a server.
 */
public final class GeneratorStructure {
    public enum Kind {
        STONE("Stone generator", Material.STONE_BRICKS, Material.COBBLESTONE),
        DUNES("Dune generator", Material.SANDSTONE, Material.SAND),
        FROST("Frost generator", Material.GLASS, Material.SNOW_BLOCK),
        EMBER("Ember generator", Material.NETHER_BRICKS, Material.NETHERRACK);

        public final String title;
        final Material ring;
        /** The block the generator makes above its Fountain (dunes and frost mix in others). */
        final Material output;

        Kind(String title, Material ring, Material output) {
            this.title = title;
            this.ring = ring;
            this.output = output;
        }

        /** What stands on the south side: a Spring Stone, or a magma block for the Ember generator. */
        boolean needsSpring() {
            return this != EMBER;
        }
    }

    /** What the south cell holds. */
    static final Material SPRING = Material.PRISMARINE_BRICKS;

    /** Offsets of the ring around the centre, with the side name used in messages. */
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    private static final String[] SIDES = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
    private static final int SOUTH = 4;

    /** The generator found (null when nothing is complete) and, for the nearest plan, what is still missing. */
    record Result(Kind kind, Kind nearest, List<String> missing) {
        boolean complete() {
            return kind != null;
        }
    }

    private GeneratorStructure() {}

    /**
     * @param at what the world holds at (dx, dz) from the Fountain, at the Fountain's height; the Spring Stone is
     *           reported as {@link #SPRING} by the caller (it knows its machine store)
     */
    static Result detect(BiFunction<Integer, Integer, Material> at) {
        Kind best = null;
        List<String> bestMissing = null;
        for (Kind kind : Kind.values()) {
            List<String> missing = missing(kind, at);
            if (missing.isEmpty()) {
                return new Result(kind, kind, List.of());
            }
            if (bestMissing == null || missing.size() < bestMissing.size()) {
                best = kind;
                bestMissing = missing;
            }
        }
        return new Result(null, best, bestMissing);
    }

    static List<String> missing(Kind kind, BiFunction<Integer, Integer, Material> at) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < RING.length; i++) {
            Material want = i == SOUTH ? (kind.needsSpring() ? SPRING : Material.MAGMA_BLOCK) : kind.ring;
            if (at.apply(RING[i][0], RING[i][1]) != want) {
                out.add(describe(want) + " on the " + SIDES[i] + " side");
            }
        }
        return out;
    }

    static String describe(Material material) {
        if (material == SPRING) {
            return "a Spring Stone";
        }
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return name;
    }

    /** The plan as text lines for /guide: the ring seen from above, north at the top. */
    public static List<String> blueprint(Kind kind) {
        String r = shortName(kind.ring);
        String s = kind.needsSpring() ? "Spring Stone" : "Magma Block";
        return List.of(r + "  " + r + "  " + r, r + "  FOUNTAIN  " + r, r + "  " + s + "  " + r);
    }

    private static String shortName(Material material) {
        return describe(material);
    }
}
