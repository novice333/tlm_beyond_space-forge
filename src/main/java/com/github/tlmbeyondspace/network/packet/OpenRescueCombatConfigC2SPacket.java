package com.github.tlmbeyondspace.network.packet;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tlmbeyondspace.inventory.RescueCombatConfigMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public record OpenRescueCombatConfigC2SPacket(int maidId) {
    public static void encode(OpenRescueCombatConfigC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidId);
    }

    public static OpenRescueCombatConfigC2SPacket decode(FriendlyByteBuf buffer) {
        return new OpenRescueCombatConfigC2SPacket(buffer.readVarInt());
    }

    public static void handle(OpenRescueCombatConfigC2SPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> open(packet, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void open(OpenRescueCombatConfigC2SPacket packet, ServerPlayer player) {
        if (player == null) {
            return;
        }
        Entity entity = player.level().getEntity(packet.maidId);
        if (!(entity instanceof EntityMaid maid)
                || !maid.isAlive()
                || maid.isSleeping()
                || !maid.isOwnedBy(player)
                || player.distanceToSqr(maid) > 64.0D) {
            return;
        }
        NetworkHooks.openScreen(player, RescueCombatConfigMenu.create(maid.getId()),
                buffer -> buffer.writeInt(maid.getId()));
    }
}
