package win.vampyuyu.werewolf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Werewolf data that an operator can change without rebuilding the plugin. The jar keeps the defaults
 * (also read by the pack's art scripts); a file of the same resource path under
 * {@code <plugin data>/overrides/} wins when it exists, and {@code /ww reload} picks it up. Nothing is
 * copied out of the jar, so a plugin update still brings new defaults to every file not overridden.
 * Unset (tests): the jar only.
 */
public final class DataFiles {
  private static volatile Path root;

  private DataFiles() {}

  /** A null folder turns the overrides off (tests). */
  public static void init(Path dataFolder) {
    root = dataFolder == null ? null : dataFolder.resolve("overrides");
  }

  /** The override file for a resource path such as "/roles/SEER.json", or null when there is none. */
  public static Path override(String resource) {
    Path base = root;
    if (base == null) return null;
    Path file = base.resolve(resource.substring(1)).normalize();
    return file.startsWith(base) && Files.isRegularFile(file) ? file : null;
  }

  /** The override when present, else the jar resource; null when neither exists. Caller closes. */
  public static InputStream open(Class<?> owner, String resource) throws IOException {
    Path file = override(resource);
    return file != null ? Files.newInputStream(file) : owner.getResourceAsStream(resource);
  }
}
