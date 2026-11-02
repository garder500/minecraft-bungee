package win.vampyuyu.survival.trades;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.MinerBoostApi;
import win.vampyuyu.survival.api.Trade;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;

/** {@code /trade}: status, choice, and the two abilities (Overclock, Mend). Never required by any content (GDD 11.4). */
final class TradeCommand implements CommandExecutor, TabCompleter {
    static final String ADMIN = "vampyuyu.sb.trades.admin";

    private final Core core;
    private final TradeService service;
    private final Cooldowns cooldowns = new Cooldowns();

    TradeCommand(Core core, TradeService service) {
        this.core = core;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Players only.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status", "info" -> status(player);
            case "choose" -> choose(player, args);
            case "drop" -> drop(player, args);
            case "overclock" -> overclock(player);
            case "mend" -> mend(player);
            case "xp" -> xp(player, args);
            default -> core.msg().info(player, "/trade [status | choose <artisan|warden> | drop <trade> | overclock | mend]");
        }
        return true;
    }

    private void status(Player player) {
        TradeData d = service.data(player.getUniqueId());
        TradeConfig cfg = service.config();
        core.msg().mini(player, "<gold><bold>Trades</bold> <gray>- " + d.trades.size() + "/" + TradeRules.slots(d, cfg)
            + " held. A trade is never required: it only makes some things faster.");
        for (Trade t : Trade.values()) {
            int level = TradeRules.level(d, t, cfg);
            String effect = t == Trade.ARTISAN
                ? "Forge " + pct(1 - cfg.forgeFactor()) + " faster, Miners " + pct(1 - cfg.minerEnergyFactor())
                    + " less Flux, Overclock (/trade overclock)"
                : "Ranch and Alembic " + pct(cfg.yieldFactor() - 1) + " yield, Mend (/trade mend)";
            if (level == 0) {
                core.msg().mini(player, "<white>" + t.title() + " <dark_gray>not taken <gray>- " + effect);
                continue;
            }
            long xp = d.xp.getOrDefault(t.id(), 0L);
            long next = TradeRules.toNext(xp, cfg);
            core.msg().mini(player, "<green>" + t.title() + " <white>level " + level + "/" + Trade.MAX_LEVEL + " <gray>("
                + xp + " xp" + (next > 0 ? ", " + next + " to the next level" : ", Master") + ") - " + effect);
        }
        if (cfg.secondTradeAtLevel() > 0) {
            core.msg().mini(player, "<gray>A second trade opens once one of them reaches level " + cfg.secondTradeAtLevel() + ".");
        }
    }

    private Trade parse(Player player, String[] args, String usage) {
        Trade trade = args.length < 2 ? null : Trade.parse(args[1]);
        if (trade == null) {
            core.msg().error(player, "Usage: " + usage);
        }
        return trade;
    }

    private void choose(Player player, String[] args) {
        Trade trade = parse(player, args, "/trade choose <artisan|warden>");
        if (trade == null) {
            return;
        }
        TradeData d = service.data(player.getUniqueId());
        switch (TradeRules.choose(d, trade, service.config())) {
            case ALREADY -> core.msg().error(player, "You already practise " + trade.title() + ".");
            case FULL -> core.msg().error(player, "You practise as many trades as you may. A second one opens at level "
                + service.config().secondTradeAtLevel() + " of the first (or drop one with /trade drop).");
            case OK -> {
                core.profiles().markDirty(player.getUniqueId());
                core.msg().ok(player, "You are now " + trade.title() + ". It levels up as you use it.");
            }
        }
    }

    private void drop(Player player, String[] args) {
        Trade trade = parse(player, args, "/trade drop <artisan|warden>");
        if (trade == null) {
            return;
        }
        TradeData d = service.data(player.getUniqueId());
        if (!d.trades.remove(trade.id())) {
            core.msg().error(player, "You do not practise " + trade.title() + ".");
            return;
        }
        // The experience is kept: taking the trade again picks up where you stopped.
        core.profiles().markDirty(player.getUniqueId());
        core.msg().ok(player, "You gave up " + trade.title() + " (your experience is kept).");
    }

    private void overclock(Player player) {
        TradeConfig cfg = service.config();
        if (service.level(player.getUniqueId(), Trade.ARTISAN) < cfg.overclockLevel()) {
            core.msg().error(player, "Overclock needs the Artisan trade at level " + cfg.overclockLevel() + ".");
            return;
        }
        MinerBoostApi boost = core.service(MinerBoostApi.class).orElse(null);
        if (boost == null) {
            core.msg().error(player, "Miners are not available on this server.");
            return;
        }
        long left = cooldowns.remaining(player.getUniqueId(), "overclock");
        if (left > 0) {
            core.msg().error(player, "Overclock is ready in " + (left / 1000 + 1) + " s.");
            return;
        }
        if (!boost.overclockNearest(player.getUniqueId(), player.getLocation(), cfg.overclockRange(),
                cfg.overclockSeconds() * 1000, cfg.overclockSpeed())) {
            core.msg().error(player, "None of your Miners is within " + (int) cfg.overclockRange() + " blocks.");
            return;
        }
        cooldowns.set(player.getUniqueId(), "overclock", cfg.overclockCooldownSeconds() * 1000);
        core.msg().ok(player, "Overclock: your nearest Miner runs " + (cfg.overclockSpeed() == Math.rint(cfg.overclockSpeed())
            ? String.valueOf((long) cfg.overclockSpeed()) : String.valueOf(cfg.overclockSpeed())) + "x faster for "
            + cfg.overclockSeconds() + " s (and draws that much more Flux).");
    }

    private void mend(Player player) {
        TradeConfig cfg = service.config();
        if (service.level(player.getUniqueId(), Trade.WARDEN) < cfg.mendLevel()) {
            core.msg().error(player, "Mend needs the Warden trade at level " + cfg.mendLevel() + ".");
            return;
        }
        long left = cooldowns.remaining(player.getUniqueId(), "mend");
        if (left > 0) {
            core.msg().error(player, "Mend is ready in " + (left / 1000 + 1) + " s.");
            return;
        }
        Player target = null;
        double best = cfg.mendRange() * cfg.mendRange();
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || other.isDead()) {
                continue;
            }
            double d = other.getLocation().distanceSquared(player.getLocation());
            if (d <= best) {
                best = d;
                target = other;
            }
        }
        if (target == null) {
            core.msg().error(player, "No ally within " + (int) cfg.mendRange() + " blocks.");
            return;
        }
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        target.setHealth(Math.min(cap, target.getHealth() + cap * cfg.mendFraction()));
        cooldowns.set(player.getUniqueId(), "mend", cfg.mendCooldownSeconds() * 1000);
        service.addXp(player.getUniqueId(), Trade.WARDEN, 3);
        core.msg().ok(player, "You mended " + target.getName() + ".");
        core.msg().ok(target, player.getName() + " mended you.");
    }

    /** Admin: {@code /trade xp <trade> <amount>}. */
    private void xp(Player player, String[] args) {
        if (!player.hasPermission(ADMIN)) {
            core.msg().error(player, "You cannot do that.");
            return;
        }
        Trade trade = args.length < 3 ? null : Trade.parse(args[1]);
        long amount;
        try {
            amount = args.length < 3 ? 0 : Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (trade == null || amount <= 0) {
            core.msg().info(player, "Usage: /trade xp <artisan|warden> <amount>");
            return;
        }
        service.addXp(player.getUniqueId(), trade, (int) Math.min(Integer.MAX_VALUE, amount));
        core.msg().ok(player, "Added " + amount + " xp (only counts if you practise the trade).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("status", "choose", "drop", "overclock", "mend"));
            if (sender.hasPermission(ADMIN)) {
                out.add("xp");
            }
        } else if (args.length == 2 && List.of("choose", "drop", "xp").contains(args[0].toLowerCase(Locale.ROOT))) {
            Arrays.stream(Trade.values()).forEach(t -> out.add(t.id()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }

    private static String pct(double fraction) {
        return Math.round(fraction * 100) + "%";
    }
}
