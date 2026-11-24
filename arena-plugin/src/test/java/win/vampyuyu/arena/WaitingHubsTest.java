package win.vampyuyu.arena;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import org.junit.Test;
import win.vampyuyu.valoblock.VbMapService;
import win.vampyuyu.valoblock.VbPreset;
import win.vampyuyu.werewolf.WerewolfPreset;
import win.vampyuyu.werewolf.sky.SkySlot;
import win.vampyuyu.werewolf.sky.SkyWorld;

public class WaitingHubsTest {
  @Test
  public void valoblockHubConfigIsValidAndDisjointFromMatchSlots() {
    ArenaConfig c = WaitingHubs.valoblockConfig(new JsonObject());
    assertEquals("valoblock", c.id());
    assertEquals(VbPreset.ID, c.preset());
    assertEquals(VbMapService.WORLD, c.worldName());
    assertTrue("spawn must sit inside bounds", c.bounds().contains(c.spawn().x(), c.spawn().y(), c.spawn().z()));
    assertEquals(1, c.minPlayers());
    assertEquals(10, c.maxPlayers());
    // Every match slot starts at x = slot * SPACING >= 0; the waiting hub must stay strictly negative.
    assertTrue("hub must not overlap match slot 0", c.bounds().maxX() < 0);
  }

  @Test
  public void werewolfHubConfigIsValidAndDisjointFromSkySlots() {
    WerewolfPreset preset = new WerewolfPreset(null);
    ArenaConfig c = WaitingHubs.werewolfConfig(preset.defaultOptions());
    assertEquals("werewolf", c.id());
    assertEquals("werewolf", c.preset());
    assertEquals(SkyWorld.NAME, c.worldName());
    assertTrue("spawn must sit inside bounds", c.bounds().contains(c.spawn().x(), c.spawn().y(), c.spawn().z()));
    assertEquals(4, c.minPlayers());
    assertEquals(20, c.maxPlayers());
    // Every sky platform slot is centred at x = index * SPACING >= 0 (max radius ~40).
    SkySlot first = new SkySlot(0);
    assertTrue("hub must stay far from sky slot 0", c.bounds().maxX() < first.centreX() - 100);
  }
}
