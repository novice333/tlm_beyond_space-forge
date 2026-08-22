package com.github.tlmbeyondspace.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskModeProfileTest {
    @Test
    void globalCombatTaskUsesStringNbtAndRoundTrips() {
        ResourceLocation configured = new ResourceLocation("third_party", "magic_attack");
        TaskModeProfile source = new TaskModeProfile();
        source.setCombatTask(configured);

        CompoundTag saved = source.save();
        TaskModeProfile loaded = TaskModeProfile.load(saved);

        assertTrue(saved.contains("CombatTask", Tag.TAG_STRING));
        assertEquals(configured, loaded.getCombatTask());
        assertEquals(configured, loaded.copy().getCombatTask());
    }

    @Test
    void missingCombatTaskFallsBackToNativeAttack() {
        assertEquals(TaskModeProfile.DEFAULT_COMBAT_TASK,
                TaskModeProfile.load(new CompoundTag()).getCombatTask());
    }
}
