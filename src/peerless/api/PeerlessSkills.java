package peerless.api;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;

/** Player training and story reward API. All state belongs to the current sector. */
public final class PeerlessSkills {
    private PeerlessSkills() {}
    private static final String POINTS = "$peerless_skills_points_v1";
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
                || POINTS.equals(rewardKey) || TRAINED.equals(rewardKey) || m.getBoolean(rewardKey)) return false;
        int balance = getPoints();
        if (balance == Integer.MAX_VALUE) return false;
        m.set(POINTS, balance + 1);
        m.set(rewardKey, true);
        return true;
    }

    public static boolean isTrained(String skillId) {
        MemoryAPI m = memory();
        return m != null && Skills.HELMSMANSHIP.equals(skillId) && skillId.equals(m.getString(TRAINED));
    }

    public static boolean canTrain(String skillId) {
        if (!Skills.HELMSMANSHIP.equals(skillId) || getPoints() <= 0 || isTrained(skillId)) return false;
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        return stats != null && stats.getSkillLevel(skillId) >= 2f;
    }

    public static boolean train(String skillId) {
        if (!canTrain(skillId)) return false;
        int balance = getPoints();
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        memory().set(POINTS, balance - 1);
        memory().set(TRAINED, skillId);
        stats.setSkillLevel(skillId, stats.getSkillLevel(skillId));
        return true;
    }
}
