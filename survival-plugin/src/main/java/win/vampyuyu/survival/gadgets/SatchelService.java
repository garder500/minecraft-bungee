package win.vampyuyu.survival.gadgets;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;

/**
 * The Satchel bag of each player: one live 27-slot inventory per player (shared by the open view and by pickups),
 * written to the profile section {@code satchel} after every change.
 */
public final class SatchelService {
    public static final int SIZE = 27;

    /** Marks the bag inventory so listeners can recognise it. */
    static final class Holder implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Core core;
    private final Map<UUID, Inventory> live = new HashMap<>();

    SatchelService(Core core) {
        this.core = core;
    }

    /** The live bag of a player, loaded from the profile on first use. */
    public Inventory bag(UUID player) {
        return live.computeIfAbsent(player, id -> {
            Holder holder = new Holder(id);
            Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Satchel"));
            holder.inventory = inv;
            List<String> slots = data(id).slots;
            for (int i = 0; i < SIZE && i < slots.size(); i++) {
                String text = slots.get(i);
                if (text != null && !text.isEmpty()) {
                    try {
                        inv.setItem(i, ItemStack.deserializeBytes(Base64.getDecoder().decode(text)));
                    } catch (RuntimeException e) {
                        core.getPlugin().getLogger().warning("satchel: slot " + i + " of " + id + " unreadable, emptied");
                    }
                }
            }
            return inv;
        });
    }

    private SatchelData data(UUID id) {
        return core.profiles().section(id, "satchel", SatchelData.class, SatchelData::new);
    }

    /** Writes the live bag to the profile. */
    public void save(UUID player) {
        Inventory inv = live.get(player);
        if (inv == null) {
            return;
        }
        SatchelData data = data(player);
        data.slots = new java.util.ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            data.slots.add(stack == null || stack.getType().isAir() ? ""
                : Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        }
        core.profiles().markDirty(player);
    }

    /** Puts the stack in the bag. Returns how many items did not fit. The bag is saved. */
    public int add(UUID player, ItemStack stack) {
        Map<Integer, ItemStack> left = bag(player).addItem(stack.clone());
        save(player);
        return left.values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    /** Saves and forgets the bag when nobody looks at it (player left). */
    void release(UUID player) {
        save(player);
        Inventory inv = live.get(player);
        if (inv != null && inv.getViewers().isEmpty()) {
            live.remove(player);
        }
    }

    void saveAll() {
        List.copyOf(live.keySet()).forEach(this::save);
    }
}
