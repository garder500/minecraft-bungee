package win.vampyuyu.survival;

import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;
import win.vampyuyu.survival.back.BackCommand;
import win.vampyuyu.survival.back.BackService;

public final class SurvivalPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        Path file = getDataFolder().toPath().resolve("back.json");
        BackService back = new BackService(file, System::currentTimeMillis, BackService.COOLDOWN_MS);
        back.load();
        BackCommand backCommand = new BackCommand(back);
        getServer().getPluginManager().registerEvents(backCommand, this);
        var command = getCommand("back");
        if (command != null) {
            command.setExecutor(backCommand);
        }
    }
}
