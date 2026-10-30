package win.vampyuyu.survival.items.pet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.menu.ItemMenu;

/**
 * The {@code /pets} menu. Left click a pet to summon it (or put it away), right click to select it for the held
 * item slot, shift click to turn it back into an egg item. Nothing here can duplicate a pet: every change goes
 * through {@link PetService} and the collection is the single source of truth.
 */
public final class PetMenu extends ItemMenu {
    private static final int[] PET_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int PREV = 45;
    private static final int AUTO = 47;
    private static final int HELD = 49;
    private static final int DESPAWN = 51;
    private static final int NEXT = 53;
    private static final int CLOSE = 52;

    private final PetService service;
    private final UUID id;
    private int page;
    private String selected = "";

    public PetMenu(Core core, Player player, PetService service) {
        super(core, player, 6, Component.text("Pets", NamedTextColor.DARK_GREEN));
        this.service = service;
        this.id = player.getUniqueId();
        render();
    }

    private List<PetData.Entry> sorted() {
        PetData data = service.data(id);
        List<PetData.Entry> list = new ArrayList<>(data.pets);
        list.sort(Comparator.<PetData.Entry>comparingInt(e -> e.id.equals(data.active) ? 0 : 1)
            .thenComparing(Comparator.<PetData.Entry>comparingInt(e -> -service.rarityOf(e).ordinal()))
            .thenComparing(Comparator.<PetData.Entry>comparingDouble(e -> -e.xp)));
        return list;
    }

    private int pages(int total) {
        return Math.max(1, (total + PET_SLOTS.length - 1) / PET_SLOTS.length);
    }

    private void render() {
        inventory.clear();
        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        PetData data = service.data(id);
        List<PetData.Entry> pets = sorted();
        page = Math.max(0, Math.min(page, pages(pets.size()) - 1));
        inventory.setItem(4, Guis.icon(Material.EGG, "<green>Your pets",
            "<gray>Owned: <white>" + pets.size() + "/" + service.settings().petMaxOwned,
            "<gray>Pets level up with the matching skill's experience.", "",
            "<yellow>Left click<gray> a pet to summon or put it away",
            "<yellow>Right click<gray> a pet to select it for a held item",
            "<yellow>Shift click<gray> a pet to turn it back into an egg"));
        for (int i = 0; i < PET_SLOTS.length; i++) {
            int index = page * PET_SLOTS.length + i;
            inventory.setItem(PET_SLOTS[i], index < pets.size() ? petIcon(pets.get(index), data) : null);
        }
        inventory.setItem(PREV, page > 0 ? Guis.icon(Material.ARROW, "<yellow>Previous page")
            : Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        inventory.setItem(NEXT, page < pages(pets.size()) - 1 ? Guis.icon(Material.ARROW, "<yellow>Next page")
            : Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        inventory.setItem(AUTO, Guis.icon(data.autoPet ? Material.LIME_DYE : Material.GRAY_DYE,
            data.autoPet ? "<green>Auto-pet: ON" : "<red>Auto-pet: OFF",
            "<gray>Summon your last pet again when you join.", "", "<yellow>Click to toggle"));
        inventory.setItem(HELD, heldIcon());
        inventory.setItem(DESPAWN, Guis.icon(Material.BARRIER, "<red>Put the pet away",
            "<gray>Your active pet goes back to the collection."));
        inventory.setItem(CLOSE, Guis.icon(Material.OAK_DOOR, "<gray>Close"));
    }

    private ItemStack petIcon(PetData.Entry entry, PetData data) {
        Optional<PetType> type = PetTypes.get(entry.type);
        if (type.isEmpty()) {
            return Guis.icon(Material.BARRIER, "<red>Unknown pet", "<gray>Type: " + entry.type);
        }
        ItemStack icon = PetVisuals.displayItem(type.get());
        var meta = icon.getItemMeta();
        boolean active = entry.id.equals(data.active);
        meta.displayName(Component.text("[Lvl " + service.level(entry) + "] " + type.get().name(),
            service.rarityOf(entry).color()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>(service.describe(entry));
        lore.add(Component.empty());
        lore.add(Component.text(service.rarityOf(entry).label() + " PET", service.rarityOf(entry).color(), TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text(active ? "Click to put away" : "Click to summon",
            active ? NamedTextColor.RED : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(active || entry.id.equals(selected));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack heldIcon() {
        Optional<PetData.Entry> entry = service.find(id, selected);
        if (entry.isEmpty()) {
            return Guis.icon(Material.ITEM_FRAME, "<gray>Held item", "<gray>Right click a pet first, then click here", "<gray>with a held item on your cursor.");
        }
        PetType type = PetTypes.get(entry.get().type).orElse(null);
        String name = type == null ? entry.get().type : type.name();
        if (entry.get().held.isEmpty()) {
            return Guis.icon(Material.ITEM_FRAME, "<gray>Held item of " + name, "<gray>Empty.", "",
                "<yellow>Click with a pet item on your cursor", "<yellow>to equip it.");
        }
        ItemStack held = service.gear().items().create(entry.get().held);
        var meta = held.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Click with an empty cursor to take it back", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        held.setItemMeta(meta);
        return held;
    }

    private Optional<PetData.Entry> entryAt(int slot) {
        for (int i = 0; i < PET_SLOTS.length; i++) {
            if (PET_SLOTS[i] == slot) {
                List<PetData.Entry> pets = sorted();
                int index = page * PET_SLOTS.length + i;
                return index < pets.size() ? Optional.of(pets.get(index)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    protected void click(InventoryClickEvent event, boolean top) {
        if (!top) {
            return;
        }
        int slot = event.getSlot();
        ClickType click = event.getClick();
        if (slot == PREV && page > 0) {
            page--;
            render();
        } else if (slot == NEXT) {
            page++;
            render();
        } else if (slot == AUTO) {
            PetData data = service.data(id);
            data.autoPet = !data.autoPet;
            core.profiles().markDirty(id);
            render();
        } else if (slot == DESPAWN) {
            service.despawn(id);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.8f);
            render();
        } else if (slot == CLOSE) {
            core.scheduler().nextTick(player::closeInventory);
        } else if (slot == HELD) {
            heldClick();
        } else {
            entryAt(slot).ifPresent(entry -> petClick(entry, click));
        }
    }

    private void petClick(PetData.Entry entry, ClickType click) {
        if (click.isShiftClick()) {
            toEgg(entry);
        } else if (click.isRightClick()) {
            selected = entry.id;
            render();
        } else if (click.isLeftClick()) {
            PetData data = service.data(id);
            if (entry.id.equals(data.active)) {
                service.despawn(id);
                core.msg().info(player, "You put your pet away.");
            } else if (service.summon(player, entry.id)) {
                core.msg().ok(player, "You summoned your " + PetTypes.get(entry.type).map(PetType::name).orElse("pet") + ".");
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.7f, 1.4f);
            } else {
                core.msg().error(player, "That pet cannot be summoned right now.");
            }
            render();
        }
    }

    private void toEgg(PetData.Entry entry) {
        ItemStack egg = service.createEgg(entry);
        if (!service.remove(id, entry.id)) {
            return;
        }
        if (entry.id.equals(selected)) {
            selected = "";
        }
        giveBack(egg);
        core.msg().ok(player, "Your pet is now an egg in your inventory.");
        render();
    }

    private void heldClick() {
        Optional<PetData.Entry> entry = service.find(id, selected);
        if (entry.isEmpty()) {
            core.msg().warn(player, "Right click a pet first.");
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        boolean empty = cursor == null || cursor.getType().isAir();
        if (empty) {
            if (entry.get().held.isEmpty()) {
                return;
            }
            ItemStack back = service.gear().items().create(entry.get().held);
            entry.get().held = "";
            core.profiles().markDirty(id);
            service.refresh(id);
            giveBack(back);
            render();
            return;
        }
        Optional<String> effect = service.gear().items().getString(cursor, Keys.HELD_EFFECT);
        Optional<String> itemId = service.gear().items().idOf(cursor);
        if (effect.isEmpty() || itemId.isEmpty()) {
            core.msg().error(player, "That is not a pet item.");
            return;
        }
        String previous = entry.get().held;
        entry.get().held = itemId.get();
        ItemStack rest = cursor.clone();
        rest.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(rest.getAmount() <= 0 ? null : rest);
        if (!previous.isEmpty()) {
            giveBack(service.gear().items().create(previous));
        }
        core.profiles().markDirty(id);
        service.refresh(id);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.2f);
        render();
    }
}
