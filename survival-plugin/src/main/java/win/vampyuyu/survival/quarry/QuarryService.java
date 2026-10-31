package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Items;

/** Generator tiers, the ore draw and the Forge queue. Main thread only. */
public final class QuarryService implements win.vampyuyu.survival.api.EndAccessApi, win.vampyuyu.survival.api.GeneratorApi {
    public record Drop(String ore, ItemStack stack) {}

    /** Told about a draw: a geode was cracked, or a named custom ore was found (name null otherwise). */
    @FunctionalInterface
    public interface Notifier {
        void roll(boolean geode, boolean pity, String customOreName);
    }

    private final Core core;
    private final Items items;
    private volatile QuarryConfig config;

    Core core() {
        return core;
    }

    QuarryService(Core core, QuarryConfig config) {
        this.core = core;
        this.items = core.items();
        this.config = config;
    }

    void config(QuarryConfig config) {
        this.config = config;
    }

    public QuarryConfig config() {
        return config;
    }

    public QuarryData data(UUID player) {
        return core.profiles().section(player, "quarry", QuarryData.class, QuarryData::new);
    }

    private void dirty(UUID player) {
        core.profiles().markDirty(player);
    }

    @Override
    public long blocksMined(UUID player) {
        return data(player).blocks;
    }

    @Override
    public int tier(UUID owner) {
        return Math.max(1, Math.min(data(owner).generatorTier, config.tiers));
    }

    /** Raises the generator tier of the island owned by {@code owner} to {@code tier}. False when it is not higher. */
    public boolean upgrade(UUID owner, int tier) {
        QuarryData data = data(owner);
        if (tier <= data.generatorTier || tier > config.tiers) {
            return false;
        }
        data.generatorTier = tier;
        dirty(owner);
        return true;
    }

    @Override
    public boolean canEnterEnd(UUID player) {
        return data(player).endUnlocked;
    }

    @Override
    public String endLockedMessage() {
        return "The End is sealed. Forge a Void Lens and use it on your island to open the way.";
    }

    /** Opens the End for a player. False when it already was. */
    public boolean unlockEnd(UUID player) {
        QuarryData data = data(player);
        if (data.endUnlocked) {
            return false;
        }
        data.endUnlocked = true;
        dirty(player);
        return true;
    }

    // ---------------------------------------------------------------- the draw

    /** One broken generator block of the island of {@code owner}. */
    public void draw(UUID owner, double luck, Random random, Consumer<Drop> dropper, Notifier notifier) {
        QuarryData data = data(owner);
        int tier = tier(owner);
        OreRoller.Roll roll = config.roller.roll(tier, luck, data.counters, random);
        data.blocks++;
        dirty(owner);
        if (roll.geode()) {
            data.geodes++;
        }
        String customName = null;
        for (OreRoller.Drop drop : roll.drops()) {
            OreRoller.Ore ore = config.roller.ores().stream().filter(o -> o.id().equals(drop.ore())).findFirst().orElse(null);
            if (ore == null) {
                continue;
            }
            Optional<ItemStack> stack = stack(ore.item(), drop.amount());
            if (stack.isEmpty()) {
                continue;
            }
            dropper.accept(new Drop(ore.id(), stack.get()));
            if (ore.item().startsWith("custom:")) {
                customName = items.def(ore.item().substring(7)).map(d -> d.name()).orElse(ore.id());
            }
        }
        if (roll.geode() || customName != null) {
            notifier.roll(roll.geode(), roll.pity(), customName);
        }
    }

    /** A stack for a config key; empty when a custom id is not registered. */
    Optional<ItemStack> stack(String key, int amount) {
        if (key.startsWith("custom:")) {
            String id = key.substring(7);
            return items.def(id).isPresent() ? Optional.of(items.create(id, amount)) : Optional.empty();
        }
        Material material = Material.matchMaterial(key);
        return material == null ? Optional.empty() : Optional.of(new ItemStack(material, amount));
    }

    // ---------------------------------------------------------------- the Forge

    boolean matches(String key, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Optional<String> id = items.idOf(stack);
        if (key.startsWith("custom:")) {
            return id.isPresent() && id.get().equals(key.substring(7));
        }
        Material material = Material.matchMaterial(key);
        return id.isEmpty() && material != null && stack.getType() == material;
    }

    boolean canAfford(Player player, ForgeRecipe recipe) {
        for (Map.Entry<String, Integer> need : recipe.cost().entrySet()) {
            int have = 0;
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (matches(need.getKey(), stack)) {
                    have += stack.getAmount();
                }
            }
            if (have < need.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void take(Player player, ForgeRecipe recipe) {
        for (Map.Entry<String, Integer> need : recipe.cost().entrySet()) {
            int left = need.getValue();
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int i = 0; i < contents.length && left > 0; i++) {
                if (matches(need.getKey(), contents[i])) {
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
    }

    /** Result of {@link #start}: null on success, otherwise the reason. */
    public String start(Player player, String recipeId, long now) {
        ForgeRecipe recipe = config.recipes.get(recipeId);
        if (recipe == null || items.def(recipe.result()).isEmpty()) {
            return "That recipe is not available.";
        }
        QuarryData data = data(player.getUniqueId());
        if (data.jobs.size() >= config.forgeSlots) {
            return "All your Forge slots are busy.";
        }
        if (!canAfford(player, recipe)) {
            return "You do not have the materials.";
        }
        take(player, recipe);
        data.jobs.add(new QuarryData.Job(recipeId, now + recipe.seconds() * 1000L));
        dirty(player.getUniqueId());
        return null;
    }

    /** Hands over every finished job; what does not fit is dropped at the player's feet. Returns the item names. */
    public List<String> collect(Player player, long now) {
        return collect(player, now, id -> { });
    }

    /** Same, telling {@code forged} the id of every item handed over. */
    public List<String> collect(Player player, long now, Consumer<String> forged) {
        QuarryData data = data(player.getUniqueId());
        List<String> names = new ArrayList<>();
        List<QuarryData.Job> keep = new ArrayList<>();
        for (QuarryData.Job job : data.jobs) {
            ForgeRecipe recipe = config.recipes.get(job.recipe);
            if (job.finishAt > now) {
                keep.add(job);
                continue;
            }
            if (recipe == null || items.def(recipe.result()).isEmpty()) {
                continue; // the recipe was removed: the job is dropped, its materials are not refunded
            }
            ItemStack stack = items.create(recipe.result(), recipe.amount());
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            Location at = player.getLocation();
            left.values().forEach(rest -> at.getWorld().dropItem(at, rest));
            names.add(items.def(recipe.result()).get().name());
            forged.accept(recipe.result());
        }
        if (keep.size() != data.jobs.size()) {
            data.jobs = keep;
            dirty(player.getUniqueId());
        }
        return names;
    }
}
