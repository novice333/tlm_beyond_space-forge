package com.github.tlmbeyondspace.data;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidWeaponSwapDataTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void missingFieldsAlwaysLoadAsInactive() {
        assertFalse(MaidWeaponSwapData.Data.load(new CompoundTag()).active());
    }

    @Test
    void activeStateRoundTripsTokenAndSlot() {
        UUID token = UUID.randomUUID();
        ItemStack originalTool = new ItemStack(Items.IRON_PICKAXE);
        originalTool.setDamageValue(12);
        MaidWeaponSwapData.Data source = new MaidWeaponSwapData.Data(true, 3, token, originalTool);

        MaidWeaponSwapData.Data loaded = MaidWeaponSwapData.Data.load(source.save());

        assertTrue(loaded.active());
        assertEquals(3, loaded.caseSlot());
        assertEquals(token, loaded.token());
        assertTrue(ItemStack.matches(originalTool, loaded.expectedCaseStack()));
    }
}
