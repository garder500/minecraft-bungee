package win.vampyuyu.valoblock;

import java.util.List;
import java.util.Map;

/** An agent (agents/&lt;id&gt;.json). The skin value and signature stay empty until a signed property is pasted in: the swap is skipped then. */
public record VbAgent(
    String id, String name, String role, String roleColor, String realm, String quote, String menuColor, Map<String, String> palette,
    List<String> faceRows, String skinValue, String skinSignature, String glow, String trim, List<VbAbility> abilities) {
  public VbAbility ability(int slot) {
    for (VbAbility a : abilities) if (a.slot() == slot) return a;
    return null;
  }

  public boolean hasSkin() {
    return !skinValue.isEmpty() && !skinSignature.isEmpty();
  }
}
