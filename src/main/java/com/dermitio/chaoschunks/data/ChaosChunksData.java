package com.dermitio.chaoschunks.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;

public class ChaosChunksData extends SavedData {

    public static final String ID = "chaoschunks";

    // ChaosChunks worlds should be patched
    public boolean enabled = false;

    public int regionX = 1;
    public int regionZ = 1;
    public String globalBiomes = "";

    public final Map<String, String> dimensionBiomes = new HashMap<>();
    public final Map<String, String> dimensionModes = new HashMap<>();

    public ChaosChunksData() {}

    private ChaosChunksData(boolean enabled, int rx, int rz, String global,
                            Map<String, String> biomes,
                            Map<String, String> modes) {
        this.enabled = enabled;
        this.regionX = rx;
        this.regionZ = rz;
        this.globalBiomes = (global == null) ? "" : global;
        this.dimensionBiomes.putAll(biomes);
        this.dimensionModes.putAll(modes);
    }

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
                    .forGetter(d -> d.dimensionModes)
    ).apply(inst, ChaosChunksData::new));

    public static final SavedDataType<ChaosChunksData> TYPE =
            new SavedDataType<>(ID, ChaosChunksData::new, CODEC);

    public static ChaosChunksData get(DimensionDataStorage storage) {
        return storage.computeIfAbsent(TYPE);
    }
}
