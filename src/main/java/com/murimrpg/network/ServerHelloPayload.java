package com.murimrpg.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client compatibility marker; no gameplay data is sent in this foundation step. */
public record ServerHelloPayload(String protocolVersion, String modId) implements CustomPacketPayload {
    public static final Type<ServerHelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("murimrpg", "server_hello"));
    public static final StreamCodec<ByteBuf, ServerHelloPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), ServerHelloPayload::protocolVersion,
            ByteBufCodecs.stringUtf8(64), ServerHelloPayload::modId,
            ServerHelloPayload::new);

    @Override public Type<ServerHelloPayload> type() {
        return TYPE;
    }
}
