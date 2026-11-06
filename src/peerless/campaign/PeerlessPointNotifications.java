package peerless.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.Color;
import peerless.api.PeerlessSkills;

/** Native campaign notices; their temporary interaction target opens the shared training menu. */
public final class PeerlessPointNotifications extends BaseCampaignPlugin {
    public static final String PENDING = "$peerless_skills_pending_point_notices_v1";
    private static final String TARGET = "$peerless_skills_training_notice_target_v1";
    private static final String ID = "peerless_point_notifications";

    public static void install() {
        Global.getSector().unregisterPlugin(ID);
        Global.getSector().registerPlugin(new PeerlessPointNotifications());
    }
    @Override public String getId() { return ID; }
    @Override public boolean isTransient() { return true; }
    @Override public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken target) {
        return target != null && target.getMemoryWithoutUpdate().getBoolean(TARGET)
                ? new PluginPick<>(new TrainingDialog(), PickPriority.MOD_SPECIFIC) : null;
    }
    @Override public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(Object param, SectorEntityToken target) {
        return pickInteractionDialogPlugin(target);
    }
    private static long pending() {
        Object value = Global.getSector().getMemoryWithoutUpdate().get(PENDING);
        return value instanceof Number ? Math.max(0L, ((Number)value).longValue()) : 0L;
    }
    public static void queueAward() {
        Global.getSector().getMemoryWithoutUpdate().set(PENDING, pending() + 1L);
    }
    public static void showPending() {
        var sector = Global.getSector();
        if (sector == null || pending() == 0) return;
        var ui = sector.getCampaignUI();
        if (ui == null || ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null
                || sector.getCurrentLocation() == null) return;
        // createToken does not add an entity to the location. Only this message holds it.
        var target = sector.getCurrentLocation().createToken(0f, 0f);
        target.getMemoryWithoutUpdate().set(TARGET, true);
        ui.addMessage(new Notice(PeerlessSkills.getPoints()), MessageClickAction.INTERACTION_DIALOG, target);
        sector.getMemoryWithoutUpdate().set(PENDING, pending() - 1L);
    }

    public static final class Notice extends BaseIntelPlugin {
        private final int points;
        private static final Color GOLD = new Color(255, 215, 70);
        public Notice(int points) { this.points = points; }
        @Override public String getName() { return "Peerless point gained"; }
        @Override public String getIcon() { return "graphics/icons/skills/peerless_helmsmanship_gold_v1.png"; }
        @Override public String getCommMessageSound() { return getSoundMinorMessage(); }
        @Override public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), GOLD, 0f);
            info.addPara("You have " + points + " Peerless " + (points == 1 ? "point" : "points") + " to spend.", 3f);
            info.addPara("Click to open Peerless training.", 3f);
        }
    }
}
