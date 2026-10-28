package win.vampyuyu.survival.skills;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code skills} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class SkillsFeature implements Feature {
    @Override
    public String id() {
        return "skills";
    }

    @Override
    public void enable(Core core) {
        core.debug("skills feature stub enabled");
    }
}
