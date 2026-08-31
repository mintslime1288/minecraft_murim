package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.*;

import com.murimrpg.domain.*;
import com.murimrpg.infrastructure.config.ContentConfigLoader;
import com.murimrpg.infrastructure.config.CultivationConfigLoader;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OrganizationServiceTest {
    private final CultivationProgression progression = new CultivationConfigLoader()
            .load(Path.of("src/main/resources/realms.yml"));
    private final ContentCatalog content = new ContentConfigLoader().load(
            Path.of("src/main/resources/factions.yml"), Path.of("src/main/resources/sects.yml"),
            Path.of("src/main/resources/ranks.yml"), Path.of("src/main/resources/skills.yml"),
            Path.of("src/main/resources/monsters.yml"), Path.of("src/main/resources/quests.yml"), progression);
    private final OrganizationService service = new OrganizationService(content.factions());

    @Test
    void exposesOnlyFiveNineSectsOneGangOrganizationsInGame() {
        assertEquals(Set.of("HUASHAN_SECT", "ZHONGNAN_SECT", "WUDANG_SECT", "SHAOLIN_TEMPLE", "BEGGARS_GANG"),
                service.visibleOrganizations().stream().map(OrganizationDefinition::id).collect(Collectors.toSet()));
        assertEquals(5, service.visibleOrganizations().size());
    }

    @Test
    void rejectsHiddenOrganizationLookupButKeepsItInCatalog() {
        assertNotNull(service.require("NAMGUNG_FAMILY"));
        assertThrows(IllegalArgumentException.class, () -> service.requireVisible("NAMGUNG_FAMILY"));
        assertThrows(IllegalArgumentException.class, () -> service.requireVisible("DEMON_CULT"));
    }
}

