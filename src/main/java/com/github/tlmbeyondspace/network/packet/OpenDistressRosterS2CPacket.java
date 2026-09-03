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
                                          boolean recallMode, boolean maidReformAvailable,
                                          boolean knockdownRescue) {
    public OpenDistressRosterS2CPacket(InteractionHand hand, List<MaidRosterEntry> entries) {
        this(hand, entries, false, false, false);
    }

    public OpenDistressRosterS2CPacket(InteractionHand hand, List<MaidRosterEntry> entries,
                                      boolean recallMode) {
        this(hand, entries, recallMode, false, false);
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
            buffer.writeBoolean(entry.positionKnown());
            if (entry.positionKnown()) {
                buffer.writeBlockPos(entry.lastPosition());
            }
            buffer.writeLong(entry.lastSeen());
            buffer.writeBoolean(entry.rescueOriginKnown());
            if (entry.rescueOriginKnown()) {
                buffer.writeUtf(entry.rescueOriginDimension(), 256);
                buffer.writeBlockPos(entry.rescueOriginPosition());
            }
            buffer.writeBoolean(entry.loadUnloaded());
            buffer.writeBoolean(entry.storedInSoulSpell());
        }
        buffer.writeBoolean(packet.recallMode);
        buffer.writeBoolean(packet.maidReformAvailable);
        buffer.writeBoolean(packet.knockdownRescue);
    }

    public static OpenDistressRosterS2CPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        int size = buffer.readVarInt();
        if (size < 0 || size > com.github.tlmbeyondspace.data.DistressSignalData.MAX_SELECTIONS) {
            throw new IllegalArgumentException("Invalid distress roster size: " + size);
        }
        List<MaidRosterEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            java.util.UUID maidId = buffer.readUUID();
            net.minecraft.network.chat.Component name = buffer.readComponent();
            String dimension = buffer.readUtf(256);
            boolean loaded = buffer.readBoolean();
            boolean sameDimension = buffer.readBoolean();
            boolean enabled = buffer.readBoolean();
            net.minecraft.resources.ResourceLocation task = buffer.readResourceLocation();
            boolean positionKnown = buffer.readBoolean();
            net.minecraft.core.BlockPos position = positionKnown ? buffer.readBlockPos()
                    : net.minecraft.core.BlockPos.ZERO;
            long lastSeen = buffer.readLong();
            boolean rescueOriginKnown = buffer.readBoolean();
            String rescueOriginDimension = rescueOriginKnown ? buffer.readUtf(256) : "";
            net.minecraft.core.BlockPos rescueOriginPosition = rescueOriginKnown
                    ? buffer.readBlockPos() : net.minecraft.core.BlockPos.ZERO;
            boolean loadUnloaded = buffer.readBoolean();
            boolean storedInSoulSpell = buffer.readBoolean();
            entries.add(new MaidRosterEntry(maidId, name, dimension, loaded, sameDimension, enabled,
                    task, positionKnown, position, lastSeen, rescueOriginKnown,
                    rescueOriginDimension, rescueOriginPosition, loadUnloaded, storedInSoulSpell));
        }
        return new OpenDistressRosterS2CPacket(hand, entries, buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(OpenDistressRosterS2CPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSetup.openDistressRosterScreen(packet.hand, packet.entries, packet.recallMode,
                        packet.maidReformAvailable, packet.knockdownRescue));
    }
}
