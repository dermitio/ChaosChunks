package com.dermitio.chaoschunks.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ChaosBiomeSource extends BiomeSource {

    // ** Filters biome holders so only keyed entries are encoded, preventing registry serialization issues **
    private static HolderSet<Biome> encodeSafeDirect(List<Holder<Biome>> src) {
        if (src == null || src.isEmpty()) {
            return HolderSet.direct(List.of());
        }

        var out = new java.util.ArrayList<Holder<Biome>>(src.size());
        for (var h : src) {
            if (h != null && h.unwrapKey().isPresent()) {
                out.add(h);
            }
        }

        if (out.isEmpty()) out.addAll(src);
        return HolderSet.direct(out);
    }

    // ** Defines how the biome source is serialized and reconstructed from worldgen data **
    public static final MapCodec<ChaosBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.LONG.fieldOf("seed").forGetter(bs -> bs.seed),
                    Codec.intRange(1, 512).fieldOf("size_x").forGetter(bs -> bs.sizeX),
                    Codec.intRange(1, 512).fieldOf("size_z").forGetter(bs -> bs.sizeZ),

                    RegistryCodecs.homogeneousList(Registries.BIOME)
                            .fieldOf("biomes")
                            .forGetter(bs -> encodeSafeDirect(bs.selectionList)),

                    RegistryCodecs.homogeneousList(Registries.BIOME)
                            .optionalFieldOf("feature_biomes")
                            .forGetter(bs -> (bs.featureList.equals(bs.selectionList))
                                    ? Optional.empty()
                                    : Optional.of(encodeSafeDirect(bs.featureList)))
            ).apply(instance, (seed, sx, sz, biomes, featureOpt) ->
                    new ChaosBiomeSource(seed, sx, sz, biomes, featureOpt.orElse(biomes))
            ));

    // ** Stores seed used to deterministically randomize biome regions **
    private final long seed;

    // ** Stores horizontal region size controlling biome patch width **
    private final int sizeX;

    // ** Stores vertical region size controlling biome patch height **
    private final int sizeZ;

    // ** Holds biome set used for serialization and registry exposure **
    private final HolderSet<Biome> biomes;

    // ** Holds biome set used for feature placement compatibility **
    private final HolderSet<Biome> featureBiomes;

    // ** Cached list used for fast biome selection during noise lookup **
    private final List<Holder<Biome>> selectionList;

    // ** Cached list used for feature sorting and generation logic **
    private final List<Holder<Biome>> featureList;

    // ** Constructor used when feature biomes match selection biomes **
    public ChaosBiomeSource(long seed, int sizeX, int sizeZ, HolderSet<Biome> biomes) {
        this(seed, sizeX, sizeZ, biomes, biomes);
    }

    // ** Primary constructor storing configuration and caching biome lists **
    public ChaosBiomeSource(long seed, int sizeX, int sizeZ, HolderSet<Biome> biomes, HolderSet<Biome> featureBiomes) {
        this.seed = seed;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.biomes = biomes;
        this.featureBiomes = featureBiomes;
        this.selectionList = biomes.stream().toList();
        this.featureList = featureBiomes.stream().toList();
    }

    // ** Returns codec used by Minecraft to serialize this biome source **
    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    // ** Supplies biome stream used by feature sorting and structure placement systems **
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return featureList.stream();
    }

    // ** Selects the biome for a chunk by hashing region coordinates with the world seed **
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int chunkX = QuartPos.toBlock(x) >> 4;
        int chunkZ = QuartPos.toBlock(z) >> 4;

        int regionX = Math.floorDiv(chunkX, sizeX);
        int regionZ = Math.floorDiv(chunkZ, sizeZ);

        long mix = seed ^ (regionX * 341873128712L) ^ (regionZ * 132897987541L);
        RandomSource rand = new XoroshiroRandomSource(mix);

        return selectionList.get(rand.nextInt(selectionList.size()));
    }
}
