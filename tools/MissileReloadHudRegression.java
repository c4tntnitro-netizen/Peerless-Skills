package peerless.skills;

import com.fs.starfarer.api.*;
import com.fs.starfarer.renderers.A.I;
import com.fs.starfarer.ui.interfacenew;
import org.lwjgl.opengl.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import javax.imageio.ImageIO;
import peerless.combat.MissileReloadHud;
import peerless.combat.PeerlessCombatPlugin;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.combat.K;
import com.fs.starfarer.renderers.A.G;
import com.fs.starfarer.combat.systems.o00O;
import static peerless.skills.PeerlessFixture.*;

/** Real native loading-bar widgets rendered into an OpenGL framebuffer, no screenshot mocks. */
public final class MissileReloadHudRegression {
    static void clear() { GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); }
    static int[] pixel(int x,int y) {
        ByteBuffer data=org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x,y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        return new int[]{data.get(0)&255,data.get(1)&255,data.get(2)&255};
    }
    static void green(int[] rgb,String message){check(rgb[1]>100 && rgb[1]>rgb[0]*1.5f && rgb[1]>rgb[2]*1.4f,message);}
    public static void main(String[] args)throws Exception {
        Global.setSettings(mock(SettingsAPI.class,(n,a)->n.equals("getFloat")||n.equals("getScreenScaleMult")?1f:n.equals("getColor")?Color.WHITE:null));
        org.json.JSONObject settings=new org.json.JSONObject(Files.readString(Path.of("../../starsector-core/data/config/settings.json")).replaceAll("(?m)#.*$",""));
        for(java.lang.reflect.Field field:com.fs.starfarer.settings.StarfarerSettings.class.getDeclaredFields()) {
            if(field.getType()==org.json.JSONObject.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);field.set(null,settings);
            }
        }
        Pbuffer buffer=new Pbuffer(256,96,new PixelFormat().withAlphaBits(8),null,null);
        try {
            buffer.makeCurrent();GL11.glViewport(0,0,256,96);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,256,0,96,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
            interfacenew row=new interfacenew(200,40);
            I original=new I(new Color(255,40,40));
            row.add(original).setSize(100,8).inBL(20,20);
            original.update(.8f);
            var position=original.getPosition();
            float x=position.getX(),y=position.getY();
            double[] progress={.5};
            var replacement=MissileReloadHud.replace(row,original,()->progress[0]);
            check(row.getChildrenCopy().size()==1 && row.getChildrenCopy().get(0)==replacement,"Only the original loading-bar slot is replaced");
            check(replacement.getPosition().getX()==position.getX() && replacement.getPosition().getY()==position.getY(),"Exact native anchored position retained");
            clear();replacement.render(1f);
            green(pixel((int)x+20,(int)y+3),"Reloaded half is green");
            int[] empty=pixel((int)x+75,(int)y+3);check(empty[0]+empty[1]+empty[2]==0,"Unfilled half remains empty");
            progress[0]=.9;clear();replacement.render(1f);
            green(pixel((int)x+75,(int)y+3),"Green fill follows actual progress");
            progress[0]=0;clear();replacement.render(1f);
            int[] reset=pixel((int)x+20,(int)y+3);check(reset[0]+reset[1]+reset[2]==0,"Progress resets after a missile is awarded");
            progress[0]=-1;clear();replacement.render(1f);
            int[] normal=pixel((int)x+20,(int)y+3);
            check(normal[0]>normal[1]*2 && normal[0]>100,"Inactive state delegates the untouched native bar and color");
            progress[0]=Double.NaN;clear();replacement.render(1f);
            check(pixel((int)x+20,(int)y+3)[0]>100,"Invalid progress safely retains ordinary native loading bar");
            progress[0]=.5;clear();replacement.render(0f);
            check(pixel((int)x+20,(int)y+3)[1]==0,"Native HUD fade alpha is honored");
            row.getPosition().setLocation(30,10);
            check(position.getX()==x+30 && position.getY()==y+10,"Bar moves with native row anchors");
            clear();replacement.render(1f);
            green(pixel((int)position.getX()+20,(int)position.getY()+3),"Green bar follows repositioned HUD");
            replacement.restore();replacement.restore();
            check(row.getChildrenCopy().size()==1 && row.getChildrenCopy().get(0)==original,"Restoration is idempotent and keeps one native bar");
            check(original.getPosition()==position,"Restoring native bar preserves its original anchor");
            clear();original.render(1f);
            check(pixel((int)position.getX()+20,(int)position.getY()+3)[0]>100,"Restored original still renders normally");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"Native bar replacement leaves no GL errors");
            // Save a real render with half reload progress for inspection.
            replacement=MissileReloadHud.replace(row,original,()->.5);
            clear();replacement.render(1f);
            ByteBuffer rgba=org.lwjgl.BufferUtils.createByteBuffer(256*96*4);
            GL11.glReadPixels(0,0,256,96,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba);
            BufferedImage image=new BufferedImage(256,96,BufferedImage.TYPE_INT_ARGB);
            for(int yy=0;yy<96;yy++)for(int xx=0;xx<256;xx++) {
                int p=(yy*256+xx)*4;
                image.setRGB(xx,95-yy,0xff000000|((rgba.get(p)&255)<<16)|((rgba.get(p+1)&255)<<8)|(rgba.get(p+2)&255));
            }
            Path output=Path.of("build/missile-reload-bar.png");Files.createDirectories(output.getParent());ImageIO.write(image,"png",output.toFile());
            replacement.restore();
            discovery();
            System.out.println("PASS: native green reload bar, fractional/reset progress, original fallback, HUD alpha/anchors, exact slot replacement and restoration.");
        }finally{buffer.destroy();}
    }
    @SuppressWarnings("unchecked")
    static void discovery()throws Exception {
        // Native HUD-row constructors also build font labels. This external harness initializes
        // their ordinary panel state directly so discovery can be tested without a full combat UI.
        java.lang.reflect.Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)singleton.get(null);
        G row=(G)unsafe.allocateInstance(G.class);
        interfacenew seed=new interfacenew(200,40);
        for(Class<?> type=interfacenew.class;type!=Object.class;type=type.getSuperclass())for(java.lang.reflect.Field field:type.getDeclaredFields()) {
            if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())) { field.setAccessible(true);field.set(row,field.get(seed)); }
        }
        var fixture=new PeerlessFixture();PeerlessRewardsRegression.load(fixture);
        peerless.api.PeerlessSkills.unlockAllForTesting();
        ShipAPI ship=mock(ShipAPI.class,(n,a)->n.equals("getCaptain")?fixture.player:n.equals("isAlive")?true:null);
        o00O weapon=mock(o00O.class,(n,a)->n.equals("getMaxAmmo")?28:n.equals("getAmmo")?0:null);
        for(java.lang.reflect.Field field:G.class.getDeclaredFields())if(field.getType()==o00O.class) { field.setAccessible(true);field.set(row,weapon); }
        I original=new I(Color.RED);row.add(original).setSize(20,8).inBL(0,0);
        var controller=new PeerlessCombatPlugin.Controller(ship);
        var empty=PeerlessCombatPlugin.Controller.class.getDeclaredField("emptied");empty.setAccessible(true);
        ((java.util.Set<WeaponAPI>)empty.get(controller)).add(weapon);
        var reload=PeerlessCombatPlugin.Controller.class.getDeclaredField("reload");reload.setAccessible(true);
        ((java.util.Map<WeaponAPI,Float>)reload.get(controller)).put(weapon,.5f);
        interfacenew root=new interfacenew(256,96);root.add(row);
        K nativeUI=mock(K.class,(n,a)->n.equals("getScreenPanel")?root:null);
        CombatEngineAPI engine=mock(CombatEngineAPI.class,(n,a)->n.equals("getCombatUI")?nativeUI:n.equals("getPlayerShip")?ship:null);
        var hud=new MissileReloadHud();hud.update(engine,controller);
        check(row.getChildrenCopy().size()==1 && row.getChildrenCopy().get(0) instanceof MissileReloadHud.Replacement,"Actual native weapon row is discovered and patched");
        Object replacement=row.getChildrenCopy().get(0);hud.update(engine,controller);
        check(row.getChildrenCopy().get(0)==replacement,"HUD scan does not stack replacements");
        hud.update(engine,null);
        check(row.getChildrenCopy().size()==1 && row.getChildrenCopy().get(0)==original,"Leaving player ship restores actual native row");
    }
}
