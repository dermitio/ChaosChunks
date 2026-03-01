package com.dermitio.chaoschunks.server;

import java.util.HashMap;
import java.util.Map;

public final class ChaosChunksPendingConfig {

    // ** Returns the current pending configuration without clearing it **
public static Pending peek() {
    return pending;
}

    // ** Immutable record storing worldgen settings chosen in the UI **
    public record Pending(int regionX, int regionZ, String globalBiomes,
                          Map<String, String> dimensionBiomes,
                          Map<String, String> dimensionModes) {}

    // ** Holds pending configuration until the server consumes it **
    private static volatile Pending pending;

    // ** Prevents instantiation since this class only manages static state **
    private ChaosChunksPendingConfig() {}

    // ** Stores a new pending configuration snapshot **
    public static void set(int rx, int rz, String global,
                           Map<String, String> dimBiomes,
                           Map<String, String> dimModes) {
        pending = new Pending(
                rx,
                rz,
                (global == null) ? "" : global,
                new HashMap<>(dimBiomes),
                new HashMap<>(dimModes)
        );
    }

    // ** Returns and clears the pending configuration so it is applied once **
    public static Pending consume() {
        Pending p = pending;
        pending = null;
        return p;
    }
}
