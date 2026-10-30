package win.vampyuyu.survival.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.enchant.Enchant;
import win.vampyuyu.survival.items.enchant.EnchantRules;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.menu.TableListener;
import win.vampyuyu.survival.items.pet.PetData;
import win.vampyuyu.survival.items.pet.PetService;
import win.vampyuyu.survival.items.pet.PetTypes;
import win.vampyuyu.survival.items.stats.PlayerState;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** Every command of the items feature: player menus and the {@code /sbitems} admin tool. */
public final class ItemsCommands {
    public static final String USE = "vampyuyu.sb.items.use";
    public static final String ADMIN = "vampyuyu.sb.items.admin";
    public static final String REMOTE = "vampyuyu.sb.items.remote";

    private final Core core;
    private final Gear gear;
    private final StatsEngine engine;
    private final PetService pets;
    private final Menus menus;
    private final Runnable reload;

    /** Opens one of the menus for a player. */
    @FunctionalInterface
    public interface Menus {
        void open(Player player, Menu menu);
    }

    /** The menus a command can open. */
    public enum Menu { ACCESSORIES, PETS, REFORGE, ENCHANT, ANVIL }

    public ItemsCommands(Core core, Gear gear, StatsEngine engine, PetService pets, Menus menus,
            Runnable reload) {
        this.core = core;
        this.gear = gear;
        this.engine = engine;
        this.pets = pets;
        this.menus = menus;
        this.reload = reload;
    }

    public void register() {
        core.permissions().register(USE, "Use the Skyblock item menus", PermissionDefault.TRUE);
        core.permissions().register(REMOTE, "Open the enchanting table and anvil from anywhere", PermissionDefault.OP);
        core.permissions().register(ADMIN, "Items administration", PermissionDefault.OP);
        menuCommand("accessories", new String[] {"acc", "bag"}, "Open your accessory bag", Menu.ACCESSORIES, USE,
            PermissionDefault.TRUE);
        menuCommand("pets", new String[] {"pet"}, "Open your pets", Menu.PETS, USE, PermissionDefault.TRUE);
        menuCommand("reforge", new String[] {"blacksmith"}, "Open the blacksmith to reforge an item", Menu.REFORGE, USE,
            PermissionDefault.TRUE);
        menuCommand("enchanttable", new String[] {"etable"}, "Open the enchanting table from anywhere", Menu.ENCHANT,
            REMOTE, PermissionDefault.OP);
        menuCommand("anvil", new String[0], "Open the anvil from anywhere", Menu.ANVIL, REMOTE, PermissionDefault.OP);
        core.commands().command("stats").aliases("mystats").description("Show your Skyblock stats")
            .usage("/stats").permission(USE, PermissionDefault.TRUE).executor(this::stats).register();
        core.commands().command("sbitems").description("Items administration")
            .usage("/sbitems <book|enchant|reforge|pet|mana|stats|list|reload> ...")
            .permission(ADMIN, PermissionDefault.OP).executor(this::admin).tab(this::complete).register();
    }

    private void menuCommand(String name, String[] aliases, String description, Menu menu, String permission,
            PermissionDefault def) {
        core.commands().command(name).aliases(aliases).description(description).usage("/" + name)
            .permission(permission, def).executor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    core.msg().error(sender, "Only players can open this menu.");
                    return true;
                }
                menus.open(player, menu);
                return true;
            }).register();
    }

    // ---------------------------------------------------------------- /stats

    private boolean stats(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players have stats.");
            return true;
        }
        engine.recompute(player);
        PlayerState st = engine.state(player);
        core.msg().info(player, "Your stats" + (engine.active(player) ? "" : " (not applied outside Skyblock worlds)") + ":");
        for (Stat stat : Stat.values()) {
            double value = st.stat(stat);
            if (Math.abs(value - stat.base()) < 1e-9 && stat != Stat.HEALTH && stat != Stat.DEFENSE) {
                continue;
            }
            player.sendMessage(net.kyori.adventure.text.Component.text(stat.symbol() + " " + stat.displayName() + " ", stat.color())
                .append(net.kyori.adventure.text.Component.text(format(value) + (stat.percent() ? "%" : ""),
                    net.kyori.adventure.text.format.NamedTextColor.WHITE)));
        }
        player.sendMessage(net.kyori.adventure.text.Component.text("Mana ", net.kyori.adventure.text.format.NamedTextColor.AQUA)
            .append(net.kyori.adventure.text.Component.text(format(st.mana.mana()) + "/" + format(st.stat(Stat.INTELLIGENCE)),
                net.kyori.adventure.text.format.NamedTextColor.WHITE)));
        return true;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    // ---------------------------------------------------------------- /sbitems

    private boolean admin(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "book" -> book(sender, args);
                case "enchant" -> enchant(sender, args);
                case "reforge" -> reforge(sender, args);
                case "pet" -> pet(sender, args);
                case "mana" -> mana(sender, args);
                case "stats" -> adminStats(sender, args);
                case "list" -> list(sender, args);
                case "reload" -> {
                    reload.run();
                    core.msg().ok(sender, "items.yml reloaded (enchant and reforge definitions need a restart).");
                }
                default -> help(sender);
            }
        } catch (RuntimeException e) {
            core.msg().error(sender, "Failed: " + e.getMessage());
        }
        return true;
    }

    private void help(CommandSender sender) {
        core.msg().info(sender, "Items administration:");
        for (String line : List.of(
            "/sbitems book <enchant> <level> [player]  - give an enchanted book",
            "/sbitems enchant <enchant> <level>  - enchant the item in your hand (0 removes it)",
            "/sbitems reforge <id|none>  - reforge the item in your hand",
            "/sbitems pet <type> <rarity> [level] [player]  - give a pet egg",
            "/sbitems mana <player> <amount>  - set a player's mana",
            "/sbitems stats <player>  - print a player's stat breakdown",
            "/sbitems list <enchants|reforges|pets|items>",
            "/sbitems reload")) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(line, net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    private @Nullable Player target(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player found = Bukkit.getPlayerExact(args[index]);
            if (found == null) {
                core.msg().error(sender, "Player not found: " + args[index]);
            }
            return found;
        }
        if (sender instanceof Player player) {
            return player;
        }
        core.msg().error(sender, "Name a player.");
        return null;
    }

    private static int level(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + text);
        }
    }

    /** An enchanted book holding {@code enchant} at {@code level}. */
    public ItemStack createBook(Enchant enchant, int level) {
        ItemStack book = gear.items().create("enchanted_book");
        gear.items().set(book, Keys.BOOK, enchant.id() + ":" + level);
        gear.rebuild(book);
        return book;
    }

    private void book(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.msg().error(sender, "Usage: /sbitems book <enchant> <level> [player]");
            return;
        }
        Optional<Enchant> enchant = gear.enchants().get(args[1].toLowerCase(Locale.ROOT));
        if (enchant.isEmpty()) {
            core.msg().error(sender, "Unknown enchant: " + args[1]);
            return;
        }
        int level = level(args[2]);
        if (level < 1 || level > enchant.get().maxLevel()) {
            core.msg().error(sender, enchant.get().name() + " goes from 1 to " + enchant.get().maxLevel() + ".");
            return;
        }
        Player player = target(sender, args, 3);
        if (player == null) {
            return;
        }
        give(player, createBook(enchant.get(), level));
        core.msg().ok(sender, "Gave " + enchant.get().label(level) + " to " + player.getName() + ".");
    }

    private void enchant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Hold the item in game.");
            return;
        }
        if (args.length < 3) {
            core.msg().error(sender, "Usage: /sbitems enchant <enchant> <level>");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<GearInfo> info = gear.read(hand);
        Optional<Enchant> enchant = gear.enchants().get(args[1].toLowerCase(Locale.ROOT));
        if (info.isEmpty() || enchant.isEmpty()) {
            core.msg().error(sender, info.isEmpty() ? "Hold a Skyblock item." : "Unknown enchant: " + args[1]);
            return;
        }
        int level = level(args[2]);
        java.util.Map<String, Integer> merged = new java.util.LinkedHashMap<>(info.get().enchants());
        if (level <= 0) {
            merged.remove(enchant.get().id());
        } else {
            if (!enchant.get().appliesTo(info.get().kind())) {
                core.msg().error(sender, enchant.get().name() + " does not apply to this item.");
                return;
            }
            if (enchant.get().ultimate() && EnchantRules.hasOtherUltimate(merged, gear.enchants(), enchant.get().id())) {
                core.msg().error(sender, "The item already has another ultimate enchant.");
                return;
            }
            merged.put(enchant.get().id(), Math.min(level, enchant.get().maxLevel()));
        }
        gear.setEnchants(hand, merged);
        gear.rebuild(hand);
        player.getInventory().setItemInMainHand(hand);
        engine.markDirty(player.getUniqueId(), true);
        core.msg().ok(sender, "Done.");
    }

    private void reforge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            core.msg().error(sender, "Usage (in game): /sbitems reforge <id|none>");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<GearInfo> info = gear.read(hand);
        if (info.isEmpty()) {
            core.msg().error(sender, "Hold a Skyblock item.");
            return;
        }
        if (args[1].equalsIgnoreCase("none")) {
            gear.setReforge(hand, null);
        } else {
            var reforge = gear.reforges().get(args[1].toLowerCase(Locale.ROOT));
            if (reforge.isEmpty() || !reforge.get().appliesTo(info.get().kind())) {
                core.msg().error(sender, "Unknown reforge, or it does not fit this item.");
                return;
            }
            gear.setReforge(hand, reforge.get().id());
        }
        gear.rebuild(hand);
        player.getInventory().setItemInMainHand(hand);
        engine.markDirty(player.getUniqueId(), true);
        core.msg().ok(sender, "Done.");
    }

    private void pet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.msg().error(sender, "Usage: /sbitems pet <type> <rarity> [level] [player]");
            return;
        }
        if (PetTypes.get(args[1].toLowerCase(Locale.ROOT)).isEmpty()) {
            core.msg().error(sender, "Unknown pet: " + args[1]);
            return;
        }
        Rarity rarity;
        try {
            rarity = Rarity.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            core.msg().error(sender, "Unknown rarity: " + args[2]);
            return;
        }
        int level = args.length > 3 ? Math.max(1, Math.min(100, level(args[3]))) : 1;
        Player player = target(sender, args, 4);
        if (player == null) {
            return;
        }
        PetData.Entry entry = new PetData.Entry();
        entry.id = java.util.UUID.randomUUID().toString();
        entry.type = args[1].toLowerCase(Locale.ROOT);
        entry.rarity = rarity.name();
        entry.xp = pets.leveling().totalForLevel(level, rarity);
        give(player, pets.createEgg(entry));
        core.msg().ok(sender, "Gave a pet egg to " + player.getName() + ".");
    }

    private void mana(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.msg().error(sender, "Usage: /sbitems mana <player> <amount>");
            return;
        }
        Player player = target(sender, args, 1);
        if (player == null) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            core.msg().error(sender, "Not a number: " + args[2]);
            return;
        }
        PlayerState st = engine.state(player);
        st.mana.fill(Math.max(0, Math.min(amount, st.stat(Stat.INTELLIGENCE))));
        core.msg().ok(sender, "Mana set.");
    }

    private void adminStats(CommandSender sender, String[] args) {
        Player player = target(sender, args, 1);
        if (player == null) {
            return;
        }
        engine.recompute(player);
        PlayerState st = engine.state(player);
        core.msg().info(sender, player.getName() + ", stat sources:");
        for (Map.Entry<String, Map<Stat, Double>> source : st.sources().entrySet()) {
            StringBuilder line = new StringBuilder(source.getKey()).append(": ");
            source.getValue().forEach((stat, value) -> {
                if (value != 0) {
                    line.append(stat.key()).append('=').append(format(value)).append(' ');
                }
            });
            sender.sendMessage(net.kyori.adventure.text.Component.text(line.toString(),
                net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
        core.msg().info(sender, "Enchants in play: " + st.enchantLevels());
    }

    private void list(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "enchants";
        List<String> ids = switch (what) {
            case "reforges" -> gear.reforges().all().stream().map(r -> r.id()).toList();
            case "pets" -> PetTypes.all().stream().map(t -> t.id()).toList();
            case "items" -> gear.items().ids().stream().filter(this::ours).sorted().toList();
            default -> gear.enchants().ids();
        };
        core.msg().info(sender, what + " (" + ids.size() + "): " + String.join(", ", ids));
    }

    private boolean ours(String id) {
        return gear.items().def(id).map(def -> def.itemModel() != null && def.itemModel().startsWith("vampyuyu:items/"))
            .orElse(false);
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    private List<String> complete(CommandSender sender, Command command, String alias, String[] args) {
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.addAll(List.of("book", "enchant", "reforge", "pet", "mana", "stats", "list", "reload"));
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "book", "enchant" -> {
                    if (args.length == 2) {
                        options.addAll(gear.enchants().ids());
                    } else if (args.length == 3) {
                        options.addAll(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
                    } else if (sub.equals("book")) {
                        online(options);
                    }
                }
                case "reforge" -> {
                    options.add("none");
                    gear.reforges().all().forEach(r -> options.add(r.id()));
                }
                case "pet" -> {
                    if (args.length == 2) {
                        PetTypes.all().forEach(t -> options.add(t.id()));
                    } else if (args.length == 3) {
                        Stream.of(Rarity.values()).forEach(r -> options.add(r.name().toLowerCase(Locale.ROOT)));
                    } else if (args.length == 4) {
                        options.addAll(List.of("1", "25", "50", "100"));
                    } else {
                        online(options);
                    }
                }
                case "mana", "stats" -> {
                    if (args.length == 2) {
                        online(options);
                    }
                }
                case "list" -> options.addAll(List.of("enchants", "reforges", "pets", "items"));
                default -> { }
            }
        }
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).sorted().toList();
    }

    private static void online(List<String> options) {
        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
    }

    /** Opens a menu on the next tick (called from events that must not open inventories themselves). */
    public static void later(Core core, Player player, Runnable open) {
        core.scheduler().nextTick(() -> {
            if (player.isOnline()) {
                open.run();
            }
        });
    }

    /** Maps the table listener kinds onto menus. */
    public static Menu menuOf(TableListener.Kind kind) {
        return switch (kind) {
            case ENCHANT -> Menu.ENCHANT;
            case ANVIL -> Menu.ANVIL;
            case REFORGE -> Menu.REFORGE;
        };
    }
}
