package com.dermitio.chaoschunks.worldgen.biome;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.worldgen.chunk.ChaosChunkGenerator;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// =========
// Isolates vanilla-style structure and feature decoration using Chaos region seeds //
// =========
public final class ChaosBiomeDecoration {

    private ChaosBiomeDecoration() {}

    public static void place(
            ChaosChunkGenerator generator,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            List<FeatureSorter.StepFeatureData> featureList,
            long seed
    ) {
        ChunkPos centerPos = chunk.getPos();
        if (SharedConstants.debugVoidTerrain(centerPos)) return;

        SectionPos sectionPos = SectionPos.of(centerPos, level.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<Structure> structuresRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> structuresByStep = structuresRegistry.stream()
                .collect(Collectors.groupingBy(structure -> structure.step().ordinal()));
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(seed, origin.getX(), origin.getZ());
        ObjectArraySet<Holder<Biome>> possibleBiomes = collectPossibleBiomes(generator, level, sectionPos);
        int featureStepCount = featureList.size();

        try {
            Registry<PlacedFeature> featureRegistry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            int generationSteps = Math.max(GenerationStep.Decoration.values().length, featureStepCount);

            for (int stepIndex = 0; stepIndex < generationSteps; stepIndex++) {
                placeStructures(generator, level, chunk, structureManager, structuresRegistry, structuresByStep, random, decorationSeed, sectionPos, centerPos, stepIndex);

                if (stepIndex < featureStepCount) {
                    placeFeatures(generator, level, featureRegistry, possibleBiomes, featureList, random, decorationSeed, origin, stepIndex);
                }
            }

            level.setCurrentlyGenerating(null);
            if (SharedConstants.DEBUG_FEATURE_COUNT) {
                FeatureCountTracker.chunkDecorated(level.getLevel());
            }
        } catch (Exception e) {
            CrashReport report = CrashReport.forThrowable(e, "Biome decoration");
            report.addCategory("Generation")
                    .setDetail("CenterX", centerPos.x())
                    .setDetail("CenterZ", centerPos.z())
                    .setDetail("Decoration Seed", decorationSeed);
            throw new ReportedException(report);
        }
    }

    private static ObjectArraySet<Holder<Biome>> collectPossibleBiomes(ChaosChunkGenerator generator, WorldGenLevel level, SectionPos sectionPos) {
        ObjectArraySet<Holder<Biome>> possibleBiomes = new ObjectArraySet<>();

        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(chunkPos -> {
            ChunkAccess chunkInRange = level.getChunk(chunkPos.x(), chunkPos.z());
            for (LevelChunkSection section : chunkInRange.getSections()) {
                section.getBiomes().getAll(possibleBiomes::add);
            }
        });

        possibleBiomes.retainAll(featurePlacementBiomes(generator));
        return possibleBiomes;
    }

    private static Collection<Holder<Biome>> featurePlacementBiomes(ChaosChunkGenerator generator) {
        if (generator.getBiomeSource() instanceof ChaosBiomeSource chaos) {
            return chaos.featureBiomeSet();
        }

        return generator.getBiomeSource().possibleBiomes();
    }

    private static void placeStructures(
            ChaosChunkGenerator generator,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            Registry<Structure> structuresRegistry,
            Map<Integer, List<Structure>> structuresByStep,
            WorldgenRandom random,
            long decorationSeed,
            SectionPos sectionPos,
            ChunkPos centerPos,
            int stepIndex
    ) {
        if (!structureManager.shouldGenerateStructures()) return;

        int index = 0;
        for (Structure structure : structuresByStep.getOrDefault(stepIndex, Collections.emptyList())) {
            random.setFeatureSeed(decorationSeed, index, stepIndex);
            Supplier<String> currentlyGenerating = () -> structuresRegistry.getResourceKey(structure)
                    .map(Object::toString)
                    .orElseGet(structure::toString);

            try {
                level.setCurrentlyGenerating(currentlyGenerating);
                structureManager.startsForStructure(sectionPos, structure)
                        .forEach(start -> start.placeInChunk(level, structureManager, generator, random, writableArea(chunk), centerPos));
            } catch (Exception e) {
                CrashReport report = CrashReport.forThrowable(e, "Feature placement");
                report.addCategory("Feature").setDetail("Description", currentlyGenerating::get);
                throw new ReportedException(report);
            }

            index++;
        }
    }

    private static void placeFeatures(
            ChaosChunkGenerator generator,
            WorldGenLevel level,
            Registry<PlacedFeature> featureRegistry,
            ObjectArraySet<Holder<Biome>> possibleBiomes,
            List<FeatureSorter.StepFeatureData> featureList,
            WorldgenRandom random,
            long decorationSeed,
            BlockPos origin,
            int stepIndex
    ) {
        IntSet possibleFeaturesThisStep = new IntArraySet();

        for (Holder<Biome> biome : possibleBiomes) {
            BiomeGenerationSettings settings = generator.getBiomeGenerationSettings(biome);
            List<HolderSet<PlacedFeature>> featuresInBiome = settings.features();
            if (stepIndex < featuresInBiome.size()) {
                HolderSet<PlacedFeature> featuresInBiomeThisStep = featuresInBiome.get(stepIndex);
                FeatureSorter.StepFeatureData stepFeatureData = featureList.get(stepIndex);
                featuresInBiomeThisStep.stream()
                        .map(Holder::value)
                        .forEach(feature -> possibleFeaturesThisStep.add(stepFeatureData.indexMapping().applyAsInt(feature)));
            }
        }

        int[] indexArray = possibleFeaturesThisStep.toIntArray();
        Arrays.sort(indexArray);
        FeatureSorter.StepFeatureData stepFeatureData = featureList.get(stepIndex);

        for (int globalIndexOfFeature : indexArray) {
            PlacedFeature feature = stepFeatureData.features().get(globalIndexOfFeature);
            if (isDisabledExperimentalFeature(featureRegistry, feature)) continue;

            Supplier<String> currentlyGenerating = () -> featureRegistry.getResourceKey(feature)
                    .map(Object::toString)
                    .orElseGet(feature::toString);
            random.setFeatureSeed(decorationSeed, globalIndexOfFeature, stepIndex);

            try {
                level.setCurrentlyGenerating(currentlyGenerating);
                feature.placeWithBiomeCheck(level, generator, random, origin);
            } catch (Exception e) {
                CrashReport report = CrashReport.forThrowable(e, "Feature placement");
                report.addCategory("Feature").setDetail("Description", currentlyGenerating::get);
                throw new ReportedException(report);
            }
        }
    }

    private static boolean isDisabledExperimentalFeature(Registry<PlacedFeature> featureRegistry, PlacedFeature feature) {
        if (ChaosChunksExperimentsConfig.timeVoidMint()) return false;

        return featureRegistry.getResourceKey(feature)
                .map(ChaosBiomeDecoration::stableId)
                .map(id -> id.startsWith(ChaosChunks.MODID + ":mint_bush"))
                .orElse(false);
    }

    private static String stableId(net.minecraft.resources.ResourceKey<?> key) {
        String s = String.valueOf(key);
        int sep = s.indexOf(" / ");
        if (sep >= 0) {
            int end = s.indexOf(']', sep);
            if (end > sep) return s.substring(sep + 3, end);
            return s.substring(sep + 3);
        }
        return s;
    }

    private static BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int targetBlockX = chunkPos.getMinBlockX();
        int targetBlockZ = chunkPos.getMinBlockZ();
        LevelHeightAccessor heightAccessor = chunk.getHeightAccessorForGeneration();
        int minY = heightAccessor.getMinY() + 1;
        int maxY = heightAccessor.getMaxY();
        return new BoundingBox(targetBlockX, minY, targetBlockZ, targetBlockX + 15, maxY, targetBlockZ + 15);
    }
}
