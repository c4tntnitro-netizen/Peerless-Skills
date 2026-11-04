package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import peerless.api.*;

/** Shared description; subclasses may also supply a native ship-stat effect. */
public class PeerlessDescription extends BaseSkillEffectDescription implements ShipSkillEffect {
    @Override public void apply(MutableShipStatsAPI stats,HullSize size,String id,float level) {}
    @Override public void unapply(MutableShipStatsAPI stats,HullSize size,String id) {}
    @Override public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }
    @Override public void createCustomDescription(MutableCharacterStatsAPI stats,SkillSpecAPI skill,
            TooltipMakerAPI info,float width) {
        if (Global.getSector()==null || stats!=Global.getSector().getPlayerStats()
                || skill==null || !PeerlessSkills.isTrained(skill.getId())) return;
        String effect=SkillCatalog.EFFECTS.get(skill.getId());
        if (effect==null) return;
        info.setParaFont("graphics/fonts/orbitron12condensed.fnt");
        info.addPara(stats.getSkillLevel(skill.getId())<2f?"Peerless (inactive)":"Peerless",Misc.getStoryOptionColor(),10f);
        info.setParaFontDefault();
        info.addPara(effect,Misc.getHighlightColor(),3f);
    }
}
