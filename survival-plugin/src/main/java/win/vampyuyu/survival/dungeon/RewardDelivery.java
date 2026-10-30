package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;

/**
 * Hands out reward items without ever losing or duplicating one: what does not fit in the inventory goes into the
 * pending list of the profile section {@code dungeon} (claimed with {@code /dungeon claim}); nothing is dropped.
 */
public final class RewardDelivery {
    private RewardDelivery() {}

    /** Builds a stack for a reward id (custom item id or {@code mc:MATERIAL}); null when the id is unknown. */
    public static ItemStack make(Core core, String item, int amount) {
        if (item == null || amount <= 0) {
            return null;
        }
        if (item.regionMatches(true, 0, "mc:", 0, 3)) {
            Material m = Material.matchMaterial(item.substring(3).toUpperCase(Locale.ROOT));
            if (m == null || !m.isItem() || m.isAir()) {
                return null;
            }
            return new ItemStack(m, Math.min(amount, m.getMaxStackSize()));
        }
        try {
            ItemStack s = core.items().create(item);
            s.setAmount(Math.min(amount, Math.max(1, s.getMaxStackSize())));
            return s;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gives {@code amount} of an item. Returns how many went to the pending list (inventory full, or the player
     * is offline). Unknown ids are logged and dropped from the reward (they cannot be given later either).
     */
    public static int give(Core core, UUID owner, Player player, String item, int amount) {
        if (amount <= 0) {
            return 0;
        }
        ItemStack probe = make(core, item, 1);
        if (probe == null) {
            core.getPlugin().getLogger().warning("Dungeon reward: unknown item '" + item + "', skipped");
            return 0;
        }
        int remaining = amount;
        if (player != null && player.isOnline()) {
            int stack = Math.max(1, probe.getMaxStackSize());
            while (remaining > 0) {
                int n = Math.min(remaining, stack);
                ItemStack s = make(core, item, n);
                s.setAmount(n);
                Map<Integer, ItemStack> left = player.getInventory().addItem(s);
                if (!left.isEmpty()) {
                    int notFit = 0;
                    for (ItemStack l : left.values()) {
                        notFit += l.getAmount();
                    }
                    remaining = remaining - n + notFit;
                    break;
                }
                remaining -= n;
            }
        }
        if (remaining > 0) {
            final int pending = remaining;
            core.profiles().edit(owner, "dungeon", DungeonData.class, DungeonData::new, d -> d.addPending(item, pending));
        }
        return remaining;
    }

    /** Moves pending items to the inventory as far as it fits. Returns the number of items handed over. */
    public static int claim(Core core, Player player) {
        DungeonData data = core.profiles().section(player.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        int given = 0;
        for (DungeonData.Pending p : new ArrayList<>(data.pending)) {
            int remaining = p.amount;
            ItemStack probe = make(core, p.item, 1);
            if (probe == null) {
                data.pending.remove(p); // an item that no longer exists cannot be claimed, forget it
                continue;
            }
            int stack = Math.max(1, probe.getMaxStackSize());
            while (remaining > 0) {
                int n = Math.min(remaining, stack);
                ItemStack s = make(core, p.item, n);
                s.setAmount(n);
                Map<Integer, ItemStack> left = player.getInventory().addItem(s);
                if (!left.isEmpty()) {
                    int notFit = 0;
                    for (ItemStack l : left.values()) {
                        notFit += l.getAmount();
                    }
                    given += n - notFit;
                    remaining = remaining - n + notFit;
                    break;
                }
                given += n;
                remaining -= n;
            }
            if (remaining <= 0) {
                data.pending.remove(p);
            } else {
                p.amount = remaining;
                break; // inventory is full
            }
        }
        core.profiles().markDirty(player.getUniqueId());
        return given;
    }

    /** Display names of the pending list, for messages. */
    public static List<String> describe(DungeonData data) {
        List<String> out = new ArrayList<>();
        for (DungeonData.Pending p : data.pending) {
            out.add(p.amount + "x " + p.item.replace("mc:", "").replace('_', ' ').toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
