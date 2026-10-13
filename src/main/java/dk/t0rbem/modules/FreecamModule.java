package dk.t0rbem.modules;

import com.mojang.authlib.GameProfile;
import dk.t0rbem.settings.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.Locale;

/**
 * Freecam: kameraet flyver rundt i den retning du kigger (WASD, Mellemrum op, Shift ned),
 * mens din spiller står stille. Taster: V (kan ændres under Styring).
 */
public class FreecamModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 0.8, 0.1, 4.0, 0.1));

    private static class CamEntity extends OtherClientPlayerEntity {
        CamEntity(ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public float getYaw(float tickDelta) { return getYaw(); }

        @Override
        public float getPitch(float tickDelta) { return getPitch(); }
    }

    private CamEntity cam;
    private ClientWorld camWorld;
    private Input savedInput;

    public FreecamModule() {
        super("Freecam", "Flyv kameraet rundt uden at flytte din spiller (tast: V).", Category.RENDER, false);
    }

    @Override
    public void onEnable() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            enabled = false;
            return;
        }

        // Undgå at andre bevægelses-moduler flytter din rigtige spiller samtidig
        for (String name : new String[]{"Flight", "Auto Sprint"}) {
            Module other = ModuleManager.get(name);
            if (other != null) other.setEnabled(false);
        }

        camWorld = mc.world;
        cam = new CamEntity(mc.world, p.getGameProfile());
        cam.setPosition(p.getX(), p.getY(), p.getZ());
        cam.setYaw(p.getYaw());
        cam.setPitch(p.getPitch());
        cam.lastRenderX = cam.getX();
        cam.lastRenderY = cam.getY();
        cam.lastRenderZ = cam.getZ();

        savedInput = p.input;
        p.input = new Input(); // tom input: din rigtige spiller bevæger sig ikke
        mc.setCameraEntity(cam);
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            if (savedInput != null) mc.player.input = savedInput;
            mc.setCameraEntity(mc.player);
        }
        savedInput = null;
        cam = null;
        camWorld = null;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || cam == null) return;
        if (mc.world != camWorld) { // dimension/server skiftet
            setEnabled(false);
            return;
        }

        // Sørg for at kameraet bliver ved med at være vores (noget andet kan nulstille det)
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);

        double vx = 0, vy = 0, vz = 0;
        if (mc.currentScreen == null) {
            double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
            double s = (mc.options.rightKey.isPressed() ? 1 : 0) - (mc.options.leftKey.isPressed() ? 1 : 0);
            double u = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

            double yaw = Math.toRadians(p.getYaw());
            double pitch = Math.toRadians(p.getPitch());

            // frem/tilbage følger hvor du kigger (inkl. op/ned), sideværts er vandret
            vx = (-Math.sin(yaw) * Math.cos(pitch)) * f + (-Math.cos(yaw)) * s;
            vy = (-Math.sin(pitch)) * f + u;
            vz = (Math.cos(yaw) * Math.cos(pitch)) * f + (-Math.sin(yaw)) * s;

            double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len > 1.0) {
                vx /= len;
                vy /= len;
                vz /= len;
            }
            vx *= speed.get();
            vy *= speed.get();
            vz *= speed.get();
        }

        cam.lastRenderX = cam.getX();
        cam.lastRenderY = cam.getY();
        cam.lastRenderZ = cam.getZ();
        cam.setPosition(cam.getX() + vx, cam.getY() + vy, cam.getZ() + vz);
        cam.setYaw(p.getYaw());
        cam.setPitch(p.getPitch());
    }

    /** Lille tekst øverst, så du kan se at Freecam er aktiv, og hvor kameraet er. */
    @Override
    public void renderHud(DrawContext ctx, float partialTicks) {
        if (cam == null) return;
        String text = String.format(Locale.ROOT, "FREECAM  %.1f / %.1f / %.1f   (WASD, Space, Shift)",
                cam.getX(), cam.getY(), cam.getZ());
        int x = (ctx.getScaledWindowWidth() - mc.textRenderer.getWidth(text)) / 2;
        ctx.drawTextWithShadow(mc.textRenderer, text, x, 14, 0xFF55FFAA);
    }
}
