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
 * Hands items to players without ever losing or duplicating them: straight into the inventory, the overflow dropped
 * at their feet reserved for them, and for offline players a queue in their profile delivered at the next login.
 */
public final class LootDelivery {
    /** Maximum queued stacks per player (a safety net against a runaway queue). */
    public static final int MAX_PENDING = 200;

    private final Core core;

    public LootDelivery(Core core) {
        this.core = core;
    }

    /** Gives items to an online player; what does not fit is dropped at their feet, reserved for them. */
    public void give(Player player, Collection<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
            for (ItemStack rest : left.values()) {
                dropReserved(player.getLocation(), rest, player.getUniqueId());
            }
        }
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

    /** Appends items to the profile queue of a (possibly offline) player. */
    public void queue(UUID id, Collection<ItemStack> items) {
        List<String> encoded = new ArrayList<>();
        for (ItemStack item : items) {
            String text = encode(item);
            if (text != null) {
                encoded.add(text);
            }
        }
        if (encoded.isEmpty()) {
            return;
        }
        Runnable edit = () -> core.profiles().edit(id, "boss", BossData.class, BossData::new, data -> {
            for (String text : encoded) {
                if (data.pending.size() < MAX_PENDING) {
                    data.pending.add(text);
                }
            }
        });
        if (Bukkit.isPrimaryThread() && !core.profiles().isLoaded(id)) {
            core.scheduler().async(edit);
        } else {
            edit.run();
        }
    }

    /** Delivers the queued items of a player who just logged in. Returns how many stacks were delivered. */
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
        data.pending.clear();
        core.profiles().markDirty(player.getUniqueId());
        give(player, items);
        return items.size();
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
