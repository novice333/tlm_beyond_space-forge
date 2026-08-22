package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidRescueSessionDataTest {
    @Test
    void sessionRoundTripPreservesRestorationState() {
        MaidRescueSessionData.Data source = new MaidRescueSessionData.Data(true,
                RescueSessionKind.DISTRESS, new ResourceLocation("example", "mining"),
                new ResourceLocation("example", "spell_attack"), new ResourceLocation("minecraft", "overworld"),
                new Vec3(12.5, 64, -3.25), true, MaidSchedule.NIGHT, 1234L, 17, RescueMode.BOND,
                true, true);

        MaidRescueSessionData.Data loaded = MaidRescueSessionData.Data.load(source.save());

        assertTrue(loaded.active());
        assertEquals(source.kind(), loaded.kind());
        assertEquals(source.sourceTask(), loaded.sourceTask());
        assertEquals(source.combatTask(), loaded.combatTask());
        assertEquals(source.originDimension(), loaded.originDimension());
        assertEquals(source.origin(), loaded.origin());
        assertEquals(source.sourceHomeMode(), loaded.sourceHomeMode());
        assertEquals(source.sourceSchedule(), loaded.sourceSchedule());
        assertEquals(source.startedAt(), loaded.startedAt());
        assertEquals(source.quietTicks(), loaded.quietTicks());
        assertEquals(source.triggerMode(), loaded.triggerMode());
        assertTrue(loaded.sittingCaptured());
        assertTrue(loaded.sourceSitting());
    }
}
