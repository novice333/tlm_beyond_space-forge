package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.data.RescueSessionKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class DistressRecallService {
    public static RecallResult recallForOwner(ServerPlayer player, boolean notify) {
        int active = 0;
        int success = 0;
        int failed = 0;
        List<EntityMaid> activeMaids = new ArrayList<>();
        for (var level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid && player.getUUID().equals(maid.getOwnerUUID())) {
                    MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
                    if (session.active() && session.kind() == RescueSessionKind.DISTRESS) {
                        activeMaids.add(maid);
                    }
                }
            }
        }
        for (EntityMaid maid : activeMaids) {
            MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
            active++;
            RescueSessionManager.FinishResult finish =
                    RescueSessionManager.INSTANCE.finishAndReturnResult(maid, session);
            if (finish == RescueSessionManager.FinishResult.RETURNED) {
                success++;
            } else {
                failed++;
            }
        }
        RecallResult result = new RecallResult(active, success, failed);
        if (notify) {
            notify(player, result);
        }
        return result;
    }

    public static void recallForOwnerQuiet(ServerPlayer player) {
        recallForOwner(player, false);
    }

    /** Return all active rescue maids before the owner respawns. */
    public static void recallForOwnerDeath(ServerPlayer player) {
        for (EntityMaid maid : findActiveRescueMaids(player)) {
            MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
            boolean returned = false;
            try {
                returned = DistressCrossDimSupport.finishAndReturn(maid, session);
            } catch (Exception | LinkageError ignored) {
                // Owner death must remain safe even when an optional mod changes entity behavior.
            }
            if (!returned) {
                try {
                    DistressCrossDimSupport.restoreForPendingReturn(maid, session);
                } catch (Exception | LinkageError ignored) {
                }
            }
            if (!returned) {
                PendingMaidReturnService.defer(player.server, maid, session);
            }
        }
    }

    private static List<EntityMaid> findActiveRescueMaids(ServerPlayer player) {
        List<EntityMaid> activeMaids = new ArrayList<>();
        for (var level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid && player.getUUID().equals(maid.getOwnerUUID())) {
                    MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
                    if (session.active()) {
                        activeMaids.add(maid);
                    }
                }
            }
        }
        return activeMaids;
    }

    private static void notify(ServerPlayer player, RecallResult result) {
        if (result.active() == 0) {
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.no_active_distress"), true);
        } else if (result.failed() == 0) {
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.recall_success",
                    result.success()), true);
        } else {
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.recall_partial",
                    result.success(), result.failed()), true);
        }
    }

    public record RecallResult(int active, int success, int failed) {
    }

    private DistressRecallService() {
    }
}
