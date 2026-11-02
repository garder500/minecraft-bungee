package win.vampyuyu.survival.runes;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.RuneApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * Runes (GDD 10) and the runes of the Realms (GDD 3.2): the Runecraft table, rune items, engraving. Stat runes ride on the
 * item bonuses; Realm runes (Scorch of the Cracked Ashes, Verdant of the Whispering Grove) bring an effect. See
 * {@code docs/skyblock-notes/runes.md}.
 */
public final class RuneFeature implements Feature {
    @Override
    public String id() {
        return "runes";
    }

    @Override
    public void enable(Core core) {
        RuneConfig config = RuneConfig.load(core.data());
        config.problems().forEach(p -> core.getPlugin().getLogger().warning("runes data: " + p));
        RuneItems.register(core.items(), config);
        RuneService service = new RuneService(core, config);
        core.items().loreExtra(service::lore);
        core.listen(new RuneListener(core, service));
        core.permissions().register("vampyuyu.sb.runes.use", "Open the Runecraft table", PermissionDefault.TRUE);
        core.commands().command("runecraft").aliases("runes").description("Open the Runecraft table")
            .usage("/runecraft").permission("vampyuyu.sb.runes.use", PermissionDefault.TRUE)
            .executor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    RunecraftMenu.open(core, service, player);
                } else {
                    core.msg().error(sender, "Players only.");
                }
                return true;
            }).register();
        core.register(RuneApi.class, service);
        core.register(RuneService.class, service);
        core.onDataChange(domain -> {
            if (domain.equals("runes")) {
                service.config(RuneConfig.load(core.data()));
            }
        });
    }
}
