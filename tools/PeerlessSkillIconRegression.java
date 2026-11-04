package peerless.skills;
import peerless.api.PeerlessSkills;
import com.fs.starfarer.campaign.CharacterStats;
import com.fs.starfarer.coreui.voidsuper;
import com.fs.starfarer.coreui.Z;


import com.fs.starfarer.ui.n;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import static peerless.skills.PeerlessFixture.*;

/** Exercises real GL textures/framebuffer, including a button cached before selection. */
public final class PeerlessSkillIconRegression {
    private static final int SIZE = 64;
    private static final String ORIGINAL = "graphics/icons/skills/helmsmanship.png";

    private static final Map<Integer, com.fs.starfarer.ui.ooO0> cachedButtons = new HashMap<>();

    private static int texture(Path path) throws Exception {
        String key = path.toString();
        // Native loader/cache and the same native icon component used by the
        // character screen. Reflection stays in this external harness only.
        // The file loader used in-game generates mipmaps. The BufferedImage
        // overload omits them and previously hid the live-render regression.
        com.fs.graphics.oOoO.class.getMethod("super", String.class, String.class)
                .invoke(null, key, path.toAbsolutePath().toString());
        com.fs.starfarer.ui.ooO0 button = new com.fs.starfarer.ui.ooO0(key);
        button.setSize(SIZE, SIZE);
        button.setShowBorder(false);
        button.setWithOutline(false);
        button.render(1f);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        check(GL11.glIsTexture(texture), "Native icon binds its cached texture");
        cachedButtons.put(texture, button);
        return texture;
    }

    private static byte[] pixels(int texture) {
        return pixels(texture,0);
    }

    private static byte[] pixels(int texture, int level) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            int size=SIZE>>level;
            ByteBuffer pixels = BufferUtils.createByteBuffer(size*size*4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,level,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            byte[] result = new byte[pixels.remaining()];pixels.get(result);return result;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);
            GL11.glPopClientAttrib();
        }
    }

    private static byte[][] mipPixels(int texture) {
        byte[][] levels=new byte[7][];
        for(int level=0;level<levels.length;level++)levels[level]=pixels(texture,level);
        return levels;
    }

    private static void checkMipPixels(int texture, byte[][] expected, String message) {
        for(int level=0;level<expected.length;level++) {
            check(Arrays.equals(pixels(texture,level),expected[level]),message+" at mip level "+level);
        }
    }

    private static void drawCachedIcon(int texture, int left) {
        com.fs.starfarer.ui.ooO0 button = cachedButtons.get(texture);
        button.getPosition().setLocation(left, 0);
        button.render(1f);
    }

    private static void checkState(int binding) {
        check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==binding,"Texture binding preserved");
        check(GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH)==77,"Pack stride preserved");
        check(GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)==81,"Unpack stride preserved");
    }

    private static void replace(int id, int w, int h, byte[] data) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        ByteBuffer bytes=BufferUtils.createByteBuffer(w*h*4);
        bytes.put(data,0,w*h*4).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
    }

    private static void hardeningCases(Pbuffer buffer, CoreUITabId[] tab,
            int original, int gold, byte[] nativePixels, byte[] goldPixels) throws Exception {
        SettingsAPI baseSettings=Global.getSettings();SectorAPI baseSector=Global.getSector();
        tab[0]=CoreUITabId.CHARACTER;
        PeerlessSkillIcon hook=new PeerlessSkillIcon();

        // These bindings made the old CPU-buffer calls throw. Keep them bound
        // across both callbacks to check the real driver, not a GL mock.
        int pack=GL15.glGenBuffers(),unpack=GL15.glGenBuffers();
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);
        GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER,32,GL15.GL_STREAM_READ);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
        GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER,32,GL15.GL_STREAM_DRAW);
        GL11.glPixelTransferf(GL11.GL_RED_SCALE,0.25f);
        GL11.glPixelTransferf(GL11.GL_GREEN_BIAS,0.125f);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,2);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,3);
        int serverDepth=GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
        int clientDepth=GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH);
        hook.renderInUICoordsBelowUI(null);
        hook.renderInUICoordsAboveUIAndTooltips(null);
        check(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)==pack,"Pack PBO binding restored");
        check(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)==unpack,"Unpack PBO binding restored");
        check(GL11.glGetFloat(GL11.GL_RED_SCALE)==0.25f&&GL11.glGetFloat(GL11.GL_GREEN_BIAS)==0.125f,"Color-transfer state restored");
        check(GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS)==2&&GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)==3,"Pixel skip state restored");
        check(GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)==serverDepth
                &&GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH)==clientDepth,"Balanced GL state stacks");
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
        GL15.glDeleteBuffers(pack);GL15.glDeleteBuffers(unpack);
        GL11.glPixelTransferf(GL11.GL_RED_SCALE,1f);GL11.glPixelTransferf(GL11.GL_GREEN_BIAS,0f);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
        check(Arrays.equals(pixels(original),nativePixels),"Hostile transfer state cannot corrupt the original bitmap");
        hook.renderInUICoordsBelowUI(null);
        check(Arrays.equals(pixels(original),goldPixels),"PBO/transfer handling remains functional");
        hook.renderInUICoordsAboveUIAndTooltips(null);

        buffer.releaseContext();
        new PeerlessSkillIcon().renderInUICoordsBelowUI(null);
        hook.renderInUICoordsAboveUIAndTooltips(null);
        buffer.makeCurrent();
        hook.renderInUICoordsBelowUI(null);
        buffer.releaseContext();hook.renderInUICoordsAboveUIAndTooltips(null);
        buffer.makeCurrent();hook.renderInUICoordsAboveUIAndTooltips(null);
        check(Arrays.equals(pixels(original),nativePixels),"Interrupted context resumes cleanup without throwing");

        Pbuffer other=new Pbuffer(SIZE,SIZE,new PixelFormat().withAlphaBits(8),null,null);
        byte[] foreign=new byte[SIZE*SIZE*4];Arrays.fill(foreign,(byte)93);
        try {
            buffer.makeCurrent();hook.renderInUICoordsBelowUI(null);
            other.makeCurrent();replace(original,SIZE,SIZE,foreign);
            hook.renderInUICoordsAboveUIAndTooltips(null);
            check(Arrays.equals(pixels(original),foreign),"Same numeric texture ID in another context is untouched");
            buffer.makeCurrent();hook.renderInUICoordsAboveUIAndTooltips(null);
            check(Arrays.equals(pixels(original),nativePixels),"Original-context snapshot survives a temporary context switch");
        } finally {other.destroy();buffer.makeCurrent();}

        hook.renderInUICoordsBelowUI(null);replace(original,SIZE,SIZE,foreign);
        hook.renderInUICoordsAboveUIAndTooltips(null);
        check(Arrays.equals(pixels(original),foreign),"A later renderer's replacement wins over stale restoration");
        replace(original,SIZE,SIZE,nativePixels);
        hook.renderInUICoordsBelowUI(null);replace(original,32,32,foreign);
        hook.renderInUICoordsAboveUIAndTooltips(null);
        check(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==32,"Resized texture is not overwritten");
        hook.renderInUICoordsBelowUI(null);
        check(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==32,"Incompatible size is rejected before transfer");
        replace(original,SIZE,SIZE,nativePixels);
        int autoMipmaps=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_GENERATE_MIPMAP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_GENERATE_MIPMAP,GL11.GL_FALSE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
        hook.renderInUICoordsBelowUI(null);
        check(Arrays.equals(pixels(original),nativePixels),"Manually maintained mipmaps fall back to native art");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,original);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_GENERATE_MIPMAP,autoMipmaps);

        // A changed gold image at the same ID must not reuse stale cached pixels.
        replace(gold,SIZE,SIZE,foreign);hook.renderInUICoordsBelowUI(null);
        check(Arrays.equals(pixels(original),foreign),"Gold pixels refreshed after resource replacement");
        hook.renderInUICoordsAboveUIAndTooltips(null);replace(gold,SIZE,SIZE,goldPixels);

        hook.renderInUICoordsBelowUI(null);
        int max=GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH);
        for(int i=serverDepth;i<max;i++)GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        hook.renderInUICoordsAboveUIAndTooltips(null);
        check(GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)==max,"Full stack is neither pushed nor popped by failed cleanup");
        for(int i=serverDepth;i<max;i++)GL11.glPopAttrib();
        hook.renderInUICoordsAboveUIAndTooltips(null);
        check(Arrays.equals(pixels(original),nativePixels),"Failed cleanup retains the backup and retries safely");
        hook=new PeerlessSkillIcon();hook.renderInUICoordsBelowUI(null);
        GL11.glDeleteTextures(original);hook.renderInUICoordsAboveUIAndTooltips(null);
        check(!GL11.glIsTexture(original),"Cleanup never recreates a deleted texture");
        hook.renderInUICoordsBelowUI(null);
        check(!GL11.glIsTexture(original),"Missing texture is a harmless fallback");
        replace(original,SIZE,SIZE,nativePixels);

        Global.setSettings(null);new PeerlessSkillIcon().renderInUICoordsBelowUI(null);
        Global.setSettings(mock(SettingsAPI.class,(n,a)->null));new PeerlessSkillIcon().renderInUICoordsBelowUI(null);
        int[] attempts={0};
        Global.setSettings(mock(SettingsAPI.class,(n,a)-> {
            if(n.equals("getSkillSpec")){attempts[0]++;throw new IllegalStateException("Expected fixture lookup failure");}
            return null;
        }));
        hook=new PeerlessSkillIcon();hook.renderInUICoordsBelowUI(null);hook.renderInUICoordsBelowUI(null);
        check(attempts[0]==1,"Failed hook disables itself without repeated lookup failures");
        check(Arrays.equals(pixels(original),nativePixels),"Lookup failures retain native pixels");
        Global.setSettings(baseSettings);

        List<Object> listeners=new ArrayList<>();
        ListenerManagerAPI manager=mock(ListenerManagerAPI.class,(n,a)-> {
            if(n.equals("addListener")){check(a.length==2&&Boolean.TRUE.equals(a[1]),"Transient listener");listeners.add(a[0]);}
            if(n.equals("removeListenerOfClass"))listeners.removeIf(v->((Class<?>)a[0]).isInstance(v));
            return null;
        });
        Global.setSector(mock(SectorAPI.class,(n,a)-> {
            if(n.equals("getListenerManager"))return manager;
            if(n.equals("getPlayerStats"))return baseSector.getPlayerStats();
            if(n.equals("getMemoryWithoutUpdate"))return baseSector.getMemoryWithoutUpdate();
            if(n.equals("getCampaignUI"))return baseSector.getCampaignUI();
            return null;
        }));
        PeerlessSkillIcon.install();check(listeners.size()==1,"One installed hook");
        PeerlessSkillIcon first=(PeerlessSkillIcon)listeners.get(0);
        first.renderInUICoordsBelowUI(null);
        PeerlessSkillIcon.install();
        check(listeners.size()==1&&listeners.get(0)!=first,"Repeated load replaces exactly one hook");
        check(Arrays.equals(pixels(original),nativePixels),"Reload finishes the old hook's interrupted draw");
        ((PeerlessSkillIcon)listeners.get(0)).renderInUICoordsBelowUI(null);
        Global.setSettings(mock(SettingsAPI.class,(n,a)-> {
            if(n.equals("loadTexture"))throw new IllegalStateException("Expected fixture asset failure");return null;
        }));
        PeerlessSkillIcon.install();
        check(listeners.isEmpty()&&Arrays.equals(pixels(original),nativePixels),"Failed asset load leaves restored native art and no active hook");
        Global.setSettings(baseSettings);Global.setSector(baseSector);
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"Hardening cases produce no GL errors");
    }

    private static void allSkills(org.json.JSONArray catalog, boolean sic) throws Exception {
        Map<String,Integer> textures=new HashMap<>();
        Map<String,SkillSpecAPI> specs=new HashMap<>();
        Map<String,byte[][]> originals=new HashMap<>(), upgrades=new HashMap<>();
        for (int i=0;i<catalog.length();i++) {
            org.json.JSONObject asset=catalog.getJSONObject(i);
            String id=asset.getString("id"), source=asset.getString("source"), runtime=asset.getString("runtime");
            check(runtime.equals(PeerlessSkillIcon.spriteFor(id)),"Every skill maps to its authored bitmap: "+id);
            // Native file-loader/cache paths match the actual bitmap resources.
            int original=texture(Path.of("../../starsector-core",source).toAbsolutePath()), gold=texture(Path.of(runtime).toAbsolutePath());
            textures.put(source,original);textures.put(runtime,gold);
            originals.put(id,mipPixels(original));upgrades.put(id,mipPixels(gold));
            specs.put(id,mock(SkillSpecAPI.class,(n,a)->n.equals("getSpriteName")?source:null));
        }
        PeerlessFixture f=new PeerlessFixture();
        CoreUITabId[] tab={CoreUITabId.CHARACTER};List<Object> listeners=new ArrayList<>();
        ListenerManagerAPI manager=mock(ListenerManagerAPI.class,(n,a)->{
            if(n.equals("addListener"))listeners.add(a[0]);
            if(n.equals("removeListenerOfClass"))listeners.removeIf(((Class<?>)a[0])::isInstance);
            return null;
        });
        Global.setSector(mock(SectorAPI.class,(n,a)->switch(n){
            case "getMemoryWithoutUpdate" -> f.memory;case "getPlayerStats" -> f.stats;case "getPlayerPerson" -> f.player;
            case "getListenerManager" -> manager;
            case "getCampaignUI" -> mock(CampaignUIAPI.class,(nn,aa)->nn.equals("getCurrentCoreTab")?tab[0]:null);
            default -> null;
        }));
        Global.setSettings(mock(SettingsAPI.class,(n,a)->{
            if(n.equals("getModManager"))return mock(com.fs.starfarer.api.ModManagerAPI.class,(nn,aa)->nn.equals("isModEnabled") && "second_in_command".equals(aa[0]) ? sic : null);
            if(n.equals("getSkillSpec"))return specs.get(a[0]);
            if(n.equals("getSprite"))return mock(SpriteAPI.class,(nn,aa)->nn.equals("getTextureId")?textures.get(a[0]):null);
            return null;
        }));
        PeerlessSkills.unlockAllForTesting();PeerlessSkillIcon.install();
        check(listeners.size()==1,"Fourteen swaps use one UI listener");
        PeerlessSkillIcon hook=(PeerlessSkillIcon)listeners.get(0);
        hook.renderInUICoordsBelowUI(null);
        for(int i=0;i<catalog.length();i++) {
            var asset=catalog.getJSONObject(i);String id=asset.getString("id");
            if(sic && asset.getString("metal").equals("bronze")) {
                int original=textures.get(asset.getString("source"));
                byte[] bronze=upgrades.get(id)[0], tinted=pixels(original);
                int greenGain=0;
                for(int p=0;p<tinted.length;p+=4) {
                    check(tinted[p]==bronze[p] && tinted[p+2]==bronze[p+2] && tinted[p+3]==bronze[p+3],"SiC gold tint preserves red, blue, alpha and icon details: "+id);
                    greenGain+=(tinted[p+1]&255)-(bronze[p+1]&255);
                }
                check(greenGain>0,"SiC industry icon shifts from orange bronze toward yellow gold: "+id);
                upgrades.put(id,mipPixels(original));
            }
            checkMipPixels(textures.get(asset.getString("source")),upgrades.get(id),"All fourteen visible together: "+id);
        }
        if(sic) {
            GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            int slot=0;
            for(int i=0;i<catalog.length();i++) {
                var asset=catalog.getJSONObject(i);
                if(!asset.getString("metal").equals("bronze"))continue;
                drawCachedIcon(textures.get(asset.getString("runtime")),SIZE*slot++);
                drawCachedIcon(textures.get(asset.getString("source")),SIZE*slot++);
            }
            ByteBuffer frame=BufferUtils.createByteBuffer(SIZE*4*SIZE*4);
            GL11.glReadPixels(0,0,SIZE*4,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,frame);
            BufferedImage preview=new BufferedImage(SIZE*4,SIZE,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE*4;x++) {
                int p=(y*SIZE*4+x)*4;
                preview.setRGB(x,SIZE-1-y,0xff000000|((frame.get(p)&255)<<16)|((frame.get(p+1)&255)<<8)|(frame.get(p+2)&255));
            }
            ImageIO.write(preview,"png",Path.of("build/sic-industry-icons.png").toFile());
        }
        hook.renderInUICoordsAboveUIAndTooltips(null);
        for(int i=0;i<catalog.length();i++) {
            var asset=catalog.getJSONObject(i);String id=asset.getString("id");
            checkMipPixels(textures.get(asset.getString("source")),originals.get(id),"All fourteen restored: "+id);
        }
        f.levels.put("combat_endurance",1f);
        f.saved.remove("$peerless_skills_trained_v1_impact_mitigation");
        hook.renderInUICoordsBelowUI(null);
        for(int i=0;i<catalog.length();i++) {
            var asset=catalog.getJSONObject(i);String id=asset.getString("id");
            boolean nativeArt=id.equals("combat_endurance")||id.equals("impact_mitigation");
            checkMipPixels(textures.get(asset.getString("source")),nativeArt?originals.get(id):upgrades.get(id),"Independent eligibility: "+id);
        }
        PeerlessSkillIcon.install();
        check(listeners.size()==1,"Reload still has exactly one listener");
        for(int i=0;i<catalog.length();i++) {
            var asset=catalog.getJSONObject(i);String id=asset.getString("id");
            checkMipPixels(textures.get(asset.getString("source")),originals.get(id),"Reload restores every interrupted swap: "+id);
        }
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"Fourteen simultaneous swaps leave no GL errors");
        System.out.println("PASS: all fourteen cached icons, "+(sic?"SiC industry gold":"vanilla industry bronze")+", per-skill gates, mip restoration and interrupted reload cleanup.");
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of("out_peerless_20261101");
        Files.createDirectories(output);
        SettingsAPI oldSettings=Global.getSettings();SectorAPI oldSector=Global.getSector();
        Pbuffer buffer=new Pbuffer(SIZE*4,SIZE,new PixelFormat().withAlphaBits(8),null,null);
        try {
            buffer.makeCurrent();
            // Register native absolute/current-directory resource lookup as
            // normal game startup does; this is an external test process.
            for (java.lang.reflect.Method method : com.fs.util.C.class.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount()==0 && method.getReturnType()==com.fs.util.C.class) {
                    Object resources=method.invoke(null);
                    com.fs.util.C.class.getMethod("class").invoke(resources);
                }
            }
            org.json.JSONObject settings = new org.json.JSONObject(Files.readString(
                    Path.of("../../starsector-core/data/config/settings.json")).replaceAll("(?m)#.*$", ""));
            for (java.lang.reflect.Field field : com.fs.starfarer.settings.StarfarerSettings.class.getDeclaredFields()) {
                if (field.getType()==org.json.JSONObject.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);field.set(null,settings);
                }
            }
            GL11.glViewport(0,0,SIZE*4,SIZE);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,SIZE*4,0,SIZE,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
            org.json.JSONArray catalog = new org.json.JSONObject(Files.readString(
                    Path.of("docs/PEERLESS_SKILL_ICONS.json"))).getJSONArray("assets");
            check(catalog.length()==14,"Complete current Elite icon roster");
            for (int i=0;i<catalog.length();i++) {
                Path icon=Path.of(catalog.getJSONObject(i).getString("runtime"));
                BufferedImage bitmap=ImageIO.read(icon.toFile());
                check(bitmap.getWidth()==SIZE&&bitmap.getHeight()==SIZE,"Runtime icon size: "+icon);
                texture(icon);
                check(GL11.glGetError()==GL11.GL_NO_ERROR,"Native texture loader renders: "+icon);
            }
            // Run the simultaneous-icon case before destructive texture lifecycle cases.
            allSkills(catalog,false);
            allSkills(catalog,true);

            Global.setSettings(oldSettings);Global.setSector(oldSector);
            int original=texture(Path.of("../../starsector-core",ORIGINAL));
            int gold=texture(Path.of(PeerlessSkillIcon.SPRITE));
            System.out.println("Native file-loaded icon: min filter="
                    +GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)
                    +", automatic mipmaps="+GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_GENERATE_MIPMAP));
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_LINEAR_MIPMAP_LINEAR
                    &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_GENERATE_MIPMAP)==GL11.GL_TRUE,
                    "Exercise native automatic-mipmap texture loading");
            int cachedButtonTexture=original;
            byte[] nativePixels=pixels(original), goldPixels=pixels(gold);
            byte[][] nativeMips=mipPixels(original), goldMips=mipPixels(gold);
            check(!Arrays.equals(nativePixels,goldPixels),"Purpose-built icon is different from native art");
            PeerlessFixture f=new PeerlessFixture();
            f.skill=2;
            f.saved.put(PeerlessSkills.TRAINED,Skills.HELMSMANSHIP);
            Map<String,Object> saved=new HashMap<>(f.saved);
            CoreUITabId[] tab={CoreUITabId.CHARACTER};
            SkillSpecAPI spec=mock(SkillSpecAPI.class,(n,a)-> {
                if(n.equals("setSpriteName"))throw new AssertionError("Shared spec must stay untouched");
                return n.equals("getSpriteName")?ORIGINAL:null;
            });
            CampaignUIAPI ui=mock(CampaignUIAPI.class,(n,a)-> {
                if(n.equals("showCoreUITab"))throw new AssertionError("No UI rebuild");
                return n.equals("getCurrentCoreTab")?tab[0]:null;
            });
            Global.setSettings(mock(SettingsAPI.class,(n,a)-> {
                if(n.equals("getSkillSpec"))return spec;
                if(n.equals("getSprite"))return mock(SpriteAPI.class,(sn,sa)->sn.equals("getTextureId")
                        ?(PeerlessSkillIcon.SPRITE.equals(a[0])?gold:original):null);
                return null;
            }));
            Global.setSector(mock(SectorAPI.class,(n,a)-> {
                if(n.equals("getPlayerStats"))return f.stats;
                if(n.equals("getPlayerPerson"))return f.player;
                if(n.equals("getMemoryWithoutUpdate"))return f.memory;
                if(n.equals("getCampaignUI"))return ui;
                return null;
            }));
            PeerlessSkillIcon hook=new PeerlessSkillIcon();
            GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            drawCachedIcon(cachedButtonTexture,0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,gold);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,77);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,81);
            hook.renderInUICoordsBelowUI(null);checkState(gold);
            check(Arrays.equals(pixels(cachedButtonTexture),goldPixels),"Existing cached icon now contains exact gold bitmap");
            checkMipPixels(original,goldMips,"Gold supplies the complete native mip chain");
            drawCachedIcon(cachedButtonTexture,SIZE);
            hook.renderInUICoordsAboveUIBelowTooltips(null);
            check(Arrays.equals(pixels(original),goldPixels),"Gold retained through tooltip rendering");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,gold);
            hook.renderInUICoordsAboveUIAndTooltips(null);checkState(gold);
            check(Arrays.equals(pixels(original),nativePixels),"Exact RGBA restored after character UI draw");
            checkMipPixels(original,nativeMips,"Exact original mip chain restored");
            drawCachedIcon(cachedButtonTexture,SIZE*2);
            drawCachedIcon(gold,SIZE*3);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);
            ByteBuffer frame=BufferUtils.createByteBuffer(SIZE*SIZE*4*4);
            GL11.glReadPixels(0,0,SIZE*4,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,frame);
            BufferedImage preview=new BufferedImage(SIZE*4,SIZE,BufferedImage.TYPE_INT_ARGB);
            int changed=0;
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE*4;x++) {
                int offset=(y*SIZE*4+x)*4;
                int argb=((frame.get(offset+3)&255)<<24)|((frame.get(offset)&255)<<16)
                        |((frame.get(offset+1)&255)<<8)|(frame.get(offset+2)&255);
                preview.setRGB(x,SIZE-1-y,argb);
                if(x<SIZE*2)for(int c=0;c<4;c++)check(frame.get(offset+c)==frame.get(offset+SIZE*2*4+c),
                        "Native cached button matches directly rendered native/gold control");
                if(x<SIZE && frame.get(offset)!=frame.get(offset+SIZE*4))changed++;
            }
            check(changed>SIZE,"Framebuffer visibly changes during selected player's render pass");
            ImageIO.write(preview,"png",output.resolve("peerless_cached_icon_render.png").toFile());
            for(int i=0;i<100;i++) {
                hook.renderInUICoordsBelowUI(null);hook.renderInUICoordsAboveUIAndTooltips(null);
                checkMipPixels(original,nativeMips,"Repeated frame restores every source level");
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,original);
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_LINEAR_MIPMAP_LINEAR
                    &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_GENERATE_MIPMAP)==GL11.GL_TRUE,
                    "Native filtering and mipmap parameters are preserved");
            for(CoreUITabId other:CoreUITabId.values())if(other!=CoreUITabId.CHARACTER) {
                tab[0]=other;hook.renderInUICoordsBelowUI(null);
                check(Arrays.equals(pixels(original),nativePixels),"Other tabs/officers retain native art: "+other);
            }
            tab[0]=null;hook.renderInUICoordsBelowUI(null);
            check(Arrays.equals(pixels(original),nativePixels),"Closed core UI retains native art");
            tab[0]=CoreUITabId.CHARACTER;f.skill=1;hook.renderInUICoordsBelowUI(null);
            check(Arrays.equals(pixels(original),nativePixels),"Requires active Elite");
            f.skill=2;f.saved.remove(PeerlessSkills.TRAINED);hook.renderInUICoordsBelowUI(null);
            check(Arrays.equals(pixels(original),nativePixels),"Unspent point retains native art");
            f.saved.put(PeerlessSkills.TRAINED,Skills.HELMSMANSHIP);
            hook.renderInUICoordsBelowUI(null);tab[0]=CoreUITabId.FLEET;hook.renderInUICoordsBelowUI(null);
            check(Arrays.equals(pixels(original),nativePixels),"Interrupted frame restores before changing tab");
            check(f.saved.equals(saved),"Progression untouched");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"No OpenGL errors");
            hardeningCases(buffer,tab,original,gold,nativePixels,goldPixels);
            System.out.println("PASS: actual cached-texture framebuffer gold swap, exact restoration, GL state, 100 frames, tab/Elite/selection gates and progression preservation.");
            System.out.println("PASS: PBO/color transfer state, absent/switched contexts, foreign/resized/deleted/mipmapped textures, resource refresh, cleanup retry, missing assets and repeated install.");
            System.out.println("PASS: native file loading for all 14 icons; gold and exact restoration across all seven mip levels, with original filtering preserved.");
        } finally {
            Global.setSettings(oldSettings);Global.setSector(oldSector);buffer.destroy();
        }
    }
}
