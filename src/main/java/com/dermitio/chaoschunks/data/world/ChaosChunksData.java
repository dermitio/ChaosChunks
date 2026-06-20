package com.dermitio.chaoschunks.data.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.util.HashMap;
import java.util.Map;

// =========
// Persistent world-level ChaosChunks settings stored with the save data //
// =========
public class ChaosChunksData extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chaoschunks", "chaoschunks");

    public boolean enabled = false;

    public int regionX = 1;
    public int regionZ = 1;
    public String globalBiomes = "";

    public final Map<String, String> dimensionBiomes = new HashMap<>();
    public final Map<String, String> dimensionModes = new HashMap<>();
    public final Map<String, Long> dimensionSeedRandomizers = new HashMap<>();
    public final Map<String, Long> dimensionTerrainRandomizers = new HashMap<>();

    public ChaosChunksData() {}

    private ChaosChunksData(boolean enabled, int rx, int rz, String global,
                            Map<String, String> biomes,
                            Map<String, String> modes,
                            Map<String, Long> seedRandomizers,
                            Map<String, Long> terrainRandomizers) {
        this.enabled = enabled;
        this.regionX = rx;
        this.regionZ = rz;
        this.globalBiomes = (global == null) ? "" : global;
        this.dimensionBiomes.putAll(biomes);
        this.dimensionModes.putAll(modes);
        this.dimensionSeedRandomizers.putAll(seedRandomizers);
        this.dimensionTerrainRandomizers.putAll(terrainRandomizers);
    }

    // =========
    // Codec for loading older saves with default values for missing fields //
    // =========
    public static final Codec<ChaosChunksData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(d -> d.enabled),
            Codec.INT.optionalFieldOf("rx", 1).forGetter(d -> d.regionX),
            Codec.INT.optionalFieldOf("rz", 1).forGetter(d -> d.regionZ),
            Codec.STRING.optionalFieldOf("global", "").forGetter(d -> d.globalBiomes),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("dimBiomes", Map.of())
                    .forGetter(d -> d.dimensionBiomes),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("dimModes", Map.of())
                    .forGetter(d -> d.dimensionModes),
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("dimSeedRandomizers", Map.of())
                    .forGetter(d -> d.dimensionSeedRandomizers),
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("dimTerrainRandomizers", Map.of())
                    .forGetter(d -> d.dimensionTerrainRandomizers)
    ).apply(inst, ChaosChunksData::new));

    public static final SavedDataType<ChaosChunksData> TYPE =
            new SavedDataType<ChaosChunksData>(ID, ChaosChunksData::new, CODEC);

    public static ChaosChunksData get(SavedDataStorage storage) {
        return storage.computeIfAbsent(TYPE);
    }
}
