package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.compat.MaidUsefulTaskCompat;
import net.minecraft.resources.ResourceLocation;

public final class TaskSwitchService {
    public static boolean prepareAndSwitch(EntityMaid maid, IMaidTask targetTask) {
        try {
            MaidUsefulTaskCompat.resetTransientWork(maid);
            FunctionCallSwitchResult result = CombatTaskCompatibility.prepareSwitch(maid, targetTask);
            if (result == FunctionCallSwitchResult.MISSING_REQUIRED_ITEM) {
                return false;
            }
            maid.setHomeModeEnable(false);
            maid.setSchedule(MaidSchedule.ALL);
            maid.setOrderedToSit(false);
            maid.setTask(targetTask);
            return true;
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Rescue task switch failed for maid {} to task {}",
                    maid.getUUID(), safeTaskId(targetTask), error);
            return false;
        }
    }

    public static boolean restore(EntityMaid maid, MaidRescueSessionData.Data session, boolean restoreTask) {
        boolean restored = true;
        try {
            SafeTeleportService.clearCombatMemories(maid);
        } catch (Exception | LinkageError error) {
            restored = false;
            TlmBeyondSpace.LOGGER.warn("Could not clear rescue memories for maid {}", maid.getUUID(), error);
        }
        if (restoreTask) {
            IMaidTask source;
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
            try {
                maid.setTask(source);
            } catch (Exception | LinkageError error) {
                restored = false;
                TlmBeyondSpace.LOGGER.warn("Could not restore source task {} for maid {}",
                        safeTaskId(source), maid.getUUID(), error);
            }
        }
        try {
            maid.setHomeModeEnable(session.sourceHomeMode());
        } catch (Exception | LinkageError error) {
            restored = false;
            TlmBeyondSpace.LOGGER.warn("Could not restore HomeMode for maid {}", maid.getUUID(), error);
        }
        try {
            maid.setSchedule(session.sourceSchedule());
        } catch (Exception | LinkageError error) {
            restored = false;
            TlmBeyondSpace.LOGGER.warn("Could not restore schedule for maid {}", maid.getUUID(), error);
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
