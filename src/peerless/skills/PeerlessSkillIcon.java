package peerless.skills;

import peerless.api.PeerlessSkills;
import peerless.api.SkillCatalog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBBufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GLContext;

/** Replaces the cached icon's pixels for the player character UI render pass. */
public final class PeerlessSkillIcon implements CampaignUIRenderingListener {
    public static final String SPRITE = "graphics/icons/skills/peerless_helmsmanship_gold_v1.png";
    private static final int SIZE = 64;
    private static final int BYTES = SIZE * SIZE * 4;
    private static PeerlessSkillIcon installed;
    private final String skillId;
    private final String sprite;
    private final boolean industryGold;
    private final List<PeerlessSkillIcon> additional = new ArrayList<>();

    public PeerlessSkillIcon() { this(Skills.HELMSMANSHIP, SPRITE); }
    private PeerlessSkillIcon(String skillId, String sprite) {
        this.skillId=skillId; this.sprite=sprite;
        // SiC places both industry skills in its combat roster; match the other gold upgrades.
        industryGold=("ordnance_expert".equals(skillId) || "polarized_armor".equals(skillId))
                && Global.getSettings()!=null && Global.getSettings().getModManager()!=null
                && Global.getSettings().getModManager().isModEnabled("second_in_command");
    }
    public static String spriteFor(String skillId) {
        String metal = "ordnance_expert".equals(skillId) || "polarized_armor".equals(skillId) ? "bronze" : "gold";
        return "graphics/icons/skills/peerless_"+skillId+"_"+metal+"_v1.png";
    }

    private ByteBuffer originalPixels;
    private ByteBuffer goldPixels;
    private ByteBuffer scratch;
    private ContextCapabilities swapContext;
    private int originalTexture;
    private boolean pendingRestore;
    private boolean verifiedSwap;
    private boolean failed;
    private boolean warned;

    public static void install() {
        if (Global.getSector() == null) return;
        // Textures outlive campaign listeners. Finish any interrupted draw
        // before replacing the hook, including when loading a different save.
        if (installed != null) {
            installed.failed = true;
            if (!installed.restoreAll()) return;
        }
        Global.getSector().getListenerManager().removeListenerOfClass(PeerlessSkillIcon.class);
        Global.getSector().removeTransientScriptsOfClass(PeerlessSkillIcon.class);
        installed = null;
        try {
            PeerlessSkillIcon hook = new PeerlessSkillIcon();
            for (String id : SkillCatalog.ids()) {
                String asset = spriteFor(id);
                Global.getSettings().loadTexture(asset);
                if (!Skills.HELMSMANSHIP.equals(id)) hook.additional.add(new PeerlessSkillIcon(id,asset));
            }
            Global.getSector().getListenerManager().addListener(hook, true);
            installed = hook;
        } catch (java.io.IOException | RuntimeException ex) {
            Global.getLogger(PeerlessSkillIcon.class).warn("Unable to load Peerless skill icon", ex);
        }
    }

    private boolean eligible() {
        if (Global.getSector() == null || !PeerlessSkills.isTrained(skillId)
                || Global.getSector().getPlayerStats() == null
                || Global.getSector().getPlayerStats().getSkillLevel(skillId) < 2f) return false;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        return ui != null && ui.getCurrentCoreTab() == CoreUITabId.CHARACTER;
    }

    private static ContextCapabilities currentContext() {
        try {
            return GLContext.getCapabilities();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        if (!restoreAll()) return;
        renderOne();
        for (PeerlessSkillIcon icon : additional) icon.renderOne();
    }

    private void renderOne() {
        if (failed) return;
        try {
            if (!eligible()) return;
            ContextCapabilities context = currentContext();
            SettingsAPI settings = Global.getSettings();
            if (context == null || settings == null) return;
            SkillSpecAPI skill = settings.getSkillSpec(skillId);
            if (skill == null || skill.getSpriteName() == null) return;
            SpriteAPI original = settings.getSprite(skill.getSpriteName());
            SpriteAPI gold = settings.getSprite(sprite);
            if (original == null || gold == null) return;
            int originalId = original.getTextureId();
            int goldId = gold.getTextureId();
            if (originalId == goldId) return;
            try (PixelState state = new PixelState(context)) {
                if (!compatible(originalId) || !compatible(goldId)) return;
                if (originalPixels == null) {
                    // Fixed bounds and reusable buffers: no allocation from a
                    // foreign texture's size, and no per-frame direct buffers.
                    originalPixels = BufferUtils.createByteBuffer(BYTES);
                    goldPixels = BufferUtils.createByteBuffer(BYTES);
                    scratch = BufferUtils.createByteBuffer(BYTES);
                }
                read(originalId, originalPixels);
                read(goldId, goldPixels);
                if (industryGold) tintGold(goldPixels);
                originalTexture = originalId;
                swapContext = context;
                verifiedSwap = false;
                pendingRestore = true;
                // Keep the native button's cached texture ID. Both snapshots
                // are complete before this first write to the shared texture.
                write(originalTexture, goldPixels);
                read(originalTexture, scratch);
                if (!scratch.equals(goldPixels)) throw new IllegalStateException("Peerless icon upload did not complete");
                verifiedSwap = true;
            }
        } catch (RuntimeException ex) {
            failed = true;
            warn(ex);
            restoreSafely();
        }
    }

    /** Shift warm bronze pixels to gold, preserving brightness, detail and alpha. */
    private static void tintGold(ByteBuffer pixels) {
        for (int i=0;i<BYTES;i+=4) {
            int red=pixels.get(i)&255, green=pixels.get(i+1)&255, blue=pixels.get(i+2)&255;
            if (red>green && green>=blue) pixels.put(i+1,(byte)(blue+(red-blue)*3/4));
        }
    }

    /** Only bounded, uncompressed icons whose sampled levels follow base-level writes. */
    private static boolean compatible(int texture) {
        if (texture <= 0 || !GL11.glIsTexture(texture)) return false;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != SIZE
                || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != SIZE
                || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_BORDER) != 0) return false;
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        if (format != GL11.GL_RGBA8 && format != GL11.GL_RGB8
                && format != GL11.GL_RGBA && format != GL11.GL_RGB && format != 3 && format != 4) return false;
        int filter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        if (filter == GL11.GL_LINEAR || filter == GL11.GL_NEAREST) return true;
        boolean mipmapped = filter == GL11.GL_NEAREST_MIPMAP_NEAREST
                || filter == GL11.GL_LINEAR_MIPMAP_NEAREST
                || filter == GL11.GL_NEAREST_MIPMAP_LINEAR
                || filter == GL11.GL_LINEAR_MIPMAP_LINEAR;
        // Native file-loaded icons keep automatic mipmap generation enabled.
        // Both the gold upload and restoration regenerate their lower levels;
        // manually maintained chains remain unsupported and untouched.
        return mipmapped && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,
                GL14.GL_GENERATE_MIPMAP) == GL11.GL_TRUE;
    }

    private static void read(int texture, ByteBuffer pixels) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        pixels.clear();
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        pixels.rewind();
    }

    private static void write(int texture, ByteBuffer pixels) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        pixels.rewind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }

    /** A failed cleanup remains pending for the next callback; it never escapes into the game. */
    private boolean restoreSafely() {
        if (!pendingRestore) return true;
        ContextCapabilities context = currentContext();
        if (context == null) return false;
        if (context != swapContext) {
            // Never use an old texture ID in another context. Keep the backup
            // available if the original context resumes after a temporary switch.
            return false;
        }
        try (PixelState state = new PixelState(context)) {
            if (!compatible(originalTexture)) {
                pendingRestore = false;
                return true;
            }
            read(originalTexture, scratch);
            if (verifiedSwap && !scratch.equals(goldPixels)) {
                // Another renderer replaced this texture since our draw.
                // Its pixels take precedence over our stale backup.
                pendingRestore = false;
                return true;
            }
            write(originalTexture, originalPixels);
            read(originalTexture, scratch);
            if (!scratch.equals(originalPixels)) throw new IllegalStateException("Peerless icon restoration did not complete");
            pendingRestore = false;
            return true;
        } catch (RuntimeException ex) {
            failed = true;
            warn(ex);
            return false;
        }
    }

    private boolean restoreAll() {
        boolean restored = restoreSafely();
        for (PeerlessSkillIcon icon : additional) restored = icon.restoreSafely() && restored;
        return restored;
    }

    private void warn(RuntimeException ex) {
        if (warned) return;
        warned = true;
        Global.getLogger(PeerlessSkillIcon.class).warn("Peerless icon rendering disabled after a graphics error", ex);
    }

    /** Isolates CPU pixel transfers from PBO bindings and color-transfer state left by other renderers. */
    private static final class PixelState implements AutoCloseable {
        private final ContextCapabilities context;
        private final int texture;
        private final boolean buffers;
        private final int pack;
        private final int unpack;

        PixelState(ContextCapabilities context) {
            this.context = context;
            if (GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH) >= GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH)
                    || GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH) >= GL11.glGetInteger(GL11.GL_MAX_CLIENT_ATTRIB_STACK_DEPTH)) {
                throw new IllegalStateException("No OpenGL state-stack space for Peerless icon rendering");
            }
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            buffers = context.OpenGL21 || context.GL_ARB_pixel_buffer_object;
            pack = buffers ? GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING) : 0;
            unpack = buffers ? GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) : 0;
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GL11.glPushAttrib(GL11.GL_PIXEL_MODE_BIT);
            try {
                if (buffers) {
                    bindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                    bindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                }
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelTransferi(GL11.GL_MAP_COLOR, GL11.GL_FALSE);
                GL11.glPixelTransferf(GL11.GL_RED_SCALE, 1f);
                GL11.glPixelTransferf(GL11.GL_GREEN_SCALE, 1f);
                GL11.glPixelTransferf(GL11.GL_BLUE_SCALE, 1f);
                GL11.glPixelTransferf(GL11.GL_ALPHA_SCALE, 1f);
                GL11.glPixelTransferf(GL11.GL_RED_BIAS, 0f);
                GL11.glPixelTransferf(GL11.GL_GREEN_BIAS, 0f);
                GL11.glPixelTransferf(GL11.GL_BLUE_BIAS, 0f);
                GL11.glPixelTransferf(GL11.GL_ALPHA_BIAS, 0f);
            } catch (RuntimeException ex) {
                close();
                throw ex;
            }
        }

        private void bindBuffer(int target, int buffer) {
            if (context.OpenGL15) GL15.glBindBuffer(target, buffer);
            else ARBBufferObject.glBindBufferARB(target, buffer);
        }

        @Override
        public void close() {
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            } finally {
                try {
                    GL11.glPopAttrib();
                } finally {
                    GL11.glPopClientAttrib();
                    if (buffers) {
                        bindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pack);
                        bindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
                    }
                }
            }
        }
    }

    @Override public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) { }
    @Override public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) { restoreAll(); }
}
