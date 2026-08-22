package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class MaidCombatPreferenceData {
    public static final ResourceLocation ID = new ResourceLocation(TlmBeyondSpace.MOD_ID, "combat_preference");
    public static final TaskDataKey<Data> KEY = new TaskDataKey<>() {
        @Override
        public ResourceLocation getKey() {
            return ID;
        }

        @Override
        public CompoundTag writeSaveData(Data value) {
            return value.save();
        }

        @Override
        public Data readSaveData(CompoundTag tag) {
            return Data.load(tag);
        }
    };

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, Data.unset());
    }

    public static ResourceLocation getPreferredTaskId(EntityMaid maid) {
        Data preference = get(maid);
        if (preference.configured()) {
            return preference.combatTask();
        }
        MaidRescueProfileData.Data legacy = MaidRescueProfileData.get(maid);
        if (legacy.bound() && legacy.profile() != null) {
            return legacy.profile().getCombatTask();
        }
        return TaskModeProfile.DEFAULT_COMBAT_TASK;
    }

    public static void set(EntityMaid maid, ResourceLocation taskId) {
        maid.setAndSyncData(KEY, new Data(true, sanitize(taskId)));
    }

    public static void migrateIfUnset(EntityMaid maid, ResourceLocation legacyTaskId) {
        if (!get(maid).configured() && legacyTaskId != null
                && !TaskModeProfile.DEFAULT_COMBAT_TASK.equals(legacyTaskId)) {
            set(maid, legacyTaskId);
        }
    }

    private static ResourceLocation sanitize(ResourceLocation taskId) {
        return taskId == null ? TaskModeProfile.DEFAULT_COMBAT_TASK : taskId;
    }

    public record Data(boolean configured, ResourceLocation combatTask) {
        public Data {
            combatTask = sanitize(combatTask);
        }

        public static Data unset() {
            return new Data(false, TaskModeProfile.DEFAULT_COMBAT_TASK);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Configured", configured);
            tag.putString("CombatTask", combatTask.toString());
            return tag;
        }

        public static Data load(CompoundTag tag) {
            ResourceLocation taskId = tag.contains("CombatTask", Tag.TAG_STRING)
                    ? ResourceLocation.tryParse(tag.getString("CombatTask")) : null;
            return new Data(tag.getBoolean("Configured"), sanitize(taskId));
        }
    }

    private MaidCombatPreferenceData() {
    }
}
