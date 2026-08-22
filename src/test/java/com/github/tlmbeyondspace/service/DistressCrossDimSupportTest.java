package com.github.tlmbeyondspace.service;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistressCrossDimSupportTest {
    @Test
    void summonPointsUseAdditionalRingsAfterEightHelpers() {
        Vec3 center = new Vec3(10.0D, 64.0D, -3.0D);
        Vec3 first = DistressCrossDimSupport.summonPoint(center, 0, 20);
        Vec3 ninth = DistressCrossDimSupport.summonPoint(center, 8, 20);

        assertEquals(2.5D, horizontalDistance(center, first), 0.0001D);
        assertEquals(4.5D, horizontalDistance(center, ninth), 0.0001D);
        assertTrue(horizontalDistance(center, ninth) > horizontalDistance(center, first));
    }

    private static double horizontalDistance(Vec3 center, Vec3 point) {
        double x = point.x - center.x;
        double z = point.z - center.z;
        return Math.sqrt(x * x + z * z);
    }
}
