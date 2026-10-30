package win.vampyuyu.survival.items.stats;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.api.event.StatsRecalculateEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.Keys;

/**
 * The stats engine and {@link StatsApi}. A stat is its base value plus the sum of the modifier sources: the ones
 * other features register through {@link #addModifier} and the engine's own ("held", "armor" and every
 * {@link StatProvider}: accessories, pet, set bonus). Totals are cached per player; they are recomputed when a
 * player's gear signature changes (checked for dirty players every few ticks and for everyone every
 * {@code scan-ticks}), after login, respawn and world change, and when {@link #recompute} is called.
 *
 * <p>Rule for item stats: every custom item in an armor slot, and the main hand item when its type label is a
 * "held type" (weapon / tool), contributes {@code Items.statsOf} (definition + per-item bonuses). Items whose id is
 * in {@code stats.ignore-item-ids} (default: {@code wings}) are skipped because their owner feature registers its own
 * modifier, so nothing is counted twice.
 */
public final class StatsEngine implements StatsApi, Listener {
    private final Core core;
    private volatile ItemsSettings settings;
    private final Gear gear;
    private final AttributeApplier attributes;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final List<StatProvider> providers = new CopyOnWriteArrayList<>();
    private final List<Scheduler.Handle> tasks = new ArrayList<>();
    /** Health and mana ratios remembered when a player's attributes are removed (logout, leaving a Skyblock world). */
    private record Saved(double health, double mana, double overflow) {}

    private final Map<UUID, Saved> saved = new ConcurrentHashMap<>();
    private int tickCounter;
    private long lastSecond = System.currentTimeMillis();

    public StatsEngine(Core core, ItemsSettings settings, Gear gear) {
        this.core = core;
        this.settings = settings;
        this.gear = gear;
        this.attributes = new AttributeApplier(core.getPlugin().getLogger());
    }

    public void settings(ItemsSettings settings) { this.settings = settings; }

    public ItemsSettings settings() { return settings; }

    public void addProvider(StatProvider provider) { providers.add(provider); }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        tasks.add(core.scheduler().repeating(1, settings.flushTicks, this::flush));
        tasks.add(core.scheduler().repeating(20, 20, this::everySecond));
        for (Player player : Bukkit.getOnlinePlayers()) {
            state(player);
            markDirty(player.getUniqueId(), true);
        }
    }

    public void stop() {
        tasks.forEach(Scheduler.Handle::cancel);
        tasks.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            attributes.clear(player);
        }
        states.clear();
        dirty.clear();
        saved.clear();
    }

    // ---------------------------------------------------------------- state access

    /** The state of an online player, created on first use. */
    public PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), PlayerState::new);
    }

    /** The state if the player has one. */
    public Optional<PlayerState> existing(UUID id) {
        return Optional.ofNullable(states.get(id));
    }

    /** True when the player is in a Skyblock world (or the restriction is off). */
    public boolean active(@Nullable Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        return !settings.skyblockOnly || SkyblockWorlds.isSkyblock(player.getWorld().getName());
    }

    /** Level of an enchant summed over the worn / held gear of the player. */
    public int enchantLevel(Player player, String enchantId) {
        PlayerState st = states.get(player.getUniqueId());
        return st == null ? 0 : st.enchantLevel(enchantId);
    }

    public void markDirty(UUID id, boolean force) {
        if (force) {
            PlayerState st = states.get(id);
            if (st != null) {
                st.signature(Integer.MIN_VALUE);
            }
        }
        dirty.add(id);
    }

    // ---------------------------------------------------------------- StatsApi

    @Override
    public double stat(Player player, Stat stat) {
        PlayerState st = states.get(player.getUniqueId());
        if (st == null) {
            if (!Bukkit.isPrimaryThread()) {
                return stat.base();
            }
            st = state(player);
            recompute(player);
        }
        return st.stat(stat);
    }

    @Override
    public void addModifier(UUID player, String source, Stat stat, double amount) {
        states.computeIfAbsent(player, PlayerState::new).setModifier(source, stat, amount);
        dirty.add(player);
    }

    @Override
    public void removeModifiers(UUID player, String source) {
        PlayerState st = states.get(player);
        if (st != null) {
            st.removeSource(source);
            dirty.add(player);
        }
    }

    @Override
    public void recompute(Player player) {
        PlayerState st = state(player);
        compute(player, st);
    }

    // ---------------------------------------------------------------- computation

    /** Pure: a drill (an item with a fuel tank) with an empty tank gives no stats at all. */
    public static boolean outOfFuel(GearInfo info, long fuel) {
        return info.def().pdcLongs().containsKey(Keys.FUEL_MAX) && fuel <= 0;
    }

    private boolean counts(GearInfo info) {
        return !settings.ignoredItemIds.contains(info.id());
    }

    private boolean heldType(GearInfo info) {
        String label = info.def().typeLabel();
        return label != null && settings.heldTypes.contains(label.toUpperCase(Locale.ROOT));
    }

    private int signatureOf(Player player, PlayerState st) {
        int hash = st.modVersion();
        hash = hash * 31 + (active(player) ? 1 : 0);
        ItemStack held = player.getInventory().getItemInMainHand();
        hash = hash * 31 + (held.getType().isAir() ? 0 : held.hashCode());
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            hash = hash * 31 + (piece == null || piece.getType().isAir() ? 0 : piece.hashCode());
        }
        for (StatProvider provider : providers) {
            hash = hash * 31 + provider.signature(player);
        }
        return hash;
    }

    private void compute(Player player, PlayerState st) {
        Map<String, Integer> enchants = new HashMap<>();
        EnumMap<Stat, Double> held = new EnumMap<>(Stat.class);
        EnumMap<Stat, Double> armor = new EnumMap<>(Stat.class);

        ItemStack main = player.getInventory().getItemInMainHand();
        Optional<GearInfo> heldInfo = gear.read(main);
        if (heldInfo.isPresent() && counts(heldInfo.get()) && heldType(heldInfo.get())
                && !outOfFuel(heldInfo.get(), gear.items().getLong(main, Keys.FUEL).orElse(0L))) {
            gear.items().statsOf(main).forEach((stat, v) -> held.merge(stat, v, Double::sum));
            addEnchants(enchants, heldInfo.get());
        }
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            Optional<GearInfo> info = gear.read(piece);
            if (info.isPresent() && counts(info.get())) {
                gear.items().statsOf(piece).forEach((stat, v) -> armor.merge(stat, v, Double::sum));
                addEnchants(enchants, info.get());
            }
        }
        st.replaceSource("held", held);
        st.replaceSource("armor", armor);
        for (StatProvider provider : providers) {
            try {
                st.replaceSource(provider.source(), provider.stats(player));
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().warning("Stat provider " + provider.source() + " failed: " + e);
            }
        }
        st.enchantLevels(Map.copyOf(enchants));

        EnumMap<Stat, Double> totals = PlayerState.baseTotals();
        st.sumModifiers().forEach((stat, v) -> totals.merge(stat, v, Double::sum));
        StatsRecalculateEvent event = new StatsRecalculateEvent(player, totals);
        Bukkit.getPluginManager().callEvent(event);
        EnumMap<Stat, Double> finalTotals = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            double value = event.totals().getOrDefault(stat, stat.base());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                value = stat.base();
            }
            value = clampStat(stat, value);
            finalTotals.put(stat, value);
        }
        st.totals(finalTotals);
        st.signature(signatureOf(player, st));

        if (active(player)) {
            boolean first = !st.everApplied;
            Saved back = first && !player.isDead() ? saved.remove(player.getUniqueId()) : null;
            attributes.apply(player, finalTotals, settings, first, back == null ? -1 : back.health());
            if (back != null) {
                double max = finalTotals.getOrDefault(Stat.INTELLIGENCE, Stat.INTELLIGENCE.base());
                st.mana.load(Math.min(max, back.mana() * max), back.overflow());
            }
            st.everApplied = true;
            st.attributesActive = true;
        } else if (st.attributesActive) {
            remember(player, st);
            attributes.clear(player);
            st.attributesActive = false;
            st.everApplied = false;
        }
    }

    /** Remembers the health and mana ratios before the attributes are removed, so leaving never heals. */
    private void remember(Player player, PlayerState st) {
        if (!st.attributesActive || player.isDead()) {
            return;
        }
        var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double cap = maxHealth == null ? 20 : maxHealth.getValue();
        double intelligence = st.stat(Stat.INTELLIGENCE);
        double mana = st.mana.pristine() || intelligence <= 0 ? 1 : Math.min(1, st.mana.mana() / intelligence);
        if (saved.size() > 10000) {
            saved.clear();
        }
        saved.put(player.getUniqueId(), new Saved(Math.max(0, Math.min(1, player.getHealth() / Math.max(1, cap))),
            mana, st.mana.overflow()));
    }

    /** Test hook: the remembered health ratio of a player, or -1. */
    public double rememberedHealth(UUID id) {
        Saved s = saved.get(id);
        return s == null ? -1 : s.health();
    }

    private double clampStat(Stat stat, double value) {
        return switch (stat) {
            case CRIT_CHANCE -> Math.max(0, Math.min(settings.critCap, value));
            case HEALTH -> Math.max(1, value);
            case SPEED -> Math.max(10, value);
            default -> stat.base() > 0 ? Math.max(0, value) : Math.max(-1000, value);
        };
    }

    private void addEnchants(Map<String, Integer> into, GearInfo info) {
        boolean oneForAll = info.enchants().containsKey("one_for_all");
        info.enchants().forEach((id, level) -> {
            if (!oneForAll || id.equals("one_for_all")) {
                into.merge(id, level, Integer::sum);
            }
        });
    }

    // ---------------------------------------------------------------- scheduled work

    private void flush() {
        tickCounter += settings.flushTicks;
        boolean scanAll = tickCounter >= settings.scanTicks;
        if (scanAll) {
            tickCounter = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (active(player)) {
                    dirty.add(player.getUniqueId());
                }
            }
        }
        if (dirty.isEmpty()) {
            return;
        }
        List<UUID> batch = new ArrayList<>(dirty);
        dirty.removeAll(batch);
        for (UUID id : batch) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            PlayerState st = state(player);
            // Inactive players are only recomputed when forced (world change) so the scan stays cheap.
            if (st.signature() != Integer.MIN_VALUE && signatureOf(player, st) == st.signature()) {
                continue;
            }
            try {
                compute(player, st);
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().warning("Stats recompute failed for " + player.getName() + ": " + e);
            }
        }
    }

    /** Advances the mana pool of a player by {@code seconds} (called by the HUD several times a second). */
    public void tickMana(Player player, double seconds) {
        PlayerState st = state(player);
        st.mana.tick(st.stat(Stat.INTELLIGENCE), seconds, settings.manaRegenPercent, settings.overflowMaxPercent,
            settings.overflowDecayPercent);
    }

    private void everySecond() {
        long now = System.currentTimeMillis();
        double seconds = Math.max(0.2, Math.min(5, (now - lastSecond) / 1000.0));
        lastSecond = now;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!active(player)) {
                continue;
            }
            PlayerState st = state(player);
            if (player.isDead()) {
                continue;
            }
            double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            double health = player.getHealth();
            if (health < max) {
                double percent = settings.regenPercentPerSecond + st.enchantLevel("rejuvenate")
                    * gear.enchants().perLevel("rejuvenate");
                double heal = max * percent / 100.0 * seconds;
                if (heal > 0) {
                    player.setHealth(Math.min(max, health + heal));
                }
            }
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // No reset of everApplied here: the state is created fresh after a quit, and another feature (skills, at LOW)
        // may already have recomputed during this join and consumed the remembered health. Resetting it would make the
        // next recompute a "first" one again with nothing remembered, healing the player to full on every relog.
        state(player);
        markDirty(player.getUniqueId(), true);
        core.scheduler().nextTick(() -> {
            if (player.isOnline()) {
                markDirty(player.getUniqueId(), true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerState leaving = states.get(player.getUniqueId());
        if (leaving != null) {
            remember(player, leaving);
        }
        attributes.clear(player);
        states.remove(player.getUniqueId());
        dirty.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerState st = state(player);
        saved.remove(player.getUniqueId());
        st.everApplied = false;
        st.mana.fill(st.stat(Stat.INTELLIGENCE));
        markDirty(player.getUniqueId(), true);
        core.scheduler().later(2, () -> markDirty(player.getUniqueId(), true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        markDirty(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        dirty.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        dirty.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        dirty.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            dirty.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(PlayerItemBreakEvent event) {
        dirty.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            dirty.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            dirty.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            dirty.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(EntityEquipmentChangedEvent event) {
        if (event.getEntity() instanceof Player player) {
            dirty.add(player.getUniqueId());
        }
    }

    /** Vanilla regeneration is replaced by the percent based one in {@link #everySecond}. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!settings.cancelVanillaRegen || !(event.getEntity() instanceof Player player) || !active(player)) {
            return;
        }
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED || reason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    /** Pure helper for tests and menus: the engine's slot order. */
    public static List<EquipmentSlot> armorSlots() {
        return List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    }
}
