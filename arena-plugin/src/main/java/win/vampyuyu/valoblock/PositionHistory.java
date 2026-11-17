package win.vampyuyu.valoblock;

/**
 * Ring buffer of one player's feet positions for lag compensation: {@link #at} answers where the
 * player was up to {@link #MAX_REWIND_MS} ago, interpolating between samples. Not thread safe: use it on
 * the server thread only.
 */
public final class PositionHistory {
  public static final long MAX_REWIND_MS = 200;

  /** A position at a time. */
  public record Sample(long time, double x, double y, double z, boolean sneaking) {}

  private final Sample[] ring;
  private int head; // next write
  private int size;

  public PositionHistory() {
    this(16);
  }

  PositionHistory(int capacity) {
    ring = new Sample[capacity];
  }

  public void record(long time, double x, double y, double z, boolean sneaking) {
    if (size > 0 && time <= last().time()) return; // time never goes backwards
    ring[head] = new Sample(time, x, y, z, sneaking);
    head = (head + 1) % ring.length;
    size = Math.min(size + 1, ring.length);
  }

  private Sample last() {
    return ring[(head - 1 + ring.length) % ring.length];
  }

  /** The position {@code rewindMs} before {@code now} (clamped to 0..200 ms and to what was recorded); null with no sample at all. */
  public Sample at(long now, long rewindMs) {
    if (size == 0) return null;
    long t = now - Math.max(0, Math.min(MAX_REWIND_MS, rewindMs));
    Sample newer = null;
    for (int i = 0; i < size; i++) {
      Sample s = ring[(head - 1 - i + 2 * ring.length) % ring.length];
      if (s.time() <= t) {
        if (newer == null) return s; // the latest sample is at or before t
        double f = (double) (t - s.time()) / (newer.time() - s.time());
        return new Sample(t, s.x() + (newer.x() - s.x()) * f, s.y() + (newer.y() - s.y()) * f, s.z() + (newer.z() - s.z()) * f, f < 0.5 ? s.sneaking() : newer.sneaking());
      }
      newer = s;
    }
    return ring[(head - size + ring.length) % ring.length]; // older than anything kept: the oldest sample
  }
}
