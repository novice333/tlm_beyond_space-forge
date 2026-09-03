package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.compat.MaidReformCompat;
import com.github.tlmbeyondspace.data.DistressSignalData;
import com.github.tlmbeyondspace.data.LastKnownMaidData;
import com.github.tlmbeyondspace.data.MaidRosterEntry;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.data.StoredMaidData;
import com.github.tlmbeyondspace.network.BeyondSpaceNetwork;
import com.github.tlmbeyondspace.network.packet.OpenDistressRosterS2CPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidRosterService {
    private static final Map<UUID, ObservedMaid> OBSERVED_MAIDS = new ConcurrentHashMap<>();

    public static void observe(EntityMaid maid) {
        if (maid.level().isClientSide || maid.getOwnerUUID() == null
                || DistressCrossDimSupport.isUncommittedTransfer(maid)) {
            return;
        }
        ObservedMaid current = new ObservedMaid(maid.getOwnerUUID(), maid.getDisplayName(),
                maid.level().dimension().location().toString());
        OBSERVED_MAIDS.compute(maid.getUUID(), (id, previous) -> current.equals(previous) ? previous : current);
        if (maid.level() instanceof ServerLevel level) {
            LastKnownMaidData.get(level).observe(maid, false);
        }
    }

    public static void observeImmediately(EntityMaid maid) {
        if (DistressCrossDimSupport.isUncommittedTransfer(maid)) {
            return;
        }
        observe(maid);
        if (maid.level() instanceof ServerLevel level) {
            LastKnownMaidData.get(level).observe(maid, true);
        }
    }

    public static void openRoster(ServerPlayer player, InteractionHand hand, ItemStack signal) {
        DistressSignalData data = DistressSignalData.fromItem(signal);
        if (!data.canUse(player.getUUID())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tlm_beyond_space.signal_wrong_owner"), true);
            return;
        }
        List<MaidRosterEntry> entries = buildRoster(player, data);
        BeyondSpaceNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenDistressRosterS2CPacket(hand, entries, data.recallMode(),
                        MaidReformCompat.isLoaded(), data.knockdownRescue()));
    }

    public static List<MaidRosterEntry> buildRoster(ServerPlayer player, DistressSignalData data) {
        SoulSpellMaidService.refreshKnownItems(player);
        Map<UUID, MaidRosterEntry> entries = new LinkedHashMap<>();
        OBSERVED_MAIDS.forEach((maidId, observed) -> {
            if (!observed.ownerId().equals(player.getUUID()) || entries.size() >= DistressSignalData.MAX_SELECTIONS) {
                return;
            }
            Optional<EntityMaid> loaded = findLoadedMaid(player.server, maidId)
                    .filter(maid -> maid.isOwnedBy(player));
            if (loaded.isPresent()) {
                EntityMaid maid = loaded.get();
                MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
                DistressSignalData.Selection selection = data.get(maidId);
                entries.put(maidId, new MaidRosterEntry(maidId, maid.getDisplayName(),
                        maid.level().dimension().location().toString(), true, maid.level() == player.level(),
                        selection.enabled(), selection.combatTask(), true, maid.blockPosition(),
                        System.currentTimeMillis(), session.recoveryTracked(),
                        session.originDimension() == null ? "" : session.originDimension().toString(),
                        session.recoveryTracked() ? net.minecraft.core.BlockPos.containing(session.origin())
                                : net.minecraft.core.BlockPos.ZERO, selection.loadUnloaded(), false));
            }
        });

        for (MaidInfo info : unloadedInfos(player)) {
            if (entries.size() >= DistressSignalData.MAX_SELECTIONS) break;
            LastKnownMaidData.get(player.serverLevel()).importInfo(info);
            Optional<EntityMaid> loaded = findLoadedMaid(player.server, info.getEntityId())
                    .filter(maid -> maid.isOwnedBy(player));
            DistressSignalData.Selection selection = data.get(info.getEntityId());
            LastKnownMaidData.Entry known = LastKnownMaidData.get(player.serverLevel())
                    .get(info.getEntityId()).orElse(null);
            boolean stored = loaded.isEmpty() && SoulSpellMaidService.isStored(player, info.getEntityId());
            entries.putIfAbsent(info.getEntityId(), new MaidRosterEntry(info.getEntityId(), info.getName(),
                    info.getDimension(), loaded.isPresent(), loaded.filter(maid -> maid.level() == player.level()).isPresent(),
                    selection.enabled(), selection.combatTask(), true, info.getChunkPos(), info.getTimestamp(),
                    known != null && known.rescueOriginKnown(),
                    known == null ? "" : known.rescueOriginDimension(),
                    known == null ? net.minecraft.core.BlockPos.ZERO : known.rescueOriginPosition(),
                    selection.loadUnloaded(), stored));
        }

        for (LastKnownMaidData.Entry known : LastKnownMaidData.get(player.serverLevel())
                .ownedBy(player.getUUID())) {
            if (entries.size() >= DistressSignalData.MAX_SELECTIONS) break;
            Optional<EntityMaid> loaded = findLoadedMaid(player.server, known.maidId())
                    .filter(maid -> maid.isOwnedBy(player));
            DistressSignalData.Selection selection = data.get(known.maidId());
            Component name = componentName(known.name(), known.maidId());
            entries.putIfAbsent(known.maidId(), new MaidRosterEntry(known.maidId(), name,
                    known.dimension(), loaded.isPresent(),
                    loaded.filter(maid -> maid.level() == player.level()).isPresent(), selection.enabled(),
                    selection.combatTask(), true, known.position(), known.lastSeen(),
                    known.rescueOriginKnown(), known.rescueOriginDimension(),
                    known.rescueOriginPosition(), selection.loadUnloaded(),
                    loaded.isEmpty() && SoulSpellMaidService.isStored(player, known.maidId())));
        }

        for (StoredMaidData.Entry stored : StoredMaidData.get(player.serverLevel()).ownedBy(player.getUUID())) {
            if (entries.size() >= DistressSignalData.MAX_SELECTIONS) break;
            if (findLoadedMaid(player.server, stored.maidId()).isPresent()) {
                continue;
            }
            DistressSignalData.Selection selection = data.get(stored.maidId());
            LastKnownMaidData.Entry known = LastKnownMaidData.get(player.serverLevel())
                    .get(stored.maidId()).orElse(null);
            entries.putIfAbsent(stored.maidId(), new MaidRosterEntry(stored.maidId(),
                    componentName(stored.name(), stored.maidId()), known == null ? "?" : known.dimension(),
                    false, false, selection.enabled(), selection.combatTask(), known != null,
                    known == null ? net.minecraft.core.BlockPos.ZERO : known.position(),
                    known == null ? stored.storedAt() : known.lastSeen(),
                    known != null && known.rescueOriginKnown(),
                    known == null ? "" : known.rescueOriginDimension(),
                    known == null ? net.minecraft.core.BlockPos.ZERO : known.rescueOriginPosition(),
                    selection.loadUnloaded(), true));
        }

        for (Map.Entry<UUID, DistressSignalData.Selection> saved : data.selections().entrySet()) {
            if (entries.size() >= DistressSignalData.MAX_SELECTIONS) break;
            UUID maidId = saved.getKey();
            Optional<EntityMaid> loaded = findLoadedMaid(player.server, maidId)
                    .filter(maid -> maid.isOwnedBy(player));
            if (loaded.isPresent()) {
                EntityMaid maid = loaded.get();
                MaidRescueSessionData.Data session = MaidRescueSessionData.get(maid);
                entries.put(maidId, new MaidRosterEntry(maidId, maid.getDisplayName(),
                        maid.level().dimension().location().toString(), true, maid.level() == player.level(),
                        saved.getValue().enabled(), saved.getValue().combatTask(), true,
                        maid.blockPosition(), System.currentTimeMillis(), session.recoveryTracked(),
                        session.originDimension() == null ? "" : session.originDimension().toString(),
                        session.recoveryTracked() ? net.minecraft.core.BlockPos.containing(session.origin())
                                : net.minecraft.core.BlockPos.ZERO, saved.getValue().loadUnloaded(), false));
            } else {
                boolean stored = SoulSpellMaidService.isStored(player, maidId);
                entries.putIfAbsent(maidId, new MaidRosterEntry(maidId,
                        net.minecraft.network.chat.Component.translatable(
                                "gui.tlm_beyond_space.unknown_maid", maidId.toString().substring(0, 8)),
                        "?", false, false, saved.getValue().enabled(), saved.getValue().combatTask(),
                        false, net.minecraft.core.BlockPos.ZERO, 0L, false, "",
                        net.minecraft.core.BlockPos.ZERO, saved.getValue().loadUnloaded(), stored));
            }
        }
        List<MaidRosterEntry> sorted = new ArrayList<>(entries.values());
        sorted.sort((a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString()));
        return sorted;
    }

    public static Set<UUID> knownOwnedMaidIds(ServerPlayer player) {
        SoulSpellMaidService.refreshKnownItems(player);
        Set<UUID> ids = new LinkedHashSet<>();
        OBSERVED_MAIDS.forEach((maidId, observed) -> {
            if (observed.ownerId().equals(player.getUUID())) {
                ids.add(maidId);
            }
        });
        unloadedInfos(player).forEach(info -> ids.add(info.getEntityId()));
        LastKnownMaidData.get(player.serverLevel()).ownedBy(player.getUUID())
                .forEach(entry -> ids.add(entry.maidId()));
        StoredMaidData.get(player.serverLevel()).ownedBy(player.getUUID())
                .forEach(entry -> ids.add(entry.maidId()));
        return ids;
    }

    public static Optional<LastKnownMaidData.Entry> lastKnown(ServerPlayer player, UUID maidId) {
        LastKnownMaidData data = LastKnownMaidData.get(player.serverLevel());
        SoulSpellMaidService.refreshKnownItems(player);
        if (SoulSpellMaidService.isStored(player, maidId)) {
            return data.get(maidId).filter(entry -> player.getUUID().equals(entry.ownerId()));
        }
        // Refresh the persistent roster from TLM's latest offline record when it is newer.
        unloadedInfos(player).stream()
                .filter(info -> maidId.equals(info.getEntityId()))
                .max(java.util.Comparator.comparingLong(MaidInfo::getTimestamp))
                .ifPresent(data::importInfo);
        return data.get(maidId).filter(entry -> player.getUUID().equals(entry.ownerId()));
    }

    public static Optional<EntityMaid> findLoadedMaid(MinecraftServer server, UUID maidId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(maidId) instanceof EntityMaid maid) {
                return Optional.of(maid);
            }
        }
        return Optional.empty();
    }

    public static void clearCaches() {
        OBSERVED_MAIDS.clear();
    }

    private static List<MaidInfo> unloadedInfos(ServerPlayer player) {
        MaidWorldData worldData = MaidWorldData.get(player.level());
        if (worldData == null) {
            return List.of();
        }
        List<MaidInfo> infos = worldData.getPlayerMaidInfos(player);
        if (infos == null || infos.isEmpty()) {
            return List.of();
        }

        // TLM's MaidWorldData.addInfo appends blindly. A cross-dimension transfer from another mod
        // can therefore leave several offline rows for one entity UUID (as seen in Red Fox Scroll),
        // even though they do not represent several live maids. Keep only the newest row per UUID
        // and remove offline rows for an entity that is currently loaded.
        Map<UUID, MaidInfo> newestByMaid = new LinkedHashMap<>();
        for (MaidInfo info : infos) {
            if (info == null || info.getEntityId() == null) {
                continue;
            }
            newestByMaid.merge(info.getEntityId(), info,
                    (left, right) -> right.getTimestamp() >= left.getTimestamp() ? right : left);
        }
        newestByMaid.entrySet().removeIf(entry ->
                findLoadedMaid(player.server, entry.getKey()).isPresent());

        int originalSize = infos.size();
        List<MaidInfo> repaired = new ArrayList<>(newestByMaid.values());
        if (repaired.size() != originalSize || !repaired.equals(infos)) {
            try {
                infos.clear();
                infos.addAll(repaired);
                worldData.setDirty();
                TlmBeyondSpace.LOGGER.info("Repaired MaidWorldData offline roster for owner {}: {} -> {} rows",
                        player.getUUID(), originalSize, repaired.size());
            } catch (UnsupportedOperationException error) {
                TlmBeyondSpace.LOGGER.warn("Could not repair immutable MaidWorldData roster for owner {}",
                        player.getUUID(), error);
            }
        }
        return List.copyOf(repaired);
    }

    private static Component componentName(String name, UUID maidId) {
        if (name == null || name.isBlank()) {
            return Component.translatable("gui.tlm_beyond_space.unknown_maid",
                    maidId.toString().substring(0, 8));
        }
        return Component.literal(name);
    }

    private record ObservedMaid(UUID ownerId, net.minecraft.network.chat.Component name, String dimension) {
    }

    private MaidRosterService() {
    }
}
