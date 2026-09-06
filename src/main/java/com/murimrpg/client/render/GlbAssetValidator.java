package com.murimrpg.client.render;

import java.io.IOException;

/**
 * Rejects an external model before it reaches Minecraft's player renderer.
 *
 * <p>Unskinned glTF POSITION bounds are not enough: a broken inverse-bind conversion can
 * make a 1.8-block source model stretch hundreds of blocks after CPU skinning. Validation is
 * intentionally conservative because the visual model must never affect a playable client.</p>
 */
final class GlbAssetValidator {
    private static final float[] SAMPLE_FRACTIONS = {0f, .25f, .5f, .75f, 1f};

    private GlbAssetValidator() { }

    static void validatePlayerClip(GlbAnimatedPlayerMesh mesh, String label) throws IOException {
        var source = mesh.bounds();
        requireFinite(source, label + " source bounds");
        if (source.height() < 1.5f || source.height() > 2.1f || source.width() > 1.2f || source.depth() > 1.2f) {
            throw new IOException(label + " source dimensions are unsafe: " + source);
        }
        for (float fraction : SAMPLE_FRACTIONS) {
            var skinned = mesh.skinnedBounds(mesh.durationSeconds() * fraction);
            requireFinite(skinned, label + " skinned bounds at " + fraction);
            if (skinned.height() < 1.4f || skinned.height() > 2.4f
                    || skinned.width() > 1.5f || skinned.depth() > 1.5f) {
                throw new IOException(label + " skinning validation failed at clip fraction " + fraction
                        + ": " + skinned + ". Full Meshy rendering was disabled; vanilla player rendering remains active.");
            }
        }
    }

    private static void requireFinite(GlbAnimatedPlayerMesh.Bounds bounds, String name) throws IOException {
        if (!Float.isFinite(bounds.minX()) || !Float.isFinite(bounds.minY()) || !Float.isFinite(bounds.minZ())
                || !Float.isFinite(bounds.maxX()) || !Float.isFinite(bounds.maxY()) || !Float.isFinite(bounds.maxZ())) {
            throw new IOException(name + " contains non-finite values: " + bounds);
        }
    }
}
