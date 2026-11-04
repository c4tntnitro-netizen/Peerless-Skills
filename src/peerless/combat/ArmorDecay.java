package peerless.combat;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.DisintegratorEffect;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

/** Uses the native Disintegrator renderer, timing lifecycle, and armor-distribution algorithm.
 * The damage routine accepts a firing ship directly so beam hits need no synthetic projectile. */
public class ArmorDecay extends DisintegratorEffect {
    private final ShipAPI source;
    private final float total;
    public ArmorDecay(ShipAPI source, ShipAPI target, Vector2f point, float total) {
        super(null, target, Misc.rotateAroundOrigin(Vector2f.sub(point, target.getLocation(), new Vector2f()), -target.getFacing()));
        this.source=source; this.total=total;
        interval = new IntervalUtil(.5f, .5f); // Ten ticks, first at .5s, final at 5s.
    }
    public static void add(CombatEngineAPI engine, ShipAPI source, ShipAPI target, Vector2f point, float total) {
        CombatEntityAPI visual = engine.addLayeredRenderingPlugin(new ArmorDecay(source, target, point, total));
        visual.getLocation().set(point);
    }
    @Override protected float getTotalDamage() { return total; }
    @Override protected int getNumTicks() { return 10; }
    @Override protected boolean canDamageHull() { return false; }
    @Override protected void dealDamage() {
        for (int i=0; i<getNumParticlesPerTick(); i++) addParticle();
        applyArmorTick(target, entity.getLocation(), total / getNumTicks(), getDamageTypeMult(source, target));
    }
    /** Native Disintegrator's weighted 21-cell patch; absent armor never spills into hull. */
    public static float applyArmorTick(ShipAPI target, Vector2f point, float damage, float multiplier) {
        ArmorGridAPI armor = target.getArmorGrid();
        int[] cell = armor.getCellAtLocation(point);
        if (cell == null) return 0f;
        float removed = 0f;
        float[][] grid = armor.getGrid();
        for (int x=-2; x<=2; x++) for (int y=-2; y<=2; y++) {
            if (Math.abs(x)==2 && Math.abs(y)==2) continue;
            int i=cell[0]+x, j=cell[1]+y;
            if (i<0 || i>=grid.length || j<0 || j>=grid[i].length) continue;
            float weight = Math.abs(x)<=1 && Math.abs(y)<=1 ? 1f/15f : 1f/30f;
            float before = armor.getArmorValue(i,j);
            float delta = Math.min(before, Math.max(0f, damage * multiplier * weight));
            if (delta > 0f) { armor.setArmorValue(i,j,before-delta); removed+=delta; }
        }
        if (removed>0f) target.syncWithArmorGridState();
        return removed;
    }
}
