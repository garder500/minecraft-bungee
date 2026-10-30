package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.BossLootHook;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/** Owns every live {@link BossSession}: spawning, the update loop, cleanup and reward hand-off. Main thread. */
public final class BossManager {
    /** Hard cap of simultaneous fights, a safety net for the server tick. */
    public static final int MAX_SESSIONS = 40;

    private final Core core;
    private volatile BossConfig config;
    private final Map<UUID, BossSession> byEntity = new ConcurrentHashMap<>();
    private final Map<UUID, BossSession> aux = new ConcurrentHashMap<>();
    private final BossRewards rewards;
    private final LootDelivery delivery;
    private Scheduler.Handle loop;
    private boolean shuttingDown;

    public BossManager(Core core, BossConfig config) {
        this.core = core;
        this.config = config;
        this.delivery = new LootDelivery(core);
        this.rewards = new BossRewards(core, config.settings(), delivery);
    }

    public Core core() { return core; }

    public BossConfig config() { return config; }

    public BossSettings settings() { return config.settings(); }

    public BossRewards rewards() { return rewards; }

    public LootDelivery delivery() { return delivery; }

    public void reload(BossConfig fresh) {
        this.config = fresh;
    }

    public void start() {
        int period = config.settings().tickPeriod();
        loop = core.scheduler().repeating(20, period, this::tickAll);
    }

    private void tickAll() {
        for (BossSession session : new ArrayList<>(byEntity.values())) {
            try {
                session.tick();
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.SEVERE, "Boss tick failed for " + session.def.id()
                    + ", ending the fight", e);
                session.end(BossSession.End.ENTITY_LOST, null);
            }
        }
    }

    // ---------------------------------------------------------------- spawning

    /** The {@code BossApi.spawn} path: no summon rules, any world. */
    public boolean spawn(String id, Location location, @Nullable Player summoner) {
        return config.definition(id).flatMap(def -> spawn(def, location, summoner, BossSession.SpawnOptions.regular()))
            .isPresent();
    }

    /** Spawns a boss. Empty when refused (null world, unloaded chunk, cap reached, entity spawn refused). */
    public Optional<BossSession> spawn(BossDefinition def, @Nullable Location location, @Nullable Player summoner,
            BossSession.SpawnOptions options) {
        if (shuttingDown || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return Optional.empty();
        }
        if (byEntity.size() >= MAX_SESSIONS) {
            return Optional.empty();
        }
        if (options.personal() && summoner != null) {
            for (BossSession other : byEntity.values()) {
                if (summoner.getUniqueId().equals(other.owner()) && other.def.id().equals(def.id())) {
                    return Optional.empty();
                }
            }
        }
        BossSession session = new BossSession(this, def, location, summoner, options);
        boolean started;
        try {
            started = session.begin();
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Could not spawn boss " + def.id(), e);
            session.end(BossSession.End.ADMIN, null);
            return Optional.empty();
        }
        if (!started) {
            return Optional.empty();
        }
        byEntity.put(session.entityId(), session);
        return Optional.of(session);
    }

    // ---------------------------------------------------------------- lookup

    public Collection<BossSession> active() {
        return List.copyOf(byEntity.values());
    }

    public int activeCount(String bossId) {
        int n = 0;
        for (BossSession s : byEntity.values()) {
            if (s.def.id().equals(bossId)) {
                n++;
            }
        }
        return n;
    }

    /** The session of a boss entity (also resolves dragon parts). */
    public @Nullable BossSession sessionOfBoss(@Nullable Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        return entity == null ? null : byEntity.get(entity.getUniqueId());
    }

    /** The session a minion or crystal belongs to. */
    public @Nullable BossSession sessionOfAux(@Nullable Entity entity) {
        return entity == null ? null : aux.get(entity.getUniqueId());
    }

    void registerAux(UUID id, BossSession session) {
        aux.put(id, session);
    }

    void unregisterAux(UUID id) {
        aux.remove(id);
    }

    public Optional<BossSession> sessionOfOwner(UUID owner) {
        for (BossSession s : byEntity.values()) {
            if (owner.equals(s.owner())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- lifecycle events

    /** Called by the session when it ends (after cleanup), before the end listeners. */
    void sessionEnded(BossSession.EndInfo info) {
        BossSession session = info.session();
        byEntity.remove(session.entityId());
        aux.values().removeIf(s -> s == session);
        try {
            if (info.reason() == BossSession.End.KILLED) {
                rewards.distribute(session, info.killer());
            } else {
                refund(info);
            }
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.SEVERE, "Boss end handling failed for " + session.def.id(), e);
        }
    }

    private void refund(BossSession.EndInfo info) {
        BossSession session = info.session();
        String item = session.options().refundItem();
        UUID summoner = session.summoner();
        if (item == null || summoner == null || core.items().def(item).isEmpty()) {
            return;
        }
        boolean serverSide = switch (info.reason()) {
            case SHUTDOWN, WORLD_UNLOAD, ENTITY_LOST -> true;
            case DESPAWN -> session.ledger.total() <= 0;
            default -> false;
        };
        if (!serverSide) {
            return;
        }
        ItemStack stack = core.items().create(item);
        delivery.giveOrQueue(summoner, List.of(stack));
        Player p = Bukkit.getPlayer(summoner);
        if (p != null) {
            core.msg().info(p, "The summoning was interrupted, your item was returned.");
        }
    }

    public void playerDied(Player player) {
        sessionOfOwner(player.getUniqueId()).ifPresent(s -> s.end(BossSession.End.OWNER_DIED, null));
    }

    public void playerQuit(Player player) {
        sessionOfOwner(player.getUniqueId()).ifPresent(s -> s.end(BossSession.End.OWNER_LEFT, null));
    }

    public void worldUnloading(World world) {
        for (BossSession s : new ArrayList<>(byEntity.values())) {
            if (world.equals(s.world)) {
                s.end(BossSession.End.WORLD_UNLOAD, null);
            }
        }
    }

    /** Ends every fight. */
    public int killAll(BossSession.End reason) {
        List<BossSession> all = new ArrayList<>(byEntity.values());
        all.forEach(s -> s.end(reason, null));
        return all.size();
    }

    public void shutdown() {
        shuttingDown = true;
        if (loop != null) {
            loop.cancel();
        }
        killAll(BossSession.End.SHUTDOWN);
        aux.clear();
    }

    /** Removes tagged entities that have no live session (leftovers of a crash). Returns how many. */
    public int purge(Collection<Entity> entities) {
        int removed = 0;
        for (Entity e : entities) {
            String boss = Tags.bossId(e);
            String minion = Tags.minionOf(e);
            if (boss == null && minion == null) {
                continue;
            }
            boolean live = (boss != null && byEntity.containsKey(e.getUniqueId()))
                || (minion != null && aux.containsKey(e.getUniqueId()));
            if (!live) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    public void addHook(BossLootHook hook) {
        rewards.addHook(hook);
    }
}
