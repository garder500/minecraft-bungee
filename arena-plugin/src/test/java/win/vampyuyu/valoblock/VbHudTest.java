package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

public class VbHudTest {
  private static String plain(net.kyori.adventure.text.Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  private VbMatch match() {
    List<UUID> humans = new ArrayList<>();
    for (int i = 0; i < 10; i++) humans.add(UUID.randomUUID());
    return new VbMatch(VbData.rules(), humans, new Random(3));
  }

  @Test
  public void clockIsMinutesAndSeconds() {
    assertEquals("1:12", VbHudService.clock(72));
    assertEquals("0:05", VbHudService.clock(5));
  }

  private static String json(net.kyori.adventure.text.Component c) {
    return net.kyori.adventure.text.serializer.json.JSONComponentSerializer.json().serialize(c);
  }

  private static long count(String text, String needle) {
    return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
  }

  @Test
  public void topLineShowsHeadsScoreClockAndRound() {
    VbMatch m = match();
    m.advance();
    m.advance(); // combat, round 1
    String line = plain(VbHudService.topLine(m, 0, VbPhase.COMBAT, 72));
    assertTrue(line, line.contains("1:12"));
    assertTrue(line, line.contains("ROUND 1"));
    // five heads a side, on the inline font and in white so the sprites keep their colours
    String j = json(VbHudService.topLine(m, 0, VbPhase.COMBAT, 72));
    assertEquals(10, count(j, "vampyuyu:vbinline"));
    assertTrue(plain(VbHudService.topLine(m, 1, VbPhase.PLANTED, 30)).contains("SEED PLANTED"));
    assertTrue(plain(VbHudService.topLine(m, 0, VbPhase.BUY, 20)).contains("BUY PHASE"));
  }

  @Test
  public void aHeadIsTheAgentAliveCrossedOutWhenDeadOrBlankBeforeTheLock() {
    VbGlyphs g = VbGlyphs.get();
    assertEquals("head_cinder", VbHudService.headName("cinder", true, g));
    assertEquals("head_cinder_dead", VbHudService.headName("cinder", false, g));
    assertEquals("head_none", VbHudService.headName("", true, g));
    assertEquals("head_none", VbHudService.headName("nobody", true, g));
    for (String id : VbData.agents().ids()) {
      assertTrue(id, g.hasInline("head_" + id));
      assertTrue(id, g.hasInline("head_" + id + "_dead"));
    }
    for (VbWeapon w : VbData.weapons().all()) assertTrue(w.id() + " needs a kill feed icon", g.hasInline("gun_" + w.id()));
    assertTrue(g.hasInline("skull"));
  }

  @Test
  public void aDeadPlayerGetsADeadHeadInThePips() {
    VbMatch m = match();
    UUID victim = m.squad(0).get(0);
    String agent = VbData.agents().ids().get(0);
    m.lockAgent(victim, agent);
    m.advance();
    m.advance();
    var killer = m.squad(1).get(0);
    String before = json(VbHudService.heads(m, 0));
    m.damage(victim, killer, 1000);
    String after = json(VbHudService.heads(m, 0));
    assertNotEquals("the dead player's head changes", before, after);
    assertEquals("head_cinder_dead", VbHudService.headName(agent, false, VbGlyphs.get()));
  }

  @Test
  public void theKillFeedShowsTheWeaponIconAndASkullForAHeadShot() {
    VbWeapon w = VbData.weapons().get("flux_rifle");
    var plainKill = new VbHudService.Feed(UUID.randomUUID(), UUID.randomUUID(), w.name(), 0, 1, 0L, false);
    var headKill = new VbHudService.Feed(plainKill.killer(), plainKill.victim(), w.name(), 0, 1, 0L, true);
    String a = json(VbHudService.weaponMark(plainKill)), b = json(VbHudService.weaponMark(headKill));
    assertEquals(1, count(a, "vampyuyu:vbinline"));
    assertEquals(2, count(b, "vampyuyu:vbinline"));
    var unknown = new VbHudService.Feed(plainKill.killer(), plainKill.victim(), "Mystery", 0, 1, 0L, false);
    assertTrue(plain(VbHudService.weaponMark(unknown)).contains("Mystery"));
  }

  // ------------------------------------------------------------------ the glyph HUD state

  private static VbHudState.Input input(int hp, int shield, int[] charges, int ammo, String hit) {
    return new VbHudState.Input(true, hp, shield, "cinder", charges, new int[] {1, 2, 2, 1}, ammo, 25, 1, hit);
  }

  private static VbGlyphs.Glyph find(List<VbGlyphs.Glyph> l, String element) {
    return l.stream().filter(g -> g.element().equals(element)).findFirst().orElse(null);
  }

  @Test
  public void healthIsThreeDigitsAndABarThatTurnsRedWhenLow() {
    var g = VbHudState.glyphs(input(100, 0, new int[] {1, 0, 2, 0}, -1, null));
    assertEquals("bd_w_1", find(g, "hp1").sprite());
    assertEquals("bd_w_0", find(g, "hp2").sprite());
    assertEquals("bd_w_0", find(g, "hp3").sprite());
    assertEquals("hp_bar", find(g, "hp_bar").sprite());
    assertEquals(255, find(g, "hp_bar").param());
    g = VbHudState.glyphs(input(7, 0, new int[] {1, 0, 2, 0}, -1, null));
    assertEquals("bd_blank", find(g, "hp1").sprite());
    assertEquals("bd_blank", find(g, "hp2").sprite());
    assertEquals("bd_r_7", find(g, "hp3").sprite());
    assertEquals("hp_bar_low", find(g, "hp_bar").sprite());
    assertEquals(18, find(g, "hp_bar").param());
    assertNull("no shield, no shield figures", find(g, "sh1"));
    assertNull(find(g, "sh_bar"));
  }

  @Test
  public void shieldShowsItsFiguresAndAHalfBarAtTwentyFive() {
    var g = VbHudState.glyphs(input(100, 25, new int[] {1, 0, 2, 0}, -1, null));
    assertEquals("sd_blank", find(g, "sh1").sprite());
    assertEquals("sd_b_2", find(g, "sh2").sprite());
    assertEquals("sd_b_5", find(g, "sh3").sprite());
    assertEquals(128, find(g, "sh_bar").param());
  }

  @Test
  public void abilityTilesShowTheirStateIconAndChargePips() {
    var g = VbHudState.glyphs(input(100, 0, new int[] {1, 0, 2, 0}, -1, null));
    assertEquals("tile_4_on", find(g, "tile0").sprite());
    assertEquals("tile_5_off", find(g, "tile1").sprite());
    assertEquals("tile_6_on", find(g, "tile2").sprite());
    assertEquals("tile_7_off", find(g, "tile3").sprite());
    assertEquals("ic_cinder_4_on", find(g, "icon0").sprite());
    assertEquals("ic_cinder_5_off", find(g, "icon1").sprite());
    assertEquals("ic_cinder_7_off", find(g, "icon3").sprite());
    // slot 4 holds up to 1 charge (1 pip, lit), slot 5 up to 2 (both dark), slot 6 up to 2 (both lit); the ultimate has none
    assertEquals("pip_on", find(g, "pip0_0").sprite());
    assertNull(find(g, "pip0_1"));
    assertEquals("pip_off", find(g, "pip1_0").sprite());
    assertEquals("pip_off", find(g, "pip1_1").sprite());
    assertEquals("pip_on", find(g, "pip2_1").sprite());
    assertTrue(g.stream().noneMatch(x -> x.element().startsWith("pip3")));
    // no agent, no tiles
    var none = VbHudState.glyphs(new VbHudState.Input(true, 100, 0, null, null, null, -1, 0, 1, null));
    assertTrue(none.stream().noneMatch(x -> x.element().startsWith("tile") || x.element().startsWith("icon") || x.element().startsWith("pip")));
  }

  @Test
  public void ammoShowsTheMagazineAndItsSizeAndTurnsRedWhenNearlyEmpty() {
    var g = VbHudState.glyphs(input(100, 0, new int[] {1, 0, 2, 0}, 18, null));
    assertEquals("bd_w_1", find(g, "am1").sprite());
    assertEquals("bd_w_8", find(g, "am2").sprite());
    assertEquals("sd_blank", find(g, "rs1").sprite());
    assertEquals("sd_g_2", find(g, "rs2").sprite());
    assertEquals("sd_g_5", find(g, "rs3").sprite());
    g = VbHudState.glyphs(input(100, 0, new int[] {1, 0, 2, 0}, 3, null));
    assertEquals("bd_blank", find(g, "am1").sprite());
    assertEquals("bd_r_3", find(g, "am2").sprite());
    assertNull("a hand without a gun shows no ammo plate", find(VbHudState.glyphs(input(100, 0, null, -1, null)), "am_plate"));
    assertArrayEquals(new int[] {18, 50}, VbHudState.parseAmmo("18 / 50"));
    assertNull(VbHudState.parseAmmo(null));
    assertNull(VbHudState.parseAmmo("x"));
  }

  @Test
  public void crosshairFollowsWhatThePlayerDoesAndTheHitMarkerSitsOnTop() {
    assertEquals(-1, VbHudState.crosshair(true, 1));
    assertEquals(2, VbHudState.crosshair(false, 2));
    assertEquals(0, VbHudState.spreadLevel(true, false, false));
    assertEquals(1, VbHudState.spreadLevel(false, false, false));
    assertEquals(2, VbHudState.spreadLevel(false, true, false));
    assertEquals(3, VbHudState.spreadLevel(true, true, true));
    var g = VbHudState.glyphs(input(100, 0, null, -1, "k"));
    assertEquals("xh_1", find(g, "xh").sprite());
    assertEquals("hit_k", find(g, "hit").sprite());
    assertEquals("the hit marker is drawn after the crosshair", g.size() - 1, g.indexOf(find(g, "hit")));
    assertNull(find(VbHudState.glyphs(input(100, 0, null, -1, null)), "hit"));
  }

  @Test
  public void aDeadPlayerGetsNoGlyphs() {
    assertTrue(VbHudState.glyphs(new VbHudState.Input(false, 0, 0, "cinder", null, null, -1, 0, 1, null)).isEmpty());
  }

  @Test
  public void everyGlyphTheBuilderCanEmitExistsInThePackTable() {
    VbGlyphs t = VbGlyphs.get();
    for (int hp : new int[] {0, 7, 25, 26, 100, 150})
      for (int ammo : new int[] {-1, 0, 3, 18, 25})
        for (String hit : new String[] {null, "w", "h", "k"})
          for (String agent : VbData.agents().ids()) {
            var in = new VbHudState.Input(true, hp, hp % 60, agent, new int[] {1, 2, 3, 1}, new int[] {3, 3, 3, 1}, ammo, 25, hp % 4, hit);
            for (var gl : VbHudState.glyphs(in)) {
              assertTrue("sprite " + gl.sprite(), t.hasSprite(gl.sprite()));
              assertTrue("element " + gl.element(), t.hasElement(gl.element()));
            }
          }
  }

  @Test
  public void everyGlyphIsOnTheValoBlockFontWithTheShaderColours() {
    VbGlyphs t = VbGlyphs.get();
    var g = VbHudState.glyphs(input(100, 25, new int[] {1, 0, 2, 0}, 18, "h"));
    for (var gl : g) {
      var c = (net.kyori.adventure.text.TextComponent) t.glyph(gl);
      assertEquals(VbGlyphs.HUD_FONT, c.font());
      var color = c.color();
      assertEquals(VbGlyphs.HUD_R, color.red());
      assertTrue(color.green() >= VbGlyphs.ID_BASE && color.green() < VbGlyphs.ID_BASE + 37);
      assertEquals("the sprite and the glyph that takes its advance back", 2, c.content().length());
    }
    var bar = (net.kyori.adventure.text.TextComponent) t.glyph(find(g, "hp_bar"));
    assertEquals("the blue channel is the bar width", 255, bar.color().blue());
  }

  @Test
  public void aClearedMatchIsNeverDrawnAgain() {
    VbHudService hud = new VbHudService(null);
    VbMatch m = match();
    assertFalse(hud.isCleared(m));
    hud.markCleared(m);
    assertTrue(hud.isCleared(m));
    hud.render(m, 0, 0); // returns at the guard: nothing is queued (there is no server here, so any queueing would throw)
  }
}
