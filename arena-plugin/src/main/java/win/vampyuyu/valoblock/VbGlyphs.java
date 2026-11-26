package win.vampyuyu.valoblock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The ValoBlock glyph tables, read from vb-hud-sprites.txt (written by resource-pack/art/valoblock_ui.py).
 *
 * <p>Two fonts of the ValoBlock pack: {@code vampyuyu:vbhud} glyphs are coloured (84, 160 + element, parameter) in the action
 * bar and the pack's core shader moves them to their element's box (resource-pack/vb_layout.py); {@code vampyuyu:vbinline}
 * glyphs just flow inside a text line (the heads of the top bar, the weapon icons of the kill feed). Pure text, no Bukkit.
 */
public final class VbGlyphs {
  public static final Key HUD_FONT = Key.key("vampyuyu", "vbhud"), INLINE_FONT = Key.key("vampyuyu", "vbinline");
  public static final int HUD_R = 84, ID_BASE = 160, SPRITE_BASE = 0xE000, NEG_BASE = 0xF000;

  /** One shader-positioned glyph: the element it fills, the sprite in it and the colour's blue channel (a bar's width, 0-255). */
  public record Glyph(String element, String sprite, int param) {}

  private static VbGlyphs instance;

  private final Map<String, Integer> sprites = new HashMap<>(), elements = new HashMap<>(), inline = new HashMap<>();

  private VbGlyphs() {
    try (var in = VbGlyphs.class.getResourceAsStream("/valoblock/vb-hud-sprites.txt")) {
      if (in == null) throw new IllegalStateException("valoblock/vb-hud-sprites.txt missing");
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.trim().split(" ");
        if (p.length != 3) continue;
        switch (p[0]) {
          case "sprite" -> sprites.put(p[2], Integer.parseInt(p[1]));
          case "element" -> elements.put(p[2], Integer.parseInt(p[1]));
          case "inline" -> inline.put(p[2], Integer.parseInt(p[1]));
          default -> {}
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static synchronized VbGlyphs get() {
    if (instance == null) instance = new VbGlyphs();
    return instance;
  }

  public boolean hasSprite(String name) {
    return sprites.containsKey(name);
  }

  public boolean hasElement(String name) {
    return elements.containsKey(name);
  }

  public boolean hasInline(String name) {
    return inline.containsKey(name);
  }

  /** The glyph pair (the sprite and the glyph that takes its advance back, so the run stays centred) of a sprite in an element. */
  public Component glyph(Glyph g) {
    Integer id = elements.get(g.element()), index = sprites.get(g.sprite());
    if (id == null || index == null) return Component.empty();
    String chars = "" + (char) (SPRITE_BASE + index) + (char) (NEG_BASE + index);
    return Component.text(chars, TextColor.color(HUD_R, ID_BASE + id, Math.max(0, Math.min(255, g.param())))).font(HUD_FONT);
  }

  /**
   * A minimap marker: coloured (86 or 87 for its size, x, y) so the shader centres it on that point of the minimap's plan
   * (MiniMapMath). No text shadow: the shadow would be a second quad the shader does not move.
   */
  Component mark(MiniMapMath.Mark m) {
    Integer index = sprites.get(m.sprite());
    if (index == null) return Component.empty();
    String chars = "" + (char) (SPRITE_BASE + index) + (char) (NEG_BASE + index);
    TextColor colour = TextColor.color(m.big() ? MiniMapMath.R_BIG : MiniMapMath.R_SMALL, clamp(m.x()), clamp(m.y()));
    return Component.text(chars, colour).shadowColor(ShadowColor.none()).font(HUD_FONT);
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }

  public Component glyphs(List<Glyph> list) {
    Component out = Component.empty();
    for (Glyph g : list) out = out.append(glyph(g));
    return out;
  }

  /** An inline sprite (white, so the sprite keeps its own colours); empty for an unknown name. */
  public Component inline(String name) {
    Integer index = inline.get(name);
    if (index == null) return Component.empty();
    return Component.text(String.valueOf((char) (SPRITE_BASE + index)), NamedTextColor.WHITE).font(INLINE_FONT);
  }
}
