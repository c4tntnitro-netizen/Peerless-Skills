package peerless.skills;

import com.fs.starfarer.api.combat.ShipAPI;
import peerless.api.PeerlessSkills;
import static peerless.skills.PeerlessFixture.*;

public final class PeerlessCombatEnduranceRegression {
    public static void main(String[] args) {
        PeerlessFixture f = new PeerlessFixture();
        PeerlessRewardsRegression.load(f);
        PeerlessSkills.unlockAllForTesting();
        float[] hp = {1000f};
        int[] visuals = {0};
        ShipAPI ship = mock(ShipAPI.class, (n,a) -> {
            if (n.equals("getCaptain")) return f.player;
            if (n.equals("getHitpoints")) return hp[0];
            if (n.equals("getMaxHitpoints")) return 1000f;
            if (n.equals("setHitpoints")) hp[0] = ((Number)a[0]).floatValue();
            if (n.equals("isAlive")) return hp[0] > 0;
            if (n.equals("setJitter") || n.equals("setJitterUnder")) visuals[0]++;
            return null;
        });
        var effect = new PeerlessCombatEndurance.Enduring(ship);
        check(!effect.notifyAboutToTakeHullDamage(null, ship, null, 50f), "Ordinary hits pass through");
        check(effect.notifyAboutToTakeHullDamage(null, ship, null, 5000f) && hp[0] == 100f,
                "Lethal triggering hit stops at ten percent");
        effect.advance(2f);
        check(visuals[0] == 2, "Damper overlay and underlay rendered");
        check(effect.notifyAboutToTakeHullDamage(null, ship, null, 5000f) && hp[0] == 100f,
                "Repeated lethal hits blocked during protection");
        hp[0] = 300f;
        check(!effect.notifyAboutToTakeHullDamage(null, ship, null, 50f), "Healing does not grant full invulnerability");
        effect.advance(3f);
        check(!effect.notifyAboutToTakeHullDamage(null, ship, null, 5000f), "Expires at five seconds and never retriggers");
        hp[0] = 1000f;
        var nextBattle = new PeerlessCombatEndurance.Enduring(ship);
        check(nextBattle.notifyAboutToTakeHullDamage(null, ship, null, 5000f), "Fresh combat instance resets use");
        f.skill = 1f;
        check(!nextBattle.notifyAboutToTakeHullDamage(null, ship, null, 5000f), "Elite required");
        System.out.println("PASS: Enduring trigger, lethal overkill, hull floor, visuals, expiry, one use and prerequisites.");
    }
}
