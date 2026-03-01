package com.dermitio.chaoschunks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

@Mixin(FeatureSorter.class)
public class FeatureSorterMixin {

    // ** Provides logging for fallback activation and mixin diagnostics **
    private static final Logger LOGGER = LogUtils.getLogger();

    // ** Ensures fallback warning is only printed once **
    private static boolean LOGGED_FALLBACK = false;

    // ** Wraps vanilla feature sorting to intercept cycles and substitute a safe fallback ordering **
@WrapMethod(method = "buildFeaturesPerStep")
private static <T> List<FeatureSorter.StepFeatureData> chaoschunks$wrapBuildFeaturesPerStep(
        List<T> featureSetSources,
        Function<T, List<HolderSet<PlacedFeature>>> toFeatureSetFunction,
        boolean flag,
        Operation<List<FeatureSorter.StepFeatureData>> original
) {
    try {
        List<FeatureSorter.StepFeatureData> res = original.call(featureSetSources, toFeatureSetFunction, flag);
        return chaoschunks$nonEmptyOrFallback(chaoschunks$sanitize(res));
    } catch (IndexOutOfBoundsException e) {
    LOGGER.warn("[ChaosChunks] FeatureSorter produced invalid/empty step data; using fallback ordering.", e);
    return chaoschunks$nonEmptyOrFallback(chaoschunks$fallback(featureSetSources, toFeatureSetFunction));
} catch (IllegalStateException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("Feature order cycle")) {
            if (!LOGGED_FALLBACK) {
                LOGGED_FALLBACK = true;
                LOGGER.warn("[ChaosChunks] FeatureSorter cycle detected; using fallback feature ordering (keeps all features; ignores ordering constraints). Message: {}", msg);
            }
            return chaoschunks$nonEmptyOrFallback(chaoschunks$fallback(featureSetSources, toFeatureSetFunction));
        }
        throw e;
    }
}
    // ** Ensures buildFeaturesPerStep never returns an empty list to prevent biome decoration crashes **
private static List<FeatureSorter.StepFeatureData> chaoschunks$nonEmptyOrFallback(
        List<FeatureSorter.StepFeatureData> in
) {
    if (in != null && !in.isEmpty()) return in;
    return java.util.List.of(new FeatureSorter.StepFeatureData(java.util.List.of(), pf -> 0));
}

    // ** Repairs invalid feature index mappings to prevent crashes during generation **
private static List<FeatureSorter.StepFeatureData> chaoschunks$sanitize(
        List<FeatureSorter.StepFeatureData> in
) {
    if (in == null || in.isEmpty()) return in;

    List<FeatureSorter.StepFeatureData> out = new ArrayList<>(in.size());

    for (FeatureSorter.StepFeatureData d : in) {
        if (d == null) continue;

        List<PlacedFeature> features = (d.features() == null) ? java.util.List.of() : d.features();
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

    // ** Builds a deterministic fallback feature ordering when vanilla sorting detects cycles **
    private static <T> List<FeatureSorter.StepFeatureData> chaoschunks$fallback(
            List<T> sources,
            Function<T, List<HolderSet<PlacedFeature>>> func
    ) {
        List<List<HolderSet<PlacedFeature>>> perSource = new ArrayList<>(sources.size());
        int maxSteps = 0;

        for (T src : sources) {
            List<HolderSet<PlacedFeature>> steps = func.apply(src);
            if (steps == null) steps = List.of();
            perSource.add(steps);
            maxSteps = Math.max(maxSteps, steps.size());
        }

        List<FeatureSorter.StepFeatureData> out = new ArrayList<>(maxSteps);

        for (int step = 0; step < maxSteps; step++) {
            Map<String, PlacedFeature> byId = new HashMap<>();
            List<PlacedFeature> unknown = new ArrayList<>();
            IdentityHashMap<PlacedFeature, Boolean> seenUnknown = new IdentityHashMap<>();

            for (List<HolderSet<PlacedFeature>> steps : perSource) {
                if (step >= steps.size()) continue;
                HolderSet<PlacedFeature> set = steps.get(step);
                if (set == null) continue;

                for (Holder<PlacedFeature> h : set) {
                    if (h == null) continue;
                    PlacedFeature pf = h.value();
                    if (pf == null) continue;

                    String id = holderKeyToIdString(h);
                    if (id != null) {
                        byId.putIfAbsent(id, pf);
                    } else {
                        if (!seenUnknown.containsKey(pf)) {
                            seenUnknown.put(pf, Boolean.TRUE);
                            unknown.add(pf);
                        }
                    }
                }
            }

            List<String> ids = new ArrayList<>(byId.keySet());
            ids.sort(Comparator.naturalOrder());

            List<PlacedFeature> features = new ArrayList<>(ids.size() + unknown.size());
            for (String id : ids) features.add(byId.get(id));
            features.addAll(unknown);

            IdentityHashMap<PlacedFeature, Integer> indexMap = new IdentityHashMap<>(features.size() * 2);
            for (int i = 0; i < features.size(); i++) indexMap.put(features.get(i), i);

            ToIntFunction<PlacedFeature> indexer = pf -> {
                Integer idx = indexMap.get(pf);
                return (idx != null && idx >= 0) ? idx : 0;
            };

            out.add(new FeatureSorter.StepFeatureData(features, indexer));
        }

        return out;
    }

    // ** Extracts a stable feature ID string from holder keys for deterministic sorting **
    private static String holderKeyToIdString(Holder<?> h) {
        var keyOpt = h.unwrapKey();
        if (keyOpt.isEmpty()) return null;

        String s = String.valueOf(keyOpt.get());
        int idx = s.lastIndexOf(" / ");
        if (idx < 0) return null;

        String tail = s.substring(idx + 3);
        if (tail.endsWith("]")) tail = tail.substring(0, tail.length() - 1);
        return tail;
    }
}
