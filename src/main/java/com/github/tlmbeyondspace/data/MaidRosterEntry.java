package com.github.tlmbeyondspace.data;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record MaidRosterEntry(UUID maidId, Component name, String dimension, boolean loaded,
                              boolean sameDimension, boolean enabled, ResourceLocation combatTask) {
}
