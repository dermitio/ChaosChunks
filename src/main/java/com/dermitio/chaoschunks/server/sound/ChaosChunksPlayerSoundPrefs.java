package com.dermitio.chaoschunks.server.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChaosChunksPlayerSoundPrefs {

    private static final Map<UUID, Map<String, Boolean>> PREFS = new ConcurrentHashMap<>();

    private ChaosChunksPlayerSoundPrefs() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> save());
    }

    public static void load() {
        try {
            Path file = Paths.get("config/chaoschunks_sound_prefs.json");
            if (!Files.exists(file)) return;

            Gson gson = new Gson();
            Type type = new TypeToken<Map<UUID, Map<String, Boolean>>>(){}.getType();
            Map<UUID, Map<String, Boolean>> loaded = gson.fromJson(Files.readString(file), type);

            PREFS.clear();
            if (loaded != null) {
                PREFS.putAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isEnabled(UUID playerId, String eventKey) {
        Map<String, Boolean> map = PREFS.get(playerId);
        if (map == null) {
            return true;
        }
        return map.getOrDefault(eventKey, true);
    }

    public static void set(UUID playerId, String eventKey, boolean enabled) {
        PREFS.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(eventKey, enabled);
        save();
    }

    public static void clear(UUID playerId) {
        if (PREFS.remove(playerId) != null) {
            save();
        }
    }

    public static void save() {
        try {
            Path file = Paths.get("config/chaoschunks_sound_prefs.json");
            Files.createDirectories(file.getParent());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(PREFS));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
