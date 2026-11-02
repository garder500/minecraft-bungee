package win.vampyuyu.survival.promotion;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.api.PromotionApi;
import win.vampyuyu.survival.classes.ClassService;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.resonance.ResonanceData;

/**
 * Class promotions (GDD 11.5). A player asks the Master of their class for the next rank; the Master picks a quest chain
 * from the player's profile (what they did so far), the steps are counted by the listeners, and the end of the Adept chain
 * is the player's own choice between the two specializations. Main thread only.
 */
public final class PromotionService implements PromotionApi {
    private final Core core;
    private final ClassService classes;
    private volatile PromotionConfig cfg;
    private final Map<String, Long> lastEntry = new ConcurrentHashMap<>();

    public PromotionService(Core core, ClassService classes, PromotionConfig cfg) {
        this.core = core;
        this.classes = classes;
        this.cfg = cfg;
    }

    public PromotionConfig config() { return cfg; }

    public void reload(PromotionConfig fresh) { this.cfg = fresh; }

    private PromotionData data(UUID player) {
        return core.profiles().section(player, "promotion", PromotionData.class, PromotionData::new);
    }

    // ------------------------------------------------------------ PromotionApi

    @Override
    public Mods modifiers(UUID player) {
        return specOf(player).map(PromotionConfig.Spec::mods).orElse(Mods.NONE);
    }

    /** The specialization of the player, only while it belongs to their class. */
    public Optional<PromotionConfig.Spec> specOf(UUID player) {
        Optional<PlayerClass> vocation = classes.classOf(player);
        Optional<String> id = classes.spec(player);
        if (vocation.isEmpty() || id.isEmpty()) {
            return Optional.empty();
        }
        return cfg.spec(id.get()).filter(s -> s.vocation() == vocation.get());
    }

    @Override
    public Optional<Ability> masterAbility(UUID player) {
        if (!"MASTER".equals(classes.rank(player))) {
            return Optional.empty();
        }
        return classes.classOf(player).flatMap(cfg::ability);
    }

    @Override
    public void recordCast(UUID player) {
        bump(player, PromotionRules.CASTS, 1);
        record(player, StepKind.CAST, "any", 1);
    }

    // ------------------------------------------------------------ history

    /** Adds to a lifetime counter. */
    public void bump(UUID player, String counter, int amount) {
        core.profiles().edit(player, "promotion", PromotionData.class, PromotionData::new,
            d -> d.counters.merge(counter, amount, Integer::sum));
    }

    /** A lifetime counter. */
    public int counter(UUID player, String counter) {
        return data(player).counters.getOrDefault(counter, 0);
    }

    /** The player entered a Realm world: remembers it, and counts a step unless it is a bounce through the same portal. */
    public void enteredRealm(UUID player, String realmId) {
        long now = System.currentTimeMillis();
        Long last = lastEntry.put(player + ":" + realmId, now);
        if (last != null && now - last < PromotionRules.ENTRY_DEBOUNCE_MILLIS) {
            return;
        }
        core.profiles().edit(player, "promotion", PromotionData.class, PromotionData::new, d -> {
            if (!d.realms.contains(realmId)) {
                d.realms.add(realmId);
            }
            d.counters.merge(PromotionRules.ENTRIES, 1, Integer::sum);
        });
        record(player, StepKind.REALM, realmId, 1);
    }

    /** The profiles of the player, strongest first. */
    public List<Profile> profiles(UUID player) {
        PromotionData d = data(player);
        return PromotionRules.ranking(d.counters, d.realms.size());
    }

    // ------------------------------------------------------------ quest

    /** Counts an event toward the current step of the player's chain, and moves on when the step is done. */
    public void record(UUID player, StepKind kind, String target, int amount) {
        if (!cfg.enabled) {
            return;
        }
        Object[] outcome = new Object[3];   // 0 = step done (Step), 1 = chain done (Chain), 2 = progress text
        core.profiles().edit(player, "promotion", PromotionData.class, PromotionData::new, d -> {
            if (d.chain == null) {
                return;
            }
            PromotionConfig.Chain chain = cfg.chain(d.chain).orElse(null);
            if (chain == null || d.step >= chain.steps().size()) {
                return;
            }
            PromotionConfig.Step step = chain.steps().get(d.step);
            if (!PromotionRules.matches(step, kind, target)) {
                return;
            }
            d.progress += amount;
            if (d.progress < step.amount()) {
                outcome[2] = step.text() + " " + d.progress + "/" + step.amount();
                return;
            }
            outcome[0] = step;
            d.step++;
            d.progress = 0;
            if (d.step >= chain.steps().size()) {
                outcome[1] = chain;
                d.chain = null;
                d.step = 0;
                d.pendingSpec = chain.rank().equals("ADEPT");
            }
        });
        Player online = org.bukkit.Bukkit.getPlayer(player);
        if (online == null) {
            if (outcome[1] instanceof PromotionConfig.Chain chain && chain.rank().equals("MASTER")) {
                classes.promote(player, "MASTER", null);
            }
            return;
        }
        if (outcome[2] != null) {
            online.sendActionBar(Msg.plain((String) outcome[2], NamedTextColor.YELLOW));
        }
        if (outcome[0] instanceof PromotionConfig.Step done) {
            core.msg().ok(online, "Step complete: " + done.text());
        }
        if (outcome[1] instanceof PromotionConfig.Chain chain) {
            if (chain.rank().equals("MASTER")) {
                classes.promote(player, "MASTER", null);
                core.msg().ok(online, "You are now a Master " + classes.classOf(player).map(PlayerClass::displayName).orElse("") + "! "
                    + masterAbility(player).map(a -> "New capacity: " + a.name() + " (/class master).").orElse(""));
            } else {
                core.msg().ok(online, chain.title() + " complete! Choose your specialization:");
                sendSpecChoice(online);
            }
        } else if (outcome[0] instanceof PromotionConfig.Step) {
            nextStepHint(online);
        }
    }

    private void nextStepHint(Player player) {
        PromotionData d = data(player.getUniqueId());
        cfg.chain(d.chain).ifPresent(c -> {
            if (d.step < c.steps().size()) {
                core.msg().info(player, "Next: " + c.steps().get(d.step).text());
            }
        });
    }

    // ------------------------------------------------------------ eligibility

    /** What a player has done in the Bastions: total clears and different Guardians cleared (from the Resonance record). */
    private int[] bastionHistory(UUID player) {
        ResonanceData rd = core.profiles().section(player, "resonance", ResonanceData.class, ResonanceData::new);
        int total = rd.clears.values().stream().mapToInt(Integer::intValue).sum();
        int guardians = (int) rd.clears.values().stream().filter(n -> n > 0).count();
        return new int[] {total, guardians};
    }

    /** What the player still has to do before starting the next promotion; empty when they can. */
    public List<String> missing(UUID player) {
        String target = PromotionRules.nextRank(classes.rank(player));
        if (target == null) {
            return List.of();
        }
        int echo = core.service(BossApi.class).map(b -> b.kills(player, cfg.echoBoss)).orElse(0);
        int[] history = bastionHistory(player);
        return PromotionRules.missing(target, echo, history[0], history[1], cfg.adeptBastionClears, cfg.masterGuardians);
    }

    // ------------------------------------------------------------ talking to the Master

    /**
     * Starts the promotion chain for the profile (null = the player's strongest one that has a chain).
     *
     * @return the English refusal, or null when the chain started
     */
    public String start(Player player, Profile requested) {
        UUID id = player.getUniqueId();
        if (!cfg.enabled) {
            return "Promotions are closed right now.";
        }
        if (classes.classOf(id).isEmpty()) {
            return "Choose a class first (/class).";
        }
        PromotionData d = data(id);
        if (d.pendingSpec) {
            return "Choose your specialization first.";
        }
        if (d.chain != null) {
            return "You already have a trial in progress. Abandon it first to start another.";
        }
        String target = PromotionRules.nextRank(classes.rank(id));
        if (target == null) {
            return "You are a Master. The Paragon trials come later.";
        }
        List<String> missing = missing(id);
        if (!missing.isEmpty()) {
            return "You are not ready: " + String.join("; ", missing) + ".";
        }
        PromotionConfig.Chain chain = null;
        if (requested != null) {
            chain = cfg.chainFor(target, requested).orElse(null);
        }
        for (Profile p : profiles(id)) {
            if (chain != null) {
                break;
            }
            chain = cfg.chainFor(target, p).orElse(null);
        }
        if (chain == null) {
            return "The Master has no trial for you yet.";
        }
        String chainId = chain.id();
        core.profiles().edit(id, "promotion", PromotionData.class, PromotionData::new, x -> {
            x.chain = chainId;
            x.step = 0;
            x.progress = 0;
        });
        return null;
    }

    /** Gives up the chain in progress (its steps start again next time). */
    public boolean abandon(UUID player) {
        boolean[] had = new boolean[1];
        core.profiles().edit(player, "promotion", PromotionData.class, PromotionData::new, d -> {
            had[0] = d.chain != null;
            d.chain = null;
            d.step = 0;
            d.progress = 0;
        });
        return had[0];
    }

    /** The player's choice at the end of the Adept chain. @return the English refusal, or null when done */
    public String choose(Player player, String specId) {
        UUID id = player.getUniqueId();
        if (!data(id).pendingSpec) {
            return "You have no specialization to choose.";
        }
        PromotionConfig.Spec spec = cfg.spec(specId).orElse(null);
        if (spec == null || classes.classOf(id).filter(c -> c == spec.vocation()).isEmpty()) {
            return "That specialization is not one of your class.";
        }
        core.profiles().edit(id, "promotion", PromotionData.class, PromotionData::new, d -> d.pendingSpec = false);
        classes.promote(id, "ADEPT", spec.id());
        core.msg().ok(player, "You are now an Adept: " + spec.name() + ". " + spec.summary());
        return null;
    }

    /** Resets everything the promotions recorded for the player (admin). */
    public void reset(UUID player) {
        core.profiles().edit(player, "promotion", PromotionData.class, PromotionData::new, d -> {
            d.chain = null;
            d.step = 0;
            d.progress = 0;
            d.pendingSpec = false;
        });
        classes.promote(player, "NOVICE", null);
        core.profiles().edit(player, "classes", win.vampyuyu.survival.classes.ClassData.class,
            win.vampyuyu.survival.classes.ClassData::new, d -> d.spec = null);
    }

    /**
     * What the Master of the player's class says: the state of the promotion and the clickable next action.
     * The same text is shown by {@code /promotion}.
     */
    public void talk(Player player, PlayerClass masterOf) {
        UUID id = player.getUniqueId();
        PlayerClass mine = classes.classOf(id).orElse(null);
        if (mine == null) {
            core.msg().info(player, "Choose a class first: /class.");
            return;
        }
        if (masterOf != null && masterOf != mine) {
            core.msg().info(player, "I only guide " + masterOf.displayName() + "s. Your Master is the " + mine.displayName() + " Master.");
            return;
        }
        String rank = classes.rank(id);
        PromotionData d = data(id);
        core.msg().info(player, mine.displayName() + ", rank " + rank.charAt(0) + rank.substring(1).toLowerCase(Locale.ROOT)
            + specOf(id).map(s -> " - " + s.name()).orElse(""));
        if (d.pendingSpec) {
            core.msg().info(player, "Your trial is done. Choose your specialization:");
            sendSpecChoice(player);
            return;
        }
        if (d.chain != null) {
            Optional<PromotionConfig.Chain> chain = cfg.chain(d.chain);
            if (chain.isPresent() && d.step < chain.get().steps().size()) {
                PromotionConfig.Step step = chain.get().steps().get(d.step);
                core.msg().info(player, chain.get().title() + " - step " + (d.step + 1) + "/" + chain.get().steps().size() + ": "
                    + step.text() + " (" + d.progress + "/" + step.amount() + ")");
                player.sendMessage(button("[Abandon this trial]", "/promotion abandon", "You will start again from the first step"));
                return;
            }
        }
        String target = PromotionRules.nextRank(rank);
        if (target == null) {
            core.msg().info(player, "You are a Master. " + masterAbility(id).map(a -> a.name() + ": " + a.summary()).orElse(""));
            return;
        }
        List<String> missing = missing(id);
        if (!missing.isEmpty()) {
            core.msg().info(player, "You are not ready for " + target.charAt(0) + target.substring(1).toLowerCase(Locale.ROOT) + ":");
            missing.forEach(m -> core.msg().info(player, " - " + m));
            return;
        }
        core.msg().info(player, "You are ready to become " + (target.equals("ADEPT") ? "an Adept" : "a Master")
            + ". I looked at how you play. Pick the trial you want:");
        int shown = 0;
        for (Profile p : profiles(id)) {
            Optional<PromotionConfig.Chain> chain = cfg.chainFor(target, p);
            if (chain.isEmpty()) {
                continue;
            }
            String tag = shown == 0 ? " (suits you best)" : "";
            player.sendMessage(button(" [" + chain.get().title() + "]" + tag, "/promotion start " + p.name().toLowerCase(Locale.ROOT),
                p.label() + ": " + chain.get().intro() + " " + chain.get().steps().size() + " steps."));
            if (++shown == 2) {
                break;
            }
        }
    }

    private void sendSpecChoice(Player player) {
        PlayerClass mine = classes.classOf(player.getUniqueId()).orElse(null);
        if (mine == null) {
            return;
        }
        for (PromotionConfig.Spec s : cfg.specsOf(mine)) {
            player.sendMessage(button(" [" + s.name() + "] ", "/promotion choose " + s.id(), s.summary())
                .append(Component.text(s.summary(), NamedTextColor.GRAY)));
        }
    }

    private static Component button(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));
    }

    /** Public view of the current chain for tests and the NPC. */
    public Optional<PromotionConfig.Chain> activeChain(UUID player) {
        return cfg.chain(data(player).chain);
    }

    public int step(UUID player) { return data(player).step; }

    public int progress(UUID player) { return data(player).progress; }

    public boolean pendingSpec(UUID player) { return data(player).pendingSpec; }
}
