package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import java.util.*;
import lunalib.backend.ui.settings.LunaSettingsLoader;
import lunalib.lunaSettings.LunaSettings;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;
import peerless.api.PeerlessSkills;
import peerless.campaign.*;
import static peerless.skills.PeerlessFixture.*;

/** Public-API observations only: no native XP implementation or script registry in this test. */
public final class PeerlessProgressionRegression {
    static final String BANK = "$peerless_skills_postcap_levels_v1";
    static final String SEQUENCE = "$peerless_skills_postcap_awards_v1";
    static final String BALANCE = "$peerless_skills_points_v1";
    static final class Levels implements LevelupPlugin {
        int cap = 15;
        long cycle = 400, aboveStep;
        public long getXPForLevel(int n) {
            if (n < 1) throw new AssertionError("Invalid level query");
            return n <= cap ? (n - 1) * 100L : (cap - 1) * 100L + cycle + (n - cap - 1) * aboveStep;
        }
        public int getMaxLevel() { return cap; }
        public int getPointsAtLevel(int n) { return 1; }
        public int getStoryPointsPerLevel() { return 4; }
        public int getBonusXPUseMultAtMaxLevel() { return 1; }
    }
    static final class Harness {
        final Map<String,Object> saved = new HashMap<>();
        final PeerlessFixture fixture = new PeerlessFixture();
        final CommonDataJSONObject config = new CommonDataJSONObject("peerless-polling-test-unused.json");
        final CampaignEffects script = new CampaignEffects();
        Levels levels = new Levels();
        long xp = 1400;
        int level = 15, storyPoints = 8, writes;
        boolean luna = true;
        final MemoryAPI memory = mock(MemoryAPI.class, (n,a) -> switch(n) {
            case "get" -> saved.get(a[0]);
            case "getBoolean" -> Boolean.TRUE.equals(saved.get(a[0]));
            case "getString" -> saved.get(a[0]);
            case "set" -> { writes++; saved.put((String)a[0], a[1]); yield null; }
            case "unset" -> { writes++; saved.remove(a[0]); yield null; }
            default -> null;
        });
        final MutableCharacterStatsAPI stats = mock(MutableCharacterStatsAPI.class, (n,a) -> {
            if (n.startsWith("set") || n.startsWith("add") || n.startsWith("spend") || n.startsWith("levelUp"))
                throw new AssertionError("Tracking must not mutate character stats: " + n);
            return switch(n) {
                case "getXP" -> xp;
                case "getLevel" -> level;
                case "getStoryPoints" -> storyPoints;
                case "getPoints" -> 2;
                case "getDynamic" -> fixture.dynamic;
                default -> null;
            };
        });
        Harness(boolean enabled, int ratio) {
            setting("peerless_postCapProgression", enabled);
            setting("peerless_levelsPerPoint", ratio);
            LunaSettingsLoader.INSTANCE.setHasLoaded(true);
            LunaSettingsLoader.getSettings().put("peerless_skills", config);
            Global.setSettings(mock(SettingsAPI.class, (n,a) -> switch(n) {
                case "getLevelupPlugin" -> levels;
                case "getModManager" -> mock(ModManagerAPI.class, (nn,aa) ->
                        nn.equals("isModEnabled") && aa[0].equals("lunalib") && luna);
                case "getColor" -> java.awt.Color.WHITE;
                default -> null;
            }));
            var person = mock(PersonAPI.class, (n,a) -> n.equals("getStats") ? stats : null);
            var fleet = mock(CampaignFleetAPI.class, (n,a) -> n.equals("getFleetData")
                    ? mock(FleetDataAPI.class, (nn,aa) -> nn.equals("getMembersListCopy") ? List.of() : null) : null);
            Global.setSector(mock(SectorAPI.class, (n,a) -> switch(n) {
                case "getMemoryWithoutUpdate" -> memory;
                case "getPlayerStats" -> stats;
                case "getPlayerPerson" -> person;
                case "getPlayerFleet" -> fleet;
                default -> null;
            }));
            PeerlessProgression.install();
            check(script.runWhilePaused(), "Campaign sampling remains enabled while paused");
        }
        void frame() { script.advance(0f); }
        void frame(long value) { xp = value; frame(); }
        void setting(String key, Object value) {
            try { config.put(key, value); }
            catch (org.json.JSONException ex) { throw new AssertionError(ex); }
        }
        void enable(boolean value) {
            setting("peerless_postCapProgression", value);
            LunaSettings.reportSettingsChanged("peerless_skills");
        }
        void ratio(int value) {
            setting("peerless_levelsPerPoint", value);
            LunaSettings.reportSettingsChanged("peerless_skills");
        }
        double bank() { return ((Number)saved.getOrDefault(BANK, 0d)).doubleValue(); }
        int points() { return PeerlessSkills.getPoints(); }
        void bankIs(double value, String why) { check(Math.abs(bank() - value) < 1e-8, why + ": " + bank()); }
    }

    public static void main(String[] args) {
        defaultsAndQuests();
        visibleProgressAndLimits();
        togglesAndReload();
        capAndCurveChanges();
        invalidAndMissingState();
        diagnosticsAndMigration();
        System.out.println("PASS: public-API campaign-frame XP polling, rollovers and known losses, quest rewards, toggles, reload/migration, cap changes, invalid ranges, diagnostics and unchanged native stats/plugin.");
    }
    static void defaultsAndQuests() {
        Harness h = new Harness(false, 10);
        h.luna = false;
        check(!ProgressionSettings.read().enabled() && ProgressionSettings.read().levelsPerPoint() == 10, "Defaults without Luna");
        h.frame(1700);
        h.saved.put("$gaATG_missionCompleted", true);
        h.saved.put("$shipTrophyGanEdenQuestStage", "SPACE_ELEVATOR");
        h.frame(); h.frame();
        check(h.points() == 1, "Galatia once; unfinished HoT grants nothing");
        h.saved.put("$shipTrophyGanEdenQuestStage", "COMPLETED");
        h.saved.put("$chief_navigator_menelaus_final_debrief_complete_v1", true);
        h.frame(); h.frame();
        check(h.points() == 3 && !PeerlessSkills.awardOnce("$ship_trophy_room_peerless_isa_v1")
                && !PeerlessSkills.awardOnce("$chief_navigator_domain_epic_v1"), "All three quests once; old callbacks cannot duplicate rewards");
        h.bankIs(0, "Disabled XP excluded");
        check(Global.getSettings().getLevelupPlugin() == h.levels, "Selected plugin is never replaced");
    }
    static void visibleProgressAndLimits() {
        Harness h = new Harness(true, 10);
        h.frame(1500); h.bankIs(.25, "Visible increase");
        h.frame(1750); h.bankIs(.875, "Another partial increase");
        h.frame(1450); h.bankIs(1.125, "Observed rollover uses the public range");
        int writes = h.writes;
        for (int i = 0; i < 10000; i++) h.frame();
        check(h.writes == writes && h.points() == 0, "Unchanged/paused frames do not write progress or award again");

        h = new Harness(true, 10);
        for (int i = 0; i < 40; i++) h.frame(1400 + ((i + 1) % 4) * 100);
        check(h.points() == 1 && h.saved.get(PeerlessPointNotifications.PENDING).equals(1L), "Ten observed bars award one point and notification");
        h.bankIs(0, "Paid bars deducted once");

        h = new Harness(true, 10);
        h.frame(1400); // One or many whole bars can leave exactly the same public XP value.
        h.bankIs(0, "Unobservable full cycles are not guessed");
        h.frame(1500); h.bankIs(.25, "A multi-bar grant leaving +100 counts only that visible remainder");
        h.storyPoints += 100; h.frame();
        h.bankIs(.25, "Story-point grants are not treated as XP");

        h = new Harness(true, 100);
        h.frame(5400); h.bankIs(10, "An unnormalized XP value is fully visible");
        h.frame(1400); h.bankIs(10, "Later normalization cannot double count it");
        h.frame(1500); h.bankIs(10.25, "Sampling resumes after normalization");
    }
    static void togglesAndReload() {
        Harness h = new Harness(false, 10);
        h.xp = 1550; h.enable(true);
        h.frame(1650); h.bankIs(.25, "Enable excludes old XP and captures XP before the next frame");
        h.xp = 1750; h.ratio(2); h.bankIs(.5, "Ratio-only event retains pending visible XP");
        h.xp = 1790; h.enable(false); h.bankIs(.6, "Disabling samples the final enabled gain");
        h.frame(1470); h.xp = 1600; h.enable(true);
        h.frame(1640); h.bankIs(.7, "Disabled gains excluded; prior bank retained");
        int listeners = LunaSettings.getListeners$LunaLib().size();
        var saved = new HashMap<>(h.saved);
        h = new Harness(true, 2);
        h.saved.putAll(saved); h.xp = 1640;
        PeerlessProgression.install(); PeerlessProgression.install(); h.frame();
        h.bankIs(.7, "Reload preserves partial progress without replaying XP");
        check(LunaSettings.getListeners$LunaLib().size() == listeners, "Reload deduplicates settings listener");
        h.ratio(1); h.frame(1760);
        check(h.points() == 1, "Saved progress contributes to next payout");
        h.bankIs(0, "Reloaded progress pays once");
        h.setting("peerless_postCapProgression", false); h.frame(1780);
        h.setting("peerless_postCapProgression", true); h.frame(1600);
        h.frame(1700); h.bankIs(.25, "Frame fallback rebases unreported toggles");
    }
    static void capAndCurveChanges() {
        Harness h = new Harness(true, 100);
        h.frame(1550);
        h.levels.cap = 25; h.levels.cycle = 800; h.level = 25;
        h.frame(2400); h.bankIs(.375, "Changed cap rebases rather than looking like a rollover");
        h.frame(2800); h.bankIs(.875, "Changed cap uses its public XP requirement");
        h.levels.cycle = 1000; h.frame(2900); h.bankIs(.875, "Curve-only change rebases");
        h.frame(3000); h.bankIs(.975, "New curve used on later gains");
        h.levels = new Levels(); h.levels.cap = 25; h.levels.cycle = 1000;
        h.frame(3100); h.bankIs(.975, "Replacement plugin rebases without mutating either plugin");
        h.frame(3200); h.bankIs(1.075, "Replacement plugin remains usable");

        h = new Harness(true, 1);
        h.level = 14; h.xp = 1300; PeerlessProgression.install();
        h.frame(1350); h.bankIs(0, "Below-cap XP excluded");
        h.level = 15; h.frame(1500); h.bankIs(.25, "Only visible excess after reaching cap counts");
        h.frame(1700); h.frame(1400);
        check(h.points() == 1, "Post-cap excess plus subsequent XP fills one bar");

        h = new Harness(true, 100);
        h.level = 22; h.xp = 1750; PeerlessProgression.install();
        h.frame(1780); h.bankIs(.075, "Above a lowered cap, visible gains count");
        h.frame(1720); h.bankIs(.075, "A flat above-cap curve supplies no rollover size; do not invent one");
        h.frame(1750); h.bankIs(.15, "Resume after unexplained drop");
        h.levels.aboveStep = 100; h.frame(2450);
        h.frame(2490); h.frame(2400); h.bankIs(.275, "Known above-cap rollover counts using its range and the cap's reward size");
    }
    static void invalidAndMissingState() {
        Harness h = new Harness(true, 1);
        h.levels.cycle = 0; h.frame(1500); h.frame(1600);
        h.bankIs(0, "Zero-width curve cannot divide by zero");
        h.levels.cycle = -1; h.frame(1700); h.bankIs(0, "Descending curve ignored");
        h.levels.cycle = 400; h.frame(1400); h.frame(1500); h.bankIs(.25, "Recovery rebases first");
        h.levels.cap = Integer.MAX_VALUE; h.frame(); h.bankIs(.25, "Cap-plus-one overflow avoided");
        h.levels.cap = 15; h.frame(1500);
        h.frame(-1); h.frame(1400); h.bankIs(.25, "Invalid XP resets baseline without inventing earnings");
        h.levels = null; h.frame();
        check(PeerlessProgression.diagnostics().contains("waiting for a valid XP requirement"), "Missing plugin handled");
        h = new Harness(true, 100);
        h.level = Integer.MAX_VALUE; h.xp = 1500; PeerlessProgression.install();
        h.frame(1600); h.frame(1400); h.bankIs(.25, "Actual-level overflow cannot fabricate a cap-sized rollover");
        Global.setSector(null);
        PeerlessProgression.install(); PeerlessProgression.advance();
        LunaSettings.reportSettingsChanged("peerless_skills");
    }
    static void diagnosticsAndMigration() {
        Harness h = new Harness(true, 1);
        h.saved.put(BANK, 2.5d); h.saved.put(SEQUENCE, 5L); h.saved.put(BALANCE, 2);
        h.saved.put("$peerless_skills_postcap_award_v1_5", true);
        h.saved.put("$peerless_skills_postcap_last_xp_v1", 99999999L);
        PeerlessProgression.install();
        h.xp = 1500;
        var before = new HashMap<>(h.saved);
        String text = PeerlessProgression.diagnostics();
        check(text.contains("public API, every campaign frame") && text.contains("XP per bar: 400"), "Diagnostics describe polling");
        check(h.saved.equals(before), "Diagnostics do not sample pending XP or mutate saves");
        h.frame(); check(h.points() == 3, "Old bank pays at most one point per frame");
        h.frame(); h.frame();
        check(h.points() == 4 && h.saved.get(SEQUENCE).equals(7L), "Legacy receipts and award sequence continue");
        h.bankIs(.75, "Migration retains bank and adds only newly observed XP");
        var command = new peerless.console.APSProgress();
        check(command.runCommand("", CommandContext.COMBAT_MISSION) == CommandResult.WRONG_CONTEXT, "Campaign-only command");
        check(command.runCommand("extra", CommandContext.CAMPAIGN_MAP) == CommandResult.BAD_SYNTAX, "Command syntax");
        check(command.runCommand("", CommandContext.CAMPAIGN_MAP) == CommandResult.SUCCESS, "Progress command remains usable");
        Global.setSector(null);
        check(command.runCommand("", CommandContext.CAMPAIGN_MAP) == CommandResult.ERROR, "No campaign command guard");
    }
}
