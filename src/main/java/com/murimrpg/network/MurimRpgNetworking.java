package com.murimrpg.network;

import com.murimrpg.GanghoImunrok;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.logging.Logger;

/** Registers the small common protocol that later custom client renderers will use. */
public final class MurimRpgNetworking {
    public static final String PROTOCOL_VERSION = "1";
    private static final Logger LOGGER = Logger.getLogger("MurimRPG-Network");
    private static boolean registered;

    private MurimRpgNetworking() {
    }

    public static synchronized void registerPayloadTypes() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ServerHelloPayload.TYPE, ServerHelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EffectPlayPayload.TYPE, EffectPlayPayload.CODEC);
        registered = true;
    }

    public static void sendServerHello(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ServerHelloPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new ServerHelloPayload(PROTOCOL_VERSION, GanghoImunrok.MOD_ID));
    }
    public static void sendEffect(ServerPlayer player, EffectPlayPayload payload) {
        if (ServerPlayNetworking.canSend(player, EffectPlayPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
            LOGGER.info("Sent visual effect " + payload.effectId() + " to " + player.getGameProfile().name());
        } else {
            LOGGER.info("Skipped visual effect " + payload.effectId() + "; client visual receiver is unavailable for " + player.getGameProfile().name());
        }
    }
}
