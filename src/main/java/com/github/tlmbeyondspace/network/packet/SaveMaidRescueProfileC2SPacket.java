package com.github.tlmbeyondspace.network.packet;

import com.github.tlmbeyondspace.data.MaidRescueProfileData;
import com.github.tlmbeyondspace.data.TaskModeProfile;
import com.github.tlmbeyondspace.service.MaidRosterService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SaveMaidRescueProfileC2SPacket(UUID maidId, CompoundTag profileTag) {
    private static final double MAX_CONFIG_DISTANCE_SQR = 64.0D;

    public static void encode(SaveMaidRescueProfileC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.maidId);
        buffer.writeNbt(packet.profileTag);
    }

    public static SaveMaidRescueProfileC2SPacket decode(FriendlyByteBuf buffer) {
        UUID maidId = buffer.readUUID();
        CompoundTag profileTag = buffer.readNbt();
        return new SaveMaidRescueProfileC2SPacket(maidId,
                profileTag == null ? new CompoundTag() : profileTag);
    }

    public static void handle(SaveMaidRescueProfileC2SPacket packet,
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
                .ifPresent(maid -> MaidRescueProfileData.updateProfile(
                        maid, player.getUUID(), TaskModeProfile.load(packet.profileTag)));
    }
}
