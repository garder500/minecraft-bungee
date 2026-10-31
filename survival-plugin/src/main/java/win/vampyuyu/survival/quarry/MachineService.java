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
                if (type == null || world.getBlockAt(p.x(), p.y(), p.z()).getType() != type.block) {
                    BlockModels.hide(world.getBlockAt(p.x(), p.y(), p.z()));
                    store.remove(worldName, p); // the block is gone (broken by something that is not a player)
                    continue;
                }
                Block block = world.getBlockAt(p.x(), p.y(), p.z());
                if (!BlockModels.has(block)) {
                    unmodelled.put(block, entry.getValue());
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
                BlockModels.show(block, quarry.core().items().create(data.kind().itemId), data.yaw)));
        }
        if (++ticks % SAVE_EVERY == 0) {
            store.flush();
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
        return true;
    }
}
