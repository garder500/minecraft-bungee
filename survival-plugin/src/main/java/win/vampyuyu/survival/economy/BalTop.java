package win.vampyuyu.survival.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.JsonStore;

/**
 * Richest players (purse + bank), cached. The scan reads every {@code players/*.json} OFF the main thread without
 * loading those profiles into the profile cache; online players are read from memory so they are up to date.
 */
final class BalTop {
    record Row(UUID id, String name, long purse, long bank) {
        long total() { return purse + bank; }
    }

    private final Core core;
    private final EconomyService economy;
    private volatile List<Row> rows = List.of();
    private volatile long computedAt;
    private final AtomicBoolean running = new AtomicBoolean();

    BalTop(Core core, EconomyService economy) {
        this.core = core;
        this.economy = economy;
    }

    List<Row> rows() {
        return rows;
    }

    boolean fresh() {
        return computedAt > 0 && System.currentTimeMillis() - computedAt < economy.settings().baltopCacheMillis();
    }

    /** Refreshes in the background; {@code done} runs on the main thread when the rows are ready. */
    void refresh(Consumer<List<Row>> done) {
        if (!running.compareAndSet(false, true)) {
            if (done != null) {
                core.scheduler().later(40, () -> done.accept(rows));
            }
            return;
        }
        core.scheduler().async(() -> {
            List<Row> result;
            try {
                result = scan();
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not compute /baltop", e);
                result = rows;
            } finally {
                running.set(false);
            }
            List<Row> finalRows = result;
            core.scheduler().sync(() -> {
                rows = finalRows;
                computedAt = System.currentTimeMillis();
                if (done != null) {
                    done.accept(finalRows);
                }
            });
        });
    }

    List<Row> scan() {
        Path dir = core.store().root().resolve("players");
        List<Row> out = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : files) {
                    Row row = readRow(file);
                    if (row != null && row.total() > 0) {
                        out.add(row);
                    }
                }
            } catch (IOException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not list the player profiles", e);
            }
        }
        out.sort(Comparator.comparingLong(Row::total).reversed().thenComparing(Row::name));
        int max = economy.settings().baltopSize();
        return List.copyOf(out.size() > max ? out.subList(0, max) : out);
    }

    private Row readRow(Path file) {
        String fileName = file.getFileName().toString();
        UUID id;
        try {
            id = UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
        try {
            JsonObject doc = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String name = doc.has("name") ? doc.get("name").getAsString() : "";
            if (core.profiles().isLoaded(id)) {
                EconomyData live = economy.data(id);
                return new Row(id, name.isEmpty() ? id.toString().substring(0, 8) : name, live.cents, live.bankCents);
            }
            JsonElement sections = doc.get("sections");
            if (sections == null || !sections.isJsonObject() || !sections.getAsJsonObject().has(EconomyService.SECTION)) {
                return null;
            }
            EconomyData data = JsonStore.GSON.fromJson(sections.getAsJsonObject().get(EconomyService.SECTION),
                EconomyData.class);
            if (data == null) {
                return null;
            }
            return new Row(id, name.isEmpty() ? id.toString().substring(0, 8) : name, data.cents, data.bankCents);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
