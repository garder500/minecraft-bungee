package win.vampyuyu.survival.menu;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.CoinsChangeEvent;
import win.vampyuyu.survival.api.event.IslandEnterEvent;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.api.event.SkillLevelUpEvent;
import win.vampyuyu.survival.core.Cooldowns;

/** Feeds Bukkit and Skyblock API events into the {@link QuestService}. */
final class QuestListener implements Listener {
    private final MenuRuntime rt;
    private final QuestService quests;
    private final PlacedBlocks placed;
    private final Cooldowns cooldowns = new Cooldowns();

    QuestListener(MenuRuntime rt, QuestService quests) {
        this.rt = rt;
        this.quests = quests;
        this.placed = new PlacedBlocks(rt.config().placedMemory);
    }

    private boolean counts(Player player) {
        return rt.config().questsEnabled && player.getGameMode() != GameMode.CREATIVE
            && player.getGameMode() != GameMode.SPECTATOR && MenuRuntime.inSkyblock(player.getWorld());
    }

    // ---------------------------------------------------------------- the Quarry

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuarry(QuarryProgressEvent event) {
        QuestType type;
        try {
            type = QuestType.valueOf(event.getKind());
        } catch (IllegalArgumentException e) {
            return;
        }
        quests.record(event.getPlayer(), type, event.getSubject(), event.getAmount());
    }

    // ---------------------------------------------------------------- blocks

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!counts(player)) {
            return;
        }
        boolean wasPlaced = placed.consumePlaced(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (wasPlaced || !matureOrPlain(block)) {
            return;
        }
        quests.record(player, QuestType.BREAK_BLOCK, block.getType().name(), 1);
    }

    /** Growing crops count only when fully grown; sugar cane and cactus only when grown above their base. */
    private static boolean matureOrPlain(Block block) {
        Material type = block.getType();
        if (type == Material.SUGAR_CANE || type == Material.CACTUS) {
            return block.getRelative(0, -1, 0).getType() == type;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!counts(player)) {
            return;
        }
        Material type = block.getType();
        boolean grows = Tag.SAPLINGS.isTagged(type)
            || (block.getBlockData() instanceof Ageable && type != Material.SUGAR_CANE && type != Material.CACTUS);
        if (!grows) { // planting something that must grow is real work and its harvest still counts
            placed.markPlaced(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
        quests.record(player, QuestType.PLACE_BLOCK, type.name(), 1);
    }

    // ---------------------------------------------------------------- crafting, enchanting, fishing, mobs

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !counts(player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }
        int crafts = 1;
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            int min = Integer.MAX_VALUE;
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient != null && !ingredient.getType().isAir()) {
                    min = Math.min(min, ingredient.getAmount());
                }
            }
            crafts = min == Integer.MAX_VALUE ? 1 : Math.max(1, min);
        }
        int amount = crafts * result.getAmount();
        amount = Math.min(amount, roomFor(player.getInventory(), result));
        if (amount <= 0) {
            return;
        }
        String subject = rt.core.items().idOf(result).map(QuestMatcher::customSubject)
            .orElse(result.getType().name());
        quests.record(player, QuestType.CRAFT_ITEM, subject, amount);
    }

    /** How many items of this kind still fit (so a shift-click on a full inventory does not over-count). */
    private static int roomFor(Inventory inventory, ItemStack result) {
        int room = 0;
        int max = result.getMaxStackSize();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                room += max;
            } else if (slot.isSimilar(result)) {
                room += Math.max(0, max - slot.getAmount());
            }
        }
        return room;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (counts(event.getEnchanter())) {
            quests.record(event.getEnchanter(), QuestType.ENCHANT_ITEM, "ANY", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && counts(event.getPlayer())) {
            quests.record(event.getPlayer(), QuestType.FISH, "ANY", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player || dead instanceof org.bukkit.entity.ArmorStand) {
            return;
        }
        Player killer = dead.getKiller();
        if (killer != null && counts(killer)) {
            quests.record(killer, QuestType.KILL_MOB, dead.getType().name(), 1);
        }
    }

    // ---------------------------------------------------------------- API events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(SkillLevelUpEvent event) {
        quests.record(event.getPlayer(), QuestType.SKILL_LEVEL, event.getSkill().name(), event.getNewLevel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        java.util.Set<UUID> credited = new java.util.HashSet<>();
        Player killer = event.getKiller();
        if (killer != null && credited.add(killer.getUniqueId())) {
            quests.record(killer, QuestType.BOSS_KILL, event.getBossId(), 1);
        }
        for (BossKillEvent.Damager damager : event.getTopDamagers()) {
            Player player = Bukkit.getPlayer(damager.player());
            if (player != null && credited.add(player.getUniqueId())) {
                quests.record(player, QuestType.BOSS_KILL, event.getBossId(), 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandEnter(IslandEnterEvent event) {
        Player player = event.getPlayer();
        if (cooldowns.tryUse(player.getUniqueId(), "enter", 10_000)) {
            quests.record(player, QuestType.ISLAND_ENTER, "ANY", 1);
        }
        if (event.getIsland().members().contains(player.getUniqueId())) {
            quests.sync(player);
        }
    }

    /** CoinsChangeEvent can fire off the main thread: hop over before touching profiles. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCoins(CoinsChangeEvent event) {
        if (event.getDelta() <= 0 || ignored(event.getReason())) {
            return;
        }
        UUID id = event.getPlayer();
        int amount = (int) Math.min(Integer.MAX_VALUE, Math.floor(event.getDelta()));
        Runnable task = () -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null && counts(player)) {
                quests.record(player, QuestType.COINS_EARN, "ANY", amount);
            }
        };
        if (event.isAsynchronous()) {
            rt.core.scheduler().sync(task);
        } else {
            task.run();
        }
    }

    private boolean ignored(String reason) {
        String lower = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        for (String prefix : rt.config().ignoreCoinReasons) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        String label = message.substring(1).split(" ", 2)[0];
        if (!label.isEmpty()) {
            quests.command(event.getPlayer(), label);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        quests.record(player, QuestType.VISIT_WORLD, MenuConfig.worldToken(player.getWorld().getName()), 1);
    }
}
