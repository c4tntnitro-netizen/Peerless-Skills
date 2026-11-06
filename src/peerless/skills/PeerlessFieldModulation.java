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
import peerless.api.SkillCatalog;

/** Cancels the first overload, vents if allowed, and grants a decaying maneuverability boost. */
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
                ? SkillCatalog.EFFECTS.get(Skills.FIELD_MODULATION)
                : "Requires Elite Field Modulation", Misc.getHighlightColor(), 3f);
    }

    public static final class EmergencyVent implements AdvanceableListener {
        private static final String MANEUVER_ID = "peerless_field_modulation_maneuver";
        private static final float DURATION = 5f;
        private final ShipAPI ship;
        private boolean spent;
        private boolean emergencyVenting;
        private float maneuverRemaining;
        public EmergencyVent(ShipAPI ship) { this.ship = ship; }

        public boolean isEmergencyVenting() {
            return emergencyVenting && ship.getFluxTracker() != null && ship.getFluxTracker().isVenting();
        }
        public float getManeuverabilityBonus() { return 200f * maneuverRemaining / DURATION; }

        private void applyManeuverability() {
            var stats = ship.getMutableStats();
            if (stats == null) return;
            for (MutableStat stat : new MutableStat[]{stats.getAcceleration(), stats.getDeceleration(),
                    stats.getTurnAcceleration(), stats.getMaxTurnRate()}) {
                if (maneuverRemaining > 0f) stat.modifyMult(MANEUVER_ID, 1f + getManeuverabilityBonus() / 100f);
                else stat.unmodify(MANEUVER_ID);
            }
        }

        @Override public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null && engine.isPaused()) return;
            FluxTrackerAPI flux = ship.getFluxTracker();
            if (!ship.isAlive() || !eligible(ship)) {
                emergencyVenting = false;
                maneuverRemaining = 0f;
                applyManeuverability();
                return;
            }
            if (emergencyVenting && !isEmergencyVenting()) emergencyVenting = false;
            maneuverRemaining = Math.max(0f, maneuverRemaining - Math.max(0f, amount));
            applyManeuverability();
            if (spent) return;
            if (flux == null || !flux.isOverloaded()) return;
            spent = true;
            if (!peerless.combat.BattleUses.claim(ship, "field_modulation")) return;
            // Cancellation is unconditional; native ventFlux still enforces venting
            // restrictions such as Safety Overrides. Neither action erases flux here.
            flux.stopOverload();
            flux.ventFlux();
            emergencyVenting = flux.isVenting();
            maneuverRemaining = DURATION;
            applyManeuverability();
        }
    }
}
