package com.dermitio.chaoschunks.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// =========
// Client-side toggles for date-based UI event themes //
// =========
public final class ChaosChunksUiEventConfig {

    private static final Map<String, Boolean> ENABLED = new HashMap<>();

    static {
        resetDefaults();
    }

    private ChaosChunksUiEventConfig() {}

    public record Entry(String key, String label) {}

    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry("pride_month", "Pride Month"));
        out.add(new Entry("new_year", "New Year"));
        out.add(new Entry("halloween", "Halloween"));
        out.add(new Entry("a_leader", "A Leader"));
        out.add(new Entry("april_1", "April 1"));
        return out;
    }

    public static boolean isEnabled(String key) {
        return ENABLED.getOrDefault(key, defaultEnabled(key));
    }

    public static void setEnabled(String key, boolean enabled) {
        ENABLED.put(key, enabled);
    }

    public static void save() {
        try {
            Path file = Paths.get("config/chaoschunks_ui_events.json");
            Files.createDirectories(file.getParent());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(ENABLED));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        resetDefaults();

        try {
            Path file = Paths.get("config/chaoschunks_ui_events.json");
            if (!Files.exists(file)) return;

            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Boolean>>(){}.getType();
            Map<String, Boolean> loaded = gson.fromJson(Files.readString(file), type);
            if (loaded != null) ENABLED.putAll(loaded);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void resetDefaults() {
        ENABLED.clear();
        for (Entry entry : entries()) {
            ENABLED.put(entry.key(), defaultEnabled(entry.key()));
        }
    }

    private static boolean defaultEnabled(String key) {
        return !"a_leader".equals(key);
    }
}
