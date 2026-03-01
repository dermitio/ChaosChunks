package com.dermitio.chaoschunks.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;

public class ChaosChunksData extends SavedData {

    // ** Defines the storage ID used to save and load ChaosChunks world data **
    public static final String ID = "chaoschunks";

    // ** Stores global region size configuration for chaos generation **
    public int regionX = 1;
    public int regionZ = 1;

    // ** Stores the global biome list applied when no dimension-specific override exists **
    public String globalBiomes = "";

    // ** Stores per-dimension biome lists keyed by dimension ID **
    public final Map<String, String> dimensionBiomes = new HashMap<>();

    // ** Stores per-dimension generation modes keyed by dimension ID **
    public final Map<String, String> dimensionModes = new HashMap<>();

    // ** Default constructor used when creating fresh saved data **
    public ChaosChunksData() {}

    // ** Constructor used by the codec to rebuild saved data from disk **
    private ChaosChunksData(int rx, int rz, String global,
                            Map<String, String> biomes,
                            Map<String, String> modes) {
        this.regionX = rx;
        this.regionZ = rz;
        this.globalBiomes = (global == null) ? "" : global;
        this.dimensionBiomes.putAll(biomes);
        this.dimensionModes.putAll(modes);
    }

    // ** Codec defining how ChaosChunks data is serialized and deserialized **
    public static final Codec<ChaosChunksData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("rx", 1).forGetter(d -> d.regionX),
            Codec.INT.optionalFieldOf("rz", 1).forGetter(d -> d.regionZ),
            Codec.STRING.optionalFieldOf("global", "").forGetter(d -> d.globalBiomes),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("dimBiomes", Map.of())
                    .forGetter(d -> d.dimensionBiomes),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("dimModes", Map.of())
                    .forGetter(d -> d.dimensionModes)
    ).apply(inst, ChaosChunksData::new));

    // ** Declares the SavedData type so Minecraft can manage persistence automatically **
    public static final SavedDataType<ChaosChunksData> TYPE =
            new SavedDataType<>(ID, ChaosChunksData::new, CODEC);

    // ** Retrieves or creates the ChaosChunks data instance from dimension storage **
    public static ChaosChunksData get(DimensionDataStorage storage) {
        return storage.computeIfAbsent(TYPE);
    }
}
