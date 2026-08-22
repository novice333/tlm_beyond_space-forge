package com.github.tlmbeyondspace.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidCombatPreferenceDataTest {
    @Test
    void configuredPreferenceRoundTrips() {
        ResourceLocation epicFight = new ResourceLocation("ef_tlm", "fight_mode_task");
        MaidCombatPreferenceData.Data source = new MaidCombatPreferenceData.Data(true, epicFight);

        MaidCombatPreferenceData.Data loaded = MaidCombatPreferenceData.Data.load(source.save());

        assertTrue(loaded.configured());
        assertEquals(epicFight, loaded.combatTask());
    }

    @Test
    void malformedPreferenceFallsBackToNativeAttack() {
        CompoundTag malformed = new CompoundTag();
        malformed.putBoolean("Configured", true);
        malformed.putString("CombatTask", "not a resource location");

        MaidCombatPreferenceData.Data loaded = MaidCombatPreferenceData.Data.load(malformed);

        assertTrue(loaded.configured());
        assertEquals(TaskModeProfile.DEFAULT_COMBAT_TASK, loaded.combatTask());
    }

    @Test
    void unsetPreferenceIsNotConfigured() {
        assertFalse(MaidCombatPreferenceData.Data.unset().configured());
    }
}
