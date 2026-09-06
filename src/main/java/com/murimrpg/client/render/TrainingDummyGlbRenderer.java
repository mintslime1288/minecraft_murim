package com.murimrpg.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only registration for the licensed local GLB training-log asset. */
public final class TrainingDummyGlbRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MurimRPG-GLB");
    private static final Identifier BARK_TEXTURE = Identifier.fromNamespaceAndPath("murimrpg", "textures/entity/training_dummy_log_continuous.png");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("murimrpg", "dynamic/training_dummy_log_pillar_continuous_bark");
    private static boolean active;
    private static TrainingLogPillarMesh mesh;
    private static boolean taggedDummyLogged;
    private static boolean submittedLogged;

    private TrainingDummyGlbRenderer() { }

    /** The client mixin intercepts only tagged armor stands; resources load after startup. */
    public static void register() {
        LOGGER.info("MurimRPG training-dummy GLB renderer mixin registered.");
    }

    /** Must run after Minecraft has created its client resource manager. */
    public static void loadResources(Minecraft client) {
        try {
            DynamicTexture texture = new DynamicTexture(
                    () -> "murimrpg_training_dummy_log_pillar", decodeResourceImage(client, BARK_TEXTURE));
            texture.upload();
            client.getTextureManager().register(TEXTURE, texture);
            // The AI-generated GLB is retained as a source asset, but its topology contains
            // literal holes. Use a verified closed pillar for the live render surface.
            mesh = new TrainingLogPillarMesh();
            active = true;
            LOGGER.info("Loaded MurimRPG closed training-log mesh: {} quads.", mesh.quadCount());
        } catch (IOException | RuntimeException error) {
            mesh = null;
            // A bad optional asset must never stop a player reaching the main menu.
            active = false;
            LOGGER.warn("Training-dummy GLB renderer disabled; vanilla/resource-pack fallback remains active", error);
        }
    }

    public static boolean isActive() { return active; }
    public static TrainingLogPillarMesh mesh() { return mesh; }
    public static Identifier texture() { return TEXTURE; }

    /**
     * Scoreboard tags are server-only and are not guaranteed to be sent to clients. The existing
     * head item with CMD 32003 is synchronized as normal equipment and is therefore the stable
     * visual-only marker for a MurimRPG training dummy.
     */
    public static boolean isTrainingDummyVisual(ArmorStand stand) {
        var head = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Items.CARVED_PUMPKIN)) return false;
        CustomModelData modelData = head.get(DataComponents.CUSTOM_MODEL_DATA);
        return modelData != null && modelData.floats().stream().anyMatch(value -> value == 32003f);
    }

    public static void logTaggedDummyDetected() {
        if (!taggedDummyLogged) {
            taggedDummyLogged = true;
            LOGGER.info("Detected a tagged MurimRPG training dummy; substituting its GLB model.");
        }
    }

    public static void logGeometrySubmitted() {
        if (!submittedLogged) {
            submittedLogged = true;
            LOGGER.info("Submitted MurimRPG training-dummy GLB geometry to the world renderer.");
        }
    }

    /**
     * GLB base-colour textures are commonly PNG or JPEG. NativeImage's byte-array reader in
     * this Minecraft version validates PNG headers, so decode with ImageIO first and then copy
     * the pixels into Minecraft's native ABGR image format.
     */
    static NativeImage decodeEmbeddedImage(byte[] bytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) throw new IOException("Unsupported embedded GLB image format");

        NativeImage decoded = new NativeImage(source.getWidth(), source.getHeight(), true);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int abgr = (argb & 0xFF000000)
                        | ((argb & 0x000000FF) << 16)
                        | (argb & 0x0000FF00)
                        | ((argb & 0x00FF0000) >> 16);
                decoded.setPixelABGR(x, y, abgr);
            }
        }
        return decoded;
    }

    private static NativeImage decodeResourceImage(Minecraft client, Identifier texture) throws IOException {
        try (var input = client.getResourceManager().open(texture)) {
            return decodeEmbeddedImage(input.readAllBytes());
        }
    }

}
