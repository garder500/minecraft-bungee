package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.island.IslandAccess.Cap;
import win.vampyuyu.survival.island.IslandConfig.Tier;
import win.vampyuyu.survival.island.IslandService.Upgrade;

/** Every island menu. Tooltips only (no pack art); a click that changes state re-opens the menu with fresh data. */
public final class IslandMenus {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final ItemStack FILLER = Guis.icon(Material.GRAY_STAINED_GLASS_PANE, "<gray> ");

    private final IslandService service;
    private final IslandConfig cfg;
    private final IslandRegistry registry;

    public IslandMenus(IslandService service) {
        this.service = service;
        this.cfg = service.config();
        this.registry = service.registry();
    }

    // ---------------------------------------------------------------- helpers

    /** Escapes player-controlled text for MiniMessage. */
    static String esc(String text) {
        return MINI.escapeTags(text == null ? "" : text);
    }

    private void reply(Player player, Result result) {
        if (result.ok()) {
            service.core().msg().ok(player, result.message());
        } else {
            service.core().msg().error(player, result.message());
        }
    }

    private Gui gui(int rows, String title) {
        Gui gui = service.core().guis().create(rows, title);
        gui.border(FILLER);
        return gui;
    }

    private ItemStack head(UUID owner, String name, String... lore) {
        ItemStack item = Guis.icon(Material.PLAYER_HEAD, name, lore);
        try {
            if (item.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                item.setItemMeta(meta);
            }
        } catch (RuntimeException e) {
            // a plain head is fine
        }
        return item;
    }

    private void back(Gui gui, int slot, Consumer<Player> target) {
        gui.set(slot, Guis.icon(Material.ARROW, "<yellow>Back"), click -> target.accept(click.player()));
    }

    private Island islandOf(Player player) {
        Island here = service.islandAtLocation(player.getLocation());
        return here != null && here.roleOf(player.getUniqueId()).isCoop() ? here
            : registry.ofPlayer(player.getUniqueId()).orElse(null);
    }

    private static String onOff(boolean value) {
        return value ? "<green>ON" : "<red>OFF";
    }

    // ---------------------------------------------------------------- main menu

    public void main(Player player) {
        Island island = islandOf(player);
        if (island == null) {
            noIsland(player);
            return;
        }
        UUID id = player.getUniqueId();
        IslandRole role = island.roleOf(id);
        Gui gui = gui(5, "<dark_aqua>Your island");
        gui.set(10, Guis.icon(Material.ENDER_PEARL, "<aqua>Island home",
            "<gray>Left-click: teleport home", "<gray>Right-click: set home here", ""
                + "<dark_gray>Owner and co-owners can set the home"), click -> {
                    if (click.right()) {
                        reply(click.player(), service.setHome(click.player()));
                    } else {
                        Result result = service.goHome(click.player());
                        if (!result.ok()) {
                            service.core().msg().error(click.player(), result.message());
                        }
                    }
                    click.close();
                });
        gui.set(12, Guis.icon(Material.COMPARATOR, "<gold>Settings", "<gray>Protection flags, island time", "<gray>and locking"),
            click -> settings(click.player()));
        gui.set(14, Guis.icon(Material.EMERALD, "<green>Upgrades", "<gray>Island radius and member slots"),
            click -> upgrades(click.player()));
        gui.set(16, Guis.icon(Material.PLAYER_HEAD, "<yellow>Members",
            "<gray>" + island.size() + "/" + cfg.memberSlotsFor(island) + " co-op members",
            "<gray>Trusted guests: " + island.trusted.size(), "<gray>Banned: " + island.banned.size()),
            click -> members(click.player()));
        gui.set(19, Guis.icon(Material.EXPERIENCE_BOTTLE, "<light_purple>Island level",
            "<gray>Level: <white>" + island.level, "<gray>Value: <white>" + Msg.number(island.value),
            "<gray>Rank: <white>" + rankLabel(island), "", "<yellow>Click to recalculate"), click -> {
                reply(click.player(), service.recalculate(island, true, done ->
                    service.core().msg().ok(click.player(), "Island level: " + done.level)));
                click.close();
            });
        gui.set(21, Guis.icon(Material.OAK_SIGN, "<aqua>Public warps", "<gray>Browse islands that opened a warp"),
            click -> warps(click.player()));
        gui.set(23, Guis.icon(Material.GOLDEN_HELMET, "<gold>Top islands", "<gray>The island leaderboard"),
            click -> top(click.player()));
        boolean canLock = IslandAccess.has(role, Cap.LOCK);
        gui.set(25, Guis.icon(island.locked ? Material.IRON_DOOR : Material.OAK_DOOR,
            "<white>Island access: " + (island.locked ? "<red>Locked" : "<green>Open"),
            island.locked ? "<gray>Only members and trusted guests can enter" : "<gray>Anyone who is not banned can visit",
            canLock ? "<yellow>Click to toggle" : "<dark_gray>Owner and co-owners only"), click -> {
                reply(click.player(), service.setLocked(click.player(), !island.locked));
                main(click.player());
            });
        boolean visible = service.data(id).borderVisible;
        gui.set(28, Guis.icon(Material.BARRIER, "<white>Island border: " + onOff(visible),
            "<gray>Show the blue border at the island edge", "<yellow>Click to toggle"), click -> {
                service.toggleBorder(click.player());
                main(click.player());
            });
        gui.set(32, Guis.icon(Material.LECTERN, "<aqua>Public warp",
            island.warp == null ? "<gray>No warp set" : "<gray>Warp: <white>" + esc(island.warp.name),
            "<gray>Left-click: set the warp where you stand", "<gray>Right-click: remove the warp"), click -> {
                reply(click.player(), click.right() ? service.removeWarp(click.player())
                    : service.setWarp(click.player(), null));
                click.close();
            });
        if (role == IslandRole.OWNER) {
            gui.set(34, Guis.icon(Material.TNT, "<red>Reset island", "<gray>Wipe everything and start again",
                "<gray>Cooldown: " + cfg.resetCooldownMinutes + " min"), click -> resetFlow(click.player()));
        } else {
            gui.set(34, Guis.icon(Material.RED_BED, "<red>Leave island", "<gray>Leave this co-op"),
                click -> confirm(click.player(), "<red>Leave the island?", List.of("<gray>You will lose access."),
                    () -> {
                        service.leave(click.player()); // arms
                        reply(click.player(), service.leave(click.player()));
                    }));
        }
        gui.set(40, Guis.icon(Material.BARRIER, "<red>Close"), click -> click.close());
        gui.open(player);
    }

    private String rankLabel(Island island) {
        List<Island> ranked = service.top(Math.max(1, registry.count()));
        int index = ranked.indexOf(island);
        return index < 0 ? "unranked" : "#" + (index + 1);
    }

    private void noIsland(Player player) {
        Gui gui = gui(3, "<dark_aqua>Skyblock islands");
        int invites = service.invites().pending(player.getUniqueId()).size();
        gui.set(11, Guis.icon(Material.GRASS_BLOCK, "<green>Create your island",
            "<gray>Pick a starting island type"), click -> templates(click.player(), choice -> {
                reply(click.player(), service.create(click.player(), choice));
                click.player().closeInventory();
            }, "<green>Pick your island"));
        gui.set(13, Guis.icon(Material.OAK_SIGN, "<aqua>Public warps", "<gray>Visit other islands"),
            click -> warps(click.player()));
        gui.set(15, Guis.icon(Material.GOLDEN_HELMET, "<gold>Top islands", "<gray>The island leaderboard"),
            click -> top(click.player()));
        if (invites > 0) {
            gui.set(22, Guis.icon(Material.WRITABLE_BOOK, "<yellow>Invitations (" + invites + ")",
                "<gray>Click to accept the latest one"), click -> {
                    reply(click.player(), service.accept(click.player(), null));
                    click.close();
                });
        }
        gui.open(player);
    }

    // ---------------------------------------------------------------- templates

    /** The island type picker; {@code choose} receives the template id. */
    public void templates(Player player, Consumer<String> choose, String title) {
        List<IslandConfig.TemplateCfg> list = service.templates();
        int rows = 3;
        Gui gui = gui(rows, title);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            IslandConfig.TemplateCfg t = list.get(i);
            List<String> lore = new ArrayList<>();
            for (String line : t.description()) {
                lore.add("<gray>" + esc(line));
            }
            lore.add("");
            lore.add("<yellow>Click to choose");
            gui.set(slots[i], Guis.icon(t.icon(), "<green>" + esc(t.name()), lore.toArray(new String[0])),
                click -> choose.accept(t.id()));
        }
        gui.set(22, Guis.icon(Material.BARRIER, "<red>Cancel"), click -> click.close());
        gui.open(player);
    }

    private void resetFlow(Player player) {
        Result check = service.checkReset(player);
        if (!check.ok()) {
            reply(player, check);
            return;
        }
        templates(player, choice -> confirm(player, "<red>Reset your island?",
            List.of("<gray>Everything on the island is destroyed", "<gray>and rebuilt as <white>" + esc(choice)),
            () -> reply(player, service.reset(player, choice))), "<red>Reset: pick the new island");
    }

    // ---------------------------------------------------------------- confirm

    public void confirm(Player player, String title, List<String> lore, Runnable action) {
        Gui gui = gui(3, title);
        gui.set(11, Guis.icon(Material.LIME_CONCRETE, "<green>Confirm", lore.toArray(new String[0])), click -> {
            action.run();
            click.close();
        });
        gui.set(15, Guis.icon(Material.RED_CONCRETE, "<red>Cancel"), click -> click.close());
        gui.open(player);
    }

    // ---------------------------------------------------------------- settings

    public void settings(Player player) {
        Island island = islandOf(player);
        if (island == null) {
            noIsland(player);
            return;
        }
        boolean can = IslandAccess.has(island.roleOf(player.getUniqueId()), Cap.SETTINGS);
        Gui gui = gui(5, "<gold>Island settings");
        IslandFlag[] flags = IslandFlag.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19};
        for (int i = 0; i < flags.length && i < slots.length; i++) {
            IslandFlag flag = flags[i];
            boolean value = island.flag(flag);
            gui.set(slots[i], Guis.icon(flag.icon(), "<white>" + flag.label() + ": " + onOff(value),
                "<gray>" + flag.description(), can ? "<yellow>Click to toggle" : "<dark_gray>Owner and co-owners only"),
                click -> {
                    reply(click.player(), service.setFlag(click.player(), flag, !island.flag(flag)));
                    settings(click.player());
                });
        }
        TimeLock lock = island.timeLock();
        gui.set(31, Guis.icon(Material.CLOCK, "<white>Island time: <aqua>" + lock.label(),
            "<gray>Fixed time of day for everyone on the island", "<gray>(only changes what players see)",
            can ? "<yellow>Click for the next time" : "<dark_gray>Owner and co-owners only"), click -> {
                reply(click.player(), service.setTimeLock(click.player(), island.timeLock().next()));
                settings(click.player());
            });
        gui.set(33, Guis.icon(island.locked ? Material.IRON_DOOR : Material.OAK_DOOR,
            "<white>Island access: " + (island.locked ? "<red>Locked" : "<green>Open"),
            can ? "<yellow>Click to toggle" : "<dark_gray>Owner and co-owners only"), click -> {
                reply(click.player(), service.setLocked(click.player(), !island.locked));
                settings(click.player());
            });
        back(gui, 40, this::main);
        gui.open(player);
    }

    // ---------------------------------------------------------------- upgrades

    public void upgrades(Player player) {
        Island island = islandOf(player);
        if (island == null) {
            noIsland(player);
            return;
        }
        Gui gui = gui(4, "<green>Island upgrades");
        if (cfg.minionUpgrade) {
            upgradeIcon(gui, 11, island, Upgrade.RADIUS, Material.SPYGLASS, "blocks");
            upgradeIcon(gui, 13, island, Upgrade.MEMBERS, Material.PLAYER_HEAD, "players");
            upgradeIcon(gui, 15, island, Upgrade.MINIONS, Material.REDSTONE_LAMP, "extra slots");
        } else {
            upgradeIcon(gui, 12, island, Upgrade.RADIUS, Material.SPYGLASS, "blocks");
            upgradeIcon(gui, 14, island, Upgrade.MEMBERS, Material.PLAYER_HEAD, "players");
        }
        back(gui, 31, this::main);
        gui.open(player);
    }

    private void upgradeIcon(Gui gui, int slot, Island island, Upgrade type, Material icon, String unit) {
        List<Tier> tiers = service.tiers(type);
        int current = service.tierOf(island, type);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current: <white>" + tiers.get(current).value() + " " + unit);
        if (current + 1 < tiers.size()) {
            Tier next = tiers.get(current + 1);
            lore.add("<gray>Next: <white>" + next.value() + " " + unit);
            lore.add("<gray>Cost: <gold>" + (service.upgradeCost(next) > 0 ? Msg.coins(service.upgradeCost(next)) : "free"));
        } else {
            lore.add("<green>Maximum reached");
        }
        lore.add("");
        for (int i = 0; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            lore.add((i <= current ? "<green>✔ " : "<dark_gray>• ") + tier.value() + " " + unit
                + (i > current && service.upgradeCost(tier) > 0 ? " <dark_gray>(" + Msg.number(service.upgradeCost(tier)) + ")" : ""));
        }
        if (current + 1 < tiers.size()) {
            lore.add("");
            lore.add("<yellow>Click to buy the next tier");
        }
        gui.set(slot, Guis.icon(icon, "<green>" + type.label(), lore.toArray(new String[0])), click -> {
            reply(click.player(), service.upgrade(click.player(), type));
            upgrades(click.player());
        });
    }

    // ---------------------------------------------------------------- members

    private record Entry(UUID player, IslandRole role) {}

    public void members(Player player) {
        Island island = islandOf(player);
        if (island == null) {
            noIsland(player);
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (UUID uuid : island.coop()) {
            entries.add(new Entry(uuid, island.roleOf(uuid)));
        }
        entries.sort(Comparator.comparingInt((Entry e) -> e.role().ordinal()));
        for (UUID uuid : island.trusted) {
            entries.add(new Entry(uuid, IslandRole.TRUSTED));
        }
        for (UUID uuid : island.banned) {
            entries.add(new Entry(uuid, IslandRole.BANNED));
        }
        IslandRole mine = island.roleOf(player.getUniqueId());
        Gui gui = gui(6, "<yellow>Island members");
        gui.paginate(entries, gui.innerSlots(), entry -> {
            String name = esc(service.nameOf(entry.player()));
            Player online = Bukkit.getPlayer(entry.player());
            return head(entry.player(), "<white>" + name, "<gray>Role: <aqua>" + entry.role().label(),
                online != null ? "<green>Online" : "<dark_gray>Offline",
                IslandAccess.has(mine, Cap.KICK) && IslandAccess.outranks(mine, entry.role())
                    ? "<yellow>Click to manage" : "");
        }, (click, entry) -> {
            if (IslandAccess.has(mine, Cap.KICK) && IslandAccess.outranks(mine, entry.role())) {
                memberMenu(click.player(), entry.player());
            }
        }, 45, 53);
        back(gui, 49, this::main);
        gui.open(player);
    }

    private void memberMenu(Player player, UUID target) {
        Island island = islandOf(player);
        if (island == null) {
            return;
        }
        IslandRole mine = island.roleOf(player.getUniqueId());
        IslandRole theirs = island.roleOf(target);
        String name = esc(service.nameOf(target));
        Gui gui = gui(3, "<yellow>" + name);
        if (theirs.isCoop() && IslandAccess.has(mine, Cap.SETROLE)) {
            IslandRole next = theirs == IslandRole.CO_OWNER ? IslandRole.MEMBER : IslandRole.CO_OWNER;
            gui.set(10, Guis.icon(Material.GOLDEN_CHESTPLATE, "<gold>Make " + next.label(),
                "<gray>Currently: " + theirs.label()), click -> {
                    reply(click.player(), service.setRole(click.player(), target, next));
                    members(click.player());
                });
        }
        if (theirs.isCoop() && IslandAccess.has(mine, Cap.TRANSFER)) {
            gui.set(12, Guis.icon(Material.NETHER_STAR, "<red>Transfer ownership",
                "<gray>Give the island to " + name), click -> confirm(click.player(), "<red>Transfer the island?",
                    List.of("<gray>" + name + " becomes the owner"), () -> {
                        service.transfer(click.player(), target); // arms
                        reply(click.player(), service.transfer(click.player(), target));
                    }));
        }
        if (theirs.isCoop() && IslandAccess.has(mine, Cap.KICK)) {
            gui.set(14, Guis.icon(Material.IRON_BOOTS, "<red>Kick", "<gray>Remove from the co-op"), click -> {
                reply(click.player(), service.kick(click.player(), target));
                members(click.player());
            });
        }
        if (theirs == IslandRole.TRUSTED) {
            gui.set(10, Guis.icon(Material.LEAD, "<yellow>Remove trust"), click -> {
                reply(click.player(), service.untrust(click.player(), target));
                members(click.player());
            });
        }
        if (theirs == IslandRole.BANNED) {
            gui.set(10, Guis.icon(Material.LIME_DYE, "<green>Unban"), click -> {
                reply(click.player(), service.unban(click.player(), target));
                members(click.player());
            });
        } else if (IslandAccess.has(mine, Cap.BAN)) {
            gui.set(16, Guis.icon(Material.BARRIER, "<red>Ban", "<gray>Removes and blocks them"), click -> {
                reply(click.player(), service.ban(click.player(), target));
                members(click.player());
            });
        }
        back(gui, 22, this::members);
        gui.open(player);
    }

    // ---------------------------------------------------------------- top and warps

    public void top(Player player) {
        List<Island> ranked = service.top(100);
        Gui gui = gui(6, "<gold>Top islands");
        Island mine = islandOf(player);
        gui.paginate(ranked, gui.innerSlots(), island -> {
            int rank = ranked.indexOf(island) + 1;
            Material icon = switch (rank) {
                case 1 -> Material.GOLD_BLOCK;
                case 2 -> Material.IRON_BLOCK;
                case 3 -> Material.COPPER_BLOCK;
                default -> Material.GRASS_BLOCK;
            };
            ItemStack stack = Guis.icon(icon, "<gold>#" + rank + " <white>" + esc(registry.nameOf(island.owner)),
                "<gray>Level: <white>" + island.level, "<gray>Value: <white>" + Msg.number(island.value),
                "<gray>Members: <white>" + island.size(), island.locked ? "<red>Locked" : "<yellow>Click to visit");
            stack.setAmount(Math.max(1, Math.min(64, rank)));
            return stack;
        }, (click, island) -> {
            reply(click.player(), service.visit(click.player(), island, false));
            click.close();
        }, 45, 53);
        if (mine != null) {
            gui.set(47, Guis.icon(Material.EXPERIENCE_BOTTLE, "<light_purple>Your island",
                "<gray>Level: <white>" + mine.level, "<gray>Rank: <white>" + rankLabel(mine)));
        }
        back(gui, 49, this::main);
        gui.open(player);
    }

    public void warps(Player player) {
        List<Island> list = service.publicWarps();
        Gui gui = gui(6, "<aqua>Public warps");
        gui.paginate(list, gui.innerSlots(), island -> Guis.icon(island.warp == null ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN,
            "<aqua>" + esc(island.warp.name), "<gray>By <white>" + esc(registry.nameOf(island.owner)),
            "<gray>Level: <white>" + island.level, "<gray>Visits: <white>" + island.warp.visits,
            "<yellow>Click to visit"), (click, island) -> {
                reply(click.player(), service.visit(click.player(), island, true));
                click.close();
            }, 45, 53);
        if (list.isEmpty()) {
            gui.set(22, Guis.icon(Material.COBWEB, "<gray>No public warps yet",
                "<gray>Owners create one with /island warp set"));
        }
        back(gui, 49, p -> {
            if (islandOf(p) != null) {
                main(p);
            } else {
                p.closeInventory();
            }
        });
        gui.open(player);
    }

    /** Lower-case id helper used by the commands. */
    static String id(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
