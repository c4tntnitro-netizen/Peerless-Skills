package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.*;
import java.util.*;
import peerless.api.PeerlessSkills;
import peerless.campaign.PlayerContact;
import peerless.campaign.TrainingDialog;
import static peerless.skills.PeerlessFixture.*;

/** Player contact registration -> native Intel button -> real training text/options menu. */
public final class PlayerContactRegression {
    public static void main(String[] args) {
        PeerlessFixture f=new PeerlessFixture();
        List<IntelInfoPlugin> contacts=new ArrayList<>();
        Map<Object,String> options=new LinkedHashMap<>();
        Set<Object> buttons=new HashSet<>();
        List<String> transcript=new ArrayList<>();
        int[] cues={0};
        Global.setSoundPlayer(mock(SoundPlayerAPI.class,(n,a)->{
            if(n.equals("playUISound")){check(a[0].equals("ui_char_level_up"),"Use the vanilla character level-up cue");cues[0]++;}
            return null;
        }));
        PersonAPI player=mock(PersonAPI.class,(n,a)->switch(n) {
            case "getStats" -> f.stats;
            case "getNameString" -> "Test Captain";
            case "getPortraitSprite" -> "graphics/portraits/portrait1.png";
            default -> null;
        });
        CampaignFleetAPI fleet=mock(CampaignFleetAPI.class,(n,a)->null);
        IntelManagerAPI manager=mock(IntelManagerAPI.class,(n,a)->switch(n) {
            case "getIntel" -> contacts.stream().filter(((Class<?>)a[0])::isInstance).toList();
            case "addIntel" -> {contacts.add((IntelInfoPlugin)a[0]);yield null;}
            default -> null;
        });
        Global.setSettings(mock(SettingsAPI.class,(n,a)->n.equals("getColor")?java.awt.Color.WHITE:null));
        Global.setSector(mock(SectorAPI.class,(n,a)->switch(n) {
            case "getIntelManager" -> manager;
            case "getMemoryWithoutUpdate" -> f.memory;
            case "getPlayerStats" -> f.stats;
            case "getPlayerPerson" -> player;
            case "getPlayerFleet" -> fleet;
            case "getFaction","getPlayerFaction" -> mock(FactionAPI.class,(nn,aa)->nn.equals("getBaseUIColor")||nn.equals("getDarkUIColor")?java.awt.Color.WHITE:null);
            default -> null;
        }));
        PlayerContact.install();PlayerContact.install();
        check(contacts.size()==1,"Player contact registered exactly once");
        PlayerContact contact=(PlayerContact)contacts.get(0);
        check(contact.getIntelTags(null).contains(Tags.INTEL_CONTACTS),"Entry appears in Contacts");
        check(contact.getName().equals("Test Captain - Peerless training"),"Contact uses player name");
        check(contact.getIcon().equals(player.getPortraitSprite()),"Contact uses player portrait");
        TooltipMakerAPI info=mock(TooltipMakerAPI.class,(n,a)-> {
            if(n.equals("addButton")){buttons.add(a[1]);return mock(ButtonAPI.class,(nn,aa)->null);}
            return null;
        });
        OptionPanelAPI optionPanel=mock(OptionPanelAPI.class,(n,a)-> {
            if(n.equals("clearOptions"))options.clear();
            if(n.equals("addOption"))options.put(a[1],(String)a[0]);
            return null;
        });
        InteractionDialogAPI dialog=mock(InteractionDialogAPI.class,(n,a)->switch(n) {
            case "getVisualPanel" -> mock(VisualPanelAPI.class,(nn,aa)->null);
            case "getTextPanel" -> mock(TextPanelAPI.class,(nn,aa)->{if(nn.equals("addPara"))transcript.add((String)aa[0]);return null;});
            case "getOptionPanel" -> optionPanel;
            default -> null;
        });
        TrainingDialog[] training={null};int[] opened={0};
        IntelUIAPI ui=mock(IntelUIAPI.class,(n,a)-> {
            if(n.equals("showDialog")) {
                check(a[0]==fleet && a[1] instanceof TrainingDialog,"Intel button opens the shared text dialogue");
                training[0]=(TrainingDialog)a[1];opened[0]++;training[0].init(dialog);
            }
            return null;
        });
        contact.createSmallDescription(info,400f,800f);
        check(buttons.contains("train"),"Training button offered before earning points");
        contact.buttonPressConfirmed("train",ui);
        check(opened[0]==1 && options.containsKey("view:helmsmanship") && options.size()==9,"Contact opens seven skills plus navigation");
        int openingLines=transcript.size();
        for(int i=0;i<10;i++)training[0].optionSelected(null,"page");
        check(transcript.size()==openingLines && cues[0]==0,"Page navigation adds no text or training sound");
        training[0].optionSelected(null,"view:helmsmanship");
        check(!options.containsKey("train") && PeerlessSkills.getPoints()==0,"Zero-point preview cannot spend");
        int previewLines=transcript.size();
        training[0].optionSelected(null,"back");training[0].optionSelected(null,"page");
        check(transcript.size()==previewLines && cues[0]==0,"Back and preview do not repeat the introduction or play training sound");
        check(options.containsKey("view:polarized_armor"),"Remaining skills accessible through contact menu");
        PeerlessSkills.awardOnce("$ship_trophy_room_peerless_isa_v1");
        contact.buttonPressConfirmed("train",ui);
        training[0].optionSelected(null,"view:helmsmanship");
        check(options.containsKey("train") && PeerlessSkills.getPoints()==1,"Eligible purchase offered without spending on view");
        training[0].optionSelected(null,"train");
        check(PeerlessSkills.isTrained("helmsmanship") && PeerlessSkills.getPoints()==0,"Contact training spends exactly one point");
        check(cues[0]==1 && transcript.get(transcript.size()-1).equals("Peerless training complete. Unspent points: 0."),"Successful training plays one cue and updates the balance");
        training[0].optionSelected(null,"train");
        check(PeerlessSkills.getPoints()==0,"Stale purchase does not spend twice");
        check(cues[0]==1 && transcript.stream().filter(t->t.startsWith("Peerless training. Unspent points:")).count()==2,"One introduction per opened dialog; stale purchase is silent");
        buttons.clear();contact.createSmallDescription(info,400f,800f);
        check(buttons.contains("train"),"Contact remains available after spending");
        contact.buttonPressConfirmed("unrelated",ui);
        check(opened[0]==2,"Unrelated contact action does nothing");
        Global.setSoundPlayer(null);
        System.out.println("PASS: player contact, navigation without repeated text, one introduction per dialog, updated balance and one sound per successful training.");
    }
}
