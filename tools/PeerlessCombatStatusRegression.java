package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import peerless.api.*;
import peerless.combat.*;
import static peerless.skills.PeerlessFixture.*;

/** Real effect/controller state feeds the public combat-status API. Does not simulate HUD rendering. */
public final class PeerlessCombatStatusRegression {
    record Status(String icon,String title,String effect,boolean debuff) {}
    public static void main(String[] args) {
        var f=new PeerlessFixture();PeerlessRewardsRegression.load(f);PeerlessSkills.unlockAllForTesting();
        Global.setSettings(mock(SettingsAPI.class,(name,a)->name.equals("getFloat")?1f:name.equals("getColor")?java.awt.Color.WHITE:null));
        boolean[] paused={false},system={false},venting={false},overloaded={false},alive={true},firing={false};
        float[] hp={1000f},mass={100f},cr={.7f},energyBonus={1.3f};int[] op={40};
        PersonAPI[] captain={f.player};ShipAPI[] piloted={null};
        List<Object> listeners=new ArrayList<>();Map<String,Object> battle=new HashMap<>();
        Map<String,MutableStat> values=new HashMap<>();Map<String,Status> shown=new LinkedHashMap<>();
        var variant=mock(ShipVariantAPI.class,(name,a)->name.equals("computeHullModOPCost")?op[0]:null);
        var stats=mock(MutableShipStatsAPI.class,(name,a)->{
            if(name.equals("getVariant"))return variant;if(name.equals("getDynamic"))return f.dynamic;
            if(name.startsWith("get"))return values.computeIfAbsent(name,k->new MutableStat(k.equals("getMaxSpeed")?100f:1f));
            return null;
        });
        var flux=mock(FluxTrackerAPI.class,(name,a)->switch(name){
            case "isVenting" -> venting[0];case "isOverloaded" -> overloaded[0];
            case "stopOverload" -> {overloaded[0]=false;yield null;}
            case "ventFlux" -> {venting[0]=true;yield null;}
            default -> null;
        });
        var missile=new PrototypeRegression.Launcher(28);
        var weapon=mock(WeaponAPI.class,(name,a)->switch(name){
            case "getType" -> WeaponAPI.WeaponType.ENERGY;
            case "hasAIHint" -> a[0]==WeaponAPI.AIHints.PD;
            case "isFiring" -> firing[0];
            default -> null;
        });
        List<WeaponAPI> weapons=new ArrayList<>(List.of(weapon,missile.weapon));
        var ship=mock(ShipAPI.class,(name,a)->switch(name){
            case "getCaptain" -> captain[0];case "isAlive" -> alive[0];
            case "getMutableStats" -> stats;case "getVariant" -> variant;
            case "getSystem" -> mock(ShipSystemAPI.class,(nn,aa)->nn.equals("isActive")?system[0]:null);
            case "getFluxTracker" -> flux;case "getAllWeapons" -> weapons;
            case "getMass" -> mass[0];case "setMass" -> {mass[0]=(float)a[0];yield null;}
            case "getHitpoints" -> hp[0];case "getMaxHitpoints" -> 1000f;case "setHitpoints" -> {hp[0]=(float)a[0];yield null;}
            case "getCurrentCR" -> cr[0];case "getFluxBasedEnergyWeaponDamageMultiplier" -> energyBonus[0];
            case "getHullSize" -> ShipAPI.HullSize.CRUISER;case "getLocation" -> new Vector2f();
            case "getListeners" -> listeners.stream().filter(((Class<?>)a[0])::isInstance).toList();
            case "hasListenerOfClass" -> listeners.stream().anyMatch(((Class<?>)a[0])::isInstance);
            case "addListener" -> {listeners.add(a[0]);yield null;}
            case "removeListenerOfClass" -> {listeners.removeIf(((Class<?>)a[0])::isInstance);yield null;}
            default -> null;
        });
        piloted[0]=ship;
        var engine=mock(CombatEngineAPI.class,(name,a)->switch(name){
            case "getPlayerShip" -> piloted[0];case "getShips" -> List.of(ship);
            case "isPaused" -> paused[0];case "getCustomData" -> battle;
            case "maintainStatusForPlayerShip" -> {shown.put((String)a[0],new Status((String)a[1],(String)a[2],(String)a[3],(boolean)a[4]));yield null;}
            default -> null;
        });
        Global.setCombatEngine(engine);var plugin=new PeerlessCombatPlugin();plugin.advance(0f,List.of());
        var controller=(PeerlessCombatPlugin.Controller)listeners.stream().filter(PeerlessCombatPlugin.Controller.class::isInstance).findFirst().orElseThrow();
        var enduring=(PeerlessCombatEndurance.Enduring)listeners.stream().filter(PeerlessCombatEndurance.Enduring.class::isInstance).findFirst().orElseThrow();
        var emergency=(PeerlessFieldModulation.EmergencyVent)listeners.stream().filter(PeerlessFieldModulation.EmergencyVent.class::isInstance).findFirst().orElseThrow();
        controller.advance(0f);shown.clear();plugin.advance(0f,List.of());
        check(shown.size()==7,"Seven applicable passive bonuses; dormant temporary effects are absent");
        check(status(shown,"damage_control").effect().startsWith("+70%"),"Repair speed follows current CR");
        check(status(shown,"ordnance_expert").effect().equals("+30 flux dissipation, +400 capacity"),"Hullmod flux bonuses show actual OP totals");
        firing[0]=true;controller.advance(5f);system[0]=true;missile.ammo=0;
        overloaded[0]=true;emergency.advance(0f);enduring.notifyAboutToTakeHullDamage(null,ship,null,5000f);controller.advance(0f);
        shown.clear();plugin.advance(0f,List.of());
        check(shown.size()==14,"All fourteen active Peerless effects have independent entries");
        for(String id:SkillCatalog.ids()) {
            Status status=status(shown,id);
            check(status.title().equals("Peerless "+SkillCatalog.NAMES.get(id)) && !status.debuff(),"Named positive status: "+id);
            check(Files.isRegularFile(Path.of(status.icon())),"Packaged icon: "+id);
        }
        check(status(shown,"combat_endurance").effect().endsWith("5.0s"),"Enduring time is shown");
        check(status(shown,"helmsmanship").effect().startsWith("+100 speed"),"Initial speed bonus shown");
        check(status(shown,"ballistic_mastery").effect().equals("+100% accuracy, -100% recoil"),"Actual firing ramp shown");
        Map<String,Status> beforePause=new HashMap<>(shown);paused[0]=true;
        controller.advance(10f);enduring.advance(10f);emergency.advance(10f);shown.clear();plugin.advance(10f,List.of());
        check(shown.equals(beforePause),"Paused HUD stays visible without advancing effects");
        paused[0]=false;controller.advance(.375f);enduring.advance(.375f);shown.clear();plugin.advance(0f,List.of());
        check(status(shown,"systems_expertise").effect().equals("25% less hull damage"),"Protection displays its decay");
        check(status(shown,"helmsmanship").effect().startsWith("+62.5 speed"),"Speed displays its decay");
        firing[0]=false;controller.advance(.25f);shown.clear();plugin.advance(0f,List.of());
        check(!shown.containsKey("peerless_status_systems_expertise") && status(shown,"ballistic_mastery").effect().startsWith("+50%"),"Expired protection disappears while accuracy decays");
        controller.advance(.5f);enduring.advance(5f);venting[0]=false;emergency.advance(0f);
        missile.ammo=missile.max;cr[0]=0f;energyBonus[0]=1f;op[0]=0;controller.advance(0f);
        shown.clear();plugin.advance(0f,List.of());
        check(shown.keySet().equals(Set.of("peerless_status_impact_mitigation","peerless_status_point_defense","peerless_status_target_analysis","peerless_status_gunnery_implants")),"Expired, completed and zero-value bonuses stop being refreshed");
        venting[0]=true;controller.advance(0f);shown.clear();plugin.advance(0f,List.of());
        check(shown.containsKey("peerless_status_polarized_armor") && !shown.containsKey("peerless_status_field_modulation"),"Later manual vents do not revive the one-use emergency status");
        weapons.clear();shown.clear();plugin.advance(0f,List.of());
        check(!shown.containsKey("peerless_status_point_defense") && !shown.containsKey("peerless_status_target_analysis"),"Weapon bonuses require applicable weapons");
        paused[0]=true;piloted[0]=mock(ShipAPI.class,(name,a)->name.equals("isAlive")?true:null);
        shown.clear();plugin.advance(0f,List.of());check(shown.isEmpty(),"No status leaks to an officer-controlled ship");
        piloted[0]=ship;captain[0]=null;shown.clear();plugin.advance(0f,List.of());check(shown.isEmpty(),"Captain gate applies while paused");
        captain[0]=f.player;alive[0]=false;shown.clear();plugin.advance(0f,List.of());check(shown.isEmpty(),"Dead ships have no Peerless status");
        alive[0]=true;f.skill=1f;shown.clear();plugin.advance(0f,List.of());check(shown.isEmpty(),"Elite is required");
        f.skill=2f;f.saved.clear();shown.clear();plugin.advance(0f,List.of());check(shown.isEmpty(),"Untrained skills have no status");
        Global.setCombatEngine(null);
        System.out.println("PASS: all fourteen HUD statuses, live values, durations, pause, expiry, weapon gates, one-use vent, ship/captain/Elite/training isolation.");
    }
    static Status status(Map<String,Status> shown,String id) {
        Status result=shown.get("peerless_status_"+id);check(result!=null,"Status present: "+id);return result;
    }
}
