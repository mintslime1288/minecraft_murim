package com.murimrpg.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative visual request. It carries no combat or inventory state. */
public record EffectPlayPayload(String effectId, double x, double y, double z, float scale, int durationTicks, int particleCount, long seed)
        implements CustomPacketPayload {
    public static final Type<EffectPlayPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("murimrpg", "effect_play"));
    public static final StreamCodec<ByteBuf, EffectPlayPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), EffectPlayPayload::effectId, ByteBufCodecs.DOUBLE, EffectPlayPayload::x,
            ByteBufCodecs.DOUBLE, EffectPlayPayload::y, ByteBufCodecs.DOUBLE, EffectPlayPayload::z,
            ByteBufCodecs.FLOAT, EffectPlayPayload::scale, ByteBufCodecs.VAR_INT, EffectPlayPayload::durationTicks, ByteBufCodecs.VAR_INT, EffectPlayPayload::particleCount,
            ByteBufCodecs.VAR_LONG, EffectPlayPayload::seed, EffectPlayPayload::new);
    @Override public Type<EffectPlayPayload> type() { return TYPE; }
}
