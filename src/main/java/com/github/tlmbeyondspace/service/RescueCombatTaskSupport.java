package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.data.MaidCombatPreferenceData;
import com.github.tlmbeyondspace.data.TaskModeProfile;
import net.minecraft.resources.ResourceLocation;

public final class RescueCombatTaskSupport {
    public static IMaidTask resolveForMaid(EntityMaid maid) {
        return resolveAttackTask(maid);
    }

    public static IAttackTask resolveAttackTask(EntityMaid maid) {
        ResourceLocation configured = MaidCombatPreferenceData.getPreferredTaskId(maid);
        IAttackTask resolved = findAttackTask(configured);
        if (resolved != null) {
            return resolved;
        }
        if (configured != null && !TaskModeProfile.DEFAULT_COMBAT_TASK.equals(configured)) {
            TlmBeyondSpace.LOGGER.warn("Configured rescue combat task {} is unavailable; falling back to {}",
                    configured, TaskModeProfile.DEFAULT_COMBAT_TASK);
        }
        return findAttackTask(TaskModeProfile.DEFAULT_COMBAT_TASK);
    }

    private static IAttackTask findAttackTask(ResourceLocation taskId) {
        if (taskId == null) {
            return null;
        }
        return TaskManager.findTask(taskId)
                .filter(RescueTaskClassifier::isCombatTask)
                .map(IAttackTask.class::cast)
                .orElse(null);
    }

    private RescueCombatTaskSupport() {
    }
}
