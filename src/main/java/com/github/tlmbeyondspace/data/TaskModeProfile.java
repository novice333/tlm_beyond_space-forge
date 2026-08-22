package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tlmbeyondspace.service.RescueTaskClassifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskModeProfile {
    public static final String ITEM_TAG = "BeyondSpaceTaskProfile";
    private static final int MAX_ENTRIES = 512;
    public static final ResourceLocation DEFAULT_COMBAT_TASK = new ResourceLocation("touhou_little_maid", "attack");
    private final Map<ResourceLocation, RescueMode> modes = new LinkedHashMap<>();
    private ResourceLocation combatTask = DEFAULT_COMBAT_TASK;

    public RescueMode get(ResourceLocation taskId) {
        return modes.getOrDefault(taskId, RescueMode.FORBIDDEN);
    }

    public void set(ResourceLocation taskId, RescueMode mode) {
        if (mode == RescueMode.FORBIDDEN) {
            modes.remove(taskId);
        } else {
            modes.put(taskId, mode);
        }
    }

    public Map<ResourceLocation, RescueMode> view() {
        return Collections.unmodifiableMap(modes);
    }

    public ResourceLocation getCombatTask() {
        return combatTask == null ? DEFAULT_COMBAT_TASK : combatTask;
    }

    /** Compatibility bridge for old per-source-task callers. */
    public ResourceLocation getCombatTask(ResourceLocation ignoredSourceTask) {
        return getCombatTask();
    }

    public void setCombatTask(ResourceLocation combatTask) {
        this.combatTask = combatTask == null ? DEFAULT_COMBAT_TASK : combatTask;
    }

    /** Compatibility bridge for old per-source-task callers. */
    public void setCombatTask(ResourceLocation ignoredSourceTask, ResourceLocation combatTask) {
        setCombatTask(combatTask);
    }

    public TaskModeProfile copy() {
        TaskModeProfile copy = new TaskModeProfile();
        copy.modes.putAll(modes);
        copy.combatTask = getCombatTask();
        return copy;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putString("CombatTask", getCombatTask().toString());
        ListTag entries = new ListTag();
        int saved = 0;
        for (Map.Entry<ResourceLocation, RescueMode> profileEntry : modes.entrySet()) {
            if (saved++ >= MAX_ENTRIES) {
                break;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("Task", profileEntry.getKey().toString());
            entry.putString("Mode", profileEntry.getValue().name());
            entries.add(entry);
        }
        root.put("Entries", entries);
        return root;
    }

    public static TaskModeProfile load(CompoundTag root) {
        TaskModeProfile profile = new TaskModeProfile();
        boolean hasGlobalCombatTask = root.contains("CombatTask", Tag.TAG_STRING);
        if (hasGlobalCombatTask) {
            ResourceLocation parsed = ResourceLocation.tryParse(root.getString("CombatTask"));
            if (parsed != null) {
                profile.combatTask = parsed;
            }
        }
        ListTag entries = root.getList("Entries", Tag.TAG_COMPOUND);
        ResourceLocation legacyCombatTask = null;
        for (int i = 0; i < entries.size() && i < MAX_ENTRIES; i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation taskId = ResourceLocation.tryParse(entry.getString("Task"));
            RescueMode mode;
            try {
                mode = RescueMode.valueOf(entry.getString("Mode"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (taskId == null || mode == RescueMode.FORBIDDEN) {
                continue;
            }
            if (TaskManager.findTask(taskId).filter(RescueTaskClassifier::isSourceTask).isPresent()) {
                profile.modes.put(taskId, mode);
                if (!hasGlobalCombatTask && legacyCombatTask == null
                        && entry.contains("CombatTask", Tag.TAG_STRING)) {
                    legacyCombatTask = ResourceLocation.tryParse(entry.getString("CombatTask"));
                }
            }
        }
        if (legacyCombatTask != null) {
            profile.combatTask = legacyCombatTask;
        }
        return profile;
    }

    public static TaskModeProfile fromItem(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(ITEM_TAG, Tag.TAG_COMPOUND)
                ? load(root.getCompound(ITEM_TAG))
                : new TaskModeProfile();
    }

    public static void writeItem(ItemStack stack, TaskModeProfile profile) {
        stack.getOrCreateTag().put(ITEM_TAG, profile.save());
    }
}
