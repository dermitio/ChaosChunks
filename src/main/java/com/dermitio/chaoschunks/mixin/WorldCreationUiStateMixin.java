package com.dermitio.chaoschunks.mixin;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.server.ChaosChunksPendingConfig;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Mixin(WorldCreationUiState.class)
public class WorldCreationUiStateMixin {

    @Inject(method = "setWorldType", at = @At("TAIL"))
    private void chaoschunks$onSetWorldType(WorldCreationUiState.WorldTypeEntry entry, CallbackInfo ci) {
        ResourceKey<WorldPreset> presetKey = extractPresetKey(entry);

        if (ChaosChunks.CHAOS_PRESET_KEY.equals(presetKey)) {
            ChaosChunksPendingConfig.set(1, 1, "", Map.of(), Map.of());
        } else {
            ChaosChunksPendingConfig.clear();
        }
    }

    /**
     * Best-effort extraction of the selected preset key from WorldTypeEntry.
     * This is intentionally “robust” so you don’t need to guess the exact field name.
     */
    @SuppressWarnings("unchecked")
    private static ResourceKey<WorldPreset> extractPresetKey(Object entry) {
        if (entry == null) return null;

        // 1) Try common getter method names
        for (String mname : new String[]{"presetKey", "key", "worldPresetKey", "preset"}) {
            try {
                Method m = entry.getClass().getDeclaredMethod(mname);
                m.setAccessible(true);
                Object v = m.invoke(entry);
                ResourceKey<WorldPreset> k = coerceToPresetKey(v);
                if (k != null) return k;
            } catch (Throwable ignored) {}
        }

        // 2) Try fields with common names
        for (String fname : new String[]{"presetKey", "key", "preset", "worldPreset", "holder"}) {
            try {
                Field f = entry.getClass().getDeclaredField(fname);
                f.setAccessible(true);
                Object v = f.get(entry);
                ResourceKey<WorldPreset> k = coerceToPresetKey(v);
                if (k != null) return k;
            } catch (Throwable ignored) {}
        }

        // 3) Last resort: scan all fields for something coercible
        try {
            for (Field f : entry.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(entry);
                ResourceKey<WorldPreset> k = coerceToPresetKey(v);
                if (k != null) return k;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<WorldPreset> coerceToPresetKey(Object v) {
        if (v == null) return null;

        // Already a ResourceKey
        if (v instanceof ResourceKey<?> rk) {
            // we can’t 100% type-check, but it’s good enough here
            return (ResourceKey<WorldPreset>) rk;
        }

        // Identifier / ResourceLocation-like
        if (v instanceof Identifier id) {
            return ResourceKey.create(net.minecraft.core.registries.Registries.WORLD_PRESET, id);
        }

        // Holder<WorldPreset> -> unwrapKey()
        try {
            Method unwrapKey = v.getClass().getMethod("unwrapKey");
            Object opt = unwrapKey.invoke(v);
            // Optional<ResourceKey<WorldPreset>>
            Method orElse = opt.getClass().getMethod("orElse", Object.class);
            Object rk = orElse.invoke(opt, (Object) null);
            if (rk instanceof ResourceKey<?> rkk) return (ResourceKey<WorldPreset>) rkk;
        } catch (Throwable ignored) {}

        return null;
    }
}
