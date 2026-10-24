package com.murimrpg.client.render.wuxia;

/**
 * Chooses which authored clip plays, at what phase, and how two clips cross-fade.
 *
 * <p>Pure and frame-driven: every input arrives through {@link #update}, so the whole state
 * machine is unit testable without a game. Three decisions matter.
 *
 * <h2>Phase comes from distance, not from a timer</h2>
 * A clip played at a fixed rate only avoids foot sliding at exactly one movement speed. These
 * clips were captured at 1.42 m/s (walk) and 2.10 m/s (run) while Minecraft moves a player at
 * 4.317 and 5.612 blocks per second, so a fixed rate would slide the feet by more than half the
 * stride. Advancing the phase by {@code distance / strideMetres} instead keeps the planted foot
 * planted at any speed, including speed potions, and is what the PAL path already does.
 *
 * <p>The price is playback rate: matching 4.317 m/s with a 1.54 m stride needs about 3x speed.
 * {@code maximumPlaybackRate} caps that; above the cap the feet do slip again, and
 * {@link State#slipPerSecond} reports exactly how much so it is visible rather than guessed at.
 *
 * <h2>Transitions are cross-fades with phase carried across</h2>
 * Walk and run are both two-step cycles, so the outgoing phase is reused by the incoming clip.
 * That is what stops a leg from snapping to the other side mid-stride.
 *
 * <h2>Anything unauthored returns {@link Clip#NONE}</h2>
 * There is no swim, ride, death, attack or item-use clip in the asset. Rather than reuse a walk
 * for them, the selector says so and the renderer hands the frame back to the existing path.
 */
public final class WuxiaLocomotion {

    /** The authored clips, plus the explicit "not covered by this asset" answer. */
    public enum Clip { NONE, IDLE, WALK, RUN, CROUCH_WALK, JUMP }

    /** Everything the renderer needs for one frame. */
    public record State(Clip primary, float primaryPhase, Clip secondary, float secondaryPhase,
                        float secondaryWeight, float slipPerSecond) {
        public boolean renders() { return primary != Clip.NONE; }
    }

    /** Per-frame inputs, all read-only observations of the player. */
    public record Input(float horizontalSpeedPerSecond, float deltaSeconds, boolean sprinting,
                        boolean crouching, boolean airborne, boolean supported) { }

    private final WuxiaPlayerSettings settings;
    private Clip current = Clip.IDLE;
    private Clip previous = Clip.IDLE;
    private float phase;
    private float idleTime;
    private float jumpPhase;
    private float transition = 1f;
    private float previousPhase;
    private boolean started;

    public WuxiaLocomotion(WuxiaPlayerSettings settings) {
        this.settings = settings;
    }

    /** Resets to a clean idle, used when the renderer is re-enabled or the asset is reloaded. */
    public void reset() {
        current = previous = Clip.IDLE;
        phase = idleTime = jumpPhase = previousPhase = 0f;
        transition = 1f;
        started = false;
    }

    public State update(Input input) {
        if (!input.supported()) {
            reset();
            return new State(Clip.NONE, 0f, Clip.NONE, 0f, 0f, 0f);
        }
        float delta = Math.max(0f, Math.min(.25f, input.deltaSeconds()));
        Clip target = select(input);

        if (target == Clip.JUMP) {
            jumpPhase = Math.min(1f, jumpPhase + delta / Math.max(.05f, settings.jumpSeconds()));
        } else {
            jumpPhase = 0f;
        }

        float slip = 0f;
        if (target == Clip.IDLE) {
            idleTime += delta;
        } else if (target != Clip.JUMP) {
            float stride = settings.strideMetres(target);
            float cycleSeconds = settings.clipSeconds(target);
            float advance = input.horizontalSpeedPerSecond() * delta / Math.max(.01f, stride);
            float maximumAdvance = settings.maximumPlaybackRate() * delta / Math.max(.01f, cycleSeconds);
            if (advance > maximumAdvance) {
                // Report the un-played distance as slip so the F5 check has a number to compare.
                slip = (advance - maximumAdvance) * stride / Math.max(1.0e-4f, delta);
                advance = maximumAdvance;
            }
            phase = WuxiaMath.wrapPhase(phase + advance);
        }

        if (!started) {
            current = previous = target;
            transition = 1f;
            started = true;
        } else if (target != current) {
            previous = current;
            previousPhase = phaseFor(previous);
            current = target;
            transition = 0f;
        }
        float blendSeconds = Math.max(.01f, settings.transitionSeconds());
        transition = Math.min(1f, transition + delta / blendSeconds);
        // The outgoing clip keeps advancing at the same phase as the incoming one, so a
        // cross-fade between two gait clips never rewinds a leg.
        previousPhase = previous == Clip.IDLE || previous == Clip.JUMP ? previousPhase : phaseFor(current);

        float weight = WuxiaMath.smooth(transition);
        if (weight >= .999f || previous == current) {
            return new State(current, phaseFor(current), Clip.NONE, 0f, 0f, slip);
        }
        return new State(current, phaseFor(current), previous, previousPhase, 1f - weight, slip);
    }

    private Clip select(Input input) {
        if (input.airborne()) return settings.jumpEnabled() ? Clip.JUMP : Clip.NONE;
        boolean moving = input.horizontalSpeedPerSecond() > settings.movementThreshold();
        if (input.crouching()) return Clip.CROUCH_WALK;
        if (!moving) return Clip.IDLE;
        return input.sprinting() || input.horizontalSpeedPerSecond() >= settings.runSpeedThreshold()
                ? Clip.RUN : Clip.WALK;
    }

    private float phaseFor(Clip clip) {
        return switch (clip) {
            case IDLE -> WuxiaMath.wrapPhase(idleTime / Math.max(.05f, settings.clipSeconds(Clip.IDLE)));
            case JUMP -> jumpPhase;
            // A crouched player standing still freezes the crouch-walk clip at its planted frame
            // rather than borrowing another clip, which would be a different stance entirely.
            case CROUCH_WALK, WALK, RUN -> phase;
            case NONE -> 0f;
        };
    }

    /** Current gait phase, exposed for diagnostics. */
    public float gaitPhase() { return phase; }
}
