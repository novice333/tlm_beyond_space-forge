package com.github.tlmbeyondspace.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DistressSignalDataTest {
    @Test
    void nbtRoundTripPreservesOwnerAndPerMaidTask() {
        UUID owner = UUID.randomUUID();
        UUID maid = UUID.randomUUID();
        ResourceLocation task = new ResourceLocation("example", "magic_attack");
        DistressSignalData source = new DistressSignalData(owner,
                Map.of(maid, new DistressSignalData.Selection(true, task)), true);

        DistressSignalData loaded = DistressSignalData.load(source.save());

        assertEquals(owner, loaded.ownerId().orElseThrow());
        assertTrue(loaded.canUse(owner));
        assertFalse(loaded.canUse(UUID.randomUUID()));
        assertTrue(loaded.get(maid).enabled());
        assertEquals(task, loaded.get(maid).combatTask());
        assertTrue(loaded.recallMode());
    }

    @Test
    void selectionsPreserveVisibleRosterPriorityOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        LinkedHashMap<UUID, DistressSignalData.Selection> selections = new LinkedHashMap<>();
        selections.put(first, new DistressSignalData.Selection(true,
                new ResourceLocation("example", "first")));
        selections.put(second, new DistressSignalData.Selection(true,
                new ResourceLocation("example", "second")));

        DistressSignalData loaded = DistressSignalData.load(
                new DistressSignalData(null, selections).save());

        assertEquals(List.of(first, second), List.copyOf(loaded.selections().keySet()));
    }
}
