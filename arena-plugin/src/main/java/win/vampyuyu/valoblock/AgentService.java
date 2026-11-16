package win.vampyuyu.valoblock;

import java.util.List;
import java.util.UUID;

/** Slice 3: agents, their skins (applied at lock in, restored at match end) and abilities. */
public interface AgentService {
  AgentService NOOP = new AgentService() {};

  default List<String> agents() {
    return List.of();
  }

  /** The player locked an agent in: apply its skin and abilities. */
  default void lockedIn(VbMatch match, UUID player, String agent) {}

  /** Match over, quit or crash recovery: give the player's own skin back. */
  default void restore(UUID player) {}

  /** The whole match is over: stop every ability effect and restore the blocks they changed. */
  default void matchEnded(VbMatch match) {}

  /** A round starts: refill the signature, hand out the ability items ({@code keep}: bought charges stay). */
  default void roundStarted(VbMatch match, UUID player, boolean keep) {}

  default void phaseChanged(VbMatch match, VbPhase from, VbPhase to) {}

  /** A kill: ultimate points and signature recharge for the killer. */
  default void killed(VbMatch match, UUID victim, UUID killer) {}

  /** The player picked up an ultimate orb (the map slice places them). */
  default void orb(VbMatch match, UUID player) {}

  /** A bullet stopped on this block (walls of Bark and ice count hits). */
  default void bulletHit(org.bukkit.block.Block block) {}

  /** A shot travelled {@code dist} blocks from {@code o} along {@code d} (ghosts of Echo Step react to it). */
  default void bulletPassed(org.bukkit.entity.Player shooter, double[] o, double[] d, double dist) {}

  /** The Armory chest was built for a player: add the buyable abilities of their agent (last row). */
  default void decorateArmory(org.bukkit.inventory.Inventory inv, org.bukkit.entity.Player p, VbMatch match) {}

  /** A click in the Armory on a cell of the agents slice; true when it handled it. */
  default boolean armoryClick(org.bukkit.entity.Player p, VbMatch match, int slot, boolean sell) {
    return false;
  }

  /** The player is held still and cannot act (Hold the Moment). */
  default boolean frozen(UUID player) {
    return false;
  }

  /** Charges of the ability slots 4 to 7 (the ultimate is 1 when ready), or null without a kit. */
  default int[] charges(UUID player) {
    return null;
  }
}
