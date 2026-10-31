package win.vampyuyu.survival.quarry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.quarry.EnergyNetwork.Pos;

/** Runs the machines once per second: networks, fuel, batteries, and the miners breaking generator cobble. */
final class MachineService {
    private static final BlockFace[] SIDES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST};
    private static final int SAVE_EVERY = 30;

    private final QuarryService quarry;
    private final MachineStore store;
    private final BlockMarks cobble = new BlockMarks("cobble");
    private final Random random = new Random();
    private int ticks;

    MachineService(QuarryService quarry, MachineStore store) {
        this.quarry = quarry;
        this.store = store;
    }

    MachineStore store() {
        return store;
    }

    MachineSettings settings() {
        return quarry.config().machines;
    }

    void tick() {
        MachineSettings cfg = settings();
        Map<Block, MachineData> unmodelled = new LinkedHashMap<>();
        for (String worldName : store.worlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            Map<Pos, MachineData> all = store.all(worldName);
            Map<Pos, MachineData> live = new LinkedHashMap<>();
            for (Map.Entry<Pos, MachineData> entry : all.entrySet()) {
                Pos p = entry.getKey();
                if (!world.isChunkLoaded(p.x() >> 4, p.z() >> 4)) {
                    continue;
                }
                MachineType type = entry.getValue().kind();
                if (type == MachineType.CABLE && world.getBlockAt(p.x(), p.y(), p.z()).getType() == Material.LIGHTNING_ROD) {
                    migrateCable(world.getBlockAt(p.x(), p.y(), p.z())); // cables used to be lightning rods
                }
                if (type == null || world.getBlockAt(p.x(), p.y(), p.z()).getType() != type.block) {
                    BlockModels.hide(world.getBlockAt(p.x(), p.y(), p.z()));
                    store.remove(worldName, p); // the block is gone (broken by something that is not a player)
                    continue;
                }
                Block block = world.getBlockAt(p.x(), p.y(), p.z());
                boolean shapeChanged = false;
                if (type == MachineType.CABLE) {
                    String mask = Integer.toString(cableMask(block));
                    shapeChanged = !mask.equals(entry.getValue().built);
                    entry.getValue().built = mask;
                }
                if (shapeChanged || !BlockModels.has(block)) {
                    unmodelled.put(block, entry.getValue());
                }
                if (type.passive()) {
                    if (type == MachineType.FOUNTAIN) {
                        runFountain(world, block, entry.getValue());
                    }
                    continue;
                }
                live.put(p, entry.getValue());
            }
            for (List<Pos> group : EnergyNetwork.components(live.keySet())) {
                if (touchesUnloaded(group, all, live)) {
                    continue; // a part of this network is in an unloaded chunk: do not run half of it
                }
                Map<Pos, MachineData> network = new LinkedHashMap<>();
                group.forEach(p -> network.put(p, live.get(p)));
                for (EnergyNetwork.Fire fire : EnergyNetwork.tick(network, cfg, p -> ready(world, p), p -> sunny(world, p))) {
                    for (int i = 0; i < fire.cycles(); i++) {
                        if (!mine(world, fire.pos(), network.get(fire.pos()))) {
                            break;
                        }
                    }
                }
                store.markDirty();
            }
        }
        if (!unmodelled.isEmpty()) { // not inside the tick: spawning an entity may run other tasks
            Bukkit.getScheduler().runTask(quarry.core().getPlugin(), () -> unmodelled.forEach((block, data) ->
                BlockModels.show(block, modelItem(block, data), data.yaw)));
        }
        if (++ticks % SAVE_EVERY == 0) {
            store.flush();
        }
    }

    private final BlockMarks outputs = new BlockMarks("genout");

    /** Which sides of a redstone wire connect: N=1 E=2 S=4 W=8. Zero when the server cannot say (tests). */
    static int cableMask(Block wire) {
        try {
            if (!(wire.getBlockData() instanceof org.bukkit.block.data.type.RedstoneWire data)) {
                return 0;
            }
            int mask = 0;
            if (data.getFace(BlockFace.NORTH) != org.bukkit.block.data.type.RedstoneWire.Connection.NONE) mask |= 1;
            if (data.getFace(BlockFace.EAST) != org.bukkit.block.data.type.RedstoneWire.Connection.NONE) mask |= 2;
            if (data.getFace(BlockFace.SOUTH) != org.bukkit.block.data.type.RedstoneWire.Connection.NONE) mask |= 4;
            if (data.getFace(BlockFace.WEST) != org.bukkit.block.data.type.RedstoneWire.Connection.NONE) mask |= 8;
            return mask;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The item a machine's display shows: a cable picks the model of its connection shape. */
    ItemStack modelItem(Block block, MachineData data) {
        ItemStack stack = quarry.core().items().create(data.kind().itemId);
        if (data.kind() == MachineType.CABLE) {
            int mask = cableMask(block);
            stack.editMeta(meta -> meta.setItemModel(new org.bukkit.NamespacedKey("vampyuyu", "machine/cable_" + mask)));
        }
        return stack;
    }

    /** An old cable (a lightning rod) becomes redstone wire when the block under it can carry it, else the item comes back. */
    private void migrateCable(Block rod) {
        MachineData data = store.get(rod.getWorld().getName(), MachineStore.pos(rod));
        if (rod.getRelative(BlockFace.DOWN).getType().isSolid()) {
            rod.setType(Material.REDSTONE_WIRE, true);
        } else {
            rod.setType(Material.AIR);
            rod.getWorld().dropItemNaturally(rod.getLocation().add(0.5, 0.5, 0.5), quarry.core().items().create("cable"));
            if (data != null) {
                store.remove(rod.getWorld().getName(), MachineStore.pos(rod));
            }
        }
    }

    /** What stands at (dx, dz) from the Fountain: a Spring Stone counts only when the machine store knows it. */
    private Material ringAt(Block fountain, int dx, int dz) {
        Block b = fountain.getRelative(dx, 0, dz);
        if (b.getType() == GeneratorStructure.SPRING) {
            MachineData d = store.get(b.getWorld().getName(), MachineStore.pos(b));
            return d != null && d.kind() == MachineType.SPRING_STONE ? GeneratorStructure.SPRING : Material.AIR;
        }
        return b.getType();
    }

    /** What a Fountain is right now: used by its status message and by the production below. */
    GeneratorStructure.Result structureOf(Block fountain) {
        return GeneratorStructure.detect((dx, dz) -> ringAt(fountain, dx, dz));
    }

    /** One second of a Fountain: a block above itself every 2 s alone, every second as a built generator. */
    private void runFountain(World world, Block fountain, MachineData data) {
        GeneratorStructure.Result result = structureOf(fountain);
        String now = result.complete() ? result.kind().name() : "";
        if (!now.equals(data.built)) {
            data.built = now;
            store.markDirty();
            if (result.complete()) {
                announce(data, "generator_built:" + result.kind().name().toLowerCase(java.util.Locale.ROOT));
                announce(data, "generator_built");
            }
        }
        if (result.complete() && ticks % 10 == 0) {
            // said again every 10 s: a ring finished before its quest opened still counts
            announce(data, "generator_built:" + result.kind().name().toLowerCase(java.util.Locale.ROOT));
            announce(data, "generator_built");
        }
        data.progress += 1;
        if (data.progress < (result.complete() ? 1 : 2)) {
            return;
        }
        data.progress = 0;
        Block above = fountain.getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.getType().isAir()) {
            return;
        }
        Material out = result.complete() ? pick(result.kind()) : Material.COBBLESTONE;
        above.setType(out, false);
        if (out == Material.COBBLESTONE) {
            cobble.add(above); // the ore table and the Miners treat it like generator cobble
        } else {
            outputs.add(above);
        }
    }

    private Material pick(GeneratorStructure.Kind kind) {
        int roll = random.nextInt(100);
        return switch (kind) {
            case STONE -> Material.COBBLESTONE;
            case DUNES -> roll < 60 ? Material.SAND : roll < 90 ? Material.GRAVEL : Material.RED_SAND;
            case FROST -> roll < 60 ? Material.SNOW_BLOCK : Material.PACKED_ICE;
            case EMBER -> roll < 85 ? Material.NETHERRACK : Material.SOUL_SAND;
        };
    }

    private void announce(MachineData data, String key) {
        try {
            Player owner = Bukkit.getPlayer(UUID.fromString(data.owner));
            if (owner != null) {
                Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(owner, "EVENT", key, 1));
            }
        } catch (IllegalArgumentException ignored) {
            // an old record without an owner
        }
    }

    private static boolean touchesUnloaded(List<Pos> group, Map<Pos, MachineData> all, Map<Pos, MachineData> live) {
        for (Pos p : group) {
            for (Pos n : p.neighbours()) {
                if (all.containsKey(n) && !live.containsKey(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Block at(World world, Pos p) {
        return world.getBlockAt(p.x(), p.y(), p.z());
    }

    private Block cobbleNextTo(Block miner) {
        for (BlockFace face : SIDES) {
            Block side = miner.getRelative(face);
            if (CobbleListener.generatorBlock(side.getType()) && cobble.has(side)) {
                return side;
            }
        }
        return null;
    }

    private Container containerNextTo(Block miner) {
        for (BlockFace face : SIDES) {
            if (miner.getRelative(face).getState() instanceof Container container) {
                return container;
            }
        }
        return null;
    }

    /** A solar panel makes Flux in daylight with the sky open above it. */
    private boolean sunny(World world, Pos p) {
        long time = world.getTime() % 24000;
        return time < 12300 && at(world, p).getLightFromSky() >= 15;
    }

    private boolean ready(World world, Pos p) {
        Block block = at(world, p);
        return cobbleNextTo(block) != null && containerNextTo(block) != null;
    }

    /** One mining cycle. False when there was nothing to break any more. */
    private boolean mine(World world, Pos p, MachineData data) {
        Block miner = at(world, p);
        Block target = cobbleNextTo(miner);
        Container container = containerNextTo(miner);
        if (target == null || container == null || data == null || data.owner == null) {
            return false;
        }
        UUID owner;
        try {
            owner = UUID.fromString(data.owner);
        } catch (IllegalArgumentException e) {
            return false;
        }
        cobble.remove(target);
        target.setType(Material.AIR); // the water and lava next to it form the next block
        quarry.draw(owner, 0, random, drop -> {
            for (ItemStack left : container.getInventory().addItem(drop.stack()).values()) {
                world.dropItemNaturally(miner.getLocation().add(0.5, 1.2, 0.5), left);
            }
            Player online = Bukkit.getPlayer(owner);
            if (online != null) {
                Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(online, "ORE_FOUND", drop.ore(), drop.stack().getAmount()));
            }
        }, (geode, pity, name) -> { });
        announce(data, "machine_mined");
        return true;
    }
}
