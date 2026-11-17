package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;

/**
 * What one ability item on the hotbar shows, no Bukkit: its item model (the pack's ab_&lt;agent&gt;_&lt;ability&gt; icon, the
 * dimmed {@code _empty} one when no charge is left, the lit {@code _ready} one for a charged ultimate), the stack size
 * (the charges left, 1 at least so an empty item stays in its slot), the name, the tooltip and the vanilla cooldown group.
 */
public record AbilityItemState(String model, int count, boolean empty, boolean lit, String title, String titleColor, List<AgentText.Line> lore, String cooldownGroup) {
  /** The anti-spam gap between two casts (VbAgentService.COOLDOWN_MS) as the item cooldown overlay, in ticks. */
  public static final int GAP_TICKS = 7;

  public static AbilityItemState of(VbAgent a, VbAbility ab, AgentKit kit) {
    int slot = ab.slot();
    boolean ult = ab.kind() == VbAbility.Kind.ULTIMATE;
    int charges = kit.charges(slot);
    boolean empty = charges <= 0; // an ultimate that is not ready counts as 0, like the dimmed HUD tile
    boolean lit = ult && kit.ultReady();
    String base = baseModel(a.id(), ab.id());
    String model = lit ? base + "_ready" : empty ? base + "_empty" : base;
    int count = ult ? 1 : Math.max(1, Math.min(99, charges));
    boolean ok = kit.canUse(slot) == AgentKit.Result.OK;
    String color = lit ? AgentText.GOLD : ok ? a.trim() : "#8a8f9c";
    return new AbilityItemState(model, count, empty, lit, ab.name(), color, AgentText.abilityLines(a, ab, kit), cooldownGroup(a.id(), ab.id()));
  }

  /** The item model name (the key is vampyuyu:valoblock/&lt;name&gt;) of the ability's normal icon. */
  public static String baseModel(String agentId, String abilityId) {
    return "ab_" + agentId + "_" + abilityId;
  }

  /** One vanilla cooldown group per ability, so using one ability never greys another. */
  public static String cooldownGroup(String agentId, String abilityId) {
    return "vb_ab_" + agentId + "_" + abilityId;
  }

  /** The lines under the title: the text, the charges (or points) line, and how to use it. */
  public static List<AgentText.Line> lines(VbAgent a, VbAbility ab, AgentKit kit) {
    List<AgentText.Line> l = new ArrayList<>();
    l.add(new AgentText.Line(ab.text(), AgentText.GRAY));
    switch (ab.kind()) {
      case BUY -> l.add(new AgentText.Line("Charges: " + kit.charges(ab.slot()) + " / " + ab.maxCharges() + " · " + ab.cost() + " Shards in the Armory", AgentText.GOLD));
      case SIGNATURE -> l.add(new AgentText.Line("Free · charges: " + kit.charges(6) + " / " + ab.charges() + (ab.rechargeKills() > 0 ? " · +1 every " + ab.rechargeKills() + " kills" : " · refills every round"), a.trim()));
      case ULTIMATE -> {
        l.add(new AgentText.Line("Ultimate points: " + kit.ultPoints() + " / " + ab.ultPoints(), AgentText.GOLD));
        if (kit.ultReady()) l.add(new AgentText.Line("Ready", AgentText.GREEN));
      }
    }
    l.add(new AgentText.Line("Key " + ab.slot() + " · right-click to use", AgentText.YELLOW));
    return l;
  }
}
