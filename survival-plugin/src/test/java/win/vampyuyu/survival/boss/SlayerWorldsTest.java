package win.vampyuyu.survival.boss;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

/** Design principle (docs/skyblock-design.md section 13): slayers never run in the hub. */
public class SlayerWorldsTest {
    @Test
    public void shippedSlayersStayOutOfTheHub() throws Exception {
        try (var in = new InputStreamReader(getClass().getResourceAsStream("/boss.yml"))) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(in);
            ConfigurationSection slayers = yml.getConfigurationSection("slayers");
            assertTrue(slayers != null && !slayers.getKeys(false).isEmpty());
            for (String id : slayers.getKeys(false)) {
                var worlds = slayers.getStringList(id + ".worlds");
                assertFalse(id + " must list at least one world", worlds.isEmpty());
                assertFalse(id + " must not run in the hub", worlds.contains("sb_hub"));
            }
        }
    }
}
