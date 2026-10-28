package win.vampyuyu.survival.utility;

import java.nio.file.Path;
import org.bukkit.command.PluginCommand;
import win.vampyuyu.survival.back.BackCommand;
import win.vampyuyu.survival.back.BackService;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Quality-of-life features. Wires /back exactly as before the core existed (plugin.yml entry, back.json at the data root). */
public final class UtilityFeature implements Feature {
    @Override
    public String id() {
        return "utility";
    }

    @Override
    public void enable(Core core) {
        Path file = core.getDataFolder().toPath().resolve("back.json");
        BackService back = new BackService(file, System::currentTimeMillis, BackService.COOLDOWN_MS);
        back.load();
        BackCommand backCommand = new BackCommand(back);
        core.listen(backCommand);
        PluginCommand command = core.getPlugin().getCommand("back");
        if (command != null) {
            command.setExecutor(backCommand);
        }
    }
}
