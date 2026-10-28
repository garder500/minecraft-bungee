package win.vampyuyu.survival.menu;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The menu feature: the only join router, the Skyblock Menu item, warps, quests, fairy souls, sidebar, settings and
 * damage numbers. See docs/skyblock-notes/menu.md.
 */
public final class MenuFeature implements Feature {
    private MenuRuntime rt;
    private SkyblockMenuItem item;
    private MenuItemGuard guard;
    private JoinFlow join;
    private HandoffSource source;
    private QuestService quests;
    private WarpService warps;
    private PublicWarps publicWarps;
    private FairySouls fairy;
    private SidebarService sidebar;
    private DamageIndicators damage;
    private Scheduler.Handle maintenance;

    @Override
    public String id() {
        return "menu";
    }

    @Override
    public void enable(Core core) {
        try {
            build(core);
        } catch (RuntimeException e) {
            disable(); // custom items, tasks and entities are not rolled back by the core
            throw e;
        }
    }

    private void build(Core core) {
        MenuConfig config = new MenuConfig(core.featureConfig("menu"));
        report(core, config);
        rt = new MenuRuntime(core, config);

        item = new SkyblockMenuItem(core);
        item.register();
        quests = new QuestService(rt);
        QuestsGui questsGui = new QuestsGui(rt, quests);
        publicWarps = new PublicWarps(core);
        publicWarps.load();
        warps = new WarpService(rt, publicWarps);
        WarpsGui warpsGui = new WarpsGui(rt, warps);
        fairy = new FairySouls(rt, quests);
        sidebar = new SidebarService(rt, quests);
        SettingsGui settingsGui = new SettingsGui(rt, sidebar::refresh);
        MainMenu mainMenu = new MainMenu(rt, quests, questsGui, warpsGui, settingsGui, fairy);
        questsGui.backTo(mainMenu.opener());
        warpsGui.backTo(mainMenu.opener());
        settingsGui.backTo(mainMenu.opener());
        guard = new MenuItemGuard(rt, item, mainMenu.opener());
        damage = new DamageIndicators(rt);
        source = config.redisEnabled ? RedisHandoffSource.fromEnvironment(core.getPlugin().getLogger(),
            config.redisHost, config.redisPort, config.redisPasswordFile, config.redisConnectTimeoutMs,
            config.redisSocketTimeoutMs, config.redisBackoffMs) : null;
        join = new JoinFlow(rt, source, this::arrive);

        core.listen(guard);
        core.listen(join);
        core.listen(new QuestListener(rt, quests));
        core.listen(fairy);
        core.listen(sidebar);
        core.listen(damage);
        core.listen(new Cleanup());

        new MenuCommands(rt, mainMenu, questsGui, quests, warpsGui, warps, settingsGui, fairy, () -> reload(core))
            .register();

        fairy.start();
        sidebar.start();
        // Housekeeping: keeps the menu item in place, rolls the quest day over, re-checks island and skill quests.
        maintenance = core.scheduler().repeating(100, 100, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                guard.sync(player);
            }
        });
        core.scheduler().repeating(600, 600, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (MenuRuntime.inSkyblock(player.getWorld())) {
                    quests.sync(player);
                }
            }
        });
        // players already online (plugin reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            core.scheduler().nextTick(() -> arrive(player));
        }
    }

    private static void report(Core core, MenuConfig config) {
        for (String problem : config.problems()) {
            core.getPlugin().getLogger().warning("menu.yml: " + problem);
        }
    }

    /** Re-reads menu.yml. Returns false when the file had problems (it still applies what was valid). */
    private boolean reload(Core core) {
        MenuConfig fresh = new MenuConfig(core.featureConfig("menu"));
        report(core, fresh);
        rt.reload(fresh);
        for (Player player : Bukkit.getOnlinePlayers()) {
            guard.sync(player);
            sidebar.refresh(player);
        }
        return fresh.problems().isEmpty();
    }

    /** Everything that depends on the player being settled in the world after a join and its routing. */
    void arrive(Player player) {
        if (!player.isOnline()) {
            return;
        }
        guard.sync(player);
        if (MenuRuntime.inSkyblock(player.getWorld())) {
            quests.record(player, QuestType.VISIT_WORLD, MenuConfig.worldToken(player.getWorld().getName()), 1);
        }
        quests.sync(player);
        fairy.onArrive(player);
        join.welcome(player);
        sidebar.refresh(player);
    }

    @Override
    public void disable() {
        if (maintenance != null) {
            maintenance.cancel();
            maintenance = null;
        }
        if (sidebar != null) {
            sidebar.stop();
        }
        if (fairy != null) {
            fairy.stop();
        }
        if (damage != null) {
            damage.stop();
        }
        if (warps != null) {
            warps.shutdown();
        }
        if (publicWarps != null) {
            publicWarps.flushNow();
        }
        if (guard != null) {
            guard.stripEveryone();
        }
        if (source != null) {
            source.close();
        }
        if (item != null) {
            item.unregister();
        }
    }

    /** Per-player cleanup on quit. */
    private final class Cleanup implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            warps.forget(event.getPlayer().getUniqueId());
            sidebar.clear(event.getPlayer());
        }
    }

    /** For tests: the commands every instance of the feature registers. */
    static List<String> commandNames() {
        return List.of("sbmenu", "quests", "fairy", "warps", "hub", "sbsettings");
    }
}
