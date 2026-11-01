package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/** The /quests menus: overview, tutorial line and daily quests. */
final class QuestsGui {
    private final MenuRuntime rt;
    private final QuestService quests;
    private Consumer<Player> back = p -> { };

    QuestsGui(MenuRuntime rt, QuestService quests) {
        this.rt = rt;
        this.quests = quests;
    }

    void backTo(Consumer<Player> back) {
        this.back = back;
    }

    /** Slots of the main path, a serpentine route over the panel (each category opens the next one). */
    static final int[] PATH = {10, 11, 12, 13, 14, 15, 16, 25, 24, 23, 22, 21, 20, 19, 28, 29, 30, 31, 32, 33, 34};
    /** Slots of the side categories: a row of their own under the path. */
    static final int[] SIDE = {37, 38, 39, 40, 41, 42, 43};

    /** Compact progress text, {@code 12/30 · 40%}. */
    static String bar(int progress, int total) {
        return "<gray>" + Guis.progress(progress, total);
    }

    private static String esc(String text) {
        return MiniMessage.miniMessage().escapeTags(text);
    }

    void open(Player player) {
        if (!rt.config().questsEnabled) {
            rt.core.msg().warn(player, "Quests are switched off on this server.");
            return;
        }
        quests.sync(player);
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        Gui gui = rt.core.guis().create(3, "<dark_gray>Quests");
        int done = engine.completedCount(data);
        int total = engine.catalog().tutorial().size();
        boolean planned = !engine.catalog().categories().isEmpty();
        String tracking = engine.tracked(data).map(t -> " · tracking " + esc(t.def().name())).orElse("");
        gui.set(11, Guis.menuButton(Material.WRITABLE_BOOK, "quests", planned ? "<gold>Quests" : "<gold>Tutorial",
            (planned ? "Main path and side quests " : "A guided line of quests ") + Guis.progress(done, total) + tracking,
            "Open"), click -> { if (planned) categories(player); else tutorial(player); });
        long reset = rt.config().dailyClock.millisUntilReset(java.time.Instant.now());
        int dailyDone = (int) engine.dailyQuests(data).stream().filter(d -> data.dailyDone.contains(d.id())).count();
        gui.set(13, Guis.button(Material.CLOCK, "<aqua>Daily Quests", "Today " + dailyDone + "/"
            + engine.dailyQuests(data).size() + " · resets in " + Msg.duration(reset), "Open"), click -> daily(player));
        int streak = engine.liveStreak(data, quests.today());
        gui.set(15, Guis.icon(Material.BLAZE_POWDER, "<gold>Daily Streak",
            "<gray>Streak " + streak + " days · " + data.dailyTotal + " days completed",
            "<gray>Bonus per day: <gold>+" + Msg.number(rt.config().dailyStreakCoins) + " coins <gray>(up to "
                + rt.config().dailyStreakCap + " days)"));
        gui.back("To the Isles", click -> back.accept(player));
        gui.open(player);
        rt.play(player, "click");
    }

    /** The category overview: the main path first, then the quests you can play in parallel. */
    void categories(Player player) {
        quests.sync(player);
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        QuestCatalog catalog = engine.catalog();
        Gui gui = rt.core.guis().create(6, "<dark_gray>Quests");
        int main = 0;
        int side = 0;
        for (QuestCatalog.Category category : catalog.categories()) {
            int slot = category.main() ? PATH[Math.min(main++, PATH.length - 1)] : SIDE[Math.min(side++, SIDE.length - 1)];
            gui.set(slot, categoryIcon(data, category, category.main() ? main : side), click -> category(player, category.id()));
        }
        gui.back("To Quests", click -> open(player));
        gui.open(player);
        rt.play(player, "click");
    }

    private ItemStack categoryIcon(QuestData data, QuestCatalog.Category category, int step) {
        QuestEngine engine = rt.engine();
        List<QuestDef> list = engine.catalog().questsOf(category.id());
        int done = (int) list.stream().filter(d -> data.completed.contains(d.id())).count();
        boolean open = category.unlock().isEmpty() || data.completed.contains(category.unlock());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + esc(category.description()));
        lore.add(bar(done, list.size()) + (category.main() ? " · step " + step + " of the main path" : " · side quests"));
        if (done == list.size()) {
            lore.add("<green>Category complete");
        } else if (!open) {
            String need = engine.catalog().tutorialQuest(category.unlock()).map(QuestDef::name).orElse(category.unlock());
            lore.add("<red>Locked. Finish first: <yellow>" + esc(need));
        } else {
            list.stream().filter(d -> engine.isActive(data, d)).findFirst()
                .ifPresent(d -> lore.add("<gray>Next: <yellow>" + esc(d.name())));
            lore.add(Guis.ACCENT + "▸ Open");
        }
        String color = done == list.size() ? "<green>" : open ? (category.main() ? "<gold>" : "<aqua>") : "<gray>";
        ItemStack stack = Guis.icon(category.icon(), color + esc(category.name()), lore.toArray(String[]::new));
        if (done == list.size()) {
            stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return stack;
    }

    /** One category's quests in their order, each with its done, active or locked icon. */
    void category(Player player, String id) {
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        QuestCatalog.Category category = engine.catalog().category(id).orElse(null);
        if (category == null) {
            categories(player);
            return;
        }
        List<QuestDef> list = new ArrayList<>(engine.catalog().questsOf(id));
        int done = (int) list.stream().filter(d -> data.completed.contains(d.id())).count();
        Gui gui = rt.core.guis().create(6, "<dark_gray>" + esc(category.name()) + " " + done + "/" + list.size());
        gui.paginate(list, gui.innerSlots(), def -> icon(player, data, def, false), (click, def) -> {
            if (click.right() && handIn(player, def)) {
                category(player, id);
                return;
            }
            if (engine.isActive(data, def) && quests.track(player, def.id())) {
                rt.core.msg().ok(player, "Now tracking: " + def.name());
                rt.play(player, "click");
                category(player, id);
            }
        });
        gui.back("To the categories", click -> categories(player));
        gui.open(player);
    }

    void tutorial(Player player) {
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        Gui gui = rt.core.guis().create(6, "<dark_gray>Tutorial Quests");
        List<QuestDef> all = new ArrayList<>(engine.catalog().tutorial());
        // active first, then locked, then completed
        all.sort(java.util.Comparator.comparingInt(def -> data.completed.contains(def.id()) ? 2
            : engine.isActive(data, def) ? 0 : 1));
        gui.paginate(all, gui.innerSlots(), def -> icon(player, data, def, false), (click, def) -> {
            if (click.right() && handIn(player, def)) {
                tutorial(player);
                return;
            }
            if (engine.isActive(data, def) && quests.track(player, def.id())) {
                rt.core.msg().ok(player, "Now tracking: " + def.name());
                rt.play(player, "click");
                tutorial(player);
            }
        });
        gui.back("To Quests", click -> open(player));
        gui.open(player);
    }

    void daily(Player player) {
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        Gui gui = rt.core.guis().create(6, "<dark_gray>Daily Quests");
        List<QuestDef> dailies = engine.dailyQuests(data);
        int[] slots = gui.innerSlots();
        for (int i = 0; i < dailies.size() && i < slots.length; i++) {
            QuestDef def = dailies.get(i);
            gui.set(slots[i], icon(player, data, def, true), click -> {
                if (!data.dailyDone.contains(def.id()) && quests.track(player, "daily:" + def.id())) {
                    rt.core.msg().ok(player, "Now tracking: " + def.name());
                    rt.play(player, "click");
                    daily(player);
                }
            });
        }
        if (dailies.isEmpty()) {
            gui.set(13, Guis.icon(Material.BARRIER, "<red>No daily quests", "<gray>None are configured."));
        }
        MenuConfig cfg = rt.config();
        gui.set(22, Guis.icon(Material.CHEST, "<gold>All Done Bonus",
            "<gray>Every daily quest: <gold>" + Msg.number(cfg.dailyBonusCoins) + " coins <gray>plus <gold>"
                + Msg.number(cfg.dailyStreakCoins) + " coins <gray>per streak day.",
            data.dailyBonusPaid ? "<green>Claimed" : "<yellow>Not yet"));
        gui.back("To Quests", click -> open(player));
        gui.open(player);
    }

    ItemStack icon(Player player, QuestData data, QuestDef def, boolean daily) {
        QuestEngine engine = rt.engine();
        boolean complete = daily ? data.dailyDone.contains(def.id()) : data.completed.contains(def.id());
        boolean active = daily ? !complete : engine.isActive(data, def);
        String name = esc(def.name());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + esc(def.description()));
        if (complete) {
            lore.addAll(rewards(def));
            lore.add("<green>Completed");
            ItemStack stack = Guis.icon(def.icon(), "<green>✔ " + name, lore.toArray(String[]::new));
            stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return stack;
        }
        if (!active) {
            List<String> needs = new ArrayList<>();
            for (String id : def.requires()) {
                if (!data.completed.contains(id)) {
                    needs.add(esc(rt.engine().catalog().tutorialQuest(id).map(QuestDef::name).orElse(id)));
                }
            }
            return Guis.icon(Material.GRAY_DYE, "<gray>" + name + " <dark_gray>(locked)", "<gray>Requires: <red>"
                + String.join("<gray>, <red>", needs));
        }
        int progress = engine.progress(data, def, daily);
        lore.add(bar(progress, def.amount()));
        lore.addAll(rewards(def));
        String key = daily ? "daily:" + def.id() : def.id();
        boolean tracked = key.equals(data.tracked) || engine.tracked(data).map(t -> t.def().id().equals(def.id())
            && t.daily() == daily).orElse(false);
        int handIn = daily ? 0 : quests.handInAvailable(player, def);
        if (handIn > 0) {
            lore.add(Guis.ACCENT + "▸ Right-click: hand in " + handIn + " from your inventory");
        }
        lore.add(tracked ? "<aqua>Tracking in the sidebar" : Guis.ACCENT + "▸ Track");
        return Guis.icon(def.icon(), "<yellow>" + name, lore.toArray(String[]::new));
    }

    private boolean handIn(Player player, QuestDef def) {
        int taken = quests.handIn(player, def);
        if (taken <= 0) {
            return false;
        }
        rt.core.msg().ok(player, "Handed in " + taken + " for " + def.name() + ".");
        rt.play(player, "click");
        return true;
    }

    private List<String> rewards(QuestDef def) {
        QuestDef.Rewards r = def.rewards();
        List<String> lines = new ArrayList<>();
        if (r.isEmpty()) {
            return lines;
        }
        lines.add("<gray>Rewards:");
        if (r.coins() > 0) {
            lines.add(" <gold>+" + Msg.number(r.coins()) + " coins");
        }
        r.skillXp().forEach((skill, xp) -> lines.add(" <aqua>+" + Msg.number(xp) + " " + titleCase(skill) + " XP"));
        r.items().forEach((id, amount) -> lines.add(" <green>" + amount + "x " + esc(rt.core.items().def(id)
            .map(d -> d.name()).orElse(id))));
        return lines;
    }

    private static String titleCase(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
