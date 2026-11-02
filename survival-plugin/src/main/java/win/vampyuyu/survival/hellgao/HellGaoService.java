package win.vampyuyu.survival.hellgao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.BastionHook;
import win.vampyuyu.survival.api.BreathApi;
import win.vampyuyu.survival.api.HellGaoApi;
import win.vampyuyu.survival.api.ParagonApi;
import win.vampyuyu.survival.api.ResonanceApi;
import win.vampyuyu.survival.boss.BossItems;
import win.vampyuyu.survival.core.Core;

/**
 * Hell Gao, the Anomaly (GDD 19.4): the secret post-game superboss. It is a Bastion ({@code hell_gao_bastion}, 2 to 5
 * players) whose entry this service guards: Paragon rank, the window between two pulses, an Interpulse Key exchanged
 * for a Breath key. Ascension ranks scale the Guardian; a clear pays titles, an aura, lore and Paragon points, never a stat.
 */
public final class HellGaoService implements BastionHook, HellGaoApi {
    static final String SECTION = "hellgao";
    private final Core core;
    private final LongSupplier clock;
    private HellGaoConfig cfg;
    private long manualUntil;
    private boolean wasOpen;

    public HellGaoService(Core core, HellGaoConfig cfg, LongSupplier clock) {
        this.core = core;
        this.cfg = cfg;
        this.clock = clock;
    }

    public HellGaoConfig cfg() { return cfg; }

    public void reload(HellGaoConfig fresh) { this.cfg = fresh; }

    // ------------------------------------------------------------------ data

    public HellGaoData data(UUID id) {
        return core.profiles().section(id, SECTION, HellGaoData.class, HellGaoData::new);
    }

    /** Edits a section, off the main thread when the profile is not loaded (an offline player is read from disk). */
    public void edit(UUID id, Consumer<HellGaoData> change) {
        Runnable job = () -> core.profiles().edit(id, SECTION, HellGaoData.class, HellGaoData::new, change);
        if (Bukkit.isPrimaryThread() && !core.profiles().isLoaded(id) && core.getPlugin().isEnabled()) {
            core.scheduler().async(job);
        } else {
            job.run();
        }
    }

    // ------------------------------------------------------------------ window

    private long nowSeconds() { return clock.getAsLong() / 1000; }

    @Override
    public boolean windowOpen() {
        if (clock.getAsLong() < manualUntil) {
            return true;
        }
        Optional<BreathApi> breath = core.service(BreathApi.class);
        if (breath.isPresent()) {
            return HellGaoMath.breathWindowOpen(breath.get().active(), breath.get().millisToNextPulse(), cfg.windowSeconds);
        }
        return HellGaoMath.windowOpen(nowSeconds(), cfg.pulsePeriodSeconds, cfg.windowSeconds);
    }

    @Override
    public void openWindow(int seconds) {
        if (seconds > 0) {
            manualUntil = Math.max(manualUntil, clock.getAsLong() + seconds * 1000L);
        }
    }

    @Override
    public void closeWindow() { manualUntil = 0; }

    /** Seconds until the window opens, 0 when open. */
    public long secondsToOpen() {
        if (windowOpen()) {
            return 0;
        }
        Optional<BreathApi> breath = core.service(BreathApi.class);
        if (breath.isPresent()) {
            long next = breath.get().millisToNextPulse();
            return next < 0 ? -1 : Math.max(0, next / 1000 - cfg.windowSeconds);
        }
        return Math.max(0, HellGaoMath.secondsToOpen(nowSeconds(), cfg.pulsePeriodSeconds, cfg.windowSeconds));
    }

    /** Called every two seconds: tells the holders of a key when the window opens. */
    public void tick() {
        boolean open = windowOpen();
        if (open && !wasOpen) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (keysOf(p) > 0 && !core.service(win.vampyuyu.survival.bastion.BastionService.class)
                        .map(b -> b.inBastion(p.getUniqueId())).orElse(false)) {
                    core.msg().mini(p, "<dark_purple>The pulse holds its breath. <gray>Your key is cold: the Rift is open "
                        + "(<white>/hellgao<gray>).");
                }
            }
        }
        wasOpen = open;
    }

    /** Draws the aura of the players who unlocked and switched it on. */
    public void drawAuras() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!core.profiles().isLoaded(p.getUniqueId())) {
                continue;
            }
            HellGaoData d = data(p.getUniqueId());
            if (d.aura && d.titles.contains(cfg.auraTitle)) {
                var at = p.getLocation().add(0, 1.0, 0);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 4, 0.35, 0.7, 0.35, 0.02);
                p.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0.45, 0.8, 0.45, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ items

    int countOf(Player p, String itemId) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && core.items().is(s, itemId)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    int keysOf(Player p) { return countOf(p, BossItems.INTERPULSE_KEY); }

    void give(Player p, String itemId, int amount) {
        for (ItemStack left : p.getInventory().addItem(core.items().create(itemId, amount)).values()) {
            p.getWorld().dropItem(p.getLocation(), left);
        }
    }

    /**
     * Exchanges one Interpulsation Key of the Celestial Breath (assembled from Fragments, {@link BreathApi}) for the
     * Interpulse Key item the Rift opens with. Returns the English refusal, or null when done.
     */
    public String claim(Player p) {
        if (!cfg.enabled) {
            return "Nothing answers.";
        }
        BreathApi breath = core.service(BreathApi.class).orElse(null);
        if (breath == null || breath.keys(p.getUniqueId()) < 1) {
            return "You hold no Interpulsation Key. Its Fragments fall in the Fractures and from immune cells during the Breath.";
        }
        if (!breath.consumeKey(p.getUniqueId())) {
            return "You hold no Interpulsation Key.";
        }
        give(p, BossItems.INTERPULSE_KEY, 1);
        return null;
    }

    // ------------------------------------------------------------------ Paragon, Resonance, Ascension

    /** Whether a player is a Paragon, or has been let in by an admin. */
    boolean paragon(UUID id) {
        if (!cfg.paragonRequired) {
            return true;
        }
        return core.service(ParagonApi.class).map(a -> a.isParagon(id)).orElse(false);
    }

    /** True when the player cleared enough Guardians at Resonance V to open the Ascension ranks. */
    boolean resonanceVDone(UUID id) {
        if (cfg.ascensionResonanceMin <= 0) {
            return true;
        }
        Optional<ResonanceApi> r = core.service(ResonanceApi.class);
        if (r.isEmpty()) {
            return false;
        }
        int done = 0;
        for (String boss : cfg.ascensionBosses) {
            if (r.get().bestRank(id, boss) >= win.vampyuyu.survival.api.Resonance.MAX_RANK) {
                done++;
            }
        }
        return done >= cfg.ascensionResonanceMin;
    }

    /** The Ascension rank the player's next fight really runs at. */
    public int effectiveAscension(UUID id) {
        HellGaoData d = data(id);
        return Math.min(HellGaoMath.clampRank(d.selectedAscension), HellGaoMath.maxSelectable(d.bestAscension, resonanceVDone(id)));
    }

    /** Selects an Ascension rank. Returns the English refusal, or null when set. */
    public String select(Player p, int rank) {
        if (rank < 0 || rank > HellGaoMath.MAX_ASCENSION) {
            return "Ascension goes from 0 (none) to " + HellGaoMath.MAX_ASCENSION + ".";
        }
        HellGaoData d = data(p.getUniqueId());
        boolean resonance = resonanceVDone(p.getUniqueId());
        if (!HellGaoMath.canSelect(rank, d.bestAscension, resonance)) {
            if (!resonance) {
                return "Ascension opens once you cleared " + cfg.ascensionResonanceMin + " Guardians at Resonance V.";
            }
            return "Ascension " + rank + " opens once you cleared Ascension " + (rank - 1) + ".";
        }
        edit(p.getUniqueId(), x -> x.selectedAscension = rank);
        return null;
    }

    // ------------------------------------------------------------------ BastionHook

    private boolean mine(String bastionId) { return bastionId.equals(cfg.bastionId); }

    @Override
    public String refuse(Player leader, List<UUID> members, String bastionId) {
        if (!mine(bastionId)) {
            return null;
        }
        if (!cfg.enabled) {
            return "Nothing answers.";
        }
        for (UUID id : members) {
            if (!paragon(id)) {
                Player p = Bukkit.getPlayer(id);
                String name = p == null ? "A party member" : p.getName();
                return name + " is not a Paragon. The Rift does not open for them.";
            }
        }
        if (!windowOpen()) {
            return "The Rift is shut. It only opens between two pulses.";
        }
        return null;
    }

    @Override
    public boolean visibleTo(Player viewer, String bastionId) {
        if (!mine(bastionId)) {
            return true;
        }
        if (viewer.hasPermission(HellGaoCommand.ADMIN) || keysOf(viewer) > 0) {
            return true;
        }
        return core.profiles().isLoaded(viewer.getUniqueId()) && data(viewer.getUniqueId()).clears > 0;
    }

    @Override
    public Scaling scaling(Player leader, String bastionId) {
        if (!mine(bastionId)) {
            return null;
        }
        int rank = effectiveAscension(leader.getUniqueId());
        return rank == 0 ? Scaling.NONE : new Scaling(HellGaoMath.hpMultiplier(rank), HellGaoMath.damageMultiplier(rank));
    }

    @Override
    public void opened(String bastionId, Player leader, Collection<UUID> participants, Scaling scaling) {
        if (!mine(bastionId)) {
            return;
        }
        int rank = HellGaoMath.rankOfHp(scaling.hpMultiplier());
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                core.msg().mini(p, "<dark_purple>Hell Gao <gray>opens its eyes between two pulses."
                    + (rank > 0 ? " <light_purple>Ascension " + rank + "." : ""));
            }
        }
    }

    @Override
    public void cleared(String bastionId, Collection<UUID> participants, long elapsedMillis, Scaling scaling) {
        if (!mine(bastionId)) {
            return;
        }
        int rank = HellGaoMath.rankOfHp(scaling.hpMultiplier());
        for (UUID id : participants) {
            reward(id, rank);
        }
    }

    /** Pays one clear: titles, lore, Paragon points (ParagonApi). Cosmetics and prestige only. */
    void reward(UUID id, int rank) {
        List<String> newTitles = new ArrayList<>();
        String[] lore = new String[1];
        int points = cfg.paragonPointsClear + cfg.paragonPointsPerAscension * rank;
        List<String> order = cfg.lore.stream().map(HellGaoConfig.Lore::id).toList();
        edit(id, d -> {
            d.clears++;
            d.bestAscension = Math.max(d.bestAscension, rank);
            for (HellGaoConfig.Title t : cfg.titles) {
                if (rank >= t.minAscension() && d.titles.add(t.id())) {
                    newTitles.add(t.name());
                }
            }
            String next = HellGaoMath.nextLore(order, d.lore);
            if (next != null) {
                d.lore.add(next);
                lore[0] = next;
            }
        });
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            for (String t : newTitles) {
                core.msg().mini(p, "<light_purple>Title unlocked: <reset>" + t);
            }
            if (lore[0] != null) {
                core.msg().mini(p, "<gray>A memory surfaces. <white>/hellgao lore");
            }
        }
        if (points > 0) {
            core.service(ParagonApi.class).ifPresent(a -> a.addPoints(id, points, "Hell Gao"));
        }
    }
}
