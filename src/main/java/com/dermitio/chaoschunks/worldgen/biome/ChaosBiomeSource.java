package com.dermitio.chaoschunks.worldgen.biome;

import com.dermitio.chaoschunks.worldgen.chunk.ChaosRegionSeed;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

// =========
// Biome source that maps chunk regions to deterministic random biome selections //
// =========
public class ChaosBiomeSource extends BiomeSource {

    // =========
    // Keeps serialized biome lists registry-backed whenever possible //
    // =========
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

    // =========
    // Codec used by worldgen JSON and saved chunk-generator settings //
    // =========
    public static final MapCodec<ChaosBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.LONG.fieldOf("seed").forGetter(bs -> bs.seed),
                    Codec.intRange(1, 512).fieldOf("size_x").forGetter(bs -> bs.sizeX),
                    Codec.intRange(1, 512).fieldOf("size_z").forGetter(bs -> bs.sizeZ),
                    Codec.LONG.optionalFieldOf("seed_randomizer", 0L).forGetter(bs -> bs.seedRandomizer),
                    Codec.LONG.optionalFieldOf("terrain_randomizer", 0L).forGetter(bs -> bs.terrainRandomizer),

                    RegistryCodecs.homogeneousList(Registries.BIOME)
                            .fieldOf("biomes")
                            .forGetter(bs -> encodeSafeDirect(bs.selectionList())),

                    RegistryCodecs.homogeneousList(Registries.BIOME)
                            .optionalFieldOf("feature_biomes")
                            .forGetter(bs -> (bs.featureList().equals(bs.selectionList()))
                                    ? Optional.empty()
                                    : Optional.of(encodeSafeDirect(bs.featureList()))),

                    BiomeSource.CODEC
                            .optionalFieldOf("original_biome_source")
                            .forGetter(bs -> bs.originalBiomeSource)
            ).apply(instance, (seed, sx, sz, seedRandomizer, terrainRandomizer, biomes, featureOpt, originalOpt) ->
                    new ChaosBiomeSource(seed, sx, sz, biomes, featureOpt.orElse(biomes), seedRandomizer, terrainRandomizer, originalOpt)
            ));

    // ** Stores seed used to deterministically randomize biome regions **
    private final long seed;

    // ** Stores per-dimension salt used to randomize each region's local seed **
    private final long seedRandomizer;

    // ** Stores per-dimension salt used to choose region terrain profiles **
    private final long terrainRandomizer;

    // ** Stores horizontal region size controlling biome patch width **
    private final int sizeX;

    // ** Stores vertical region size controlling biome patch height **
    private final int sizeZ;

    // ** Holds biome set used for serialization and registry exposure **
    private final HolderSet<Biome> biomes;

    // ** Holds biome set used for feature placement compatibility **
    private final HolderSet<Biome> featureBiomes;

    // ** Original biome source used to restore custom dimensions when Chaos is disabled **
    private final Optional<BiomeSource> originalBiomeSource;

    // ** Cached list used for fast biome selection during noise lookup **
    private volatile List<Holder<Biome>> selectionList;

    // ** Cached list used for feature sorting and generation logic **
    private volatile List<Holder<Biome>> featureList;

    private volatile Set<Holder<Biome>> featureBiomeSet;
    private volatile int cachedPickerBiomeCount = -1;
    private volatile int cachedPickerColumns = 1;
    private volatile int cachedPickerRows = 1;

    // ** Constructor used when feature biomes match selection biomes **
    public ChaosBiomeSource(long seed, int sizeX, int sizeZ, HolderSet<Biome> biomes) {
        this(seed, sizeX, sizeZ, biomes, biomes, 0L);
    }

    // ** Primary constructor storing configuration and caching biome lists **
    public ChaosBiomeSource(long seed, int sizeX, int sizeZ, HolderSet<Biome> biomes, HolderSet<Biome> featureBiomes) {
        this(seed, sizeX, sizeZ, biomes, featureBiomes, 0L);
    }

    public ChaosBiomeSource(long seed, int sizeX, int sizeZ, HolderSet<Biome> biomes, HolderSet<Biome> featureBiomes, long seedRandomizer) {
        this(seed, sizeX, sizeZ, biomes, featureBiomes, seedRandomizer, 0L, Optional.empty());
    }

    public ChaosBiomeSource(long seed, int sizeX, int sizeZ, HolderSet<Biome> biomes, HolderSet<Biome> featureBiomes, long seedRandomizer, long terrainRandomizer) {
        this(seed, sizeX, sizeZ, biomes, featureBiomes, seedRandomizer, terrainRandomizer, Optional.empty());
    }

    public ChaosBiomeSource(
            long seed,
            int sizeX,
            int sizeZ,
            HolderSet<Biome> biomes,
            HolderSet<Biome> featureBiomes,
            long seedRandomizer,
            BiomeSource originalBiomeSource
    ) {
        this(seed, sizeX, sizeZ, biomes, featureBiomes, seedRandomizer, 0L, Optional.ofNullable(originalBiomeSource));
    }

    public ChaosBiomeSource(
            long seed,
            int sizeX,
            int sizeZ,
            HolderSet<Biome> biomes,
            HolderSet<Biome> featureBiomes,
            long seedRandomizer,
            long terrainRandomizer,
            BiomeSource originalBiomeSource
    ) {
        this(seed, sizeX, sizeZ, biomes, featureBiomes, seedRandomizer, terrainRandomizer, Optional.ofNullable(originalBiomeSource));
    }

    private ChaosBiomeSource(
            long seed,
            int sizeX,
            int sizeZ,
            HolderSet<Biome> biomes,
            HolderSet<Biome> featureBiomes,
            long seedRandomizer,
            long terrainRandomizer,
            Optional<BiomeSource> originalBiomeSource
    ) {
        this.seed = seed;
        this.seedRandomizer = seedRandomizer;
        this.terrainRandomizer = terrainRandomizer;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.biomes = biomes;
        this.featureBiomes = featureBiomes;
        this.originalBiomeSource = sanitizeOriginalSource(originalBiomeSource);
    }

    // ** Returns codec used by Minecraft to serialize this biome source **
    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    // ** Supplies biome stream used by feature sorting and structure placement systems **
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return selectionList().stream();
    }

    // =========
    // Selects the biome for a quart position using the containing chunk region //
    // =========
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int chunkX = QuartPos.toBlock(x) >> 4;
        int chunkZ = QuartPos.toBlock(z) >> 4;

        int regionX = Math.floorDiv(chunkX, sizeX);
        int regionZ = Math.floorDiv(chunkZ, sizeZ);

        List<Holder<Biome>> biomes = selectionList();
        return biomes.get(pickBiomeIndex(regionX, regionZ, biomes.size()));
    }

    // =========
    // Converts region coordinates to a stable biome index without allocating RNG state //
    // =========
    private int pickBiomeIndex(int regionX, int regionZ, int biomeCount) {
        if (biomeCount > 1) {
            return pickFairRandomizedBiomeIndex(regionX, regionZ, biomeCount);
        }

        long h = ChaosRegionSeed.biomeSelectionSeed(seed, seedRandomizer, sizeX, sizeZ, regionX, regionZ);
        return boundedIndex(h, biomeCount);
    }

    private int pickFairRandomizedBiomeIndex(int regionX, int regionZ, int biomeCount) {
        ensurePickerGrid(biomeCount);
        int columns = cachedPickerColumns;
        int rows = cachedPickerRows;

        int localX = Math.floorMod(regionX, columns);
        int localZ = Math.floorMod(regionZ, rows);
        int slot = localZ * columns + localX;

        if (slot >= biomeCount) {
            long h = ChaosRegionSeed.biomeSelectionSeed(seed, seedRandomizer, sizeX, sizeZ, regionX, regionZ);
            return boundedIndex(h, biomeCount);
        }

        int tileX = Math.floorDiv(regionX, columns);
        int tileZ = Math.floorDiv(regionZ, rows);
        long tileSeed = ChaosRegionSeed.biomeSelectionSeed(seed, seedRandomizer, sizeX, sizeZ, tileX, tileZ);
        return (slot + boundedIndex(tileSeed, biomeCount)) % biomeCount;
    }

    private static int boundedIndex(long value, int bound) {
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static int ceilSqrt(int value) {
        int root = (int) Math.sqrt(value);
        return root * root == value ? root : root + 1;
    }

    private void ensurePickerGrid(int biomeCount) {
        if (cachedPickerBiomeCount == biomeCount) return;

        synchronized (this) {
            if (cachedPickerBiomeCount == biomeCount) return;

            int columns = ceilSqrt(biomeCount);
            cachedPickerColumns = columns;
            cachedPickerRows = Math.ceilDiv(biomeCount, columns);
            cachedPickerBiomeCount = biomeCount;
        }
    }

    public long seed() {
        return seed;
    }

    public long seedRandomizer() {
        return seedRandomizer;
    }

    public long terrainRandomizer() {
        return terrainRandomizer;
    }

    public boolean usesIndependentRegionSeed() {
        return seedRandomizer != 0L;
    }

    public boolean usesRandomizedTerrainProfiles() {
        return terrainRandomizer != 0L;
    }

    public int regionSizeX() {
        return sizeX;
    }

    public int regionSizeZ() {
        return sizeZ;
    }

    public Optional<BiomeSource> originalBiomeSource() {
        return originalBiomeSource;
    }

    Stream<Holder<Biome>> structureBiomes() {
        return selectionList().stream();
    }  // restoring chaos!

    public Stream<Holder<Biome>> featureBiomes() {
        return featureList().stream();
    }

    public Set<Holder<Biome>> featureBiomeSet() {
        Set<Holder<Biome>> set = featureBiomeSet;
        if (set == null) {
            set = Set.copyOf(featureList());
            featureBiomeSet = set;
        }
        return set;
    }

    // =========
    // Resolves tag-backed holder sets only after registry binding has completed //
    // =========
    private List<Holder<Biome>> selectionList() {
        List<Holder<Biome>> list = selectionList;
        if (list == null) {
            list = biomes.stream().toList();
            selectionList = list;
        }
        return list;
    }

    // ** Resolves feature holders lazily so tag-backed holder sets are bound before streaming **
    private List<Holder<Biome>> featureList() {
        List<Holder<Biome>> list = featureList;
        if (list == null) {
            list = featureBiomes.stream().toList();
            featureList = list;
        }
        return list;
    }

    private static Optional<BiomeSource> sanitizeOriginalSource(Optional<BiomeSource> source) {
        if (source == null || source.isEmpty() || source.get() instanceof ChaosBiomeSource) {
            return Optional.empty();
        }
        return source;
    }

}
