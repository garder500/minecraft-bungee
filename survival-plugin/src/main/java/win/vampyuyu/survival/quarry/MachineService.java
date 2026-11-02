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

/** Runs the machines once per second: networks, fuel, batteries, and the miners breaking generator blocks. */
final class MachineService implements win.vampyuyu.survival.api.MinerBoostApi {
    private static final BlockFace[] SIDES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST};
    private static final int SAVE_EVERY = 30;

    private final QuarryService quarry;
    private final MachineStore store;
    private final BlockMarks cobble = new BlockMarks("cobble");
    private final Random random = new Random();
    private int ticks;
    /** The crack each working Miner currently shows on its target (visible break, GDD 7.2). Main thread only. */
    private final Map<Pos, Crack> cracks = new java.util.HashMap<>();

    /** What a Miner is breaking: the block, the progress at the last tick, how fast it grows, and the stage shown. */
    private static final class Crack {
        final World world;
        final Block target;
        private long ticked = System.currentTimeMillis();
        double base;
        double rate;
        int stage;

        Crack(World world, Block target) {
            this.world = world;
            this.target = target;
        }

        void resetClock() {
            ticked = System.currentTimeMillis();
        }

        long tickedAt() {
            return ticked;
        }
    }

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

    /** Trade effects on a miner: the Artisan's lower Flux draw (its owner's) and a running Overclock. */
    private final EnergyNetwork.Modifiers mods = new EnergyNetwork.Modifiers() {
        @Override
        public double energy(MachineData miner) {
            UUID owner = ownerOf(miner);
            double breath = quarry.core().service(win.vampyuyu.survival.api.BreathApi.class)
                .map(win.vampyuyu.survival.api.BreathApi::machineFactor).orElse(1.0);
            return breath * (owner == null ? 1 : quarry.core().service(win.vampyuyu.survival.api.TradeApi.class)
                .map(t -> t.minerEnergyFactor(owner)).orElse(1.0));
        }

        @Override
        public double speed(MachineData miner) {
            return miner.overclockUntil > System.currentTimeMillis() ? Math.max(1, miner.overclockSpeed) : 1;
        }
    };

    private static UUID ownerOf(MachineData data) {
        try {
            return data == null || data.owner == null ? null : UUID.fromString(data.owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Artisan experience: a miner that ran counts for its owner (a no-op for a player without the trade). */
    private void creditArtisan(MachineData miner) {
        UUID owner = ownerOf(miner);
        if (owner != null) {
            quarry.core().service(win.vampyuyu.survival.api.TradeApi.class)
                .ifPresent(t -> t.addXp(owner, win.vampyuyu.survival.api.Trade.ARTISAN, 1));
        }
    }

    /**
     * The Artisan's Overclock: the miner of {@code player} nearest to {@code at} within {@code range} blocks runs
     * {@code speed} times faster for {@code millis}. False when none is in range.
     */
    @Override
    public boolean overclockNearest(UUID player, org.bukkit.Location at, double range, long millis, double speed) {
        if (at.getWorld() == null) {
            return false;
        }
        MachineData best = null;
        double bestDistance = range * range;
        for (Map.Entry<Pos, MachineData> entry : store.all(at.getWorld().getName()).entrySet()) {
            MachineData data = entry.getValue();
            MachineType type = data.kind();
            if (type == null || !type.miner() || !player.equals(ownerOf(data))) {
                continue;
            }
            Pos p = entry.getKey();
            double d = at.distanceSquared(new org.bukkit.Location(at.getWorld(), p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
            if (d <= bestDistance) {
                bestDistance = d;
                best = data;
            }
        }
        if (best == null) {
            return false;
        }
        best.overclockUntil = System.currentTimeMillis() + millis;
        best.overclockSpeed = speed;
        store.markDirty();
        return true;
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
                } else if (type != null && type.legacy != type.block
                        && world.getBlockAt(p.x(), p.y(), p.z()).getType() == type.legacy) {
                    world.getBlockAt(p.x(), p.y(), p.z()).setType(type.block, false); // a vanilla block of an older version: now an invisible hitbox under the model
                }
                if (type == null || world.getBlockAt(p.x(), p.y(), p.z()).getType() != type.block) {
                    BlockModels.hide(world.getBlockAt(p.x(), p.y(), p.z()));
                    store.remove(worldName, p); // the block is gone (broken by something that is not a player)
                    continue;
                }
                Block block = world.getBlockAt(p.x(), p.y(), p.z());
                boolean shapeChanged = false;
                if (type == MachineType.CABLE) {
                    entry.getValue().yaw = CABLE_YAW; // cables are drawn aligned with the world, whatever way they were placed
                    // "built" holds the drawn shape, plus "p" while the cable carries Flux (lit copper)
                    String old = entry.getValue().built == null ? "" : entry.getValue().built;
                    String key = cableMask(block) + (old.endsWith(POWERED) ? POWERED : "");
                    shapeChanged = !key.equals(old);
                    entry.getValue().built = key;
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
            EnergyNetwork.Sky sky = new EnergyNetwork.Sky() {
                @Override
                public boolean stormy(Pos p) {
                    return world.isThundering() && at(world, p).getLightFromSky() >= 15;
                }

                @Override
                public long second() {
                    return world.getFullTime() / 20;
                }
            };
            for (List<Pos> group : EnergyNetwork.components(live.keySet())) {
                if (touchesUnloaded(group, all, live)) {
                    continue; // a part of this network is in an unloaded chunk: do not run half of it
                }
                Map<Pos, MachineData> network = new LinkedHashMap<>();
                group.forEach(p -> network.put(p, live.get(p)));
                boolean powered = EnergyNetwork.hasFlux(network, p -> sunny(world, p), sky);
                for (Map.Entry<Pos, MachineData> cable : network.entrySet()) {
                    MachineData data = cable.getValue();
                    if (data.kind() != MachineType.CABLE) {
                        continue;
                    }
                    String shape = data.built == null ? "" : data.built.replace(POWERED, "");
                    String key = shape + (powered ? POWERED : "");
                    if (!key.equals(data.built)) { // the cable lights up or goes dark
                        data.built = key;
                        Pos at = cable.getKey();
                        unmodelled.put(world.getBlockAt(at.x(), at.y(), at.z()), data);
                    }
                }
                Map<Pos, Double> before = new LinkedHashMap<>();
                network.forEach((pos, data) -> {
                    if (data.kind() != null && data.kind().miner()) {
                        before.put(pos, data.progress);
                    }
                });
                List<EnergyNetwork.Fire> fires = EnergyNetwork.tick(network, cfg, p -> ready(world, p), p -> sunny(world, p), mods, sky);
                Map<Pos, Integer> fired = new LinkedHashMap<>();
                fires.forEach(f -> fired.put(f.pos(), f.cycles()));
                for (Map.Entry<Pos, Double> b : before.entrySet()) {
                    MachineData data = network.get(b.getKey());
                    // cycles per second this tick (0 when the Miner did not work: no Flux, nowhere to put the ore)
                    double rate = data.progress - b.getValue() + fired.getOrDefault(b.getKey(), 0);
                    if (fired.containsKey(b.getKey())) {
                        endCrack(b.getKey()); // the block is broken just below; the next cycle starts a new crack
                        continue;
                    }
                    startCrack(world, b.getKey(), data, rate);
                }
                for (EnergyNetwork.Fire fire : fires) {
                    creditArtisan(network.get(fire.pos()));
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

    /**
     * Which sides of a cable the model joins: N=1 E=2 S=4 W=8. It follows the same rule as the energy network (a cable
     * or any machine next to it), never the redstone wire underneath, which only joins other wires.
     */
    int cableMask(Block cable) {
        int mask = 0;
        int bit = 1;
        for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block next = cable.getRelative(face);
            if (store.get(next.getWorld().getName(), MachineStore.pos(next)) != null) {
                mask |= bit;
            }
            bit <<= 1;
        }
        return mask;
    }

    /** Cables around a block that was placed or removed show their new shape at once, not at the next tick. */
    void refreshCablesAround(Block changed) {
        for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block next = changed.getRelative(face);
            MachineData data = store.get(next.getWorld().getName(), MachineStore.pos(next));
            if (data != null && data.kind() == MachineType.CABLE) {
                String mask = Integer.toString(cableMask(next));
                if (!mask.equals(data.built)) {
                    data.built = mask;
                    BlockModels.show(next, modelItem(next, data), CABLE_YAW);
                }
            }
        }
    }

    /** The model's own orientation puts its +z face towards the player at yaw 180: that is the one aligned with the world. */
    static final float CABLE_YAW = 180f;
    /** Suffix of a cable's drawn state while its network has Flux. */
    static final String POWERED = "p";

    /** The item a machine's display shows: a cable picks the model of its connection shape. */
    ItemStack modelItem(Block block, MachineData data) {
        ItemStack stack = quarry.core().items().create(data.kind().itemId);
        if (data.kind() == MachineType.CABLE) {
            int mask = cableMask(block);
            String lit = data.built != null && data.built.endsWith(POWERED) ? "_on" : "";
            stack.editMeta(meta -> meta.setItemModel(new org.bukkit.NamespacedKey("vampyuyu", "machine/cable_" + mask + lit)));
        }
        return stack;
    }

    /** An old cable (a lightning rod) becomes a cable block when the block under it can carry it, else the item comes back. */
    private void migrateCable(Block rod) {
        MachineData data = store.get(rod.getWorld().getName(), MachineStore.pos(rod));
        if (rod.getRelative(BlockFace.DOWN).getType().isSolid()) {
            rod.setType(MachineType.CABLE.block, false);
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
        if (b.getType() == Material.BARRIER || b.getType() == GeneratorStructure.SPRING) {
            MachineData d = store.get(b.getWorld().getName(), MachineStore.pos(b));
            return d != null && d.kind() == MachineType.SPRING_STONE ? GeneratorStructure.SPRING : b.getType();
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
            // the ring's own quest is said again every 10 s, so a ring finished before its quest opened still counts;
            // the "any ring" count is only told when a ring is completed, or each ring would count again and again
            announce(data, "generator_built:" + result.kind().name().toLowerCase(java.util.Locale.ROOT));
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
        fanOut(data.owner, "EVENT", key, 1);
    }

    /** Tells every online member of the owner's island about one thing a machine did (the quests count it once). */
    private void fanOut(String ownerId, String kind, String subject, int amount) {
        for (QuarryProgressEvent event : QuarryProgressEvent.fanOut(islandMembers(ownerId), kind, subject, amount)) {
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    /** The online players of the machine owner's island (the owner alone when there is no island service). */
    private java.util.List<Player> islandMembers(String ownerId) {
        UUID owner;
        try {
            owner = UUID.fromString(ownerId);
        } catch (IllegalArgumentException e) {
            return java.util.List.of(); // an old record without an owner
        }
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
        ids.add(owner);
        quarry.core().service(win.vampyuyu.survival.api.IslandApi.class).flatMap(api -> api.islandOf(owner))
            .ifPresent(island -> {
                ids.add(island.owner());
                ids.addAll(island.members());
            });
        java.util.List<Player> online = new java.util.ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) online.add(p);
        }
        return online;
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

    /** The generator block a miner can break: Fountain or stone generator cobble, or what a ring generator made. */
    private Block cobbleNextTo(Block miner) {
        for (BlockFace face : SIDES) {
            Block side = miner.getRelative(face);
            if ((CobbleListener.generatorBlock(side.getType()) && cobble.has(side))
                    || (!side.getType().isAir() && outputs.has(side))) {
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
        if (outputs.has(target)) {
            mineOutput(world, miner, target, container, owner, data);
            return true;
        }
        cobble.remove(target);
        shatter(target);
        target.setType(Material.AIR); // the water and lava next to it form the next block
        MachineModule module = MachineModule.of(data.module);
        ItemStack[] last = new ItemStack[1];
        quarry.draw(owner, 0, random, drop -> {
            ItemStack stack = module == MachineModule.EMBER ? MachineModule.smelt(drop.stack()) : drop.stack();
            last[0] = stack;
            for (ItemStack left : container.getInventory().addItem(stack).values()) {
                world.dropItemNaturally(miner.getLocation().add(0.5, 1.2, 0.5), left);
            }
            fanOut(data.owner, "ORE_FOUND", drop.ore(), drop.stack().getAmount());
        }, (geode, pity, name) -> { });
        bloom(world, miner, container, module, data);
        memory(module, data);
        recycle(world, miner, container, module, last[0]);
        announce(data, "machine_mined");
        return true;
    }

    /** A Recycler module sometimes gives back a second copy of what the cycle mined. */
    private void recycle(World world, Block miner, Container container, MachineModule module, ItemStack mined) {
        if (module != MachineModule.RECYCLER || mined == null || !MachineModule.salvages(settings().recyclerSalvageChance, random)) {
            return;
        }
        for (ItemStack left : container.getInventory().addItem(mined.clone()).values()) {
            world.dropItemNaturally(miner.getLocation().add(0.5, 1.2, 0.5), left);
        }
    }

    /** A Memory module sometimes distils a Memory Shard for the Miner's owner (GDD 15.3). */
    private void memory(MachineModule module, MachineData data) {
        if (module != MachineModule.MEMORY || data.owner == null
                || MachineModule.memoryShards(settings().memoryShardChance, random) == 0) {
            return;
        }
        try {
            UUID owner = UUID.fromString(data.owner);
            quarry.core().service(win.vampyuyu.survival.api.ResonanceApi.class).ifPresent(r -> r.grantShards(owner, 1));
        } catch (IllegalArgumentException e) {
            // an owner id that is not a player: nobody to pay
        }
    }

    /** A Bloom module sometimes gathers a bone meal with the cycle, a Cryo module a packed ice, a Tide module a prismarine shard. */
    private void bloom(World world, Block miner, Container container, MachineModule module, MachineData data) {
        Material bonus;
        if (module == MachineModule.BLOOM && MachineModule.bloomBonus(settings().bloomBoneMealChance, random) > 0) {
            bonus = Material.BONE_MEAL;
        } else if (module == MachineModule.CRYO && MachineModule.cryoBonus(settings().cryoIceChance, random) > 0) {
            bonus = Material.PACKED_ICE;
        } else if (module == MachineModule.TIDE && MachineModule.tideBonus(settings().tideShardChance, random) > 0) {
            bonus = Material.PRISMARINE_SHARD;
        } else {
            return;
        }
        for (ItemStack left : container.getInventory().addItem(new ItemStack(bonus)).values()) {
            world.dropItemNaturally(miner.getLocation().add(0.5, 1.2, 0.5), left);
        }
    }

    /**
     * A block of a dune, frost or ember generator: the block itself goes to the chest (whole, as with Silk Touch, so
     * packed ice is not lost) and the ring's ores are rolled with the owner's pity counters, as for a player.
     */
    private void mineOutput(World world, Block miner, Block target, Container container, UUID owner, MachineData data) {
        Material type = target.getType();
        outputs.remove(target);
        shatter(target);
        target.setType(Material.AIR); // the generator makes the next one above its Fountain
        java.util.List<ItemStack> got = new java.util.ArrayList<>();
        MachineModule module = MachineModule.of(data.module);
        got.add(module == MachineModule.EMBER ? MachineModule.smelt(new ItemStack(type)) : new ItemStack(type));
        String source = OutputListener.source(type);
        java.util.Optional<QuarryService.Drop> ore = source == null ? java.util.Optional.empty()
            : quarry.drawOutput(owner, source, type, random);
        ore.ifPresent(drop -> got.add(drop.stack()));
        for (ItemStack stack : got) {
            for (ItemStack left : container.getInventory().addItem(stack).values()) {
                world.dropItemNaturally(miner.getLocation().add(0.5, 1.2, 0.5), left);
            }
        }
        if (ore.isPresent() && quarry.core().items().idOf(ore.get().stack()).isPresent()) {
            fanOut(data.owner, "ORE_FOUND", ore.get().ore(), 1);
        }
        bloom(world, miner, container, module, data);
        memory(module, data);
        recycle(world, miner, container, module, got.get(0));
        announce(data, "machine_mined");
    }

    private static final double CRACK_RANGE = 40;

    /** The block breaks for real: the vanilla break sound and particles (setType alone is silent). */
    private void shatter(Block target) {
        target.getWorld().playEffect(target.getLocation().add(0.5, 0.5, 0.5), org.bukkit.Effect.STEP_SOUND, target.getBlockData());
    }

    private static int crackId(Block target) {
        return 0x40000000 | (target.getX() * 31 + target.getY() * 17 + target.getZ()) & 0x3fffffff;
    }

    private void sendCrack(World world, Block target, int stage) {
        float damage = MiningCrack.damage(stage);
        for (Player player : world.getNearbyPlayers(target.getLocation().add(0.5, 0.5, 0.5), CRACK_RANGE)) {
            player.sendBlockDamage(target.getLocation(), damage, crackId(target));
        }
    }

    /** Clears the crack of a Miner (the break effect itself plays in {@link #shatter}). */
    private void endCrack(Pos miner) {
        Crack crack = cracks.remove(miner);
        if (crack != null && crack.stage > 0) {
            sendCrack(crack.world, crack.target, 0);
        }
    }

    /** Called once per tick for each Miner: remembers how fast its cycle grows so the crack can follow it. */
    private void startCrack(World world, Pos miner, MachineData data, double rate) {
        Block target = ready(world, miner) ? cobbleNextTo(at(world, miner)) : null;
        Crack crack = cracks.get(miner);
        if (crack != null && (target == null || !crack.target.equals(target))) {
            endCrack(miner);
            crack = null;
        }
        if (target == null) {
            return;
        }
        if (crack == null) {
            crack = new Crack(world, target);
            cracks.put(miner, crack);
        }
        crack.base = data.progress;
        crack.rate = Math.max(0, rate);
        crack.resetClock();
    }

    /** Every 4 ticks: moves each working Miner's crack to the stage its progress has reached, sending only changes. */
    void animateCracks() {
        long now = System.currentTimeMillis();
        cracks.values().removeIf(c -> c.target.getType().isAir()); // broken or gone by other means
        for (Crack crack : cracks.values()) {
            if (crack.rate <= 0 && crack.stage > 0) {
                continue; // stalled: the crack stays where it is
            }
            int stage = MiningCrack.stage(MiningCrack.extrapolate(crack.base, crack.rate, now - crack.tickedAt()));
            if (stage != crack.stage) {
                crack.stage = stage;
                sendCrack(crack.world, crack.target, stage);
                if (stage > 0) {
                    org.bukkit.SoundGroup sound = crack.target.getBlockData().getSoundGroup();
                    crack.world.playSound(crack.target.getLocation().add(0.5, 0.5, 0.5), sound.getHitSound(),
                        org.bukkit.SoundCategory.BLOCKS, 0.6f, sound.getPitch() * 0.5f);
                }
            }
        }
    }
}
