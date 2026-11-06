package peerless.campaign;

import com.fs.starfarer.api.Global;

/** LunaLib remains optional; quest rewards do not depend on it. */
public record ProgressionSettings(boolean enabled, int levelsPerPoint) {
    public static ProgressionSettings read() {
        if (Global.getSettings() == null || Global.getSettings().getModManager() == null
                || !Global.getSettings().getModManager().isModEnabled("lunalib"))
            return new ProgressionSettings(false, 10);
        return Luna.read();
    }
    private static final class Luna {
        static ProgressionSettings read() {
            Boolean enabled = lunalib.lunaSettings.LunaSettings.getBoolean("peerless_skills", "peerless_postCapProgression");
            Integer ratio = lunalib.lunaSettings.LunaSettings.getInt("peerless_skills", "peerless_levelsPerPoint");
            return new ProgressionSettings(Boolean.TRUE.equals(enabled), ratio == null ? 10 : Math.max(1, Math.min(100, ratio)));
        }
    }
}
