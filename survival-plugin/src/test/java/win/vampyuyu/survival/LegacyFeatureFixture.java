package win.vampyuyu.survival;

import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.FeatureManager;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.HubManager;
import win.vampyuyu.survival.island.IslandService;
import win.vampyuyu.survival.island.WorldSetup;

/** Isolated compatibility tests for retired modules. Never used by the production plugin. */
public final class LegacyFeatureFixture {
    private static final java.util.Map<SurvivalPlugin, FeatureManager> instances = new java.util.WeakHashMap<>();
    private LegacyFeatureFixture() {}
    public static FeatureManager features(SurvivalPlugin plugin) { return instances.get(plugin); }
    public static void disable(SurvivalPlugin plugin) {
        FeatureManager manager = instances.remove(plugin);
        if (manager != null) manager.disableAll();
    }
    public static void enable(SurvivalPlugin plugin) {
        var core = plugin.core();
        HandlerList.unregisterAll(core.modes());
        for (var listener : HandlerList.getRegisteredListeners(plugin))
            if (listener.getListener() instanceof win.vampyuyu.survival.island.IslandTracker)
                HandlerList.unregisterAll(listener.getListener());
        var worlds = new WorldSetup(plugin.getLogger());
        worlds.ensure(SkyblockWorlds.HUB, World.Environment.NORMAL, Biome.PLAINS, 101);
        worlds.ensure(SkyblockWorlds.END, World.Environment.THE_END, Biome.THE_END, 65);
        new HubManager(core, (IslandService) core.require(IslandApi.class)).start();
        // economy and boss are live features now: only load the retired ones the plugin did not start itself
        var live = plugin.features().enabledIds();
        FeatureManager features = new FeatureManager(plugin.getLogger(), id -> true, List.<win.vampyuyu.survival.core.Feature>of(
            new win.vampyuyu.survival.economy.EconomyFeature(),
            new win.vampyuyu.survival.items.ItemsFeature(),
            new win.vampyuyu.survival.skills.SkillsFeature(),
            new win.vampyuyu.survival.boss.BossFeature(),
            new win.vampyuyu.survival.minions.MinionsFeature(),
            new win.vampyuyu.survival.dungeon.DungeonFeature()).stream().filter(f -> !live.contains(f.id())).toList());
        instances.put(plugin, features);
        features.enableAll(core);
        core.listen(new Listener() {
            @EventHandler public void disable(PluginDisableEvent event) {
                if (event.getPlugin() == plugin) features.disableAll();
            }
        });
    }
}
