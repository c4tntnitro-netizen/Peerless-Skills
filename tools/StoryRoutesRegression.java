package peerless.skills;

import org.json.*;
import java.nio.file.*;
import java.util.*;
import static peerless.skills.PeerlessFixture.*;

/** Checks installed optional mods through Starsector's own CSV parser. */
public final class StoryRoutesRegression {
    static Map<String,JSONObject> read(Path path) throws Exception {
        JSONArray rows=(JSONArray)Class.forName("com.fs.starfarer.loading.G").getMethod("o00000",String.class)
                .invoke(null,Files.readString(path));
        Map<String,JSONObject> result=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            check(result.put(row.getString("id"),row)==null,"Unique CSV id: "+path);
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path mods=Path.of("..");
        Path cn=mods.resolve("Chief Navigator/data/campaign/rules.csv");
        if(Files.exists(cn)) {
            var rows=read(cn);
            check(rows.keySet().stream().noneMatch(id->id.startsWith("chiefNavigatorSinniPeerless")),"Sinni training rules removed");
            String trial=Files.readString(mods.resolve("Chief Navigator/src/chiefnavigator/quest/MenelausTrial.java"));
            check(trial.contains("peerless.api.PeerlessSkills.awardOnce(DOMAIN_EPIC)"),"Final Labor reward preserved");
            check(trial.contains("memory().set(FINAL_DEBRIEF_COMPLETE, true);\n        receiveDomainEpic();")
                    || trial.contains("memory().set(FINAL_DEBRIEF_COMPLETE, true);\r\n        receiveDomainEpic();"),"Final debrief still grants its reward automatically");
        }
        Path hot=mods.resolve("HallOfTriumph/data/campaign/rules.csv");
        if(Files.exists(hot)) {
            var rows=read(hot);
            check(rows.keySet().stream().filter(id->id.startsWith("shipTrophyIsaPeerless")).toList().equals(List.of("shipTrophyIsaPeerlessAward")),"Isa training removed; only reward rule retained");
            check(rows.get("shipTrophyIsaPeerlessAward").getString("script").equals("PeerlessSkillsCMD award ship_trophy_room_peerless_isa_v1"),"Story award preserved");
            check(rows.get("shipTrophyIsaPeerlessAward").getString("conditions").contains("GanEdenQuestCMD questCompleted"),"HoT award remains gated by main quest completion");
        }
        Path sic=mods.resolve("SecondInCommand/src/second_in_command/ui/SCPlayerPanel.kt");
        if(Files.exists(sic)) {
            String source=Files.readString(sic);
            for(String id:peerless.api.SkillCatalog.ids())check(source.contains("skills.add(\""+id+"\")"),"SiC exposes "+id);
            check(source.contains("stats.setSkillLevel(active, 2f)"),"SiC selected skills meet native Elite prerequisite");
        }
        JSONObject settings=new JSONObject(Files.readString(Path.of("data/config/settings.json")));
        Class<?> plugin=Class.forName(settings.getJSONObject("plugins").getString("peerlessCombat"));
        check(com.fs.starfarer.api.combat.EveryFrameCombatPlugin.class.isAssignableFrom(plugin),"Runtime plugin registered");
        System.out.println("PASS: native story CSV loaders, shared contact routes, preserved rewards, all fourteen SiC player skills, runtime registration.");
    }
}
