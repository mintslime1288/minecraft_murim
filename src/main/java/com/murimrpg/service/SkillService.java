package com.murimrpg.service;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.SkillDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SkillService {
    private final ContentCatalog catalog;
    private final CultivationProgression progression;

    public SkillService(ContentCatalog catalog, CultivationProgression progression) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.progression = Objects.requireNonNull(progression, "progression");
    }

    public List<SkillDefinition> all() {
        return catalog.skills();
    }

    public SkillDefinition require(String id) {
        return catalog.findSkill(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill ID: " + id));
    }

    public boolean canLearn(String skillId, Optional<String> playerOrganizationId, String currentStageId) {
        SkillDefinition skill = require(skillId);
        boolean correctOrganization = skill.organizationId().isEmpty()
                || skill.organizationId().equals(playerOrganizationId);
        return correctOrganization
                && progression.stage(currentStageId).order()
                >= progression.stage(skill.requiredStageId()).order();
    }
}
