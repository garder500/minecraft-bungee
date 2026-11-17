package win.vampyuyu.valoblock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Slots whose map may still be standing (built, not yet cleared): a small file, one slot number per line, rewritten on every
 * change. A server that stops or crashes during a match leaves its slot here, and the next start wipes it.
 */
public final class VbDirtySlots {
  private final Path file;
  private final TreeSet<Integer> slots = new TreeSet<>();

  public VbDirtySlots(Path file) {
    this.file = file;
    try {
      if (Files.isRegularFile(file))
        for (String line : Files.readAllLines(file)) {
          String t = line.trim();
          if (t.isEmpty()) continue;
          try {
            slots.add(Integer.parseInt(t));
          } catch (NumberFormatException ignored) {
            // a damaged line is dropped: the scan at start finds the slot anyway
          }
        }
    } catch (IOException e) {
      // unreadable file: treated as empty, the scan at start still wipes every slot that has blocks
    }
  }

  public synchronized boolean mark(int slot) {
    if (!slots.add(slot)) return false;
    save();
    return true;
  }

  public synchronized boolean clear(int slot) {
    if (!slots.remove(slot)) return false;
    save();
    return true;
  }

  public synchronized boolean isDirty(int slot) {
    return slots.contains(slot);
  }

  public synchronized List<Integer> snapshot() {
    return new ArrayList<>(slots);
  }

  private void save() {
    try {
      if (slots.isEmpty()) {
        Files.deleteIfExists(file);
        return;
      }
      if (file.getParent() != null) Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      StringBuilder b = new StringBuilder();
      for (int s : slots) b.append(s).append('\n');
      Files.writeString(tmp, b.toString());
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
