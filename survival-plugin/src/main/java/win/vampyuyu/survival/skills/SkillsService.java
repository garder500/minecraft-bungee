package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.api.event.SkillLevelUpEvent;
import win.vampyuyu.survival.api.event.SkillXpEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/**
 * The skills engine and the {@link SkillsApi} implementation: experience, levels, stat modifiers, collections and
 * their rewards. Experience is granted on the main thread only (a call from another thread is moved there).
 * Reward bookkeeping lives in {@link SkillsData#claimed}: a reward is paid once, and a coin reward that could not be
 * paid because the economy feature is absent stays unclaimed until the next {@link #settle}.
 */
public final class SkillsService implements SkillsApi {
    /** Hard cap on one grant, a guard against a broken source (1 billion). */
    static final double MAX_SINGLE_GRANT = 1_000_000_000d;
    static final String RECIPE_PREFIX = "sbskills_";

    private final Core core;
    private volatile SkillsConfig config;
    private final XpBoosts boosts = new XpBoosts();

    public SkillsService(Core core, SkillsConfig config) {
        this.core = core;
        this.config = config;
    }

    public SkillsConfig config() {
        return config;
    }

    void config(SkillsConfig config) {
        this.config = config;
    }

    public XpBoosts boosts() {
        return boosts;
    }

    public Core core() {
        return core;
    }

    public SkillsData data(UUID player) {
        return core.profiles().section(player, "skills", SkillsData.class, SkillsData::new);
    }

    private void dirty(UUID player) {
        core.profiles().markDirty(player);
    }

    /** Runs a change on a player's data from any thread (offline players included). */
    public void edit(UUID player, Consumer<SkillsData> change) {
        core.profiles().edit(player, "skills", SkillsData.class, SkillsData::new, change);
    }

    public NamespacedKey recipeKey(String recipeId) {
        return new NamespacedKey(core.getPlugin(), RECIPE_PREFIX + recipeId);
    }

    // ---------------------------------------------------------------- SkillsApi

    @Override
    public int level(UUID player, Skill skill) {
        SkillsConfig cfg = config;
        return cfg.levels.levelOf(data(player).xpOf(skill.key()), cfg.maxLevel(skill));
    }

    @Override
    public double xp(UUID player, Skill skill) {
        return data(player).xpOf(skill.key());
    }

    @Override
    public int maxLevel(Skill skill) {
        return config.maxLevel(skill);
    }

    @Override
    public void addXp(Player player, Skill skill, double amount, String source) {
        grant(player, skill, amount, source);
    }

    /** Fractional level (level + progress) of a skill: used for averages and boards. */
    public double fractionalLevel(UUID player, Skill skill) {
        SkillsConfig cfg = config;
        return cfg.levels.fractionalLevel(data(player).xpOf(skill.key()), cfg.maxLevel(skill));
    }

    /** Average of the configured skills (carpentry excluded by default), with progress inside each level. */
    public double average(UUID player) {
        SkillsConfig cfg = config;
        if (cfg.averageSkills.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Skill skill : cfg.averageSkills) {
            sum += fractionalLevel(player, skill);
        }
        return sum / cfg.averageSkills.size();
    }

    // ---------------------------------------------------------------- experience

    /**
     * Grants experience and returns what was really added (0 when refused or cancelled).
     * Pipeline: config multiplier x skill multiplier x boosts, then {@link SkillXpEvent}, then the level-ups.
     */
    public double grant(Player player, Skill skill, double amount, String source) {
        if (player == null || skill == null || source == null || !(amount > 0) || Double.isInfinite(amount)) {
            return 0;
        }
        if (!Bukkit.isPrimaryThread()) {
            core.scheduler().sync(() -> grant(player, skill, amount, source));
            return 0;
        }
        if (!player.isOnline()) {
            return 0;
        }
        SkillsConfig cfg = config;
        double scaled = amount * cfg.globalMultiplier * cfg.skill(skill).multiplier()
            * boosts.multiplier(skill, cfg.boostCap);
        SkillXpEvent event = new SkillXpEvent(player, skill, scaled, source);
        Bukkit.getPluginManager().callEvent(event);
        double gained = event.getAmount();
        if (event.isCancelled() || !(gained > 0) || Double.isNaN(gained) || Double.isInfinite(gained)) {
            return 0;
        }
        gained = Math.min(gained, MAX_SINGLE_GRANT);
        UUID id = player.getUniqueId();
        SkillsData data = data(id);
        double before = data.xpOf(skill.key());
        int max = cfg.maxLevel(skill);
        int oldLevel = cfg.levels.levelOf(before, max);
        data.xp.put(skill.key(), before + gained);
        dirty(id);
        int newLevel = cfg.levels.levelOf(before + gained, max);
        if (newLevel > oldLevel) {
            levelUp(player, skill, oldLevel, newLevel);
        }
        return gained;
    }

    private void levelUp(Player player, Skill skill, int oldLevel, int newLevel) {
        SkillsConfig cfg = config;
        double coinsPaid = 0;
        for (int level = oldLevel + 1; level <= newLevel; level++) {
            coinsPaid += payLevel(player.getUniqueId(), skill, level);
            Bukkit.getPluginManager().callEvent(new SkillLevelUpEvent(player, skill, level - 1, level));
        }
        applyStats(player, skill);
        recompute(player);
        announceLevelUp(player, skill, oldLevel, newLevel, coinsPaid, cfg);
    }

    /** Pays the coins of one level once. Returns the coins paid (0 when already claimed or no economy). */
    private double payLevel(UUID id, Skill skill, int level) {
        SkillsData data = data(id);
        String claim = SkillsData.levelClaim(skill.key(), level);
        if (data.claimed.contains(claim)) {
            return 0;
        }
        double coins = config.levelCoins.at(level);
        if (coins > 0) {
            Optional<EconomyApi> eco = core.service(EconomyApi.class);
            if (eco.isEmpty()) {
                return 0;
            }
            eco.get().add(id, coins, "skills:level:" + skill.key() + ":" + level);
        }
        data.claimed.add(claim);
        dirty(id);
        return coins;
    }

    private void announceLevelUp(Player player, Skill skill, int oldLevel, int newLevel, double coins, SkillsConfig cfg) {
        try {
            String name = skill.displayName();
            boolean milestone = cfg.levelCoins.milestoneEvery() > 0 && newLevel % cfg.levelCoins.milestoneEvery() == 0;
            StringBuilder text = new StringBuilder();
            text.append("<dark_aqua><strike>                                        </strike>\n");
            text.append(" <aqua><bold>SKILL LEVEL UP</bold> <dark_aqua>").append(name).append(" <dark_gray>")
                .append(roman(oldLevel)).append(" ➜ <aqua>").append(roman(newLevel)).append("\n");
            text.append("\n <green><bold>REWARDS</bold>\n");
            for (int level = oldLevel + 1; level <= newLevel; level++) {
                List<String> lines = RewardText.levelLines(cfg, skill, level);
                // Coins are shown once, summed, so skip the per-level coin line when several levels were gained.
                for (String line : lines) {
                    if (newLevel - oldLevel > 1 && line.startsWith("<gold>+")) {
                        continue;
                    }
                    text.append("  <dark_gray>▪ ").append(line).append("\n");
                }
            }
            if (newLevel - oldLevel > 1 && coins > 0) {
                text.append("  <dark_gray>▪ <gold>+").append(Msg.coins(coins)).append("\n");
            } else if (coins <= 0 && cfg.levelCoins.at(newLevel) > 0 && core.service(EconomyApi.class).isEmpty()) {
                text.append("  <dark_gray>▪ <gray>Coins will be paid when the economy is available\n");
            }
            text.append("<dark_aqua><strike>                                        </strike>");
            player.sendMessage(Msg.component(text.toString()));
            Component title = Msg.component("<aqua><bold>SKILL LEVEL UP");
            Component sub = Msg.component("<dark_aqua>" + name + " <aqua>" + roman(newLevel));
            player.showTitle(Title.title(title, sub, Title.Times.times(java.time.Duration.ofMillis(150),
                java.time.Duration.ofMillis(milestone ? 2400 : 1500), java.time.Duration.ofMillis(400))));
            player.playSound(player.getLocation(),
                milestone ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            player.getWorld().spawnParticle(milestone ? Particle.TOTEM_OF_UNDYING : Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), milestone ? 40 : 15, 0.5, 0.8, 0.5, 0.1);
        } catch (RuntimeException e) {
            core.debug("skills: level-up presentation failed for " + player.getName() + ": " + e);
        }
    }

    private static String roman(int level) {
        return Integer.toString(level);
    }

    // ---------------------------------------------------------------- stats

    /** Pushes the stat modifiers of one skill to the stats engine (no recompute). */
    public void applyStats(Player player, Skill skill) {
        applyStats(player.getUniqueId(), skill);
    }

    void applyStats(UUID id, Skill skill) {
        Optional<StatsApi> api = core.service(StatsApi.class);
        if (api.isEmpty()) {
            return;
        }
        SkillsConfig cfg = config;
        int level = cfg.levels.levelOf(data(id).xpOf(skill.key()), cfg.maxLevel(skill));
        String source = "skill:" + skill.key();
        api.get().removeModifiers(id, source);
        for (SkillsConfig.StatReward reward : cfg.skill(skill).stats()) {
            double total = reward.total(level);
            if (total != 0) {
                api.get().addModifier(id, source, reward.stat(), total);
            }
        }
    }

    /** Applies every skill's modifiers and recomputes the player's stats. Main thread. */
    public void applyAllStats(Player player) {
        for (Skill skill : Skill.values()) {
            applyStats(player, skill);
        }
        recompute(player);
    }

    /** Removes our modifiers from the stats engine (player left). */
    public void clearStats(UUID id) {
        core.service(StatsApi.class).ifPresent(api -> {
            for (Skill skill : Skill.values()) {
                api.removeModifiers(id, "skill:" + skill.key());
            }
        });
    }

    private void recompute(Player player) {
        core.service(StatsApi.class).ifPresent(api -> {
            try {
                api.recompute(player);
            } catch (RuntimeException e) {
                core.debug("skills: stats recompute failed: " + e);
            }
        });
    }

    // ---------------------------------------------------------------- collections

    /** Adds to the collection that owns the material; no-op when none does or the amount is not positive. */
    public void addCollection(Player player, Material material, long amount) {
        SkillsConfig.CollectionDef def = config.collectionByMaterial.get(material);
        if (def != null) {
            addCollection(player, def, amount);
        }
    }

    public void addCollection(Player player, SkillsConfig.CollectionDef def, long amount) {
        if (player == null || def == null || amount <= 0) {
            return;
        }
        UUID id = player.getUniqueId();
        SkillsData data = data(id);
        long before = data.collected(def.id());
        long after = Math.min(Long.MAX_VALUE / 4, before + amount);
        data.collections.put(def.id(), after);
        dirty(id);
        int oldTier = def.tierOf(before);
        int newTier = def.tierOf(after);
        if (newTier <= oldTier) {
            return;
        }
        List<SkillsConfig.RecipeDef> newlyUnlocked = new ArrayList<>();
        for (SkillsConfig.RecipeDef recipe : config.recipes.values()) {
            Integer need = recipe.requires().get(def.id());
            if (need != null && oldTier < need && newTier >= need && meetsRequirements(data, recipe)) {
                newlyUnlocked.add(recipe);
            }
        }
        double coins = 0;
        for (int tier = oldTier + 1; tier <= newTier; tier++) {
            coins += payTier(id, def, tier);
        }
        announceTier(player, def, oldTier, newTier, coins, newlyUnlocked);
        for (SkillsConfig.RecipeDef recipe : newlyUnlocked) {
            discover(player, recipe);
            data.seenRecipes.add(recipe.id());
        }
    }

    private double payTier(UUID id, SkillsConfig.CollectionDef def, int tier) {
        SkillsData data = data(id);
        String claim = SkillsData.tierClaim(def.id(), tier);
        if (data.claimed.contains(claim)) {
            return 0;
        }
        double coins = def.coinsFor(tier);
        if (coins > 0) {
            Optional<EconomyApi> eco = core.service(EconomyApi.class);
            if (eco.isEmpty()) {
                return 0;
            }
            eco.get().add(id, coins, "skills:collection:" + def.id() + ":" + tier);
        }
        data.claimed.add(claim);
        dirty(id);
        return coins;
    }

    private void announceTier(Player player, SkillsConfig.CollectionDef def, int oldTier, int newTier, double coins,
            List<SkillsConfig.RecipeDef> unlocked) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("<gold><strike>                                        </strike>\n");
            text.append(" <yellow><bold>COLLECTION UNLOCKED</bold> <gold>").append(def.name()).append(" <yellow>")
                .append(RewardText.roman(newTier)).append("\n");
            if (coins > 0) {
                text.append("  <dark_gray>▪ <gold>+").append(Msg.coins(coins)).append("\n");
            }
            for (SkillsConfig.RecipeDef recipe : unlocked) {
                text.append("  <dark_gray>▪ <green>Recipe unlocked: <white>").append(resultName(recipe)).append("\n");
            }
            text.append("<gold><strike>                                        </strike>");
            player.sendMessage(Msg.component(text.toString()));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        } catch (RuntimeException e) {
            core.debug("skills: collection message failed: " + e);
        }
    }

    /** Display name of a recipe's result. */
    public String resultName(SkillsConfig.RecipeDef recipe) {
        SkillsConfig.EnchantedDef ench = recipe.resultCustom() == null ? null : config.enchantedItems.get(recipe.resultCustom());
        if (ench != null) {
            return ench.name();
        }
        if (recipe.resultCustom() != null) {
            return core.items().def(recipe.resultCustom()).map(d -> d.name()).orElse(recipe.resultCustom());
        }
        return SkillsConfig.prettify(recipe.resultMaterial().name());
    }

    // ---------------------------------------------------------------- recipes

    /** True when every collection requirement of the recipe is met. */
    public boolean meetsRequirements(SkillsData data, SkillsConfig.RecipeDef recipe) {
        for (var need : recipe.requires().entrySet()) {
            SkillsConfig.CollectionDef def = config.collections.get(need.getKey());
            if (def == null || def.tierOf(data.collected(def.id())) < need.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean meetsRequirements(UUID player, SkillsConfig.RecipeDef recipe) {
        return meetsRequirements(data(player), recipe);
    }

    /** Adds the recipe to the player's recipe book, ignoring servers that refuse. */
    public void discover(Player player, SkillsConfig.RecipeDef recipe) {
        try {
            player.discoverRecipe(recipeKey(recipe.id()));
        } catch (RuntimeException e) {
            core.debug("skills: discoverRecipe failed: " + e);
        }
    }

    // ---------------------------------------------------------------- join / settle

    /** Pays rewards that were earned while the economy was missing and syncs recipes. Main thread. */
    public void settle(Player player) {
        UUID id = player.getUniqueId();
        SkillsData data = data(id);
        SkillsConfig cfg = config;
        for (Skill skill : Skill.values()) {
            int level = cfg.levels.levelOf(data.xpOf(skill.key()), cfg.maxLevel(skill));
            for (int l = 1; l <= level; l++) {
                payLevel(id, skill, l);
            }
        }
        for (SkillsConfig.CollectionDef def : cfg.collections.values()) {
            int tier = def.tierOf(data.collected(def.id()));
            for (int t = 1; t <= tier; t++) {
                payTier(id, def, t);
            }
        }
        for (SkillsConfig.RecipeDef recipe : cfg.recipes.values()) {
            if (meetsRequirements(data, recipe)) {
                discover(player, recipe);
            }
        }
    }

    /** Coins of reached levels and tiers that are still waiting for the economy. */
    public double pendingCoins(UUID id) {
        SkillsData data = data(id);
        SkillsConfig cfg = config;
        double sum = 0;
        for (Skill skill : Skill.values()) {
            int level = cfg.levels.levelOf(data.xpOf(skill.key()), cfg.maxLevel(skill));
            for (int l = 1; l <= level; l++) {
                if (!data.claimed.contains(SkillsData.levelClaim(skill.key(), l))) {
                    sum += cfg.levelCoins.at(l);
                }
            }
        }
        for (SkillsConfig.CollectionDef def : cfg.collections.values()) {
            int tier = def.tierOf(data.collected(def.id()));
            for (int t = 1; t <= tier; t++) {
                if (!data.claimed.contains(SkillsData.tierClaim(def.id(), t))) {
                    sum += def.coinsFor(t);
                }
            }
        }
        return sum;
    }

    // ---------------------------------------------------------------- admin (any thread)

    /**
     * Sets experience without paying anything: the levels that are reached count as claimed, the levels above are
     * un-claimed so earning them later pays them. Safe from any thread. Re-applies stats for an online player.
     */
    public void adminSetXp(UUID id, Skill skill, double xp) {
        double value = Math.max(0, Math.min(xp, 1e13));
        SkillsConfig cfg = config;
        edit(id, data -> {
            data.xp.put(skill.key(), value);
            int level = cfg.levels.levelOf(value, cfg.maxLevel(skill));
            for (int l = 1; l <= cfg.levels.maxLevel(); l++) {
                String claim = SkillsData.levelClaim(skill.key(), l);
                if (l <= level) {
                    data.claimed.add(claim);
                } else {
                    data.claimed.remove(claim);
                }
            }
        });
        refreshOnline(id);
    }

    /** Clears one skill (or all of them when {@code skill} is null), claims included. */
    public void adminReset(UUID id, Skill skill) {
        edit(id, data -> {
            for (Skill s : Skill.values()) {
                if (skill == null || s == skill) {
                    data.xp.remove(s.key());
                    data.claimed.removeIf(c -> c.startsWith("lvl:" + s.key() + ":"));
                }
            }
        });
        refreshOnline(id);
    }

    /** Sets a collection counter; reached tiers count as claimed, higher ones are un-claimed. */
    public void adminSetCollection(UUID id, SkillsConfig.CollectionDef def, long amount) {
        long value = Math.max(0, Math.min(amount, Long.MAX_VALUE / 4));
        edit(id, data -> {
            data.collections.put(def.id(), value);
            int tier = def.tierOf(value);
            for (int t = 1; t <= def.maxTier(); t++) {
                String claim = SkillsData.tierClaim(def.id(), t);
                if (t <= tier) {
                    data.claimed.add(claim);
                } else {
                    data.claimed.remove(claim);
                }
            }
        });
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            core.scheduler().sync(() -> settleRecipes(online));
        }
    }

    private void settleRecipes(Player player) {
        SkillsData data = data(player.getUniqueId());
        for (SkillsConfig.RecipeDef recipe : config.recipes.values()) {
            if (meetsRequirements(data, recipe)) {
                discover(player, recipe);
            }
        }
    }

    private void refreshOnline(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            core.scheduler().sync(() -> {
                if (online.isOnline()) {
                    applyAllStats(online);
                }
            });
        }
    }

    // ---------------------------------------------------------------- formatting helpers for menus and commands

    /** "Mining", "mining", "MINING" to the skill; null when unknown. */
    public static Skill parseSkill(String text) {
        return SkillsConfig.skillByName(text == null ? null : text.toLowerCase(Locale.ROOT));
    }
}
