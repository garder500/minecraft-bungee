package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.minions.MinionConfig.Fuel;
import win.vampyuyu.survival.minions.MinionConfig.Skin;
import win.vampyuyu.survival.minions.MinionConfig.Type;
import win.vampyuyu.survival.minions.MinionConfig.Upgrade;

/**
 * The minion menu. The core menu system cancels every click, so nothing is dragged in or out: storage stacks are
 * clicked to take them, and fuel, upgrades and hats are picked from a list of what the player carries.
 */
public final class MinionGui {
    private static final int[] STORAGE = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32,
        33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_COLLECT = 1;
    private static final int SLOT_COMPRESS = 2;
    private static final int SLOT_TIER = 3;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_STATUS = 5;
    private static final int SLOT_FUEL = 45;
    private static final int SLOT_UPGRADE_FIRST = 46;
    private static final int SLOT_HAT = 52;
    private static final int SLOT_PICKUP = 53;

    private final Core core;
    private final MinionManager manager;

    public MinionGui(Core core, MinionManager manager) {
        this.core = core;
        this.manager = manager;
    }

    private MinionConfig config() {
        return manager.config();
    }

    /** Opens the menu of a minion. */
    public void open(Player p, MinionData d) {
        Type type = config().types.get(d.type);
        if (type == null) {
            core.msg().error(p, "This minion type no longer exists. Ask an admin.");
            return;
        }
        MinionLogic logic = manager.logic();
        Storage storage = logic.storage(d);
        Status status = manager.statusOf(d);
        Gui gui = core.guis().create(6, "<dark_gray>" + MinionItems.minionName(type, d.tier));
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        String id = d.id;

        // ---- storage
        List<Storage.Stack> stacks = storage.stacks();
        for (int i = 0; i < STORAGE.length; i++) {
            if (i < stacks.size()) {
                Storage.Stack stack = stacks.get(i);
                gui.set(STORAGE[i], stackIcon(stack), c -> act(c.player(), id, (pl, m) -> {
                    long moved = manager.withdraw(pl, m, stack.key(), c.shift() ? Long.MAX_VALUE : stack.amount());
                    if (moved == 0) {
                        core.msg().error(pl, "Your inventory is full.");
                    }
                    return null;
                }));
            } else {
                gui.set(STORAGE[i], Guis.icon(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>Empty storage slot"));
            }
        }
        if (stacks.size() > STORAGE.length) {
            gui.set(44, Guis.icon(Material.CHEST, "<yellow>+" + (stacks.size() - STORAGE.length) + " more stacks",
                "<gray>Collect to see the rest."));
        }

        // ---- top bar
        gui.set(SLOT_COLLECT, Guis.icon(Material.HOPPER, "<green>Collect all",
            "<gray>Takes every item to your inventory.", "", "<gray>Stored: <white>" + Msg.grouped(storage.total())
            + " items", "<gray>Slots: <white>" + storage.slotsUsed() + "/" + storage.capacity()),
            c -> act(c.player(), id, (pl, m) -> {
                long moved = manager.collectAll(pl, m);
                if (moved == 0) {
                    core.msg().info(pl, "Nothing to collect, or your inventory is full.");
                } else {
                    core.msg().ok(pl, "Collected " + Msg.grouped(moved) + " items.");
                }
                return null;
            }));
        gui.set(SLOT_COMPRESS, Guis.icon(Material.PISTON, "<aqua>Compress resources",
            "<gray>Turns the raw " + MinionConfig.prettify(type.primary().item()) + " in your inventory",
            "<gray>into Enchanted items (" + config().compactRatio + " per item).", "",
            "<gray>Enchanted items pay for tier upgrades."), c -> act(c.player(), id, (pl, m) -> {
                long made = manager.compressInventory(pl, type);
                if (made == 0) {
                    core.msg().info(pl, "You need at least " + config().compactRatio + " of the resource.");
                } else {
                    core.msg().ok(pl, "Compressed into " + made + " enchanted item" + (made == 1 ? "" : "s") + ".");
                }
                return null;
            }));
        gui.set(SLOT_TIER, tierIcon(p, d, type), c -> act(c.player(), id, (pl, m) -> {
            MinionManager.Result r = manager.tierUp(pl, m);
            if (r.ok()) {
                core.msg().ok(pl, "Upgraded to " + MinionItems.minionName(type, m.tier) + ".");
            }
            return r;
        }));
        gui.set(SLOT_INFO, infoIcon(d, type, storage));
        gui.set(SLOT_STATUS, Guis.icon(status.blocked() ? Material.RED_DYE : Material.LIME_DYE,
            status.color() + status.label(), "<gray>" + status.hint(type.kind())));

        // ---- bottom bar
        Fuel fuel = logic.fuelOf(d);
        gui.set(SLOT_FUEL, fuelIcon(d, fuel), c -> core.scheduler().nextTick(() -> openFuelPicker(c.player(), id)));
        for (int i = 0; i < config().upgradeSlots; i++) {
            int slot = SLOT_UPGRADE_FIRST + i;
            Upgrade up = i < d.upgrades.size() ? config().upgrades.get(d.upgrades.get(i)) : null;
            if (up != null) {
                String upId = up.id();
                gui.set(slot, upgradeIcon(up), c -> act(c.player(), id, (pl, m) -> {
                    MinionManager.Result r = manager.removeUpgrade(pl, m, upId);
                    if (r.ok()) {
                        core.msg().ok(pl, up.name() + " removed.");
                    }
                    return r;
                }));
            } else if (i < d.upgrades.size()) {
                String stale = d.upgrades.get(i);
                gui.set(slot, Guis.icon(Material.BARRIER, "<red>Unknown upgrade", "<gray>Click to clear the slot."),
                    c -> act(c.player(), id, (pl, m) -> manager.removeUpgrade(pl, m, stale)));
            } else {
                gui.set(slot, Guis.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Upgrade slot",
                    "<yellow>Click to add an upgrade", "<gray>from your inventory."),
                    c -> core.scheduler().nextTick(() -> openUpgradePicker(c.player(), id)));
            }
        }
        Skin skin = d.skin == null ? null : config().skins.get(d.skin);
        gui.set(SLOT_HAT, skin != null ? skinIcon(skin, true)
            : Guis.icon(Material.LEATHER_HELMET, "<gray>Hat slot", "<yellow>Click to pick a hat", "<gray>from your inventory."),
            c -> core.scheduler().nextTick(() -> openHatPicker(c.player(), id)));
        gui.set(SLOT_PICKUP, Guis.icon(Material.BARRIER, "<red>Pick up", "<gray>Takes the minion back with its",
            "<gray>upgrades, hat and unused fuel.", "", "<gray>Collect the storage first."),
            c -> act(c.player(), id, (pl, m) -> {
                MinionManager.Result r = manager.pickUp(pl, m);
                if (r.ok()) {
                    core.msg().ok(pl, "Minion picked up.");
                    pl.closeInventory();
                    return null;
                }
                return r;
            }, false));
        gui.open(p);
    }

    /** Runs an action on the live minion, reports a refusal, and reopens the menu next tick. */
    private void act(Player p, String id, Action action) {
        act(p, id, action, true);
    }

    private void act(Player p, String id, Action action, boolean reopen) {
        MinionData d = manager.store().get(id).orElse(null);
        if (d == null) {
            core.msg().error(p, "That minion is gone.");
            core.scheduler().nextTick(p::closeInventory);
            return;
        }
        if (!manager.canAccess(p, d)) {
            core.msg().error(p, "This is not your minion.");
            core.scheduler().nextTick(p::closeInventory);
            return;
        }
        MinionManager.Result r = action.run(p, d);
        if (r != null && !r.ok()) {
            core.msg().error(p, r.error());
        }
        if (reopen) {
            core.scheduler().nextTick(() -> manager.store().get(id).ifPresent(m -> open(p, m)));
        }
    }

    /** What a menu click does; null result means done. */
    @FunctionalInterface
    private interface Action {
        MinionManager.Result run(Player p, MinionData d);
    }

    // ---------------------------------------------------------------- icons

    private ItemStack stackIcon(Storage.Stack stack) {
        ItemStack item = ItemKey.create(core.items(), stack.key(), stack.amount());
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(line("<yellow>Click: take this stack"));
        lore.add(line("<yellow>Shift-click: take all of it"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String mini) {
        return Msg.component(mini).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack infoIcon(MinionData d, Type type, Storage storage) {
        MinionLogic logic = manager.logic();
        MinionLogic.Speeds speeds = logic.speeds(d, type);
        Fuel fuel = logic.fuelOf(d);
        double speed = fuel != null ? speeds.fuel() : speeds.plain();
        int diameter = logic.radius(d) * 2 + 1;
        ItemStack icon = Guis.icon(Material.PAPER, config().tierRarity[d.tier - 1].color() + MinionItems.minionName(type, d.tier),
            "<gray>Kind: <white>" + prettyKind(type.kind()),
            "<gray>Time per item: <green>" + MinionItems.trim(logic.secondsPerItem(d, type)) + "s",
            "<gray>Speed: <green>" + MinionItems.trim(speed * 100) + "%",
            "<gray>Working area: <white>" + diameter + "x" + diameter,
            "<gray>Storage: <white>" + storage.slotsUsed() + "/" + storage.capacity() + " slots",
            "", "<gray>Items produced: <white>" + Msg.grouped(d.produced));
        ItemMeta meta = icon.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString("vampyuyu:minions/" + type.id());
        if (model != null) {
            meta.setItemModel(model);
        }
        icon.setItemMeta(meta);
        return icon;
    }

    private static String prettyKind(MinionConfig.Kind kind) {
        return kind.name().charAt(0) + kind.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private ItemStack tierIcon(Player p, MinionData d, Type type) {
        if (d.tier >= MinionConfig.MAX_TIER) {
            return Guis.icon(Material.NETHER_STAR, "<light_purple>Highest tier", "<gray>This minion cannot be upgraded further.");
        }
        MinionConfig.TierUp cost = config().tierUp(d.tier);
        List<String> keys = manager.tierUpKeys(type, cost.superCompacted());
        long have = manager.countIn(p, keys);
        String name = (cost.superCompacted() ? "Super " : "") + "Enchanted " + MinionConfig.prettify(type.primary().item());
        int next = d.tier + 1;
        double seconds = type.actionSeconds() * config().actionFactor[next - 1];
        return Guis.icon(Material.ANVIL, "<gold>Upgrade to tier " + MinionConfig.roman(next),
            "<gray>Time between actions: <green>" + MinionItems.trim(seconds) + "s",
            "<gray>Storage: <yellow>" + config().storageSlots(next) + " slots",
            "<gray>Working area: <yellow>" + (config().radius(next, 0) * 2 + 1) + "x" + (config().radius(next, 0) * 2 + 1),
            "", "<gray>Cost: " + (have >= cost.amount() ? "<green>" : "<red>") + cost.amount() + " " + name,
            "<gray>In your inventory: <white>" + have, "", "<yellow>Click to upgrade");
    }

    private ItemStack fuelIcon(MinionData d, Fuel fuel) {
        if (fuel == null) {
            return Guis.icon(Material.COAL, "<gray>Fuel slot", "<gray>Fuel makes the minion faster.",
                "<gray>It burns only while the minion works.", "", "<yellow>Click to add fuel", "<gray>from your inventory.");
        }
        ItemStack icon = fuel.custom() ? core.items().create(MinionItems.fuelId(fuel.id())) : new ItemStack(fuel.material());
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(line("<gray>Speed: <green>+" + MinionItems.trim(fuel.speed() * 100) + "%"));
        lore.add(line("<gray>Left: <white>" + Msg.duration(Math.round(d.fuelMs / Math.max(0.1, manager.logic().speeds(d,
            manager.config().types.get(d.type)).burn())))));
        lore.add(Component.empty());
        lore.add(line("<yellow>Click to add more fuel"));
        meta.lore(lore);
        meta.displayName(line("<white>" + fuel.name() + " <gray>(burning)"));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack upgradeIcon(Upgrade up) {
        ItemStack icon = core.items().create(MinionItems.upgradeId(up.id()));
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(line("<red>Click to remove"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack skinIcon(Skin skin, boolean installed) {
        ItemStack icon = core.items().create(MinionItems.skinId(skin.id()));
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(line(installed ? "<yellow>Click to change the hat" : "<yellow>Click to wear it"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    // ---------------------------------------------------------------- pickers

    private record Choice(ItemStack icon, Gui.ClickHandler action) {}

    private void picker(Player p, String id, String title, List<Choice> choices, String emptyHint) {
        Gui gui = core.guis().create(4, "<dark_gray>" + title);
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        int[] slots = gui.innerSlots();
        for (int i = 0; i < Math.min(choices.size(), slots.length); i++) {
            Choice choice = choices.get(i);
            gui.set(slots[i], choice.icon(), choice.action());
        }
        if (choices.isEmpty()) {
            gui.set(13, Guis.icon(Material.BARRIER, "<red>Nothing to add", "<gray>" + emptyHint));
        }
        gui.set(31, Guis.icon(Material.ARROW, "<yellow>Back", "<gray>Return to the minion."),
            c -> core.scheduler().nextTick(() -> manager.store().get(id).ifPresent(m -> open(c.player(), m))));
        gui.open(p);
    }

    private ItemStack withLore(ItemStack icon, String... extra) {
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        for (String e : extra) {
            lore.add(line(e));
        }
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private void openFuelPicker(Player p, String id) {
        MinionData d = manager.store().get(id).orElse(null);
        Type type = d == null ? null : config().types.get(d.type);
        if (d == null || type == null || !manager.canAccess(p, d)) {
            return;
        }
        List<Choice> choices = new ArrayList<>();
        for (Fuel fuel : config().fuels.values()) {
            long have = manager.countIn(p, List.of(fuel.key()));
            if (have > 0 && fuel.accepts(type.kind())) {
                ItemStack icon = fuel.custom() ? core.items().create(MinionItems.fuelId(fuel.id())) : new ItemStack(fuel.material());
                if (!fuel.custom()) {
                    ItemMeta meta = icon.getItemMeta();
                    meta.displayName(line("<white>" + fuel.name()));
                    meta.lore(List.of(line("<gray>Minion speed: <green>+" + MinionItems.trim(fuel.speed() * 100) + "%"),
                        line("<gray>Lasts: <green>" + MinionItems.trim(fuel.hours()) + " hours")));
                    icon.setItemMeta(meta);
                }
                choices.add(new Choice(withLore(icon, "<gray>You have: <white>" + have, "<yellow>Click: add one",
                    "<yellow>Shift-click: add as many as fit"), c -> act(c.player(), id, (pl, m) -> {
                        MinionManager.Result r = manager.addFuel(pl, m, fuel, c.shift());
                        if (r.ok()) {
                            core.msg().ok(pl, fuel.name() + " added.");
                        }
                        return r;
                    })));
            }
        }
        picker(p, id, "Add fuel", choices, "Carry fuel such as Coal in your inventory.");
    }

    private void openUpgradePicker(Player p, String id) {
        MinionData d = manager.store().get(id).orElse(null);
        if (d == null || !manager.canAccess(p, d)) {
            return;
        }
        List<Choice> choices = new ArrayList<>();
        for (Upgrade up : config().upgrades.values()) {
            long have = manager.countIn(p, List.of(up.key()));
            if (have > 0 && !d.upgrades.contains(up.id())) {
                choices.add(new Choice(withLore(core.items().create(MinionItems.upgradeId(up.id())),
                    "<gray>You have: <white>" + have, "<yellow>Click to install"), c -> act(c.player(), id, (pl, m) -> {
                        MinionManager.Result r = manager.installUpgrade(pl, m, up);
                        if (r.ok()) {
                            core.msg().ok(pl, up.name() + " installed.");
                        }
                        return r;
                    })));
            }
        }
        picker(p, id, "Add an upgrade", choices, "Carry an upgrade item in your inventory.");
    }

    private void openHatPicker(Player p, String id) {
        MinionData d = manager.store().get(id).orElse(null);
        if (d == null || !manager.canAccess(p, d)) {
            return;
        }
        List<Choice> choices = new ArrayList<>();
        if (d.skin != null) {
            choices.add(new Choice(Guis.icon(Material.BARRIER, "<red>Remove the hat", "<gray>The hat goes back to your inventory."),
                c -> act(c.player(), id, (pl, m) -> manager.setSkin(pl, m, null))));
        }
        for (Skin skin : config().skins.values()) {
            long have = manager.countIn(p, List.of(skin.key()));
            if (have > 0 && !skin.id().equals(d.skin)) {
                choices.add(new Choice(skinIcon(skin, false), c -> act(c.player(), id, (pl, m) -> manager.setSkin(pl, m, skin))));
            }
        }
        picker(p, id, "Pick a hat", choices, "Carry a hat item in your inventory.");
    }
}
