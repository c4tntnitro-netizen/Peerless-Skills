package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
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
            check(!PeerlessSkills.train("helmsmanship") && !PeerlessSkills.train("combat_endurance"), "No duplicate or unsupported training");
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
        System.out.println("PASS: both story orders, two banked points, one spend, repeat claims, reload and existing campaigns.");
    }
}
