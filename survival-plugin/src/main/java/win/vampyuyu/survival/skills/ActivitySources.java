package win.vampyuyu.survival.skills;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Core;
import org.bukkit.Keyed;

/**
 * Experience and collections from everything that is not block breaking: mob kills (combat, sea creatures),
 * fishing, enchanting, brewing and crafting.
 *
 * <p>Other features can steer mob kills with entity tags in the {@code vampyuyu} namespace:
 * {@code no_skill_xp} (byte) refuses experience and collections for that mob, {@code skill_xp} (byte) allows a mob that
 * would be refused because of its spawn reason (plugin spawns have reason CUSTOM), {@code sea_creature} (byte) turns
 * the kill into Fishing experience.
 */
final class ActivitySources implements Listener {
    static final NamespacedKey NO_XP = new NamespacedKey("vampyuyu", "no_skill_xp");
    static final NamespacedKey FORCE_XP = new NamespacedKey("vampyuyu", "skill_xp");
    static final NamespacedKey SEA_CREATURE = new NamespacedKey("vampyuyu", "sea_creature");
    private static final long BREW_OWNER_TTL_MS = 30 * 60_000L;

    private record BrewKey(UUID world, long block) {}

    private record BrewOwner(UUID player, long at) {}

    private final Core core;
    private final SkillsService service;
    private volatile AntiFarm mobAntiFarm;
    private final Map<BrewKey, BrewOwner> brewOwners = new HashMap<>();

    ActivitySources(Core core, SkillsService service) {
        this.core = core;
        this.service = service;
        this.mobAntiFarm = new AntiFarm(service.config().mobAntiFarm, System::currentTimeMillis);
    }

    void reload() {
        this.mobAntiFarm = new AntiFarm(service.config().mobAntiFarm, System::currentTimeMillis);
    }

    private boolean allowed(org.bukkit.World world) {
        return world != null && service.config().worldAllowed(world.getName());
    }

    private static boolean earns(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mobAntiFarm.forget(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- combat

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (service.config().blockedSpawnReasons.contains(event.getSpawnReason())
                && !entity.getPersistentDataContainer().has(FORCE_XP, PersistentDataType.BYTE)) {
            entity.getPersistentDataContainer().set(NO_XP, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        SkillsConfig cfg = service.config();
        if (killer == null || entity instanceof Player || entity instanceof ArmorStand || !earns(killer)
                || !allowed(entity.getWorld()) || cfg.blockedEntities.contains(entity.getType())) {
            return;
        }
        var pdc = entity.getPersistentDataContainer();
        if (pdc.has(NO_XP, PersistentDataType.BYTE)) {
            return;
        }
        boolean sea = pdc.has(SEA_CREATURE, PersistentDataType.BYTE);
        double xp;
        Skill skill;
        if (sea) {
            skill = Skill.FISHING;
            xp = cfg.seaCreatureXp.getOrDefault(entity.getType(), cfg.defaultSeaCreatureXp);
        } else {
            skill = Skill.COMBAT;
            Double configured = cfg.mobXp.get(entity.getType());
            if (configured != null) {
                xp = configured;
            } else if (entity instanceof Enemy) {
                AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
                xp = (health == null ? entity.getHealth() : health.getValue()) * cfg.healthFactor;
            } else {
                xp = 0;
            }
        }
        xp = Math.min(xp, cfg.maxPerKill);
        if (xp > 0) {
            var at = entity.getLocation();
            double multiplier = mobAntiFarm.next(killer.getUniqueId(), entity.getWorld().getName(), at.getBlockX(),
                at.getBlockY(), at.getBlockZ());
            service.grant(killer, skill, xp * multiplier,
                (sea ? "sea:" : "combat:") + entity.getType().name().toLowerCase(Locale.ROOT));
        }
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir() || core.items().isCustom(drop)) {
                continue;
            }
            service.addCollection(killer, drop.getType(), drop.getAmount());
        }
    }

    // ---------------------------------------------------------------- fishing

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        Player player = event.getPlayer();
        if (!earns(player) || !allowed(player.getWorld())) {
            return;
        }
        ItemStack stack = caught.getItemStack();
        if (stack.getType().isAir()) {
            return;
        }
        SkillsConfig cfg = service.config();
        double xp = cfg.catchXp.getOrDefault(stack.getType(), cfg.defaultCatchXp);
        service.grant(player, Skill.FISHING, xp, "fishing:" + stack.getType().name().toLowerCase(Locale.ROOT));
        service.addCollection(player, stack.getType(), stack.getAmount());
    }

    // ---------------------------------------------------------------- enchanting

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!earns(player) || !allowed(player.getWorld())) {
            return;
        }
        SkillsConfig cfg = service.config();
        double levels = 0;
        for (Map.Entry<Enchantment, Integer> e : event.getEnchantsToAdd().entrySet()) {
            levels += e.getValue();
        }
        service.grant(player, Skill.ENCHANTING, event.getExpLevelCost() * cfg.enchantPerCostLevel
            + levels * cfg.enchantPerLevel, "enchanting:table");
    }

    // ---------------------------------------------------------------- alchemy

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING || event.getInventory().getLocation() == null
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        rememberBrewer(event.getInventory().getLocation().getBlock(), player.getUniqueId());
    }

    /** The brewing stand's experience goes to the last player who opened it (for 30 minutes). */
    void rememberBrewer(Block stand, UUID player) {
        long now = System.currentTimeMillis();
        if (brewOwners.size() > 2000) {
            brewOwners.values().removeIf(owner -> now - owner.at() > BREW_OWNER_TTL_MS);
        }
        brewOwners.put(new BrewKey(stand.getWorld().getUID(), stand.getBlockKey()), new BrewOwner(player, now));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block stand = event.getBlock();
        BrewOwner owner = brewOwners.get(new BrewKey(stand.getWorld().getUID(), stand.getBlockKey()));
        if (owner == null || System.currentTimeMillis() - owner.at() > BREW_OWNER_TTL_MS) {
            return;
        }
        Player player = Bukkit.getPlayer(owner.player());
        if (player == null || !earns(player) || !allowed(stand.getWorld())) {
            return;
        }
        BrewerInventory contents = event.getContents();
        List<ItemStack> results = event.getResults();
        int changed = 0;
        for (int slot = 0; slot < 3 && slot < results.size(); slot++) {
            ItemStack before = contents.getItem(slot);
            ItemStack after = results.get(slot);
            if (before != null && after != null && !after.getType().isAir() && !after.isSimilar(before)) {
                changed++;
            }
        }
        if (changed == 0) {
            return;
        }
        ItemStack ingredient = contents.getIngredient();
        SkillsConfig cfg = service.config();
        double per = ingredient == null ? cfg.defaultBrewXp
            : cfg.brewIngredientXp.getOrDefault(ingredient.getType(), cfg.defaultBrewXp);
        service.grant(player, Skill.ALCHEMY, per * changed, "alchemy:brew");
    }

    // ---------------------------------------------------------------- carpentry (vanilla crafting)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !earns(player) || !allowed(player.getWorld())) {
            return;
        }
        if (event.getRecipe() instanceof Keyed keyed && keyed.getKey().getKey().startsWith(SkillsService.RECIPE_PREFIX)) {
            return; // our own recipes are handled by SkillRecipes
        }
        ItemStack result = event.getRecipe().getResult();
        SkillsConfig cfg = service.config();
        double per = cfg.craftXp.getOrDefault(result.getType(), cfg.defaultCraftXp);
        if (per <= 0 || result.getType().isAir()) {
            return;
        }
        Material type = result.getType();
        int amount = Math.max(1, result.getAmount());
        int before = countOf(player, type);
        core.scheduler().nextTick(() -> {
            if (!player.isOnline()) {
                return;
            }
            int crafts = Math.max(0, (countOf(player, type) - before) / amount);
            crafts = Math.min(crafts, cfg.craftXpMaxBatch);
            if (crafts > 0) {
                service.grant(player, Skill.CARPENTRY, per * crafts, "carpentry:" + type.name().toLowerCase(Locale.ROOT));
            }
        });
    }

    private int countOf(Player player, Material type) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == type && !core.items().isCustom(stack)) {
                total += stack.getAmount();
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType() == type && !core.items().isCustom(cursor)) {
            total += cursor.getAmount();
        }
        return total;
    }
}
