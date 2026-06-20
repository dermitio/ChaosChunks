package com.dermitio.chaoschunks.worldgen.chunk;

import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeDecoration;
import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import com.dermitio.chaoschunks.worldgen.biome.ChaosStructureBiomeSource;
import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// =========
// Chaos-owned noise generator that routes biome, terrain, carver, feature, and mob seeds by region //
// =========
public class ChaosChunkGenerator extends NoiseBasedChunkGenerator {

    public static final MapCodec<ChaosChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChaosChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(ChaosChunkGenerator::generatorSettings),
                    NoiseGeneratorSettings.CODEC.listOf().optionalFieldOf("terrain_profiles", List.of()).forGetter(gen -> gen.terrainProfiles),
                    FlatLevelGeneratorSettings.CODEC.listOf().optionalFieldOf("flat_profiles", List.of()).forGetter(gen -> gen.flatProfiles)
            ).apply(instance, instance.stable(ChaosChunkGenerator::new))
    );

    private static final int MAX_TERRAIN_PROFILE_CHOICES = 4096;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final List<BlockState> DEFAULT_FLAT_LAYERS = List.of(
            Blocks.BEDROCK.defaultBlockState(),
            Blocks.DIRT.defaultBlockState(),
            Blocks.DIRT.defaultBlockState(),
            Blocks.GRASS_BLOCK.defaultBlockState()
    );

    private final ChaosNoiseState noiseStates;
    private final List<Holder<NoiseGeneratorSettings>> terrainProfiles;
    private final List<FlatLevelGeneratorSettings> flatProfiles;
    private final List<NoiseBasedChunkGenerator> terrainGenerators;
    private final Map<Long, Integer> terrainProfileChoices = new LinkedHashMap<>(MAX_TERRAIN_PROFILE_CHOICES, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return size() > MAX_TERRAIN_PROFILE_CHOICES;
        }
    };
    private volatile Supplier<List<FeatureSorter.StepFeatureData>> chaosFeaturesPerStep;

    public ChaosChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        this(biomeSource, settings, List.of(settings), List.of());
    }

    public ChaosChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, List<Holder<NoiseGeneratorSettings>> terrainProfiles) {
        this(biomeSource, settings, terrainProfiles, List.of());
    }

    public ChaosChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            List<Holder<NoiseGeneratorSettings>> terrainProfiles,
            List<FlatLevelGeneratorSettings> flatProfiles
    ) {
        super(biomeSource, settings);
        this.noiseStates = new ChaosNoiseState(settings);
        boolean useProfiles = usesTerrainProfiles(biomeSource);
        this.terrainProfiles = usesTerrainProfiles(biomeSource)
                ? normalizeTerrainProfiles(settings, terrainProfiles)
                : List.of(settings);
        this.flatProfiles = useProfiles ? normalizeFlatProfiles(flatProfiles) : List.of();
        this.terrainGenerators = createTerrainGenerators(this, biomeSource, this.terrainProfiles);
        this.chaosFeaturesPerStep = createFeatureSupplier();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void refreshFeaturesPerStep() {
        super.refreshFeaturesPerStep();
        this.chaosFeaturesPerStep = createFeatureSupplier();
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long legacyLevelSeed) {
        return ChunkGeneratorStructureState.createForNormal(
                randomState,
                structureSeed(legacyLevelSeed),
                ChaosStructureBiomeSource.wrap(this.getBiomeSource()),
                structureSets
        );
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess protoChunk) {
        TerrainChoice choice = terrainChoice(protoChunk.getPos());
        if (choice.isFlat()) {
            return super.createBiomes(randomState, blender, structureManager, protoChunk);
        }

        if (choice.profileIndex() == 0) {
            return super.createBiomes(chaosState(randomState, protoChunk.getPos(), choice), blender, structureManager, protoChunk);
        }

        return generatorFor(choice).createBiomes(chaosState(randomState, protoChunk.getPos(), choice), blender, structureManager, protoChunk);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess centerChunk) {
        TerrainChoice choice = terrainChoice(centerChunk.getPos());
        if (choice.isFlat()) {
            fillFlatChunk(centerChunk, choice.flatSettings());
            return CompletableFuture.completedFuture(centerChunk);
        }

        if (choice.profileIndex() == 0) {
            return super.fillFromNoise(blender, chaosState(randomState, centerChunk.getPos(), choice), structureManager, centerChunk);
        }

        return generatorFor(choice).fillFromNoise(blender, chaosState(randomState, centerChunk.getPos(), choice), structureManager, centerChunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk) {
        TerrainChoice choice = terrainChoice(protoChunk.getPos());
        if (choice.isFlat()) return;
        if (choice.profileIndex() == 0) {
            super.buildSurface(region, structureManager, chaosState(randomState, protoChunk.getPos(), choice), protoChunk);
            return;
        }

        generatorFor(choice).buildSurface(region, structureManager, chaosState(randomState, protoChunk.getPos(), choice), protoChunk);
    }

    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk
    ) {
        TerrainChoice choice = terrainChoice(chunk.getPos());
        if (choice.isFlat()) return;
        if (choice.profileIndex() == 0) {
            super.applyCarvers(region, seedForChunk(seed, chunk.getPos()), chaosState(randomState, chunk.getPos(), choice), biomeManager, structureManager, chunk);
            return;
        }

        generatorFor(choice).applyCarvers(region, seedForChunk(seed, chunk.getPos()), chaosState(randomState, chunk.getPos(), choice), biomeManager, structureManager, chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        TerrainChoice choice = terrainChoiceForBlock(x, z);
        if (choice.isFlat()) {
            return flatBaseHeight(heightAccessor, type, choice.flatSettings());
        }

        if (choice.profileIndex() == 0) {
            return super.getBaseHeight(x, z, type, heightAccessor, chaosState(randomState, x, z, choice));
        }

        return generatorFor(choice).getBaseHeight(x, z, type, heightAccessor, chaosState(randomState, x, z, choice));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        TerrainChoice choice = terrainChoiceForBlock(x, z);
        if (choice.isFlat()) {
            return flatBaseColumn(heightAccessor, choice.flatSettings());
        }

        if (choice.profileIndex() == 0) {
            return super.getBaseColumn(x, z, heightAccessor, chaosState(randomState, x, z, choice));
        }

        return generatorFor(choice).getBaseColumn(x, z, heightAccessor, chaosState(randomState, x, z, choice));
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        TerrainChoice choice = terrainChoiceForBlock(feetPos.getX(), feetPos.getZ());
        if (choice.isFlat()) {
            result.add("ChaosTerrain: superflat preset");
            return;
        }

        if (choice.profileIndex() == 0) {
            super.addDebugScreenInfo(result, chaosState(randomState, feetPos.getX(), feetPos.getZ(), choice), feetPos);
            return;
        }

        generatorFor(choice).addDebugScreenInfo(result, chaosState(randomState, feetPos.getX(), feetPos.getZ(), choice), feetPos);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        if (this.generatorSettings().value().disableMobGeneration()) return;

        ChunkPos center = worldGenRegion.getCenter();
        Holder<Biome> biome = worldGenRegion.getBiome(center.getWorldPosition().atY(worldGenRegion.getMaxY()));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(seedForChunk(worldGenRegion.getSeed(), center), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, biome, center, random);
    }

    // =========
    // Places structures and biome features with a region-local decoration seed //
    // =========
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        if (terrainChoice(chunk.getPos()).isFlat()) return;
        ChaosBiomeDecoration.place(this, level, chunk, structureManager, this.chaosFeaturesPerStep.get(), seedForChunk(level.getSeed(), chunk.getPos()));
    }

    private Supplier<List<FeatureSorter.StepFeatureData>> createFeatureSupplier() {
        return Suppliers.memoize(() -> FeatureSorter.buildFeaturesPerStep(
                featureSortingBiomes(),
                biome -> this.getBiomeGenerationSettings(biome).features(),
                true
        ));
    }

    private List<Holder<Biome>> featureSortingBiomes() {
        if (this.getBiomeSource() instanceof ChaosBiomeSource chaos) {
            return chaos.featureBiomes().toList();
        }

        return List.copyOf(this.getBiomeSource().possibleBiomes());
    }

    private NoiseBasedChunkGenerator generatorFor(TerrainChoice choice) {
        return terrainGenerators.get(choice.profileIndex());
    }

    private RandomState chaosState(RandomState fallback, ChunkPos pos, TerrainChoice choice) {
        return noiseStates.forChunk(this.getBiomeSource(), fallback, pos, choice.settings(), choice.profileIndex());
    }

    private RandomState chaosState(RandomState fallback, int blockX, int blockZ, TerrainChoice choice) {
        return noiseStates.forBlock(this.getBiomeSource(), fallback, blockX, blockZ, choice.settings(), choice.profileIndex());
    }

    private long seedForChunk(long fallbackSeed, ChunkPos pos) {
        if (this.getBiomeSource() instanceof ChaosBiomeSource chaos) {
            return ChaosRegionSeed.seedForChunk(chaos, fallbackSeed, pos);
        }
        return fallbackSeed;
    }

    private long structureSeed(long fallbackSeed) {
        if (this.getBiomeSource() instanceof ChaosBiomeSource chaos) {
            return ChaosRegionSeed.dimensionSeed(fallbackSeed, chaos.seedRandomizer(), chaos.regionSizeX(), chaos.regionSizeZ());
        }
        return fallbackSeed;
    }

    private TerrainChoice terrainChoice(ChunkPos pos) {
        if (!(this.getBiomeSource() instanceof ChaosBiomeSource chaos) || !chaos.usesRandomizedTerrainProfiles()) {
            return TerrainChoice.noise(0, this.terrainProfiles.getFirst());
        }

        int regionX = ChaosRegionSeed.regionFromChunk(pos.x(), chaos.regionSizeX());
        int regionZ = ChaosRegionSeed.regionFromChunk(pos.z(), chaos.regionSizeZ());
        return terrainChoice(chaos, regionX, regionZ);
    }

    private TerrainChoice terrainChoiceForBlock(int blockX, int blockZ) {
        if (!(this.getBiomeSource() instanceof ChaosBiomeSource chaos) || !chaos.usesRandomizedTerrainProfiles()) {
            return TerrainChoice.noise(0, this.terrainProfiles.getFirst());
        }

        int regionX = ChaosRegionSeed.regionFromBlock(blockX, chaos.regionSizeX());
        int regionZ = ChaosRegionSeed.regionFromBlock(blockZ, chaos.regionSizeZ());
        return terrainChoice(chaos, regionX, regionZ);
    }

    private TerrainChoice terrainChoice(ChaosBiomeSource chaos, int regionX, int regionZ) {
        int index = terrainProfileIndex(chaos, regionX, regionZ);
        if (index >= this.terrainProfiles.size()) {
            int flatIndex = index - this.terrainProfiles.size();
            FlatLevelGeneratorSettings settings = this.flatProfiles.isEmpty() ? null : this.flatProfiles.get(flatIndex);
            return TerrainChoice.flat(settings);
        }

        return TerrainChoice.noise(index, this.terrainProfiles.get(index));
    }

    private int terrainProfileIndex(ChaosBiomeSource chaos, int regionX, int regionZ) {
        long cacheKey = ChaosRegionSeed.cacheKey(regionX, regionZ);
        synchronized (terrainProfileChoices) {
            Integer cached = terrainProfileChoices.get(cacheKey);
            if (cached != null) return cached;

            int profileCount = this.terrainProfiles.size() + Math.max(1, this.flatProfiles.size());
            long h = ChaosRegionSeed.terrainProfileSeed(chaos.terrainRandomizer(), chaos.regionSizeX(), chaos.regionSizeZ(), regionX, regionZ);
            int index = (int) Long.remainderUnsigned(h, profileCount);
            terrainProfileChoices.put(cacheKey, index);
            return index;
        }
    }

    private static List<Holder<NoiseGeneratorSettings>> normalizeTerrainProfiles(Holder<NoiseGeneratorSettings> primary, List<Holder<NoiseGeneratorSettings>> profiles) {
        LinkedHashMap<String, Holder<NoiseGeneratorSettings>> out = new LinkedHashMap<>();
        putTerrainProfile(out, primary);
        if (profiles != null) {
            for (Holder<NoiseGeneratorSettings> profile : profiles) {
                putTerrainProfile(out, profile);
            }
        }

        return List.copyOf(out.values());
    }

    private static List<FlatLevelGeneratorSettings> normalizeFlatProfiles(List<FlatLevelGeneratorSettings> profiles) {
        if (profiles == null || profiles.isEmpty()) return List.of();

        ArrayList<FlatLevelGeneratorSettings> out = new ArrayList<>(profiles.size());
        for (FlatLevelGeneratorSettings profile : profiles) {
            if (profile == null) continue;
            profile.updateLayers();
            if (!out.contains(profile)) out.add(profile);
        }

        return List.copyOf(out);
    }

    private static void putTerrainProfile(Map<String, Holder<NoiseGeneratorSettings>> out, Holder<NoiseGeneratorSettings> profile) {
        if (profile == null) return;
        String id = profile.unwrapKey()
                .map(String::valueOf)
                .orElseGet(() -> "direct:" + System.identityHashCode(profile));
        out.putIfAbsent(id, profile);
    }

    private static boolean usesTerrainProfiles(BiomeSource source) {
        return source instanceof ChaosBiomeSource chaos && chaos.usesRandomizedTerrainProfiles();
    }

    private static List<NoiseBasedChunkGenerator> createTerrainGenerators(
            NoiseBasedChunkGenerator primaryGenerator,
            BiomeSource source,
            List<Holder<NoiseGeneratorSettings>> profiles
    ) {
        ArrayList<NoiseBasedChunkGenerator> out = new ArrayList<>(profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            if (i == 0) {
                out.add(primaryGenerator);
            } else {
                out.add(new NoiseBasedChunkGenerator(source, profiles.get(i)));
            }
        }
        return List.copyOf(out);
    }

    private void fillFlatChunk(ChunkAccess chunk, FlatLevelGeneratorSettings settings) {
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        LevelHeightAccessor heightAccessor = chunk.getHeightAccessorForGeneration();
        int baseY = flatBaseY(heightAccessor);
        List<BlockState> layers = flatLayers(settings);

        for (int x = 0; x < 16; x++) {
            int blockX = pos.getMinBlockX() + x;
            for (int z = 0; z < 16; z++) {
                int blockZ = pos.getMinBlockZ() + z;
                for (int layer = 0; layer < layers.size(); layer++) {
                    int y = baseY + layer;
                    if (y < heightAccessor.getMinY() || y >= heightAccessor.getMinY() + heightAccessor.getHeight()) continue;
                    BlockState state = layers.get(layer);
                    if (!state.isAir()) {
                        chunk.setBlockState(mutable.set(blockX, y, blockZ), state, 0);
                    }
                }
            }
        }
    }

    private static int flatBaseHeight(LevelHeightAccessor heightAccessor, Heightmap.Types type, FlatLevelGeneratorSettings settings) {
        List<BlockState> layers = flatLayers(settings);
        for (int layer = layers.size() - 1; layer >= 0; layer--) {
            if (type.isOpaque().test(layers.get(layer))) {
                return flatBaseY(heightAccessor) + layer + 1;
            }
        }

        return heightAccessor.getMinY();
    }

    private static NoiseColumn flatBaseColumn(LevelHeightAccessor heightAccessor, FlatLevelGeneratorSettings settings) {
        int minY = heightAccessor.getMinY();
        int height = heightAccessor.getHeight();
        BlockState[] states = new BlockState[height];
        java.util.Arrays.fill(states, AIR);

        int baseY = flatBaseY(heightAccessor);
        List<BlockState> layers = flatLayers(settings);
        for (int layer = 0; layer < layers.size(); layer++) {
            int index = baseY + layer - minY;
            if (index >= 0 && index < states.length) {
                states[index] = layers.get(layer);
            }
        }

        return new NoiseColumn(minY, states);
    }

    private static int flatBaseY(LevelHeightAccessor heightAccessor) {
        return Math.max(0, heightAccessor.getMinY());
    }

    private static List<BlockState> flatLayers(FlatLevelGeneratorSettings settings) {
        if (settings == null) return DEFAULT_FLAT_LAYERS;
        List<BlockState> layers = settings.getLayers();
        return layers == null || layers.isEmpty() ? DEFAULT_FLAT_LAYERS : layers;
    }

    private record TerrainChoice(
            int profileIndex,
            Holder<NoiseGeneratorSettings> settings,
            FlatLevelGeneratorSettings flatSettings,
            boolean flatTerrain
    ) {
        boolean isFlat() {
            return flatTerrain;
        }

        static TerrainChoice noise(int profileIndex, Holder<NoiseGeneratorSettings> settings) {
            return new TerrainChoice(profileIndex, settings, null, false);
        }

        static TerrainChoice flat(FlatLevelGeneratorSettings settings) {
            return new TerrainChoice(-1, null, settings, true);
        }
    }

}
