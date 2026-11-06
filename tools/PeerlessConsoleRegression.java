package peerless.skills;

import peerless.console.AddPeerlessPoint;
import peerless.api.PeerlessSkills;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ModManagerAPI;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;
import java.util.HashMap;
import java.nio.file.*;
import static peerless.skills.PeerlessFixture.*;

/** Invokes the actual console command, including native console output. */
public final class PeerlessConsoleRegression {
    public static void main(String[] args) throws Exception {
        PeerlessFixture f = new PeerlessFixture();
        Global.setSector(mock(SectorAPI.class,(n,a)->n.equals("getMemoryWithoutUpdate")?f.memory:n.equals("getPlayerPerson")?f.player:n.equals("getPlayerStats")?f.stats:null));
        f.saved.clear(); f.skill = 1f;
        Global.setSettings(mock(SettingsAPI.class, (n,a) -> n.equals("getModManager")
                ? mock(ModManagerAPI.class, (n2,a2) -> null) : null));
        AddPeerlessPoint command = new AddPeerlessPoint();
        check(command.runCommand("", CommandContext.COMBAT_MISSION) == CommandResult.WRONG_CONTEXT,
                "Campaign only");
        check(command.runCommand("officer", CommandContext.CAMPAIGN_MAP) == CommandResult.BAD_SYNTAX,
                "Reject unsupported target");
        check(f.saved.isEmpty(), "Invalid calls do not mutate state");
        check(command.runCommand("", CommandContext.CAMPAIGN_MAP) == CommandResult.SUCCESS,
                "Grant before completing any Labors");
        check(PeerlessSkills.getPoints() == 1 && !f.saved.containsKey("$chief_navigator_domain_epic_v1")
                && !PeerlessSkills.isTrained("helmsmanship"), "One unspent point; no quest advancement");
        command.runCommand("helmsmanship", CommandContext.CAMPAIGN_MAP);
        check(!PeerlessSkills.isTrained("helmsmanship") && f.skill == 1f, "Elite still required; point retained");
        f.skill = 2f;
        command.runCommand("HELMSMANSHIP", CommandContext.CAMPAIGN_MAP);
        check(PeerlessSkills.isTrained("helmsmanship") && f.skill == 2f, "Spend without altering native skill level");
        var snapshot = new HashMap<>(f.saved);
        command.runCommand("", CommandContext.CAMPAIGN_MAP);
        command.runCommand("helmsmanship", CommandContext.CAMPAIGN_MAP);
        check(f.saved.equals(snapshot), "No repeat grants or replacement");
        peerless.console.APSunlock unlock = new peerless.console.APSunlock();
        f.saved.clear(); f.skill = 0f;
        check(unlock.runCommand("", CommandContext.COMBAT_MISSION) == CommandResult.WRONG_CONTEXT,
                "Unlock campaign only");
        check(unlock.runCommand("extra", CommandContext.CAMPAIGN_MAP) == CommandResult.BAD_SYNTAX,
                "Unlock rejects arguments");
        check(f.saved.isEmpty() && f.skill == 0f, "Invalid unlock does not mutate state");
        PeerlessSkills.awardOnce("$test_reward");
        check(unlock.runCommand("", CommandContext.CAMPAIGN_MAP) == CommandResult.SUCCESS,
                "Unlock succeeds without learned prerequisites");
        for (String id : PeerlessSkills.getSupportedSkillIds()) {
            check(PeerlessSkills.isTrained(id) && f.skill >= 2f, "All supported skills trained and Elite");
        }
        check(PeerlessSkills.getPoints() == 1 && !f.saved.containsKey("$chief_navigator_domain_epic_v1"),
                "Unlock preserves reward balance and quest state");
        snapshot = new HashMap<>(f.saved);
        unlock.runCommand("", CommandContext.CAMPAIGN_MAP);
        check(f.saved.equals(snapshot), "Repeated unlock is idempotent");
        Global.setSector(null);
        check(unlock.runCommand("", CommandContext.CAMPAIGN_MAP) == CommandResult.ERROR,
                "Unlock requires campaign");
        check(command.runCommand("", CommandContext.CAMPAIGN_MAP) == CommandResult.ERROR,
                "Missing campaign rejected");
        Path root = Files.isDirectory(Path.of("data")) ? Path.of("") : Path.of("Peerless Skills");
        check(Files.readString(root.resolve("data/console/commands.csv"))
                .contains("AddPeerlessPoint,peerless.console.AddPeerlessPoint,"), "Registered command");
        check(Files.readString(root.resolve("data/console/commands.csv"))
                .contains("APSunlock,peerless.console.APSunlock,"), "Registered unlock command");
        check(Files.readString(root.resolve("data/console/commands.csv"))
                .contains("APSProgress,peerless.console.APSProgress,"), "Registered progress command");
        System.out.println("PASS: actual console grant/spend, prerequisites, context, syntax, one-point cap and quest-state preservation.");
    }
}
