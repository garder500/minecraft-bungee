package win.vampyuyu.survival.astral;

import java.util.List;

/**
 * Where everything is in the Astral Interiority (GDD 4.5): pure coordinates relative to the build origin, surface of the
 * Rift Landing at y = 0. {@link AstralPlan} and the rules read the same lists, so they cannot drift apart.
 *
 * <p>Three regions, west to east: the Sea of Devoured Ideas (a shallow sea of liquid memory crossed on glass threads between
 * ruined islets), the Nervous Tree (a tall trunk climbed by synapses, the guided lifts of {@link Synapse}) and the
 * Gravitational Throne (a causeway curved around a singularity, where gravity pulls toward the centre, see {@link AstralMath}).
 */
public final class AstralLayout {
    /** Bump to rebuild existing worlds when the layout changes. */
    public static final int VERSION = 1;
    /** World y of the Rift Landing surface. */
    public static final int ORIGIN_Y = 100;
    /** Plans placed above this many blocks are cut: the test keeps the layout well below it. */
    public static final int CAP = 90_000;
    /** Where players arrive. */
    public static final int[] SPAWN = {0, 1, 4};
    public static final int LANDING_RADIUS = 10;
    /** Under this height (relative) a player has fallen out of the Interiority. */
    public static final int VOID_DEPTH = 40;

    // ---- Sea of Devoured Ideas
    public static final int SEA_MIN_X = 14;
    public static final int SEA_MAX_X = 150;
    public static final int SEA_HALF_WIDTH = 30;
    /** Depth of the sea: water from y = -SEA_DEPTH to y = -1. */
    public static final int SEA_DEPTH = 4;

    // ---- Nervous Tree
    public static final int[] TREE = {195, 0, 0};
    public static final int TREE_BASE_RADIUS = 12;
    public static final int TRUNK_RADIUS = 3;
    public static final int TREE_STEP = 10;
    public static final int PLATFORMS = 9;
    public static final int PLATFORM_RADIUS = 5;
    /** Distance from the centre of a platform to its pad, away from the trunk: a lift lands in the middle, the next pad is a few steps on. */
    public static final int PAD_OFFSET = 3;
    /** Distance from the trunk axis to the centre of the platforms. */
    public static final int PLATFORM_ORBIT = 11;
    public static final int CROWN_RADIUS = 8;
    /** Height of the Crown, above the last platform. */
    public static final int CROWN_Y = (PLATFORMS + 1) * TREE_STEP;

    // ---- Gravitational Throne
    /** Centre of the singularity (relative). */
    public static final int[] SINGULARITY = {300, CROWN_Y, 0};
    public static final int SINGULARITY_RADIUS = 6;
    /** Horizontal distance to the centre under which a player is lost to the singularity. */
    public static final double HORIZON = 11.5;
    /** Reach of the pull. */
    public static final double PULL_RADIUS = 45;
    /** Radius of the causeway around the singularity. */
    public static final int ARC_RADIUS = 22;
    public static final int NEXUS_RADIUS = 9;
    public static final int ANCHOR_SHELTER = 4;
    /** Horizontal reach of a synapse pad (blocks from its centre). */
    public static final int SYNAPSE_TRIGGER = 1;
    /** Centre of the Nexus disc, where the causeway starts. */
    public static final int[] NEXUS = {SINGULARITY[0] - ARC_RADIUS - NEXUS_RADIUS + 1, SINGULARITY[1], 0};

    private AstralLayout() {}

    /** A place to discover. {@code y} is the floor level to stand on. */
    public record Landmark(String id, String name, int x, int y, int z, String lore) {}

    /** A ruined islet of the sea. */
    public record Islet(String id, int x, int z, int radius) {}

    /** A guided lift of the Nervous Tree: stepping on the pad carries the player to {@code to}. */
    public record Synapse(String id, int x, int y, int z, int toX, int toY, int toZ) {}

    /** A gravity anchor: lit by right-click, then a shelter from the pull for the player who lit it. */
    public record Anchor(String id, int x, int y, int z) {}

    public static final List<Islet> ISLETS = List.of(
        new Islet("library", 32, -12, 6),
        new Islet("amphitheatre", 58, 14, 7),
        new Islet("lighthouse", 86, -10, 5),
        new Islet("cradle", 112, 12, 6),
        new Islet("shore", 136, 0, 7));

    public static final List<Synapse> SYNAPSES = synapses();
    public static final List<Anchor> ANCHORS = anchors();
    /** The throne seat, on the dais at the far end of the causeway. */
    public static final int[] THRONE = {SINGULARITY[0] + ARC_RADIUS, SINGULARITY[1], 0};

    public static final List<Landmark> LANDMARKS = List.of(
        new Landmark("rift", "Rift Landing", 0, 1, 0,
            "The sky tore, the Devourer opened, and you walked in. Nobody at the Quai said it was a mouth."),
        new Landmark("library", "The Drowned Library", islet("library").x(), 1, islet("library").z(),
            "Shelves of a city that thought itself eternal. The books are wet and still read aloud, one line each."),
        new Landmark("amphitheatre", "The Amphitheatre of Echoes", islet("amphitheatre").x(), 1, islet("amphitheatre").z(),
            "A crowd that was swallowed in mid-applause. The stone keeps the sound of it."),
        new Landmark("lighthouse", "The Unlit Lighthouse", islet("lighthouse").x(), 1, islet("lighthouse").z(),
            "It guided ships to a harbour that has since been eaten. It still turns, for nobody."),
        new Landmark("cradle", "The Cradle of Names", islet("cradle").x(), 1, islet("cradle").z(),
            "A civilization wrote its children's names here so the Devourer could not forget them. It did not forget them."),
        new Landmark("roots", "Roots of the Nervous Tree", TREE[0] - TREE_BASE_RADIUS + 2, 1, TREE[2],
            "The Tree is not a plant. Every branch is a thought the Devourer has not finished having."),
        new Landmark("crown", "The Crown of Synapses", TREE[0], CROWN_Y + 1, TREE[2],
            "From up here the sky is seen from inside: a ceiling of ribs, slowly pulsing. The old world's last breath is up there."),
        new Landmark("nexus", "The Gravitational Nexus", NEXUS[0], NEXUS[1] + 1, NEXUS[2],
            "The Devourer's centre of mass. Everything it ever ate leans this way, and so will you."),
        new Landmark("dais", "The Throne", THRONE[0], THRONE[1] + 1, THRONE[2],
            "Here the Devourer sits, at the edge of its own hunger. The chair is empty. It was never made for a king, but for a witness."));

    public static Islet islet(String id) {
        for (Islet islet : ISLETS) {
            if (islet.id().equals(id)) {
                return islet;
            }
        }
        throw new IllegalArgumentException(id);
    }

    /** Centre of the platform {@code i} (1-based) of the Nervous Tree: a spiral of 100 degrees per step. */
    public static int[] platform(int i) {
        double angle = Math.toRadians(100.0 * i);
        return new int[] {TREE[0] + (int) Math.round(PLATFORM_ORBIT * Math.cos(angle)), i * TREE_STEP,
            TREE[2] + (int) Math.round(PLATFORM_ORBIT * Math.sin(angle))};
    }

    /** The pad of platform {@code i} (1-based): outside the orbit, {@link #PAD_OFFSET} blocks from the centre. */
    public static int[] pad(int i) {
        double angle = Math.toRadians(100.0 * i);
        int[] c = platform(i);
        return new int[] {c[0] + (int) Math.round(PAD_OFFSET * Math.cos(angle)), c[1], c[2] + (int) Math.round(PAD_OFFSET * Math.sin(angle))};
    }

    private static List<Synapse> synapses() {
        java.util.ArrayList<Synapse> list = new java.util.ArrayList<>();
        // first lift: from the foot of the Tree to platform 1; then pad i -> platform i + 1; the last one leads to the Crown
        int[] from = {TREE[0] - TREE_BASE_RADIUS + 5, 0, TREE[2] + 3};
        for (int i = 1; i <= PLATFORMS + 1; i++) {
            int[] to = i <= PLATFORMS ? platform(i) : new int[] {TREE[0], CROWN_Y, TREE[2] + 6};
            list.add(new Synapse("synapse:" + (i - 1), from[0], from[1] + 1, from[2], to[0], to[1] + 1, to[2]));
            from = pad(i);
        }
        return List.copyOf(list);
    }

    private static List<Anchor> anchors() {
        java.util.ArrayList<Anchor> list = new java.util.ArrayList<>();
        // the Nexus anchor, then four along the arc at 35 degree steps starting 215 degrees around the singularity
        list.add(new Anchor("anchor:0", NEXUS[0], NEXUS[1] + 1, NEXUS[2]));
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(215 + 35 * i);
            list.add(new Anchor("anchor:" + (i + 1), SINGULARITY[0] + (int) Math.round(ARC_RADIUS * Math.cos(angle)),
                SINGULARITY[1] + 1, SINGULARITY[2] + (int) Math.round(ARC_RADIUS * Math.sin(angle))));
        }
        return List.copyOf(list);
    }

    /** True when the point (relative) is inside the sea basin, under its surface. */
    public static boolean inSea(double x, double y, double z) {
        return x >= SEA_MIN_X && x <= SEA_MAX_X && Math.abs(z) <= SEA_HALF_WIDTH && y <= -0.2 && y >= -SEA_DEPTH - 1;
    }

    /** Horizontal distance from the singularity centre. */
    public static double horizonDistance(double x, double z) {
        return Math.hypot(x - SINGULARITY[0], z - SINGULARITY[2]);
    }
}
