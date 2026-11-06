package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.combat.entities.ship.D;
import com.fs.starfarer.combat.entities.ship.o0OO;
import peerless.api.PeerlessSkills;
import static peerless.skills.PeerlessFixture.*;

public final class PeerlessFieldModulationRegression {
    public static void main(String[] args) throws Exception {
        PeerlessFixture f = new PeerlessFixture();
        PeerlessRewardsRegression.load(f);
        PeerlessSkills.unlockAllForTesting();
        boolean[] overloaded = {false}, paused = {false}, alive = {true}, venting = {false};
        Map<String,MutableStat> values = new HashMap<>();
        MutableShipStatsAPI stats = mock(MutableShipStatsAPI.class,(n,a)->n.startsWith("get")
                ? values.computeIfAbsent(n,k->new MutableStat(10f)) : null);
        Map<String,Object> battle = new HashMap<>();
        int[] stops = {0}, vents = {0}, fluxWrites = {0};
        StringBuilder order = new StringBuilder();
        FluxTrackerAPI tracker = mock(FluxTrackerAPI.class, (n,a) -> {
            if (n.equals("isOverloaded")) return overloaded[0];
            if (n.equals("isVenting")) return venting[0];
            if (n.equals("stopOverload")) { stops[0]++; overloaded[0] = false; order.append("stop "); }
            if (n.equals("ventFlux")) { vents[0]++; venting[0]=true; check(!overloaded[0], "Clear overload before vent"); order.append("vent "); }
            if (n.equals("setCurrFlux") || n.equals("setHardFlux") || n.equals("decreaseFlux")) fluxWrites[0]++;
            return null;
        });
        ShipAPI ship = mock(ShipAPI.class, (n,a) -> switch(n) {
            case "getCaptain" -> f.player;
            case "isAlive" -> alive[0];
            case "getFluxTracker" -> tracker;
            case "getMutableStats" -> stats;
            default -> null;
        });
        Global.setCombatEngine(mock(CombatEngineAPI.class, (n,a) -> n.equals("isPaused") ? paused[0] : n.equals("getCustomData")?battle:null));
        var effect = new PeerlessFieldModulation.EmergencyVent(ship);
        effect.advance(1f);
        check(vents[0] == 0, "Ordinary flux does not trigger");
        overloaded[0] = true; paused[0] = true;
        effect.advance(1f);
        check(vents[0] == 0 && overloaded[0], "Pause does not consume use");
        paused[0] = false; f.skill = 1f;
        effect.advance(1f);
        check(vents[0] == 0, "Elite required");
        f.skill = 2f; alive[0] = false;
        effect.advance(1f);
        check(vents[0] == 0, "Dead ships excluded");
        alive[0] = true;
        effect.advance(1f);
        check(stops[0] == 1 && vents[0] == 1 && order.toString().equals("stop vent "), "First overload becomes forced vent");
        check(fluxWrites[0] == 0, "Conversion does not erase current or hard flux");
        maneuver(stats,3f,"Full +200% maneuverability on activation");
        check(stats.getMaxSpeed().getModifiedValue()==10f,"Maneuver bonus does not change top speed");
        paused[0]=true;effect.advance(10f);paused[0]=false;
        maneuver(stats,3f,"Pause freezes maneuver decay");
        effect.advance(2.5f);maneuver(stats,2f,"Linear decay reaches +100% halfway through");
        venting[0]=false;effect.advance(0f);
        check(!effect.isEmergencyVenting() && effect.getManeuverabilityBonus()==100f,"Early vent completion preserves the five-second boost");
        stats.getAcceleration().modifyMult("other_skill",2f);
        effect.advance(0f);check(stats.getAcceleration().getModifiedValue()==40f,"Other movement modifiers stack independently");
        effect.advance(2.5f);
        check(stats.getAcceleration().getModifiedValue()==20f,"Expiry preserves another skill's modifier");
        stats.getAcceleration().unmodify("other_skill");maneuver(stats,1f,"All four modifiers removed at five seconds");
        overloaded[0] = true;
        effect.advance(1f);
        check(vents[0] == 1 && overloaded[0], "Second overload left intact");
        new PeerlessFieldModulation.EmergencyVent(ship).advance(1f);
        check(vents[0] == 1,"Reapplying a listener cannot reuse the battle receipt");
        battle.clear();
        var nextBattle = new PeerlessFieldModulation.EmergencyVent(ship);nextBattle.advance(1f);
        check(vents[0] == 2, "Fresh battle instance restores use");
        f.skill=1f;nextBattle.advance(0f);maneuver(stats,1f,"Losing Elite removes movement modifiers");f.skill=2f;
        battle.clear();
        // Trigger again only in a new battle, then ensure death clears the effect.
        overloaded[0]=true;var deathEffect=new PeerlessFieldModulation.EmergencyVent(ship);deathEffect.advance(0f);
        alive[0]=false;deathEffect.advance(0f);maneuver(stats,1f,"Death removes movement modifiers");alive[0]=true;
        ShipAPI officer = mock(ShipAPI.class, (n,a) -> n.equals("isAlive") ? true : n.equals("getFluxTracker") ? tracker : null);
        overloaded[0] = true;
        new PeerlessFieldModulation.EmergencyVent(officer).advance(1f);
        check(vents[0] == 3, "Nonplayer captain excluded");
        String definition = Files.readString(Path.of("data/config/settings.json"));
        check(definition.contains("peerless.combat.PeerlessCombatPlugin"), "Registered independently of vanilla and SiC skill files");
        safetyOverrides(f,battle);
        Global.setCombatEngine(null);
        System.out.println("PASS: forced vent, five-second +200% maneuver decay, pause, stacking, cleanup, battle receipts, and native Safety Overrides vent prohibition with independent movement boost.");
    }
    static void maneuver(MutableShipStatsAPI stats,float mult,String message) {
        for(MutableStat stat:List.of(stats.getAcceleration(),stats.getDeceleration(),stats.getTurnAcceleration(),stats.getMaxTurnRate()))
            check(Math.abs(stat.getModifiedValue()-10f*mult)<.001f,message);
    }
    static void safetyOverrides(PeerlessFixture f,Map<String,Object> battle)throws Exception {
        // Test-only native allocation: exercise the real ventFlux guard with the game's own
        // SafetyOverrides hullmod and native ship stats, without needing a combat renderer.
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)field.get(null);
        var config=new org.json.JSONObject().put("fluxPerCapacitor",200).put("dissipationPerVent",10);
        for(Field configField:com.fs.starfarer.settings.StarfarerSettings.class.getDeclaredFields())
            if(configField.getType()==org.json.JSONObject.class && Modifier.isStatic(configField.getModifiers())) {
                configField.setAccessible(true);configField.set(null,config);
            }
        var stats=(o0OO)unsafe.allocateInstance(o0OO.class);
        for(Field statField:o0OO.class.getDeclaredFields()) {
            if(statField.getType()==MutableStat.class) {statField.setAccessible(true);statField.set(stats,new MutableStat(1f));}
            if(statField.getType()==StatBonus.class) {statField.setAccessible(true);statField.set(stats,new StatBonus());}
        }
        stats.getFluxCapacity().setBaseValue(1000f);stats.getFluxDissipation().setBaseValue(100f);
        new data.hullmods.SafetyOverrides().applyEffectsBeforeShipCreation(ShipAPI.HullSize.FRIGATE,stats,HullMods.SAFETYOVERRIDES);
        var nativeShip=(Ship)unsafe.allocateInstance(Ship.class);
        for(Field shipField:Ship.class.getDeclaredFields())if(shipField.getType()==o0OO.class) {
            shipField.setAccessible(true);shipField.set(nativeShip,stats);
        }
        var flux=(D)unsafe.allocateInstance(D.class);
        for(Field fluxField:D.class.getDeclaredFields()) {
            if(fluxField.getType()==Ship.class) {fluxField.setAccessible(true);fluxField.set(flux,nativeShip);}
            if(fluxField.getType()==com.fs.starfarer.combat.entities.ship.C.class) {fluxField.setAccessible(true);fluxField.set(flux,stats);}
        }
        flux.resetFlux();flux.updateFluxValuesFromStats();flux.setCurrFlux(1000f);flux.setHardFlux(800f);
        flux.ventFlux();check(!flux.isVenting() && flux.getVentRate()==0f,"Real native SO ship rejects ordinary venting");
        var restriction=stats.getVentRateMult().getMultStatMod(HullMods.SAFETYOVERRIDES);
        float acceleration=stats.getAcceleration().getModifiedValue();
        flux.setOverloadDuration(10f);flux.setOverloadProgress(0f);
        check(flux.isOverloaded() && flux.getOverloadTimeRemaining()>0f,"Native SO overload is active before the trigger");
        ShipAPI ship=mock(ShipAPI.class,(n,a)->switch(n) {
            case "getCaptain" -> f.player;case "isAlive" -> true;
            case "getMutableStats" -> stats;case "getFluxTracker" -> flux;default -> null;
        });
        battle.clear();var effect=new PeerlessFieldModulation.EmergencyVent(ship);effect.advance(0f);
        check(!flux.isOverloaded() && !flux.isVenting(),"SO first overload is cancelled even though venting is prohibited");
        check(flux.getOverloadTimeRemaining()==0f,"SO first overload ends immediately");
        check(flux.getCurrFlux()==1000f && flux.getHardFlux()==800f,"SO trigger leaves all flux intact");
        check(effect.getManeuverabilityBonus()==200f && Math.abs(stats.getAcceleration().getModifiedValue()-3f*acceleration)<.001f,
                "SO movement receives the full boost despite refused venting");
        check(stats.getVentRateMult().getMultStatMod(HullMods.SAFETYOVERRIDES)==restriction,"SO restriction is never replaced or removed");
        effect.advance(2.5f);check(Math.abs(stats.getAcceleration().getModifiedValue()-2f*acceleration)<.001f,"SO boost decays normally");
        check(!flux.isOverloaded() && !flux.isVenting(),"Boost updates do not restart the cancelled overload or enable venting");
        effect.advance(2.5f);check(stats.getAcceleration().getModifiedValue()==acceleration,"SO's original movement bonuses survive expiry");
        flux.ventFlux();check(!flux.isVenting() && flux.getVentRate()==0f,"SO remains unable to vent after overload cancellation");
        flux.setOverloadDuration(10f);flux.setOverloadProgress(0f);effect.advance(0f);
        check(flux.isOverloaded() && effect.getManeuverabilityBonus()==0f,"SO second overload cannot retrigger the boost");
    }
}
