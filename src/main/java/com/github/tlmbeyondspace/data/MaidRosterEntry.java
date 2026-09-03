package com.github.tlmbeyondspace.data;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public record MaidRosterEntry(UUID maidId, Component name, String dimension, boolean loaded,
                              boolean sameDimension, boolean enabled, ResourceLocation combatTask,
                              boolean positionKnown, BlockPos lastPosition, long lastSeen,
                              boolean rescueOriginKnown, String rescueOriginDimension,
                              BlockPos rescueOriginPosition, boolean loadUnloaded,
                              boolean storedInSoulSpell) {
    public MaidRosterEntry(UUID maidId, Component name, String dimension, boolean loaded,
                           boolean sameDimension, boolean enabled, ResourceLocation combatTask) {
        this(maidId, name, dimension, loaded, sameDimension, enabled, combatTask,
                false, BlockPos.ZERO, 0L, false, "", BlockPos.ZERO, true, false);
    }

    public MaidRosterEntry(UUID maidId, Component name, String dimension, boolean loaded,
                           boolean sameDimension, boolean enabled, ResourceLocation combatTask,
                           boolean positionKnown, BlockPos lastPosition, long lastSeen) {
        this(maidId, name, dimension, loaded, sameDimension, enabled, combatTask,
                positionKnown, lastPosition, lastSeen, false, "", BlockPos.ZERO, true, false);
    }

    public MaidRosterEntry(UUID maidId, Component name, String dimension, boolean loaded,
                           boolean sameDimension, boolean enabled, ResourceLocation combatTask,
                           boolean positionKnown, BlockPos lastPosition, long lastSeen,
                           boolean rescueOriginKnown, String rescueOriginDimension,
                           BlockPos rescueOriginPosition) {
        this(maidId, name, dimension, loaded, sameDimension, enabled, combatTask,
                positionKnown, lastPosition, lastSeen, rescueOriginKnown, rescueOriginDimension,
                rescueOriginPosition, true, false);
    }
}
