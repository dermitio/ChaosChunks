package com.dermitio.chaoschunks.client;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.data.ChaosChunksCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public final class ChaosChunksCatalogClient {

    private ChaosChunksCatalogClient() {}

    // ResourceLocation instead of Identifier
    private static final ResourceLocation CATALOG_LISTENER_ID =
        ResourceLocation.fromNamespaceAndPath(ChaosChunks.MODID, "catalog");

    public static void init(IEventBus modBus) {
        modBus.addListener(ChaosChunksCatalogClient::onRegisterClientReloadListeners);
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(CatalogReloadListener.INSTANCE);
    }

    private static final class CatalogReloadListener implements ResourceManagerReloadListener {

        private static final CatalogReloadListener INSTANCE = new CatalogReloadListener();

        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            ChaosChunksCatalog.writeFromResources(resourceManager);
        }
    }
}
