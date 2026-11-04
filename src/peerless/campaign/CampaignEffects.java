package peerless.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import peerless.api.PeerlessSkills;
import peerless.combat.PeerlessCombatPlugin;

/** Keeps refit/campaign flux and ECM previews consistent with combat, including SiC respecs. */
public final class CampaignEffects implements EveryFrameScript {
    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return true; }
    public void advance(float amount) {
        if (Global.getSector()==null || Global.getSector().getPlayerFleet()==null) return;
        PeerlessCombatPlugin.updateCommanderCap();
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            boolean player=member.getCaptain()==Global.getSector().getPlayerPerson();
            member.getStats().getMaxSpeed().unmodify("chief_navigator_peerless_helmsmanship");
            boolean ordnance=player && PeerlessSkills.isTrained("ordnance_expert") && Global.getSector().getPlayerStats().getSkillLevel("ordnance_expert")>=2f;
            boolean gunnery=player && PeerlessSkills.isTrained("gunnery_implants") && Global.getSector().getPlayerStats().getSkillLevel("gunnery_implants")>=2f;
            PeerlessCombatPlugin.staticStats(member.getStats(),ordnance,gunnery,Global.getSector().getPlayerStats());
        }
    }
}
