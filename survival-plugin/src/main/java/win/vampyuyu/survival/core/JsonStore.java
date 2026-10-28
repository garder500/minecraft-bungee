package win.vampyuyu.survival.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Atomic JSON files under a root folder (the plugin's data folder). Writes go to a temporary file then
 * {@code ATOMIC_MOVE}, so a crash never leaves a half-written file (the temporary name is unique per write). A file that does not parse is copied to
 * {@code <name>.corrupt}, logged, and replaced by the defaults in memory. Thread safe (no shared state; concurrent writes of the same path are whole-file, last one wins).
 * Paths are relative to the root and may not escape it.
 */
public final class JsonStore {
    /** Shared Gson (pretty printed, records supported). */
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path root;
    private final Logger log;

    public JsonStore(Path root, Logger log) {
        this.root = root.toAbsolutePath().normalize();
        this.log = log;
    }

    public Path root() {
        return root;
    }

    /** Resolves a relative path inside the root, rejecting traversal. */
    public Path resolve(String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the data folder: " + relative);
        }
        return path;
    }

    public boolean exists(String relative) {
        return Files.isRegularFile(resolve(relative));
    }

    /**
     * Reads a document. Missing file: {@code defaults.get()}. Corrupt file: logs, keeps a {@code .corrupt}
     * copy and returns {@code defaults.get()}. The file on disk is not rewritten by a read.
     */
    public <T> T read(String relative, Class<T> type, Supplier<T> defaults) {
        Path path = resolve(relative);
        if (!Files.isRegularFile(path)) {
            return defaults.get();
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            T value = GSON.fromJson(text, type);
            if (value == null) {
                throw new JsonParseException("empty document");
            }
            return value;
        } catch (JsonParseException | IOException e) {
            quarantine(path, e);
            return defaults.get();
        }
    }

    /** Reads raw text (null when missing). Used by callers that parse themselves; see {@link #quarantine}. */
    public String readText(String relative) throws IOException {
        Path path = resolve(relative);
        return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
    }

    /** Copies a bad file to {@code <name>.corrupt} and logs why. */
    public void quarantine(Path path, Exception cause) {
        Path copy = path.resolveSibling(path.getFileName() + ".corrupt");
        try {
            Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not keep a copy of corrupt file " + path, e);
        }
        log.log(Level.SEVERE, "Corrupt file " + path + " (starting empty, copy kept as " + copy.getFileName() + ")", cause);
    }

    /** Serialises and atomically writes {@code value}. */
    public void write(String relative, Object value) throws IOException {
        writeText(relative, GSON.toJson(value));
    }

    /**
     * Atomically writes text (already serialised JSON). The temporary file name is unique per call, so two threads
     * writing the same path never share a half-written file (the last move wins); the data is fsynced before the move.
     */
    public void writeText(String relative, String text) throws IOException {
        Path path = resolve(relative);
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
        try {
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(tmp,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Deletes a file if present. */
    public void delete(String relative) throws IOException {
        Files.deleteIfExists(resolve(relative));
    }
}
