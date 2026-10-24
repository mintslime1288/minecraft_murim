package com.murimrpg.client.render.wuxia;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a loaded rig is safe to draw, before a single frame is submitted.
 *
 * <p>Unskinned POSITION bounds are not evidence. A file can have a perfectly ordinary 1.7 m
 * POSITION box and still skin into something hundreds of blocks across if the root node scale
 * and the inverse bind matrices disagree. This asset is exactly the shape where that happens:
 * its root carries a 0.01 unit conversion and its hips sit at y = 88.89 in rig units. So every
 * check here runs on real CPU-skinned output, sampled uniformly across each clip.
 *
 * <p>A failure disables the renderer and is reported verbatim; it never falls back to drawing
 * something approximate.
 */
public final class WuxiaRigValidator {

    /** Outcome of validation, with the per-clip measurements that produced it. */
    public record Result(boolean valid, String message, List<ClipReport> clips) { }

    /** What one clip measured across the sample sweep. */
    public record ClipReport(String name, int samples, float minHeight, float maxHeight,
                             float minFloor, float maxFloor, float maxWidth, float maxDepth,
                             float maxHorizontalDrift) {
        @Override public String toString() {
            return String.format(Locale.ROOT,
                    "%s: n=%d height %.4f..%.4f floor %+.4f..%+.4f width<=%.4f depth<=%.4f drift<=%.4f",
                    name, samples, minHeight, maxHeight, minFloor, maxFloor, maxWidth, maxDepth,
                    maxHorizontalDrift);
        }
    }

    private WuxiaRigValidator() {
    }

    public static Result validate(WuxiaRig rig, WuxiaPlayerSettings settings) {
        List<ClipReport> reports = new ArrayList<>();
        for (WuxiaLocomotion.Clip clip : WuxiaLocomotion.Clip.values()) {
            if (clip == WuxiaLocomotion.Clip.NONE) continue;
            if (clip == WuxiaLocomotion.Clip.JUMP && !settings.jumpEnabled()) continue;
            String name = settings.clipName(clip);
            if (!rig.hasClip(name)) {
                return new Result(false, "clip \"" + name + "\" is not in the asset; present: "
                        + String.join(", ", rig.clips().keySet()), reports);
            }
            WuxiaRig.RootPolicy policy = settings.rootPolicy(clip);
            float authored = rig.clips().get(name).durationSeconds();
            int samples = Math.max(120, settings.validationSamples());
            float minHeight = Float.MAX_VALUE, maxHeight = -Float.MAX_VALUE;
            float minFloor = Float.MAX_VALUE, maxFloor = -Float.MAX_VALUE;
            float maxWidth = 0f, maxDepth = 0f;
            float minCentreX = Float.MAX_VALUE, maxCentreX = -Float.MAX_VALUE;
            float minCentreZ = Float.MAX_VALUE, maxCentreZ = -Float.MAX_VALUE;
            for (int i = 0; i < samples; i++) {
                float phase = (float) i / (samples - 1);
                WuxiaRig.Bounds bounds = rig.skinnedBounds(
                        name, settings.clipTimeSeconds(clip, phase, authored), policy);
                if (!bounds.finite()) {
                    return new Result(false, name + " produced a non-finite coordinate at phase "
                            + phase + "; the renderer stays off", reports);
                }
                minHeight = Math.min(minHeight, bounds.height());
                maxHeight = Math.max(maxHeight, bounds.height());
                minFloor = Math.min(minFloor, bounds.minY());
                maxFloor = Math.max(maxFloor, bounds.minY());
                maxWidth = Math.max(maxWidth, bounds.width());
                maxDepth = Math.max(maxDepth, bounds.depth());
                float centreX = (bounds.minX() + bounds.maxX()) * .5f;
                float centreZ = (bounds.minZ() + bounds.maxZ()) * .5f;
                minCentreX = Math.min(minCentreX, centreX); maxCentreX = Math.max(maxCentreX, centreX);
                minCentreZ = Math.min(minCentreZ, centreZ); maxCentreZ = Math.max(maxCentreZ, centreZ);
            }
            float drift = Math.max(maxCentreX - minCentreX, maxCentreZ - minCentreZ);
            ClipReport report = new ClipReport(name, samples, minHeight, maxHeight,
                    minFloor, maxFloor, maxWidth, maxDepth, drift);
            reports.add(report);

            float scale = settings.renderScale();
            if (minHeight * scale < settings.minimumSkinnedHeight()
                    || maxHeight * scale > settings.maximumSkinnedHeight()) {
                return new Result(false, "drawn height out of range for " + report, reports);
            }
            if (maxWidth * scale > settings.maximumSkinnedWidth()
                    || maxDepth * scale > settings.maximumSkinnedDepth()) {
                return new Result(false, "drawn footprint out of range for " + report, reports);
            }
            // A clip whose body centre wanders is still carrying root motion. That would walk
            // the drawn player away from where the server says they are.
            if (drift > MAXIMUM_HORIZONTAL_DRIFT_METRES) {
                return new Result(false, "root motion was not removed from " + report, reports);
            }
        }
        return new Result(true, "ok", reports);
    }

    /**
     * How far the skinned body's horizontal centre may wander within one clip. Limb swing alone
     * moves it by under 0.1 m on every clip in this asset; uncancelled root motion moves the
     * crouch walk by 1.85 m, so anything near that is a policy failure rather than a pose.
     */
    public static final float MAXIMUM_HORIZONTAL_DRIFT_METRES = .40f;
}
