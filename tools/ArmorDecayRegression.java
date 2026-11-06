package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.DisintegratorEffect;
import java.lang.reflect.Field;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import peerless.combat.ArmorDecay;
import static peerless.skills.PeerlessFixture.*;

/** Damage schedule and structural load checks; does not claim live FPS or VRAM measurements. */
public final class ArmorDecayRegression {
    static void near(double actual, double expected, String label) {
        check(Math.abs(actual-expected)<.08, label+": "+actual+" != "+expected);
    }
    static int particles(ArmorDecay decay) throws Exception {
        Field field=DisintegratorEffect.class.getDeclaredField("particles");field.setAccessible(true);
        return ((List<?>)field.get(decay)).size();
    }
    static int stacks(ArmorDecay decay) throws Exception {
        Field field=ArmorDecay.class.getDeclaredField("pending");field.setAccessible(true);
        return ((Collection<?>)field.get(decay)).size();
    }
    static final class Target {
        final float[][] armor=new float[15][15];
        final Vector2f location=new Vector2f();
        float facing, multiplier=1f;
        boolean alive=true, inPlay=true;
        int syncs,writes;
        final ArmorGridAPI grid;
        final ShipAPI ship;
        Target() {
            for(float[] row:armor)Arrays.fill(row,1000f);
            grid=mock(ArmorGridAPI.class,(n,a)->switch(n) {
                case "getGrid" -> armor;
                case "getCellAtLocation" -> {
                    Vector2f p=(Vector2f)a[0];double radians=Math.toRadians(-facing);
                    double x=p.x-location.x,y=p.y-location.y;
                    int cx=(int)Math.round(x*Math.cos(radians)-y*Math.sin(radians))+7;
                    int cy=(int)Math.round(x*Math.sin(radians)+y*Math.cos(radians))+7;
                    yield cx<0||cy<0||cx>=15||cy>=15?null:new int[]{cx,cy};
                }
                case "getArmorValue" -> armor[(int)a[0]][(int)a[1]];
                case "setArmorValue" -> {armor[(int)a[0]][(int)a[1]]=(float)a[2];writes++;yield null;}
                default -> null;
            });
            var stats=mock(MutableShipStatsAPI.class,(n,a)->n.equals("getArmorDamageTakenMult")?new MutableStat(multiplier):new MutableStat(1f));
            ship=mock(ShipAPI.class,(n,a)->switch(n) {
                case "getArmorGrid" -> grid;case "getLocation" -> location;
                case "getFacing" -> facing;case "getVelocity" -> new Vector2f();
                case "getMutableStats" -> stats;case "getHullSize" -> ShipAPI.HullSize.CRUISER;
                case "isAlive" -> alive;case "getCollisionRadius" -> 100f;
                case "syncWithArmorGridState" -> {syncs++;yield null;}
                case "setHitpoints" -> throw new AssertionError("Decay must never damage hull");
                default -> null;
            });
        }
        double sum() {double sum=0;for(float[] row:armor)for(float value:row)sum+=value;return sum;}
    }
    static final class Battle {
        final Map<String,Object> data=new HashMap<>();
        final Map<ShipAPI,Target> targets=new IdentityHashMap<>();
        final List<ArmorDecay> effects=new ArrayList<>();
        final CombatEngineAPI engine;
        boolean paused;
        int sprites;
        Battle() {
            Global.setSettings(mock(SettingsAPI.class,(n,a)->{
                if(n.equals("getSprite")){sprites++;return mock(SpriteAPI.class,(nn,aa)->null);}
                return null;
            }));
            Global.setSoundPlayer(mock(SoundPlayerAPI.class,(n,a)->null));
            engine=mock(CombatEngineAPI.class,(n,a)->switch(n) {
                case "getCustomData" -> data;case "isPaused" -> paused;
                case "isEntityInPlay" -> targets.containsKey(a[0])&&targets.get(a[0]).inPlay;
                case "addLayeredRenderingPlugin" -> {
                    ArmorDecay effect=(ArmorDecay)a[0];effects.add(effect);
                    Vector2f location=new Vector2f();
                    var entity=mock(CombatEntityAPI.class,(nn,aa)->nn.equals("getLocation")?location:null);
                    effect.init(entity);yield entity;
                }
                default -> null;
            });
            Global.setCombatEngine(engine);
        }
        Target target(){Target t=new Target();targets.put(t.ship,t);return t;}
        void hit(Target target,Vector2f point,float total){ArmorDecay.add(engine,target.ship,target.ship,point,total);}
        void step(float amount){for(ArmorDecay effect:List.copyOf(effects))if(!effect.isExpired())effect.advance(amount);}
        boolean registryEmpty(){return data.values().stream().allMatch(v->!(v instanceof Map)||((Map<?,?>)v).isEmpty());}
    }
    public static void main(String[] args) throws Exception {
        // Independent deadlines: hits at t=0 and t=.25; neither refreshes the other.
        Battle b=new Battle();Target t=b.target();double start=t.sum();
        b.hit(t,new Vector2f(),100f);b.step(.25f);b.hit(t,new Vector2f(),50f);
        check(b.effects.size()==1,"One renderer for overlapping hits");
        b.step(.24f);near(start-t.sum(),0,"No early damage");b.step(.01f);near(start-t.sum(),10,"First hit ticks at .5s");
        b.paused=true;b.step(20f);near(start-t.sum(),10,"Pause freezes both schedules");b.paused=false;
        b.step(.25f);near(start-t.sum(),15,"Second hit ticks at its own .5s");
        for(int i=0;i<17;i++)b.step(.25f);
        near(start-t.sum(),145,"At t=5 first hit is complete, second has one tick left");
        b.step(.25f);near(start-t.sum(),150,"Both full amounts delivered at their five-second deadlines");
        check(stacks(b.effects.get(0))==0,"Completed damage stacks released before visual tail ends");
        b.step(1f);near(start-t.sum(),150,"Old damage does not continue");
        b.hit(t,new Vector2f(),20f);check(b.effects.size()==1,"Hit during visual tail reuses renderer");
        b.step(5f);near(start-t.sum(),170,"Long frame catches up all ten new ticks without reviving old ones");
        b.step(5.1f);check(b.effects.get(0).isExpired()&&b.registryEmpty(),"Visuals expire and registry releases target");
        b.hit(t,new Vector2f(),10f);check(b.effects.size()==2,"Fresh effect after complete cleanup");

        // Different impact locations remain independent as a target moves and rotates.
        b=new Battle();t=b.target();start=t.sum();
        b.hit(t,new Vector2f(-4f,0),100f);b.hit(t,new Vector2f(4f,0),200f);
        t.location.set(100f,200f);t.facing=90f;b.step(.5f);
        near(1000-t.armor[3][7],10.0/15,"First hit retains its local armor patch");
        near(1000-t.armor[11][7],20.0/15,"Second hit retains its own local armor patch");
        check(t.syncs==1,"One armor sync for both hits");
        t.multiplier=2f;b.step(4.5f);near(start-t.sum(),570,"Each due tick uses current damage modifiers");
        Target other=b.target();b.hit(other,new Vector2f(),40f);check(b.effects.size()==2,"Separate targets have separate managers");

        // A large simultaneous volley batches cell writes and cannot bypass the visual cap.
        b=new Battle();t=b.target();start=t.sum();
        for(int i=0;i<1000;i++)b.hit(t,new Vector2f(),10f);
        check(b.effects.size()==1,"Thousand-hit volley creates one renderer");
        for(int i=0;i<10;i++) {
            int writes=t.writes,syncs=t.syncs;b.step(.5f);
            check(t.writes-writes==21&&t.syncs-syncs==1,"One write per patch cell and one sync per update");
            check(particles(b.effects.get(0))<=ArmorDecay.MAX_PARTICLES,"Hard per-target particle cap");
        }
        near(start-t.sum(),10000,"Thousand hits stack without dropping or refreshing damage");
        check(b.sprites<=30,"Volley's sprite allocations bounded by emission rate, not hit count");

        // Sustained beam-like callbacks: lightweight stacks are retained only for five seconds.
        b=new Battle();t=b.target();start=t.sum();
        for(int frame=0;frame<600;frame++) {
            for(int hit=0;hit<10;hit++)b.hit(t,new Vector2f(),.1f);
            b.step(1f/60f);
            check(b.effects.size()==1&&particles(b.effects.get(0))<=ArmorDecay.MAX_PARTICLES,"Sustained fire keeps one capped visual");
            check(stacks(b.effects.get(0))<=3010,"Completed hits do not accumulate indefinitely");
        }
        for(int frame=0;frame<300;frame++)b.step(1f/60f);
        near(start-t.sum(),600,"Six thousand small hits preserve total stacked damage");
        check(b.sprites<=90,"Particle creation stays at most six per second, independent of callback count");
        b.step(5.1f);check(b.registryEmpty(),"Sustained-fire registry is released after visual tail");

        // Death/removal, empty armor, invalid impacts, paused emission and cleanup.
        b=new Battle();t=b.target();start=t.sum();b.hit(t,new Vector2f(),100f);b.step(.5f);
        int sprites=b.sprites;t.alive=false;b.step(5.1f);
        near(start-t.sum(),10,"Death stops pending damage");check(b.sprites==sprites&&b.registryEmpty(),"Death stops particles and releases target");
        t.alive=true;t.inPlay=true;b.hit(t,new Vector2f(),100f);t.inPlay=false;b.step(5.1f);
        near(start-t.sum(),10,"Removal stops pending damage");check(b.registryEmpty(),"Removal releases target");
        t.inPlay=true;for(float[] row:t.armor)Arrays.fill(row,0f);b.hit(t,new Vector2f(),100f);b.step(5f);near(t.sum(),0,"No negative armor or hull spill");
        b.effects.get(b.effects.size()-1).cleanup();check(b.registryEmpty(),"Explicit cleanup releases references");
        int count=b.effects.size();b.hit(t,new Vector2f(),Float.NaN);b.hit(t,new Vector2f(),0f);check(count==b.effects.size(),"Invalid damage creates no effect");
        b.hit(t,new Vector2f(1000f,1000f),100f);b.step(5f);check(stacks(b.effects.get(b.effects.size()-1))==0,"Off-grid damage expires normally");
        Global.setCombatEngine(null);Global.setSoundPlayer(null);
        System.out.println("PASS: independent five-second damage stacks, per-hit locations/modifiers, batched cell writes, 1000-hit volley, 6000-hit sustained load, 24-particle cap, pause and cleanup.");
    }
}
