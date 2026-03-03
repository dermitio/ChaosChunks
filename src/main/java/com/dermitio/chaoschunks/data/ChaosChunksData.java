package com.dermitio.chaoschunks.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import java.util.Map;
import java.util.HashMap;

public class ChaosChunksData extends SavedData {

    public static final String ID = "chaoschunks";

    public boolean enabled = false;
    public int regionX = 1;
    public int regionZ = 1;
    public String globalBiomes = "";

    public final Map<String, String> dimensionBiomes = new HashMap<>();
    public final Map<String, String> dimensionModes = new HashMap<>();

    public ChaosChunksData() {}

    public static ChaosChunksData load(CompoundTag tag, HolderLookup.Provider provider) {
        ChaosChunksData data = new ChaosChunksData();

        data.enabled = tag.getBoolean("enabled");
        data.regionX = tag.getInt("rx");
        data.regionZ = tag.getInt("rz");
        data.globalBiomes = tag.getString("global");

        CompoundTag biomesTag = tag.getCompound("dimBiomes");
        for (String key : biomesTag.getAllKeys()) {
            data.dimensionBiomes.put(key, biomesTag.getString(key));
        }

        CompoundTag modesTag = tag.getCompound("dimModes");
        for (String key : modesTag.getAllKeys()) {
            data.dimensionModes.put(key, modesTag.getString(key));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("enabled", enabled);
        tag.putInt("rx", regionX);
        tag.putInt("rz", regionZ);
        tag.putString("global", globalBiomes);

        CompoundTag biomesTag = new CompoundTag();
        dimensionBiomes.forEach(biomesTag::putString);
        tag.put("dimBiomes", biomesTag);

        CompoundTag modesTag = new CompoundTag();
        dimensionModes.forEach(modesTag::putString);
        tag.put("dimModes", modesTag);

        return tag;
    }

   public static ChaosChunksData get(DimensionDataStorage storage) {
    return storage.computeIfAbsent(
            new SavedData.Factory<>(
                    ChaosChunksData::new,
                    ChaosChunksData::load
            ),
            ID
    );
}
}
