package peerless;

import com.fs.starfarer.api.BaseModPlugin;
import peerless.skills.PeerlessSkillIcon;

public final class PeerlessModPlugin extends BaseModPlugin {
    @Override public void onGameLoad(boolean newGame) {
        PeerlessSkillIcon.install();
    }
}
