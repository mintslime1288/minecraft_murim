package com.murimrpg.client.render.wuxia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the shipped rigged asset itself: its clips, its skinned bounds across 121 uniform
 * samples of every state, its floor contact and that its root motion really is removed.
 *
 * <p>Every test skips, rather than fails, when the GLB is not on the classpath. The restored
 * source snapshot deliberately omits large binary assets, and a skip keeps that distinct from a
 * real regression.
 */
class WuxiaRigAssetTest {

    private static final String RESOURCE = "/assets/murimrpg/models_glb/player/wuxia_rigged_all.glb";
    private static final String SHA256 =
            "F8CC902DDC0BA196FE139DD1B308CA3B6FE6534DB81D21F9501F4F88898DCE22";
    private static final int SAMPLES = 121;

    private static WuxiaGlbReader.Asset load() throws IOException {
        InputStream stream = WuxiaRigAssetTest.class.getResourceAsStream(RESOURCE);
        Assumptions.assumeTrue(stream != null, "rigged player GLB is not in this build's resources");
        try (stream) {
            return WuxiaGlbReader.read(stream, RESOURCE);
        }
    }

    private static WuxiaPlayerSettings settings() {
        WuxiaPlayerSettings d = WuxiaPlayerSettings.disabled();
        return new WuxiaPlayerSettings(true, "murimrpg:models_glb/player/wuxia_rigged_all.glb",
                d.targetHeightBlocks(), d.sourceHeightMetres(), d.groundOffsetBlocks(), d.yawDegrees(),
                d.flipVertical(), d.transitionSeconds(), d.movementThreshold(), d.runSpeedThreshold(),
                d.maximumPlaybackRate(), d.jumpEnabled(), d.jumpSeconds(), d.jumpClipStart(),
                d.jumpClipEnd(), d.idleClip(), d.walkClip(), d.runClip(), d.crouchClip(), d.jumpClip(),
                d.idleSeconds(), d.walkSeconds(), d.runSeconds(), d.crouchSeconds(),
                d.walkStrideMetres(), d.runStrideMetres(), d.crouchStrideMetres(),
                d.allowHeldItems(), d.allowArmour(), d.minimumSkinnedHeight(), d.maximumSkinnedHeight(),
                d.maximumSkinnedWidth(), d.maximumSkinnedDepth(), SAMPLES);
    }

    @Test
    @DisplayName("the asset is the exact file that was audited")
    void assetMatchesTheAuditedBytes() throws Exception {
        InputStream stream = WuxiaRigAssetTest.class.getResourceAsStream(RESOURCE);
        Assumptions.assumeTrue(stream != null, "rigged player GLB is not in this build's resources");
        byte[] bytes;
        try (stream) { bytes = stream.readAllBytes(); }
        var digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02X", b));
        assertEquals(SHA256, hex.toString(), "the shipped GLB is not the audited file");
    }

    @Test
    @DisplayName("all six clips load by name, not just the first one")
    void everyClipIsLoadedByName() throws IOException {
        var rig = load().rig();
        assertEquals(List.of("Running", "Walking", "Cautious_Crouch_Walk_Forward",
                "Idle_5", "Regular_Jump", "Armature|clip0|baselayer"),
                List.copyOf(rig.clips().keySet()));
        assertEquals(14955, rig.vertexCount());
        assertEquals(9152, rig.triangleCount());
        assertEquals(23, rig.jointCount());
        assertEquals("mixamorig:Hips", rig.rootJointName());
    }

    @Test
    @DisplayName("the bind pose skins to exactly the documented 1.7 m with its feet on the floor")
    void bindPoseMatchesTheDocumentedHeight() throws IOException {
        var rig = load().rig();
        var bounds = rig.skinnedBounds(null, 0f, WuxiaRig.RootPolicy.LOCK_HORIZONTAL);
        assertTrue(bounds.finite());
        assertEquals(1.7000f, bounds.height(), .0005f,
                "the root 0.01 unit-conversion scale must be kept, not cancelled");
        assertEquals(0.8867f, bounds.width(), .0005f);
        assertEquals(0.4359f, bounds.depth(), .0005f);
        assertEquals(0f, bounds.minY(), .0005f);
        assertEquals(1.8f / 1.7f, settings().renderScale(), 1.0e-6);
    }

    @Test
    @DisplayName("121 uniform samples of every clip stay finite and inside the drawn-size limits")
    void everyClipPassesTheFullSweep() throws IOException {
        var rig = load().rig();
        var result = WuxiaRigValidator.validate(rig, settings());
        assertTrue(result.valid(), result.message());
        assertEquals(5, result.clips().size(), "idle, walk, run, crouch and jump are all checked");
        for (var report : result.clips()) {
            assertEquals(SAMPLES, report.samples());
            assertTrue(report.minHeight() > 1.2f && report.maxHeight() < 1.85f, report.toString());
        }
    }

    @Test
    @DisplayName("locking the hips horizontally removes the 1.85 m root-motion drift")
    void rootMotionIsRemovedFromEveryGroundedClip() throws IOException {
        var rig = load().rig();
        float freeDrift = horizontalDrift(rig, "Cautious_Crouch_Walk_Forward", WuxiaRig.RootPolicy.FREE);
        float lockedDrift = horizontalDrift(rig, "Cautious_Crouch_Walk_Forward",
                WuxiaRig.RootPolicy.LOCK_HORIZONTAL);
        assertTrue(freeDrift > 1.8f, "the authored clip really does walk away: " + freeDrift);
        assertTrue(lockedDrift < .1f, "locking must stop it, got " + lockedDrift);
        for (String clip : List.of("Walking", "Running", "Idle_5", "Cautious_Crouch_Walk_Forward")) {
            assertTrue(horizontalDrift(rig, clip, WuxiaRig.RootPolicy.LOCK_HORIZONTAL)
                            < WuxiaRigValidator.MAXIMUM_HORIZONTAL_DRIFT_METRES,
                    clip + " still carries root motion");
        }
    }

    @Test
    @DisplayName("grounded clips keep their feet on the floor only if the vertical hip channel survives")
    void verticalHipChannelIsWhatKeepsTheFeetDown() throws IOException {
        var rig = load().rig();
        // Idle is the clearest case: with Y kept the feet sit at 0, with Y dropped it hovers.
        float keptFloor = worstFloor(rig, "Idle_5", WuxiaRig.RootPolicy.LOCK_HORIZONTAL);
        float droppedFloor = worstFloor(rig, "Idle_5", WuxiaRig.RootPolicy.LOCK_ALL);
        assertTrue(Math.abs(keptFloor) < .01f, "feet must touch the floor, got " + keptFloor);
        assertTrue(droppedFloor > .07f,
                "dropping Y must visibly hover, which is why grounded clips keep it: " + droppedFloor);
    }

    @Test
    @DisplayName("the jump sub-range covers exactly one hop out of the three in the clip")
    void jumpSubRangeIsASingleHop() throws IOException {
        var rig = load().rig();
        var s = settings();
        float authored = rig.clips().get(s.jumpClip()).durationSeconds();
        assertEquals(1.9583f, authored, .001f);
        float liftOff = -1f, touchDown = -1f;
        for (int i = 0; i < SAMPLES; i++) {
            float phase = (float) i / (SAMPLES - 1);
            float floor = rig.skinnedBounds(s.jumpClip(),
                    s.clipTimeSeconds(WuxiaLocomotion.Clip.JUMP, phase, authored),
                    WuxiaRig.RootPolicy.LOCK_ALL).minY();
            if (liftOff < 0f && floor > .03f) liftOff = phase;
            else if (liftOff >= 0f && touchDown < 0f && floor < .02f) touchDown = phase;
        }
        assertTrue(liftOff > 0f, "the sub-range must contain a take-off");
        assertTrue(touchDown > liftOff, "the sub-range must contain the matching landing");
        assertTrue(touchDown < 1f, "the sub-range must not run into the clip's second hop");
    }

    @Test
    @DisplayName("the baselayer clip is the bind pose and is never selected for play")
    void baselayerIsTheRestPoseOnly() throws IOException {
        var rig = load().rig();
        var bounds = rig.skinnedBounds("Armature|clip0|baselayer", .02f, WuxiaRig.RootPolicy.LOCK_HORIZONTAL);
        assertEquals(1.7000f, bounds.height(), .0005f);
        var s = settings();
        for (WuxiaLocomotion.Clip clip : WuxiaLocomotion.Clip.values()) {
            if (clip == WuxiaLocomotion.Clip.NONE) continue;
            assertTrue(!s.clipName(clip).contains("baselayer"),
                    clip + " must not be mapped to the rest-pose layer");
        }
    }

    @Test
    @DisplayName("the right hand joint resolves, which is what a future item anchor would need")
    void handJointIsAddressable() throws IOException {
        var rig = load().rig();
        assertTrue(rig.jointIndex("mixamorig:RightHand") >= 0);
        float[] hand = rig.jointMatrix("Idle_5", 0f, WuxiaRig.RootPolicy.LOCK_HORIZONTAL,
                "mixamorig:RightHand");
        assertNotNull(hand);
        assertTrue(hand[13] > .5f && hand[13] < 1.3f,
                "the hand should hang at roughly hip-to-chest height, got y=" + hand[13]);
    }

    @Test
    @DisplayName("a validator failure reports the clip that caused it rather than drawing anyway")
    void validationFailureIsExplicit() throws IOException {
        var rig = load().rig();
        WuxiaPlayerSettings s = settings();
        WuxiaPlayerSettings impossible = new WuxiaPlayerSettings(true, s.modelPath(),
                s.targetHeightBlocks(), s.sourceHeightMetres(), s.groundOffsetBlocks(), s.yawDegrees(),
                s.flipVertical(), s.transitionSeconds(), s.movementThreshold(), s.runSpeedThreshold(),
                s.maximumPlaybackRate(), s.jumpEnabled(), s.jumpSeconds(), s.jumpClipStart(),
                s.jumpClipEnd(), "NoSuchIdleClip", s.walkClip(), s.runClip(), s.crouchClip(),
                s.jumpClip(), s.idleSeconds(), s.walkSeconds(), s.runSeconds(), s.crouchSeconds(),
                s.walkStrideMetres(), s.runStrideMetres(), s.crouchStrideMetres(), s.allowHeldItems(),
                s.allowArmour(), s.minimumSkinnedHeight(), s.maximumSkinnedHeight(),
                s.maximumSkinnedWidth(), s.maximumSkinnedDepth(), SAMPLES);
        var result = WuxiaRigValidator.validate(rig, impossible);
        assertTrue(!result.valid());
        assertTrue(result.message().contains("NoSuchIdleClip"), result.message());
    }

    private static float horizontalDrift(WuxiaRig rig, String clip, WuxiaRig.RootPolicy policy) {
        float duration = rig.clips().get(clip).durationSeconds();
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            var b = rig.skinnedBounds(clip, duration * i / (SAMPLES - 1), policy);
            float cx = (b.minX() + b.maxX()) * .5f, cz = (b.minZ() + b.maxZ()) * .5f;
            minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
            minZ = Math.min(minZ, cz); maxZ = Math.max(maxZ, cz);
        }
        return Math.max(maxX - minX, maxZ - minZ);
    }

    private static float worstFloor(WuxiaRig rig, String clip, WuxiaRig.RootPolicy policy) {
        float duration = rig.clips().get(clip).durationSeconds();
        float worst = 0f;
        for (int i = 0; i < SAMPLES; i++) {
            float value = rig.skinnedBounds(clip, duration * i / (SAMPLES - 1), policy).minY();
            if (Math.abs(value) > Math.abs(worst)) worst = value;
        }
        return worst;
    }
}
