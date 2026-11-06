package peerless.campaign;

import com.fs.starfarer.api.plugins.LevelupPlugin;
import com.fs.starfarer.loading.scripts.ScriptStore;

/** Isolated 0.98a adapter: delegate the selected level-up plugin and observe its native XP-wrap call. */
public final class NativeLevelProgress {
    private NativeLevelProgress() {}
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
            if (level == delegate.getMaxLevel() + 1) {
                // The same query is used repeatedly to calculate story points and by UI tooltips.
                // Only the direct final query from levelUpIfNeeded precedes XP normalization.
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
