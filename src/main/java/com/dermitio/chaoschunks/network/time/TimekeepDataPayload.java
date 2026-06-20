package com.dermitio.chaoschunks.network.time;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.data.time.TimekeepData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record TimekeepDataPayload(List<TimekeepData.Page> pages, boolean editingEnabled) implements CustomPacketPayload {

    private static final int MAX_PAGES = 16;
    private static final int MAX_NODES_PER_PAGE = 256;
    private static final int MAX_CONNECTIONS_PER_PAGE = 512;
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_HTML_LENGTH = 16_384;
    private static final int MAX_FIELD_LENGTH = 256;

    public static final Type<TimekeepDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "timekeep_data"));

    public static final StreamCodec<FriendlyByteBuf, TimekeepDataPayload> STREAM_CODEC =
            StreamCodec.of(TimekeepDataPayload::write, TimekeepDataPayload::read);

    public TimekeepDataPayload {
        pages = List.copyOf(pages);
    }

    private static void write(FriendlyByteBuf buf, TimekeepDataPayload payload) {
        buf.writeBoolean(payload.editingEnabled());
        List<TimekeepData.Page> pages = payload.pages().subList(0, Math.min(payload.pages().size(), MAX_PAGES));
        buf.writeVarInt(pages.size());
        for (TimekeepData.Page page : pages) {
            buf.writeVarInt(page.index());
            List<TimekeepData.Node> nodes = page.nodes().subList(0, Math.min(page.nodes().size(), MAX_NODES_PER_PAGE));
            buf.writeVarInt(nodes.size());
            for (TimekeepData.Node node : nodes) {
                buf.writeUtf(node.id(), MAX_ID_LENGTH);
                buf.writeFloat(node.x());
                buf.writeFloat(node.y());
                buf.writeUtf(node.html(), MAX_HTML_LENGTH);
                buf.writeUtf(node.htmlFile(), MAX_FIELD_LENGTH);
                buf.writeUtf(node.iconItem(), MAX_FIELD_LENGTH);
                buf.writeUtf(node.unlockType(), MAX_FIELD_LENGTH);
                buf.writeUtf(node.unlockTarget(), MAX_FIELD_LENGTH);
                buf.writeBoolean(node.unlocked());
            }

            List<TimekeepData.Connection> connections =
                    page.connections().subList(0, Math.min(page.connections().size(), MAX_CONNECTIONS_PER_PAGE));
            buf.writeVarInt(connections.size());
            for (TimekeepData.Connection connection : connections) {
                buf.writeUtf(connection.from(), MAX_ID_LENGTH);
                buf.writeUtf(connection.to(), MAX_ID_LENGTH);
            }
        }
    }

    private static TimekeepDataPayload read(FriendlyByteBuf buf) {
        boolean editingEnabled = buf.readBoolean();
        int pageCount = buf.readVarInt();
        if (pageCount < 0 || pageCount > MAX_PAGES) {
            throw new IllegalArgumentException("Too many Timekeep pages: " + pageCount);
        }
        List<TimekeepData.Page> pages = new ArrayList<>(pageCount);

        for (int i = 0; i < pageCount; i++) {
            int index = buf.readVarInt();

            int nodeCount = buf.readVarInt();
            if (nodeCount < 0 || nodeCount > MAX_NODES_PER_PAGE) {
                throw new IllegalArgumentException("Too many Timekeep nodes: " + nodeCount);
            }
            List<TimekeepData.Node> nodes = new ArrayList<>(nodeCount);
            for (int n = 0; n < nodeCount; n++) {
                nodes.add(new TimekeepData.Node(
                        buf.readUtf(MAX_ID_LENGTH),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readUtf(MAX_HTML_LENGTH),
                        buf.readUtf(MAX_FIELD_LENGTH),
                        buf.readUtf(MAX_FIELD_LENGTH),
                        buf.readUtf(MAX_FIELD_LENGTH),
                        buf.readUtf(MAX_FIELD_LENGTH),
                        buf.readBoolean()
                ));
            }

            int connectionCount = buf.readVarInt();
            if (connectionCount < 0 || connectionCount > MAX_CONNECTIONS_PER_PAGE) {
                throw new IllegalArgumentException("Too many Timekeep connections: " + connectionCount);
            }
            List<TimekeepData.Connection> connections = new ArrayList<>(connectionCount);
            for (int c = 0; c < connectionCount; c++) {
                connections.add(new TimekeepData.Connection(buf.readUtf(MAX_ID_LENGTH), buf.readUtf(MAX_ID_LENGTH)));
            }

            pages.add(new TimekeepData.Page(index, nodes, connections));
        }

        return new TimekeepDataPayload(pages, editingEnabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
