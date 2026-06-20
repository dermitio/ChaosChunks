package com.dermitio.chaoschunks.network;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.data.time.TimekeepData;
import com.dermitio.chaoschunks.network.time.TimekeepDataPayload;
import com.dermitio.chaoschunks.network.time.TimekeepEditPayload;
import com.dermitio.chaoschunks.server.sound.ChaosChunksPlayerSoundPrefs;
import com.dermitio.chaoschunks.server.time.TimekeepSync;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// =========
// Optional client-to-server sync for per-player sound toggle preferences //
// =========
public final class ChaosChunksNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private ChaosChunksNetwork() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ChaosChunksNetwork::register);
    }

    // =========
    // Registers payloads as optional so clients can join servers without this mod //
    // =========
    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ChaosChunks.MODID)
                .versioned(PROTOCOL_VERSION)
                .optional();

        registrar.playToServer(
                ChaosChunksSetSoundTogglePayload.TYPE,
                ChaosChunksSetSoundTogglePayload.STREAM_CODEC,
                ChaosChunksNetwork::handleSetSoundToggle
        );

        registrar.playToServer(
                TimekeepEditPayload.TYPE,
                TimekeepEditPayload.STREAM_CODEC,
                ChaosChunksNetwork::handleTimekeepEdit
        );

        registrar.playToClient(
                TimekeepDataPayload.TYPE,
                TimekeepDataPayload.STREAM_CODEC,
                ChaosChunksNetwork::handleTimekeepData
        );
    } // yes I did miss that before

    // =========
    // Applies a client sound-toggle update to the server-side player preference store //
    // =========
    private static void handleSetSoundToggle(ChaosChunksSetSoundTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() == null) return;
            if (payload.eventKey().isBlank()
                    || payload.eventKey().length() > ChaosChunksSetSoundTogglePayload.MAX_EVENT_KEY_LENGTH) return;

            ChaosChunksPlayerSoundPrefs.set(
                    context.player().getUUID(),
                    payload.eventKey(),
                    payload.enabled()
            );
        });
    }

    private static void handleTimekeepData(TimekeepDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.getDist() != Dist.CLIENT) return;

            try {
                Class<?> clientPayloads = Class.forName("com.dermitio.chaoschunks.client.ChaosChunksClientPayloads");
                clientPayloads.getMethod("handleTimekeepData", TimekeepDataPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to handle Timekeep client payload", e);
            }
        });
    }

    private static void handleTimekeepEdit(TimekeepEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;

            if (!payload.hasValidBounds()) {
                player.sendSystemMessage(Component.literal("[ChaosChunks] Ignored invalid Timekeep edit payload."));
                return;
            }

            TimekeepData data = TimekeepData.get(player.level().getServer().overworld().getDataStorage());
            if (!data.editingEnabled) return;

            boolean changed;
            try {
                changed = switch (payload.action()) {
                    case SET_NODE -> data.setNode(payload.page(), payload.id(), payload.x(), payload.y());
                    case SET_HTML_FILE -> data.setNodeHtmlFile(payload.page(), payload.id(), payload.value());
                    case SET_ICON_ITEM -> data.setNodeIcon(payload.page(), payload.id(), payload.value());
                    case REMOVE_NODE -> data.removeNode(payload.page(), payload.id());
                    case CONNECT -> data.connect(payload.page(), payload.id(), payload.value());
                    case CLEAR_PAGE -> data.clearPage(payload.page());
                    case IMPORT_DATA -> TimekeepData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(payload.value()))
                            .result()
                            .map(imported -> data.replacePages(imported.pages()))
                            .orElse(false);
                };
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("[ChaosChunks] Failed to apply Timekeep edit payload."));
                return;
            }

            if (changed) {
                TimekeepSync.syncAll(player.level().getServer());
            }
        });
    }
}
