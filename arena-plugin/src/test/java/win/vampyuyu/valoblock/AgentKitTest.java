package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AgentKitTest {
  private final VbAgents.Ultimate ult = VbData.agents().ultimate();

  private VbMatch buyPhase(List<UUID> humans) {
    VbMatch m = new VbMatch(VbData.rules(), humans, new Random(3));
    m.advance();
    assertEquals(VbPhase.BUY, m.vbPhase());
    return m;
  }

  @Test
  public void shippedAgentsAreTheEightOriginals() {
    assertEquals(List.of("cinder", "gale", "thorn", "rime", "vesper", "nacre", "sahrin", "murk"), VbData.agents().ids());
    for (VbAgent a : VbData.agents().all()) {
      assertEquals(a.name(), 4, a.abilities().size());
      assertEquals(VbAbility.Kind.BUY, a.ability(4).kind());
      assertEquals(VbAbility.Kind.BUY, a.ability(5).kind());
      assertEquals(VbAbility.Kind.SIGNATURE, a.ability(6).kind());
      assertEquals(VbAbility.Kind.ULTIMATE, a.ability(7).kind());
      assertFalse("skins are TODO", a.hasSkin());
    }
    VbAgent cinder = VbData.agents().get("cinder");
    assertEquals("Slag Pot", cinder.ability(4).name());
    assertEquals(200, cinder.ability(4).cost());
    assertEquals(150, cinder.ability(5).cost());
    assertEquals(2, cinder.ability(6).rechargeKills());
    assertEquals(7, cinder.ability(7).ultPoints());
    assertEquals("free x2", VbData.agents().get("sahrin").ability(6).costLabel());
    assertEquals("8 pts", VbData.agents().get("rime").ability(7).costLabel());
    assertEquals(300, VbData.agents().get("rime").ability(5).cost());
  }

  @Test
  public void everyAbilityHasAnImplementation() {
    // the id is the dispatch key of AbilitiesA / AbilitiesB: a typo in a data file would silently do nothing
    Set<String> implemented = new HashSet<>(List.of(
        "slag_pot", "heat_shimmer", "ember_dash", "colossus_step", "wind_pad", "static_orb", "charged_slide", "storm_call", "root_snare", "spore_mend",
        "bark_wall", "overgrowth", "frost_mine", "stasis_pane", "hold_moment", "glacial_hour", "echo_step", "judgment", "soul_lantern", "verdict",
        "ink_burst", "pressure_bubble", "sonar_pearl", "undertow", "rewind", "hourglass_trap", "sand_veil", "time_tide", "leech_pool", "rot_cloud",
        "miasma_wall", "bog_tide"));
    Set<String> data = new HashSet<>();
    for (VbAgent a : VbData.agents().all()) for (VbAbility ab : a.abilities()) data.add(ab.id());
    assertEquals(implemented, data);
  }

  @Test
  public void buyingNeedsTheBuyPhaseShardsAndRoom() {
    UUID a = UUID.randomUUID();
    VbMatch m = new VbMatch(VbData.rules(), List.of(a, UUID.randomUUID()), new Random(1));
    AgentKit kit = new AgentKit(VbData.agents().get("cinder"), ult);
    assertEquals(AgentKit.Result.NOT_BUY_PHASE, kit.buy(m, a, 4)); // agent select is not the buy phase
    m.advance();
    int before = m.credits(a); // 800 at the pistol round
    assertEquals(AgentKit.Result.OK, kit.buy(m, a, 4)); // Slag Pot 200
    assertEquals(before - 200, m.credits(a));
    assertEquals(1, kit.charges(4));
    assertEquals(AgentKit.Result.FULL, kit.buy(m, a, 4)); // max 1
    assertEquals(AgentKit.Result.OK, kit.buy(m, a, 5)); // 150
    assertEquals(before - 350, m.credits(a));
    assertEquals(AgentKit.Result.NOT_FOR_SALE, kit.buy(m, a, 6));
    assertEquals(AgentKit.Result.NOT_FOR_SALE, kit.buy(m, a, 7));
  }

  @Test
  public void notEnoughShardsSpendsNothing() {
    UUID a = UUID.randomUUID();
    VbMatch m = buyPhase(List.of(a, UUID.randomUUID()));
    AgentKit kit = new AgentKit(VbData.agents().get("rime"), ult); // Stasis Pane 300
    assertTrue(m.buy(a, 700)); // 100 left
    assertEquals(AgentKit.Result.TOO_POOR, kit.buy(m, a, 5));
    assertEquals(100, m.credits(a));
    assertEquals(0, kit.charges(5));
  }

  @Test
  public void sellBackOnlyWhatWasBoughtThisPhaseAtFullPrice() {
    UUID a = UUID.randomUUID();
    VbMatch m = buyPhase(List.of(a, UUID.randomUUID()));
    AgentKit kit = new AgentKit(VbData.agents().get("gale"), ult);
    assertEquals(AgentKit.Result.NOTHING_TO_SELL, kit.sell(m, a, 4));
    kit.buy(m, a, 4);
    assertEquals(650, m.credits(a));
    assertEquals(AgentKit.Result.OK, kit.sell(m, a, 4));
    assertEquals(800, m.credits(a));
    assertEquals(0, kit.charges(4));
  }

  @Test
  public void signatureRefillsEveryRoundAndRechargesOnKills() {
    AgentKit kit = new AgentKit(VbData.agents().get("cinder"), ult); // Ember Dash: 1 charge, +1 after 2 kills
    assertEquals(1, kit.charges(6));
    assertEquals(AgentKit.Result.OK, kit.use(6));
    assertEquals(AgentKit.Result.NONE_LEFT, kit.use(6));
    kit.onKill();
    assertEquals(0, kit.charges(6)); // one kill is not enough
    kit.onKill();
    assertEquals(1, kit.charges(6));
    kit.onKill();
    kit.onKill();
    assertEquals(1, kit.charges(6)); // never above the maximum
    kit.use(6);
    kit.roundStart(true);
    assertEquals(1, kit.charges(6));
  }

  @Test
  public void sandVeilHasTwoChargesAndNoKillRecharge() {
    AgentKit kit = new AgentKit(VbData.agents().get("sahrin"), ult);
    assertEquals(2, kit.charges(6));
    kit.use(6);
    kit.onKill();
    kit.onKill();
    assertEquals(1, kit.charges(6));
  }

  @Test
  public void ultimateNeedsPointsFromKillsOrbsAndRoundsAndSpendsThem() {
    AgentKit kit = new AgentKit(VbData.agents().get("cinder"), ult); // 7 points
    assertEquals(AgentKit.Result.NOT_READY, kit.canUse(7));
    for (int i = 0; i < 3; i++) kit.onKill();
    kit.onOrb();
    kit.onOrb();
    assertEquals(5, kit.ultPoints());
    assertFalse(kit.ultReady());
    kit.roundStart(true); // +1 a round
    assertEquals(6, kit.ultPoints());
    kit.onOrb();
    assertTrue(kit.ultReady());
    kit.onKill();
    assertEquals("capped at the ultimate's cost", 7, kit.ultPoints());
    assertEquals(AgentKit.Result.OK, kit.use(7));
    assertEquals(0, kit.ultPoints());
    assertEquals(AgentKit.Result.NOT_READY, kit.use(7));
  }

  @Test
  public void aFailedCastGivesTheChargeBack() {
    AgentKit kit = new AgentKit(VbData.agents().get("thorn"), ult);
    kit.use(6);
    assertEquals(0, kit.charges(6));
    kit.refund(6);
    assertEquals(1, kit.charges(6));
    kit.refund(6);
    assertEquals("refund never exceeds the maximum", 1, kit.charges(6));
  }

  @Test
  public void boughtChargesSurviveForThoseWhoKeepTheirLoadoutOnly() {
    UUID a = UUID.randomUUID();
    VbMatch m = buyPhase(List.of(a, UUID.randomUUID()));
    AgentKit kit = new AgentKit(VbData.agents().get("murk"), ult);
    kit.buy(m, a, 4);
    kit.roundStart(true);
    assertEquals(1, kit.charges(4));
    kit.roundStart(false); // died, or a pistol round
    assertEquals(0, kit.charges(4));
    assertEquals(1, kit.charges(6));
  }
}
