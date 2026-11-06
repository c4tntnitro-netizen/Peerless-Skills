package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.DisintegratorEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.util.vector.Vector2f;
import peerless.api.*;
import peerless.combat.*;
import peerless.campaign.TrainingDialog;
import java.util.*;
import static peerless.skills.PeerlessFixture.*;

/** Invokes real controllers, listeners and the shared Disintegrator visual with public API entities. */
public final class PrototypeRegression {
    static void near(float actual,float expected,String why) { check(Math.abs(actual-expected)<.002f, why+": "+actual+" != "+expected); }
    static final class Launcher {
        int ammo, max;
        float nativeRegen;
        WeaponAPI.WeaponSize size=WeaponAPI.WeaponSize.LARGE;
        boolean firing;
        final WeaponAPI weapon=mock(WeaponAPI.class,(n,a)->switch(n) {
            case "getType" -> WeaponAPI.WeaponType.MISSILE;
            case "getSize" -> size;
            case "usesAmmo" -> true;
            case "getMaxAmmo" -> max;
            case "getAmmo" -> ammo;
            case "getAmmoPerSecond" -> nativeRegen;
            case "setAmmo" -> { ammo=(int)a[0]; yield null; }
            case "isFiring" -> firing;
            default -> null;
        });
        Launcher(int max) { this.max=max; ammo=max; }
    }
    public static void main(String[] args) throws Exception {
        Global.setSettings(mock(SettingsAPI.class,(n,a)->n.equals("getFloat")?1f:n.equals("getColor")?java.awt.Color.WHITE:
                n.equals("getSprite")?mock(com.fs.starfarer.api.graphics.SpriteAPI.class,(nn,aa)->nn.equals("getWidth")||nn.equals("getHeight")?64f:null):null));
        PeerlessFixture f=new PeerlessFixture(); PeerlessRewardsRegression.load(f);
        check(SkillCatalog.ids().size()==14,"All fourteen registered");
        PeerlessSkills.unlockAllForTesting();
        for(String id:SkillCatalog.ids())check(PeerlessSkills.isTrained(id),"Independent training: "+id);
        Map<String,MutableStat> values=new HashMap<>();
        ShipVariantAPI variant=mock(ShipVariantAPI.class,(n,a)->n.equals("computeHullModOPCost")?40:null);
        MutableShipStatsAPI stats=mock(MutableShipStatsAPI.class,(n,a)-> {
            if(n.equals("getVariant"))return variant;
            if(n.equals("getDynamic"))return f.dynamic;
            if(n.startsWith("get"))return values.computeIfAbsent(n,key->new MutableStat(
                    key.equals("getMaxSpeed")?100f:key.equals("getFluxCapacity")?10000f:key.equals("getFluxDissipation")?500f:1f));
            return null;
        });
        boolean[] system={false}, paused={false}, venting={false};
        float[] mass={100f}, cr={.7f};
        PersonAPI[] captain={f.player};
        List<WeaponAPI> weapons=new ArrayList<>();
        List<Object> listeners=new ArrayList<>();
        FluxTrackerAPI flux=mock(FluxTrackerAPI.class,(n,a)->n.equals("isVenting")?venting[0]:null);
        float[][] armor=new float[7][7]; for(float[] row:armor)Arrays.fill(row,100f);
        ArmorGridAPI grid=mock(ArmorGridAPI.class,(n,a)->switch(n) {
            case "getGrid" -> armor;
            case "getCellAtLocation" -> new int[]{3,3};
            case "getArmorValue" -> armor[(int)a[0]][(int)a[1]];
            case "setArmorValue" -> { armor[(int)a[0]][(int)a[1]]=(float)a[2]; yield null; }
            default -> null;
        });
        Vector2f location=new Vector2f();
        ShipAPI ship=mock(ShipAPI.class,(n,a)->switch(n) {
            case "getCaptain" -> captain[0];
            case "getMutableStats" -> stats;
            case "getSystem" -> mock(ShipSystemAPI.class,(n2,a2)->n2.equals("isActive")?system[0]:null);
            case "getFluxTracker" -> flux;
            case "getVelocity" -> new Vector2f(200f,0f);
            case "getLocation" -> location;
            case "getCurrentCR" -> cr[0];
            case "getMass" -> mass[0];
            case "setMass" -> { mass[0]=(float)a[0]; yield null; }
            case "isAlive" -> true;
            case "getAllWeapons" -> weapons;
            case "getArmorGrid" -> grid;
            case "getHullSize" -> ShipAPI.HullSize.CRUISER;
            case "getFluxBasedEnergyWeaponDamageMultiplier" -> 1.3f;
            case "hasListenerOfClass" -> listeners.stream().anyMatch(((Class<?>)a[0])::isInstance);
            case "addListener" -> { listeners.add(a[0]); yield null; }
            case "removeListenerOfClass" -> { listeners.removeIf(((Class<?>)a[0])::isInstance); yield null; }
            default -> null;
        });
        List<ArmorDecay> decays=new ArrayList<>();
        List<Float> emp=new ArrayList<>();
        Vector2f visualLocation=new Vector2f();
        CombatEntityAPI visual=mock(CombatEntityAPI.class,(n,a)->n.equals("getLocation")?visualLocation:null);
        Map<String,Object> combatData=new HashMap<>();
        CombatEngineAPI engine=mock(CombatEngineAPI.class,(n,a)->switch(n) {
            case "isPaused" -> paused[0];
            case "isEntityInPlay" -> true;
            case "getCustomData" -> combatData;
            case "getShips" -> List.of(ship);
            case "getPlayerShip" -> ship;
            case "addLayeredRenderingPlugin" -> { ArmorDecay d=(ArmorDecay)a[0]; d.init(visual); decays.add(d); yield visual; }
            case "applyDamage" -> { emp.add((float)a[4]); yield null; }
            default -> null;
        });
        Global.setCombatEngine(engine);
        Global.setSoundPlayer(mock(SoundPlayerAPI.class,(n,a)->null));
        var plugin=new PeerlessCombatPlugin(); plugin.advance(0f,List.of());
        var controller=(PeerlessCombatPlugin.Controller)listeners.stream().filter(x->x instanceof PeerlessCombatPlugin.Controller).findFirst().orElseThrow();
        check(listeners.stream().anyMatch(x->x instanceof PeerlessCombatEndurance.Enduring),"Endurance installed without skill definition");
        check(listeners.stream().anyMatch(x->x instanceof PeerlessFieldModulation.EmergencyVent),"Field Modulation installed without skill definition");
        plugin.advance(0f,List.of());
        check(listeners.stream().filter(x->x instanceof PeerlessCombatEndurance.Enduring).count()==1,"No duplicate protection listener");
        controller.advance(0f);
        near(mass[0],200f,"Mass doubled"); controller.advance(0f);near(mass[0],200f,"Mass does not compound");
        near(values.get("getCombatWeaponRepairTimeMult").getModifiedValue(),1f/1.7f,"CR gives repair speed, not time subtraction");
        venting[0]=true;controller.advance(0f);
        near(values.get("getCombatEngineRepairTimeMult").getModifiedValue(),1f/2.7f,"Venting and CR repair bonuses add");
        near(values.get("getFluxDissipation").getFlatMod(),30f,"Hullmod dissipation at forty OP");
        near(values.get("getFluxCapacity").getFlatMod(),400f,"Hullmod capacity at forty OP");
        near(f.dynamicMods.get(Stats.ELECTRONIC_WARFARE_MAX).getFlatBonus(),5f,"ECM cap on commander");
        system[0]=true;controller.advance(0f);
        near(values.get("getMaxSpeed").getFlatMod(),100f,"Burst snapshots top speed with a one hundred cap");
        near(values.get("getAcceleration").getModifiedValue(),2f,"Burst doubles acceleration");
        near(values.get("getDeceleration").getModifiedValue(),2f,"Burst doubles deceleration");
        near(values.get("getTurnAcceleration").getModifiedValue(),2f,"Burst doubles turn acceleration");
        near(values.get("getMaxTurnRate").getModifiedValue(),2f,"Burst doubles maximum turn rate");
        near(values.get("getHullDamageTakenMult").getModifiedValue(),.5f,"Initial hull protection");
        controller.advance(.25f);near(values.get("getHullDamageTakenMult").getModifiedValue(),.5f,"Protection holds for quarter second");
        controller.advance(.125f);near(values.get("getHullDamageTakenMult").getModifiedValue(),.75f,"Protection decays linearly");
        controller.advance(.125f);near(values.get("getHullDamageTakenMult").getModifiedValue(),1f,"Protection ends at half second");
        controller.advance(.5f);near(values.get("getMaxSpeed").getFlatMod(),0f,"Burst expires even if system remains active");
        near(values.get("getTurnAcceleration").getModifiedValue(),1f,"Turning bonus expires with speed boost");
        system[0]=false;controller.advance(0f);system[0]=true;controller.advance(0f);
        near(values.get("getMaxSpeed").getFlatMod(),100f,"Reactivation refreshes without stacking");
        values.get("getMaxSpeed").setBaseValue(60f);
        system[0]=false;controller.advance(0f);system[0]=true;controller.advance(0f);
        near(values.get("getMaxSpeed").getFlatMod(),60f,"Slow ships receive one hundred percent of top speed");
        values.get("getMaxSpeed").setBaseValue(180f);
        system[0]=false;controller.advance(0f);system[0]=true;controller.advance(0f);
        near(values.get("getMaxSpeed").getFlatMod(),100f,"Fast ships receive the lower flat cap without compounding");
        Launcher reaper=new Launcher(28), locust=new Launcher(1200), medium=new Launcher(10), nativeReload=new Launcher(100);
        medium.size=WeaponAPI.WeaponSize.MEDIUM;nativeReload.nativeRegen=1f;
        weapons.addAll(List.of(reaper.weapon,locust.weapon,medium.weapon,nativeReload.weapon));
        controller.advance(60f);check(reaper.ammo==28 && locust.ammo==1200,"No regeneration before depletion");
        check(controller.missileReloadProgress(reaper.weapon)<0f,"Native HUD bar before first depletion");
        reaper.ammo=0;locust.ammo=0;medium.ammo=0;nativeReload.ammo=0;
        controller.advance(30f);check(reaper.ammo==0 && locust.ammo==30,"Thirty second budgets");
        near(controller.missileReloadProgress(reaper.weapon),.5f,"Reaper HUD half full at thirty seconds");
        near(controller.missileReloadProgress(locust.weapon),0f,"Locust HUD resets each regenerated missile");
        controller.advance(30f);check(reaper.ammo==1 && locust.ammo==60,"Sixty second Reaper and Locust targets");
        check(medium.ammo==0 && nativeReload.ammo==0,"Exclude medium and native regenerators");
        check(controller.missileReloadProgress(medium.weapon)<0f && controller.missileReloadProgress(nativeReload.weapon)<0f,"Non-Peerless regeneration keeps native bars");
        controller.advance(60f);check(reaper.ammo==2 && locust.ammo==120,"Regeneration continues while ammo remains");
        controller.advance(15f);near(controller.missileReloadProgress(reaper.weapon),.25f,"Green reload bar remains active with ammo available");
        f.levels.put("missile_specialization",0f);check(controller.missileReloadProgress(reaper.weapon)<0f,"Deselection restores native HUD bar");f.levels.remove("missile_specialization");
        int savedAmmo=reaper.ammo;reaper.ammo=reaper.max;check(controller.missileReloadProgress(reaper.weapon)<0f,"Full launcher restores native HUD bar");reaper.ammo=savedAmmo;
        locust.firing=true;controller.advance(5f);
        near(values.get("getRecoilPerShotMult").getModifiedValue(),0f,"Full recoil reduction after five seconds");
        locust.firing=false;controller.advance(.25f);
        near(values.get("getMaxRecoilMult").getModifiedValue(),.5f,"Half of recoil bonus remains after quarter second");
        controller.advance(.25f);near(values.get("getMaxRecoilMult").getModifiedValue(),1f,"Bonus decays fully in half second");
        captain[0]=null;controller.advance(0f);near(mass[0],100f,"Captain change restores mass");
        near(values.get("getCombatEngineRepairTimeMult").getModifiedValue(),1f,"Captain change removes repair modifiers");
        near(values.get("getFluxCapacity").getFlatMod(),0f,"Captain change removes hullmod capacity");
        captain[0]=f.player;f.levels.put("impact_mitigation",0f);controller.advance(0f);
        near(mass[0],100f,"SiC deselection deactivates without forgetting purchase");
        check(PeerlessSkills.isTrained("impact_mitigation"),"Purchased training persists");
        f.levels.put("impact_mitigation",2f);controller.advance(0f);near(mass[0],200f,"SiC reselection reactivates");
        paused[0]=true;controller.advance(60f);check(reaper.ammo==2,"Paused combat does not regenerate");paused[0]=false;

        // Projectile and beam hit semantics, additive EMP, shield gate, and per-second beam cap.
        WeaponAPI pd=mock(WeaponAPI.class,(n,a)->switch(n) {
            case "hasAIHint" -> a[0]==WeaponAPI.AIHints.PD;
            case "getType" -> WeaponAPI.WeaponType.ENERGY;
            case "getSpec" -> mock(com.fs.starfarer.api.loading.WeaponSpecAPI.class,(nn,aa)->nn.equals("getType")?WeaponAPI.WeaponType.ENERGY:null);
            default -> null;
        });
        DamagingProjectileAPI projectile=mock(DamagingProjectileAPI.class,(n,a)->n.equals("getWeapon")?pd:n.equals("getSpawnLocation")?new Vector2f():null);
        float[] hitDamage={500f}, duration={1f};boolean[] dps={false};
        MutableStat modifier=new MutableStat(1f);
        DamageAPI hit=mock(DamageAPI.class,(n,a)->switch(n) {
            case "getDamage" -> hitDamage[0];
            case "getMultiplier" -> 1f;
            case "isDps" -> dps[0];
            case "getDpsDuration" -> duration[0];
            case "getModifier" -> modifier;
            default -> null;
        });
        controller.modifyDamageDealt(projectile,ship,hit,new Vector2f(),true);controller.advance(0f);
        check(decays.isEmpty() && emp.isEmpty(),"Shielded hits block decay and EMP even if shields drop before next update");
        controller.modifyDamageDealt(projectile,ship,hit,new Vector2f(),false);controller.advance(0f);
        check(decays.size()==1,"Unshielded hit creates Disintegrator subclass");
        near(emp.get(0),50f,"Ten percent EMP on unshielded hits");
        check(decays.get(0) instanceof DisintegratorEffect,"Native Disintegrator code reused");
        hitDamage[0]=2000f;dps[0]=true;duration[0]=.1f;
        BeamAPI beam=mock(BeamAPI.class,(n,a)->n.equals("getWeapon")?pd:n.equals("getFrom")?new Vector2f():null);
        controller.modifyDamageDealt(beam,ship,hit,new Vector2f(),false);controller.advance(0f);
        near(emp.get(emp.size()-1),20f,"Beam EMP accounts for tick duration");
        var range=new PeerlessCombatPlugin.EnergyRange(ship);
        range.modifyDamageDealt(projectile,ship,hit,new Vector2f(1300f,0f),false);
        near(modifier.getPercentMod(),15f,"Energy bonus is half at 1300 range");
        range.modifyDamageDealt(projectile,ship,hit,new Vector2f(600f,0f),false);
        near(modifier.getPercentMod(),30f,"Energy bonus floor remains 600");
        range.modifyDamageDealt(projectile,ship,hit,new Vector2f(2000f,0f),false);
        near(modifier.getPercentMod(),0f,"Energy bonus ends at 2000");
        float tickStart=sum(armor);
        check(decays.size()==1,"Projectile and beam damage share one target renderer");
        decays.get(0).advance(.5f);
        near(tickStart-sum(armor),6f,"Shared first tick includes five projectile damage plus one normalized beam damage");
        near(ArmorDecay.applyArmorTick(ship,new Vector2f(),100f,1f),100f,"Native weighted armor patch totals damage");
        for(float[] row:armor)Arrays.fill(row,0f);
        near(ArmorDecay.applyArmorTick(ship,new Vector2f(),100f,1f),0f,"No armor spillover into hull");
        for(float[] row:armor)Arrays.fill(row,100f);
        ArmorDecay nativeDecay=new ArmorDecay(ship,ship,new Vector2f(),100f) {
            @Override protected int getNumParticlesPerTick(){return 0;}
        };
        nativeDecay.init(visual);
        float before=sum(armor);nativeDecay.advance(.49f);near(sum(armor),before,"No immediate decay burst");
        for(int i=0;i<10;i++)nativeDecay.advance(.5f);
        near(before-sum(armor),100f,"Ten native Disintegrator ticks deliver capped total over five seconds");
        float after=sum(armor);nativeDecay.advance(2f);near(sum(armor),after,"Decay ends after ten ticks");

        // Exercise actual standard text dialog: inspect, cancel, spend once, and both pages.
        f.saved.clear();f.skill=2f;PeerlessSkills.awardOnce("$dialog_test");
        List<String> texts=new ArrayList<>();Map<Object,String> options=new LinkedHashMap<>();
        TextPanelAPI text=mock(TextPanelAPI.class,(n,a)->{if(n.equals("addPara"))texts.add((String)a[0]);return null;});
        OptionPanelAPI option=mock(OptionPanelAPI.class,(n,a)->{
            if(n.equals("clearOptions"))options.clear();if(n.equals("addOption"))options.put(a[1],(String)a[0]);return null;
        });
        InteractionDialogAPI dialog=mock(InteractionDialogAPI.class,(n,a)->switch(n){
            case "getTextPanel" -> text;case "getOptionPanel" -> option;
            case "getVisualPanel" -> mock(VisualPanelAPI.class,(nn,aa)->null);default -> null;
        });
        TrainingDialog training=new TrainingDialog();training.init(dialog);
        check(options.containsKey("view:helmsmanship"),"First training page");
        training.optionSelected("","page");check(options.containsKey("view:polarized_armor"),"Second training page");
        training.optionSelected("","view:polarized_armor");check(PeerlessSkills.getPoints()==1,"Inspect does not spend");
        training.optionSelected("","back");check(PeerlessSkills.getPoints()==1,"Back does not spend");
        training.optionSelected("","view:polarized_armor");training.optionSelected("","train");
        check(PeerlessSkills.isTrained("polarized_armor") && PeerlessSkills.getPoints()==0,"Confirmed training spends exactly once");
        training.optionSelected("","train");check(PeerlessSkills.getPoints()==0,"Stale confirmation cannot double-spend");
        Map<String,Object> receipts=new HashMap<>();
        Global.setCombatEngine(mock(CombatEngineAPI.class,(n,a)->n.equals("getCustomData")?receipts:null));
        check(BattleUses.claim(ship,"endurance") && !BattleUses.claim(ship,"endurance"),"Combat receipt prevents resetting once-per-battle effect");
        check(BattleUses.claim(ship,"vent"),"Each once-per-battle skill has its own receipt");
        Global.setCombatEngine(null);Global.setSoundPlayer(null);
        System.out.println("PASS: all 14 skills, combat activation/expiry, repair stacking, player/SiC gates, native decay, missiles, ECM, text training and stale clicks.");
    }
    static float sum(float[][] grid){float result=0f;for(float[] row:grid)for(float v:row)result+=v;return result;}
}
