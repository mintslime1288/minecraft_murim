package com.murimrpg.event;

import com.murimrpg.GanghoContext;
import com.murimrpg.network.MurimRpgNetworking;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.BlockHitResult;

public final class EventRegistry {
    private final GanghoContext context;
    private final Logger logger;

    public EventRegistry(GanghoContext context, Logger logger) {
        this.context = context;
        this.logger = logger;
    }

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            MurimRpgNetworking.sendServerHello(player);
            var uuid = player.getUUID();
            context.profiles().beginSession(uuid);
            context.profiles().loadOrCreate(uuid).thenCombine(context.content().loadOrCreate(uuid), (profile, journey) -> profile)
                    .whenComplete((profile, error) -> server.executeIfPossible(() -> {
                        if (error != null) {
                            logger.log(Level.SEVERE, "Failed to load player data for " + uuid, root(error));
                            player.sendSystemMessage(Component.literal("[강호] 데이터를 불러오지 못했습니다. 관리자에게 문의하세요."));
                            return;
                        }
                        player.sendSystemMessage(Component.literal("[강호] " + profile.realmDisplayName() + " · /강호 도움말"));
                        if (!profile.characterData().completed()) {
                            context.characters().ensureDraft(uuid).whenComplete((draft, draftError) -> server.executeIfPossible(() -> {
                                if (draftError != null) player.sendSystemMessage(Component.literal("[강호] 운명 후보 생성에 실패했습니다."));
                                else context.ui().openCharacterCreation(player, draft);
                            }));
                        }
                    }));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var uuid = handler.player.getUUID();
            context.ui().clear(handler.player);
            context.meditation().stop(uuid);
            context.progressionService().forgetPromotionRequest(uuid);
            context.profiles().saveAndUnload(uuid).exceptionally(error -> { logger.log(Level.SEVERE, "Profile unload failed", root(error)); return null; });
            context.content().saveAndUnload(uuid).exceptionally(error -> { logger.log(Level.SEVERE, "Content unload failed", root(error)); return null; });
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> placeDummy(player, level, hand, hit));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> recoverDummy(player, level, hand, entity));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !hasRole(entity.entityTags(), "training_dummy")) return InteractionResult.PASS;
            if (!context.trainingItems().is(serverPlayer.getItemInHand(hand), com.murimrpg.service.TrainingItemService.WOODEN_SWORD)) {
                serverPlayer.sendSystemMessage(Component.literal("[훈련 허수아비] 목검을 들어야 수련 EXP를 받을 수 있습니다.")); return InteractionResult.FAIL;
            }
            context.progressionService().addExp(serverPlayer.getUUID(), 2).whenComplete((result, error) -> serverPlayer.level().getServer().executeIfPossible(() ->
                    serverPlayer.sendSystemMessage(Component.literal(error == null ? "[훈련 허수아비] 수련 EXP 2를 얻었습니다." : "[훈련 허수아비] EXP 지급에 실패했습니다."))));
            context.visualEffects().play(serverPlayer, "WOODEN_SWORD_SWING", serverPlayer);
            context.visualEffects().play(serverPlayer, "TRAINING_DUMMY_HIT", entity);
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damage, base, taken, blocked) -> {
            if (damage.getEntity() instanceof ServerPlayer player) {
                var held = player.getMainHandItem();
                if (held.isEmpty()) context.visualEffects().play(player, "FIST_IMPACT", entity);
                else if (held.getItem().builtInRegistryHolder().is(ItemTags.SWORDS)
                        && !context.trainingItems().is(held, com.murimrpg.service.TrainingItemService.WOODEN_SWORD)) {
                    context.visualEffects().play(player, "BASIC_SWORD_HIT", entity);
                }
            }
            if (!hasRole(entity.entityTags(), "examiner") || !(damage.getEntity() instanceof ServerPlayer player)
                    || !ownedBy(entity.entityTags(), player)) return;
            double remaining = Math.max(0, entity.getHealth()) * 100.0 / entity.getMaxHealth();
            context.huashan().recordExaminerHealth(player.getUUID(), context.catalog().huashanSettings().examinerId(), remaining)
                    .whenComplete((result, error) -> player.level().getServer().executeIfPossible(() -> {
                        if (error == null && result.activityCompleted()) entity.discard();
                        player.sendSystemMessage(Component.literal(error == null ? "[화산파] " + result.message() : "[화산파] " + root(error).getMessage()));
                    }));
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
            if (!hasRole(entity.entityTags(), "bandit_leader") || !(damage.getEntity() instanceof ServerPlayer player)
                    || !ownedBy(entity.entityTags(), player)) return;
            context.huashan().recordMonsterKill(player.getUUID(), "BANDIT_LEADER")
                    .whenComplete((result, error) -> player.level().getServer().executeIfPossible(() ->
                            player.sendSystemMessage(Component.literal(error == null ? "[화산파] " + result.message() : "[화산파] " + root(error).getMessage()))));
        });
    }

    private InteractionResult placeDummy(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide() || !context.trainingItems().is(player.getItemInHand(hand), com.murimrpg.service.TrainingItemService.TRAINING_DUMMY)) return InteractionResult.PASS;
        var pos = hit.getBlockPos().relative(hit.getDirection());
        if (!context.dummyCollision().install(level, pos)) { player.sendSystemMessage(Component.literal("[훈련 허수아비] 높이 2칸의 빈 공간이 필요합니다.")); return InteractionResult.FAIL; }
        ArmorStand stand = new ArmorStand(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        stand.setInvisible(true); stand.setNoBasePlate(true); stand.setNoGravity(true); stand.addTag("murimrpg.training_dummy"); stand.addTag("murimrpg.owner." + player.getUUID());
        stand.setItemSlot(EquipmentSlot.HEAD, context.trainingItems().displayItem()); level.addFreshEntity(stand);
        if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
        player.sendSystemMessage(Component.literal("[훈련 허수아비] 통나무 수련대를 설치했습니다. 목검으로 공격하세요.")); return InteractionResult.SUCCESS_SERVER;
    }
    private InteractionResult recoverDummy(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand, net.minecraft.world.entity.Entity entity) {
        if (!(player instanceof ServerPlayer serverPlayer) || !hasRole(entity.entityTags(), "training_dummy")) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) { player.sendSystemMessage(Component.literal("[훈련 허수아비] 웅크린 상태로 우클릭하면 회수합니다.")); return InteractionResult.SUCCESS_SERVER; }
        if (!ownedBy(entity.entityTags(), serverPlayer) && !serverPlayer.level().getServer().getPlayerList().isOp(serverPlayer.nameAndId())) { player.sendSystemMessage(Component.literal("[훈련 허수아비] 설치자 또는 OP만 회수할 수 있습니다.")); return InteractionResult.FAIL; }
        context.dummyCollision().remove(level, entity); entity.discard(); if (!context.trainingItems().give(serverPlayer, com.murimrpg.service.TrainingItemService.TRAINING_DUMMY, 1)) serverPlayer.drop(context.trainingItems().create(com.murimrpg.service.TrainingItemService.TRAINING_DUMMY, 1), false);
        player.sendSystemMessage(Component.literal("[훈련 허수아비] 회수했습니다.")); return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean hasRole(java.util.Set<String> tags, String role) { return tags.contains("murimrpg." + role); }
    private static boolean ownedBy(java.util.Set<String> tags, ServerPlayer player) { return tags.contains("murimrpg.owner." + player.getUUID()); }

    private static Throwable root(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error;
    }
}
