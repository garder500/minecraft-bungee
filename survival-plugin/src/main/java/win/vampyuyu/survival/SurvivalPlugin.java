package win.vampyuyu.survival;

import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.FeatureManager;
import win.vampyuyu.survival.island.IslandFeature;
import win.vampyuyu.survival.menu.MenuFeature;
import win.vampyuyu.survival.utility.UtilityFeature;

public class SurvivalPlugin extends JavaPlugin {
    private Core core;
    private FeatureManager features;

    /**
     * The features in enable order. Adding a feature means adding ONE line here (foundation owner only).
     * Services are published during enable(), so a feature only sees services of features listed before it.
     */
    static List<Feature> featureList() {
        // The simple Skyblock deliberately does not load the former progression systems.
        return List.of(new UtilityFeature(), new IslandFeature(), new MenuFeature());
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = new Core(this);
        core.start();
        features = new FeatureManager(getLogger(),
            id -> getConfig().getBoolean("features." + id + ".enabled", true), featureList());
        features.enableAll(core);
    }

    @Override
    public void onDisable() {
        if (features != null) {
            features.disableAll();
        }
        if (core != null) {
            core.stop();
        }
    }

    /** Core of the running plugin (tests). */
    public Core core() {
        return core;
    }

    public FeatureManager features() {
        return features;
    }
}
