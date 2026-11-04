package peerless.skills;

import peerless.api.PeerlessSkills;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Misc.TokenType;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.CharacterStats;
import com.fs.starfarer.loading.SkillSpec;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import sun.misc.Unsafe;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.combat.entities.ship.o0OO;
import peerless.rules.PeerlessSkillsCMD;

/** Native engine regression; reflection is confined to this external harness. */
public final class PeerlessHelmsmanshipRegression {
    private interface Call { Object invoke(String name, Object[] args); }

    private static <T> T mock(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            Object value = call.invoke(method.getName(), args);
            if (value != null) return value;
            Class<?> result = method.getReturnType();
            if (result == boolean.class) return false;
            if (result == int.class) return 0;
            if (result == float.class) return 0f;
            if (result == long.class) return 0L;
            if (result == double.class) return 0d;
            return null;
        }));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void engine(CampaignEngine value) throws Exception {
        Field field = CampaignEngine.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, value);
    }

    /** Colony administration is unrelated to this headless skill fixture. */
    private static final class NativeStats extends CharacterStats {
        int refreshes;
        @Override public void refreshCharacterStatsEffects() { refreshes++; super.refreshCharacterStatsEffects(); }
        @Override public void refreshAllOutpostsEffectsForPlayerOutposts() { }
    }

    private static final class NativeEngine extends CampaignEngine {
        CharacterStats player;
        @Override public CharacterStats getPlayerStats() { return player; }
    }

    private static final class NativeOrdnanceVariant extends com.fs.starfarer.loading.specs.HullVariantSpec {
        int spent;
        // The external fixture allocates this without running unrelated hull-data initialization.
        NativeOrdnanceVariant() { super("refit-fixture", null); }
        @Override public int computeHullModOPCost(com.fs.starfarer.api.characters.MutableCharacterStatsAPI commander) { return spent; }
        @Override public int computeWeaponOPCost(com.fs.starfarer.api.characters.MutableCharacterStatsAPI commander) { return 30; }
    }


    private static void exercise(SkillSpec skill) throws Exception {
        Map<String,Object> saved = new HashMap<>();
        MemoryAPI memory = mock(MemoryAPI.class, (name,args) -> {
            if (name.equals("getBoolean")) return Boolean.TRUE.equals(saved.get(args[0]));
            if (name.equals("get") || name.equals("getString")) return saved.get(args[0]);
            if (name.equals("set")) {
                check(args.length == 2, "All state must be durable");
                saved.put((String) args[0],args[1]);
            }
            return null;
        });
        NativeStats playerStats = new NativeStats();
        NativeStats officerStats = new NativeStats();
        NativeEngine nativeEngine = (NativeEngine) unsafe().allocateInstance(NativeEngine.class);
        nativeEngine.player = playerStats; engine(nativeEngine);
        PersonAPI player = mock(PersonAPI.class, (name,args) -> name.equals("getStats") ? playerStats : null);
        PersonAPI officer = mock(PersonAPI.class, (name,args) -> name.equals("getStats") ? officerStats : null);
        Global.setSector(mock(SectorAPI.class,(name,args)-> {
            if (name.equals("getListenerManager")) return mock(ListenerManagerAPI.class,(n,a)->n.equals("getListeners") ? List.of() : null);
            if (name.equals("getPlayerFleet")) return mock(CampaignFleetAPI.class,(n,a)->n.equals("getFleetData") ? mock(FleetDataAPI.class,(n2,a2)->n2.equals("getMembersListCopy") || n2.equals("getOfficersCopy") ? List.of() : null) : null);
            if (name.equals("getPlayerStats")) return playerStats;
            if (name.equals("getPlayerPerson")) return player;
            if (name.equals("getMemoryWithoutUpdate")) return memory;
            return null;
        }));
        playerStats.setPoints(4); playerStats.setStoryPoints(6);
        playerStats.setSkillLevel(Skills.HELMSMANSHIP,2f);
        officerStats.setSkillLevel(Skills.HELMSMANSHIP,2f);
        check(!PeerlessSkills.train(Skills.HELMSMANSHIP), "Cannot train without a point");
        check(PeerlessSkills.awardOnce("$fixture_story"), "First story reward");
        check(!PeerlessSkills.awardOnce("$fixture_story"), "No duplicate story reward");
        PersonAPI[] captain={player};
        FleetMemberAPI member=mock(FleetMemberAPI.class,(name,args)->name.equals("getCaptain") ? captain[0] : null);
        o0OO ship=(o0OO) unsafe().allocateInstance(o0OO.class);
        for(Field f:o0OO.class.getDeclaredFields()) {
            if(Modifier.isStatic(f.getModifiers())) continue;
            if(f.getType()==MutableStat.class) {f.setAccessible(true);f.set(ship,new MutableStat(0f));}
            if(f.getType()==StatBonus.class) {f.setAccessible(true);f.set(ship,new StatBonus());}
        }
        ship.setFleetMember(member);
        playerStats.applyPersonalToStats(ship,HullSize.FRIGATE);
        float baseline=ship.getMaxSpeed().getFlatMod();
        float maneuver=ship.getAcceleration().getPercentMod();
        float zero=ship.getAllowZeroFluxAtAnyLevel().getFlatMod();
        check(baseline==10f && zero==1f,"Native Elite baseline +10 and boolean boost");
        check(PeerlessSkills.train(Skills.HELMSMANSHIP),"Eligible player selects");
        check(playerStats.getSkillLevel(Skills.HELMSMANSHIP)==2f,"Keep native Elite rank");
        ship.getMaxSpeed().modifyFlat(PeerlessHelmsmanship.MODIFIER,10f);
        playerStats.applyPersonalToStats(ship,HullSize.FRIGATE);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Native dispatch removes obsolete permanent speed modifier");
        check(ship.getAcceleration().getPercentMod()==maneuver && ship.getAllowZeroFluxAtAnyLevel().getFlatMod()==zero,"Base and boolean effects unchanged");
        for(int i=0;i<10;i++)playerStats.applyPersonalToStats(ship,HullSize.FRIGATE);
        check(ship.getMaxSpeed().getFlatMod()==10f && !PeerlessSkills.train(Skills.HELMSMANSHIP),"No repeated stacking or slot claim");
        check(playerStats.getPoints()==4 && playerStats.getStoryPoints()==6,"No point spending or refunds");
        captain[0]=officer;
        officerStats.applyPersonalToStats(ship,HullSize.FRIGATE);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Officer receives only vanilla Elite even on former player ship");
        // Live ship captain wins over stale campaign member ownership.
        captain[0]=player;
        ship.setEntity(mock(ShipAPI.class,(name,args)->name.equals("getCaptain") ? officer : null));
        playerStats.applyPersonalToStats(ship,HullSize.FRIGATE);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Live officer overrides campaign player captain");
        ship.setEntity(null);
        playerStats.applyPersonalToStats(ship,HullSize.FRIGATE);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Player's new ship eligible");
        PeerlessHelmsmanship effect=new PeerlessHelmsmanship();
        effect.unapply(ship,HullSize.FRIGATE,"native-id");
        check(ship.getMaxSpeed().getFlatMod()==10f,"Unapply preserves native Elite bonus");
        playerStats.setSkillLevel(Skills.HELMSMANSHIP,1f);
        effect.apply(ship,HullSize.FRIGATE,"native-id",1f);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Own modifier absent without Elite");
        check(PeerlessSkills.isTrained(Skills.HELMSMANSHIP) && !PeerlessSkills.canTrain(Skills.HELMSMANSHIP),"Respec retains slot");
        playerStats.setSkillLevel(Skills.HELMSMANSHIP,2f);
        playerStats.applyPersonalToStats(ship,HullSize.FRIGATE);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Relearning Elite reactivates");
        List<String> labels=new ArrayList<>();
        List<String> fonts=new ArrayList<>();
        List<Object[]> paragraphs=new ArrayList<>();
        TooltipMakerAPI tooltip=mock(TooltipMakerAPI.class,(name,args)-> {
            check(!name.equals("addSectionHeading"),"Peerless uses the native plain heading, without a banner");
            if(name.equals("setParaFont"))fonts.add((String)args[0]);
            if(name.equals("setParaFontDefault"))fonts.add("default");
            if(name.equals("addPara")){labels.add((String)args[0]);paragraphs.add(args);}
            return null;
        });
        effect.createCustomDescription(playerStats,skill,tooltip,400);
        check(labels.equals(List.of("Peerless",peerless.api.SkillCatalog.EFFECTS.get(Skills.HELMSMANSHIP))),"Concise heading and total benefit");
        check(fonts.equals(List.of("graphics/fonts/orbitron12condensed.fnt","default")),"Native Elite heading and effect fonts");
        check(paragraphs.get(0)[1].equals(com.fs.starfarer.api.util.Misc.getStoryOptionColor())
                && paragraphs.get(0)[2].equals(10f),"Native Elite heading color and padding");
        check(paragraphs.get(1)[1].equals(com.fs.starfarer.api.util.Misc.getHighlightColor())
                && paragraphs.get(1)[2].equals(3f),"Native effect color and padding");
        labels.clear();effect.createCustomDescription(officerStats,skill,tooltip,400);
        check(labels.isEmpty(),"Officer tooltip has no Peerless label");
        saved.put(PeerlessSkills.TRAINED,"some_other_skill");
        check(!PeerlessSkills.canTrain(Skills.HELMSMANSHIP),"Spent point cannot be used again");
        effect.apply(ship,HullSize.FRIGATE,"native-id",2f);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Different selection removes modifier");
        saved.put(PeerlessSkills.TRAINED,Skills.HELMSMANSHIP);
        new PeerlessHelmsmanship().apply(ship,HullSize.FRIGATE,"native-id",2f);
        check(ship.getMaxSpeed().getFlatMod()==10f,"Fresh effect instance uses durable selection");
        check(!new PeerlessSkillsCMD().execute("test",null,List.of(new Token("train",TokenType.LITERAL)),Map.of()),"Read-only/null dialog cannot grant");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        SettingsAPI oldSettings = Global.getSettings(); SectorAPI oldSector = Global.getSector();
        Field engineSingleton = CampaignEngine.class.getDeclaredField("instance");
        engineSingleton.setAccessible(true);
        CampaignEngine oldEngine = (CampaignEngine) engineSingleton.get(null);
        Map<Field, Object> nativeSettings = new HashMap<>();
        Class<?> store = Class.forName("com.fs.starfarer.loading.SpecStore");
        Map<String, Object> specs = (Map<String, Object>) store.getMethod("new", Class.class).invoke(null, SkillSpec.class);
        Map<String, Object> oldSpecs = new HashMap<>(specs);
        Map<String, Class<?>> scripts = (Map<String, Class<?>>) Class.forName("com.fs.starfarer.loading.scripts.ScriptStore").getField("void").get(null);
        Map<String, Class<?>> oldScripts = new HashMap<>(scripts);
        Map oldCache = new HashMap(CharacterStats.getEffectCache());
        try {
            JSONObject settings = new JSONObject().put("baseMaxAdmins", 2).put("baseMaxOutposts", 4)
                    .put("startingCommandPoints", 5).put("baseNumOfficers", 8);
            for (Field field : Class.forName("com.fs.starfarer.settings.StarfarerSettings").getDeclaredFields()) {
                if (field.getType() == JSONObject.class && Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true); nativeSettings.put(field, field.get(null)); field.set(null, settings);
                }
            }
            JSONObject definition = new JSONObject(Files.readString(Path.of("../../starsector-core/data/characters/skills/helmsmanship.skill")).replaceAll("(?m)#.*$", ""));
            // Exercise the legacy cleanup adapter against native dispatch without replacing the runtime skill file.
            definition.getJSONArray("effectGroups").getJSONObject(1).getJSONArray("effects")
                    .put(new JSONObject().put("type","SHIP").put("script","peerless.skills.PeerlessHelmsmanship"));
            JSONArray groups = definition.getJSONArray("effectGroups");
            for (int group = 0; group < groups.length(); group++) {
                JSONArray effects = groups.getJSONObject(group).getJSONArray("effects");
                for (int effect = 0; effect < effects.length(); effect++) {
                    String script = effects.getJSONObject(effect).getString("script"); scripts.put(script, Class.forName(script));
                }
            }
            SkillSpec skill = new SkillSpec(definition);
            specs.clear(); specs.put(Skills.HELMSMANSHIP, skill); CharacterStats.getEffectCache().clear();
            Global.setSettings(mock(SettingsAPI.class, (name, values) -> name.equals("getSkillSpec") ? skill : name.equals("getColor") ? java.awt.Color.WHITE : null));
            exercise(skill);
            JSONObject ordnanceDefinition = new JSONObject(Files.readString(Path.of("../../starsector-core/data/characters/skills/ordnance_expert.skill")).replaceAll("(?m)#.*$", ""));
            SkillSpec ordnance = new SkillSpec(ordnanceDefinition);
            NativeSkillDescriptions.append(ordnance);
            for (var effect : ordnance.getEffectsAPI()) scripts.put(effect.getEffectClass(), Class.forName(effect.getEffectClass()));
            specs.clear(); specs.put("ordnance_expert", ordnance); CharacterStats.getEffectCache().clear();
            Global.setSettings(mock(SettingsAPI.class, (name, values) -> name.equals("getSkillSpec") ? ordnance : name.equals("getColor") ? java.awt.Color.WHITE : null));
            exerciseOrdnance();
            Global.setSector(null); check(!PeerlessSkills.canTrain(Skills.HELMSMANSHIP), "No campaign must fail closed");
            System.out.println("PASS: native Helmsmanship effects, legacy modifier cleanup and native Elite speed, one slot, idempotence, respec, captain change, tooltip and durable selection.");
        } finally {
            Global.setSector(oldSector); Global.setSettings(oldSettings); engine(oldEngine);
            for (Map.Entry<Field, Object> entry : nativeSettings.entrySet()) entry.getKey().set(null, entry.getValue());
            specs.clear(); specs.putAll(oldSpecs); scripts.clear(); scripts.putAll(oldScripts);
            CharacterStats.getEffectCache().clear(); ((Map) CharacterStats.getEffectCache()).putAll(oldCache);
        }
    }

    private static void exerciseOrdnance() throws Exception {
        NativeStats playerStats = new NativeStats();
        NativeEngine nativeEngine = (NativeEngine) unsafe().allocateInstance(NativeEngine.class);
        nativeEngine.player = playerStats; engine(nativeEngine);
        PeerlessFixture fixture = new PeerlessFixture();
        fixture.saved.put("$peerless_skills_trained_v1_ordnance_expert", true);
        PersonAPI player = mock(PersonAPI.class, (name,args)->name.equals("getStats") ? playerStats : null);
        PersonAPI officer = mock(PersonAPI.class, (name,args)->name.equals("getStats") ? playerStats : null);
        Global.setSector(mock(SectorAPI.class, (name,args)->switch(name) {
            case "getMemoryWithoutUpdate" -> fixture.memory;
            case "getPlayerStats" -> playerStats;
            case "getPlayerPerson" -> player;
            case "getListenerManager" -> mock(ListenerManagerAPI.class,(n,a)->n.equals("getListeners") ? List.of() : null);
            case "getPlayerFleet" -> mock(CampaignFleetAPI.class,(n,a)->n.equals("getFleetData") ? mock(FleetDataAPI.class,(nn,aa)->nn.equals("getMembersListCopy") || nn.equals("getOfficersCopy") ? List.of() : null) : null);
            default -> null;
        }));
        playerStats.setSkillLevel("ordnance_expert", 2f);
        NativeOrdnanceVariant variant=(NativeOrdnanceVariant) unsafe().allocateInstance(NativeOrdnanceVariant.class);
        variant.spent=20;
        PersonAPI[] captain = {player};
        FleetMemberAPI member = mock(FleetMemberAPI.class,(n,a)->switch(n) {
            case "getCaptain", "getFleetCommanderForStats" -> captain[0];
            case "getVariant" -> variant;
            default -> null;
        });
        o0OO preview=(o0OO) unsafe().allocateInstance(o0OO.class);
        for(Field field:o0OO.class.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())) continue;
            if(field.getType()==MutableStat.class) {field.setAccessible(true);field.set(preview,new MutableStat(0f));}
            if(field.getType()==StatBonus.class) {field.setAccessible(true);field.set(preview,new StatBonus());}
            if(field.getType()==com.fs.starfarer.loading.specs.HullVariantSpec.class) {field.setAccessible(true);field.set(preview,variant);}
        }
        preview.setFleetMember(member);
        playerStats.applyPersonalToStats(preview,HullSize.CRUISER);
        check(preview.getFluxDissipation().getFlatMod()==60f,"Native refit dispatcher includes weapon and hullmod dissipation: "+preview.getFluxDissipation().getFlatMod()+"; variant="+preview.getVariant()+"; member="+(preview.getFleetMember()==member));
        check(preview.getFluxCapacity().getFlatMod()==800f,"Native refit dispatcher includes weapon and hullmod capacity");
        variant.spent=40;
        playerStats.applyPersonalToStats(preview,HullSize.CRUISER);
        check(preview.getFluxDissipation().getFlatMod()==75f && preview.getFluxCapacity().getFlatMod()==1000f,"Refit edits recompute hullmod OP without stacking");
        captain[0]=officer;
        playerStats.applyPersonalToStats(preview,HullSize.CRUISER);
        check(preview.getFluxDissipation().getFlatMod()==45f && preview.getFluxCapacity().getFlatMod()==600f,"Officer keeps native bonuses without Peerless");
        captain[0]=player; playerStats.setSkillLevel("ordnance_expert",1f);
        preview.getFluxCapacity().unmodify(); preview.getFluxDissipation().unmodify();
        playerStats.applyPersonalToStats(preview,HullSize.CRUISER);
        check(preview.getFluxDissipation().getFlatMod()==45f && preview.getFluxCapacity().getFlatMod()==0f,"Respec removes Peerless and native Elite capacity");
        playerStats.setSkillLevel("ordnance_expert",2f);
        playerStats.applyPersonalToStats(preview,HullSize.CRUISER);
        peerless.combat.PeerlessCombatPlugin.staticStats(preview,true,false,playerStats);
        check(preview.getFluxCapacity().getFlatMod()==1000f,"Combat/campaign reuse the same modifier without duplication");
        variant.spent=0;
        playerStats.applyPersonalToStats(preview,HullSize.CRUISER);
        check(preview.getFluxCapacity().getFlatMod()==600f,"Zero-cost hullmods do not contribute OP");
        fixture.saved.clear(); variant.spent=40;
        playerStats.applyPersonalToStats(preview,HullSize.CRUISER);
        check(preview.getFluxCapacity().getFlatMod()==600f,"Untrained player has no Peerless bonus");
        System.out.println("PASS: native Ordnance refit skill dispatch, hullmod edits, no stacking, free hullmods and player/Elite gates; no saved variant mutation.");
    }
}
