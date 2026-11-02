package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import win.vampyuyu.survival.api.Realm;

/**
 * The Quai des Huit (GDD 17.1), relative coordinates: a floating platform with a central beacon and eight Sky Gates
 * on a ring, one per Realm. The platform surface is y = 0, its centre (0, 0); {@link #CENTER_Z} is where it sits in
 * the hub world, north of it a bridge joins the south avenue of the hub plaza. The gates are built dark (black
 * glass); a lit gate is shown to its owner by client-side block changes (see {@link SkyGates}). Pure data.
 */
public final class QuaiPlan {
    /** Bump when the layout changes so an existing Quai is rebuilt (the marker file stores it). */
    public static final int VERSION = 1;
    public static final int RADIUS = 22;
    /** Hub world Z of the platform centre: the hub border is +-100, the hub ring road ends at z = 44. */
    public static final int CENTER_Z = 72;
    public static final int GATE_RADIUS = 15;
    /** Bridge from the hub ring road (world z 44) to the platform edge. */
    public static final int BRIDGE_FROM = 44 - CENTER_Z;
    /** Where players arrive, relative to the centre: the platform end of the bridge, looking south. */
    public static final int SPAWN_Z = -(RADIUS - 3);

    /** The membrane is the dark glass pane of a gate that a lit gate recolours. */
    public static final Material DARK_MEMBRANE = Material.BLACK_STAINED_GLASS;
    public static final Material DARK_CROWN = Material.DEEPSLATE_TILES;
    public static final Material LIT_CROWN = Material.SEA_LANTERN;

    /** One Sky Gate. Positions are relative to the platform centre; membrane cells are the walkable opening. */
    public record Gate(Realm realm, int x, int z, double outX, double outZ, List<int[]> membrane, int[] crown) {
        /** Block of the opening when lit, by Realm colour. */
        public Material litMembrane() {
            return QuaiPlan.litMembrane(realm);
        }
    }

    public static Material litMembrane(Realm realm) {
        return switch (realm) {
            case ASHES -> Material.ORANGE_STAINED_GLASS;
            case GROVE -> Material.LIME_STAINED_GLASS;
            case HEIGHTS -> Material.LIGHT_BLUE_STAINED_GLASS;
            case CITADEL -> Material.YELLOW_STAINED_GLASS;
            case MARSHES -> Material.BROWN_STAINED_GLASS;
            case ARCHIPELAGO -> Material.PURPLE_STAINED_GLASS;
            case DEPTHS -> Material.BLUE_STAINED_GLASS;
            case NECROPOLIS -> Material.CYAN_STAINED_GLASS;
        };
    }

    /** Gates in Realm order, clockwise from the north-east (the bridge comes in from the north between 1 and 8). */
    public static final List<Gate> GATES = buildGates();

    private QuaiPlan() {}

    private static List<Gate> buildGates() {
        List<Gate> gates = new ArrayList<>();
        for (Realm realm : Realm.values()) {
            double angle = Math.toRadians(22.5 + 45 * realm.ordinal());
            double ox = Math.sin(angle);
            double oz = -Math.cos(angle);
            double tx = Math.cos(angle);
            double tz = Math.sin(angle);
            int gx = (int) Math.round(GATE_RADIUS * ox);
            int gz = (int) Math.round(GATE_RADIUS * oz);
            Set<Long> frame = framePositions(gx, gz, tx, tz);
            Set<Long> cells = new LinkedHashSet<>();
            List<int[]> membrane = new ArrayList<>();
            for (double s = -2; s <= 2.001; s += 0.5) {
                int x = (int) Math.round(gx + s * tx);
                int z = (int) Math.round(gz + s * tz);
                long key = BlockPlan.key(x, 0, z);
                if (!frame.contains(key) && cells.add(key)) {
                    for (int y = 1; y <= 5; y++) {
                        membrane.add(new int[] {x, y, z});
                    }
                }
            }
            gates.add(new Gate(realm, gx, gz, ox, oz, List.copyOf(membrane), new int[] {gx, 7, gz}));
        }
        return List.copyOf(gates);
    }

    /** Pillar columns at both sides of the opening (two cells wide so a diagonal gate has no gap). */
    private static Set<Long> framePositions(int gx, int gz, double tx, double tz) {
        Set<Long> frame = new LinkedHashSet<>();
        for (double s = 2.6; s <= 3.61; s += 0.5) {
            frame.add(BlockPlan.key((int) Math.round(gx + s * tx), 0, (int) Math.round(gz + s * tz)));
            frame.add(BlockPlan.key((int) Math.round(gx - s * tx), 0, (int) Math.round(gz - s * tz)));
        }
        return frame;
    }

    /** The relative arrival point (centre of the block), one above the surface. */
    public static int[] spawn() {
        return new int[] {0, 1, SPAWN_Z};
    }

    public static Gate gate(Realm realm) {
        return GATES.get(realm.ordinal());
    }

    public static BlockPlan build(int cap) {
        BlockPlan plan = new BlockPlan(cap);
        deck(plan);
        paths(plan);
        heart(plan);
        for (Gate gate : GATES) {
            gate(plan, gate);
        }
        bridge(plan);
        underside(plan);
        plan.anchor("spawn", 0, 1, SPAWN_Z);
        for (Gate gate : GATES) {
            plan.anchor("gate:" + gate.realm().id(), gate.x(), 1, gate.z());
        }
        return plan;
    }

    // ---------------------------------------------------------------- pieces

    private static void deck(BlockPlan plan) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = Math.sqrt((double) x * x + z * z);
                if (d > RADIUS + 0.4) {
                    continue;
                }
                Material m;
                if (d > RADIUS - 1) {
                    m = Material.DEEPSLATE_TILES;
                } else if (d > RADIUS - 2) {
                    m = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (d <= 7.5) {
                    m = d <= 3.5 ? Material.CHISELED_QUARTZ_BLOCK : d <= 6 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS;
                } else if (d > GATE_RADIUS - 0.5 && d < GATE_RADIUS + 1.5) {
                    m = Material.POLISHED_BLACKSTONE;
                } else {
                    m = ((x + z) & 1) == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
                }
                plan.set(x, 0, z, m);
            }
        }
    }

    /** Eight strips from the central dais to the gates. */
    private static void paths(BlockPlan plan) {
        for (Gate gate : GATES) {
            double tx = -gate.outZ();
            double tz = gate.outX();
            for (double r = 7.5; r <= GATE_RADIUS - 1.5; r += 0.5) {
                for (double w = -1; w <= 1.001; w += 0.5) {
                    int x = (int) Math.round(gate.outX() * r + tx * w);
                    int z = (int) Math.round(gate.outZ() * r + tz * w);
                    plan.set(x, 0, z, Math.abs(w) < 0.4 ? Material.QUARTZ_BRICKS : Material.POLISHED_BLACKSTONE);
                }
            }
        }
    }

    /** The central structure: a dais, eight sigil stones (one per gate) and a tall light. */
    private static void heart(BlockPlan plan) {
        ShapeKit.ring(plan, 0, 1, 0, 6, 7, Material.QUARTZ_SLAB);
        for (Gate gate : GATES) {
            int x = (int) Math.round(gate.outX() * 5);
            int z = (int) Math.round(gate.outZ() * 5);
            plan.set(x, 0, z, Material.CRYING_OBSIDIAN);
            plan.set(x, 1, z, Material.LODESTONE);
            plan.set(x, 2, z, Material.LANTERN, "hanging=false");
        }
        ShapeKit.disc(plan, 0, 1, 0, 2.2, Material.POLISHED_BLACKSTONE_BRICKS);
        ShapeKit.disc(plan, 0, 2, 0, 1.5, Material.SMOOTH_QUARTZ);
        ShapeKit.column(plan, 0, 3, 14, 0, Material.QUARTZ_PILLAR);
        for (int y = 5; y <= 11; y += 3) {
            ShapeKit.ring(plan, 0, y, 0, 1.5, 1.5, Material.CHISELED_QUARTZ_BLOCK);
        }
        plan.set(0, 15, 0, Material.SEA_LANTERN);
        plan.set(0, 16, 0, Material.END_ROD, "facing=up");
        plan.set(0, 17, 0, Material.END_ROD, "facing=up");
    }

    private static void gate(BlockPlan plan, Gate gate) {
        double tx = -gate.outZ();
        double tz = gate.outX();
        // pad under the gate
        ShapeKit.disc(plan, gate.x(), 0, gate.z(), 4.4, Material.POLISHED_BLACKSTONE_BRICKS);
        ShapeKit.disc(plan, gate.x(), 0, gate.z(), 2.6, Material.CHISELED_POLISHED_BLACKSTONE);
        // pillars
        Set<Long> frame = framePositions(gate.x(), gate.z(), tx, tz);
        for (long key : frame) {
            int x = BlockPlan.keyX(key);
            int z = BlockPlan.keyZ(key);
            plan.set(x, 0, z, Material.SMOOTH_QUARTZ);
            ShapeKit.column(plan, x, 1, 6, z, Material.QUARTZ_PILLAR);
            plan.set(x, 7, z, Material.CHISELED_QUARTZ_BLOCK);
        }
        // lintel and crown
        for (double s = -3.6; s <= 3.601; s += 0.5) {
            int x = (int) Math.round(gate.x() + s * tx);
            int z = (int) Math.round(gate.z() + s * tz);
            plan.set(x, 6, z, Material.SMOOTH_QUARTZ);
        }
        for (int[] cell : gate.membrane()) {
            plan.set(cell[0], cell[1], cell[2], DARK_MEMBRANE);
            plan.set(cell[0], 6, cell[2], Material.SMOOTH_QUARTZ);
        }
        plan.set(gate.crown()[0], gate.crown()[1], gate.crown()[2], DARK_CROWN);
        // sign, between the gate and the dais, facing the dais
        int[] at = signPos(gate);
        int sx = at[0];
        int sz = at[1];
        plan.set(sx, 0, sz, Material.SMOOTH_QUARTZ);
        String[] lines = signLines(gate.realm());
        plan.sign(sx, 1, sz, Material.OAK_SIGN, "rotation=" + HubPlan.signRotation(-gate.outX(), -gate.outZ()),
            lines[0], lines[1], lines[2], "Dormant");
    }

    /** Relative X and Z of the sign in front of a gate (the sign block is at y = 1). */
    public static int[] signPos(Gate gate) {
        return new int[] {(int) Math.round(gate.outX() * (GATE_RADIUS - 4.5)), (int) Math.round(gate.outZ() * (GATE_RADIUS - 4.5))};
    }

    /** The four sign lines (the last one is replaced by the caller): the number, then the title split in two. */
    public static String[] signLines(Realm realm) {
        String title = realm.title();
        int cut = title.indexOf(' ');
        for (int i = 0; i < title.length(); i++) {
            if (title.charAt(i) == ' ' && Math.abs(i - title.length() / 2) < Math.abs(cut - title.length() / 2)) {
                cut = i;
            }
        }
        String first = "Gate " + realm.number();
        return new String[] {first, title.substring(0, cut), title.substring(cut + 1)};
    }

    private static void bridge(BlockPlan plan) {
        for (int z = BRIDGE_FROM - 1; z <= -RADIUS + 3; z++) {
            for (int w = -2; w <= 2; w++) {
                Material m = w == 0 ? Material.QUARTZ_BRICKS : Math.abs(w) == 2 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
                plan.set(w, 0, z, m);
                plan.set(w, -1, z, Material.STONE_BRICKS);
            }
        }
        for (int z = BRIDGE_FROM + 2; z < -RADIUS; z += 3) {
            for (int sx : new int[] {-3, 3}) {
                plan.set(sx, 0, z, Material.POLISHED_BLACKSTONE_BRICKS);
                ShapeKit.column(plan, sx, 1, 2, z, Material.STONE_BRICK_WALL);
                plan.set(sx, 3, z, Material.LANTERN, "hanging=false");
            }
        }
    }

    private static void underside(BlockPlan plan) {
        for (int k = 1; k <= 12; k++) {
            double scale = 1 - (k - 1) / 13.0;
            Material m = k <= 2 ? Material.STONE_BRICKS : k <= 6 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE;
            ShapeKit.disc(plan, 0, -k, 0, RADIUS * scale, m);
        }
    }
}
