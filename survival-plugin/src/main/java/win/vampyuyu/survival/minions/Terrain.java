package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.minions.MinionConfig.Kind;
import win.vampyuyu.survival.minions.MinionConfig.Type;

/**
 * What a minion sees and does in the world: the flat square of blocks at its own height. Rules:
 * <ul>
 * <li>The area is the layer the minion stands in, radius blocks each way, without its own column.</li>
 * <li>It only ever replaces air, and it only ever removes the exact block it placed itself for the current cycle
 *     (recorded in {@link MinionData}, so it survives a restart): never a player's chest, wall or crop, even when
 *     it is made of the same material. Liquids are not air, so it never fills water or lava.</li>
 * <li>Nothing here runs unless every chunk of the area is loaded.</li>
 * </ul>
 */
public final class Terrain {
    /** PDC key marking a mob spawned by a minion; the value is the minion id. */
    public static final NamespacedKey MOB_TAG = new NamespacedKey("vampyuyu", "minion_mob");

    private Terrain() {}

    /** A work area: the minion block and radius. */
    public record Area(World world, int x, int y, int z, int radius) {
        public boolean contains(int bx, int by, int bz) {
            return by == y && Math.abs(bx - x) <= radius && Math.abs(bz - z) <= radius && !(bx == x && bz == z);
        }
    }

    public static Area areaOf(World world, MinionData d, int radius) {
        return new Area(world, d.x, d.y, d.z, radius);
    }

    /** True when every chunk the area touches is loaded. */
    public static boolean loaded(Area area) {
        int minX = (area.x() - area.radius()) >> 4;
        int maxX = (area.x() + area.radius()) >> 4;
        int minZ = (area.z() - area.radius()) >> 4;
        int maxZ = (area.z() + area.radius()) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!area.world().isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The four corners, for the island containment check. */
    public static List<Location> corners(Area area) {
        int r = area.radius();
        World w = area.world();
        return List.of(new Location(w, area.x() - r, area.y(), area.z() - r), new Location(w, area.x() + r, area.y(), area.z() - r),
            new Location(w, area.x() - r, area.y(), area.z() + r), new Location(w, area.x() + r, area.y(), area.z() + r));
    }

    /** Blocks of the area layer, the minion's own column excluded. */
    public static List<Block> blocks(Area area) {
        return layer(area, 0);
    }

    private static List<Block> layer(Area area, int dy) {
        List<Block> out = new ArrayList<>();
        int r = area.radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx != 0 || dz != 0) {
                    out.add(area.world().getBlockAt(area.x() + dx, area.y() + dy, area.z() + dz));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- scanning

    /** Looks at the area and says whether the minion can work there (no cycle in progress). */
    public static Status scan(Area area, Type type, MinionConfig config) {
        return scan(area, type, config, null);
    }

    /**
     * Looks at the area and says whether the minion can work there. The block the minion placed for the cycle in
     * progress ({@code d}) counts as workable: it is going to be removed at the end of the cycle.
     */
    public static Status scan(Area area, Type type, MinionConfig config, @Nullable MinionData d) {
        return switch (type.kind()) {
            case MINING -> scanMining(area, type, d);
            case FARMING -> scanFarming(area, type, d);
            case FORAGING -> scanForaging(area, type, d);
            case ANIMAL, COMBAT -> scanRoom(area) ? Status.WORKING : Status.NO_SPACE;
            case FISHING -> countWater(area) >= config.fishingMinWater ? Status.WORKING : Status.NO_WATER;
        };
    }

    /** True when the minion has a placed block inside this area waiting to be removed. */
    private static boolean hasTracked(Area area, @Nullable MinionData d) {
        return d != null && d.placed && area.contains(d.spotX, d.spotY, d.spotZ);
    }

    private static Status scanMining(Area area, Type type, @Nullable MinionData d) {
        if (hasTracked(area, d)) {
            return Status.WORKING;
        }
        boolean airWithoutGround = false;
        for (Block b : blocks(area)) {
            if (isFreeSpot(b)) {
                return Status.WORKING;
            }
            airWithoutGround |= b.getType().isAir();
        }
        // Air but nothing to stand the block on (a pillar, the void): the terrain is wrong, not just crowded.
        return airWithoutGround ? Status.INVALID_TERRAIN : Status.NO_SPACE;
    }

    /** A mining minion needs air with a solid block below it, so it never fills mid-air or the void. */
    private static boolean isFreeSpot(Block b) {
        return b.getType().isAir() && b.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    private static Status scanFarming(Area area, Type type, @Nullable MinionData d) {
        if (hasTracked(area, d)) {
            return Status.WORKING;
        }
        for (Block b : blocks(area)) {
            if (canPlant(b, type)) {
                return Status.WORKING;
            }
        }
        return Status.INVALID_TERRAIN;
    }

    private static boolean canPlant(Block b, Type type) {
        if (!b.getType().isAir()) {
            return false;
        }
        Material below = b.getRelative(BlockFace.DOWN).getType();
        return type.ageable() ? below == Material.FARMLAND : below == Material.FARMLAND || isSoil(below);
    }

    private static boolean isSoil(Material m) {
        return m == Material.DIRT || m == Material.GRASS_BLOCK || m == Material.PODZOL || m == Material.COARSE_DIRT
            || m == Material.ROOTED_DIRT || m == Material.MOSS_BLOCK || m == Material.MUD;
    }

    private static Status scanForaging(Area area, Type type, @Nullable MinionData d) {
        if (hasTracked(area, d)) {
            return Status.WORKING;
        }
        for (Block b : blocks(area)) {
            if (canGrow(b)) {
                return Status.WORKING;
            }
        }
        return Status.INVALID_TERRAIN;
    }

    private static boolean canGrow(Block b) {
        return b.getType().isAir() && isSoil(b.getRelative(BlockFace.DOWN).getType());
    }

    private static boolean scanRoom(Area area) {
        for (Block b : blocks(area)) {
            if (hasRoom(b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoom(Block b) {
        return b.getType().isAir() && b.getRelative(BlockFace.UP).getType().isAir()
            && b.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    private static int countWater(Area area) {
        int n = 0;
        for (Block b : layer(area, -1)) {
            if (b.getType() == Material.WATER) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- work steps

    /**
     * First half of a cycle: places the block / crop / sapling or spawns the mob. Returns the spot, null when
     * nothing could be placed (the cycle still counts: the scan already said the terrain is fine).
     */
    public static @Nullable Block place(Area area, Type type, MinionData d, MinionRuntime rt, RandomGenerator random,
            double scale) {
        switch (type.kind()) {
            case MINING -> {
                Block spot = pick(area, Terrain::isFreeSpot, random);
                if (spot != null) {
                    spot.setType(type.block(), false);
                    effect(spot, Particle.BLOCK, spot.getBlockData(), Sound.BLOCK_STONE_PLACE);
                }
                return spot;
            }
            case FARMING -> {
                Block spot = pick(area, b -> canPlant(b, type), random);
                if (spot != null) {
                    BlockData data = Bukkit.createBlockData(type.block());
                    if (data instanceof Ageable crop) {
                        crop.setAge(crop.getMaximumAge());
                    }
                    spot.setBlockData(data, false);
                    effect(spot, Particle.HAPPY_VILLAGER, null, Sound.ITEM_CROP_PLANT);
                }
                return spot;
            }
            case FORAGING -> {
                // The tree is a trunk block (the type's log): it appears on the soil and is chopped at the end of the
                // cycle. No sapling is planted, so nothing can grow, be bone-mealed or be mistaken for a player's.
                Block spot = pick(area, Terrain::canGrow, random);
                if (spot != null) {
                    spot.setType(type.block(), false);
                    effect(spot, Particle.HAPPY_VILLAGER, null, Sound.ITEM_CROP_PLANT);
                }
                return spot;
            }
            case ANIMAL, COMBAT -> {
                Block spot = pick(area, Terrain::hasRoom, random);
                if (spot != null) {
                    Entity mob = spawnMob(spot, type, d.id);
                    rt.mob = mob;
                    if (mob == null) {
                        return null;
                    }
                }
                return spot;
            }
            default -> {
                return null;
            }
        }
    }

    /** Remembers the block just placed as the minion's own. */
    public static void track(MinionData d, Block spot) {
        d.placed = true;
        d.spotX = spot.getX();
        d.spotY = spot.getY();
        d.spotZ = spot.getZ();
        d.spotMaterial = spot.getType().name();
    }

    /** Forgets the tracked block without touching the world. */
    public static void untrack(MinionData d) {
        d.placed = false;
        d.spotMaterial = null;
    }

    /** True for the kinds that place a real block in the world. */
    public static boolean placesBlocks(Type type) {
        return type.kind() == Kind.MINING || type.kind() == Kind.FARMING || type.kind() == Kind.FORAGING;
    }

    /**
     * Removes the tracked block when it is still the block the minion placed. Returns true when a block was removed.
     * Never removes anything else: a player who replaced or removed it keeps what they built.
     */
    public static boolean removeTracked(World world, MinionData d, boolean effects) {
        if (!d.placed) {
            return false;
        }
        boolean removed = false;
        // Never load a chunk just to clean up: an unloaded chunk keeps its block, the record stays for later.
        if (world.isChunkLoaded(d.spotX >> 4, d.spotZ >> 4)) {
            Block spot = world.getBlockAt(d.spotX, d.spotY, d.spotZ);
            if (d.spotMaterial != null && spot.getType().name().equals(d.spotMaterial)) {
                BlockData data = spot.getBlockData();
                spot.setType(Material.AIR, false);
                if (effects) {
                    effect(spot, Particle.BLOCK, data, breakSound(data.getMaterial()));
                }
                removed = true;
            }
            untrack(d);
        }
        return removed;
    }

    /**
     * Second half: removes the block placed by {@link #place} (and only that one), or kills the mob, with the
     * matching particles. Never touches anything else.
     */
    public static void harvest(Area area, Type type, MinionData d, MinionRuntime rt, RandomGenerator random) {
        switch (type.kind()) {
            case MINING, FARMING, FORAGING -> removeTracked(area.world(), d, true);
            case ANIMAL, COMBAT -> {
                Entity mob = rt.mob;
                if (mob != null && mob.isValid()) {
                    Location at = mob.getLocation().add(0, 0.5, 0);
                    mob.remove();
                    area.world().spawnParticle(Particle.POOF, at, 8, 0.25, 0.25, 0.25, 0.02);
                    area.world().playSound(at, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.25f, 1.6f);
                }
                rt.mob = null;
            }
            case FISHING -> {
                List<Block> water = new ArrayList<>();
                for (Block b : layer(area, -1)) {
                    if (b.getType() == Material.WATER) {
                        water.add(b);
                    }
                }
                if (!water.isEmpty()) {
                    Block b = water.get(random.nextInt(water.size()));
                    Location at = centre(b, 1.0);
                    area.world().spawnParticle(Particle.FISHING, at, 10, 0.2, 0.05, 0.2, 0.01);
                    area.world().playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.3f, 1.2f);
                }
            }
        }
    }

    /** A fresh location in the middle of a block, {@code up} above its floor (never mutates the block's own). */
    private static Location centre(Block b, double up) {
        return new Location(b.getWorld(), b.getX() + 0.5, b.getY() + up, b.getZ() + 0.5);
    }

    /** Removes the leftover of an interrupted cycle (the minion is being picked up or removed). */
    public static void cleanup(World world, MinionData d, MinionRuntime rt) {
        Entity mob = rt.mob;
        if (mob != null) {
            mob.remove();
            rt.mob = null;
        }
        rt.placed = false;
        removeTracked(world, d, false);
    }

    private static Sound breakSound(Material m) {
        return switch (m) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING -> Sound.BLOCK_GRASS_BREAK;
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG -> Sound.BLOCK_WOOD_BREAK;
            case SAND, CLAY -> Sound.BLOCK_SAND_BREAK;
            default -> Sound.BLOCK_STONE_BREAK;
        };
    }

    private static void effect(Block at, Particle particle, @Nullable BlockData data, Sound sound) {
        Location centre = centre(at, 0.5);
        if (data != null) {
            at.getWorld().spawnParticle(particle, centre, 14, 0.25, 0.25, 0.25, 0.05, data);
        } else {
            at.getWorld().spawnParticle(particle, centre, 6, 0.3, 0.3, 0.3, 0.0);
        }
        at.getWorld().playSound(centre, sound, 0.35f, 1.0f);
    }

    private static @Nullable Block pick(Area area, java.util.function.Predicate<Block> test, RandomGenerator random) {
        List<Block> ok = new ArrayList<>();
        for (Block b : blocks(area)) {
            if (test.test(b)) {
                ok.add(b);
            }
        }
        return ok.isEmpty() ? null : ok.get(random.nextInt(ok.size()));
    }

    // ---------------------------------------------------------------- mobs

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable Entity spawnMob(Block spot, Type type, String minionId) {
        Class<? extends Entity> cls = type.mob().getEntityClass();
        if (cls == null) {
            return null;
        }
        Location at = centre(spot, 0);
        Consumer setup = e -> prepare((Entity) e, minionId);
        Entity mob = spot.getWorld().spawn(at, (Class) cls, setup);
        return mob.isValid() ? mob : null;
    }

    /** Freezes a spawned mob into a harmless prop. */
    private static void prepare(Entity e, String minionId) {
        e.getPersistentDataContainer().set(MOB_TAG, PersistentDataType.STRING, minionId);
        e.setPersistent(false);
        e.setInvulnerable(true);
        e.setSilent(true);
        if (e instanceof LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(true);
        }
        if (e instanceof Mob mob) {
            mob.setAware(false);
        }
        if (e instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
            zombie.setBaby(false);
        }
        if (e instanceof Skeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
        if (e instanceof Slime slime) {
            slime.setSize(1);
        }
    }
}
