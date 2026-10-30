package win.vampyuyu.survival.minions;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads the shipped minions.yml for tests. */
final class TestConfig {
    private TestConfig() {}

    static YamlConfiguration yaml() {
        try (var in = TestConfig.class.getClassLoader().getResourceAsStream("minions.yml");
                var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static MinionConfig load() {
        return MinionConfig.load(yaml(), Logger.getLogger("test"));
    }
}
