package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.TlmBeyondSpace;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import net.minecraft.server.MinecraftServer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, failure-isolated retries for safe origin returns. */
public final class PendingMaidReturnService {
    private static final int RETRY_INTERVAL = 20;
    private static final int INITIAL_DELAY = 3;
    private static final int MAX_LIFETIME = 72_000;
    private static final double ORIGIN_TOLERANCE_SQR = 4.0D;
    private static final Map<UUID, Request> REQUESTS = new LinkedHashMap<>();

    public static void defer(MinecraftServer server, EntityMaid maid, MaidRescueSessionData.Data session) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            return;
        }
        long now = server.getTickCount();
        REQUESTS.put(maid.getUUID(), new Request(maid.getUUID(), ownerId, session,
                now + INITIAL_DELAY, now + MAX_LIFETIME));
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<Request> iterator = REQUESTS.values().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            if (now >= request.expiresAt) {
                TlmBeyondSpace.LOGGER.warn("Giving up deferred origin return for maid {} after timeout",
                        request.maidId);
                iterator.remove();
                continue;
            }
            if (now < request.nextAttempt) {
                continue;
            }
            try {
                EntityMaid maid = MaidRosterService.findLoadedMaid(server, request.maidId).orElse(null);
                if (maid == null) {
                    request.nextAttempt = now + RETRY_INTERVAL;
                    continue;
                }
                if (!request.ownerId.equals(maid.getOwnerUUID())) {
                    iterator.remove();
                    continue;
                }
                MaidRescueSessionData.Data current = MaidRescueSessionData.get(maid);
                if (current.recoveryTracked() && !sameSession(current, request.session)) {
                    request.nextAttempt = now + RETRY_INTERVAL;
                    continue;
                }
                if (isAtOrigin(maid, request.session)) {
                    iterator.remove();
                    continue;
                }

                MaidRescueSessionData.set(maid, request.session);
                if (DistressCrossDimSupport.finishAndReturn(maid, request.session)) {
                    iterator.remove();
                } else {
                    DistressCrossDimSupport.restoreForPendingReturn(maid, request.session);
                    request.nextAttempt = now + RETRY_INTERVAL;
                }
            } catch (Exception | LinkageError error) {
                TlmBeyondSpace.LOGGER.warn("Deferred origin return failed safely for maid {}",
                        request.maidId, error);
                request.nextAttempt = now + RETRY_INTERVAL;
            }
        }
    }

    static boolean isAtOrigin(EntityMaid maid, MaidRescueSessionData.Data session) {
        return session.originDimension() != null
                && maid.level().dimension().location().equals(session.originDimension())
                && maid.position().distanceToSqr(session.origin()) <= ORIGIN_TOLERANCE_SQR;
    }

    private static boolean sameSession(MaidRescueSessionData.Data left, MaidRescueSessionData.Data right) {
        return left.kind() == right.kind() && left.startedAt() == right.startedAt()
                && java.util.Objects.equals(left.originDimension(), right.originDimension())
                && left.origin().equals(right.origin());
    }

    public static void clear() {
        REQUESTS.clear();
    }

    private static final class Request {
        private final UUID maidId;
        private final UUID ownerId;
        private final MaidRescueSessionData.Data session;
        private long nextAttempt;
        private final long expiresAt;

        private Request(UUID maidId, UUID ownerId, MaidRescueSessionData.Data session, long nextAttempt,
                        long expiresAt) {
            this.maidId = maidId;
            this.ownerId = ownerId;
            this.session = session;
            this.nextAttempt = nextAttempt;
            this.expiresAt = expiresAt;
        }
    }

    private PendingMaidReturnService() {
    }
}
