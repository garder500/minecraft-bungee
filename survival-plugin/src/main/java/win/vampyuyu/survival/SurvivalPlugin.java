package win.vampyuyu.survival;

import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import win.vampyuyu.survival.boss.BossFeature;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.FeatureManager;
import win.vampyuyu.survival.dungeon.DungeonFeature;
import win.vampyuyu.survival.economy.EconomyFeature;
import win.vampyuyu.survival.island.IslandFeature;
import win.vampyuyu.survival.items.ItemsFeature;
import win.vampyuyu.survival.menu.MenuFeature;
import win.vampyuyu.survival.minions.MinionsFeature;
import win.vampyuyu.survival.skills.SkillsFeature;
import win.vampyuyu.survival.utility.UtilityFeature;
import win.vampyuyu.survival.wings.WingsFeature;

public class SurvivalPlugin extends JavaPlugin {
    private Core core;
    private FeatureManager features;

    /**
     * The features in enable order. Adding a feature means adding ONE line here (foundation owner only).
     * Services are published during enable(), so a feature only sees services of features listed before it.
     */
    static List<Feature> featureList() {
        return List.of(
            new UtilityFeature(),
            new IslandFeature(),
            new EconomyFeature(),
            new ItemsFeature(),
            new SkillsFeature(),
            new WingsFeature(),
            new BossFeature(),
            new MinionsFeature(),
            new MenuFeature(),
            new DungeonFeature());
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
