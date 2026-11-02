package win.vampyuyu.survival.npc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import win.vampyuyu.survival.api.NpcMemoryApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.data.Settings;

/**
 * Living NPCs (GDD 18): a persisted memory of facts and relations and deterministic reactions. Merchants greet through it
 * ({@code economy}); other features record facts through {@link NpcMemoryApi}. The AI never decides game state.
 */
public final class NpcMemoryFeature implements Feature {
    @Override
    public String id() {
        return "npc";
    }

    @Override
    public void enable(Core core) {
        NpcConfig cfg = NpcConfig.load(core.data());
        cfg.problems().forEach(p -> core.getPlugin().getLogger().warning("npc data: " + p));
        NpcMemory memory = new NpcMemory(core.data(), cfg, System::currentTimeMillis);
        core.register(NpcMemoryApi.class, memory);
        core.register(NpcMemory.class, memory);
        core.listen(new Recorder(core, memory));
        core.onDataChange(domain -> {
            if (domain.equals("npc")) {
                memory.reload(NpcConfig.load(core.data()));
            }
        });
    }

    /** Facts that come from events of other features. */
    private static final class Recorder implements Listener {
        private final Core core;
        private final NpcMemory memory;

        Recorder(Core core, NpcMemory memory) {
            this.core = core;
            this.memory = memory;
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBossKill(BossKillEvent event) {
            Set<UUID> who = new HashSet<>();
            event.getTopDamagers().forEach(d -> who.add(d.player()));
            if (event.getKiller() != null) {
                who.add(event.getKiller().getUniqueId());
            }
            boolean guardian = Settings.load(core.data(), "boss").getList("guardian.ids").contains(event.getBossId());
            for (UUID p : who) {
                memory.recordFact(p, "defeated." + event.getBossId());
                if (guardian) {
                    memory.recordFact(p, "defeated.guardian");
                }
            }
        }
    }
}
