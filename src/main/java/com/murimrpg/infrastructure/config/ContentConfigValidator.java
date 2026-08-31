package com.murimrpg.infrastructure.config;

import com.murimrpg.domain.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ContentConfigValidator {
    public void validate(ContentCatalog content, CultivationProgression progression) {
        FactionCatalog factions = content.factions();
        List<String> errors = new ArrayList<>();
        Set<String> stageIds = new HashSet<>();
        progression.stages().forEach(stage -> stageIds.add(stage.stageId()));

        for (AllianceDefinition alliance : factions.alliances()) {
            if (factions.findAlignment(alliance.alignment()).isEmpty()) {
                errors.add("factions.yml.alliances[id=" + alliance.id() + "].alignment-id references missing "
                        + alliance.alignment());
            }
        }
        for (OrganizationCategoryDefinition category : factions.categories()) {
            if (factions.findAlliance(category.allianceId()).isEmpty()) {
                errors.add("factions.yml.categories[id=" + category.id() + "].alliance-id references missing "
                        + category.allianceId());
            }
        }
        for (OrganizationDefinition organization : factions.organizations()) {
            String path = "sects.yml.organizations[id=" + organization.id() + "]";
            AllianceDefinition alliance = factions.findAlliance(organization.allianceId()).orElse(null);
            OrganizationCategoryDefinition category = factions.findCategory(organization.category()).orElse(null);
            if (alliance == null) errors.add(path + ".alliance-id references missing " + organization.allianceId());
            if (category == null) {
                errors.add(path + ".category-id references missing " + organization.category());
            } else if (!category.allianceId().equals(organization.allianceId())) {
                errors.add(path + ".category-id belongs to alliance " + category.allianceId());
            }
            organization.rankSystemId().ifPresent(rankSystemId -> {
                RankSystemDefinition ranks = factions.findRankSystem(rankSystemId).orElse(null);
                if (ranks == null) {
                    errors.add(path + ".rank-system-id references missing " + rankSystemId);
                } else if (ranks.organizationType() != organization.type()) {
                    errors.add(path + ".rank-system-id type " + ranks.organizationType()
                            + " does not match organization type " + organization.type());
                }
            });
        }
        for (SkillDefinition skill : content.skills()) {
            String path = "skills.yml.skills[id=" + skill.id() + "]";
            if (!stageIds.contains(skill.requiredStageId())) {
                errors.add(path + ".required-stage-id references missing " + skill.requiredStageId());
            }
            skill.organizationId().ifPresent(id -> {
                if (factions.findOrganization(id).isEmpty()) {
                    errors.add(path + ".organization-id references missing " + id);
                }
            });
        }
        for (MonsterDefinition monster : content.monsters()) {
            for (String skillId : monster.skillIds()) {
                if (content.findSkill(skillId).isEmpty()) {
                    errors.add("monsters.yml.monsters[id=" + monster.id() + "].skill-ids references missing " + skillId);
                }
            }
        }
        for (QuestDefinition quest : content.quests().all()) {
            String path = "quests.yml.quests[id=" + quest.id() + "]";
            OrganizationDefinition organization = factions.findOrganization(quest.organizationId()).orElse(null);
            if (organization == null) {
                errors.add(path + ".organization-id references missing " + quest.organizationId());
                continue;
            }
            quest.requiredRankId().ifPresent(id -> requireOrganizationRank(factions, organization, id,
                    path + ".required-rank-id", errors));
            quest.resultingRankId().ifPresent(id -> requireOrganizationRank(factions, organization, id,
                    path + ".resulting-rank-id", errors));
            quest.grantedSkillId().ifPresent(id -> {
                SkillDefinition skill = content.findSkill(id).orElse(null);
                if (skill == null) errors.add(path + ".granted-skill-id references missing " + id);
                else if (!skill.organizationId().equals(java.util.Optional.of(quest.organizationId()))) {
                    errors.add(path + ".granted-skill-id belongs to another organization");
                }
            });
            quest.nextQuestId().ifPresent(id -> {
                if (content.quests().find(id).isEmpty()) errors.add(path + ".next-quest-id references missing " + id);
            });
            if (quest.objectiveType() == QuestObjectiveType.MONSTER_KILL
                    && content.findMonster(quest.targetId().orElse("")).isEmpty()) {
                errors.add(path + ".target-id references missing monster " + quest.targetId().orElse("<missing>"));
            }
        }
        for (QuestDefinition quest : content.quests().all()) {
            Set<String> seen = new HashSet<>();
            QuestDefinition current = quest;
            while (current.nextQuestId().isPresent()) {
                if (!seen.add(current.id())) {
                    errors.add("quests.yml.quests[id=" + quest.id() + "].next-quest-id forms a cycle");
                    break;
                }
                current = content.quests().find(current.nextQuestId().orElseThrow()).orElse(null);
                if (current == null) break;
            }
        }
        HuashanSettings huashan = content.huashanSettings();
        if (factions.findOrganization(huashan.organizationId()).isEmpty()) {
            errors.add("quests.yml.huashan.organization-id references missing " + huashan.organizationId());
        }
        for (String id : List.of(huashan.entranceTrialId(), huashan.liuheQuestId(),
                huashan.secondPromotionTrialId(), huashan.firstPromotionTrialId())) {
            if (content.quests().find(id).isEmpty()) errors.add("quests.yml.huashan references missing quest " + id);
        }
        content.quests().find(huashan.secondPromotionTrialId()).ifPresent(quest -> {
            if (quest.objectiveType() != QuestObjectiveType.NPC_HEALTH_THRESHOLD
                    || !quest.targetId().filter(huashan.examinerId()::equals).isPresent()) {
                errors.add("quests.yml.huashan.second-promotion-trial-id must reference the configured examiner NPC health quest");
            }
        });
        content.quests().find(huashan.firstPromotionTrialId()).ifPresent(quest -> {
            if (quest.objectiveType() != QuestObjectiveType.MONSTER_KILL) {
                errors.add("quests.yml.huashan.first-promotion-trial-id must reference a monster kill quest");
            }
        });
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid content configuration:\n - " + String.join("\n - ", errors));
        }
    }

    private void requireOrganizationRank(FactionCatalog factions, OrganizationDefinition organization,
            String rankId, String path, List<String> errors) {
        RankSystemDefinition system = organization.rankSystemId().flatMap(factions::findRankSystem).orElse(null);
        if (system == null || system.ranks().stream().noneMatch(rank -> rank.id().equals(rankId))) {
            errors.add(path + " references missing rank " + rankId + " for " + organization.id());
        }
    }
}
