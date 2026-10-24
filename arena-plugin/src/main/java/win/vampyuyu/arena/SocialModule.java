package win.vampyuyu.arena;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Rules of the village's quiet powers: spellcaster, old hag, ghost, mason, minion and old man. */
final class SocialModule implements RoleModule {
  private final Set<UUID> spelled = new HashSet<>();
  private String letter;

  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(
        WerewolfGame.Role.SPELLCASTER,
        WerewolfGame.Role.OLD_HAG,
        WerewolfGame.Role.GHOST,
        WerewolfGame.Role.MASON,
        WerewolfGame.Role.MINION,
        WerewolfGame.Role.OLD_MAN);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.SPELL, WerewolfGame.Phase.HAG, WerewolfGame.Phase.GHOST);
  }

  // ------------------------------------------------------------------ the game's rhythm

  @Override
  public void nightfall(WerewolfGame g) {
    if (g.nightNumber() != 1) return;
    List<UUID> masons = livingWith(g, WerewolfGame.Role.MASON);
    for (UUID mason : masons) g.notify(mason, masonNotice(masons, mason));
    List<UUID> wolves = new ArrayList<>();
    // The pack as it stands: a drunk wolf who has not woken yet is not in it.
    for (UUID p : g.living()) if (g.packMember(p)) wolves.add(p);
    for (UUID minion : livingWith(g, WerewolfGame.Role.MINION))
      g.notify(minion, minionNotice(wolves));
  }

  @Override
  public void dawn(WerewolfGame g) {
    if (g.nightNumber() == 1)
      for (UUID ghost : holders(g, WerewolfGame.Role.GHOST))
        if (g.living().contains(ghost)) g.slay(ghost, "became a ghost");
    if (letter != null) {
      g.notifyLiving("A ghostly letter appears in the mist: " + letter);
      letter = null;
    }
    long wolves = g.living().stream().filter(p -> WerewolfGame.wolf(g.roleOf(p))).count();
    if (g.nightNumber() >= wolves + 1)
      for (UUID oldMan : livingWith(g, WerewolfGame.Role.OLD_MAN)) g.slay(oldMan, "died of old age");
  }

  // ------------------------------------------------------------------ calls

  @Override
  public String select(WerewolfGame g, WerewolfGame.Phase phase, UUID actor, UUID target) {
    return switch (phase) {
      case SPELL -> cast(g, actor, target);
      case HAG -> banish(g, actor, target);
      default -> RoleModule.super.select(g, phase, actor, target);
    };
  }

  private String cast(WerewolfGame g, UUID actor, UUID target) {
    if (!g.living().contains(target)) throw new IllegalArgumentException("Select a living player");
    if (spelled.contains(target))
      throw new IllegalArgumentException("You already silenced that player once");
    spelled.add(target);
    g.mute(target);
    g.done(actor);
    return target + " will be silent tomorrow.";
  }

  private String banish(WerewolfGame g, UUID actor, UUID target) {
    if (!g.living().contains(target)) throw new IllegalArgumentException("Select a living player");
    if (actor.equals(target)) throw new IllegalArgumentException("You cannot send yourself away");
    g.exile(target);
    g.notify(target, "The old hag sent you away for the day.");
    g.done(actor);
    return target + " is sent away for the day.";
  }

  @Override
  public void bot(WerewolfGame g, WerewolfGame.Phase phase, UUID bot, Random random) {
    if (phase != WerewolfGame.Phase.SPELL && phase != WerewolfGame.Phase.HAG) return;
    List<UUID> pool = new ArrayList<>(g.living());
    if (phase == WerewolfGame.Phase.HAG) pool.remove(bot);
    else pool.removeAll(spelled);
    if (pool.isEmpty()) return;
    g.select(bot, pool.get(random.nextInt(pool.size())));
  }

  // ------------------------------------------------------------------ chat

  @Override
  public boolean chat(WerewolfGame g, UUID speaker, String text) {
    if (!g.phase().equals("GHOST") || !g.actors().contains(speaker)) return false;
    if (g.roleOf(speaker) != WerewolfGame.Role.GHOST) return false;
    String word = text.strip();
    char c = word.length() == 1 ? word.charAt(0) : '0';
    if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')) return false;
    letter = word.toUpperCase(Locale.ROOT);
    g.done(speaker);
    return true;
  }

  // ------------------------------------------------------------------ helpers

  private static List<UUID> holders(WerewolfGame g, WerewolfGame.Role role) {
    List<UUID> out = new ArrayList<>();
    for (UUID p : g.table()) if (g.roleOf(p) == role) out.add(p);
    return out;
  }

  private static List<UUID> livingWith(WerewolfGame g, WerewolfGame.Role role) {
    List<UUID> out = holders(g, role);
    out.retainAll(g.living());
    return out;
  }

  private static String masonNotice(List<UUID> masons, UUID self) {
    List<UUID> others = new ArrayList<>(masons);
    others.remove(self);
    if (others.isEmpty()) return "You are the only mason in the village.";
    StringBuilder text = new StringBuilder("The other masons: ");
    for (int i = 0; i < others.size(); i++) text.append(i == 0 ? "" : ", ").append(others.get(i));
    return text.append(".").toString();
  }

  private static String minionNotice(List<UUID> wolves) {
    if (wolves.isEmpty()) return "The pack has no wolf left.";
    StringBuilder text = new StringBuilder("The wolves are: ");
    for (int i = 0; i < wolves.size(); i++) text.append(i == 0 ? "" : ", ").append(wolves.get(i));
    return text.append(".").toString();
  }
}
