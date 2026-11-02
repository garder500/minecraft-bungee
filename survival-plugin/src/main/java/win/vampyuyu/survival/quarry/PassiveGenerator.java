package win.vampyuyu.survival.quarry;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The passive Flux generators (GDD 7.3): they burn nothing, they only need their cells around them and the compression
 * Block of their tier inside (GDD 7.4). Pure: the caller says which cells touch the machine, so it is tested without a
 * server. The numbers are the {@code quarry.machines.<key>.output} settings; {@link #defaultOutput} is only the fallback.
 */
enum PassiveGenerator {
    /** Tier I-II: an infinite water source (a Spring Stone) next to the wheel. */
    SPRING_WHEEL("spring_wheel", "spring-wheel", "Spring Wheel", 10, "bronzite_block", EnumSet.of(Cell.SPRING), false),
    /** Tier III: a magma cell next to the tap. */
    HEAT_TAP("heat_tap", "heat-tap", "Heat Tap", 30, "sunstone_block", EnumSet.of(Cell.MAGMA), false),
    /** Tier IV: a Void cell next to the reactor. */
    VOID_REACTOR("void_reactor", "void-reactor", "Void Reactor", 80, "voidglass_block", EnumSet.of(Cell.VOID), false),
    /** Tier V: a Void cell and a geode next to the lattice, and the open sky above it. */
    ASTRAL_LATTICE("astral_lattice", "astral-lattice", "Astral Lattice", 200, "starcore_block", EnumSet.of(Cell.VOID, Cell.GEODE), true);

    /** What can touch a generator and feed it. */
    enum Cell {
        SPRING("a Spring Stone"), MAGMA("a magma block"), VOID("a Void Cell (crying obsidian)"), GEODE("a block of amethyst (the geode)");

        final String describe;

        Cell(String describe) {
            this.describe = describe;
        }
    }

    final String itemId;
    /** Settings key: {@code quarry.machines.<key>.output}. */
    final String key;
    final String title;
    final double defaultOutput;
    /** The custom item that activates it (and that the overload adds more of). */
    final String blockItem;
    final Set<Cell> cells;
    final boolean needsSky;

    PassiveGenerator(String itemId, String key, String title, double defaultOutput, String blockItem, Set<Cell> cells, boolean needsSky) {
        this.itemId = itemId;
        this.key = key;
        this.title = title;
        this.defaultOutput = defaultOutput;
        this.blockItem = blockItem;
        this.cells = cells;
        this.needsSky = needsSky;
    }

    /** "Bronzite Block": the name of the Block that activates it. */
    String blockTitle() {
        String ore = blockItem.substring(0, blockItem.length() - "_block".length());
        return Character.toUpperCase(ore.charAt(0)) + ore.substring(1) + " Block";
    }

    /** True for the id of any compression Block: one per tiered ore (nine bars, 81 ores each). */
    static boolean isBlock(String id) {
        for (String ore : new String[] {"bronzite", "frostium", "sunstone", "voidglass", "starcore"}) {
            if ((ore + "_block").equals(id)) {
                return true;
            }
        }
        return false;
    }

    static Optional<PassiveGenerator> ofItem(String itemId) {
        for (PassiveGenerator g : values()) {
            if (g.itemId.equals(itemId)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    /** True when every cell it needs touches it and, for the Lattice, the sky is open above it. */
    boolean met(Set<Cell> around, boolean openSky) {
        return around.containsAll(cells) && (!needsSky || openSky);
    }

    /** What is still missing, for the message a player reads when the generator does not run. */
    String missing(Set<Cell> around, boolean openSky) {
        StringBuilder out = new StringBuilder();
        for (Cell cell : cells) {
            if (!around.contains(cell)) {
                out.append(out.length() == 0 ? "" : ", ").append(cell.describe).append(" touching it");
            }
        }
        if (needsSky && !openSky) {
            out.append(out.length() == 0 ? "" : ", ").append("the open sky above it");
        }
        return out.toString();
    }

    /**
     * Factor on the base output of a generator holding {@code blocks} Blocks: 0 with none, 1 with the first (it
     * activates the generator), then {@code bonus} more for each of up to {@code max} overload Blocks (1.25, 1.5, 1.75).
     */
    static double overload(int blocks, int max, double bonus) {
        if (blocks <= 0) {
            return 0;
        }
        return 1 + bonus * Math.min(blocks - 1, Math.max(0, max));
    }
}
