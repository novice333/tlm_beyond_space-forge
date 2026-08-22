package com.github.tlmbeyondspace.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidRescueProfileDataTest {
    @Test
    void legacyDataWithoutBookIdRemainsReadable() {
        UUID owner = UUID.randomUUID();
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("Bound", true);
        legacy.putUUID("Binder", owner);
        legacy.put("Profile", new TaskModeProfile().save());

        MaidRescueProfileData.Data loaded = MaidRescueProfileData.Data.load(legacy);

        assertTrue(loaded.bound());
        assertEquals(owner, loaded.binderIdOptional().orElseThrow());
        assertTrue(loaded.sourceBookIdOptional().isEmpty());
    }

    @Test
    void newBookIdentityRoundTrips() {
        UUID owner = UUID.randomUUID();
        UUID book = UUID.randomUUID();
        MaidRescueProfileData.Data source = new MaidRescueProfileData.Data(true, owner, book,
                new TaskModeProfile());

        MaidRescueProfileData.Data loaded = MaidRescueProfileData.Data.load(source.save());

        assertEquals(book, loaded.sourceBookIdOptional().orElseThrow());
    }

    @Test
    void directEditKeepsBookIdentityForTheSameOwner() {
        UUID owner = UUID.randomUUID();
        UUID book = UUID.randomUUID();
        MaidRescueProfileData.Data source = new MaidRescueProfileData.Data(true, owner, book,
                new TaskModeProfile());

        MaidRescueProfileData.Data updated = source.withProfile(owner, new TaskModeProfile());

        assertTrue(updated.bound());
        assertEquals(owner, updated.binderIdOptional().orElseThrow());
        assertEquals(book, updated.sourceBookIdOptional().orElseThrow());
    }
}
