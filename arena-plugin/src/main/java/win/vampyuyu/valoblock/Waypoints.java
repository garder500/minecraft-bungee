package win.vampyuyu.valoblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * The waypoint graph bots walk on (valoblock/map.json): nodes with a position, undirected links,
 * spawn nodes per side and, per site, its weight, anchors, staging node, plant node, smoke spots and
 * peek corners. Pure: no world access. Straight-line steering between two linked nodes is the whole
 * movement model.
 */
public final class Waypoints {
  public record Node(String id, double x, double y, double z) {
    public double[] pos() {
      return new double[] {x, y, z};
    }

    double dist(double ax, double ay, double az) {
      return Math.sqrt((x - ax) * (x - ax) + (y - ay) * (y - ay) + (z - az) * (z - az));
    }
  }

  /** Where to stand ({@code node}) and where to throw ({@code at}). */
  public record Smoke(String node, double[] at) {}

  public record SiteInfo(String id, double weight, List<String> anchors, String stage, String plant, List<Smoke> smokes, List<String> peeks) {}

  private final String map;
  private final Map<String, Node> nodes;
  private final Map<String, List<String>> links;
  private final Map<VbSide, List<String>> spawns;
  private final Map<String, SiteInfo> sites;
  /** Nodes bots must not route through right now (a closed gate); the start and the goal of a path are exempt. */
  private volatile Set<String> blocked = Set.of();

  private Waypoints(String map, Map<String, Node> nodes, Map<String, List<String>> links, Map<VbSide, List<String>> spawns, Map<String, SiteInfo> sites) {
    this.map = map;
    this.nodes = nodes;
    this.links = links;
    this.spawns = spawns;
    this.sites = sites;
  }

  public String map() {
    return map;
  }

  /** Sets the nodes a path avoids (empty: none). A route to or from a blocked node itself still works. */
  public void setBlocked(Collection<String> ids) {
    blocked = Set.copyOf(ids);
  }

  public Set<String> blocked() {
    return blocked;
  }

  public Node node(String id) {
    Node n = nodes.get(id);
    if (n == null) throw new IllegalArgumentException("No waypoint " + id);
    return n;
  }

  public Collection<Node> nodes() {
    return nodes.values();
  }

  public List<String> spawns(VbSide side) {
    return spawns.get(side);
  }

  public SiteInfo site(String id) {
    SiteInfo s = sites.get(id);
    if (s == null) throw new IllegalArgumentException("No waypoint data for site " + id);
    return s;
  }

  public Collection<SiteInfo> sites() {
    return sites.values();
  }

  public Node nearest(double x, double y, double z) {
    Node best = null;
    double bd = Double.MAX_VALUE;
    for (Node n : nodes.values()) {
      double d = n.dist(x, y, z);
      if (d < bd) {
        bd = d;
        best = n;
      }
    }
    return best;
  }

  /** Shortest list of node ids from one node to another (inclusive), or empty when unreachable. */
  public List<String> path(String from, String to) {
    return path(from, to, blocked);
  }

  /** Same, going around the given nodes (never the start or the goal). */
  public List<String> path(String from, String to, Set<String> avoid) {
    node(from);
    node(to);
    Map<String, Double> dist = new HashMap<>();
    Map<String, String> prev = new HashMap<>();
    PriorityQueue<Map.Entry<String, Double>> q = new PriorityQueue<>(Map.Entry.comparingByValue());
    dist.put(from, 0.0);
    q.add(Map.entry(from, 0.0));
    while (!q.isEmpty()) {
      var cur = q.poll();
      String u = cur.getKey();
      if (cur.getValue() > dist.getOrDefault(u, Double.MAX_VALUE)) continue;
      if (u.equals(to)) break;
      for (String v : links.getOrDefault(u, List.of())) {
        if (!avoid.isEmpty() && avoid.contains(v) && !v.equals(to)) continue;
        Node a = nodes.get(u), b = nodes.get(v);
        double nd = cur.getValue() + a.dist(b.x, b.y, b.z);
        if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
          dist.put(v, nd);
          prev.put(v, u);
          q.add(Map.entry(v, nd));
        }
      }
    }
    if (!dist.containsKey(to)) return List.of();
    LinkedList<String> out = new LinkedList<>();
    for (String at = to; at != null; at = prev.get(at)) out.addFirst(at);
    return out;
  }

  /** Positions to walk from a point to a node: the node nearest to the point first, then along the graph. Empty when unreachable. */
  public List<double[]> route(double x, double y, double z, String to) {
    Node start = nearest(x, y, z);
    List<String> ids = path(start.id(), to);
    if (ids.isEmpty()) return List.of();
    List<double[]> out = new ArrayList<>();
    for (String id : ids) out.add(node(id).pos());
    return out;
  }

  public static Waypoints parse(JsonObject j, List<VbSite> boxes) {
    Map<String, Node> nodes = new LinkedHashMap<>();
    JsonElement nl = j.get("nodes");
    if (nl == null || !nl.isJsonArray() || nl.getAsJsonArray().isEmpty()) throw new IllegalArgumentException("map.json: missing 'nodes'");
    for (JsonElement e : nl.getAsJsonArray()) {
      JsonObject n = e.getAsJsonObject();
      String id = text(n, "id");
      JsonArray p = n.getAsJsonArray("pos");
      if (p == null || p.size() != 3) throw new IllegalArgumentException("map.json: node " + id + " needs pos with 3 numbers");
      if (nodes.put(id, new Node(id, p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble())) != null)
        throw new IllegalArgumentException("map.json: duplicate node " + id);
    }
    Map<String, List<String>> links = new HashMap<>();
    JsonArray ll = j.getAsJsonArray("links");
    if (ll == null) throw new IllegalArgumentException("map.json: missing 'links'");
    for (JsonElement e : ll) {
      JsonArray l = e.getAsJsonArray();
      if (l.size() != 2) throw new IllegalArgumentException("map.json: a link needs 2 node ids");
      String a = l.get(0).getAsString(), b = l.get(1).getAsString();
      need(nodes, a, "link");
      need(nodes, b, "link");
      links.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
      links.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }
    Map<VbSide, List<String>> spawns = new EnumMap<>(VbSide.class);
    JsonObject sp = VbRules.obj(j, "spawns");
    for (VbSide side : VbSide.values()) {
      JsonArray a = sp.getAsJsonArray(side.name());
      if (a == null || a.isEmpty()) throw new IllegalArgumentException("map.json: no spawn node for " + side.name());
      List<String> ids = new ArrayList<>();
      for (JsonElement e : a) ids.add(need(nodes, e.getAsString(), "spawn"));
      spawns.put(side, List.copyOf(ids));
    }
    Map<String, SiteInfo> sites = new LinkedHashMap<>();
    JsonObject sd = VbRules.obj(j, "siteData");
    for (var e : sd.entrySet()) {
      JsonObject s = e.getValue().getAsJsonObject();
      String id = e.getKey();
      double weight = VbRules.d(s, "weight");
      if (weight <= 0) throw new IllegalArgumentException("map.json: site " + id + " weight must be positive");
      List<String> anchors = new ArrayList<>();
      for (JsonElement a : s.getAsJsonArray("anchors")) anchors.add(need(nodes, a.getAsString(), "anchor of " + id));
      if (anchors.isEmpty()) throw new IllegalArgumentException("map.json: site " + id + " needs an anchor");
      List<Smoke> smokes = new ArrayList<>();
      if (s.has("smokes"))
        for (JsonElement a : s.getAsJsonArray("smokes")) {
          JsonObject o = a.getAsJsonObject();
          JsonArray at = o.getAsJsonArray("at");
          if (at == null || at.size() != 3) throw new IllegalArgumentException("map.json: smoke 'at' needs 3 numbers");
          smokes.add(new Smoke(need(nodes, text(o, "node"), "smoke of " + id), new double[] {at.get(0).getAsDouble(), at.get(1).getAsDouble(), at.get(2).getAsDouble()}));
        }
      List<String> peeks = new ArrayList<>();
      if (s.has("peeks")) for (JsonElement a : s.getAsJsonArray("peeks")) peeks.add(need(nodes, a.getAsString(), "peek of " + id));
      sites.put(id, new SiteInfo(id, weight, List.copyOf(anchors), need(nodes, text(s, "stage"), "stage of " + id), need(nodes, text(s, "plant"), "plant of " + id), List.copyOf(smokes), List.copyOf(peeks)));
    }
    for (VbSite box : boxes) if (!sites.containsKey(box.id())) throw new IllegalArgumentException("map.json: no siteData for site " + box.id());
    for (String id : sites.keySet()) if (boxes.stream().noneMatch(b -> b.id().equals(id))) throw new IllegalArgumentException("map.json: siteData for unknown site " + id);
    Waypoints w = new Waypoints(j.has("map") ? j.get("map").getAsString() : "", Collections.unmodifiableMap(nodes), links, spawns, Collections.unmodifiableMap(sites));
    // Every spawn must reach every site's stage, plant and anchors, or a bot would stand still forever.
    String lost = w.unreachable();
    if (lost != null) throw new IllegalArgumentException("map.json: " + lost);
    return w;
  }

  /** The first target (stage, plant, anchor of a site) a spawn cannot reach, as a sentence, or null when all are reachable. */
  String unreachable() {
    for (VbSide side : VbSide.values())
      for (SiteInfo s : sites.values()) {
        List<String> wanted = new ArrayList<>(s.anchors());
        wanted.add(s.stage());
        wanted.add(s.plant());
        for (String spawn : spawns.get(side))
          for (String target : wanted)
            if (path(spawn, target, Set.of()).isEmpty()) return target + " cannot be reached from spawn " + spawn;
      }
    return null;
  }

  /** Every link once, as {a, b}. */
  public synchronized List<String[]> linkList() {
    List<String[]> out = new ArrayList<>();
    for (var e : links.entrySet()) for (String v : e.getValue()) if (e.getKey().compareTo(v) < 0) out.add(new String[] {e.getKey(), v});
    out.sort(Comparator.comparing((String[] l) -> l[0]).thenComparing(l -> l[1]));
    return out;
  }

  /**
   * Drops the links the test calls blocked (a wall in the real world between the two nodes), one at a time, but never one whose loss would
   * leave a spawn unable to reach a site (it is kept and reported). Returns one line per decision.
   */
  public synchronized List<String> pruneLinks(java.util.function.Predicate<String[]> blockedLink) {
    List<String> report = new ArrayList<>();
    for (String[] l : linkList()) {
      if (!blockedLink.test(l)) continue;
      links.get(l[0]).remove(l[1]);
      links.get(l[1]).remove(l[0]);
      String lost = unreachable();
      if (lost != null) {
        links.get(l[0]).add(l[1]);
        links.get(l[1]).add(l[0]);
        report.add("kept " + l[0] + " - " + l[1] + " (blocked, but dropping it: " + lost + ")");
      } else report.add("dropped " + l[0] + " - " + l[1]);
    }
    return report;
  }

  private static String text(JsonObject o, String key) {
    if (!o.has(key)) throw new IllegalArgumentException("map.json: missing '" + key + "'");
    return o.get(key).getAsString();
  }

  private static String need(Map<String, Node> nodes, String id, String what) {
    if (!nodes.containsKey(id)) throw new IllegalArgumentException("map.json: " + what + " names the unknown node " + id);
    return id;
  }
}
