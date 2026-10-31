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

    /** A ten segment progress bar. */
    static String bar(int progress, int total) {
        int filled = total <= 0 ? 0 : (int) Math.round(10.0 * Math.min(progress, total) / total);
        return "<green>" + "▮".repeat(filled) + "<dark_gray>" + "▮".repeat(10 - filled);
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
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        int done = data.completed.size();
        int total = engine.catalog().tutorial().size();
        boolean planned = !engine.catalog().categories().isEmpty();
        List<String> overall = new ArrayList<>(List.of(planned
            ? "<gray>The main path and the side quests,"  : "<gray>A guided line of quests that",
            planned ? "<gray>in 14 categories." : "<gray>teaches you the basics.", "",
            "<gray>Completed: <green>" + done + "<gray>/" + total, bar(done, total)));
        engine.tracked(data).ifPresent(t -> overall.add("<gray>Tracking: <yellow>" + esc(t.def().name())));
        overall.add("");
        overall.add("<yellow>Click to open!");
        gui.set(11, Guis.icon(Material.WRITABLE_BOOK, planned ? "<yellow>Quests" : "<yellow>Tutorial",
            overall.toArray(String[]::new)), click -> { if (planned) categories(player); else tutorial(player); });
        long reset = rt.config().dailyClock.millisUntilReset(java.time.Instant.now());
        int dailyDone = (int) engine.dailyQuests(data).stream().filter(d -> data.dailyDone.contains(d.id())).count();
        gui.set(13, Guis.icon(Material.CLOCK, "<aqua>Daily Quests",
            "<gray>New quests every day.", "<gray>Finish them all for a bonus.", "",
            "<gray>Today: <green>" + dailyDone + "<gray>/" + engine.dailyQuests(data).size(),
            "<gray>Resets in <yellow>" + Msg.duration(reset), "", "<yellow>Click to open!"), click -> daily(player));
        int streak = engine.liveStreak(data, quests.today());
        gui.set(15, Guis.icon(Material.BLAZE_POWDER, "<gold>Daily Streak",
            "<gray>Days in a row with every", "<gray>daily quest completed.", "",
            "<gray>Current streak: <yellow>" + streak,
            "<gray>Days completed: <yellow>" + data.dailyTotal, "",
            "<gray>Bonus per day: <gold>+" + Msg.number(rt.config().dailyStreakCoins) + " coins",
            "<gray>(up to " + rt.config().dailyStreakCap + " days)"));
        gui.set(22, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Skyblock Menu"), click -> back.accept(player));
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
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        int[] slots = gui.innerSlots();
        int main = 0;
        int side = 7;
        for (QuestCatalog.Category category : catalog.categories()) {
            int slot = category.main() ? slots[main++] : slots[side++];
            gui.set(slot, categoryIcon(data, category), click -> category(player, category.id()));
        }
        gui.set(slots[6], Guis.icon(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Main path (left, in order)",
            "<gray>Each category opens the next.", "<gray>The side quests below are played", "<gray>whenever you like."));
        gui.set(49, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Quests"), click -> open(player));
        gui.open(player);
        rt.play(player, "click");
    }

    private ItemStack categoryIcon(QuestData data, QuestCatalog.Category category) {
        QuestEngine engine = rt.engine();
        List<QuestDef> list = engine.catalog().questsOf(category.id());
        int done = (int) list.stream().filter(d -> data.completed.contains(d.id())).count();
        boolean open = category.unlock().isEmpty() || data.completed.contains(category.unlock());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + esc(category.description()));
        lore.add("");
        lore.add(category.main() ? "<gold>Main path" : "<aqua>Side quests");
        lore.add("<gray>Completed: <green>" + done + "<gray>/" + list.size());
        lore.add(bar(done, list.size()));
        lore.add("");
        if (done == list.size()) {
            lore.add("<green>Category complete!");
        } else if (!open) {
            String need = engine.catalog().tutorialQuest(category.unlock()).map(QuestDef::name).orElse(category.unlock());
            lore.add("<red>Locked. Finish first: <yellow>" + esc(need));
        } else {
            list.stream().filter(d -> engine.isActive(data, d)).findFirst()
                .ifPresent(d -> lore.add("<gray>Next: <yellow>" + esc(d.name())));
            lore.add("<yellow>Click to open!");
        }
        String color = done == list.size() ? "<green>" : open ? (category.main() ? "<gold>" : "<aqua>") : "<gray>";
        ItemStack stack = Guis.icon(category.icon(), color + esc(category.name()), lore.toArray(String[]::new));
        if (done == list.size()) {
            stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return stack;
    }

    /** One category's quests: the ones you can do now first, then the locked ones, then the finished ones. */
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
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        list.sort(java.util.Comparator.comparingInt(def -> data.completed.contains(def.id()) ? 2
            : engine.isActive(data, def) ? 0 : 1));
        gui.paginate(list, gui.innerSlots(), def -> icon(data, def, false), (click, def) -> {
            if (engine.isActive(data, def) && quests.track(player, def.id())) {
                rt.core.msg().ok(player, "Now tracking: " + def.name());
                rt.play(player, "click");
                category(player, id);
            }
        }, 45, 53);
        gui.set(49, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To the categories"), click -> categories(player));
        gui.open(player);
    }

    void tutorial(Player player) {
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        Gui gui = rt.core.guis().create(6, "<dark_gray>Tutorial Quests");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        List<QuestDef> all = new ArrayList<>(engine.catalog().tutorial());
        // active first, then locked, then completed
        all.sort(java.util.Comparator.comparingInt(def -> data.completed.contains(def.id()) ? 2
            : engine.isActive(data, def) ? 0 : 1));
        gui.paginate(all, gui.innerSlots(), def -> icon(data, def, false), (click, def) -> {
            if (engine.isActive(data, def) && quests.track(player, def.id())) {
                rt.core.msg().ok(player, "Now tracking: " + def.name());
                rt.play(player, "click");
                tutorial(player);
            }
        }, 45, 53);
        gui.set(49, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Quests"), click -> open(player));
        gui.open(player);
    }

    void daily(Player player) {
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        Gui gui = rt.core.guis().create(4, "<dark_gray>Daily Quests");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        List<QuestDef> dailies = engine.dailyQuests(data);
        int[] slots = gui.innerSlots();
        for (int i = 0; i < dailies.size() && i < slots.length; i++) {
            QuestDef def = dailies.get(i);
            gui.set(slots[i], icon(data, def, true), click -> {
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
        gui.set(31, Guis.icon(Material.CHEST, "<gold>All Done Bonus",
            "<gray>Finish every daily quest to get", "<gold>" + Msg.number(cfg.dailyBonusCoins) + " coins <gray>plus <gold>"
                + Msg.number(cfg.dailyStreakCoins) + " coins <gray>per streak day.", "",
            "<gray>Status: " + (data.dailyBonusPaid ? "<green>Claimed" : "<yellow>Not yet")));
        gui.set(27, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Quests"), click -> open(player));
        gui.open(player);
    }

    ItemStack icon(QuestData data, QuestDef def, boolean daily) {
        QuestEngine engine = rt.engine();
        boolean complete = daily ? data.dailyDone.contains(def.id()) : data.completed.contains(def.id());
        boolean active = daily ? !complete : engine.isActive(data, def);
        String name = esc(def.name());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + esc(def.description()));
        lore.add("");
        if (complete) {
            lore.addAll(rewards(def));
            lore.add("");
            lore.add("<green>Completed!");
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
        lore.add("<gray>Progress: <yellow>" + Msg.grouped(progress) + "<gray>/<yellow>" + Msg.grouped(def.amount()));
        lore.add(bar(progress, def.amount()));
        lore.add("");
        lore.addAll(rewards(def));
        lore.add("");
        String key = daily ? "daily:" + def.id() : def.id();
        boolean tracked = key.equals(data.tracked) || engine.tracked(data).map(t -> t.def().id().equals(def.id())
            && t.daily() == daily).orElse(false);
        lore.add(tracked ? "<aqua>Tracking in the sidebar" : "<yellow>Click to track in the sidebar");
        return Guis.icon(def.icon(), "<yellow>" + name, lore.toArray(String[]::new));
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
