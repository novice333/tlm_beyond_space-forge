package com.github.tlmbeyondspace.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent identities of maids converted into a TLM maid-storage item such as a Soul Spell. */
public final class StoredMaidData extends SavedData {
    private static final String DATA_NAME = "tlm_beyond_space_stored_maids";
    private static final int MAX_ENTRIES = 4096;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static StoredMaidData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(StoredMaidData::load, StoredMaidData::new, DATA_NAME);
    }

    public void markStored(UUID maidId, UUID ownerId, String name) {
        if (maidId == null || ownerId == null || (!entries.containsKey(maidId) && entries.size() >= MAX_ENTRIES)) {
            return;
        }
        Entry entry = new Entry(maidId, ownerId, name == null ? "" : name, System.currentTimeMillis());
        if (!entry.equals(entries.put(maidId, entry))) {
            setDirty();
        }
    }

    public void clearStored(UUID maidId) {
        if (maidId != null && entries.remove(maidId) != null) {
            setDirty();
        }
    }

    public boolean isStored(UUID maidId, UUID ownerId) {
        Entry entry = entries.get(maidId);
        return entry != null && ownerId.equals(entry.ownerId());
    }

    public Optional<Entry> get(UUID maidId) {
        return Optional.ofNullable(entries.get(maidId));
    }

    public List<Entry> ownedBy(UUID ownerId) {
        return entries.values().stream().filter(entry -> ownerId.equals(entry.ownerId())).toList();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.values().forEach(value -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Maid", value.maidId());
            entry.putUUID("Owner", value.ownerId());
            entry.putString("Name", value.name());
            entry.putLong("StoredAt", value.storedAt());
            list.add(entry);
        });
        tag.put("Maids", list);
        return tag;
    }

    public static StoredMaidData load(CompoundTag tag) {
        StoredMaidData data = new StoredMaidData();
        ListTag list = tag.getList("Maids", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && data.entries.size() < MAX_ENTRIES; index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("Maid") || !entry.hasUUID("Owner")) {
                continue;
            }
            UUID maidId = entry.getUUID("Maid");
            data.entries.put(maidId, new Entry(maidId, entry.getUUID("Owner"), entry.getString("Name"),
                    Math.max(0L, entry.getLong("StoredAt"))));
        }
        return data;
    }

    public record Entry(UUID maidId, UUID ownerId, String name, long storedAt) {
    }
}
