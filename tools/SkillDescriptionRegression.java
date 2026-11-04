package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.loading.SkillSpec;
import org.json.*;
import java.nio.file.*;
import java.util.*;
import peerless.api.*;
import static peerless.skills.PeerlessFixture.*;

/** Real native SkillSpecs and SiC's real effects cache, including the callback used by both tooltips. */
public final class SkillDescriptionRegression {
    public static void main(String[] args) throws Exception {
        PeerlessFixture f=new PeerlessFixture();PeerlessRewardsRegression.load(f);
        PeerlessSkills.unlockAllForTesting();
        Map<String,SkillSpec> skills=new LinkedHashMap<>();Map<String,JSONObject> definitions=new HashMap<>();
        boolean[] sic={false};List<String> text=new ArrayList<>();
        Global.setSettings(mock(SettingsAPI.class,(n,a)->switch(n){
            case "getSkillSpec" -> skills.get(a[0]);
            case "getModManager" -> mock(ModManagerAPI.class,(nn,aa)->nn.equals("isModEnabled")?sic[0]:null);
            case "loadJSON" -> definitions.get(Path.of((String)a[0]).getFileName().toString().replace(".skill",""));
            case "getScriptClassLoader" -> SkillDescriptionRegression.class.getClassLoader();
            case "getColor" -> java.awt.Color.WHITE;
            case "getFloat" -> 1f;
            default -> null;
        }));
        TooltipMakerAPI tooltip=mock(TooltipMakerAPI.class,(n,a)->{if(n.equals("addPara"))text.add((String)a[0]);return null;});
        @SuppressWarnings("unchecked") Map<String,Class<?>> scripts=(Map<String,Class<?>>)Class.forName("com.fs.starfarer.loading.scripts.ScriptStore").getField("void").get(null);
        scripts.put(PeerlessDescription.class.getName(),PeerlessDescription.class);
        scripts.put(PeerlessOrdnanceExpertise.class.getName(),PeerlessOrdnanceExpertise.class);
        boolean vanillaOnly=args.length>0 && args[0].equals("vanilla-only");
        for(boolean withSiC:vanillaOnly?new boolean[]{false}:new boolean[]{false,true}) {
            sic[0]=withSiC;skills.clear();definitions.clear();
            for(String id:SkillCatalog.ids()) {
                Path path=Path.of("../../starsector-core/data/characters/skills/"+id+".skill");
                Path override=Path.of("../SecondInCommand/data/characters/skills/"+id+".skill");
                if(withSiC && Files.exists(override))path=override;
                JSONObject json=new JSONObject(Files.readString(path).replaceAll("(?m)#.*$",""));
                SkillSpec skill=new SkillSpec(json);skill.setName(SkillCatalog.NAMES.get(id));skills.put(id,skill);definitions.put(id,json);
            }
            Map<String,Integer> counts=new HashMap<>();
            for(var entry:skills.entrySet())counts.put(entry.getKey(),(int)entry.getValue().getEffectsAPI().stream()
                    .filter(effect->effect.getType()!=SkillEffectType.DESCRIPTION).count());
            NativeSkillDescriptions.install();NativeSkillDescriptions.install();
            for(String id:SkillCatalog.ids()) {
                SkillSpec skill=skills.get(id);
                check(skill.getEffectsAPI().size()==counts.get(id)+1,"Exactly one native description appended: "+id);
                check(skill.getEffectsAPI().stream().noneMatch(e->e.getType()==SkillEffectType.DESCRIPTION),"Grey footnotes removed: "+id);
                var effect=skill.getEffectsAPI().get(skill.getEffectsAPI().size()-1);
                check(effect.getRequiredSkillLevel()==0f && !effect.isLevelBased(),"Description entry is unconditional; gameplay checks player/Elite gates");
                check(effect.getAsShipEffect() instanceof PeerlessDescription,"Native script lookup resolves description class");
                text.clear();((PeerlessDescription)effect.getAsShipEffect()).createCustomDescription(f.stats,skill,tooltip,700f);
                check(text.equals(List.of("Peerless",SkillCatalog.EFFECTS.get(id))),"Player tooltip has correct description: "+id);
                f.levels.put(id,0f);text.clear();new PeerlessDescription().createCustomDescription(f.stats,skill,tooltip,700f);
                check(text.size()==2 && text.get(0).equals("Peerless (inactive)"),"Deselected purchase visibly inactive without a grey caveat: "+id);f.levels.remove(id);
                text.clear();new PeerlessDescription().createCustomDescription(mock(MutableCharacterStatsAPI.class,(n,a)->null),skill,tooltip,700f);
                check(text.isEmpty(),"Officers never show player upgrades");
                if(withSiC) {
                    var groups=second_in_command.misc.VanillaSkillsUtil.getLevelEffects(id);
                    long descriptions=groups.stream().flatMap(Collection::stream).filter(x->x instanceof PeerlessDescription).count();
                    check(descriptions==1,"SiC cached tooltip has exactly one description: "+id);
                }
            }
        }
        f.saved.clear();text.clear();new PeerlessDescription().createCustomDescription(f.stats,skills.get("helmsmanship"),tooltip,700f);
        check(text.isEmpty(),"Untrained skills do not claim a Peerless effect");
        System.out.println(vanillaOnly ? "PASS: native descriptions work with SiC absent from the runtime classpath."
                : "PASS: all fourteen descriptions, original gameplay effects, grey footnote removal, inactive headings, idempotent reload, player/Elite gates, and real SiC tooltip cache.");
    }
}
