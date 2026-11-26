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

  @Test
  public void builtinConfigFromTheCodeAlwaysWins() {
    ArenaConfig fresh = WaitingHubs.valoblockConfig(new JsonObject());
    ArenaConfig old =
        new ArenaConfig(
            fresh.id(), "Old", fresh.worldName(), fresh.preset(),
            new ArenaConfig.Bounds(-532, 55, -20, -492, 95, 20),
            new ArenaConfig.Spawn(-511.5, 65, 3.5, 180f, 0f),
            fresh.allowedBreakBlocks(), fresh.allowedPlaceBlocks(), fresh.allowPvP(), fresh.allowChests(),
            fresh.allowDoors(), fresh.gameMode(), fresh.durationSeconds(), fresh.minPlayers(), fresh.maxPlayers(),
            fresh.rewards(), fresh.options());
    assertTrue("missing: written", ArenaManager.builtinNeedsWrite(null, false, fresh));
    assertTrue("an older saved copy is overwritten", ArenaManager.builtinNeedsWrite(old, false, fresh));
    assertFalse("the same config is not rewritten", ArenaManager.builtinNeedsWrite(fresh, false, fresh));
    assertFalse("an arena in use is left alone", ArenaManager.builtinNeedsWrite(old, true, fresh));
  }

  @Test
  public void onlyTheHubArenasAreHubs() {
    assertEquals(WaitingHubs.VALOBLOCK_WORLD, WaitingHubs.hubOf(WaitingHubs.valoblockConfig(new JsonObject())));
    assertEquals(
        WaitingHubs.WEREWOLF_WORLD,
        WaitingHubs.hubOf(WaitingHubs.werewolfConfig(new WerewolfPreset(null).defaultOptions())));
    ArenaConfig vb = WaitingHubs.valoblockConfig(new JsonObject());
    ArenaConfig elsewhere =
        new ArenaConfig(
            "e2e", "e2e", "world", vb.preset(), new ArenaConfig.Bounds(-20, 40, -20, 20, 90, 20),
            new ArenaConfig.Spawn(0, 65, 0, 0f, 0f),
            vb.allowedBreakBlocks(), vb.allowedPlaceBlocks(), vb.allowPvP(), vb.allowChests(), vb.allowDoors(),
            vb.gameMode(), vb.durationSeconds(), vb.minPlayers(), vb.maxPlayers(), vb.rewards(), vb.options());
    assertNull(WaitingHubs.hubOf(elsewhere));
  }
}
