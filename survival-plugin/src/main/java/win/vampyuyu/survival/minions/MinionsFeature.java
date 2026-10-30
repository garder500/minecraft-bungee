package win.vampyuyu.survival.minions;

import java.util.random.RandomGenerator;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.MinionApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * Minions: placeable workers that mine, farm, chop, breed, hunt and fish on a player's island, produce into a
 * storage even while the owner is away, and are upgraded with fuel, upgrades and tiers. See
 * docs/skyblock-notes/minions.md.
 */
public final class MinionsFeature implements Feature {
    private Core core;
    private MinionConfig config;
    private MinionManager manager;
    private MinionRecipes recipes;

    @Override
    public String id() {
        return "minions";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        var log = core.getPlugin().getLogger();
        config = MinionConfig.load(core.featureConfig("minions"), log);
        if (config.types.isEmpty()) {
            throw new IllegalStateException("minions.yml defines no valid minion type");
        }
        boolean registered = false;
        try {
            MinionItems.register(core.items(), config);
            registered = true;
            MinionStore store = new MinionStore(core.store(), core.getDataFolder(), log);
            store.loadAll();
            MinionLogic logic = new MinionLogic(config, RandomGenerator.getDefault());
            MinionVisuals visuals = new MinionVisuals(config, log);
            manager = new MinionManager(core, config, store, logic, visuals, System::currentTimeMillis);
            MinionGui gui = new MinionGui(core, manager);
            recipes = new MinionRecipes(core.getPlugin());
            recipes.register(core.items(), config);
            core.listen(new MinionListener(core, manager, gui, recipes));
            core.listen(new MinionItemGuard(core.items()));

            core.permissions().register("vampyuyu.sb.minions.admin", "Give, list and purge minions",
                PermissionDefault.OP);
            MinionCommand command = new MinionCommand(core, manager);
            core.commands().command("minion").aliases("minions").description("Minions: help, slots, list")
                .usage("/minion [help|info|list]")
                .permission("vampyuyu.sb.minions.use", PermissionDefault.TRUE)
                .executor(command).tab(command).register();

            long period = Math.max(1, config.tickTicks);
            core.scheduler().repeating(40, period, manager::tick);
            core.register(MinionApi.class, manager);
            core.debug("minions: " + config.types.size() + " types, " + store.size() + " placed");
        } catch (RuntimeException e) {
            if (recipes != null) {
                recipes.unregister();
            }
            if (registered) {
                MinionItems.unregister(core.items(), config);
            }
            throw e;
        }
    }

    @Override
    public void disable() {
        if (manager != null) {
            manager.shutdown();
        }
        if (recipes != null) {
            recipes.unregister();
        }
        if (core != null && config != null) {
            MinionItems.unregister(core.items(), config);
        }
    }
}
