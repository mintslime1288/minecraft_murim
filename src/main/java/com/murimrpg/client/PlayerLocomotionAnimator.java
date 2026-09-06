package com.murimrpg.client;

import java.util.HashMap;
import java.util.Map;

/** Selects, samples and smoothly blends walk/run animation-only GLB clips. */
final class PlayerLocomotionAnimator {
    enum State { IDLE, WALK, RUN, AIRBORNE, CROUCH, SWIM, CLIMB, RIDE, ATTACK, USE_ITEM, SLEEP, DEAD }
    private final Map<Integer, LocomotionBlendState> players = new HashMap<>();

    MeshyWalkAnimation.Pose sample(int playerId, float age, float phase, State state,
            NaturalWalkSettings settings) {
        var blend = players.computeIfAbsent(playerId, ignored -> new LocomotionBlendState());
        if (Float.compare(age, blend.lastAge) == 0) return blend.cached;
        NaturalWalkDiagnostics.Mode forced = NaturalWalkDiagnostics.mode();
        boolean walk = state == State.WALK && forced != NaturalWalkDiagnostics.Mode.RUN;
        boolean run = state == State.RUN && forced != NaturalWalkDiagnostics.Mode.WALK;
        boolean diagnosticSafe = state == State.IDLE || state == State.WALK || state == State.RUN;
        if (forced == NaturalWalkDiagnostics.Mode.WALK && diagnosticSafe) walk = true;
        if (forced == NaturalWalkDiagnostics.Mode.RUN && diagnosticSafe) run = true;
        blend.update(age, walk ? 1f : 0f, run ? 1f : 0f, settings);
        float samplePhase = forced != NaturalWalkDiagnostics.Mode.AUTO && state == State.IDLE ? age * .75f : phase;
        var walkPose = MeshyWalkAnimation.sample(MeshyWalkAnimation.Clip.WALK, samplePhase * settings.walkSpeedMultiplier());
        var runPose = MeshyWalkAnimation.sample(MeshyWalkAnimation.Clip.RUN, samplePhase * settings.runSpeedMultiplier());
        blend.cached = MeshyWalkAnimation.Pose.blend(walkPose, runPose, blend.walkWeight, blend.runWeight);
        return blend.cached;
    }

    static State state(PlayerAnimationState state, boolean onGround, boolean water, boolean climbing,
            boolean flying, boolean sleeping, boolean dead) {
        if (dead) return State.DEAD;
        if (sleeping) return State.SLEEP;
        if (state.meleeAttacking()) return State.ATTACK;
        if (state.usingItem()) return State.USE_ITEM;
        if (state.passenger()) return State.RIDE;
        if (state.swimming() || water) return State.SWIM;
        if (climbing) return State.CLIMB;
        if (state.crouching()) return State.CROUCH;
        if (!onGround || flying) return State.AIRBORNE;
        if (!state.moving()) return State.IDLE;
        return state.sprinting() ? State.RUN : State.WALK;
    }
}
