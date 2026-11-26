package win.vampyuyu.survival.island;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * The landmark of each Realm, seen from its landing: a monument floating high in the sky north of the arrival spot (a
 * rock with a deep keel, the Realm's own crown of blocks on top, chains, roots or icicles hanging under it) with the
 * Realm's relic turning over it (a display model, {@code structure/<realm>_relic}). It is out of reach and changes no
 * mechanic: blocks never replace what the Realm's plan already placed. Pure data, plus the runtime that keeps the
 * relics up ({@link #tick}).
 */
public final class RealmLandmarks {
    /** Where the monument of a Realm floats (relative to its plan origin, top surface at {@code y}). */
    public record Site(Realm realm, String world, int originY, int x, int y, int z, int radius) {}

    /** The blocks of one Realm's monument. */
    record Palette(Material top, Material body, Material deep, Material glow, Material hang, String relic) {}

    private static final Map<Realm, StructureModels> LIVE = new EnumMap<>(Realm.class);

    private RealmLandmarks() {}

    public static Site site(Realm realm) {
        return switch (realm) {
            case ASHES -> new Site(realm, SkyblockWorlds.ASHES, 96, 0, 30, -16, 8);
            case GROVE -> new Site(realm, SkyblockWorlds.GROVE, 100, 0, 30, -14, 8);
            case HEIGHTS -> new Site(realm, SkyblockWorlds.HEIGHTS, 100, 0, 32, -14, 8);
            case CITADEL -> new Site(realm, SkyblockWorlds.CITADEL, 100, 0, 40, -22, 8);
            case MARSHES -> new Site(realm, SkyblockWorlds.MARSH, 100, 0, 28, -10, 8);
            case ARCHIPELAGO -> new Site(realm, SkyblockWorlds.STORM, 120, 0, 30, -18, 8);
            case DEPTHS -> new Site(realm, SkyblockWorlds.DEPTHS, 100, 0, 30, -22, 8);
            case NECROPOLIS -> new Site(realm, SkyblockWorlds.NECROPOLIS, 100, 0, 30, -14, 8);
        };
    }

    static Palette palette(Realm realm) {
        return switch (realm) {
            case ASHES -> new Palette(Material.BLACKSTONE, Material.BASALT, Material.POLISHED_BLACKSTONE_BRICKS,
                Material.MAGMA_BLOCK, Material.IRON_CHAIN, "ashes_relic");
            case GROVE -> new Palette(Material.MOSS_BLOCK, Material.ROOTED_DIRT, Material.MUD, Material.SHROOMLIGHT,
                Material.HANGING_ROOTS, "grove_relic");
            case HEIGHTS -> new Palette(Material.SNOW_BLOCK, Material.PACKED_ICE, Material.STONE, Material.SEA_LANTERN,
                Material.POINTED_DRIPSTONE, "heights_relic");
            case CITADEL -> new Palette(Material.SMOOTH_SANDSTONE, Material.SANDSTONE, Material.CUT_RED_SANDSTONE,
                Material.GLOWSTONE, Material.IRON_CHAIN, "citadel_relic");
            case MARSHES -> new Palette(Material.MUD, Material.PACKED_MUD, Material.MUD_BRICKS, Material.OCHRE_FROGLIGHT,
                Material.HANGING_ROOTS, "marsh_relic");
            case ARCHIPELAGO -> new Palette(Material.WAXED_OXIDIZED_CUT_COPPER, Material.ANDESITE, Material.DEEPSLATE,
                Material.SEA_LANTERN, Material.IRON_CHAIN, "storm_relic");
            case DEPTHS -> new Palette(Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.DARK_PRISMARINE,
                Material.SEA_LANTERN, Material.IRON_CHAIN, "depths_relic");
            case NECROPOLIS -> new Palette(Material.SOUL_SOIL, Material.DEEPSLATE_TILES, Material.BLACKSTONE,
                Material.VERDANT_FROGLIGHT, Material.IRON_CHAIN, "necropolis_relic");
        };
    }

    private static int h(int x, int z, int salt) {
        int n = x * 374761393 + z * 668265263 + salt * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return (n ^ (n >>> 16)) & 0x7fffffff;
    }

    /** Sets a block only where the Realm's plan left nothing (the monument never overwrites the Realm). */
    private static void put(BlockPlan plan, int x, int y, int z, Material m, String props) {
        if (plan.get(x, y, z) == null) {
            plan.set(x, y, z, m, props);
        }
    }

    private static void put(BlockPlan plan, int x, int y, int z, Material m) {
        put(plan, x, y, z, m, null);
    }

    /** Height of the crown over the monument's top (the relic floats above it). */
    static int crownHeight(Realm realm) {
        return switch (realm) {
            case GROVE -> 12;
            case HEIGHTS -> 14;
            case CITADEL -> 13;
            case ARCHIPELAGO -> 12;
            default -> 9;
        };
    }

    /** Adds a Realm's monument and its relic to the Realm's plan. Call it last: it only fills empty cells. */
    public static void build(BlockPlan plan, Realm realm) {
        Site s = site(realm);
        Palette p = palette(realm);
        int r = s.radius();
        int depth = 14;
        // the rock: a wobbly disc, then a keel tapering to a point, hollow under the first layers
        for (int k = 0; k <= depth; k++) {
            double rk = r * Math.pow(1 - (double) k / (depth + 1), 0.8);
            int ri = (int) Math.ceil(rk) + 1;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double wobble = (h(dx, dz, k == 0 ? 1 : 2) % 100) / 100.0 * 1.2;
                    double d = Math.sqrt((double) dx * dx + dz * dz);
                    if (d > rk + wobble - 0.6) {
                        continue;
                    }
                    if (k > 2 && d < rk - 2.2) {
                        continue; // hollow keel: only its skin is built
                    }
                    Material m = k == 0 ? p.top() : k < 4 ? p.body() : p.deep();
                    if (k > 3 && h(dx, dz, k) % 11 == 0) {
                        m = p.glow();
                    }
                    put(plan, s.x() + dx, s.y() - k, s.z() + dz, m);
                }
            }
        }
        // things hanging under the rim
        for (int i = 0; i < 14; i++) {
            double a = Math.toRadians(i * 360.0 / 14 + 9);
            int x = s.x() + (int) Math.round(Math.cos(a) * (r - 2));
            int z = s.z() + (int) Math.round(Math.sin(a) * (r - 2));
            int len = 3 + h(x, z, 5) % 7;
            int top = s.y() - 3;
            for (int k = 0; k < len; k++) {
                if (p.hang() == Material.POINTED_DRIPSTONE) {
                    String thick = k == len - 1 ? "tip" : k == len - 2 ? "frustum" : "middle";
                    put(plan, x, top - k, z, p.hang(), "thickness=" + thick + ",vertical_direction=down");
                } else {
                    put(plan, x, top - k, z, p.hang(), p.hang() == Material.IRON_CHAIN ? "axis=y" : null);
                }
            }
            if (p.hang() == Material.IRON_CHAIN) {
                put(plan, x, top - len, z, realm == Realm.NECROPOLIS || realm == Realm.ASHES ? Material.SOUL_LANTERN
                    : Material.LANTERN, "hanging=true");
            }
        }
        crown(plan, realm, s, p);
        plan.model(relic(realm));
    }

    /** The relic over a Realm's monument, relative to the Realm's plan origin. */
    public static BlockPlan.Model relic(Realm realm) {
        Site s = site(realm);
        return BlockPlan.Model.of("structure/" + palette(realm).relic(), s.x() + 0.5, s.y() + crownHeight(realm) + 7.5,
            s.z() + 0.5, relicScale(realm)).spin(realm == Realm.DEPTHS || realm == Realm.MARSHES ? 6 : 12, "y");
    }

    /** Scale of a relic: the slimmer models (hourglass, lantern, bell, mushrooms) are shown bigger. */
    static float relicScale(Realm realm) {
        return switch (realm) {
            case CITADEL, MARSHES, DEPTHS -> 5.5f;
            case GROVE, NECROPOLIS, ASHES -> 4.8f;
            default -> 4f;
        };
    }

    /** The Realm's own crown of blocks on top of the rock. */
    private static void crown(BlockPlan plan, Realm realm, Site s, Palette p) {
        int x = s.x();
        int y = s.y() + 1;
        int z = s.z();
        switch (realm) {
            case ASHES -> { // three jagged basalt horns round a magma vent
                spike(plan, x - 3, y, z - 1, 9, Material.BASALT, Material.MAGMA_BLOCK);
                spike(plan, x + 3, y, z + 1, 7, Material.BASALT, Material.MAGMA_BLOCK);
                spike(plan, x, y, z + 3, 6, Material.BLACKSTONE, Material.MAGMA_BLOCK);
                put(plan, x, y - 1, z, Material.MAGMA_BLOCK);
                put(plan, x, y, z, Material.CAMPFIRE, "lit=true");
            }
            case GROVE -> { // a gnarled tree with a broad moss canopy
                for (int k = 0; k < 10; k++) {
                    put(plan, x + (k > 6 ? 1 : 0), y + k, z, Material.MANGROVE_LOG);
                }
                ShapeKit.leaves(plan, x + 1, y + 11, z, 5.5, Material.AZALEA_LEAVES);
                ShapeKit.leaves(plan, x - 2, y + 9, z + 2, 3.5, Material.FLOWERING_AZALEA_LEAVES);
                put(plan, x + 1, y + 10, z, Material.SHROOMLIGHT);
            }
            case HEIGHTS -> { // three ice peaks
                spike(plan, x, y, z, 14, Material.PACKED_ICE, Material.BLUE_ICE);
                spike(plan, x - 4, y, z + 2, 9, Material.PACKED_ICE, Material.BLUE_ICE);
                spike(plan, x + 4, y, z - 2, 10, Material.PACKED_ICE, Material.BLUE_ICE);
            }
            case CITADEL -> { // an obelisk with a gold cap between two small pyramids
                for (int k = 0; k < 12; k++) {
                    put(plan, x, y + k, z, k % 4 == 3 ? Material.CHISELED_SANDSTONE : Material.CUT_SANDSTONE);
                }
                put(plan, x, y + 12, z, Material.GOLD_BLOCK);
                for (int sx : new int[] {-4, 4}) {
                    for (int k = 0; k < 3; k++) {
                        ShapeKit.disc(plan, x + sx, y + k, z, 2.4 - k * 0.9, Material.SMOOTH_SANDSTONE);
                    }
                    put(plan, x + sx, y + 3, z, Material.GLOWSTONE);
                }
            }
            case MARSHES -> { // a drowned mangrove: arched roots and a crooked trunk
                for (int[] root : new int[][] {{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
                    for (int k = 0; k < 3; k++) {
                        put(plan, x + root[0] * (3 - k) / 3, y + k, z + root[1] * (3 - k) / 3, Material.MANGROVE_ROOTS);
                    }
                }
                for (int k = 2; k < 9; k++) {
                    put(plan, x, y + k, z, Material.MANGROVE_LOG);
                }
                ShapeKit.leaves(plan, x, y + 10, z, 4.2, Material.MANGROVE_LEAVES);
                put(plan, x, y + 9, z, Material.OCHRE_FROGLIGHT);
            }
            case ARCHIPELAGO -> { // copper storm spires with lightning rods
                spike(plan, x, y, z, 12, Material.WAXED_OXIDIZED_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER);
                spike(plan, x - 4, y, z + 1, 7, Material.WAXED_WEATHERED_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER);
                spike(plan, x + 4, y, z - 1, 8, Material.WAXED_WEATHERED_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER);
                put(plan, x, y + 12, z, Material.LIGHTNING_ROD, "facing=up");
            }
            case DEPTHS -> { // a ring of prismarine pillars round a sea lantern well
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(i * 60);
                    int px = x + (int) Math.round(Math.cos(a) * 4);
                    int pz = z + (int) Math.round(Math.sin(a) * 4);
                    for (int k = 0; k < 4 + i % 3 * 2; k++) {
                        put(plan, px, y + k, pz, k % 3 == 2 ? Material.SEA_LANTERN : Material.PRISMARINE_BRICKS);
                    }
                }
                put(plan, x, y - 1, z, Material.SEA_LANTERN);
            }
            case NECROPOLIS -> { // bone pillars round a soul fire
                for (int i = 0; i < 5; i++) {
                    double a = Math.toRadians(i * 72 + 18);
                    int px = x + (int) Math.round(Math.cos(a) * 4);
                    int pz = z + (int) Math.round(Math.sin(a) * 4);
                    for (int k = 0; k < 5 + i % 2 * 3; k++) {
                        put(plan, px, y + k, pz, Material.BONE_BLOCK, "axis=y");
                    }
                    put(plan, px, y + 5 + i % 2 * 3, pz, Material.SOUL_LANTERN, "hanging=false");
                }
                put(plan, x, y, z, Material.SOUL_CAMPFIRE, "lit=true");
            }
        }
    }

    /** A tapering spike of {@code height}, a glowing core every few blocks. */
    private static void spike(BlockPlan plan, int x, int y, int z, int height, Material m, Material vein) {
        for (int k = 0; k < height; k++) {
            double r = 1.6 * (1 - (double) k / height);
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (Math.sqrt((double) dx * dx + dz * dz) <= r + 0.3) {
                        put(plan, x + dx, y + k, z + dz, dx == 0 && dz == 0 && k % 3 == 1 ? vein : m);
                    }
                }
            }
        }
    }

    /** The paving of a Realm's arrival plaza: field, bands, the glowing inlay and the posts round its edge. */
    record Plaza(Material field, Material alt, Material band, Material inlay, Material post, Material light) {}

    static Plaza plaza(Realm realm) {
        return switch (realm) {
            case ASHES -> new Plaza(Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                Material.POLISHED_BASALT, Material.MAGMA_BLOCK, Material.POLISHED_BLACKSTONE_BRICK_WALL, Material.SOUL_LANTERN);
            case GROVE -> new Plaza(Material.MOSS_BLOCK, Material.MUDDY_MANGROVE_ROOTS, Material.STRIPPED_MANGROVE_WOOD,
                Material.SHROOMLIGHT, Material.MANGROVE_FENCE, Material.LANTERN);
            case HEIGHTS -> new Plaza(Material.STONE_BRICKS, Material.SNOW_BLOCK, Material.PACKED_ICE, Material.SEA_LANTERN,
                Material.STONE_BRICK_WALL, Material.LANTERN);
            case CITADEL -> new Plaza(Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE,
                Material.GLOWSTONE, Material.SANDSTONE_WALL, Material.LANTERN);
            case MARSHES -> new Plaza(Material.MUD_BRICKS, Material.PACKED_MUD, Material.MANGROVE_PLANKS, Material.OCHRE_FROGLIGHT,
                Material.MUD_BRICK_WALL, Material.LANTERN);
            case ARCHIPELAGO -> new Plaza(Material.QUARTZ_BRICKS, Material.SMOOTH_QUARTZ, Material.WAXED_OXIDIZED_CUT_COPPER,
                Material.SEA_LANTERN, Material.LIGHTNING_ROD, Material.LANTERN);
            case DEPTHS -> new Plaza(Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.DARK_PRISMARINE,
                Material.SEA_LANTERN, Material.PRISMARINE_WALL, Material.LANTERN);
            case NECROPOLIS -> new Plaza(Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.CHISELED_DEEPSLATE,
                Material.VERDANT_FROGLIGHT, Material.DEEPSLATE_TILE_WALL, Material.SOUL_LANTERN);
        };
    }

    /** Radius of the arrival plaza round the spot players land on. */
    public static final int PLAZA_RADIUS = 7;

    /**
     * Repaves the arrival of a Realm as a plaza: only the floor that already exists round the arrival spot is changed
     * (nothing is added over the void, no gameplay block is moved), in concentric bands with a ray of the Realm's colour
     * toward the return arch, eight glowing stones in the floor, low posts with lanterns where the floor meets the void
     * (never where a path leaves), and light blocks overhead. Call it before {@link ReturnArch#build}.
     */
    public static void landing(BlockPlan plan, Realm realm, int sx, int sy, int sz) {
        Plaza p = plaza(realm);
        ReturnArch.Side side = ReturnArch.side(realm);
        int fy = sy - 1;
        int r = PLAZA_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt((double) dx * dx + dz * dz);
                int x = sx + dx;
                int z = sz + dz;
                Material under = plan.get(x, fy, z);
                Material above = plan.get(x, sy, z);
                if (d > r + 0.3 || under == null || under.isAir() || (above != null && !above.isAir())) {
                    continue;
                }
                int along = side.ox * dx + side.oz * dz;   // toward the arch
                int across = side.ax * dx + side.az * dz;
                Material m;
                if (d > r - 0.7) {
                    m = p.band();
                } else if (Math.abs(d - 3.5) < 0.5) {
                    m = p.band();
                } else if (along > 0 && Math.abs(across) <= 0) {
                    m = p.inlay(); // the ray of light that leads to the arch
                } else {
                    m = ((dx + dz) & 1) == 0 ? p.field() : p.alt();
                }
                plan.set(x, fy, z, m);
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int x = sx + (int) Math.round(Math.cos(a) * 5.4);
            int z = sz + (int) Math.round(Math.sin(a) * 5.4);
            Material under = plan.get(x, fy, z);
            Material above = plan.get(x, sy, z);
            if (under != null && !under.isAir() && (above == null || above.isAir())) {
                plan.set(x, fy, z, p.inlay());
            }
        }
        // posts where the plaza's floor ends over the void: never where the floor (a path, more ground) carries on
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5);
            int x = sx + (int) Math.round(Math.cos(a) * r);
            int z = sz + (int) Math.round(Math.sin(a) * r);
            int ox = sx + (int) Math.round(Math.cos(a) * (r + 2));
            int oz = sz + (int) Math.round(Math.sin(a) * (r + 2));
            Material under = plan.get(x, fy, z);
            Material beyond = plan.get(ox, fy, oz);
            boolean nearArch = Math.abs(side.ox * (x - sx) + side.oz * (z - sz)) >= 1
                && side.ox * (x - sx) + side.oz * (z - sz) > 0 && Math.abs(side.ax * (x - sx) + side.az * (z - sz)) <= 4;
            if (under == null || under.isAir() || (beyond != null && !beyond.isAir()) || nearArch
                || plan.get(x, sy, z) != null || plan.get(x, sy + 1, z) != null) {
                continue;
            }
            plan.set(x, sy, z, p.post(), p.post() == Material.LIGHTNING_ROD ? "facing=up" : null);
            plan.set(x, sy + 1, z, p.light(), "hanging=false");
        }
        // light from above, so the plaza reads at night
        for (int[] o : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {0, -6}, {-6, 0}, {6, 0}}) {
            int x = sx + o[0];
            int z = sz + o[1];
            if (plan.get(x, sy + 3, z) == null && plan.get(x, fy, z) != null) {
                plan.set(x, sy + 3, z, Material.LIGHT, "level=14");
            }
        }
    }

    /** Keeps each Realm's relic up while players are near it (called every {@code periodTicks}). */
    public static void tick(Plugin plugin, int periodTicks) {
        for (Realm realm : Realm.values()) {
            Site s = site(realm);
            World world = Bukkit.getWorld(s.world());
            if (world == null) {
                continue;
            }
            StructureModels models = LIVE.computeIfAbsent(realm, k -> new StructureModels(plugin, "relic:" + realm.id(),
                world, 0, s.originY(), 0, List.of(relic(realm))));
            models.tick(periodTicks);
        }
        tickAstral(plugin, periodTicks);
    }

    private static StructureModels astral;

    /** The Astral Interiority's models (accretion rings, the Throne's halo), kept up like the relics. */
    private static void tickAstral(Plugin plugin, int periodTicks) {
        World world = Bukkit.getWorld(SkyblockWorlds.ASTRAL);
        if (world == null) {
            return;
        }
        if (astral == null) {
            astral = new StructureModels(plugin, "astral", world, 0, win.vampyuyu.survival.astral.AstralLayout.ORIGIN_Y, 0,
                win.vampyuyu.survival.astral.AstralPlan.models());
        }
        astral.tick(periodTicks);
    }

    /** Removes every relic (feature stop). */
    public static void close() {
        LIVE.values().forEach(StructureModels::remove);
        LIVE.clear();
        if (astral != null) {
            astral.remove();
            astral = null;
        }
    }
}
