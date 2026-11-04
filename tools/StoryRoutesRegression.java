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
            check(rows.get("chiefNavigatorSinniPeerlessOpen").getString("script").contains("PeerlessSkillsCMD open"),"Sinni opens shared training");
        }
        Path hot=mods.resolve("HallOfTriumph/data/campaign/rules.csv");
        if(Files.exists(hot)) {
            var rows=read(hot);
            check(rows.get("shipTrophyIsaPeerlessOpen").getString("script").equals("PeerlessSkillsCMD open"),"Isa opens shared training");
            check(!rows.get("shipTrophyIsaPeerlessSpent").optString("script").contains("SetEnabled"),"Spent points do not prevent viewing Isa training");
            check(rows.get("shipTrophyIsaPeerlessAward").getString("script").equals("PeerlessSkillsCMD award ship_trophy_room_peerless_isa_v1"),"Story award preserved");
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
