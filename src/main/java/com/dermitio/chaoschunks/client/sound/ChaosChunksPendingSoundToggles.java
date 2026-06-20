package com.dermitio.chaoschunks.client.sound;

import java.util.HashMap;
import java.util.Map;

public final class ChaosChunksPendingSoundToggles {

    private static final Map<String, Boolean> QUEUED = new HashMap<>();

    private ChaosChunksPendingSoundToggles() {}

    public static void queue(String key, boolean value) {
        QUEUED.put(key, value);
    }

    public static Map<String, Boolean> drain() {
        Map<String, Boolean> copy = new HashMap<>(QUEUED);
        QUEUED.clear();
        return copy;
    }
}
