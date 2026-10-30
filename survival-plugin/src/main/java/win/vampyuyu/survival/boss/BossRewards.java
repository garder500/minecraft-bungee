package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.api.WingsApi;
import win.vampyuyu.survival.api.BossLootHook;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.BossKillEvent.Damager;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** Distributes the rewards of a killed boss: coins, combat xp, rolled drops, hooks, Wings. Exactly once per fight. */
public final class BossRewards {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Core core;
    private final BossSettings settings;
    private final LootDelivery delivery;
    private final List<BossLootHook> hooks = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    public BossRewards(Core core, BossSettings settings, LootDelivery delivery) {
        this.core = core;
        this.settings = settings;
        this.delivery = delivery;
    }

    public void addHook(BossLootHook hook) {
        hooks.add(hook);
    }

    /** Fires the kill event and pays everybody. Main thread. */
    public void distribute(BossSession session, @Nullable Player killer) {
        BossDefinition def = session.def;
        List<Damager> ranking = session.ledger.ranking();
        BossKillEvent event = new BossKillEvent(def.id(), killer, ranking, session.location());
        Bukkit.getPluginManager().callEvent(event);
        BossDefinition.Loot loot = def.loot();
        for (int i = 0; i < ranking.size(); i++) {
            Damager damager = ranking.get(i);
            double share = session.ledger.share(damager.player());
            if (share < loot.minShare()) {
                continue;
            }
            try {
                reward(session, event, damager, share, i + 1, ranking);
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.SEVERE,
                    "Boss reward failed for " + damager.player() + " on " + def.id(), e);
            }
        }
        if (!session.options().personal()) {
            announceLeaderboard(session, ranking);
        }
    }

    private void reward(BossSession session, BossKillEvent event, Damager damager, double share, int rank,
            List<Damager> ranking) {
        BossDefinition def = session.def;
        BossDefinition.Loot loot = def.loot();
        UUID id = damager.player();
        Player player = Bukkit.getPlayer(id);
        double coins = LootMath.share(loot.coins(), share, loot.shareFloor());
        double xp = LootMath.share(loot.combatXp(), share, loot.shareFloor());
        if (coins > 0) {
            core.service(EconomyApi.class).ifPresent(e -> e.add(id, coins, "boss:" + def.id()));
        }
        if (xp > 0 && player != null) {
            core.service(SkillsApi.class).ifPresent(s -> s.addXp(player, Skill.COMBAT, xp, "boss:" + def.id()));
        }
        double mf = player == null ? 0 : core.service(StatsApi.class)
            .map(s -> Math.min(settings.magicFindCap(), s.stat(player, Stat.MAGIC_FIND))).orElse(0.0);
        List<ItemStack> items = new ArrayList<>();
        BossData data = null;
        // Profile access for offline players blocks on disk; only online participants get pity tracking.
        if (player != null) {
            data = core.profiles().section(id, "boss", BossData.class, BossData::new);
        }
        LootRoller.Result result = LootRoller.roll(def.id(), loot, share, rank, mf,
            data == null ? java.util.Map.of() : data.dry, random);
        for (LootRoller.Drop drop : result.drops()) {
            ItemStack stack = resolve(drop.entry().item(), drop.amount());
            if (stack == null) {
                continue;
            }
            items.add(stack);
            if (drop.chance() <= loot.announceBelow() || (drop.entry().chance() <= loot.announceBelow())) {
                announceRare(player, id, stack, def, drop.chance());
            }
        }
        for (BossLootHook hook : hooks) {
            if (player == null) {
                break;
            }
            try {
                List<ItemStack> extra = hook.roll(event, player);
                if (extra != null) {
                    items.addAll(extra);
                }
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Boss loot hook failed", e);
            }
        }
        if (loot.wingsMinShare() >= 0 && share >= loot.wingsMinShare() && player != null) {
            core.service(WingsApi.class).ifPresent(w -> {
                if (w.grantOnce(player, "boss:" + def.id())) {
                    Bukkit.broadcast(MINI.deserialize("<gradient:#b892ff:#ff8ad8><bold>" + esc(player.getName())
                        + "</bold> earned the Wings from " + def.name() + "</gradient>"));
                }
            });
        }
        if (data != null) {
            data.dry.clear();
            data.dry.putAll(result.dry());
            data.kills.merge(def.id(), 1, Integer::sum);
            core.profiles().markDirty(id);
        }
        if (!items.isEmpty()) {
            delivery.giveOrQueue(id, items);
        }
        if (player != null) {
            summary(player, def, damager.damage(), share, rank, coins, xp, items);
        }
    }

    private void summary(Player player, BossDefinition def, double damage, double share, int rank, double coins,
            double xp, List<ItemStack> items) {
        player.sendMessage(MINI.deserialize("<dark_gray><strikethrough>                    </strikethrough>"));
        player.sendMessage(MINI.deserialize(def.name() + " <green>defeated!"));
        player.sendMessage(MINI.deserialize("<gray>Your damage: <red>" + Msg.number(damage) + " <gray>("
            + Math.round(share * 100) + "%), rank <yellow>#" + rank));
        if (coins > 0) {
            player.sendMessage(MINI.deserialize("<gray>Coins: <gold>" + Msg.coins(coins)));
        }
        if (xp > 0) {
            player.sendMessage(MINI.deserialize("<gray>Combat XP: <aqua>" + Msg.number(xp)));
        }
        for (ItemStack item : items) {
            player.sendMessage(Component.text("  + ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(item.displayName()).append(Component.text(" x" + item.getAmount(),
                    net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        }
        player.sendMessage(MINI.deserialize("<dark_gray><strikethrough>                    </strikethrough>"));
    }

    private void announceRare(@Nullable Player player, UUID id, ItemStack stack, BossDefinition def, double chance) {
        String name = player != null ? player.getName() : core.profiles().lastName(id);
        Component line = MINI.deserialize("<gold><bold>RARE DROP! </bold><white>" + MiniEsc.esc(name) + " <gray>got ")
            .append(stack.displayName()).append(MINI.deserialize(" <gray>from " + def.name()));
        Bukkit.broadcast(line);
    }

    private void announceLeaderboard(BossSession session, List<Damager> ranking) {
        if (ranking.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder(session.def.name() + " <gray>was slain! Top damage:");
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Damager d = ranking.get(i);
            String name = Optional.ofNullable(Bukkit.getPlayer(d.player())).map(Player::getName)
                .orElseGet(() -> core.profiles().lastName(d.player()));
            text.append("\n <yellow>#").append(i + 1).append(" <white>")
                .append(esc(name))
                .append(" <gray>- <red>").append(Msg.number(d.damage()));
        }
        Component message = MINI.deserialize(text.toString());
        for (Player p : session.world.getPlayers()) {
            if (p.getLocation().distanceSquared(session.location()) < 160 * 160 || session.ledger.contains(p.getUniqueId())) {
                p.sendMessage(message);
            }
        }
    }

    static String esc(String text) {
        return MINI.escapeTags(text);
    }

    /** {@code custom:<id>} or {@code vanilla:<MATERIAL>} to a stack, null when unknown. */
    public @Nullable ItemStack resolve(String spec, int amount) {
        if (spec.startsWith("custom:")) {
            String id = spec.substring(7);
            if (core.items().def(id).isEmpty()) {
                core.getPlugin().getLogger().warning("Boss loot references unknown custom item " + id);
                return null;
            }
            return core.items().create(id, amount);
        }
        Material material = Material.matchMaterial(spec.startsWith("vanilla:") ? spec.substring(8) : spec);
        return material == null || material.isAir() ? null : new ItemStack(material, amount);
    }
}
