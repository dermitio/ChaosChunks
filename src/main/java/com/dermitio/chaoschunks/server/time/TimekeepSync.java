package com.dermitio.chaoschunks.server.time;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.data.time.TimekeepData;
import com.dermitio.chaoschunks.network.time.TimekeepDataPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// =========
// Sends persistent Timekeep page data to clients that can render the custom book //
// =========
public final class TimekeepSync {

    private TimekeepSync() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TimekeepSync::onPlayerLoggedIn);
    }

    public static void sync(ServerPlayer player) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;
        if (!player.connection.hasChannel(TimekeepDataPayload.TYPE)) return;

        TimekeepData data = TimekeepData.get(player.level().getServer().overworld().getDataStorage());
        PacketDistributor.sendToPlayer(player, new TimekeepDataPayload(data.pages(), data.editingEnabled));
    }

    public static void syncAll(MinecraftServer server) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        TimekeepData data = TimekeepData.get(server.overworld().getDataStorage());
        TimekeepDataPayload payload = new TimekeepDataPayload(data.pages(), data.editingEnabled);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.connection.hasChannel(TimekeepDataPayload.TYPE)) continue;
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
