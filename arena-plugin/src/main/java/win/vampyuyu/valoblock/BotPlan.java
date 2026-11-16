package win.vampyuyu.valoblock;

import java.util.*;

/** Team plans for one round of bots. Pure. */
public final class BotPlan {
  private BotPlan() {}

  /** The site a Seedbearer squad goes for, chosen by weight. */
  public static String pickSite(Waypoints g, Random random) {
    double total = 0;
    for (Waypoints.SiteInfo s : g.sites()) total += s.weight();
    double roll = random.nextDouble() * total;
    Waypoints.SiteInfo last = null;
    for (Waypoints.SiteInfo s : g.sites()) {
      last = s;
      roll -= s.weight();
      if (roll < 0) return s.id();
    }
    return last.id();
  }

  /** The site whose plant node is nearest to a point and within {@code radius}, or null. Used to place a gunshot. */
  public static String siteNear(Waypoints g, double x, double y, double z, double radius) {
    String best = null;
    double bd = radius;
    for (Waypoints.SiteInfo s : g.sites()) {
      Waypoints.Node n = g.node(s.plant());
      double d = Math.sqrt((n.x() - x) * (n.x() - x) + (n.y() - y) * (n.y() - y) + (n.z() - z) * (n.z() - z));
      if (d <= bd) {
        bd = d;
        best = s.id();
      }
    }
    return best;
  }

  /** Seedbearers: the chosen site, and the group moves in together: first to the staging node, then, once everyone is close or the wait is over, onto the site. */
  public static final class Attack {
    private final String site;
    private final long startedMs;
    private boolean released;

    public Attack(String site, long startedMs) {
      this.site = site;
      this.startedMs = startedMs;
    }

    public String site() {
      return site;
    }

    public boolean released() {
      return released;
    }

    /** Call once per tick with the positions {x,y,z} of the living members. Returns whether the group is released. */
    public boolean update(long now, Waypoints g, BotConfig cfg, Collection<double[]> living) {
      if (released) return true;
      Waypoints.Node stage = g.node(g.site(site).stage());
      boolean together = !living.isEmpty();
      for (double[] p : living) if (Math.hypot(p[0] - stage.x(), p[2] - stage.z()) > cfg.groupRadius()) together = false;
      if (together || now - startedMs >= cfg.stageTimeoutSeconds() * 1000) released = true;
      return released;
    }
  }

  /** Sealers: who holds which site and anchor, and who has already rotated. */
  public static final class Defense {
    private final Map<UUID, String> siteOf = new HashMap<>();
    private final Map<UUID, Integer> anchorOf = new HashMap<>();
    private final Set<UUID> rotated = new HashSet<>();
    private final Waypoints g;

    private Defense(Waypoints g) {
      this.g = g;
    }

    /** Spreads the defenders over the sites in proportion to their weight (largest remainder), one anchor each, in turn. */
    public static Defense allocate(Waypoints g, List<UUID> defenders, Random random) {
      Defense d = new Defense(g);
      List<UUID> order = new ArrayList<>(defenders);
      Collections.shuffle(order, random);
      List<Waypoints.SiteInfo> sites = new ArrayList<>(g.sites());
      sites.sort(Comparator.comparingDouble(Waypoints.SiteInfo::weight).reversed());
      double total = sites.stream().mapToDouble(Waypoints.SiteInfo::weight).sum();
      int n = order.size();
      int[] share = new int[sites.size()];
      double[] frac = new double[sites.size()];
      int given = 0;
      for (int i = 0; i < sites.size(); i++) {
        double exact = sites.get(i).weight() / total * n;
        share[i] = (int) Math.floor(exact);
        frac[i] = exact - share[i];
        given += share[i];
      }
      while (given < n) {
        int best = 0;
        for (int i = 1; i < sites.size(); i++) if (frac[i] > frac[best]) best = i;
        share[best]++;
        frac[best] = -1;
        given++;
      }
      int at = 0;
      for (int i = 0; i < sites.size(); i++)
        for (int k = 0; k < share[i]; k++) {
          UUID bot = order.get(at++);
          d.siteOf.put(bot, sites.get(i).id());
          d.anchorOf.put(bot, k % sites.get(i).anchors().size());
        }
      return d;
    }

    public String siteOf(UUID bot) {
      return siteOf.get(bot);
    }

    /** The anchor node this defender stands on. */
    public String anchor(UUID bot) {
      String site = siteOf.get(bot);
      if (site == null) return null;
      List<String> anchors = g.site(site).anchors();
      return anchors.get(anchorOf.getOrDefault(bot, 0) % anchors.size());
    }

    public int holding(String site) {
      return (int) siteOf.values().stream().filter(site::equals).count();
    }

    /**
     * Gunfire at a site: up to {@code count} defenders of other sites, the nearest ones that have not rotated yet, go there.
     * A site is never left empty. Returns who was sent.
     */
    public List<UUID> rotate(String site, Map<UUID, double[]> positions, int count) {
      Waypoints.Node target = g.node(g.site(site).plant());
      List<UUID> candidates = new ArrayList<>();
      for (var e : siteOf.entrySet())
        if (!e.getValue().equals(site) && !rotated.contains(e.getKey()) && positions.containsKey(e.getKey())) candidates.add(e.getKey());
      candidates.sort(Comparator.comparingDouble(b -> Math.hypot(positions.get(b)[0] - target.x(), positions.get(b)[2] - target.z())));
      List<UUID> sent = new ArrayList<>();
      for (UUID bot : candidates) {
        if (sent.size() >= count) break;
        if (holding(siteOf.get(bot)) <= 1) continue;
        siteOf.put(bot, site);
        anchorOf.put(bot, holding(site) - 1);
        rotated.add(bot);
        sent.add(bot);
      }
      return sent;
    }

    public void remove(UUID bot) {
      siteOf.remove(bot);
    }
  }
}
