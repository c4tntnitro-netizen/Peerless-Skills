package peerless.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import peerless.api.PeerlessSkills;

public final class AddPeerlessPoint implements BaseCommand {
    @Override public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String choice = args == null ? "" : args.trim();
        if (!choice.isEmpty() && !Skills.HELMSMANSHIP.equalsIgnoreCase(choice)) return CommandResult.BAD_SYNTAX;
        if (Global.getSector() == null || Global.getSector().getPlayerPerson() == null) return CommandResult.ERROR;
        boolean granted = PeerlessSkills.awardOnce("$peerless_skills_console_point_v1");
        if (!choice.isEmpty() && PeerlessSkills.train(Skills.HELMSMANSHIP)) {
            Console.showMessage("Peerless Helmsmanship trained: ship-system speed burst.");
        } else if (!choice.isEmpty()) {
            Console.showMessage(PeerlessSkills.isTrained(Skills.HELMSMANSHIP)
                    ? "Helmsmanship is already Peerless." : "Learn Elite Helmsmanship first.");
        } else {
            Console.showMessage(granted ? "Granted one Peerless point." : "The console test point was already granted.");
        }
        Console.showMessage("Unspent Peerless points: " + PeerlessSkills.getPoints());
        return CommandResult.SUCCESS;
    }
}
