package peerless.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.skills.EnergyWeaponMastery;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import peerless.api.PeerlessSkills;
import peerless.skills.PeerlessCombatEndurance;
import peerless.skills.PeerlessFieldModulation;
import java.util.*;

/** Public-API combat hook; independent of vanilla/SiC skill-definition load order. */
public final class PeerlessCombatPlugin extends BaseEveryFrameCombatPlugin {
    public static final String ID = "peerless_prototype";
    private final Map<ShipAPI, Controller> controllers = new IdentityHashMap<>();
    private final MissileReloadHud missileHud=new MissileReloadHud();
    @Override public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || Global.getSector() == null) return;
        if(engine.isPaused()) {
            missileHud.update(engine,controllers.get(engine.getPlayerShip()));
            PeerlessCombatStatus.update(engine,controllers.get(engine.getPlayerShip()));
            return;
        }
        updateCommanderCap();
        for (ShipAPI ship : engine.getShips()) {
            if (!controllers.containsKey(ship) && player(ship)) {
                Controller c = new Controller(ship);
                controllers.put(ship, c);
                ship.addListener(c);
            }
            if (controllers.containsKey(ship) && player(ship)) {
                new PeerlessCombatEndurance().applyEffectsAfterShipCreation(ship, ID);
                new PeerlessFieldModulation().applyEffectsAfterShipCreation(ship, ID);
                if (active(ship, "energy_weapon_mastery") && !ship.hasListenerOfClass(EnergyRange.class)) {
                    ship.removeListenerOfClass(EnergyWeaponMastery.EWMDamageDealtMod.class);
                    ship.addListener(new EnergyRange(ship));
                }
            }
        }
        missileHud.update(engine,controllers.get(engine.getPlayerShip()));
        PeerlessCombatStatus.update(engine,controllers.get(engine.getPlayerShip()));
    }
    public static boolean player(ShipAPI ship) {
        return Global.getSector() != null && ship.getCaptain() != null
                && ship.getCaptain() == Global.getSector().getPlayerPerson();
    }
    public static boolean active(ShipAPI ship, String skill) {
        return player(ship) && PeerlessSkills.isTrained(skill)
                && ship.getCaptain().getStats().getSkillLevel(skill) >= 2f;
    }
    public static float systemProtection(float age) {
        return age < 0f || age >= .5f ? 0f : age <= .25f ? .5f : .5f * (.5f - age) / .25f;
    }
    public static int missilesPerMinute(int maxAmmo) { return Math.max(1, (int)Math.floor(maxAmmo * .05)); }
    public static float rangeScale(float distance, float ceiling) {
        return Math.max(0f, Math.min(1f, (ceiling - distance) / (ceiling - 600f)));
    }
    public static float recoilRamp(float bonus, float amount, boolean firing) {
        return Math.max(0f, Math.min(1f, bonus + (firing ? amount / 5f : -amount / .5f)));
    }
    public static void updateCommanderCap() {
        if (Global.getSector()==null || Global.getSector().getPlayerStats()==null) return;
        com.fs.starfarer.api.characters.MutableCharacterStatsAPI commander=Global.getSector().getPlayerStats();
        if (PeerlessSkills.isTrained("gunnery_implants") && commander.getSkillLevel("gunnery_implants")>=2f)
            commander.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_MAX).modifyFlat(ID,5f);
        else commander.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_MAX).unmodify(ID);
    }
    public static void staticStats(MutableShipStatsAPI stats, boolean ordnance, boolean gunnery,
            com.fs.starfarer.api.characters.MutableCharacterStatsAPI commander) {
        int op = ordnance && stats.getVariant() != null ? stats.getVariant().computeHullModOPCost(commander) : 0;
        if (ordnance) {
            stats.getFluxDissipation().modifyFlat(ID, op * .75f);
            stats.getFluxCapacity().modifyFlat(ID, op * 10f);
        } else {
            stats.getFluxDissipation().unmodify(ID); stats.getFluxCapacity().unmodify(ID);
        }
        // The engine reads maximum ECM from fleet COMMANDER character stats, not ship stats.
    }

    public static final class Controller implements AdvanceableListener, DamageDealtModifier {
        private final ShipAPI ship;
        private boolean systemWasActive;
        private float systemAge = -1f, burstSpeed, firingBonus;
        private Float originalMass;
        private final Map<WeaponAPI, Float> reload = new IdentityHashMap<>();
        private final Set<WeaponAPI> emptied = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<Hit> hits = new ArrayList<>();
        private boolean applyingEmp;
        public Controller(ShipAPI ship) {
            this.ship = ship;
            // Entering combat with a system already active must not synthesize an activation.
            systemWasActive = ship.getSystem() != null && ship.getSystem().isActive();
        }
        boolean controls(ShipAPI candidate) { return ship == candidate; }
        boolean hasSpeedBurst() { return systemAge >= 0f && systemAge < 1f; }
        float speedBonus() { return hasSpeedBurst() ? burstSpeed * (1f - systemAge) : 0f; }
        float systemAge() { return systemAge; }
        float accuracyBonus() { return firingBonus; }
        boolean hasMassBonus() { return originalMass != null; }
        @Override public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;
            boolean alive = ship.isAlive();
            boolean eligible = player(ship) && alive;
            MutableShipStatsAPI s = ship.getMutableStats();
            boolean system = ship.getSystem() != null && ship.getSystem().isActive();
            if (system && !systemWasActive && eligible) {
                systemAge = 0f;
                // Snapshot unboosted top speed, including other skill/hullmod modifiers.
                // Remove our previous burst before calculating a refresh.
                s.getMaxSpeed().unmodify(ID);
                s.getMaxSpeed().unmodify("chief_navigator_peerless_helmsmanship");
                burstSpeed = Math.min(100f, Math.max(0f,s.getMaxSpeed().getModifiedValue()));
            } else if (systemAge >= 0f) systemAge += amount;
            systemWasActive = system;
            if (eligible && active(ship, "helmsmanship") && systemAge >= 0f && systemAge < 1f) {
                s.getMaxSpeed().modifyFlat(ID, burstSpeed * (1f - systemAge));
                // Let heavy hulls reach and shed the short-lived speed bonus.
                s.getAcceleration().modifyMult(ID, 2f); s.getDeceleration().modifyMult(ID, 2f);
                s.getTurnAcceleration().modifyMult(ID,2f); s.getMaxTurnRate().modifyMult(ID,2f);
            } else {
                s.getMaxSpeed().unmodify(ID); s.getAcceleration().unmodify(ID); s.getDeceleration().unmodify(ID);
                s.getTurnAcceleration().unmodify(ID); s.getMaxTurnRate().unmodify(ID);
            }
            s.getMaxSpeed().unmodify("chief_navigator_peerless_helmsmanship");
            float protection = eligible && active(ship, "systems_expertise") ? systemProtection(systemAge) : 0f;
            if (protection > 0f) s.getHullDamageTakenMult().modifyMult(ID, 1f - protection);
            else s.getHullDamageTakenMult().unmodify(ID);

            if (eligible && active(ship, "impact_mitigation")) {
                if (originalMass == null) { originalMass = ship.getMass(); ship.setMass(originalMass * 2f); }
            } else if (originalMass != null) { ship.setMass(originalMass); originalMass = null; }
            float repair = eligible && active(ship, "damage_control") ? Math.max(0f, ship.getCurrentCR()) : 0f;
            if (eligible && active(ship, "polarized_armor") && ship.getFluxTracker().isVenting()) repair += 1f;
            if (repair > 0f) {
                s.getCombatEngineRepairTimeMult().modifyMult(ID, 1f / (1f + repair));
                s.getCombatWeaponRepairTimeMult().modifyMult(ID, 1f / (1f + repair));
            } else { s.getCombatEngineRepairTimeMult().unmodify(ID); s.getCombatWeaponRepairTimeMult().unmodify(ID); }

            boolean firing = false;
            if (eligible) for (WeaponAPI w : ship.getAllWeapons()) {
                if (!w.isDecorative() && !w.isDisabled() && (w.isFiring() || w.isInBurst())) firing = true;
            }
            firingBonus = eligible && active(ship, "ballistic_mastery") ? recoilRamp(firingBonus, amount, firing) : 0f;
            if (firingBonus > 0f) {
                s.getAutofireAimAccuracy().modifyPercent(ID, firingBonus * 100f);
                s.getMaxRecoilMult().modifyMult(ID, 1f - firingBonus);
                s.getRecoilPerShotMult().modifyMult(ID, 1f - firingBonus);
            } else {
                s.getAutofireAimAccuracy().unmodify(ID); s.getMaxRecoilMult().unmodify(ID); s.getRecoilPerShotMult().unmodify(ID);
            }
            staticStats(s, eligible && active(ship, "ordnance_expert"), eligible && active(ship, "gunnery_implants"),
                    Global.getSector().getPlayerStats());
            if (eligible) {
                regenerate(amount);
            }
            if (eligible && !hits.isEmpty()) {
                List<Hit> pending = new ArrayList<>(hits); hits.clear();
                for (Hit h : pending) {
                    if (!h.target.isAlive()) continue;
                    if (h.decay > 0f) ArmorDecay.add(engine, ship, h.target, h.point, h.decay);
                    if (h.emp > 0f) {
                        applyingEmp = true;
                        try { engine.applyDamage(h.target, h.point, 0f, DamageType.ENERGY, h.emp, false, false, ship); }
                        finally { applyingEmp = false; }
                    }
                }
            } else hits.clear();
        }
        private void regenerate(float amount) {
            if (!active(ship, "missile_specialization")) return;
            for (WeaponAPI w : ship.getAllWeapons()) {
                if (w.getType() != WeaponAPI.WeaponType.MISSILE || w.getSize() != WeaponAPI.WeaponSize.LARGE
                        || !w.usesAmmo() || w.getMaxAmmo() <= 0 || w.getAmmoPerSecond() > 0f) continue;
                if (w.getAmmo() == 0) emptied.add(w);
                if (!emptied.contains(w)) continue;
                if (w.getAmmo() >= w.getMaxAmmo()) { reload.put(w, 0f); continue; }
                float progress = reload.getOrDefault(w, 0f) + amount * missilesPerMinute(w.getMaxAmmo()) / 60f;
                int gained = (int)Math.floor(progress);
                if (gained > 0) w.setAmmo(Math.min(w.getMaxAmmo(), w.getAmmo() + gained));
                reload.put(w, w.getAmmo() >= w.getMaxAmmo() ? 0f : progress - gained);
            }
        }
        public boolean tracksMissileReload(WeaponAPI weapon) {
            return weapon!=null && emptied.contains(weapon);
        }
        /** -1 selects the ordinary loading bar; otherwise this is fractional progress to the next missile. */
        public float missileReloadProgress(WeaponAPI weapon) {
            if (!ship.isAlive() || !active(ship,"missile_specialization") || !tracksMissileReload(weapon)
                    || weapon.getAmmo()>=weapon.getMaxAmmo() || weapon.getAmmoPerSecond()>0f) return -1f;
            return Math.max(0f,Math.min(1f,reload.getOrDefault(weapon,0f)));
        }
        @Override public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                Vector2f point, boolean shieldHit) {
            if (applyingEmp || !player(ship) || !(target instanceof ShipAPI) || point == null) return null;
            WeaponAPI weapon = weapon(param);
            if (weapon == null) return null;
            float raw = Math.max(0f, damage.getDamage() * damage.getMultiplier());
            float duration = damage.isDps() ? Math.max(0f, damage.getDpsDuration()) : 1f;
            boolean pd = weapon.hasAIHint(WeaponAPI.AIHints.PD) || weapon.hasAIHint(WeaponAPI.AIHints.PD_ONLY)
                    || weapon.hasAIHint(WeaponAPI.AIHints.PD_ALSO);
            float emp = !shieldHit && active(ship, "point_defense") && pd ? raw * .1f * duration : 0f;
            float decay = !shieldHit && active(ship, "target_analysis") ? Math.min(100f, raw * .1f) * duration : 0f;
            if (emp > 0f || decay > 0f) hits.add(new Hit((ShipAPI)target, new Vector2f(point), emp, decay));
            return null;
        }
    }
    private static WeaponAPI weapon(Object param) {
        if (param instanceof DamagingProjectileAPI) return ((DamagingProjectileAPI)param).getWeapon();
        if (param instanceof BeamAPI) return ((BeamAPI)param).getWeapon();
        return null;
    }
    private static final class Hit {
        final ShipAPI target; final Vector2f point; final float emp, decay;
        Hit(ShipAPI t, Vector2f p, float e, float d) { target=t; point=p; emp=e; decay=d; }
    }
    public static final class EnergyRange extends EnergyWeaponMastery.EWMDamageDealtMod {
        public EnergyRange(ShipAPI ship) { super(ship); }
        @Override public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                Vector2f point, boolean shieldHit) {
            String result = super.modifyDamageDealt(param, target, damage, point, shieldHit);
            WeaponAPI w = weapon(param);
            if (!active(ship, "energy_weapon_mastery") || w == null || w.getType() != WeaponAPI.WeaponType.ENERGY || point == null) return result;
            Vector2f origin = param instanceof DamagingProjectileAPI ? ((DamagingProjectileAPI)param).getSpawnLocation()
                    : param instanceof BeamAPI ? ((BeamAPI)param).getFrom() : ship.getLocation();
            float magnitude = Math.max(0f, ship.getFluxBasedEnergyWeaponDamageMultiplier() - 1f);
            damage.getModifier().modifyPercent("ewm_dam_mod", magnitude * rangeScale(Misc.getDistance(origin, point), 2000f) * 100f);
            return "ewm_dam_mod";
        }
    }
}
