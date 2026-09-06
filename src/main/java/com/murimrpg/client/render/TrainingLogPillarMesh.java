package com.murimrpg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Closed, low-cost render mesh for the installed training log.
 *
 * <p>It intentionally has no gaps: unlike the AI source mesh, every vertical face and both end
 * caps are generated from a single 24-sided pillar. Minecraft's entity render types consume
 * vertices as quads, so this class always submits groups of four vertices. The continuous bark
 * texture supplies the fine visual detail, while the modest face count keeps several dummies
 * smooth to render.</p>
 */
public final class TrainingLogPillarMesh {
    private static final int SIDES = 24;
    private static final int VERTICAL_SEGMENTS = 16;
    private static final float HEIGHT = 2.0f;
    private static final float BASE_RADIUS = .218f;

    public int quadCount() {
        return SIDES * VERTICAL_SEGMENTS + SIDES * 2;
    }

    public void render(PoseStack.Pose pose, VertexConsumer vertices, int packedLight) {
        for (int level = 0; level < VERTICAL_SEGMENTS; level++) {
            float lower = (float) level / VERTICAL_SEGMENTS;
            float upper = (float) (level + 1) / VERTICAL_SEGMENTS;
            for (int side = 0; side < SIDES; side++) {
                float start = (float) side / SIDES;
                float end = (float) (side + 1) / SIDES;
                Vertex lowerStart = sideVertex(start, lower);
                Vertex lowerEnd = sideVertex(end, lower);
                Vertex upperStart = sideVertex(start, upper);
                Vertex upperEnd = sideVertex(end, upper);

                // Entity render types use QUADS. Sending two independent triangles here would
                // make every fourth vertex join the next triangle and produce visible holes.
                emit(vertices, pose, lowerStart, packedLight);
                emit(vertices, pose, upperStart, packedLight);
                emit(vertices, pose, upperEnd, packedLight);
                emit(vertices, pose, lowerEnd, packedLight);
            }
        }
        renderCaps(pose, vertices, packedLight);
    }

    private void renderCaps(PoseStack.Pose pose, VertexConsumer vertices, int packedLight) {
        Vertex topCenter = new Vertex(0, HEIGHT, 0, .5f, .5f, 0, 1, 0);
        Vertex bottomCenter = new Vertex(0, 0, 0, .5f, .5f, 0, -1, 0);
        for (int side = 0; side < SIDES; side++) {
            float start = (float) side / SIDES;
            float end = (float) (side + 1) / SIDES;
            Vertex topStart = capVertex(start, 1.0f, 1.0f);
            Vertex topEnd = capVertex(end, 1.0f, 1.0f);
            emit(vertices, pose, topCenter, packedLight);
            emit(vertices, pose, topEnd, packedLight);
            emit(vertices, pose, topStart, packedLight);
            emit(vertices, pose, topCenter, packedLight);

            Vertex bottomStart = capVertex(start, 0.0f, -1.0f);
            Vertex bottomEnd = capVertex(end, 0.0f, -1.0f);
            emit(vertices, pose, bottomCenter, packedLight);
            emit(vertices, pose, bottomStart, packedLight);
            emit(vertices, pose, bottomEnd, packedLight);
            emit(vertices, pose, bottomCenter, packedLight);
        }
    }

    private static Vertex sideVertex(float around, float vertical) {
        float angle = around * (float) (Math.PI * 2.0);
        float edgeTaper = .92f + .08f * (float) Math.sin(Math.PI * vertical);
        float naturalVariation = 1.0f
                + .018f * (float) Math.sin(angle * 3.0 + vertical * 2.3)
                + .010f * (float) Math.sin(angle * 7.0 - vertical * 5.1);
        float radius = BASE_RADIUS * edgeTaper * naturalVariation;
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        return new Vertex(x, vertical * HEIGHT, z, around, 1.0f - vertical,
                (float) Math.cos(angle), 0, (float) Math.sin(angle));
    }

    private static Vertex capVertex(float around, float vertical, float normalY) {
        Vertex side = sideVertex(around, vertical);
        return new Vertex(side.x, side.y, side.z, .5f + side.x / (BASE_RADIUS * 2),
                .5f + side.z / (BASE_RADIUS * 2), 0, normalY, 0);
    }

    private static void emit(VertexConsumer vertices, PoseStack.Pose pose, Vertex vertex, int packedLight) {
        vertices.addVertex(pose, vertex.x, vertex.y, vertex.z)
                .setColor(255, 255, 255, 255)
                .setUv(vertex.u, vertex.v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, vertex.normalX, vertex.normalY, vertex.normalZ);
    }

    private record Vertex(float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ) { }
}
