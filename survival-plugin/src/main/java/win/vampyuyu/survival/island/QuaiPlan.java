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
 * the hub world, north of it a bridge joins the south avenue of the hub plaza. The gates are built dark (tinted
 * glass, near-black; plain black stained glass looks light blue against the sky); a lit gate is shown to its owner by client-side block changes (see {@link SkyGates}). Pure data.
 */
public final class QuaiPlan {
    /** Bump when the layout changes so an existing Quai is rebuilt (the marker file stores it). */
    public static final int VERSION = 5; // 5: the rebuilt Quai (tall pointed Gates, piers, keel, beacon, display models)
    public static final int RADIUS = 22;
    /** Hub world Z of the platform centre: the hub border is +-100, the hub ring road ends at z = 44. */
    public static final int CENTER_Z = 72;
    public static final int GATE_RADIUS = 15;
    /** Bridge from the hub ring road (world z 44) to the platform edge. */
    public static final int BRIDGE_FROM = 44 - CENTER_Z;
    /** Where players arrive, relative to the centre: the platform end of the bridge, looking south. */
    public static final int SPAWN_Z = -(RADIUS - 3);

    /** The membrane is the dark glass pane of a gate that a lit gate recolours. */
    public static final Material DARK_MEMBRANE = Material.TINTED_GLASS;
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

    /** Tint of a lit Gate's halo, by Realm colour (the same hue as its glass). */
    public static int haloColor(Realm realm) {
        return switch (realm) {
            case ASHES -> 0xFF8A2A;
            case GROVE -> 0x8CFF5A;
            case HEIGHTS -> 0x8FE3FF;
            case CITADEL -> 0xFFD84A;
            case MARSHES -> 0xC08A4E;
            case ARCHIPELAGO -> 0xC27BFF;
            case DEPTHS -> 0x4F7DFF;
            case NECROPOLIS -> 0x4FF0D6;
        };
    }

    /** Height of the opening (membrane rows 1..OPENING_TOP); it ends in a pointed arch. */
    public static final int OPENING_TOP = 9;
    /** Height of the keystone over a Gate (the crown block that lights). */
    public static final int CROWN_Y = 12;
    /** Centre height of the halo model over a Gate. */
    static final double HALO_Y = 19.5;
    /** Centre height of the armillary over the dais, and its scale. */
    static final double ARMILLARY_Y = 31.5;
    static final float ARMILLARY_SCALE = 7f;

    /** Gates in Realm order, clockwise from the north-east (the bridge comes in from the north between 1 and 8). */
    public static final List<Gate> GATES = buildGates();

    private QuaiPlan() {}

    /** Half width (in tangent steps) of the opening at a row: 5 wide, then the pointed top. */
    static double openingHalf(int y) {
        return y <= 7 ? 2.001 : y == 8 ? 1.25 : 0.5;
    }

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
            List<int[]> membrane = new ArrayList<>();
            for (int y = 1; y <= OPENING_TOP; y++) {
                Set<Long> cells = new LinkedHashSet<>();
                double half = openingHalf(y);
                for (double s = -half; s <= half; s += 0.25) {
                    int x = (int) Math.round(gx + s * tx);
                    int z = (int) Math.round(gz + s * tz);
                    long key = BlockPlan.key(x, 0, z);
                    if (!frame.contains(key) && cells.add(key)) {
                        membrane.add(new int[] {x, y, z});
                    }
                }
            }
            gates.add(new Gate(realm, gx, gz, ox, oz, List.copyOf(membrane), new int[] {gx, CROWN_Y, gz}));
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
        return build(cap, true);
    }

    /** {@code withMouth} false gives the platform and the Gates alone (the tests check the mouth does not touch them). */
    static BlockPlan build(int cap, boolean withMouth) {
        BlockPlan plan = new BlockPlan(cap);
        deck(plan);
        paths(plan);
        piers(plan);
        heart(plan);
        for (Gate gate : GATES) {
            gate(plan, gate);
        }
        bridge(plan);
        balustrade(plan);
        if (withMouth) {
            QuaiMouth.build(plan);
        }
        underside(plan);
        lights(plan);
        models(plan);
        plan.anchor("spawn", 0, 1, SPAWN_Z);
        for (Gate gate : GATES) {
            plan.anchor("gate:" + gate.realm().id(), gate.x(), 1, gate.z());
        }
        return plan;
    }

    /** Tangent direction of a gate (along its opening). */
    private static double[] tangent(Gate gate) {
        return new double[] {-gate.outZ(), gate.outX()};
    }

    private static int cell(double v) {
        return (int) Math.round(v);
    }

    // ---------------------------------------------------------------- pieces

    /** A compass rose: a pale core, a dark gate ring, rays of tiles between the gates, a double rim. */
    private static void deck(BlockPlan plan) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = Math.sqrt((double) x * x + z * z);
                if (d > RADIUS + 0.4) {
                    continue;
                }
                double a = Math.toDegrees(Math.atan2(x, -z));
                double offRay = 22.5 - Math.abs(((a % 45) + 45) % 45 - 22.5); // degrees from the line between two gates
                Material m;
                if (d > RADIUS - 1) {
                    m = Material.DEEPSLATE_TILES;
                } else if (d > RADIUS - 2) {
                    m = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (d > RADIUS - 3) {
                    m = ((x + z) & 1) == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE;
                } else if (d <= 7.5) {
                    m = d <= 3.5 ? Material.CHISELED_QUARTZ_BLOCK : d <= 6 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS;
                } else if (d > GATE_RADIUS - 0.5 && d < GATE_RADIUS + 1.5) {
                    m = Material.POLISHED_BLACKSTONE;
                } else if (d > 9.5 && d < 10.5) {
                    m = Material.CHISELED_QUARTZ_BLOCK;
                } else if (offRay < Math.toDegrees(1.1 / d)) {
                    m = Material.POLISHED_DEEPSLATE; // a dark ray toward the pier between two gates
                } else {
                    m = ((x * 3 + z * 7) & 3) == 0 ? Material.POLISHED_ANDESITE : ((x + z) & 1) == 0 ? Material.STONE_BRICKS
                        : Material.POLISHED_DIORITE;
                }
                plan.set(x, 0, z, m);
            }
        }
    }

    /** Eight strips from the central dais to the gates, a light in their spine every three blocks. */
    private static void paths(BlockPlan plan) {
        for (Gate gate : GATES) {
            double[] t = tangent(gate);
            for (double r = 7.5; r <= GATE_RADIUS - 1.5; r += 0.5) {
                for (double w = -1; w <= 1.001; w += 0.5) {
                    int x = cell(gate.outX() * r + t[0] * w);
                    int z = cell(gate.outZ() * r + t[1] * w);
                    plan.set(x, 0, z, Math.abs(w) < 0.4 ? Material.QUARTZ_BRICKS : Material.POLISHED_BLACKSTONE);
                }
            }
            for (double r = 8; r <= GATE_RADIUS - 2; r += 3) {
                plan.set(cell(gate.outX() * r), 0, cell(gate.outZ() * r), Material.SEA_LANTERN);
            }
        }
    }

    /** Directions of the six mooring piers: between two gates, never at the bridge (north) or the mouth (south). */
    static final int[] PIER_ANGLES = {45, 90, 135, 225, 270, 315};
    static final int PIER_END = 29;

    /** Six mooring piers reaching out of the rim: a jetty, a lamp arm, a bollard, chains and lanterns underneath. */
    private static void piers(BlockPlan plan) {
        for (int deg : PIER_ANGLES) {
            double a = Math.toRadians(deg);
            double ox = Math.sin(a);
            double oz = -Math.cos(a);
            double tx = -oz;
            double tz = ox;
            for (double r = RADIUS - 2; r <= PIER_END; r += 0.5) {
                double half = r > PIER_END - 2 ? 2.4 : 1.6;
                for (double w = -half; w <= half + 0.001; w += 0.5) {
                    int x = cell(ox * r + tx * w);
                    int z = cell(oz * r + tz * w);
                    boolean edge = Math.abs(w) > half - 0.6;
                    plan.set(x, 0, z, edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.QUARTZ_BRICKS);
                    plan.set(x, -1, z, Material.STONE_BRICKS);
                    if (r > RADIUS + 1) {
                        int depth = (int) Math.round(3 - Math.abs(w)) + (r > PIER_END - 2 ? 1 : 0);
                        for (int k = 2; k <= depth; k++) {
                            plan.set(x, -k, z, k > 3 ? Material.DEEPSLATE : Material.COBBLED_DEEPSLATE);
                        }
                    }
                }
            }
            // the end: a bollard with a chain, a lamp post with a hanging arm, two lanterns hanging under the jetty
            int ex = cell(ox * (PIER_END - 0.5));
            int ez = cell(oz * (PIER_END - 0.5));
            plan.set(ex, 1, ez, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            plan.set(ex, 2, ez, Material.LODESTONE);
            for (int side : new int[] {-1, 1}) {
                int px = cell(ox * (PIER_END - 2) + tx * 2 * side);
                int pz = cell(oz * (PIER_END - 2) + tz * 2 * side);
                ShapeKit.column(plan, px, 1, 5, pz, Material.QUARTZ_PILLAR);
                plan.set(px, 6, pz, Material.CHISELED_QUARTZ_BLOCK);
                plan.set(px, 7, pz, Material.LANTERN, "hanging=false");
                int hx = cell(ox * (PIER_END + 0.6) + tx * 1.4 * side);
                int hz = cell(oz * (PIER_END + 0.6) + tz * 1.4 * side);
                for (int y = -1; y >= -6; y--) {
                    plan.set(hx, y, hz, Material.IRON_CHAIN, "axis=y");
                }
                plan.set(hx, -7, hz, Material.SOUL_LANTERN, "hanging=true");
                if (plan.get(hx, 0, hz) == null) {
                    plan.set(hx, 0, hz, Material.POLISHED_BLACKSTONE_BRICK_SLAB, "type=bottom");
                }
            }
        }
    }

    /**
     * The heart: a stepped dais with eight sigil stones, a beacon whose light rises through a cage of four quartz needles
     * to the armillary (a display model, see {@link #models}) turning high above the Quai.
     */
    private static void heart(BlockPlan plan) {
        ShapeKit.ring(plan, 0, 1, 0, 6, 7.2, Material.QUARTZ_SLAB);
        ShapeKit.disc(plan, 0, 1, 0, 6, Material.POLISHED_BLACKSTONE_BRICKS);
        ShapeKit.ring(plan, 0, 1, 0, 5.2, 6, Material.SMOOTH_QUARTZ);
        ShapeKit.ring(plan, 0, 2, 0, 3.6, 4.6, Material.SMOOTH_QUARTZ_SLAB);
        ShapeKit.disc(plan, 0, 2, 0, 3.6, Material.CHISELED_POLISHED_BLACKSTONE);
        for (Gate gate : GATES) {
            int x = cell(gate.outX() * 5.6);
            int z = cell(gate.outZ() * 5.6);
            plan.set(x, 1, z, Material.CRYING_OBSIDIAN);
            plan.set(x, 2, z, Material.LODESTONE);
            plan.set(x, 3, z, Material.LANTERN, "hanging=false");
        }
        // the beacon on its gold base, open to the sky
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                plan.set(x, 2, z, Material.GOLD_BLOCK);
            }
        }
        plan.set(0, 3, 0, Material.BEACON);
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {2, 2}, {-2, 2}}) {
            plan.set(c[0], 3, c[1], Material.CHISELED_QUARTZ_BLOCK);
            ShapeKit.column(plan, c[0], 4, 12, c[1], Material.QUARTZ_PILLAR);
            plan.set(c[0], 8, c[1], Material.CHISELED_QUARTZ_BLOCK);
            plan.set(c[0], 13, c[1], Material.CHISELED_QUARTZ_BLOCK);
            plan.set(c[0], 14, c[1], Material.END_ROD, "facing=up");
            plan.set(c[0], 15, c[1], Material.END_ROD, "facing=up");
        }
    }

    /**
     * A Sky Gate: pillars twelve high in two shades of quartz with the Realm's colour in two bands, a pointed arch over a
     * 5 x 9 opening, buttresses, a keystone (the crown that lights), lanterns on the outer side, a pad with a ring of the
     * Realm's colour, the sign in front. The halo over it is a display model (see {@link #models}).
     */
    private static void gate(BlockPlan plan, Gate gate) {
        double[] t = tangent(gate);
        Material colour = QuaiLanding.concrete(gate.realm());
        // pad under the gate
        ShapeKit.disc(plan, gate.x(), 0, gate.z(), 4.4, Material.POLISHED_BLACKSTONE_BRICKS);
        ShapeKit.ring(plan, gate.x(), 0, gate.z(), 3.5, 4.4, colour);
        ShapeKit.disc(plan, gate.x(), 0, gate.z(), 2.6, Material.CHISELED_POLISHED_BLACKSTONE);
        Set<Long> frame = framePositions(gate.x(), gate.z(), t[0], t[1]);
        for (long key : frame) {
            int x = BlockPlan.keyX(key);
            int z = BlockPlan.keyZ(key);
            plan.set(x, 0, z, Material.SMOOTH_QUARTZ);
            plan.set(x, 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
            ShapeKit.column(plan, x, 2, 10, z, Material.QUARTZ_PILLAR);
            plan.set(x, 4, z, colour);
            plan.set(x, 8, z, colour);
        }
        // buttresses: a stepped spur behind each pillar (on the outer side, so the walk between the gates stays free)
        for (int side : new int[] {-1, 1}) {
            for (int d = 1; d <= 2; d++) {
                int x = cell(gate.x() + side * 3.4 * t[0] + d * gate.outX());
                int z = cell(gate.z() + side * 3.4 * t[1] + d * gate.outZ());
                boolean inner = d == 1;
                int top = inner ? 6 : 3;
                plan.set(x, 0, z, Material.POLISHED_BLACKSTONE_BRICKS);
                ShapeKit.column(plan, x, 1, top, z, Material.POLISHED_BLACKSTONE_BRICKS);
                if (inner) {
                    plan.set(x, top + 1, z, Material.LANTERN, "hanging=false");
                } else {
                    plan.set(x, top + 1, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB, "type=bottom");
                }
            }
        }
        // the pointed arch: between the pillars above the opening (rows 8 to 10), a capped row 11, the keystone row 12
        for (int y = 8; y <= CROWN_Y; y++) {
            double span = y <= 10 ? 2.35 : y == 11 ? 3.6 : 1.6;
            for (double s = -span; s <= span + 0.001; s += 0.25) {
                if (y <= OPENING_TOP && Math.abs(s) <= openingHalf(y) + 0.3) {
                    continue; // the opening
                }
                int x = cell(gate.x() + s * t[0]);
                int z = cell(gate.z() + s * t[1]);
                if (y <= 10 && frame.contains(BlockPlan.key(x, 0, z))) {
                    continue; // the pillars
                }
                plan.set(x, y, z, y >= 11 ? Material.CHISELED_QUARTZ_BLOCK : Material.SMOOTH_QUARTZ);
            }
        }
        for (int[] cell : gate.membrane()) {
            plan.set(cell[0], cell[1], cell[2], DARK_MEMBRANE);
        }
        plan.set(gate.crown()[0], gate.crown()[1], gate.crown()[2], DARK_CROWN);
        plan.set(gate.crown()[0], gate.crown()[1] + 1, gate.crown()[2], Material.END_ROD, "facing=up");
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
                // the end of the hub's processional: same road, same pale stone
                Material m = Math.abs(w) <= 1 ? Material.QUARTZ_BRICKS : Material.POLISHED_DIORITE;
                plan.set(w, 0, z, m);
                plan.set(w, -1, z, Material.STONE_BRICKS);
            }
            if (z < -RADIUS) {
                for (int w = -1; w <= 1; w++) {
                    plan.set(w, -2, z, Material.COBBLED_DEEPSLATE);
                }
            }
        }
        // a ruined arch half way over the bridge: the left pillar whole, the right one broken, no lintel on that side
        int az = BRIDGE_FROM + 4;
        for (int sx : new int[] {-3, 3}) {
            int top = sx < 0 ? 7 : 4;
            plan.set(sx, 0, az, Material.SMOOTH_QUARTZ);
            ShapeKit.column(plan, sx, 1, top, az, Material.QUARTZ_PILLAR);
        }
        for (int x = -3; x <= 0; x++) {
            plan.set(x, 8, az, x == 0 ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_BLOCK);
        }
        plan.set(-1, 7, az, Material.LANTERN, "hanging=true");
        for (int z = BRIDGE_FROM + 2; z < -RADIUS; z += 3) {
            for (int sx : new int[] {-3, 3}) {
                plan.set(sx, 0, z, Material.POLISHED_BLACKSTONE_BRICKS);
                ShapeKit.column(plan, sx, 1, 2, z, Material.STONE_BRICK_WALL);
                plan.set(sx, 3, z, Material.LANTERN, "hanging=false");
            }
        }
        // the threshold of the Quai: two tall quartz posts where the bridge meets the rim
        for (int sx : new int[] {-3, 3}) {
            int z = -RADIUS + 1;
            plan.set(sx, 0, z, Material.POLISHED_BLACKSTONE_BRICKS);
            ShapeKit.column(plan, sx, 1, 6, z, Material.QUARTZ_PILLAR);
            plan.set(sx, 7, z, Material.CHISELED_QUARTZ_BLOCK);
            plan.set(sx, 8, z, Material.LANTERN, "hanging=false");
        }
    }

    /** A low wall round the rim with quartz posts and lanterns, open at the bridge and at each pier. */
    private static void balustrade(BlockPlan plan) {
        for (double deg = 0; deg < 360; deg += 0.5) {
            double a = Math.toRadians(deg);
            int x = cell(Math.sin(a) * (RADIUS - 0.5));
            int z = cell(-Math.cos(a) * (RADIUS - 0.5));
            if (Math.abs(x) <= 3 && z < 0) {
                continue; // the bridge
            }
            boolean pier = false;
            for (int p : PIER_ANGLES) {
                double diff = Math.abs(((deg - p) % 360 + 540) % 360 - 180);
                pier |= diff < 6;
            }
            if (pier || plan.get(x, 1, z) != null) {
                continue;
            }
            boolean post = Math.abs(((deg + 11.25) % 22.5) - 11.25) < 0.3 && !pier;
            if (post) {
                ShapeKit.column(plan, x, 1, 2, z, Material.QUARTZ_PILLAR);
                plan.set(x, 3, z, Material.LANTERN, "hanging=false");
            } else {
                plan.set(x, 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
        }
    }

    /**
     * The keel: a hollow inverted spire forty blocks deep, pale stone into deepslate into blackstone, veins of crying
     * obsidian and amethyst toward the tip, a hanging crystal at the end, dripstone off the rim.
     */
    private static void underside(BlockPlan plan) {
        int depth = 40;
        for (int k = 1; k <= depth; k++) {
            double f = 1 - (double) k / (depth + 1);
            double r = RADIUS * Math.pow(f, 1.35);
            Material m = k <= 3 ? Material.STONE_BRICKS : k <= 12 ? Material.COBBLED_DEEPSLATE : k <= 26 ? Material.DEEPSLATE
                : Material.BLACKSTONE;
            if (k <= 2 || r < 2.5) {
                ShapeKit.disc(plan, 0, -k, 0, Math.max(r, 0.6), m);
            } else {
                ShapeKit.ring(plan, 0, -k, 0, r - 2.2, r, m);
            }
            if (k > 14) {
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(i * 60 + k * 9);
                    int x = cell(Math.sin(a) * (r - 0.4));
                    int z = cell(Math.cos(a) * (r - 0.4));
                    plan.set(x, -k, z, (k + i) % 3 == 0 ? Material.AMETHYST_BLOCK : Material.CRYING_OBSIDIAN);
                }
            }
        }
        plan.set(0, -depth - 1, 0, Material.AMETHYST_BLOCK);
        plan.set(0, -depth - 2, 0, Material.AMETHYST_CLUSTER, "facing=down");
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15 + 7);
            int x = cell(Math.sin(a) * (RADIUS - 2.5));
            int z = cell(Math.cos(a) * (RADIUS - 2.5));
            int len = 1 + (i * 7) % 3;
            int top = -3;
            if (plan.get(x, top + 1, z) == null) {
                continue;
            }
            for (int y = 0; y < len; y++) {
                String thick = y == len - 1 ? "tip" : y == len - 2 ? "frustum" : "middle";
                plan.set(x, top - y, z, Material.POINTED_DRIPSTONE, "thickness=" + thick + ",vertical_direction=down");
            }
        }
    }

    /**
     * Invisible light blocks so the Quai reads at night: round each gate, along the rim and over the dais. Never on the
     * way to the mouth (x -1..1, z 8..18) or on the mouth itself, and never where a block already stands.
     */
    private static void lights(BlockPlan plan) {
        for (Gate gate : GATES) {
            double[] t = tangent(gate);
            for (int side : new int[] {-1, 1}) {
                light(plan, cell(gate.x() + side * 2.2 * t[0] - 2 * gate.outX()), 3, cell(gate.z() + side * 2.2 * t[1] - 2 * gate.outZ()));
                light(plan, cell(gate.x() + side * 3.2 * t[0] + 3 * gate.outX()), 4, cell(gate.z() + side * 3.2 * t[1] + 3 * gate.outZ()));
            }
            light(plan, cell(gate.x() - 3 * gate.outX()), 7, cell(gate.z() - 3 * gate.outZ()));
        }
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 + 11.25);
            light(plan, cell(Math.sin(a) * (RADIUS - 3)), 3, cell(-Math.cos(a) * (RADIUS - 3)));
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            light(plan, cell(Math.sin(a) * 4), 5, cell(-Math.cos(a) * 4));
        }
    }

    private static void light(BlockPlan plan, int x, int y, int z) {
        if (plan.get(x, y, z) != null || (Math.abs(x) <= 1 && z >= 8 && z <= QuaiMouth.Z) || (Math.abs(x) <= 6 && z >= QuaiMouth.Z - 4)) {
            return;
        }
        plan.set(x, y, z, Material.LIGHT, "level=15");
    }

    /** The display models: the armillary over the dais, a dormant halo over each gate and its lit twin (hidden). */
    private static void models(BlockPlan plan) {
        double cy = ARMILLARY_Y;
        float sc = ARMILLARY_SCALE;
        plan.model(BlockPlan.Model.of("structure/quai_armillary_ring_outer", 0.5, cy, 0.5, sc).tilt(14, 0, 0).spin(5, "x"));
        plan.model(BlockPlan.Model.of("structure/quai_armillary_ring_mid", 0.5, cy, 0.5, sc).spin(9, "y"));
        plan.model(BlockPlan.Model.of("structure/quai_armillary_ring_inner", 0.5, cy, 0.5, sc).tilt(0, 0, 24).spin(-13, "y"));
        plan.model(BlockPlan.Model.of("structure/quai_armillary_core", 0.5, cy, 0.5, sc).spin(20, "y"));
        for (Gate gate : GATES) {
            float yaw = (float) Math.toDegrees(Math.atan2(gate.outX(), gate.outZ()));
            double x = gate.x() + 0.5;
            double z = gate.z() + 0.5;
            String id = gate.realm().id();
            plan.model(BlockPlan.Model.of("structure/quai_halo", x, HALO_Y, z, 3.0f).tilt(0, yaw, 0).spin(10, "z")
                .key("halo:" + id, false));
            plan.model(BlockPlan.Model.of("structure/quai_halo_lit", x, HALO_Y, z, 3.0f).tilt(0, yaw, 0).spin(18, "z")
                .color(haloColor(gate.realm())).key("halo_lit:" + id, true));
        }
    }
}
