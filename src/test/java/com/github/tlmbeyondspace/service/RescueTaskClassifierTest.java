package com.github.tlmbeyondspace.service;

import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedAnimal;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueTaskClassifierTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void vanillaBreedingIsASourceTaskDespiteAttackInterface() {
        TaskFeedAnimal breeding = new TaskFeedAnimal();

        assertFalse(RescueTaskClassifier.isCombatTask(breeding));
        assertTrue(RescueTaskClassifier.isSourceTask(breeding));
    }

    @Test
    void nativeAttackRemainsACombatTask() {
        assertTrue(RescueTaskClassifier.isCombatTask(new TaskAttack()));
    }

    @Test
    void maidSoulKitchenBreedingIdIsAlsoHybrid() {
        assertTrue(RescueTaskClassifier.isHybridSourceTaskId(
                new ResourceLocation("maidsoulkitchen", "feed_animal_t")));
        assertFalse(RescueTaskClassifier.isHybridSourceTaskId(
                new ResourceLocation("maidspell", "spell_combat")));
    }
}
