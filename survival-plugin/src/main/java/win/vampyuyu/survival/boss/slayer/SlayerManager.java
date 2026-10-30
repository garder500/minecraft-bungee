package win.vampyuyu.survival.boss.slayer;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.boss.BossConfig;
import win.vampyuyu.survival.boss.BossData;
import win.vampyuyu.survival.boss.BossManager;
import win.vampyuyu.survival.boss.BossSession;
import win.vampyuyu.survival.boss.Tags;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;

/** Runs slayer quests: paying, kill tracking, spawning the personal boss, rewards, failure, stat perks. */
public final class SlayerManager implements Listener {
    /** Stat modifier source of every slayer perk. */
    public static final String SOURCE = "slayer";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Core core;
    private final BossManager bosses;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Scheduler.Handle> barHide = new HashMap<>();

    public SlayerManager(Core core, BossManager bosses) {
        this.core = core;
        this.bosses = bosses;
    }

    private BossConfig config() { return bosses.config(); }

    public BossData data(Player p) {
        return core.profiles().section(p.getUniqueId(), "boss", BossData.class, BossData::new);
    }

    public Optional<SlayerDefinition> slayer(String id) {
        return Optional.ofNullable(config().slayers().get(id));
    }

    public boolean enabled() { return config().slayerSettings().enabled(); }

    /** Every slayer in config order. */
    public java.util.Collection<SlayerDefinition> slayers() {
        return config().slayers().values();
    }

    // ---------------------------------------------------------------- quest actions

    /** Pays and starts a quest. Returns the English refusal or null on success. */
    public @Nullable String start(Player player, SlayerDefinition def, int tier) {
        if (!enabled()) {
            return "Slayer quests are disabled.";
        }
        Optional<EconomyApi> economy = core.service(EconomyApi.class);
        if (economy.isEmpty()) {
            return "The economy is unavailable right now, so quests cannot be paid.";
        }
        BossData data = data(player);
        double coins = economy.get().coins(player.getUniqueId());
        Optional<String> denial = SlayerQuests.denial(data, def, tier, coins);
        if (denial.isPresent()) {
            return denial.get();
        }
        double cost = def.tier(tier).cost();
        if (!economy.get().take(player.getUniqueId(), cost, "slayer:" + def.id() + ":" + tier)) {
            return "You cannot afford " + Msg.coins(cost) + ".";
        }
        SlayerQuests.begin(data, def, tier, System.currentTimeMillis());
        core.profiles().markDirty(player.getUniqueId());
        cue(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE);
        core.msg().mini(player, "<green>Quest started: <white>" + def.name() + " " + SlayerDefinition.roman(tier)
            + "<gray>. Kill " + describeTargets(def) + " to draw it out.");
        showProgress(player, data.quest);
        return null;
    }

    private static void cue(Player p, Sound sound) {
        p.playSound(p.getLocation(), sound, 1f, 1f);
    }

    static String describeTargets(SlayerDefinition def) {
        StringBuilder sb = new StringBuilder();
        for (var type : def.targets()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).append('s');
        }
        return sb.toString();
    }

    /** Abandons the quest without refund. Returns false when there was none. */
    public boolean cancel(Player player) {
        BossData data = data(player);
        if (data.quest == null) {
            return false;
        }
        bosses.sessionOfOwner(player.getUniqueId()).ifPresent(s -> {
            if (s.options().slayerTier() > 0) {
                s.end(BossSession.End.ADMIN, null);
            }
        });
        SlayerQuests.fail(data);
        core.profiles().markDirty(player.getUniqueId());
        hideBar(player.getUniqueId());
        return true;
    }

    /** Spawns the boss of a READY quest. Returns the English refusal or null. */
    public @Nullable String spawnBoss(Player player) {
        BossData data = data(player);
        BossData.Quest quest = data.quest;
        if (quest == null) {
            return "You have no slayer quest.";
        }
        if (BossData.Quest.FIGHTING.equals(quest.state)) {
            return "Your slayer boss is already out there.";
        }
        if (!BossData.Quest.READY.equals(quest.state)) {
            return "Keep hunting: " + Math.round(quest.progress) + "/" + Math.round(quest.required) + ".";
        }
        SlayerDefinition def = config().slayers().get(quest.slayer);
        if (def == null) {
            SlayerQuests.fail(data);
            return "That slayer no longer exists; the quest was cleared.";
        }
        if (!def.worlds().isEmpty() && !def.worlds().contains(player.getWorld().getName())) {
            return "Go to one of these worlds to face it: " + String.join(", ", def.worlds()) + ".";
        }
        Location at = findSpot(player);
        if (at == null) {
            return "No room to spawn it here. Move to open ground.";
        }
        if (!SlayerQuests.beginFight(data)) {
            return "Your quest is not ready.";
        }
        SlayerDefinition.Tier tier = def.tier(quest.tier);
        int countdown = config().slayerSettings().spawnCountdownSeconds();
        player.showTitle(Title.title(MINI.deserialize("<red><bold>SLAYER BOSS"),
            MINI.deserialize("<gray>" + def.name() + " " + SlayerDefinition.roman(quest.tier) + " is coming..."),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(Math.max(1, countdown)), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
        UUID id = player.getUniqueId();
        core.scheduler().later(Math.max(1, countdown * 20L), () -> launch(id, def, tier, at));
        return null;
    }

    void launch(UUID id, SlayerDefinition def, SlayerDefinition.Tier tier, Location at) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return; // recovered to READY at the next login
        }
        BossData data = data(player);
        Optional<BossSession> session = bosses.spawn(tier.boss(), at, player, new BossSession.SpawnOptions(true, false,
            tier.tier(), 0, tier.timeLimitSeconds(), info -> ended(id, def, tier, info), null));
        if (session.isEmpty()) {
            SlayerQuests.interrupted(data);
            core.profiles().markDirty(id);
            core.msg().warn(player, "The boss could not spawn. Use /slayer spawn to try again.");
            return;
        }
        hideBar(id);
        core.msg().mini(player, "<red>Slayer fight started! <gray>You have <white>"
            + Msg.duration(tier.timeLimitSeconds() * 1000L) + "<gray>. Only you can hurt it.");
    }

    private @Nullable Location findSpot(Player player) {
        double min = config().slayerSettings().spawnMinDistance();
        double max = config().slayerSettings().spawnMaxDistance();
        World world = player.getWorld();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = min + Math.random() * Math.max(0.5, max - min);
            Location probe = player.getLocation().add(Math.cos(angle) * dist, 3, Math.sin(angle) * dist);
            Location ground = groundBelow(world, probe);
            if (ground != null && ground.getBlock().isPassable() && ground.clone().add(0, 1, 0).getBlock().isPassable()
                && Math.abs(ground.getY() - player.getLocation().getY()) <= 6) {
                return ground;
            }
        }
        return null;
    }

    private static @Nullable Location groundBelow(World world, Location from) {
        Location probe = from.clone();
        for (int i = 0; i < 12 && probe.getBlockY() > world.getMinHeight(); i++) {
            if (!probe.getBlock().isPassable()) {
                return new Location(world, from.getX(), probe.getBlockY() + 1.05, from.getZ());
            }
            probe.subtract(0, 1, 0);
        }
        return null;
    }

    // ---------------------------------------------------------------- end of a fight

    private void ended(UUID id, SlayerDefinition def, SlayerDefinition.Tier tier, BossSession.EndInfo info) {
        Player player = Bukkit.getPlayer(id);
        // Also for an offline owner (rare: the profile is loaded from disk), otherwise a lost fight would stay FIGHTING
        // and the login recovery would hand back a free retry.
        BossData data = player != null ? data(player)
            : core.profiles().section(id, "boss", BossData.class, BossData::new);
        switch (info.reason()) {
            case KILLED -> {
                SlayerQuests.Win win = SlayerQuests.win(data, def, tier.tier());
                core.profiles().markDirty(id);
                if (player != null) {
                    core.msg().mini(player, "<green><bold>SLAYER QUEST COMPLETE! <gray>+<light_purple>" + tier.slayerXp()
                        + " <gray>" + def.name() + " XP");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    if (win.leveledUp()) {
                        levelUp(player, def, win);
                    }
                    final SlayerDefinition d = def;
                    core.service(SkillsApi.class).ifPresent(s -> s.addXp(player, Skill.COMBAT, tier.slayerXp() * 2.0,
                        "slayer:" + d.id()));
                }
            }
            default -> {
                if (SlayerQuests.failsQuest(info.reason(), info.session().ledger().total() > 0)) {
                    SlayerQuests.fail(data);
                    core.profiles().markDirty(id);
                    if (player != null) {
                        core.msg().mini(player, "<red><bold>SLAYER QUEST FAILED! <gray>" + failReason(info.reason())
                            + " The coins are lost.");
                    }
                } else {
                    SlayerQuests.interrupted(data);
                    core.profiles().markDirty(id);
                    if (player != null) {
                        core.msg().info(player, "The slayer boss vanished. Your quest is ready: /slayer spawn");
                    }
                }
            }
        }
        if (player != null) {
            applyPerks(player);
        }
    }

    private static String failReason(BossSession.End reason) {
        return switch (reason) {
            case TIMEOUT -> "You ran out of time.";
            case OWNER_DIED -> "You died.";
            case OWNER_LEFT -> "You left the fight.";
            default -> "You lost the boss.";
        };
    }

    private void levelUp(Player player, SlayerDefinition def, SlayerQuests.Win win) {
        StringBuilder text = new StringBuilder("<gold><bold>" + def.name().toUpperCase(java.util.Locale.ROOT)
            + " SLAYER LEVEL " + win.levelAfter() + "!");
        for (SlayerDefinition.Perk perk : def.perks()) {
            if (perk.level() > win.levelBefore() && perk.level() <= win.levelAfter()) {
                text.append("\n <gray>Perk: <white>+").append(Msg.number(perk.amount())).append(' ')
                    .append(perk.stat().displayName());
            }
        }
        core.msg().mini(player, text.toString());
        player.showTitle(Title.title(MINI.deserialize("<gold>Slayer Level " + win.levelAfter()),
            MINI.deserialize("<gray>" + def.name())));
    }

    // ---------------------------------------------------------------- perks

    /** Recomputes the permanent stat bonuses of every slayer (replaces the "slayer" modifiers). */
    public void applyPerks(Player player) {
        Optional<StatsApi> stats = core.service(StatsApi.class);
        if (stats.isEmpty()) {
            return;
        }
        BossData data = data(player);
        Map<Stat, Double> totals = totals(data);
        UUID id = player.getUniqueId();
        stats.get().removeModifiers(id, SOURCE);
        totals.forEach((stat, amount) -> stats.get().addModifier(id, SOURCE, stat, amount));
        stats.get().recompute(player);
    }

    /** Sum of the perks of every slayer for a player's data. */
    public Map<Stat, Double> totals(BossData data) {
        Map<Stat, Double> totals = new java.util.EnumMap<>(Stat.class);
        for (SlayerDefinition def : config().slayers().values()) {
            int level = SlayerQuests.level(data, def);
            SlayerMath.perkTotals(def.perks(), level).forEach((s, a) -> totals.merge(s, a, Double::sum));
        }
        return totals;
    }

    // ---------------------------------------------------------------- kill tracking

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !enabled() || Tags.isOurs(dead) || killer.hasMetadata("NPC")) {
            return;
        }
        BossData data = data(killer);
        BossData.Quest quest = data.quest;
        if (quest == null || !BossData.Quest.COLLECTING.equals(quest.state)) {
            return;
        }
        SlayerDefinition def = config().slayers().get(quest.slayer);
        if (def == null || !def.targets().contains(dead.getType())
            || (!def.worlds().isEmpty() && !def.worlds().contains(dead.getWorld().getName()))) {
            return;
        }
        if (config().slayerSettings().ignoreSpawnerMobs() && fromIgnoredSource(dead)) {
            return;
        }
        boolean ready = SlayerQuests.addKill(data, def, dead.getType());
        core.profiles().markDirty(killer.getUniqueId());
        showProgress(killer, quest);
        if (ready) {
            core.msg().mini(killer, "<green>Your slayer boss senses you... <gray>It is coming!");
            String refusal = spawnBoss(killer);
            if (refusal != null) {
                core.msg().warn(killer, refusal);
            }
        }
    }

    private static boolean fromIgnoredSource(LivingEntity dead) {
        return win.vampyuyu.survival.boss.SpawnSources.isArtificial(dead);
    }

    // ---------------------------------------------------------------- bossbar progress (no action bar)

    private void showProgress(Player player, BossData.Quest quest) {
        SlayerDefinition def = config().slayers().get(quest.slayer);
        if (def == null) {
            return;
        }
        UUID id = player.getUniqueId();
        float fraction = (float) Math.max(0, Math.min(1, quest.progress / Math.max(1, quest.required)));
        String title = "<red>" + def.name() + " " + SlayerDefinition.roman(quest.tier) + " <gray>- <white>"
            + Math.round(quest.progress) + "<gray>/<white>" + Math.round(quest.required);
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = BossBar.bossBar(MINI.deserialize(title), fraction, BossBar.Color.RED, BossBar.Overlay.NOTCHED_20);
            bars.put(id, bar);
            player.showBossBar(bar);
        } else {
            bar.name(MINI.deserialize(title));
            bar.progress(fraction);
        }
        Scheduler.Handle old = barHide.remove(id);
        if (old != null) {
            old.cancel();
        }
        barHide.put(id, core.scheduler().later(100, () -> hideBar(id)));
    }

    private void hideBar(UUID id) {
        Scheduler.Handle handle = barHide.remove(id);
        if (handle != null) {
            handle.cancel();
        }
        BossBar bar = bars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (bar != null && p != null) {
            p.hideBossBar(bar);
        }
    }

    // ---------------------------------------------------------------- join and quit

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later(40, () -> {
            if (!player.isOnline()) {
                return;
            }
            BossData data = data(player);
            SlayerQuests.recover(data);
            core.profiles().markDirty(player.getUniqueId());
            applyPerks(player);
            if (data.quest != null && BossData.Quest.READY.equals(data.quest.state)) {
                core.msg().info(player, "Your slayer boss is waiting. Use /slayer spawn when you are ready.");
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideBar(event.getPlayer().getUniqueId());
    }

    /** Hides every bar (disable). */
    public void shutdown() {
        for (UUID id : Set.copyOf(bars.keySet())) {
            hideBar(id);
        }
    }
}
