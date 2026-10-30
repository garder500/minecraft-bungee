package win.vampyuyu.survival.dungeon.run;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.dungeon.DungeonItems;
import win.vampyuyu.survival.dungeon.RewardDelivery;

/** The Crypt Merchant: coins for revive leaves and healing flasks. Needs the economy feature. */
final class Shop {
    private Shop() {}

    static double price(DungeonRun run, String item) {
        double base = DungeonItems.SPIRIT_LEAF.equals(item) ? run.ctx.cfg.shopLeafPrice : run.ctx.cfg.shopFlaskPrice;
        return Math.max(0, Math.floor(base * run.floor.reward()));
    }

    static void open(DungeonRun run, Player p) {
        var gui = run.ctx.core.guis().create(3, "<gold><bold>Crypt Merchant");
        gui.fill(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        boolean eco = run.ctx.economy().isPresent();
        good(run, gui, 11, DungeonItems.SPIRIT_LEAF, "<green>Spirit Leaf", "<gray>Revives a ghost of your party instantly.");
        good(run, gui, 15, DungeonItems.HEALING_FLASK, "<aqua>Healing Flask",
            "<gray>Restores " + run.ctx.cfg.flaskHealPercent + "% of your health.");
        gui.set(22, Guis.icon(Material.BARRIER, "<red>Close"), c -> c.close());
        if (!eco) {
            gui.set(4, Guis.icon(Material.BARRIER, "<red>The merchant has no coins to count", "<gray>The economy is not available."));
        }
        gui.open(p);
    }

    private static void good(DungeonRun run, win.vampyuyu.survival.core.Gui gui, int slot, String item, String name,
            String description) {
        double price = price(run, item);
        gui.set(slot, Guis.icon(item.equals(DungeonItems.SPIRIT_LEAF) ? Material.FERN : Material.GLASS_BOTTLE, name,
            description, "", "<gray>Price: <gold>" + Msg.coins(price), "<yellow>Click to buy"),
            click -> buy(run, click.player(), item, price));
    }

    static void buy(DungeonRun run, Player p, String item, double price) {
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null || rp.left || run.state.over()) {
            return;
        }
        EconomyApi eco = run.ctx.economy().orElse(null);
        if (eco == null) {
            run.say(p, "<red>The merchant cannot trade right now.");
            return;
        }
        ItemStack stack = RewardDelivery.make(run.ctx.core, item, 1);
        if (stack == null) {
            run.say(p, "<red>That item is not available.");
            return;
        }
        if (!hasRoom(p, stack)) {
            run.say(p, "<red>Your inventory is full.");
            return;
        }
        if (!eco.take(p.getUniqueId(), price, "dungeon:shop")) {
            run.say(p, "<red>You need " + Msg.coins(price) + ".");
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        if (!left.isEmpty()) {
            eco.add(p.getUniqueId(), price, "dungeon:shop-refund");
            run.say(p, "<red>Your inventory is full.");
            return;
        }
        run.say(p, "<green>Bought for <gold>" + Msg.coins(price) + "<green>.");
    }

    private static boolean hasRoom(Player p, ItemStack stack) {
        if (p.getInventory().firstEmpty() != -1) {
            return true;
        }
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.isSimilar(stack) && s.getAmount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }
}
