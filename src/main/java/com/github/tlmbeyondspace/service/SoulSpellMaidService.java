package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem;
import com.github.tlmbeyondspace.data.StoredMaidData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Tracks TLM's entity-to-item conversion so stale MaidWorldData coordinates are never force-loaded. */
public final class SoulSpellMaidService {
    public static void markStored(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel level && maid.getOwnerUUID() != null) {
            StoredMaidData.get(level).markStored(maid.getUUID(), maid.getOwnerUUID(),
                    maid.getDisplayName().getString());
        }
    }

    public static void clearStored(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel level) {
            StoredMaidData.get(level).clearStored(maid.getUUID());
        }
    }

    /** Repairs worlds containing Soul Spells created before this mod started tracking conversion events. */
    public static void refreshKnownItems(ServerPlayer player) {
        scan(player, player.getInventory());
        scan(player, player.getEnderChestInventory());
    }

    public static boolean isStored(ServerPlayer player, UUID maidId) {
        return StoredMaidData.get(player.serverLevel()).isStored(maidId, player.getUUID());
    }

    private static void scan(ServerPlayer player, Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!(stack.getItem() instanceof AbstractStoreMaidItem)
                    || !AbstractStoreMaidItem.hasMaidData(stack)) {
                continue;
            }
            CompoundTag maidData = AbstractStoreMaidItem.getMaidData(stack);
            if (!maidData.hasUUID("UUID") || !maidData.hasUUID("Owner")) {
                continue;
            }
            UUID ownerId = maidData.getUUID("Owner");
            if (!player.getUUID().equals(ownerId)) {
                continue;
            }
            StoredMaidData.get(player.serverLevel()).markStored(maidData.getUUID("UUID"), ownerId,
                    readName(maidData));
        }
    }

    private static String readName(CompoundTag maidData) {
        if (!maidData.contains("CustomName")) {
            return "";
        }
        try {
            Component name = Component.Serializer.fromJson(maidData.getString("CustomName"));
            return name == null ? "" : name.getString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private SoulSpellMaidService() {
    }
}
