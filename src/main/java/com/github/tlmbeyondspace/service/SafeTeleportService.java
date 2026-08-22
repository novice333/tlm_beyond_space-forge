package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class SafeTeleportService {
    public static boolean returnToOrigin(EntityMaid maid, MaidRescueSessionData.Data session) {
        if (!(maid.level() instanceof ServerLevel level)
                || session.originDimension() == null
                || !level.dimension().location().equals(session.originDimension())) {
            return false;
        }
        Optional<Vec3> destination = findSafeReturnPosition(level, session.origin(),
                BeyondSpaceCommonConfig.SAFE_RETURN_RADIUS.get(), maid);
        if (destination.isEmpty()) {
            return false;
        }
        maid.stopRiding();
        maid.getNavigation().stop();
        clearCombatMemories(maid);
        maid.setDeltaMovement(Vec3.ZERO);
        Vec3 pos = destination.get();
        maid.teleportTo(pos.x, pos.y, pos.z);
        return true;
    }

    public static Optional<Vec3> findSafePosition(ServerLevel level, Vec3 preferred, int radius, EntityMaid maid) {
        Entity collisionEntity = maid.level() == level ? maid : null;
        return findSafePosition(level, preferred, radius, maid.getDimensions(maid.getPose()), collisionEntity);
    }

    public static Optional<Vec3> findSafeReturnPosition(ServerLevel level, Vec3 preferred, int radius,
                                                        EntityMaid maid) {
        try {
            loadReturnArea(level, preferred, radius);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not load bounded rescue return area in {} near {}",
                    level.dimension().location(), preferred, error);
            return Optional.empty();
        }
        return findSafePosition(level, preferred, radius, maid);
    }

    private static Optional<Vec3> findSafePosition(ServerLevel level, Vec3 preferred, int radius,
                                                   EntityDimensions dimensions, Entity collisionEntity) {
        BlockPos origin = BlockPos.containing(preferred);
        for (int horizontal = 0; horizontal <= radius; horizontal++) {
            for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                for (int xOffset = -horizontal; xOffset <= horizontal; xOffset++) {
                    for (int zOffset = -horizontal; zOffset <= horizontal; zOffset++) {
                        if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != horizontal) continue;
                        BlockPos candidate = origin.offset(xOffset, yOffset, zOffset);
                        if (canStandAt(level, candidate, dimensions, collisionEntity)) {
                            return Optional.of(Vec3.atBottomCenterOf(candidate));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static void teleportTo(EntityMaid maid, Vec3 destination) {
        maid.stopRiding();
        maid.getNavigation().stop();
        clearCombatMemories(maid);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.teleportTo(destination.x, destination.y, destination.z);
    }

    private static boolean canStandAt(ServerLevel level, BlockPos feet, EntityDimensions dimensions,
                                      Entity collisionEntity) {
        if (!level.getWorldBorder().isWithinBounds(feet)
                || !level.getChunkSource().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) {
            return false;
        }
        BlockState below = level.getBlockState(feet.below());
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        if (below.getCollisionShape(level, feet.below()).isEmpty()
                || !feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, feet.above()).isEmpty()
                || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(feet.above()).isEmpty()) {
            return false;
        }
        Vec3 destination = Vec3.atBottomCenterOf(feet);
        return level.noCollision(collisionEntity, dimensions.makeBoundingBox(destination));
    }

    private static void loadReturnArea(ServerLevel level, Vec3 preferred, int radius) {
        int boundedRadius = Math.max(0, Math.min(radius, 16));
        BlockPos center = BlockPos.containing(preferred);
        int minChunkX = (center.getX() - boundedRadius) >> 4;
        int maxChunkX = (center.getX() + boundedRadius) >> 4;
        int minChunkZ = (center.getZ() - boundedRadius) >> 4;
        int maxChunkZ = (center.getZ() + boundedRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    public static void clearCombatMemories(EntityMaid maid) {
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
    }

    private SafeTeleportService() {
    }
}
