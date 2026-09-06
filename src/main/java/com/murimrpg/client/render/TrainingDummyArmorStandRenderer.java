package com.murimrpg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Replaces only tagged MurimRPG armor stands; normal armor stands delegate to vanilla. */
final class TrainingDummyArmorStandRenderer extends ArmorStandRenderer {
    private static final Set<ArmorStandRenderState> GLB_STATES = Collections.newSetFromMap(new WeakHashMap<>());

    TrainingDummyArmorStandRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void extractRenderState(ArmorStand stand, ArmorStandRenderState state, float partialTick) {
        super.extractRenderState(stand, state, partialTick);
        if (TrainingDummyGlbRenderer.isActive() && stand.entityTags().contains("murimrpg.training_dummy")) {
            GLB_STATES.add(state);
            TrainingDummyGlbRenderer.logTaggedDummyDetected();
        } else {
            GLB_STATES.remove(state);
        }
    }

    @Override
    public void submit(ArmorStandRenderState state, PoseStack poses, SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        if (!GLB_STATES.contains(state)) {
            super.submit(state, poses, collector, camera);
            return;
        }
        // Meshy exports this asset at 1.0 block high and 0.215 blocks wide.
        // These non-uniform scales make it a 1.75-block-high, 0.70-block-wide training log.
        poses.pushPose();
        poses.scale(3.25f, 1.75f, 3.25f);
        // Use the non-culling entity pass so the imported mesh remains visible if a modelling
        // tool exports one surface with reversed winding.
        collector.submitCustomGeometry(poses, RenderTypes.entityTranslucent(TrainingDummyGlbRenderer.texture()),
                (pose, vertices) -> TrainingDummyGlbRenderer.mesh().render(pose, vertices, state.lightCoords));
        TrainingDummyGlbRenderer.logGeometrySubmitted();
        poses.popPose();
    }
}
