package win.vampyuyu.survival.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.api.GeneratorApi;
import win.vampyuyu.survival.api.ResonanceApi;
import win.vampyuyu.survival.api.StationApi;
import win.vampyuyu.survival.api.Trade;
import win.vampyuyu.survival.api.TradeApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.station.StationConfig.Brew;
import win.vampyuyu.survival.station.StationConfig.Contract;
import win.vampyuyu.survival.station.StationConfig.Product;

/** The Alembic, Ranch, Crucible and contract actions of a player, around {@link StationRules}; also the published {@link StationApi}. */
public final class StationService implements StationApi {
    static final NamespacedKey RECIPE = new NamespacedKey("vampyuyu", StationItems.RECIPE_KEY);

    private record Oil(Element element, long until) {}

    private final Core core;
    private volatile StationConfig config;
    private final Map<UUID, Oil> oils = new ConcurrentHashMap<>();
    private final java.util.Random random = new java.util.Random();

    public StationService(Core core, StationConfig config) {
        this.core = core;
        this.config = config;
    }

    public StationConfig config() { return config; }

    public void reload(StationConfig fresh) { config = fresh; }

    StationData data(UUID id) {
        return core.profiles().section(id, "stations", StationData.class, StationData::new);
    }

    void dirty(UUID id) {
        core.profiles().markDirty(id);
    }

    /** The generator tier (1 to 5): the stations grow with the player's progression. */
    int tier(UUID id) {
        return Math.max(1, core.service(GeneratorApi.class).map(g -> g.tier(id)).orElse(1));
    }

    /** The Warden's yield multiplier (1 without the trade, see {@link TradeApi}). */
    double yield(UUID id) {
        return core.service(TradeApi.class).map(t -> t.yieldFactor(id)).orElse(1.0);
    }

    private void wardenXp(UUID id, int amount) {
        core.service(TradeApi.class).ifPresent(t -> t.addXp(id, Trade.WARDEN, amount));
    }

    // ---------------------------------------------------------------- items

    /** True when the stack is the item named by {@code key}: a Material name (vanilla item) or {@code custom:<id>}. */
    boolean matches(String key, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Optional<String> id = core.items().idOf(stack);
        if (key.startsWith("custom:")) {
            return id.isPresent() && id.get().equals(key.substring(7));
        }
        Material material = Material.matchMaterial(key);
        return id.isEmpty() && material != null && stack.getType() == material;
    }

    int held(Player player, String key) {
        int have = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(key, stack)) {
                have += stack.getAmount();
            }
        }
        return have;
    }

    void take(Player player, String key, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            if (matches(key, contents[i])) {
                int used = Math.min(left, contents[i].getAmount());
                left -= used;
                contents[i].setAmount(contents[i].getAmount() - used);
                if (contents[i].getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    /** A stack for an item key, null when the key names nothing on this server. */
    ItemStack stack(String key, int amount) {
        if (key.startsWith("custom:")) {
            String id = key.substring(7);
            return core.items().def(id).isPresent() ? core.items().create(id, amount) : null;
        }
        Material material = Material.matchMaterial(key);
        return material == null || material.isAir() ? null : new ItemStack(material, amount);
    }

    /** Gives an item (in stacks), dropping what does not fit at the player's feet. */
    void give(Player player, String key, int amount) {
        ItemStack template = stack(key, 1);
        if (template == null) {
            return;
        }
        int max = Math.max(1, template.getMaxStackSize());
        for (int left = amount; left > 0; left -= max) {
            ItemStack part = stack(key, Math.min(left, max));
            for (ItemStack rest : player.getInventory().addItem(part).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
        }
    }

    /** Display name of an item key. */
    String label(String key) {
        if (key.startsWith("custom:")) {
            return core.items().def(key.substring(7)).map(CustomItemDef::name).orElse(key.substring(7));
        }
        String name = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    boolean canAfford(Player player, List<StationConfig.Ingredient> cost) {
        for (StationConfig.Ingredient i : cost) {
            if (held(player, i.item()) < i.amount()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- Alembic

    private int seconds(String recipe) {
        Brew b = config.brews().get(recipe);
        return b == null ? 1 : b.seconds();
    }

    /** Moves the player's queue to now; tells them when brews finished while they were away. */
    int advance(Player player) {
        StationData d = data(player.getUniqueId());
        int done = StationRules.advance(d, System.currentTimeMillis(), config.alembicSlots, this::seconds);
        if (done > 0) {
            dirty(player.getUniqueId());
        }
        return done;
    }

    /** Puts a brew in the queue and takes its ingredients. Returns the English refusal, or null when it was queued. */
    String enqueue(Player player, String recipeId) {
        Brew brew = config.brews().get(recipeId);
        if (brew == null) {
            return "That recipe does not exist.";
        }
        UUID id = player.getUniqueId();
        if (tier(id) < brew.minTier()) {
            return "Your generator must reach tier " + brew.minTier() + " before you can brew this.";
        }
        advance(player);
        StationData d = data(id);
        if (d.jobs.size() >= config.alembicQueue) {
            return "The queue is full (" + config.alembicQueue + " brews). Collect or wait.";
        }
        if (!canAfford(player, brew.cost())) {
            return "You are missing ingredients.";
        }
        for (StationConfig.Ingredient i : brew.cost()) {
            take(player, i.item(), i.amount());
        }
        d.jobs.add(new StationData.Job(brew.id()));
        StationRules.advance(d, System.currentTimeMillis(), config.alembicSlots, this::seconds);
        dirty(id);
        return null;
    }

    /** The flask of a brew, with its recipe in the PDC. */
    ItemStack flask(Brew brew) {
        ItemStack stack = core.items().create(StationItems.FLASK);
        ItemMeta meta = stack.getItemMeta();
        String color = switch (brew.family()) {
            case "tonic" -> "<aqua>";
            case "oil" -> "<gold>";
            default -> "<light_purple>";
        };
        meta.displayName(Msg.component(color + brew.name()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.component("<gray>" + describe(brew)).decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.component("<dark_gray>Drink it to use it.").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(RECIPE, PersistentDataType.STRING, brew.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /** One line saying what a brew does. */
    String describe(Brew brew) {
        String time = Msg.duration(brew.durationSeconds() * 1000L);
        if ("oil".equals(brew.effect())) {
            Element e = Element.parse(brew.element());
            return "Your weapons hit with " + (e == null ? brew.element() : e.displayName()) + " for " + time + ".";
        }
        return label(brew.effect()) + (brew.amplifier() > 0 ? " " + (brew.amplifier() + 1) : "") + " for " + time + ".";
    }

    /** Gives every finished brew to the player. Returns how many. */
    int collect(Player player) {
        advance(player);
        StationData d = data(player.getUniqueId());
        int n = 0;
        boolean had = !d.ready.isEmpty();
        for (String recipe : new ArrayList<>(d.ready)) {
            Brew brew = config.brews().get(recipe);
            if (brew == null) {
                continue;
            }
            // a Warden's bonus is a chance of one more flask
            int flasks = random.nextDouble() < yield(player.getUniqueId()) - 1 ? 2 : 1;
            for (int f = 0; f < flasks; f++) {
                for (ItemStack rest : player.getInventory().addItem(flask(brew)).values()) {
                    player.getWorld().dropItem(player.getLocation(), rest);
                }
            }
            n++;
        }
        d.ready.clear();
        if (n > 0) {
            wardenXp(player.getUniqueId(), n);
        }
        if (had) {
            dirty(player.getUniqueId());
        }
        return n;
    }

    /** Applies a drunk flask. */
    void drink(Player player, String recipeId) {
        Brew brew = config.brews().get(recipeId);
        if (brew == null) {
            core.msg().warn(player, "The liquid has gone flat.");
            return;
        }
        if ("oil".equals(brew.effect())) {
            Element e = Element.parse(brew.element());
            if (e != null) {
                oils.put(player.getUniqueId(), new Oil(e, System.currentTimeMillis() + brew.durationSeconds() * 1000L));
                core.msg().ok(player, brew.name() + ": " + describe(brew));
            }
            return;
        }
        PotionEffectType type = effectType(brew.effect());
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, brew.durationSeconds() * 20, brew.amplifier()));
            core.msg().ok(player, brew.name() + ": " + describe(brew));
        }
    }

    static PotionEffectType effectType(String key) {
        return switch (key) {
            case "resistance" -> PotionEffectType.RESISTANCE;
            case "fire_resistance" -> PotionEffectType.FIRE_RESISTANCE;
            case "water_breathing" -> PotionEffectType.WATER_BREATHING;
            case "regeneration" -> PotionEffectType.REGENERATION;
            case "haste" -> PotionEffectType.HASTE;
            case "night_vision" -> PotionEffectType.NIGHT_VISION;
            case "speed" -> PotionEffectType.SPEED;
            case "absorption" -> PotionEffectType.ABSORPTION;
            case "strength" -> PotionEffectType.STRENGTH;
            case "jump_boost" -> PotionEffectType.JUMP_BOOST;
            case "slow_falling" -> PotionEffectType.SLOW_FALLING;
            default -> null;
        };
    }

    @Override
    public Optional<Element> oilElement(UUID player) {
        Oil oil = oils.get(player);
        if (oil == null) {
            return Optional.empty();
        }
        if (oil.until() <= System.currentTimeMillis()) {
            oils.remove(player, oil);
            return Optional.empty();
        }
        return Optional.of(oil.element());
    }

    /** Seconds of oil left, 0 when none. */
    long oilSecondsLeft(UUID player) {
        Oil oil = oils.get(player);
        return oil == null ? 0 : Math.max(0, (oil.until() - System.currentTimeMillis()) / 1000);
    }

    void forget(UUID player) {
        oils.remove(player);
    }

    // ---------------------------------------------------------------- Ranch

    /** Starts the Ranch clock when the player puts their first Ranch down. */
    void startRanch(UUID id) {
        StationData d = data(id);
        if (d.ranchSince <= 0) {
            d.ranchSince = System.currentTimeMillis();
            dirty(id);
        }
    }

    /** What waits at the Ranch, by product id (only products the tier unlocks). */
    Map<Product, Integer> ranchStock(UUID id, long now) {
        StationData d = data(id);
        int tier = tier(id);
        Map<Product, Integer> out = new java.util.LinkedHashMap<>();
        for (Product p : config.products().values()) {
            int units = StationRules.ranchUnits(d.ranchSince, now, p, tier, config.tierBonus, config.storageHours);
            out.put(p, (int) Math.floor(units * yield(id) + 1e-9));
        }
        return out;
    }

    /** Hands out what the Ranch made and restarts its clock. Returns the number of items given. */
    int collectRanch(Player player) {
        long now = System.currentTimeMillis();
        int total = 0;
        for (Map.Entry<Product, Integer> e : ranchStock(player.getUniqueId(), now).entrySet()) {
            if (e.getValue() > 0) {
                give(player, e.getKey().item(), e.getValue());
                total += e.getValue();
            }
        }
        if (total > 0) {
            wardenXp(player.getUniqueId(), 2);
            data(player.getUniqueId()).ranchSince = now;
            dirty(player.getUniqueId());
        }
        return total;
    }

    // ---------------------------------------------------------------- Crucible

    /** What melting this item would give back, empty when the Forge cannot make it. */
    Map<String, Integer> meltDown(String itemId) {
        return StationRules.refund(config.forgeCost(itemId), config.returnRate, config.components());
    }

    /** Melts one item of the main hand. Returns the English refusal, or null when it worked. */
    String melt(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<String> id = core.items().idOf(hand);
        if (id.isEmpty()) {
            return "Hold the item you want to melt down.";
        }
        Map<String, Integer> back = meltDown(id.get());
        if (back.isEmpty()) {
            return "The Crucible has nothing to take from that item.";
        }
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        for (Map.Entry<String, Integer> e : back.entrySet()) {
            give(player, e.getKey(), e.getValue());
        }
        return null;
    }

    /** Duplicate boss components the player holds: item id to the copies beyond the first. */
    Map<String, Integer> duplicates(Player player) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        Optional<ResonanceApi> res = core.service(ResonanceApi.class);
        if (res.isEmpty()) {
            return out;
        }
        for (String component : config.components()) {
            int have = held(player, "custom:" + component);
            if (have > 1 && res.get().foundComponent(player.getUniqueId(), component)) {
                out.put(component, have - 1);
            }
        }
        return out;
    }

    /** Turns every duplicate component (all but one copy) into Memory Shards. Returns the Shards paid. */
    long convertDuplicates(Player player) {
        long shards = 0;
        for (Map.Entry<String, Integer> e : duplicates(player).entrySet()) {
            take(player, "custom:" + e.getKey(), e.getValue());
            shards += (long) e.getValue() * config.duplicateShards;
        }
        if (shards > 0) {
            final long paid = shards;
            core.service(ResonanceApi.class).ifPresent(r -> r.grantShards(player.getUniqueId(), paid));
        }
        return shards;
    }

    // ---------------------------------------------------------------- Contracts

    /** Today's contracts, rolled over when the UTC day changed. */
    StationData contracts(Player player) {
        UUID id = player.getUniqueId();
        StationData d = data(id);
        if (StationRules.rollDay(d, StationRules.day(System.currentTimeMillis()), id, config.contracts().values(), tier(id),
                config.contractsPerDay)) {
            dirty(id);
        }
        return d;
    }

    /** Counts a kill for the player's contracts. */
    void countKill(Player player, String kind, String target) {
        StationData d = contracts(player);
        for (String id : StationRules.count(d, config.contracts(), kind, target)) {
            Contract c = config.contracts().get(id);
            core.msg().ok(player, "Contract ready: " + c.title() + ". Report to the Contract Hunter (/contracts).");
        }
        dirty(player.getUniqueId());
    }

    /** Progress of a contract for display: kills counted, or items held, capped to the amount. */
    int progress(Player player, StationData d, Contract c) {
        int have = "deliver".equals(c.kind()) ? held(player, c.target()) : d.progress.getOrDefault(c.id(), 0);
        return Math.min(have, c.amount());
    }

    /** Turns a contract in. Returns the English refusal, or null when it paid. */
    String turnIn(Player player, String contractId) {
        StationData d = contracts(player);
        Contract c = config.contracts().get(contractId);
        if (c == null || !d.offered.contains(contractId)) {
            return "That contract is not on your board today.";
        }
        if (d.done.contains(contractId)) {
            return "You already finished that contract today.";
        }
        if (progress(player, d, c) < c.amount()) {
            return "The contract is not finished yet.";
        }
        if ("deliver".equals(c.kind())) {
            take(player, c.target(), c.amount());
        }
        d.done.add(contractId);
        int coins = StationRules.payableCoins(d, c, config.dailyCoinCap);
        if (coins > 0) {
            d.coinsToday += coins;
            core.service(EconomyApi.class).ifPresent(e -> e.add(player.getUniqueId(), coins, "contract:" + c.id()));
        }
        if (c.shards() > 0) {
            core.service(ResonanceApi.class).ifPresent(r -> r.grantShards(player.getUniqueId(), c.shards()));
        }
        if (c.rewardItem() != null && !c.rewardItem().isBlank()) {
            give(player, c.rewardItem(), c.rewardAmount());
        }
        dirty(player.getUniqueId());
        String paid = (coins > 0 ? Msg.coins(coins) : "no coins (daily cap reached)")
            + (c.shards() > 0 ? " and " + c.shards() + " Memory Shards" : "");
        core.msg().ok(player, "Contract done: " + c.title() + ". Paid " + paid + ".");
        return null;
    }
}
