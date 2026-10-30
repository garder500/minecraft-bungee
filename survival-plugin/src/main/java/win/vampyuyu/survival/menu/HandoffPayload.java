package win.vampyuyu.survival.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Optional;

/**
 * The value stored under {@code sb:transfer:<uuid>} by whoever sends a player to the survival server.
 *
 * <h2>Accepted formats</h2>
 * <ul>
 *   <li>JSON object {@code {"dest":"skyblock","arg":"hub","ts":1730000000000}}: only {@code dest} is needed.
 *       {@code arg} and {@code ts} (epoch millis) are optional.</li>
 *   <li>A bare word that is not JSON, e.g. {@code skyblock} or {@code dungeon}: same as {@code {"dest":"word"}}.</li>
 * </ul>
 *
 * <h2>Destinations</h2>
 * <ul>
 *   <li>{@code skyblock} (also {@code hub}, {@code island}, {@code home}): the Skyblock mode. {@code arg} may force
 *       {@code hub} or {@code island}; with no arg the player goes to their island, or to the hub when they have none.</li>
 *   <li>{@code dungeon}: rejoin a dungeon run, only honoured when the dungeon feature says the player is in one.</li>
 *   <li>Anything else, an empty value or a JSON value of another shape: treated as {@code skyblock} (the key prefix
 *       {@code sb:} is reserved for Skyblock, so a present key means "this player comes for Skyblock").</li>
 * </ul>
 * A payload whose {@code ts} is older than the configured maximum age is stale and ignored by the router.
 */
public record HandoffPayload(Dest dest, String arg, long timestamp) {
    /** What the sender wants. */
    public enum Dest { SKYBLOCK, HUB, ISLAND, DUNGEON }

    public static final long NO_TIMESTAMP = -1;

    public HandoffPayload {
        arg = arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT);
    }

    /** Parses a raw value; never throws. An empty/blank value gives {@code empty()}. */
    public static Optional<HandoffPayload> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = raw.trim();
        String dest = text;
        String arg = "";
        long ts = NO_TIMESTAMP;
        if (text.startsWith("{")) {
            try {
                JsonElement parsed = JsonParser.parseString(text);
                if (parsed.isJsonObject()) {
                    JsonObject json = parsed.getAsJsonObject();
                    dest = string(json, "dest");
                    arg = string(json, "arg");
                    ts = number(json, "ts");
                } else {
                    dest = "";
                }
            } catch (RuntimeException e) {
                dest = "";
            }
        }
        return Optional.of(new HandoffPayload(destOf(dest), arg, ts));
    }

    private static Dest destOf(String dest) {
        return switch (dest == null ? "" : dest.trim().toLowerCase(Locale.ROOT)) {
            case "hub" -> Dest.HUB;
            case "island", "home" -> Dest.ISLAND;
            case "dungeon" -> Dest.DUNGEON;
            default -> Dest.SKYBLOCK;
        };
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString();
    }

    private static long number(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return NO_TIMESTAMP;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return NO_TIMESTAMP;
        }
    }

    /** True when {@code ts} is present and more than {@code maxAgeMillis} before {@code nowMillis}. */
    public boolean isStale(long nowMillis, long maxAgeMillis) {
        return timestamp != NO_TIMESTAMP && nowMillis - timestamp > maxAgeMillis;
    }
}
