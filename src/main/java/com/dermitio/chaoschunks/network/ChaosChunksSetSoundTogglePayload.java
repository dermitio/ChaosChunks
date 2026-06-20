package com.dermitio.chaoschunks.network;

import com.dermitio.chaoschunks.ChaosChunks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChaosChunksSetSoundTogglePayload(String eventKey, boolean enabled) implements CustomPacketPayload {

    public static final int MAX_EVENT_KEY_LENGTH = 128;

    public static final Type<ChaosChunksSetSoundTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "set_sound_toggle"));

    public static final StreamCodec<FriendlyByteBuf, ChaosChunksSetSoundTogglePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.eventKey(), MAX_EVENT_KEY_LENGTH);
                        buf.writeBoolean(payload.enabled());
                    },
                    buf -> new ChaosChunksSetSoundTogglePayload(buf.readUtf(MAX_EVENT_KEY_LENGTH), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
