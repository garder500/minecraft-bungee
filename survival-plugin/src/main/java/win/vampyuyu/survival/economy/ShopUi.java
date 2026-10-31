package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Msg;

/** Merchant menus: a shop, the buyback list and the /shop and /sellall commands. */
final class ShopUi {
    private final Core core;
    private final EconomyService economy;
    private final ItemKeys keys;
    private final ShopService shops;

    ShopUi(Core core, EconomyService economy, ItemKeys keys, ShopService shops) {
        this.core = core;
        this.economy = economy;
        this.keys = keys;
        this.shops = shops;
    }

    void register() {
        core.commands().command("shop")
            .description("Open a merchant menu from anywhere (staff)")
            .usage("/shop [merchant]")
            .permission("vampyuyu.sb.economy.shop.remote", PermissionDefault.OP)
            .executor((s, c, l, a) -> {
                if (!(s instanceof Player p)) {
                    core.msg().error(s, "Only players can shop.");
                    return true;
                }
                if (a.length == 0) {
                    openHub(p);
                    return true;
                }
                var shop = shops.catalog().shop(a[0]);
                if (shop.isEmpty()) {
                    core.msg().error(s, "Unknown merchant. Try: " + String.join(", ", shops.catalog().shops().keySet()));
                } else {
                    open(p, shop.get());
                }
                return true;
            })
            .tab((s, c, l, a) -> a.length == 1
                ? Targets.filter(new ArrayList<>(shops.catalog().shops().keySet()), a[0]) : List.of()).register();
        core.commands().command("market")
            .description("Open the market: merchants that buy and sell everyday items")
            .usage("/market")
            .permission("vampyuyu.sb.economy.market", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> {
                if (s instanceof Player p) {
                    openHub(p);
                } else {
                    core.msg().error(s, "Only players can shop.");
                }
                return true;
            }).register();
        core.commands().command("sellall").aliases("sellinventory")
            .description("Sell everything the merchants would buy from your inventory")
            .usage("/sellall")
            .permission("vampyuyu.sb.economy.sellall", PermissionDefault.OP)
            .executor((s, c, l, a) -> {
                if (s instanceof Player p) {
                    sellEverything(p, null);
                } else {
                    core.msg().error(s, "Only players can sell.");
                }
                return true;
            }).register();
    }

    // ---------------------------------------------------------------- menus

    void openHub(Player player) {
        var all = new ArrayList<>(shops.catalog().shops().values());
        Gui gui = core.guis().create(Math.max(3, Math.min(6, 2 + (all.size() + 6) / 7)), "<gold>Merchants");
        gui.border(Ui.glass());
        gui.paginate(all, gui.innerSlots(), shop -> Ui.plain(shop.icon(), shop.npcName(),
            "<gray>" + shop.description(), "", "<yellow>Click to browse"), (c, shop) -> open(c.player(), shop),
            gui.size() - 9, gui.size() - 1);
        gui.open(player);
    }

    void open(Player player, ShopCatalog.ShopDef shop) {
        List<ShopCatalog.ShopItem> items = shop.items().stream().filter(i -> keys.known(i.key())).toList();
        Gui gui = core.guis().create(6, shop.npcName());
        gui.border(Ui.glass());
        Gui.Pager<ShopCatalog.ShopItem>[] holder = newHolder();
        holder[0] = gui.paginate(items, gui.innerSlots(), i -> icon(player, shop, i),
            (click, item) -> {
                clicked(click, shop, item);
                holder[0].render();
            }, 45, 53);
        refreshBar(gui, player, shop);
        gui.open(player);
    }

    private void refreshBar(Gui gui, Player player, ShopCatalog.ShopDef shop) {
        long purse = economy.balanceCents(player.getUniqueId());
        gui.set(49, Ui.icon(Material.GOLD_INGOT, "coin", "<gold>Your purse", "<gray>" + Money.coins(purse), "",
            "<dark_gray>Left-click an item: buy 1", "<dark_gray>Shift-left-click: buy a stack",
            "<dark_gray>Right-click: sell 1", "<dark_gray>Shift-right-click: sell all of it"));
        boolean buys = shop.items().stream().anyMatch(i -> i.sellCents() > 0);
        if (buys) {
            gui.set(48, Ui.icon(Material.HOPPER, "sell_all", "<green>Sell everything",
                "<gray>Sells every item in your inventory", "<gray>that " + Ui.esc(shop.name()) + " buys.", "",
                "<yellow>Click to sell", "<dark_gray>You can buy items back for a few minutes."), c -> {
                    sellEverything(c.player(), shop);
                    refreshBar(gui, c.player(), shop);
                });
        }
        gui.set(50, Ui.icon(Material.ENDER_CHEST, "buyback", "<aqua>Buyback",
            "<gray>Items you sold lately.", "<gray>Buy them back for what they paid.", "",
            "<yellow>Click to open"), c -> openBuyback(c.player(), shop));
        gui.set(46, Ui.plain(Material.BARRIER, "<red>Close"), Gui.Click::close);
    }

    private ItemStack icon(Player player, ShopCatalog.ShopDef shop, ShopCatalog.ShopItem item) {
        ItemStack base = keys.create(item.key(), 1).orElseGet(() -> new ItemStack(Material.BARRIER));
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (item.buyCents() > 0) {
            lore.add("<gray>Buy: <gold>" + Money.coins(item.buyCents()));
            lore.add("<dark_gray>Left-click: 1  |  Shift-left: " + keys.maxStack(item.key()));
        }
        if (item.sellCents() > 0) {
            long now = shops.unitSellCents(item.key(), item.sellCents());
            lore.add("<gray>Sell: <gold>" + Money.coins(now));
            if (now < item.sellCents()) {
                lore.add("<red>Market saturated <dark_gray>(" + (now * 100 / item.sellCents()) + "% of the base price)");
            }
            lore.add("<dark_gray>Right-click: 1  |  Shift-right: all");
        }
        lore.add("");
        lore.add("<gray>You carry: <white>" + Inv.count(player, keys, item.key()));
        return Ui.withLore(base, lore);
    }

    @SuppressWarnings("unchecked")
    private static Gui.Pager<ShopCatalog.ShopItem>[] newHolder() {
        return new Gui.Pager[1];
    }

    private void clicked(Gui.Click click, ShopCatalog.ShopDef shop, ShopCatalog.ShopItem item) {
        Player player = click.player();
        String name = Ui.esc(keys.name(item.key()));
        if (click.left()) {
            int qty = click.shift() ? keys.maxStack(item.key()) : 1;
            ShopService.Result r = shops.buy(player, shop, item, qty);
            if (r.ok()) {
                core.msg().mini(player, "<green>Bought <white>" + qty + "x " + name + "</white> for <gold>"
                    + Money.coins(r.cents()) + "</gold>.");
                Sfx.success(player);
            } else {
                core.msg().error(player, r.error());
                Sfx.error(player);
            }
        } else if (click.right()) {
            if (item.sellCents() <= 0) {
                core.msg().error(player, shop.name() + " does not buy that.");
                Sfx.error(player);
            } else {
                ShopService.Result r = shops.sell(player, shop.id(), k -> k.equals(item.key()) ? item.sellCents() : 0,
                    item.key(), click.shift() ? 0 : 1);
                if (r.ok()) {
                    core.msg().mini(player, "<green>Sold <white>" + r.items() + "x " + name + "</white> for <gold>"
                        + Money.coins(r.cents()) + "</gold>.");
                    Sfx.coins(player);
                } else {
                    core.msg().error(player, r.error());
                    Sfx.error(player);
                }
            }
        }
        refreshBar(click.gui(), player, shop);
    }

    /** Sells everything one merchant (or, with null, any merchant) buys. */
    void sellEverything(Player player, ShopCatalog.ShopDef shop) {
        ShopService.Result r;
        if (shop == null) {
            r = shops.sell(player, "all", k -> shops.catalog().bestSell(k).orElse(0), null, 0);
        } else {
            r = shops.sell(player, shop.id(), k -> shop.item(k).map(ShopCatalog.ShopItem::sellCents).orElse(0L), null, 0);
        }
        if (!r.ok()) {
            core.msg().error(player, r.error());
            Sfx.error(player);
            return;
        }
        Sfx.coins(player);
        core.msg().mini(player, "<green>Sold <white>" + r.items() + " items</white> for <gold>"
            + Money.coins(r.cents()) + "</gold>.");
        r.lines().stream().limit(6).forEach(l -> core.msg().mini(player, "<dark_gray> - <gray>" + l.qty() + "x "
            + Ui.esc(keys.name(l.key())) + " <dark_gray>for <gold>" + Money.plain(l.cents())));
        if (r.lines().size() > 6) {
            core.msg().info(player, "...and " + (r.lines().size() - 6) + " more kinds.");
        }
        core.msg().info(player, "Changed your mind? Open a merchant's Buyback within "
            + Msg.duration(shops.buybackMillis()) + ".");
    }

    void openBuyback(Player player, ShopCatalog.ShopDef back) {
        List<ShopService.BuybackEntry> entries = shops.buybackOf(player.getUniqueId());
        Gui gui = core.guis().create(5, "<aqua>Buyback");
        gui.border(Ui.glass());
        long now = System.currentTimeMillis();
        gui.paginate(entries, gui.innerSlots(), e -> Ui.withLore(e.stack(), List.of("",
            "<gray>Cost: <gold>" + Money.coins(e.paidCents()),
            "<gray>Available for: <yellow>" + Msg.duration(Math.max(0, e.at() + shops.buybackMillis() - now)),
            "", "<yellow>Click to buy back")),
            (c, e) -> {
                ShopService.Result r = shops.repurchase(c.player(), e);
                if (r.ok()) {
                    core.msg().mini(c.player(), "<green>Bought back <white>" + e.stack().getAmount() + "x "
                        + Ui.esc(keys.name(e.key())) + "</white> for <gold>" + Money.coins(r.cents()) + "</gold>.");
                    Sfx.success(c.player());
                    openBuyback(c.player(), back);
                } else {
                    core.msg().error(c.player(), r.error());
                    Sfx.error(c.player());
                }
            }, 36, 44);
        if (entries.isEmpty()) {
            gui.set(22, Ui.plain(Material.PAPER, "<gray>Nothing to buy back", "<dark_gray>Items you sell show up here."));
        }
        gui.set(40, Ui.plain(Material.ARROW, "<yellow>Back"), c -> {
            if (back != null) {
                open(c.player(), back);
            } else {
                c.close();
            }
        });
        gui.open(player);
    }
}
