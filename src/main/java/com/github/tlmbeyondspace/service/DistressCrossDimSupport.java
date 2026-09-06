package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.compat.PromaidCompat;
import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import com.github.tlmbeyondspace.data.DistressSignalData;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.data.MaidRosterEntry;
import com.github.tlmbeyondspace.data.RescueSessionKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DistressCrossDimSupport {
    private static final int MAX_DETAIL_LINES = 8;
    private static final String UNCOMMITTED_TRANSFER_TAG = "tlm_beyond_space_uncommitted_transfer";

    public static void activate(ServerPlayer player, InteractionHand hand, ItemStack signal) {
        if (player.getCooldowns().isOnCooldown(signal.getItem())) {
            return;
        }
        DistressSignalData data = DistressSignalData.fromItem(signal);
        if (!data.canUse(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.signal_wrong_owner"), true);
            return;
        }
        if (data.ownerId().isEmpty()) {
            data = new DistressSignalData(player.getUUID(), data.selections(), data.recallMode(),
                    data.knockdownRescue());
            DistressSignalData.writeItem(signal, data);
        }

        int successLimit = Math.min(Math.max(1, BeyondSpaceCommonConfig.MAX_SIGNAL_HELPERS.get()), 20);
        if (MaidChunkLoadService.prepare(player, signal.getItem(), data, successLimit,
                MaidChunkLoadService.ActivationKind.MANUAL)) {
            return;
        }
        activatePrepared(player, signal.getItem(), data, successLimit,
                MaidChunkLoadService.ActivationKind.MANUAL, Map.of());
    }

    public static void activateForKnockdown(ServerPlayer player, ItemStack signal) {
        if (player.getCooldowns().isOnCooldown(signal.getItem())) {
            return;
        }
        DistressSignalData data = DistressSignalData.fromItem(signal);
        if (!data.knockdownRescue() || !data.canUse(player.getUUID())) {
            return;
        }
        if (data.ownerId().isEmpty()) {
            data = new DistressSignalData(player.getUUID(), data.selections(), data.recallMode(), true);
            DistressSignalData.writeItem(signal, data);
        }
        if (MaidChunkLoadService.prepare(player, signal.getItem(), data, 1,
                MaidChunkLoadService.ActivationKind.MAID_REFORM_KNOCKDOWN)) {
            return;
        }
        activatePrepared(player, signal.getItem(), data, 1,
                MaidChunkLoadService.ActivationKind.MAID_REFORM_KNOCKDOWN, Map.of());
    }

    static void activatePrepared(ServerPlayer player, Item signalItem, DistressSignalData data,
                                 int successLimit, MaidChunkLoadService.ActivationKind activationKind,
                                 Map<UUID, MaidChunkLoadService.LoadFailure> loadFailures) {
        List<Map.Entry<UUID, DistressSignalData.Selection>> selected = data.selections().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .toList();
        Map<UUID, String> names = buildNameMap(player, data);
        Optional<LivingEntity> priorityTarget = findPriorityThreat(player);
        ServerLevel destinationLevel = player.serverLevel();
        MinecraftServer server = player.server;
        int successCount = 0;
        List<SkipEntry> failures = new ArrayList<>();

        for (int selectionIndex = 0;
             selectionIndex < selected.size() && successCount < successLimit;
             selectionIndex++) {
            UUID maidId = selected.get(selectionIndex).getKey();
            DistressSignalData.Selection selection = selected.get(selectionIndex).getValue();
            String maidName = names.getOrDefault(maidId, shortUnknownName(maidId));
            Optional<EntityMaid> loaded = MaidRosterService.findLoadedMaid(server, maidId);
            if (loaded.isEmpty()) {
                SkipReason reason = SoulSpellMaidService.isStored(player, maidId)
                        ? SkipReason.STORED_IN_SOUL_SPELL
                        : !selection.loadUnloaded()
                        ? SkipReason.CHUNK_LOADING_DISABLED
                        : skipReasonForLoad(loadFailures.get(maidId));
                failures.add(new SkipEntry(maidName, reason));
                continue;
            }

            EntityMaid oldMaid = loaded.get();
            maidName = safeName(oldMaid, maidName);
            if (!player.getUUID().equals(oldMaid.getOwnerUUID())) {
                failures.add(new SkipEntry(maidName, SkipReason.NOT_OWNER));
                continue;
            }
            MaidRescueSessionData.Data existingSession = MaidRescueSessionData.get(oldMaid);
            if (existingSession.returnPending()) {
                // Normal summon mode must never double as recall. Only DistressSignalItem's
                // dedicated recall-mode branch is allowed to return an existing rescue session.
                failures.add(new SkipEntry(maidName, SkipReason.RETURN_PENDING));
                continue;
            }
            if (existingSession.active()) {
                if (RescueSessionManager.INSTANCE.isLiveRescueSession(oldMaid, existingSession)) {
                    failures.add(new SkipEntry(maidName, SkipReason.ACTIVE_SESSION));
                    continue;
                }
                restoreAfterExternalTaskChange(oldMaid, existingSession);
            }
            if (PromaidCompat.shouldPrioritizeSelfPreservation(oldMaid)) {
                failures.add(new SkipEntry(maidName, SkipReason.SELF_PRESERVING));
                continue;
            }
            IMaidTask combatTask = RescueCombatTaskSupport.resolveForMaid(oldMaid);
            if (!RescueTaskClassifier.isCombatTask(combatTask)) {
                failures.add(new SkipEntry(maidName, SkipReason.INVALID_TASK));
                continue;
            }

            boolean sameDimension = oldMaid.level() == destinationLevel;
            boolean preserveMounted = activationKind == MaidChunkLoadService.ActivationKind.MAID_REFORM_KNOCKDOWN
                    && oldMaid.isPassenger();
            if (preserveMounted && !sameDimension) {
                failures.add(new SkipEntry(maidName, SkipReason.MOUNTED_CROSS_DIMENSION));
                continue;
            }

            Optional<Vec3> safePosition;
            if (preserveMounted) {
                safePosition = Optional.of(oldMaid.position());
            } else {
                Vec3 preferred = summonPoint(player.position(), successCount, successLimit);
                SafeTeleportService.SearchResult safeSearch = SafeTeleportService.findSafePositionResult(
                        destinationLevel, preferred, 3, oldMaid);
                safePosition = safeSearch.position();
                if (safePosition.isEmpty()) {
                    failures.add(new SkipEntry(maidName, skipReasonForSafeSearch(safeSearch.failureReason())));
                    continue;
                }
            }

            Vec3 initialPosition = sameDimension ? safePosition.get() : oldMaid.position();
            Optional<LivingEntity> initialTarget = sameDimension ? priorityTarget : Optional.empty();
            RescueSessionManager.DistressStartResult startResult;
            try {
                startResult = RescueSessionManager.INSTANCE.startDistressDetailed(
                        oldMaid, combatTask, initialTarget, initialPosition, !preserveMounted);
            } catch (Exception | LinkageError error) {
                TlmBeyondSpace.LOGGER.warn("DISTRESS start failed for maid {}", oldMaid.getUUID(), error);
                rollbackFailedStart(oldMaid);
                startResult = RescueSessionManager.DistressStartResult.TASK_SWITCH_ERROR;
            }
            if (startResult != RescueSessionManager.DistressStartResult.SUCCESS) {
                failures.add(new SkipEntry(maidName, skipReasonForStart(startResult)));
                continue;
            }
            if (sameDimension) {
                successCount++;
                continue;
            }

            TransferOutcome transfer = transferMaid(oldMaid, destinationLevel, safePosition.get());
            if (!transfer.success()) {
                if (transfer.rollbackOriginal()) {
                    rollbackFailedStart(oldMaid);
                }
                failures.add(new SkipEntry(maidName, skipReasonForTransfer(transfer.status())));
                continue;
            }

            EntityMaid movedMaid = transfer.maid();
            setTargetIfValid(movedMaid, priorityTarget);
            successCount++;
        }

        if (successCount > 0) {
            player.getCooldowns().addCooldown(signalItem, BeyondSpaceCommonConfig.SIGNAL_COOLDOWN_TICKS.get());
        }
        if (successCount > 0 || !failures.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.signal_result",
                    successCount, failures.size()), true);
        }
        sendSkipDetails(player, failures);
    }

    public static boolean finishAndReturn(EntityMaid maid, MaidRescueSessionData.Data session) {
        if (session.kind() != RescueSessionKind.DISTRESS) {
            return finishRegular(maid, session);
        }
        return finishDistressAndReturn(maid, session);
    }

    private static boolean finishRegular(EntityMaid maid, MaidRescueSessionData.Data session) {
        boolean followedOwnerBeforeRescue = RegularRescueSupport.shouldStayNearOwnerAfterRescue(session);
        boolean mounted = RegularRescueSupport.shouldSkipAutomaticReturnTeleport(maid);
        if (!followedOwnerBeforeRescue && !mounted && !SafeTeleportService.returnToOrigin(maid, session)) {
            TlmBeyondSpace.LOGGER.debug("Could not find a safe rescue return position for maid {} at {}",
                    maid.getUUID(), session.origin());
            return false;
        }
        restoreTemporaryState(maid, session, true);
        return true;
    }

    private static boolean finishDistressAndReturn(EntityMaid maid, MaidRescueSessionData.Data session) {
        // A mounted maid may have entered an automatic combat session without being transported.
        // Ending that session must not pull her off the broom merely to reapply the same position.
        if (maid.isPassenger()) {
            restoreTemporaryState(maid, session, true);
            return true;
        }
        if (!(maid.level() instanceof ServerLevel sourceLevel) || session.originDimension() == null) {
            return false;
        }
        ServerLevel originLevel = findLevel(sourceLevel.getServer(), session.originDimension());
        if (originLevel == null) {
            TlmBeyondSpace.LOGGER.warn("Could not resolve distress origin dimension {} for maid {}",
                    session.originDimension(), maid.getUUID());
            return false;
        }
        try (SafeTeleportService.ReturnAreaLease ignored = SafeTeleportService.openReturnArea(
                originLevel, session.origin(), BeyondSpaceCommonConfig.SAFE_RETURN_RADIUS.get())) {
            Optional<Vec3> safeOrigin = SafeTeleportService.findSafePositionResult(originLevel, session.origin(),
                    BeyondSpaceCommonConfig.SAFE_RETURN_RADIUS.get(), maid).position();
            if (safeOrigin.isEmpty()) {
                return false;
            }

            EntityMaid returnedMaid;
            if (sourceLevel == originLevel) {
                SafeTeleportService.teleportTo(maid, safeOrigin.get());
                returnedMaid = maid;
            } else {
                TransferOutcome transfer = transferMaid(maid, originLevel, safeOrigin.get());
                if (!transfer.success()) {
                    TlmBeyondSpace.LOGGER.debug("Could not cross-dimension return distress maid {}: {}",
                            maid.getUUID(), transfer.status());
                    return false;
                }
                returnedMaid = transfer.maid();
            }

            restoreTemporaryState(returnedMaid, session, true);
            return true;
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not hold distress return chunks for maid {} in {} near {}",
                    maid.getUUID(), originLevel.dimension().location(), session.origin(), error);
            return false;
        }
    }

    private static void restoreTemporaryState(EntityMaid maid, MaidRescueSessionData.Data session,
                                              boolean restoreTask) {
        restoreTemporaryState(maid, session, restoreTask, true);
    }

    private static void restoreTemporaryState(EntityMaid maid, MaidRescueSessionData.Data session,
                                              boolean restoreTask, boolean restoreSitting) {
        try {
            WeaponCaseSwapService.restoreIfActive(maid);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not restore weapon swap for maid {}", maid.getUUID(), error);
        }
        TaskSwitchService.restore(maid, session, restoreTask);
        if (restoreSitting) {
            restoreSourceSitting(maid, session);
        }
        MaidRescueSessionData.clear(maid);
        DamageSignalService.clearVictim(maid.getUUID());
        MaidRosterService.observeImmediately(maid);
    }

    static void restoreForPendingReturn(EntityMaid maid, MaidRescueSessionData.Data session) {
        try {
            WeaponCaseSwapService.restoreIfActive(maid);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not restore weapon before deferred return for maid {}",
                    maid.getUUID(), error);
        }
        TaskSwitchService.restore(maid, session, true);
        restoreSourceSitting(maid, session);
        MaidRescueSessionData.set(maid, session.asReturnPending());
        DamageSignalService.clearVictim(maid.getUUID());
        MaidRosterService.observeImmediately(maid);
    }

    static void releaseForSelfPreservation(EntityMaid maid, MaidRescueSessionData.Data session) {
        // 低血时不传送、不恢复坐姿，避免打断 Promaid 的原地逃生。
        restoreTemporaryState(maid, session, true, false);
    }

    static void restoreAfterExternalTaskChange(EntityMaid maid, MaidRescueSessionData.Data session) {
        try {
            WeaponCaseSwapService.restoreIfActive(maid);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not restore weapon after external task change for maid {}",
                    maid.getUUID(), error);
        }
        TaskSwitchService.restore(maid, session, false);
        restoreSourceSitting(maid, session);
        MaidRescueSessionData.clear(maid);
        DamageSignalService.clearVictim(maid.getUUID());
    }

    static void restoreSourceSitting(EntityMaid maid, MaidRescueSessionData.Data session) {
        if (!session.sittingCaptured()) {
            return;
        }
        try {
            maid.setInSittingPose(session.sourceSitting());
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not restore pre-DISTRESS sitting state for maid {}",
                    maid.getUUID(), error);
        }
    }

    private static TransferOutcome transferMaid(EntityMaid oldMaid, ServerLevel destination, Vec3 destinationPosition) {
        if (oldMaid.level() == destination) {
            SafeTeleportService.teleportTo(oldMaid, destinationPosition);
            return TransferOutcome.success(oldMaid);
        }
        if (!(oldMaid.level() instanceof ServerLevel source)) {
            return TransferOutcome.failed(TransferStatus.TELEPORT_FAILED, true);
        }

        UUID maidId = oldMaid.getUUID();
        EntityMaid candidateMaid = null;
        TransferStage stage = TransferStage.CREATE;
        try {
            EntityType<?> type = oldMaid.getType();
            Entity created = type.create(destination);
            if (!(created instanceof EntityMaid candidate)) {
                if (created != null) {
                    created.discard();
                }
                return TransferOutcome.failed(TransferStatus.ENTITY_CREATE_FAILED, true);
            }
            candidateMaid = candidate;

            stage = TransferStage.RESTORE;
            candidateMaid.restoreFrom(oldMaid);
            candidateMaid.getPersistentData().putBoolean(UNCOMMITTED_TRANSFER_TAG, true);
            MaidRescueSessionData.Data currentSession = MaidRescueSessionData.get(oldMaid);
            MaidRescueSessionData.set(candidateMaid, currentSession);

            stage = TransferStage.POSITION;
            candidateMaid.moveTo(destinationPosition.x, destinationPosition.y, destinationPosition.z,
                    oldMaid.getYRot(), oldMaid.getXRot());
            candidateMaid.stopRiding();
            candidateMaid.getNavigation().stop();
            SafeTeleportService.clearCombatMemories(candidateMaid);
            candidateMaid.setDeltaMovement(Vec3.ZERO);

            stage = TransferStage.ADD_DESTINATION;
            boolean addedToDestination = destination.addWithUUID(candidateMaid);

            stage = TransferStage.VERIFY_DESTINATION;
            boolean candidateValid = addedToDestination && candidateMaid.isAddedToWorld()
                    && candidateMaid.level() == destination
                    && !candidateMaid.isRemoved()
                    && maidId.equals(candidateMaid.getUUID());
            if (!candidateValid) {
                TlmBeyondSpace.LOGGER.warn(
                        "DISTRESS destination rejected maid {} in {} added={} addedToWorld={} removed={}",
                        maidId, destination.dimension().location(), addedToDestination,
                        candidateMaid.isAddedToWorld(), candidateMaid.isRemoved());
                boolean cleaned = discardCandidate(candidateMaid);
                return TransferOutcome.failed(cleaned
                        ? TransferStatus.ENTITY_VERIFY_FAILED : TransferStatus.PARTIAL_TRANSFER, true);
            }

            stage = TransferStage.VERIFY_SESSION;
            MaidRescueSessionData.Data candidateSession = MaidRescueSessionData.get(candidateMaid);
            if (!candidateSession.recoveryTracked()
                    || candidateSession.kind() != RescueSessionKind.DISTRESS) {
                MaidRescueSessionData.set(candidateMaid, currentSession);
                candidateSession = MaidRescueSessionData.get(candidateMaid);
            }
            if (!candidateSession.recoveryTracked()
                    || candidateSession.kind() != RescueSessionKind.DISTRESS) {
                boolean cleaned = discardCandidate(candidateMaid);
                return TransferOutcome.failed(cleaned
                        ? TransferStatus.ENTITY_VERIFY_FAILED : TransferStatus.PARTIAL_TRANSFER, true);
            }

            stage = TransferStage.REMOVE_SOURCE;
            oldMaid.unRide();
            oldMaid.remove(Entity.RemovalReason.CHANGED_DIMENSION);
            if (!oldMaid.isRemoved()) {
                boolean cleaned = discardCandidate(candidateMaid);
                return TransferOutcome.failed(cleaned
                        ? TransferStatus.SOURCE_REMOVE_FAILED : TransferStatus.PARTIAL_TRANSFER, true);
            }

            stage = TransferStage.COMMIT;
            // MaidWorldData is stored globally in the overworld. Source removal temporarily writes
            // the old position; one UUID removal is sufficient while the destination entity is loaded.
            clearMaidWorldInfo(destination, candidateMaid);
            candidateMaid.getPersistentData().remove(UNCOMMITTED_TRANSFER_TAG);
            MaidRosterService.observeImmediately(candidateMaid);
            EntityMaid movedMaid = candidateMaid;
            return TransferOutcome.success(movedMaid);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn(
                    "DISTRESS cross-dimension transfer failed maid={} source={} destination={} stage={}",
                    maidId, source.dimension().location(), destination.dimension().location(), stage, error);
            if (oldMaid.isRemoved() && candidateMaid != null && !candidateMaid.isRemoved()
                    && candidateMaid.level() == destination && maidId.equals(candidateMaid.getUUID())) {
                clearMaidWorldInfo(destination, candidateMaid);
                candidateMaid.getPersistentData().remove(UNCOMMITTED_TRANSFER_TAG);
                MaidRosterService.observeImmediately(candidateMaid);
                return TransferOutcome.success(candidateMaid);
            }
            boolean cleaned = discardCandidate(candidateMaid);
            return TransferOutcome.failed(cleaned ? statusForStage(stage) : TransferStatus.PARTIAL_TRANSFER,
                    !oldMaid.isRemoved());
        }
    }

    private static boolean discardCandidate(EntityMaid candidateMaid) {
        if (candidateMaid == null) {
            return true;
        }
        try {
            if (!candidateMaid.isRemoved()) {
                candidateMaid.remove(Entity.RemovalReason.DISCARDED);
            }
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.error("Could not clean up failed DISTRESS candidate maid {}",
                    candidateMaid.getUUID(), error);
        }
        return candidateMaid.isRemoved();
    }

    static boolean isUncommittedTransfer(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(UNCOMMITTED_TRANSFER_TAG);
    }

    private static void clearMaidWorldInfo(ServerLevel level, EntityMaid maid) {
        try {
            MaidWorldData data = MaidWorldData.get(level);
            if (data != null) {
                data.removeInfo(maid);
            }
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not clear stale MaidWorldData entry after DISTRESS transfer for {}",
                    maid.getUUID(), error);
        }
    }

    private static void rollbackFailedStart(EntityMaid oldMaid) {
        if (oldMaid == null || oldMaid.isRemoved()) {
            return;
        }
        MaidRescueSessionData.Data session = MaidRescueSessionData.get(oldMaid);
        if (!session.active() || session.kind() != RescueSessionKind.DISTRESS) {
            return;
        }
        try {
            SafeTeleportService.returnToOrigin(oldMaid, session);
            SafeTeleportService.clearCombatMemories(oldMaid);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not return DISTRESS maid {} during rollback",
                    oldMaid.getUUID(), error);
        }
        try {
            WeaponCaseSwapService.restoreIfActive(oldMaid);
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not restore weapon during DISTRESS rollback for maid {}",
                    oldMaid.getUUID(), error);
        }
        TaskSwitchService.restore(oldMaid, session, true);
        restoreSourceSitting(oldMaid, session);
        MaidRescueSessionData.clear(oldMaid);
        DamageSignalService.clearVictim(oldMaid.getUUID());
    }

    private static Optional<LivingEntity> findPriorityThreat(ServerPlayer player) {
        Optional<LivingEntity> attacker = DamageSignalService.findAttacker(player);
        if (attacker.isPresent()) {
            return attacker;
        }
        double radius = BeyondSpaceCommonConfig.RESCUE_RADIUS.get();
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        entity -> entity.isAlive() && entity instanceof Enemy && !player.isAlliedTo(entity))
                .stream().findFirst();
    }

    private static void setTargetIfValid(EntityMaid maid, Optional<LivingEntity> target) {
        target.filter(LivingEntity::isAlive)
                .filter(entity -> entity.level() == maid.level() && !maid.isAlliedTo(entity))
                .ifPresent(entity -> {
                    maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, entity);
                    maid.setTarget(entity);
                });
    }

    private static Map<UUID, String> buildNameMap(ServerPlayer player, DistressSignalData data) {
        Map<UUID, String> names = new HashMap<>();
        try {
            for (MaidRosterEntry entry : MaidRosterService.buildRoster(player, data)) {
                names.put(entry.maidId(), entry.name().getString());
            }
        } catch (Exception | LinkageError ignored) {
        }
        return names;
    }

    private static String safeName(EntityMaid maid, String fallback) {
        try {
            String name = maid.getName().getString();
            return name == null || name.isBlank() ? fallback : name;
        } catch (Exception | LinkageError ignored) {
            return fallback;
        }
    }

    private static String shortUnknownName(UUID maidId) {
        String id = maidId.toString();
        return "未载入的女仆（" + id.substring(0, Math.min(8, id.length())) + "）";
    }

    private static void sendSkipDetails(ServerPlayer player, List<SkipEntry> failures) {
        int details = Math.min(MAX_DETAIL_LINES, failures.size());
        for (int index = 0; index < details; index++) {
            SkipEntry failure = failures.get(index);
            player.displayClientMessage(Component.translatable(failure.reason().translationKey,
                    failure.maidName()), false);
        }
        if (failures.size() > details) {
            player.displayClientMessage(Component.translatable("message.tlm_beyond_space.signal_skip.more",
                    failures.size() - details), false);
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    static Vec3 summonPoint(Vec3 center, int index, int total) {
        int boundedIndex = Math.max(0, index);
        int ring = boundedIndex / 8;
        int indexInRing = boundedIndex % 8;
        int remaining = Math.max(1, total - ring * 8);
        int ringSize = Math.min(8, remaining);
        double angle = Math.PI * 2.0D * indexInRing / ringSize;
        double radius = 2.5D + ring * 2.0D;
        return center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
    }

    private static TransferStatus statusForStage(TransferStage stage) {
        return switch (stage) {
            case CREATE -> TransferStatus.ENTITY_CREATE_FAILED;
            case ADD_DESTINATION -> TransferStatus.ENTITY_ADD_FAILED;
            case VERIFY_DESTINATION, VERIFY_SESSION -> TransferStatus.ENTITY_VERIFY_FAILED;
            case REMOVE_SOURCE, COMMIT -> TransferStatus.SOURCE_REMOVE_FAILED;
            default -> TransferStatus.TELEPORT_FAILED;
        };
    }

    private static SkipReason skipReasonForTransfer(TransferStatus status) {
        return switch (status) {
            case ENTITY_CREATE_FAILED -> SkipReason.ENTITY_CREATE_FAILED;
            case ENTITY_VERIFY_FAILED -> SkipReason.ENTITY_VERIFY_FAILED;
            case SOURCE_REMOVE_FAILED -> SkipReason.SOURCE_REMOVE_FAILED;
            case PARTIAL_TRANSFER -> SkipReason.PARTIAL_TRANSFER;
            default -> SkipReason.TELEPORT_FAILED;
        };
    }

    private static SkipReason skipReasonForSafeSearch(SafeTeleportService.FailureReason reason) {
        return switch (reason) {
            case WATER_REQUIRES_DROWN_PROTECTION -> SkipReason.WATER_REQUIRES_DROWN_PROTECTION;
            case LAVA -> SkipReason.LAVA;
            default -> SkipReason.NO_SAFE_POSITION;
        };
    }

    private static SkipReason skipReasonForStart(RescueSessionManager.DistressStartResult result) {
        return switch (result) {
            case MISSING_REQUIRED_ITEM -> SkipReason.MISSING_REQUIRED_WEAPON;
            case SOURCE_TASK_MISSING -> SkipReason.SOURCE_TASK_MISSING;
            case SITTING_RELEASE_FAILED -> SkipReason.SITTING_RELEASE_FAILED;
            case TASK_SWITCH_REJECTED -> SkipReason.TASK_SWITCH_REJECTED;
            case TASK_SWITCH_ERROR -> SkipReason.TASK_SWITCH_ERROR;
            default -> SkipReason.TASK_SWITCH_FAILED;
        };
    }

    private static SkipReason skipReasonForLoad(MaidChunkLoadService.LoadFailure failure) {
        if (failure == null) {
            return SkipReason.NOT_LOADED;
        }
        return switch (failure) {
            case DIMENSION_MISSING -> SkipReason.DIMENSION_MISSING;
            case CHUNK_LOAD_FAILED -> SkipReason.CHUNK_LOAD_FAILED;
            case MAID_NOT_FOUND -> SkipReason.MAID_NOT_FOUND;
            case LOCATION_UNKNOWN -> SkipReason.LOCATION_UNKNOWN;
        };
    }

    private enum SkipReason {
        NOT_LOADED("message.tlm_beyond_space.signal_skip.not_loaded"),
        DIMENSION_MISSING("message.tlm_beyond_space.signal_skip.dimension_missing"),
        CHUNK_LOAD_FAILED("message.tlm_beyond_space.signal_skip.chunk_load_failed"),
        MAID_NOT_FOUND("message.tlm_beyond_space.signal_skip.maid_not_found"),
        LOCATION_UNKNOWN("message.tlm_beyond_space.signal_skip.location_unknown"),
        STORED_IN_SOUL_SPELL("message.tlm_beyond_space.signal_skip.stored_in_soul_spell"),
        CHUNK_LOADING_DISABLED("message.tlm_beyond_space.signal_skip.chunk_loading_disabled"),
        NOT_OWNER("message.tlm_beyond_space.signal_skip.not_owner"),
        ACTIVE_SESSION("message.tlm_beyond_space.signal_skip.active_session"),
        RETURN_PENDING("message.tlm_beyond_space.signal_skip.return_pending"),
        SELF_PRESERVING("message.tlm_beyond_space.signal_skip.self_preserving"),
        INVALID_TASK("message.tlm_beyond_space.signal_skip.invalid_task"),
        TASK_SWITCH_FAILED("message.tlm_beyond_space.signal_skip.task_switch_failed"),
        MISSING_REQUIRED_WEAPON("message.tlm_beyond_space.signal_skip.missing_required_weapon"),
        SOURCE_TASK_MISSING("message.tlm_beyond_space.signal_skip.source_task_missing"),
        SITTING_RELEASE_FAILED("message.tlm_beyond_space.signal_skip.sitting_release_failed"),
        TASK_SWITCH_REJECTED("message.tlm_beyond_space.signal_skip.task_switch_rejected"),
        TASK_SWITCH_ERROR("message.tlm_beyond_space.signal_skip.task_switch_error"),
        NO_SAFE_POSITION("message.tlm_beyond_space.signal_skip.no_safe_position"),
        WATER_REQUIRES_DROWN_PROTECTION(
                "message.tlm_beyond_space.signal_skip.water_requires_drown_protection"),
        LAVA("message.tlm_beyond_space.signal_skip.lava"),
        MOUNTED_CROSS_DIMENSION("message.tlm_beyond_space.signal_skip.mounted_cross_dimension"),
        TELEPORT_FAILED("message.tlm_beyond_space.signal_skip.teleport_failed"),
        ENTITY_CREATE_FAILED("message.tlm_beyond_space.signal_skip.entity_create_failed"),
        ENTITY_VERIFY_FAILED("message.tlm_beyond_space.signal_skip.entity_verify_failed"),
        SOURCE_REMOVE_FAILED("message.tlm_beyond_space.signal_skip.source_remove_failed"),
        PARTIAL_TRANSFER("message.tlm_beyond_space.signal_skip.partial_transfer");

        private final String translationKey;

        SkipReason(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record SkipEntry(String maidName, SkipReason reason) {
    }

    private enum TransferStage {
        CREATE,
        RESTORE,
        POSITION,
        ADD_DESTINATION,
        VERIFY_DESTINATION,
        VERIFY_SESSION,
        REMOVE_SOURCE,
        COMMIT
    }

    private enum TransferStatus {
        SUCCESS,
        ENTITY_CREATE_FAILED,
        ENTITY_ADD_FAILED,
        ENTITY_VERIFY_FAILED,
        SOURCE_REMOVE_FAILED,
        PARTIAL_TRANSFER,
        TELEPORT_FAILED
    }

    private record TransferOutcome(TransferStatus status, EntityMaid maid, boolean rollbackOriginal) {
        static TransferOutcome success(EntityMaid maid) {
            return new TransferOutcome(TransferStatus.SUCCESS, maid, false);
        }

        static TransferOutcome failed(TransferStatus status, boolean rollbackOriginal) {
            return new TransferOutcome(status, null, rollbackOriginal);
        }

        boolean success() {
            return status == TransferStatus.SUCCESS && maid != null;
        }
    }

    private DistressCrossDimSupport() {
    }
}
