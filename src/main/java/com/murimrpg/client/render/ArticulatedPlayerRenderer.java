package com.murimrpg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.murimrpg.client.NaturalWalkDiagnostics;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

/**
 * A deliberately small humanoid mesh whose upper/lower limbs are separate boxes.
 * Unlike vanilla's six-part body, this produces visible elbow and knee hinges while
 * sampling the currently selected player's own Minecraft skin texture.
 */
public final class ArticulatedPlayerRenderer {
    private static ArticulatedPlayerSettings settings;
    private static boolean loaded;
    private ArticulatedPlayerRenderer() { }

    public static void loadResources(Minecraft client) {
        settings = ArticulatedPlayerSettings.load(FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
        loaded = settings.enabled();
    }

    public static boolean shouldReplace(AvatarRenderState state) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (!loaded || !NaturalWalkDiagnostics.enabled() || player == null || state.id != player.getId()) return false;
        // Armour and complex poses retain Mojang's renderer until matching articulated
        // armour/item attachments exist. This is intentional, not a silent visual loss.
        return !state.isCrouching && !state.isFallFlying && !state.isVisuallySwimming && !state.isPassenger
                && !state.isUsingItem && state.attackTime <= .01f && state.deathTime == 0
                && state.headEquipment.isEmpty() && state.chestEquipment.isEmpty()
                && state.legsEquipment.isEmpty() && state.feetEquipment.isEmpty() && state.skin != null;
    }

    public static String status() { return loaded ? "활성" : "설정에서 꺼짐"; }

    public static void submit(AvatarRenderState state, PoseStack stack, SubmitNodeCollector collector) {
        float movement = Math.clamp(state.walkAnimationSpeed, 0f, 1f);
        boolean running = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isSprinting();
        float phase = state.walkAnimationPos * (running ? 1.25f : 1f);
        float stride = radians(running ? settings.runStrideDegrees() : settings.walkStrideDegrees()) * movement;
        float knee = radians(running ? settings.runKneeDegrees() : settings.walkKneeDegrees()) * movement;
        float elbow = radians(running ? settings.runElbowDegrees() : settings.walkElbowDegrees()) * movement;
        float rightLeg = (float) Math.sin(phase) * stride;
        float leftLeg = -rightLeg;
        float rightArm = -rightLeg * .86f;
        float leftArm = -leftLeg * .86f;
        float bodyTurn = (float) Math.sin(phase) * radians(settings.bodyTurnDegrees()) * movement;

        // This mirrors the validated Y-up correction used by the former GLB path.
        stack.pushPose();
        stack.translate(0f, 1.501f, 0f);
        // submitCustomGeometry is submitted from the humanoid model stack, whose
        // coordinate space is 16 model units per world block.  This mesh is authored
        // in metres, so without this conversion a 1.8m player becomes 28.8 blocks tall.
        stack.scale(1f / 16f, 1f / 16f, 1f / 16f);
        stack.scale(1f, -1f, 1f);
        stack.mulPose(new Quaternionf().rotateY((float) Math.PI));
        var skin = state.skin.body().texturePath();
        collector.submitCustomGeometry(stack, RenderTypes.entityCutout(skin), (pose, vertices) -> {
            // SubmitNodeCollector invokes this renderer later.  `stack` has already
            // been popped by then, so never use it here: doing so loses the captured
            // 1/16 model-unit conversion and turns a player into a giant.  Start from
            // the immutable submitted pose instead.
            PoseStack local = new PoseStack();
            local.last().set(pose);
            // torso and head retain the standard player silhouette; limbs are subdivided.
            local.pushPose(); local.mulPose(new Quaternionf().rotateY(bodyTurn));
            box(local, vertices, -.25f, .90f, -.125f, .25f, 1.50f, .125f, SkinRegion.BODY, state.lightCoords);
            box(local, vertices, -.25f, 1.50f, -.25f, .25f, 1.80f, .25f, SkinRegion.HEAD, state.lightCoords);
            leg(local, vertices, -.23f, rightLeg, knee, SkinRegion.RIGHT_LEG, state.lightCoords);
            leg(local, vertices, .23f, leftLeg, knee, SkinRegion.LEFT_LEG, state.lightCoords);
            arm(local, vertices, -.39f, rightArm, elbow, SkinRegion.RIGHT_ARM, state.lightCoords, false);
            arm(local, vertices, .39f, leftArm, elbow, SkinRegion.LEFT_ARM, state.lightCoords, true);
            local.popPose();
        });
        stack.popPose();
    }

    private static void leg(PoseStack s, VertexConsumer out, float x, float upper, float knee, SkinRegion region, int light) {
        s.pushPose(); s.translate(x, .90f, 0f); s.mulPose(new Quaternionf().rotateX(upper));
        box(s, out, -.115f, -.42f, -.115f, .115f, 0f, .115f, region, light);
        s.translate(0f, -.42f, 0f); s.mulPose(new Quaternionf().rotateX(Math.max(0f, -upper) * .52f + knee * (float)Math.max(0d, Math.sin(upper))));
        box(s, out, -.11f, -.42f, -.11f, .11f, 0f, .11f, region, light); s.popPose();
    }

    private static void arm(PoseStack s, VertexConsumer out, float x, float upper, float elbow, SkinRegion region, int light, boolean left) {
        float direction = left ? -1f : 1f;
        s.pushPose(); s.translate(x, 1.43f, 0f); s.mulPose(new Quaternionf().rotateX(upper));
        box(s, out, -.105f, -.34f, -.105f, .105f, 0f, .105f, region, light);
        s.translate(0f, -.34f, 0f); s.mulPose(new Quaternionf().rotateX(direction * elbow * (float)Math.max(0d, Math.sin(Math.abs(upper)))));
        box(s, out, -.10f, -.34f, -.10f, .10f, 0f, .10f, region, light); s.popPose();
    }

    private static void box(PoseStack s, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, SkinRegion r, int light) {
        // Six opaque faces.  The small overlap at hinge seams prevents daylight holes.
        face(s, out, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r, light, 0,0,1);
        face(s, out, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, r, light, 0,0,-1);
        face(s, out, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r, light, -1,0,0);
        face(s, out, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, r, light, 1,0,0);
        face(s, out, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, r, light, 0,1,0);
        face(s, out, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r, light, 0,-1,0);
    }

    private static void face(PoseStack s, VertexConsumer out, float ax,float ay,float az, float bx,float by,float bz, float cx,float cy,float cz, float dx,float dy,float dz, SkinRegion r, int light, float nx,float ny,float nz) {
        vertex(s, out, ax,ay,az,r.u0,r.v1,light,nx,ny,nz); vertex(s,out,bx,by,bz,r.u1,r.v1,light,nx,ny,nz);
        vertex(s, out, cx,cy,cz,r.u1,r.v0,light,nx,ny,nz); vertex(s,out,dx,dy,dz,r.u0,r.v0,light,nx,ny,nz);
    }
    private static void vertex(PoseStack s, VertexConsumer out, float x,float y,float z,float u,float v,int light,float nx,float ny,float nz) {
        out.addVertex(s.last(),x,y,z).setColor(255,255,255,255).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(s.last(),nx,ny,nz);
    }
    private static float radians(float degrees) { return (float)Math.toRadians(degrees); }

    // Regions intentionally use the standard 64x64 player skin's base-layer areas.
    private enum SkinRegion {
        HEAD(8,8,16,16), BODY(20,20,28,32), RIGHT_ARM(44,20,48,32), LEFT_ARM(36,52,40,64),
        RIGHT_LEG(4,20,8,32), LEFT_LEG(20,52,24,64);
        final float u0,v0,u1,v1;
        SkinRegion(float x0,float y0,float x1,float y1) { u0=x0/64f; v0=y0/64f; u1=x1/64f; v1=y1/64f; }
    }
}
