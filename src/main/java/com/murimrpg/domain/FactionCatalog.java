package com.murimrpg.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class FactionCatalog {
    private final Map<FactionAlignment, FactionAlignmentDefinition> alignments;
    private final Map<String, AllianceDefinition> alliances;
    private final Map<OrganizationCategory, OrganizationCategoryDefinition> categories;
    private final Map<String, RankSystemDefinition> rankSystems;
    private final Map<String, OrganizationDefinition> organizations;

    public FactionCatalog(
            List<FactionAlignmentDefinition> alignments,
            List<AllianceDefinition> alliances,
            List<OrganizationCategoryDefinition> categories,
            List<RankSystemDefinition> rankSystems,
            List<OrganizationDefinition> organizations
    ) {
        this.alignments = index(alignments, FactionAlignmentDefinition::id, "alignment");
        this.alliances = index(alliances, AllianceDefinition::id, "alliance");
        this.categories = index(categories, OrganizationCategoryDefinition::id, "category");
        this.rankSystems = index(rankSystems, RankSystemDefinition::id, "rank system");
        this.organizations = index(organizations, OrganizationDefinition::id, "organization");
    }

    public List<FactionAlignmentDefinition> alignments() { return List.copyOf(alignments.values()); }
    public List<AllianceDefinition> alliances() { return List.copyOf(alliances.values()); }
    public List<OrganizationCategoryDefinition> categories() { return List.copyOf(categories.values()); }
    public List<RankSystemDefinition> rankSystems() { return List.copyOf(rankSystems.values()); }
    public List<OrganizationDefinition> organizations() { return List.copyOf(organizations.values()); }
    public Optional<FactionAlignmentDefinition> findAlignment(FactionAlignment id) { return Optional.ofNullable(alignments.get(id)); }
    public Optional<AllianceDefinition> findAlliance(String id) { return Optional.ofNullable(alliances.get(id)); }
    public Optional<OrganizationCategoryDefinition> findCategory(OrganizationCategory id) { return Optional.ofNullable(categories.get(id)); }
    public Optional<RankSystemDefinition> findRankSystem(String id) { return Optional.ofNullable(rankSystems.get(id)); }
    public Optional<OrganizationDefinition> findOrganization(String id) { return Optional.ofNullable(organizations.get(id)); }

    private static <K, T> Map<K, T> index(List<T> values, Function<T, K> id, String type) {
        Map<K, T> result = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            T value = values.get(index);
            K key = id.apply(value);
            if (result.putIfAbsent(key, value) != null) {
                String root = switch (type) {
                    case "alignment" -> "factions.yml.alignments";
                    case "alliance" -> "factions.yml.alliances";
                    case "category" -> "factions.yml.categories";
                    case "rank system" -> "ranks.yml.rank-systems";
                    case "organization" -> "sects.yml.organizations";
                    default -> type;
                };
                throw new IllegalArgumentException(root + "[" + index + "].id: Duplicate "
                        + type + " ID: " + key);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }
}
