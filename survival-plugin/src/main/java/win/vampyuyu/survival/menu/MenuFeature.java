package win.vampyuyu.survival.menu;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;

/** Network mode routing, the quests (with their side panel) and the guide commands. /sbmenu is the island menu. */
public final class MenuFeature implements Feature {
    private static final String CHANNEL = "vampyuyu:skyblock";
    private HandoffSource source;
    private Core core;
    private QuestService quests;
    private SidebarService sidebar;
    private Scheduler.Handle maintenance;

    @Override public String id() { return "menu"; }

    @Override public void enable(Core core) {
        this.core = core;
        MenuConfig cfg = new MenuConfig(core.featureConfig("menu"),
            core.featureConfig("quarry").getConfigurationSection("quests.tutorial"), true, core.featureConfig("quests"));
        MenuRuntime rt = new MenuRuntime(core, cfg);
        source = cfg.redisEnabled ? RedisHandoffSource.fromEnvironment(core.getPlugin().getLogger(),
            cfg.redisHost, cfg.redisPort, cfg.redisPasswordFile, cfg.redisConnectTimeoutMs,
            cfg.redisSocketTimeoutMs, cfg.redisBackoffMs) : null;
        quests = new QuestService(rt);
        QuestsGui questsGui = new QuestsGui(rt, quests);
        sidebar = new SidebarService(rt, quests);
        JoinFlow join = new JoinFlow(rt, source, this::arrive);
        core.listen(new QuestListener(rt, quests));
        core.listen(sidebar);
        new GuideCommands(rt, quests, questsGui, sidebar).register();
        sidebar.start();
        // rolls the quest day over and re-checks island quests
        maintenance = core.scheduler().repeating(600, 600, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (MenuRuntime.inSkyblock(player.getWorld())) {
                    quests.sync(player);
                }
            }
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            core.scheduler().nextTick(() -> arrive(player));
        }
        core.listen(join);
        core.getPlugin().getServer().getMessenger().registerIncomingPluginChannel(core.getPlugin(), CHANNEL,
            (channel, player, bytes) -> {
                try {
                    String raw = new String(bytes, StandardCharsets.UTF_8);
                    String nonce = JsonParser.parseString(raw).getAsJsonObject().get("nonce").getAsString();
                    boolean trusted = player.getMetadata(CHANNEL).stream().anyMatch(value ->
                        value.getOwningPlugin().getName().equals("VampyuyuNetwork") && nonce.equals(value.asString()));
                    if (trusted) join.route(player, HandoffPayload.parse(raw));
                } catch (RuntimeException ignored) {
                    // Client-originated or malformed messages never authorize mode changes.
                }
            });
    }

    /** Everything that depends on the player being settled in the world after a join and its routing. */
    void arrive(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (MenuRuntime.inSkyblock(player.getWorld())) {
            quests.record(player, QuestType.VISIT_WORLD, MenuConfig.worldToken(player.getWorld().getName()), 1);
        }
        quests.sync(player);
        sidebar.refresh(player);
    }

    @Override public void disable() {
        if (maintenance != null) maintenance.cancel();
        if (sidebar != null) sidebar.stop();
        if (source != null) source.close();
        if (core != null) core.getPlugin().getServer().getMessenger().unregisterIncomingPluginChannel(core.getPlugin(), CHANNEL);
    }

    static List<String> commandNames() { return List.of("sbmenu"); }
}
