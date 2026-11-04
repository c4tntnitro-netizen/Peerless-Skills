package peerless.api;

import java.util.*;

/** Player-facing skill and training text. Describe combat benefits here;
 * keep visual effects, implementation details and development notes in the docs. */
public final class SkillCatalog {
    private SkillCatalog() {}
    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    public static final Map<String, String> EFFECTS = new LinkedHashMap<>();
    static {
        add("helmsmanship", "Helmsmanship", "Activating your ship system grants +100% maneuverability for 1 second. Gain +100% top speed, capped at +100 su/second, fading to zero over that second.");
        add("combat_endurance", "Combat Endurance", "Once per battle, prevents your hull from dropping below 10% for 5 seconds.");
        add("impact_mitigation", "Impact Mitigation", "+100% ship mass.");
        add("damage_control", "Damage Control", "Weapon and engine repair speed increases by your current CR.");
        add("field_modulation", "Field Modulation", "Once per battle, your first overload becomes a forced vent.");
        add("point_defense", "Point Defense", "Point-defense weapons deal an additional 10% damage as EMP.");
        add("target_analysis", "Target Analysis", "Unshielded weapon hits deal 10% additional armor damage over 5 seconds, up to 100 damage per hit.");
        add("ballistic_mastery", "Ballistic Mastery", "weapons grow more accurate when firing");
        add("systems_expertise", "Systems Expertise", "Activating your ship system reduces hull damage taken by 50% for 0.25 seconds, decaying to zero at 0.5 seconds.");
        add("missile_specialization", "Missile Specialization", "Large missiles slowly regenerate ammo after depletion");
        add("gunnery_implants", "Gunnery Implants", "+5 percentage points to your fleet's maximum ECM rating.");
        add("energy_weapon_mastery", "Energy Weapon Mastery", "Extends the energy weapon damage bonus to 2000 range.");
        add("ordnance_expert", "Ordnance Expertise", "+0.75 flux dissipation and +10 flux capacity per OP spent on hullmods.");
        add("polarized_armor", "Polarized Armor", "+100% engine and weapon repair speed while venting.");
    }
    private static void add(String id, String name, String effect) { NAMES.put(id, name); EFFECTS.put(id, effect); }
    public static java.util.List<String> ids() { return Collections.unmodifiableList(new ArrayList<>(NAMES.keySet())); }
}
