package com.dermitio.chaoschunks.client;

import com.dermitio.chaoschunks.client.time.TimekeepClientData;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.network.time.TimekeepDataPayload;

public final class ChaosChunksClientPayloads {

    private ChaosChunksClientPayloads() {}

    public static void handleTimekeepData(TimekeepDataPayload payload) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) {
            TimekeepClientData.set(java.util.List.of(), false);
            return;
        }

        TimekeepClientData.set(payload.pages(), payload.editingEnabled());
    }
}
