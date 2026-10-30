package win.vampyuyu.survival.utility;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure rules for home names and how many homes a player may own. */
public final class HomeBook {
    /** Permission prefix: {@code vampyuyu.sb.utility.homes.<n>} grants n homes. */
    public static final String LIMIT_PREFIX = "vampyuyu.sb.utility.homes.";
    public static final String UNLIMITED = LIMIT_PREFIX + "unlimited";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    private HomeBook() {}

    /** Lower cases and trims; null when the name is empty. Does not validate the characters. */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        return name.isEmpty() ? null : name;
    }

    /** Null when valid, otherwise an English reason. */
    public static String validate(String name, int maxLength) {
        if (name == null || name.isEmpty()) {
            return "A home needs a name.";
        }
        if (name.length() > maxLength) {
            return "Home names are at most " + maxLength + " characters long.";
        }
        if (!NAME.matcher(name).matches()) {
            return "Home names may only contain letters, digits, '-' and '_'.";
        }
        return null;
    }

    /**
     * Number of homes allowed: the biggest {@code vampyuyu.sb.utility.homes.<n>} among the granted permissions,
     * {@code defaultLimit} when there is none (or when it is bigger), never above {@code maxLimit}.
     *
     * @param granted permission names that are set to true for the player
     */
    public static int limit(Collection<String> granted, int defaultLimit, int maxLimit) {
        int best = Math.min(defaultLimit, maxLimit);
        for (String permission : granted) {
            if (permission == null || !permission.startsWith(LIMIT_PREFIX)) {
                continue;
            }
            String rest = permission.substring(LIMIT_PREFIX.length());
            if (rest.equals("unlimited")) {
                return maxLimit;
            }
            try {
                int n = Integer.parseInt(rest);
                if (n > best) {
                    best = Math.min(n, maxLimit);
                }
            } catch (NumberFormatException ignored) {
                // vampyuyu.sb.utility.homes.<something else>: not a count
            }
        }
        return best;
    }
}
