package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;

/**
 * Experience, collections and fortune from breaking blocks (mining, foraging, farming).
 *
 * <p>Anti-exploit: blocks that a player placed, a piston moved or a falling block landed as are remembered in
 * {@link PlacedBlocks} and give nothing when broken; crops that need to grow give nothing before they are mature;
 * breaking the same spot again and again (cobblestone generators) is subject to {@link AntiFarm}.
 */
final class BlockSources implements Listener {
    private static final int COLUMN_LIMIT = 32;
    private static final NamespacedKey FALLING_PLACED = new NamespacedKey("vampyuyu", "skills_falling_placed");

    /** What the break handler decided, read back by the drop handler of the same break. */
    private record PosKey(UUID world, long block) {}

    /** The decision for one break, plus what the block originally dropped (read at LOWEST, before other features). */
    private static final class Credit {
        final boolean ok;
        Map<Material, Integer> snapshot;

        Credit(boolean ok) {
            this.ok = ok;
        }
    }

    private final Core core;
    private final SkillsService service;
    private final PlacedBlocks placed;
    private volatile AntiFarm antiFarm;
    private final Map<PosKey, Credit> pending = new HashMap<>();
    private boolean clearScheduled;

    BlockSources(Core core, SkillsService service, PlacedBlocks placed) {
        this.core = core;
        this.service = service;
        this.placed = placed;
        this.antiFarm = new AntiFarm(service.config().blockAntiFarm, System::currentTimeMillis);
    }

    void reload() {
        this.antiFarm = new AntiFarm(service.config().blockAntiFarm, System::currentTimeMillis);
    }

    void forget(UUID player) {
        antiFarm.forget(player);
    }

    private boolean allowed(World world) {
        return world != null && service.config().worldAllowed(world.getName());
    }

    private static boolean earns(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    // ---------------------------------------------------------------- placed-block tracking

    private void mark(Block block) {
        placed.mark(block.getWorld().getUID(), block.getWorld().getMinHeight(), block.getX(), block.getY(), block.getZ());
    }

    private boolean unmark(Block block) {
        return placed.unmark(block.getWorld().getUID(), block.getWorld().getMinHeight(), block.getX(), block.getY(),
            block.getZ());
    }

    private boolean isMarked(Block block) {
        return placed.isPlaced(block.getWorld().getUID(), block.getWorld().getMinHeight(), block.getX(), block.getY(),
            block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!allowed(block.getWorld())) {
            return;
        }
        if (service.config().trackedPlaced.contains(block.getType())) {
            mark(block);
        } else {
            unmark(block);
        }
        placed.flush();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        move(event.getBlock(), event.getBlocks(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        move(event.getBlock(), event.getBlocks(), true);
    }

    /** Marks follow the blocks a piston pushes (along its facing) or pulls (against it). */
    private void move(Block piston, List<Block> blocks, boolean retract) {
        if (blocks.isEmpty() || !allowed(piston.getWorld()) || !(piston.getBlockData() instanceof Directional dir)) {
            return;
        }
        BlockFace face = retract ? dir.getFacing().getOppositeFace() : dir.getFacing();
        List<Block> marked = new ArrayList<>();
        for (Block block : blocks) {
            if (unmark(block)) {
                marked.add(block);
            }
        }
        for (Block block : marked) {
            mark(block.getRelative(face));
        }
        placed.flush();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (allowed(event.getLocation().getWorld())) {
            event.blockList().forEach(this::unmark);
            placed.flush();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        if (allowed(event.getBlock().getWorld())) {
            event.blockList().forEach(this::unmark);
            placed.flush();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        unmark(event.getBlock());
        placed.flush();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling) || !allowed(event.getBlock().getWorld())) {
            return;
        }
        if (event.getTo().isAir()) {
            // The block turns into a falling entity: carry its mark on the entity.
            if (unmark(event.getBlock())) {
                falling.getPersistentDataContainer().set(FALLING_PLACED, PersistentDataType.BYTE, (byte) 1);
            }
        } else if (falling.getPersistentDataContainer().has(FALLING_PLACED, PersistentDataType.BYTE)) {
            mark(event.getBlock());
        }
        placed.flush();
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // The event's own chunk goes to the backend: looking it up by coordinates is not reliable while it unloads.
        placed.unload(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ(), event.getChunk());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        placed.dropWorld(event.getWorld().getUID());
    }

    // ---------------------------------------------------------------- breaking

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        try {
            handleBreak(event);
        } finally {
            placed.flush();
        }
    }

    private void handleBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!allowed(block.getWorld())) {
            return;
        }
        boolean wasPlaced = unmark(block);
        SkillsConfig cfg = service.config();
        Material type = block.getType();
        boolean earns = player != null && earns(player);
        if (cfg.columnPlants.contains(type)) {
            breakColumn(player, earns, block, type, cfg);
        }
        if (!earns) {
            return;
        }
        boolean ok = !wasPlaced;
        if (ok && cfg.matureOnly.contains(type) && block.getBlockData() instanceof Ageable crop
                && crop.getAge() < crop.getMaximumAge()) {
            ok = false;
        }
        pending.put(new PosKey(block.getWorld().getUID(), block.getBlockKey()), new Credit(ok));
        if (!clearScheduled) {
            clearScheduled = true;
            core.scheduler().nextTick(() -> {
                pending.clear();
                clearScheduled = false;
            });
        }
        if (!ok) {
            return;
        }
        grantBlock(player, block, type, cfg);
    }

    private void grantBlock(Player player, Block block, Material type, SkillsConfig cfg) {
        Double xp = cfg.blockXp.get(type);
        Skill skill = cfg.blockSkill.get(type);
        if (xp == null || skill == null || xp <= 0) {
            return;
        }
        double multiplier = antiFarm.next(player.getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(),
            block.getZ());
        service.grant(player, skill, xp * multiplier, skill.key() + ":" + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Sugar cane, cactus and bamboo: the blocks above the broken one fall by physics without a break event of their
     * own, so they are paid here. A block the player placed pays nothing (its mark is consumed); grown ones do.
     */
    private void breakColumn(Player player, boolean earns, Block base, Material type, SkillsConfig cfg) {
        for (int i = 1; i <= COLUMN_LIMIT; i++) {
            Block above = base.getRelative(0, i, 0);
            if (above.getType() != type) {
                break;
            }
            boolean wasPlaced = unmark(above);
            if (!earns || wasPlaced) {
                continue;
            }
            grantBlock(player, above, type, cfg);
            service.addCollection(player, type, 1);
        }
    }

    /** First look at the drops, before other features edit or consume them (telekinesis, smelting touch...). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDropsFirst(BlockDropItemEvent event) {
        BlockState state = event.getBlockState();
        Credit credit = pending.get(new PosKey(state.getWorld().getUID(), state.getBlock().getBlockKey()));
        if (credit == null || !credit.ok) {
            return;
        }
        credit.snapshot = totals(event.getItems());
    }

    private Map<Material, Integer> totals(List<Item> items) {
        Map<Material, Integer> out = new EnumMap<>(Material.class);
        for (Item drop : items) {
            ItemStack stack = drop.getItemStack();
            if (stack.getType().isAir() || core.items().isCustom(stack)) {
                continue;
            }
            out.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        BlockState state = event.getBlockState();
        Credit credit = pending.remove(new PosKey(state.getWorld().getUID(), state.getBlock().getBlockKey()));
        if (credit == null || !credit.ok || player == null) {
            return;
        }
        SkillsConfig cfg = service.config();
        Map<Material, Integer> finalTotals = totals(event.getItems());
        // What the block dropped to begin with counts even if another feature took the items (telekinesis); extras
        // another feature added in place count too. Items that are not in the first snapshot (a smelted result) do not.
        Map<Material, Long> counts = new EnumMap<>(Material.class);
        if (credit.snapshot == null) {
            finalTotals.forEach((m, n) -> counts.put(m, (long) n));
        } else {
            credit.snapshot.forEach((m, n) -> counts.put(m, (long) Math.max(n, finalTotals.getOrDefault(m, 0))));
        }
        List<ItemStack> extras = new ArrayList<>();
        double fortune = fortuneOf(player, cfg, cfg.blockSkill.get(state.getType()));
        if (fortune > 0) {
            for (Item drop : event.getItems()) {
                ItemStack stack = drop.getItemStack();
                if (stack.getType().isAir() || core.items().isCustom(stack)) {
                    continue;
                }
                int extra = fortuneExtra(stack.getAmount(), fortune);
                if (extra > 0) {
                    counts.merge(stack.getType(), (long) extra, Long::sum);
                    ItemStack bonus = stack.clone();
                    bonus.setAmount(Math.min(extra, bonus.getMaxStackSize()));
                    extras.add(bonus);
                }
            }
        }
        Location where = state.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack bonus : extras) {
            state.getWorld().dropItemNaturally(where, bonus);
        }
        Map<SkillsConfig.CollectionDef, Long> counted = new HashMap<>();
        counts.forEach((material, amount) -> {
            SkillsConfig.CollectionDef def = cfg.collectionByMaterial.get(material);
            if (def != null) {
                counted.merge(def, amount, Long::sum);
            }
        });
        counted.forEach((def, amount) -> service.addCollection(player, def, amount));
    }

    /** Fortune of the player for this block's skill, 0 when disabled, unknown or the tool has Silk Touch. */
    private double fortuneOf(Player player, SkillsConfig cfg, Skill skill) {
        if (!cfg.fortuneEnabled || skill == null) {
            return 0;
        }
        Stat stat = fortuneStat(skill);
        if (stat == null || player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            return 0;
        }
        double fortune = core.service(StatsApi.class).map(api -> api.stat(player, stat)).orElse(0.0);
        return Math.min(fortune, (cfg.fortuneMaxMultiplier - 1) * 100);
    }

    static Stat fortuneStat(Skill skill) {
        return switch (skill) {
            case MINING -> Stat.MINING_FORTUNE;
            case FARMING -> Stat.FARMING_FORTUNE;
            case FORAGING -> Stat.FORAGING_FORTUNE;
            default -> null;
        };
    }

    /** Extra items for {@code amount} dropped items at {@code fortune} (100 = one extra per item on average). */
    static int fortuneExtra(int amount, double fortune) {
        return fortuneExtra(amount, fortune, ThreadLocalRandom.current().nextDouble());
    }

    static int fortuneExtra(int amount, double fortune, double roll) {
        if (fortune <= 0 || amount <= 0) {
            return 0;
        }
        double expected = amount * fortune / 100.0;
        int whole = (int) Math.floor(expected);
        return Math.min(whole + (roll < expected - whole ? 1 : 0), 64);
    }

    /** Test hook: number of unread break decisions. */
    int pendingCount() {
        return pending.size();
    }

    Set<Material> tracked() {
        return new HashSet<>(service.config().trackedPlaced);
    }
}
