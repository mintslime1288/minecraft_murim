package com.murimrpg.service;

import com.murimrpg.domain.SkillDefinition;
import com.murimrpg.domain.SkillType;
import java.util.Objects;

/** Exposes combat configuration without applying damage or touching Bukkit entities. */
public final class CombatService {
    private final SkillService skillService;

    public CombatService(SkillService skillService) {
        this.skillService = Objects.requireNonNull(skillService, "skillService");
    }

    public SkillDefinition requireMartialArt(String skillId) {
        SkillDefinition skill = skillService.require(skillId);
        if (skill.type() != SkillType.MARTIAL_ART) {
            throw new IllegalArgumentException("Skill is not a martial art: " + skillId);
        }
        return skill;
    }
}
