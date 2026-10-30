package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The warps menu: hub, island, Nether, End, boss arena hints and the island feature's public warps. */
final class WarpsGui {
    private final MenuRuntime rt;
    private final WarpService warps;
    private Consumer<Player> back = p -> { };
    private BiConsumer<Player, String> run = (p, command) -> p.performCommand(command);

    WarpsGui(MenuRuntime rt, WarpService warps) {
        this.rt = rt;
        this.warps = warps;
    }

    void backTo(Consumer<Player> back) {
        this.back = back;
    }

    /** How a linked feature's command is run for the player (the main menu's runner also counts it for quests). */
    void runWith(BiConsumer<Player, String> run) {
        this.run = run;
    }

    private static String esc(String text) {
        return MiniMessage.miniMessage().escapeTags(text);
    }

    void open(Player player) {
        Gui gui = rt.core.guis().create(4, "<dark_gray>Warps");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        Optional<String> blocked = warps.blocked(player);
        String status = blocked.map(b -> "<red>" + b).orElse("<yellow>Click to warp!");
        boolean hasIsland = rt.islands().flatMap(api -> api.islandOf(player.getUniqueId())).isPresent();

        gui.set(10, rt.islands().isPresent()
            ? Guis.icon(Material.BELL, "<green>Hub", "<gray>The central village with shops,", "<gray>quests and fairy souls.", "", status)
            : unavailable("Hub"), click -> go(click, WarpService.Target.HUB));
        gui.set(11, !rt.islands().isPresent() ? unavailable("My Island")
            : hasIsland ? Guis.icon(Material.GRASS_BLOCK, "<green>My Island", "<gray>Your own floating island.", "", status)
            : Guis.icon(Material.GRAY_DYE, "<gray>My Island <dark_gray>(none yet)", "<gray>Create one from the hub!"),
            click -> go(click, WarpService.Target.ISLAND));
        gui.set(12, Bukkit.getWorld(SkyblockWorlds.NETHER) != null
            ? Guis.icon(Material.NETHERRACK, "<red>The Nether", "<gray>Dangerous mobs and rare drops.", "", status)
            : unavailable("The Nether"), click -> go(click, WarpService.Target.NETHER));
        gui.set(13, Bukkit.getWorld(SkyblockWorlds.END) != null
            ? Guis.icon(Material.END_STONE, "<light_purple>The End", "<gray>Endermen and the way to the", "<gray>end bosses.", "", status)
            : unavailable("The End"), click -> go(click, WarpService.Target.END));
        gui.set(14, bossIcon());
        boolean islandCommands = rt.commandFor("island", player).isPresent();
        gui.set(15, islandCommands
            ? Guis.icon(Material.SPYGLASS, "<aqua>Public Islands",
                "<gray>Visit islands that their", "<gray>owners opened to everyone.", "", "<yellow>Click to browse!")
            : unavailable("Public Islands"), click -> islandCommand(click, "warp"));
        gui.set(16, islandCommands
            ? Guis.icon(Material.PLAYER_HEAD, "<green>Visit a Player",
                "<gray>Visit the island of a friend", "<gray>who invited you.", "",
                "<gray>Type <yellow>/island visit <player>", "", "<yellow>Click to get the command!")
            : unavailable("Visit a Player"), click -> {
                click.close();
                rt.core.scheduler().nextTick(() -> player.sendMessage(rt.core.msg().prefix().append(
                    Component.text("Click here to visit a player: /island visit ", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.suggestCommand("/island visit ")))));
            });
        gui.set(31, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Skyblock Menu"), click -> back.accept(player));
        gui.open(player);
        rt.play(player, "click");
    }

    private ItemStack unavailable(String name) {
        return Guis.icon(Material.GRAY_DYE, "<gray>" + name + " <dark_gray>(unavailable)", "<red>Not available right now.");
    }

    private void go(Gui.Click click, WarpService.Target target) {
        Player player = click.player();
        click.close();
        rt.core.scheduler().nextTick(() -> warps.warp(player, target));
    }

    private ItemStack bossIcon() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Powerful bosses guard great loot.");
        MenuConfig cfg = rt.config();
        if (!cfg.bossHint.isBlank()) {
            lore.add("<gray>" + esc(cfg.bossHint));
        }
        lore.add("");
        Optional<BossApi> bosses = rt.core.service(BossApi.class);
        if (bosses.isPresent()) {
            Collection<String> ids = bosses.get().bossIds();
            lore.add("<gray>Known bosses: <red>" + ids.size());
            ids.stream().sorted().limit(8).forEach(id -> lore.add("<dark_gray> - <red>" + esc(id)));
            if (ids.size() > 8) {
                lore.add("<dark_gray>   and " + (ids.size() - 8) + " more");
            }
        } else {
            lore.add("<dark_gray>Bosses are not available right now.");
        }
        return Guis.icon(Material.WITHER_SKELETON_SKULL, "<red>Boss Arenas", lore.toArray(String[]::new));
    }

    /** Runs {@code /island <sub>} as the player (the island feature owns public warps and visits). */
    private void islandCommand(Gui.Click click, String sub) {
        Player player = click.player();
        click.close();
        Optional<String> command = rt.commandFor("island", player);
        if (command.isEmpty()) {
            rt.core.msg().error(player, "Islands are not available right now.");
            return;
        }
        run.accept(player, command.get() + " " + sub);
    }
}
