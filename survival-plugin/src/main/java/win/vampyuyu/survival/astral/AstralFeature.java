package win.vampyuyu.survival.astral;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The {@code astral} feature: the Astral Interiority (GDD 4.5, Act III), reserved to the players who defeated the Guardian of
 * every Realm ({@link AstralRealm}). Must be listed after the island, economy and boss features.
 */
public final class AstralFeature implements Feature {
    private AstralRealm astral;

    @Override
    public String id() {
        return "astral";
    }

    @Override
    public void enable(Core core) {
        astral = new AstralRealm(core);
        astral.start();
    }

    @Override
    public void disable() {
        if (astral != null) {
            astral.close();
            astral = null;
        }
    }
}
