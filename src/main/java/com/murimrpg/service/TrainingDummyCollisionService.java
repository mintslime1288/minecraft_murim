package com.murimrpg.service;

import com.murimrpg.block.MurimBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Owns the invisible physical block paired with each installed training log. */
public final class TrainingDummyCollisionService {
    private static final String TRAINING_DUMMY_TAG = "murimrpg.training_dummy";

    public void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (isTrainingDummy(entity)) ensureCollision(level, entity.blockPosition());
        });
    }

    public boolean canPlace(Level level, BlockPos pos) {
        return level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above());
    }

    public boolean install(Level level, BlockPos pos) {
        return canPlace(level, pos) && level.setBlock(pos,
                MurimBlocks.TRAINING_DUMMY_COLLISION.defaultBlockState(), Block.UPDATE_ALL);
    }

    public void remove(Level level, Entity dummy) {
        BlockPos pos = dummy.blockPosition();
        if (level.getBlockState(pos).is(MurimBlocks.TRAINING_DUMMY_COLLISION)) {
            level.removeBlock(pos, false);
        }
    }

    private void ensureCollision(Level level, BlockPos pos) {
        if (level.isEmptyBlock(pos)) {
            level.setBlock(pos, MurimBlocks.TRAINING_DUMMY_COLLISION.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean isTrainingDummy(Entity entity) {
        return entity instanceof ArmorStand && entity.entityTags().contains(TRAINING_DUMMY_TAG);
    }
}
