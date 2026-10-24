package com.murimrpg.client;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Avatar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Licensed Player Animation Library integration. PAL owns animation layering,
 * Bendable Cuboids performs visible elbow/knee deformation, and Minecraft keeps
 * the authoritative skin, held-item, movement, hitbox and combat rendering paths.
 */
public final class PalPlayerAnimationIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("MurimRPG-PAL");
    private static final String PAL_MOD_ID = "player_animation_library";
    private static final String BEND_MOD_ID = "bendable_cuboids";
    private static final Identifier LOCOMOTION_LAYER = id("locomotion");
    private static final Identifier COMBAT_LAYER = id("combat");
    private static final Set<Identifier> MISSING_WARNED = new HashSet<>();
    private static final AtomicBoolean HEAD_LOOK_CONFIRMED = new AtomicBoolean();
    private static PalAnimationSettings settings;
    private static PalNaturalSettings naturalSettings;
    private static Map<PalAnimationStateSelector.Locomotion,PalSmoothClip> naturalClips=Map.of();
    private static boolean active;
    private static boolean thirdPartyProviderActive;

    private PalPlayerAnimationIntegration() { }

    public static void initialize(Path configDirectory) {
        settings = PalAnimationSettings.load(configDirectory);
        try {
            naturalSettings = PalNaturalSettings.load(configDirectory);
        } catch (RuntimeException e) {
            LOGGER.error("Natural motion settings rejected; keeping existing PAL clips.",e);
            naturalSettings = null;
        }
        thirdPartyProviderActive = settings.preferThirdPartyProvider()
                && FabricLoader.getInstance().isModLoaded(settings.thirdPartyProviderModId());
        if (thirdPartyProviderActive) {
            active = false;
            LOGGER.info("Third-party player animation provider '{}' is installed; MurimRPG PAL clips and experimental player renderers are safely bypassed.",
                    settings.thirdPartyProviderModId());
            return;
        }
        if (FabricLoader.getInstance().isModLoaded(settings.thirdPartyProviderModId())) {
            LOGGER.info("Third-party provider '{}' is installed but disabled by pal-player-animation.yml; using MurimRPG PAL+Bendable Cuboids skin-joint motions.",
                    settings.thirdPartyProviderModId());
        }
        boolean palLoaded = FabricLoader.getInstance().isModLoaded(PAL_MOD_ID);
        boolean bendLoaded = FabricLoader.getInstance().isModLoaded(BEND_MOD_ID);
        if (!settings.enabled()) {
            LOGGER.info("PAL player animation is disabled in pal-player-animation.yml; using the safe legacy/vanilla fallback.");
            return;
        }
        if (!palLoaded || !bendLoaded) {
            LOGGER.warn("PAL animation fallback: player_animation_library={}, bendable_cuboids={}. Both are required for visible joints.", palLoaded, bendLoaded);
            return;
        }

        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LOCOMOTION_LAYER, settings.locomotionPriority(), PalPlayerAnimationIntegration::locomotionController);
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                COMBAT_LAYER, settings.combatPriority(), PalPlayerAnimationIntegration::combatController);
        active = true;
        LOGGER.info("PAL 1.2.6 + Bendable Cuboids articulated animation integration registered.");
    }

    public static boolean isActive() {
        return active;
    }

    /** True when a configured third-party provider owns the vanilla player model pose. */
    public static boolean isThirdPartyProviderActive() {
        return thirdPartyProviderActive;
    }

    public static void verifyResources(Minecraft client) {
        if (!active) return;
        int found = 0;
        for (String name : new String[]{"pal_idle", "pal_walk", "pal_run", "pal_fast_run",
                "pal_jump_takeoff", "pal_jump_rise", "pal_jump_apex", "pal_jump_fall", "pal_jump_land",
                "pal_punch", "pal_punch_left", "pal_boxing_guard", "pal_sword_swing"}) {
            Identifier animation = id(name);
            if (PlayerAnimResources.hasAnimation(animation)) found++;
            else LOGGER.error("Missing PAL animation resource: {}", animation);
        }
        if (found != 13) {
            active = false;
            LOGGER.error("PAL animation resource validation failed ({}/13); restored vanilla/legacy renderer fallback.", found);
        } else {
            LOGGER.info("Validated all 13 MurimRPG PAL animations, including connected-joint boxing guard and alternating punches.");
            loadNaturalClips(client);
        }
    }

    private static void loadNaturalClips(Minecraft client) {
        naturalClips=Map.of();
        if(naturalSettings==null||!naturalSettings.enabled()) return;
        var loaded=new EnumMap<PalAnimationStateSelector.Locomotion,PalSmoothClip>(PalAnimationStateSelector.Locomotion.class);
        try {
            for(var state:PalAnimationStateSelector.Locomotion.values()) {
                String name=animationName(state);
                if(name==null) continue;
                var resource=client.getResourceManager().getResource(id("player_animations/"+name+".json"))
                        .orElseThrow(()->new IllegalArgumentException("Missing natural source: "+name));
                try(var reader=new java.io.InputStreamReader(resource.open(),java.nio.charset.StandardCharsets.UTF_8)) {
                    loaded.put(state,new PalSmoothClip(reader));
                }
            }
            naturalClips=Map.copyOf(loaded);
            LOGGER.info(naturalSettings.distanceCadence()
                            ? "Natural motion v2 ready: {} validated clips, distance-driven cadence, walk/run/fast cycles={}/{}/{} blocks; legacy shoulder cache bypassed."
                            : "Natural motion v2 ready: {} validated clips, fixed-period cadence, walk/run/fast cycles={}/{}/{} ticks; legacy shoulder cache bypassed.",
                    loaded.size(),
                    naturalSettings.distanceCadence()?naturalSettings.walkCycleBlocks():naturalSettings.walkTicks(),
                    naturalSettings.distanceCadence()?naturalSettings.runCycleBlocks():naturalSettings.runTicks(),
                    naturalSettings.distanceCadence()?naturalSettings.fastCycleBlocks():naturalSettings.fastTicks());
        } catch(Exception e) {
            LOGGER.error("Natural motion resources rejected; retaining PAL fallback.",e);
        }
    }

    private static String animationName(PalAnimationStateSelector.Locomotion state) {
        return switch(state) {
            case IDLE -> "pal_idle"; case WALK -> "pal_walk"; case RUN -> "pal_run";
            case FAST_RUN -> "pal_fast_run"; case JUMP_TAKEOFF -> "pal_jump_takeoff";
            case JUMP_RISE -> "pal_jump_rise"; case JUMP_APEX -> "pal_jump_apex";
            case JUMP_FALL -> "pal_jump_fall"; case JUMP_LAND -> "pal_jump_land";
            case VANILLA -> null;
        };
    }

    private static PlayerAnimationController locomotionController(Avatar avatar) {
        var tracker = new PalLocomotionTracker(naturalSettings!=null && naturalSettings.enabled()
                ? naturalSettings.minimumFallTicks() : 0);
        var previous = new PalAnimationStateSelector.Locomotion[]{null};
        var controller = new PlayerAnimationController(avatar, (current, data, setter) -> {
            boolean compatible = isCompatible(avatar);
            double speed = avatar.getDeltaMovement().horizontalDistance();
            double verticalSpeed = avatar.getDeltaMovement().y;
            var selected = tracker.select(compatible, avatar.onGround(), avatar.isSprinting(), speed,
                    verticalSpeed, avatar.tickCount, settings);
            String animation = animationName(selected);
            if (animation == null) {
                previous[0] = null;
                current.stopTriggeredAnimation();
                return PlayState.STOP;
            }
            RawAnimation clip = clip(animation, loopType(selected));
            if (clip == null) return PlayState.STOP;
            if (previous[0] != selected) {
                int fadeTicks = transitionTicks(previous[0], selected);
                previous[0] = selected;
                if (fadeTicks > 0 && current.isActive()) {
                    current.replaceAnimationWithFade(
                            AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE),
                            clip, false);
                    return PlayState.CONTINUE;
                }
            }
            return setter.setAnimation(clip);
        });
        controller.receiveTriggeredAnimations();
        if(naturalSettings!=null && naturalSettings.enabled()) {
            controller.addModifierLast(new PalNaturalMotionModifier(avatar,()->previous[0],naturalSettings,()->naturalClips));
        } else controller.addModifierLast(new PalWholeBodyFollowModifier(() -> previous[0], settings));
        controller.addModifierBefore(new PalHeadLookModifier());
        preserveVanillaHeadLook(controller);
        controller.setFirstPersonMode(FirstPersonMode.DISABLED);
        controller.setFirstPersonTransitionLength(0);
        return controller;
    }

    private static PlayerAnimationController combatController(Avatar avatar) {
        var boxing = new PalBoxingTracker();
        var controller = new PlayerAnimationController(avatar, (current, data, setter) -> {
            boolean compatible = isCompatible(avatar) && !avatar.isUsingItem();
            boolean sword = avatar.getMainHandItem().is(ItemTags.SWORDS);
            String animation;
            Animation.LoopType loopType;
            if (compatible && avatar.swinging && sword) {
                animation = "pal_sword_swing";
                loopType = Animation.LoopType.PLAY_ONCE;
            } else {
                boolean emptyHands = avatar.getMainHandItem().isEmpty() && avatar.getOffhandItem().isEmpty();
                var pose = boxing.select(compatible, emptyHands, avatar.swinging,
                        avatar.getAttackAnim(1.0F), avatar.tickCount,
                        settings.boxingGuardHoldTicks(), settings.boxingRandomHands(),
                        ThreadLocalRandom.current().nextBoolean());
                animation = switch (pose) {
                    case PUNCH_RIGHT -> "pal_punch";
                    case PUNCH_LEFT -> "pal_punch_left";
                    case GUARD -> "pal_boxing_guard";
                    case NONE -> null;
                };
                loopType = pose == PalBoxingTracker.Pose.GUARD
                        ? Animation.LoopType.LOOP : Animation.LoopType.PLAY_ONCE;
            }
            if (animation == null) return PlayState.STOP;
            RawAnimation clip = clip(animation, loopType);
            return clip == null ? PlayState.STOP : setter.setAnimation(clip);
        });
        controller.addModifierBefore(new PalHeadLookModifier());
        preserveVanillaHeadLook(controller);
        controller.setFirstPersonMode(FirstPersonMode.DISABLED);
        controller.setFirstPersonTransitionLength(0);
        return controller;
    }

    /**
     * PAL begins each render from Mojang's already-computed head yaw and pitch. The
     * authored clips contain small cinematic head counter-rotations, but enabling
     * those axes replaces the live F5 look instead of adding to it. Keep all three
     * rotation axes disabled in both PAL layers so the mouse-driven head remains
     * authoritative while the torso, arms, legs and Bendable hinges still animate.
     */
    private static void preserveVanillaHeadLook(PlayerAnimationController controller) {
        controller.setPostAnimationSetupConsumer(bones -> {
            var head = bones.apply("head");
            if (head == null) return;
            head.rotXEnabled = false;
            head.rotYEnabled = false;
            head.rotZEnabled = false;
            if (HEAD_LOOK_CONFIRMED.compareAndSet(false, true)) {
                LOGGER.info("PAL head rotation channels disabled; Mojang F5 yaw/pitch remains authoritative.");
            }
        });
    }

    private static boolean isCompatible(Avatar avatar) {
        if (settings.localPlayerOnly() && Minecraft.getInstance().player != avatar) return false;
        return !avatar.isDeadOrDying() && !avatar.isPassenger() && !avatar.isCrouching()
                && !avatar.isSwimming() && !avatar.isVisuallySwimming() && !avatar.isFallFlying()
                && !avatar.isSleeping();
    }

    private static Animation.LoopType loopType(PalAnimationStateSelector.Locomotion state) {
        return switch (state) {
            case JUMP_TAKEOFF, JUMP_LAND -> Animation.LoopType.PLAY_ONCE;
            case JUMP_RISE, JUMP_APEX, JUMP_FALL -> Animation.LoopType.HOLD_ON_LAST_FRAME;
            case IDLE, WALK, RUN, FAST_RUN -> Animation.LoopType.LOOP;
            case VANILLA -> Animation.LoopType.DEFAULT;
        };
    }

    private static int transitionTicks(PalAnimationStateSelector.Locomotion previous,
                                       PalAnimationStateSelector.Locomotion next) {
        if (next == PalAnimationStateSelector.Locomotion.JUMP_LAND) return settings.landingFadeTicks();
        if (PalAnimationStateSelector.isAirborne(next)
                || previous != null && PalAnimationStateSelector.isAirborne(previous)) {
            return settings.airborneFadeTicks();
        }
        return settings.locomotionFadeTicks();
    }

    private static RawAnimation clip(String name, Animation.LoopType loopType) {
        Identifier id = id(name);
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) {
            if (MISSING_WARNED.add(id)) LOGGER.warn("PAL ignored unknown MurimRPG animation {}; vanilla fallback remains active.", id);
            return null;
        }
        return RawAnimation.begin().then(animation, loopType);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("murimrpg", path);
    }
}
