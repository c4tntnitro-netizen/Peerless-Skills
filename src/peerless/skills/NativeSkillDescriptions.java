package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.loading.SkillSpec;
import org.json.JSONObject;
import peerless.api.SkillCatalog;

/** Narrow native-data adapter: the public effects accessor returns a copy, so append through
 * the native skill model. No reflection, UI traversal or skill-file replacement.
 * This is isolated here to make checking a future Starsector update straightforward. */
public final class NativeSkillDescriptions {
    private NativeSkillDescriptions() {}
    public static void install() {
        for (String id : SkillCatalog.ids()) {
            SkillSpecAPI skill=Global.getSettings().getSkillSpec(id);
            if (skill instanceof SkillSpec) append((SkillSpec)skill);
        }
        if (Global.getSettings().getModManager().isModEnabled("second_in_command"))
            SiCSkillDescriptions.install();
    }
    public static void append(SkillSpec skill) {
        // Native DESCRIPTION entries here are the grey explanatory footnotes, not gameplay.
        skill.getEffects().removeIf(effect->effect.getType()==com.fs.starfarer.api.characters.SkillEffectType.DESCRIPTION);
        String script="ordnance_expert".equals(skill.getId())
                ? PeerlessOrdnanceExpertise.class.getName() : PeerlessDescription.class.getName();
        for (SkillSpecAPI.SkillEffectSpecAPI effect : skill.getEffectsAPI())
            if (script.equals(effect.getEffectClass())) return;
        try {
            SkillSpec.SkillEffectSpec effect=new SkillSpec.SkillEffectSpec(new JSONObject()
                    .put("type","SHIP").put("script",script));
            effect.setGoverningSkill(skill.getId());
            effect.setRequiredSkillLevel(0f);
            effect.setLevelBased(false);
            // Continue the existing Elite group; the custom description supplies its own heading.
            effect.setName("Elite");
            skill.getEffects().add(effect);
        } catch (org.json.JSONException ex) { throw new IllegalStateException("Unable to add Peerless description",ex); }
    }
}
