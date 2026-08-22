package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public final class OwnerFollowTeleportService {
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final double TELEPORT_DISTANCE_SQR = 12.0 * 12.0;

    public static void tick(EntityMaid maid) {
        if (maid.level().isClientSide || MaidRescueSessionData.get(maid).active()
                || maid.tickCount % CHECK_INTERVAL_TICKS != Math.floorMod(maid.getId(), CHECK_INTERVAL_TICKS)
                || maid.isHomeModeEnable() || !maid.canBrainMoving()) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null || owner.isSpectator() || owner.isDeadOrDying() || owner.level() != maid.level()
                || maid.distanceToSqr(owner) <= TELEPORT_DISTANCE_SQR || !isWalkingToOwner(maid, owner)) {
            return;
        }
        if (maid.teleportToOwner(owner)) {
            maid.getNavigationManager().resetNavigation();
        }
    }

    private static boolean isWalkingToOwner(EntityMaid maid, LivingEntity owner) {
        return maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(entity -> entity.equals(owner) || entity.equals(owner.getVehicle()))
                .isPresent();
    }

    private OwnerFollowTeleportService() {
    }
}
