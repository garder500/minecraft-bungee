package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Inventory helpers. Main thread only. */
final class Inv {
    private Inv() {}

    /** Splits {@code amount} items of {@code template} into legal stacks. */
    static List<ItemStack> stacks(ItemStack template, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, template.getMaxStackSize());
        int left = amount;
        while (left > 0) {
            ItemStack s = template.clone();
            s.setAmount(Math.min(max, left));
            out.add(s);
            left -= s.getAmount();
        }
        return out;
    }

    /** True when every stack would fit in the player's storage slots (hotbar + main inventory). */
    static boolean canFit(Player player, List<ItemStack> stacks) {
        Inventory sim = Bukkit.createInventory(null, 36);
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < 36 && i < storage.length; i++) {
            sim.setItem(i, storage[i] == null ? null : storage[i].clone());
        }
        return sim.addItem(stacks.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty();
    }

    /** Gives the stacks; anything that does not fit is dropped at the player's feet (callers check canFit first). */
    static void give(Player player, List<ItemStack> stacks) {
        var left = player.getInventory().addItem(stacks.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        Location at = player.getLocation();
        if (at.getWorld() != null) {
            left.values().forEach(s -> at.getWorld().dropItem(at, s));
        }
    }

    /** Number of clean items with this key in the storage slots. */
    static int count(Player player, ItemKeys keys, String key) {
        int n = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && key.equals(keys.keyOf(s)) && keys.clean(s)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    /** Removes exactly {@code amount} clean items with this key; false (nothing removed) when there are fewer. */
    static boolean remove(Player player, ItemKeys keys, String key, int amount) {
        if (count(player, keys, key) < amount) {
            return false;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        int left = amount;
        for (int i = 0; i < storage.length && left > 0; i++) {
            ItemStack s = storage[i];
            if (s != null && key.equals(keys.keyOf(s)) && keys.clean(s)) {
                int take = Math.min(left, s.getAmount());
                if (take == s.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    s.setAmount(s.getAmount() - take);
                    player.getInventory().setItem(i, s);
                }
                left -= take;
            }
        }
        return true;
    }

    /** Asks the server to write the player's data now (after an item left the inventory for an escrow). */
    static void saveData(Player player, boolean enabled) {
        if (!enabled) {
            return;
        }
        try {
            player.saveData();
        } catch (RuntimeException | LinkageError e) {
            Bukkit.getLogger().log(Level.FINE, "saveData not available", e);
        }
    }
}
