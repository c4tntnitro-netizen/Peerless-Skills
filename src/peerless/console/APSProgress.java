package peerless.console;

import com.fs.starfarer.api.Global;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import peerless.campaign.PeerlessProgression;

/** Reports the XP tracker without changing campaign state. */
public final class APSProgress implements BaseCommand {
    @Override public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        if (args != null && !args.trim().isEmpty()) return CommandResult.BAD_SYNTAX;
        if (Global.getSector() == null || Global.getSector().getPlayerStats() == null)
            return CommandResult.ERROR;
        Console.showMessage(PeerlessProgression.diagnostics());
        return CommandResult.SUCCESS;
    }
}
