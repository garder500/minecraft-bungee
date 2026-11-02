package win.vampyuyu.survival;

import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.FeatureManager;
import win.vampyuyu.survival.bastion.BastionFeature;
import win.vampyuyu.survival.boss.BossFeature;
import win.vampyuyu.survival.resonance.ResonanceFeature;
import win.vampyuyu.survival.classes.ClassesFeature;
import win.vampyuyu.survival.realm.AshesFeature;
import win.vampyuyu.survival.dungeon.DungeonFeature;
import win.vampyuyu.survival.economy.EconomyFeature;
import win.vampyuyu.survival.gadgets.GadgetsFeature;
import win.vampyuyu.survival.island.IslandFeature;
import win.vampyuyu.survival.menu.MenuFeature;
import win.vampyuyu.survival.quarry.QuarryFeature;
import win.vampyuyu.survival.realm.RealmFeature;
import win.vampyuyu.survival.trades.TradesFeature;
import win.vampyuyu.survival.utility.UtilityFeature;
import win.vampyuyu.survival.wings.WingsFeature;

public class SurvivalPlugin extends JavaPlugin {
    private Core core;
    private FeatureManager features;
    private win.vampyuyu.survival.data.DataHttpApi dataApi;

    /**
     * The features in enable order. Adding a feature means adding ONE line here (foundation owner only).
     * Services are published during enable(), so a feature only sees services of features listed before it.
     */
    static List<Feature> featureList() {
        // The simple Skyblock does not load the former progression systems, except the Wings.
        return List.of(new UtilityFeature(), new IslandFeature(), new WingsFeature(), new EconomyFeature(), new QuarryFeature(), new ClassesFeature(), new TradesFeature(), new GadgetsFeature(),
            new BossFeature(), new ResonanceFeature(), new DungeonFeature(), new BastionFeature(), new RealmFeature(), new AshesFeature(), new MenuFeature());
    }

    @Override
    public void onEnable() {
        core = new Core(this);
        core.start();
        win.vampyuyu.survival.data.Settings toggles = core.coreSettings();
        features = new FeatureManager(getLogger(),
            id -> toggles.getBoolean("features." + id + ".enabled", true), featureList());
        features.enableAll(core);
        startDataApi();
    }

    /**
     * The panel's Skyblock data endpoint (port 8086, same bridge token as the arena engine). Without the token file
     * (a dev server, the tests) it stays off: the plugin never fails to start because of it.
     */
    private void startDataApi() {
        try {
            java.nio.file.Path file = java.nio.file.Path.of(System.getenv()
                .getOrDefault("ROLE_BRIDGE_TOKEN_FILE", "/run/secrets/role_bridge_token"));
            if (!java.nio.file.Files.isReadable(file)) {
                getLogger().info("Skyblock data API off: no bridge token file");
                return;
            }
            String token = java.nio.file.Files.readString(file).trim();
            if (token.length() < 32) {
                getLogger().warning("Skyblock data API off: the bridge token is too short");
                return;
            }
            int port = Integer.parseInt(System.getenv().getOrDefault("SKYBLOCK_DATA_PORT", "8086"));
            dataApi = new win.vampyuyu.survival.data.DataHttpApi(core.dataAdmin(), token, port, getLogger());
        } catch (java.io.IOException | RuntimeException e) {
            getLogger().warning("Skyblock data API off: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (dataApi != null) {
            dataApi.close();
            dataApi = null;
        }
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
