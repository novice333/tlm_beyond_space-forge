package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

public final class MaidRescueProfileData {
    public static final ResourceLocation ID = new ResourceLocation(TlmBeyondSpace.MOD_ID, "rescue_profile");
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
        return maid.getOrCreateData(KEY, Data.empty());
    }

    public static void bind(EntityMaid maid, UUID ownerId, TaskModeProfile profile, UUID sourceBookId) {
        maid.setAndSyncData(KEY, new Data(true, ownerId, sourceBookId, profile.copy()));
    }

    public static void updateProfile(EntityMaid maid, UUID ownerId, TaskModeProfile profile) {
        maid.setAndSyncData(KEY, get(maid).withProfile(ownerId, profile));
    }

    public static void clear(EntityMaid maid) {
        maid.setAndSyncData(KEY, Data.empty());
    }

    public record Data(boolean bound, UUID binderId, UUID sourceBookId, TaskModeProfile profile) {
        public static Data empty() {
            return new Data(false, null, null, new TaskModeProfile());
        }

        public Optional<UUID> binderIdOptional() {
            return Optional.ofNullable(binderId);
        }

        public Optional<UUID> sourceBookIdOptional() {
            return Optional.ofNullable(sourceBookId);
        }

        public Data withProfile(UUID ownerId, TaskModeProfile updatedProfile) {
            UUID retainedBookId = bound && ownerId != null && ownerId.equals(binderId)
                    ? sourceBookId : null;
            return new Data(true, ownerId, retainedBookId, updatedProfile.copy());
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Bound", bound);
            if (binderId != null) {
                tag.putUUID("Binder", binderId);
            }
            if (sourceBookId != null) {
                tag.putUUID("SourceBook", sourceBookId);
            }
            tag.put("Profile", profile.save());
            return tag;
        }

        public static Data load(CompoundTag tag) {
            UUID binder = tag.hasUUID("Binder") ? tag.getUUID("Binder") : null;
            UUID sourceBook = tag.hasUUID("SourceBook") ? tag.getUUID("SourceBook") : null;
            return new Data(tag.getBoolean("Bound"), binder, sourceBook,
                    TaskModeProfile.load(tag.getCompound("Profile")));
        }
    }

    private MaidRescueProfileData() {
    }
}
