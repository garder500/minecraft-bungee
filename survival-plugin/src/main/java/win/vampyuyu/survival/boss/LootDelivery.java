package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.core.Core;

/**
 * Hands items to players without ever losing or duplicating them: straight into the inventory, what does not fit
 * queued in their profile (delivered at the next login or with {@code /boss claim}, never dropped into the void), and
 * for offline players the same queue.
 */
public final class LootDelivery {
    /** Maximum queued stacks per player (a safety net against a runaway queue; the overflow is dropped, reserved). */
    public static final int MAX_PENDING = 1000;

    private final Core core;

    public LootDelivery(Core core) {
        this.core = core;
    }

    /**
     * Gives items to an online player; what does not fit is queued for later. Returns how many stacks were queued
     * because the inventory was full.
     */
    public int give(Player player, Collection<ItemStack> items) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
            overflow.addAll(left.values());
        }
        if (overflow.isEmpty()) {
            return 0;
        }
        int stored = queue(player.getUniqueId(), overflow);
        if (stored < overflow.size()) {
            // The queue itself is full: keep the stack in the world, reserved and without a despawn timer.
            for (ItemStack rest : overflow.subList(stored, overflow.size())) {
                Item dropped = dropReserved(player.getLocation(), rest, player.getUniqueId());
                dropped.setUnlimitedLifetime(true);
            }
            core.getPlugin().getLogger().warning("Boss loot queue of " + player.getName() + " is full, "
                + (overflow.size() - stored) + " stack(s) dropped at their feet");
        }
        core.msg().warn(player, "Your inventory is full: " + stored + (stored == 1 ? " boss item is" : " boss items are")
            + " waiting. Make room and use /boss claim.");
        return overflow.size();
    }

    /** Drops an item that only {@code owner} can pick up (hoppers and mobs cannot, see BossListener). */
    public Item dropReserved(Location at, ItemStack item, UUID owner) {
        Item dropped = at.getWorld().dropItem(at, item);
        dropped.setOwner(owner);
        dropped.setCanMobPickup(false);
        dropped.getPersistentDataContainer().set(Tags.LOOT_OWNER, PersistentDataType.STRING, owner.toString());
        dropped.setUnlimitedLifetime(false);
        return dropped;
    }

    /** Gives items now when the player is online, otherwise queues them in their profile (async safe). */
    public void giveOrQueue(UUID id, Collection<ItemStack> items) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            give(online, items);
        } else {
            queue(id, items);
        }
    }

    /**
     * Whether a queue edit may be handed to an async worker: only from the main thread, for a profile that is not in
     * memory, and while the plugin is enabled. A disabled plugin cannot schedule anything (Bukkit throws), and
     * shutdown is exactly when queued refunds must not be lost, so the edit then runs inline.
     */
    static boolean useAsync(boolean primaryThread, boolean profileLoaded, boolean pluginEnabled) {
        return primaryThread && !profileLoaded && pluginEnabled;
    }

    /**
     * Appends items to the profile queue of a (possibly offline) player. Returns how many stacks were stored (fewer
     * than given only when the queue is full; with an async edit all are assumed stored).
     */
    public int queue(UUID id, Collection<ItemStack> items) {
        List<String> encoded = new ArrayList<>();
        for (ItemStack item : items) {
            String text = encode(item);
            if (text != null) {
                encoded.add(text);
            }
        }
        if (encoded.isEmpty()) {
            return 0;
        }
        int[] stored = {0};
        Runnable edit = () -> core.profiles().edit(id, "boss", BossData.class, BossData::new, data -> {
            for (String text : encoded) {
                if (data.pending.size() < MAX_PENDING) {
                    data.pending.add(text);
                    stored[0]++;
                } else {
                    core.getPlugin().getLogger().warning("Boss loot queue of " + id + " is full, an item was lost");
                }
            }
        });
        if (useAsync(Bukkit.isPrimaryThread(), core.profiles().isLoaded(id), core.getPlugin().isEnabled())) {
            core.scheduler().async(edit);
            return encoded.size();
        }
        edit.run();
        return stored[0];
    }

    /**
     * Delivers the queued items of a player. Returns how many stacks were delivered now (the ones that still do not
     * fit go back to the queue).
     */
    public int claimPending(Player player) {
        BossData data = core.profiles().section(player.getUniqueId(), "boss", BossData.class, BossData::new);
        if (data.pending.isEmpty()) {
            return 0;
        }
        List<ItemStack> items = new ArrayList<>();
        for (String text : data.pending) {
            ItemStack item = decode(text);
            if (item != null) {
                items.add(item);
            }
        }
        // Emptied BEFORE giving: the overflow of give() is queued again, nothing is ever in both places.
        data.pending.clear();
        core.profiles().markDirty(player.getUniqueId());
        return items.size() - giveQuiet(player, items);
    }

    /** Like {@link #give} but silent about a full inventory (the caller reports it). */
    private int giveQuiet(Player player, Collection<ItemStack> items) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : items) {
            overflow.addAll(player.getInventory().addItem(item.clone()).values());
        }
        if (!overflow.isEmpty()) {
            queue(player.getUniqueId(), overflow);
        }
        return overflow.size();
    }

    /** Number of queued stacks (online or loaded players). */
    public int pendingCount(UUID id) {
        return core.profiles().section(id, "boss", BossData.class, BossData::new).pending.size();
    }

    public static String encode(ItemStack item) {
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (RuntimeException e) {
            Bukkit.getLogger().log(Level.WARNING, "Could not serialize a boss loot item", e);
            return null;
        }
    }

    public static ItemStack decode(String text) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
        } catch (RuntimeException e) {
            Bukkit.getLogger().log(Level.WARNING, "Could not read a queued boss loot item", e);
            return null;
        }
    }
}
