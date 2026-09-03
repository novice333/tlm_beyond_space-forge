package com.github.tlmbeyondspace.service;

import com.github.tlmbeyondspace.compat.MaidReformCompat;
import com.github.tlmbeyondspace.data.DistressSignalData;
import com.github.tlmbeyondspace.item.DistressSignalItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Detects only the false -> true MaidReform knockdown edge, preventing repeated summons. */
public final class MaidReformRescueService {
    private static final Map<UUID, Boolean> PREVIOUS_DOWN = new HashMap<>();

    public static void tick(ServerPlayer player) {
        if (!MaidReformCompat.isLoaded()) {
            PREVIOUS_DOWN.remove(player.getUUID());
            return;
        }
        boolean down = MaidReformCompat.isPlayerKnockedDown(player);
        boolean previous = PREVIOUS_DOWN.getOrDefault(player.getUUID(), false);
        PREVIOUS_DOWN.put(player.getUUID(), down);
        if (!down || previous) {
            return;
        }
        ItemStack signal = findEnabledSignal(player);
        if (!signal.isEmpty()) {
            DistressCrossDimSupport.activateForKnockdown(player, signal);
        }
    }

    private static ItemStack findEnabledSignal(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (isEnabledFor(player, stack)) {
                return stack;
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isEnabledFor(player, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isEnabledFor(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof DistressSignalItem)) {
            return false;
        }
        DistressSignalData data = DistressSignalData.fromItem(stack);
        return data.knockdownRescue() && data.canUse(player.getUUID())
                && data.selections().values().stream().anyMatch(DistressSignalData.Selection::enabled);
    }

    public static void removePlayer(UUID playerId) {
        PREVIOUS_DOWN.remove(playerId);
    }

    public static void clear() {
        PREVIOUS_DOWN.clear();
    }

    private MaidReformRescueService() {
    }
}
