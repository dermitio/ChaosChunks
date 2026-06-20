package com.dermitio.chaoschunks.server.time;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksItems;
import com.dermitio.chaoschunks.data.time.TimekeepData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;

// =========
// Handles passive and observed Timekeep node unlock conditions //
// =========
public final class TimekeepUnlocks {

    private static final double OBSERVE_RANGE = 10.0D;

    private TimekeepUnlocks() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TimekeepUnlocks::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(TimekeepUnlocks::onPlayerTick);
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getItemStack().is(ChaosChunksItems.TIME_BOOK.get())) return;
        if (!player.isShiftKeyDown()) return;

        TimekeepData data = TimekeepData.get(level.getServer().overworld().getDataStorage());
        boolean changed = false;
        for (TimekeepData.Page page : data.pages()) {
            for (TimekeepData.Node node : page.nodes()) {
                if (node.unlocked() || !"observe".equalsIgnoreCase(node.unlockType())) continue;
                if (matchesObserve(level, player, node.unlockTarget())) {
                    changed |= data.unlockNode(node.id());
                }
            }
        }

        if (changed) TimekeepSync.syncAll(level.getServer());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 20 != 0) return;

        TimekeepData data = TimekeepData.get(level.getServer().overworld().getDataStorage());
        boolean changed = false;
        for (TimekeepData.Page page : data.pages()) {
            for (TimekeepData.Node node : page.nodes()) {
                if (node.unlocked() || !"acquire".equalsIgnoreCase(node.unlockType())) continue;
                if (matchesAcquire(player, node.unlockTarget())) {
                    changed |= data.unlockNode(node.id());
                }
            }
        }

        if (changed) TimekeepSync.syncAll(level.getServer());
    }

    public static void triggerEvent(ServerLevel level, String eventId) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        if (level == null || level.getServer() == null || eventId == null || eventId.isBlank()) return;

        TimekeepData data = TimekeepData.get(level.getServer().overworld().getDataStorage());
        boolean changed = false;
        for (TimekeepData.Page page : data.pages()) {
            for (TimekeepData.Node node : page.nodes()) {
                if (node.unlocked() || !"event".equalsIgnoreCase(node.unlockType())) continue;
                if (eventId.equalsIgnoreCase(stripPrefix(node.unlockTarget(), "event"))) {
                    changed |= data.unlockNode(node.id());
                }
            }
        }

        if (changed) TimekeepSync.syncAll(level.getServer());
    }

    private static boolean matchesAcquire(ServerPlayer player, String target) {
        String id = stripPrefix(target, "item");
        if (id.isBlank()) return false;

        try {
            var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
            return item.isPresent() && player.getInventory().contains(stack -> stack.is(item.get()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean matchesObserve(ServerLevel level, ServerPlayer player, String target) {
        if (target == null || target.isBlank()) return false;

        String type = conditionType(target);
        return switch (type) {
            case "biome" -> matchesBiome(level, player, stripPrefix(target, "biome"));
            case "block" -> matchesBlock(level, player, stripPrefix(target, "block"));
            case "item", "entity" -> matchesEntity(level, player, stripPrefix(target, type), type);
            case "structure" -> matchesStructure(level, player, stripPrefix(target, "structure"));
            default -> matchesBiome(level, player, target)
                    || matchesBlock(level, player, target)
                    || matchesEntity(level, player, target, "entity")
                    || matchesStructure(level, player, target);
        };
    }

    private static boolean matchesBiome(ServerLevel level, ServerPlayer player, String id) {
        if (player.getLookAngle().y < 0.65D || !level.canSeeSky(player.blockPosition())) return false;
        return level.getBiome(player.blockPosition())
                .unwrapKey()
                .map(key -> key.identifier().toString().equals(id))
                .orElse(false);
    }

    private static boolean matchesBlock(ServerLevel level, ServerPlayer player, String id) {
        BlockHitResult hit = blockHit(level, player);
        if (hit.getType() != HitResult.Type.BLOCK) return false;

        BlockState state = level.getBlockState(hit.getBlockPos());
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return blockId != null && blockId.toString().equals(id);
    }

    private static boolean matchesEntity(ServerLevel level, ServerPlayer player, String id, String type) {
        EntityHitResult hit = entityHit(level, player);
        if (hit == null) return false;

        Entity entity = hit.getEntity();
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if ("entity".equals(type)) return entityId != null && entityId.toString().equals(id);

        if (!"item".equals(type) || !(entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) return false;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        return itemId != null && itemId.toString().equals(id);
    }

    private static boolean matchesStructure(ServerLevel level, ServerPlayer player, String id) {
        BlockPos pos = blockHit(level, player).getBlockPos();
        Map<Structure, ?> structures = level.structureManager().getAllStructuresAt(pos);
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Structure structure : structures.keySet()) {
            Identifier structureId = registry.getKey(structure);
            if (structureId != null && structureId.toString().equals(id)) return true;
        }
        return false;
    }

    private static BlockHitResult blockHit(ServerLevel level, ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(OBSERVE_RANGE));
        return level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static EntityHitResult entityHit(ServerLevel level, ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(OBSERVE_RANGE));
        AABB box = player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0D);
        return ProjectileUtil.getEntityHitResult(level, player, from, to, box, entity -> !entity.isSpectator() && entity.isPickable(), 0.3F);
    }

    private static String conditionType(String target) {
        int idx = target.indexOf(':');
        if (idx < 0) return "";
        String prefix = target.substring(0, idx);
        return switch (prefix) {
            case "biome", "block", "item", "entity", "structure" -> prefix;
            default -> "";
        };
    }

    private static String stripPrefix(String target, String prefix) {
        if (target == null) return "";
        String fullPrefix = prefix + ":";
        return target.startsWith(fullPrefix) ? target.substring(fullPrefix.length()) : target;
    }
}
