package win.vampyuyu.survival.wings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.WingsApi;
import win.vampyuyu.survival.core.Core;

/**
 * The Wings rules: who owns which item, grants, claims, upgrades. Everything here runs on the main thread except
 * {@link #hasReceived} and {@link #grantOnce} which marshal themselves.
 *
 * <p>Identity model: the profile holds the one valid item id ({@code activeId}) and the level. An item only works
 * when it carries the owner's UUID and the active id. Granting, claiming and returning after a loss rotate the id,
 * so every older copy (a duplicate, a stale stack) becomes inert and is removed the next time it is seen.
 */
public final class WingsService implements WingsApi {
    /** Outcome of {@link #upgrade}. */
    public enum UpgradeResult { OK, NO_WINGS, MAX_LEVEL, MISSING_CORES, MISSING_COINS }

    /** What an upgrade attempt did. {@code cost} is null at the top level. */
    public record UpgradeOutcome(UpgradeResult result, int level, WingsSettings.@Nullable Upgrade cost) {}

    /** Outcome of {@link #claim}. */
    public enum ClaimResult { OK, NEVER_RECEIVED, HAS_WINGS, COOLDOWN, MISSING_COINS, RESTORED }

    private final Core core;
    private final WingsItems wingsItems;
    private volatile WingsSettings settings;
    private FlightManager flight;

    public WingsService(Core core, WingsItems wingsItems, WingsSettings settings) {
        this.core = core;
        this.wingsItems = wingsItems;
        this.settings = settings;
    }

    FlightManager flight() {
        return flight;
    }

    void flight(FlightManager flight) {
        this.flight = flight;
    }

    void settings(WingsSettings settings) {
        this.settings = settings;
    }

    public WingsSettings settings() {
        return settings;
    }

    public Core core() {
        return core;
    }

    public WingsItems wingsItems() {
        return wingsItems;
    }

    /** The player's section. Online players only (main thread). */
    public WingsData data(UUID id) {
        return core.profiles().section(id, "wings", WingsData.class, WingsData::new);
    }

    private void dirty(UUID id) {
        core.profiles().markDirty(id);
    }

    // ---------------------------------------------------------------- WingsApi

    @Override
    public ItemStack create() {
        return wingsItems.createUnbound(1);
    }

    @Override
    public boolean isWings(ItemStack stack) {
        return wingsItems.isWings(stack);
    }

    @Override
    public boolean isWearing(Player player) {
        return wornLevel(player) > 0;
    }

    @Override
    public boolean grantOnce(Player player, String source) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            core.scheduler().nextTick(() -> {
                try {
                    done.complete(grantOnce(player, source));
                } catch (RuntimeException e) {
                    done.completeExceptionally(e);
                }
            });
            try {
                return done.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Wings grant failed", e);
            }
        }
        String cleanSource = clean(source);
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            boolean[] granted = {false};
            core.profiles().edit(id, "wings", WingsData.class, WingsData::new, d -> {
                if (!d.received) {
                    d.received = true;
                    d.source = cleanSource;
                    d.grantedAt = System.currentTimeMillis();
                    d.level = 1;
                    d.activeId = UUID.randomUUID().toString();
                    d.pendingReturn = true;
                    granted[0] = true;
                }
            });
            return granted[0];
        }
        WingsData d = data(id);
        if (d.received) {
            return false;
        }
        d.received = true;
        d.source = cleanSource;
        d.grantedAt = System.currentTimeMillis();
        d.level = 1;
        d.activeId = UUID.randomUUID().toString();
        d.pendingReturn = false;
        dirty(id);
        deliver(player, build(player, d));
        announce(player);
        core.debug("wings granted to " + player.getName() + " by " + cleanSource);
        return true;
    }

    @Override
    public boolean hasReceived(UUID player) {
        // an offline player that is not cached costs one small disk read (see PlayerProfiles)
        return data(player).received;
    }

    private static String clean(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        String s = source.strip();
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    // ---------------------------------------------------------------- items

    /** Level of the valid Wings worn in the chest slot, 0 when none (or unbound, foreign, stale). */
    public int wornLevel(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() != org.bukkit.Material.ELYTRA) {
            return 0;  // cheap exit for the common case, before any item meta is built
        }
        WingsItems.Tag tag = wingsItems.read(chest);
        if (tag == null || tag.unbound() || !player.getUniqueId().equals(tag.owner())) {
            return 0;
        }
        WingsData d = data(player.getUniqueId());
        if (d.activeId.isEmpty() || !d.activeId.equals(tag.uuid())) {
            return 0;
        }
        return settings.clampLevel(d.level);
    }

    /** Builds the one valid item of a player from the profile. */
    public ItemStack build(Player player, WingsData d) {
        return wingsItems.create(settings.clampLevel(d.level), player.getUniqueId(), player.getName(), d.activeId);
    }

    /** Puts the item in the inventory, dropping what does not fit at the player's feet. */
    public void deliver(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    private void announce(Player player) {
        player.showTitle(Title.title(Component.text("The Wings", NamedTextColor.LIGHT_PURPLE),
            Component.text("They are yours. Wear them and take flight.", NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        core.msg().ok(player, "You received The Wings! Wear them in the chest slot, open /wings to upgrade them.");
    }

    /** Index in {@code getContents()} of the valid Wings of the player, -1 when there is none. */
    public int findValidSlot(Player player) {
        WingsData d = data(player.getUniqueId());
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            WingsItems.Tag tag = wingsItems.read(contents[i]);
            if (tag != null && !tag.unbound() && player.getUniqueId().equals(tag.owner()) && !d.activeId.isEmpty()
                && d.activeId.equals(tag.uuid())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Enforces ownership on everything the player carries: binds unbound items, sends foreign items home, deletes
     * stale and duplicate copies, rebuilds a copy whose level is out of date, and hands back a pending return.
     */
    public void scan(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        UUID id = player.getUniqueId();
        WingsData d = data(id);
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean valid = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            WingsItems.Tag tag = wingsItems.read(stack);
            if (tag == null) {
                if (stack != null && (WingsGuard.isShulker(stack) || WingsGuard.isBundle(stack))
                    && stripNested(player, stack)) {
                    inv.setItem(i, stack);
                    core.msg().warn(player, "The Wings cannot be stored in shulker boxes or bundles: they came back to you.");
                }
                continue;
            }
            if (tag.unbound()) {
                if (!d.received) {
                    d.received = true;
                    d.source = "bound";
                    d.grantedAt = System.currentTimeMillis();
                    d.level = settings.clampLevel(tag.level());
                    d.activeId = UUID.randomUUID().toString();
                    d.pendingReturn = false;
                    dirty(id);
                    inv.setItem(i, build(player, d));
                    valid = true;
                    announce(player);
                } else {
                    inv.setItem(i, null);
                    core.msg().warn(player, "Unbound Wings crumbled to dust: you already own The Wings.");
                }
                continue;
            }
            if (!id.equals(tag.owner())) {
                inv.setItem(i, null);
                returnToOwner(tag.owner(), tag.uuid(), stack);
                core.msg().warn(player, "Those Wings are bound to someone else and flew back to their owner.");
                continue;
            }
            if (d.activeId.isEmpty() || !d.activeId.equals(tag.uuid())) {
                inv.setItem(i, null);
                core.msg().warn(player, "An outdated pair of Wings turned to dust.");
                continue;
            }
            if (valid) {
                inv.setItem(i, null);  // a duplicate of the valid item
                continue;
            }
            valid = true;
            if (tag.level() != settings.clampLevel(d.level)) {
                inv.setItem(i, build(player, d));
            }
        }
        if (d.pendingReturn) {
            if (valid) {
                d.pendingReturn = false;
                dirty(id);
            } else if (!d.activeId.isEmpty() || d.received) {
                d.pendingReturn = false;
                d.activeId = UUID.randomUUID().toString();
                dirty(id);
                deliver(player, build(player, d));
                core.msg().ok(player, "Your Wings found their way back to you.");
            }
        }
    }

    /**
     * Removes Wings hidden inside a shulker box or a bundle (they could be traded inside it). The valid copy of the
     * holder goes back to the owner, anything else is lost. Returns true when the stack was changed.
     */
    boolean stripNested(Player holder, ItemStack container) {
        org.bukkit.inventory.meta.ItemMeta meta = container.getItemMeta();
        List<ItemStack> found = new ArrayList<>();
        if (meta instanceof org.bukkit.inventory.meta.BundleMeta bundle) {
            List<ItemStack> keep = new ArrayList<>();
            for (ItemStack inside : bundle.getItems()) {
                (wingsItems.isWings(inside) ? found : keep).add(inside);
            }
            if (found.isEmpty()) {
                return false;
            }
            bundle.setItems(keep);
        } else if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta bsm
            && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
            var inside = box.getInventory();
            for (int i = 0; i < inside.getSize(); i++) {
                ItemStack item = inside.getItem(i);
                if (wingsItems.isWings(item)) {
                    found.add(item);
                    inside.setItem(i, null);
                }
            }
            if (found.isEmpty()) {
                return false;
            }
            bsm.setBlockState(box);
        } else {
            return false;
        }
        container.setItemMeta(meta);
        for (ItemStack wings : found) {
            WingsItems.Tag tag = wingsItems.read(wings);
            if (tag != null && tag.owner() != null) {
                returnToOwner(tag.owner(), tag.uuid(), wings);
            }
        }
        return true;
    }

    /** Rebuilds the valid Wings of every online player (after a config reload changed the lore). */
    public void rebuildAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int slot = findValidSlot(p);
            if (slot >= 0) {
                p.getInventory().setItem(slot, build(p, data(p.getUniqueId())));
            }
        }
    }

    /** Sends a wings stack that ended up with a stranger back to its owner (online: the inventory; offline: next join). */
    public void returnToOwner(UUID owner, @Nullable String itemUuid, ItemStack stack) {
        Player online = Bukkit.getPlayer(owner);
        if (online != null && online.isOnline()) {
            WingsData d = data(owner);
            if (itemUuid != null && itemUuid.equals(d.activeId)) {
                if (online.isDead()) {
                    d.pendingReturn = true;
                    dirty(owner);
                } else {
                    deliver(online, stack.clone());
                    core.msg().info(online, "Your Wings returned to you.");
                }
            }
            return;
        }
        core.scheduler().async(() -> core.profiles().edit(owner, "wings", WingsData.class, WingsData::new, d -> {
            if (itemUuid != null && itemUuid.equals(d.activeId)) {
                d.pendingReturn = true;
            }
        }));
    }

    /** Removes every Wings item (valid or not) from the inventory and the ender chest. Returns how many stacks. */
    public int removeAllWings(Player player) {
        int removed = 0;
        for (var inv : List.of(player.getInventory(), player.getEnderChest())) {
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < contents.length; i++) {
                if (wingsItems.isWings(contents[i])) {
                    inv.setItem(i, null);
                    removed++;
                }
            }
        }
        return removed;
    }

    // ---------------------------------------------------------------- admin

    /** Admin give: replaces whatever the player had (older copies turn inert) with a fresh item of {@code level}. */
    public void adminGive(Player target, @Nullable Integer level, String source) {
        UUID id = target.getUniqueId();
        WingsData d = data(id);
        if (!d.received) {
            d.received = true;
            d.source = clean(source);
            d.grantedAt = System.currentTimeMillis();
        }
        d.level = settings.clampLevel(level != null ? level : (d.activeId.isEmpty() ? 1 : d.level));
        d.activeId = UUID.randomUUID().toString();
        d.pendingReturn = false;
        dirty(id);
        removeAllWings(target);
        deliver(target, build(target, d));
        if (flight != null) {
            flight.refresh(target);
        }
    }

    /**
     * Takes the Wings away: the active id is cleared so every item turns inert. {@code reset} also forgets that the
     * player ever received them (so {@code grantOnce} works again).
     */
    public void revoke(UUID id, boolean reset) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            WingsData d = data(id);
            applyRevoke(d, reset);
            dirty(id);
            removeAllWings(online);
            if (flight != null) {
                flight.refresh(online);
            }
        } else {
            core.scheduler().async(() -> core.profiles().edit(id, "wings", WingsData.class, WingsData::new,
                d -> applyRevoke(d, reset)));
        }
    }

    static void applyRevoke(WingsData d, boolean reset) {
        d.activeId = "";
        d.pendingReturn = false;
        if (reset) {
            d.received = false;
            d.level = 1;
            d.source = "";
            d.grantedAt = 0;
            d.energy = -1;
        }
    }

    // ---------------------------------------------------------------- claim

    /** Pure rules of a claim, shared by the command and the tests. */
    static ClaimResult claimCheck(WingsData d, boolean hasValidItem, long now, long cooldownMillis) {
        if (!d.received) {
            return ClaimResult.NEVER_RECEIVED;
        }
        if (hasValidItem) {
            return ClaimResult.HAS_WINGS;
        }
        if (d.pendingReturn) {
            return ClaimResult.RESTORED;
        }
        if (d.lastClaim > 0 && now - d.lastClaim < cooldownMillis) {
            return ClaimResult.COOLDOWN;
        }
        return ClaimResult.OK;
    }

    /** Milliseconds before the next claim is possible (0 = now). */
    public long claimRemaining(Player player) {
        WingsData d = data(player.getUniqueId());
        if (d.lastClaim <= 0) {
            return 0;
        }
        return Math.max(0, settings.claimCooldownMillis - (System.currentTimeMillis() - d.lastClaim));
    }

    /** True when the player carries or stores a valid Wings (inventory or ender chest). */
    public boolean hasValidWings(Player player) {
        if (findValidSlot(player) >= 0) {
            return true;
        }
        WingsData d = data(player.getUniqueId());
        for (ItemStack s : player.getEnderChest().getContents()) {
            WingsItems.Tag tag = wingsItems.read(s);
            if (tag != null && !tag.unbound() && player.getUniqueId().equals(tag.owner()) && !d.activeId.isEmpty()
                && d.activeId.equals(tag.uuid())) {
                return true;
            }
        }
        return false;
    }

    /** Recovers lost Wings: a new id, the old copies turn inert. Costs coins and has a cooldown. */
    public ClaimResult claim(Player player) {
        UUID id = player.getUniqueId();
        WingsData d = data(id);
        long now = System.currentTimeMillis();
        ClaimResult check = claimCheck(d, hasValidWings(player), now, settings.claimCooldownMillis);
        if (check == ClaimResult.RESTORED) {
            scan(player);
            return ClaimResult.RESTORED;
        }
        if (check != ClaimResult.OK) {
            return check;
        }
        EconomyApi eco = core.service(EconomyApi.class).orElse(null);
        double cost = settings.claimCoins;
        if (eco != null && cost > 0 && !eco.take(id, cost, "wings:claim")) {
            return ClaimResult.MISSING_COINS;
        }
        d.activeId = UUID.randomUUID().toString();
        d.lastClaim = now;
        d.claims++;
        d.pendingReturn = false;
        dirty(id);
        deliver(player, build(player, d));
        return ClaimResult.OK;
    }

    // ---------------------------------------------------------------- upgrade

    /** Number of cores of {@code tier} in the player's inventory. */
    public int countCores(Player player, int tier) {
        int total = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && wingsItems.coreTier(s) == tier) {
                total += s.getAmount();
            }
        }
        return total;
    }

    /** Removes {@code amount} cores of {@code tier}; false (nothing removed) when there are not enough. */
    public boolean takeCores(Player player, int tier, int amount) {
        if (countCores(player, tier) < amount) {
            return false;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < storage.length && left > 0; i++) {
            ItemStack s = storage[i];
            if (s == null || wingsItems.coreTier(s) != tier) {
                continue;
            }
            int take = Math.min(left, s.getAmount());
            left -= take;
            if (take == s.getAmount()) {
                inv.setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
                inv.setItem(i, s);
            }
        }
        return left == 0;
    }

    /** Consumes the cores (and coins) of the next level. All or nothing. */
    public UpgradeOutcome upgrade(Player player) {
        UUID id = player.getUniqueId();
        WingsData d = data(id);
        int slot = d.received && !d.activeId.isEmpty() ? findValidSlot(player) : -1;
        if (slot < 0) {
            return new UpgradeOutcome(UpgradeResult.NO_WINGS, d.level, null);
        }
        int level = settings.clampLevel(d.level);
        if (level >= settings.maxLevel) {
            return new UpgradeOutcome(UpgradeResult.MAX_LEVEL, level, null);
        }
        WingsSettings.Upgrade cost = settings.upgradeFrom(level);
        if (countCores(player, cost.coreTier()) < cost.amount()) {
            return new UpgradeOutcome(UpgradeResult.MISSING_CORES, level, cost);
        }
        EconomyApi eco = core.service(EconomyApi.class).orElse(null);
        boolean pay = eco != null && cost.coins() > 0;
        if (pay && !eco.has(id, cost.coins())) {
            return new UpgradeOutcome(UpgradeResult.MISSING_COINS, level, cost);
        }
        if (!takeCores(player, cost.coreTier(), cost.amount())) {
            return new UpgradeOutcome(UpgradeResult.MISSING_CORES, level, cost);
        }
        if (pay && !eco.take(id, cost.coins(), "wings:upgrade")) {
            deliver(player, wingsItems.createCore(cost.coreTier(), cost.amount()));  // give the cores back
            return new UpgradeOutcome(UpgradeResult.MISSING_COINS, level, cost);
        }
        d.level = level + 1;
        d.coresUsed += cost.amount();
        dirty(id);
        // the slot may have shifted when cores were removed from the same inventory: look it up again
        int fresh = findValidSlot(player);
        if (fresh >= 0) {
            player.getInventory().setItem(fresh, build(player, d));
        }
        if (flight != null) {
            flight.refresh(player);
        }
        Location at = player.getLocation();
        player.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        return new UpgradeOutcome(UpgradeResult.OK, level + 1, cost);
    }

    // ---------------------------------------------------------------- info

    /** Human readable summary for /wings info (works for offline players if the profile is loaded). */
    public List<String> describe(OfflinePlayer target) {
        List<String> lines = new ArrayList<>();
        WingsData d = data(target.getUniqueId());
        lines.add("Wings of " + (target.getName() == null ? target.getUniqueId().toString() : target.getName()));
        lines.add("Received: " + (d.received ? "yes (" + d.source + ")" : "no"));
        if (d.received) {
            lines.add("Level: " + settings.clampLevel(d.level) + "/" + settings.maxLevel
                + (d.activeId.isEmpty() ? " (revoked)" : ""));
            lines.add("Item id: " + (d.activeId.isEmpty() ? "-" : d.activeId));
            lines.add("Cores used: " + d.coresUsed + ", claims: " + d.claims
                + (d.pendingReturn ? ", a copy is waiting to be returned" : ""));
        }
        return lines;
    }
}
