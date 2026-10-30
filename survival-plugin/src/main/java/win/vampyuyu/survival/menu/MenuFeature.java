package win.vampyuyu.survival.menu;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Only explicit network mode routing remains. /sbmenu is the simple island menu. */
public final class MenuFeature implements Feature {
    private static final String CHANNEL = "vampyuyu:skyblock";
    private HandoffSource source;
    private Core core;

    @Override public String id() { return "menu"; }

    @Override public void enable(Core core) {
        this.core = core;
        MenuConfig cfg = new MenuConfig(core.featureConfig("menu"));
        MenuRuntime rt = new MenuRuntime(core, cfg);
        source = cfg.redisEnabled ? RedisHandoffSource.fromEnvironment(core.getPlugin().getLogger(),
            cfg.redisHost, cfg.redisPort, cfg.redisPasswordFile, cfg.redisConnectTimeoutMs,
            cfg.redisSocketTimeoutMs, cfg.redisBackoffMs) : null;
        JoinFlow join = new JoinFlow(rt, source, player -> {});
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

    @Override public void disable() {
        if (source != null) source.close();
        if (core != null) core.getPlugin().getServer().getMessenger().unregisterIncomingPluginChannel(core.getPlugin(), CHANNEL);
    }

    static List<String> commandNames() { return List.of("sbmenu"); }
}
