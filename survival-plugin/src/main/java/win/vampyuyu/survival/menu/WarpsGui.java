package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The warps menu: hub, island, Nether, End, boss arena hints and public islands. */
final class WarpsGui {
    private final MenuRuntime rt;
    private final WarpService warps;
    private Consumer<Player> back = p -> { };

    WarpsGui(MenuRuntime rt, WarpService warps) {
        this.rt = rt;
        this.warps = warps;
    }

    void backTo(Consumer<Player> back) {
        this.back = back;
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
        gui.set(15, Guis.icon(Material.SPYGLASS, "<aqua>Public Islands",
            "<gray>Visit islands that their", "<gray>owners opened to everyone.", "",
            "<gray>Open islands: <aqua>" + warps.publicWarps().list().size(), "", "<yellow>Click to browse!"),
            click -> publicIslands(player));
        boolean open = warps.publicWarps().isPublic(player.getUniqueId());
        gui.set(16, !hasIsland ? Guis.icon(Material.GRAY_DYE, "<gray>Open My Island", "<gray>You need an island first.")
            : Guis.icon(open ? Material.LIME_DYE : Material.GRAY_DYE,
                open ? "<green>My Island is Public" : "<gray>My Island is Private",
                "<gray>Let anyone visit your island", "<gray>from the Public Islands list.", "",
                "<yellow>Click to " + (open ? "close" : "open") + " it!"), click -> {
                    if (open) {
                        warps.publicWarps().unpublish(player.getUniqueId());
                        rt.core.msg().ok(player, "Your island is private again.");
                    } else if (rt.islands().flatMap(api -> api.islandOf(player.getUniqueId())).isPresent()) {
                        warps.publicWarps().publish(player.getUniqueId(), player.getName(), "");
                        rt.core.msg().ok(player, "Your island is now listed in Public Islands.");
                    }
                    open(player);
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

    void publicIslands(Player player) {
        Gui gui = rt.core.guis().create(5, "<dark_gray>Public Islands");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        List<Map.Entry<UUID, PublicWarps.Entry>> list = warps.publicWarps().list();
        gui.paginate(list, gui.innerSlots(), entry -> islandIcon(entry.getKey(), entry.getValue()), (click, entry) -> {
            click.close();
            rt.core.scheduler().nextTick(() -> warps.visit(player, entry.getKey()));
        }, 36, 44);
        gui.set(40, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Warps"), click -> open(player));
        if (list.isEmpty()) {
            gui.set(22, Guis.icon(Material.BARRIER, "<red>No public islands yet",
                "<gray>Open your own island from the", "<gray>Warps menu or with <yellow>/sbwarps public on<gray>."));
        }
        gui.open(player);
    }

    private ItemStack islandIcon(UUID owner, PublicWarps.Entry entry) {
        List<String> lore = new ArrayList<>();
        if (!entry.note.isBlank()) {
            lore.add("<gray>" + esc(entry.note));
            lore.add("");
        }
        Optional<IslandInfo> island = rt.islands().flatMap(api -> api.islandOf(owner));
        island.ifPresent(i -> {
            long online = i.members().stream().filter(m -> Bukkit.getPlayer(m) != null).count();
            lore.add("<gray>Members online: <green>" + online + "<gray>/" + i.members().size());
        });
        lore.add("");
        lore.add("<yellow>Click to visit!");
        ItemStack head = Guis.icon(Material.PLAYER_HEAD, "<green>" + esc(entry.name) + "<gray>'s Island",
            lore.toArray(String[]::new));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            head.setItemMeta(meta);
        }
        return head;
    }
}
