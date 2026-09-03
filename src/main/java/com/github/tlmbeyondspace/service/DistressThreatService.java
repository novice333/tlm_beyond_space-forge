package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class DistressThreatService {
    static final int ABSOLUTE_MINIMUM_ACTIVE_TICKS = 400;

    public static void tick(EntityMaid maid, MaidRescueSessionData.Data session) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player) || !player.isAlive() || player.level() != maid.level()) {
            RescueSessionManager.INSTANCE.finishImmediatelyWhenOwnerUnavailable(maid, session);
            return;
        }

        boolean immediateDanger = RescueSessionManager.INSTANCE.hasCurrentTarget(maid)
                || DamageSignalService.hasLiveSignal(maid)
                || DamageSignalService.hasLiveSignal(player);
        int scanInterval = Math.max(1, BeyondSpaceCommonConfig.SCAN_INTERVAL_TICKS.get());
        boolean scanNow = Math.floorMod(maid.tickCount, scanInterval)
                == Math.floorMod(maid.getId(), scanInterval);
        int quietTicks = session.quietTicks();

        if (immediateDanger) {
            quietTicks = 0;
        } else if (scanNow) {
            double radius = BeyondSpaceCommonConfig.RESCUE_RADIUS.get();
            boolean nearbyDanger = ThreatDetectionService.findThreatAroundAnchor(maid, maid.position(), radius).isPresent()
                    || ThreatDetectionService.findThreatAroundAnchor(maid, player.position(), radius).isPresent();
            if (nearbyDanger) {
                quietTicks = 0;
            } else {
                long advanced = (long) quietTicks + scanInterval;
                quietTicks = advanced > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) advanced;
            }
        }

        MaidRescueSessionData.Data updated = session.withQuietTicks(quietTicks);
        MaidRescueSessionData.set(maid, updated);
        long elapsed = maid.level().getGameTime() - session.startedAt();
        int minimum = Math.max(ABSOLUTE_MINIMUM_ACTIVE_TICKS,
                BeyondSpaceCommonConfig.SIGNAL_MIN_ACTIVE_TICKS.get());
        int maximum = Math.max(minimum, BeyondSpaceCommonConfig.SIGNAL_MAX_ACTIVE_TICKS.get());
        int quietRequired = BeyondSpaceCommonConfig.SIGNAL_QUIET_TICKS.get();
        if (elapsed >= maximum || (elapsed >= minimum && quietTicks >= quietRequired)) {
            RescueSessionManager.INSTANCE.finishAndReturn(maid, updated);
        }
    }

    private DistressThreatService() {
    }
}
