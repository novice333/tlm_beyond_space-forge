package com.github.tlmbeyondspace.item;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidWeaponCaseItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void storedStackPreservesCompleteNbtAndUsesCopies() {
        ItemStack holder = new ItemStack(Items.BOOK);
        ItemStack weapon = new ItemStack(Items.DIAMOND_SWORD);
        weapon.setDamageValue(37);
        weapon.setHoverName(Component.literal("Rescue Blade"));
        weapon.getOrCreateTag().putString("ThirdPartyPayload", "preserved");

        MaidWeaponCaseItem.setStoredStack(holder, weapon);
        ItemStack loaded = MaidWeaponCaseItem.getStoredStack(holder);

        assertTrue(ItemStack.matches(weapon, loaded));
        assertNotSame(weapon, loaded);
        loaded.getOrCreateTag().putBoolean("ChangedAfterRead", true);
        assertFalse(MaidWeaponCaseItem.getStoredStack(holder).getOrCreateTag().getBoolean("ChangedAfterRead"));

        MaidWeaponCaseItem.setStoredStack(holder, ItemStack.EMPTY);
        assertTrue(MaidWeaponCaseItem.getStoredStack(holder).isEmpty());
    }

    @Test
    void arbitraryNonCaseStacksAreAccepted() {
        assertTrue(MaidWeaponCaseItem.canStore(new ItemStack(Items.BREAD)));
        assertEquals(64, new ItemStack(Items.BREAD).getMaxStackSize());
    }
}
