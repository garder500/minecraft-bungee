package win.vampyuyu.survival.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Rarity;

/**
 * /trash: a chest menu whose top 27 slots accept anything; closing it destroys what is inside, except valuable custom
 * items (configured ids or a minimum rarity) which are handed back. The bottom row is a locked control row. This
 * menu does not use {@link win.vampyuyu.survival.core.Gui} because that class cancels every click and the trash
 * must accept items.
 */
final class TrashCommand implements Listener {
    static final int ROWS = 4;
    static final int TRASH_SLOTS = 27;
    private static final int TAKE_BACK_SLOT = 30;
    private static final int INFO_SLOT = 31;

    /** Marks the inventory as ours. */
    static final class Holder implements InventoryHolder {
        Inventory inventory;
        boolean closing;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Core core;
    private final Msg msg;
    private final Supplier<UtilityConfig> config;
    private final Map<java.util.UUID, Holder> open = new HashMap<>();

    TrashCommand(Core core, Supplier<UtilityConfig> config) {
        this.core = core;
        this.msg = core.msg();
        this.config = config;
    }

    void register() {
        core.listen(this);
        core.commands().command("trash").aliases("bin", "disposal").description("Open a menu that destroys what you put in")
            .usage("/trash").permission(Perms.TRASH, PermissionDefault.TRUE).executor(this::trash).register();
    }

    private boolean trash(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, ROWS * 9,
            Msg.component("<dark_gray>Trash <red>(destroyed on close)"));
        holder.inventory = inventory;
        ItemStack pane = Guis.icon(Material.RED_STAINED_GLASS_PANE, " ");
        for (int slot = TRASH_SLOTS; slot < ROWS * 9; slot++) {
            inventory.setItem(slot, pane);
        }
        inventory.setItem(TAKE_BACK_SLOT, Guis.icon(Material.HOPPER, "<green>Take everything back",
            "<gray>Moves the items below back to your inventory"));
        inventory.setItem(INFO_SLOT, Guis.icon(Material.LAVA_BUCKET, "<red>Items are destroyed when you close this",
            "<gray>There is no undo after closing.", "<gray>Valuable custom items are handed back."));
        open.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        return true;
    }

    private static InventoryHolder holderOf(Inventory inventory) {
        try {
            return inventory.getHolder(false);
        } catch (RuntimeException | AbstractMethodError e) {
            return inventory.getHolder();
        }
    }

    private static boolean ours(Inventory inventory) {
        return inventory != null && holderOf(inventory) instanceof Holder;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!ours(top)) {
            return;
        }
        ClickType type = event.getClick();
        // A double click gathers matching stacks from every slot, including the locked row.
        if (type == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();
        if (inTop && raw >= TRASH_SLOTS) {
            event.setCancelled(true);
            if (raw == TAKE_BACK_SLOT && event.getWhoClicked() instanceof Player player) {
                takeBack(player, top);
            }
            return;
        }
        // Number key and offhand swaps use the clicked slot as source or target: fine inside the trash area only.
        if (type == ClickType.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!ours(top)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= TRASH_SLOTS && raw < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!ours(inventory) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Holder holder = (Holder) holderOf(inventory);
        if (holder.closing) {
            return;
        }
        holder.closing = true;
        open.remove(player.getUniqueId(), holder);
        settle(player, inventory);
    }

    /** Destroys the trash content, handing valuables back. */
    private void settle(Player player, Inventory inventory) {
        int destroyed = 0;
        List<ItemStack> keep = new ArrayList<>();
        for (int slot = 0; slot < TRASH_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            if (shouldKeep(item)) {
                keep.add(item);
            } else {
                destroyed += item.getAmount();
            }
        }
        giveBack(player, keep);
        if (destroyed > 0) {
            msg.info(player, "Destroyed " + destroyed + " item" + (destroyed == 1 ? "" : "s") + ".");
        }
        if (!keep.isEmpty()) {
            msg.info(player, "Valuable items were handed back instead of being destroyed.");
        }
    }

    /** True for custom items that must never be destroyed by accident. */
    boolean shouldKeep(ItemStack item) {
        UtilityConfig cfg = config.get();
        Optional<String> id = core.items().idOf(item);
        if (id.isEmpty()) {
            return false;
        }
        if (cfg.trashKeepIds().contains(id.get())) {
            return true;
        }
        Rarity min = cfg.trashKeepRarity();
        if (min == null) {
            return false;
        }
        return core.items().def(id.get()).map(CustomItemDef::rarity).map(r -> r.ordinal() >= min.ordinal())
            .orElse(false);
    }

    private void takeBack(Player player, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < TRASH_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item);
                inventory.setItem(slot, null);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        // What does not fit goes back into the trash area (still visible, still recoverable).
        int slot = 0;
        for (ItemStack rest : left.values()) {
            while (slot < TRASH_SLOTS && inventory.getItem(slot) != null) {
                slot++;
            }
            if (slot < TRASH_SLOTS) {
                inventory.setItem(slot, rest);
            } else {
                giveBack(player, List.of(rest));
            }
        }
        msg.ok(player, "Items moved back to your inventory.");
    }

    private void giveBack(Player player, Collection<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        Location at = player.getLocation();
        for (ItemStack rest : left.values()) {
            // The inventory is full: drop at the feet rather than lose a valuable item.
            at.getWorld().dropItem(at, rest);
        }
    }

    /** Hands the contents back to everybody still looking at a trash menu (plugin disable). */
    void shutdown() {
        for (Map.Entry<java.util.UUID, Holder> entry : Map.copyOf(open).entrySet()) {
            Holder holder = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            holder.closing = true;
            List<ItemStack> all = new ArrayList<>();
            for (int slot = 0; slot < TRASH_SLOTS; slot++) {
                ItemStack item = holder.inventory.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    all.add(item);
                    holder.inventory.setItem(slot, null);
                }
            }
            giveBack(player, all);
            player.closeInventory();
        }
        open.clear();
    }
}
