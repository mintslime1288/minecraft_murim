package com.murimrpg.client;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rendering-only tuning, separately switchable for comparison with the previous PAL clips.
 *
 * <p>The {@code *-cycle-ticks} values are the legacy fixed-period clock and are used only when
 * {@code distance-driven-cadence} is false. With it on, the {@code *-cycle-blocks} values are the
 * ground distance one complete left+right cycle covers, so the cadence follows the player's actual
 * speed the way Mojang's own limb swing does, instead of running at a constant rate whatever the
 * player is doing. Both paths are kept so the two can be compared in one F5 session.
 *
 * <p>{@code stride-match} closes the other half of that same relationship. A cadence says how OFTEN
 * a step happens; it cannot say how FAR the foot travels while it happens, and on a rotation-only
 * rig the foot's travel comes entirely from the authored hip swing. When the two disagree the foot
 * skates whatever the cadence is. With stride matching on, each gait clip's limb swing is scaled
 * about its own cycle average by a gain solved from that clip's measured foot travel, so one step's
 * foot travel meets the ground that step covers. One flag turns it off, and it is a no-op - bit for
 * bit - once a clip is authored long enough to need a gain of 1.
 *
 * <p>{@code body-chain} is the third and separate layer: where the chest points while standing,
 * how far the chest and then the shoulders trail the feet, and the weight accent at each contact.
 * See {@link PalBodyChain} for what that layer can and cannot express on this rig. It is one flag
 * off, and every value inside it can be set to 0 on its own, so a bad F5 result can be bisected
 * without a rebuild.
 */
record PalNaturalSettings(boolean enabled, double walkTicks, double runTicks, double fastTicks,
                          double blendTicks, double airBlendTicks, double landingBlendTicks,
                          double responseTicks, double torsoRollWeight, double extraRunLean,
                          double idleArmRelax, double strafeWeight, double airCarryTicks,
                          int minimumFallTicks, boolean diagnostics,
                          boolean distanceCadence, double walkCycleBlocks, double runCycleBlocks,
                          double fastCycleBlocks, double minimumCycleTicks,
                          boolean strideMatch, double strideMatchStrength, double maximumStrideGain,
                          double maximumLimbSwing, PalBodyChain.Settings bodyChain) {
    static PalNaturalSettings load(Path directory) {
        Path file = directory.resolve("pal-natural-motion.yml");
        try {
            if (!Files.exists(file)) try (var in = PalNaturalSettings.class.getResourceAsStream("/pal-natural-motion.yml")) {
                if (in == null) throw new IOException("Missing pal-natural-motion.yml");
                Files.copy(in, file);
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load natural motion settings", e); }
        var y = YamlDocument.load(file);
        return new PalNaturalSettings(!Boolean.FALSE.equals(y.get("enabled")),
                range(y,"walk-cycle-ticks",32,20,48), range(y,"run-cycle-ticks",16,12,24),
                range(y,"fast-run-cycle-ticks",14,10,22), range(y,"transition-ticks",5,1,12),
                range(y,"air-transition-ticks",2,1,6), range(y,"landing-transition-ticks",4,1,10),
                range(y,"response-ticks",4,1,12), range(y,"torso-roll-weight",.35,0,1),
                Math.toRadians(range(y,"extra-run-lean-degrees",0,0,5)),
                range(y,"idle-arm-relax-weight",.65,.3,1), range(y,"strafe-swing-weight",.35,0,.6),
                range(y,"moving-jump-carry-ticks",8,2,20), (int)range(y,"minimum-fall-ticks",4,2,10),
                Boolean.TRUE.equals(y.get("diagnostics")),
                !Boolean.FALSE.equals(y.get("distance-driven-cadence")),
                range(y,"walk-cycle-blocks",2.36,0.8,6), range(y,"run-cycle-blocks",2.65,0.8,6),
                range(y,"fast-run-cycle-blocks",2.65,0.8,6), range(y,"minimum-cycle-ticks",7,4,20),
                !Boolean.FALSE.equals(y.get("stride-match")),
                range(y,"stride-match-strength",1,0,1.2), range(y,"maximum-stride-gain",3,1,5),
                Math.toRadians(range(y,"maximum-limb-swing-degrees",80,20,90)),
                PalBodyChain.load(y));
    }
    private static double range(YamlDocument y, String key, double fallback, double min, double max) {
        double value = y.decimal(key, fallback);
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Invalid pal-natural-motion.yml: " + key);
        return value;
    }
}
