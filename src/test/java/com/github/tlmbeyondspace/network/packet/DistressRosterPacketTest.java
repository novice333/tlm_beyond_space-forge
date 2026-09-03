package com.github.tlmbeyondspace.network.packet;

import com.github.tlmbeyondspace.data.MaidRosterEntry;
import com.github.tlmbeyondspace.data.DistressSignalData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistressRosterPacketTest {
    @Test
    void openPacketRoundTripsRecallMode() {
        MaidRosterEntry entry = new MaidRosterEntry(UUID.randomUUID(), Component.literal("Maid"),
                "minecraft:overworld", true, true, true,
                new ResourceLocation("touhou_little_maid", "attack"), true,
                new BlockPos(12, 63, -8), 12345L, true, "minecraft:overworld",
                new BlockPos(80, 70, 20), false, true);
        OpenDistressRosterS2CPacket source = new OpenDistressRosterS2CPacket(
                InteractionHand.MAIN_HAND, List.of(entry), true, true, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        OpenDistressRosterS2CPacket.encode(source, buffer);
        OpenDistressRosterS2CPacket loaded = OpenDistressRosterS2CPacket.decode(buffer);

        assertTrue(loaded.recallMode());
        assertTrue(loaded.maidReformAvailable());
        assertTrue(loaded.knockdownRescue());
        assertEquals(source.hand(), loaded.hand());
        assertEquals(source.entries().get(0).maidId(), loaded.entries().get(0).maidId());
        assertEquals(entry.lastPosition(), loaded.entries().get(0).lastPosition());
        assertEquals(entry.lastSeen(), loaded.entries().get(0).lastSeen());
        assertTrue(loaded.entries().get(0).rescueOriginKnown());
        assertEquals(entry.rescueOriginPosition(), loaded.entries().get(0).rescueOriginPosition());
        assertEquals(entry.loadUnloaded(), loaded.entries().get(0).loadUnloaded());
        assertEquals(entry.storedInSoulSpell(), loaded.entries().get(0).storedInSoulSpell());
    }

    @Test
    void savePacketRoundTripsRecallMode() {
        SaveDistressRosterC2SPacket source = new SaveDistressRosterC2SPacket(
                InteractionHand.OFF_HAND,
                List.of(new SaveDistressRosterC2SPacket.Entry(UUID.randomUUID(), true, false,
                        new ResourceLocation("third_party", "attack"))), true, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        SaveDistressRosterC2SPacket.encode(source, buffer);
        SaveDistressRosterC2SPacket loaded = SaveDistressRosterC2SPacket.decode(buffer);

        assertTrue(loaded.recallMode());
        assertTrue(loaded.knockdownRescue());
        assertEquals(source.hand(), loaded.hand());
        assertEquals(source.entries(), loaded.entries());
    }

    @Test
    void savePacketRejectsRosterLargerThanProtocolLimit() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(InteractionHand.MAIN_HAND);
        buffer.writeVarInt(DistressSignalData.MAX_SELECTIONS + 1);

        assertThrows(IllegalArgumentException.class,
                () -> SaveDistressRosterC2SPacket.decode(buffer));
    }

    @Test
    void openPacketRejectsRosterLargerThanProtocolLimit() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(InteractionHand.MAIN_HAND);
        buffer.writeVarInt(DistressSignalData.MAX_SELECTIONS + 1);

        assertThrows(IllegalArgumentException.class,
                () -> OpenDistressRosterS2CPacket.decode(buffer));
    }
}
