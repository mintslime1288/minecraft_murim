package com.murimrpg.service;

import com.murimrpg.domain.PlayerJourney;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Paper-independent boundary for preparation eligibility, qi recovery and cancellation. */
public final class MeditationService {
    private final HuashanService huashan;

    public MeditationService(HuashanService huashan) {
        this.huashan = huashan;
    }

    public boolean canStart(PlayerJourney state) {
        return state.journey().activeActivityId().isPresent()
                || state.journey().activeSkillId().isPresent();
    }

    public CompletableFuture<HuashanResult> progressSecond(UUID uuid, long maxQi, long regeneration) {
        if (maxQi <= 0 || regeneration < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("잘못된 내공 회복 수치입니다."));
        }
        return huashan.meditateSecond(uuid, maxQi, regeneration);
    }

    public CompletableFuture<HuashanResult> cancel(UUID uuid) {
        return huashan.cancelContinuousMeditation(uuid);
    }
}
