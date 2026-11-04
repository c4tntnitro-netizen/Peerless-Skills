package peerless.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.combat.K;
import com.fs.starfarer.renderers.A.G;
import com.fs.starfarer.renderers.A.I;
import com.fs.starfarer.ui.OO0O;
import com.fs.starfarer.ui.c;
import com.fs.starfarer.ui.interfacenew;
import java.awt.Color;
import java.util.*;
import java.util.function.DoubleSupplier;

/** Isolated Starsector 0.98a HUD adapter. The public combat API has no per-weapon bar-color
 * hook. Replace only the loading bar inside a native weapon row, retain its live native
 * position, and delegate back to the original bar whenever Peerless is not reloading.
 * No reflection, global HUD colors, hardcoded screen coordinates, or ammo-tracker writes. */
public final class MissileReloadHud {
    public static final Color GREEN=new Color(100,255,140);
    private final Map<interfacenew,Replacement> bars=new IdentityHashMap<>();
    private ShipAPI ship;
    private K ui;
    private long nextScan;
    private boolean failed;

    public void update(CombatEngineAPI engine,PeerlessCombatPlugin.Controller controller) {
        if (failed) return;
        try {
            if (!(engine.getCombatUI() instanceof K) || controller==null) { restore(); return; }
            K current=(K)engine.getCombatUI();
            ShipAPI player=engine.getPlayerShip();
            if (current!=ui || player!=ship) {
                restore();ui=current;ship=player;nextScan=0L;
            }
            long now=System.nanoTime();
            if (now<nextScan) return;
            nextScan=now+200_000_000L;
            interfacenew root=current.getScreenPanel();
            if (root==null) return;
            Set<interfacenew> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<interfacenew> pending=new ArrayDeque<>();pending.add(root);
            int visited=0;
            while (!pending.isEmpty() && visited++<2048) {
                interfacenew panel=pending.removeFirst();
                if (!seen.add(panel)) continue;
                if (panel instanceof G) {
                    G row=(G)panel;
                    if (!bars.containsKey(row) && controller.tracksMissileReload(row.getWeapon())) {
                        for (c child:row.getChildrenCopy()) {
                            if (child instanceof I && !(child instanceof Replacement)) {
                                final com.fs.starfarer.api.combat.WeaponAPI weapon=row.getWeapon();
                                bars.put(row,replace(row,(I)child,()->controller.missileReloadProgress(weapon)));
                                break;
                            }
                        }
                    }
                }
                for (c child:panel.getChildrenCopy()) if (child instanceof interfacenew) pending.add((interfacenew)child);
            }
            // HUD group rebuilds retire rows. Restore them before dropping our references.
            Iterator<Map.Entry<interfacenew,Replacement>> iterator=bars.entrySet().iterator();
            while(iterator.hasNext()) {
                var entry=iterator.next();
                if(!seen.contains(entry.getKey())) { entry.getValue().restore();iterator.remove(); }
            }
        } catch (RuntimeException | LinkageError ex) {
            failed=true;
            restore();
            Global.getLogger(MissileReloadHud.class).warn("Peerless missile HUD disabled; native loading bars retained",ex);
        }
    }
    private void restore() {
        for(Replacement bar:bars.values()) {
            // Retired/rebuilt HUD trees must never make a display-only feature interrupt combat.
            try { bar.restore(); } catch(RuntimeException | LinkageError ignored) { }
        }
        bars.clear();ship=null;ui=null;
    }

    /** Public for the native-widget regression harness; not a gameplay integration API. */
    public static Replacement replace(interfacenew row,I original,DoubleSupplier progress) {
        Replacement result=new Replacement(row,original,progress);
        List<c> children=row.getChildrenNonCopy();
        int index=children.indexOf(original);
        if(index<0)throw new IllegalArgumentException("Loading bar is no longer in its native row");
        result.setParent(row);
        result.setPos(result.position);
        // Preserve the exact slot and native anchor graph; add/remove would re-anchor the bar.
        children.set(index,result);row.setNeedToCopy(true);
        return result;
    }

    public static final class Replacement extends I {
        private final interfacenew row;
        private final I original;
        private final OO0O position;
        private final DoubleSupplier progress;
        private boolean restored;
        private Replacement(interfacenew row,I original,DoubleSupplier progress) {
            super(GREEN);
            this.row=row;this.original=original;this.position=original.getPosition();this.progress=progress;
        }
        @Override public void advance(float amount) {
            original.advance(amount);
            super.advance(amount);
        }
        @Override public void render(float alpha) {
            if(restored) { original.render(alpha);return; }
            float fill=(float)progress.getAsDouble();
            if (!Float.isFinite(fill) || fill<0f) { original.render(alpha);return; }
            update(Math.min(1f,fill));
            // Native coordinates keep UI scaling, row shifts, and HUD transitions aligned.
            // Use this bar's native renderer, without mutating the original charge/firing bar.
            super.render(position.getX(),position.getY(),position.getWidth(),position.getHeight(),alpha);
        }
        public void restore() {
            if(restored)return;
            restored=true;
            List<c> children=row.getChildrenNonCopy();int index=children.indexOf(this);
            if(index>=0) { children.set(index,original);row.setNeedToCopy(true); }
        }
    }
}
