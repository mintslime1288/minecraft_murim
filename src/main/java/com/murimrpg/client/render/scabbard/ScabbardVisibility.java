package com.murimrpg.client.render.scabbard;

/**
 * Decides whether the virtual scabbard is drawn, and on which physical arm.
 *
 * <p>Pure on purpose: every ruling the feature needs - a real off-hand item, a left-handed
 * player, spectators, a missing model, another item in the main hand - is a function of a few
 * observed booleans, so it can be pinned down in a unit test instead of being discovered in
 * game. The renderer does nothing but ask this class and then draw.
 *
 * <h2>The rulings</h2>
 * <ul>
 *   <li><b>A real off-hand item wins.</b> If the player is actually holding something in the off
 *       hand, the scabbard is not drawn at all. Two models in one hand is the one outcome that
 *       cannot be allowed, and the real item is the one the player can interact with.
 *   <li><b>Left-handed players are mirrored.</b> The scabbard goes on the arm opposite the main
 *       arm, not on the physically-left arm. With the Minecraft "left hand" option the sword is
 *       in the left hand and the scabbard belongs on the right.
 *   <li><b>A missing model means nothing is drawn.</b> Without the resource pack the item model
 *       resolves to Minecraft's missing-model placeholder; drawing that would hang a black and
 *       magenta cube off the player's hand. Better to show no scabbard.
 *   <li><b>Invisibility follows vanilla.</b> Vanilla keeps drawing a held item for an invisible
 *       player, so the scabbard does too. Hiding it would make the pair inconsistent: a sword
 *       floating in one hand and nothing in the other.
 *   <li><b>Spectators are skipped.</b> Belt and braces - the layer normally never runs for a
 *       spectator, but the check costs one boolean and removes the question.
 * </ul>
 */
public final class ScabbardVisibility {

    /** Which physical arm carries the scabbard, or neither. */
    public enum Arm { NONE, LEFT, RIGHT }

    /** Everything the decision depends on. All of it is observed, none of it is written back. */
    public record Input(boolean featureEnabled,
                        boolean modelAvailable,
                        boolean mainHandIsConfiguredSword,
                        boolean offHandEmpty,
                        boolean spectator,
                        boolean mainArmIsRight) { }

    /** Why nothing is drawn, for the diagnostics line. Never shown to the player as an error. */
    public enum Reason {
        SHOWN,
        DISABLED_IN_CONFIG,
        MODEL_NOT_INSTALLED,
        MAIN_HAND_IS_NOT_THE_SWORD,
        OFF_HAND_HOLDS_A_REAL_ITEM,
        SPECTATOR
    }

    /** The decision: an arm to draw on, plus the reason, so diagnostics never have to guess. */
    public record Decision(Arm arm, Reason reason) {
        public boolean visible() { return arm != Arm.NONE; }
    }

    private ScabbardVisibility() {
    }

    public static Decision decide(Input input) {
        if (!input.featureEnabled()) return new Decision(Arm.NONE, Reason.DISABLED_IN_CONFIG);
        if (!input.modelAvailable()) return new Decision(Arm.NONE, Reason.MODEL_NOT_INSTALLED);
        if (input.spectator()) return new Decision(Arm.NONE, Reason.SPECTATOR);
        if (!input.mainHandIsConfiguredSword()) {
            return new Decision(Arm.NONE, Reason.MAIN_HAND_IS_NOT_THE_SWORD);
        }
        if (!input.offHandEmpty()) return new Decision(Arm.NONE, Reason.OFF_HAND_HOLDS_A_REAL_ITEM);
        // The scabbard hangs on the hand that is not holding the sword.
        return new Decision(input.mainArmIsRight() ? Arm.LEFT : Arm.RIGHT, Reason.SHOWN);
    }
}
