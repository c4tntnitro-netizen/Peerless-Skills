package peerless.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import peerless.api.PeerlessSkills;

/** Durable quest receipts and optional progress measured in post-cap XP bars. */
public final class PeerlessProgression {
    private static final String GALATIA = "$peerless_skills_galatia_reward_v1";
    private static final String PROGRESS = "$peerless_skills_postcap_levels_v1";
    private static final String SEQUENCE = "$peerless_skills_postcap_awards_v1";
    private static final String XP = "$peerless_skills_postcap_last_xp_v1";
    private static final String CAP = "$peerless_skills_postcap_last_cap_v1";

    public static void install() {
        ProgressionSettings.listen();
        var settings = ProgressionSettings.read();
        NativeLevelProgress.configure(settings.enabled());
        rebase(Global.getSettings().getLevelupPlugin());
    }
    static void settingsChanged() {
        if (Global.getSector() == null || Global.getSector().getPlayerStats() == null) return;
        // Settings and console dialogs can suspend campaign scripts. Attach/detach now,
        // before another XP award can wrap; changing only the ratio must not rebase XP.
        if (NativeLevelProgress.configure(ProgressionSettings.read().enabled()))
            rebase(Global.getSettings().getLevelupPlugin());
    }
    /** Read-only console snapshot; never installs the hook, rebases XP or pays out points. */
    public static String diagnostics() {
        var settings = ProgressionSettings.read();
        var stats = Global.getSector().getPlayerStats();
        var levels = Global.getSettings().getLevelupPlugin();
        int cap = levels == null ? -1 : levels.getMaxLevel();
        long cycle = levels == null ? 0L : levels.getXPForLevel(cap + 1) - levels.getXPForLevel(cap);
        return "Post-cap progression: " + (settings.enabled() ? "enabled" : "disabled")
                + "\nXP tracker: " + (NativeLevelProgress.isInstalled(levels) ? "attached" : "not attached")
                + "\nPlayer level: " + stats.getLevel() + "; cap: " + cap
                + "\nXP: " + stats.getXP() + "; last recorded XP: " + number(XP, -1L)
                + "; XP per bar: " + cycle
                + "\nBanked progress: " + String.format(java.util.Locale.ROOT, "%.4f", number(PROGRESS, 0d).doubleValue())
                + " / " + settings.levelsPerPoint() + " bars per Peerless point"
                + "\nXP points awarded: " + number(SEQUENCE, 0L)
                + "; unspent Peerless points: " + PeerlessSkills.getPoints();
    }
    public static void advance() {
        if (Global.getSector() == null || Global.getSector().getPlayerStats() == null) return;
        var memory = Global.getSector().getMemoryWithoutUpdate();
        if (memory.getBoolean("$gaATG_missionCompleted")) PeerlessSkills.awardOnce(GALATIA);
        var settings = ProgressionSettings.read();
        if (NativeLevelProgress.configure(settings.enabled())) rebase(Global.getSettings().getLevelupPlugin());
        if (!settings.enabled()) return;
        var levels = Global.getSettings().getLevelupPlugin();
        if (levels == null) return;
        if (number(CAP, -1).intValue() != levels.getMaxLevel()) rebase(levels);
        double progress = number(PROGRESS, 0d).doubleValue();
        if (progress + 1e-9 < settings.levelsPerPoint()) return;
        long next = number(SEQUENCE, 0L).longValue() + 1L;
        String receipt = "$peerless_skills_postcap_award_v1_" + next;
        // At most one payout per frame; surplus progress remains banked.
        if (PeerlessSkills.awardOnce(receipt) || memory.getBoolean(receipt)) {
            memory.set(SEQUENCE, next);
            memory.set(PROGRESS, Math.max(0d, progress - settings.levelsPerPoint()));
        }
    }
    static void rebase(LevelupPlugin levels) {
        if (levels == null || Global.getSector() == null || Global.getSector().getPlayerStats() == null) return;
        var memory = Global.getSector().getMemoryWithoutUpdate();
        memory.set(XP, Global.getSector().getPlayerStats().getXP());
        memory.set(CAP, levels.getMaxLevel());
    }
    static void observeBeforeXPWrap(LevelupPlugin levels) {
        if (Global.getSector() == null || Global.getSector().getPlayerStats() == null) return;
        var stats = Global.getSector().getPlayerStats();
        int cap = levels.getMaxLevel();
        if (stats.getLevel() < cap) return;
        long base = levels.getXPForLevel(cap), end = levels.getXPForLevel(cap + 1);
        if (base < 0 || end <= base) return;
        long cycle = end - base, raw = stats.getXP();
        var memory = Global.getSector().getMemoryWithoutUpdate();
        if (number(CAP, -1).intValue() != cap || memory.get(XP) == null) { rebase(levels); return; }
        long previous = number(XP, raw).longValue();
        long gained = raw > Math.max(base, previous) ? raw - Math.max(base, previous) : 0L;
        double progress = number(PROGRESS, 0d).doubleValue();
        memory.set(PROGRESS, progress + (double)gained / cycle);
        // Reward size follows the configured cap, while native XP wrapping follows the actual level.
        memory.set(XP, NativeLevelProgress.xpAfterUpdate(levels, stats.getLevel(), raw));
    }
    private static Number number(String key, Number fallback) {
        Object value = Global.getSector().getMemoryWithoutUpdate().get(key);
        return value instanceof Number ? (Number)value : fallback;
    }
}
