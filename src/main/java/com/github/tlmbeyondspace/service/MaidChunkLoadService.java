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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Loads at most two recorded maid chunks concurrently and never reconstructs an entity from NBT. */
public final class MaidChunkLoadService {
    private static final int MAX_CONCURRENT_CHUNKS = 2;
    private static final int REQUEST_TIMEOUT_TICKS = 300;
    private static final int ENTITY_RESOLVE_DELAY_TICKS = 2;
    private static final int ENTITY_RESOLVE_TIMEOUT_TICKS = 100;
    private static final TicketType<UUID> TICKET = TicketType.create("tlm_beyond_space_rescue",
            Comparator.comparing(UUID::toString), 440);
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
        int considered = 0;
        for (Map.Entry<UUID, DistressSignalData.Selection> selection : data.selections().entrySet()) {
            if (!selection.getValue().enabled()) {
                continue;
            }
            UUID maidId = selection.getKey();
            boolean loaded = MaidRosterService.findLoadedMaid(player.server, maidId).isPresent();
            if (!loaded && (!selection.getValue().loadUnloaded()
                    || SoulSpellMaidService.isStored(player, maidId))) {
                continue;
            }
            if (considered++ >= Math.min(helperLimit, 20)) {
                break;
            }
            if (loaded) {
                continue;
            }
            LastKnownMaidData.Entry known = MaidRosterService.lastKnown(player, maidId).orElse(null);
            if (known == null || known.dimension() == null || known.dimension().isBlank()) {
                continue;
            }
            int chunkX = known.position().getX() >> 4;
            int chunkZ = known.position().getZ() >> 4;
            chunks.computeIfAbsent(new ChunkKey(known.dimension(), chunkX, chunkZ), ignored -> new ArrayList<>())
                    .add(maidId);
        }
        if (chunks.isEmpty()) {
            return false;
        }
        Request request = new Request(UUID.randomUUID(), player.getUUID(), signalItem, data, helperLimit, kind,
                player.server.getTickCount() + REQUEST_TIMEOUT_TICKS, new ArrayDeque<>(chunks.entrySet()));
        REQUESTS.add(request);
        BY_PLAYER.put(player.getUUID(), request);
        player.displayClientMessage(Component.translatable("message.tlm_beyond_space.chunk_load.started",
                chunks.size()), true);
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
            if (now >= request.expiresAt) {
                request.pending.forEach(entry -> entry.getValue().forEach(
                        maidId -> request.failures.put(maidId, LoadFailure.CHUNK_LOAD_FAILED)));
                request.pending.clear();
            }
            if (request.pending.isEmpty() && request.activeChunks == 0) {
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
        MAID_NOT_FOUND
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
        private final Deque<Map.Entry<ChunkKey, List<UUID>>> pending;
        private final Map<UUID, LoadFailure> failures = new HashMap<>();
        private int activeChunks;

        private Request(UUID id, UUID playerId, Item signalItem, DistressSignalData data, int helperLimit,
                        ActivationKind kind, long expiresAt,
                        Deque<Map.Entry<ChunkKey, List<UUID>>> pending) {
            this.id = id;
            this.playerId = playerId;
            this.signalItem = signalItem;
            this.data = data;
            this.helperLimit = helperLimit;
            this.kind = kind;
            this.expiresAt = expiresAt;
            this.pending = pending;
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
