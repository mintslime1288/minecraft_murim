package com.murimrpg.service;

import com.murimrpg.domain.FactionCatalog;
import com.murimrpg.domain.OrganizationDefinition;
import com.murimrpg.domain.OrganizationCategory;
import java.util.List;
import java.util.Objects;

public final class OrganizationService {
    private final FactionCatalog catalog;

    public OrganizationService(FactionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public List<OrganizationDefinition> all() {
        return catalog.organizations();
    }

    public OrganizationDefinition require(String id) {
        return catalog.findOrganization(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization ID: " + id));
    }

    public List<OrganizationDefinition> visibleOrganizations() {
        return catalog.organizations().stream()
                .filter(value -> value.category() == OrganizationCategory.NINE_SECTS_ONE_GANG)
                .toList();
    }

    public OrganizationDefinition requireVisible(String id) {
        return visibleOrganizations().stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("현재 공개되지 않은 조직입니다: " + id));
    }
}
