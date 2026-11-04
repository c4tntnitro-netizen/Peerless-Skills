package peerless.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import java.nio.file.*;
import peerless.api.PeerlessSkills;
import static peerless.skills.PeerlessFixture.*;

public final class PeerlessFieldModulationRegression {
    public static void main(String[] args) throws Exception {
        PeerlessFixture f = new PeerlessFixture();
        PeerlessRewardsRegression.load(f);
        PeerlessSkills.unlockAllForTesting();
        boolean[] overloaded = {false}, paused = {false}, alive = {true};
        int[] stops = {0}, vents = {0}, fluxWrites = {0};
        StringBuilder order = new StringBuilder();
        FluxTrackerAPI tracker = mock(FluxTrackerAPI.class, (n,a) -> {
            if (n.equals("isOverloaded")) return overloaded[0];
            if (n.equals("stopOverload")) { stops[0]++; overloaded[0] = false; order.append("stop "); }
            if (n.equals("ventFlux")) { vents[0]++; check(!overloaded[0], "Clear overload before vent"); order.append("vent "); }
            if (n.equals("setCurrFlux") || n.equals("setHardFlux") || n.equals("decreaseFlux")) fluxWrites[0]++;
            return null;
        });
        ShipAPI ship = mock(ShipAPI.class, (n,a) -> switch(n) {
            case "getCaptain" -> f.player;
            case "isAlive" -> alive[0];
            case "getFluxTracker" -> tracker;
            default -> null;
        });
        Global.setCombatEngine(mock(CombatEngineAPI.class, (n,a) -> n.equals("isPaused") ? paused[0] : null));
        var effect = new PeerlessFieldModulation.EmergencyVent(ship);
        effect.advance(1f);
        check(vents[0] == 0, "Ordinary flux does not trigger");
        overloaded[0] = true; paused[0] = true;
        effect.advance(1f);
        check(vents[0] == 0 && overloaded[0], "Pause does not consume use");
        paused[0] = false; f.skill = 1f;
        effect.advance(1f);
        check(vents[0] == 0, "Elite required");
        f.skill = 2f; alive[0] = false;
        effect.advance(1f);
        check(vents[0] == 0, "Dead ships excluded");
        alive[0] = true;
        effect.advance(1f);
        check(stops[0] == 1 && vents[0] == 1 && order.toString().equals("stop vent "), "First overload becomes forced vent");
        check(fluxWrites[0] == 0, "Conversion does not erase current or hard flux");
        overloaded[0] = true;
        effect.advance(1f);
        check(vents[0] == 1 && overloaded[0], "Second overload left intact");
        new PeerlessFieldModulation.EmergencyVent(ship).advance(1f);
        check(vents[0] == 2, "Fresh battle instance restores use");
        ShipAPI officer = mock(ShipAPI.class, (n,a) -> n.equals("isAlive") ? true : n.equals("getFluxTracker") ? tracker : null);
        overloaded[0] = true;
        new PeerlessFieldModulation.EmergencyVent(officer).advance(1f);
        check(vents[0] == 2, "Nonplayer captain excluded");
        String definition = Files.readString(Path.of("data/config/settings.json"));
        check(definition.contains("peerless.combat.PeerlessCombatPlugin"), "Registered independently of vanilla and SiC skill files");
        Global.setCombatEngine(null);
        System.out.println("PASS: forced vent, once per battle, flux preservation, pause, Elite, alive and player gates.");
    }
}
