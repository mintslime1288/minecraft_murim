package com.murimrpg.service;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.OrganizationDefinition;
import com.murimrpg.domain.PlayerContentState;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.PlayerJourneyState;
import com.murimrpg.domain.ProgressUpdate;
import com.murimrpg.domain.RealmPhase;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Application service for explicitly authorized staff and development state changes. */
public final class GanghoAdministrationService {
    private final ContentCatalog catalog;
    private final PlayerContentService content;
    private final PlayerProfileService profiles;

    public GanghoAdministrationService(ContentCatalog catalog, PlayerContentService content,
            PlayerProfileService profiles) {
        this.catalog = catalog; this.content = content; this.profiles = profiles;
    }

    public CompletableFuture<PlayerJourney> joinOrganization(UUID uuid, String organizationId) {
        OrganizationDefinition organization = organization(organizationId);
        return content.mutate(uuid, state -> new PlayerJourney(new PlayerContentState(uuid,
                Optional.of(organization.id()), Optional.empty(), 0, state.content().learnedSkillIds()), state.journey()));
    }

    public CompletableFuture<PlayerJourney> leaveOrganization(UUID uuid) {
        return content.mutate(uuid, state -> new PlayerJourney(PlayerContentState.empty(uuid),
                PlayerJourneyState.empty(uuid)));
    }

    public CompletableFuture<PlayerJourney> setRank(UUID uuid, String rankId) {
        return content.mutate(uuid, state -> {
            String organizationId = state.content().organizationId()
                    .orElseThrow(() -> new IllegalStateException("대상이 소속된 문파가 없습니다."));
            OrganizationDefinition organization = organization(organizationId);
            boolean valid = organization.rankSystemId().flatMap(catalog.factions()::findRankSystem)
                    .stream().flatMap(system -> system.ranks().stream()).anyMatch(rank -> rank.id().equals(rankId));
            if (!valid) throw new IllegalArgumentException("해당 문파에서 사용할 수 없는 계급입니다: " + rankId);
            return new PlayerJourney(new PlayerContentState(uuid, Optional.of(organizationId), Optional.of(rankId),
                    state.content().organizationContribution(), state.content().learnedSkillIds()), state.journey());
        });
    }

    public CompletableFuture<PlayerJourney> setContribution(UUID uuid, long value) {
        if (value < 0) return CompletableFuture.failedFuture(new IllegalArgumentException("공헌도는 음수일 수 없습니다."));
        return content.mutate(uuid, state -> new PlayerJourney(new PlayerContentState(uuid,
                state.content().organizationId(), state.content().organizationRankId(), value,
                state.content().learnedSkillIds()), state.journey()));
    }

    public CompletableFuture<PlayerJourney> grantSkill(UUID uuid, String skillId) { return changeSkill(uuid, skillId, true); }
    public CompletableFuture<PlayerJourney> revokeSkill(UUID uuid, String skillId) { return changeSkill(uuid, skillId, false); }

    public CompletableFuture<PlayerJourney> startQuest(UUID uuid, String questId) {
        catalog.quests().require(questId);
        return content.mutate(uuid, state -> new PlayerJourney(state.content(), new PlayerJourneyState(uuid,
                Optional.of(questId), 0, 0, state.journey().activeSkillId(), state.journey().currentQi(),
                state.journey().completedActivityIds())));
    }

    public CompletableFuture<PlayerJourney> resetQuest(UUID uuid, String questId) {
        catalog.quests().require(questId);
        return content.mutate(uuid, state -> {
            var completed = new HashSet<>(state.journey().completedActivityIds()); completed.remove(questId);
            Optional<String> active = state.journey().activeActivityId().filter(id -> !id.equals(questId));
            return new PlayerJourney(state.content(), new PlayerJourneyState(uuid, active, 0, 0,
                    state.journey().activeSkillId(), state.journey().currentQi(), completed));
        });
    }

    public CompletableFuture<?> setRealm(UUID uuid, MajorRealm realm, RealmPhase phase) {
        return profiles.loadOrCreate(uuid).thenCompose(profile -> profiles.setProgress(uuid,
                new ProgressUpdate(realm, phase, profile.cultivationExp())));
    }

    public CompletableFuture<?> reset(UUID uuid) {
        return CompletableFuture.allOf(profiles.resetAll(uuid), leaveOrganization(uuid));
    }

    private CompletableFuture<PlayerJourney> changeSkill(UUID uuid, String skillId, boolean grant) {
        catalog.findSkill(skillId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 무공입니다: " + skillId));
        return content.mutate(uuid, state -> {
            var skills = new HashSet<>(state.content().learnedSkillIds());
            if (grant) skills.add(skillId); else skills.remove(skillId);
            Optional<String> active = state.journey().activeSkillId().filter(id -> !id.equals(skillId) || grant);
            return new PlayerJourney(new PlayerContentState(uuid, state.content().organizationId(),
                    state.content().organizationRankId(), state.content().organizationContribution(), skills),
                    new PlayerJourneyState(uuid, state.journey().activeActivityId(), state.journey().dummyHits(),
                            state.journey().meditationSeconds(), active, state.journey().currentQi(),
                            state.journey().completedActivityIds()));
        });
    }

    private OrganizationDefinition organization(String id) {
        return catalog.factions().findOrganization(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문파입니다: " + id));
    }
}
