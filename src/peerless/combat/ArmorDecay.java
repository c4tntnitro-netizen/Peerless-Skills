package peerless.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.DisintegratorEffect;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import org.lwjgl.util.vector.Vector2f;

/** One scheduler and native Disintegrator visual per target; every hit keeps its own damage clock. */
public class ArmorDecay extends DisintegratorEffect {
    private static final String REGISTRY = "peerless_armor_decay_targets";
    private static final int DAMAGE_TICKS = 10;
    private static final double TICK_SECONDS = .5;
    public static final int MAX_PARTICLES = 24;
    private final PriorityQueue<Stack> pending = new PriorityQueue<>(
            Comparator.comparingDouble((Stack s) -> s.nextTick).thenComparingLong(s -> s.sequence));
    private Map<ShipAPI, ArmorDecay> registry;
    private DamageBatch batch;
    private double elapsed;
    private long sequence;
    private final Vector2f visualOffset = new Vector2f();
    private boolean emitting;

    public ArmorDecay(ShipAPI source, ShipAPI target, Vector2f point, float total) {
        // Center the shared renderer on the target; damage still uses each hit's local offset.
        super(null, target, new Vector2f());
        interval = new IntervalUtil(.5f, .5f);
        enqueue(source, point, total);
    }

    @SuppressWarnings("unchecked")
    public static void add(CombatEngineAPI engine, ShipAPI source, ShipAPI target, Vector2f point, float total) {
        if (!target.isAlive() || !engine.isEntityInPlay(target) || !Float.isFinite(total) || total <= 0f) return;
        Map<ShipAPI, ArmorDecay> effects = (Map<ShipAPI, ArmorDecay>) engine.getCustomData()
                .computeIfAbsent(REGISTRY, key -> new IdentityHashMap<ShipAPI, ArmorDecay>());
        ArmorDecay effect = effects.get(target);
        if (effect != null && !effect.isExpired()) {
            effect.enqueue(source, point, total);
            return;
        }
        effect = new ArmorDecay(source, target, point, total);
        effect.registry = effects;
        effects.put(target, effect);
        CombatEntityAPI visual = engine.addLayeredRenderingPlugin(effect);
        visual.getLocation().set(target.getLocation());
    }

    private void enqueue(ShipAPI source, Vector2f point, float total) {
        Vector2f local = Misc.rotateAroundOrigin(Vector2f.sub(point, target.getLocation(), new Vector2f()), -target.getFacing());
        pending.add(new Stack(source, local, total / DAMAGE_TICKS, elapsed, sequence++));
        // A hit during the old particles' fade reuses this renderer without reviving old damage.
        fader.fadeIn();
    }

    @Override public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        elapsed += Math.max(0f, amount);
        boolean valid = target.isAlive() && engine.isEntityInPlay(target);
        boolean hadDamage = !pending.isEmpty();
        if (!valid) pending.clear();
        if (valid && !pending.isEmpty() && pending.peek().nextTick <= elapsed + 1e-6) {
            if (batch == null) batch = new DamageBatch(target.getArmorGrid());
            while (!pending.isEmpty() && pending.peek().nextTick <= elapsed + 1e-6) {
                Stack hit = pending.remove();
                Vector2f point = Misc.rotateAroundOrigin(hit.local, target.getFacing());
                Vector2f.add(target.getLocation(), point, point);
                batch.add(point, hit.damage, getDamageTypeMult(hit.source, target));
                visualOffset.set(hit.local);
                if (++hit.delivered < DAMAGE_TICKS) {
                    hit.nextTick = hit.started + (hit.delivered + 1) * TICK_SECONDS;
                    pending.add(hit);
                }
            }
            if (batch.apply() > 0f) target.syncWithArmorGridState();
        }

        // Native code handles particle aging, sound and rendering. Its tick only emits
        // the shared visual; it never applies damage or determines a hit's expiry.
        emitting = valid && hadDamage;
        ticks = emitting ? 0 : DAMAGE_TICKS;
        super.advance(amount);
        ticks = pending.isEmpty() ? DAMAGE_TICKS : 0;
        if (isExpired()) detach();
    }

    @Override protected int getNumTicks() { return DAMAGE_TICKS; }
    @Override protected boolean canDamageHull() { return false; }
    @Override protected void dealDamage() {
        if (!emitting) return;
        int count = Math.min(getNumParticlesPerTick(), MAX_PARTICLES - particles.size());
        Vector2f relative = Misc.rotateAroundOrigin(visualOffset, target.getFacing());
        for (int i = 0; i < count; i++) {
            super.addParticle();
            ParticleData particle = particles.get(particles.size() - 1);
            Vector2f.add(particle.offset, relative, particle.offset);
        }
    }

    @Override public float getRenderRadius() { return Math.max(500f, target.getCollisionRadius() + 200f); }
    @Override public boolean isExpired() { return pending.isEmpty() && particles.isEmpty(); }
    @Override public void cleanup() {
        pending.clear();
        particles.clear();
        detach();
        super.cleanup();
    }
    private void detach() {
        if (registry != null && registry.get(target) == this) registry.remove(target);
    }

    private static final class Stack {
        final ShipAPI source;
        final Vector2f local;
        final float damage;
        final double started;
        final long sequence;
        double nextTick;
        int delivered;
        Stack(ShipAPI source, Vector2f local, float damage, double started, long sequence) {
            this.source = source; this.local = local; this.damage = damage;
            this.started = started; this.sequence = sequence; nextTick = started + TICK_SECONDS;
        }
    }

    /** Same weighted 21-cell patch as before, accumulated before one armor write per affected cell. */
    private static final class DamageBatch {
        final ArmorGridAPI armor;
        final float[][] damage;
        final int[] touchedX, touchedY;
        int touched;
        DamageBatch(ArmorGridAPI armor) {
            this.armor = armor;
            float[][] grid = armor.getGrid();
            damage = new float[grid.length][];
            int cells = 0;
            for (int x = 0; x < grid.length; x++) { damage[x] = new float[grid[x].length]; cells += grid[x].length; }
            touchedX = new int[cells]; touchedY = new int[cells];
        }
        void add(Vector2f point, float amount, float multiplier) {
            int[] cell = armor.getCellAtLocation(point);
            if (cell == null) return;
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) {
                if (Math.abs(x) == 2 && Math.abs(y) == 2) continue;
                int i = cell[0] + x, j = cell[1] + y;
                if (i < 0 || i >= damage.length || j < 0 || j >= damage[i].length) continue;
                float weight = Math.abs(x) <= 1 && Math.abs(y) <= 1 ? 1f / 15f : 1f / 30f;
                float delta = Math.max(0f, amount * multiplier * weight);
                if (delta <= 0f) continue;
                if (damage[i][j] == 0f) { touchedX[touched] = i; touchedY[touched++] = j; }
                damage[i][j] += delta;
            }
        }
        float apply() {
            float removed = 0f;
            for (int n = 0; n < touched; n++) {
                int x = touchedX[n], y = touchedY[n];
                float before = armor.getArmorValue(x, y);
                float delta = Math.min(before, damage[x][y]);
                if (delta > 0f) { armor.setArmorValue(x, y, before - delta); removed += delta; }
                damage[x][y] = 0f;
            }
            touched = 0;
            return removed;
        }
    }

    public static float applyArmorTick(ShipAPI target, Vector2f point, float damage, float multiplier) {
        DamageBatch batch = new DamageBatch(target.getArmorGrid());
        batch.add(point, damage, multiplier);
        float removed = batch.apply();
        if (removed > 0f) target.syncWithArmorGridState();
        return removed;
    }
}
