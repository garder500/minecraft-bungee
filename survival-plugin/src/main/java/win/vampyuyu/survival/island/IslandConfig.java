package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** Typed view of {@code island.yml}. Built once at enable (and by tests from a YAML string). */
public final class IslandConfig {
    /** One upgrade step: the resulting value and the price of reaching it. */
    public record Tier(int value, double cost) {}

    /** An item given by a chest list entry such as {@code ICE:2}. */
    public record ItemSpec(Material material, int amount) {}

    /** One island template as configured. */
    public record TemplateCfg(String id, boolean enabled, String name, Material icon, String biome,
            List<String> description, List<ItemSpec> chest) {}

    /** Per world settings. */
    public record WorldCfg(String time, double border, boolean weatherClear, Map<String, Object> gamerules) {}

    public final int cellSize;
    public final int islandY;
    public final int edgeMargin;
    public final Map<String, WorldCfg> worlds = new LinkedHashMap<>();
    public final boolean hubEnabled;
    public final int hubY;
    public final int hubMaxBlocks;
    public final boolean hubInteract;
    public final int blocksPerTick;
    public final int maxMillisPerTick;
    public final int chunkTimeoutTicks;
    public final List<Tier> radiusTiers;
    public final List<Tier> memberTiers;
    public final List<Tier> minionTiers;
    public final Map<String, TemplateCfg> templates = new LinkedHashMap<>();
    public final boolean starterOnce;
    public final List<ItemSpec> starterRepeat;
    public final List<ItemSpec> netherChest;
    public final List<ItemSpec> endChest;
    public final boolean endGateLit;
    public final boolean trustedContainers;
    public final long messageCooldownMs;
    public final Map<IslandFlag, Boolean> flagDefaults = new EnumMap<>(IslandFlag.class);
    public final boolean borderVisual;
    public final boolean borderEnforce;
    public final boolean enterMessages;
    public final boolean voidRescue;
    public final String voidMessage;
    public final boolean respawnHome;
    public final boolean netherPortals;
    public final boolean endPortals;
    public final boolean shortcuts;
    public final int shortcutCooldownSeconds;
    public final int inviteExpirySeconds;
    public final int maxPendingInvites;
    public final int resetCooldownMinutes;
    public final int confirmSeconds;
    public final boolean minionUpgrade;
    public final int buildRetries;
    public final int retryDelayTicks;
    public final boolean visitorPickup;
    public final boolean visitorDrop;
    public final int joinCheckTicks;
    public final int createCooldownMinutes;
    public final double levelDivisor;
    public final int levelAutoMinutes;
    public final int levelManualCooldownSeconds;
    public final int levelChunksPerTick;
    public final double levelDefaultWeight;
    public final Map<Material, Double> levelWeights = new LinkedHashMap<>();
    public final int warpNameMax;
    public final int warpCooldownSeconds;
    public final int visitCooldownSeconds;

    public IslandConfig(ConfigurationSection c, Logger log) {
        int cell = Math.max(64, c.getInt("grid.cell-size", 400));
        this.cellSize = cell;
        this.islandY = c.getInt("grid.island-y", 64);
        this.edgeMargin = Math.max(2, c.getInt("grid.edge-margin", 16));
        for (String key : List.of("hub", "world", "nether", "end")) {
            String base = "worlds." + key + ".";
            Map<String, Object> rules = new LinkedHashMap<>();
            ConfigurationSection gr = c.getConfigurationSection("worlds." + key + ".gamerules");
            if (gr != null) {
                for (String rule : gr.getKeys(false)) {
                    rules.put(rule, gr.get(rule));
                }
            }
            worlds.put(key, new WorldCfg(c.getString(base + "time", "CYCLE"), c.getDouble(base + "border", 60000000),
                c.getBoolean(base + "weather-clear", false), rules));
        }
        this.hubEnabled = c.getBoolean("hub.enabled", true);
        this.hubY = c.getInt("hub.y", 100);
        this.hubMaxBlocks = Math.max(500, c.getInt("hub.max-blocks", 45000));
        this.hubInteract = c.getBoolean("hub.allow-interact", true);
        this.blocksPerTick = Math.max(50, c.getInt("generation.blocks-per-tick", 3000));
        this.maxMillisPerTick = Math.max(2, c.getInt("generation.max-millis-per-tick", 20));
        this.chunkTimeoutTicks = Math.max(40, c.getInt("generation.chunk-timeout-ticks", 600));

        int maxRadius = cell / 2 - edgeMargin;
        this.radiusTiers = tiers(c, "upgrades.radius", "radius", List.of(new Tier(30, 0)), maxRadius, log);
        this.memberTiers = tiers(c, "upgrades.members", "slots", List.of(new Tier(3, 0)), 1000, log);
        this.minionTiers = tiers(c, "upgrades.minions", "slots", List.of(new Tier(0, 0)), 1000, log);

        ConfigurationSection templateSection = c.getConfigurationSection("templates");
        if (templateSection != null) {
            for (String id : templateSection.getKeys(false)) {
                ConfigurationSection t = templateSection.getConfigurationSection(id);
                if (t == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(t.getString("icon", "GRASS_BLOCK"));
                templates.put(id.toLowerCase(Locale.ROOT), new TemplateCfg(id.toLowerCase(Locale.ROOT),
                    t.getBoolean("enabled", true), t.getString("name", id), icon == null ? Material.GRASS_BLOCK : icon,
                    t.getString("biome", ""), t.getStringList("description"), items(t.getStringList("chest"), log)));
            }
        }
        this.starterOnce = c.getBoolean("starter-chest.once-per-player", true);
        this.starterRepeat = items(c.getStringList("starter-chest.repeat-items"), log);
        this.netherChest = items(c.getStringList("platforms.nether-chest"), log);
        this.endChest = items(c.getStringList("platforms.end-chest"), log);
        this.endGateLit = c.getBoolean("platforms.end-gate-lit", true);
        this.trustedContainers = c.getBoolean("protection.trusted-containers", false);
        this.messageCooldownMs = Math.max(0, c.getLong("protection.message-cooldown-ms", 1500));
        for (IslandFlag flag : IslandFlag.values()) {
            flagDefaults.put(flag, c.getBoolean("flags.defaults." + flag.id(), flag.defaultValue()));
        }
        this.borderVisual = c.getBoolean("border.visual", true);
        this.borderEnforce = c.getBoolean("border.enforce", true);
        this.enterMessages = c.getBoolean("border.enter-messages", true);
        this.voidRescue = c.getBoolean("void.rescue", true);
        this.voidMessage = c.getString("void.message", "You fell into the void and were pulled back to safety.");
        this.respawnHome = !"HUB".equalsIgnoreCase(c.getString("respawn.mode", "HOME"));
        this.netherPortals = c.getBoolean("portals.nether", true);
        this.endPortals = c.getBoolean("portals.end", true);
        this.shortcuts = c.getBoolean("portals.shortcuts", true);
        this.shortcutCooldownSeconds = Math.max(0, c.getInt("portals.shortcut-cooldown-seconds", 10));
        this.inviteExpirySeconds = Math.max(5, c.getInt("invites.expire-seconds", 120));
        this.maxPendingInvites = Math.max(1, c.getInt("invites.max-pending-per-player", 5));
        this.resetCooldownMinutes = Math.max(0, c.getInt("reset.cooldown-minutes", 60));
        this.confirmSeconds = Math.max(5, c.getInt("reset.confirm-seconds", 30));
        this.minionUpgrade = c.getBoolean("upgrades.minions-enabled", false);
        this.buildRetries = Math.max(0, c.getInt("generation.retries", 3));
        this.retryDelayTicks = Math.max(20, c.getInt("generation.retry-delay-ticks", 100));
        this.visitorPickup = c.getBoolean("protection.visitor-pickup", false);
        this.visitorDrop = c.getBoolean("protection.visitor-drop", false);
        this.joinCheckTicks = Math.max(20, c.getInt("protection.join-check-delay-ticks", 120));
        this.createCooldownMinutes = Math.max(0, c.getInt("create.cooldown-minutes", 0));
        this.levelDivisor = Math.max(0.0001, c.getDouble("level.divisor", 100.0));
        this.levelAutoMinutes = Math.max(0, c.getInt("level.auto-interval-minutes", 30));
        this.levelManualCooldownSeconds = Math.max(0, c.getInt("level.manual-cooldown-seconds", 300));
        this.levelChunksPerTick = Math.max(1, c.getInt("level.chunks-per-tick", 2));
        this.levelDefaultWeight = Math.max(0, c.getDouble("level.default-weight", 0.0));
        ConfigurationSection weights = c.getConfigurationSection("level.weights");
        if (weights != null) {
            for (String key : weights.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    log.warning("island.yml: unknown material in level.weights: " + key);
                } else {
                    levelWeights.put(material, Math.max(0, weights.getDouble(key)));
                }
            }
        }
        this.warpNameMax = Math.max(4, c.getInt("warps.max-name-length", 24));
        this.warpCooldownSeconds = Math.max(0, c.getInt("warps.cooldown-seconds", 5));
        this.visitCooldownSeconds = Math.max(0, c.getInt("commands.visit-cooldown-seconds", 3));
    }

    /** Largest border radius that still leaves a gap between two neighbouring islands. */
    public int maxRadius() {
        return cellSize / 2 - edgeMargin;
    }

    public int radiusFor(Island island) {
        return radiusTiers.get(Math.min(Math.max(0, island.radiusTier), radiusTiers.size() - 1)).value();
    }

    public int memberSlotsFor(Island island) {
        return memberTiers.get(Math.min(Math.max(0, island.memberTier), memberTiers.size() - 1)).value();
    }

    public int minionSlotsFor(Island island) {
        if (!minionUpgrade) {
            return 0;
        }
        return minionTiers.get(Math.min(Math.max(0, island.minionTier), minionTiers.size() - 1)).value();
    }

    /** Enabled templates in menu order. */
    public List<TemplateCfg> enabledTemplates() {
        List<TemplateCfg> list = new ArrayList<>();
        for (TemplateCfg template : templates.values()) {
            if (template.enabled()) {
                list.add(template);
            }
        }
        return list;
    }

    private static List<Tier> tiers(ConfigurationSection c, String path, String valueKey, List<Tier> fallback,
            int cap, Logger log) {
        List<Tier> out = new ArrayList<>();
        for (Map<?, ?> map : c.getMapList(path)) {
            Object value = map.get(valueKey);
            Object cost = map.get("cost");
            if (!(value instanceof Number v)) {
                log.warning("island.yml: bad tier in " + path);
                continue;
            }
            int clamped = Math.min(v.intValue(), cap);
            if (clamped != v.intValue()) {
                log.warning("island.yml: " + path + " value " + v.intValue() + " clamped to " + cap
                    + " (cell size minus margin)");
            }
            if (!out.isEmpty() && clamped <= out.get(out.size() - 1).value()) {
                continue;
            }
            out.add(new Tier(clamped, cost instanceof Number n ? Math.max(0, n.doubleValue()) : 0));
        }
        if (out.isEmpty()) {
            out.addAll(fallback);
        }
        // the first tier is always free
        Tier first = out.get(0);
        if (first.cost() != 0) {
            out.set(0, new Tier(first.value(), 0));
        }
        return List.copyOf(out);
    }

    /** Parses {@code MATERIAL} or {@code MATERIAL:amount} entries; bad ones are logged and skipped. */
    public static List<ItemSpec> items(List<String> raw, Logger log) {
        List<ItemSpec> out = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.trim().split(":");
            Material material = Material.matchMaterial(parts[0].trim());
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    log.warning("island.yml: bad amount in '" + entry + "'");
                    continue;
                }
            }
            if (material == null || material.isAir() || !material.isItem() || amount < 1) {
                log.warning("island.yml: skipping item '" + entry + "'");
                continue;
            }
            out.add(new ItemSpec(material, Math.min(amount, material.getMaxStackSize())));
        }
        return List.copyOf(out);
    }
}
