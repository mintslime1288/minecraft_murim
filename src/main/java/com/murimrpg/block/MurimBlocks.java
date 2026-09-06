package com.murimrpg.block;

import com.murimrpg.GanghoImunrok;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Common block registration; both the dedicated server and Fabric client need the same ID. */
public final class MurimBlocks {
    public static final Identifier TRAINING_DUMMY_COLLISION_ID = Identifier.fromNamespaceAndPath(
            GanghoImunrok.MOD_ID, "training_dummy_collision");
    public static final ResourceKey<Block> TRAINING_DUMMY_COLLISION_KEY = ResourceKey.create(
            Registries.BLOCK, TRAINING_DUMMY_COLLISION_ID);
    public static final Block TRAINING_DUMMY_COLLISION = Registry.register(BuiltInRegistries.BLOCK,
            TRAINING_DUMMY_COLLISION_KEY, new TrainingDummyCollisionBlock(BlockBehaviour.Properties.of()
                    .setId(TRAINING_DUMMY_COLLISION_KEY)
                    .strength(-1.0f, 3_600_000.0f)
                    .noOcclusion()
                    .noTerrainParticles()
                    .noLootTable()));

    private MurimBlocks() { }

    public static void register() {
        // Accessing this class initializes the static registry entry exactly once.
    }
}
