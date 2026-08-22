package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tlmbeyondspace.data.MaidRescueSessionData;
import com.github.tlmbeyondspace.data.RescueMode;
import com.github.tlmbeyondspace.data.RescueSessionKind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegularRescueSupportTest {
    @Test
    void onlyRegularBondFollowModeSkipsOriginReturn() {
        assertTrue(RegularRescueSupport.shouldStayNearOwnerAfterRescue(
                session(RescueSessionKind.REGULAR, RescueMode.BOND, false)));
        assertFalse(RegularRescueSupport.shouldStayNearOwnerAfterRescue(
                session(RescueSessionKind.REGULAR, RescueMode.BOND, true)));
        assertFalse(RegularRescueSupport.shouldStayNearOwnerAfterRescue(
                session(RescueSessionKind.REGULAR, RescueMode.SOLO, false)));
        assertFalse(RegularRescueSupport.shouldStayNearOwnerAfterRescue(
                session(RescueSessionKind.DISTRESS, RescueMode.BOND, false)));
    }

    private static MaidRescueSessionData.Data session(RescueSessionKind kind, RescueMode mode,
                                                      boolean sourceHomeMode) {
        return new MaidRescueSessionData.Data(true, kind, null, null, null, Vec3.ZERO,
                sourceHomeMode, MaidSchedule.ALL, 0, 0, mode);
    }
}
