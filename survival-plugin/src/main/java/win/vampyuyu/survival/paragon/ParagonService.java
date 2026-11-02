package win.vampyuyu.survival.paragon;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.ParagonApi;
import win.vampyuyu.survival.api.PromotionApi;
import win.vampyuyu.survival.classes.ClassService;
import win.vampyuyu.survival.core.Core;

/**
 * Paragon (GDD 11.5, 19.1): who survived the Astral Interior, their Transcendence rank, the cosmetics and perks of the
 * ranks they reached, and the ultimate of their class. Main thread only.
 */
public final class ParagonService implements ParagonApi {
    private final Core core;
    private volatile ParagonConfig cfg;

    public ParagonService(Core core, ParagonConfig cfg) {
        this.core = core;
        this.cfg = cfg;
    }

    public ParagonConfig config() { return cfg; }

    public void reload(ParagonConfig fresh) { this.cfg = fresh; }

    private ParagonData data(UUID player) {
        return core.profiles().section(player, "paragon", ParagonData.class, ParagonData::new);
    }

    // ------------------------------------------------------------ ParagonApi

    @Override
    public boolean isParagon(UUID player) {
        return cfg.enabled && data(player).astralCleared;
    }

    @Override
    public int rank(UUID player) {
        return isParagon(player) ? ParagonRules.rankFor(cfg.ranks(), data(player).points) : 0;
    }

    @Override
    public int points(UUID player) {
        return data(player).points;
    }

    @Override
    public void markAstralCleared(UUID player) {
        if (!cfg.enabled || data(player).astralCleared) {
            return;
        }
        core.profiles().edit(player, "paragon", ParagonData.class, ParagonData::new, d -> d.astralCleared = true);
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            core.msg().ok(online, "You survived the Astral Interior. Ether of the Devourer now runs in you: you are a Paragon.");
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        addPoints(player, cfg.astralClearPoints, "the Astral Interior");
    }

    @Override
    public void addPoints(UUID player, int points, String reason) {
        if (!cfg.enabled || points <= 0 || !data(player).astralCleared) {
            return;
        }
        int[] ranks = new int[2];
        core.profiles().edit(player, "paragon", ParagonData.class, ParagonData::new, d -> {
            ranks[0] = ParagonRules.rankFor(cfg.ranks(), d.points);
            d.points = (int) Math.min(Integer.MAX_VALUE, (long) d.points + points);
            ranks[1] = ParagonRules.rankFor(cfg.ranks(), d.points);
        });
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        core.msg().info(online, "+" + points + " Paragon point" + (points == 1 ? "" : "s") + (reason == null || reason.isBlank() ? "" : " (" + reason + ")") + ".");
        for (ParagonConfig.Rank r : cfg.ranks()) {
            if (r.number() > ranks[0] && r.number() <= ranks[1]) {
                core.msg().ok(online, "Transcendence " + r.number() + ": " + r.name() + ". " + r.text());
                online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
            }
        }
    }

    @Override
    public String title(UUID player) {
        int rank = rank(player);
        if (rank == 0) {
            return "";
        }
        List<ParagonConfig.Rank> titles = ParagonRules.reached(cfg.ranks(), rank, ParagonConfig.Kind.TITLE);
        String chosen = data(player).title;
        for (ParagonConfig.Rank r : titles) {
            if (r.key().equalsIgnoreCase(chosen)) {
                return r.key();
            }
        }
        return titles.isEmpty() ? "" : titles.get(titles.size() - 1).key();
    }

    @Override
    public int perk(UUID player, String key) {
        return ParagonRules.perk(cfg.ranks(), rank(player), key);
    }

    @Override
    public Optional<PromotionApi.Ability> ultimate(UUID player) {
        if (rank(player) < 1) {
            return Optional.empty();
        }
        ClassService classes = core.service(ClassService.class).orElse(null);
        if (classes == null || !"MASTER".equals(classes.rank(player))) {
            return Optional.empty();
        }
        return classes.classOf(player).flatMap(cfg::ultimate);
    }

    // ------------------------------------------------------------ choices

    /** The aura the player shows (the chosen one if reached, else the highest), empty when hidden or none. */
    public Optional<String> aura(UUID player) {
        int rank = rank(player);
        ParagonData d = data(player);
        if (rank == 0 || !d.showAura) {
            return Optional.empty();
        }
        List<ParagonConfig.Rank> auras = ParagonRules.reached(cfg.ranks(), rank, ParagonConfig.Kind.AURA);
        for (ParagonConfig.Rank r : auras) {
            if (r.key().equalsIgnoreCase(d.aura)) {
                return Optional.of(r.key());
            }
        }
        return auras.isEmpty() ? Optional.empty() : Optional.of(auras.get(auras.size() - 1).key());
    }

    /** Ids of the auras the player reached. */
    public List<String> aurasOf(UUID player) {
        return ParagonRules.reached(cfg.ranks(), rank(player), ParagonConfig.Kind.AURA).stream().map(ParagonConfig.Rank::key).toList();
    }

    /** Titles the player reached. */
    public List<String> titlesOf(UUID player) {
        return ParagonRules.reached(cfg.ranks(), rank(player), ParagonConfig.Kind.TITLE).stream().map(ParagonConfig.Rank::key).toList();
    }

    /** Picks an aura, or "off". @return the English refusal, or null when done */
    public String chooseAura(UUID player, String id) {
        if (rank(player) == 0) {
            return "You are not a Paragon.";
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (key.equals("off") || key.equals("on")) {
            core.profiles().edit(player, "paragon", ParagonData.class, ParagonData::new, d -> d.showAura = key.equals("on"));
            return null;
        }
        if (aurasOf(player).stream().noneMatch(a -> a.equals(key))) {
            return "You have not reached that aura. Yours: " + (aurasOf(player).isEmpty() ? "none yet" : String.join(", ", aurasOf(player))) + ".";
        }
        core.profiles().edit(player, "paragon", ParagonData.class, ParagonData::new, d -> {
            d.aura = key;
            d.showAura = true;
        });
        return null;
    }

    /** Picks a title. @return the English refusal, or null when done */
    public String chooseTitle(UUID player, String title) {
        if (rank(player) == 0) {
            return "You are not a Paragon.";
        }
        String found = titlesOf(player).stream().filter(t -> t.equalsIgnoreCase(title)).findFirst().orElse(null);
        if (found == null) {
            return "You have not reached that title. Yours: " + (titlesOf(player).isEmpty() ? "none yet" : String.join(", ", titlesOf(player))) + ".";
        }
        core.profiles().edit(player, "paragon", ParagonData.class, ParagonData::new, d -> d.title = found);
        return null;
    }

    /** Admin: forgets everything about the player's Paragon status. */
    public void reset(UUID player) {
        core.profiles().edit(player, "paragon", ParagonData.class, ParagonData::new, d -> {
            d.astralCleared = false;
            d.points = 0;
            d.title = null;
            d.aura = null;
            d.showAura = true;
        });
    }

    /** Admin: sets the Paragon status without the raid. */
    public void setParagon(UUID player, boolean paragon) {
        core.profiles().edit(player, "paragon", ParagonData.class, ParagonData::new, d -> d.astralCleared = paragon);
    }

    // ------------------------------------------------------------ aura

    /** Draws the aura of every online Paragon (called every {@code aura-seconds}). */
    public void drawAuras() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            aura(p.getUniqueId()).ifPresent(id -> draw(p, id));
        }
    }

    private static void draw(Player player, String id) {
        Location base = player.getLocation().add(0, 1, 0);
        switch (id) {
            case "ether" -> player.getWorld().spawnParticle(Particle.END_ROD, base, 4, 0.5, 0.8, 0.5, 0.01);
            case "starlight" -> player.getWorld().spawnParticle(Particle.ENCHANT, base, 12, 0.6, 0.9, 0.6, 0.4);
            case "interpulse" -> player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, base, 10, 0.4, 0.9, 0.4, 0.05);
            default -> { }
        }
    }
}
