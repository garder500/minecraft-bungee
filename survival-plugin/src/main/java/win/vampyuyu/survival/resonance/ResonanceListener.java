package win.vampyuyu.survival.resonance;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.BossLootHook;
import win.vampyuyu.survival.api.Resonance;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.BossKillEvent.Damager;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.resonance.ResonanceConfig.Bastion;

/** Pays the Memory Shards of a Bastion clear and hands out the boss guarantee. */
final class ResonanceListener implements Listener, BossLootHook {
    private final Core core;
    private final ResonanceService service;

    ResonanceListener(Core core, ResonanceService service) {
        this.core = core;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossKill(BossKillEvent event) {
        ResonanceConfig cfg = service.config();
        Bastion bastion = cfg.bastions().get(event.getBossId());
        if (bastion == null) {
            return;
        }
        int rank = Resonance.clamp(event.getResonance());
        double total = event.getTopDamagers().stream().mapToDouble(Damager::damage).sum();
        long now = System.currentTimeMillis();
        for (Damager damager : event.getTopDamagers()) {
            if (total <= 0 || damager.damage() / total < cfg.minShare()) {
                continue;
            }
            UUID id = damager.player();
            service.edit(id, d -> {
                ResonanceRules.Clear clear = ResonanceRules.clear(d, bastion, rank, now, cfg);
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    core.scheduler().nextTick(() -> announce(player, bastion, rank, clear));
                }
            });
        }
    }

    private void announce(Player player, Bastion bastion, int rank, ResonanceRules.Clear clear) {
        String bonus = clear.firstOfDay() ? " <yellow>(first clear of the day: x2)" : "";
        core.msg().mini(player, "<gray>Memory Shards: <aqua>+" + clear.shards() + bonus);
        if (clear.newBest() && rank < Resonance.MAX_RANK) {
            core.msg().mini(player, "<gold>Resonance " + Resonance.roman(rank + 1)
                + " <gray>is now open on this Bastion. Use <white>/resonance rank " + (rank + 1));
        }
    }

    /** The guarantee: the component, handed out when the boss did not drop it for too many runs (GDD 15.2). */
    @Override
    public List<ItemStack> roll(BossKillEvent event, Player recipient) {
        return roll(event, recipient, List.of());
    }

    @Override
    public List<ItemStack> roll(BossKillEvent event, Player recipient, List<ItemStack> alreadyWon) {
        ResonanceConfig cfg = service.config();
        Bastion bastion = cfg.bastions().get(event.getBossId());
        if (bastion == null) {
            return List.of();
        }
        ItemStack component = service.component(bastion, 1);
        if (component == null) {
            return List.of();
        }
        boolean dropped = service.isComponent(bastion, alreadyWon);
        boolean due = ResonanceRules.pity(service.data(recipient.getUniqueId()), bastion,
            Resonance.clamp(event.getResonance()), dropped, cfg);
        core.profiles().markDirty(recipient.getUniqueId());
        if (!due) {
            return List.of();
        }
        core.msg().mini(recipient, "<gold>Guaranteed component: <gray>the Bastion remembers your effort.");
        return List.of(component);
    }
}
