package peerless;

import com.fs.starfarer.api.BaseModPlugin;
import peerless.skills.PeerlessSkillIcon;

public final class PeerlessModPlugin extends BaseModPlugin {
    @Override public void onApplicationLoad() {
        // Register before the engine builds its skill-effect dispatch caches.
        peerless.skills.NativeSkillDescriptions.install();
    }
    @Override public void onGameLoad(boolean newGame) {
        peerless.skills.NativeSkillDescriptions.install();
        PeerlessSkillIcon.install();
        peerless.campaign.PlayerContact.install();
        com.fs.starfarer.api.Global.getSector().removeTransientScriptsOfClass(peerless.campaign.CampaignEffects.class);
        com.fs.starfarer.api.Global.getSector().addTransientScript(new peerless.campaign.CampaignEffects());
    }
}
