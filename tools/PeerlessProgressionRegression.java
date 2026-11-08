package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.CharacterStats;
import com.fs.starfarer.campaign.rules.Memory;
import com.fs.starfarer.loading.scripts.ScriptStore;
import com.fs.util.container.repo.ObjectRepository;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;
import lunalib.backend.ui.settings.LunaSettingsLoader;
import lunalib.lunaSettings.LunaSettings;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;
import peerless.api.PeerlessSkills;
import peerless.campaign.*;
import static peerless.skills.PeerlessFixture.*;

/** Runs actual native CharacterStats.addXP/levelUpIfNeeded, including multiple wraps within one award. */
public final class PeerlessProgressionRegression {
    static final class Levels implements LevelupPlugin {
        int cap=15;long cycle=400;
        public long getXPForLevel(int level){return level<=cap?(level-1)*100L:(cap-1)*100L+cycle;}
        public int getMaxLevel(){return cap;}
        public int getPointsAtLevel(int level){return 1;}
        public int getStoryPointsPerLevel(){return 4;}
        public int getBonusXPUseMultAtMaxLevel(){return 1;}
    }
    static final class Engine extends CampaignEngine {
        CharacterStats stats;
        @Override public CharacterStats getPlayerStats(){return stats;}
        @Override public PersonAPI getPlayerPerson(){return Global.getSector().getPlayerPerson();}
    }
    public static void main(String[] args)throws Exception {
        var memory=new Memory();var levels=new Levels();boolean[] luna={false};
        var config=new JSONObject().put("baseMaxAdmins",2).put("baseMaxOutposts",4).put("startingCommandPoints",5).put("baseNumOfficers",8);
        for(Field field:Class.forName("com.fs.starfarer.settings.StarfarerSettings").getDeclaredFields())
            if(field.getType()==JSONObject.class && Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(null,config);}
        ObjectRepository repository=null;
        for(Field field:ScriptStore.class.getDeclaredFields())if(field.getType()==ObjectRepository.class){field.setAccessible(true);repository=(ObjectRepository)field.get(null);}
        repository.add(levels);
        Global.setSettings(mock(SettingsAPI.class,(name,a)->switch(name){
            case "getModManager" -> mock(ModManagerAPI.class,(nn,aa)->nn.equals("isModEnabled") && aa[0].equals("lunalib")?luna[0]:null);
            case "getLevelupPlugin" -> ScriptStore.Object(LevelupPlugin.class).get(0);
            case "getFloat" -> switch((String)a[0]) {
                case "playerMaxLevel" -> 15f;case "storyPointsPerLevel" -> 4f;default -> 1f;
            };
            case "getInt" -> 1;case "getColor" -> java.awt.Color.WHITE;
            default -> null;
        }));
        CharacterStats stats=new CharacterStats();stats.setSkipRefresh(true);stats.setLevel(15);stats.setXP(levels.getXPForLevel(15));
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(sun.misc.Unsafe)uf.get(null);
        Engine engine=(Engine)unsafe.allocateInstance(Engine.class);engine.stats=stats;
        Field singleton=CampaignEngine.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,engine);
        Global.setSector(mock(SectorAPI.class,(name,a)->switch(name){
            case "getPlayerStats" -> engine.stats;case "getMemoryWithoutUpdate" -> memory;
            case "getPlayerPerson" -> mock(PersonAPI.class,(nn,aa)->nn.equals("getStats")?engine.stats:null);
            case "getCampaignUI" -> mock(CampaignUIAPI.class,(nn,aa)->null);
            case "getPlayerFaction" -> mock(FactionAPI.class,(nn,aa)->nn.contains("Color")?java.awt.Color.WHITE:null);
            default -> null;
        }));
        check(!ProgressionSettings.read().enabled() && ProgressionSettings.read().levelsPerPoint()==10,"Luna absent defaults to quest-only with ratio ten");
        var settings=new CommonDataJSONObject("peerless-regression-unused.json");
        settings.put("peerless_postCapProgression",false);settings.put("peerless_levelsPerPoint",10);
        LunaSettingsLoader.INSTANCE.setHasLoaded(true);LunaSettingsLoader.getSettings().put("peerless_skills",settings);
        luna[0]=true;PeerlessProgression.install();
        check(ScriptStore.Object(LevelupPlugin.class).get(0)==levels,"Disabled progression leaves the selected level plugin intact");
        stats.addXP(8000,null,false,false,true);PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==0,"No XP rewards by default");
        memory.set("$gaATG_missionCompleted",true);PeerlessProgression.advance();PeerlessProgression.advance();
        memory.set("$shipTrophyGanEdenQuestStage","SPACE_ELEVATOR");PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==1,"Unfinished Borrowed Name grants nothing");
        memory.set("$shipTrophyGanEdenQuestStage","COMPLETED");PeerlessProgression.advance();PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==2 && memory.getBoolean(PeerlessRewardsRegression.ISA),"Completed Borrowed Name backfills exactly once");
        check(!PeerlessSkills.awardOnce(PeerlessRewardsRegression.ISA),"Story integration cannot duplicate backfill");
        memory.set("$chief_navigator_menelaus_final_debrief_complete_v1",true);
        PeerlessProgression.advance();PeerlessProgression.advance();
        check(!PeerlessSkills.awardOnce(PeerlessRewardsRegression.SINNI),"SoS legacy callback cannot duplicate detected reward");
        check(PeerlessSkills.getPoints()==3,"Galatia, HoT and SoS independently grant one default point");
        if (args.length > 0 && "rewards-only".equals(args[0])) {
            System.out.println("PASS: completion flags grant Galatia and Borrowed Name once; unfinished quests and duplicate integrations grant nothing.");
            return;
        }
        memory.clear();settings.put("peerless_postCapProgression",true);PeerlessProgression.advance();
        check(ScriptStore.Object(LevelupPlugin.class).get(0)!=levels,"Optional observer delegates the selected plugin");
        int storyPointsBefore=stats.getStoryPoints();int skillPointsBefore=stats.getPoints();
        stats.addXP(3960,null,false,false,true);PeerlessProgression.advance();check(PeerlessSkills.getPoints()==0,"No reward before ten full XP bars");
        stats.addXP(40,null,false,false,true);PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==1 && stats.getXP()==levels.getXPForLevel(15),"Exact tenth bar awards once with native XP wrapping intact");
        check(stats.getStoryPoints()==storyPointsBefore+40 && stats.getPoints()==skillPointsBefore,"Vanilla story-point gains and ordinary skill points are unchanged");
        // Several identical awards before any campaign frame must each be counted.
        stats.addXP(4000,null,false,false,true);stats.addXP(4000,null,false,false,true);
        PeerlessProgression.advance();PeerlessProgression.advance();PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==3,"Repeated same-size awards in one frame are not lost or duplicated");
        stats.addXP(12000,null,false,false,true);
        for(int i=0;i<4;i++)PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==6,"Large multi-wrap XP award pays every earned point");
        for(int i=0;i<20;i++)Global.getSettings().getLevelupPlugin().getXPForLevel(16);
        PeerlessProgression.advance();check(PeerlessSkills.getPoints()==6,"Tooltip XP queries cannot create points");
        stats.addStoryPoints(100);PeerlessProgression.advance();check(PeerlessSkills.getPoints()==6,"Direct story-point grants are not XP");
        settings.put("peerless_levelsPerPoint",2);stats.addXP(600,null,false,false,true);PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==6,"Partial progress remains banked");
        settings.put("peerless_postCapProgression",false);PeerlessProgression.advance();
        check(ScriptStore.Object(LevelupPlugin.class).get(0)==levels,"Disabling restores the original plugin");
        stats.addXP(8000,null,false,false,true);PeerlessProgression.advance();
        settings.put("peerless_postCapProgression",true);PeerlessProgression.advance();
        stats.addXP(200,null,false,false,true);PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==7,"Toggle preserves prior progress while excluding disabled XP");
        PeerlessProgression.install();PeerlessProgression.advance();check(PeerlessSkills.getPoints()==7,"Reload does not repay earlier progression");
        levels.cap=25;levels.cycle=800;stats.setLevel(25);stats.setXP(levels.getXPForLevel(25));
        PeerlessProgression.install();stats.addXP(1600,null,false,false,true);PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==8,"Changed cap and XP requirements use the delegated level plugin");
        check(stats.getLevel()==25 && stats.getXP()==levels.getXPForLevel(25),"Native level cap and XP bar preserved");
        memory.clear();levels.cap=15;levels.cycle=400;stats.setLevel(14);stats.setXP(1300);
        settings.put("peerless_levelsPerPoint",1);PeerlessProgression.install();
        stats.addXP(300,null,false,false,true);PeerlessProgression.advance();
        check(stats.getLevel()==15 && PeerlessSkills.getPoints()==0,"XP used to reach the cap is excluded");
        stats.addXP(200,null,false,false,true);PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==1,"Only XP beyond the cap fills Peerless progression");
        settings.put("peerless_levelsPerPoint",0);check(ProgressionSettings.read().levelsPerPoint()==1,"Invalid ratio is clamped");
        settings.put("peerless_postCapProgression",false);PeerlessProgression.advance();
        // Settings/console dialogs can award XP before the next campaign-script frame.
        memory.clear();PeerlessProgression.install();
        settings.put("peerless_postCapProgression",true);
        LunaSettings.reportSettingsChanged("peerless_skills");
        stats.addXP(800,null,false,false,true);
        String diagnostic=PeerlessProgression.diagnostics();long xpBefore=stats.getXP();
        check(diagnostic.contains("XP tracker: attached") && diagnostic.contains("Banked progress: 2.0000 / 1")
                && diagnostic.contains("XP per bar: 400"),"Diagnostics expose actual pending progress");
        var command=new peerless.console.APSProgress();
        check(command.runCommand("",CommandContext.COMBAT_MISSION)==CommandResult.WRONG_CONTEXT,"Progress command is campaign-only");
        check(command.runCommand("extra",CommandContext.CAMPAIGN_MAP)==CommandResult.BAD_SYNTAX,"Progress command rejects arguments");
        check(command.runCommand("",CommandContext.CAMPAIGN_MAP)==CommandResult.SUCCESS,"Progress command runs against native stats and memory");
        check(diagnostic.equals(PeerlessProgression.diagnostics()) && stats.getXP()==xpBefore && PeerlessSkills.getPoints()==0,
                "Diagnostics do not alter XP, baseline, pending rewards or point balance");
        PeerlessProgression.advance();PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==2,"Enabling in Luna counts XP awarded before the next campaign frame");
        settings.put("peerless_postCapProgression",false);LunaSettings.reportSettingsChanged("peerless_skills");
        check(ScriptStore.Object(LevelupPlugin.class).get(0)==levels,"Luna disable detaches before another frame");
        stats.addXP(800,null,false,false,true);
        settings.put("peerless_postCapProgression",true);LunaSettings.reportSettingsChanged("another_mod");
        check(ScriptStore.Object(LevelupPlugin.class).get(0)==levels,"Other mods' settings events do not reconfigure progression");
        LunaSettings.reportSettingsChanged("peerless_skills");
        stats.addXP(100,null,false,false,true);
        settings.put("peerless_levelsPerPoint",2);LunaSettings.reportSettingsChanged("peerless_skills");
        stats.addXP(700,null,false,false,true);PeerlessProgression.advance();PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==3,"Immediate toggles exclude disabled XP and ratio changes retain banked progress");
        int listenerCount=LunaSettings.getListeners$LunaLib().size();
        PeerlessProgression.install();PeerlessProgression.install();
        check(LunaSettings.getListeners$LunaLib().size()==listenerCount,"Reload does not duplicate the Luna listener");
        settings.put("peerless_postCapProgression",false);LunaSettings.reportSettingsChanged("peerless_skills");
        overCapProgression(engine,settings,memory);
        settings.put("peerless_postCapProgression",false);LunaSettings.reportSettingsChanged("peerless_skills");
        Global.setSector(null);LunaSettings.reportSettingsChanged("peerless_skills");
        check(command.runCommand("",CommandContext.CAMPAIGN_MAP)==CommandResult.ERROR,"No campaign is handled safely");
        System.out.println("PASS: Luna defaults/toggle/ratio, three quest rewards, native stats/memory, actual XP wraps, large awards, direct-SP exclusion, reload, modified caps, immediate settings events and read-only progress command.");
    }

    static CharacterStats overCapStats() {
        var stats=new CharacterStats();stats.setSkipRefresh(true);
        stats.setLevel(22);stats.setXP(19572481L);
        return stats;
    }
    static void overCapProgression(Engine engine,JSONObject settings,Memory memory)throws Exception {
        // Exact diagnostic state from the live report; compile the installed game's authored
        // level plugin into the test output, rather than substituting a fake XP curve.
        LevelupPlugin vanilla=new data.scripts.plugins.LevelupPluginImpl();
        ScriptStore.Object(LevelupPlugin.class).set(0,vanilla);
        check(vanilla.getMaxLevel()==15 && vanilla.getXPForLevel(16)-vanilla.getXPForLevel(15)==4000000L,
                "Reported cap and native XP-bar requirement");
        long[] grants={10000000L,10000000L,25000L,75000L,3900000L};
        engine.stats=overCapStats();
        for(long grant:grants)engine.stats.addXP(grant,null,false,false,true);
        long controlXP=engine.stats.getXP();int controlSP=engine.stats.getStoryPoints();
        memory.clear();engine.stats=overCapStats();
        settings.put("peerless_levelsPerPoint",1);settings.put("peerless_postCapProgression",true);
        PeerlessProgression.install();
        long earned=0L;
        for(long grant:grants) {
            engine.stats.addXP(grant,null,false,false,true);earned+=grant;
            Object recorded=memory.get("$peerless_skills_postcap_levels_v1");
            check(recorded instanceof Number && Math.abs(((Number)recorded).doubleValue()-earned/4000000d)<1e-9,
                    "Level 22 / cap 15 records every native XP grant before a campaign frame: "+earned);
            check(((Number)memory.get("$peerless_skills_postcap_last_xp_v1")).longValue()==engine.stats.getXP(),
                    "Recorded baseline matches native normalization above the cap");
        }
        for(int i=0;i<7;i++)PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==6,"24 million XP above the lowered cap awards six points at ratio one");
        check(engine.stats.getXP()==controlXP && engine.stats.getStoryPoints()==controlSP
                && engine.stats.getLevel()==22 && engine.stats.getPoints()==0,
                "Over-cap tracking preserves native XP, story points, character level and skill points");
        for(int i=0;i<20;i++) {
            Global.getSettings().getLevelupPlugin().getXPForLevel(16);
            Global.getSettings().getLevelupPlugin().getXPForLevel(23);
        }
        PeerlessProgression.install();PeerlessProgression.advance();
        check(PeerlessSkills.getPoints()==6,"Over-cap tooltip queries and reload do not duplicate points");
        System.out.println("PASS: live level-22/cap-15/19572481-XP reproducer, installed vanilla XP curve, repeated grants, exact wrapped baseline and unchanged native rewards.");
    }
}
