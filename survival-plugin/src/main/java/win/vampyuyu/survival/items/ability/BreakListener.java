package win.vampyuyu.survival.items.ability;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.stats.PlayerState;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Block breaking in Skyblock worlds: break abilities (tree capitator), drill fuel, and the drop pipeline
 * (fortune, smelting touch, telekinesis, replanting) applied to {@code BlockDropItemEvent}.
 */
public final class BreakListener implements Listener {
    private static final Map<Material, Material> SMELT = new EnumMap<>(Material.class);
    private static final Map<Material, Material> SEEDS = new EnumMap<>(Material.class);

    static {
        SMELT.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        SMELT.put(Material.COBBLESTONE, Material.STONE);
        SMELT.put(Material.SAND, Material.GLASS);
        SMELT.put(Material.RED_SAND, Material.GLASS);
        SMELT.put(Material.CLAY_BALL, Material.BRICK);
        SMELT.put(Material.NETHERRACK, Material.NETHER_BRICK);
        SMELT.put(Material.CACTUS, Material.GREEN_DYE);
        SEEDS.put(Material.WHEAT, Material.WHEAT_SEEDS);
        SEEDS.put(Material.CARROTS, Material.CARROT);
        SEEDS.put(Material.POTATOES, Material.POTATO);
        SEEDS.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        SEEDS.put(Material.NETHER_WART, Material.NETHER_WART);
    }

    private final Core core;
    private final StatsEngine engine;
    private final Gear gear;
    private final AbilityRegistry abilities;
    private volatile PlacedBlocks placed;

    public BreakListener(Core core, StatsEngine engine, Gear gear, AbilityRegistry abilities) {
        this.core = core;
        this.engine = engine;
        this.gear = gear;
        this.abilities = abilities;
        this.placed = new PlacedBlocks(engine.settings().placedBlockMemory);
    }

    public PlacedBlocks placed() { return placed; }

    private ItemsSettings settings() { return engine.settings(); }

    /** Pure: number of copies a drop is multiplied by for a fortune value (100 = one guaranteed extra copy). */
    public static int fortuneCopies(double fortune, double roll) {
        if (fortune <= 0) {
            return 0;
        }
        int whole = (int) Math.floor(fortune / 100.0);
        double rest = fortune - whole * 100.0;
        return whole + (roll * 100.0 < rest ? 1 : 0);
    }

    /** Pure: the smelted form of a drop, or the drop itself. */
    public static Material smelted(Material drop) {
        return SMELT.getOrDefault(drop, drop);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (engine.active(event.getPlayer()) && tracksPlacement(settings(), event.getBlockPlaced().getBlockData())) {
            placed.add(event.getBlockPlaced());
        }
    }

    /**
     * Pure: true when a block placed by a player must not give fortune drops when it is broken again. Ores and logs
     * always; crops only when they are not {@link Ageable} (pumpkin, melon: they have no growth stage, so a placed one
     * would look "mature"). Planted seeds (wheat, carrots...) are not tracked: they only give fortune once grown.
     */
    public static boolean tracksPlacement(ItemsSettings s, BlockData data) {
        Material type = data.getMaterial();
        if (s.fortuneOres.contains(type.name()) || s.fortuneLogs.contains(type.name()) || Tag.LOGS.isTagged(type)) {
            return true;
        }
        return s.fortuneCrops.contains(type.name()) && (!(data instanceof Ageable) || stacking(type));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!engine.active(player) || Abilities.chaining()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<GearInfo> read = gear.read(hand);
        if (read.isEmpty()) {
            return;
        }
        GearInfo info = read.get();
        if (info.def().pdcLongs().containsKey(Keys.FUEL_MAX)) {
            long fuel = gear.items().getLong(hand, Keys.FUEL).orElse(0L);
            if (fuel <= 0) {
                engine.state(player).notice = "§cYour drill is out of fuel";
                engine.state(player).noticeUntil = System.currentTimeMillis() + 1500;
            } else {
                gear.items().set(hand, Keys.FUEL, fuel - 1);
                if (fuel - 1 == 0) {
                    engine.markDirty(player.getUniqueId(), true);
                }
                if ((fuel - 1) % 25 == 0 || fuel - 1 == 0) {
                    gear.rebuild(hand);
                }
            }
        }
        for (Ability ability : abilities.forItem(info)) {
            if (ability.trigger() == Ability.Trigger.BREAK_BLOCK) {
                abilities.use(player, hand, info, ability, event);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!engine.active(player) || event.getItems().isEmpty()) {
            return;
        }
        ItemsSettings s = settings();
        PlayerState st = engine.state(player);
        BlockState state = event.getBlockState();
        Material type = state.getType();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<GearInfo> read = gear.read(hand);
        Map<String, Integer> tool = read.map(GearInfo::enchants).orElse(Map.of());
        boolean oneForAll = tool.containsKey("one_for_all");
        boolean wasPlaced = placed.consume(event.getBlock());

        // Fortune
        Stat fortuneStat = null;
        if (s.fortuneOres.contains(type.name())) {
            fortuneStat = Stat.MINING_FORTUNE;
        } else if (s.fortuneCrops.contains(type.name()) && mature(state.getBlockData())) {
            fortuneStat = Stat.FARMING_FORTUNE;
        } else if (Tag.LOGS.isTagged(type) || s.fortuneLogs.contains(type.name())) {
            fortuneStat = Stat.FORAGING_FORTUNE;
        }
        if (fortuneStat != null && !wasPlaced) {
            int copies = fortuneCopies(st.stat(fortuneStat), ThreadLocalRandom.current().nextDouble());
            if (copies > 0) {
                for (Item item : event.getItems()) {
                    ItemStack stack = item.getItemStack();
                    if (!gear.items().isCustom(stack)) {
                        stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * (1 + copies)));
                        item.setItemStack(stack);
                    }
                }
            }
        }
        // Replant
        boolean replant = read.map(i -> i.def().pdcStrings().getOrDefault(Keys.ABILITIES, "").contains("replant"))
            .orElse(false) || (!oneForAll && tool.containsKey("replenish"));
        if (replant && SEEDS.containsKey(type) && mature(state.getBlockData())
                && gear.items().isCustom(hand) && abilities.get("replant").isPresent()) {
            Material seed = SEEDS.get(type);
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                if (stack.getType() == seed && stack.getAmount() >= 1 && engine.active(player)) {
                    stack.setAmount(stack.getAmount() - 1);
                    item.setItemStack(stack);
                    replantLater(player, event.getBlock(), type);
                    break;
                }
            }
            event.getItems().removeIf(item -> item.getItemStack().getAmount() <= 0);
        }
        // Smelting Touch
        if (!oneForAll && tool.containsKey("smelting_touch")) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                Material to = smelted(stack.getType());
                if (to != stack.getType() && !gear.items().isCustom(stack)) {
                    item.setItemStack(new ItemStack(to, stack.getAmount()));
                }
            }
        }
        // Telekinesis
        if (!oneForAll && tool.containsKey("telekinesis")) {
            List<Item> taken = new ArrayList<>();
            for (Item item : event.getItems()) {
                Map<Integer, ItemStack> left = player.getInventory().addItem(item.getItemStack());
                if (left.isEmpty()) {
                    taken.add(item);
                } else {
                    item.setItemStack(left.values().iterator().next());
                }
            }
            event.getItems().removeAll(taken);
            taken.forEach(Item::remove);
        }
    }

    /** Crops that grow as a stack of blocks: their age is not a growth stage, every block counts as ripe. */
    private static boolean stacking(Material type) {
        return type == Material.SUGAR_CANE || type == Material.CACTUS;
    }

    /** Pure: true when a crop block can be harvested (fully grown, or a crop without growth stages). */
    public static boolean mature(BlockData data) {
        if (stacking(data.getMaterial())) {
            return true;
        }
        return !(data instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge();
    }

    private void replantLater(Player player, Block block, Material crop) {
        core.scheduler().nextTick(() -> {
            if (!player.isOnline() || !block.getType().isAir() || !crop.isBlock()) {
                return;
            }
            Block below = block.getRelative(0, -1, 0);
            Material soil = below.getType();
            boolean ok = crop == Material.NETHER_WART ? soil == Material.SOUL_SAND : soil == Material.FARMLAND;
            if (ok && abilities.get("replant").isPresent()) {
                block.setType(crop, false);
            }
        });
    }
}
