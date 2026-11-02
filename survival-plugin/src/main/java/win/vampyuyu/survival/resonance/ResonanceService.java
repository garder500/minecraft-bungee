package win.vampyuyu.survival.resonance;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Resonance;
import win.vampyuyu.survival.api.ResonanceApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.resonance.ResonanceConfig.Bastion;

/** Profile access and item delivery around {@link ResonanceRules}; also the published {@link ResonanceApi}. */
public final class ResonanceService implements ResonanceApi {
    private final Core core;
    private volatile ResonanceConfig config;

    public ResonanceService(Core core, ResonanceConfig config) {
        this.core = core;
        this.config = config;
    }

    public ResonanceConfig config() {
        return config;
    }

    public void reload(ResonanceConfig fresh) {
        config = fresh;
    }

    /** The live section of an online player (main thread). */
    public ResonanceData data(UUID id) {
        return core.profiles().section(id, "resonance", ResonanceData.class, ResonanceData::new);
    }

    /** Edits a section, off the main thread when the profile is not loaded (an offline player is read from disk). */
    public void edit(UUID id, Consumer<ResonanceData> change) {
        Runnable job = () -> core.profiles().edit(id, "resonance", ResonanceData.class, ResonanceData::new, change);
        if (Bukkit.isPrimaryThread() && !core.profiles().isLoaded(id) && core.getPlugin().isEnabled()) {
            core.scheduler().async(job);
        } else {
            job.run();
        }
    }

    @Override
    public int selectedRank(UUID player) {
        return Resonance.clamp(data(player).selected);
    }

    @Override
    public int effectiveRank(UUID player, String bossId) {
        ResonanceData d = data(player);
        return ResonanceMath.effectiveRank(d.selected, d.best.getOrDefault(bossId, 0));
    }

    @Override
    public int bestRank(UUID player, String bossId) {
        return data(player).best.getOrDefault(bossId, 0);
    }

    @Override
    public long shards(UUID player) {
        return data(player).shards;
    }

    /** Gives a stack or drops what does not fit at the player's feet. */
    public void give(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    /** The component item of a Bastion, null when the item is not registered. */
    public ItemStack component(Bastion b, int amount) {
        if (core.items().def(b.component()).isEmpty()) {
            return null;
        }
        return core.items().create(b.component(), amount);
    }

    public boolean isComponent(Bastion b, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (core.items().is(s, b.component())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void grantShards(UUID id, long amount) {
        if (amount > 0) {
            edit(id, d -> {
                d.shards += amount;
                d.earned += amount;
            });
        }
    }

    @Override
    public boolean foundComponent(UUID id, String componentItemId) {
        ResonanceData d = data(id);
        for (Bastion b : config.bastions().values()) {
            if (b.component().equals(componentItemId)) {
                return d.found.contains(b.bossId());
            }
        }
        return false;
    }

    @Override
    public void payDefeat(UUID id, String bossId, int rank, double progress, double share) {
        ResonanceConfig cfg = config;
        Bastion bastion = cfg.bastions().get(bossId);
        if (bastion == null || share < cfg.minShare() || !(progress > 0)) {
            return;
        }
        int r = win.vampyuyu.survival.api.Resonance.clamp(rank);
        edit(id, d -> {
            long shards = ResonanceRules.defeat(d, bastion, r, Math.min(1, progress), cfg);
            Player player = Bukkit.getPlayer(id);
            if (player != null && shards > 0) {
                core.scheduler().nextTick(() -> core.msg().mini(player, "<gray>Memory Shards for what you learned: <aqua>+" + shards));
            }
        });
    }
}
