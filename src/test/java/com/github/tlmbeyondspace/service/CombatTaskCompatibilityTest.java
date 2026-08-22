package com.github.tlmbeyondspace.service;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTaskCompatibilityTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void recognizesOnlyEpicFightMaidTaskId() {
        assertTrue(CombatTaskCompatibility.isEpicFightTaskId(
                CombatTaskCompatibility.EPIC_FIGHT_TASK));
        assertFalse(CombatTaskCompatibility.isEpicFightTaskId(
                new net.minecraft.resources.ResourceLocation("touhou_little_maid", "attack")));
    }

    @Test
    void invokesEpicFightTaskWeaponContractReflectively() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack bread = new ItemStack(Items.BREAD);
        EpicFightTaskFixture fixture = new EpicFightTaskFixture();

        assertTrue(CombatTaskCompatibility.invokeEpicWeaponCheck(fixture, sword));
        assertFalse(CombatTaskCompatibility.invokeEpicWeaponCheck(fixture, bread));
    }

    @Test
    void missingOrInvalidEpicFightContractFailsClosed() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

        assertFalse(CombatTaskCompatibility.invokeEpicWeaponCheck(new MissingContractFixture(), sword));
        assertFalse(CombatTaskCompatibility.invokeEpicWeaponCheck(new WrongReturnTypeFixture(), sword));
        assertFalse(CombatTaskCompatibility.invokeEpicWeaponCheck(new ThrowingContractFixture(), sword));
        assertFalse(CombatTaskCompatibility.invokeEpicWeaponCheck(new EpicFightTaskFixture(), ItemStack.EMPTY));
    }

    public static final class EpicFightTaskFixture {
        public boolean isWeaponCap(ItemStack stack) {
            return stack.is(Items.DIAMOND_SWORD);
        }
    }

    public static final class MissingContractFixture {
    }

    public static final class WrongReturnTypeFixture {
        public Component isWeaponCap(ItemStack stack) {
            return stack.getHoverName();
        }
    }

    public static final class ThrowingContractFixture {
        public boolean isWeaponCap(ItemStack stack) {
            throw new IllegalStateException("fixture failure");
        }
    }
}
