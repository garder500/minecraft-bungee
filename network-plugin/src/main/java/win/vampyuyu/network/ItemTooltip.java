package win.vampyuyu.network;

import java.util.*;
import org.bukkit.Material;

/**
 * The lines of an item's tooltip (enchantments, lore, durability, custom data...), read from its NBT
 * components the way the client would draw them, so the panel can show a real hover tooltip.
 */
final class ItemTooltip {
    record Line(String text, String color, boolean italic) {}

    private static final String GRAY = "#aaaaaa", BLUE = "#5555ff", PURPLE = "#aa00aa", GREEN = "#55ff55", CURSE = "#ff5555", DARK_PURPLE = "#aa00aa";
    private static final int MAX_LINES = 40;

    private ItemTooltip() {}

    static String title(String key) {
        String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        name = name.substring(name.lastIndexOf('.') + 1);
        StringBuilder out = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    static String roman(int n) {
        if (n <= 0 || n > 10) return String.valueOf(n);
        return new String[] {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[n];
    }

    /** Plain text, colour and italic of a text component stored as NBT (a string or a compound). */
    record Text(String text, String color, Boolean italic) {}

    static Text component(PlayerNbt.Tag tag) {
        if (tag == null) return new Text("", null, null);
        if (tag.type() == 8) {
            String raw = (String) tag.value();
            if (raw.startsWith("{") || raw.startsWith("["))
                try { return json(com.google.gson.JsonParser.parseString(raw)); } catch (RuntimeException ignored) { /* plain text below */ }
            return new Text(unquote(raw), null, null);
        }
        if (tag.type() == 9) {
            StringBuilder out = new StringBuilder(); String color = null; Boolean italic = null;
            for (PlayerNbt.Tag child : ((PlayerNbt.Tags) tag.value()).values()) { Text t = component(child); out.append(t.text()); if (color == null) color = t.color(); if (italic == null) italic = t.italic(); }
            return new Text(out.toString(), color, italic);
        }
        if (tag.type() != 10) return new Text("", null, null);
        Map<String, PlayerNbt.Tag> fields = PlayerNbt.compound(tag);
        StringBuilder out = new StringBuilder(PlayerNbt.string(fields, "text", PlayerNbt.string(fields, "translate", "")));
        PlayerNbt.Tag extra = fields.get("extra");
        String color = PlayerNbt.string(fields, "color", null);
        Boolean italic = fields.get("italic") == null ? null : PlayerNbt.number(fields, "italic", 0) != 0;
        if (extra != null && extra.type() == 9)
            for (PlayerNbt.Tag child : ((PlayerNbt.Tags) extra.value()).values()) {
                Text t = component(child); out.append(t.text()); if (color == null) color = t.color(); if (italic == null) italic = t.italic();
            }
        return new Text(out.toString(), color, italic);
    }

    /** A text component written as JSON (a string, an object or an array). */
    private static Text json(com.google.gson.JsonElement e) {
        if (e.isJsonPrimitive()) return new Text(e.getAsString(), null, null);
        if (e.isJsonArray()) {
            StringBuilder out = new StringBuilder(); String color = null; Boolean italic = null;
            for (var child : e.getAsJsonArray()) { Text t = json(child); out.append(t.text()); if (color == null) color = t.color(); if (italic == null) italic = t.italic(); }
            return new Text(out.toString(), color, italic);
        }
        var o = e.getAsJsonObject();
        StringBuilder out = new StringBuilder(o.has("text") ? o.get("text").getAsString() : o.has("translate") ? o.get("translate").getAsString() : "");
        String color = o.has("color") ? o.get("color").getAsString() : null;
        Boolean italic = o.has("italic") ? o.get("italic").getAsBoolean() : null;
        if (o.has("extra"))
            for (var child : o.getAsJsonArray("extra")) { Text t = json(child); out.append(t.text()); if (color == null) color = t.color(); if (italic == null) italic = t.italic(); }
        return new Text(out.toString(), color, italic);
    }

    /** Some stores keep components as JSON text inside a string. */
    private static String unquote(String raw) {
        if (raw.startsWith("{") && raw.contains("\"text\"")) {
            int i = raw.indexOf("\"text\""); int a = raw.indexOf('"', raw.indexOf(':', i) + 1); int b = a < 0 ? -1 : raw.indexOf('"', a + 1);
            if (a >= 0 && b > a) return raw.substring(a + 1, b);
        }
        return raw;
    }

    private static final Map<String, String> NAMED = Map.ofEntries(
        Map.entry("black", "#000000"), Map.entry("dark_blue", "#0000aa"), Map.entry("dark_green", "#00aa00"), Map.entry("dark_aqua", "#00aaaa"),
        Map.entry("dark_red", "#aa0000"), Map.entry("dark_purple", "#aa00aa"), Map.entry("gold", "#ffaa00"), Map.entry("gray", "#aaaaaa"),
        Map.entry("dark_gray", "#555555"), Map.entry("blue", "#5555ff"), Map.entry("green", "#55ff55"), Map.entry("aqua", "#55ffff"),
        Map.entry("red", "#ff5555"), Map.entry("light_purple", "#ff55ff"), Map.entry("yellow", "#ffff55"), Map.entry("white", "#ffffff"));

    static String color(String value, String fallback) {
        if (value == null) return fallback;
        if (value.matches("#[0-9a-fA-F]{6}")) return value.toLowerCase(Locale.ROOT);
        return NAMED.getOrDefault(value, fallback);
    }

    private static String rarity(Map<String, PlayerNbt.Tag> components, boolean enchanted) {
        String rarity = PlayerNbt.string(components, "minecraft:rarity", "common");
        if (enchanted) rarity = switch (rarity) { case "common", "uncommon" -> "rare"; case "rare" -> "epic"; default -> rarity; };
        return switch (rarity) { case "uncommon" -> "#ffff55"; case "rare" -> "#55ffff"; case "epic" -> "#ff55ff"; default -> "#ffffff"; };
    }

    /** Name colour for the first tooltip line. */
    static String nameColor(Map<String, PlayerNbt.Tag> item) {
        Map<String, PlayerNbt.Tag> components = components(item);
        Text name = components.containsKey("minecraft:custom_name") ? component(components.get("minecraft:custom_name")) : component(components.get("minecraft:item_name"));
        return color(name.color(), rarity(components, hasEnchantments(components)));
    }

    static boolean italicName(Map<String, PlayerNbt.Tag> item) {
        Map<String, PlayerNbt.Tag> components = components(item);
        if (!components.containsKey("minecraft:custom_name")) return false;
        Boolean italic = component(components.get("minecraft:custom_name")).italic();
        return italic == null || italic;
    }

    private static Map<String, PlayerNbt.Tag> components(Map<String, PlayerNbt.Tag> item) {
        PlayerNbt.Tag c = item.get("components");
        return c != null && c.type() == 10 ? PlayerNbt.compound(c) : Map.of();
    }

    private static boolean hasEnchantments(Map<String, PlayerNbt.Tag> components) {
        return !levels(components.get("minecraft:enchantments")).isEmpty()
            || !levels(components.get("minecraft:stored_enchantments")).isEmpty()
            || PlayerNbt.number(components, "minecraft:enchantment_glint_override", 0) != 0;
    }

    /** Enchantment levels, in the 1.21.5+ flat compound or the older {levels: {...}} one. */
    private static Map<String, Integer> levels(PlayerNbt.Tag tag) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (tag == null || tag.type() != 10) return out;
        Map<String, PlayerNbt.Tag> map = PlayerNbt.compound(tag);
        if (map.get("levels") != null && map.get("levels").type() == 10) map = PlayerNbt.compound(map.get("levels"));
        for (var e : map.entrySet()) if (e.getValue().value() instanceof Number n) out.put(e.getKey(), n.intValue());
        return out;
    }

    private static String value(PlayerNbt.Tag tag) {
        if (tag == null) return "";
        return switch (tag.type()) {
            case 10 -> "{…}"; case 9 -> "[" + ((PlayerNbt.Tags) tag.value()).values().size() + " entries]";
            case 7, 11, 12 -> "[array]"; default -> String.valueOf(tag.value());
        };
    }

    /** Tooltip lines below the name; {@code maxLevel} tells which enchantments hide their level. */
    static List<Line> lines(Map<String, PlayerNbt.Tag> item, java.util.function.ToIntFunction<String> maxLevel) {
        Map<String, PlayerNbt.Tag> components = components(item);
        List<Line> out = new ArrayList<>();
        for (String key : List.of("minecraft:enchantments", "minecraft:stored_enchantments"))
            for (var e : levels(components.get(key)).entrySet()) {
                boolean curse = e.getKey().endsWith("_curse") || e.getKey().endsWith("curse_of_binding") || e.getKey().endsWith("curse_of_vanishing");
                int max = maxLevel.applyAsInt(e.getKey());
                String text = title(e.getKey()) + (max == 1 && e.getValue() == 1 ? "" : " " + roman(e.getValue()));
                out.add(new Line(text, curse ? CURSE : GRAY, false));
            }
        PlayerNbt.Tag lore = components.get("minecraft:lore");
        if (lore != null && lore.type() == 9)
            for (PlayerNbt.Tag line : ((PlayerNbt.Tags) lore.value()).values()) {
                Text t = component(line);
                out.add(new Line(t.text(), color(t.color(), DARK_PURPLE), t.italic() == null || t.italic()));
            }
        PlayerNbt.Tag attributes = components.get("minecraft:attribute_modifiers");
        List<PlayerNbt.Tag> mods = new ArrayList<>();
        if (attributes != null && attributes.type() == 9) mods.addAll(((PlayerNbt.Tags) attributes.value()).values());
        else if (attributes != null && attributes.type() == 10 && PlayerNbt.compound(attributes).get("modifiers") != null && PlayerNbt.compound(attributes).get("modifiers").type() == 9)
            mods.addAll(((PlayerNbt.Tags) PlayerNbt.compound(attributes).get("modifiers").value()).values());
        for (PlayerNbt.Tag mod : mods) {
            if (mod.type() != 10) continue;
            Map<String, PlayerNbt.Tag> m = PlayerNbt.compound(mod);
            double amount = m.get("amount") != null && m.get("amount").value() instanceof Number n ? n.doubleValue() : 0;
            String op = PlayerNbt.string(m, "operation", "add_value");
            String shown = op.equals("add_value") ? trim(amount) : trim(amount * 100) + "%";
            out.add(new Line((amount >= 0 ? "+" : "") + shown + " " + title(PlayerNbt.string(m, "type", "attribute")) + " (" + title(PlayerNbt.string(m, "slot", "any")) + ")", amount >= 0 ? BLUE : CURSE, false));
        }
        if (components.containsKey("minecraft:unbreakable")) out.add(new Line("Unbreakable", BLUE, false));
        String id = PlayerNbt.string(item, "id", "");
        Material material = id.isEmpty() ? null : Material.matchMaterial(id);
        int max = PlayerNbt.number(components, "minecraft:max_damage", material == null ? 0 : material.getMaxDurability());
        if (max > 0 && !components.containsKey("minecraft:unbreakable")) {
            int damage = PlayerNbt.number(components, "minecraft:damage", 0);
            out.add(new Line("Durability: " + Math.max(0, max - damage) + " / " + max, GRAY, false));
        }
        PlayerNbt.Tag custom = components.get("minecraft:custom_data");
        if (custom != null && custom.type() == 10) {
            Map<String, PlayerNbt.Tag> data = PlayerNbt.compound(custom);
            if (!data.isEmpty()) {
                out.add(new Line("Custom data", "#ff55ff", false));
                for (var e : data.entrySet()) {
                    if (e.getKey().equals("PublicBukkitValues") && e.getValue().type() == 10)
                        for (var b : PlayerNbt.compound(e.getValue()).entrySet()) out.add(new Line(" " + b.getKey() + ": " + value(b.getValue()), "#777777", false));
                    else out.add(new Line(" " + e.getKey() + ": " + value(e.getValue()), "#777777", false));
                    if (out.size() >= MAX_LINES) break;
                }
            }
        }
        for (String key : List.of("minecraft:potion_contents")) {
            PlayerNbt.Tag potion = components.get(key);
            if (potion != null && potion.type() == 10) {
                String id2 = PlayerNbt.string(PlayerNbt.compound(potion), "potion", "");
                if (!id2.isEmpty()) out.add(new Line(title(id2), BLUE, false));
            }
        }
        return out.size() > MAX_LINES ? out.subList(0, MAX_LINES) : out;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
