package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;

/** /bz and the Bazaar menus. */
final class BazaarUi {
    private final Core core;
    private final EconomyService economy;
    private final ItemKeys keys;
    private final BazaarService bz;

    BazaarUi(Core core, EconomyService economy, ItemKeys keys, BazaarService bz) {
        this.core = core;
        this.economy = economy;
        this.keys = keys;
        this.bz = bz;
    }

    void register() {
        core.commands().command("bazaar").aliases("bz")
            .description("Trade items with other players")
            .usage("/bz [search <text> | buy <item> <amount> | sell <item> <amount|all> | order <buy|sell> <item> <amount> <price> | orders | claim | cancel <id> | price <item>]")
            .permission("vampyuyu.sb.economy.bazaar", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> command(s, a))
            .tab((s, c, l, a) -> tab(a)).register();
    }

    private List<String> tab(String[] a) {
        if (a.length == 1) {
            return Targets.filter(List.of("search", "buy", "sell", "order", "orders", "claim", "cancel", "price"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && sub.equals("order")) {
            return Targets.filter(List.of("buy", "sell"), a[1]);
        }
        boolean productSlot = (a.length == 2 && List.of("buy", "sell", "price").contains(sub))
            || (a.length == 3 && sub.equals("order"));
        if (productSlot) {
            return Targets.filter(bz.products(null), a[a.length - 1].toUpperCase(Locale.ROOT)).stream().limit(40).toList();
        }
        return List.of();
    }

    private void say(Player p, BazaarService.Outcome o) {
        if (o.ok()) {
            core.msg().ok(p, o.message());
        } else {
            core.msg().error(p, o.message());
        }
    }

    // ---------------------------------------------------------------- command

    private boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can use the Bazaar.");
            return true;
        }
        if (args.length == 0) {
            openMain(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "search" -> {
                if (args.length < 2) {
                    return false;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                List<String> found = bz.search(text);
                if (found.isEmpty()) {
                    core.msg().error(player, "Nothing in the Bazaar matches '" + text + "'.");
                } else {
                    openList(player, "<gold>Search: " + Ui.esc(text), found, null);
                }
            }
            case "orders" -> openOrders(player);
            case "claim" -> bz.claim(player, o -> say(player, o));
            case "cancel" -> {
                if (args.length != 2) {
                    return false;
                }
                try {
                    say(player, bz.cancel(player, Long.parseLong(args[1].replace("#", ""))));
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case "price" -> {
                if (args.length < 2) {
                    return false;
                }
                var product = bz.find(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                if (product.isEmpty()) {
                    core.msg().error(player, "Unknown or ambiguous item.");
                } else {
                    openProduct(player, product.get(), 1, null);
                }
            }
            case "buy", "sell" -> {
                if (args.length != 3) {
                    return false;
                }
                var product = bz.find(args[1]);
                if (product.isEmpty()) {
                    core.msg().error(player, "Unknown or ambiguous item '" + args[1] + "'. Try /bz search " + args[1]);
                    return true;
                }
                int amount = amount(player, product.get(), args[2], args[0].equalsIgnoreCase("sell"));
                if (amount < 1) {
                    core.msg().error(player, "'" + args[2] + "' is not a valid amount.");
                    return true;
                }
                say(player, args[0].equalsIgnoreCase("buy") ? bz.instantBuy(player, product.get(), amount)
                    : bz.instantSell(player, product.get(), amount));
            }
            case "order" -> {
                if (args.length != 5 || !(args[1].equalsIgnoreCase("buy") || args[1].equalsIgnoreCase("sell"))) {
                    return false;
                }
                var product = bz.find(args[2]);
                if (product.isEmpty()) {
                    core.msg().error(player, "Unknown or ambiguous item '" + args[2] + "'.");
                    return true;
                }
                boolean buy = args[1].equalsIgnoreCase("buy");
                int amount = amount(player, product.get(), args[3], !buy);
                OptionalLong cents = Money.parse(args[4]);
                if (amount < 1 || cents.isEmpty()) {
                    core.msg().error(player, "Invalid amount or price.");
                    return true;
                }
                if (cents.getAsLong() % BazaarMarket.CENTS_PER_TICK != 0) {
                    core.msg().error(player, "Prices move in steps of 0.1 coin.");
                    return true;
                }
                long ticks = cents.getAsLong() / BazaarMarket.CENTS_PER_TICK;
                say(player, buy ? bz.placeBuy(player, product.get(), ticks, amount)
                    : bz.placeSell(player, product.get(), ticks, amount));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private int amount(Player player, String product, String text, boolean fromInventory) {
        if (text.equalsIgnoreCase("all") && fromInventory) {
            return Inv.count(player, keys, product);
        }
        try {
            return Integer.parseInt(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- menus

    void openMain(Player player) {
        Gui gui = core.guis().create(6, "<gold>Bazaar");
        gui.fill(Ui.glass());
        BazaarMarket.Claim claim = bz.claimable(player);
        gui.set(4, Ui.icon(Material.EMERALD, "bazaar", "<gold>Bazaar",
            "<gray>Trade raw goods with every player.", "<gray>Instant trades, buy orders, sell orders.", "",
            "<gray>Tax on sales: <yellow>" + (bz.market().rules().taxBp() / 100.0) + "%"));
        BazaarService.Category[] cats = BazaarService.Category.values();
        int[] slots = {19, 20, 21, 23, 24, 25};
        for (int i = 0; i < cats.length; i++) {
            BazaarService.Category cat = cats[i];
            List<String> items = bz.products(cat);
            gui.set(slots[i], Ui.plain(cat.icon, "<yellow>" + cat.label, "<gray>" + items.size() + " products", "",
                "<yellow>Click to browse"), c -> openList(c.player(), "<gold>Bazaar: " + cat.label, bz.products(cat), cat));
        }
        gui.set(40, Ui.plain(Material.OAK_SIGN, "<aqua>Search", "<gray>Type in chat:", "<yellow>/bz search [text]",
            "<dark_gray>Example: /bz search coal"));
        gui.set(48, Ui.icon(Material.WRITABLE_BOOK, "orders", "<green>My orders",
            "<gray>" + bz.market().ordersOf(player.getUniqueId().toString()).size() + " orders and claims", "",
            "<yellow>Click to open"), c -> openOrders(c.player()));
        gui.set(50, Ui.plain(Material.CHEST, claim.empty() ? "<gray>Nothing to claim" : "<green>Claim everything",
            claim.empty() ? "<dark_gray>Filled orders show up here." : "<gray>Coins: <gold>" + Money.coins(claim.cents()),
            claim.empty() ? "" : "<gray>Items: <white>" + claim.items().values().stream().mapToInt(Integer::intValue).sum(),
            "", claim.empty() ? "" : "<yellow>Click to collect"), c -> claimAndRefresh(c, () -> openMain(c.player())));
        gui.set(49, Ui.plain(Material.BARRIER, "<red>Close"), Gui.Click::close);
        gui.open(player);
    }

    private void claimAndRefresh(Gui.Click c, Runnable then) {
        Player p = c.player();
        bz.claim(p, o -> {
            say(p, o);
            then.run();
        });
    }

    void openList(Player player, String title, List<String> products, BazaarService.Category back) {
        Gui gui = core.guis().create(6, title);
        gui.border(Ui.glass());
        long now = System.currentTimeMillis();
        gui.paginate(products, gui.innerSlots(), key -> {
            ItemStack base = keys.create(key, 1).orElseGet(() -> new ItemStack(Material.BARRIER));
            long ask = bz.market().bestAsk(key);
            long bid = bz.market().bestBid(key);
            return Ui.withLore(base, List.of("",
                "<gray>Buy instantly: <gold>" + (ask > 0 ? BazaarService.price(ask) : "<red>no offers"),
                "<gray>Sell instantly: <gold>" + (bid > 0 ? BazaarService.price(bid) : "<red>no orders"),
                "<gray>24h volume: <white>" + bz.market().volume24h(key, now), "", "<yellow>Click to trade"));
        }, (c, key) -> openProduct(c.player(), key, 1, back), 45, 53);
        gui.set(49, Ui.plain(Material.ARROW, "<yellow>Back to the Bazaar"), c -> openMain(c.player()));
        gui.open(player);
    }

    void openProduct(Player player, String product, int startAmount, BazaarService.Category back) {
        Gui gui = core.guis().create(5, "<gold>" + Ui.esc(keys.name(product)));
        int[] amount = {Math.max(1, startAmount)};
        renderProduct(gui, player, product, amount, back);
        gui.open(player);
    }

    private void renderProduct(Gui gui, Player player, String product, int[] amount, BazaarService.Category back) {
        gui.fill(Ui.glass());
        BazaarMarket m = bz.market();
        long now = System.currentTimeMillis();
        String me = player.getUniqueId().toString();
        ItemStack base = keys.create(product, 1).orElseGet(() -> new ItemStack(Material.BARRIER));
        int stack = keys.maxStack(product);
        long ask = m.bestAsk(product);
        long bid = m.bestBid(product);
        long avg = m.average24h(product, now);
        int carry = Inv.count(player, keys, product);

        List<String> info = new ArrayList<>();
        info.add("");
        info.add("<gray>Cheapest offer: <gold>" + (ask > 0 ? BazaarService.price(ask) : "-"));
        info.add("<gray>Best buy order: <gold>" + (bid > 0 ? BazaarService.price(bid) : "-"));
        if (ask > 0 && bid > 0) {
            info.add("<gray>Spread: <yellow>" + BazaarService.price(ask - bid) + " <dark_gray>("
                + (ask - bid) * 100 / ask + "%)");
        }
        info.add("<gray>Average (24h): <gold>" + (avg > 0 ? BazaarService.price(avg) : "-"));
        info.add("<gray>Volume (24h): <white>" + m.volume24h(product, now));
        List<Long> spark = m.sparkline(product, 24);
        if (spark.size() > 1) {
            info.add("<gray>History: <aqua>" + Ui.spark(spark));
        }
        info.add("<gray>You carry: <white>" + carry);
        gui.set(4, Ui.withLore(base, info));

        gui.set(13, Ui.plain(Material.PAPER, "<red>Sell offers", levels(product, false)));
        gui.set(15, Ui.plain(Material.PAPER, "<green>Buy orders", levels(product, true)));

        int n = amount[0];
        var buyQuote = m.quoteBuy(me, product, n);
        gui.set(19, Ui.icon(Material.GOLD_INGOT, "buy_now", "<green>Buy instantly",
            "<gray>Amount: <white>" + n,
            buyQuote.check().ok() ? "<gray>Cost: <gold>" + Money.coins(buyQuote.cents()) : "<red>" + buyQuote.check().error(),
            "", "<yellow>Click to buy"), c -> {
                say(c.player(), bz.instantBuy(c.player(), product, n));
                renderProduct(gui, c.player(), product, amount, back);
            });
        var sellQuote = m.quoteSell(me, product, n);
        gui.set(25, Ui.icon(Material.HOPPER, "sell_now", "<red>Sell instantly",
            "<gray>Amount: <white>" + n + " <dark_gray>(you carry " + carry + ")",
            sellQuote.check().ok() ? "<gray>You get: <gold>" + Money.coins(sellQuote.cents()) + " <dark_gray>after tax"
                : "<red>" + sellQuote.check().error(), "", "<yellow>Click to sell"), c -> {
                    say(c.player(), bz.instantSell(c.player(), product, n));
                    renderProduct(gui, c.player(), product, amount, back);
                });

        long buyPrice = suggestBuy(product, ask, bid, avg);
        gui.set(20, Ui.icon(Material.WRITABLE_BOOK, "buy_order", "<green>Buy order: " + BazaarService.price(buyPrice),
            "<gray>Amount: <white>" + n, "<gray>Held now: <gold>" + Money.coins(buyPrice * 10 * n),
            "<gray>One tick above the best buy order,", "<gray>so you are first in line.", "",
            "<yellow>Click to place"), c -> {
                say(c.player(), bz.placeBuy(c.player(), product, buyPrice, n));
                renderProduct(gui, c.player(), product, amount, back);
            });
        gui.set(21, Ui.plain(Material.OAK_SIGN, "<green>Custom buy order", "<gray>Pick your own price:",
            "<yellow>/bz order buy " + product.toLowerCase(Locale.ROOT) + " " + n + " [price]",
            "<dark_gray>Prices move in steps of 0.1"));
        long sellPrice = suggestSell(product, ask, bid, avg);
        gui.set(24, Ui.icon(Material.WRITABLE_BOOK, "sell_order", "<red>Sell order: " + BazaarService.price(sellPrice),
            "<gray>Amount: <white>" + n + " <dark_gray>(you carry " + carry + ")",
            "<gray>One tick below the cheapest offer,", "<gray>so you are first in line.", "",
            "<yellow>Click to place"), c -> {
                say(c.player(), bz.placeSell(c.player(), product, sellPrice, n));
                renderProduct(gui, c.player(), product, amount, back);
            });
        gui.set(23, Ui.plain(Material.OAK_SIGN, "<red>Custom sell order", "<gray>Pick your own price:",
            "<yellow>/bz order sell " + product.toLowerCase(Locale.ROOT) + " " + n + " [price]",
            "<dark_gray>Prices move in steps of 0.1"));

        int[] deltas = {-stack, -16, -1, 1, 16, stack};
        int[] slots = {37, 38, 39, 41, 42, 43};
        for (int i = 0; i < deltas.length; i++) {
            int d = deltas[i];
            gui.set(slots[i], Ui.plain(d < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                (d < 0 ? "<red>" : "<green>+") + d, "<gray>Changes the amount to trade."), c -> {
                    amount[0] = Math.max(1, Math.min(bz.market().rules().maxAmount(), amount[0] + d));
                    renderProduct(gui, c.player(), product, amount, back);
                });
        }
        gui.set(40, Ui.plain(Material.PAPER, "<white>Amount: " + n, "<dark_gray>Use the buttons around me."), c -> {
            amount[0] = Math.max(1, Inv.count(c.player(), keys, product));
            renderProduct(gui, c.player(), product, amount, back);
        });
        gui.set(36, Ui.plain(Material.ARROW, "<yellow>Back"), c -> {
            if (back != null) {
                openList(c.player(), "<gold>Bazaar: " + back.label, bz.products(back), back);
            } else {
                openMain(c.player());
            }
        });
        gui.set(44, Ui.plain(Material.CHEST, "<green>Claim", "<gray>Collect filled orders."), c ->
            claimAndRefresh(c, () -> renderProduct(gui, c.player(), product, amount, back)));
    }

    private long suggestBuy(String product, long ask, long bid, long avg) {
        long p = bid > 0 ? bid + 1 : avg > 0 ? avg : ask > 1 ? ask - 1 : 10;
        if (ask > 0 && p >= ask) {
            p = Math.max(1, ask - 1);
        }
        return Math.max(1, p);
    }

    private long suggestSell(String product, long ask, long bid, long avg) {
        long p = ask > 1 ? ask - 1 : avg > 0 ? avg : bid > 0 ? bid + 1 : 10;
        if (bid > 0 && p <= bid) {
            p = bid + 1;
        }
        return Math.max(1, p);
    }

    private String[] levels(String product, boolean buySide) {
        List<long[]> levels = bz.market().levels(product, buySide, 6);
        if (levels.isEmpty()) {
            return new String[] {"<gray>Nothing here yet."};
        }
        List<String> lines = new ArrayList<>();
        for (long[] l : levels) {
            lines.add("<gold>" + BazaarService.price(l[0]) + " <gray>x <white>" + l[1] + " <dark_gray>(" + l[2]
                + (l[2] == 1 ? " order)" : " orders)"));
        }
        return lines.toArray(new String[0]);
    }

    void openOrders(Player player) {
        List<BazaarMarket.Order> orders = bz.market().ordersOf(player.getUniqueId().toString());
        Gui gui = core.guis().create(6, "<green>My Bazaar orders");
        gui.border(Ui.glass());
        gui.paginate(orders, gui.innerSlots(), o -> {
            ItemStack base = keys.create(o.product, 1).orElseGet(() -> new ItemStack(Material.PAPER));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add((o.buy ? "<green>BUY ORDER" : "<red>SELL ORDER") + " <dark_gray>#" + o.id);
            lore.add("<gray>Price: <gold>" + BazaarService.price(o.price) + " <gray>each");
            lore.add("<gray>Filled: <white>" + o.filled() + "<gray>/<white>" + o.total
                + (o.closed ? " <dark_gray>(cancelled)" : ""));
            if (o.hasClaim()) {
                lore.add("<green>Ready: " + (o.claimItems > 0 ? o.claimItems + " items " : "")
                    + (o.claimCents > 0 ? Money.coins(o.claimCents) : ""));
                lore.add("<yellow>Click to claim");
            }
            if (o.live()) {
                lore.add("<red>Right-click to cancel");
            }
            return Ui.withLore(base, lore);
        }, (c, o) -> {
            if (c.right() && o.live()) {
                say(c.player(), bz.cancel(c.player(), o.id));
                openOrders(c.player());
            } else if (o.hasClaim()) {
                claimAndRefresh(c, () -> openOrders(c.player()));
            }
        }, 45, 53);
        gui.set(49, Ui.plain(Material.ARROW, "<yellow>Back to the Bazaar"), c -> openMain(c.player()));
        gui.set(50, Ui.plain(Material.CHEST, "<green>Claim everything"),
            c -> claimAndRefresh(c, () -> openOrders(c.player())));
        gui.open(player);
    }
}
