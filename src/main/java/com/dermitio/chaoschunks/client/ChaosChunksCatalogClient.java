package com.dermitio.chaoschunks.client;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.data.ChaosChunksCatalog;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

public final class ChaosChunksCatalogClient {

    // ** Prevents instantiation since this class only provides static client hooks **
    private ChaosChunksCatalogClient() {}

    // ** Identifier used to register the catalog reload listener **
    private static final Identifier CATALOG_LISTENER_ID =
            Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "catalog");

    // ** Registers client reload listener hook on the mod event bus **
    public static void init(IEventBus modBus) {
        modBus.addListener(ChaosChunksCatalogClient::onAddClientReloadListeners);
    }

    // ** Adds the catalog reload listener when the client reload listener event fires **
    private static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(CATALOG_LISTENER_ID, CatalogReloadListener.INSTANCE);
    }

    // ** Reload listener that rebuilds the biome/dimension catalog from resources **
    private static final class CatalogReloadListener implements ResourceManagerReloadListener {

        // ** Singleton instance used by the reload system **
        private static final CatalogReloadListener INSTANCE = new CatalogReloadListener();

        // ** Rewrites catalog.json whenever client resources reload **
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            ChaosChunksCatalog.writeFromResources(resourceManager);
        }
    }
}
