package com.github.tlmbeyondspace.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastKnownMaidDataTest {
    @Test
    void nbtRoundTripKeepsOneStableLocationRecord() {
        UUID maid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Maid", maid);
        entry.putUUID("Owner", owner);
        entry.putString("Name", "Alice");
        entry.putString("Dimension", "minecraft:the_nether");
        entry.putInt("X", 12);
        entry.putInt("Y", 64);
        entry.putInt("Z", -9);
        entry.putLong("LastSeen", 456L);
        entry.putBoolean("RescueOriginKnown", true);
        entry.putString("RescueOriginDimension", "minecraft:overworld");
        entry.putInt("RescueOriginX", 100);
        entry.putInt("RescueOriginY", 70);
        entry.putInt("RescueOriginZ", -20);
        ListTag list = new ListTag();
        list.add(entry);
        CompoundTag root = new CompoundTag();
        root.put("Maids", list);

        LastKnownMaidData.Entry loaded = LastKnownMaidData.load(root).get(maid).orElseThrow();

        assertEquals(owner, loaded.ownerId());
        assertEquals("Alice", loaded.name());
        assertEquals("minecraft:the_nether", loaded.dimension());
        assertEquals(12, loaded.position().getX());
        assertEquals(-9, loaded.position().getZ());
        assertEquals(456L, loaded.lastSeen());
        assertTrue(loaded.rescueOriginKnown());
        assertEquals("minecraft:overworld", loaded.rescueOriginDimension());
        assertEquals(100, loaded.rescueOriginPosition().getX());
    }
}
