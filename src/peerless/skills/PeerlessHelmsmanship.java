package peerless.skills;

import peerless.api.PeerlessSkills;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import com.fs.starfarer.api.impl.campaign.skills.Helmsmanship;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

/** A player-only addition to native Elite Helmsmanship, from shared Peerless training. */
public final class PeerlessHelmsmanship extends BaseSkillEffectDescription implements ShipSkillEffect {
    public static final String MODIFIER = "chief_navigator_peerless_helmsmanship";
    public static final Color GOLD = new Color(255, 209, 105);
    private static final float ELITE_MULTIPLIER = 3f;

    private static boolean isPlayer(MutableCharacterStatsAPI stats) {
        return Global.getSector() != null && stats != null
                && stats == Global.getSector().getPlayerStats();
    }

    private static boolean appliesTo(MutableShipStatsAPI stats) {
        if (!PeerlessSkills.isTrained(Skills.HELMSMANSHIP)) return false;
        PersonAPI captain = null;
        if (stats.getEntity() instanceof ShipAPI) {
            captain = ((ShipAPI) stats.getEntity()).getCaptain();
        } else if (stats.getFleetMember() != null) {
            captain = stats.getFleetMember().getCaptain();
        }
        return captain != null && captain == Global.getSector().getPlayerPerson()
                && captain.getStats().getSkillLevel(Skills.HELMSMANSHIP) >= 2f;
    }

    @Override
    public void apply(MutableShipStatsAPI stats, HullSize size, String id, float level) {
        // Remove the prototype's former permanent +20 speed, including in old saves.
        // The combat controller now owns the system-triggered burst.
        unapply(stats, size, id);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, HullSize size, String id) {
        stats.getMaxSpeed().unmodify(MODIFIER);
    }

    @Override
    public void createCustomDescription(MutableCharacterStatsAPI stats,
            SkillSpecAPI skill, TooltipMakerAPI info, float width) {
        if (!isPlayer(stats) || !PeerlessSkills.isTrained(Skills.HELMSMANSHIP)) return;
        init(stats, skill);
        // Match the native Elite group: condensed heading, then default-font
        // effect text in the native highlight color, with the same padding.
        info.setParaFont("graphics/fonts/orbitron12condensed.fnt");
        info.addPara("Peerless", Misc.getStoryOptionColor(), 10f);
        info.setParaFontDefault();
        boolean active = stats.getSkillLevel(Skills.HELMSMANSHIP) >= 2f;
        String bonus = "+" + (int) (ELITE_MULTIPLIER * Helmsmanship.ELITE_SPEED_BONUS_FLAT);
        info.addPara(active ? peerless.api.SkillCatalog.EFFECTS.get(Skills.HELMSMANSHIP)
                : "Requires Elite Helmsmanship", Misc.getHighlightColor(), 3f);
    }

    @Override
    public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }
}
