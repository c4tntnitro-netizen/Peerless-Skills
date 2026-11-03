package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.util.Misc;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import peerless.api.PeerlessSkills;
import peerless.rules.PeerlessSkillsCMD;
import shiptrophy.GanEdenQuestCMD;
import static peerless.skills.PeerlessFixture.*;

/** Optional integration check. Requires Hall of Triumph only on the test classpath. */
public final class HallOfTriumphRegression {
    public static void main(String[] args) throws Exception {
        String csv=Files.readString(Path.of(args[0]));
        JSONArray parsed=(JSONArray)Class.forName("com.fs.starfarer.loading.G")
                .getMethod("o00000",String.class).invoke(null,csv);
        Map<String,JSONObject> rows=new HashMap<>();
        for(int i=0;i<parsed.length();i++) {
            JSONObject row=parsed.getJSONObject(i);
            check(rows.put(row.getString("id"),row)==null,"Unique rule");
        }
        JSONObject finale=rows.get("shipTrophyGanEdenEpitaphFinish"), award=rows.get("shipTrophyIsaPeerlessAward");
        check(rows.get("shipTrophyGanEdenEpitaphTalk").getString("options").contains("ship_trophy_gan_eden_epitaph_finish:"),"Final Isa conversation enters finish handler");
        check(finale.getString("script").lines().toList().equals(List.of("GanEdenQuestCMD markEpitaph","FireAll ShipTrophyIsaPeerlessAward","DismissDialog")),"Commit first, check completion, then dismiss");
        check(rows.get("shipTrophyGanEdenEpitaphLeave").getString("script").equals("DismissDialog"),"Other elevator exits cannot complete the story");
        check(award.getString("conditions").lines().toList().equals(List.of("GanEdenQuestCMD questCompleted","!$global.ship_trophy_room_peerless_isa_v1")),"Completed story and unclaimed source");
        PeerlessFixture f=new PeerlessFixture();
        PeerlessRewardsRegression.load(f);
        Global.setSettings(mock(com.fs.starfarer.api.SettingsAPI.class,(n,a)->n.equals("getColor")?java.awt.Color.WHITE:null));
        InteractionDialogAPI dialog=mock(InteractionDialogAPI.class,(n,a)->null);
        GanEdenQuestCMD quest=new GanEdenQuestCMD();
        PeerlessSkillsCMD cmd=new PeerlessSkillsCMD();
        String awardArgs=award.getString("script").substring("PeerlessSkillsCMD ".length());
        f.saved.put("$shipTrophyGanEdenQuestStage","SPACE_ELEVATOR");
        check(!quest.execute("test",dialog,Misc.tokenize("questCompleted"),Map.of()),"No point for coming to the elevator");
        f.saved.put("$shipTrophyGanEdenQuestStage","COMPLETED");
        check(quest.execute("test",dialog,Misc.tokenize("questCompleted"),Map.of()),"Installed HoT recognizes completed stage");
        check(cmd.execute("award",dialog,Misc.tokenize(awardArgs),Map.of()) && PeerlessSkills.getPoints()==1,"Actual authored Isa reward pays one");
        check(!cmd.execute("award",dialog,Misc.tokenize(awardArgs),Map.of()),"No repeat payment");
        String action=rows.get("shipTrophyIsaPeerlessTrain").getString("script").lines().findFirst().orElseThrow().substring("PeerlessSkillsCMD ".length());
        f.skill=1; check(!cmd.execute("train",dialog,Misc.tokenize(action),Map.of()) && PeerlessSkills.getPoints()==1,"Isa respects Elite prerequisite");
        f.skill=2; check(cmd.execute("train",dialog,Misc.tokenize(action),Map.of()) && PeerlessSkills.getPoints()==0,"Isa installs the core effect without Sinni");
        check(!cmd.execute("train",dialog,Misc.tokenize(action),Map.of()),"Stale install cannot spend twice");
        check(PeerlessSkills.awardOnce("$chief_navigator_domain_epic_v1") && PeerlessSkills.getPoints()==1,"Second story still pays after training");
        System.out.println("PASS: native Hall CSV, finale vs ordinary exit, installed quest completion gate, repeat claims, Isa training and independent second reward.");
    }
}
