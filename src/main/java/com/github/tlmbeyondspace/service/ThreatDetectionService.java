package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class ThreatDetectionService {
    public static Optional<LivingEntity> findThreatAround(EntityMaid maid, LivingEntity center, double radius) {
        if (!(maid.level() instanceof ServerLevel level) || center.level() != level) {
            return Optional.empty();
        }
        AABB area = center.getBoundingBox().inflate(radius, Math.min(radius, 8), radius);
        return level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> isThreat(maid, entity) && entity.distanceToSqr(center) <= radius * radius)
                .stream().findFirst();
    }

    public static Optional<LivingEntity> findThreatAroundAnchor(EntityMaid maid, Vec3 anchor, double radius) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        AABB area = new AABB(anchor, anchor).inflate(radius, Math.min(radius, 8), radius);
        return level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> isThreat(maid, entity) && entity.distanceToSqr(anchor) <= radius * radius)
                .stream().findFirst();
    }

    public static boolean isThreat(EntityMaid maid, LivingEntity entity) {
        return entity != maid && entity.isAlive() && entity instanceof Enemy && !maid.isAlliedTo(entity);
    }

    private ThreatDetectionService() {
    }
}
