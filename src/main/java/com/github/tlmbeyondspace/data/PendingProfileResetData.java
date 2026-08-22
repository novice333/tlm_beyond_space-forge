package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Applies a bulk "all forbidden" reset when an owned maid is loaded again. */
public final class PendingProfileResetData extends SavedData {
    private static final String DATA_NAME = "tlm_beyond_space_pending_profile_reset";
    private static final int MAX_ENTRIES = 4096;
    private final Map<UUID, UUID> ownersByMaid = new LinkedHashMap<>();

    public static PendingProfileResetData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(PendingProfileResetData::load, PendingProfileResetData::new, DATA_NAME);
    }

    public void add(UUID maidId, UUID ownerId) {
        if (ownersByMaid.size() >= MAX_ENTRIES && !ownersByMaid.containsKey(maidId)) {
            return;
        }
        if (!ownerId.equals(ownersByMaid.put(maidId, ownerId))) {
            setDirty();
        }
    }

    public boolean process(EntityMaid maid) {
        UUID ownerId = ownersByMaid.remove(maid.getUUID());
        if (ownerId == null) {
            return false;
        }
        if (ownerId.equals(maid.getOwnerUUID())) {
            MaidRescueProfileData.updateProfile(maid, ownerId, new TaskModeProfile());
        }
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        ownersByMaid.forEach((maidId, ownerId) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Maid", maidId);
            entry.putUUID("Owner", ownerId);
            entries.add(entry);
        });
        tag.put("Requests", entries);
        return tag;
    }

    public static PendingProfileResetData load(CompoundTag tag) {
        PendingProfileResetData data = new PendingProfileResetData();
        ListTag entries = tag.getList("Requests", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size() && data.ownersByMaid.size() < MAX_ENTRIES; index++) {
            CompoundTag entry = entries.getCompound(index);
            if (entry.hasUUID("Maid") && entry.hasUUID("Owner")) {
                data.ownersByMaid.put(entry.getUUID("Maid"), entry.getUUID("Owner"));
            }
        }
        return data;
    }
}
