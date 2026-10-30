package win.vampyuyu.survival.utility;

import java.util.LinkedHashMap;
import java.util.Map;

/** Profile section {@code utility}: the player's homes and /tpa preference. Plain Gson class. */
public final class UtilityData {
    /** A saved home. The world is stored by name (worlds of Skyblock are recreated with the same names). */
    public static final class Home {
        public String world = "";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public long created;

        public Home() {}

        public Home(String world, double x, double y, double z, float yaw, float pitch, long created) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.created = created;
        }
    }

    /** Lower case name to home, in creation order. */
    public Map<String, Home> homes = new LinkedHashMap<>();
    /** True when the player does not want to receive /tpa and /tpahere requests. */
    public boolean tpaDisabled = false;
}
