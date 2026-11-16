package peerless.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import peerless.api.PeerlessSkills;

/** Durable quest receipts and optional progress measured in post-cap XP bars. */
public final class PeerlessProgression {
    private static final String GALATIA = "$peerless_skills_galatia_reward_v1";
    private static final String PROGRESS = "$peerless_skills_postcap_levels_v1";
    private static final String SEQUENCE = "$peerless_skills_postcap_awards_v1";
    private static final String XP = "$peerless_skills_postcap_last_xp_v1";
    private static final String CAP = "$peerless_skills_postcap_last_cap_v1";
    // Transient observations are rebased on load. Only earned progress and receipts persist.
    private static Sample previous;

    private record Sample(SectorAPI sector, MutableCharacterStatsAPI stats, LevelupPlugin plugin,
                          boolean enabled, int level, int cap, long base, long cycle,
                          long wrapBase, long wrapEnd, long xp) {}

    public static void install() {
        ProgressionSettings.listen();
        sample(ProgressionSettings.read().enabled(), true);
    }
    static void settingsChanged() {
        boolean enabled = ProgressionSettings.read().enabled();
        // Account for the last visible change before a toggle, then establish the new baseline.
        // Ratio-only changes keep both banked progress and the current observation.
        if (previous != null && previous.sector == Global.getSector() && previous.enabled != enabled)
            sample(previous.enabled, false);
        sample(enabled, false);
    }
    /** Read-only console snapshot; never samples XP, rebases or pays out points. */
    public static String diagnostics() {
        var settings = ProgressionSettings.read();
        var stats = Global.getSector().getPlayerStats();
        var levels = Global.getSettings().getLevelupPlugin();
        int cap = levels == null ? -1 : levels.getMaxLevel();
        long base = levels == null || cap < 1 || cap == Integer.MAX_VALUE ? -1L : levels.getXPForLevel(cap);
        long end = base < 0 ? -1L : levels.getXPForLevel(cap + 1);
        long cycle = end > base && base >= 0 ? end - base : 0L;
        return "Post-cap progression: " + (settings.enabled() ? "enabled" : "disabled")
                + "\nXP tracker: " + (!settings.enabled() ? "disabled"
                        : cycle <= 0 ? "waiting for a valid XP requirement" : "public API, every campaign frame")
                + "\nPlayer level: " + stats.getLevel() + "; cap: " + cap
                + "\nXP: " + stats.getXP() + "; last recorded XP: " + number(XP, -1L)
                + "; XP per bar: " + cycle
                + "\nBanked progress: " + String.format(java.util.Locale.ROOT, "%.4f", number(PROGRESS, 0d).doubleValue())
                + " / " + settings.levelsPerPoint() + " bars per Peerless point"
                + "\nXP points awarded: " + number(SEQUENCE, 0L)
                + "; unspent Peerless points: " + PeerlessSkills.getPoints()
                + "\nFull XP cycles completed between samples cannot be recovered.";
    }
    public static void advance() {
        if (Global.getSector() == null || Global.getSector().getPlayerStats() == null) return;
        var memory = Global.getSector().getMemoryWithoutUpdate();
        if (memory.getBoolean("$gaATG_missionCompleted")) PeerlessSkills.awardOnce(GALATIA);
        if ("COMPLETED".equals(memory.getString("$shipTrophyGanEdenQuestStage")))
            PeerlessSkills.awardOnce("$ship_trophy_room_peerless_isa_v1");
        if (memory.getBoolean("$chief_navigator_menelaus_final_debrief_complete_v1"))
            PeerlessSkills.awardOnce("$chief_navigator_domain_epic_v1");
        var settings = ProgressionSettings.read();
        sample(settings.enabled(), false);
        if (!settings.enabled() || previous == null || previous.cycle <= 0) return;
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
    /** Observe only public values; never replace a plugin or alter the player's stats.
     *  A drop within a known XP range counts one visible rollover. Complete cycles hidden
     *  between samples are indistinguishable from no gain and cannot be reconstructed. */
    private static void sample(boolean enabled, boolean reset) {
        var sector = Global.getSector();
        var stats = sector == null ? null : sector.getPlayerStats();
        var levels = Global.getSettings() == null ? null : Global.getSettings().getLevelupPlugin();
        if (stats == null || levels == null) { previous = null; return; }
        int cap = levels.getMaxLevel();
        int level = stats.getLevel();
        long xp = stats.getXP();
        long base = cap < 1 || cap == Integer.MAX_VALUE ? -1L : levels.getXPForLevel(cap);
        long end = base < 0 ? -1L : levels.getXPForLevel(cap + 1);
        long cycle = base >= 0 && end > base ? end - base : 0L;
        long wrapBase = base, wrapEnd = end;
        if (level > cap) {
            wrapBase = level < Integer.MAX_VALUE ? levels.getXPForLevel(level) : -1L;
            wrapEnd = level < Integer.MAX_VALUE ? levels.getXPForLevel(level + 1) : -1L;
        }
        Sample old = previous;
        boolean sameCurve = old != null && old.sector == sector && old.stats == stats
                && old.plugin == levels && old.cap == cap && old.base == base && old.cycle == cycle;
        if (!reset && sameCurve && old.enabled == enabled && old.level == level
                && old.wrapBase == wrapBase && old.wrapEnd == wrapEnd && old.xp == xp) return;

        // No new snapshot or memory writes on an unchanged frame.
        previous = new Sample(sector, stats, levels, enabled, level, cap, base, cycle, wrapBase, wrapEnd, xp);
        var memory = sector.getMemoryWithoutUpdate();
        memory.set(XP, xp);
        memory.set(CAP, cap);
        if (reset || !sameCurve || !enabled || !old.enabled || cycle <= 0 || level < cap || xp < 0) return;

        long gained = 0L;
        if (old.level < cap && level == cap) {
            // Exclude XP needed to reach the cap; only the visible excess is eligible.
            gained = Math.max(0L, xp - base);
        } else if (old.level == level && old.wrapBase == wrapBase && old.wrapEnd == wrapEnd && old.xp >= 0) {
            if (xp >= old.xp) {
                gained = Math.max(0L, xp - Math.max(base, old.xp));
            } else if (wrapBase >= 0 && wrapEnd > wrapBase
                    && old.xp >= wrapBase && old.xp < wrapEnd && xp >= wrapBase && xp < wrapEnd) {
                gained = (wrapEnd - old.xp) + (xp - wrapBase);
            }
            // With no public rollover range (e.g. an old character above a lowered cap),
            // keep visible increases but rebase on drops rather than inventing a cycle size.
        }
        if (gained > 0L) memory.set(PROGRESS, number(PROGRESS, 0d).doubleValue() + (double)gained / cycle);
    }
    private static Number number(String key, Number fallback) {
        Object value = Global.getSector().getMemoryWithoutUpdate().get(key);
        return value instanceof Number ? (Number)value : fallback;
    }
}
