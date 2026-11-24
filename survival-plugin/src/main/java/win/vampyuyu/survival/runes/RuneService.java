package win.vampyuyu.survival.runes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.RuneApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.items.gear.ItemKind;

/**
 * Engraving, scraping and crafting of runes. A stat rune is written into the item's own {@code bonus_<stat>} (so
 * {@code Items.statsOf} counts it like any other bonus) and recorded in the {@code runes} PDC value, which is what
 * the slot count and the effects read.
 */
public final class RuneService implements RuneApi {
    /** Result of an action: shown to the player as is. */
    public record Outcome(boolean ok, String message) {}

    /** One thing a craft spends. */
    public record Cost(String label, Predicate<ItemStack> match, int amount) {}

    private final Core core;
    private volatile RuneConfig config;
    private final Map<UUID, Long> verdantReady = new HashMap<>();
    private final Map<UUID, Long> rewindReady = new HashMap<>();
    private final Map<UUID, Long> undertowReady = new HashMap<>();

    public RuneService(Core core, RuneConfig config) {
        this.core = core;
        this.config = config;
    }

    public RuneConfig config() { return config; }

    public void config(RuneConfig config) { this.config = config; }

    // ---------------------------------------------------------------- reading

    @Override
    public Map<String, Integer> runesOf(ItemStack stack) {
        return RuneRules.parse(core.items().getString(stack, RuneItems.ENGRAVED).orElse(null));
    }

    /** The rune a rune item is, with its rank; empty for any other stack. */
    public Optional<Map.Entry<Rune, Integer>> runeItem(ItemStack stack) {
        Optional<String> id = core.items().getString(stack, RuneItems.RUNE);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return config.rune(id.get()).map(r -> Map.entry(r, core.items().getLong(stack, RuneItems.RANK).orElse(0L).intValue()));
    }

    private static int tier(CustomItemDef def) {
        return def.tier() == null ? 1 : def.tier();
    }

    // ---------------------------------------------------------------- engrave / scrape

    /** Engraves {@code rune} (a rune item) onto {@code target}, modified in place. The caller consumes the rune item. */
    public Outcome engrave(ItemStack target, ItemStack rune) {
        Optional<Map.Entry<Rune, Integer>> source = runeItem(rune);
        Optional<CustomItemDef> def = core.items().idOf(target).flatMap(core.items()::def);
        if (source.isEmpty() || def.isEmpty()) {
            return new Outcome(false, "Nothing happens.");
        }
        ItemKind kind = ItemKind.of(def.get().typeLabel());
        Map<String, Integer> engraved = runesOf(target);
        String problem = RuneRules.problem(config, source.get().getKey(), source.get().getValue(), engraved, kind.weapon(),
            kind.armor(), tier(def.get()));
        if (problem != null) {
            return new Outcome(false, problem);
        }
        Map<String, Integer> next = new java.util.LinkedHashMap<>(engraved);
        next.put(source.get().getKey().id(), source.get().getValue());
        write(target, engraved, next);
        Rune r = source.get().getKey();
        return new Outcome(true, "Engraved " + r.name() + (r.realm() ? "" : " " + Rune.roman(source.get().getValue()))
            + " (" + next.size() + "/" + config.slotsFor(tier(def.get())) + " slots).");
    }

    /** Removes every rune from {@code target}, in place. The runes are lost. */
    public Outcome scrape(ItemStack target) {
        Map<String, Integer> engraved = runesOf(target);
        if (engraved.isEmpty()) {
            return new Outcome(false, "This item carries no rune.");
        }
        write(target, engraved, Map.of());
        return new Outcome(true, "Scraped " + engraved.size() + " rune(s). They crumble to dust.");
    }

    /** Moves the item's stat bonuses from the old runes to the new ones, stores the list and redraws the lore. */
    private void write(ItemStack stack, Map<String, Integer> before, Map<String, Integer> after) {
        Map<Stat, Double> was = RuneRules.bonus(config, before);
        Map<Stat, Double> now = RuneRules.bonus(config, after);
        Set<Stat> stats = EnumSet.noneOf(Stat.class);
        stats.addAll(was.keySet());
        stats.addAll(now.keySet());
        Map<Stat, Double> current = core.items().bonuses(stack);
        for (Stat stat : stats) {
            double delta = now.getOrDefault(stat, 0.0) - was.getOrDefault(stat, 0.0);
            if (delta != 0) {
                core.items().setBonus(stack, stat, Math.max(0, current.getOrDefault(stat, 0.0) + delta));
            }
        }
        core.items().set(stack, RuneItems.ENGRAVED, RuneRules.format(after));
        core.items().refresh(stack);
    }

    /** The lore lines of an item's runes (registered with {@code Items.loreExtra}). */
    public List<Component> lore(ItemStack stack) {
        Map<String, Integer> engraved = runesOf(stack);
        if (engraved.isEmpty()) {
            return List.of();
        }
        int slots = core.items().idOf(stack).flatMap(core.items()::def).map(d -> config.slotsFor(tier(d))).orElse(engraved.size());
        List<Component> out = new ArrayList<>();
        out.add(line(Component.text("Runes (" + engraved.size() + "/" + slots + ")", NamedTextColor.LIGHT_PURPLE)));
        engraved.forEach((id, rank) -> config.rune(id).ifPresent(rune -> {
            String what = rune.realm() ? effectText(rune, config) : "+" + config.value(rank) + " " + rune.stat().displayName();
            out.add(line(Component.text(" ✧ " + rune.name() + (rune.realm() ? "" : " " + Rune.roman(rank)) + ": ", NamedTextColor.GRAY)
                .append(Component.text(what, rune.realm() ? NamedTextColor.GOLD : NamedTextColor.AQUA))));
        }));
        return out;
    }

    /** What a Realm rune does, in a few words (item lore, menu, engraved lines). */
    static String effectText(Rune rune, RuneConfig config) {
        return switch (rune.id()) {
            case "scorch" -> "your Surge sets the target ablaze";
            case "verdant" -> "taking a hit grants Regeneration";
            case "stasis" -> "your Surge slows and freezes the target";
            case "rewind" -> "below " + pct(config.effects().rewindBelow()) + " health, the sands turn back: +"
                + pct(config.effects().rewindHeal()) + " (" + config.effects().rewindCooldownSeconds() + " s)";
            case "blight" -> "your Surge rots the target (Wither)";
            case "tempest" -> "your Surge chains lightning to " + config.effects().tempestChains() + " more foes";
            case "undertow" -> "taking a hit throws your attackers back";
            case "soulbind" -> "each monster you slay gives back " + pct(config.effects().soulbindHeal()) + " health";
            default -> "a Realm effect";
        };
    }

    private static String pct(double share) {
        return Math.round(share * 100) + " %";
    }

    private static Component line(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ---------------------------------------------------------------- crafting

    /** What the craft of {@code rune} at {@code rank} spends (rank is ignored for a Realm rune). */
    public List<Cost> costs(Rune rune, int rank) {
        List<Cost> costs = new ArrayList<>();
        if (rune.realm()) {
            costs.add(item(RuneItems.POWDER, config.realmPowder()));
            costs.add(item(rune.component(), config.realmResource()));
            if (config.realmDiamonds() > 0) {
                costs.add(vanilla(Material.DIAMOND, config.realmDiamonds()));
            }
        } else if (rank == 1) {
            costs.add(item(RuneItems.POWDER, config.powder()));
        } else if (rank == 2) {
            costs.add(item(rune.itemId(1), config.fuse()));
            costs.add(item(rune.component(), 1));
        } else {
            costs.add(item(rune.itemId(2), config.fuse()));
            costs.add(vanilla(Material.DIAMOND, 1));
        }
        return costs;
    }

    private Cost item(String id, int amount) {
        String label = core.items().def(id).map(CustomItemDef::name).orElse(id);
        return new Cost(label, stack -> core.items().is(stack, id), amount);
    }

    private Cost vanilla(Material material, int amount) {
        String label = material == Material.DIAMOND ? "Diamond" : material.name();
        return new Cost(label, stack -> stack.getType() == material && !core.items().isCustom(stack), amount);
    }

    /** How many of a cost the player holds in the 36 main slots. */
    public static int count(Player player, Cost cost) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && !stack.getType().isAir() && cost.match().test(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void take(Player player, Cost cost) {
        int left = cost.amount();
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir() || !cost.match().test(stack)) {
                continue;
            }
            int used = Math.min(left, stack.getAmount());
            left -= used;
            if (used == stack.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - used);
            }
        }
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** Crafts one rune from the player's inventory. Nothing is spent unless everything is there. */
    public Outcome craft(Player player, Rune rune, int rank) {
        int rankToMake = rune.realm() ? 0 : rank;
        List<Cost> costs = costs(rune, rankToMake);
        for (Cost cost : costs) {
            int have = count(player, cost);
            if (have < cost.amount()) {
                return new Outcome(false, "You need " + cost.amount() + " " + cost.label() + " (you have " + have + ").");
            }
        }
        costs.forEach(cost -> take(player, cost));
        give(player, core.items().create(rune.itemId(rankToMake)));
        return new Outcome(true, "Crafted a " + rune.name() + " Rune" + (rune.realm() ? "" : " " + Rune.roman(rank)) + ".");
    }

    /** Grinds one amethyst shard into Rune Powder. */
    public Outcome grind(Player player) {
        Cost shard = vanilla(Material.AMETHYST_SHARD, 1);
        if (count(player, shard) < 1) {
            return new Outcome(false, "You need an Amethyst Shard.");
        }
        take(player, shard);
        give(player, core.items().create(RuneItems.POWDER, config.grindOutput()));
        return new Outcome(true, "Ground an Amethyst Shard into " + config.grindOutput() + " Rune Powder.");
    }

    // ---------------------------------------------------------------- Realm effects

    @Override
    public void onSurge(Player attacker, LivingEntity target, ItemStack weapon) {
        onSurge(attacker, target, weapon, 0);
    }

    @Override
    public void onSurge(Player attacker, LivingEntity target, ItemStack weapon, double hit) {
        var runes = runesOf(weapon);
        RuneConfig.Effects fx = config.effects();
        if (runes.containsKey("blight")) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, fx.blightTicks(), 0, true, true));
            target.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, target.getLocation().add(0, target.getHeight() * 0.5, 0), 16, 0.3, 0.4, 0.3, 0);
        }
        if (runes.containsKey("tempest")) {
            try {
                target.getWorld().strikeLightningEffect(target.getLocation()); // a flash and thunder, no damage, no fire
            } catch (RuntimeException e) {
                // the test server has no lightning: the chain below still runs
            }
            int left = fx.tempestChains();
            for (org.bukkit.entity.Entity e : target.getNearbyEntities(fx.tempestRadius(), fx.tempestRadius(), fx.tempestRadius())) {
                if (left <= 0) {
                    break;
                }
                if (e instanceof LivingEntity other && other instanceof org.bukkit.entity.Enemy && other != attacker && !other.isDead()) {
                    left--;
                    other.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, other.getLocation().add(0, other.getHeight() * 0.5, 0), 20, 0.3, 0.5, 0.3, 0.1);
                    if (hit > 0) {
                        other.damage(hit * fx.tempestShare()); // no source: it must not start another Surge (and chain)
                    }
                }
            }
        }
        if (runes.containsKey("scorch")) {
            target.setFireTicks(Math.max(target.getFireTicks(), config.burnTicks()));
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, target.getHeight() * 0.5, 0), 14, 0.25, 0.3, 0.25, 0.02);
        }
        if (runes.containsKey("stasis")) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, config.stasisTicks(), 2, true, true));
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), Math.min(target.getMaxFreezeTicks(), config.stasisTicks() * 2)));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, target.getHeight() * 0.5, 0), 18, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Realm armor runes when a hit lands: Verdant, Rewind and Undertow. */
    public void onHurt(Player victim, org.bukkit.event.entity.EntityDamageEvent event) {
        onHurt(victim);
        RuneConfig.Effects fx = config.effects();
        // the attacker test comes first: ready() arms the cooldown, and a fall or a Wither tick must not spend it
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by && worn(victim, "undertow")
                && ready(undertowReady, victim.getUniqueId(), fx.undertowCooldownSeconds())) {
            org.bukkit.entity.Entity source = by.getDamager();
            if (source instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof org.bukkit.entity.Entity shooter) {
                source = shooter;
            }
            java.util.List<LivingEntity> pushed = new java.util.ArrayList<>();
            if (source instanceof LivingEntity attacker && attacker != victim) {
                pushed.add(attacker);
            }
            for (org.bukkit.entity.Entity e : victim.getNearbyEntities(fx.undertowRadius(), fx.undertowRadius(), fx.undertowRadius())) {
                if (e instanceof LivingEntity l && l instanceof org.bukkit.entity.Enemy && !pushed.contains(l)) {
                    pushed.add(l);
                }
            }
            for (LivingEntity l : pushed) {
                org.bukkit.util.Vector away = l.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 0.01) {
                    l.setVelocity(away.normalize().multiply(fx.undertowPush()).setY(0.4));
                }
                l.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, fx.undertowSlowTicks(), 1, true, true));
            }
            victim.getWorld().spawnParticle(Particle.BUBBLE_POP, victim.getLocation().add(0, 1, 0), 40, fx.undertowRadius() / 2, 0.5, fx.undertowRadius() / 2, 0.05);
        }
        if (worn(victim, "rewind")) {
            core.scheduler().nextTick(() -> {
                var max = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (!victim.isValid() || victim.isDead() || max == null || victim.getHealth() >= max.getValue() * fx.rewindBelow()
                        || !ready(rewindReady, victim.getUniqueId(), fx.rewindCooldownSeconds())) {
                    return;
                }
                victim.setHealth(Math.min(max.getValue(), victim.getHealth() + max.getValue() * fx.rewindHeal()));
                victim.getWorld().spawnParticle(Particle.FALLING_DUST, victim.getLocation().add(0, 1.2, 0), 30, 0.4, 0.6, 0.4, 0,
                    org.bukkit.Material.SAND.createBlockData());
                victim.playSound(victim.getLocation(), org.bukkit.Sound.BLOCK_SAND_BREAK, 1f, 0.6f);
                core.msg().info(victim, "The sands turn back: your Rewind rune mends you.");
            });
        }
    }

    /** Soulbind: a monster killed with the weapon gives some health back. */
    public void onKill(Player killer, LivingEntity victim) {
        if (!(victim instanceof org.bukkit.entity.Enemy) || !runesOf(killer.getInventory().getItemInMainHand()).containsKey("soulbind")) {
            return;
        }
        var max = killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (max != null && !killer.isDead()) {
            killer.setHealth(Math.min(max.getValue(), killer.getHealth() + max.getValue() * config.effects().soulbindHeal()));
            victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, victim.getHeight() * 0.5, 0), 10, 0.3, 0.4, 0.3, 0.02);
        }
    }

    private boolean worn(Player player, String rune) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && runesOf(piece).containsKey(rune)) {
                return true;
            }
        }
        return false;
    }

    /** True (and arms the cooldown) when the effect may trigger now. */
    private static boolean ready(Map<UUID, Long> until, UUID player, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        synchronized (until) {
            if (until.getOrDefault(player, 0L) > now) {
                return false;
            }
            until.put(player, now + cooldownSeconds * 1000L);
            return true;
        }
    }

    /** Verdant: a hit taken grants Regeneration I, at most once per cooldown. */
    public void onHurt(Player victim) {
        boolean worn = false;
        for (ItemStack piece : victim.getInventory().getArmorContents()) {
            if (piece != null && runesOf(piece).containsKey("verdant")) {
                worn = true;
                break;
            }
        }
        if (!worn) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (verdantReady) {
            if (verdantReady.getOrDefault(victim.getUniqueId(), 0L) > now) {
                return;
            }
            verdantReady.put(victim.getUniqueId(), now + config.regenCooldownSeconds() * 1000L);
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, config.regenTicks(), 0, true, true));
        victim.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, victim.getLocation().add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0);
    }

    public void forget(UUID player) {
        synchronized (verdantReady) {
            verdantReady.remove(player);
        }
        synchronized (rewindReady) {
            rewindReady.remove(player);
        }
        synchronized (undertowReady) {
            undertowReady.remove(player);
        }
    }
}
