package com.github.tlmbeyondspace.service;

import com.github.tlmbeyondspace.config.BeyondSpaceCommonConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DamageSignalService {
    private static final Map<UUID, AttackSignal> SIGNALS = new ConcurrentHashMap<>();

    public static void record(LivingEntity victim, DamageSource source) {
        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker) || attacker == victim || !attacker.isAlive()) {
            return;
        }
        long expiresAt = victim.level().getGameTime() + BeyondSpaceCommonConfig.HURT_MEMORY_TICKS.get();
        SIGNALS.put(victim.getUUID(), new AttackSignal(attacker.getUUID(), victim.level().dimension(), expiresAt));
    }

    public static Optional<LivingEntity> findAttacker(LivingEntity victim) {
        AttackSignal signal = SIGNALS.get(victim.getUUID());
        if (signal == null) {
            return Optional.empty();
        }
        if (victim.level().getGameTime() > signal.expiresAt || victim.level().dimension() != signal.dimension) {
            SIGNALS.remove(victim.getUUID());
            return Optional.empty();
        }
        if (!(victim.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return Optional.empty();
        }
        Entity attacker = level.getEntity(signal.attackerId);
        return attacker instanceof LivingEntity living && living.isAlive() ? Optional.of(living) : Optional.empty();
    }

    public static boolean hasLiveSignal(LivingEntity victim) {
        return findAttacker(victim).isPresent();
    }

    public static void clearVictim(UUID victimId) {
        SIGNALS.remove(victimId);
    }

    public static void clearAll() {
        SIGNALS.clear();
    }

    private record AttackSignal(UUID attackerId, ResourceKey<Level> dimension, long expiresAt) {
    }

    private DamageSignalService() {
    }
}
