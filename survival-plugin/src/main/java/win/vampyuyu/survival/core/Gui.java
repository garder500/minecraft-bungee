package win.vampyuyu.survival.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A chest-style menu of 1 to 6 rows. Build with {@link Guis#create}, fill slots, then {@link #open}.
 * All clicks and drags are cancelled by the global {@link Guis} listener; only the handler you attach to a slot runs.
 * <pre>{@code
 * Gui gui = core.guis().create(3, Component.text("My menu"));
 * gui.set(13, Guis.button(Material.DIAMOND, "<aqua>Click me", "Does a thing", "Open"), click -> {
 *     if (click.shift()) { ... }
 *     click.close();
 * });
 * gui.open(player);
 * }</pre>
 */
public final class Gui implements InventoryHolder {
    /** Everything a slot handler needs to know about a click. */
    public record Click(Player player, Gui gui, int slot, ClickType type) {
        public boolean left() { return type.isLeftClick(); }

        public boolean right() { return type.isRightClick(); }

        public boolean shift() { return type.isShiftClick(); }

        /** Closes the menu at the start of the next tick (closing inside the click event is unsafe). */
        public void close() {
            gui.guis.scheduler.nextTick(() -> {
                if (Guis.holderOf(player.getOpenInventory().getTopInventory()) == gui) {
                    player.closeInventory();
                }
            });
        }
    }

    /** Called when a slot is clicked. */
    @FunctionalInterface
    public interface ClickHandler {
        void onClick(Click click);
    }

    private final Guis guis;
    private final int rows;
    private final Inventory inventory;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();
    private Consumer<Player> closeHandler;

    Gui(Guis guis, int rows, Component title) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1-6, got " + rows);
        }
        this.guis = guis;
        this.rows = rows;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    /** Puts an item in a slot with no click action (the click is still cancelled). */
    public Gui set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    /** Puts an item in a slot; {@code handler} runs on left, right and shift clicks (null for none). */
    public Gui set(int slot, ItemStack item, ClickHandler handler) {
        checkSlot(slot);
        inventory.setItem(slot, item);
        if (handler == null) {
            handlers.remove(slot);
        } else {
            handlers.put(slot, handler);
        }
        return this;
    }

    /** Empties a slot and drops its handler. */
    public Gui clear(int slot) {
        checkSlot(slot);
        inventory.setItem(slot, null);
        handlers.remove(slot);
        return this;
    }

    /** No-op: the menus have a painted background and no filler. Kept so older callers still compile. */
    @Deprecated
    public Gui fill(ItemStack item) {
        return this;
    }

    /** No-op: the menus have a painted background and no glass border. Kept so older callers still compile. */
    @Deprecated
    public Gui border(ItemStack item) {
        return this;
    }

    /** Gives the item already in {@code slot} the pack item model {@code vampyuyu:<path>} (a menu header icon). */
    public Gui model(int slot, String path) {
        checkSlot(slot);
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            Guis.model(item, path);
        }
        return this;
    }

    /** Slot of the Back button: bottom-left corner (45 in a 6-row menu, 18 in a 3-row one). */
    public int backSlot() {
        return (rows - 1) * 9;
    }

    /** Slot of the Previous page arrow: bottom row, second to last. */
    public int prevSlot() {
        return size() - 2;
    }

    /** Slot of the Next page arrow: bottom-right corner. */
    public int nextSlot() {
        return size() - 1;
    }

    /** Puts a Back button in the bottom-left corner. */
    public Gui back(String label, ClickHandler handler) {
        return set(backSlot(), Guis.backIcon(label), handler);
    }

    /** Runs when the menu is closed, by the player or because another inventory replaced it (not on a plugin disable that never closes it). */
    public Gui onClose(Consumer<Player> handler) {
        this.closeHandler = handler;
        return this;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Content slots: rows 2..n-1, columns 2..8 (the bottom row is for Back and the page arrows). */
    public int[] innerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= size()) {
            throw new IllegalArgumentException("slot " + slot + " outside a " + rows + " row menu");
        }
    }

    // ---------------------------------------------------------------- pagination

    /**
     * Shows a list over several pages. Fills {@code slots} with {@code icon(item)}, clicking one calls
     * {@code onItem}. Small Previous/Next arrows go on the bottom row corners ({@link #prevSlot}, {@link #nextSlot})
     * and re-render the menu in place. Returns a handle to jump to a page.
     */
    public <T> Pager<T> paginate(List<T> items, int[] slots, Function<T, ItemStack> icon,
            BiConsumer<Click, T> onItem) {
        return paginate(items, slots, icon, onItem, prevSlot(), nextSlot());
    }

    /** Older form: the slots given are ignored, the arrows always go on the bottom corners. */
    @Deprecated
    public <T> Pager<T> paginate(List<T> items, int[] slots, Function<T, ItemStack> icon,
            BiConsumer<Click, T> onItem, int prevSlot, int nextSlot) {
        if (slots.length == 0) {
            throw new IllegalArgumentException("paginate needs at least one content slot");
        }
        Pager<T> pager = new Pager<>(this, List.copyOf(items), slots, icon, onItem, prevSlot(), nextSlot());
        pager.render();
        return pager;
    }

    /** Pagination state, see {@link #paginate}. */
    public static final class Pager<T> {
        private final Gui gui;
        private final List<T> items;
        private final int[] slots;
        private final Function<T, ItemStack> icon;
        private final BiConsumer<Click, T> onItem;
        private final int prevSlot;
        private final int nextSlot;
        private int page;

        private Pager(Gui gui, List<T> items, int[] slots, Function<T, ItemStack> icon,
                BiConsumer<Click, T> onItem, int prevSlot, int nextSlot) {
            this.gui = gui;
            this.items = items;
            this.slots = slots;
            this.icon = icon;
            this.onItem = onItem;
            this.prevSlot = prevSlot;
            this.nextSlot = nextSlot;
        }

        public int page() { return page; }

        public int pages() {
            return Math.max(1, (items.size() + slots.length - 1) / slots.length);
        }

        public void page(int page) {
            this.page = Math.max(0, Math.min(page, pages() - 1));
            render();
        }

        /** Redraws the content slots and the arrows for the current page. */
        public void render() {
            for (int slot : slots) {
                gui.clear(slot);
            }
            int from = page * slots.length;
            for (int i = 0; i < slots.length && from + i < items.size(); i++) {
                T item = items.get(from + i);
                gui.set(slots[i], icon.apply(item), click -> onItem.accept(click, item));
            }
            gui.clear(prevSlot);
            gui.clear(nextSlot);
            if (page > 0) {
                gui.set(prevSlot, Guis.arrow("Previous", "Page " + page + "/" + pages(), "back"),
                    click -> page(page - 1));
            }
            if (page < pages() - 1) {
                gui.set(nextSlot, Guis.arrow("Next", "Page " + (page + 2) + "/" + pages(), "next"),
                    click -> page(page + 1));
            }
        }
    }

    // package private for Guis
    ClickHandler handler(int slot) {
        return handlers.get(slot);
    }

    void closed(Player player) {
        if (closeHandler != null) {
            closeHandler.accept(player);
        }
    }
}
