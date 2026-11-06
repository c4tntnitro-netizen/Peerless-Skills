package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import peerless.api.PeerlessSkills;

/** Converts the first overload to a native forced vent, preserving current and hard flux. */
public final class PeerlessFieldModulation extends BaseSkillEffectDescription
        implements ShipSkillEffect, AfterShipCreationSkillEffect {
    private static boolean eligible(ShipAPI ship) {
        return Global.getSector() != null && PeerlessSkills.isTrained(Skills.FIELD_MODULATION)
                && ship.getCaptain() != null
                && ship.getCaptain() == Global.getSector().getPlayerPerson()
                && ship.getCaptain().getStats().getSkillLevel(Skills.FIELD_MODULATION) >= 2f;
    }

    @Override public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (eligible(ship) && !ship.hasListenerOfClass(EmergencyVent.class)) {
            ship.addListener(new EmergencyVent(ship));
        }
    }
    // Retain the spent flag when native skill effects are reapplied during combat.
    @Override public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {}
    @Override public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize size, String id, float level) {}
    @Override public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize size, String id) {}
    @Override public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }
    @Override public void createCustomDescription(MutableCharacterStatsAPI stats, SkillSpecAPI skill,
            TooltipMakerAPI info, float width) {
        if (Global.getSector() == null || stats != Global.getSector().getPlayerStats()
                || !PeerlessSkills.isTrained(Skills.FIELD_MODULATION)) return;
        info.setParaFont("graphics/fonts/orbitron12condensed.fnt");
        info.addPara("Peerless", Misc.getStoryOptionColor(), 10f);
        info.setParaFontDefault();
        info.addPara(stats.getSkillLevel(Skills.FIELD_MODULATION) >= 2f
                ? "Once per battle, the first overload is replaced by a forced vent."
                : "Requires Elite Field Modulation", Misc.getHighlightColor(), 3f);
    }

    public static final class EmergencyVent implements AdvanceableListener {
        private final ShipAPI ship;
        private boolean spent;
        private boolean emergencyVenting;
        public EmergencyVent(ShipAPI ship) { this.ship = ship; }

        public boolean isEmergencyVenting() {
            return emergencyVenting && ship.getFluxTracker() != null && ship.getFluxTracker().isVenting();
        }

        @Override public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null && engine.isPaused()) return;
            if (emergencyVenting && (!ship.isAlive() || !eligible(ship) || !isEmergencyVenting())) emergencyVenting = false;
            if (spent || !ship.isAlive() || !eligible(ship)) return;
            FluxTrackerAPI flux = ship.getFluxTracker();
            if (flux == null || !flux.isOverloaded()) return;
            spent = true;
            if (!peerless.combat.BattleUses.claim(ship, "field_modulation")) return;
            // Detect native overload on the first listener update, then enter the native
            // vent state directly instead of issuing a player/AI vent command.
            flux.stopOverload();
            flux.ventFlux();
            emergencyVenting = true;
        }
    }
}
