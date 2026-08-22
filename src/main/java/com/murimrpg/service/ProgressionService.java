package com.murimrpg.service;

import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.CultivationStageDefinition;
import com.murimrpg.domain.ExperienceChange;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.ProgressUpdate;
import com.murimrpg.domain.PromotionRequirement;
import com.murimrpg.domain.PromotionResult;
import com.murimrpg.domain.RealmProgressView;
import com.murimrpg.domain.RequirementChange;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ProgressionService {
    private final PlayerProfileService profileService;
    private final CultivationProgression progression;
    private final Set<UUID> promotionsInProgress = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> lastPromotionRequestTokens = new ConcurrentHashMap<>();

    public ProgressionService(PlayerProfileService profileService, CultivationProgression progression) {
        this.profileService = profileService;
        this.progression = progression;
    }

    public CompletableFuture<RealmProgressView> realm(UUID uuid) {
        return profileService.loadOrCreate(uuid).thenApply(this::realmView);
    }

    public CompletableFuture<PromotionResult> promote(UUID uuid) {
        if (!promotionsInProgress.add(uuid)) {
            return CompletableFuture.completedFuture(PromotionResult.inProgress());
        }
        CompletableFuture<PromotionResult> future = profileService.mutate(uuid, this::promoteAtomically);
        return future.whenComplete((result, error) -> promotionsInProgress.remove(uuid));
    }

    public CompletableFuture<PromotionResult> promote(UUID uuid, long requestToken) {
        Long previousToken = lastPromotionRequestTokens.put(uuid, requestToken);
        if (previousToken != null && previousToken == requestToken) {
            return CompletableFuture.completedFuture(PromotionResult.inProgress());
        }
        return promote(uuid);
    }

    public void forgetPromotionRequest(UUID uuid) {
        lastPromotionRequestTokens.remove(uuid);
    }

    public CompletableFuture<ExperienceChange> addExp(UUID uuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("추가 EXP는 양수여야 합니다."));
        }
        return profileService.mutate(uuid, (profile, now) -> {
            long after = Math.addExact(profile.cultivationExp(), amount);
            PlayerProfile updated = profile.withCultivationState(
                    profile.majorRealm(),
                    profile.phase(),
                    after,
                    profile.completedRequirements(),
                    profile.unlockedContent(),
                    now
            );
            return ProfileMutation.saved(updated, new ExperienceChange(profile.cultivationExp(), after, updated));
        });
    }

    public CompletableFuture<ExperienceChange> setExp(UUID uuid, long amount) {
        if (amount < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("수련 EXP는 음수일 수 없습니다."));
        }
        return profileService.mutate(uuid, (profile, now) -> {
            PlayerProfile updated = profile.withCultivationState(
                    profile.majorRealm(),
                    profile.phase(),
                    amount,
                    profile.completedRequirements(),
                    profile.unlockedContent(),
                    now
            );
            return ProfileMutation.saved(updated, new ExperienceChange(profile.cultivationExp(), amount, updated));
        });
    }

    public CompletableFuture<RequirementChange> grantRequirement(UUID uuid, String requirementId) {
        validateRequirement(requirementId);
        return profileService.mutate(uuid, (profile, now) -> changeRequirement(
                profile,
                now,
                requirementId,
                true
        ));
    }

    public CompletableFuture<RequirementChange> revokeRequirement(UUID uuid, String requirementId) {
        validateRequirement(requirementId);
        return profileService.mutate(uuid, (profile, now) -> changeRequirement(
                profile,
                now,
                requirementId,
                false
        ));
    }

    public CompletableFuture<PlayerProfile> prepareTestProgress(UUID uuid) {
        return profileService.mutate(uuid, (profile, now) -> {
            CultivationStageDefinition first = progression.firstStage();
            PlayerProfile prepared = profile.withCultivationState(
                    first.majorRealm(),
                    first.phase(),
                    progression.finalStage().requiredCultivationExp(),
                    progression.requirementIds(),
                    first.unlocks(),
                    now
            );
            return ProfileMutation.saved(prepared, prepared);
        });
    }

    public CompletableFuture<PlayerProfile> clearTestProgress(UUID uuid) {
        return profileService.mutate(uuid, (profile, now) -> {
            CultivationStageDefinition first = progression.firstStage();
            PlayerProfile cleared = profile.withCultivationState(
                    first.majorRealm(),
                    first.phase(),
                    0,
                    Set.of(),
                    first.unlocks(),
                    now
            );
            return ProfileMutation.saved(cleared, cleared);
        });
    }

    public CompletableFuture<PlayerProfile> setProgressForTesting(UUID uuid, ProgressUpdate progressUpdate) {
        return profileService.mutate(uuid, (profile, now) -> {
            CultivationStageDefinition stage = progression.stage(
                    progressUpdate.majorRealm(),
                    progressUpdate.phase()
            );
            PlayerProfile updated = profile.withCultivationState(
                    stage.majorRealm(),
                    stage.phase(),
                    progressUpdate.cultivationExp(),
                    profile.completedRequirements(),
                    progression.unlocksThrough(stage),
                    now
            );
            return ProfileMutation.saved(updated, updated);
        });
    }

    public CultivationProgression progression() {
        return progression;
    }

    private ProfileMutation<PromotionResult> promoteAtomically(PlayerProfile profile, Instant now) {
        CultivationStageDefinition current = progression.stage(profile);
        var next = progression.next(current);
        if (next.isEmpty()) {
            return ProfileMutation.unchanged(profile, PromotionResult.maximum(profile, current));
        }

        CultivationStageDefinition target = next.orElseThrow();
        long missingExp = Math.max(0, target.requiredCultivationExp() - profile.cultivationExp());
        Set<String> missingRequirements = target.promotionRequirements().stream()
                .map(PromotionRequirement::id)
                .filter(id -> !profile.completedRequirements().contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (missingExp > 0 || !missingRequirements.isEmpty()) {
            return ProfileMutation.unchanged(
                    profile,
                    PromotionResult.blocked(profile, current, target, missingExp, missingRequirements)
            );
        }

        Set<String> unlocked = new LinkedHashSet<>(profile.unlockedContent());
        Set<String> newlyUnlocked = new LinkedHashSet<>(target.unlocks());
        newlyUnlocked.removeAll(unlocked);
        unlocked.addAll(target.unlocks());
        PlayerProfile promoted = profile.withCultivationState(
                target.majorRealm(),
                target.phase(),
                profile.cultivationExp(),
                profile.completedRequirements(),
                unlocked,
                now
        );
        return ProfileMutation.saved(
                promoted,
                PromotionResult.success(promoted, current, target, newlyUnlocked)
        );
    }

    private RealmProgressView realmView(PlayerProfile profile) {
        CultivationStageDefinition current = progression.stage(profile);
        var next = progression.next(current);
        if (next.isEmpty()) {
            return new RealmProgressView(profile, current, next, 0, Set.of(), Set.of());
        }
        CultivationStageDefinition target = next.orElseThrow();
        Set<String> requirements = target.promotionRequirements().stream()
                .map(PromotionRequirement::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> missing = requirements.stream()
                .filter(id -> !profile.completedRequirements().contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RealmProgressView(
                profile,
                current,
                next,
                Math.max(0, target.requiredCultivationExp() - profile.cultivationExp()),
                requirements,
                missing
        );
    }

    private ProfileMutation<RequirementChange> changeRequirement(
            PlayerProfile profile,
            Instant now,
            String requirementId,
            boolean grant
    ) {
        Set<String> requirements = new LinkedHashSet<>(profile.completedRequirements());
        boolean changed = grant ? requirements.add(requirementId) : requirements.remove(requirementId);
        if (!changed) {
            return ProfileMutation.unchanged(
                    profile,
                    new RequirementChange(requirementId, grant, false, profile)
            );
        }
        PlayerProfile updated = profile.withCultivationState(
                profile.majorRealm(),
                profile.phase(),
                profile.cultivationExp(),
                requirements,
                profile.unlockedContent(),
                now
        );
        return ProfileMutation.saved(
                updated,
                new RequirementChange(requirementId, grant, true, updated)
        );
    }

    private void validateRequirement(String requirementId) {
        if (!progression.requirementIds().contains(requirementId)) {
            throw new IllegalArgumentException("지원하지 않는 승급 조건 ID입니다: " + requirementId);
        }
    }
}
