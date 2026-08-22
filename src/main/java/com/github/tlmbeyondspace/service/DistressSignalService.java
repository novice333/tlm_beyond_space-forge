package com.github.tlmbeyondspace.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class DistressSignalService {
    public static void activate(ServerPlayer player, InteractionHand hand, ItemStack signal) {
        DistressCrossDimSupport.activate(player, hand, signal);
    }

    private DistressSignalService() {
    }
}
