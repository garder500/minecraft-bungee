package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/**
 * The Crypt Forge: the sink for dungeon materials. Trades are defined in {@code dungeon.yml}. A purchase is always
 * validate, roll, verify the count, remove the payment, deliver: everything runs on the main thread and nothing is
 * given before the exact payment has been taken, so a trade can neither duplicate nor lose items. Items that do not
 * fit go to the pending list ({@code /dungeon claim}).
 */
public final class Forge {
    /** Outcome of a purchase attempt. */
    public record Result(boolean ok, String message) {}

    private final DungeonService svc;
    private final Random rng = new Random();

    public Forge(DungeonService svc) {
        this.svc = svc;
    }

    public DungeonConfig.ForgeTrade trade(String id) {
        for (DungeonConfig.ForgeTrade t : svc.cfg().forgeTrades) {
            if (t.id().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ counting

    private boolean matches(String id, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return false;
        }
        if (id.regionMatches(true, 0, "mc:", 0, 3)) {
            Material m = Material.matchMaterial(id.substring(3).toUpperCase(Locale.ROOT));
            return m != null && stack.getType() == m && !svc.core.items().isCustom(stack);
        }
        return svc.core.items().is(stack, id);
    }

    /** How many of an item the player carries (main inventory and hotbar). */
    public int count(Player p, String id) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (matches(id, s)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private void take(Player p, String id, int amount) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (!matches(id, s)) {
                continue;
            }
            int n = Math.min(left, s.getAmount());
            left -= n;
            if (n >= s.getAmount()) {
                contents[i] = null;
            } else {
                s.setAmount(s.getAmount() - n);
            }
        }
        inv.setStorageContents(contents);
    }

    static String pretty(String id) {
        return id.replaceFirst("^dungeon_", "").replaceFirst("^mc:", "").replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ purchase

    /** Why the trade cannot work at all right now (missing item definition, no economy), or null. */
    private String unavailable(DungeonConfig.ForgeTrade t) {
        if (!t.give().isEmpty() && RewardDelivery.make(svc.core, t.give(), 1) == null) {
            return "This trade is unavailable (unknown item " + t.give() + "). Tell an administrator.";
        }
        if (t.coins() > 0 && svc.ctx.economy().isEmpty()) {
            return "The economy is not available right now.";
        }
        for (String id : t.cost().keySet()) {
            if (!id.regionMatches(true, 0, "mc:", 0, 3) && svc.core.items().def(id).isEmpty()) {
                return "This trade is unavailable (unknown item " + id + "). Tell an administrator.";
            }
        }
        return null;
    }

    public Result buy(Player p, DungeonConfig.ForgeTrade t) {
        if (p == null || !p.isOnline() || t == null) {
            return new Result(false, "Nothing to buy.");
        }
        if (svc.runs.runOf(p.getUniqueId()) != null) {
            return new Result(false, "The forge is closed while you are in a dungeon run.");
        }
        String problem = unavailable(t);
        if (problem != null) {
            return new Result(false, problem);
        }
        for (Map.Entry<String, Integer> c : t.cost().entrySet()) {
            int have = count(p, c.getKey());
            if (have < c.getValue()) {
                return new Result(false, "You need " + (c.getValue() - have) + " more " + pretty(c.getKey()) + ".");
            }
        }
        // roll first: if it cannot produce anything nothing has been taken yet
        List<RewardTable.Drop> drops = List.of();
        if (t.cacheRolls() > 0) {
            DungeonData data = svc.core.profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
            int floor = Math.max(1, Math.min(svc.cfg().floorCount(), data.highestFloorCleared));
            drops = RewardTable.rollN(svc.cfg(), floor, t.cacheGrade(), t.cacheRolls(), rng);
            if (drops.isEmpty()) {
                return new Result(false, "The cache is empty. Tell an administrator.");
            }
        }
        for (Map.Entry<String, Integer> c : t.cost().entrySet()) {
            take(p, c.getKey(), c.getValue());
        }
        StringBuilder got = new StringBuilder();
        if (!t.give().isEmpty()) {
            RewardDelivery.give(svc.core, p.getUniqueId(), p, t.give(), t.amount());
            got.append(t.amount()).append("x ").append(pretty(t.give()));
        } else if (t.cacheRolls() > 0) {
            for (RewardTable.Drop d : drops) {
                RewardDelivery.give(svc.core, p.getUniqueId(), p, d.item(), d.amount());
                if (got.length() > 0) {
                    got.append(", ");
                }
                got.append(d.amount()).append("x ").append(pretty(d.item()));
            }
        } else {
            double coins = t.coins();
            svc.ctx.economy().ifPresent(e -> e.add(p.getUniqueId(), coins, "dungeon:forge:" + t.id()));
            got.append(Msg.coins(coins));
        }
        return new Result(true, t.name() + ": you received " + got + ".");
    }

    // ------------------------------------------------------------------ menu

    private Material icon(DungeonConfig.ForgeTrade t) {
        Material m = Material.matchMaterial(t.icon());
        return m == null || !m.isItem() || m.isAir() ? Material.CHEST : m;
    }

    public void open(Player p) {
        Gui gui = svc.core.guis().create(4, "<dark_red><bold>Crypt Forge");
        gui.border(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        gui.paginate(new ArrayList<>(svc.cfg().forgeTrades), gui.innerSlots(), t -> tradeIcon(p, t), (click, t) -> {
            Result r = buy(click.player(), t);
            if (r.ok()) {
                svc.core.msg().ok(click.player(), r.message());
            } else {
                svc.core.msg().error(click.player(), r.message());
            }
            svc.core.scheduler().nextTick(() -> {
                if (click.player().isOnline()) {
                    open(click.player());
                }
            });
        }, 30, 32);
        gui.set(31, Guis.icon(Material.BARRIER, "<red>Close"), c -> c.close());
        gui.open(p);
    }

    private ItemStack tradeIcon(Player p, DungeonConfig.ForgeTrade t) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Cost:");
        boolean affordable = true;
        for (Map.Entry<String, Integer> c : t.cost().entrySet()) {
            int have = count(p, c.getKey());
            boolean enough = have >= c.getValue();
            affordable &= enough;
            lore.add((enough ? "<green>" : "<red>") + c.getValue() + "x " + pretty(c.getKey()) + " <dark_gray>(you have " + have + ")");
        }
        lore.add("");
        if (!t.give().isEmpty()) {
            lore.add("<gray>You get: <white>" + t.amount() + "x " + pretty(t.give()));
        } else if (t.cacheRolls() > 0) {
            lore.add("<gray>You get: <white>" + t.cacheRolls() + " item rolls <gray>(rank " + t.cacheGrade().label() + " table)");
        } else {
            lore.add("<gray>You get: <gold>" + Msg.coins(t.coins()));
        }
        lore.add("");
        lore.add(affordable ? "<yellow>Click to trade" : "<red>Not enough materials");
        return Guis.icon(icon(t), (affordable ? "<gold><bold>" : "<gray><bold>") + t.name(), lore.toArray(new String[0]));
    }

    /** Core accessor for tests. */
    Core core() { return svc.core; }
}
