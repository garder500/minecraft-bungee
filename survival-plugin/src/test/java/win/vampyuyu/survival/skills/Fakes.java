package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;

/** Minimal stand-ins for the services other features publish. */
final class Fakes {
    private Fakes() {}

    static final class Stats implements StatsApi {
        final Map<UUID, Map<String, Map<Stat, Double>>> modifiers = new HashMap<>();
        int recomputes;
        final Map<Stat, Double> flat = new HashMap<>();

        @Override
        public double stat(Player player, Stat stat) {
            double total = flat.getOrDefault(stat, 0.0);
            for (Map<Stat, Double> bySource : modifiers.getOrDefault(player.getUniqueId(), Map.of()).values()) {
                total += bySource.getOrDefault(stat, 0.0);
            }
            return total;
        }

        @Override
        public void addModifier(UUID player, String source, Stat stat, double amount) {
            modifiers.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(source, k -> new HashMap<>())
                .put(stat, amount);
        }

        @Override
        public void removeModifiers(UUID player, String source) {
            Map<String, Map<Stat, Double>> bySource = modifiers.get(player);
            if (bySource != null) {
                bySource.remove(source);
            }
        }

        @Override
        public void recompute(Player player) {
            recomputes++;
        }

        double modifier(UUID player, String source, Stat stat) {
            return modifiers.getOrDefault(player, Map.of()).getOrDefault(source, Map.of()).getOrDefault(stat, 0.0);
        }
    }

    static final class Economy implements EconomyApi {
        final Map<UUID, Double> balances = new HashMap<>();
        final List<String> reasons = new ArrayList<>();

        @Override
        public double coins(UUID player) {
            return balances.getOrDefault(player, 0.0);
        }

        @Override
        public boolean has(UUID player, double amount) {
            return coins(player) >= amount;
        }

        @Override
        public boolean take(UUID player, double amount, String reason) {
            if (amount <= 0 || !has(player, amount)) {
                return false;
            }
            balances.merge(player, -amount, Double::sum);
            return true;
        }

        @Override
        public void add(UUID player, double amount, String reason) {
            if (amount > 0) {
                balances.merge(player, amount, Double::sum);
                reasons.add(reason);
            }
        }

        @Override
        public void addTransactionListener(Consumer<Transaction> listener) {}
    }
}
