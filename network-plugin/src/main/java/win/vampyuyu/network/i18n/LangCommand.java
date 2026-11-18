package win.vampyuyu.network.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /lang}: shows the language, {@code /lang fr|en|de} picks one, {@code /lang auto} follows the client again. */
public final class LangCommand implements TabExecutor {
    private final I18n i18n;

    public LangCommand(I18n i18n) {
        this.i18n = i18n;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            i18n.send(sender, "lang.players_only");
            return true;
        }
        if (args.length == 0) {
            Lang current = i18n.of(player);
            i18n.send(player, i18n.isChosen(player) ? "lang.current_chosen" : "lang.current_auto", "language", current.label());
            i18n.send(player, "lang.usage", "options", options());
            return true;
        }
        String arg = args[0].toLowerCase(Locale.ROOT);
        if (arg.equals("auto")) {
            i18n.choose(player, null);
            i18n.send(player, "lang.auto", "language", i18n.of(player).label());
            return true;
        }
        Lang lang = Lang.parse(arg);
        if (lang == null) {
            i18n.send(player, "lang.unknown", "input", args[0], "options", options());
            return true;
        }
        i18n.choose(player, lang);
        // The confirmation is already in the new language.
        i18n.send(player, "lang.set", "language", lang.label());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Arrays.stream(Lang.values()).map(Lang::code).filter(code -> code.startsWith(prefix)).toList();
    }

    private static String options() {
        return String.join(", ", Arrays.stream(Lang.values()).map(Lang::code).toList()) + ", auto";
    }
}
