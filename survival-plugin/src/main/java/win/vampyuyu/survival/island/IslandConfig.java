package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import win.vampyuyu.survival.data.Settings;

/** Typed view of the island tuning ({@code island.*} settings and the {@code island_*} tables, see {@link IslandRepo}). Built once at enable. */
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

    public IslandConfig(Settings c, IslandRepo.Rows rows, Logger log) {
        int cell = Math.max(64, c.getInt("grid.cell-size", 400));
        this.cellSize = cell;
        this.islandY = c.getInt("grid.island-y", 64);
        this.edgeMargin = Math.max(2, c.getInt("grid.edge-margin", 16));
        for (String key : List.of("hub", "world", "nether", "end")) {
            IslandRepo.WorldRow row = rows.worlds().get(key);
            worlds.put(key, row == null ? new WorldCfg("CYCLE", 60000000, false, new LinkedHashMap<>())
                : new WorldCfg(row.time(), row.border(), row.weatherClear(), new LinkedHashMap<>(row.gamerules())));
        }
        this.hubEnabled = c.getBoolean("hub.enabled", true);
        this.hubY = c.getInt("hub.y", 100);
        this.hubMaxBlocks = Math.max(500, c.getInt("hub.max-blocks", 45000));
        this.hubInteract = c.getBoolean("hub.allow-interact", true);
        this.blocksPerTick = Math.max(50, c.getInt("generation.blocks-per-tick", 3000));
        this.maxMillisPerTick = Math.max(2, c.getInt("generation.max-millis-per-tick", 20));
        this.chunkTimeoutTicks = Math.max(40, c.getInt("generation.chunk-timeout-ticks", 600));

        int maxRadius = cell / 2 - edgeMargin;
        this.radiusTiers = tiers(rows.tiers("radius"), "upgrades.radius", List.of(new Tier(30, 0)), maxRadius, log);
        this.memberTiers = tiers(rows.tiers("members"), "upgrades.members", List.of(new Tier(3, 0)), 1000, log);

        for (IslandRepo.TemplateRow t : rows.templates()) {
            Material icon = Material.matchMaterial(t.icon());
            String id = t.id().toLowerCase(Locale.ROOT);
            templates.put(id, new TemplateCfg(id, t.enabled(), t.name(), icon == null ? Material.GRASS_BLOCK : icon,
                t.biome(), t.description(), items(t.items(), log)));
        }
        this.starterOnce = c.getBoolean("starter-chest.once-per-player", true);
        this.starterRepeat = items(rows.chest("starter-repeat"), log);
        this.netherChest = items(rows.chest("nether"), log);
        this.endChest = items(rows.chest("end"), log);
        this.endGateLit = c.getBoolean("platforms.end-gate-lit", true);
        this.trustedContainers = c.getBoolean("protection.trusted-containers", false);
        this.messageCooldownMs = Math.max(0, c.getLong("protection.message-cooldown-ms", 1500));
        for (IslandFlag flag : IslandFlag.values()) {
            flagDefaults.put(flag, rows.flags().getOrDefault(flag.id(), flag.defaultValue()));
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
        for (Map.Entry<String, Double> weight : rows.weights().entrySet()) {
            Material material = Material.matchMaterial(weight.getKey());
            if (material == null) {
                log.warning("island level weights: unknown material " + weight.getKey());
            } else {
                levelWeights.put(material, Math.max(0, weight.getValue()));
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

    private static List<Tier> tiers(List<IslandRepo.TierRow> raw, String path, List<Tier> fallback, int cap, Logger log) {
        List<Tier> out = new ArrayList<>();
        for (IslandRepo.TierRow row : raw) {
            int clamped = Math.min(row.value(), cap);
            if (clamped != row.value()) {
                log.warning("island " + path + " value " + row.value() + " clamped to " + cap
                    + " (cell size minus margin)");
            }
            if (!out.isEmpty() && clamped <= out.get(out.size() - 1).value()) {
                continue;
            }
            out.add(new Tier(clamped, Math.max(0, row.cost())));
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

    /** Checks the materials of a chest list; bad ones are logged and skipped, the amount is capped to one stack. */
    public static List<ItemSpec> items(List<IslandRepo.ItemRow> raw, Logger log) {
        List<ItemSpec> out = new ArrayList<>();
        for (IslandRepo.ItemRow entry : raw) {
            Material material = Material.matchMaterial(entry.material().trim());
            if (material == null || material.isAir() || !material.isItem() || entry.amount() < 1) {
                log.warning("island chest: skipping item '" + entry.material() + ":" + entry.amount() + "'");
                continue;
            }
            out.add(new ItemSpec(material, Math.min(entry.amount(), material.getMaxStackSize())));
        }
        return List.copyOf(out);
    }
}
