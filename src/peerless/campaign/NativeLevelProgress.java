package peerless.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import com.fs.starfarer.loading.scripts.ScriptStore;

/** Isolated 0.98a adapter: delegate the selected level-up plugin and observe its native XP-wrap call. */
public final class NativeLevelProgress {
    private NativeLevelProgress() {}
    static boolean isInstalled(LevelupPlugin plugin) { return plugin instanceof Observer; }
    /** Predict only the recorded baseline; native CharacterStats still performs the XP update.
     *  A lowered cap can make successive thresholds equal. Native 0.98a then wraps in 100k steps.
     *  Keep this compatibility detail here alongside the native call-site check. */
    static long xpAfterUpdate(LevelupPlugin levels, int playerLevel, long xp) {
        long end = levels.getXPForLevel(playerLevel + 1);
        if (xp < end) return xp;
        long step = end - levels.getXPForLevel(playerLevel);
        if (step <= 0L) step = 100_000L;
        return end - step + (xp - end) % step;
    }
    public static boolean configure(boolean enabled) {
        var plugins = ScriptStore.Object(LevelupPlugin.class);
        if (plugins.isEmpty()) return false;
        LevelupPlugin current = plugins.get(0);
        if (enabled && !(current instanceof Observer)) {
            plugins.set(0, new Observer(current));
            return true;
        }
        if (!enabled && current instanceof Observer) {
            plugins.set(0, ((Observer)current).delegate);
            return true;
        }
        return false;
    }
    private static final class Observer implements LevelupPlugin {
        private final LevelupPlugin delegate;
        Observer(LevelupPlugin delegate) { this.delegate = delegate; }
        @Override public long getXPForLevel(int level) {
            long value = delegate.getXPForLevel(level);
            var sector = Global.getSector();
            var player = sector == null ? null : sector.getPlayerStats();
            if (player != null && player.getLevel() >= delegate.getMaxLevel()
                    && level == player.getLevel() + 1) {
                // The same query is used repeatedly to calculate story points and by UI tooltips.
                // Only the direct final query from levelUpIfNeeded precedes XP normalization.
                // It uses the character's actual level, even when above a newly lowered cap.
                StackTraceElement caller = new Throwable().getStackTrace()[1];
                if (caller.getClassName().equals("com.fs.starfarer.campaign.CharacterStats")
                        && caller.getMethodName().equals("levelUpIfNeeded"))
                    PeerlessProgression.observeBeforeXPWrap(delegate);
            }
            return value;
        }
        @Override public int getMaxLevel() { return delegate.getMaxLevel(); }
        @Override public int getPointsAtLevel(int level) { return delegate.getPointsAtLevel(level); }
        @Override public int getStoryPointsPerLevel() { return delegate.getStoryPointsPerLevel(); }
        @Override public int getBonusXPUseMultAtMaxLevel() { return delegate.getBonusXPUseMultAtMaxLevel(); }
    }
}
