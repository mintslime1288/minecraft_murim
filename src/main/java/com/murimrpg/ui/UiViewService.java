package com.murimrpg.ui;

import com.murimrpg.domain.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Converts cached domain state into immutable UI views without persistence or platform access. */
public final class UiViewService {
    private final ContentCatalog catalog;
    private final CultivationProgression progression;
    public UiViewService(ContentCatalog catalog, CultivationProgression progression) { this.catalog = catalog; this.progression = progression; }

    public PlayerUiSnapshot snapshot(PlayerProfile profile, PlayerJourney journey, long cooldownTicks) {
        Optional<OrganizationDefinition> organization = journey.content().organizationId().flatMap(catalog.factions()::findOrganization);
        Optional<SkillDefinition> selected = journey.journey().activeSkillId().flatMap(catalog::findSkill);
        return new PlayerUiSnapshot(profile, journey, progression.stage(profile).maxQi(), organization,
                rankName(organization, journey.content().organizationRankId()), selected,
                status(profile, journey, selected, cooldownTicks), Math.max(0, cooldownTicks));
    }

    public Optional<QuestSidebarView> activeQuest(PlayerJourney journey) {
        Optional<String> active = journey.journey().activeActivityId();
        if (active.isEmpty()) return Optional.empty();
        QuestDefinition quest = catalog.quests().find(active.orElseThrow()).orElse(null);
        if (quest == null) return Optional.empty();
        List<String> lines = new ArrayList<>();
        lines.add(quest.displayName());
        int completed;
        int total;
        if (quest.objectiveType() == QuestObjectiveType.TRAINING) {
            int hits = Math.min(journey.journey().dummyHits(), quest.requiredHits());
            int meditation = Math.min(journey.journey().meditationSeconds(), quest.requiredMeditationSeconds());
            total = quest.requiredHits() + quest.requiredMeditationSeconds();
            completed = hits + meditation;
            if (quest.requiredHits() > 0) lines.add("표적 " + hits + "/" + quest.requiredHits());
            if (quest.requiredMeditationSeconds() > 0) lines.add("운기 " + meditation + "/" + quest.requiredMeditationSeconds() + "초");
        } else {
            total = 1; completed = 0; lines.add("대상: " + quest.targetId().orElse("없음"));
        }
        int percent = total == 0 ? 100 : completed * 100 / total;
        lines.add("진행도 " + percent + "%");
        return Optional.of(new QuestSidebarView("진행 중 퀘스트", lines, percent));
    }

    public Optional<QuestSidebarView> entranceTrial(PlayerJourney journey) {
        return journey.journey().activeActivityId().filter(catalog.huashanSettings().entranceTrialId()::equals)
                .flatMap(ignored -> activeQuest(journey));
    }

    public List<SkillDefinition> organizationSkills(String organizationId) {
        return catalog.skills().stream().filter(skill -> skill.organizationId().filter(organizationId::equals).isPresent()).toList();
    }

    private MartialArtStatus status(PlayerProfile profile, PlayerJourney journey, Optional<SkillDefinition> selected, long cooldownTicks) {
        if (journey.journey().activeSkillId().isEmpty() || selected.isEmpty()) return MartialArtStatus.LOCKED;
        SkillDefinition skill = selected.orElseThrow();
        if (!journey.content().learnedSkillIds().contains(skill.id())) return MartialArtStatus.LOCKED;
        boolean organization = skill.organizationId().isEmpty() || skill.organizationId().equals(journey.content().organizationId());
        boolean stage = progression.stage(profile).order() >= progression.stage(skill.requiredStageId()).order();
        if (!organization || !stage) return MartialArtStatus.CONDITION_UNMET;
        if (cooldownTicks > 0) return MartialArtStatus.COOLDOWN;
        if (journey.journey().currentQi() < skill.qiCost()) return MartialArtStatus.INSUFFICIENT_QI;
        return MartialArtStatus.READY;
    }

    private String rankName(Optional<OrganizationDefinition> organization, Optional<String> rankId) {
        if (rankId.isEmpty()) return "신분 없음";
        return organization.flatMap(OrganizationDefinition::rankSystemId).flatMap(catalog.factions()::findRankSystem)
                .map(RankSystemDefinition::ranks).stream().flatMap(List::stream)
                .filter(rank -> rank.id().equals(rankId.orElseThrow())).map(OrganizationRankDefinition::displayName)
                .findFirst().orElse(rankId.orElseThrow());
    }
}
