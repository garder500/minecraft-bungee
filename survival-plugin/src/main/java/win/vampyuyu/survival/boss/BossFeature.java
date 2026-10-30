package win.vampyuyu.survival.boss;

import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.boss.slayer.SlayerCommand;
import win.vampyuyu.survival.boss.slayer.SlayerGui;
import win.vampyuyu.survival.boss.slayer.SlayerManager;
import win.vampyuyu.survival.boss.world.WorldBossCommand;
import win.vampyuyu.survival.boss.world.WorldBossManager;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * Entry point of the {@code boss} feature: the boss framework, the six contract bosses, summon items, the slayer
 * system and the world boss events (see docs/skyblock-notes/boss.md).
 */
public final class BossFeature implements Feature {
    private Core core;
    private BossItems items;
    private BossManager manager;
    private SlayerManager slayer;
    private WorldBossManager worldBoss;
    private SummonListener summon;

    @Override
    public String id() {
        return "boss";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        BossConfig config = BossConfig.load(core.featureConfig("boss"));
        report(config);
        try {
            items = new BossItems(core);
            items.register(config);
            manager = new BossManager(core, config);
            manager.start();
            summon = new SummonListener(core, manager);
            core.listen(new BossListener(core, manager));
            core.listen(summon);
            core.listen(new BossItemGuard(core));
            slayer = new SlayerManager(core, manager);
            core.listen(slayer);
            SlayerGui gui = new SlayerGui(core, slayer);
            worldBoss = new WorldBossManager(core, manager);
            worldBoss.start();
            registerPermissions();
            registerCommands(gui);
            core.scheduler().nextTick(this::purgeLoadedWorlds);
            core.register(BossApi.class, new BossApiImpl(manager));
        } catch (RuntimeException e) {
            disable();
            throw e;
        }
        core.debug("boss feature enabled: " + config.bosses().size() + " bosses, " + config.slayers().size() + " slayers");
    }

    private void report(BossConfig config) {
        for (String error : config.errors()) {
            core.getPlugin().getLogger().warning("boss.yml: " + error);
        }
        if (config.bosses().isEmpty()) {
            core.getPlugin().getLogger().warning("boss.yml defines no valid boss");
        }
    }

    private void registerPermissions() {
        core.permissions().register(BossListener.ADMIN, "Boss administration (spawn, kill, reload, bypass)", PermissionDefault.OP);
        core.permissions().register(SummonListener.PERMISSION, "Summon bosses with summon items", PermissionDefault.TRUE);
    }

    private void registerCommands(SlayerGui gui) {
        BossCommand bossCommand = new BossCommand(core, manager, this::reload);
        core.commands().command("boss").description("Bosses: list, info and admin tools")
            .usage("/boss <list|info|claim|spawn|kill|active|reload>")
            .permission("vampyuyu.sb.boss.info", PermissionDefault.TRUE)
            .executor(bossCommand).tab(bossCommand).register();
        SlayerCommand slayerCommand = new SlayerCommand(core, slayer, gui);
        core.commands().command("slayer").description("Slayer quests: hunt, fight a personal boss, earn perks")
            .usage("/slayer [start <slayer> <tier>|status|spawn|cancel]")
            .permission("vampyuyu.sb.boss.slayer", PermissionDefault.TRUE)
            .executor(slayerCommand).tab(slayerCommand).register();
        WorldBossCommand worldCommand = new WorldBossCommand(core, manager, worldBoss);
        core.commands().command("worldboss").aliases("wb").description("World boss events")
            .usage("/worldboss status")
            .permission("vampyuyu.sb.boss.worldboss", PermissionDefault.TRUE)
            .executor(worldCommand).tab(worldCommand).register();
    }

    /** Re-reads boss.yml. Running fights keep their definition; summon item names need a restart. */
    String reload() {
        BossConfig fresh = BossConfig.load(core.featureConfig("boss"));
        report(fresh);
        manager.reload(fresh);
        summon.refresh();
        worldBoss.reload();
        return "Reloaded boss.yml: " + fresh.bosses().size() + " bosses, " + fresh.slayers().size() + " slayers, "
            + fresh.errors().size() + " problems (see the console).";
    }

    private void purgeLoadedWorlds() {
        try {
            for (World world : Bukkit.getWorlds()) {
                manager.purge(world.getEntities().stream().filter(Tags::isOurs).toList());
            }
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Boss orphan purge failed", e);
        }
    }

    @Override
    public void disable() {
        if (worldBoss != null) {
            worldBoss.stop();
        }
        if (slayer != null) {
            slayer.shutdown();
        }
        if (manager != null) {
            manager.shutdown();
        }
        if (items != null) {
            items.unregister();
        }
        worldBoss = null;
        slayer = null;
        manager = null;
        items = null;
    }

    /** For tests. */
    BossManager manager() {
        return manager;
    }

    /** For tests. */
    List<String> itemIds() {
        return items == null ? List.of() : items.registeredIds();
    }
}
