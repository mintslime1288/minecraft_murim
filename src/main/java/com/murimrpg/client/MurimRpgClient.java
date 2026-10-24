package com.murimrpg.client;

import com.murimrpg.network.MurimRpgNetworking;
import com.murimrpg.network.ServerHelloPayload;
import com.murimrpg.network.EffectPlayPayload;
import com.murimrpg.client.render.TrainingDummyGlbRenderer;
import com.murimrpg.client.render.AnimatedPlayerGlbRenderer;
import com.murimrpg.client.render.ArticulatedPlayerRenderer;
import com.murimrpg.client.render.ModelPartArticulatedPlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import java.util.logging.Logger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/** Client entrypoint. GLB loading and rendering will be added after a licensed source asset is supplied. */
public final class MurimRpgClient implements ClientModInitializer {
    private static final Logger LOGGER = Logger.getLogger("MurimRPG-Client");
    private static final ClientConnectionState CONNECTION = new ClientConnectionState();
    private static PlayerAnimationService playerAnimations;
    private static int resourceValidationTicks;
    private static boolean resourceValidationComplete;

    @Override public void onInitializeClient() {
        // The dedicated server registers this from its common initializer. A client
        // must register the clientbound payload types without starting server storage.
        MurimRpgNetworking.registerPayloadTypes();
        ClientVisualSettings settings = ClientVisualSettings.load(FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
        PalPlayerAnimationIntegration.initialize(FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
        playerAnimations = new PlayerAnimationService(settings,
                FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
        LocalMovementVisualService footstepVisuals = new LocalMovementVisualService(settings);
        LOGGER.info("MurimRPG walk/run controller initialized; third-party provider active="
                + PalPlayerAnimationIntegration.isThirdPartyProviderActive());
        NaturalWalkDiagnostics.register(playerAnimations::reload);
        TrainingDummyGlbRenderer.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(TrainingDummyGlbRenderer::loadResources);
        // Registered unconditionally, unlike the older GLB path above, because the Wuxia
        // replacement is meant to run while PAL is the active body. It gates itself entirely on
        // its own YAML, so with enabled:false this costs one file read at startup and nothing else.
        ClientLifecycleEvents.CLIENT_STARTED.register(
                com.murimrpg.client.render.WuxiaPlayerRenderer::loadResources);
        if (!PalPlayerAnimationIntegration.isThirdPartyProviderActive()) {
            ClientLifecycleEvents.CLIENT_STARTED.register(playerAnimations::loadAnimations);
            ClientLifecycleEvents.CLIENT_STARTED.register(AnimatedPlayerGlbRenderer::loadResources);
            ClientLifecycleEvents.CLIENT_STARTED.register(ArticulatedPlayerRenderer::loadResources);
            ClientLifecycleEvents.CLIENT_STARTED.register(ModelPartArticulatedPlayerRenderer::loadResources);
        }
        // PlayerAnimResources is populated during the first client resource reload, which
        // happens after CLIENT_STARTED. Validate on a later tick instead of disabling all
        // clips during that startup race.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            footstepVisuals.tick(client);
            if (!resourceValidationComplete && ++resourceValidationTicks >= 40) {
                PalPlayerAnimationIntegration.verifyResources(client);
                resourceValidationComplete = true;
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ServerHelloPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    CONNECTION.acceptServerHello(payload.protocolVersion());
                    if (CONNECTION.isCompatible()) {
                        LOGGER.info("MurimRPG client protocol confirmed: " + payload.protocolVersion());
                    } else {
                        LOGGER.warning("MurimRPG protocol mismatch: client=" + MurimRpgNetworking.PROTOCOL_VERSION
                                + ", server=" + payload.protocolVersion());
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CONNECTION.clear());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (client.player == null) return;
            client.player.sendSystemMessage(Component.literal(TrainingDummyGlbRenderer.isActive()
                    ? "[강호] 고급 통나무 허수아비 3D 렌더러가 활성화되었습니다."
                    : "[강호] 통나무 허수아비 3D 렌더러를 불러오지 못했습니다. 로그를 확인하세요."));
        }));
        ClientPlayNetworking.registerGlobalReceiver(EffectPlayPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            // A registered custom payload proves this server understands our visual protocol.
            // Do not hide an effect only because the optional hello packet arrived late.
            if (context.client().level == null) return;
            int count = Math.min(96, Math.max(1, payload.particleCount()));
            var particle = switch (payload.effectId()) {
                case "WOODEN_SWORD_SWING" -> ParticleTypes.SWEEP_ATTACK;
                case "TRAINING_DUMMY_HIT" -> ParticleTypes.CRIT;
                case "FIST_IMPACT" -> ParticleTypes.CRIT;
                case "BASIC_SWORD_HIT" -> ParticleTypes.SWEEP_ATTACK;
                case "REALM_BREAKTHROUGH_SUCCESS" -> ParticleTypes.END_ROD;
                case "REALM_BREAKTHROUGH_FAIL" -> ParticleTypes.SMOKE;
                default -> null;
            };
            if (particle == null) { LOGGER.warning("Ignoring unknown MurimRPG effect: " + payload.effectId()); return; }
            for (int index = 0; index < count; index++) {
                double angle = (Math.PI * 2.0 * index / count) + (payload.seed() & 31) * .1;
                double radius = payload.scale() * (0.12 + (index % 4) * .025);
                context.client().level.addParticle(particle, payload.x() + Math.cos(angle) * radius, payload.y() + (index % 3) * .035,
                        payload.z() + Math.sin(angle) * radius, Math.cos(angle) * .025, .025 + (index % 2) * .015, Math.sin(angle) * .025);
            }
            if (context.client().player != null) {
                if (payload.effectId().equals("WOODEN_SWORD_SWING") || payload.effectId().equals("BASIC_SWORD_HIT")) context.client().player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, .55f, 1.15f);
                else if (payload.effectId().equals("FIST_IMPACT")) context.client().player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, .42f, 1.05f);
                else if (payload.effectId().equals("TRAINING_DUMMY_HIT")) context.client().player.playSound(SoundEvents.WOOD_HIT, .7f, .85f);
                else if (payload.effectId().equals("REALM_BREAKTHROUGH_SUCCESS")) context.client().player.playSound(SoundEvents.BEACON_ACTIVATE, .55f, 1.35f);
            }
            LOGGER.info("Played MurimRPG visual effect: " + payload.effectId());
        }));
        LOGGER.info("MurimRPG client foundation initialized.");
    }

    public static ClientConnectionState connection() {
        return CONNECTION;
    }

    /** Used only by the client-side player-model mixin after this entrypoint has initialized. */
    public static PlayerAnimationService playerAnimations() {
        return playerAnimations;
    }
}
