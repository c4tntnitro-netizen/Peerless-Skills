package peerless.skills;

import peerless.api.SkillCatalog;
import second_in_command.misc.VanillaSkillsUtil;
import java.util.*;

/** Optional display adapter, loaded only when SiC is enabled. Its tooltip uses this effects cache
 * instead of the native SkillSpec list. Appending a display-only group preserves all SiC effects. */
final class SiCSkillDescriptions {
    static void install() {
        for (String id : SkillCatalog.ids()) {
            List<List<Object>> groups=VanillaSkillsUtil.getLevelEffects(id);
            for(List<Object> group:groups)group.removeIf(effect->effect instanceof com.fs.starfarer.api.characters.DescriptionSkillEffect);
            boolean present=false;
            for (List<Object> group : groups) for (Object effect : group)
                if (effect instanceof PeerlessDescription) present=true;
            if (!present) groups.add(new ArrayList<>(Collections.singletonList(new PeerlessDescription())));
        }
    }
}
