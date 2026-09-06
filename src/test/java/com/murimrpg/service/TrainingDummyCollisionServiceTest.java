package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.block.TrainingDummyCollisionBlock;
import org.junit.jupiter.api.Test;

class TrainingDummyCollisionServiceTest {
    @Test
    void collisionShapeIsClosedAndMatchesTheVisibleLog() {
        var shape = TrainingDummyCollisionBlock.collisionShape();
        assertFalse(shape.isEmpty());
        assertTrue(shape.max(net.minecraft.core.Direction.Axis.Y) > 1.7);
        assertTrue(shape.min(net.minecraft.core.Direction.Axis.X) >= .14);
        assertTrue(shape.max(net.minecraft.core.Direction.Axis.X) <= .86);
    }
}
