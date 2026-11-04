package peerless.console;

import com.fs.starfarer.api.Global;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import peerless.api.PeerlessSkills;

public final class APSunlock implements BaseCommand {
    @Override public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        if (args != null && !args.trim().isEmpty()) return CommandResult.BAD_SYNTAX;
        if (Global.getSector() == null || Global.getSector().getPlayerPerson() == null
                || Global.getSector().getPlayerStats() == null) return CommandResult.ERROR;
        int count = PeerlessSkills.unlockAllForTesting();
        Console.showMessage("Unlocked all " + count + " implemented Peerless skills for the player, including Elite prerequisites.");
        return CommandResult.SUCCESS;
    }
}
