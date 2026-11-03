package peerless.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import java.util.List;
import java.util.Map;
import peerless.api.PeerlessSkills;

/** Rules adapter for story mods without a Java integration. */
public final class PeerlessSkillsCMD extends BaseCommandPlugin {
    @Override public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> args,
            Map<String, MemoryAPI> memories) {
        if (args.isEmpty()) return false;
        String action = args.get(0).getString(memories);
        switch (action) {
            case "hasPoint": return PeerlessSkills.getPoints() > 0;
            case "unlocked": return PeerlessSkills.isUnlocked();
            case "trained": return PeerlessSkills.isTrained(Skills.HELMSMANSHIP);
            case "canTrain": return PeerlessSkills.canTrain(Skills.HELMSMANSHIP);
            case "train": return dialog != null && PeerlessSkills.train(Skills.HELMSMANSHIP);
            case "award":
                // Rules resolve $variable tokens; accept the bare key and add its sigil here.
                return dialog != null && args.size() == 2
                        && PeerlessSkills.awardOnce("$" + args.get(1).getString(memories));
            default: return false;
        }
    }
}
