package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.MinionApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.minions.MinionConfig.Effect;
import win.vampyuyu.survival.minions.MinionConfig.Fuel;
import win.vampyuyu.survival.minions.MinionConfig.Skin;
import win.vampyuyu.survival.minions.MinionConfig.Type;
import win.vampyuyu.survival.minions.MinionConfig.Upgrade;

/**
 * The rules of minions in the world: placing, picking up, collecting, upgrading and the ticker that makes them
 * work. Every item that changes hands goes through a "state first, save, then items" order so a crash can lose an
 * item but never duplicate one.
 */
public final class MinionManager implements MinionApi {
    /** Result of an action that can be refused with a reason (shown to the player as is). */
    public record Result(@Nullable String error) {
        public static final Result OK = new Result(null);

        public boolean ok() {
            return error == null;
        }

        public static Result fail(String reason) {
            return new Result(reason);
        }
    }

    private final Core core;
    private final MinionConfig config;
    private final MinionStore store;
    private final MinionLogic logic;
    private final MinionVisuals visuals;
    private final LongSupplier clock;
    private final Random random = new Random();
    private final java.util.Map<String, MinionRuntime> runtime = new java.util.HashMap<>();
    private long lastFlush;
    private long lastOwedCheck;

    public MinionManager(Core core, MinionConfig config, MinionStore store, MinionLogic logic, MinionVisuals visuals,
            LongSupplier clock) {
        this.core = core;
        this.config = config;
        this.store = store;
        this.logic = logic;
        this.visuals = visuals;
        this.clock = clock;
    }

    public MinionConfig config() {
        return config;
    }

    public MinionStore store() {
        return store;
    }

    public MinionLogic logic() {
        return logic;
    }

    MinionVisuals visuals() {
        return visuals;
    }

    private Items items() {
        return core.items();
    }

    private Optional<IslandApi> islands() {
        return core.service(IslandApi.class);
    }

    MinionRuntime runtimeOf(MinionData d) {
        return runtime.computeIfAbsent(d.id, k -> new MinionRuntime());
    }

    public Optional<Type> typeOf(MinionData d) {
        return Optional.ofNullable(config.types.get(d.type));
    }

    // ---------------------------------------------------------------- MinionApi

    @Override
    public int slotsFor(UUID player) {
        if (!core.profiles().isLoaded(player)) {
            return config.baseSlots;
        }
        return SlotRules.slots(config, profileOf(player).crafted.size());
    }

    @Override
    public int placedCount(UUID player) {
        return store.countOwned(player.toString());
    }

    MinionProfile profileOf(UUID player) {
        return core.profiles().section(player, "minions", MinionProfile.class, MinionProfile::new);
    }

    /** Counts a minion toward the slot unlocks. */
    public void markCrafted(UUID player, String type, int tier) {
        core.profiles().edit(player, "minions", MinionProfile.class, MinionProfile::new,
            p -> p.crafted.add(type + ":" + tier));
    }

    // ---------------------------------------------------------------- permissions

    private boolean isAdmin(Player p) {
        return p.hasPermission("vampyuyu.sb.minions.admin");
    }

    /** Owner, a member of the island the minion stands on, or an admin. */
    public boolean canAccess(Player p, MinionData d) {
        if (d.owner.equals(p.getUniqueId().toString()) || isAdmin(p)) {
            return true;
        }
        World w = Bukkit.getWorld(d.world);
        Optional<IslandApi> api = islands();
        if (w == null || api.isEmpty()) {
            return false;
        }
        return api.get().islandAt(new Location(w, d.x, d.y, d.z)).map(i -> i.members().contains(p.getUniqueId()))
            .orElse(false);
    }

    /** Owner, owner of the island, or an admin. */
    public boolean canPickUp(Player p, MinionData d) {
        if (d.owner.equals(p.getUniqueId().toString()) || isAdmin(p)) {
            return true;
        }
        World w = Bukkit.getWorld(d.world);
        Optional<IslandApi> api = islands();
        if (w == null || api.isEmpty()) {
            return false;
        }
        return api.get().islandAt(new Location(w, d.x, d.y, d.z)).map(i -> i.owner().equals(p.getUniqueId()))
            .orElse(false);
    }

    // ---------------------------------------------------------------- placing

    /** Places the minion item {@code stack} (not consumed here) against {@code face} of {@code clicked}. */
    public Result place(Player p, ItemStack stack, Block clicked, BlockFace face) {
        MinionItems.Parsed parsed = MinionItems.parse(items(), stack);
        if (parsed == null) {
            return Result.fail("That is not a minion.");
        }
        Type type = config.types.get(parsed.type());
        if (type == null) {
            return Result.fail("This minion type no longer exists. Ask an admin.");
        }
        Optional<IslandApi> api = islands();
        if (api.isEmpty()) {
            return Result.fail("Islands are not available right now.");
        }
        Block target = clicked.getRelative(face);
        if (!target.getType().isAir() || !target.getRelative(BlockFace.UP).getType().isAir()) {
            return Result.fail("There is not enough room for a minion here.");
        }
        if (!target.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return Result.fail("A minion needs solid ground to stand on.");
        }
        Location at = target.getLocation();
        Optional<IslandInfo> island = api.get().islandAt(at);
        if (island.isEmpty()) {
            return Result.fail("Minions can only be placed on an island.");
        }
        UUID id = p.getUniqueId();
        if (!island.get().members().contains(id)) {
            return Result.fail("You can only place minions on your own island.");
        }
        if (!api.get().canBuild(p, at)) {
            return Result.fail("You cannot build here.");
        }
        String owner = id.toString();
        int slots = slotsFor(id);
        if (store.countOwned(owner) >= slots) {
            return Result.fail("You have no free minion slot (" + store.countOwned(owner) + "/" + slots
                + "). Craft more different minions to unlock slots.");
        }
        String islandId = island.get().id().toString();
        if (store.countOnIsland(islandId) >= config.perIslandCap) {
            return Result.fail("This island already has " + config.perIslandCap + " minions.");
        }
        if (store.atBlock(target.getWorld().getName(), target.getX(), target.getY(), target.getZ()).isPresent()) {
            return Result.fail("There is already a minion here.");
        }
        int radius = config.radius(parsed.tier(), 0);
        if (!areaInside(api.get(), Terrain.areaOf(target.getWorld(), probe(target), radius), islandId)) {
            return Result.fail("The working area of this minion would leave your island. Place it further inside.");
        }
        String uid = parsed.uid() != null ? parsed.uid() : MinionItems.newUid();
        if (!store.spendUid(uid)) {
            return Result.fail("This minion item was already used.");
        }
        MinionData d = new MinionData();
        d.id = UUID.randomUUID().toString();
        d.uid = uid;
        d.owner = owner;
        d.island = islandId;
        d.world = target.getWorld().getName();
        d.x = target.getX();
        d.y = target.getY();
        d.z = target.getZ();
        d.yaw = Math.round((p.getLocation().getYaw() + 180f) / 45f) * 45f;
        d.type = type.id();
        d.tier = parsed.tier();
        d.placedAt = clock.getAsLong();
        d.lastTick = d.placedAt;
        store.add(d);
        store.saveNow(owner);
        return Result.OK;
    }

    private static MinionData probe(Block b) {
        MinionData d = new MinionData();
        d.x = b.getX();
        d.y = b.getY();
        d.z = b.getZ();
        return d;
    }

    /** True when all four corners of the area belong to the same island. */
    boolean areaInside(IslandApi api, Terrain.Area area, String islandId) {
        for (Location corner : Terrain.corners(area)) {
            Optional<IslandInfo> info = api.islandAt(corner);
            if (info.isEmpty() || !info.get().id().toString().equals(islandId)) {
                return false;
            }
        }
        return true;
    }

    /** The minion placed by {@link #place}, looked up by block. */
    public Optional<MinionData> at(Block target) {
        return store.atBlock(target.getWorld().getName(), target.getX(), target.getY(), target.getZ());
    }

    // ---------------------------------------------------------------- storage

    /**
     * Moves up to {@code wanted} items of {@code key} from the minion's chest to the player. Order: take from the
     * state, save, hand over; whatever does not fit goes back into the chest.
     */
    public long withdraw(Player p, MinionData d, String key, long wanted) {
        Storage storage = logic.storage(d);
        long n = Math.min(wanted, storage.count(key));
        if (n <= 0 || !ItemKey.valid(items(), key)) {
            return 0;
        }
        storage.remove(key, n);
        store.saveNow(d.owner);
        long left = give(p, key, n);
        if (left > 0) {
            long back = logic.storage(d).add(key, left);
            if (back < left) {
                dropAt(p, key, left - back);
            }
            store.markDirty(d);
        }
        return n - left;
    }

    /** Collects everything; returns the number of items that reached the inventory. */
    public long collectAll(Player p, MinionData d) {
        long moved = 0;
        for (String key : new ArrayList<>(d.storage.keySet())) {
            moved += withdraw(p, d, key, Long.MAX_VALUE);
        }
        return moved;
    }

    /** True for the key of a minion item (it carries a uid, so every copy handed out gets its own). */
    private boolean isMinionKey(String key) {
        return ItemKey.isCustom(key) && ItemKey.id(key).startsWith("minion_")
            && items().def(ItemKey.id(key)).map(def -> def.pdcStrings().containsKey(MinionItems.PDC_TYPE)).orElse(false);
    }

    /** One stack of the key; minion items get a fresh uid. */
    private ItemStack make(String key, int amount) {
        if (isMinionKey(key)) {
            ItemStack stack = items().create(ItemKey.id(key), amount);
            items().set(stack, MinionItems.PDC_UID, MinionItems.newUid());
            return stack;
        }
        return ItemKey.create(items(), key, amount);
    }

    /** Gives items to the inventory; returns how many did not fit. */
    long give(Player p, String key, long amount) {
        long left = 0;
        long remaining = amount;
        boolean unique = isMinionKey(key);
        int stack = unique ? 1 : Math.max(1, ItemKey.stackSize(key));
        long chunk = stack; // one real stack per call: leftovers are then exact on every server
        while (remaining > 0) {
            int part = (int) Math.min(remaining, chunk);
            for (ItemStack rest : p.getInventory().addItem(make(key, part)).values()) {
                left += rest.getAmount();
            }
            remaining -= part;
            if (left > 0) {
                left += remaining;
                break;
            }
        }
        return left;
    }

    /** Drops items at the player's feet (what did not fit). */
    void giveDropped(Player p, String key, long amount) {
        dropAt(p, key, amount);
    }

    private void dropAt(Player p, String key, long amount) {
        long remaining = amount;
        int stack = Math.max(1, ItemKey.stackSize(key));
        while (remaining > 0) {
            int part = (int) Math.min(remaining, stack);
            p.getWorld().dropItemNaturally(p.getLocation(), make(key, part));
            remaining -= part;
        }
    }

    // ---------------------------------------------------------------- inventory helpers

    /** Items of the given keys in the player's main inventory. */
    long countIn(Player p, List<String> keys) {
        long n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            String key = ItemKey.of(items(), s);
            if (key != null && keys.contains(key)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    /** Removes {@code amount} items of the keys (earlier keys first); false and nothing removed if not enough. */
    boolean takeFrom(Player p, List<String> keys, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (countIn(p, keys) < amount) {
            return false;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        long left = amount;
        for (String key : keys) {
            for (int i = 0; i < contents.length && left > 0; i++) {
                if (key.equals(ItemKey.of(items(), contents[i]))) {
                    int take = (int) Math.min(left, contents[i].getAmount());
                    if (take >= contents[i].getAmount()) {
                        contents[i] = null;
                    } else {
                        contents[i].setAmount(contents[i].getAmount() - take);
                    }
                    left -= take;
                }
            }
        }
        inv.setStorageContents(contents);
        return true;
    }

    // ---------------------------------------------------------------- tier up

    /** Keys that pay the next tier of a minion (the main resource, and its smelted form). */
    public List<String> tierUpKeys(Type type, boolean superCompacted) {
        List<String> keys = new ArrayList<>();
        Material primary = type.primary().item();
        keys.add(ItemKey.custom(superCompacted ? MinionItems.superId(primary) : MinionItems.enchantedId(primary)));
        Material smelted = config.smelted(primary);
        if (smelted != primary) {
            keys.add(ItemKey.custom(superCompacted ? MinionItems.superId(smelted) : MinionItems.enchantedId(smelted)));
        }
        return keys;
    }

    /** Upgrades the minion by one tier, paying with compacted resources from the inventory. */
    public Result tierUp(Player p, MinionData d) {
        Type type = config.types.get(d.type);
        if (type == null) {
            return Result.fail("This minion type no longer exists.");
        }
        if (d.tier >= MinionConfig.MAX_TIER) {
            return Result.fail("This minion is already at the highest tier.");
        }
        MinionConfig.TierUp cost = config.tierUp(d.tier);
        List<String> keys = tierUpKeys(type, cost.superCompacted());
        if (countIn(p, keys) < cost.amount()) {
            return Result.fail("You need " + cost.amount() + " " + (cost.superCompacted() ? "Super " : "") + "Enchanted "
                + MinionConfig.prettify(type.primary().item()) + " in your inventory.");
        }
        Optional<IslandApi> api = islands();
        World w = Bukkit.getWorld(d.world);
        if (api.isPresent() && w != null) {
            int next = config.radius(d.tier + 1, 0) + (logic.radius(d) - config.radius(d.tier, 0));
            if (!areaInside(api.get(), new Terrain.Area(w, d.x, d.y, d.z, next), d.island)) {
                return Result.fail("The larger working area of the next tier would leave your island.");
            }
        }
        takeFrom(p, keys, cost.amount());
        d.tier++;
        store.markDirty(d);
        store.saveNow(d.owner);
        markCrafted(UUID.fromString(d.owner), d.type, d.tier);
        visuals.refresh(d, type);
        runtimeOf(d).labelText = "";
        return Result.OK;
    }

    /** Turns plain resources in the player's inventory into the compacted forms; returns the items made. */
    public long compressInventory(Player p, Type type) {
        Material primary = type.primary().item();
        long made = compress(p, ItemKey.vanilla(primary), ItemKey.custom(MinionItems.enchantedId(primary)), config.compactRatio);
        Material smelted = config.smelted(primary);
        if (smelted != primary) {
            made += compress(p, ItemKey.vanilla(smelted), ItemKey.custom(MinionItems.enchantedId(smelted)), config.compactRatio);
        }
        long supers = compress(p, ItemKey.custom(MinionItems.enchantedId(primary)),
            ItemKey.custom(MinionItems.superId(primary)), config.superRatio);
        return made + supers;
    }

    private long compress(Player p, String from, String to, int ratio) {
        long have = countIn(p, List.of(from));
        long batches = have / ratio;
        if (batches <= 0) {
            return 0;
        }
        takeFrom(p, List.of(from), batches * ratio);
        long left = give(p, to, batches);
        if (left > 0) {
            dropAt(p, to, left);
        }
        return batches;
    }

    // ---------------------------------------------------------------- fuel, upgrades, hat

    /** Adds fuel items from the inventory (one, or as many as fit when {@code all}). */
    public Result addFuel(Player p, MinionData d, Fuel fuel, boolean all) {
        Type type = config.types.get(d.type);
        if (type == null || !fuel.accepts(type.kind())) {
            return Result.fail("This fuel does not work on this kind of minion.");
        }
        Fuel current = logic.fuelOf(d);
        if (current != null && !current.id().equals(fuel.id())) {
            return Result.fail("The minion is already burning " + current.name() + ". Wait until it is used up.");
        }
        long per = fuel.durationMillis();
        double room = config.maxFuelMillis() - (current == null ? 0 : d.fuelMs);
        long fit = (long) Math.floor(room / per);
        if (fit <= 0) {
            return Result.fail("The fuel tank is full.");
        }
        long have = countIn(p, List.of(fuel.key()));
        if (have <= 0) {
            return Result.fail("You do not have that fuel.");
        }
        long units = all ? Math.min(fit, have) : 1;
        takeFrom(p, List.of(fuel.key()), units);
        d.fuel = fuel.id();
        d.fuelMs = (current == null ? 0 : d.fuelMs) + units * per;
        store.markDirty(d);
        store.saveNow(d.owner);
        return Result.OK;
    }

    /** Installs one upgrade item from the inventory. */
    public Result installUpgrade(Player p, MinionData d, Upgrade up) {
        if (d.upgrades.size() >= config.upgradeSlots) {
            return Result.fail("All upgrade slots are in use.");
        }
        if (d.upgrades.contains(up.id())) {
            return Result.fail("That upgrade is already installed.");
        }
        boolean clash = logic.upgradesOf(d).stream().anyMatch(o -> o.effect() == up.effect()
            || (isCompaction(o.effect()) && isCompaction(up.effect())));
        if (clash && up.effect() != Effect.SPEED) {
            return Result.fail("An upgrade with the same job is already installed.");
        }
        if (up.effect() == Effect.EXPAND) {
            Optional<IslandApi> api = islands();
            World w = Bukkit.getWorld(d.world);
            if (api.isPresent() && w != null && !areaInside(api.get(),
                    new Terrain.Area(w, d.x, d.y, d.z, logic.radius(d) + up.radius()), d.island)) {
                return Result.fail("The wider area would leave your island.");
            }
        }
        if (!takeFrom(p, List.of(up.key()), 1)) {
            return Result.fail("You do not have that upgrade.");
        }
        d.upgrades.add(up.id());
        store.markDirty(d);
        store.saveNow(d.owner);
        runtimeOf(d).labelText = "";
        return Result.OK;
    }

    private static boolean isCompaction(Effect e) {
        return e == Effect.COMPACT || e == Effect.SUPER_COMPACT;
    }

    /** Takes an upgrade out and gives the item back. */
    public Result removeUpgrade(Player p, MinionData d, String upgradeId) {
        Upgrade up = config.upgrades.get(upgradeId);
        if (!d.upgrades.contains(upgradeId)) {
            return Result.fail("That upgrade is not installed.");
        }
        if (up == null) {
            d.upgrades.remove(upgradeId);
            store.saveNow(d.owner);
            return Result.OK;
        }
        d.upgrades.remove(upgradeId);
        store.markDirty(d);
        store.saveNow(d.owner);
        long left = give(p, up.key(), 1);
        if (left > 0) {
            dropAt(p, up.key(), left);
        }
        runtimeOf(d).labelText = "";
        return Result.OK;
    }

    /** Puts a hat on (the previous one is given back). */
    public Result setSkin(Player p, MinionData d, @Nullable Skin skin) {
        Type type = config.types.get(d.type);
        if (skin != null && !takeFrom(p, List.of(skin.key()), 1)) {
            return Result.fail("You do not have that hat.");
        }
        String old = d.skin;
        d.skin = skin == null ? null : skin.id();
        store.markDirty(d);
        store.saveNow(d.owner);
        if (old != null && config.skins.get(old) != null) {
            String key = config.skins.get(old).key();
            long left = give(p, key, 1);
            if (left > 0) {
                dropAt(p, key, left);
            }
        }
        if (type != null) {
            visuals.refresh(d, type);
        }
        return Result.OK;
    }

    // ---------------------------------------------------------------- pick up and removal

    /** The stacks a minion turns into when it is removed: the item (fresh uid), upgrades, hat, whole fuel units. */
    List<MinionProfile.Owed> contents(MinionData d, String reason) {
        List<MinionProfile.Owed> out = new ArrayList<>();
        if (config.types.containsKey(d.type)) {
            out.add(new MinionProfile.Owed(ItemKey.custom(MinionItems.minionId(d.type, d.tier)), 1, reason));
        }
        for (String id : d.upgrades) {
            Upgrade up = config.upgrades.get(id);
            if (up != null) {
                out.add(new MinionProfile.Owed(up.key(), 1, reason));
            }
        }
        if (d.skin != null && config.skins.get(d.skin) != null) {
            out.add(new MinionProfile.Owed(config.skins.get(d.skin).key(), 1, reason));
        }
        Fuel fuel = logic.fuelOf(d);
        if (fuel != null) {
            long units = (long) Math.floor(d.fuelMs / fuel.durationMillis());
            if (units > 0) {
                out.add(new MinionProfile.Owed(fuel.key(), units, reason));
            }
        }
        d.storage.forEach((key, n) -> {
            if (n > 0) {
                out.add(new MinionProfile.Owed(key, n, reason));
            }
        });
        return out;
    }

    /** Picks the minion up: it must be emptied first; everything returns to the player. */
    public Result pickUp(Player p, MinionData d) {
        if (!canPickUp(p, d)) {
            return Result.fail("Only the owner of the minion or of the island can pick it up.");
        }
        collectAll(p, d);
        if (!logic.storage(d).isEmpty()) {
            return Result.fail("Your inventory is full: collect the items first.");
        }
        List<MinionProfile.Owed> back = contents(d, "pick up");
        removeMinion(d);
        store.saveNow(d.owner);
        for (MinionProfile.Owed o : back) {
            long left = give(p, o.key, o.amount);
            if (left > 0) {
                dropAt(p, o.key, left);
            }
        }
        return Result.OK;
    }

    /** Stamps a fresh uid on minion items handed out. */
    ItemStack freshMinionItem(String type, int tier) {
        return MinionItems.createMinion(items(), type, tier, MinionItems.newUid());
    }

    /** Removes the minion from the world and the store (not saved). */
    void removeMinion(MinionData d) {
        MinionRuntime rt = runtime.remove(d.id);
        World w = Bukkit.getWorld(d.world);
        Type type = config.types.get(d.type);
        if (rt != null && type != null && w != null) {
            Terrain.cleanup(new Terrain.Area(w, d.x, d.y, d.z, 1), type, rt);
        }
        visuals.remove(d.id);
        store.remove(d.id);
    }

    /**
     * Removes a minion and owes everything it holds to its owner (island reset, kicked from a co-op, admin
     * purge). Nothing is lost: it arrives next time the owner has room.
     */
    public void returnToOwner(MinionData d, String reason) {
        UUID owner = UUID.fromString(d.owner);
        List<MinionProfile.Owed> owed = contents(d, reason);
        removeMinion(d);
        store.saveNow(d.owner);
        core.profiles().edit(owner, "minions", MinionProfile.class, MinionProfile::new, prof -> prof.owed.addAll(owed));
        Player online = Bukkit.getPlayer(owner);
        if (online != null) {
            deliverOwed(online);
        }
    }

    /** Gives the player what they are owed, as far as it fits. */
    public void deliverOwed(Player p) {
        if (!core.profiles().isLoaded(p.getUniqueId())) {
            return;
        }
        MinionProfile prof = profileOf(p.getUniqueId());
        if (prof.owed.isEmpty()) {
            return;
        }
        long delivered = 0;
        List<MinionProfile.Owed> keep = new ArrayList<>();
        for (MinionProfile.Owed o : prof.owed) {
            if (!ItemKey.valid(items(), o.key) || o.amount <= 0) {
                continue;
            }
            long left = deliverOne(p, o);
            delivered += o.amount - left;
            if (left > 0) {
                keep.add(new MinionProfile.Owed(o.key, left, o.reason));
            }
        }
        prof.owed = keep;
        core.profiles().markDirty(p.getUniqueId());
        if (delivered > 0) {
            core.msg().ok(p, "Items from your minions were added to your inventory.");
        }
        if (!keep.isEmpty()) {
            core.msg().warn(p, "Some minion items are waiting for free inventory space.");
        }
    }

    private long deliverOne(Player p, MinionProfile.Owed o) {
        return give(p, o.key, o.amount);
    }

    // ---------------------------------------------------------------- the ticker

    /** One pass over every placed minion. Never throws: one broken minion must not stop the others. */
    public void tick() {
        long now = clock.getAsLong();
        for (MinionData d : store.all()) {
            try {
                tickOne(d, now);
            } catch (RuntimeException e) {
                MinionRuntime rt = runtimeOf(d);
                if (now - rt.lastChat > 60_000) {
                    rt.lastChat = now;
                    core.getPlugin().getLogger().log(Level.WARNING, "minions: tick failed for " + d.id, e);
                }
            }
        }
        if (now - lastFlush >= 10_000) {
            lastFlush = now;
            store.flushDirty();
        }
        if (now - lastOwedCheck >= 30_000) {
            lastOwedCheck = now;
            for (Player online : Bukkit.getOnlinePlayers()) {
                deliverOwed(online);
            }
        }
    }

    /** Runs one minion's tick right now (after placing it, so its body appears at once). */
    public void refresh(MinionData d) {
        try {
            tickOne(d, clock.getAsLong());
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "minions: refresh failed for " + d.id, e);
        }
    }

    private void tickOne(MinionData d, long now) {
        Type type = config.types.get(d.type);
        World w = Bukkit.getWorld(d.world);
        if (type == null || w == null || !w.isChunkLoaded(d.x >> 4, d.z >> 4)) {
            return;
        }
        MinionRuntime rt = runtimeOf(d);
        Terrain.Area area = Terrain.areaOf(w, d, logic.radius(d));
        if (!Terrain.loaded(area)) {
            return;
        }
        if (now - rt.lastIslandCheck >= config.islandCheckSeconds * 1000L) {
            rt.lastIslandCheck = now;
            if (checkIsland(d, w, area, rt)) {
                return;
            }
        }
        boolean near = playerNear(w, d);
        rt.visualNear = near;
        Status scanned = scan(d, type, area, rt, near, now);
        Production.Outcome out = logic.advance(d, type, now, scanned == Status.WORKING);
        Status status = scanned == Status.WORKING && out.stalled() ? Status.STORAGE_FULL : scanned;
        if (out.cycles() > 0) {
            store.markDirty(d);
        }
        if (near) {
            if (!visuals.present(d.id)) {
                visuals.ensure(w, d, type, labelFor(d, type, status));
                rt.labelText = "";
            }
            animate(d, type, area, rt, status, out);
            updateLabel(d, type, rt, status);
        } else {
            visuals.remove(d.id);
        }
        if (status != rt.status) {
            notifyOwner(d, type, rt, status, now);
            rt.status = status;
        }
    }

    private Status scan(MinionData d, Type type, Terrain.Area area, MinionRuntime rt, boolean near, long now) {
        if (rt.status == Status.ISLAND_MISSING || rt.status == Status.OUTSIDE_ISLAND) {
            return rt.status;
        }
        Status base = rt.status == Status.STORAGE_FULL ? Status.WORKING : rt.status;
        if (near || rt.lastScan == 0 || now - rt.lastScan >= config.scanSeconds * 1000L) {
            rt.lastScan = now;
            return Terrain.scan(area, type, config);
        }
        return base;
    }

    private void animate(MinionData d, Type type, Terrain.Area area, MinionRuntime rt, Status status,
            Production.Outcome out) {
        if (status != Status.WORKING && status != Status.STORAGE_FULL) {
            return;
        }
        if (out.cycles() > 0) {
            Terrain.harvest(area, type, rt, random);
            rt.placed = false;
            visuals.swing(d.id);
        }
        double half = logic.cycleMillis(d, type) / 2d;
        if (status == Status.WORKING && !rt.placed && d.progress >= half) {
            Block spot = Terrain.place(area, type, d, rt, random, config.scale);
            if (spot != null) {
                rt.spotX = spot.getX();
                rt.spotY = spot.getY();
                rt.spotZ = spot.getZ();
            }
            if (spot != null || type.kind() == MinionConfig.Kind.FISHING) {
                rt.placed = true;
                visuals.swing(d.id);
            }
        }
    }

    /** Label text for the floating hologram. */
    Component labelFor(MinionData d, Type type, Status status) {
        Storage storage = logic.storage(d);
        String rarity = config.tierRarity[MinionConfig.clampTier(d.tier) - 1].color().asHexString();
        String text = "<" + rarity + "><bold>" + type.name() + " Minion " + MinionConfig.roman(d.tier) + "</bold></" + rarity + ">"
            + "<newline>" + status.color() + status.label()
            + "<newline><gray>Storage: <white>" + storage.slotsUsed() + "/" + storage.capacity();
        return Msg.component(text);
    }

    private void updateLabel(MinionData d, Type type, MinionRuntime rt, Status status) {
        Storage storage = logic.storage(d);
        String key = status.name() + ":" + d.tier + ":" + storage.slotsUsed() + ":" + d.upgrades.size();
        if (!key.equals(rt.labelText)) {
            rt.labelText = key;
            visuals.setLabel(d.id, labelFor(d, type, status));
        }
    }

    private void notifyOwner(MinionData d, Type type, MinionRuntime rt, Status status, long now) {
        if (!status.blocked() || now - rt.lastChat < config.chatCooldownSeconds * 1000L) {
            return;
        }
        Player owner = Bukkit.getPlayer(UUID.fromString(d.owner));
        if (owner == null || !owner.getWorld().getName().equals(d.world)) {
            return;
        }
        rt.lastChat = now;
        core.msg().warn(owner, type.name() + " Minion at " + d.x + ", " + d.y + ", " + d.z + ": " + status.label()
            + ". " + status.hint(type.kind()));
    }

    private boolean playerNear(World w, MinionData d) {
        double range = config.visualRange * config.visualRange;
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - d.x;
            double dy = l.getY() - d.y;
            double dz = l.getZ() - d.z;
            if (dx * dx + dy * dy + dz * dz <= range) {
                return true;
            }
        }
        return false;
    }

    /** Island verification. Returns true when the minion was removed. */
    private boolean checkIsland(MinionData d, World w, Terrain.Area area, MinionRuntime rt) {
        Optional<IslandApi> api = islands();
        if (api.isEmpty()) {
            return false;
        }
        Optional<IslandInfo> info = api.get().islandAt(new Location(w, d.x, d.y, d.z));
        if (info.isEmpty()) {
            rt.status = Status.ISLAND_MISSING;
            return false;
        }
        IslandInfo island = info.get();
        UUID owner = UUID.fromString(d.owner);
        if (!island.id().toString().equals(d.island) || !island.members().contains(owner)) {
            if (++rt.islandStrikes >= 2) {
                returnToOwner(d, "island changed");
                return true;
            }
            return false;
        }
        rt.islandStrikes = 0;
        if (rt.status == Status.ISLAND_MISSING) {
            rt.status = Status.WORKING;
        }
        boolean inside = areaInside(api.get(), area, d.island);
        if (!inside) {
            rt.status = Status.OUTSIDE_ISLAND;
        } else if (rt.status == Status.OUTSIDE_ISLAND) {
            rt.status = Status.WORKING;
        }
        return false;
    }

    // ---------------------------------------------------------------- chunk and world events

    /** Drops the bodies of the minions in a chunk (it is unloading). */
    public void chunkUnloaded(String world, int chunkX, int chunkZ) {
        for (MinionData d : store.inChunk(world, chunkX, chunkZ)) {
            MinionRuntime rt = runtime.get(d.id);
            if (rt != null) {
                rt.mob = null;
                rt.placed = false;
            }
            visuals.remove(d.id);
        }
    }

    /** Drops every body in a world (it is unloading). */
    public void worldUnloaded(String world) {
        for (MinionData d : store.all()) {
            if (d.world.equals(world)) {
                MinionRuntime rt = runtime.get(d.id);
                if (rt != null) {
                    rt.mob = null;
                    rt.placed = false;
                }
                visuals.remove(d.id);
            }
        }
    }

    /** Called on disable: removes every body and prop, then saves. */
    public void shutdown() {
        for (MinionData d : store.all()) {
            MinionRuntime rt = runtime.get(d.id);
            if (rt != null && rt.mob != null) {
                rt.mob.remove();
            }
        }
        visuals.removeAll();
        store.close();
    }

    /** Admin: removes minions (all of an owner, or those whose island is gone) and owes their content back. */
    public int purge(@Nullable UUID owner, boolean orphansOnly) {
        int n = 0;
        for (MinionData d : store.all()) {
            boolean match = owner != null ? d.owner.equals(owner.toString()) : false;
            if (orphansOnly) {
                MinionRuntime rt = runtimeOf(d);
                match = rt.status == Status.ISLAND_MISSING || !config.types.containsKey(d.type);
            }
            if (match) {
                returnToOwner(d, "admin purge");
                n++;
            }
        }
        return n;
    }

    /** Status of a minion as last seen by the ticker. */
    public Status statusOf(MinionData d) {
        return runtimeOf(d).status;
    }
}
