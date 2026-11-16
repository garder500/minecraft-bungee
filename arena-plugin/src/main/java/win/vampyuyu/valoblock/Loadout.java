package win.vampyuyu.valoblock;

import java.util.EnumMap;
import java.util.Map;

/** What one player carries: one weapon per slot (the shield lives in the match, as a value). */
public final class Loadout {
  private final Map<VbWeapon.Slot, VbWeapon> items = new EnumMap<>(VbWeapon.Slot.class);
  private final VbWeapon defaultSidearm;
  /** What was bought in the current buy phase, by slot (the shield counts): only that can be sold back at full price. */
  private final Map<VbWeapon.Slot, VbWeapon> bought = new EnumMap<>(VbWeapon.Slot.class);

  public Loadout(VbWeapon defaultSidearm) {
    this.defaultSidearm = defaultSidearm;
  }

  /** The start of every pistol round: the free sidearm and the knife. */
  public static Loadout starting(VbWeapons catalogue) {
    Loadout l = new Loadout(catalogue.defaultSidearm());
    l.items.put(VbWeapon.Slot.SIDEARM, catalogue.defaultSidearm());
    l.items.put(VbWeapon.Slot.MELEE, catalogue.melee());
    return l;
  }

  public VbWeapon get(VbWeapon.Slot slot) {
    return items.get(slot);
  }

  /** Puts a weapon in its slot without a purchase (a pickup): it is not refundable. */
  public void set(VbWeapon w) {
    items.put(w.slot(), w);
    bought.remove(w.slot());
  }

  /** Records a purchase of this buy phase (the armory calls it after {@link #set} for a gun). */
  public void markBought(VbWeapon w) {
    bought.put(w.slot(), w);
  }

  /** Whether this very item was bought in the current buy phase. */
  public boolean boughtThisPhase(VbWeapon w) {
    VbWeapon b = bought.get(w.slot());
    return b != null && b.id().equals(w.id());
  }

  /** A new buy phase: nothing is refundable any more. */
  public void newBuyPhase() {
    bought.clear();
  }

  /** Empties a slot; the sidearm slot falls back to the free sidearm (everyone has one). */
  public void remove(VbWeapon.Slot slot) {
    bought.remove(slot);
    if (slot == VbWeapon.Slot.SIDEARM) items.put(slot, defaultSidearm);
    else items.remove(slot);
  }

  public boolean owns(VbWeapon w) {
    return items.get(w.slot()) == w || (items.get(w.slot()) != null && items.get(w.slot()).id().equals(w.id()));
  }
}
