package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import net.minecraft.core.BlockPos;
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

/** One authoritative, bounded last-contact record per maid UUID. */
public final class LastKnownMaidData extends SavedData {
    private static final String DATA_NAME = "tlm_beyond_space_last_known_maids";
    private static final int MAX_ENTRIES = 4096;
    private static final long PERIODIC_UPDATE_MILLIS = 5_000L;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static LastKnownMaidData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(LastKnownMaidData::load, LastKnownMaidData::new, DATA_NAME);
    }

    public void observe(EntityMaid maid, boolean force) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null || maid.level().isClientSide) {
            return;
        }
        long now = System.currentTimeMillis();
        String name = maid.getDisplayName().getString();
        String dimension = maid.level().dimension().location().toString();
        MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
        boolean originKnown = session.recoveryTracked() && session.originDimension() != null;
        String originDimension = originKnown ? session.originDimension().toString() : "";
        BlockPos originPosition = originKnown ? BlockPos.containing(session.origin()) : BlockPos.ZERO;
        Entry previous = entries.get(maid.getUUID());
        boolean identityChanged = previous == null || !ownerId.equals(previous.ownerId())
                || !name.equals(previous.name()) || !dimension.equals(previous.dimension())
                || originKnown != previous.rescueOriginKnown()
                || !originDimension.equals(previous.rescueOriginDimension())
                || !originPosition.equals(previous.rescueOriginPosition());
        if (!force && !identityChanged && now - previous.lastSeen() < PERIODIC_UPDATE_MILLIS) {
            return;
        }
        put(new Entry(maid.getUUID(), ownerId, name, dimension, maid.blockPosition(), now,
                originKnown, originDimension, originPosition));
    }

    public void importInfo(MaidInfo info) {
        if (info == null || info.getOwnerId() == null || info.getEntityId() == null) {
            return;
        }
        Entry previous = entries.get(info.getEntityId());
        if (previous != null && previous.lastSeen() > info.getTimestamp()) {
            return;
        }
        String name = info.getName() == null ? "" : info.getName().getString();
        boolean originKnown = previous != null && previous.rescueOriginKnown();
        put(new Entry(info.getEntityId(), info.getOwnerId(), name, info.getDimension(),
                info.getChunkPos(), info.getTimestamp(), originKnown,
                originKnown ? previous.rescueOriginDimension() : "",
                originKnown ? previous.rescueOriginPosition() : BlockPos.ZERO));
    }

    public Optional<Entry> get(UUID maidId) {
        return Optional.ofNullable(entries.get(maidId));
    }

    public List<Entry> ownedBy(UUID ownerId) {
        return entries.values().stream().filter(entry -> ownerId.equals(entry.ownerId())).toList();
    }

    private void put(Entry entry) {
        if (entries.size() >= MAX_ENTRIES && !entries.containsKey(entry.maidId())) {
            return;
        }
        Entry previous = entries.put(entry.maidId(), entry);
        if (!entry.equals(previous)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.values().forEach(value -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Maid", value.maidId());
            entry.putUUID("Owner", value.ownerId());
            entry.putString("Name", value.name());
            entry.putString("Dimension", value.dimension());
            entry.putInt("X", value.position().getX());
            entry.putInt("Y", value.position().getY());
            entry.putInt("Z", value.position().getZ());
            entry.putLong("LastSeen", value.lastSeen());
            entry.putBoolean("RescueOriginKnown", value.rescueOriginKnown());
            if (value.rescueOriginKnown()) {
                entry.putString("RescueOriginDimension", value.rescueOriginDimension());
                entry.putInt("RescueOriginX", value.rescueOriginPosition().getX());
                entry.putInt("RescueOriginY", value.rescueOriginPosition().getY());
                entry.putInt("RescueOriginZ", value.rescueOriginPosition().getZ());
            }
            list.add(entry);
        });
        tag.put("Maids", list);
        return tag;
    }

    public static LastKnownMaidData load(CompoundTag tag) {
        LastKnownMaidData data = new LastKnownMaidData();
        ListTag list = tag.getList("Maids", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && data.entries.size() < MAX_ENTRIES; index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("Maid") || !entry.hasUUID("Owner")) {
                continue;
            }
            UUID maidId = entry.getUUID("Maid");
            boolean originKnown = entry.getBoolean("RescueOriginKnown");
            data.entries.put(maidId, new Entry(maidId, entry.getUUID("Owner"), entry.getString("Name"),
                    entry.getString("Dimension"), new BlockPos(entry.getInt("X"), entry.getInt("Y"),
                    entry.getInt("Z")), Math.max(0L, entry.getLong("LastSeen")), originKnown,
                    originKnown ? entry.getString("RescueOriginDimension") : "",
                    originKnown ? new BlockPos(entry.getInt("RescueOriginX"), entry.getInt("RescueOriginY"),
                            entry.getInt("RescueOriginZ")) : BlockPos.ZERO));
        }
        return data;
    }

    public record Entry(UUID maidId, UUID ownerId, String name, String dimension,
                        BlockPos position, long lastSeen, boolean rescueOriginKnown,
                        String rescueOriginDimension, BlockPos rescueOriginPosition) {
    }
}
