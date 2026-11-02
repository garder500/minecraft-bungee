package win.vampyuyu.survival.boss;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.BossLootHook;
import win.vampyuyu.survival.core.Core;

/** The published {@link BossApi}: no summon rules, any world, works without a summoner (dungeons). */
public final class BossApiImpl implements BossApi {
    private final Core core;
    private final BossManager manager;

    public BossApiImpl(Core core, BossManager manager) {
        this.core = core;
        this.manager = manager;
    }

    @Override
    public boolean spawn(String bossId, Location location, @Nullable Player summoner) {
        if (bossId == null || location == null) {
            return false;
        }
        return manager.spawn(bossId, location, summoner);
    }

    @Override
    public Collection<String> bossIds() {
        return List.copyOf(manager.config().bosses().keySet());
    }

    @Override
    public int kills(UUID player, String bossId) {
        return core.profiles().section(player, "boss", BossData.class, BossData::new).kills.getOrDefault(bossId, 0);
    }

    @Override
    public void registerLootHook(BossLootHook hook) {
        if (hook != null) {
            manager.addHook(hook);
        }
    }

    @Override
    public Optional<Fight> spawnFight(String bossId, Location location, Player leader, @Nullable String refundItem,
            Consumer<FightResult> onEnd) {
        if (bossId == null || location == null || leader == null) {
            return Optional.empty();
        }
        Optional<BossDefinition> def = manager.config().definition(bossId);
        if (def.isEmpty()) {
            return Optional.empty();
        }
        int resonance = core.service(win.vampyuyu.survival.api.ResonanceApi.class)
            .map(r -> r.effectiveRank(leader.getUniqueId(), bossId)).orElse(0);
        Consumer<BossSession.EndInfo> end = info -> {
            BossSession s = info.session();
            java.util.Map<UUID, Double> damage = new LinkedHashMap<>();
            for (var d : s.ledger().ranking()) {
                damage.put(d.player(), d.damage());
            }
            double progress = s.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, 1 - s.hp() / s.maxHp()));
            Outcome outcome;
            if (info.reason() == BossSession.End.KILLED) {
                outcome = Outcome.CLEARED;
                progress = 1;
            } else if (BossManager.refundable(info.reason(), s.ledger().total() > 0)) {
                outcome = Outcome.FAULT;
            } else {
                outcome = Outcome.DEFEATED;
            }
            onEnd.accept(new FightResult(outcome, damage, progress, s.elapsedMillis(), resonance));
        };
        Optional<BossSession> session = manager.spawn(def.get(), location, leader,
            new BossSession.SpawnOptions(false, false, 0, 0, -1, end, refundItem, resonance));
        return session.map(s -> new Fight() {
            @Override
            public void abort() {
                s.end(BossSession.End.ADMIN, null);
            }
        });
    }
}
