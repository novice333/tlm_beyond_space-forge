package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SafeTeleportService {
    private static final TicketType<UUID> RETURN_TICKET = TicketType.create("tlm_beyond_space_return",
            Comparator.comparing(UUID::toString), 200);

    public static boolean returnToOrigin(EntityMaid maid, MaidRescueSessionData.Data session) {
        if (!(maid.level() instanceof ServerLevel level)
                || session.originDimension() == null
                || !level.dimension().location().equals(session.originDimension())) {
            return false;
        }
        try (ReturnAreaLease ignored = openReturnArea(level, session.origin(),
                BeyondSpaceCommonConfig.SAFE_RETURN_RADIUS.get())) {
            Optional<Vec3> destination = findSafePositionResult(level, session.origin(),
                    BeyondSpaceCommonConfig.SAFE_RETURN_RADIUS.get(), maid).position();
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
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not hold bounded rescue return area in {} near {}",
                    level.dimension().location(), session.origin(), error);
            return false;
        }
    }

    public static Optional<Vec3> findSafePosition(ServerLevel level, Vec3 preferred, int radius, EntityMaid maid) {
        return findSafePositionResult(level, preferred, radius, maid).position();
    }

    public static SearchResult findSafePositionResult(ServerLevel level, Vec3 preferred, int radius,
                                                       EntityMaid maid) {
        Entity collisionEntity = maid.level() == level ? maid : null;
        return findSafePosition(level, preferred, radius, maid.getDimensions(maid.getPose()), collisionEntity,
                hasDrownProtection(maid));
    }

    public static Optional<Vec3> findSafeReturnPosition(ServerLevel level, Vec3 preferred, int radius,
                                                        EntityMaid maid) {
        try (ReturnAreaLease ignored = openReturnArea(level, preferred, radius)) {
            return findSafePositionResult(level, preferred, radius, maid).position();
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not load bounded rescue return area in {} near {}",
                    level.dimension().location(), preferred, error);
            return Optional.empty();
        }
    }

    private static SearchResult findSafePosition(ServerLevel level, Vec3 preferred, int radius,
                                                 EntityDimensions dimensions, Entity collisionEntity,
                                                 boolean waterProtected) {
        BlockPos origin = BlockPos.containing(preferred);
        boolean sawLava = false;
        boolean sawWaterWithoutProtection = false;
        for (int horizontal = 0; horizontal <= radius; horizontal++) {
            for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                for (int xOffset = -horizontal; xOffset <= horizontal; xOffset++) {
                    for (int zOffset = -horizontal; zOffset <= horizontal; zOffset++) {
                        if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != horizontal) continue;
                        BlockPos candidate = origin.offset(xOffset, yOffset, zOffset);
                        CandidateResult result = inspectCandidate(level, candidate, dimensions, collisionEntity,
                                waterProtected);
                        if (result == CandidateResult.SAFE) {
                            return SearchResult.success(Vec3.atBottomCenterOf(candidate));
                        }
                        sawLava |= result == CandidateResult.LAVA;
                        sawWaterWithoutProtection |= result == CandidateResult.WATER_REQUIRES_PROTECTION;
                    }
                }
            }
        }
        if (sawLava) {
            return SearchResult.failed(FailureReason.LAVA);
        }
        if (sawWaterWithoutProtection) {
            return SearchResult.failed(FailureReason.WATER_REQUIRES_DROWN_PROTECTION);
        }
        return SearchResult.failed(FailureReason.NO_SPACE);
    }

    public static void teleportTo(EntityMaid maid, Vec3 destination) {
        maid.stopRiding();
        maid.getNavigation().stop();
        clearCombatMemories(maid);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.teleportTo(destination.x, destination.y, destination.z);
    }

    private static CandidateResult inspectCandidate(ServerLevel level, BlockPos feet, EntityDimensions dimensions,
                                                    Entity collisionEntity, boolean waterProtected) {
        if (!level.getWorldBorder().isWithinBounds(feet)
                || !level.getChunkSource().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) {
            return CandidateResult.BLOCKED;
        }
        BlockState below = level.getBlockState(feet.below());
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        var belowFluid = level.getFluidState(feet.below());
        var feetFluid = level.getFluidState(feet);
        var headFluid = level.getFluidState(feet.above());
        if (belowFluid.is(FluidTags.LAVA) || feetFluid.is(FluidTags.LAVA)
                || headFluid.is(FluidTags.LAVA)) {
            return CandidateResult.LAVA;
        }
        if (below.getCollisionShape(level, feet.below()).isEmpty()
                || !feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, feet.above()).isEmpty()) {
            return CandidateResult.BLOCKED;
        }
        Vec3 destination = Vec3.atBottomCenterOf(feet);
        if (!level.noCollision(collisionEntity, dimensions.makeBoundingBox(destination))) {
            return CandidateResult.BLOCKED;
        }
        boolean feetWater = feetFluid.is(FluidTags.WATER);
        boolean headWater = headFluid.is(FluidTags.WATER);
        if ((!feetFluid.isEmpty() && !feetWater) || (!headFluid.isEmpty() && !headWater)) {
            return CandidateResult.BLOCKED;
        }
        if ((feetWater || headWater) && !waterProtected) {
            return CandidateResult.WATER_REQUIRES_PROTECTION;
        }
        return CandidateResult.SAFE;
    }

    public static boolean hasDrownProtection(EntityMaid maid) {
        try {
            return maid.getMaidBauble().containsItem(InitItems.DROWN_PROTECT_BAUBLE.get());
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.debug("Could not inspect drown-protection bauble for maid {}",
                    maid.getUUID(), error);
            return false;
        }
    }

    public static ReturnAreaLease openReturnArea(ServerLevel level, Vec3 preferred, int radius) {
        int boundedRadius = Math.max(0, Math.min(radius, 16));
        BlockPos center = BlockPos.containing(preferred);
        int minChunkX = (center.getX() - boundedRadius) >> 4;
        int maxChunkX = (center.getX() + boundedRadius) >> 4;
        int minChunkZ = (center.getZ() - boundedRadius) >> 4;
        int maxChunkZ = (center.getZ() + boundedRadius) >> 4;
        UUID ticketId = UUID.randomUUID();
        List<ChunkPos> held = new ArrayList<>();
        try {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                    level.getChunkSource().addRegionTicket(RETURN_TICKET, pos, 2, ticketId);
                    held.add(pos);
                    // Synchronous load is intentional: the safe-position scan and entity transfer happen
                    // in the same server task while the temporary ticket remains held.
                    level.getChunk(chunkX, chunkZ);
                }
            }
            return new ReturnAreaLease(level, ticketId, List.copyOf(held));
        } catch (Exception | LinkageError error) {
            held.forEach(pos -> level.getChunkSource()
                    .removeRegionTicket(RETURN_TICKET, pos, 2, ticketId));
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof LinkageError linkage) {
                throw linkage;
            }
            throw new IllegalStateException("Could not load rescue return chunks", error);
        }
    }

    public static final class ReturnAreaLease implements AutoCloseable {
        private final ServerLevel level;
        private final UUID ticketId;
        private final List<ChunkPos> held;
        private boolean closed;

        private ReturnAreaLease(ServerLevel level, UUID ticketId, List<ChunkPos> held) {
            this.level = level;
            this.ticketId = ticketId;
            this.held = held;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            held.forEach(pos -> level.getChunkSource()
                    .removeRegionTicket(RETURN_TICKET, pos, 2, ticketId));
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

    public enum FailureReason {
        NONE,
        NO_SPACE,
        WATER_REQUIRES_DROWN_PROTECTION,
        LAVA
    }

    public record SearchResult(Optional<Vec3> position, FailureReason failureReason) {
        static SearchResult success(Vec3 position) {
            return new SearchResult(Optional.of(position), FailureReason.NONE);
        }

        static SearchResult failed(FailureReason reason) {
            return new SearchResult(Optional.empty(), reason);
        }
    }

    private enum CandidateResult {
        SAFE,
        BLOCKED,
        WATER_REQUIRES_PROTECTION,
        LAVA
    }
}
