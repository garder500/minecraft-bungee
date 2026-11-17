package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.Set;
import org.bukkit.GameMode;
import org.junit.Test;
import win.vampyuyu.arena.ArenaConfig;

/** /arena create builds a new arena from its preset's defaults: a ValoBlock arena made that way must pass validate as is. */
public class VbPresetDefaultsTest {
  private final VbPreset preset = new VbPreset(null, null);

  private ArenaConfig created(boolean pvp, int duration, int max) {
    return new ArenaConfig("valoblock", "valoblock", "world", VbPreset.ID,
        new ArenaConfig.Bounds(0, 60, 0, 60, 90, 60), new ArenaConfig.Spawn(30, 65, 30, 0, 0), Set.of(), Set.of(),
        pvp, false, false, GameMode.ADVENTURE, duration, preset.defaultMinPlayers(), max, null,
        new ArenaConfig.Options(preset.defaultOptions()));
  }

  @Test
  public void anArenaCreatedWithThePresetDefaultsIsValid() {
    assertTrue(preset.defaultAllowPvP());
    preset.validate(created(preset.defaultAllowPvP(), preset.defaultDurationSeconds(), preset.defaultMaxPlayers()), null);
  }

  @Test
  public void theOldEngineDefaultsWereRefused() {
    // what /arena create used for every game before: PvP off, 900 s, 16 players
    assertThrows(IllegalArgumentException.class, () -> preset.validate(created(false, 900, 16), null));
  }
}
