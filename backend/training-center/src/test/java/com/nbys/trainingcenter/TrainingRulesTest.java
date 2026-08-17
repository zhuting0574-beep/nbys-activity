package com.nbys.trainingcenter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrainingRulesTest {
    @Test
    void targetCountDefaultsToSupportedRange() {
        assertEquals(1, TrainingRules.targetCount(0));
        assertEquals(1, TrainingRules.targetCount(1));
        assertEquals(2, TrainingRules.targetCount(2));
        assertEquals(3, TrainingRules.targetCount(4));
    }

    @Test
    void roomCapacityIsTenMembers() {
        assertFalse(TrainingRules.roomIsFull(9));
        assertTrue(TrainingRules.roomIsFull(10));
        assertTrue(TrainingRules.roomIsFull(11));
    }
}
