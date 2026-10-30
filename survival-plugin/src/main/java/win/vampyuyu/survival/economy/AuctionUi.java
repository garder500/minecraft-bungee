package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.LongUnaryOperator;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Msg;

/** /ah and the Auction House menus. */
final class AuctionUi implements Listener {
    private final Core core;
    private final ItemKeys keys;
    private final AuctionService ah;
    private Prompts prompts;

    AuctionUi(Core core, ItemKeys keys, AuctionService ah) {
        this.core = core;
        this.keys = keys;
        this.ah = ah;
    }

    /** Click-then-type input; without it the menus tell the player which command to type instead. */
    void prompts(Prompts prompts) {
        this.prompts = prompts;
    }

    void register() {
        core.commands().command("auctionhouse").aliases("ah", "auction")
            .description("Auction items to other players")
            .usage("/ah [browse [category] | search <text> | sell <price> [bin] [hours] | bid <id> <amount> | buy <id> | mine | claim | cancel <id> | history]")
            .permission("vampyuyu.sb.economy.auction", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> command(s, a))
            .tab((s, c, l, a) -> tab(a)).register();
        core.listen(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later(60, () -> {
            if (p.isOnline()) {
                ah.remind(p);
            }
        });
    }

    private List<String> tab(String[] a) {
        if (a.length == 1) {
            return Targets.filter(List.of("browse", "search", "sell", "bid", "buy", "mine", "claim", "cancel", "history"),
                a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && sub.equals("browse")) {
            return Targets.filter(Arrays.stream(AuctionService.Category.values())
                .map(c -> c.name().toLowerCase(Locale.ROOT)).toList(), a[1]);
        }
        if (sub.equals("sell") && a.length == 3) {
            return Targets.filter(List.of("bin", "auction"), a[2]);
        }
        if (sub.equals("sell") && a.length == 4) {
            return Targets.filter(ah.settings().durations().stream().map(String::valueOf).toList(), a[3]);
        }
        return List.of();
    }

    private void say(Player p, BazaarService.Outcome o) {
        if (o.ok()) {
            core.msg().ok(p, o.message());
            Sfx.success(p);
        } else {
            core.msg().error(p, o.message());
            Sfx.error(p);
        }
    }

    // ---------------------------------------------------------------- command

    private boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can use the Auction House.");
            return true;
        }
        if (args.length == 0) {
            openMain(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "browse" -> {
                AuctionService.Category cat = null;
                if (args.length > 1) {
                    try {
                        cat = AuctionService.Category.valueOf(args[1].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        core.msg().error(player, "Unknown category '" + args[1] + "'.");
                        return true;
                    }
                }
                openBrowse(player, cat, null, AuctionService.Sort.ENDING);
            }
            case "search" -> {
                if (args.length < 2) {
                    return false;
                }
                openBrowse(player, null, String.join(" ", Arrays.copyOfRange(args, 1, args.length)),
                    AuctionService.Sort.ENDING);
            }
            case "sell" -> {
                if (args.length < 2 || args.length > 4) {
                    return false;
                }
                OptionalLong cents = Money.parse(args[1]);
                if (cents.isEmpty()) {
                    core.msg().error(player, "'" + args[1] + "' is not a valid price.");
                    return true;
                }
                boolean bin = args.length > 2 && args[2].equalsIgnoreCase("bin");
                int hours = ah.settings().defaultHours();
                if (args.length > 3) {
                    try {
                        hours = Integer.parseInt(args[3].replace("h", ""));
                    } catch (NumberFormatException e) {
                        core.msg().error(player, "'" + args[3] + "' is not a valid duration in hours.");
                        return true;
                    }
                }
                say(player, ah.list(player, cents.getAsLong(), bin, hours));
            }
            case "bid" -> {
                if (args.length != 3) {
                    return false;
                }
                OptionalLong cents = Money.parse(args[2]);
                Long id = id(args[1]);
                if (cents.isEmpty() || id == null) {
                    core.msg().error(player, "Invalid auction id or amount.");
                    return true;
                }
                say(player, ah.bid(player, id, cents.getAsLong()));
            }
            case "buy" -> {
                Long id = args.length == 2 ? id(args[1]) : null;
                if (id == null) {
                    return false;
                }
                say(player, ah.buyNow(player, id));
            }
            case "cancel" -> {
                Long id = args.length == 2 ? id(args[1]) : null;
                if (id == null) {
                    return false;
                }
                say(player, ah.cancel(player, id));
            }
            case "mine" -> openMine(player);
            case "claim" -> ah.claim(player, o -> say(player, o));
            case "history" -> openHistory(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Long id(String text) {
        try {
            return Long.parseLong(text.replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- menus

    private void claimAndRefresh(Gui.Click c, Runnable then) {
        Player p = c.player();
        ah.claim(p, o -> {
            say(p, o);
            then.run();
        });
    }

    void openMain(Player player) {
        Gui gui = core.guis().create(6, "<gold>Auction House");
        gui.fill(Ui.glass());
        long now = System.currentTimeMillis();
        AuctionBook.Claim claim = ah.claimable(player);
        gui.set(4, Ui.icon(Material.GOLD_BLOCK, "auction", "<gold>Auction House",
            "<gray>Sell your finds to the whole server.", "<gray>Bid, or buy at a fixed price.", "",
            "<gray>Active listings: <white>" + ah.book().active(now).size(),
            "<gray>Sale tax: <yellow>" + (ah.book().rules().saleTaxBp() / 100.0) + "%"));
        AuctionService.Category[] cats = AuctionService.Category.values();
        int[] slots = {19, 20, 21, 23, 24, 25};
        for (int i = 0; i < cats.length; i++) {
            AuctionService.Category cat = cats[i];
            int n = ah.browse(cat, null, AuctionService.Sort.ENDING, now).size();
            gui.set(slots[i], Ui.plain(cat.icon, "<yellow>" + cat.label, "<gray>" + n + " listings", "",
                "<yellow>Click to browse"), c -> openBrowse(c.player(), cat, null, AuctionService.Sort.ENDING));
        }
        gui.set(22, Ui.icon(Material.COMPASS, "browse", "<yellow>Browse everything",
            "<gray>Every active listing.", "", "<yellow>Click to browse"),
            c -> openBrowse(c.player(), null, null, AuctionService.Sort.ENDING));
        gui.set(31, Ui.plain(Material.OAK_SIGN, "<aqua>Search", "<gray>Find listings by item name.", "",
            "<yellow>Click, then type the name in chat", "<dark_gray>Or use /ah search [text]"), c -> {
                if (prompts == null) {
                    core.msg().info(c.player(), "Type /ah search [text].");
                    return;
                }
                c.close();
                prompts.ask(c.player(), "What are you looking for?",
                    text -> openBrowse(c.player(), null, text, AuctionService.Sort.ENDING),
                    () -> openMain(c.player()));
            });
        gui.set(38, Ui.icon(Material.ANVIL, "create", "<green>Create an auction",
            "<gray>Sell the item in your main hand.", "", "<yellow>Click to start"), c -> openCreate(c.player(), null));
        gui.set(40, Ui.icon(Material.WRITABLE_BOOK, "my_auctions", "<green>My auctions",
            "<gray>Your listings and bids.", "", "<yellow>Click to open"), c -> openMine(c.player()));
        gui.set(42, Ui.icon(Material.BOOK, "history", "<aqua>Recent sales",
            "<gray>What items sold for lately.", "", "<yellow>Click to open"), c -> openHistory(c.player()));
        gui.set(49, Ui.plain(Material.BARRIER, "<red>Close"), Gui.Click::close);
        gui.set(50, Ui.icon(Material.CHEST, "claim", claim.empty() ? "<gray>Nothing to claim" : "<green>Claim everything",
            claim.empty() ? "<dark_gray>Won items and sold proceeds show up here."
                : "<gray>Coins: <gold>" + Money.coins(claim.cents()),
            claim.empty() ? "" : "<gray>Items: <white>" + claim.items().size(), "",
            claim.empty() ? "" : "<yellow>Click to collect"), c -> claimAndRefresh(c, () -> openMain(c.player())));
        gui.open(player);
    }

    private ItemStack listingIcon(AuctionBook.Auction a, long now, Player viewer) {
        ItemStack base = ah.stack(a);
        if (base == null) {
            return Ui.plain(Material.BARRIER, "<red>Unreadable item", "<gray>Auction #" + a.id);
        }
        String me = viewer.getUniqueId().toString();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Seller: <white>" + Ui.esc(a.sellerName));
        if (a.active()) {
            if (a.bin) {
                lore.add("<gray>Buy It Now: <gold>" + Money.coins(a.startCents));
            } else if (a.bids == 0) {
                lore.add("<gray>Starting bid: <gold>" + Money.coins(a.startCents));
            } else {
                lore.add("<gray>Top bid: <gold>" + Money.coins(a.bidCents) + " <dark_gray>(" + a.bids + " bids)");
                lore.add("<gray>Bidder: <white>" + Ui.esc(a.bidderName)
                    + (a.bidder.equals(me) ? " <green>(you)" : ""));
            }
            lore.add("<gray>Ends in: <yellow>" + Msg.duration(a.endsAt - now));
        } else {
            lore.add("<gray>Status: <white>" + a.state.toLowerCase(Locale.ROOT));
            if (AuctionBook.SOLD.equals(a.state)) {
                lore.add("<gray>Sold for: <gold>" + Money.coins(a.finalCents));
            }
        }
        long avg = ah.averageSale(a.itemKey);
        if (avg > 0) {
            lore.add("<gray>Recent price each: <aqua>" + Money.coins(avg));
        }
        lore.add("<dark_gray>#" + a.id);
        ItemStack icon = Ui.withLore(base, lore);
        icon.setAmount(Math.max(1, Math.min(a.amount, icon.getMaxStackSize())));
        return icon;
    }

    /** The icon with extra lore lines, keeping the stack size. */
    private static ItemStack more(ItemStack icon, String... lines) {
        ItemStack out = Ui.withLore(icon, Arrays.asList(lines));
        out.setAmount(icon.getAmount());
        return out;
    }

    void openBrowse(Player player, AuctionService.Category cat, String search, AuctionService.Sort sort) {
        openBrowse(player, cat, search, sort, AuctionService.Kind.ALL);
    }

    void openBrowse(Player player, AuctionService.Category cat, String search, AuctionService.Sort sort,
            AuctionService.Kind kind) {
        long now = System.currentTimeMillis();
        List<AuctionBook.Auction> found = ah.browse(cat, search, sort, kind, now);
        String title = search != null ? "<gold>Search: " + Ui.esc(search)
            : cat != null ? "<gold>Auctions: " + cat.label : "<gold>All auctions";
        Gui gui = core.guis().create(6, title);
        gui.border(Ui.glass());
        gui.paginate(found, gui.innerSlots(), a -> {
            return more(listingIcon(a, now, player), a.bin ? "<yellow>Click to view and buy" : "<yellow>Click to view and bid");
        }, (c, a) -> openDetail(c.player(), a.id, () -> openBrowse(c.player(), cat, search, sort, kind)), 45, 53);
        gui.set(49, Ui.plain(Material.ARROW, "<yellow>Back to the Auction House"), c -> openMain(c.player()));
        gui.set(48, Ui.plain(Material.HOPPER, "<aqua>Sort: " + sort.label, "<gray>Click to change the order."),
            c -> openBrowse(c.player(), cat, search, sort.next(), kind));
        gui.set(50, Ui.plain(kind == AuctionService.Kind.BIN ? Material.EMERALD
            : kind == AuctionService.Kind.AUCTION ? Material.BELL : Material.COMPASS, "<aqua>Show: " + kind.label,
            "<gray>Click to switch between all listings,", "<gray>Buy It Now only and auctions only."),
            c -> openBrowse(c.player(), cat, search, sort, kind.next()));
        if (found.isEmpty()) {
            gui.set(22, Ui.plain(Material.STRUCTURE_VOID, "<gray>No listings here", "<dark_gray>Be the first: /ah sell"));
        }
        gui.open(player);
    }

    void openDetail(Player player, long id, Runnable back) {
        AuctionBook.Auction a = ah.book().byId(id);
        long now = System.currentTimeMillis();
        if (a == null || !a.active() || a.endsAt <= now) {
            core.msg().error(player, "That listing has ended.");
            back.run();
            return;
        }
        Gui gui = core.guis().create(4, "<gold>Auction #" + id);
        gui.fill(Ui.glass());
        gui.set(13, listingIcon(a, now, player));
        gui.set(31, Ui.plain(Material.ARROW, "<yellow>Back"), c -> back.run());
        if (a.bin) {
            gui.set(22, Ui.icon(Material.GOLD_INGOT, "buy_it_now", "<green>Buy It Now: " + Money.coins(a.startCents),
                "<gray>The item is yours at once;", "<gray>collect it with /ah claim.", "", "<yellow>Click to buy"), c -> {
                    say(c.player(), ah.buyNow(c.player(), id));
                    back.run();
                });
        } else {
            long min = ah.book().minBid(a);
            long bigger = Math.max(min, ah.price(a) + Math.max(min - ah.price(a), ah.price(a) / 4));
            gui.set(20, Ui.icon(Material.GOLD_NUGGET, "bid", "<green>Bid " + Money.coins(min),
                "<gray>The minimum bid.", "<gray>Outbid coins return to /ah claim.", "", "<yellow>Click to bid"), c -> {
                    say(c.player(), ah.bid(c.player(), id, min));
                    openDetail(c.player(), id, back);
                });
            gui.set(24, Ui.icon(Material.GOLD_INGOT, "bid", "<green>Bid " + Money.coins(bigger),
                "<gray>A stronger bid to scare off rivals.", "", "<yellow>Click to bid"), c -> {
                    say(c.player(), ah.bid(c.player(), id, bigger));
                    openDetail(c.player(), id, back);
                });
            gui.set(22, Ui.plain(Material.OAK_SIGN, "<aqua>Custom bid", "<gray>Click, then type your bid in chat.",
                "<dark_gray>Or /ah bid " + id + " [amount]", "<dark_gray>Last-minute bids extend the auction."), c -> {
                    if (prompts == null) {
                        core.msg().info(c.player(), "Type /ah bid " + id + " [amount].");
                        return;
                    }
                    c.close();
                    prompts.ask(c.player(), "How much do you bid on auction #" + id + " (minimum "
                        + Money.coins(min) + ")?", text -> {
                            OptionalLong cents = Money.parse(text);
                            if (cents.isEmpty()) {
                                core.msg().error(c.player(), "'" + text + "' is not a valid amount.");
                                Sfx.error(c.player());
                            } else {
                                say(c.player(), ah.bid(c.player(), id, cents.getAsLong()));
                            }
                            openDetail(c.player(), id, back);
                        }, () -> openDetail(c.player(), id, back));
                });
        }
        if (a.seller.equals(player.getUniqueId().toString()) && a.bids == 0) {
            gui.set(35, Ui.plain(Material.RED_STAINED_GLASS_PANE, "<red>Cancel this listing",
                "<gray>Get your item back (the fee is kept).", "", "<yellow>Click to cancel"), c -> {
                    say(c.player(), ah.cancel(c.player(), id));
                    back.run();
                });
        }
        gui.open(player);
    }

    void openMine(Player player) {
        String me = player.getUniqueId().toString();
        long now = System.currentTimeMillis();
        List<AuctionBook.Auction> mine = ah.book().ownedBy(me);
        Gui gui = core.guis().create(6, "<green>My auctions");
        gui.border(Ui.glass());
        gui.paginate(mine, gui.innerSlots(), a -> {
            List<String> lines = new ArrayList<>();
            boolean seller = a.seller.equals(me);
            lines.add(seller ? "<green>You are selling this" : "<aqua>You are bidding or won");
            if (a.active() && seller && a.bids == 0) {
                lines.add("<red>Right-click to cancel");
            }
            boolean ready = !a.active() && ((seller && !a.sellerClaimed) || (!seller && !a.buyerClaimed));
            if (ready) {
                lines.add("<yellow>Click to claim");
            }
            return more(listingIcon(a, now, player), lines.toArray(new String[0]));
        }, (c, a) -> {
            boolean seller = a.seller.equals(me);
            if (c.right() && a.active() && seller && a.bids == 0) {
                say(c.player(), ah.cancel(c.player(), a.id));
                openMine(c.player());
            } else if (!a.active()) {
                claimAndRefresh(c, () -> openMine(c.player()));
            } else {
                openDetail(c.player(), a.id, () -> openMine(c.player()));
            }
        }, 45, 53);
        gui.set(49, Ui.plain(Material.ARROW, "<yellow>Back to the Auction House"), c -> openMain(c.player()));
        gui.set(50, Ui.plain(Material.CHEST, "<green>Claim everything"), c -> claimAndRefresh(c, () -> openMine(c.player())));
        gui.open(player);
    }

    void openHistory(Player player) {
        List<AuctionBook.Sale> sales = new ArrayList<>(ah.book().state().sales);
        java.util.Collections.reverse(sales);
        Gui gui = core.guis().create(6, "<aqua>Recent sales");
        gui.border(Ui.glass());
        gui.paginate(sales, gui.innerSlots(), s -> {
            ItemStack base = keys.create(s.itemKey, 1).orElseGet(() -> new ItemStack(Material.PAPER));
            return Ui.withLore(base, List.of("", "<white>" + Ui.esc(s.name) + " <gray>x" + s.amount,
                "<gray>Sold for: <gold>" + Money.coins(s.cents), "<gray>Each: <gold>" + Money.coins(s.cents / Math.max(1, s.amount)),
                "<gray>" + (s.bin ? "Buy It Now" : "Auction") + ", <dark_gray>"
                    + Msg.duration(System.currentTimeMillis() - s.t) + " ago"));
        }, (c, s) -> { }, 45, 53);
        gui.set(49, Ui.plain(Material.ARROW, "<yellow>Back to the Auction House"), c -> openMain(c.player()));
        gui.open(player);
    }

    /** The "create an auction" menu: the held item, the price, the kind and the duration. */
    void openCreate(Player player, long[] state) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String why = ah.refuseItem(hand);
        if (why != null) {
            core.msg().error(player, why);
            return;
        }
        String key = keys.keyOf(hand);
        if (state == null) {
            long suggested = key == null ? 0 : ah.averageSale(key) * hand.getAmount();
            long start = Math.max(ah.book().rules().minPriceCents(), suggested > 0 ? suggested : 100_00);
            state = new long[] {start, 0, ah.settings().defaultHours()};
        }
        renderCreate(player, state);
    }

    private void renderCreate(Player player, long[] st) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String why = ah.refuseItem(hand);
        if (why != null) {
            core.msg().error(player, why);
            return;
        }
        AuctionBook.Rules rules = ah.book().rules();
        st[0] = Math.max(rules.minPriceCents(), Math.min(rules.maxPriceCents(), st[0]));
        boolean bin = st[1] == 1;
        int hours = (int) st[2];
        long fee = ah.settings().fee(st[0], hours);
        Gui gui = core.guis().create(5, "<green>Create an auction");
        gui.fill(Ui.glass());
        ItemStack show = Ui.withLore(hand, List.of("", "<gray>This is the item in your main hand."));
        show.setAmount(Math.max(1, Math.min(hand.getAmount(), show.getMaxStackSize())));
        gui.set(4, show);
        gui.set(13, Ui.plain(Material.GOLD_INGOT, "<gold>" + (bin ? "Buy It Now price" : "Starting bid") + ": "
            + Money.coins(st[0]), "<gray>Use the buttons below,", "<gray>or click here and type a price.", "",
            "<dark_gray>Or /ah sell [price] [bin] [hours]"), c -> {
                if (prompts == null) {
                    return;
                }
                c.close();
                prompts.ask(c.player(), "What price (examples: 25000, 1.5m)?", text -> {
                    OptionalLong cents = Money.parse(text);
                    if (cents.isEmpty()) {
                        core.msg().error(c.player(), "'" + text + "' is not a valid price.");
                        Sfx.error(c.player());
                    } else {
                        st[0] = cents.getAsLong();
                    }
                    renderCreate(c.player(), st);
                }, () -> renderCreate(c.player(), st));
            });
        record Step(String name, int slot, LongUnaryOperator f, Material glass) {}
        List<Step> steps = List.of(
            new Step("Half", 19, v -> v / 2, Material.RED_STAINED_GLASS_PANE),
            new Step("-10%", 20, v -> v - v / 10, Material.RED_STAINED_GLASS_PANE),
            new Step("-1k coins", 21, v -> v - 100_000, Material.RED_STAINED_GLASS_PANE),
            new Step("+1k coins", 23, v -> v + 100_000, Material.LIME_STAINED_GLASS_PANE),
            new Step("+10%", 24, v -> v + Math.max(100, v / 10), Material.LIME_STAINED_GLASS_PANE),
            new Step("Double", 25, v -> v * 2, Material.LIME_STAINED_GLASS_PANE));
        for (Step s : steps) {
            gui.set(s.slot(), Ui.plain(s.glass(), (s.glass() == Material.RED_STAINED_GLASS_PANE ? "<red>" : "<green>")
                + s.name(), "<gray>Changes the price."), c -> {
                    st[0] = s.f().applyAsLong(st[0]);
                    renderCreate(c.player(), st);
                });
        }
        gui.set(29, Ui.plain(bin ? Material.EMERALD : Material.BELL, "<aqua>Type: " + (bin ? "Buy It Now" : "Auction"),
            bin ? "<gray>A fixed price, bought at once." : "<gray>Players bid up the price.", "",
            "<yellow>Click to switch"), c -> {
                st[1] = bin ? 0 : 1;
                renderCreate(c.player(), st);
            });
        gui.set(31, Ui.plain(Material.CLOCK, "<aqua>Duration: " + hours + "h", "<gray>Longer listings cost a bigger fee.", "",
            "<yellow>Click to change"), c -> {
                List<Integer> options = ah.settings().durations();
                st[2] = options.get((options.indexOf((int) st[2]) + 1) % options.size());
                renderCreate(c.player(), st);
            });
        gui.set(33, Ui.plain(Material.PAPER, "<gold>Listing fee: " + Money.coins(fee),
            "<gray>Paid now, never refunded.", "<gray>Sale tax: <yellow>" + (rules.saleTaxBp() / 100.0) + "%"));
        gui.set(39, Ui.plain(Material.RED_CONCRETE, "<red>Cancel"), c -> openMain(c.player()));
        gui.set(41, Ui.icon(Material.LIME_CONCRETE, "confirm", "<green>List it", "<gray>" + hand.getAmount() + "x "
            + Ui.esc(ah.displayName(hand)), "<gray>Price: <gold>" + Money.coins(st[0]), "<gray>Fee: <gold>" + Money.coins(fee),
            "", "<yellow>Click to confirm"), c -> {
                say(c.player(), ah.list(c.player(), st[0], bin, hours));
                openMain(c.player());
            });
        gui.open(player);
    }
}
