package com.murimrpg.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.murimrpg.client.render.wuxia.WuxiaGlbReader;
import com.murimrpg.client.render.wuxia.WuxiaLocomotion;
import com.murimrpg.client.render.wuxia.WuxiaPlayerSettings;
import com.murimrpg.client.render.wuxia.WuxiaRig;
import com.murimrpg.client.render.wuxia.WuxiaRigValidator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the supplied rigged character in place of the player's own body, in third person only.
 *
 * <h2>One decision per frame</h2>
 * {@link #shouldReplace} is the single gate. When it answers true the mixin submits this mesh
 * and nothing else; when it answers false the existing PAL or vanilla submission happens and
 * this class does nothing at all. There is no path where both run, and none where the mesh is
 * drawn twice.
 *
 * <h2>What makes it answer false</h2>
 * Disabled in YAML, the asset missing or failing validation, another player's render state, and
 * every player state the asset has no authored clip for. Equipment and held items also turn it
 * off by default, because Minecraft positions armour and the held item from Mojang's player
 * model rather than from this skeleton; standing aside keeps the sword in the hand instead of
 * leaving it floating beside a body that is no longer drawn.
 *
 * <h2>What it never touches</h2>
 * Position, velocity, collision, hitbox, eye height, interaction range, server movement and
 * attack judgement. The clips' own root motion is removed before skinning, so the drawn body
 * cannot wander away from where the server says the player is.
 */
public final class WuxiaPlayerRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MurimRPG-Wuxia");
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("murimrpg", "dynamic/wuxia_player_body");

    private static WuxiaPlayerSettings settings = WuxiaPlayerSettings.disabled();
    private static WuxiaRig rig;
    private static WuxiaLocomotion locomotion;
    private static boolean active;
    private static String status = "not loaded";
    private static String validationSummary = "";
    private static float lastAgeInTicks = Float.NaN;
    private static float lastSlip;
    private static boolean submittedLogged;

    private WuxiaPlayerRenderer() {
    }

    public static void loadResources(Minecraft client) {
        reload(client, FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
    }

    /** Separated from {@link #loadResources} so the reload command and tests share one path. */
    public static void reload(Minecraft client, Path configDirectory) {
        active = false;
        rig = null;
        locomotion = null;
        settings = WuxiaPlayerSettings.load(configDirectory);
        if (!settings.enabled()) {
            status = "disabled in " + WuxiaPlayerSettings.FILE_NAME;
            return;
        }
        Identifier model = parse(settings.modelPath());
        if (model == null) {
            status = "model path is not a valid identifier: " + settings.modelPath();
            LOGGER.warn("Wuxia player renderer stays off: {}", status);
            return;
        }
        try (var input = client.getResourceManager().open(model)) {
            WuxiaGlbReader.Asset asset = WuxiaGlbReader.read(input, model.toString());
            WuxiaRigValidator.Result result = WuxiaRigValidator.validate(asset.rig(), settings);
            StringBuilder summary = new StringBuilder();
            for (var report : result.clips()) summary.append(report).append('\n');
            validationSummary = summary.toString();
            if (!result.valid()) {
                status = "validation failed: " + result.message();
                LOGGER.warn("Wuxia player renderer stays off: {}", status);
                LOGGER.warn("Clip measurements:\n{}", validationSummary);
                return;
            }
            NativeImage image = TrainingDummyGlbRenderer.decodeEmbeddedImage(asset.baseColorImage());
            DynamicTexture texture = new DynamicTexture(() -> "murimrpg_wuxia_player", image);
            texture.upload();
            client.getTextureManager().register(TEXTURE, texture);
            rig = asset.rig();
            locomotion = new WuxiaLocomotion(settings);
            active = true;
            status = "active";
            LOGGER.info("Wuxia player renderer ready: {} vertices, {} triangles, {} joints, "
                            + "{}x{} texture, scale {}; {}",
                    rig.vertexCount(), rig.triangleCount(), rig.jointCount(),
                    asset.textureWidth(), asset.textureHeight(),
                    String.format(Locale.ROOT, "%.6f", settings.renderScale()), settings.describe());
            LOGGER.info("Clip validation across {} uniform samples each:\n{}",
                    settings.validationSamples(), validationSummary);
        } catch (IOException | RuntimeException error) {
            status = "load failed: " + error.getMessage();
            LOGGER.warn("Wuxia player renderer stays off; the existing player body is unchanged", error);
        }
    }

    private static Identifier parse(String path) {
        int colon = path.indexOf(':');
        if (colon <= 0 || colon == path.length() - 1) return null;
        try {
            return Identifier.fromNamespaceAndPath(path.substring(0, colon), path.substring(colon + 1));
        } catch (RuntimeException error) {
            return null;
        }
    }

    public static boolean isActive() { return active; }

    public static String status() {
        return active ? "활성 (" + settings.describe() + String.format(Locale.ROOT, " slip=%.3f m/s)", lastSlip)
                : "비활성: " + status;
    }

    /**
     * The single render decision. Everything that could leave the player without equipment, or
     * without a body, answers false here and the existing path keeps the frame.
     */
    public static boolean shouldReplace(AvatarRenderState state) {
        if (!active || rig == null) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || state.id != player.getId()) return false;
        if (state.isFallFlying || state.isVisuallySwimming || state.isPassenger) return false;
        if (state.deathTime > 0 || state.isUsingItem || state.attackTime > .01f) return false;
        if (!settings.allowArmour() && !(state.headEquipment.isEmpty() && state.chestEquipment.isEmpty()
                && state.legsEquipment.isEmpty() && state.feetEquipment.isEmpty())) return false;
        if (!settings.allowHeldItems() && holdingSomething(player)) return false;
        return true;
    }

    private static boolean holdingSomething(net.minecraft.world.entity.player.Player player) {
        return !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
    }

    public static void submit(AvatarRenderState state, PoseStack poses, SubmitNodeCollector collector) {
        var player = Minecraft.getInstance().player;
        if (player == null || rig == null || locomotion == null) return;

        float delta = Float.isFinite(lastAgeInTicks)
                ? Math.max(0f, Math.min(5f, state.ageInTicks - lastAgeInTicks)) / 20f : 0f;
        lastAgeInTicks = state.ageInTicks;
        // Horizontal ground speed in blocks per second, read from the client's own movement.
        // This is an observation; nothing here writes back to the player.
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        float speed = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0);

        WuxiaLocomotion.State frame = locomotion.update(new WuxiaLocomotion.Input(
                speed, delta, player.isSprinting(), state.isCrouching, !player.onGround(), true));
        lastSlip = frame.slipPerSecond();
        if (!frame.renders()) return;

        float scale = settings.renderScale();
        poses.pushPose();
        poses.translate(0f, settings.groundOffsetBlocks(), 0f);
        // LivingEntityRenderer hands us Mojang's Y-down model space; the GLB is Y-up.
        if (settings.flipVertical()) poses.scale(1f, -1f, 1f);
        poses.mulPose(new Quaternionf().rotateY((float) Math.toRadians(settings.yawDegrees())));

        float[][] primary = paletteFor(frame.primary(), frame.primaryPhase());
        float[][] secondary = frame.secondaryWeight() > .001f
                ? paletteFor(frame.secondary(), frame.secondaryPhase()) : null;
        float weight = secondary == null ? 0f : frame.secondaryWeight();
        int light = state.lightCoords;

        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(TEXTURE),
                (pose, vertices) -> draw(pose, vertices, light, primary, secondary, weight, scale));
        poses.popPose();

        if (!submittedLogged) {
            submittedLogged = true;
            LOGGER.info("Submitted Wuxia player geometry for the first time; clip={}", frame.primary());
        }
    }

    private static float[][] paletteFor(WuxiaLocomotion.Clip clip, float phase) {
        String name = settings.clipName(clip);
        float authored = rig.clips().get(name).durationSeconds();
        return rig.palette(name, settings.clipTimeSeconds(clip, phase, authored), settings.rootPolicy(clip));
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer out, int light,
                             float[][] primary, float[][] secondary, float weight, float scale) {
        int[] indices = rig.indices();
        float[] uvs = rig.uvs();
        float[] position = new float[3];
        float[] normal = new float[3];
        float[] otherPosition = new float[3];
        float[] otherNormal = new float[3];
        for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
            int a = indices[triangle], b = indices[triangle + 1], c = indices[triangle + 2];
            // The entity pipeline consumes quads, so each triangle is emitted as a degenerate
            // quad with its last vertex repeated. This matches the existing GLB renderer.
            emit(pose, out, light, a, primary, secondary, weight, scale, uvs, position, normal, otherPosition, otherNormal);
            emit(pose, out, light, b, primary, secondary, weight, scale, uvs, position, normal, otherPosition, otherNormal);
            emit(pose, out, light, c, primary, secondary, weight, scale, uvs, position, normal, otherPosition, otherNormal);
            emit(pose, out, light, c, primary, secondary, weight, scale, uvs, position, normal, otherPosition, otherNormal);
        }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer out, int light, int vertex,
                             float[][] primary, float[][] secondary, float weight, float scale,
                             float[] uvs, float[] position, float[] normal,
                             float[] otherPosition, float[] otherNormal) {
        rig.skinPosition(primary, vertex, position);
        rig.skinNormal(primary, vertex, normal);
        if (secondary != null) {
            rig.skinPosition(secondary, vertex, otherPosition);
            rig.skinNormal(secondary, vertex, otherNormal);
            for (int i = 0; i < 3; i++) {
                position[i] += (otherPosition[i] - position[i]) * weight;
                normal[i] += (otherNormal[i] - normal[i]) * weight;
            }
        }
        float length = normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2];
        if (length > 1.0e-6f) {
            float inverse = (float) (1.0 / Math.sqrt(length));
            normal[0] *= inverse; normal[1] *= inverse; normal[2] *= inverse;
        } else {
            normal[0] = 0f; normal[1] = 1f; normal[2] = 0f;
        }
        int u = vertex * 2;
        out.addVertex(pose, position[0] * scale, position[1] * scale, position[2] * scale)
                .setColor(255, 255, 255, 255)
                .setUv(uvs[u], 1f - uvs[u + 1])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normal[0], normal[1], normal[2]);
    }
}
