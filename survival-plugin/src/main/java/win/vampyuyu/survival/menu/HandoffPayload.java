package win.vampyuyu.survival.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Optional;

/** Explicit mode selection from the network. Legacy hub/home landings resolve to the personal island.
 * Malformed or unknown destinations never authorize a mode change. */
public record HandoffPayload(Dest dest, String arg, long timestamp) {
    /** What the sender wants. */
    public enum Dest { SKYBLOCK, HUB, ISLAND, DUNGEON, SURVIVAL }

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
        Dest destination = destOf(dest);
        return destination == null ? Optional.empty() : Optional.of(new HandoffPayload(destination, arg, ts));
    }

    private static Dest destOf(String dest) {
        return switch (dest == null ? "" : dest.trim().toLowerCase(Locale.ROOT)) {
            case "survival" -> Dest.SURVIVAL;
            case "hub" -> Dest.HUB;
            case "island", "home" -> Dest.ISLAND;
            case "dungeon" -> Dest.DUNGEON;
            case "skyblock" -> Dest.SKYBLOCK;
            default -> null;
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
