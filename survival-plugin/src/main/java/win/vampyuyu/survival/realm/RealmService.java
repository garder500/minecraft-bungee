package win.vampyuyu.survival.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.RealmResourceApi;
import win.vampyuyu.survival.core.Core;

/** Pity draws of the Realm resources. Counters are per player, resource and source kind, kept in the profile. */
public final class RealmService implements RealmResourceApi {
    private final Core core;
    private volatile RealmRepo.Loaded content;
    private final DoubleSupplier random;

    public RealmService(Core core, RealmRepo.Loaded content) {
        this(core, content, () -> ThreadLocalRandom.current().nextDouble());
    }

    RealmService(Core core, RealmRepo.Loaded content, DoubleSupplier random) {
        this.core = core;
        this.content = content;
        this.random = random;
    }

    void reload(RealmRepo.Loaded loaded) {
        this.content = loaded;
    }

    Map<String, RealmResource> resources() {
        return content.resources();
    }

    private static String key(String resourceId, String kind) {
        return resourceId + ":" + kind;
    }

    @Override
    public List<String> resourcesOf(Realm realm) {
        return content.resources().values().stream().filter(r -> r.realm() == realm).map(RealmResource::id).toList();
    }

    @Override
    public Realm realmOf(String worldName) {
        return worldName == null ? null : content.worlds().get(worldName);
    }

    @Override
    public int misses(UUID player, String resourceId, String kind) {
        return core.profiles().section(player, "realm", RealmData.class, RealmData::new).misses.getOrDefault(key(resourceId, kind), 0);
    }

    @Override
    public double chance(UUID player, String resourceId, String kind) {
        RealmResource r = content.resources().get(resourceId);
        return r == null ? 0 : RealmPity.chance(r.p0(), r.soft(), r.dur(), misses(player, resourceId, kind));
    }

    @Override
    public List<ItemStack> roll(UUID player, String resourceId, String kind) {
        RealmResource r = content.resources().get(resourceId);
        if (r == null || core.items().def(r.item()).isEmpty()) {
            return List.of();
        }
        boolean[] hit = new boolean[1];
        core.profiles().edit(player, "realm", RealmData.class, RealmData::new, d -> {
            int k = d.misses.getOrDefault(key(resourceId, kind), 0);
            hit[0] = random.getAsDouble() < RealmPity.chance(r.p0(), r.soft(), r.dur(), k);
            d.misses.put(key(resourceId, kind), hit[0] ? 0 : k + 1);
        });
        if (!hit[0]) {
            return List.of();
        }
        int amount = r.amountMin() + (r.amountMax() > r.amountMin() ? (int) (random.getAsDouble() * (r.amountMax() - r.amountMin() + 1)) : 0);
        List<ItemStack> out = new ArrayList<>();
        out.add(core.items().create(r.item(), amount));
        return out;
    }
}
