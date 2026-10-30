package win.vampyuyu.survival.items.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.items.Costs;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.catalog.ItemLoreExtras;
import win.vampyuyu.survival.items.enchant.Enchant;
import win.vampyuyu.survival.items.enchant.EnchantRules;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.ItemKind;

/**
 * Replaces the vanilla enchanting table in Skyblock worlds. Put an item in the middle slot, click an enchant to raise
 * it by one level (coins, and an Enchanting skill level for the higher levels), shift + right click removes it.
 * Awards Enchanting XP through {@code SkillsApi.addXp}.
 */
public final class EnchantTableMenu extends ItemMenu {
    private static final int ITEM = 13;
    private static final int[] OPTIONS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Gear gear;
    private final Costs costs;
    private final ItemLoreExtras lore;
    private final java.util.function.Supplier<ItemsSettings> settings;
    private int page;

    public EnchantTableMenu(Core core, Player player, Gear gear, Costs costs, ItemLoreExtras lore,
            java.util.function.Supplier<ItemsSettings> settings) {
        super(core, player, 6, Component.text("Enchanting Table", NamedTextColor.DARK_PURPLE));
        this.gear = gear;
        this.costs = costs;
        this.lore = lore;
        this.settings = settings;
        render();
    }

    private boolean accepts(ItemStack stack) {
        Optional<GearInfo> info = gear.read(stack);
        return info.isPresent() && info.get().kind() != ItemKind.OTHER && info.get().kind() != ItemKind.BOOK
            && info.get().kind() != ItemKind.PET && info.get().kind() != ItemKind.ACCESSORY
            && !gear.enchants().tableFor(info.get().kind()).isEmpty();
    }

    /** Skill level needed for level {@code level} of an enchant (level I is free). */
    public static int requiredSkill(int level, int perLevel) {
        return Math.max(0, (level - 1) * perLevel);
    }

    private int skillLevel() {
        return core.service(SkillsApi.class).map(api -> api.level(player.getUniqueId(), Skill.ENCHANTING)).orElse(Integer.MAX_VALUE);
    }

    private void render() {
        inventory.clear();
        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(4, Guis.icon(Material.ENCHANTING_TABLE, "<light_purple>Enchanting",
            "<gray>Put an item in the middle slot, then click an", "<gray>enchant to raise it by one level.",
            "", "<gray>Higher levels need a higher <green>Enchanting</green> level.",
            "<gray>Shift + right click removes an enchant.", "<gray>Ultimate enchants come from books (see the anvil)."));
        inventory.setItem(ITEM, null);
        inventory.setItem(49, Guis.icon(Material.BARRIER, "<red>Close"));
        drawOptions();
    }

    private void drawOptions() {
        for (int slot : OPTIONS) {
            inventory.setItem(slot, Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        ItemStack stack = inventory.getItem(ITEM);
        Optional<GearInfo> info = gear.read(stack);
        if (info.isEmpty() || !accepts(stack)) {
            inventory.setItem(31, Guis.icon(Material.RED_STAINED_GLASS_PANE, "<red>No item",
                "<gray>Place a weapon, tool or armor piece above."));
            return;
        }
        List<Enchant> list = gear.enchants().tableFor(info.get().kind());
        ItemsSettings s = settings.get();
        int perPage = OPTIONS.length;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < perPage && page * perPage + i < list.size(); i++) {
            Enchant enchant = list.get(page * perPage + i);
            inventory.setItem(OPTIONS[i], option(enchant, info.get(), s));
        }
        if (pages > 1) {
            inventory.setItem(45, Guis.icon(Material.ARROW, "<yellow>Previous page", "<gray>Page " + (page + 1) + "/" + pages));
            inventory.setItem(53, Guis.icon(Material.ARROW, "<yellow>Next page", "<gray>Page " + (page + 1) + "/" + pages));
        }
    }

    private ItemStack option(Enchant enchant, GearInfo info, ItemsSettings s) {
        int have = info.enchants().getOrDefault(enchant.id(), 0);
        int max = Math.min(enchant.maxLevel(), enchant.tableMax());
        List<String> out = new ArrayList<>();
        for (String line : lore.describe(enchant, Math.max(1, Math.min(max, have + 1)))) {
            out.add("<gray>" + line);
        }
        out.add("");
        out.add("<gray>Current level: <white>" + (have == 0 ? "none" : Enchant.roman(have)));
        if (have >= max) {
            out.add(have >= enchant.maxLevel() ? "<green>Maxed out" : "<yellow>Higher levels need a book");
        } else {
            int next = have + 1;
            double cost = EnchantRules.tableCost(next, s.enchantCostBase, s.enchantCostGrowth);
            int need = requiredSkill(next, s.i("costs.enchant-table.skill-levels-per-level", 5));
            out.add("<gray>Next: <white>" + enchant.label(next));
            out.add("<gray>Cost: <gold>" + Costs.price(cost));
            boolean ok = skillLevel() >= need;
            out.add((ok ? "<green>" : "<red>") + "Enchanting level " + need + " needed");
            out.add("");
            out.add(ok ? "<yellow>Click to enchant" : "<red>You are not skilled enough yet");
        }
        return Guis.icon(have > 0 ? Material.ENCHANTED_BOOK : Material.BOOK, "<aqua>" + enchant.name(), out.toArray(new String[0]));
    }

    @Override
    protected void click(InventoryClickEvent event, boolean top) {
        if (!top) {
            if (event.getClick().isShiftClick() && moveFromPlayer(event, new int[] {ITEM}, this::accepts)) {
                drawOptions();
            }
            return;
        }
        int slot = event.getSlot();
        if (slot == ITEM) {
            if (inputSlot(event, ITEM, this::accepts)) {
                page = 0;
                drawOptions();
            }
            return;
        }
        if (slot == 49) {
            core.scheduler().nextTick(player::closeInventory);
            return;
        }
        if (slot == 45 || slot == 53) {
            page = Math.max(0, page + (slot == 53 ? 1 : -1));
            drawOptions();
            return;
        }
        int index = -1;
        for (int i = 0; i < OPTIONS.length; i++) {
            if (OPTIONS[i] == slot) {
                index = i;
            }
        }
        ItemStack stack = inventory.getItem(ITEM);
        Optional<GearInfo> info = gear.read(stack);
        if (index < 0 || info.isEmpty()) {
            return;
        }
        List<Enchant> list = gear.enchants().tableFor(info.get().kind());
        int at = page * OPTIONS.length + index;
        if (at >= list.size()) {
            return;
        }
        Enchant enchant = list.get(at);
        if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            remove(stack, info.get(), enchant);
        } else {
            raise(stack, info.get(), enchant);
        }
        drawOptions();
    }

    private void remove(ItemStack stack, GearInfo info, Enchant enchant) {
        if (!info.enchants().containsKey(enchant.id())) {
            return;
        }
        Map<String, Integer> next = new java.util.LinkedHashMap<>(info.enchants());
        next.remove(enchant.id());
        gear.setEnchants(stack, next);
        gear.rebuild(stack);
        inventory.setItem(ITEM, stack);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.6f, 1.2f);
    }

    private void raise(ItemStack stack, GearInfo info, Enchant enchant) {
        ItemsSettings s = settings.get();
        int have = info.enchants().getOrDefault(enchant.id(), 0);
        int max = Math.min(enchant.maxLevel(), enchant.tableMax());
        if (have >= max) {
            core.msg().warn(player, have >= enchant.maxLevel() ? "That enchant is already at its maximum." : "Higher levels need an enchanted book.");
            return;
        }
        int next = have + 1;
        int need = requiredSkill(next, s.i("costs.enchant-table.skill-levels-per-level", 5));
        if (skillLevel() < need) {
            core.msg().error(player, "You need Enchanting level " + need + " for " + enchant.label(next) + ".");
            return;
        }
        Map<String, Integer> current = new java.util.LinkedHashMap<>(info.enchants());
        if (EnchantRules.hasOtherUltimate(current, gear.enchants(), enchant.id()) && enchant.ultimate()) {
            core.msg().error(player, EnchantRules.Refusal.ALREADY_ULTIMATE.message());
            return;
        }
        double cost = EnchantRules.tableCost(next, s.enchantCostBase, s.enchantCostGrowth);
        if (!costs.charge(player, cost, "items:enchant_table")) {
            return;
        }
        try {
            current.put(enchant.id(), next);
            gear.setEnchants(stack, current);
            gear.rebuild(stack);
            inventory.setItem(ITEM, stack);
        } catch (RuntimeException e) {
            costs.refund(player, cost, "items:enchant_table");
            core.msg().error(player, "Something went wrong, you were refunded.");
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        core.msg().ok(player, "Applied " + enchant.label(next) + ".");
        double xp = s.enchantXpPerLevel * next;
        core.service(SkillsApi.class).ifPresent(api -> api.addXp(player, Skill.ENCHANTING, xp, "enchant:table"));
    }

    @Override
    protected void closed(InventoryCloseEvent event) {
        returnSlots(ITEM);
    }
}
