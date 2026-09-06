package com.github.tlmbeyondspace.service;

import com.mojang.datafixers.util.Either;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.data.DistressSignalData;
import com.github.tlmbeyondspace.data.LastKnownMaidData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Loads at most two recorded maid chunks concurrently and never reconstructs an entity from NBT. */
public final class MaidChunkLoadService {
    private static final int MAX_CONCURRENT_CHUNKS = 2;
    private static final int MAX_SELECTED_MAIDS_TO_SCAN = 20;
    /** Allows a full 20-chunk request to make progress even when several entity loads are slow. */
    private static final int REQUEST_TIMEOUT_TICKS = 7_200;
    /** FULL chunk completion precedes entity registration; give TLM half a second before polling. */
    private static final int ENTITY_RESOLVE_DELAY_TICKS = 10;
    /** Some mod-heavy worlds register saved entities several seconds after the chunk reaches FULL. */
    private static final int ENTITY_RESOLVE_TIMEOUT_TICKS = 600;
    /** A newly written TLM offline record may become visible a few ticks after the entity unloads. */
    private static final int LOCATION_RESOLVE_TIMEOUT_TICKS = 100;
    private static final TicketType<UUID> TICKET = TicketType.create("tlm_beyond_space_rescue",
            Comparator.comparing(UUID::toString), 1_200);
    private static final Deque<Request> REQUESTS = new ArrayDeque<>();
    private static final List<ActiveChunk> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Request> BY_PLAYER = new HashMap<>();
    private static long lastStartTick = Long.MIN_VALUE;

    public static boolean prepare(ServerPlayer player, Item signalItem, DistressSignalData data,
                                  int helperLimit, ActivationKind kind) {
        if (BY_PLAYER.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "message.tlm_beyond_space.chunk_load.already_loading"), true);
            return true;
        }
        SoulSpellMaidService.refreshKnownItems(player);
        LinkedHashMap<ChunkKey, List<UUID>> chunks = new LinkedHashMap<>();
        Set<UUID> unresolvedLocations = new LinkedHashSet<>();
        int scannedSelections = 0;
        for (Map.Entry<UUID, DistressSignalData.Selection> selection : data.selections().entrySet()) {
            if (!selection.getValue().enabled()) {
                continue;
            }
            // helperLimit limits successful responders, not how far down the ordered roster we
            // search. Earlier entries may already be assisting or otherwise unable to respond;
            // stopping after helperLimit entries made later unloaded maids miss preloading and
            // appear as "currently not loaded" on the first use.
            if (scannedSelections++ >= MAX_SELECTED_MAIDS_TO_SCAN) {
                break;
            }
            UUID maidId = selection.getKey();
            boolean loaded = MaidRosterService.findLoadedMaid(player.server, maidId).isPresent();
            if (!loaded && (!selection.getValue().loadUnloaded()
                    || SoulSpellMaidService.isStored(player, maidId))) {
                continue;
            }
            if (loaded) {
                continue;
            }
            LastKnownMaidData.Entry known = MaidRosterService.lastKnown(player, maidId).orElse(null);
            if (known == null || known.dimension() == null || known.dimension().isBlank()) {
                unresolvedLocations.add(maidId);
                continue;
            }
            int chunkX = known.position().getX() >> 4;
            int chunkZ = known.position().getZ() >> 4;
            chunks.computeIfAbsent(new ChunkKey(known.dimension(), chunkX, chunkZ), ignored -> new ArrayList<>())
                    .add(maidId);
        }
        if (chunks.isEmpty() && unresolvedLocations.isEmpty()) {
            return false;
        }
        long now = player.server.getTickCount();
        Request request = new Request(UUID.randomUUID(), player.getUUID(), signalItem, data, helperLimit, kind,
                now + REQUEST_TIMEOUT_TICKS, now + LOCATION_RESOLVE_TIMEOUT_TICKS,
                new ArrayDeque<>(chunks.entrySet()), unresolvedLocations);
        REQUESTS.add(request);
        BY_PLAYER.put(player.getUUID(), request);
        int queuedMaidCount = chunks.values().stream().mapToInt(List::size).sum()
                + unresolvedLocations.size();
        player.displayClientMessage(Component.translatable("message.tlm_beyond_space.chunk_load.started",
                queuedMaidCount), true);
        TlmBeyondSpace.LOGGER.info("Queued maid recovery for player {}: {} chunk(s), {} location(s) pending",
                player.getUUID(), chunks.size(), unresolvedLocations.size());
        return true;
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<ActiveChunk> activeIterator = ACTIVE.iterator();
        while (activeIterator.hasNext()) {
            ActiveChunk active = activeIterator.next();
            if (active.future.isCompletedExceptionally() || active.future.isCancelled()) {
                completeChunk(active, LoadFailure.CHUNK_LOAD_FAILED);
                activeIterator.remove();
                continue;
            }
            if (!active.future.isDone()) {
                if (now >= active.request.expiresAt) {
                    completeChunk(active, LoadFailure.CHUNK_LOAD_FAILED);
                    activeIterator.remove();
                }
                continue;
            }
            Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result = active.future.getNow(null);
            if (result == null || result.right().isPresent()) {
                completeChunk(active, LoadFailure.CHUNK_LOAD_FAILED);
                activeIterator.remove();
                continue;
            }
            if (active.resolveAt < 0) {
                active.resolveAt = now + ENTITY_RESOLVE_DELAY_TICKS;
                active.resolveDeadline = now + ENTITY_RESOLVE_TIMEOUT_TICKS;
                continue;
            }
            if (now < active.resolveAt) {
                continue;
            }
            boolean allResolved = true;
            for (UUID maidId : active.maidIds) {
                if (MaidRosterService.findLoadedMaid(server, maidId).isEmpty()) {
                    allResolved = false;
                }
            }
            if (allResolved) {
                completeChunk(active, null);
                activeIterator.remove();
                continue;
            }
            if (now >= active.resolveDeadline) {
                for (UUID maidId : active.maidIds) {
                    if (MaidRosterService.findLoadedMaid(server, maidId).isEmpty()) {
                        active.request.failures.put(maidId, LoadFailure.MAID_NOT_FOUND);
                    }
                }
                completeChunk(active, null);
                activeIterator.remove();
            }
        }

        Iterator<Request> requestIterator = REQUESTS.iterator();
        while (requestIterator.hasNext()) {
            Request request = requestIterator.next();
            resolvePendingLocations(server, request, now);
            if (now >= request.expiresAt) {
                request.pending.forEach(entry -> entry.getValue().forEach(
                        maidId -> request.failures.put(maidId, LoadFailure.CHUNK_LOAD_FAILED)));
                request.pending.clear();
                request.unresolvedLocations.forEach(
                        maidId -> request.failures.put(maidId, LoadFailure.LOCATION_UNKNOWN));
                request.unresolvedLocations.clear();
            }
            if (request.pending.isEmpty() && request.unresolvedLocations.isEmpty()
                    && request.activeChunks == 0) {
                finish(server, request);
                requestIterator.remove();
            }
        }

        if (ACTIVE.size() >= MAX_CONCURRENT_CHUNKS || lastStartTick == now) {
            return;
        }
        Request next = REQUESTS.stream().filter(request -> !request.pending.isEmpty()).findFirst().orElse(null);
        if (next == null) {
            return;
        }
        Map.Entry<ChunkKey, List<UUID>> entry = next.pending.removeFirst();
        ServerLevel level = findLevel(server, entry.getKey().dimension);
        if (level == null) {
            entry.getValue().forEach(maidId -> next.failures.put(maidId, LoadFailure.DIMENSION_MISSING));
            return;
        }
        ChunkPos pos = new ChunkPos(entry.getKey().chunkX, entry.getKey().chunkZ);
        try {
            level.getChunkSource().addRegionTicket(TICKET, pos, 2, next.id);
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future =
                    level.getChunkSource().getChunkFuture(pos.x, pos.z,
                    ChunkStatus.FULL, true);
            ACTIVE.add(new ActiveChunk(next, level, pos, List.copyOf(entry.getValue()), future));
            next.activeChunks++;
            lastStartTick = now;
        } catch (Exception | LinkageError error) {
            TlmBeyondSpace.LOGGER.warn("Could not queue maid chunk {} in {}", pos, entry.getKey().dimension, error);
            entry.getValue().forEach(maidId -> next.failures.put(maidId, LoadFailure.CHUNK_LOAD_FAILED));
            try {
                level.getChunkSource().removeRegionTicket(TICKET, pos, 2, next.id);
            } catch (Exception | LinkageError ignored) {
            }
        }
    }

    private static void resolvePendingLocations(MinecraftServer server, Request request, long now) {
        if (request.unresolvedLocations.isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(request.playerId);
        if (player == null) {
            return;
        }
        Map<ChunkKey, List<UUID>> discovered = new LinkedHashMap<>();
        Iterator<UUID> iterator = request.unresolvedLocations.iterator();
        while (iterator.hasNext()) {
            UUID maidId = iterator.next();
            if (MaidRosterService.findLoadedMaid(server, maidId).isPresent()) {
                iterator.remove();
                continue;
            }
            LastKnownMaidData.Entry known = MaidRosterService.lastKnown(player, maidId).orElse(null);
            if (known != null && known.dimension() != null && !known.dimension().isBlank()) {
                ChunkKey key = new ChunkKey(known.dimension(), known.position().getX() >> 4,
                        known.position().getZ() >> 4);
                discovered.computeIfAbsent(key, ignored -> new ArrayList<>()).add(maidId);
                iterator.remove();
            } else if (now >= request.locationResolveDeadline) {
                request.failures.put(maidId, LoadFailure.LOCATION_UNKNOWN);
                iterator.remove();
            }
        }
        discovered.entrySet().forEach(request.pending::addLast);
    }

    private static void completeChunk(ActiveChunk active, LoadFailure failure) {
        if (failure != null) {
            active.maidIds.forEach(maidId -> active.request.failures.put(maidId, failure));
        }
        try {
            active.level.getChunkSource().removeRegionTicket(TICKET, active.pos, 2, active.request.id);
        } catch (Exception | LinkageError ignored) {
        }
        active.request.activeChunks = Math.max(0, active.request.activeChunks - 1);
    }

    private static void finish(MinecraftServer server, Request request) {
        BY_PLAYER.remove(request.playerId);
        ServerPlayer player = server.getPlayerList().getPlayer(request.playerId);
        if (player != null) {
            DistressCrossDimSupport.activatePrepared(player, request.signalItem, request.data,
                    request.helperLimit, request.kind, Map.copyOf(request.failures));
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(id)) {
                return level;
            }
        }
        return null;
    }

    public static void clear() {
        ACTIVE.forEach(active -> {
            try {
                active.level.getChunkSource().removeRegionTicket(TICKET, active.pos, 2, active.request.id);
            } catch (Exception | LinkageError ignored) {
            }
        });
        ACTIVE.clear();
        REQUESTS.clear();
        BY_PLAYER.clear();
    }

    public enum ActivationKind {
        MANUAL,
        MAID_REFORM_KNOCKDOWN
    }

    public enum LoadFailure {
        DIMENSION_MISSING,
        CHUNK_LOAD_FAILED,
        MAID_NOT_FOUND,
        LOCATION_UNKNOWN
    }

    private record ChunkKey(String dimension, int chunkX, int chunkZ) {
    }

    private static final class Request {
        private final UUID id;
        private final UUID playerId;
        private final Item signalItem;
        private final DistressSignalData data;
        private final int helperLimit;
        private final ActivationKind kind;
        private final long expiresAt;
        private final long locationResolveDeadline;
        private final Deque<Map.Entry<ChunkKey, List<UUID>>> pending;
        private final Set<UUID> unresolvedLocations;
        private final Map<UUID, LoadFailure> failures = new HashMap<>();
        private int activeChunks;

        private Request(UUID id, UUID playerId, Item signalItem, DistressSignalData data, int helperLimit,
                        ActivationKind kind, long expiresAt, long locationResolveDeadline,
                        Deque<Map.Entry<ChunkKey, List<UUID>>> pending, Set<UUID> unresolvedLocations) {
            this.id = id;
            this.playerId = playerId;
            this.signalItem = signalItem;
            this.data = data;
            this.helperLimit = helperLimit;
            this.kind = kind;
            this.expiresAt = expiresAt;
            this.locationResolveDeadline = locationResolveDeadline;
            this.pending = pending;
            this.unresolvedLocations = new LinkedHashSet<>(unresolvedLocations);
        }
    }

    private static final class ActiveChunk {
        private final Request request;
        private final ServerLevel level;
        private final ChunkPos pos;
        private final List<UUID> maidIds;
        private final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future;
        private long resolveAt = -1L;
        private long resolveDeadline = -1L;

        private ActiveChunk(Request request, ServerLevel level, ChunkPos pos, List<UUID> maidIds,
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future) {
            this.request = request;
            this.level = level;
            this.pos = pos;
            this.maidIds = maidIds;
            this.future = future;
        }
    }

    private MaidChunkLoadService() {
    }
}
