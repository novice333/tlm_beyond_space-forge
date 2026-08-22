package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Separates pure combat tasks from hybrid work tasks that happen to implement IAttackTask. */
public final class RescueTaskClassifier {
    public static final ResourceLocation VANILLA_FEED_ANIMAL =
            new ResourceLocation("touhou_little_maid", "feed_animal");
    public static final ResourceLocation MAID_SOUL_KITCHEN_FEED_ANIMAL =
            new ResourceLocation("maidsoulkitchen", "feed_animal_t");
    private static final Set<ResourceLocation> HYBRID_SOURCE_TASKS = Set.of(
            VANILLA_FEED_ANIMAL,
            MAID_SOUL_KITCHEN_FEED_ANIMAL
    );

    public static boolean isCombatTask(IMaidTask task) {
        if (!(task instanceof IAttackTask)) {
            return false;
        }
        try {
            return !isHybridSourceTaskId(task.getUid());
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isSourceTask(IMaidTask task) {
        return task != null && !isCombatTask(task);
    }

    static boolean isHybridSourceTaskId(ResourceLocation taskId) {
        return taskId != null && HYBRID_SOURCE_TASKS.contains(taskId);
    }

    private RescueTaskClassifier() {
    }
}
