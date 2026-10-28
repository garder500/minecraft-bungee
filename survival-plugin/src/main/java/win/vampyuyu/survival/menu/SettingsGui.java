package win.vampyuyu.survival.menu;

import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;

/** Per-player preferences, stored in the profile section {@code menu}. */
final class SettingsGui {
    /** A toggleable preference. */
    enum Setting {
        SIDEBAR("Sidebar", Material.OAK_SIGN, "Show the Skyblock sidebar with the date,", "your location and your purse.") {
            boolean get(MenuData d) { return d.sidebar; }
            void set(MenuData d, boolean v) { d.sidebar = v; }
        },
        QUEST_TRACKER("Quest Tracker", Material.WRITABLE_BOOK, "Show your tracked quest on the sidebar.", "") {
            boolean get(MenuData d) { return d.questTracker; }
            void set(MenuData d, boolean v) { d.questTracker = v; }
        },
        DAMAGE_INDICATORS("Damage Indicators", Material.IRON_SWORD, "Show floating damage numbers when you hit", "something.") {
            boolean get(MenuData d) { return d.damageIndicators; }
            void set(MenuData d, boolean v) { d.damageIndicators = v; }
        },
        SOUNDS("Menu Sounds", Material.NOTE_BLOCK, "Play sounds for menus, quests and", "fairy souls.") {
            boolean get(MenuData d) { return d.sounds; }
            void set(MenuData d, boolean v) { d.sounds = v; }
        };

        final String title;
        final Material icon;
        final String[] about;

        Setting(String title, Material icon, String... about) {
            this.title = title;
            this.icon = icon;
            this.about = about;
        }

        abstract boolean get(MenuData data);

        abstract void set(MenuData data, boolean value);

        /** Command argument, e.g. {@code quest-tracker}. */
        String key() {
            return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    private final MenuRuntime rt;
    private final Consumer<Player> onChange;
    private Consumer<Player> back = p -> { };

    /** @param onChange called after a preference changed (the sidebar redraws at once) */
    SettingsGui(MenuRuntime rt, Consumer<Player> onChange) {
        this.rt = rt;
        this.onChange = onChange;
    }

    void backTo(Consumer<Player> back) {
        this.back = back;
    }

    /** Flips a setting and returns its new value. */
    boolean toggle(Player player, Setting setting) {
        MenuData data = rt.prefs(player.getUniqueId());
        boolean value = !setting.get(data);
        setting.set(data, value);
        rt.dirty(player.getUniqueId());
        onChange.accept(player);
        return value;
    }

    void open(Player player) {
        Gui gui = rt.core.guis().create(3, "<dark_gray>Settings");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        render(gui, player);
        gui.set(22, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Skyblock Menu"), click -> back.accept(player));
        gui.open(player);
        rt.play(player, "click");
    }

    private void render(Gui gui, Player player) {
        MenuData data = rt.prefs(player.getUniqueId());
        int slot = 10;
        for (Setting setting : Setting.values()) {
            boolean on = setting.get(data);
            ItemStack icon = Guis.icon(on ? setting.icon : Material.GRAY_DYE,
                (on ? "<green>" : "<gray>") + setting.title,
                "<gray>" + setting.about[0], setting.about.length > 1 && !setting.about[1].isEmpty()
                    ? "<gray>" + setting.about[1] : " ",
                "", "<gray>Status: " + (on ? "<green>ON" : "<red>OFF"), "", "<yellow>Click to toggle!");
            gui.set(slot, icon, click -> {
                toggle(player, setting);
                rt.play(player, "click");
                render(gui, player);
            });
            slot += 2;
        }
    }
}
