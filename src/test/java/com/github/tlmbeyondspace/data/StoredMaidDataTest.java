package com.github.tlmbeyondspace.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredMaidDataTest {
    @Test
    void storedIdentityPersistsAndCanBeCleared() {
        UUID maid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        StoredMaidData source = new StoredMaidData();
        source.markStored(maid, owner, "小酒狐");

        StoredMaidData loaded = StoredMaidData.load(source.save(new CompoundTag()));

        assertTrue(loaded.isStored(maid, owner));
        assertEquals("小酒狐", loaded.get(maid).orElseThrow().name());
        loaded.clearStored(maid);
        assertFalse(loaded.isStored(maid, owner));
    }
}
