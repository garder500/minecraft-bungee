package win.vampyuyu.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

final class WorldSelector implements Listener {
    // Keep the in-game selector aligned with the panel's burgundy/ivory/gold palette.
    static final TextColor WINE = TextColor.color(0x8e2e35);
    static final TextColor ROSE = TextColor.color(0xd58b8e);
    static final TextColor IVORY = TextColor.color(0xf5e9cf);
    static final TextColor GOLD = TextColor.color(0xe9d99b);
    static final TextColor MUTED = TextColor.color(0xc5a69b);
    /** Skyblock's accent: the island's grass green. */
    static final TextColor SKY = TextColor.color(0x8ee06e);
    private static final int CREATIVE_SLOT = 11;
    private static final int SKYBLOCK_SLOT = 13;
    private static final int SURVIVAL_SLOT = 15;
    private static final int LOBBY_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    /** "VAMPYUYU " in bold rose, the lead of every chat line of the selector. */
    static Component prefix() {
        return text("VAMPYUYU ", ROSE).decorate(TextDecoration.BOLD);
    }

    private static Component brand() {
        return text("Vampyuyu", WINE).decorate(TextDecoration.BOLD)
                .append(text(" · ", MUTED)).append(text("Worlds", GOLD));
    }

    private static ItemStack item(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(List.of(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    // Painted layout: Creative and Survival are 3x3 arches; the 3x2 card between them is Skyblock, with the lobby link under it.
    static final int[] CREATIVE_AREA = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    static final int[] SURVIVAL_AREA = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    static final int[] LOBBY_AREA = {21, 22, 23};
    static final int[] SKYBLOCK_AREA = {3, 4, 5, 12, 13, 14};

    private static boolean in(int[] area, int slot) {
        for (int candidate : area) if (candidate == slot) return true;
        return false;
    }

    static final class Menu implements InventoryHolder {
        private final Inventory inventory;
        final boolean skinned;

        /** @param routing false when the Skyblock handoff is unreachable: the card then says so */
        Menu(boolean skinned, boolean routing) {
            this.skinned = skinned;
            if (skinned) {
                inventory = Bukkit.createInventory(this, 27, MenuPack.title(MenuPack.LOBBY, MenuPack.ROWS_27, Component.empty()));
                fill(CREATIVE_AREA, creativeCard());
                fill(SURVIVAL_AREA, survivalCard());
                fill(LOBBY_AREA, lobbyCard());
                fill(SKYBLOCK_AREA, skyblockCard(routing));
                return;
            }
            inventory = Bukkit.createInventory(this, 27, brand());
            ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
            ItemStack wine = item(Material.RED_STAINED_GLASS_PANE, Component.empty());
            ItemStack ivory = item(Material.WHITE_STAINED_GLASS_PANE, Component.empty());
            ItemStack gold = item(Material.YELLOW_STAINED_GLASS_PANE, Component.empty());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, dark);
            }
            // A symmetrical red frame, with pale accents around the two world cards.
            for (int slot : new int[] {0, 1, 2, 6, 7, 8, 9, 17, 18, 19, 20, 24, 25, 26}) {
                inventory.setItem(slot, wine);
            }
            for (int slot : new int[] {3, 5, 21, 23}) {
                inventory.setItem(slot, ivory);
            }
            inventory.setItem(4, gold);
            inventory.setItem(CREATIVE_SLOT, creativeCard());
            inventory.setItem(SKYBLOCK_SLOT, skyblockCard(routing));
            inventory.setItem(SURVIVAL_SLOT, survivalCard());
            inventory.setItem(LOBBY_SLOT, item(Material.COMPASS,
                    text("LOBBY", ROSE).decorate(TextDecoration.BOLD),
                    text("Return to the network hub.", MUTED)));
            inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                    text("Close menu", ROSE),
                    text("Stay in your current world.", MUTED)));
        }

        private void fill(int[] area, ItemStack card) {
            for (int slot : area) inventory.setItem(slot, MenuPack.invisible(card.clone()));
        }

        private static ItemStack creativeCard() {
            return item(Material.BRICKS,
                    text("CREATIVE", ROSE).decorate(TextDecoration.BOLD),
                    text("A world for your imagination.", IVORY),
                    Component.empty(),
                    text("Build freely. Dream bigger.", MUTED),
                    text("Creative mode · Free building", MUTED),
                    Component.empty(),
                    text("Click to enter →", GOLD));
        }

        private static ItemStack survivalCard() {
            return item(Material.GOLDEN_PICKAXE,
                    text("SURVIVAL", GOLD).decorate(TextDecoration.BOLD),
                    text("An adventure worth sharing.", IVORY),
                    Component.empty(),
                    text("Gather. Explore. Make it yours.", MUTED),
                    text("Survival mode · Open exploration", MUTED),
                    Component.empty(),
                    text("Click to enter →", GOLD));
        }

        private static ItemStack lobbyCard() {
            return item(Material.COMPASS,
                    text("LOBBY", ROSE).decorate(TextDecoration.BOLD),
                    text("Return to the network hub.", MUTED),
                    Component.empty(),
                    text("Click to return →", GOLD));
        }

        static ItemStack skyblockCard(boolean routing) {
            List<Component> lore = new ArrayList<>(List.of(
                    text("Build your island in the sky.", IVORY),
                    Component.empty(),
                    text("Skills, minions, bosses and dungeons", MUTED),
                    text("in one co-op adventure.", MUTED),
                    Component.empty()));
            if (!routing) {
                lore.add(text("Skyblock routing is offline right now:", ROSE));
                lore.add(text("you will join the Survival server instead.", ROSE));
                lore.add(Component.empty());
            }
            lore.add(text("Click to enter →", GOLD));
            return item(Material.GRASS_BLOCK, text("SKYBLOCK", SKY).decorate(TextDecoration.BOLD), lore.toArray(Component[]::new));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final JavaPlugin plugin;
    private final MenuPack pack;
    private final SkyblockHandoff handoff;
    private final SkyblockRoute skyblock;

    WorldSelector(JavaPlugin plugin, MenuPack pack, String server) {
        this(plugin, pack, server, SkyblockHandoff.load(plugin));
    }

    WorldSelector(JavaPlugin plugin, MenuPack pack, String server, SkyblockHandoff handoff) {
        this.plugin = plugin;
        this.pack = pack;
        this.handoff = handoff;
        this.skyblock = new SkyblockRoute(plugin, server, handoff);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("menu")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                open(player);
            } else {
                sender.sendMessage("Use this command in game.");
            }
            return true;
        });
    }

    void close() {
        handoff.close();
    }

    private void open(Player player) {
        player.openInventory(new Menu(pack != null && pack.active(player), handoff.available()).getInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.1f);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!menu.skinned && slot == CLOSE_SLOT) {
            // Inventory view changes must run after the click event has completed.
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        Target target = destination(menu.skinned, slot);
        if (target == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (target == Target.SKYBLOCK) {
                skyblock.go(player);
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.3f);
            if (!connect(plugin, player, target.server())) return;
            player.closeInventory();
            player.sendMessage(prefix()
                    .append(text("Connecting to ", IVORY))
                    .append(text(target.label(), GOLD))
                    .append(text("… If unavailable, please try again shortly.", MUTED)));
        });
    }

    /** What a card leads to. Skyblock lives on the survival server, so it shares the backend name with Survival. */
    enum Target {
        CREATIVE("creative", "Creative"),
        SURVIVAL("survival", "Survival"),
        SKYBLOCK("survival", "Skyblock"),
        LOBBY("lobby", "Lobby");

        private final String server;
        private final String label;

        Target(String server, String label) {
            this.server = server;
            this.label = label;
        }

        String server() {
            return server;
        }

        String label() {
            return label;
        }
    }

    /** The BungeeCord "Connect" message body. */
    static byte[] connectMessage(String server) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeUTF("Connect");
        data.writeUTF(server);
        return bytes.toByteArray();
    }

    /** Sends the Connect message; false (and a log line) when it cannot be encoded. */
    static boolean connect(JavaPlugin plugin, Player player, String server) {
        try {
            player.sendPluginMessage(plugin, "BungeeCord", connectMessage(server));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to encode server selection");
            return false;
        }
    }

    /** Maps a clicked top-inventory slot to a card, or null for decoration. */
    static Target destination(boolean skinned, int slot) {
        if (skinned) {
            return in(CREATIVE_AREA, slot) ? Target.CREATIVE
                    : in(SURVIVAL_AREA, slot) ? Target.SURVIVAL
                    : in(SKYBLOCK_AREA, slot) ? Target.SKYBLOCK
                    : in(LOBBY_AREA, slot) ? Target.LOBBY : null;
        }
        return slot == CREATIVE_SLOT ? Target.CREATIVE
                : slot == SURVIVAL_SLOT ? Target.SURVIVAL
                : slot == SKYBLOCK_SLOT ? Target.SKYBLOCK
                : slot == LOBBY_SLOT ? Target.LOBBY : null;
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
}
