package com.github.tlmbeyondspace.network.packet;

import com.github.tlmbeyondspace.data.MaidRosterEntry;
import com.github.tlmbeyondspace.data.DistressSignalData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
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
                new ResourceLocation("touhou_little_maid", "attack"));
        OpenDistressRosterS2CPacket source = new OpenDistressRosterS2CPacket(
                InteractionHand.MAIN_HAND, List.of(entry), true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        OpenDistressRosterS2CPacket.encode(source, buffer);
        OpenDistressRosterS2CPacket loaded = OpenDistressRosterS2CPacket.decode(buffer);

        assertTrue(loaded.recallMode());
        assertEquals(source.hand(), loaded.hand());
        assertEquals(source.entries().get(0).maidId(), loaded.entries().get(0).maidId());
    }

    @Test
    void savePacketRoundTripsRecallMode() {
        SaveDistressRosterC2SPacket source = new SaveDistressRosterC2SPacket(
                InteractionHand.OFF_HAND,
                List.of(new SaveDistressRosterC2SPacket.Entry(UUID.randomUUID(), true,
                        new ResourceLocation("third_party", "attack"))), true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        SaveDistressRosterC2SPacket.encode(source, buffer);
        SaveDistressRosterC2SPacket loaded = SaveDistressRosterC2SPacket.decode(buffer);

        assertTrue(loaded.recallMode());
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
