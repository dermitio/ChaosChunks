package com.dermitio.chaoschunks.server.command;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.data.world.ChaosChunksData;
import com.dermitio.chaoschunks.data.time.TimekeepData;
import com.dermitio.chaoschunks.server.config.ChaosChunksServerWorldgenConfig;
import com.dermitio.chaoschunks.server.runtime.ChaosChunksRuntimeApplier;
import com.dermitio.chaoschunks.server.time.TimekeepSync;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.nio.file.Paths;

// =========
// Server commands for managing ChaosChunks world generation without a client UI //
// =========
public final class ChaosChunksCommands {

    private ChaosChunksCommands() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerRoot(dispatcher, "chaoschunks");
        registerRoot(dispatcher, "ccs");
    }

    private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        var root = Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        root.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())));
        root.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));
        root.then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())));
        root.then(Commands.literal("save").executes(ctx -> save(ctx.getSource())));

        root.then(Commands.literal("export")
                .then(Commands.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> exportConfig(ctx.getSource(), StringArgumentType.getString(ctx, "path")))));

        root.then(Commands.literal("import")
                .then(Commands.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> importConfig(ctx.getSource(), StringArgumentType.getString(ctx, "path")))));

        var set = Commands.literal("set");
        set.then(Commands.literal("region")
                .then(Commands.argument("x", IntegerArgumentType.integer(1, 512))
                        .then(Commands.argument("z", IntegerArgumentType.integer(1, 512))
                                .executes(ctx -> setRegion(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "x"),
                                        IntegerArgumentType.getInteger(ctx, "z")
                                )))));
        set.then(Commands.literal("enabled")
                .then(Commands.literal("true").executes(ctx -> setEnabled(ctx.getSource()))));
        root.then(set);

        var dimensionId = Commands.argument("id", StringArgumentType.word());
        dimensionId.then(Commands.literal("mode")
                .then(Commands.literal("ON").executes(ctx -> setDimensionMode(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "ON")))
                .then(Commands.literal("SAFE").executes(ctx -> setDimensionMode(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "SAFE")))
                .then(Commands.literal("OFF").executes(ctx -> setDimensionMode(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "OFF"))));
        dimensionId.then(Commands.literal("seed")
                .then(Commands.literal("world").executes(ctx -> setDimensionSeed(ctx.getSource(), StringArgumentType.getString(ctx, "id"), false)))
                .then(Commands.literal("randomized").executes(ctx -> setDimensionSeed(ctx.getSource(), StringArgumentType.getString(ctx, "id"), true))));
        dimensionId.then(Commands.literal("terrain")
                .then(Commands.literal("normal").executes(ctx -> setDimensionTerrain(ctx.getSource(), StringArgumentType.getString(ctx, "id"), false)))
                .then(Commands.literal("randomized").executes(ctx -> setDimensionTerrain(ctx.getSource(), StringArgumentType.getString(ctx, "id"), true))));
        root.then(Commands.literal("dimension").then(dimensionId));

        root.then(Commands.literal("timekeep")
                .then(Commands.literal("editon").executes(ctx -> setTimekeepEditing(ctx.getSource(), true)))
                .then(Commands.literal("editoff").executes(ctx -> setTimekeepEditing(ctx.getSource(), false))));

        dispatcher.register(root);
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs status"), false);
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs reload | save"), false);
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs export <path> | import <path>"), false);
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs set region <x> <z> | set enabled true"), false);
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs dimension <id> mode ON|SAFE|OFF"), false);
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs dimension <id> seed world|randomized"), false);
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs dimension <id> terrain normal|randomized"), false);
        source.sendSuccess(() -> Component.literal("[ChaosChunks] /ccs timekeep editon|editoff"), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ChaosChunksData data = data(server);

        source.sendSuccess(() -> Component.literal(
                "[ChaosChunks] enabled=" + data.enabled
                        + ", region=" + data.regionX + "x" + data.regionZ
                        + ", globalBiomes=" + display(data.globalBiomes)
                        + ", dimensionModes=" + data.dimensionModes
                        + ", randomizedSeeds=" + data.dimensionSeedRandomizers.keySet()
                        + ", randomizedTerrain=" + data.dimensionTerrainRandomizers.keySet()
        ), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ChaosChunksServerWorldgenConfig.load();
        boolean applied = ChaosChunksServerWorldgenConfig.applyFirstWorldDefaults(source.getServer());
        ChaosChunksRuntimeApplier.applyToAllLevels(source.getServer());

        source.sendSuccess(() -> Component.literal(
                "[ChaosChunks] Reloaded " + ChaosChunksServerWorldgenConfig.defaultPath()
                        + (applied ? " and applied first-world defaults." : ".")
        ), true);
        return 1;
    }

    private static int save(CommandSourceStack source) {
        ChaosChunksServerWorldgenConfig.save(
                ChaosChunksServerWorldgenConfig.defaultPath(),
                ChaosChunksServerWorldgenConfig.fromData(data(source.getServer()))
        );

        source.sendSuccess(() -> Component.literal("[ChaosChunks] Saved active world settings to " + ChaosChunksServerWorldgenConfig.defaultPath()), true);
        return 1;
    }

    private static int exportConfig(CommandSourceStack source, String rawPath) {
        Path path = Paths.get(rawPath);
        ChaosChunksServerWorldgenConfig.save(path, ChaosChunksServerWorldgenConfig.fromData(data(source.getServer())));
        source.sendSuccess(() -> Component.literal("[ChaosChunks] Exported active world settings to " + path), true);
        return 1;
    }

    private static int importConfig(CommandSourceStack source, String rawPath) {
        Path path = Paths.get(rawPath);
        ChaosChunksServerWorldgenConfig.ServerWorldgenConfig config = ChaosChunksServerWorldgenConfig.load(path);
        ChaosChunksServerWorldgenConfig.applyToData(data(source.getServer()), config);
        ChaosChunksRuntimeApplier.applyToAllLevels(source.getServer());

        source.sendSuccess(() -> Component.literal("[ChaosChunks] Imported and applied world settings from " + path), true);
        return 1;
    }

    private static int setRegion(CommandSourceStack source, int x, int z) {
        ChaosChunksData data = data(source.getServer());
        data.enabled = true;
        data.regionX = x;
        data.regionZ = z;
        data.setDirty();
        ChaosChunksRuntimeApplier.applyToAllLevels(source.getServer());

        source.sendSuccess(() -> Component.literal("[ChaosChunks] Region size set to " + x + "x" + z), true);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source) {
        ChaosChunksData data = data(source.getServer());
        data.enabled = true;
        data.setDirty();
        ChaosChunksRuntimeApplier.applyToAllLevels(source.getServer());

        source.sendSuccess(() -> Component.literal("[ChaosChunks] Enabled set to true"), true);
        return 1;
    }

    private static int setDimensionMode(CommandSourceStack source, String dimId, String mode) {
        ChaosChunksData data = data(source.getServer());
        data.enabled = true;
        data.dimensionModes.put(dimId, ChaosChunksServerWorldgenConfig.normalizeMode(mode));
        data.setDirty();
        ChaosChunksRuntimeApplier.applyToAllLevels(source.getServer());

        source.sendSuccess(() -> Component.literal("[ChaosChunks] " + dimId + " mode set to " + mode), true);
        return 1;
    }

    private static int setDimensionSeed(CommandSourceStack source, String dimId, boolean randomized) {
        ChaosChunksData data = data(source.getServer());
        data.enabled = true;
        if (randomized) {
            data.dimensionSeedRandomizers.put(dimId, ChaosChunksServerWorldgenConfig.newSeedRandomizerSalt());
        } else {
            data.dimensionSeedRandomizers.remove(dimId);
        }
        data.setDirty();
        ChaosChunksRuntimeApplier.applyToAllLevels(source.getServer());

        source.sendSuccess(() -> Component.literal("[ChaosChunks] " + dimId + " region seed set to " + (randomized ? "randomized" : "world")), true);
        return 1;
    }

    private static int setDimensionTerrain(CommandSourceStack source, String dimId, boolean randomized) {
        ChaosChunksData data = data(source.getServer());
        data.enabled = true;
        if (randomized) {
            data.dimensionTerrainRandomizers.put(dimId, ChaosChunksServerWorldgenConfig.newSeedRandomizerSalt());
        } else {
            data.dimensionTerrainRandomizers.remove(dimId);
        }
        data.setDirty();
        ChaosChunksRuntimeApplier.applyToAllLevels(source.getServer());

        source.sendSuccess(() -> Component.literal("[ChaosChunks] " + dimId + " terrain profiles set to " + (randomized ? "randomized" : "normal")), true);
        return 1;
    }

    private static int setTimekeepEditing(CommandSourceStack source, boolean enabled) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) {
            source.sendFailure(Component.literal("[ChaosChunks] Timekeep is disabled by the Time, Void, Mint experiment."));
            return 0;
        }

        TimekeepData data = TimekeepData.get(source.getServer().overworld().getDataStorage());
        data.editingEnabled = enabled;
        data.setDirty();
        TimekeepSync.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("[ChaosChunks] Timekeep editing " + (enabled ? "enabled" : "disabled")), true);
        return 1;
    }

    private static ChaosChunksData data(MinecraftServer server) {
        return ChaosChunksData.get(server.overworld().getDataStorage());
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }
}
