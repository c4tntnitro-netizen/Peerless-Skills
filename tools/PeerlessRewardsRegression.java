package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.campaign.rules.Memory;
import peerless.api.PeerlessSkills;
import java.util.HashMap;
import static peerless.skills.PeerlessFixture.*;

public final class PeerlessRewardsRegression {
    static final String ISA = "$ship_trophy_room_peerless_isa_v1";
    static final String SINNI = "$chief_navigator_domain_epic_v1";
    static void load(PeerlessFixture f) {
        Global.setSector(mock(SectorAPI.class,(n,a)->switch(n) {
            case "getMemoryWithoutUpdate" -> f.memory;
            case "getPlayerStats" -> f.stats;
            case "getPlayerPerson" -> f.player;
            default -> null;
        }));
    }
    public static void main(String[] args) {
        nativeReassignment();
        for (String first : new String[]{ISA, SINNI}) {
            String second = first.equals(ISA) ? SINNI : ISA;
            PeerlessFixture f = new PeerlessFixture();
            load(f);
            check(!PeerlessSkills.isUnlocked() && PeerlessSkills.getPoints()==0, "Fresh campaign");
            check(PeerlessSkills.awardOnce(first) && !PeerlessSkills.awardOnce(first), "First story once");
            check(PeerlessSkills.awardOnce(second) && PeerlessSkills.getPoints()==2, "Both points bank");
            f.skill=1;
            check(!PeerlessSkills.train("helmsmanship") && PeerlessSkills.getPoints()==2, "Elite required");
            f.skill=2;
            check(PeerlessSkills.train("helmsmanship") && PeerlessSkills.getPoints()==1, "Spend only one");
            check(!PeerlessSkills.train("helmsmanship") && !PeerlessSkills.train("unsupported"), "No duplicate or unsupported training");
            PeerlessFixture reloaded=new PeerlessFixture(); reloaded.saved.putAll(new HashMap<>(f.saved)); load(reloaded);
            check(!PeerlessSkills.awardOnce(first) && !PeerlessSkills.awardOnce(second)
                    && PeerlessSkills.isTrained("helmsmanship") && PeerlessSkills.getPoints()==1, "Durable source receipts and balance");
        }
        for (boolean spent : new boolean[]{false,true}) {
            PeerlessFixture f = new PeerlessFixture(); f.saved.put(SINNI,true);
            if(spent) f.saved.put(PeerlessSkills.TRAINED,"helmsmanship");
            load(f);
            check(!PeerlessSkills.awardOnce(SINNI) && PeerlessSkills.getPoints()==(spent?0:1), "Original campaign keeps its reward");
            check(PeerlessSkills.awardOnce(ISA) && PeerlessSkills.getPoints()==(spent?1:2), "Isa still pays independently");
        }
        Global.setSector(null);
        check(!PeerlessSkills.awardOnce(ISA) && PeerlessSkills.getPoints()==0, "No campaign");
        PeerlessFixture f=new PeerlessFixture();load(f);
        PeerlessSkills.awardOnce(ISA);PeerlessSkills.awardOnce(SINNI);
        PeerlessSkills.train("helmsmanship");PeerlessSkills.train("impact_mitigation");
        f.levels.put("helmsmanship",0f);
        check(PeerlessSkills.refundInactiveTraining()==1 && PeerlessSkills.getPoints()==1,"Removing Elite refunds its paid training");
        check(!PeerlessSkills.isTrained("helmsmanship") && PeerlessSkills.isTrained("impact_mitigation"),"Only reassigned skill loses training");
        check(PeerlessSkills.refundInactiveTraining()==0,"Refund is not repeated");
        f.levels.put("helmsmanship",2f);
        check(!PeerlessSkills.isTrained("helmsmanship") && PeerlessSkills.train("helmsmanship"),"Refunded point can be allocated again");
        PeerlessSkills.unlockAllForTesting();f.skill=0;f.levels.clear();
        check(PeerlessSkills.refundInactiveTraining()==14 && PeerlessSkills.getPoints()==14,"Every removed Peerless upgrade refunds a point, including console unlocks");
        check(PeerlessSkills.getSupportedSkillIds().stream().noneMatch(PeerlessSkills::isTrained),"Full reassignment removes all upgrades");
        check(PeerlessSkills.refundInactiveTraining()==0 && PeerlessSkills.getPoints()==14,"Repeated refresh cannot refund console upgrades twice");
        System.out.println("PASS: story rewards, durable receipts, reload, legacy campaigns, paid/test-unlock refunds and retraining.");
    }

    static void nativeReassignment() {
        PeerlessFixture f=new PeerlessFixture();Memory memory=new Memory();
        Global.setSector(mock(SectorAPI.class,(n,a)->switch(n) {
            case "getMemoryWithoutUpdate" -> memory;
            case "getPlayerStats" -> f.stats;
            case "getPlayerPerson" -> f.player;
            default -> null;
        }));
        check(!PeerlessSkills.isTrained("helmsmanship"),"Native memory: fresh campaign has no training");
        memory.set(PeerlessSkills.TRAINED,null);
        boolean nativeNullFailure=false;
        try { memory.getString(PeerlessSkills.TRAINED); }
        catch(NullPointerException expected){nativeNullFailure=true;}
        check(nativeNullFailure,"Reproduce the native null-string exception from the crash log");
        check(!PeerlessSkills.isTrained("helmsmanship"),"Previously cleared/null training reads safely");
        SkillSpecAPI skill=mock(SkillSpecAPI.class,(n,a)->n.equals("getId")?"helmsmanship":null);
        TooltipMakerAPI tooltip=mock(TooltipMakerAPI.class,(n,a)->{
            throw new AssertionError("Untrained skill must not draw a Peerless description");
        });
        new PeerlessDescription().createCustomDescription(f.stats,skill,tooltip,500f);
        check(PeerlessSkills.awardOnce(ISA)&&PeerlessSkills.train("helmsmanship"),"Native memory: train after old null state");
        // Skill-screen previews still retain ownership; refund is explicitly after confirmation/closing.
        f.levels.put("helmsmanship",0f);
        check(PeerlessSkills.isTrained("helmsmanship"),"Lowering skill during reassignment preserves training until refund");
        f.levels.put("helmsmanship",1f);f.levels.put("helmsmanship",2f);
        check(PeerlessSkills.isTrained("helmsmanship")&&PeerlessSkills.refundInactiveTraining()==0,"Restoring Elite keeps Peerless without a new charge");
        f.levels.put("helmsmanship",0f);
        check(PeerlessSkills.refundInactiveTraining()==1&&PeerlessSkills.getPoints()==1,"Confirmed reassignment refunds once with native memory");
        check(!memory.contains(PeerlessSkills.TRAINED)&&!PeerlessSkills.isTrained("helmsmanship"),"Refund removes the key instead of storing null");
        new PeerlessDescription().createCustomDescription(f.stats,skill,tooltip,500f);
        check(PeerlessSkills.refundInactiveTraining()==0&&PeerlessSkills.getPoints()==1,"Repeated refresh cannot duplicate refunds");
        f.levels.put("helmsmanship",2f);
        check(PeerlessSkills.train("helmsmanship")&&PeerlessSkills.getPoints()==0,"Refunded native-memory point can train again");
        PeerlessSkills.unlockAllForTesting();
        f.levels.clear();f.skill=0f;
        check(PeerlessSkills.refundInactiveTraining()==14&&PeerlessSkills.getPoints()==14,"Reported save case: one paid and thirteen console-granted upgrades all refund");
        check(PeerlessSkills.refundInactiveTraining()==0&&PeerlessSkills.getPoints()==14,"Native memory: repeated refresh keeps exactly fourteen points");
        check(PeerlessSkills.getSupportedSkillIds().stream().noneMatch(PeerlessSkills::isTrained),"Native memory: all refunded training cleared");
        f.skill=2f;
        check(PeerlessSkills.train("combat_endurance")&&PeerlessSkills.getPoints()==13,"A refunded test point can train a different skill");
        check(!PeerlessSkills.awardOnce(ISA),"Refunding preserves story reward receipts");
        System.out.println("PASS: real native Memory reproduces old crash; safe descriptions, fourteen-point mixed refunds, retraining and preview restoration.");
    }
}
