package com.github.tlmbeyondspace.network.packet;

import com.github.tlmbeyondspace.data.TaskModeProfile;
import com.github.tlmbeyondspace.item.SpacetimeRescueCharmItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SaveCharmProfileC2SPacket(InteractionHand hand, CompoundTag profileTag) {
    public static void encode(SaveCharmProfileC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeNbt(packet.profileTag);
    }

    public static SaveCharmProfileC2SPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        CompoundTag tag = buffer.readNbt();
        return new SaveCharmProfileC2SPacket(hand, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SaveCharmProfileC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        ItemStack held = player.getItemInHand(packet.hand);
        if (!(held.getItem() instanceof SpacetimeRescueCharmItem)) {
            return;
        }
        TaskModeProfile sanitized = TaskModeProfile.load(packet.profileTag);
        TaskModeProfile.writeItem(held, sanitized);
        player.displayClientMessage(Component.translatable("message.tlm_beyond_space.profile_saved",
                sanitized.view().size()), true);
    }
}
