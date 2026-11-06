package peerless.api;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import peerless.campaign.PeerlessPointNotifications;

/** Player training and story reward API. All state belongs to the current sector. */
public final class PeerlessSkills {
    /** Implemented Peerless skills; extend this registry as new effects are added. */
    public static java.util.List<String> getSupportedSkillIds() {
        return SkillCatalog.ids();
    }

    /** Testing shortcut: grants prerequisites and training without spending reward points. */
    public static int unlockAllForTesting() {
        if (memory() == null || Global.getSector().getPlayerPerson() == null) return 0;
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        if (stats == null) return 0;
        int balance = getPoints();
        memory().set(POINTS, balance);
        int count = 0;
        for (String skillId : getSupportedSkillIds()) {
            if (!isTrained(skillId)) memory().set(TEST_UNLOCK_PREFIX + skillId, true);
            markTrained(skillId);
            stats.setSkillLevel(skillId, Math.max(2f, stats.getSkillLevel(skillId)));
            count++;
        }
        return count;
    }

    private PeerlessSkills() {}
    private static final String POINTS = "$peerless_skills_points_v1";
    private static final String TRAINED_PREFIX = "$peerless_skills_trained_v1_";
    private static final String TEST_UNLOCK_PREFIX = "$peerless_skills_test_unlock_v1_";

    private static void markTrained(String skillId) {
        if (Skills.HELMSMANSHIP.equals(skillId)) memory().set(TRAINED, skillId);
        else memory().set(TRAINED_PREFIX + skillId, true);
    }
    // The prototype already persisted the trained skill ID and native stat modifier.
    // Keep that save representation for Helmsmanship when installing the split mod.
    public static final String TRAINED = "$chief_navigator_peerless_skill_v1";
    private static final String PROTOTYPE_TITLE = "$chief_navigator_domain_epic_v1";

    private static MemoryAPI memory() {
        return Global.getSector() == null ? null : Global.getSector().getMemoryWithoutUpdate();
    }

    public static int getPoints() {
        MemoryAPI m = memory();
        if (m == null) return 0;
        Object balance = m.get(POINTS);
        if (balance instanceof Number) return ((Number) balance).intValue();
        // The original one-point reward was represented by a title and an empty slot.
        return m.getBoolean(PROTOTYPE_TITLE) && m.get(TRAINED) == null ? 1 : 0;
    }

    public static boolean isUnlocked() {
        MemoryAPI m = memory();
        return m != null && (m.get(POINTS) != null || m.getBoolean(PROTOTYPE_TITLE));
    }

    /** Supply an exclusive, permanent sector-memory key for this reward (e.g. "$myMod_finale").
     *  Returns true only on the first grant. Do not set the key before calling. */
    public static boolean awardOnce(String rewardKey) {
        MemoryAPI m = memory();
        if (m == null || Global.getSector().getPlayerPerson() == null || rewardKey == null
                || !rewardKey.startsWith("$") || rewardKey.length() < 2
                || POINTS.equals(rewardKey) || TRAINED.equals(rewardKey) || PeerlessPointNotifications.PENDING.equals(rewardKey)
                || rewardKey.startsWith(TRAINED_PREFIX) || rewardKey.startsWith(TEST_UNLOCK_PREFIX) || m.getBoolean(rewardKey)) return false;
        int balance = getPoints();
        if (balance == Integer.MAX_VALUE) return false;
        m.set(POINTS, balance + 1);
        m.set(rewardKey, true);
        PeerlessPointNotifications.queueAward();
        return true;
    }

    public static boolean isTrained(String skillId) {
        MemoryAPI m = memory();
        return m != null && getSupportedSkillIds().contains(skillId)
                // Native getString throws for a present key containing null (older refunds).
                && (Skills.HELMSMANSHIP.equals(skillId) ? skillId.equals(m.get(TRAINED))
                : m.getBoolean(TRAINED_PREFIX + skillId));
    }

    public static boolean canTrain(String skillId) {
        if (!getSupportedSkillIds().contains(skillId) || getPoints() <= 0 || isTrained(skillId)) return false;
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        return stats != null && stats.getSkillLevel(skillId) >= 2f;
    }

    public static boolean train(String skillId) {
        if (!canTrain(skillId)) return false;
        int balance = getPoints();
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        memory().set(POINTS, balance - 1);
        memory().set(TEST_UNLOCK_PREFIX + skillId, false);
        markTrained(skillId);
        stats.setSkillLevel(skillId, stats.getSkillLevel(skillId));
        if (Global.getSoundPlayer() != null)
            Global.getSoundPlayer().playUISound("peerless_training_complete", 1f, 1f);
        return true;
    }

    /** Call after the skill screen closes, so previews do not refund training. */
    public static int refundInactiveTraining() {
        MemoryAPI m = memory();
        if (m == null || Global.getSector().getPlayerStats() == null) return 0;
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        int refunded = 0;
        boolean changed = false;
        for (String id : getSupportedSkillIds()) {
            if (!isTrained(id) || stats.getSkillLevel(id) >= 2f) continue;
            boolean free = m.getBoolean(TEST_UNLOCK_PREFIX + id);
            int balance = getPoints();
            if (!free && balance == Integer.MAX_VALUE) continue;
            m.set(POINTS, balance + (free ? 0 : 1));
            if (Skills.HELMSMANSHIP.equals(id)) m.unset(TRAINED);
            else m.set(TRAINED_PREFIX + id, false);
            m.set(TEST_UNLOCK_PREFIX + id, false);
            if (!free) refunded++;
            changed = true;
        }
        if (changed) stats.refreshCharacterStatsEffects();
        return refunded;
    }
}
