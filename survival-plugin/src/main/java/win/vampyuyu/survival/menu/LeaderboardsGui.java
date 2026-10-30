package win.vampyuyu.survival.menu;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;

/**
 * Menu-owned leaderboards hub: one button per leaderboard command that another feature registered
 * ({@code commands.baltop}, {@code commands.skilltop}). A board whose command does not exist is greyed out.
 */
final class LeaderboardsGui {
    private final MenuRuntime rt;
    private Consumer<Player> back = p -> { };
    private BiConsumer<Player, String> run = (p, command) -> p.performCommand(command);

    LeaderboardsGui(MenuRuntime rt) {
        this.rt = rt;
    }

    void backTo(Consumer<Player> back) {
        this.back = back;
    }

    /** How a linked feature's command is run for the player (counts for quests in the main menu's runner). */
    void runWith(BiConsumer<Player, String> run) {
        this.run = run;
    }

    void open(Player player) {
        Gui gui = rt.core.guis().create(3, "<dark_gray>Leaderboards");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        board(gui, player, 11, "baltop", Material.GOLD_INGOT, "Richest Players", "The players with the most coins.");
        board(gui, player, 15, "skilltop", Material.EXPERIENCE_BOTTLE, "Skill Leaders", "The highest skill levels.");
        gui.set(22, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Skyblock Menu"), click -> back.accept(player));
        gui.open(player);
        rt.play(player, "click");
    }

    private void board(Gui gui, Player player, int slot, String entry, Material icon, String name, String about) {
        Optional<String> command = rt.commandFor(entry, player);
        ItemStack item = command.isPresent()
            ? Guis.icon(icon, "<green>" + name, "<gray>" + about, "", "<yellow>Click to view!")
            : Guis.icon(Material.GRAY_DYE, "<gray>" + name + " <dark_gray>(unavailable)", "<red>Not available right now.");
        gui.set(slot, item, click -> command.ifPresent(c -> {
            click.close();
            run.accept(player, c);
        }));
    }
}
