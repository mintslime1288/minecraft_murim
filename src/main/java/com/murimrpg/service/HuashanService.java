package com.murimrpg.service;

import com.murimrpg.domain.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class HuashanService {
    public static final String THIRD = "THIRD_GENERATION_DISCIPLE";
    public static final String SECOND = "SECOND_GENERATION_DISCIPLE";
    public static final String FIRST = "FIRST_GENERATION_DISCIPLE";
    private final PlayerContentService states;
    private final ContentCatalog catalog;

    public HuashanService(PlayerContentService states, ContentCatalog catalog) {
        this.states = states; this.catalog = catalog;
    }

    public double examinerPassThresholdPercent() {
        return catalog.quests().require(catalog.huashanSettings().secondPromotionTrialId()).healthThresholdPercent();
    }

    public CompletableFuture<HuashanResult> apply(UUID uuid, String organizationId) {
        return update(uuid, current -> {
            HuashanSettings settings = catalog.huashanSettings();
            if (!settings.organizationId().equals(organizationId)) throw new IllegalArgumentException("지원하지 않는 입문 조직입니다: " + organizationId);
            if (current.content().organizationId().isPresent()) throw new IllegalStateException("이미 조직에 소속되어 있습니다.");
            if (current.journey().activeActivityId().isPresent()) throw new IllegalStateException("이미 진행 중인 시험 또는 퀘스트가 있습니다.");
            return withActivity(current, settings.entranceTrialId());
        }, catalog.quests().require(catalog.huashanSettings().entranceTrialId()).displayName() + "을 시작했습니다.");
    }

    public CompletableFuture<HuashanResult> recordDummyHit(UUID uuid, String dummyId) {
        return update(uuid, current -> {
            if (current.journey().activeActivityId().isEmpty()) return current;
            QuestDefinition quest = activeQuest(current);
            if (quest.objectiveType() != QuestObjectiveType.TRAINING
                    || !quest.targetId().filter(dummyId::equals).isPresent()) return current;
            int hits = Math.min(quest.requiredHits(), current.journey().dummyHits() + 1);
            return finishIfReady(withProgress(current, hits, current.journey().meditationSeconds()), quest);
        }, "수련 표적 진행도를 기록했습니다.");
    }

    public CompletableFuture<HuashanResult> meditateSecond(UUID uuid, long maxQi, long regeneration) {
        return update(uuid, current -> {
            PlayerJourney next = current;
            if (current.journey().activeActivityId().isPresent()) {
                QuestDefinition quest = activeQuest(current);
                if (quest.objectiveType() == QuestObjectiveType.TRAINING) {
                    int seconds = Math.min(quest.requiredMeditationSeconds(), current.journey().meditationSeconds() + 1);
                    next = finishIfReady(withProgress(current, current.journey().dummyHits(), seconds), quest);
                }
            }
            PlayerJourneyState journey = next.journey();
            if (journey.activeSkillId().isPresent()) {
                long qi = Math.min(maxQi, journey.currentQi() + regeneration);
                next = new PlayerJourney(next.content(), journey(next, journey.activeActivityId(), journey.dummyHits(),
                        journey.meditationSeconds(), journey.activeSkillId(), qi, journey.completedActivityIds()));
            }
            return next;
        }, "운기조식을 진행했습니다.");
    }

    public CompletableFuture<HuashanResult> cancelContinuousMeditation(UUID uuid) {
        return update(uuid, current -> {
            if (current.journey().activeActivityId().isEmpty()) return current;
            QuestDefinition quest = activeQuest(current);
            return quest.objectiveType() == QuestObjectiveType.TRAINING
                    && quest.meditationMode() == MeditationGoalMode.CONTINUOUS
                    ? withProgress(current, current.journey().dummyHits(), 0) : current;
        }, "운기조식 자세가 취소되었습니다.");
    }

    public CompletableFuture<HuashanResult> setRankForTest(UUID uuid, String organizationId, String rankId) {
        return update(uuid, current -> {
            if (!catalog.huashanSettings().organizationId().equals(organizationId)
                    || (!THIRD.equals(rankId) && !SECOND.equals(rankId))) {
                throw new IllegalArgumentException("이번 테스트에서는 화산파 삼대 또는 이대제자만 준비할 수 있습니다.");
            }
            PlayerContentState content = content(current, Optional.of(organizationId), Optional.of(rankId),
                    current.content().learnedSkillIds());
            PlayerJourney value = new PlayerJourney(content, current.journey());
            return withActivity(value, rankId.equals(THIRD)
                    ? catalog.huashanSettings().secondPromotionTrialId()
                    : catalog.huashanSettings().firstPromotionTrialId());
        }, catalog.factions().findOrganization(catalog.huashanSettings().organizationId()).orElseThrow().displayName()
                + " 승급 시험 준비를 완료했습니다.");
    }

    public CompletableFuture<HuashanResult> startPromotionTrial(UUID uuid) {
        return update(uuid, current -> {
            if (!current.content().organizationId().filter(catalog.huashanSettings().organizationId()::equals).isPresent())
                throw new IllegalStateException("화산파 소속이 아닙니다.");
            if (current.journey().activeActivityId().isPresent())
                throw new IllegalStateException("이미 진행 중인 시험 또는 퀘스트가 있습니다.");
            String rank = current.content().organizationRankId().orElseThrow(() -> new IllegalStateException("조직 계급이 없습니다."));
            String questId;
            if (THIRD.equals(rank)) {
                if (!current.content().learnedSkillIds().contains("LIUHE_ART"))
                    throw new IllegalStateException("육합공 수련을 먼저 완료해야 합니다.");
                questId = catalog.huashanSettings().secondPromotionTrialId();
            } else if (SECOND.equals(rank)) {
                questId = catalog.huashanSettings().firstPromotionTrialId();
            } else {
                throw new IllegalStateException("현재 계급에서는 시작할 승급 시험이 없습니다.");
            }
            return withActivity(current, questId);
        }, "화산파 승급 시험을 시작했습니다.");
    }

    public CompletableFuture<HuashanResult> recordExaminerHealth(UUID uuid, String examinerId,
            double remainingHealthPercent) {
        return update(uuid, current -> {
            QuestDefinition quest = activeQuest(current);
            if (quest.objectiveType() != QuestObjectiveType.NPC_HEALTH_THRESHOLD
                    || !quest.targetId().filter(examinerId::equals).isPresent()) return current;
            return remainingHealthPercent <= quest.healthThresholdPercent() ? complete(current, quest) : current;
        }, "시험관의 체력을 더 낮춰야 합니다.");
    }

    public CompletableFuture<HuashanResult> recordMonsterKill(UUID uuid, String monsterId) {
        return update(uuid, current -> {
            QuestDefinition quest = activeQuest(current);
            if (quest.objectiveType() != QuestObjectiveType.MONSTER_KILL
                    || !quest.targetId().filter(monsterId::equals).isPresent()) return current;
            return complete(current, quest);
        }, "승급 시험 대상 처치를 기록했습니다.");
    }

    public CompletableFuture<HuashanResult> clear(UUID uuid) {
        return update(uuid, current -> new PlayerJourney(
                content(current, Optional.empty(), Optional.empty(), current.content().learnedSkillIds()),
                journey(current, Optional.empty(), 0, 0, current.journey().activeSkillId(),
                        current.journey().currentQi(), current.journey().completedActivityIds())), "조직 소속과 진행 중 활동을 초기화했습니다.");
    }

    public CompletableFuture<HuashanResult> equip(UUID uuid, String skillId) {
        return update(uuid, current -> {
            SkillDefinition skill = catalog.findSkill(skillId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기술입니다."));
            if (skill.type() != SkillType.INTERNAL_ART || !current.content().learnedSkillIds().contains(skillId)) {
                throw new IllegalStateException("습득한 내공심법이 아닙니다.");
            }
            return new PlayerJourney(current.content(), journey(current, current.journey().activeActivityId(),
                    current.journey().dummyHits(), current.journey().meditationSeconds(), Optional.of(skillId),
                    current.journey().currentQi(), current.journey().completedActivityIds()));
        }, "활성 심법을 설정했습니다: " + skillId);
    }

    public CompletableFuture<HuashanResult> grantSkill(UUID uuid, String skillId) {
        return update(uuid, current -> {
            catalog.findSkill(skillId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기술입니다."));
            Set<String> skills = new HashSet<>(current.content().learnedSkillIds()); skills.add(skillId);
            return new PlayerJourney(content(current, current.content().organizationId(),
                    current.content().organizationRankId(), skills), current.journey());
        }, "기술을 지급했습니다: " + skillId);
    }

    public CompletableFuture<HuashanResult> revokeSkill(UUID uuid, String skillId) {
        return update(uuid, current -> {
            Set<String> skills = new HashSet<>(current.content().learnedSkillIds());
            skills.remove(skillId);
            Optional<String> active = current.journey().activeSkillId().filter(id -> !id.equals(skillId));
            return new PlayerJourney(content(current, current.content().organizationId(),
                    current.content().organizationRankId(), skills), journey(current,
                    current.journey().activeActivityId(), current.journey().dummyHits(),
                    current.journey().meditationSeconds(), active, current.journey().currentQi(),
                    current.journey().completedActivityIds()));
        }, "기술을 회수했습니다: " + skillId);
    }

    public CompletableFuture<HuashanResult> completeForTest(UUID uuid, String questId) {
        return update(uuid, current -> {
            QuestDefinition quest = activeQuest(current);
            if (!quest.id().equals(questId)) {
                throw new IllegalStateException("현재 진행 중인 활동이 아닙니다: " + questId);
            }
            return complete(current, quest);
        }, "테스트용 활동 완료: " + questId);
    }

    public CompletableFuture<HuashanResult> clampQi(UUID uuid, long maxQi) {
        return update(uuid, current -> {
            long clamped = Math.max(0, Math.min(maxQi, current.journey().currentQi()));
            if (clamped == current.journey().currentQi()) return current;
            return new PlayerJourney(current.content(), journey(current,
                    current.journey().activeActivityId(), current.journey().dummyHits(),
                    current.journey().meditationSeconds(), current.journey().activeSkillId(), clamped,
                    current.journey().completedActivityIds()));
        }, "현재 내공을 경지 최대치에 맞췄습니다.");
    }

    public CompletableFuture<HuashanResult> setQi(UUID uuid, long amount, long maxQi) {
        if (amount < 0 || amount > maxQi) return CompletableFuture.failedFuture(new IllegalArgumentException("내공은 0~최대 내공 범위여야 합니다."));
        return update(uuid, current -> new PlayerJourney(current.content(), journey(current,
                current.journey().activeActivityId(), current.journey().dummyHits(), current.journey().meditationSeconds(),
                current.journey().activeSkillId(), amount, current.journey().completedActivityIds())), "내공을 설정했습니다.");
    }

    private CompletableFuture<HuashanResult> update(UUID uuid, java.util.function.Function<PlayerJourney, PlayerJourney> action, String message) {
        Set<String> before = states.cached(uuid).map(value -> value.journey().completedActivityIds()).orElse(Set.of());
        return states.mutate(uuid, action).thenApply(value -> {
            Optional<String> completed = value.journey().completedActivityIds().stream()
                    .filter(id -> !before.contains(id)).findFirst();
            String resultMessage = completed.map(id -> "활동 완료: " + catalog.quests().require(id).displayName())
                    .orElse(message);
            return new HuashanResult(value, resultMessage, completed.isPresent());
        });
    }

    private QuestDefinition activeQuest(PlayerJourney state) {
        return catalog.quests().require(state.journey().activeActivityId()
                .orElseThrow(() -> new IllegalStateException("진행 중인 시험 또는 퀘스트가 없습니다.")));
    }

    private PlayerJourney finishIfReady(PlayerJourney state, QuestDefinition quest) {
        if (quest.objectiveType() != QuestObjectiveType.TRAINING) return state;
        if (state.journey().dummyHits() < quest.requiredHits()
                || state.journey().meditationSeconds() < quest.requiredMeditationSeconds()) return state;
        return complete(state, quest);
    }

    private PlayerJourney complete(PlayerJourney state, QuestDefinition quest) {
        quest.requiredRankId().ifPresent(required -> {
            if (!state.content().organizationId().filter(quest.organizationId()::equals).isPresent()
                    || !state.content().organizationRankId().filter(required::equals).isPresent()) {
                throw new IllegalStateException("시험의 소속 또는 계급 조건을 충족하지 않습니다: " + quest.id());
            }
        });
        Set<String> completed = new HashSet<>(state.journey().completedActivityIds()); completed.add(quest.id());
        Set<String> skills = new HashSet<>(state.content().learnedSkillIds()); quest.grantedSkillId().ifPresent(skills::add);
        Optional<String> organization = state.content().organizationId();
        Optional<String> rank = state.content().organizationRankId();
        if (quest.id().equals(catalog.huashanSettings().entranceTrialId())) organization = Optional.of(quest.organizationId());
        if (quest.resultingRankId().isPresent()) rank = quest.resultingRankId();
        Optional<String> activeSkill = state.journey().activeSkillId();
        if (quest.grantedSkillId().isPresent() && quest.id().equals(catalog.huashanSettings().liuheQuestId())) activeSkill = quest.grantedSkillId();
        PlayerContentState content = content(state, organization, rank, skills);
        PlayerJourneyState journey = journey(state, quest.nextQuestId(), 0, 0, activeSkill,
                state.journey().currentQi(), completed);
        return new PlayerJourney(content, journey);
    }

    private PlayerJourney withActivity(PlayerJourney state, String id) {
        return new PlayerJourney(state.content(), journey(state, Optional.of(id), 0, 0,
                state.journey().activeSkillId(), state.journey().currentQi(), state.journey().completedActivityIds()));
    }
    private PlayerJourney withProgress(PlayerJourney state, int hits, int seconds) {
        return new PlayerJourney(state.content(), journey(state, state.journey().activeActivityId(), hits, seconds,
                state.journey().activeSkillId(), state.journey().currentQi(), state.journey().completedActivityIds()));
    }
    private PlayerContentState content(PlayerJourney state, Optional<String> org, Optional<String> rank, Set<String> skills) {
        return new PlayerContentState(state.content().uuid(), org, rank, state.content().organizationContribution(), skills);
    }
    private PlayerJourneyState journey(PlayerJourney state, Optional<String> activity, int hits, int seconds,
            Optional<String> skill, long qi, Set<String> completed) {
        return new PlayerJourneyState(state.journey().uuid(), activity, hits, seconds, skill, qi, completed);
    }
}
