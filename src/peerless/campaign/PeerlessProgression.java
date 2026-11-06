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
        var settings = ProgressionSettings.read();
        NativeLevelProgress.configure(settings.enabled());
        rebase(Global.getSettings().getLevelupPlugin());
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
        // Native CharacterStats immediately reduces XP to this range after this callback.
        memory.set(XP, raw >= base ? base + (raw - base) % cycle : raw);
    }
    private static Number number(String key, Number fallback) {
        Object value = Global.getSector().getMemoryWithoutUpdate().get(key);
        return value instanceof Number ? (Number)value : fallback;
    }
}
