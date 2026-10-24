package com.murimrpg.client.render.wuxia;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Every tunable for the Wuxia player replacement, read from {@code wuxia-player-model.yml}.
 *
 * <p>No visual number is hardcoded in the renderer. The defaults below were measured from the
 * supplied asset rather than guessed, and each is documented in the shipped YAML with the
 * measurement that produced it.
 *
 * <p>{@code enabled} ships false. A missing, unreadable or malformed file also yields a disabled
 * settings object, because losing the player's own skin and equipment because of a typo in a
 * config file is worse than not showing the new model.
 */
public record WuxiaPlayerSettings(
        boolean enabled,
        String modelPath,
        float targetHeightBlocks,
        float sourceHeightMetres,
        float groundOffsetBlocks,
        float yawDegrees,
        boolean flipVertical,
        float transitionSeconds,
        float movementThreshold,
        float runSpeedThreshold,
        float maximumPlaybackRate,
        boolean jumpEnabled,
        float jumpSeconds,
        float jumpClipStart,
        float jumpClipEnd,
        String idleClip, String walkClip, String runClip, String crouchClip, String jumpClip,
        float idleSeconds, float walkSeconds, float runSeconds, float crouchSeconds,
        float walkStrideMetres, float runStrideMetres, float crouchStrideMetres,
        boolean allowHeldItems, boolean allowArmour,
        float minimumSkinnedHeight, float maximumSkinnedHeight,
        float maximumSkinnedWidth, float maximumSkinnedDepth,
        int validationSamples) {

    public static final String FILE_NAME = "wuxia-player-model.yml";

    /** A settings object that renders nothing; used for every load failure. */
    public static WuxiaPlayerSettings disabled() {
        return new WuxiaPlayerSettings(false, "", 1.8f, 1.7f, 0f, 180f, true,
                .18f, .05f, 4.9f, 3.2f, true, .62f, 0f, .35f,
                "Idle_5", "Walking", "Running", "Cautious_Crouch_Walk_Forward", "Regular_Jump",
                1.9167f, 1.0833f, .7083f, 1.2083f, 1.5408f, 1.4854f, 1.5801f,
                false, false, 1.30f, 2.00f, 1.60f, 1.60f, 121);
    }

    public static WuxiaPlayerSettings load(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) {
                try (var input = WuxiaPlayerSettings.class.getResourceAsStream("/" + FILE_NAME)) {
                    if (input == null) return disabled();
                    Files.copy(input, file);
                }
            }
            return parse(YamlDocument.load(file));
        } catch (IOException | RuntimeException error) {
            return disabled();
        }
    }

    static WuxiaPlayerSettings parse(YamlDocument yaml) {
        if (yaml == null || !bool(yaml, "enabled", false)) return disabled();
        WuxiaPlayerSettings d = disabled();
        return new WuxiaPlayerSettings(true,
                text(yaml, "model", "murimrpg:models_glb/player/wuxia_rigged_all.glb"),
                clamp(yaml, "scale.target-height-blocks", d.targetHeightBlocks(), .5f, 3f),
                clamp(yaml, "scale.source-height-metres", d.sourceHeightMetres(), .5f, 3f),
                clamp(yaml, "placement.ground-offset-blocks", d.groundOffsetBlocks(), -2f, 2f),
                clamp(yaml, "placement.yaw-degrees", d.yawDegrees(), -180f, 180f),
                bool(yaml, "placement.flip-vertical", d.flipVertical()),
                clamp(yaml, "animation.transition-seconds", d.transitionSeconds(), .02f, 1f),
                clamp(yaml, "animation.movement-threshold", d.movementThreshold(), .001f, 2f),
                clamp(yaml, "animation.run-speed-threshold", d.runSpeedThreshold(), .1f, 20f),
                clamp(yaml, "animation.maximum-playback-rate", d.maximumPlaybackRate(), .25f, 10f),
                bool(yaml, "animation.jump.enabled", d.jumpEnabled()),
                clamp(yaml, "animation.jump.seconds", d.jumpSeconds(), .1f, 3f),
                clamp(yaml, "animation.jump.clip-start", d.jumpClipStart(), 0f, 1f),
                clamp(yaml, "animation.jump.clip-end", d.jumpClipEnd(), 0f, 1f),
                text(yaml, "clips.idle", d.idleClip()), text(yaml, "clips.walk", d.walkClip()),
                text(yaml, "clips.run", d.runClip()), text(yaml, "clips.crouch", d.crouchClip()),
                text(yaml, "clips.jump", d.jumpClip()),
                clamp(yaml, "clips.idle-seconds", d.idleSeconds(), .05f, 30f),
                clamp(yaml, "clips.walk-seconds", d.walkSeconds(), .05f, 30f),
                clamp(yaml, "clips.run-seconds", d.runSeconds(), .05f, 30f),
                clamp(yaml, "clips.crouch-seconds", d.crouchSeconds(), .05f, 30f),
                clamp(yaml, "cadence.walk-stride-metres", d.walkStrideMetres(), .2f, 5f),
                clamp(yaml, "cadence.run-stride-metres", d.runStrideMetres(), .2f, 5f),
                clamp(yaml, "cadence.crouch-stride-metres", d.crouchStrideMetres(), .2f, 5f),
                bool(yaml, "compatibility.allow-held-items", d.allowHeldItems()),
                bool(yaml, "compatibility.allow-armour", d.allowArmour()),
                clamp(yaml, "validation.minimum-skinned-height", d.minimumSkinnedHeight(), .2f, 3f),
                clamp(yaml, "validation.maximum-skinned-height", d.maximumSkinnedHeight(), .2f, 4f),
                clamp(yaml, "validation.maximum-skinned-width", d.maximumSkinnedWidth(), .2f, 4f),
                clamp(yaml, "validation.maximum-skinned-depth", d.maximumSkinnedDepth(), .2f, 4f),
                (int) clamp(yaml, "validation.samples", d.validationSamples(), 120f, 2000f));
    }

    /** Render scale that maps the asset's bind height onto the configured target height. */
    public float renderScale() {
        return targetHeightBlocks() / Math.max(.01f, sourceHeightMetres());
    }

    public String clipName(WuxiaLocomotion.Clip clip) {
        return switch (clip) {
            case IDLE -> idleClip(); case WALK -> walkClip(); case RUN -> runClip();
            case CROUCH_WALK -> crouchClip(); case JUMP -> jumpClip(); case NONE -> "";
        };
    }

    public float clipSeconds(WuxiaLocomotion.Clip clip) {
        return switch (clip) {
            case IDLE -> idleSeconds(); case WALK -> walkSeconds(); case RUN -> runSeconds();
            case CROUCH_WALK -> crouchSeconds(); case JUMP -> jumpSeconds(); case NONE -> 1f;
        };
    }

    public float strideMetres(WuxiaLocomotion.Clip clip) {
        return switch (clip) {
            case RUN -> runStrideMetres(); case CROUCH_WALK -> crouchStrideMetres();
            default -> walkStrideMetres();
        };
    }

    /**
     * A grounded clip keeps its vertical hip channel so the feet stay on the floor; a jump drops
     * it, because the game already moves the player upwards and keeping it would lift twice.
     */
    public WuxiaRig.RootPolicy rootPolicy(WuxiaLocomotion.Clip clip) {
        return clip == WuxiaLocomotion.Clip.JUMP
                ? WuxiaRig.RootPolicy.LOCK_ALL : WuxiaRig.RootPolicy.LOCK_HORIZONTAL;
    }

    /** Maps a 0..1 state phase onto the authored clip's own time, honouring the jump sub-range. */
    public float clipTimeSeconds(WuxiaLocomotion.Clip clip, float phase, float authoredSeconds) {
        float wrapped = clip == WuxiaLocomotion.Clip.JUMP
                ? Math.max(0f, Math.min(1f, phase)) : WuxiaMath.wrapPhase(phase);
        if (clip == WuxiaLocomotion.Clip.JUMP) {
            float start = Math.min(jumpClipStart(), jumpClipEnd());
            float end = Math.max(jumpClipStart(), jumpClipEnd());
            return authoredSeconds * (start + (end - start) * wrapped);
        }
        return authoredSeconds * wrapped;
    }

    private static boolean bool(YamlDocument yaml, String key, boolean fallback) {
        Object raw = yaml.get(key);
        return raw instanceof Boolean value ? value : fallback;
    }

    private static String text(YamlDocument yaml, String key, String fallback) {
        Object raw = yaml.get(key);
        if (!(raw instanceof String value)) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /**
     * Reads a number and clamps it into range instead of throwing. A bad value in this file must
     * degrade one setting, not take the whole renderer down in the middle of a frame.
     */
    private static float clamp(YamlDocument yaml, String key, float fallback, float minimum, float maximum) {
        Object raw = yaml.get(key);
        if (!(raw instanceof Number number)) return fallback;
        float value = number.floatValue();
        if (!Float.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "enabled=%s model=%s height=%.3f scale=%.5f rate<=%.2f items=%s armour=%s",
                enabled(), modelPath(), targetHeightBlocks(), renderScale(),
                maximumPlaybackRate(), allowHeldItems(), allowArmour());
    }
}
