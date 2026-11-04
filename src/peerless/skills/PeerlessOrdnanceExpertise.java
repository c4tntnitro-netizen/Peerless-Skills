package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import peerless.api.PeerlessSkills;
import peerless.combat.PeerlessCombatPlugin;

/** Normal skill-stat callback, including the separate stats rebuilt by the refit screen. */
public final class PeerlessOrdnanceExpertise extends PeerlessDescription {
    @Override public void apply(MutableShipStatsAPI stats, HullSize size, String id, float level) {
        PersonAPI captain = null;
        if (stats.getEntity() instanceof ShipAPI) captain = ((ShipAPI) stats.getEntity()).getCaptain();
        if (captain == null && stats.getFleetMember() != null) captain = stats.getFleetMember().getCaptain();
        boolean active = Global.getSector() != null && captain != null
                && captain == Global.getSector().getPlayerPerson()
                && PeerlessSkills.isTrained("ordnance_expert")
                && captain.getStats().getSkillLevel("ordnance_expert") >= 2f;
        if (!active || stats.getVariant() == null) {
            unapply(stats, size, id);
            return;
        }
        // Same calculation path as native OrdnanceExpertise.Level1/Level3, with hullmod OP.
        int op = stats.getVariant().computeHullModOPCost(getCommanderStats(stats));
        stats.getFluxDissipation().modifyFlat(PeerlessCombatPlugin.ID, op * .75f);
        stats.getFluxCapacity().modifyFlat(PeerlessCombatPlugin.ID, op * 10f);
    }
    @Override public void unapply(MutableShipStatsAPI stats, HullSize size, String id) {
        stats.getFluxDissipation().unmodifyFlat(PeerlessCombatPlugin.ID);
        stats.getFluxCapacity().unmodifyFlat(PeerlessCombatPlugin.ID);
    }
}
