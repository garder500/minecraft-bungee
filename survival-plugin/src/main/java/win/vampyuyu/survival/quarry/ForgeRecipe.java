package win.vampyuyu.survival.quarry;

import java.util.Map;

/** One Forge recipe. {@code cost} maps an item key (vanilla material or custom:<id>) to an amount. */
public record ForgeRecipe(String id, String result, int seconds, Map<String, Integer> cost, int amount) {
    public ForgeRecipe {
        cost = Map.copyOf(cost);
        amount = Math.max(1, amount);
    }

    public ForgeRecipe(String id, String result, int seconds, Map<String, Integer> cost) {
        this(id, result, seconds, cost, 1);
    }
}
