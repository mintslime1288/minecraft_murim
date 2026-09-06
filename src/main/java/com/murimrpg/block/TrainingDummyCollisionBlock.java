package com.murimrpg.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible, non-targetable physical volume matching the rendered training log. */
public final class TrainingDummyCollisionBlock extends Block {
    // Five vertical slices approximate the round 0.70-block log without oversized corners.
    private static final VoxelShape COLLISION = Shapes.or(
            Block.box(2.4, 0, 4.8, 4.0, 28.0, 11.2),
            Block.box(4.0, 0, 3.2, 5.6, 28.0, 12.8),
            Block.box(5.6, 0, 2.4, 10.4, 28.0, 13.6),
            Block.box(10.4, 0, 3.2, 12.0, 28.0, 12.8),
            Block.box(12.0, 0, 4.8, 13.6, 28.0, 11.2));

    public TrainingDummyCollisionBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Exposes immutable geometry for validation without constructing another registered block. */
    public static VoxelShape collisionShape() {
        return COLLISION;
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                       CollisionContext context) {
        return COLLISION;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) {
        return Shapes.empty();
    }

    @Override protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos,
                                                   CollisionContext context) {
        return Shapes.empty();
    }

    @Override protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
