package com.murimrpg.event;

import com.murimrpg.GanghoContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

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
                            context.characters().ensureDraft(uuid).whenComplete((draft, draftError) -> server.executeIfPossible(() ->
                                    player.sendSystemMessage(Component.literal(draftError == null
                                            ? "[강호] 운명을 정하지 않았습니다. /강호 캐릭터 보기"
                                            : "[강호] 운명 후보 생성에 실패했습니다."))));
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
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !hasRole(entity.entityTags(), "training_dummy")) return InteractionResult.PASS;
            if (!ownedBy(entity.entityTags(), serverPlayer)) return InteractionResult.FAIL;
            context.huashan().recordDummyHit(serverPlayer.getUUID(), context.catalog().huashanSettings().trainingDummyId())
                    .whenComplete((result, error) -> serverPlayer.level().getServer().executeIfPossible(() -> {
                        if (error != null) serverPlayer.sendSystemMessage(Component.literal("[화산파] " + root(error).getMessage()));
                        else serverPlayer.sendSystemMessage(Component.literal("[화산파] " + result.message()));
                    }));
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damage, base, taken, blocked) -> {
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

    private static boolean hasRole(java.util.Set<String> tags, String role) { return tags.contains("murimrpg." + role); }
    private static boolean ownedBy(java.util.Set<String> tags, ServerPlayer player) { return tags.contains("murimrpg.owner." + player.getUUID()); }

    private static Throwable root(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error;
    }
}
