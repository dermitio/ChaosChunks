package com.dermitio.chaoschunks.network.time;

import com.dermitio.chaoschunks.ChaosChunks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TimekeepEditPayload(
        Action action,
        int page,
        String id,
        float x,
        float y,
        String value
) implements CustomPacketPayload {

    public static final int MAX_ID_LENGTH = 128;
    public static final int MAX_VALUE_LENGTH = 65_536;
    private static final int MAX_PAGE_INDEX = 64;

    public enum Action {
        SET_NODE,
        SET_HTML_FILE,
        SET_ICON_ITEM,
        REMOVE_NODE,
        CONNECT,
        CLEAR_PAGE,
        IMPORT_DATA
    }

    public static final Type<TimekeepEditPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "timekeep_edit"));

    public static final StreamCodec<FriendlyByteBuf, TimekeepEditPayload> STREAM_CODEC =
            StreamCodec.of(TimekeepEditPayload::write, TimekeepEditPayload::read);

    private static void write(FriendlyByteBuf buf, TimekeepEditPayload payload) {
        buf.writeEnum(payload.action());
        buf.writeVarInt(payload.page());
        buf.writeUtf(payload.id(), MAX_ID_LENGTH);
        buf.writeFloat(payload.x());
        buf.writeFloat(payload.y());
        buf.writeUtf(payload.value(), MAX_VALUE_LENGTH);
    }

    private static TimekeepEditPayload read(FriendlyByteBuf buf) {
        return new TimekeepEditPayload(
                buf.readEnum(Action.class),
                buf.readVarInt(),
                buf.readUtf(MAX_ID_LENGTH),
                buf.readFloat(),
                buf.readFloat(),
                buf.readUtf(MAX_VALUE_LENGTH)
        );
    }

    public boolean hasValidBounds() {
        if (page < 1 || page > MAX_PAGE_INDEX) return false;
        if (id == null || id.length() > MAX_ID_LENGTH) return false;
        if (value == null || value.length() > MAX_VALUE_LENGTH) return false;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return false;
        return action != null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
