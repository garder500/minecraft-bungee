package win.vampyuyu.survival.menu;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

/** The guide commands: {@code /guide}, {@code /quests}, {@code /track} and {@code /hud}. Skyblock worlds only. */
final class GuideCommands {
    private final MenuRuntime rt;
    private final QuestService quests;
    private final QuestsGui gui;
    private final SidebarService sidebar;

    GuideCommands(MenuRuntime rt, QuestService quests, QuestsGui gui, SidebarService sidebar) {
        this.rt = rt;
        this.quests = quests;
        this.gui = gui;
        this.sidebar = sidebar;
    }

    void register() {
        rt.core.commands().command("guide")
            .description("What to do next: your current quest and how to do it")
            .usage("/guide")
            .permission("vampyuyu.sb.menu.guide", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> player(s, p -> guide(p))).register();
        rt.core.commands().command("quests").aliases("quest")
            .description("Open the quest list")
            .usage("/quests")
            .permission("vampyuyu.sb.menu.quests", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> player(s, p -> {
                if (inSkyblock(p)) {
                    gui.open(p);
                }
            })).register();
        rt.core.commands().command("track")
            .description("Choose the quest shown on the side panel")
            .usage("/track <quest id>")
            .permission("vampyuyu.sb.menu.quests", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> player(s, p -> track(p, a)))
            .tab((s, c, l, a) -> a.length == 1 && s instanceof Player p ? ids(p, a[0]) : List.of()).register();
        rt.core.commands().command("hud")
            .description("Show or hide the side panel")
            .usage("/hud [on|off]")
            .permission("vampyuyu.sb.menu.guide", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> player(s, p -> hud(p, a)))
            .tab((s, c, l, a) -> a.length == 1 ? List.of("on", "off") : List.of()).register();
    }

    private boolean inSkyblock(Player player) {
        if (MenuRuntime.inSkyblock(player.getWorld())) {
            return true;
        }
        rt.core.msg().warn(player, "The guide only works in Skyblock.");
        return false;
    }

    private boolean player(CommandSender sender, java.util.function.Consumer<Player> run) {
        if (sender instanceof Player p) {
            run.accept(p);
        } else {
            rt.core.msg().error(sender, "Only players can use this command.");
        }
        return true;
    }

    private void guide(Player player) {
        if (!inSkyblock(player)) {
            return;
        }
        var tracked = quests.tracked(player);
        if (tracked.isEmpty()) {
            rt.core.msg().ok(player, "You finished every quest of the tutorial. Use /quests to see the daily ones.");
            return;
        }
        QuestDef def = tracked.get().def();
        rt.core.msg().info(player, "Current quest: " + def.name() + " (" + tracked.get().progress() + "/" + def.amount() + ")");
        rt.core.msg().info(player, def.description());
        String how = hint(def);
        if (how != null) {
            rt.core.msg().ok(player, "How: " + how);
        }
        rt.core.msg().info(player, "/quests lists them all, /track <id> picks the one on your side panel, /hud hides it.");
    }

    /** The command or the action that moves a quest forward, by what the quest counts. */
    static String hint(QuestDef def) {
        String target = def.target().toLowerCase(Locale.ROOT);
        return switch (def.type()) {
            case ORE_FOUND -> "break the cobble or stone your generator makes (water meeting lava). A Miner next to it does it for you.";
            case FORGE_ITEM -> "open the Forge with /forge (or right-click your Forge block), pick the recipe and come back when the timer ends.";
            case GENERATOR_UPGRADE -> "forge the core with /forge, then right-click it in your hand on your island.";
            case CRAFT_ITEM -> target.contains("forge")
                ? "craft it in a crafting table, place it on your island, then open it with /forge."
                : "craft it in a crafting table.";
            case BOSS_KILL -> "forge the summon item with /forge and use it on your island.";
            case COMMAND -> "type the command named in the quest.";
            case ISLAND_CREATE -> "use /island to create your island.";
            case VISIT_WORLD -> "use /island nether or /island end, or take the portal.";
            case COINS_EARN -> "sell your items at the market with /market, or to other players with /ah.";
            default -> null;
        };
    }

    private void track(Player player, String[] args) {
        if (!inSkyblock(player)) {
            return;
        }
        if (args.length != 1) {
            rt.core.msg().info(player, "Usage: /track <quest id>. Tab completes the quests you can do now.");
            return;
        }
        if (quests.track(player, args[0].toLowerCase(Locale.ROOT))) {
            rt.core.msg().ok(player, "Now tracking " + args[0] + " on your side panel.");
            sidebar.refresh(player);
        } else {
            rt.core.msg().warn(player, "No such quest, or you cannot do it yet.");
        }
    }

    private void hud(Player player, String[] args) {
        UUID id = player.getUniqueId();
        MenuData prefs = rt.prefs(id);
        boolean on = args.length > 0 ? args[0].equalsIgnoreCase("on") : !prefs.sidebar;
        prefs.sidebar = on;
        rt.dirty(id);
        sidebar.refresh(player);
        rt.core.msg().ok(player, "Side panel " + (on ? "shown" : "hidden") + ".");
    }

    private List<String> ids(Player player, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return rt.engine().activeTutorial(rt.quests(player.getUniqueId())).stream().map(QuestDef::id)
            .filter(id -> id.startsWith(lower)).toList();
    }
}
