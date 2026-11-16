package win.vampyuyu.valoblock;

import java.util.UUID;

/** Slice 2: guns, ammo, raycast hits, the armory menu. The core only talks to this interface. */
public interface WeaponService {
  WeaponService NOOP = new WeaponService() {};

  /** A round starts: hand out the loadout (survivors keep theirs when {@code keep} is true). */
  default void roundStarted(VbMatch match, UUID player, boolean keep) {}

  /** Changes how fast the player reloads for {@code seconds}: 1 is normal, 1.5 is faster, 0 or less means no reload at all, 100 is instant. */
  default void reloadModifier(UUID player, double factor, double seconds) {}

  /** Remove every gun the player holds (round or match end). */
  default void clear(UUID player) {}
}
