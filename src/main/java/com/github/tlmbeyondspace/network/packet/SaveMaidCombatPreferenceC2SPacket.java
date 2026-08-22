package com.github.tlmbeyondspace.network.packet;

import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tlmbeyondspace.data.MaidCombatPreferenceData;
import com.github.tlmbeyondspace.service.MaidRosterService;
import com.github.tlmbeyondspace.service.RescueTaskClassifier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SaveMaidCombatPreferenceC2SPacket(UUID maidId, ResourceLocation taskId) {
    private static final double MAX_CONFIG_DISTANCE_SQR = 64.0D;

    public static void encode(SaveMaidCombatPreferenceC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.maidId);
        buffer.writeResourceLocation(packet.taskId);
    }

    public static SaveMaidCombatPreferenceC2SPacket decode(FriendlyByteBuf buffer) {
        return new SaveMaidCombatPreferenceC2SPacket(buffer.readUUID(), buffer.readResourceLocation());
    }

    public static void handle(SaveMaidCombatPreferenceC2SPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        MaidRosterService.findLoadedMaid(player.server, packet.maidId)
                .filter(maid -> maid.isOwnedBy(player))
                .filter(maid -> maid.level() == player.level())
                .filter(maid -> maid.distanceToSqr(player) <= MAX_CONFIG_DISTANCE_SQR)
                .ifPresent(maid -> TaskManager.findTask(packet.taskId)
                        .filter(RescueTaskClassifier::isCombatTask)
                        .ifPresent(task -> {
                            MaidCombatPreferenceData.set(maid, task.getUid());
                            player.displayClientMessage(Component.translatable(
                                    "message.tlm_beyond_space.combat_preference_saved",
                                    maid.getDisplayName(), task.getName()), true);
                        }));
    }
}
