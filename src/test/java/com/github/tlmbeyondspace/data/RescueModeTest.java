package com.github.tlmbeyondspace.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RescueModeTest {
    @Test
    void cyclesThroughAllThreeModes() {
        assertEquals(RescueMode.SOLO, RescueMode.FORBIDDEN.next());
        assertEquals(RescueMode.BOND, RescueMode.SOLO.next());
        assertEquals(RescueMode.FORBIDDEN, RescueMode.BOND.next());
        assertEquals(RescueMode.BOND, RescueMode.FORBIDDEN.previous());
        assertEquals(RescueMode.SOLO, RescueMode.BOND.previous());
        assertEquals(RescueMode.FORBIDDEN, RescueMode.SOLO.previous());
    }
}
