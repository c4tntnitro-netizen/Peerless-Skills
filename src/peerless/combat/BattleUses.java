package peerless.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.*;

/** Combat-engine-local receipts survive withdrawal/redeployment but never cross battles. */
public final class BattleUses {
    private BattleUses() {}
    @SuppressWarnings("unchecked")
    public static boolean claim(ShipAPI ship, String effect) {
        if (Global.getCombatEngine()==null || Global.getCombatEngine().getCustomData()==null) return true;
        String key="peerless_battle_use_"+effect;
        Map<String,Object> data=Global.getCombatEngine().getCustomData();
        Set<Object> used=(Set<Object>)data.computeIfAbsent(key,k->new HashSet<>());
        Object identity=ship.getFleetMember()==null?ship:ship.getFleetMember().getId();
        return used.add(identity);
    }
}
