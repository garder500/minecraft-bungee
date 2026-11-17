package win.vampyuyu.valoblock;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import win.vampyuyu.arena.ArenaConfig;
import win.vampyuyu.arena.ArenaPreset;
import win.vampyuyu.arena.GameHooks;
import win.vampyuyu.arena.GamePreset;

/** ValoBlock as a game of the arena engine. */
public final class VbPreset implements ArenaPreset {
  public static final String ID = "valoblock";
  private final GameHooks hooks;
  private final ValoBlockModule module;

  VbPreset(ValoBlockModule module, GameHooks hooks) {
    this.module = module;
    this.hooks = hooks;
  }

  public String id() {
    return ID;
  }

  public String title() {
    return "ValoBlock";
  }

  public Material icon() {
    return Material.CROSSBOW;
  }

  public int menuSlot() {
    return 13;
  }

  public int defaultMinPlayers() {
    return 1;
  }

  /** What {@link #validate} demands, so /arena create valoblock works as is. */
  @Override
  public int defaultMaxPlayers() {
    return VbData.rules().teamSize() * 2;
  }

  @Override
  public boolean defaultAllowPvP() {
    return true;
  }

  @Override
  public int defaultDurationSeconds() {
    return 3600;
  }

  public GameHooks hooks() {
    return hooks;
  }

  public GamePreset create(ArenaConfig c, Collection<UUID> participants) {
    VbMatch match = new VbMatch(VbData.rules(), participants, new SecureRandom());
    match.setEvents(module.events());
    return match;
  }

  @Override
  public GamePreset create(ArenaConfig c, Collection<UUID> participants, Map<UUID, String> forced) {
    return create(c, participants);
  }

  @Override
  public void validate(ArenaConfig c, World world) {
    if (!c.allowPvP()) throw new IllegalArgumentException("ValoBlock requires PvP enabled");
    if (c.minPlayers() < 1) throw new IllegalArgumentException("ValoBlock needs at least 1 player");
    if (c.maxPlayers() > VbData.rules().teamSize() * 2)
      throw new IllegalArgumentException("ValoBlock hosts at most " + VbData.rules().teamSize() * 2 + " players");
    if (c.durationSeconds() < 1800)
      throw new IllegalArgumentException("ValoBlock needs a duration of at least 1800 seconds");
  }
}
