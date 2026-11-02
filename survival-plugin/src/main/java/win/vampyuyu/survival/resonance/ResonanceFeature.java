package win.vampyuyu.survival.resonance;

import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.ResonanceApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * Memory Shards (GDD 15.3-15.5) and the five Resonance ranks of the Bastions (GDD 12.4). Must be listed after the
 * {@code boss} feature: it hooks into the boss loot.
 */
public final class ResonanceFeature implements Feature {
    private ResonanceService service;

    @Override
    public String id() {
        return "resonance";
    }

    @Override
    public void enable(Core core) {
        ResonanceConfig config = ResonanceConfig.load(core.data());
        config.problems().forEach(p -> core.getPlugin().getLogger().warning("resonance data: " + p));
        service = new ResonanceService(core, config);
        ResonanceListener listener = new ResonanceListener(core, service);
        core.listen(listener);
        core.service(BossApi.class).ifPresentOrElse(b -> b.registerLootHook(listener),
            () -> core.getPlugin().getLogger().warning("resonance: the boss feature is off, no guarantee hook"));
        core.permissions().register(ResonanceCommand.ADMIN, "Resonance administration (give Memory Shards)",
            PermissionDefault.OP);
        ResonanceCommand command = new ResonanceCommand(core, service);
        core.commands().command("resonance").aliases("shards")
            .description("Memory Shards and Resonance ranks of the Bastions")
            .usage("/resonance [status|rank <1-5>|forge <boss>]")
            .permission("vampyuyu.sb.resonance.use", PermissionDefault.TRUE)
            .executor(command).tab(command).register();
        core.register(ResonanceApi.class, service);
        core.register(ResonanceService.class, service);
    }

    @Override
    public void disable() {
        service = null;
    }

    /** For tests. */
    public ResonanceService service() {
        return service;
    }
}
