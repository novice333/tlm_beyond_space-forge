package com.github.tlmbeyondspace.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromaidCompatTest {
    @Test
    void selfPreservationRequiresAllThreeConditions() {
        assertTrue(PromaidCompat.shouldPrioritizeSelfPreservation(true, true, true));
        assertFalse(PromaidCompat.shouldPrioritizeSelfPreservation(false, true, true));
        assertFalse(PromaidCompat.shouldPrioritizeSelfPreservation(true, false, true));
        assertFalse(PromaidCompat.shouldPrioritizeSelfPreservation(true, true, false));
    }
}
