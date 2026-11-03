package peerless.skills;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.*;
import java.lang.reflect.*;
import java.util.*;

class PeerlessFixture {
    interface Call { Object call(String name, Object[] args); }
    static <T> T mock(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a)-> {
            if(m.getName().equals("equals")) return p==a[0];
            if(m.getName().equals("hashCode")) return System.identityHashCode(p);
            Object result=call.call(m.getName(),a);
            if(result!=null)return result;
            if(m.getReturnType()==boolean.class)return false;
            if(m.getReturnType()==float.class)return 0f;
            if(m.getReturnType()==int.class)return 0;
            return null;
        }));
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    final Map<String,Object> saved=new HashMap<>();
    float skill=2f;
    final MemoryAPI memory=mock(MemoryAPI.class,(n,a)->{
        if(n.equals("getBoolean")) return Boolean.TRUE.equals(saved.get(a[0]));
        if(n.equals("get")||n.equals("getString")) return saved.get(a[0]);
        if(n.equals("set")) saved.put((String)a[0],a[1]);
        return null;
    });
    final MutableCharacterStatsAPI stats=mock(MutableCharacterStatsAPI.class,(n,a)->n.equals("getSkillLevel")?skill:null);
    final PersonAPI player=mock(PersonAPI.class,(n,a)->n.equals("getStats")?stats:null);
}
