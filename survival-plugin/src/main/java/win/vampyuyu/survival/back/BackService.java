package win.vampyuyu.survival.back;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Remembers each player's last death place and enforces the /back cooldown. Pure logic, no Bukkit types. */
public final class BackService {
    public static final long COOLDOWN_MS = 5 * 60 * 1000L;

    public record Spot(String world, double x, double y, double z, float yaw, float pitch) {}

    public sealed interface Result {
        record Ok(Spot spot) implements Result {}
        record NoDeath() implements Result {}
        record Cooldown(long remainingMs) implements Result {}
    }

    private record State(Map<String, Spot> deaths, Map<String, Long> lastUse) {}

    private static final Gson GSON = new Gson();
    private final Path file;
    private final LongSupplier clock;
    private final long cooldownMs;
    private final Map<String, Spot> deaths = new HashMap<>();
    private final Map<String, Long> lastUse = new HashMap<>();

    public BackService(Path file, LongSupplier clock, long cooldownMs) {
        this.file = file;
        this.clock = clock;
        this.cooldownMs = cooldownMs;
    }

    public synchronized void recordDeath(UUID player, Spot spot) {
        deaths.put(player.toString(), spot);
        save();
    }

    /** Checks without consuming anything: the cooldown only starts once {@link #confirm} is called. */
    public synchronized Result peek(UUID player, boolean bypass) {
        Spot spot = deaths.get(player.toString());
        if (spot == null) {
            return new Result.NoDeath();
        }
        Long last = lastUse.get(player.toString());
        if (!bypass && last != null) {
            long remaining = last + cooldownMs - clock.getAsLong();
            if (remaining > 0) {
                return new Result.Cooldown(remaining);
            }
        }
        return new Result.Ok(spot);
    }

    /** Call after a successful teleport: forgets the death place and starts the cooldown. */
    public synchronized void confirm(UUID player, boolean bypass) {
        deaths.remove(player.toString());
        if (!bypass) {
            lastUse.put(player.toString(), clock.getAsLong());
        }
        save();
    }

    public synchronized void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            State state = GSON.fromJson(Files.readString(file), new TypeToken<State>() {}.getType());
            if (state != null) {
                if (state.deaths() != null) deaths.putAll(state.deaths());
                if (state.lastUse() != null) lastUse.putAll(state.lastUse());
            }
        } catch (IOException | RuntimeException e) {
            // A corrupt file must not stop the server: start empty, the next save rewrites it.
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(new State(deaths, lastUse)));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Losing one save only loses a /back spot; never break the death event.
        }
    }
}
