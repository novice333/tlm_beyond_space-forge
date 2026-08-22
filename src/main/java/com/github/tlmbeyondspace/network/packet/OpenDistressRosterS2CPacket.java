package com.github.tlmbeyondspace.network.packet;

import com.github.tlmbeyondspace.client.ClientSetup;
import com.github.tlmbeyondspace.data.MaidRosterEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenDistressRosterS2CPacket(InteractionHand hand, List<MaidRosterEntry> entries,
                                          boolean recallMode) {
    public OpenDistressRosterS2CPacket(InteractionHand hand, List<MaidRosterEntry> entries) {
        this(hand, entries, false);
    }

    public static void encode(OpenDistressRosterS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeVarInt(packet.entries.size());
        for (MaidRosterEntry entry : packet.entries) {
            buffer.writeUUID(entry.maidId());
            buffer.writeComponent(entry.name());
            buffer.writeUtf(entry.dimension(), 256);
            buffer.writeBoolean(entry.loaded());
            buffer.writeBoolean(entry.sameDimension());
            buffer.writeBoolean(entry.enabled());
            buffer.writeResourceLocation(entry.combatTask());
        }
        buffer.writeBoolean(packet.recallMode);
    }

    public static OpenDistressRosterS2CPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        int size = buffer.readVarInt();
        if (size < 0 || size > com.github.tlmbeyondspace.data.DistressSignalData.MAX_SELECTIONS) {
            throw new IllegalArgumentException("Invalid distress roster size: " + size);
        }
        List<MaidRosterEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new MaidRosterEntry(buffer.readUUID(), buffer.readComponent(), buffer.readUtf(256),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readResourceLocation()));
        }
        return new OpenDistressRosterS2CPacket(hand, entries, buffer.readBoolean());
    }

    public static void handle(OpenDistressRosterS2CPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSetup.openDistressRosterScreen(packet.hand, packet.entries, packet.recallMode));
    }
}
