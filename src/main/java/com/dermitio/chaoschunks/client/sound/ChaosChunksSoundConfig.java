package com.dermitio.chaoschunks.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
// =========
// Client-side sound catalog, enable-state persistence, and preview playback //
// =========
public final class ChaosChunksSoundConfig {

    private static final Map<String, Boolean> ENABLED = new HashMap<>();
    private static final String OVER_THE_CHUNK_MODE_KEY = "_over_the_chunk_mode";
    private static final String LAVENDER_FIELDS_KEY = "lavender_fields";
    private static OverTheChunkMode overTheChunkMode = OverTheChunkMode.MIXED;
    private static SoundInstance currentPreview;
    private static final List<SoundInstance> currentPreviewSounds = new ArrayList<>();

    private ChaosChunksSoundConfig() {}

    public record Entry(String key, Identifier soundId, boolean music) {}

    public enum OverTheChunkMode {
        MIXED("Mixed"),
        SEPARATED("Separated");

        private final String label;

        OverTheChunkMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public OverTheChunkMode next() {
            return this == MIXED ? SEPARATED : MIXED;
        }

        public static OverTheChunkMode fromSerialized(String value) {
            if (value == null) {
                return MIXED;
            }

            try {
                return OverTheChunkMode.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return MIXED;
            }
        }
    }

    // =========
    // Reads sounds.json and separates streamed music entries from one-shot sound effects //
    // =========
    public static List<Entry> loadEntries(boolean wantMusic) {
        List<Entry> out = new ArrayList<>();

        try {
            Minecraft mc = Minecraft.getInstance();
            ResourceManager resourceManager = mc.getResourceManager();

            Identifier soundsJson = Identifier.fromNamespaceAndPath("chaoschunks", "sounds.json");

            try (Reader reader = resourceManager.openAsReader(soundsJson)) {
                JsonObject root = GsonHelper.parse(reader);

                for (Map.Entry<String, JsonElement> top : root.entrySet()) {
                    String key = top.getKey();
                    JsonObject eventObj = top.getValue().getAsJsonObject();

                    if (!eventObj.has("sounds")) {
                        continue;
                    }

                    JsonArray sounds = eventObj.getAsJsonArray("sounds");
                    if (sounds.isEmpty()) {
                        continue;
                    }

                    for (JsonElement soundElem : sounds) {
                        Identifier soundId = null;
                        boolean isMusic = false;

                        if (soundElem.isJsonPrimitive()) {
                            String name = soundElem.getAsString();
                            soundId = Identifier.parse(name);

                            // Path-based fallback keeps older sounds.json entries classified correctly.
                            if (soundId.getPath().startsWith("music/")) {
                                isMusic = true;
                            }

                        } else if (soundElem.isJsonObject()) {
                            JsonObject soundObj = soundElem.getAsJsonObject();

                            if (!soundObj.has("name")) {
                                continue;
                            }

                            soundId = Identifier.parse(GsonHelper.getAsString(soundObj, "name"));

                            // Streamed entries are treated as music even if their path is not under music/.
                            isMusic = GsonHelper.getAsBoolean(soundObj, "stream", false)
                                    || soundId.getPath().startsWith("music/");
                        }

                        if (soundId == null) {
                            continue;
                        }

                        if (isMusic == wantMusic) {
                            out.add(new Entry(key, soundId, isMusic));
                            ENABLED.putIfAbsent(key, true);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return out;
    }

    public static boolean isEnabled(String key) {
        return ENABLED.getOrDefault(key, true);
    }

    public static void setEnabled(String key, boolean enabled) {
        ENABLED.put(key, enabled);
    }

    public static void toggle(String key) {
        ENABLED.put(key, !isEnabled(key));
    }

    public static OverTheChunkMode overTheChunkMode() {
        return overTheChunkMode;
    }

    public static void setOverTheChunkMode(OverTheChunkMode mode) {
        overTheChunkMode = mode == null ? OverTheChunkMode.MIXED : mode;
    }

    public static void cycleOverTheChunkMode() {
        overTheChunkMode = overTheChunkMode.next();
    }

// =========
// Plays a UI preview for the selected configured sound event //
// =========
public static void playPreview(Entry entry) {
    stopPreview();

    Minecraft mc = Minecraft.getInstance();
    SoundManager soundManager = mc.getSoundManager();

    Identifier eventId = Identifier.fromNamespaceAndPath("chaoschunks", entry.key());
    SoundEvent event = SoundEvent.createVariableRangeEvent(eventId);

    if (LAVENDER_FIELDS_KEY.equals(entry.key())) {
        LavenderFieldsPreviewSound lavenderPreview = new LavenderFieldsPreviewSound(event, 1);
        currentPreview = lavenderPreview;
        trackPreviewSound(lavenderPreview);
        soundManager.play(lavenderPreview);
        return;
    }

    currentPreview = SimpleSoundInstance.forUI(event, 1.0F, 1.0F);
    trackPreviewSound(currentPreview);
    soundManager.play(currentPreview);
}

    public static void stopPreview() {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        for (SoundInstance sound : List.copyOf(currentPreviewSounds)) {
            soundManager.stop(sound);
        }
        currentPreviewSounds.clear();
        currentPreview = null;
    }
    public static void save() {
    try {
        Path file = Paths.get("config/chaoschunks_sound_config.json");
        Files.createDirectories(file.getParent());

        JsonObject root = new JsonObject();
        for (Map.Entry<String, Boolean> entry : ENABLED.entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
        }
        root.addProperty(OVER_THE_CHUNK_MODE_KEY, overTheChunkMode.name().toLowerCase());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(file, gson.toJson(root));
    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static void load() {
    try {
        Path file = Paths.get("config/chaoschunks_sound_config.json");
        if (!Files.exists(file)) return;

        Gson gson = new Gson();
        ENABLED.clear();
        overTheChunkMode = OverTheChunkMode.MIXED;

        JsonObject root = gson.fromJson(Files.readString(file), JsonObject.class);
        if (root != null) {
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (OVER_THE_CHUNK_MODE_KEY.equals(entry.getKey())) {
                    overTheChunkMode = OverTheChunkMode.fromSerialized(entry.getValue().getAsString());
                } else if (entry.getValue().isJsonPrimitive()) {
                    ENABLED.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}
public static Map<String, Boolean> snapshotEnabled() {
    return new HashMap<>(ENABLED);
}

private static void trackPreviewSound(SoundInstance sound) {
    currentPreviewSounds.add(sound);
}

private static void playLavenderRepeat(SoundEvent event, int repeat) {
    LavenderFieldsPreviewSound sound = new LavenderFieldsPreviewSound(event, repeat);
    currentPreview = sound;
    trackPreviewSound(sound);
    Minecraft.getInstance().getSoundManager().playDelayed(sound, 0);
}

private static final class LavenderFieldsPreviewSound extends AbstractTickableSoundInstance {
    private static final int TRACK_TICKS = 156;
    private static final int LOOP_COUNT = 4;
    private static final int OVERLAP_TICKS = 4;
    private static final int CROSSFADE_TICKS = 6;
    private static final int WAVE_TICKS = 160;
    private static final float MIN_VOLUME_MULTIPLIER = 0.80F;

    private final SoundEvent event;
    private final int repeat;
    private int age;
    private boolean spawnedNext;

    private LavenderFieldsPreviewSound(SoundEvent event, int repeat) {
        super(event, SoundSource.UI, SoundInstance.createUnseededRandom());
        this.event = event;
        this.repeat = repeat;
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = repeat > 1 ? 0.0F : 1.0F;
        this.pitch = 1.0F;
    }

    @Override
    public void tick() {
        this.age++;
        if (!this.spawnedNext && this.repeat < LOOP_COUNT && this.age >= TRACK_TICKS - OVERLAP_TICKS) {
            this.spawnedNext = true;
            playLavenderRepeat(this.event, this.repeat + 1);
        }

        this.volume = fadeMultiplier() * volumeWave(this.age + timeOffset());
        if (this.age >= TRACK_TICKS) {
            this.stop();
            currentPreviewSounds.remove(this);
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    private static int timeOffset() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (int) (mc.level.getGameTime() % WAVE_TICKS);
    }

    private float fadeMultiplier() {
        float fade = 1.0F;
        if (this.repeat > 1 && this.age < CROSSFADE_TICKS) {
            fade = Math.min(fade, this.age / (float) CROSSFADE_TICKS);
        }
        if (this.age >= TRACK_TICKS - CROSSFADE_TICKS) {
            fade = Math.min(fade, Math.max(0.0F, (TRACK_TICKS - this.age) / (float) CROSSFADE_TICKS));
        }
        return fade;
    }

    private static float volumeWave(int tick) {
        double wave = Math.sin((tick / (double) WAVE_TICKS) * Math.TAU);
        double normalized = (wave + 1.0D) * 0.5D;
        return (float) (MIN_VOLUME_MULTIPLIER + (1.0D - MIN_VOLUME_MULTIPLIER) * normalized);
    }
}
}
