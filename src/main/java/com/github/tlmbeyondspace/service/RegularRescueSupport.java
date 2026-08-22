package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.data.RescueMode;
import com.github.tlmbeyondspace.data.RescueSessionKind;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class RegularRescueSupport {
    private static final double OWNER_TELEPORT_DISTANCE_SQR = 144.0D;

    public static boolean startRegular(RescueSessionManager manager, EntityMaid maid,
                                       LivingEntity target, RescueMode mode) {
        IMaidTask sourceTask = maid.getTask();
        if (sourceTask == null) {
            manager.applyFailureCooldown(maid);
            return false;
        }
        MaidRescueSessionData.Data session = null;
        try {
            IAttackTask attackTask = RescueCombatTaskSupport.resolveAttackTask(maid);
            if (attackTask == null) {
                TlmBeyondSpace.LOGGER.error("No usable rescue attack task for maid {}", maid.getUUID());
                manager.applyFailureCooldown(maid);
                return false;
            }

            session = new MaidRescueSessionData.Data(true,
                    RescueSessionKind.REGULAR, sourceTask.getUid(), attackTask.getUid(),
                    maid.level().dimension().location(), maid.position(), maid.isHomeModeEnable(),
                    maid.getSchedule(), maid.level().getGameTime(), 0, mode);
            MaidRescueSessionData.set(maid, session);
            WeaponCaseSwapService.beginSwap(maid, attackTask);

            if (!TaskSwitchService.prepareAndSwitch(maid, attackTask)) {
                rollbackStart(manager, maid, session);
                return false;
            }
            if (mode == RescueMode.BOND && !summonToOwnerIfNeeded(maid)) {
                rollbackStart(manager, maid, session);
                return false;
            }
            if (target != null && target.isAlive() && target.level() == maid.level() && !maid.isAlliedTo(target)) {
                maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                maid.setTarget(target);
            }
            return true;
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("REGULAR rescue start failed for maid {}", maid.getUUID(), error);
            if (session != null) {
                rollbackStart(manager, maid, session);
            } else {
                manager.applyFailureCooldown(maid);
            }
            return false;
        }
    }

    private static void rollbackStart(RescueSessionManager manager, EntityMaid maid,
                                      MaidRescueSessionData.Data session) {
        try {
            WeaponCaseSwapService.restoreIfActive(maid);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not roll back weapon swap for maid {}", maid.getUUID(), error);
        }
        TaskSwitchService.restore(maid, session, true);
        MaidRescueSessionData.clear(maid);
        manager.applyFailureCooldown(maid);
    }

    private static boolean summonToOwnerIfNeeded(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != maid.level()) {
            return true;
        }
        if (maid.distanceToSqr(owner) <= OWNER_TELEPORT_DISTANCE_SQR) {
            return true;
        }
        return maid.teleportToOwner(owner) || maid.distanceToSqr(owner) <= OWNER_TELEPORT_DISTANCE_SQR;
    }

    static boolean shouldStayNearOwnerAfterRescue(MaidRescueSessionData.Data session) {
        return session.kind() == RescueSessionKind.REGULAR
                && session.triggerMode() == RescueMode.BOND
                && !session.sourceHomeMode();
    }

    private RegularRescueSupport() {
    }
}
