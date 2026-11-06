package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.*;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.ModAndPluginData;
import com.fs.starfarer.campaign.comms.C;
import com.fs.starfarer.ui.n;
import java.lang.reflect.*;
import java.util.*;
import peerless.api.PeerlessSkills;
import peerless.campaign.*;
import static peerless.skills.PeerlessFixture.*;

/** Award queue and real native message-click dispatch; physical hit testing remains a live check. */
public final class PeerlessPointNotificationRegression {
    record Notice(IntelInfoPlugin intel, CommMessageAPI.MessageClickAction action, SectorEntityToken target) {}
    static class NativeMessage extends C {
        Notice notice; n button;
        NativeMessage(){super(null,null,null,null);}
        @Override public CommMessageAPI.MessageClickAction getAction(){return notice.action();}
        @Override public Object getCustom(){return notice.target();}
        @Override public IntelInfoPlugin getIntel(){return notice.intel();}
        @Override public n getIconButton(){return button;}
    }
    static class Picker extends ModAndPluginData {
        @Override public InteractionDialogPlugin pickInteractionDialogPlugin(Object param, SectorEntityToken target) {
            return new PeerlessPointNotifications().pickInteractionDialogPlugin(param,target).plugin;
        }
    }
    public static void main(String[] args)throws Exception {
        var f=new PeerlessFixture();
        var notices=new ArrayList<Notice>();var plugins=new HashMap<String,CampaignPlugin>();
        boolean[] busy={true};CoreUITabId[] tab={null};
        var ui=mock(CampaignUIAPI.class,(name,a)->switch(name){
            case "isShowingDialog" -> busy[0];case "getCurrentCoreTab" -> tab[0];
            case "addMessage" -> {notices.add(new Notice((IntelInfoPlugin)a[0],(CommMessageAPI.MessageClickAction)a[1],(SectorEntityToken)a[2]));yield null;}
            default -> null;
        });
        var location=mock(LocationAPI.class,(name,a)-> {
            if(!name.equals("createToken"))throw new AssertionError("Notice must not add a map entity");
            var token=new PeerlessFixture();return mock(SectorEntityToken.class,(nn,aa)->nn.equals("getMemoryWithoutUpdate")?token.memory:null);
        });
        Global.setSector(mock(SectorAPI.class,(name,a)->switch(name){
            case "getMemoryWithoutUpdate" -> f.memory;case "getPlayerStats" -> f.stats;case "getPlayerPerson" -> f.player;
            case "getCampaignUI" -> ui;case "getCurrentLocation" -> location;
            case "unregisterPlugin" -> {plugins.remove((String)a[0]);yield null;}
            case "registerPlugin" -> {var p=(CampaignPlugin)a[0];plugins.put(p.getId(),p);yield null;}
            default -> null;
        }));
        PeerlessPointNotifications.install();PeerlessPointNotifications.install();
        check(plugins.size()==1 && plugins.values().iterator().next().isTransient(),"One transient interaction route");
        check(PeerlessSkills.awardOnce(PeerlessRewardsRegression.ISA),"First reward granted");
        PeerlessPointNotifications.showPending();check(notices.isEmpty(),"Wait until the reward dialog closes");
        busy[0]=false;tab[0]=CoreUITabId.CHARACTER;PeerlessPointNotifications.showPending();
        check(notices.isEmpty(),"Wait until the core screen closes");
        tab[0]=null;PeerlessPointNotifications.showPending();PeerlessPointNotifications.showPending();
        check(notices.size()==1,"Exactly one notification for first award");
        check(text(notices.get(0)).contains("You have 1 Peerless point to spend."),"Singular balance shown");
        check(!PeerlessSkills.awardOnce(PeerlessRewardsRegression.ISA),"Repeated award check rejected");
        PeerlessPointNotifications.showPending();check(notices.size()==1,"Repeated reward check is silent");
        PeerlessSkills.awardOnce(PeerlessRewardsRegression.SINNI);PeerlessPointNotifications.showPending();
        check(notices.size()==2 && text(notices.get(1)).contains("You have 2 Peerless points to spend."),"Second award gets its own notice and total");
        PeerlessSkills.train("helmsmanship");f.skill=1;PeerlessSkills.refundInactiveTraining();
        PeerlessPointNotifications.showPending();check(notices.size()==2,"Spending and refunds do not announce new rewards");
        check(new PeerlessPointNotifications().pickInteractionDialogPlugin(mock(SectorEntityToken.class,(nn,aa)->nn.equals("getMemoryWithoutUpdate")?new PeerlessFixture().memory:null))==null,"Unrelated targets untouched");
        PeerlessSkills.awardOnce("$test_third_reward");PeerlessSkills.awardOnce("$test_fourth_reward");
        var copy=new HashMap<>(f.saved);f.saved.clear();f.saved.putAll(copy);
        PeerlessPointNotifications.install();PeerlessPointNotifications.showPending();PeerlessPointNotifications.showPending();PeerlessPointNotifications.showPending();
        check(notices.size()==4,"Queued awards survive reload state and do not collapse into one");
        nativeClick(notices.get(1));
        System.out.println("PASS: one notice per award, totals, duplicate suppression, queued delivery, refunds silent, native click dispatch into the real training menu.");
    }
    static String text(Notice notice) {
        List<String> lines=new ArrayList<>();
        notice.intel().createIntelInfo(mock(TooltipMakerAPI.class,(name,a)->{if(name.equals("addPara"))lines.add((String)a[0]);return null;}),IntelInfoPlugin.ListInfoMode.MESSAGES);
        return String.join("\n",lines);
    }
    @SuppressWarnings("unchecked")
    static void nativeClick(Notice notice)throws Exception {
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)uf.get(null);
        var engine=(CampaignEngine)unsafe.allocateInstance(CampaignEngine.class);
        var singleton=CampaignEngine.class.getDeclaredField("instance");singleton.setAccessible(true);Object previous=singleton.get(null);
        Map<Object,String> options=new HashMap<>();int[] opened={0};
        var dialog=mock(InteractionDialogAPI.class,(name,a)->switch(name){
            case "getVisualPanel" -> mock(VisualPanelAPI.class,(nn,aa)->null);
            case "getTextPanel" -> mock(TextPanelAPI.class,(nn,aa)->null);
            case "getOptionPanel" -> mock(OptionPanelAPI.class,(nn,aa)->{if(nn.equals("addOption"))options.put(aa[1],(String)aa[0]);return null;});
            default -> null;
        });
        engine.setCampaignUI(mock(CampaignEngine.o.class,(name,a)->{
            if(name.equals("showInteractionDialog")) {
                check(a[0] instanceof TrainingDialog && a[1]==notice.target(),"Native click opens training for the message target");
                opened[0]++;((TrainingDialog)a[0]).init(dialog);return true;
            }
            return null;
        }));
        for(Field field:CampaignEngine.class.getDeclaredFields())if(field.getType()==ModAndPluginData.class){field.setAccessible(true);field.set(engine,unsafe.allocateInstance(Picker.class));}
        try {
            singleton.set(null,engine);
            Class<?> listClass=Class.forName("com.fs.starfarer.campaign.comms.super");Object list=listClass.getConstructor().newInstance();
            var message=(NativeMessage)unsafe.allocateInstance(NativeMessage.class);
            message.notice=notice;message.button=new n(null,null);
            ((List<C>)listClass.getMethod("getMessages").invoke(list)).add(message);
            listClass.getMethod("actionPerformed",Object.class,Object.class).invoke(list,null,message.button);
            check(opened[0]==1 && options.containsKey("view:helmsmanship") && options.containsKey("page"),"Actual native dispatch reaches the training options");
        } finally {singleton.set(null,previous);}
    }
}
