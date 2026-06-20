package com.dermitio.chaoschunks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FeatureSorter.class)
// =========
// Guards biome decoration sorting against feature-order cycles from mixed biome pools //
// =========
public class FeatureSorterMixin {

    // ** Provides logging for fallback activation and mixin diagnostics **
    private static final Logger LOGGER = LogUtils.getLogger();

    // ** Ensures fallback warning is only printed once **
    private static boolean LOGGED_FALLBACK = false;

    // ** Caps fallback feature count per decoration step to prevent feature explosions **
    private static final int MAX_FALLBACK_FEATURES_PER_STEP = 16;

    // ** Allows disabling all decorations on feature cycles while debugging **
    private static final boolean FAIL_CLOSED_ON_FEATURE_CYCLE = true;

    // =========
    // Intercepts vanilla feature sorting failures before they abort chunk decoration //
    // =========
    @WrapMethod(method = "buildFeaturesPerStep")
    private static <T> List<
        FeatureSorter.StepFeatureData
    > chaoschunks$wrapBuildFeaturesPerStep(
        List<T> featureSetSources,
        Function<T, List<HolderSet<PlacedFeature>>> toFeatureSetFunction,
        boolean flag,
        Operation<List<FeatureSorter.StepFeatureData>> original
    ) {
        try {
            List<FeatureSorter.StepFeatureData> res = original.call(
                featureSetSources,
                toFeatureSetFunction,
                flag
            );
            return chaoschunks$nonEmptyOrFallback(chaoschunks$sanitize(res));
        } catch (IndexOutOfBoundsException e) {
            LOGGER.warn(
                "[ChaosChunks] FeatureSorter produced invalid/empty step data; using fallback ordering.",
                e
            );
            return chaoschunks$nonEmptyOrFallback(
                chaoschunks$fallback(featureSetSources, toFeatureSetFunction)
            );
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Feature order cycle")) {
                if (!LOGGED_FALLBACK) {
                    LOGGED_FALLBACK = true;
                    LOGGER.warn(
                        "[ChaosChunks] FeatureSorter cycle detected; using bounded fallback feature ordering. Message: {}",
                        msg
                    );
                }
                return chaoschunks$nonEmptyOrFallback(
                    chaoschunks$fallback(
                        featureSetSources,
                        toFeatureSetFunction
                    )
                );
            }
            throw e;
        }
    }

    // ** Ensures buildFeaturesPerStep never returns an empty list to prevent biome decoration crashes **
    private static List<
        FeatureSorter.StepFeatureData
    > chaoschunks$nonEmptyOrFallback(List<FeatureSorter.StepFeatureData> in) {
        if (in != null && !in.isEmpty()) return in;
        return java.util.List.of(
            new FeatureSorter.StepFeatureData(java.util.List.of(), pf -> 0)
        );
    }

    // ** Repairs invalid feature index mappings to prevent crashes during generation **
    private static List<FeatureSorter.StepFeatureData> chaoschunks$sanitize(
        List<FeatureSorter.StepFeatureData> in
    ) {
        if (in == null || in.isEmpty()) return in;

        List<FeatureSorter.StepFeatureData> out = new ArrayList<>(in.size());

        for (FeatureSorter.StepFeatureData d : in) {
            if (d == null) continue;

            List<PlacedFeature> features = (d.features() == null)
                ? java.util.List.of()
                : d.features();
            ToIntFunction<PlacedFeature> orig = d.indexMapping();

            ToIntFunction<PlacedFeature> safe = pf -> {
                int idx = (orig == null) ? -1 : orig.applyAsInt(pf);

                if (idx >= 0 && idx < features.size()) return idx;

                int j = features.indexOf(pf);
                if (j >= 0) return j;

                return 0;
            };

            out.add(new FeatureSorter.StepFeatureData(features, safe));
        }

        return out;
    }

    // =========
    // Produces bounded fallback data instead of merging every feature from every biome //
    // =========
    private static <T> List<FeatureSorter.StepFeatureData> chaoschunks$fallback(
        List<T> sources,
        Function<T, List<HolderSet<PlacedFeature>>> func
    ) {
        if (FAIL_CLOSED_ON_FEATURE_CYCLE || sources == null || sources.isEmpty()) {
            return chaoschunks$emptyFallback();
        }

        // Avoid feature explosion: use the first non-empty source instead of merging every biome.
        for (T src : sources) {
            List<HolderSet<PlacedFeature>> steps = func.apply(src);
            if (chaoschunks$hasAnyFeatures(steps)) {
                return chaoschunks$boundedFallbackFromSteps(steps);
            }
        }

        return chaoschunks$emptyFallback();
    }

    private static List<FeatureSorter.StepFeatureData> chaoschunks$emptyFallback() {
        return List.of(new FeatureSorter.StepFeatureData(List.of(), pf -> 0));
    }

    private static boolean chaoschunks$hasAnyFeatures(
        List<HolderSet<PlacedFeature>> steps
    ) {
        if (steps == null) return false;

        for (HolderSet<PlacedFeature> set : steps) {
            if (set == null) continue;
            for (Holder<PlacedFeature> ignored : set) {
                return true;
            }
        }

        return false;
    }

    private static List<FeatureSorter.StepFeatureData> chaoschunks$boundedFallbackFromSteps(
        List<HolderSet<PlacedFeature>> steps
    ) {
        List<FeatureSorter.StepFeatureData> out = new ArrayList<>(steps.size());

        for (HolderSet<PlacedFeature> set : steps) {
            List<PlacedFeature> features = new ArrayList<>();
            IdentityHashMap<PlacedFeature, Boolean> seen = new IdentityHashMap<>();

            if (set != null) {
                for (Holder<PlacedFeature> h : set) {
                    if (h == null) continue;

                    PlacedFeature pf = h.value();
                    if (pf == null || seen.containsKey(pf)) continue;

                    seen.put(pf, Boolean.TRUE);
                    features.add(pf);

                    if (features.size() >= MAX_FALLBACK_FEATURES_PER_STEP) break;
                }
            }

            IdentityHashMap<PlacedFeature, Integer> indexMap =
                new IdentityHashMap<>(features.size() * 2);
            for (int i = 0; i < features.size(); i++) {
                indexMap.put(features.get(i), i);
            }

            ToIntFunction<PlacedFeature> indexer = pf -> {
                Integer idx = indexMap.get(pf);
                return idx == null ? 0 : idx;
            };

            out.add(new FeatureSorter.StepFeatureData(features, indexer));
        }

        return chaoschunks$nonEmptyOrFallback(out);
    }
}
