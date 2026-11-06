package peerless.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import java.util.Locale;
import peerless.api.SkillCatalog;
import peerless.skills.PeerlessCombatEndurance;
import peerless.skills.PeerlessFieldModulation;
import peerless.skills.PeerlessSkillIcon;

/** Native bottom-left status entries, refreshed only while an effect applies to the piloted ship. */
public final class PeerlessCombatStatus {
    private PeerlessCombatStatus() {}
    public static void update(CombatEngineAPI engine, PeerlessCombatPlugin.Controller controller) {
        ShipAPI ship = engine.getPlayerShip();
        if (ship == null || !ship.isAlive() || !PeerlessCombatPlugin.player(ship)
                || controller == null || !controller.controls(ship)) return;
        if (active(ship, "helmsmanship") && controller.hasSpeedBurst())
            show(engine, "helmsmanship", "+" + number(controller.speedBonus()) + " speed, +100% maneuverability");
        if (active(ship, "combat_endurance")) {
            var listeners = ship.getListeners(PeerlessCombatEndurance.Enduring.class);
            if (listeners != null) for (var effect : listeners) if (effect.getRemaining() > 0f) {
                show(engine, "combat_endurance", "10% hull minimum: " + seconds(effect.getRemaining()) + "s");
                break;
            }
        }
        if (active(ship, "impact_mitigation") && controller.hasMassBonus())
            show(engine, "impact_mitigation", "+100% ship mass");
        if (active(ship, "damage_control") && ship.getCurrentCR() > 0f)
            show(engine, "damage_control", "+" + percent(ship.getCurrentCR()) + "% repair speed");
        if (active(ship, "field_modulation")) {
            var listeners = ship.getListeners(PeerlessFieldModulation.EmergencyVent.class);
            if (listeners != null) for (var effect : listeners) if (effect.isEmergencyVenting()) {
                show(engine, "field_modulation", "Overload replaced by forced vent");
                break;
            }
        }
        boolean pd = false, armed = false, energy = false;
        int reloading = 0;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (!weapon.isDecorative()) {
                armed = true;
                energy |= weapon.getType() == WeaponAPI.WeaponType.ENERGY;
                pd |= weapon.hasAIHint(WeaponAPI.AIHints.PD) || weapon.hasAIHint(WeaponAPI.AIHints.PD_ONLY)
                        || weapon.hasAIHint(WeaponAPI.AIHints.PD_ALSO);
            }
            if (controller.missileReloadProgress(weapon) >= 0f) reloading++;
        }
        if (active(ship, "point_defense") && pd)
            show(engine, "point_defense", "PD hits deal +10% damage as EMP");
        if (active(ship, "target_analysis") && armed)
            show(engine, "target_analysis", "Armor decay on hit");
        if (active(ship, "ballistic_mastery") && controller.accuracyBonus() > 0f) {
            String bonus = percent(controller.accuracyBonus());
            show(engine, "ballistic_mastery", "+" + bonus + "% accuracy, -" + bonus + "% recoil");
        }
        float protection = PeerlessCombatPlugin.systemProtection(controller.systemAge());
        if (active(ship, "systems_expertise") && protection > 0f)
            show(engine, "systems_expertise", percent(protection) + "% less hull damage");
        if (active(ship, "missile_specialization") && reloading > 0)
            show(engine, "missile_specialization", "Regenerating " + reloading + " large " + (reloading == 1 ? "launcher" : "launchers"));
        if (active(ship, "gunnery_implants"))
            show(engine, "gunnery_implants", "+5% ECP cap");
        if (active(ship, "energy_weapon_mastery") && energy && ship.getFluxBasedEnergyWeaponDamageMultiplier() > 1f)
            show(engine, "energy_weapon_mastery", "Bonus damage range doubled");
        if (active(ship, "ordnance_expert") && ship.getVariant() != null) {
            int op = ship.getVariant().computeHullModOPCost(Global.getSector().getPlayerStats());
            if (op > 0) show(engine, "ordnance_expert", "+" + number(op * .75f) + " flux dissipation, +" + (op * 10) + " capacity");
        }
        if (active(ship, "polarized_armor") && ship.getFluxTracker() != null && ship.getFluxTracker().isVenting())
            show(engine, "polarized_armor", "+100% weapon/engine repair speed");
    }
    private static boolean active(ShipAPI ship, String id) { return PeerlessCombatPlugin.active(ship, id); }
    private static void show(CombatEngineAPI engine, String id, String effect) {
        engine.maintainStatusForPlayerShip("peerless_status_" + id, PeerlessSkillIcon.spriteFor(id),
                "Peerless " + SkillCatalog.NAMES.get(id), effect, false);
    }
    private static String percent(float value) { return Integer.toString(Math.round(value * 100f)); }
    private static String seconds(float value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String number(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
