package win.vampyuyu.survival.skills;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Entry point of the {@code skills} feature: eight skills with levels and stat rewards, item collections with
 * tiered rewards, collection-gated recipes for the {@code enchanted_*} resources, menus and leaderboards.
 * See docs/skyblock-notes/skills.md.
 */
public final class SkillsFeature implements Feature, Listener {
    private Core core;
    private SkillsService service;
    private EnchantedItems enchanted;
    private SkillRecipes recipes;
    private PlacedBlocks placed;
    private BlockSources blockSources;
    private ActivitySources activitySources;
    private SeaCreatures seaCreatures;
    private Leaderboard leaderboard;
    private final List<Scheduler.Handle> tasks = new ArrayList<>();

    @Override
    public String id() {
        return "skills";
    }

    @Override
    public void enable(Core core) {
        try {
            doEnable(core);
        } catch (RuntimeException | Error e) {
            disable(); // recipes, custom items and tasks are not rolled back by the core
            throw e;
        }
    }

    private void doEnable(Core core) {
        this.core = core;
        SkillsConfig config = new SkillsConfig(core.featureConfig("skills"));
        reportWarnings(config);
        service = new SkillsService(core, config);

        enchanted = new EnchantedItems(core);
        enchanted.registerAll(config);
        recipes = new SkillRecipes(core, service);
        recipes.registerAll();
        core.listen(recipes);

        placed = new PlacedBlocks(new ChunkPdcBackend());
        blockSources = new BlockSources(core, service, placed);
        core.listen(blockSources);
        activitySources = new ActivitySources(core, service);
        core.listen(activitySources);
        seaCreatures = new SeaCreatures(core, service);
        core.listen(seaCreatures);
        core.listen(new EnchantedGuard(core, service));
        core.listen(this);
        tasks.add(core.scheduler().repeating(1200, 1200, () -> {
            try {
                placed.flush();
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "skills: could not save placed-block marks", e);
            }
        }));

        leaderboard = new Leaderboard(core.getPlugin().getLogger());
        scheduleLeaderboard(config);

        SkillsMenus menus = new SkillsMenus(core, service);
        RecipeViewer viewer = new RecipeViewer(core, service, recipes);
        menus.viewer(viewer);
        new SkillsCommands(core, service, menus, leaderboard, this::reload, viewer, recipes).register();

        for (Player online : Bukkit.getOnlinePlayers()) {
            service.applyAllStats(online);
        }
        // Published last: other features only ever see a fully built service.
        core.register(SkillsApi.class, service);
    }

    @Override
    public void disable() {
        if (core == null) {
            return;
        }
        tasks.forEach(Scheduler.Handle::cancel);
        tasks.clear();
        if (seaCreatures != null) {
            seaCreatures.clear();
        }
        if (placed != null) {
            try {
                placed.flush();
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "skills: could not save placed-block marks", e);
            }
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
        if (enchanted != null) {
            enchanted.unregisterAll();
        }
        if (service != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                service.clearStats(online.getUniqueId());
            }
        }
    }

    private void reportWarnings(SkillsConfig config) {
        for (String warning : config.warnings) {
            core.getPlugin().getLogger().warning("skills.yml: " + warning);
        }
    }

    private void scheduleLeaderboard(SkillsConfig config) {
        Path players = core.getDataFolder().toPath().resolve("players");
        long period = config.leaderboardRefreshSeconds * 20;
        tasks.add(core.scheduler().repeatingAsync(40, period, () -> {
            try {
                leaderboard.rebuild(players, service.config());
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "skills: leaderboard refresh failed", e);
            }
        }));
    }

    /** Re-reads skills.yml: tables, rewards, recipes, collections. Running tasks keep their period. */
    void reload() {
        core.getPlugin().reloadConfig();
        SkillsConfig config = new SkillsConfig(reloadedFeatureConfig());
        reportWarnings(config);
        service.config(config);
        enchanted.unregisterAll();
        enchanted.registerAll(config);
        recipes.registerAll();
        blockSources.reload();
        activitySources.reload();
        for (Player online : Bukkit.getOnlinePlayers()) {
            service.applyAllStats(online);
        }
    }

    private org.bukkit.configuration.file.YamlConfiguration reloadedFeatureConfig() {
        return core.featureConfig("skills");
    }

    // ---------------------------------------------------------------- player lifecycle

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            service.applyAllStats(player);
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "skills: could not apply stats for " + player.getName(), e);
        }
        core.scheduler().later(40, () -> {
            if (player.isOnline()) {
                service.settle(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.clearStats(event.getPlayer().getUniqueId());
        blockSources.forget(event.getPlayer().getUniqueId());
    }
}
