package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.compat.PromaidCompat;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import com.github.tlmbeyondspace.data.MaidRescueProfileData;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.data.RescueMode;
import com.github.tlmbeyondspace.data.RescueSessionKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RescueSessionManager {
    public static final RescueSessionManager INSTANCE = new RescueSessionManager();
    private static final int MAX_BLOCKING_RETURN_RETRY_TICKS = 100;
    private final Map<UUID, Long> failureCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> returnFailureSince = new ConcurrentHashMap<>();

    public void tick(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel) || !maid.isAlive()) {
            return;
        }
        MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
        if (session.returnPending()) {
            if (maid.tickCount % 20 == Math.floorMod(maid.getId(), 20)) {
                DistressCrossDimSupport.finishAndReturn(maid, session);
            }
            return;
        }
        if (session.active()) {
            tickActive(maid, session);
            return;
        }
        // A save or another mod may end the task without clearing our transient swap state.
        // Reconcile it before considering a new rescue, otherwise the weapon could remain active indefinitely.
        WeaponCaseSwapService.restoreIfActive(maid);
        int interval = BeyondSpaceCommonConfig.SCAN_INTERVAL_TICKS.get();
        if (maid.tickCount % interval == Math.floorMod(maid.getId(), interval)) {
            tickReady(maid);
        }
    }

    private void tickReady(EntityMaid maid) {
        if (PromaidCompat.shouldPrioritizeSelfPreservation(maid)) {
            return;
        }
        LivingEntity currentOwner = maid.getOwner();
        if (currentOwner != null && !currentOwner.isAlive()) {
            return;
        }
        MaidRescueProfileData.Data binding = MaidRescueProfileData.get(maid);
        IMaidTask currentTask = maid.getTask();
        if (!binding.bound() || currentTask == null || RescueTaskClassifier.isCombatTask(currentTask)) {
            return;
        }
        long now = maid.level().getGameTime();
        if (failureCooldowns.getOrDefault(maid.getUUID(), 0L) > now) {
            return;
        }
        ResourceLocation currentTaskId;
        try {
            currentTaskId = currentTask.getUid();
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not read current task UID for maid {}; delaying rescue",
                    maid.getUUID(), error);
            applyFailureCooldown(maid);
            return;
        }
        RescueMode mode = binding.profile().get(currentTaskId);
        if (mode == RescueMode.FORBIDDEN) {
            return;
        }
        Optional<LivingEntity> owner = Optional.ofNullable(currentOwner);
        Optional<LivingEntity> trigger = findTrigger(maid, owner, mode);
        trigger.ifPresent(target -> startRegular(maid, target, mode));
    }

    private Optional<LivingEntity> findTrigger(EntityMaid maid, Optional<LivingEntity> owner, RescueMode mode) {
        Optional<LivingEntity> maidAttacker = DamageSignalService.findAttacker(maid);
        if (maidAttacker.isPresent()) {
            return maidAttacker;
        }
        if (mode == RescueMode.BOND && owner.isPresent()) {
            Optional<LivingEntity> ownerAttacker = DamageSignalService.findAttacker(owner.get());
            if (ownerAttacker.isPresent()) {
                return ownerAttacker;
            }
        }
        double radius = BeyondSpaceCommonConfig.RESCUE_RADIUS.get();
        Optional<LivingEntity> aroundMaid = ThreatDetectionService.findThreatAround(maid, maid, radius);
        if (aroundMaid.isPresent()) {
            return aroundMaid;
        }
        return mode == RescueMode.BOND && owner.isPresent()
                ? ThreatDetectionService.findThreatAround(maid, owner.get(), radius)
                : Optional.empty();
    }

    public boolean startRegular(EntityMaid maid, LivingEntity target, RescueMode mode) {
        if (PromaidCompat.shouldPrioritizeSelfPreservation(maid)) {
            return false;
        }
        LivingEntity owner = maid.getOwner();
        if (owner != null && !owner.isAlive()) {
            return false;
        }
        return RegularRescueSupport.startRegular(this, maid, target, mode);
    }

    public boolean startDistress(EntityMaid maid, IMaidTask combatTask, Optional<LivingEntity> target,
                                 Vec3 summonPosition) {
        return startDistress(maid, combatTask, target, summonPosition, true);
    }

    public boolean startDistress(EntityMaid maid, IMaidTask combatTask, Optional<LivingEntity> target,
                                 Vec3 summonPosition, boolean transportMaid) {
        return startDistressDetailed(maid, combatTask, target, summonPosition, transportMaid)
                == DistressStartResult.SUCCESS;
    }

    public DistressStartResult startDistressDetailed(EntityMaid maid, IMaidTask combatTask,
                                                      Optional<LivingEntity> target,
                                                      Vec3 summonPosition, boolean transportMaid) {
        if (PromaidCompat.shouldPrioritizeSelfPreservation(maid)
                || !RescueTaskClassifier.isCombatTask(combatTask)
                || MaidRescueSessionData.get(maid).active()) {
            return DistressStartResult.PRECONDITION_FAILED;
        }
        IMaidTask sourceTask = maid.getTask();
        if (sourceTask == null) {
            applyFailureCooldown(maid);
            return DistressStartResult.SOURCE_TASK_MISSING;
        }
        boolean sourceSitting = maid.isMaidInSittingPose();
        MaidRescueSessionData.Data session = new MaidRescueSessionData.Data(true,
                RescueSessionKind.DISTRESS, sourceTask.getUid(), combatTask.getUid(),
                maid.level().dimension().location(), maid.position(), maid.isHomeModeEnable(),
                maid.getSchedule(), maid.level().getGameTime(), 0, RescueMode.BOND,
                true, sourceSitting);
        MaidRescueSessionData.set(maid, session);
        if (sourceSitting) {
            try {
                maid.setInSittingPose(false);
            } catch (Exception | LinkageError error) {
                TlmBeyondSpace.LOGGER.warn("Could not release sitting pose for DISTRESS maid {}",
                        maid.getUUID(), error);
                DistressCrossDimSupport.restoreSourceSitting(maid, session);
                MaidRescueSessionData.clear(maid);
                applyFailureCooldown(maid);
                return DistressStartResult.SITTING_RELEASE_FAILED;
            }
        }
        WeaponCaseSwapService.beginSwap(maid, combatTask);
        TaskSwitchService.SwitchResult switchResult = TaskSwitchService.prepareAndSwitchDetailed(maid, combatTask);
        if (switchResult != TaskSwitchService.SwitchResult.SUCCESS) {
            WeaponCaseSwapService.restoreIfActive(maid);
            DistressCrossDimSupport.restoreSourceSitting(maid, session);
            MaidRescueSessionData.clear(maid);
            applyFailureCooldown(maid);
            return switch (switchResult) {
                case MISSING_REQUIRED_ITEM -> DistressStartResult.MISSING_REQUIRED_ITEM;
                case REJECTED -> DistressStartResult.TASK_SWITCH_REJECTED;
                case ERROR -> DistressStartResult.TASK_SWITCH_ERROR;
                default -> DistressStartResult.TASK_SWITCH_ERROR;
            };
        }
        if (transportMaid) {
            SafeTeleportService.teleportTo(maid, summonPosition);
        }
        target.filter(LivingEntity::isAlive)
                .filter(entity -> entity.level() == maid.level() && !maid.isAlliedTo(entity))
                .ifPresent(entity -> {
                    maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, entity);
                    maid.setTarget(entity);
                });
        return DistressStartResult.SUCCESS;
    }

    public enum DistressStartResult {
        SUCCESS,
        PRECONDITION_FAILED,
        SOURCE_TASK_MISSING,
        SITTING_RELEASE_FAILED,
        MISSING_REQUIRED_ITEM,
        TASK_SWITCH_REJECTED,
        TASK_SWITCH_ERROR
    }

    private void tickActive(EntityMaid maid, MaidRescueSessionData.Data session) {
        if (!isSessionCombatTask(maid, session)) {
            DistressCrossDimSupport.restoreAfterExternalTaskChange(maid, session);
            return;
        }
        if (PromaidCompat.shouldPrioritizeSelfPreservation(maid)) {
            DistressCrossDimSupport.releaseForSelfPreservation(maid, session);
            return;
        }
        if (session.kind() == RescueSessionKind.DISTRESS) {
            DistressThreatService.tick(maid, session);
            return;
        }
        boolean dangerous = hasCurrentTarget(maid)
                || DamageSignalService.hasLiveSignal(maid)
                || (session.triggerMode() == RescueMode.BOND
                && Optional.ofNullable(maid.getOwner()).filter(DamageSignalService::hasLiveSignal).isPresent())
                || ThreatDetectionService.findThreatAroundAnchor(maid, session.origin(),
                BeyondSpaceCommonConfig.RESCUE_RADIUS.get()).isPresent();

        int quietTicks = dangerous ? 0 : session.quietTicks() + 1;
        MaidRescueSessionData.Data updated = session.withQuietTicks(quietTicks);
        MaidRescueSessionData.set(maid, updated);
        if (quietTicks >= BeyondSpaceCommonConfig.QUIET_TICKS.get()) {
            finishAndReturn(maid, updated);
        }
    }

    boolean hasCurrentTarget(EntityMaid maid) {
        Optional<LivingEntity> brainTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (brainTarget.filter(LivingEntity::isAlive).isPresent()) {
            return true;
        }
        return maid.getTarget() != null && maid.getTarget().isAlive();
    }

    public boolean finishAndReturn(EntityMaid maid, MaidRescueSessionData.Data session) {
        return finishAndReturnResult(maid, session) != FinishResult.FAILED;
    }

    public FinishResult finishAndReturnResult(EntityMaid maid, MaidRescueSessionData.Data session) {
        if (!isSessionCombatTask(maid, session)) {
            returnFailureSince.remove(maid.getUUID());
            DistressCrossDimSupport.restoreAfterExternalTaskChange(maid, session);
            return FinishResult.EXTERNAL_TASK_ENDED;
        }
        if (DistressCrossDimSupport.finishAndReturn(maid, session)) {
            returnFailureSince.remove(maid.getUUID());
            return FinishResult.RETURNED;
        }
        long now = maid.level().getGameTime();
        long firstFailure = returnFailureSince.computeIfAbsent(maid.getUUID(), ignored -> now);
        if (now - firstFailure >= MAX_BLOCKING_RETURN_RETRY_TICKS
                && maid.level() instanceof ServerLevel level) {
            PendingMaidReturnService.defer(level.getServer(), maid, session);
            DistressCrossDimSupport.restoreForPendingReturn(maid, session);
            returnFailureSince.remove(maid.getUUID());
            return FinishResult.DEFERRED;
        }
        return FinishResult.FAILED;
    }

    public void finishImmediatelyWhenOwnerUnavailable(EntityMaid maid,
                                                       MaidRescueSessionData.Data session) {
        FinishResult result = finishAndReturnResult(maid, session);
        if (result == FinishResult.FAILED && maid.level() instanceof ServerLevel level) {
            PendingMaidReturnService.defer(level.getServer(), maid, session);
            DistressCrossDimSupport.restoreForPendingReturn(maid, session);
            returnFailureSince.remove(maid.getUUID());
        }
    }

    private boolean isSessionCombatTask(EntityMaid maid, MaidRescueSessionData.Data session) {
        if (session.combatTask() == null || maid.getTask() == null) {
            return false;
        }
        try {
            return session.combatTask().equals(maid.getTask().getUid());
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not verify rescue task for maid {}; ending the session safely",
                    maid.getUUID(), error);
            return false;
        }
    }

    public boolean isLiveRescueSession(EntityMaid maid, MaidRescueSessionData.Data session) {
        return session.active() && session.originDimension() != null && session.sourceTask() != null
                && session.combatTask() != null && isSessionCombatTask(maid, session);
    }

    public void clearBindingSafely(EntityMaid maid) {
        MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
        if (session.active()) {
            finishAndReturn(maid, session);
        }
        MaidRescueProfileData.clear(maid);
    }

    void applyFailureCooldown(EntityMaid maid) {
        failureCooldowns.put(maid.getUUID(), maid.level().getGameTime()
                + BeyondSpaceCommonConfig.FAILURE_COOLDOWN_TICKS.get());
    }

    public void clearCaches() {
        failureCooldowns.clear();
        returnFailureSince.clear();
    }

    private RescueSessionManager() {
    }

    public enum FinishResult {
        RETURNED,
        EXTERNAL_TASK_ENDED,
        DEFERRED,
        FAILED
    }
}
