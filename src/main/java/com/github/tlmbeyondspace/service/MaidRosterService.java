package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.github.tlmbeyondspace.data.DistressSignalData;
import com.github.tlmbeyondspace.data.MaidRosterEntry;
import com.github.tlmbeyondspace.network.BeyondSpaceNetwork;
import com.github.tlmbeyondspace.network.packet.OpenDistressRosterS2CPacket;
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
        if (maid.level().isClientSide || maid.getOwnerUUID() == null) {
            return;
        }
        ObservedMaid current = new ObservedMaid(maid.getOwnerUUID(), maid.getDisplayName(),
                maid.level().dimension().location().toString());
        OBSERVED_MAIDS.compute(maid.getUUID(), (id, previous) -> current.equals(previous) ? previous : current);
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
                new OpenDistressRosterS2CPacket(hand, entries, data.recallMode()));
    }

    public static List<MaidRosterEntry> buildRoster(ServerPlayer player, DistressSignalData data) {
        Map<UUID, MaidRosterEntry> entries = new LinkedHashMap<>();
        OBSERVED_MAIDS.forEach((maidId, observed) -> {
            if (!observed.ownerId().equals(player.getUUID()) || entries.size() >= DistressSignalData.MAX_SELECTIONS) {
                return;
            }
            Optional<EntityMaid> loaded = findLoadedMaid(player.server, maidId)
                    .filter(maid -> maid.isOwnedBy(player));
            if (loaded.isPresent()) {
                EntityMaid maid = loaded.get();
                DistressSignalData.Selection selection = data.get(maidId);
                entries.put(maidId, new MaidRosterEntry(maidId, maid.getDisplayName(),
                        maid.level().dimension().location().toString(), true, maid.level() == player.level(),
                        selection.enabled(), selection.combatTask()));
            }
        });

        for (MaidInfo info : unloadedInfos(player)) {
            if (entries.size() >= DistressSignalData.MAX_SELECTIONS) break;
            Optional<EntityMaid> loaded = findLoadedMaid(player.server, info.getEntityId())
                    .filter(maid -> maid.isOwnedBy(player));
            DistressSignalData.Selection selection = data.get(info.getEntityId());
            entries.putIfAbsent(info.getEntityId(), new MaidRosterEntry(info.getEntityId(), info.getName(),
                    info.getDimension(), loaded.isPresent(), loaded.filter(maid -> maid.level() == player.level()).isPresent(),
                    selection.enabled(), selection.combatTask()));
        }

        for (Map.Entry<UUID, DistressSignalData.Selection> saved : data.selections().entrySet()) {
            if (entries.size() >= DistressSignalData.MAX_SELECTIONS) break;
            UUID maidId = saved.getKey();
            Optional<EntityMaid> loaded = findLoadedMaid(player.server, maidId)
                    .filter(maid -> maid.isOwnedBy(player));
            if (loaded.isPresent()) {
                EntityMaid maid = loaded.get();
                entries.put(maidId, new MaidRosterEntry(maidId, maid.getDisplayName(),
                        maid.level().dimension().location().toString(), true, maid.level() == player.level(),
                        saved.getValue().enabled(), saved.getValue().combatTask()));
            } else {
                entries.putIfAbsent(maidId, new MaidRosterEntry(maidId,
                        net.minecraft.network.chat.Component.translatable(
                                "gui.tlm_beyond_space.unknown_maid", maidId.toString().substring(0, 8)),
                        "?", false, false, saved.getValue().enabled(), saved.getValue().combatTask()));
            }
        }
        List<MaidRosterEntry> sorted = new ArrayList<>(entries.values());
        sorted.sort((a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString()));
        return sorted;
    }

    public static Set<UUID> knownOwnedMaidIds(ServerPlayer player) {
        Set<UUID> ids = new LinkedHashSet<>();
        OBSERVED_MAIDS.forEach((maidId, observed) -> {
            if (observed.ownerId().equals(player.getUUID())) {
                ids.add(maidId);
            }
        });
        unloadedInfos(player).forEach(info -> ids.add(info.getEntityId()));
        return ids;
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
        return infos == null ? List.of() : List.copyOf(infos);
    }

    private record ObservedMaid(UUID ownerId, net.minecraft.network.chat.Component name, String dimension) {
    }

    private MaidRosterService() {
    }
}
