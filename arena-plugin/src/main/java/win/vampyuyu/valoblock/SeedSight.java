package win.vampyuyu.valoblock;

import java.util.List;
import java.util.UUID;

/**
 * Who sees what of the Rift Seed, as plain rules over a {@link VbMatch} (no Bukkit, so a test can read them): the carrier gets
 * the HUD icon and the planting bar; the carrier's squad sees the Seed on the carrier's back and the badge on their head in
 * the top bar; only Seedbearers see a dropped Seed glow and its marker. Sealers and the other squad see none of it.
 */
final class SeedSight {
  private SeedSight() {}

  /** The Seed is on a living carrier (before it is planted). */
  static UUID carrier(VbMatch m) {
    UUID c = m.seedCarrier();
    return c != null && m.alive().contains(c) ? c : null;
  }

  static boolean carries(VbMatch m, UUID id) {
    return id != null && id.equals(carrier(m));
  }

  /** The item display on the carrier's back: the carrier's squad-mates see it, the carrier (the item is in hand) and everyone else do not. */
  static boolean seesBack(VbMatch m, UUID viewer) {
    UUID c = carrier(m);
    return c != null && !c.equals(viewer) && m.participants().contains(viewer) && !m.hasLeft(viewer) && m.squadOf(viewer) == m.squadOf(c);
  }

  /** The small seed badge on the head of {@code head} in the top bar of a viewer of squad {@code viewerSquad}: the viewer's own squad only. */
  static boolean badgeOnHead(VbMatch m, int viewerSquad, UUID head) {
    UUID c = carrier(m);
    return c != null && c.equals(head) && m.squadOf(head) == viewerSquad;
  }

  /** The glow and the marker of a dropped Seed: every Seedbearer (dead ones too, they follow the round), nobody else. */
  static boolean seesDrop(VbMatch m, UUID viewer) {
    return m.participants().contains(viewer) && !m.hasLeft(viewer) && m.side(viewer) == VbSide.SEEDBEARERS;
  }

  /** The site whose box holds the point, or null. */
  static VbSite siteAt(List<VbSite> sites, double x, double y, double z) {
    for (VbSite s : sites) if (s.contains(x, y, z)) return s;
    return null;
  }

  /** What the HUD of this player shows of the Seed: null for everyone but the carrier. */
  static VbHudState.Seed hud(VbMatch m, UUID id, boolean inSite, long wall) {
    if (!carries(m, id)) return null;
    double required = Math.max(0.001, m.rules().plantHoldSeconds());
    boolean pulse = inSite && (wall / VbHudState.PULSE_MS) % 2 == 0;
    return new VbHudState.Seed(inSite, pulse, Math.max(0, Math.min(1, m.plantProgress() / required)));
  }
}
