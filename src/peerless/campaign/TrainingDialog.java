package peerless.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.lwjgl.input.Keyboard;
import peerless.api.*;
import java.awt.Color;
import java.util.*;

/** Ordinary text/options dialog. No custom panels, reflection, or SiC UI hooks. */
public final class TrainingDialog implements InteractionDialogPlugin {
    private InteractionDialogAPI dialog;
    private String selected;
    private int page;
    public static void open(InteractionDialogAPI dialog) {
        TrainingDialog plugin = new TrainingDialog();
        dialog.setPlugin(plugin); plugin.init(dialog);
    }
    @Override public void init(InteractionDialogAPI dialog) {
        this.dialog=dialog;
        PeerlessSkills.refundInactiveTraining();
        if (Global.getSector() != null) dialog.getVisualPanel().showPersonInfo(Global.getSector().getPlayerPerson(), true);
        dialog.getTextPanel().addPara("Peerless training. Unspent points: " + PeerlessSkills.getPoints()
                + ". Each upgrade costs one Peerless point and requires its Elite skill. Reassigning an Elite skill refunds its Peerless point.");
        menu();
    }
    private boolean elite(String id) { return Global.getSector().getPlayerStats().getSkillLevel(id)>=2f; }
    private Color skillColor(String id) {
        var settings=Global.getSettings();
        var spec=settings==null?null:settings.getSkillSpec(id);
        Color color=spec==null?null:spec.getGoverningAptitudeColor();
        return color==null?Color.WHITE:color;
    }
    private void menu() {
        selected=null;
        OptionPanelAPI options=dialog.getOptionPanel(); options.clearOptions();
        List<String> ids=SkillCatalog.ids();
        for (int i=page*7; i<Math.min(ids.size(),page*7+7); i++) {
            String id=ids.get(i);
            String status=PeerlessSkills.isTrained(id) ? elite(id)?" [trained, active]":" [trained, inactive]"
                    : elite(id)?"":" [requires Elite]";
            options.addOption(SkillCatalog.NAMES.get(id)+status,"view:"+id,skillColor(id),null);
        }
        options.addOption(page==0?"Next skills":"Previous skills", "page");
        options.addOption("End training", "leave");
        options.setShortcut("leave",Keyboard.KEY_ESCAPE,false,false,false,true);
    }
    @Override public void optionSelected(String text, Object data) {
        if ("leave".equals(data)) { dialog.dismiss(); return; }
        if ("page".equals(data)) { page=1-page; menu(); return; }
        if ("back".equals(data)) { menu(); return; }
        if (data instanceof String && ((String)data).startsWith("view:")) {
            selected=((String)data).substring(5);
            if (!SkillCatalog.NAMES.containsKey(selected)) return;
            dialog.getTextPanel().addPara(SkillCatalog.NAMES.get(selected)+": "+SkillCatalog.EFFECTS.get(selected),skillColor(selected));
            dialog.getOptionPanel().clearOptions();
            if (PeerlessSkills.canTrain(selected)) {
                dialog.getOptionPanel().addOption("Train " + SkillCatalog.NAMES.get(selected) + " (1 Peerless point)","train",skillColor(selected),null);
            } else dialog.getTextPanel().addPara(PeerlessSkills.isTrained(selected)?"Already trained. The effect requires the Elite skill to be active."
                    : !elite(selected)?"Select or learn the Elite skill first.":"You need a Peerless point first.");
            dialog.getOptionPanel().addOption("Back to skills", "back");
            dialog.getOptionPanel().setShortcut("back",Keyboard.KEY_ESCAPE,false,false,false,true);
        } else if ("train".equals(data) && selected != null) {
            String result=PeerlessSkills.train(selected)?"Peerless training complete.":"Training requirements have changed. No point spent.";
            dialog.getTextPanel().addPara(result + " Unspent points: " + PeerlessSkills.getPoints() + ".");
            menu();
        }
    }
    @Override public void optionMousedOver(String text,Object data) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI result) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String,MemoryAPI> getMemoryMap() { return Collections.emptyMap(); }
}
