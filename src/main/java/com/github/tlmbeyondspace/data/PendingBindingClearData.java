package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.service.RescueSessionManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PendingBindingClearData extends SavedData {
    private static final String DATA_NAME = "tlm_beyond_space_pending_binding_clear";
    private static final int MAX_ENTRIES = 4096;
    private final Map<UUID, Set<Request>> requests = new LinkedHashMap<>();

    public static PendingBindingClearData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(PendingBindingClearData::load, PendingBindingClearData::new, DATA_NAME);
    }

    public void add(UUID maidId, UUID ownerId, UUID sourceBookId, boolean legacy) {
        if (requests.size() >= MAX_ENTRIES && !requests.containsKey(maidId)) {
            return;
        }
        Set<Request> maidRequests = requests.computeIfAbsent(maidId, ignored -> new LinkedHashSet<>());
        if (maidRequests.add(new Request(ownerId, sourceBookId, legacy))) {
            setDirty();
        }
    }

    public boolean process(EntityMaid maid) {
        Set<Request> maidRequests = requests.get(maid.getUUID());
        if (maidRequests == null) {
            return false;
        }
        MaidRescueProfileData.Data binding = MaidRescueProfileData.get(maid);
        boolean matches = maidRequests.stream().anyMatch(request -> {
            boolean ownerMatches = request.ownerId().equals(maid.getOwnerUUID());
            boolean binderMatches = binding.binderIdOptional().filter(request.ownerId()::equals).isPresent();
            boolean bookMatches = request.legacy()
                    ? binding.sourceBookIdOptional().isEmpty()
                    : binding.sourceBookIdOptional().filter(id -> id.equals(request.sourceBookId())).isPresent();
            return ownerMatches && binding.bound() && binderMatches && bookMatches;
        });
        if (matches) {
            RescueSessionManager.INSTANCE.clearBindingSafely(maid);
        }
        requests.remove(maid.getUUID());
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        requests.forEach((maidId, maidRequests) -> maidRequests.forEach(request -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("Maid", maidId);
                    entry.putUUID("Owner", request.ownerId());
                    if (request.sourceBookId() != null) {
                        entry.putUUID("Book", request.sourceBookId());
                    }
                    entry.putBoolean("Legacy", request.legacy());
                    list.add(entry);
                }));
        tag.put("Requests", list);
        return tag;
    }

    public static PendingBindingClearData load(CompoundTag tag) {
        PendingBindingClearData data = new PendingBindingClearData();
        ListTag list = tag.getList("Requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && data.requests.size() < MAX_ENTRIES; i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Maid") || !entry.hasUUID("Owner")) {
                continue;
            }
            UUID bookId = entry.hasUUID("Book") ? entry.getUUID("Book") : null;
            data.requests.computeIfAbsent(entry.getUUID("Maid"), ignored -> new LinkedHashSet<>())
                    .add(new Request(entry.getUUID("Owner"), bookId, entry.getBoolean("Legacy")));
        }
        return data;
    }

    private record Request(UUID ownerId, UUID sourceBookId, boolean legacy) {
    }
}
