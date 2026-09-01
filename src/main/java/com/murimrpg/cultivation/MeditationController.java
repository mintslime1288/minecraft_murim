package com.murimrpg.cultivation;

import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.HuashanSettings;
import com.murimrpg.scheduler.ServerTickScheduler;
import com.murimrpg.service.MeditationService;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Crouch-and-hold meditation loop; only active sessions are scheduled. */
public final class MeditationController {
    private static final int PERIOD = 5;
    private final PlayerContentService content;
    private final PlayerProfileService profiles;
    private final CultivationProgression progression;
    private final MeditationService meditation;
    private final HuashanSettings settings;
    private final ServerTickScheduler scheduler;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public MeditationController(PlayerContentService content, PlayerProfileService profiles,
            CultivationProgression progression, MeditationService meditation, HuashanSettings settings,
            ServerTickScheduler scheduler) {
        this.content = content; this.profiles = profiles; this.progression = progression;
        this.meditation = meditation; this.settings = settings; this.scheduler = scheduler;
    }

    public String start(ServerPlayer player) {
        if (!player.isShiftKeyDown() || !player.onGround()) return "웅크린 채 지면에서 다시 실행하세요.";
        var journey = content.cached(player.getUUID()).orElseThrow(() -> new IllegalStateException("플레이어 데이터 로딩 중입니다."));
        if (!meditation.canStart(journey)) return "활성 심법이나 진행 중인 수련이 없습니다.";
        if (sessions.putIfAbsent(player.getUUID(), new Session(player.position())) == null) schedule(player);
        return "운기조식을 시작합니다. 움직이거나 웅크리기를 풀면 취소됩니다.";
    }

    public void stop(UUID uuid) {
        if (sessions.remove(uuid) != null) meditation.cancel(uuid);
    }

    private void schedule(ServerPlayer player) {
        scheduler.schedule(player.level().getServer(), PERIOD, () -> tick(player));
    }

    private void tick(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session == null) return;
        if (player.hasDisconnected() || !player.isShiftKeyDown() || !player.onGround()
                || player.position().distanceToSqr(session.origin) > 0.000001) {
            stop(player.getUUID());
            return;
        }
        session.ticks += PERIOD;
        int preparation = settings.meditationPreparationSeconds() * 20;
        if (session.ticks < preparation) {
            player.sendOverlayMessage(Component.literal("운기조식 준비 " + ((preparation - session.ticks + 19) / 20) + "초"));
            schedule(player);
            return;
        }
        var journey = content.cached(player.getUUID()).orElse(null);
        if (journey != null && journey.journey().activeSkillId().filter("PURPLE_MIST_DIVINE_ART"::equals).isPresent()) {
            session.particleTicks += PERIOD;
            if (session.particleTicks >= settings.particleIntervalTicks()) {
                session.particleTicks %= settings.particleIntervalTicks();
                player.level().sendParticles(new DustColorTransitionOptions(0xFF80B5, 0xD64D9D, 0.9f),
                        player.getX(), player.getY() + 1, player.getZ(), settings.particleCount(), .6, .8, .6, .01);
            }
        }
        if ((session.ticks - preparation) % 20 == 0) {
            var profile = profiles.cachedProfile(player.getUUID()).orElse(null);
            if (profile != null) {
                var stage = progression.stage(profile);
                meditation.progressSecond(player.getUUID(), stage.maxQi(), stage.qiRegeneration())
                        .whenComplete((result, error) -> player.level().getServer().executeIfPossible(() -> {
                            if (error != null) { player.sendSystemMessage(Component.literal("[운기조식] " + root(error).getMessage())); stop(player.getUUID()); }
                            else player.sendOverlayMessage(Component.literal("운기조식 · 내공 " + result.state().journey().currentQi() + "/" + stage.maxQi()));
                        }));
            }
        }
        schedule(player);
    }

    private static Throwable root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error; }
    private static final class Session {
        private final Vec3 origin; private int ticks; private int particleTicks;
        private Session(Vec3 origin) { this.origin = origin; }
    }
}
