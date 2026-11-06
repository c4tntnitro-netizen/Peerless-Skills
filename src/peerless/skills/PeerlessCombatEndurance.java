package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import peerless.api.PeerlessSkills;

public final class PeerlessCombatEndurance extends BaseSkillEffectDescription
        implements ShipSkillEffect, AfterShipCreationSkillEffect {
    private static boolean eligible(ShipAPI ship) {
        return Global.getSector() != null && PeerlessSkills.isTrained(Skills.COMBAT_ENDURANCE)
                && ship.getCaptain() == Global.getSector().getPlayerPerson()
                && ship.getCaptain() != null
                && ship.getCaptain().getStats().getSkillLevel(Skills.COMBAT_ENDURANCE) >= 2f;
    }

    @Override public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (eligible(ship) && !ship.hasListenerOfClass(Enduring.class)) ship.addListener(new Enduring(ship));
    }
    // Keep the listener's spent flag if skills are reapplied during the same battle.
    @Override public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {}
    @Override public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize size, String id, float level) {}
    @Override public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize size, String id) {}
    @Override public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }
    @Override public void createCustomDescription(MutableCharacterStatsAPI stats, SkillSpecAPI skill,
            TooltipMakerAPI info, float width) {
        if (Global.getSector() == null || stats != Global.getSector().getPlayerStats()
                || !PeerlessSkills.isTrained(Skills.COMBAT_ENDURANCE)) return;
        info.setParaFont("graphics/fonts/orbitron12condensed.fnt");
        info.addPara("Peerless", Misc.getStoryOptionColor(), 10f);
        info.setParaFontDefault();
        info.addPara(stats.getSkillLevel(Skills.COMBAT_ENDURANCE) >= 2f
                ? peerless.api.SkillCatalog.EFFECTS.get(Skills.COMBAT_ENDURANCE)
                : "Requires Elite Combat Endurance", Misc.getHighlightColor(), 3f);
    }

    public static final class Enduring implements HullDamageAboutToBeTakenListener, AdvanceableListener, DamageTakenModifier {
        private static final Color UNDER = new Color(255, 165, 90, 155);
        private static final Color OVER = new Color(255, 165, 90, 55);
        private final ShipAPI ship;
        private boolean spent;
        private float remaining;
        private float textCooldown;
        public Enduring(ShipAPI ship) { this.ship = ship; }
        public float getRemaining() { return remaining; }

        @Override public boolean notifyAboutToTakeHullDamage(Object source, ShipAPI target,
                Vector2f point, float damage) {
            if (target != ship || damage <= 0f || !eligible(ship)) return false;
            float floor = ship.getMaxHitpoints() * 0.1f;
            if (!spent && ship.getHitpoints() - damage < floor) {
                spent = true;
                remaining = peerless.combat.BattleUses.claim(ship, "combat_endurance") ? 5f : 0f;
            }
            if (remaining <= 0f) return false;
            showText(point);
            if (ship.getHitpoints() - damage < floor) {
                // Consume the permitted part of this hit and cancel its remainder, including lethal overkill.
                ship.setHitpoints(Math.max(floor, ship.getHitpoints() - damage));
                return true;
            }
            return false;
        }

        private void showText(Vector2f point) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null && textCooldown <= 0f) {
                engine.addFloatingText(point == null ? ship.getLocation() : point,
                        "Enduring!", 20f, UNDER, ship, 0f, 0f);
                textCooldown = 0.15f;
            }
        }
        @Override public String modifyDamageTaken(Object source, com.fs.starfarer.api.combat.CombatEntityAPI target,
                com.fs.starfarer.api.combat.DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (target == ship && remaining > 0f && eligible(ship) && damage.getDamage() > 0f) showText(point);
            return null;
        }

        @Override public void advance(float amount) {
            if (Global.getCombatEngine() != null && Global.getCombatEngine().isPaused()) return;
            textCooldown = Math.max(0f, textCooldown - amount);
            if (remaining <= 0f) return;
            if (!eligible(ship) || !ship.isAlive()) { remaining = 0f; return; }
            remaining = Math.max(0f, remaining - amount);
            if (remaining > 0f) {
                ship.setHitpoints(Math.max(ship.getHitpoints(), ship.getMaxHitpoints() * 0.1f));
                // Match damper.system's orange overlay and underlay without activating the ship's system.
                ship.setJitterUnder(this, UNDER, 1f, 25, 0f, 7f);
                ship.setJitter(this, OVER, 1f, 2, 0f, 5f);
            }
        }
    }
}
