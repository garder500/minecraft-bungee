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

  /** A human took a bot's seat: they get its loadout. */
  default void adopt(UUID player, Loadout loadout) {}

  /** A gun lies on the ground (a dead bot's): a human can pick it up with its rounds. */
  default void dropGun(org.bukkit.Location at, VbWeapon weapon, int rounds) {}

  /** The name of the weapon in the player's hand, or null. */
  default String heldName(UUID player) {
    return null;
  }

  /** "18 / 50" for the held gun (rounds in the magazine / magazine size), or null when the hand holds no gun. */
  default String ammoText(UUID player) {
    return null;
  }

  /** Opens the Armory chest for the player (/vb debug armory); it only opens in the buy phase. */
  default void openArmory(org.bukkit.entity.Player player, VbMatch match) {}
}
