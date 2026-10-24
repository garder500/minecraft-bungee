package win.vampyuyu.arena;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.*;

/** The window of an arena menu; a game with menus of its own extends it to tag them. */
public class CompassMenuHolder implements InventoryHolder {
  public final UUID viewer;
  public final String arenaId;
  public final boolean skinned;
  public int leaveSlot = 53;
  /** Phase this menu was drawn for; clicks are ignored once the game has moved on. */
  public String phase = "";

  public final Map<Integer, UUID> targets = new HashMap<>();
  public final Map<Integer, String> abilities = new HashMap<>();

  /** Menu actions by raw slot: a game's own action strings, or leave. */
  public final Map<Integer, String> clicks = new HashMap<>();

  private final Inventory inventory;

  public CompassMenuHolder(UUID viewer, String arenaId, boolean skinned, Component title) {
    this.viewer = viewer;
    this.arenaId = arenaId;
    this.skinned = skinned;
    inventory = Bukkit.createInventory(this, 54, title);
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
