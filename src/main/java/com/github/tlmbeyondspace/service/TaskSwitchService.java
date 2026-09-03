package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.compat.MaidUsefulTaskCompat;
import com.github.tlmbeyondspace.compat.PromaidCompat;
import net.minecraft.resources.ResourceLocation;

public final class TaskSwitchService {
    public static boolean prepareAndSwitch(EntityMaid maid, IMaidTask targetTask) {
        return prepareAndSwitchDetailed(maid, targetTask) == SwitchResult.SUCCESS;
    }

    public static SwitchResult prepareAndSwitchDetailed(EntityMaid maid, IMaidTask targetTask) {
        try {
            MaidUsefulTaskCompat.resetTransientWork(maid);
            FunctionCallSwitchResult result = CombatTaskCompatibility.prepareSwitch(maid, targetTask);
            if (result == FunctionCallSwitchResult.MISSING_REQUIRED_ITEM) {
                return SwitchResult.MISSING_REQUIRED_ITEM;
            }
            maid.setHomeModeEnable(false);
            maid.setOrderedToSit(false);
            PromaidCompat.runTaskScheduleChange(maid, targetTask.getUid(), () -> {
                maid.setSchedule(MaidSchedule.ALL);
                maid.setTask(targetTask);
            });
            if (maid.getSchedule() != MaidSchedule.ALL || maid.getTask() == null
                    || !targetTask.getUid().equals(maid.getTask().getUid())) {
                TlmBeyondSpace.LOGGER.warn("Rescue task switch for maid {} was rejected or overwritten",
                        maid.getUUID());
                return SwitchResult.REJECTED;
            }
            return SwitchResult.SUCCESS;
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Rescue task switch failed for maid {} to task {}",
                    maid.getUUID(), safeTaskId(targetTask), error);
            return SwitchResult.ERROR;
        }
    }

    public enum SwitchResult {
        SUCCESS,
        MISSING_REQUIRED_ITEM,
        REJECTED,
        ERROR
    }

    public static boolean restore(EntityMaid maid, MaidRescueSessionData.Data session, boolean restoreTask) {
        boolean restored = true;
        try {
            SafeTeleportService.clearCombatMemories(maid);
        } catch (Exception | LinkageError error) {
            restored = false;
            TlmBeyondSpace.LOGGER.warn("Could not clear rescue memories for maid {}", maid.getUUID(), error);
        }
        IMaidTask source = null;
        if (restoreTask) {
            try {
                source = findOrIdle(session.sourceTask());
            } catch (Exception | LinkageError error) {
                restored = false;
                source = TaskManager.getIdleTask();
                TlmBeyondSpace.LOGGER.warn("Could not resolve source task for maid {}; using idle",
                        maid.getUUID(), error);
            }
            try {
                MaidUsefulTaskCompat.resetTransientWork(maid);
                CombatTaskCompatibility.prepareSwitch(maid, source);
            } catch (Exception | LinkageError error) {
                restored = false;
                TlmBeyondSpace.LOGGER.warn("Source task callback failed while restoring maid {} to {}",
                        maid.getUUID(), safeTaskId(source), error);
            }
        }
        try {
            maid.setHomeModeEnable(session.sourceHomeMode());
        } catch (Exception | LinkageError error) {
            restored = false;
            TlmBeyondSpace.LOGGER.warn("Could not restore HomeMode for maid {}", maid.getUUID(), error);
        }
        try {
            IMaidTask sourceToRestore = source;
            ResourceLocation targetId = sourceToRestore == null ? session.sourceTask() : sourceToRestore.getUid();
            PromaidCompat.runTaskScheduleChange(maid, targetId, () -> {
                if (sourceToRestore != null) {
                    maid.setTask(sourceToRestore);
                }
                maid.setSchedule(session.sourceSchedule());
            });
            boolean taskMatches = sourceToRestore == null || (maid.getTask() != null
                    && sourceToRestore.getUid().equals(maid.getTask().getUid()));
            if (!taskMatches || maid.getSchedule() != session.sourceSchedule()) {
                restored = false;
                TlmBeyondSpace.LOGGER.warn("Source task or schedule restore for maid {} was rejected or overwritten",
                        maid.getUUID());
            }
        } catch (Exception | LinkageError error) {
            restored = false;
            TlmBeyondSpace.LOGGER.warn("Could not restore source task or schedule for maid {}",
                    maid.getUUID(), error);
        }
        return restored;
    }

    private static IMaidTask findOrIdle(ResourceLocation taskId) {
        return taskId == null ? TaskManager.getIdleTask() : TaskManager.findTask(taskId).orElseGet(TaskManager::getIdleTask);
    }

    private static String safeTaskId(IMaidTask task) {
        try {
            return task == null || task.getUid() == null ? "<unknown>" : task.getUid().toString();
        } catch (Exception | LinkageError ignored) {
            return "<unavailable>";
        }
    }

    private TaskSwitchService() {
    }
}
