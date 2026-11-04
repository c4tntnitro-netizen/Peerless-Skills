package peerless.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.*;
import java.util.*;

/** Player's own entry in the native Intel > Contacts list; never consumes a mission-contact slot. */
public final class PlayerContact extends BaseIntelPlugin {
    public static void install() {
        if (Global.getSector().getIntelManager().getIntel(PlayerContact.class).isEmpty())
            Global.getSector().getIntelManager().addIntel(new PlayerContact(), true);
    }
    @Override public String getName() { return Global.getSector().getPlayerPerson().getNameString()+" - Peerless training"; }
    @Override public String getIcon() { return Global.getSector().getPlayerPerson().getPortraitSprite(); }
    @Override public Set<String> getIntelTags(SectorMapAPI map) { return new HashSet<>(Arrays.asList(Tags.INTEL_CONTACTS)); }
    @Override public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) { info.addPara(getName(),0f); }
    @Override public void createSmallDescription(TooltipMakerAPI info,float width,float height) {
        info.addPara("Review your Peerless training. Unspent points: "+peerless.api.PeerlessSkills.getPoints()+".",0f);
        addGenericButton(info,width,"Peerless training", "train");
    }
    @Override public boolean doesButtonHaveConfirmDialog(Object id) { return false; }
    @Override public void buttonPressConfirmed(Object id,IntelUIAPI ui) {
        if ("train".equals(id)) ui.showDialog(Global.getSector().getPlayerFleet(),new TrainingDialog());
    }
}
