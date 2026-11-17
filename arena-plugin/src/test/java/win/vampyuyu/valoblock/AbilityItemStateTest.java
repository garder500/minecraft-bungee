package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class AbilityItemStateTest {
  private final VbAgents.Ultimate rules = VbData.agents().ultimate();

  private static String text(AbilityItemState s) {
    StringBuilder b = new StringBuilder();
    for (AgentText.Line l : s.lore()) b.append(l.text()).append('\n');
    return b.toString();
  }

  @Test
  public void aBuyableChargeIsTheStackSizeAndZeroChargesKeepTheItemWithTheEmptyIcon() {
    VbAgent cinder = VbData.agents().get("cinder");
    AgentKit kit = new AgentKit(cinder, rules);
    AbilityItemState none = AbilityItemState.of(cinder, cinder.ability(4), kit);
    assertTrue(none.empty());
    assertEquals(1, none.count());
    assertEquals("ab_cinder_slag_pot_empty", none.model());
    assertEquals("Slag Pot", none.title());
    assertTrue(text(none).contains("Charges: 0 / 1"));
    java.util.UUID me = java.util.UUID.randomUUID();
    VbMatch m = new VbMatch(VbData.rules(), java.util.List.of(me, java.util.UUID.randomUUID()), new java.util.Random(1));
    m.advance();
    assertEquals(AgentKit.Result.OK, kit.buy(m, me, 4));
    AbilityItemState bought = AbilityItemState.of(cinder, cinder.ability(4), kit);
    assertFalse(bought.empty());
    assertEquals(1, bought.count());
    assertEquals("ab_cinder_slag_pot", bought.model());
    assertEquals(AgentKit.Result.OK, kit.use(4));
    assertEquals("ab_cinder_slag_pot_empty", AbilityItemState.of(cinder, cinder.ability(4), kit).model());
  }

  @Test
  public void aSignatureWithTwoChargesCountsDownAndEmptiesAtZero() {
    VbAgent sahrin = VbData.agents().get("sahrin");
    AgentKit kit = new AgentKit(sahrin, rules);
    AbilityItemState two = AbilityItemState.of(sahrin, sahrin.ability(6), kit);
    assertEquals(2, two.count());
    assertFalse(two.empty());
    assertEquals("ab_sahrin_sand_veil", two.model());
    kit.use(6);
    assertEquals(1, AbilityItemState.of(sahrin, sahrin.ability(6), kit).count());
    kit.use(6);
    AbilityItemState zero = AbilityItemState.of(sahrin, sahrin.ability(6), kit);
    assertEquals(1, zero.count());
    assertTrue(zero.empty());
    assertEquals("ab_sahrin_sand_veil_empty", zero.model());
    kit.refund(6);
    assertEquals("ab_sahrin_sand_veil", AbilityItemState.of(sahrin, sahrin.ability(6), kit).model());
  }

  @Test
  public void theUltimateShowsPointsThenLightsUpWhenReadyAndDimsAfterUse() {
    VbAgent cinder = VbData.agents().get("cinder");
    AgentKit kit = new AgentKit(cinder, rules);
    int need = cinder.ability(7).ultPoints();
    AbilityItemState cold = AbilityItemState.of(cinder, cinder.ability(7), kit);
    assertFalse(cold.lit());
    assertTrue("an ultimate that is not ready is dimmed like the HUD tile", cold.empty());
    assertEquals("ab_cinder_colossus_step_empty", cold.model());
    assertEquals(1, cold.count());
    assertTrue(text(cold), text(cold).contains("Ultimate points: 0 / " + need));
    kit.addPoints(need - 2);
    assertTrue(text(AbilityItemState.of(cinder, cinder.ability(7), kit)).contains("Ultimate points: " + (need - 2) + " / " + need));
    kit.addPoints(2);
    AbilityItemState hot = AbilityItemState.of(cinder, cinder.ability(7), kit);
    assertTrue(hot.lit());
    assertEquals("ab_cinder_colossus_step_ready", hot.model());
    assertEquals(1, hot.count());
    assertTrue(text(hot).contains("Ready"));
    kit.use(7);
    assertEquals("ab_cinder_colossus_step_empty", AbilityItemState.of(cinder, cinder.ability(7), kit).model());
  }

  @Test
  public void everyAbilityHasItsOwnModelAndItsOwnCooldownGroup() {
    Set<String> models = new HashSet<>(), groups = new HashSet<>();
    for (VbAgent a : VbData.agents().all()) {
      AgentKit kit = new AgentKit(a, rules);
      for (VbAbility ab : a.abilities()) {
        AbilityItemState s = AbilityItemState.of(a, ab, kit);
        assertEquals(ab.name(), s.title());
        assertTrue(s.model().startsWith("ab_" + a.id() + "_" + ab.id()));
        assertTrue(text(s).contains("Key " + ab.slot()));
        models.add(AbilityItemState.baseModel(a.id(), ab.id()));
        groups.add(s.cooldownGroup());
      }
    }
    assertEquals(32, models.size());
    assertEquals(32, groups.size());
  }
}
