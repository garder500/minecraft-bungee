package win.vampyuyu.arena;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Hotbar items that configure the selected arena by right/left-clicking in the world. */
public final class ArenaTools implements Listener {
  /** One hotbar tool; the engine's own, plus those a game adds. */
  private record Tool(String name, Material material, String title, String... lore) {}

  private static final Tool SELECT =
      new Tool("SELECT", Material.MAP, "Arena selector", "Right-click: next arena", "Left-click: previous arena");
  private static final Tool WAND =
      new Tool(
          "WAND",
          Material.GOLDEN_AXE,
          "Bounds wand",
          "Left-click a block: corner 1",
          "Right-click a block: corner 2",
          "Sneak + right-click: apply to the arena",
          "Height gets 5 blocks of headroom");
  private static final Tool SPAWN =
      new Tool("SPAWN", Material.COMPASS, "Arena spawn", "Right-click: set the spawn where you stand");
  private static final Tool STATUS =
      new Tool(
          "STATUS",
          Material.WRITABLE_BOOK,
          "Readiness checklist",
          "Right-click: what is still missing",
          "Sneak + right-click: open the setup menu");

  /** The tools of an arena: the engine's, with its game's between the spawn and the checklist. */
  private List<Tool> kit(String arenaId) {
    List<Tool> kit = new ArrayList<>(List.of(SELECT, WAND, SPAWN));
    if (arenaId != null)
      for (var extra : plugin.arenas.require(arenaId).hooks.tools())
        kit.add(new Tool(extra.id(), extra.material(), extra.title(), extra.lore()));
    kit.add(STATUS);
    return kit;
  }

  private static final int HEADROOM = 5;
  private final Map<UUID, Block[]> marked = new ConcurrentHashMap<>();

  private final NexusArenaEngine plugin;
  private final NamespacedKey key;
  private final Map<UUID, String> selected = new ConcurrentHashMap<>();
  private final Set<UUID> visualizing = ConcurrentHashMap.newKeySet();

  public ArenaTools(NexusArenaEngine plugin) {
    this.plugin = plugin;
    key = new NamespacedKey(plugin, "arena-tool");
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ---------------------------------------------------------------- commands

  public void command(Player p, String sub, String[] args) {
    if (sub.equals("select")) {
      if (args.length != 2) throw new IllegalArgumentException("/arena select <id>");
      select(p, args[1]);
      return;
    }
    // /arena tools [id | off]
    if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
      int n = removeAll(p);
      plugin.message(p, n + " admin tool(s) removed");
      return;
    }
    give(p, args.length >= 2 ? args[1] : null);
  }

  private void select(Player p, String id) {
    plugin.arenas.require(id);
    selected.put(p.getUniqueId(), id);
    plugin.message(p, "Selected arena: " + id);
    report(p, id);
  }

  public void give(Player p, String id) {
    if (id != null) {
      plugin.arenas.require(id);
      selected.put(p.getUniqueId(), id);
    } else if (current(p) == null) {
      throw new IllegalArgumentException(
          "No arena yet. Use the corner wand, then /arena create <id> <" + plugin.admin.presetNames() + ">");
    }
    int added = 0;
    for (Tool t : kit(current(p))) {
      if (has(p, t)) continue;
      var left = p.getInventory().addItem(make(t));
      if (!left.isEmpty()) {
        plugin.message(p, "Inventory full: free some slots and run /arena tools again");
        break;
      }
      added++;
    }
    plugin.message(
        p,
        "Admin tools ready for '" + current(p) + "' (" + added + " added). Hold one to see the"
            + " configured points as particles; /arena tools off removes them.");
    startVisualizer(p);
    if (current(p) != null) report(p, current(p));
  }

  private int removeAll(Player p) {
    int n = 0;
    var inv = p.getInventory();
    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack it = inv.getItem(i);
      if (tool(it) != null) {
        inv.setItem(i, null);
        n++;
      }
    }
    return n;
  }

  private ItemStack make(Tool t) {
    ItemStack item = CompassGUI.item(t.material(), t.title(), t.lore());
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.name());
    item.setItemMeta(meta);
    return item;
  }

  private Tool tool(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return null;
    String name = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    if (name == null) return null;
    for (Tool t : List.of(SELECT, WAND, SPAWN, STATUS)) if (t.name().equals(name)) return t;
    // A tool of a game: only its id matters from here on.
    return new Tool(name, item.getType(), "");
  }

  private boolean has(Player p, Tool t) {
    for (ItemStack it : p.getInventory().getContents()) { Tool held = tool(it); if (held != null && held.name().equals(t.name())) return true; }
    return false;
  }

  private String current(Player p) {
    String id = selected.get(p.getUniqueId());
    if (id != null && exists(id)) return id;
    var all = plugin.arenas.all().stream().map(s -> s.config.id()).sorted().toList();
    if (all.size() == 1) {
      selected.put(p.getUniqueId(), all.get(0));
      return all.get(0);
    }
    selected.remove(p.getUniqueId());
    return null;
  }

  private boolean exists(String id) {
    return plugin.arenas.all().stream().anyMatch(s -> s.config.id().equals(id));
  }

  public String needArena(Player p) {
    String id = current(p);
    if (id == null)
      throw new IllegalArgumentException(
          "Select an arena first: right-click the Arena selector or use /arena select <id>");
    return id;
  }

  // ------------------------------------------------------------------ report

  /** Sends a ✔/✘ checklist of everything the arena needs before it can start. */
  public void report(Player p, String id) {
    ArenaConfig c = plugin.arenas.require(id).config;
    List<String> lines = new ArrayList<>();
    lines.add("§6━━ " + c.name() + " (" + id + ", " + c.preset() + ") ━━");
    var b = c.bounds();
    lines.add(
        ok(true,
            String.format(
                Locale.ROOT,
                "Bounds %.0f × %.0f × %.0f in %s",
                b.maxX() - b.minX(),
                b.maxY() - b.minY(),
                b.maxZ() - b.minZ(),
                c.worldName())));
    lines.add(ok(true, "Spawn " + coords(c.spawn())));
    lines.add(ok(true, "Players " + c.minPlayers() + "–" + c.maxPlayers()));
    String missing = plugin.arenas.require(id).hooks.report(this, p, c, lines);
    lines.add(
        missing == null
            ? "§a★ Ready to start once " + c.minPlayers() + " players have joined."
            : "§c✘ Not ready: " + missing + ".");
    for (String line : lines) p.sendMessage(Component.text(line));
    plugin.arenas.require(id).hooks.reportMore(this, p, c);
  }

  public static String ok(boolean good, String text) {
    return (good ? "§a✔ §f" : "§c✘ §f") + text;
  }

  public static String at(ArenaConfig.Spawn s) {
    return s == null ? " §c(not set)" : " " + coords(s);
  }

  public static String coords(ArenaConfig.Spawn s) {
    return String.format(Locale.ROOT, "§7(%.0f, %.0f, %.0f)", s.x(), s.y(), s.z());
  }

  public void feedback(Player p, String text) {
    p.sendMessage(Component.text("§a✔ " + text));
    p.sendActionBar(Component.text(text, NamedTextColor.GREEN));
  }

  public void warn(Player p, String text) {
    p.sendMessage(Component.text("§c✘ " + text));
    p.sendActionBar(Component.text(text, NamedTextColor.RED));
  }

  // ------------------------------------------------------------------ events

  @EventHandler(priority = EventPriority.LOW)
  public void interact(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) return;
    Tool t = tool(e.getItem());
    if (t == null) return;
    e.setCancelled(true);
    Player p = e.getPlayer();
    try {
      plugin.admin.authorize(p);
      use(p, t, e);
    } catch (Exception ex) {
      warn(p, NexusArenaEngine.reason(ex));
    }
  }

  private void use(Player p, Tool t, PlayerInteractEvent e) throws Exception {
    boolean left = e.getAction().isLeftClick();
    boolean sneak = p.isSneaking();
    Block block = e.getClickedBlock();
    switch (t.name()) {
      case "SELECT" -> cycle(p, left ? -1 : 1);
      case "WAND" -> wand(p, left, sneak, block);
      case "SPAWN" -> {
        if (left) return;
        String id = needArena(p);
        var position = plugin.admin.point(p, id);
        plugin.admin.change(id, j -> j.add("spawn", ArenaManager.JSON.toJsonTree(position)));
        feedback(p, "Spawn of " + id + " set " + coords(position));
      }
      case "STATUS" -> {
        if (left) return;
        String id = needArena(p);
        if (sneak) plugin.admin.setup(p, id);
        else report(p, id);
      }
      default -> {
        // A tool of the selected arena's game.
        String id = needArena(p);
        plugin.arenas.require(id).hooks.useTool(this, p, t.name(), left, sneak, block);
      }
    }
  }

  private void change(String id, java.util.function.Consumer<JsonObject> edit) throws Exception {
    plugin.admin.change(id, edit);
  }

  private void cycle(Player p, int step) {
    var ids = plugin.arenas.all().stream().map(s -> s.config.id()).sorted().toList();
    if (ids.isEmpty())
      throw new IllegalArgumentException("No arena yet: mark corners, then /arena create <id> <type>");
    int at = ids.indexOf(selected.get(p.getUniqueId()));
    String id = ids.get(Math.floorMod(at + step, ids.size()));
    selected.put(p.getUniqueId(), id);
    feedback(p, "Arena selected: " + id + " (" + (ids.indexOf(id) + 1) + "/" + ids.size() + ")");
    report(p, id);
  }

  private void wand(Player p, boolean left, boolean sneak, Block block) throws Exception {
    if (sneak && !left) {
      String id = needArena(p);
      Block[] raw = marked.get(p.getUniqueId());
      if (raw == null || raw[0] == null || raw[1] == null)
        throw new IllegalArgumentException("Mark both corners first (left and right click)");
      var b = plugin.admin.bounds(p);
      plugin.admin.change(id, j -> j.add("bounds", ArenaManager.JSON.toJsonTree(b)));
      feedback(p, "Bounds applied to " + id + " " + size(b));
      report(p, id);
      return;
    }
    if (block == null)
      throw new IllegalArgumentException("Click a block to mark a corner");
    int index = left ? 0 : 1;
    Block[] raw = marked.computeIfAbsent(p.getUniqueId(), k -> new Block[2]);
    raw[index] = block;
    String line =
        "Corner " + (index + 1) + " = " + block.getX() + ", " + block.getY() + ", " + block.getZ();
    if (raw[0] != null && raw[1] != null && raw[0].getWorld().equals(raw[1].getWorld())) {
      // Whole blocks are inclusive on X/Z; Y gets headroom so players fit under the ceiling.
      var lo = new Location(block.getWorld(),
          Math.min(raw[0].getX(), raw[1].getX()), Math.min(raw[0].getY(), raw[1].getY()),
          Math.min(raw[0].getZ(), raw[1].getZ()));
      var hi = new Location(block.getWorld(),
          Math.max(raw[0].getX(), raw[1].getX()) + 1, Math.max(raw[0].getY(), raw[1].getY()) + HEADROOM,
          Math.max(raw[0].getZ(), raw[1].getZ()) + 1);
      plugin.admin.capture(p, 0, lo);
      plugin.admin.capture(p, 1, hi);
      try {
        line += "  →  " + size(plugin.admin.bounds(p)) + " | sneak + right-click to apply";
      } catch (IllegalArgumentException ex) {
        line += "  (" + NexusArenaEngine.reason(ex) + ")";
      }
    } else {
      line += " | now mark corner " + (index == 0 ? "2 (right-click)" : "1 (left-click)");
    }
    feedback(p, line);
  }

  private static String size(ArenaConfig.Bounds b) {
    return String.format(
        Locale.ROOT, "%.0f × %.0f × %.0f", b.maxX() - b.minX(), b.maxY() - b.minY(), b.maxZ() - b.minZ());
  }

  /** Right-clicking air with a tool should not require a target block, so cancel containers. */
  @EventHandler
  public void drop(PlayerDropItemEvent e) {
    if (tool(e.getItemDrop().getItemStack()) != null) {
      e.setCancelled(true);
      plugin.message(e.getPlayer(), "Admin tools cannot be dropped; use /arena tools off");
    }
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    removeAll(e.getPlayer());
    selected.remove(e.getPlayer().getUniqueId());
    marked.remove(e.getPlayer().getUniqueId());
    visualizing.remove(e.getPlayer().getUniqueId());
  }

  // ----------------------------------------------------------- visualisation

  private void startVisualizer(Player p) {
    if (!visualizing.add(p.getUniqueId())) return;
    p.getScheduler()
        .runAtFixedRate(
            plugin,
            task -> {
              if (!p.isOnline() || !hasAny(p) || plugin.arenas.locked(p.getUniqueId())) {
                visualizing.remove(p.getUniqueId());
                task.cancel();
                return;
              }
              if (tool(p.getInventory().getItemInMainHand()) != null) draw(p);
            },
            () -> visualizing.remove(p.getUniqueId()),
            10,
            10);
  }

  private boolean hasAny(Player p) {
    for (ItemStack it : p.getInventory().getContents()) if (tool(it) != null) return true;
    return false;
  }

  private void draw(Player p) {
    String id = current(p);
    if (id == null) return;
    var c = plugin.arenas.require(id).config;
    if (!p.getWorld().getName().equals(c.worldName())) return;
    var w = p.getWorld();
    dot(p, w, c.spawn(), Particle.CLOUD);
    plugin.arenas.require(id).hooks.draw(this, p, c);
    var dust = new Particle.DustOptions(Color.fromRGB(0xe9, 0xd9, 0x9b), 1.2f);
    var b = c.bounds();
    double[][] corners = {
      {b.minX(), b.minZ()}, {b.maxX(), b.minZ()}, {b.minX(), b.maxZ()}, {b.maxX(), b.maxZ()}
    };
    double py = p.getLocation().getY();
    for (double[] k : corners)
      if (Math.abs(k[0] - p.getX()) < 48 && Math.abs(k[1] - p.getZ()) < 48)
        for (double y = Math.max(b.minY(), py - 3); y <= Math.min(b.maxY(), py + 5); y += 1)
          p.spawnParticle(Particle.DUST, k[0], y, k[1], 1, 0, 0, 0, 0, dust);
    // Floor outline near the player.
    for (double x = b.minX(); x <= b.maxX(); x += 2) {
      edge(p, x, b.minY() + .2, b.minZ(), dust);
      edge(p, x, b.minY() + .2, b.maxZ(), dust);
    }
    for (double z = b.minZ(); z <= b.maxZ(); z += 2) {
      edge(p, b.minX(), b.minY() + .2, z, dust);
      edge(p, b.maxX(), b.minY() + .2, z, dust);
    }
  }

  private void edge(Player p, double x, double y, double z, Particle.DustOptions dust) {
    if (near(p, x, y, z)) p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
  }

  private boolean near(Player p, double x, double y, double z) {
    var l = p.getLocation();
    return Math.abs(x - l.getX()) < 32 && Math.abs(z - l.getZ()) < 32 && Math.abs(y - l.getY()) < 24;
  }

  public void dot(Player p, World w, ArenaConfig.Spawn s, Particle particle) {
    if (s == null || !near(p, s.x(), s.y(), s.z())) return;
    p.spawnParticle(particle, s.x(), s.y() + 1, s.z(), 8, .25, .6, .25, 0.01);
  }
}
